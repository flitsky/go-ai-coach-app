#!/usr/bin/env python3
"""E6 보조 — 매 수 형세를 사람 모델(가장 센 프로필)로 적으면 「5집 이상 출렁인 수」가 얼마나 달라지나.

앱의 변곡점·복기 추천은 **연속한 두 평가의 차가 5집 이상**인 수를 고른다(#200·#207). 그 평가를 주 모델 대신
사람 모델 하나로 적었을 때, 같은 판에서 주 모델이 고른 수를 얼마나 놓치고 엉뚱한 수를 얼마나 더 고르는지 센다.
판은 E4가 남긴 기보(`e4_kgs_rank_matches/runs/v1`)에서 고른다.

    python3 engine-lab/experiments/e6_single_net/swings.py
"""
from __future__ import annotations

import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.gtp import local_gtp, parse_raw_nn  # noqa: E402

PROFILE = "rank_9d"
THRESHOLD = 5.0
OUT = Path(__file__).resolve().parent / "runs" / "swings-v1.json"


def lead(engine, size: int) -> float:
    return parse_raw_nn(engine.send("kata-raw-nn 0"), size).fields["whiteLead"]


def main() -> int:
    rows = [json.loads(line) for line in (paths.experiments_dir() / "e4_kgs_rank_matches" / "runs" / "v1" / "samples.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    games = [r for r in rows if r["size"] == 13 and r["weak"] in ("rank_17k", "rank_11k", "rank_5k")][:6]
    games += [r for r in rows if r["size"] == 19 and r["weak"] in ("rank_14k", "rank_8k")][:2]
    main_gtp = local_gtp(katago=paths.katago_binary(), model=paths.main_model(), config=paths.gtp_config(), overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), label="main")
    human_gtp = local_gtp(katago=paths.katago_binary(), model=paths.human_model(), config=paths.gtp_config(), overrides={**ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), "humanSLProfile": PROFILE}, label="human-only")
    out = []
    try:
        for game in games:
            size = game["size"]
            for engine in (main_gtp, human_gtp):
                for command in (f"boardsize {size}", "komi 6.5", "kata-set-rules japanese", "clear_board"):
                    engine.send(command)
            by_main, by_human = [lead(main_gtp, size)], [lead(human_gtp, size)]
            for color, move in game["record"]:
                for engine in (main_gtp, human_gtp):
                    engine.send(f"play {color} {move}")
                by_main.append(lead(main_gtp, size))
                by_human.append(lead(human_gtp, size))
            swings = lambda leads: {i for i in range(len(leads) - 1) if abs(leads[i + 1] - leads[i]) >= THRESHOLD}  # noqa: E731
            main_swings, human_swings = swings(by_main), swings(by_human)
            diffs = [abs(a - b) for a, b in zip(by_main, by_human)]
            out.append(
                {
                    "game": game["key"],
                    "moves": len(game["record"]),
                    "mainSwings": len(main_swings),
                    "humanSwings": len(human_swings),
                    "both": len(main_swings & human_swings),
                    "missedByHuman": len(main_swings - human_swings),
                    "extraByHuman": len(human_swings - main_swings),
                    "meanAbsLeadDiff": round(statistics.mean(diffs), 2),
                    "maxAbsLeadDiff": round(max(diffs), 1),
                }
            )
            print(out[-1])
    finally:
        main_gtp.close()
        human_gtp.close()
    total = {key: sum(row[key] for row in out) for key in ("moves", "mainSwings", "humanSwings", "both", "missedByHuman", "extraByHuman")}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"profile": PROFILE, "threshold": THRESHOLD, "games": out, "total": total}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
