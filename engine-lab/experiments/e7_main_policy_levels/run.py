#!/usr/bin/env python3
"""E7 — 사람 모델 없이 주 모델 하나로 급수를 내면 얼마나 세고, 얼마나 사람 같은가(백로그 #215).

폰에 신경망을 하나 더 올리는 값(E5: 메모리 +500MB, 앱 +99MB)을 아예 안 내는 길 — 주 모델의 **정책을 온도로 흩어** 한 수를 뽑는다
(평가 1회, 지금 16방문의 1/15). 사람 모델은 폰에 싣지 않고 **여기 실험실에서 자(尺)로만** 쓴다.

1. 사람다움(`--skip-games`로 이것만): 국면마다 「주 모델 정책 · 온도 T」로 뽑을 수의 분포가 사람 모델 급수 프로필의 분포와 얼마나 겹치나,
   그리고 **그 급수의 사람이 거의 안 두는 수**(사람 정책 0.5% 미만)를 둘 확률이 얼마인가. 비교 눈금으로 사람 프로필끼리도 잰다.
2. 세기: 온도별 선수가 사람 모델 프로필(R2)과 13줄에서 끝까지 둔 승률.

    python3 engine-lab/experiments/e7_main_policy_levels/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import random
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import human, paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.board import BLACK, PASS, Position, load_positions  # noqa: E402
from lab.gtp import GtpEngine, local_gtp  # noqa: E402
from lab.match import HumanPlayer, Player, play_game  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e7_main_policy_levels"
RARE = 0.005


@dataclass
class MainPolicyPlayer(Player):
    """주 모델 정책 1회 평가 → 온도로 흩어 뽑는다. 통과는 정책 1위가 통과일 때만(`HumanPlayer`와 같은 규칙)."""

    temperature: float

    @property
    def name(self) -> str:  # type: ignore[override]
        return f"main-T{self.temperature:g}"

    def choose(self, engine: GtpEngine, position: Position, rng: random.Random) -> tuple[str, dict]:
        raw = engine.raw_nn(position.size, human=False)
        if raw.pass_prob is not None and raw.pass_prob > max(raw.policy.values(), default=0.0):
            return PASS, {"pass": True}
        if not raw.policy:
            return PASS, {}
        distribution = human.temperature_transform(raw.policy, self.temperature, 1.0)
        move = human.sample(distribution, rng)
        return move, {"prob": round(distribution[move], 4)}


def overlap(p: dict[str, float], q: dict[str, float]) -> float:
    return sum(min(value, q.get(move, 0.0)) for move, value in p.items())


def rare_mass(p: dict[str, float], reference: dict[str, float]) -> float:
    """`p`로 뽑은 수가, `reference`(그 급수의 사람)가 0.5% 미만으로 보는 수일 확률."""
    return sum(value for move, value in p.items() if reference.get(move, 0.0) < RARE)


def likeness(engine: GtpEngine, positions: list[Position], profiles: list[str], temperatures: list[float], run: RunDir) -> None:
    for position in positions:
        engine.setup(position)
        main_policy = engine.raw_nn(position.size, human=False).policy
        humans = {}
        for profile in profiles:
            engine.set_param("humanSLProfile", profile)
            humans[profile] = human.choice_distribution(engine.raw_nn(position.size, human=True).policy, human.R2_TAIL, position.move_number, position.size)
        row = {"kind": "likeness", "positionId": position.id, "size": position.size, "byTemperature": {}, "humanVsHuman": {}}
        for t in temperatures:
            distribution = human.temperature_transform(main_policy, t, 1.0)
            row["byTemperature"][str(t)] = {profile: {"overlap": overlap(distribution, humans[profile]), "rare": rare_mass(distribution, humans[profile])} for profile in profiles}
        for a in profiles:
            row["humanVsHuman"][a] = {b: {"overlap": overlap(humans[a], humans[b]), "rare": rare_mass(humans[a], humans[b])} for b in profiles}
        run.sample(row)


def games(engine: GtpEngine, judge, profiles: list[str], temperatures: list[float], count: int, size: int, seed: int, run: RunDir) -> None:
    for t in temperatures:
        for profile in profiles:
            for index in range(count):
                bot_is_black = index % 2 == 0
                bot, opponent = MainPolicyPlayer(t), HumanPlayer(profile)
                rng = random.Random(f"{seed}|{t}|{profile}|{index}")
                result = play_game(engine, judge, bot if bot_is_black else opponent, opponent if bot_is_black else bot, size=size, rng=rng)
                bot_lead = None if result.black_lead is None else (result.black_lead if bot_is_black else -result.black_lead)
                run.sample({"kind": "game", "temperature": t, "profile": profile, "game": index + 1, "botColor": BLACK if bot_is_black else "W", "botLead": bot_lead, "botWon": None if bot_lead is None else bot_lead > 0, "moves": len(result.moves), "endedBy": result.ended_by, "record": result.moves})
            print(f"  T{t:g} vs {profile} done", file=sys.stderr)


def summarize(rows: list[dict], profiles: list[str], temperatures: list[float]) -> tuple[dict, str]:
    pct = lambda value: "–" if value is None else f"{value * 100:.0f}%"  # noqa: E731
    like = [r for r in rows if r["kind"] == "likeness"]
    played = [r for r in rows if r["kind"] == "game"]
    summary: dict = {"likeness": {}, "humanVsHuman": {}, "games": {}}
    lines = ["# E7 — 주 모델 하나로 급수를 내면", ""]
    if like:
        lines += [f"## 사람다움({len(like)}국면) — 그 급수의 사람이 거의 안 두는 수(사람 정책 0.5% 미만)를 둘 확률 / 분포가 겹치는 정도", "", "| 누가 두나 | " + " | ".join(profiles) + " |", "| --- | " + " | ".join("---:" for _ in profiles) + " |"]
        for t in temperatures:
            cells = []
            for profile in profiles:
                rare = statistics.mean(r["byTemperature"][str(t)][profile]["rare"] for r in like)
                over = statistics.mean(r["byTemperature"][str(t)][profile]["overlap"] for r in like)
                summary["likeness"].setdefault(str(t), {})[profile] = {"rare": rare, "overlap": over}
                cells.append(f"{pct(rare)} / {pct(over)}")
            lines.append(f"| 주 모델 · 온도 {t:g} | " + " | ".join(cells) + " |")
        for a in profiles:
            cells = []
            for b in profiles:
                rare = statistics.mean(r["humanVsHuman"][a][b]["rare"] for r in like)
                over = statistics.mean(r["humanVsHuman"][a][b]["overlap"] for r in like)
                summary["humanVsHuman"].setdefault(a, {})[b] = {"rare": rare, "overlap": over}
                cells.append(f"{pct(rare)} / {pct(over)}")
            lines.append(f"| 사람 모델 · {a} | " + " | ".join(cells) + " |")
        lines += ["", "(열 = 자로 삼은 사람 프로필. 앞 숫자가 작고 뒤 숫자가 클수록 그 급수의 사람처럼 둔다. 사람 모델 줄의 대각선이 「자기 자신」이다.)", ""]
    if played:
        lines += ["## 세기 — 온도별 선수가 사람 모델 프로필을 이긴 비율", "", "| 주 모델 온도 | " + " | ".join(profiles) + " |", "| ---: | " + " | ".join("---:" for _ in profiles) + " |"]
        for t in temperatures:
            cells = []
            for profile in profiles:
                picked = [r for r in played if r["temperature"] == t and r["profile"] == profile and r["botWon"] is not None]
                rate = sum(1 for r in picked if r["botWon"]) / len(picked) if picked else None
                summary["games"].setdefault(str(t), {})[profile] = {"games": len(picked), "winRate": rate}
                cells.append(f"{pct(rate)} ({len(picked)}판)" if picked else "–")
            lines.append(f"| {t:g} | " + " | ".join(cells) + " |")
    return summary, "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--positions", default=str(paths.positions_dir() / "selfplay-human-v1.json"))
    parser.add_argument("--profiles", default="rank_15k,rank_8k,rank_3k,rank_3d")
    parser.add_argument("--temperatures", default="1.0,1.5,2.0,3.0,4.0")
    parser.add_argument("--games", type=int, default=6, help="온도 × 프로필마다 몇 판(흑백 번갈아)")
    parser.add_argument("--size", type=int, default=ap.DEFAULT_BOARD_SIZE)
    parser.add_argument("--seed", type=int, default=20261005)
    parser.add_argument("--skip-games", action="store_true")
    args = parser.parse_args()
    profiles = [p for p in args.profiles.split(",") if p]
    temperatures = [float(t) for t in args.temperatures.split(",") if t]

    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    engine = local_gtp(katago=paths.katago_binary(), model=paths.main_model(), config=paths.gtp_config(), human_model=paths.human_model(), overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), label="main+human")
    judge = local_analysis(katago=paths.katago_binary(), model=paths.main_model(), config=paths.analysis_config(), overrides={"numAnalysisThreads": "2", "numSearchThreads": "4", "logToStderr": "false", "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"}, label="judge")
    run.set_engine_versions({"gtp": engine.version, "judge": judge.version})
    try:
        likeness(engine, load_positions(Path(args.positions)), profiles, temperatures, run)
        summary, markdown = summarize(run.completed_samples(), profiles, temperatures)
        run.write_summary(summary, markdown)
        if not args.skip_games:
            games(engine, judge, profiles, temperatures, args.games, args.size, args.seed, run)
    finally:
        engine.close()
        judge.close()
    summary, markdown = summarize(run.completed_samples(), profiles, temperatures)
    run.finish(summary, markdown)
    print(markdown)
    return 0


if __name__ == "__main__":
    sys.exit(main())
