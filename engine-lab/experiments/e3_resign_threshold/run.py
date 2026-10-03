#!/usr/bin/env python3
"""E3 — 「불리한 채 N수」 기권 조건을 걸면 얼마나 자주 **잘못 던지나**(백로그 #214 → #213).

E2b 대국 기보를 다시 두며 매 수 뒤 앱과 같은 신호 — `kata-raw-nn 0`(탐색 없는 신경망 1회)의 점수·승률 — 를 받는다.
진영마다 「자기 수를 둔 뒤 자기 기준 점수 ≤ −X집(또는 승률 ≤ Y)이 N수 연속」이면 그 자리에서 기권했다고 치고,
그 진영이 **실제로는 이긴 판**이면 잘못 던진 것이다. 조건 격자(X × N)마다:
- 잘못 던진 비율(발동한 판 중 실제로 이긴 판)
- 덮는 비율(실제로 진 판 중 발동한 판)
- 발동 시점(끝까지 남은 수의 중앙값)

⚠️ 앱은 엔진이 실패하면 척도가 다른 로컬 계가(`LocalAreaEstimate`)로 대신한다 — 이 실험은 엔진 값(`EngineEstimate`)만 본다(#200과 같은 원칙).

    python3 engine-lab/experiments/e3_resign_threshold/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from statistics import median

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.board import BLACK, Position  # noqa: E402
from lab.gtp import local_gtp  # noqa: E402
from lab.runs import RunDir, read_jsonl  # noqa: E402

EXPERIMENT = "e3_resign_threshold"
LEAD_THRESHOLDS = (5.0, 10.0, 15.0, 20.0, 30.0)
WINRATE_THRESHOLDS = (0.10, 0.05)
STREAKS = (5, 10, 15, 20)


def latest_e2b_samples() -> Path:
    runs = sorted((paths.experiments_dir() / "e2b_tier_vs_human" / "runs").glob("*/samples.jsonl"))
    if not runs:
        raise SystemExit("E2b 결과가 없다 — 먼저 e2b_tier_vs_human/run.py를 돌릴 것")
    return runs[-1]


def trigger_index(values: list[float], bad, streak: int) -> int | None:
    """values = 그 진영이 둔 뒤의 평가(자기 기준). bad(v)가 streak번 연속 참이 된 **자기 수 순번**."""
    run = 0
    for i, v in enumerate(values):
        run = run + 1 if bad(v) else 0
        if run >= streak:
            return i
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--games", default="", help="E2b samples.jsonl(비우면 가장 최근)")
    args = parser.parse_args()

    source = Path(args.games) if args.games else latest_e2b_samples()
    games = read_jsonl(source)
    run = RunDir(EXPERIMENT, args.label, {**vars(args), "source": str(source.relative_to(paths.REPO_ROOT))})
    print(f"→ {run.path} ({len(games)} games)", file=sys.stderr)
    gtp = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.gtp_config(),
        overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS),
        label="e3-gtp",
    )
    run.set_engine_versions({"gtp": gtp.version})

    traces: list[dict] = []
    started = time.time()
    try:
        for index, game in enumerate(games, start=1):
            size = int(game["size"])
            position = Position(id=f"g{index}", size=size)
            gtp.setup(position)
            leads: list[float | None] = []
            wins: list[float | None] = []
            for color, move in game["record"]:
                gtp.play(color, move)
                raw = gtp.raw_nn(size, human=False)
                leads.append(raw.fields.get("whiteLead"))
                wins.append(raw.fields.get("whiteWin"))
            final_black_lead = game["blackLead"]
            trace = {
                "game": index,
                "tier": game["tier"],
                "profile": game["profile"],
                "tierColor": game["tierColor"],
                "finalBlackLead": final_black_lead,
                "moves": len(game["record"]),
                "colors": [c for c, _ in game["record"]],
                "whiteLead": leads,
                "whiteWin": wins,
            }
            run.sample(trace)
            traces.append(trace)
            if index % 20 == 0:
                print(f"[{index}/{len(games)}] {time.time() - started:.0f}s", file=sys.stderr, flush=True)
    finally:
        gtp.close()

    summary, markdown = summarize(traces, source)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def side_series(trace: dict, color: str) -> tuple[list[float], list[float], list[int]]:
    """그 진영이 **자기 수를 둔 직후**의 평가(자기 기준 점수·승률)와 그 수의 전체 수순 번호."""
    leads, wins, numbers = [], [], []
    for i, (c, lead, win) in enumerate(zip(trace["colors"], trace["whiteLead"], trace["whiteWin"])):
        if c != color or lead is None or win is None:
            continue
        leads.append(lead if color == "W" else -lead)
        wins.append(win if color == "W" else 1.0 - win)
        numbers.append(i + 1)
    return leads, wins, numbers


def summarize(traces: list[dict], source: Path) -> tuple[dict, str]:
    conditions = [("lead", x) for x in LEAD_THRESHOLDS] + [("win", y) for y in WINRATE_THRESHOLDS]
    table = []
    for kind, threshold in conditions:
        for streak in STREAKS:
            triggered = false_resign = lost_games = covered = 0
            remaining: list[int] = []
            for trace in traces:
                final = trace["finalBlackLead"]
                if final is None:
                    continue
                for color in ("B", "W"):
                    won = (final > 0) == (color == BLACK)
                    leads, wins, numbers = side_series(trace, color)
                    values = leads if kind == "lead" else wins
                    bad = (lambda v, t=threshold: v <= -t) if kind == "lead" else (lambda v, t=threshold: v <= t)
                    hit = trigger_index(values, bad, streak)
                    if not won:
                        lost_games += 1
                    if hit is None:
                        continue
                    triggered += 1
                    remaining.append(trace["moves"] - numbers[hit])
                    if won:
                        false_resign += 1
                    else:
                        covered += 1
            table.append(
                {
                    "kind": kind,
                    "threshold": threshold,
                    "streak": streak,
                    "triggered": triggered,
                    "falseResignRate": false_resign / triggered if triggered else None,
                    "falseResigns": false_resign,
                    "coverage": covered / lost_games if lost_games else None,
                    "medianMovesRemaining": median(remaining) if remaining else None,
                }
            )
    summary = {"source": str(source.relative_to(paths.REPO_ROOT)), "games": len(traces), "conditions": table}
    lines = [
        "# E3 결과 — 「불리한 채 N수」 기권 조건의 잘못 던지는 비율",
        "",
        f"- 기보: `{summary['source']}` {len(traces)}판(양 진영 모두 「기권할 쪽」으로 본다 → 진영 표본 {2 * len(traces)}).",
        "- 신호: 매 수 뒤 `kata-raw-nn 0`(앱이 쌓는 `ScoreSnapshot(EngineEstimate)`와 같은 값), 그 진영이 **자기 수를 둔 직후**의 자기 기준 값.",
        "- 잘못 던짐 = 발동했는데 실제로는 이긴 판. 덮음 = 실제로 진 판 중 발동한 비율. 남은 수 = 발동 시점부터 끝까지.",
        "",
        "| 조건 | 연속 | 발동 | 잘못 던짐 | 덮음(진 판 중) | 남은 수(중앙값) |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in table:
        cond = f"점수 ≤ −{row['threshold']:g}집" if row["kind"] == "lead" else f"승률 ≤ {row['threshold'] * 100:g}%"
        fr = "–" if row["falseResignRate"] is None else f"{row['falseResignRate'] * 100:.1f}% ({row['falseResigns']})"
        cov = "–" if row["coverage"] is None else f"{row['coverage'] * 100:.0f}%"
        rem = "–" if row["medianMovesRemaining"] is None else f"{row['medianMovesRemaining']:.0f}"
        lines.append(f"| {cond} | {row['streak']}수 | {row['triggered']} | {fr} | {cov} | {rem} |")
    lines.append("")
    return summary, "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
