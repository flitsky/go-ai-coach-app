#!/usr/bin/env python3
"""E5 — 폰에서 사람 모델을 올리면 한 수에 얼마가 드나(백로그 #215 단계 ①).

앱이 깔린 폰에서 `adb run-as`로 **앱 안의 KataGo를 그대로** 띄워, 설정을 바꿔 가며 잰다.

재는 것
- **뜨는 시간**: 프로세스를 띄워 첫 응답이 오기까지(모델을 읽는 시간) — 주 모델만 / 주 + 사람.
- **메모리**: 뜬 직후와 일을 시킨 뒤의 RSS(`/proc/<pid>/status`).
- **평가 1회**: `kata-raw-nn`(주 모델) · `kata-raw-human-nn`(사람 모델). 대칭 번호를 돌려 캐시를 피한다.
- **탐색**: 지금 캐릭터 경로(`kata-search_analyze`) 16·32·40방문. 매번 캐시를 비운다.
- **go-bot 레시피 한 수**: 사람 정책에서 착수 + 주 모델 40방문(`genmove`).
- **JSON 분석 프로세스**: 1방문(사람 정책 받기) 질의, 그리고 영역까지 받는 16·64·128방문 질의(분석을 얼마나 깊게 돌릴 수 있나).
- 손잡이 둘: **스레드**(1·2·4)와 **NN 버퍼**(19 → 13).

⚠️ 시간은 맥에서 `adb` 왕복을 포함해 잰다 — 왕복 값(`name` 명령)을 같이 적는다.
⚠️ 사람 모델은 폰에 미리 넣어 둔다(README의 「폰 준비」). 이 스크립트는 넣지도 지우지도 않는다.

    python3 engine-lab/experiments/e5_phone_cost/run.py --label s23 --adb-serial <시리얼>
"""
from __future__ import annotations

import argparse
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.analysis import AnalysisEngine  # noqa: E402
from lab.board import Position, load_positions  # noqa: E402
from lab.gtp import AdbTarget, GtpEngine, build_argv  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e5_phone_cost"
PROFILE = "rank_5k"

# go-bot / `gtp_human5k_example.cfg`의 착수 설정(리서치 §3.4) — 사람과 둬서 보정된 유일한 레시피.
GOBOT_OVERRIDES = {
    "humanSLProfile": PROFILE,
    "humanSLChosenMoveProp": "1.0",
    "humanSLChosenMoveIgnorePass": "true",
    "humanSLChosenMovePiklLambda": "100000000",
    "chosenMoveTemperatureEarly": "0.85",
    "chosenMoveTemperature": "0.70",
    "chosenMoveTemperatureHalflife": "80",
    "chosenMoveTemperatureOnlyBelowProb": "0.01",
    "rootNumSymmetriesToSample": "2",
    "humanSLCpuctPermanent": "0.2",
}


@dataclass(frozen=True)
class GtpConfig:
    name: str
    human: bool
    threads: int
    buffer: int | None = None  # None = 앱 그대로(19줄 버퍼)
    gobot: bool = False


GTP_CONFIGS = (
    GtpConfig("main-t1", human=False, threads=1),  # 지금 앱의 GTP 프로세스
    GtpConfig("human-t1", human=True, threads=1),
    GtpConfig("human-t2", human=True, threads=2),
    GtpConfig("human-t4", human=True, threads=4),
    GtpConfig("human-t1-buf13", human=True, threads=1, buffer=13),
    GtpConfig("human-t4-buf13", human=True, threads=4, buffer=13),
    GtpConfig("gobot-t1", human=True, threads=1, gobot=True),
    GtpConfig("gobot-t4", human=True, threads=4, gobot=True),
)


def adb_shell(adb: AdbTarget, script: str, *, run_as: bool = True) -> str:
    argv = ["adb", "-s", adb.serial, "shell"]
    argv += ["run-as", adb.package, "sh", "-c", script] if run_as else [script]
    return subprocess.run(argv, capture_output=True, text=True).stdout


def engine_memory(adb: AdbTarget, mode: str) -> dict:
    """`mode`(gtp·analysis)로 뜬 KataGo 프로세스의 RSS(kB). 같은 uid라 `run-as` 안에서 `/proc`을 읽을 수 있다."""
    run_as = ["adb", "-s", adb.serial, "shell", "run-as", adb.package]
    read = lambda *argv: subprocess.run(run_as + list(argv), capture_output=True, text=True).stdout  # noqa: E731
    for pid in read("pidof", "libkatago.so").split():
        cmdline = read("cat", f"/proc/{pid}/cmdline").replace("\0", " ")
        if f" {mode} " not in cmdline:
            continue
        result: dict = {}
        for line in read("cat", f"/proc/{pid}/status").splitlines():
            key, _, value = line.partition(":")
            if key in ("VmRSS", "VmHWM", "RssAnon", "RssFile"):
                result[key] = int("".join(ch for ch in value if ch.isdigit()))
        return result
    return {}


