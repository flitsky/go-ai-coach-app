#!/usr/bin/env python3
"""E1 — 적게·짧게 탐색하면 후보가 몇 개 나오고, 캐릭터 5단계는 실제로 무엇을 두는가(백로그 #214).

맥 모드(기본): 방문 수(4·8·16·32·64)로 잰다. 맥 Metal은 폰보다 수십 배 빨라서 **시간 상한은 맥에서 의미가 없다** —
같은 방문이면 같은 탐색이므로 방문으로 말한다.
폰 모드(`--adb-serial`): 앱이 깔린 폰의 엔진으로 **시간 상한**(0.5·1·2·3·5초, 최대 16방문)을 걸어 실제 방문·후보 수를 잰다.
두 모드 모두 후보의 손해는 맥의 기준 평가(주 모델, 같은 방문으로)로 잰다.

    python3 engine-lab/experiments/e1_low_visit_candidates/run.py --label mac-v1
    python3 engine-lab/experiments/e1_low_visit_candidates/run.py --label phone-s23 --adb-serial R3CW104KDNT --sizes 13
"""
from __future__ import annotations

import argparse
import sys
import time
from collections import defaultdict
from pathlib import Path
from statistics import mean

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.board import load_positions  # noqa: E402
from lab.evaluation import aggregate_tiers, count_histogram, fmt_num, fmt_pct, tier_outcomes  # noqa: E402
from lab.gtp import AdbTarget, local_gtp, phone_gtp  # noqa: E402
from lab.reference import ReferenceEvaluator  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e1_low_visit_candidates"


