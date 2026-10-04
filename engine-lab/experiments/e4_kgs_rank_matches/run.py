#!/usr/bin/env python3
"""E4 — KGS 급수 설정(사람 모델 프로필)끼리 9·13·19줄에서 둬 본다(백로그 #216).

사용자 방향(2026-10-04): **공식 KGS 값을 믿는다.** 다만 프로필은 19줄 기준이라 13·9줄에서는 급수 차이가 작게 나타날 수 있다 —
그것을 보려고, 급수 사다리(기본 3급 간격 20급 → 8단)의 **이웃한 두 급수끼리** 판 크기마다 `--games`판(기본 10)을 둔다.

- 한 판 끝날 때마다 `samples.jsonl`에 바로 쓴다. **같은 `--name`으로 다시 돌리면 끝난 판은 건너뛰고 이어서** 둔다(끊겨도 된다).
- 짝 하나가 끝날 때마다 `summary.md`를 다시 쓴다 — 도는 중에도 그때까지의 결과가 보인다.
- 선수: 사람 모델 1회 평가 + R2(`lab/match.py` HumanPlayer). 판정: 주 모델 400방문 점수(흑 기준). 흑백은 번갈아.

    python3 engine-lab/experiments/e4_kgs_rank_matches/run.py --name v1          # 처음·이어하기 같은 명령
    python3 engine-lab/experiments/e4_kgs_rank_matches/run.py --name v1 --games 20   # 판을 늘려 이어서
"""
from __future__ import annotations

import argparse
import math
import random
import sys
import time
from collections import defaultdict
from pathlib import Path
from statistics import mean

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import human, paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.gtp import local_gtp  # noqa: E402
from lab.match import HumanPlayer, play_game  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e4_kgs_rank_matches"
DEFAULT_LADDER = "rank_20k,rank_17k,rank_14k,rank_11k,rank_8k,rank_5k,rank_2k,rank_2d,rank_5d,rank_8d"


def rank_value(profile: str) -> int:
    """rank_20k → -20, rank_1k → -1, rank_1d → 1 — 급수 간격 계산용(1급과 1단 사이는 1칸)."""
    tag = profile.split("_")[-1]
    n = int(tag[:-1])
    return -n if tag.endswith("k") else n


def rank_gap(weak: str, strong: str) -> int:
    a, b = rank_value(weak), rank_value(strong)
    gap = b - a
    if a < 0 < b:
        gap -= 1  # 1급 → 1단은 한 칸
    return gap


