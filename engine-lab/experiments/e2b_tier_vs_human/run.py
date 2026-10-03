#!/usr/bin/env python3
"""E2b — 지금 캐릭터(16방문 + 버킷) 대 사람 모델 프로필(1회 평가 + R2) 실제 대국 승률(백로그 #214 → #215).

E2는 「한 수 손해」 분포를 비교했다. 그것만으로는 세기를 모른다 — 이 실험은 **끝까지 둬서 누가 이기나**를 잰다.
짝마다 `--games`판, 흑백을 번갈아 쥔다. 판정은 센 탐색 점수(기본 400방문). 기본은 13줄(앱 기본 판).

⚠️ 거친 측정이다 — 20판이면 승률의 95% 구간이 ±20%p쯤이다. 촘촘한 보정(리서치 §6, 수천 판)은 #215에서 이 러너로 늘려 돌린다.

    python3 engine-lab/experiments/e2b_tier_vs_human/run.py --label mac-v1 --tiers 1,3,5 --profiles rank_20k,rank_10k,rank_5k,rank_1d
"""
from __future__ import annotations

import argparse
import math
import random
import sys
import time
from collections import Counter
from pathlib import Path
from statistics import mean

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import human, paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.gtp import local_gtp  # noqa: E402
from lab.match import HumanPlayer, TierPlayer, play_game  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e2b_tier_vs_human"


def wilson(wins: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n == 0:
        return (0.0, 1.0)
    p = wins / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--size", type=int, default=ap.DEFAULT_BOARD_SIZE)
    parser.add_argument("--tiers", default="1,3,5")
    parser.add_argument("--profiles", default="rank_20k,rank_10k,rank_5k,rank_1d")
    parser.add_argument("--recipe", default="R2-tail")
    parser.add_argument("--games", type=int, default=20, help="짝마다 판 수(흑백 번갈아)")
    parser.add_argument("--judge-visits", type=int, default=400)
    parser.add_argument("--seed", type=int, default=214)
    args = parser.parse_args()

    tiers = [int(t) for t in args.tiers.split(",") if t]
    profiles = [p for p in args.profiles.split(",") if p]
    recipe = human.RECIPES[args.recipe]
    rng = random.Random(args.seed)

    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    gtp = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.gtp_config(),
        human_model=paths.human_model(),
        overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS),
        label="match-gtp",
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

    results: list[dict] = []
    started = time.time()
    try:
        for level in tiers:
            for profile in profiles:
                tier_player = TierPlayer(level)
                human_player = HumanPlayer(profile, recipe)
                for game in range(args.games):
                    tier_is_black = game % 2 == 0
                    black, white = (tier_player, human_player) if tier_is_black else (human_player, tier_player)
                    result = play_game(gtp, judge, black, white, size=args.size, rng=rng, judge_visits=args.judge_visits)
                    tier_color = "B" if tier_is_black else "W"
                    row = {
                        "tier": tier_player.name,
                        "level": level,
                        "profile": profile,
                        "recipe": recipe.name,
                        "game": game + 1,
                        "size": args.size,
                        "tierColor": tier_color,
                        "blackLead": result.black_lead,
                        "winner": result.winner,
                        "tierWon": result.winner == tier_color,
                        "tierLead": None if result.black_lead is None else (result.black_lead if tier_is_black else -result.black_lead),
                        "moves": len(result.moves),
                        "endedBy": result.ended_by,
                        "record": [[c, m] for c, m in result.moves],
                    }
                    run.sample(row)
                    results.append(row)
                done = [r for r in results if r["level"] == level and r["profile"] == profile]
                wins = sum(1 for r in done if r["tierWon"])
                print(f"{tier_player.name} vs {profile}: {wins}/{len(done)} — {time.time() - started:.0f}s", file=sys.stderr, flush=True)
    finally:
        gtp.close()
        judge.close()

    summary, markdown = summarize(results, tiers, profiles, args)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def summarize(results: list[dict], tiers: list[int], profiles: list[str], args) -> tuple[dict, str]:
    pairs = {}
    for level in tiers:
        for profile in profiles:
            rows = [r for r in results if r["level"] == level and r["profile"] == profile]
            if not rows:
                continue
            wins = sum(1 for r in rows if r["tierWon"])
            low, high = wilson(wins, len(rows))
            pairs[f"{level}|{profile}"] = {
                "tier": rows[0]["tier"],
                "profile": profile,
                "games": len(rows),
                "tierWins": wins,
                "tierWinRate": wins / len(rows),
                "ci95": [low, high],
                "meanTierLead": mean(r["tierLead"] for r in rows if r["tierLead"] is not None),
                "meanMoves": mean(r["moves"] for r in rows),
                "endedBy": dict(Counter(r["endedBy"] for r in rows)),
            }
    summary = {"pairs": pairs, "size": args.size, "recipe": args.recipe}
    lines = [
        f"# E2b 결과 — 캐릭터(16방문 + 버킷) 대 사람 모델({args.recipe}) 실제 대국, {args.size}줄",
        "",
        f"- 짝마다 {args.games}판, 흑백 번갈아. 덤 {ap.DEFAULT_KOMI}, {ap.DEFAULT_RULES}. 판정: 주 모델 {args.judge_visits}방문 점수.",
        "- 캐릭터는 트리를 이어 쓴다(앱의 사람 대 AI). 사람 쪽 통과는 주 모델 원시 정책 1위가 통과일 때만.",
        f"- ⚠️ {args.games}판의 95% 구간은 ±{int(round(98 / (args.games ** 0.5)))}%p쯤(승률 50% 근처) — 방향을 보는 측정이다.",
        "",
        "## 캐릭터 승률(행 = 캐릭터, 열 = 사람 모델 프로필)",
        "",
        "| 캐릭터 | " + " | ".join(profiles) + " |",
        "| --- | " + " | ".join("---:" for _ in profiles) + " |",
    ]
    for level in tiers:
        cells = []
        name = None
        for profile in profiles:
            p = pairs.get(f"{level}|{profile}")
            if not p:
                cells.append("–")
                continue
            name = p["tier"]
            cells.append(f"{p['tierWinRate'] * 100:.0f}% ({p['tierWins']}/{p['games']}, {p['ci95'][0] * 100:.0f}~{p['ci95'][1] * 100:.0f}%)")
        lines.append(f"| {name} | " + " | ".join(cells) + " |")
    lines += ["", "## 짝별 상세", "", "| 짝 | 판 | 캐릭터 승 | 평균 점수차(캐릭터 기준) | 평균 수 | 끝난 방식 |", "| --- | ---: | ---: | ---: | ---: | --- |"]
    for p in pairs.values():
        lines.append(
            f"| {p['tier']} vs {p['profile']} | {p['games']} | {p['tierWins']} | {p['meanTierLead']:+.1f} | {p['meanMoves']:.0f} | {p['endedBy']} |"
        )
    lines.append("")
    return summary, "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