def parse_int_list(text: str) -> list[int]:
    return [int(x) for x in text.split(",") if x.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--positions", default=str(paths.positions_dir() / "selfplay-human-v1.json"))
    parser.add_argument("--sizes", default="9,13,19")
    parser.add_argument("--max-positions-per-size", type=int, default=0, help="0 = 전부")
    parser.add_argument("--visits", default="4,8,16,32,64", help="맥 모드: 잴 방문 수")
    parser.add_argument("--repeats", type=int, default=3, help="같은 조건을 몇 번(대칭 무작위 때문에 결과가 흔들린다)")
    parser.add_argument("--adb-serial", default=None, help="폰 모드 — 앱이 깔린 폰의 adb 시리얼")
    parser.add_argument("--adb-package", default="com.zenit9hub.ai.baduk")
    parser.add_argument("--time-caps-ms", default="500,1000,2000,3000,5000", help="폰 모드: 시간 상한(최대 16방문)")
    parser.add_argument("--ref-root-visits", type=int, default=800)
    parser.add_argument("--ref-move-visits", type=int, default=300)
    parser.add_argument("--ref-threads", type=int, default=8)
    args = parser.parse_args()

    sizes = set(parse_int_list(args.sizes))
    positions = [p for p in load_positions(Path(args.positions)) if p.size in sizes]
    if args.max_positions_per_size:
        by_size: dict[int, list] = defaultdict(list)
        for p in positions:
            if len(by_size[p.size]) < args.max_positions_per_size:
                by_size[p.size].append(p)
        positions = [p for size in sorted(by_size) for p in by_size[size]]
    phone = args.adb_serial is not None
    conditions: list[tuple[int, int | None]]
    if phone:
        conditions = [(ap.FAST_BEGINNER_VISITS, t) for t in parse_int_list(args.time_caps_ms)]
    else:
        conditions = [(v, None) for v in parse_int_list(args.visits)]

    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)

    if phone:
        adb = AdbTarget(serial=args.adb_serial, package=args.adb_package)
        gtp = phone_gtp(adb, overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS))
    else:
        gtp = local_gtp(
            katago=paths.katago_binary(),
            model=paths.main_model(),
            config=paths.gtp_config(),
            overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS),
        )
    ref_engine = local_analysis(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.analysis_config(),
        overrides={
            "numAnalysisThreads": str(args.ref_threads),
            "numSearchThreads": "2",
            "logToStderr": "false",
            "logAllRequests": "false",
            "logAllResponses": "false",
            "logSearchInfo": "false",
        },
        label="reference",
    )
    evaluator = ReferenceEvaluator(
        ref_engine,
        paths.LAB_ROOT / "cache" / "reference-b18-r{}-m{}.jsonl".format(args.ref_root_visits, args.ref_move_visits),
        root_visits=args.ref_root_visits,
        move_visits=args.ref_move_visits,
    )
    run.set_engine_versions({"gtp": gtp.version, "reference": ref_engine.version})

    samples: list[dict] = []
    started = time.time()
    try:
        for index, position in enumerate(positions, start=1):
            gtp.setup(position)
            results = []
            for visits, time_ms in conditions:
                for repeat in range(args.repeats):
                    gtp.clear_cache()  # 매번 새 탐색(앱의 AI 대 AI와 같다 — 사람 대 AI는 직전 트리를 이어 쓴다)
                    result = gtp.search_analyze(
                        position.next_player,
                        visits=visits,
                        time_ms=time_ms,
                        max_candidates=ap.FAST_BEGINNER_CANDIDATE_COUNT,
                    )
                    results.append((visits, time_ms, repeat, result))
            moves = sorted({c.move for _, _, _, r in results for c in r.scored_plays})
            ref_best, losses = evaluator.point_losses(position, moves)
            for visits, time_ms, repeat, result in results:
                plays = [c.move for c in result.scored_plays]
                top_is_pass = bool(result.scored) and result.scored[0].move.lower() == "pass"
                row = {
                    "positionId": position.id,
                    "size": position.size,
                    "phase": position.phase,
                    "moveNumber": position.move_number,
                    "player": position.next_player,
                    "visits": visits,
                    "timeCapMs": time_ms,
                    "repeat": repeat,
                    "rootVisits": result.root_visits,
                    "elapsedMs": round(result.elapsed_ms, 1),
                    "nScoredPlays": len(plays),
                    "topIsPass": top_is_pass,
                    "refBest": ref_best,
                    "appTopIsRefBest": bool(plays) and plays[0] == ref_best,
                    "candidates": [
                        {**c.to_json(), "refLoss": losses.get(c.move)} for c in result.candidates
                    ],
                    "tiers": tier_outcomes(plays, losses, ref_best, top_is_pass),
                }
                run.sample(row)
                samples.append(row)
            elapsed = time.time() - started
            print(f"[{index}/{len(positions)}] {position.id} — {len(moves)} moves rated, {elapsed:.0f}s", file=sys.stderr, flush=True)
    finally:
        gtp.close()
        ref_engine.close()

    summary, markdown = summarize(samples, phone=phone, args=args)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def condition_key(row: dict, phone: bool) -> str:
    return f"{row['timeCapMs']}ms" if phone else f"{row['visits']}v"


def summarize(samples: list[dict], *, phone: bool, args) -> tuple[dict, str]:
    groups: dict[tuple[int, str], list[dict]] = defaultdict(list)
    for row in samples:
        groups[(row["size"], condition_key(row, phone))].append(row)
        groups[(0, condition_key(row, phone))].append(row)  # 0 = 모든 판 크기

    summary: dict = {"phone": phone, "groups": {}}
    for (size, cond), rows in sorted(groups.items(), key=lambda kv: (kv[0][0], int(kv[0][1].rstrip("msv")))):
        counts = [r["nScoredPlays"] for r in rows]
        summary["groups"][f"{size or 'all'}|{cond}"] = {
            "size": size or "all",
            "condition": cond,
            "samples": len(rows),
            "meanScoredPlays": mean(counts),
            "histogram": count_histogram(counts),
            "pAtMostOne": sum(1 for c in counts if c <= 1) / len(counts),
            "pAtMostTwo": sum(1 for c in counts if c <= 2) / len(counts),
            "meanRootVisits": mean(r["rootVisits"] for r in rows if r["rootVisits"] is not None) if any(r["rootVisits"] for r in rows) else None,
            "meanElapsedMs": mean(r["elapsedMs"] for r in rows),
            "p90ElapsedMs": sorted(r["elapsedMs"] for r in rows)[int(0.9 * (len(rows) - 1))],
            "appTopIsRefBest": mean(1.0 if r["appTopIsRefBest"] else 0.0 for r in rows),
            "tiers": aggregate_tiers([r["tiers"] for r in rows]),
        }
    # 단계별·국면별(16방문 또는 폰 1초)
    focus = "1000ms" if phone else f"{ap.FAST_BEGINNER_VISITS}v"
    by_phase: dict[str, list[dict]] = defaultdict(list)
    for row in samples:
        if condition_key(row, phone) == focus:
            by_phase[row["phase"]].append(row)
    summary["byPhaseAtFocus"] = {
        phase: {
            "samples": len(rows),
            "meanScoredPlays": mean(r["nScoredPlays"] for r in rows),
            "tiers": aggregate_tiers([r["tiers"] for r in rows]),
        }
        for phase, rows in by_phase.items()
    }
    return summary, render_markdown(summary, phone=phone, focus=focus, args=args)