def device_state(adb: AdbTarget) -> dict:
    battery = adb_shell(adb, "dumpsys battery | grep -m1 temperature", run_as=False)
    meminfo = adb_shell(adb, "grep -E 'MemAvailable' /proc/meminfo", run_as=False)
    digits = lambda text: int("".join(ch for ch in text if ch.isdigit()) or 0)  # noqa: E731
    return {"batteryTempTenthC": digits(battery), "memAvailableKb": digits(meminfo)}


def timed(fn) -> tuple[float, object]:
    start = time.perf_counter()
    value = fn()
    return (time.perf_counter() - start) * 1000.0, value


def pick_position(positions: list[Position], size: int) -> Position:
    """그 판 크기에서 수순이 가운데쯤인 국면 하나 — 평가·탐색 시간은 국면을 크게 타지 않는다."""
    same = sorted((p for p in positions if p.size == size), key=lambda p: len(p.moves))
    if not same:
        raise RuntimeError(f"{size}줄 국면이 없다")
    return same[len(same) // 2]


def phone_argv(adb: AdbTarget, *, mode: str, config: str, overrides: dict[str, str], human_model: str | None) -> list[str]:
    merged = {**overrides, "logDir": f"{adb.katago_dir}/logs", "homeDataDir": f"{adb.katago_dir}/home"}
    return build_argv(
        mode=mode,
        katago=adb.resolve_executable(),
        model=adb.model_path(),
        config=f"{adb.katago_dir}/{config}",
        overrides=merged,
        human_model=human_model,
        adb=adb,
    )


def measure_gtp(adb: AdbTarget, cfg: GtpConfig, args, positions: list[Position], run: RunDir) -> None:
    overrides = {**ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), "numSearchThreads": str(cfg.threads)}
    if cfg.buffer:
        overrides |= {"maxBoardXSizeForNNBuffer": str(cfg.buffer), "maxBoardYSizeForNNBuffer": str(cfg.buffer)}
    if cfg.gobot:
        overrides |= {**GOBOT_OVERRIDES, "maxVisits": "40"}
    before = device_state(adb)
    argv = phone_argv(adb, mode="gtp", config="gtp_learning.cfg", overrides=overrides, human_model=args.human_model_on_phone if cfg.human else None)
    startup_ms, engine = timed(lambda: GtpEngine(argv, label=cfg.name, startup_timeout=300.0))
    assert isinstance(engine, GtpEngine)
    try:
        run.sample({"config": cfg.name, "kind": "startup", "ms": round(startup_ms, 1), "version": engine.version, **before})
        run.sample({"config": cfg.name, "kind": "memory", "when": "loaded", **engine_memory(adb, "gtp")})
        for _ in range(10):
            ms, _ = timed(lambda: engine.send("name"))
            run.sample({"config": cfg.name, "kind": "roundtrip", "ms": round(ms, 2)})
        if cfg.human and not cfg.gobot:
            engine.set_param("humanSLProfile", PROFILE)
        for size in args.size_list:
            if cfg.buffer and size > cfg.buffer:
                continue
            position = pick_position(positions, size)
            engine.setup(position)
            player = position.next_player
            base = {"config": cfg.name, "size": size, "position": position.id, "moves": len(position.moves)}
            if cfg.gobot:
                for _ in range(args.search_repeats):
                    engine.clear_cache()
                    ms, move = timed(lambda: engine.genmove(player))
                    engine.send("undo")
                    run.sample({**base, "kind": "gobot-genmove", "visits": 40, "ms": round(ms, 1), "move": move})
                continue
            for index in range(args.repeats):
                ms, _ = timed(lambda: engine.send(f"kata-raw-nn {index % 8}"))
                run.sample({**base, "kind": "raw-nn", "ms": round(ms, 1)})
            if cfg.human:
                for index in range(args.repeats):
                    ms, _ = timed(lambda: engine.send(f"kata-raw-human-nn {index % 8}"))
                    run.sample({**base, "kind": "raw-human-nn", "ms": round(ms, 1)})
            for visits in args.visit_list:
                for _ in range(args.search_repeats):
                    engine.clear_cache()
                    result = engine.search_analyze(player, visits=visits, time_ms=None, max_candidates=5)
                    run.sample({**base, "kind": "search", "visits": visits, "ms": round(result.elapsed_ms, 1), "rootVisits": result.root_visits})
        run.sample({"config": cfg.name, "kind": "memory", "when": "worked", **engine_memory(adb, "gtp"), **device_state(adb)})
    finally:
        engine.close()
    time.sleep(args.cooldown)