def wilson(wins: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n == 0:
        return (0.0, 1.0)
    p = wins / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def game_key(size: int, weak: str, strong: str, index: int) -> str:
    return f"{size}|{weak}|{strong}|{index}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--name", required=True, help="결과 폴더 이름(runs/<name>/) — 같은 이름이면 이어서 둔다")
    parser.add_argument("--sizes", default="9,13,19")
    parser.add_argument("--ladder", default=DEFAULT_LADDER, help="약한 쪽부터, 이웃한 둘씩 짝이 된다")
    parser.add_argument("--games", type=int, default=10, help="짝·판 크기마다 판 수")
    parser.add_argument("--recipe", default="R2-tail")
    parser.add_argument("--judge-visits", type=int, default=400)
    parser.add_argument("--seed", type=int, default=216)
    args = parser.parse_args()

    sizes = [int(s) for s in args.sizes.split(",") if s]
    ladder = [p for p in args.ladder.split(",") if p]
    pairs = list(zip(ladder, ladder[1:]))
    recipe = human.RECIPES[args.recipe]

    run = RunDir(EXPERIMENT, args.name, vars(args), resume=True)
    done_rows = [r for r in run.completed_samples() if "key" in r]
    done = {r["key"] for r in done_rows}
    total = len(sizes) * len(pairs) * args.games
    print(f"→ {run.path}  ({len(done)}/{total} 판 끝남 — 나머지를 둔다)", file=sys.stderr, flush=True)
    if len(done) >= total:
        summary, markdown = summarize(done_rows, sizes, pairs, args)
        run.finish(summary, markdown)
        print(markdown)
        return 0

    gtp = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.gtp_config(),
        human_model=paths.human_model(),
        overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS),
        label="e4-gtp",
    )
    judge = local_analysis(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.analysis_config(),
        overrides={"numAnalysisThreads": "1", "numSearchThreads": "4", "logToStderr": "false", "logAllRequests": "false",
                   "logAllResponses": "false", "logSearchInfo": "false"},
        label="judge",
    )
    run.set_engine_versions({"gtp": gtp.version, "judge": judge.version})

    rows = list(done_rows)
    started = time.time()
    played = 0
    try:
        for size in sizes:
            for weak, strong in pairs:
                for index in range(1, args.games + 1):
                    key = game_key(size, weak, strong, index)
                    if key in done:
                        continue
                    # 판마다 씨앗을 고정 — 끊겼다 이어도 같은 판은 같은 무작위로 시작한다
                    rng = random.Random(f"{args.seed}|{key}")
                    strong_black = index % 2 == 1
                    s_player, w_player = HumanPlayer(strong, recipe), HumanPlayer(weak, recipe)
                    black, white = (s_player, w_player) if strong_black else (w_player, s_player)
                    result = play_game(gtp, judge, black, white, size=size, rng=rng, judge_visits=args.judge_visits)
                    strong_color = "B" if strong_black else "W"
                    row = {
                        "key": key,
                        "size": size,
                        "weak": weak,
                        "strong": strong,
                        "gap": rank_gap(weak, strong),
                        "game": index,
                        "strongColor": strong_color,
                        "blackLead": result.black_lead,
                        "winner": result.winner,
                        "strongWon": result.winner == strong_color,
                        "strongLead": None if result.black_lead is None else (result.black_lead if strong_black else -result.black_lead),
                        "moves": len(result.moves),
                        "endedBy": result.ended_by,
                        "record": [[c, m] for c, m in result.moves],
                    }
                    run.sample(row)
                    rows.append(row)
                    done.add(key)
                    played += 1
                summary, markdown = summarize(rows, sizes, pairs, args)
                run.write_summary(summary, markdown)
                print(
                    f"{size}줄 {weak} vs {strong}: {len(done)}/{total} — 이번 실행 {played}판 {time.time() - started:.0f}s",
                    file=sys.stderr,
                    flush=True,
                )
    finally:
        gtp.close()
        judge.close()

    summary, markdown = summarize(rows, sizes, pairs, args)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def summarize(rows: list[dict], sizes: list[int], pairs: list[tuple[str, str]], args) -> tuple[dict, str]:
    cells: dict[str, dict] = {}
    by_size: dict[int, list[dict]] = defaultdict(list)
    for size in sizes:
        for weak, strong in pairs:
            picked = [r for r in rows if r["size"] == size and r["weak"] == weak and r["strong"] == strong]
            if not picked:
                continue
            wins = sum(1 for r in picked if r["strongWon"])
            low, high = wilson(wins, len(picked))
            leads = [r["strongLead"] for r in picked if r["strongLead"] is not None]
            cells[f"{size}|{weak}|{strong}"] = {
                "size": size,
                "weak": weak,
                "strong": strong,
                "gap": rank_gap(weak, strong),
                "games": len(picked),
                "strongWins": wins,
                "strongWinRate": wins / len(picked),
                "ci95": [low, high],
                "meanStrongLead": mean(leads) if leads else None,
                "meanMoves": mean(r["moves"] for r in picked),
            }
            by_size[size].extend(picked)
    totals = {}
    for size, picked in by_size.items():
        wins = sum(1 for r in picked if r["strongWon"])
        totals[str(size)] = {"games": len(picked), "strongWins": wins, "strongWinRate": wins / len(picked) if picked else None}
    summary = {"cells": cells, "bySize": totals, "ladder": [p for pair in pairs for p in pair][:: 2] + [pairs[-1][1]] if pairs else []}

    def short(profile: str) -> str:
        return profile.replace("rank_", "")

    lines = [
        "# E4 결과 — KGS 급수 설정(사람 모델) 이웃끼리, 판 크기별",
        "",
        f"- 사다리: {' → '.join(short(p) for p in summary['ladder'])} (이웃한 둘이 한 짝). 짝·판 크기마다 목표 {args.games}판, 흑백 번갈아.",
        f"- 선수: 사람 모델 1회 평가 + {args.recipe}. 판정: 주 모델 {args.judge_visits}방문 점수. 덤 {ap.DEFAULT_KOMI}, {ap.DEFAULT_RULES}.",
        "- 칸 = **센 쪽(높은 급수)의 승률** (승/판, 평균 점수차). 이름값대로면 50%보다 크다 — 3급 차는 대략 70%대가 기대값(리서치 §5.3.2, 급당 약 64 Elo).",
        "",
        "## 짝별 승률",
        "",
        "| 짝(약 → 강) | 급 차 | " + " | ".join(f"{s}줄" for s in sizes) + " |",
        "| --- | ---: | " + " | ".join("---:" for _ in sizes) + " |",
    ]
    for weak, strong in pairs:
        row_cells = []
        for size in sizes:
            c = cells.get(f"{size}|{weak}|{strong}")
            if not c:
                row_cells.append("–")
                continue
            lead = "" if c["meanStrongLead"] is None else f", {c['meanStrongLead']:+.0f}집"
            row_cells.append(f"{c['strongWinRate'] * 100:.0f}% ({c['strongWins']}/{c['games']}{lead})")
        lines.append(f"| {short(weak)} → {short(strong)} | {rank_gap(weak, strong)} | " + " | ".join(row_cells) + " |")
    lines += ["", "## 판 크기별 합계(모든 짝)", "", "| 판 | 판 수 | 센 쪽 승률 |", "| --- | ---: | ---: |"]
    for size in sizes:
        t = totals.get(str(size))
        if t and t["games"]:
            lines.append(f"| {size}줄 | {t['games']} | {t['strongWinRate'] * 100:.0f}% |")
    lines.append("")
    return summary, "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