def render_markdown(summary: dict, *, phone: bool, focus: str, args) -> str:
    lines = [
        f"# E1 결과 — {'폰 시간 상한' if phone else '맥 방문 수'}별 후보 수와 캐릭터의 실제 착수",
        "",
        f"- 국면: `{Path(args.positions).name}`, 판 크기 {args.sizes}, 조건마다 {args.repeats}회(매번 새 탐색)",
        f"- 탐색: 앱과 같은 GTP 빠른 경로(`kata-search_analyze`, 1스레드, 후보 최대 {ap.FAST_BEGINNER_CANDIDATE_COUNT}개)",
        f"- 손해: 맥 기준 평가(주 모델, 국면 {args.ref_root_visits}방문으로 최선수 → 대상 수마다 그 수만 허용해 {args.ref_move_visits}방문)",
        "",
        "## 1. 후보 수 — 점수 붙은 착수 후보(앱이 버킷을 나누는 대상)",
        "",
        "| 판 | 조건 | 표본 | 평균 후보 | 1개 이하 | 2개 이하 | 평균 루트 방문 | 평균 시간 | 엔진 1위 = 기준 최선 |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for g in summary["groups"].values():
        lines.append(
            f"| {g['size']} | {g['condition']} | {g['samples']} | {fmt_num(g['meanScoredPlays'])} | {fmt_pct(g['pAtMostOne'])} | "
            f"{fmt_pct(g['pAtMostTwo'])} | {fmt_num(g['meanRootVisits'])} | {fmt_num(g['meanElapsedMs'], 0)}ms | {fmt_pct(g['appTopIsRefBest'])} |"
        )
    lines += [
        "",
        "> 후보가 2개 이하면 「최하」 버킷이 없어 초보·하수의 최하수가 2위로, 1개면 **최선**으로 내려간다(`FAST_BEGINNER_TIER_DESIGN.md:43-53`).",
        "",
        "## 2. 캐릭터 5단계가 실제로 두는 수 — 모든 판 크기",
        "",
        "| 조건 | 단계 | 평균 손해(집) | 엔진 1위를 둠 | 기준 최선을 둠 | 3집 이상 손해 | 10집 이상 손해 |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for g in summary["groups"].values():
        if g["size"] != "all":
            continue
        for name, t in g["tiers"].items():
            lines.append(
                f"| {g['condition']} | {name} | {fmt_num(t['expLoss'])} | {fmt_pct(t['pRank0'])} | {fmt_pct(t['pRefBest'])} | "
                f"{fmt_pct(t['pLoss3'])} | {fmt_pct(t['pLoss10'])} |"
            )
    lines += ["", f"## 3. 국면별({focus})", "", "| 국면 | 표본 | 평균 후보 | 단계 | 평균 손해 | 엔진 1위를 둠 |", "| --- | ---: | ---: | --- | ---: | ---: |"]
    for phase in ("opening", "middle", "endgame"):
        block = summary["byPhaseAtFocus"].get(phase)
        if not block:
            continue
        for name, t in block["tiers"].items():
            lines.append(
                f"| {phase} | {block['samples']} | {fmt_num(block['meanScoredPlays'])} | {name} | {fmt_num(t['expLoss'])} | {fmt_pct(t['pRank0'])} |"
            )
    lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