def measure_analysis(adb: AdbTarget, name: str, human: bool, args, positions: list[Position], run: RunDir, *, with_gtp: bool) -> None:
    """앱의 JSON 분석 프로세스(1 × 4스레드). `with_gtp`면 대국용 GTP 프로세스를 함께 띄워 **둘이 같이 떠 있을 때**의 메모리를 본다."""
    overrides = {
        **{k: v for k, v in ap.GTP_STARTUP_OVERRIDES.items() if k not in ("numSearchThreads", "allowResignation", "logAllGTPCommunication", "startupPrintMessageToStderr")},
        "numAnalysisThreads": str(ap.ANALYSIS_NUM_ANALYSIS_THREADS),
        "numSearchThreads": str(ap.ANALYSIS_SEARCH_THREADS),
        "logAllRequests": "false",
        "logAllResponses": "false",
    }
    gtp: GtpEngine | None = None
    before = device_state(adb)
    if with_gtp:
        gtp = GtpEngine(phone_argv(adb, mode="gtp", config="gtp_learning.cfg", overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), human_model=None), label="gtp-beside", startup_timeout=300.0)
    argv = phone_argv(adb, mode="analysis", config="analysis_learning.cfg", overrides=overrides, human_model=args.human_model_on_phone if human else None)
    startup_ms, engine = timed(lambda: AnalysisEngine(argv, label=name, startup_timeout=300.0))
    assert isinstance(engine, AnalysisEngine)
    try:
        run.sample({"config": name, "kind": "startup", "ms": round(startup_ms, 1), "version": engine.version, **before})
        run.sample({"config": name, "kind": "memory", "when": "loaded", **engine_memory(adb, "analysis")})
        if gtp is not None:
            run.sample({"config": name, "kind": "memory", "when": "gtp-beside", **engine_memory(adb, "gtp")})
        settings = {"humanSLProfile": PROFILE} if human else None
        for size in args.size_list:
            same = [p for p in positions if p.size == size]
            for index in range(min(args.repeats, len(same))):
                position = same[index]
                base = {"config": name, "size": size, "position": position.id, "moves": len(position.moves)}
                ms, data = timed(lambda: engine.query(position, max_visits=1, include_policy=True, override_settings=settings))
                assert isinstance(data, dict)
                run.sample({**base, "kind": "json-1visit", "ms": round(ms, 1), "hasHumanPolicy": "humanPolicy" in data})
            # 분석을 얼마나 깊게 돌릴 수 있나 — 방문 수별 시간(형세·영역·추천 수가 한 질의에 온다). 국면을 바꿔 캐시를 피한다.
            for offset, visits in enumerate(args.analysis_visit_list):
                for index in range(min(args.search_repeats, len(same))):
                    position = same[-1 - (offset * args.search_repeats + index) % len(same)]
                    base = {"config": name, "size": size, "position": position.id, "moves": len(position.moves)}
                    query = {**engine.build_query(position, max_visits=visits), "includeOwnership": True}
                    ms, data = timed(lambda: engine.query_many([query])[0])
                    assert isinstance(data, dict)
                    run.sample({**base, "kind": "json-ownership", "visits": visits, "ms": round(ms, 1), "rootVisits": data.get("rootInfo", {}).get("visits"), "hasOwnership": "ownership" in data})
        run.sample({"config": name, "kind": "memory", "when": "worked", **engine_memory(adb, "analysis"), **device_state(adb)})
    finally:
        engine.close()
        if gtp is not None:
            gtp.close()
    time.sleep(args.cooldown)


def median(rows: list[dict]) -> float | None:
    values = [row["ms"] for row in rows]
    return round(statistics.median(values), 1) if values else None


