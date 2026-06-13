#!/usr/bin/env python3
"""Compare KataGo GTP fast search and JSON position analysis latency.

The default thread settings mirror the current Android app:
- GTP fast path: numSearchThreads=1
- JSON analysis path: numAnalysisThreads=1, numSearchThreads=4
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import subprocess
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_KATAGO = "/opt/homebrew/bin/katago"
DEFAULT_MODEL = "/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
DEFAULT_GTP_CONFIG = "app-android/src/friend/assets/katago/gtp_learning.cfg"
DEFAULT_ANALYSIS_CONFIG = "app-android/src/friend/assets/katago/analysis_learning.cfg"
DEFAULT_BASE_MOVES = (("B", "E5"), ("W", "C4"), ("B", "E3"))
BENCHMARK_VARIANT_MOVES = (
    "A9",
    "J1",
    "A1",
    "J9",
    "E1",
    "E9",
    "A5",
    "J5",
    "C3",
    "G7",
    "C7",
    "G3",
    "B8",
    "H2",
    "B2",
)
INFO_BOUNDARY = re.compile(r"(?=\binfo move\s)")
WHITESPACE = re.compile(r"\s+")


@dataclass(frozen=True)
class BenchmarkPosition:
    name: str
    moves: list[list[str]]

    @property
    def next_player(self) -> str:
        return "B" if len(self.moves) % 2 == 0 else "W"


def benchmark_variant_position(
    base: BenchmarkPosition,
    sample_index: int,
) -> BenchmarkPosition:
    if sample_index <= 0:
        return base

    moves = [list(move) for move in base.moves]
    added = 0
    for move in BENCHMARK_VARIANT_MOVES:
        if added >= sample_index:
            break
        color = "B" if len(moves) % 2 == 0 else "W"
        if any(existing[1] == move for existing in moves):
            continue
        moves.append([color, move])
        added += 1
    return BenchmarkPosition(name=base.name, moves=moves)


class GtpEngine:
    def __init__(self, args: argparse.Namespace) -> None:
        overrides = [
            f"numSearchThreads={args.gtp_search_threads}",
            "logToStderr=false",
            "logAllGTPCommunication=false",
            "logSearchInfo=false",
            "allowResignation=false",
            "startupPrintMessageToStderr=false",
        ]
        if args.deterministic:
            overrides.append("nnRandomize=false")
        command = [
            args.katago,
            "gtp",
            "-model",
            args.model,
            "-config",
            args.gtp_config,
            "-override-config",
            ",".join(overrides),
        ]
        self.process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )

    def close(self) -> None:
        try:
            self.command("quit")
        except Exception:
            pass
        self.process.terminate()
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()

    def command(self, command: str) -> str:
        assert self.process.stdin is not None
        assert self.process.stdout is not None
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()
        lines: list[str] = []
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError(f"KataGo GTP process exited while waiting for: {command}")
            stripped = line.rstrip("\n")
            if stripped == "":
                if lines:
                    break
                continue
            lines.append(stripped)
        first = lines[0] if lines else ""
        if first.startswith("?"):
            raise RuntimeError(f"KataGo GTP command failed `{command}`: {' '.join(lines)}")
        if not first.startswith("="):
            raise RuntimeError(f"Unexpected GTP response for `{command}`: {' '.join(lines)}")
        return "\n".join(lines).removeprefix("=").strip()

    def sync_position(self, position: BenchmarkPosition) -> None:
        self.command("boardsize 9")
        self.command("komi 6.5")
        self.command("kata-set-rules japanese")
        self.command("clear_board")
        for color, move in position.moves:
            self.command(f"play {color} {move}")

    def analyze(
        self,
        position: BenchmarkPosition,
        visits: int,
        time_cap_ms: int,
        clear_cache: bool,
    ) -> tuple[dict[str, Any], float]:
        self.sync_position(position)
        if clear_cache:
            self.command("clear_cache")
        self.command(f"kata-set-param maxVisits {visits}")
        self.command(f"kata-set-param maxTime {time_cap_ms / 1000.0}")
        centiseconds = max(1, math.ceil(time_cap_ms / 10))
        start = time.perf_counter()
        response = self.command(f"kata-search_analyze {position.next_player} {centiseconds}")
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        return parse_gtp_response(response), elapsed_ms


class JsonAnalysisEngine:
    def __init__(self, args: argparse.Namespace) -> None:
        overrides = [
            f"numAnalysisThreads={args.json_analysis_threads}",
            f"numSearchThreads={args.json_search_threads}",
            "logToStderr=false",
            "logAllRequests=false",
            "logAllResponses=false",
            "logSearchInfo=false",
        ]
        if args.deterministic:
            overrides.append("nnRandomize=false")
        command = [
            args.katago,
            "analysis",
            "-model",
            args.model,
            "-config",
            args.analysis_config,
            "-override-config",
            ",".join(overrides),
        ]
        self.process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )

    def close(self) -> None:
        if self.process.stdin:
            self.process.stdin.close()
        self.process.terminate()
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()

    def analyze(
        self,
        query_id: str,
        position: BenchmarkPosition,
        visits: int,
        time_cap_ms: int,
    ) -> tuple[dict[str, Any], float]:
        assert self.process.stdin is not None
        assert self.process.stdout is not None
        query = {
            "id": query_id,
            "rules": "japanese",
            "komi": 6.5,
            "boardXSize": 9,
            "boardYSize": 9,
            "initialPlayer": "B",
            "initialStones": [],
            "moves": position.moves,
            "analyzeTurns": [len(position.moves)],
            "maxVisits": visits,
            "includePolicy": False,
            "includeOwnership": False,
            "includeMovesOwnership": False,
            "overrideSettings": {"maxTime": time_cap_ms / 1000.0},
        }
        start = time.perf_counter()
        self.process.stdin.write(json.dumps(query, separators=(",", ":")) + "\n")
        self.process.stdin.flush()
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError("KataGo analysis process exited before returning a response")
            response = json.loads(line)
            if response.get("id") != query_id:
                continue
            if response.get("isDuringSearch"):
                continue
            if "error" in response:
                raise RuntimeError(f"KataGo JSON query failed: {response['error']}")
            return normalize_json_response(response), (time.perf_counter() - start) * 1000.0


def parse_gtp_response(response: str) -> dict[str, Any]:
    move_infos = []
    visits_by_move: dict[str, int] = {}
    for part in INFO_BOUNDARY.split(response):
        info = part.strip()
        if not info.startswith("info move "):
            continue
        tokens = [token for token in WHITESPACE.split(info) if token]
        if len(tokens) < 3:
            continue
        move = tokens[2]
        fields: dict[str, str] = {}
        index = 3
        while index < len(tokens) - 1:
            key = tokens[index]
            if key in {"pv", "info"}:
                break
            fields[key] = tokens[index + 1]
            index += 2
        visits = int(fields["visits"]) if fields.get("visits", "").isdigit() else None
        if visits is not None:
            previous = visits_by_move.get(move)
            if previous is None or visits >= previous:
                visits_by_move[move] = visits
        move_infos.append(
            {
                "move": move,
                "order": int(fields.get("order", "999999")),
                "visits": visits,
                "winrate": optional_float(fields.get("winrate")),
                "scoreLead": optional_float(fields.get("scoreLead")),
                "prior": optional_float(fields.get("prior")),
            },
        )
    root_visits = sum(visits_by_move.values()) if visits_by_move else None
    return {
        "rootVisits": root_visits,
        "moveInfoCount": len(move_infos),
        "moveInfos": sorted(move_infos, key=lambda item: item["order"]),
    }


def normalize_json_response(response: dict[str, Any]) -> dict[str, Any]:
    root = response.get("rootInfo") or {}
    move_infos = response.get("moveInfos") or []
    return {
        "rootVisits": root.get("visits"),
        "moveInfoCount": len(move_infos),
        "moveInfos": sorted(
            [
                {
                    "move": item.get("move"),
                    "order": item.get("order", 999999),
                    "visits": item.get("visits"),
                    "winrate": item.get("winrate"),
                    "scoreLead": item.get("scoreLead"),
                    "prior": item.get("prior"),
                }
                for item in move_infos
                if isinstance(item, dict)
            ],
            key=lambda item: item["order"],
        ),
    }


def optional_float(value: str | None) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = math.ceil(len(ordered) * pct) - 1
    return ordered[max(0, min(index, len(ordered) - 1))]


def summarize(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[(row["mode"], row["visitsRequest"])].append(row)

    results = []
    for (mode, visits), group in sorted(grouped.items(), key=lambda item: (item[0][0], item[0][1])):
        elapsed_values = [float(row["elapsedMs"]) for row in group]
        root_values = [
            int(row["rootVisits"])
            for row in group
            if row.get("rootVisits") is not None
        ]
        max_elapsed = max(elapsed_values)
        results.append(
            {
                "mode": mode,
                "visits": visits,
                "samples": len(group),
                "shortCount": sum(1 for row in group if row["fill"] == "SHORT"),
                "elapsedMinMs": round(min(elapsed_values), 3),
                "elapsedAvgMs": round(mean(elapsed_values), 3),
                "elapsedMaxMs": round(max_elapsed, 3),
                "elapsedP90Ms": round(percentile(elapsed_values, 0.90), 3),
                "rootVisitsMin": min(root_values) if root_values else None,
                "rootVisitsAvg": round(mean(root_values), 3) if root_values else None,
                "rootVisitsMax": max(root_values) if root_values else None,
                "recommendedTimeCapMs": int(math.ceil((max_elapsed * 1.25) / 50.0) * 50),
            },
        )
    return results


def write_markdown(summary: dict[str, Any], path: Path) -> None:
    by_mode_visit = {
        (result["mode"], result["visits"]): result
        for result in summary["results"]
    }
    lines = [
        "# 엔진 search mode 벤치마크 결과",
        "",
        "## 조건",
        "",
        f"- samplesPerVisit: `{summary['samples']}`",
        f"- visits: `{', '.join(str(value) for value in summary['visits'])}`",
        f"- timeCap: `{summary['timeCapMs']}ms`",
        f"- deterministic: `{summary['deterministic']}`",
        f"- GTP searchThreads: `{summary['gtpSearchThreads']}`",
        f"- JSON analysisThreads/searchThreads: `{summary['jsonAnalysisThreads']}` / `{summary['jsonSearchThreads']}`",
        f"- GTP clearCacheBeforeAnalyze: `{summary['gtpClearCache']}`",
        f"- basePosition: `{', '.join(f'{color} {move}' for color, move in summary['baseMoves'])}`",
        "",
        "GTP fast path는 JSON `rootInfo.visits`를 제공하지 않으므로 root 값은 후보별 `visits` 합산 추정치다. JSON position analysis의 root 값은 `rootInfo.visits` 원본이다.",
        "",
        "## 결과",
        "",
        "| Mode | Visits | Samples | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for result in summary["results"]:
        lines.append(
            "| {mode} | {visits} | {samples} | {short} | {min_ms} | {avg_ms} | {max_ms} | {p90_ms} | {root_avg} | {recommended}ms |".format(
                mode=result["mode"],
                visits=result["visits"],
                samples=result["samples"],
                short=result["shortCount"],
                min_ms=result["elapsedMinMs"],
                avg_ms=result["elapsedAvgMs"],
                max_ms=result["elapsedMaxMs"],
                p90_ms=result["elapsedP90Ms"],
                root_avg=result["rootVisitsAvg"],
                recommended=result["recommendedTimeCapMs"],
            ),
        )

    lines.extend(
        [
            "",
            "## GTP 대비 JSON 비율",
            "",
            "| Visits | GTP avg ms | JSON avg ms | JSON/GTP |",
            "| ---: | ---: | ---: | ---: |",
        ],
    )
    for visits in summary["visits"]:
        gtp = by_mode_visit.get(("gtp_stateful_fast_clearcache", visits))
        js = by_mode_visit.get(("json_position_analysis", visits))
        if not gtp or not js:
            continue
        gtp_avg = float(gtp["elapsedAvgMs"])
        json_avg = float(js["elapsedAvgMs"])
        ratio = json_avg / gtp_avg if gtp_avg > 0 else 0.0
        lines.append(f"| {visits} | {gtp_avg} | {json_avg} | {ratio:.2f}x |")

    lines.extend(
        [
            "",
            "## 해석",
            "",
            "- 이 결과는 맥북 Homebrew KataGo Metal backend 기준이다.",
            "- GTP fast path는 현재 Android 앱의 AI vs AI 격리 흐름에 맞춰 `clear_cache` 후 측정했다.",
            "- JSON position analysis는 요청 payload에 전체 수순과 `maxVisits`를 넣는 방식이므로 AI vs AI 레벨 오염을 줄이기 쉽다.",
            "- 폰 기본값 전환 여부는 이 결과만으로 결정하지 않고, 실기기 latency와 `rootInfo.visits` fill 수집 후 판단한다.",
        ],
    )
    path.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", type=int, default=5)
    parser.add_argument("--visits", default="16,32,64")
    parser.add_argument("--time-cap-ms", type=int, default=5_000)
    parser.add_argument("--deterministic", action="store_true")
    parser.add_argument("--gtp-clear-cache", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--gtp-search-threads", type=int, default=1)
    parser.add_argument("--json-analysis-threads", type=int, default=1)
    parser.add_argument("--json-search-threads", type=int, default=4)
    parser.add_argument("--katago", default=os.environ.get("KATAGO_BIN", DEFAULT_KATAGO))
    parser.add_argument("--model", default=os.environ.get("KATAGO_MODEL", DEFAULT_MODEL))
    parser.add_argument("--gtp-config", default=os.environ.get("KATAGO_GTP_CONFIG", DEFAULT_GTP_CONFIG))
    parser.add_argument("--analysis-config", default=os.environ.get("KATAGO_ANALYSIS_CONFIG", DEFAULT_ANALYSIS_CONFIG))
    parser.add_argument("--out-dir", type=Path, default=ROOT / "docs" / "engine-benchmark-logs" / "search-mode-latest")
    args = parser.parse_args()

    visits = [int(value.strip()) for value in args.visits.split(",") if value.strip()]
    args.out_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = args.out_dir / "samples.jsonl"
    summary_path = args.out_dir / "summary.json"
    markdown_path = args.out_dir / "summary.md"

    base = BenchmarkPosition(
        name="b16-best-3-variants",
        moves=[list(move) for move in DEFAULT_BASE_MOVES],
    )
    rows: list[dict[str, Any]] = []

    gtp = GtpEngine(args)
    json_engine = JsonAnalysisEngine(args)
    try:
        # Warm both processes so startup/model load does not pollute samples.
        warm_position = BenchmarkPosition(name="warmup", moves=[])
        gtp.analyze(warm_position, visits=1, time_cap_ms=args.time_cap_ms, clear_cache=args.gtp_clear_cache)
        json_engine.analyze("warmup-json", warm_position, visits=1, time_cap_ms=args.time_cap_ms)

        with jsonl_path.open("w", encoding="utf-8") as handle:
            run_header = {
                "type": "run",
                "samples": args.samples,
                "visits": visits,
                "timeCapMs": args.time_cap_ms,
                "deterministic": args.deterministic,
                "gtpSearchThreads": args.gtp_search_threads,
                "jsonAnalysisThreads": args.json_analysis_threads,
                "jsonSearchThreads": args.json_search_threads,
                "gtpClearCache": args.gtp_clear_cache,
                "baseMoves": base.moves,
            }
            handle.write(json.dumps(run_header, ensure_ascii=False) + "\n")
            for sample in range(1, args.samples + 1):
                position = benchmark_variant_position(base, sample - 1)
                for visit_target in visits:
                    for mode in ("gtp_stateful_fast_clearcache", "json_position_analysis"):
                        if mode == "gtp_stateful_fast_clearcache":
                            data, elapsed_ms = gtp.analyze(
                                position,
                                visits=visit_target,
                                time_cap_ms=args.time_cap_ms,
                                clear_cache=args.gtp_clear_cache,
                            )
                        else:
                            data, elapsed_ms = json_engine.analyze(
                                query_id=f"json-v{visit_target}-s{sample}",
                                position=position,
                                visits=visit_target,
                                time_cap_ms=args.time_cap_ms,
                            )
                        root_visits = data["rootVisits"]
                        row = {
                            "type": "sample",
                            "mode": mode,
                            "position": position.name,
                            "sample": sample,
                            "visitsRequest": visit_target,
                            "timeCapMs": args.time_cap_ms,
                            "elapsedMs": round(elapsed_ms, 3),
                            "rootVisits": root_visits,
                            "fill": "OK" if root_visits is not None and int(root_visits) >= visit_target else "SHORT",
                            "moveInfoCount": data["moveInfoCount"],
                            "nextPlayer": position.next_player,
                            "moves": len(position.moves),
                            "positionMoves": position.moves,
                        }
                        rows.append(row)
                        handle.write(json.dumps(row, ensure_ascii=False) + "\n")
                        handle.flush()
                        print(
                            f"{mode} B{visit_target} sample {sample}/{args.samples}: "
                            f"{row['elapsedMs']}ms root={root_visits} fill={row['fill']}",
                        )
    finally:
        gtp.close()
        json_engine.close()

    summary = {
        "samples": args.samples,
        "visits": visits,
        "timeCapMs": args.time_cap_ms,
        "deterministic": args.deterministic,
        "gtpSearchThreads": args.gtp_search_threads,
        "jsonAnalysisThreads": args.json_analysis_threads,
        "jsonSearchThreads": args.json_search_threads,
        "gtpClearCache": args.gtp_clear_cache,
        "baseMoves": base.moves,
        "results": summarize(rows),
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_markdown(summary, markdown_path)
    print(f"wrote {jsonl_path}")
    print(f"wrote {summary_path}")
    print(f"wrote {markdown_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