def summarize(rows: list[dict]) -> tuple[dict, str]:
    configs = list(dict.fromkeys(row["config"] for row in rows))
    summary: dict = {"configs": {}}
    lines = ["# E5 — 폰 비용", "", "## 뜨는 시간과 메모리", "", "| 설정 | 뜨는 시간 | RSS(뜬 직후) | RSS(일한 뒤) | 최대 RSS | adb 왕복 |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
    mb = lambda kb: "–" if kb is None else f"{kb / 1024:.0f}MB"  # noqa: E731
    for name in configs:
        own = [row for row in rows if row["config"] == name]
        startup = next((row["ms"] for row in own if row["kind"] == "startup"), None)
        loaded = next((row for row in own if row["kind"] == "memory" and row.get("when") == "loaded"), {})
        worked = next((row for row in own if row["kind"] == "memory" and row.get("when") == "worked"), {})
        beside = next((row for row in own if row["kind"] == "memory" and row.get("when") == "gtp-beside"), None)
        trip = median([row for row in own if row["kind"] == "roundtrip"])
        entry = {
            "startupMs": startup,
            "rssLoadedKb": loaded.get("VmRSS"),
            "rssWorkedKb": worked.get("VmRSS"),
            "rssPeakKb": worked.get("VmHWM"),
            "gtpBesideRssKb": beside.get("VmRSS") if beside else None,
            "roundtripMs": trip,
            "bySize": {},
        }
        note = f" (+ 옆의 GTP {mb(beside.get('VmRSS'))})" if beside else ""
        lines.append(f"| {name} | {startup / 1000:.1f}초 | {mb(loaded.get('VmRSS'))}{note} | {mb(worked.get('VmRSS'))} | {mb(worked.get('VmHWM'))} | {trip if trip is not None else '–'}ms |")
        for size in sorted({row["size"] for row in own if "size" in row}):
            sized = [row for row in own if row.get("size") == size]
            kinds: dict = {}
            for kind in ("raw-nn", "raw-human-nn", "gobot-genmove", "json-1visit"):
                value = median([row for row in sized if row["kind"] == kind])
                if value is not None:
                    kinds[kind] = value
            for kind in ("search", "json-ownership"):
                for visits in sorted({row["visits"] for row in sized if row["kind"] == kind}):
                    kinds[f"{kind}-{visits}"] = median([row for row in sized if row["kind"] == kind and row["visits"] == visits])
            entry["bySize"][str(size)] = kinds
        summary["configs"][name] = entry
    lines += ["", "## 한 번에 드는 시간(중앙값, ms)", "", "| 설정 | 판 | 무엇 | 시간 |", "| --- | ---: | --- | ---: |"]
    for name, entry in summary["configs"].items():
        for size, kinds in entry["bySize"].items():
            for kind, value in kinds.items():
                lines.append(f"| {name} | {size} | {kind} | {value} |")
    return summary, "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--adb-serial", required=True)
    parser.add_argument("--adb-package", default="com.zenit9hub.ai.baduk")
    parser.add_argument("--human-model-on-phone", default="files/katago/human-b18c384nbt.bin.gz")
    parser.add_argument("--positions", default=str(paths.positions_dir() / "selfplay-human-v1.json"))
    parser.add_argument("--sizes", default="13,19")
    parser.add_argument("--visits", default="16,32,40")
    parser.add_argument("--analysis-visits", default="16,64,128", help="JSON 분석(4스레드)에서 잴 방문 수")
    parser.add_argument("--repeats", type=int, default=5, help="평가 1회·1방문 질의를 몇 번")
    parser.add_argument("--search-repeats", type=int, default=2, help="탐색·genmove를 몇 번")
    parser.add_argument("--cooldown", type=float, default=8.0, help="설정 사이에 쉬는 시간(초) — 폰이 식게")
    parser.add_argument("--only", default="", help="쉼표로 고른 설정만(예: main-t1,human-t1)")
    args = parser.parse_args()
    args.size_list = [int(s) for s in args.sizes.split(",") if s]
    args.visit_list = [int(v) for v in args.visits.split(",") if v]
    args.analysis_visit_list = [int(v) for v in args.analysis_visits.split(",") if v]
    only = {name for name in args.only.split(",") if name}

    adb = AdbTarget(serial=args.adb_serial, package=args.adb_package)
    positions = load_positions(Path(args.positions))
    model = adb_shell(adb, "getprop ro.product.model; getprop ro.soc.model; getprop ro.build.version.release", run_as=False).split()
    run = RunDir(EXPERIMENT, args.label, {k: v for k, v in vars(args).items() if k not in ("size_list", "visit_list", "analysis_visit_list")})
    run.meta["device"] = {"model": model[0] if model else None, "soc": model[1] if len(model) > 1 else None, "android": model[2] if len(model) > 2 else None}
    run.meta["humanProfile"] = PROFILE
    run._write_meta()

    wanted = lambda name: not only or name in only  # noqa: E731
    for cfg in GTP_CONFIGS:
        if wanted(cfg.name):
            print(f"[{cfg.name}]", flush=True)
            measure_gtp(adb, cfg, args, positions, run)
    for name, human, with_gtp in (("analysis-main", False, False), ("analysis-human", True, False), ("analysis-main+gtp", False, True), ("analysis-human+gtp", True, True)):
        if wanted(name):
            print(f"[{name}]", flush=True)
            measure_analysis(adb, name, human, args, positions, run, with_gtp=with_gtp)

    summary, markdown = summarize(run.completed_samples())
    run.finish(summary, markdown)
    print(markdown)
    print(f"→ {run.path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
