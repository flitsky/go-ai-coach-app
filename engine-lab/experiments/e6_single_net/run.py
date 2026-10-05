#!/usr/bin/env python3
"""E6 — 신경망을 하나만 올려도 되나: 사람 모델 하나로 형세·추천 수를 맡기면 얼마나 틀리나, 주 모델 하나로 급수를 낼 수 있나(백로그 #215).

폰에서 신경망 하나는 메모리 약 500MB다(E5). 둘을 같이 올리는 대신 **하나만** 올리는 두 길을 잰다.

1. **사람 모델만**(사용자 제안, 2026-10-05): 급수 캐릭터 대국에서 착수는 급수 프로필로, 형세 판단·추천 수는 **가장 센 프로필**로.
   - 형세: 사람 모델의 평가 1회(`kata-raw-nn`, 프로필 `rank_9d` 등)가 기준(주 모델 깊은 탐색)에서 얼마나 벗어나나 — 지금 앱의 형세(주 모델 평가 1회)와 나란히.
   - 추천 수: 사람 모델의 정책 1위 · 사람 모델 16방문 탐색 1위가 기준으로 몇 집 손해인가 — 지금 앱의 추천 수(주 모델 16방문)와 나란히.
   - 영역: 확실한 자리(|값| ≥ 0.6)에서 주인이 주 모델과 어긋나는 비율.
2. **주 모델만**: 주 모델 정책을 온도로 흩어 뽑으면 한 수 손해가 사람 모델 급수 프로필(E2)만큼 나오나.

    python3 engine-lab/experiments/e6_single_net/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import human, paths  # noqa: E402
from lab.analysis import AnalysisEngine, local_analysis, to_mover  # noqa: E402
from lab.board import WHITE, Position, load_positions, vertex  # noqa: E402
from lab.gtp import GtpEngine, local_gtp, parse_raw_nn  # noqa: E402
from lab.reference import ReferenceEvaluator, to_mover_winrate  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e6_single_net"
COVER_MASS = 0.97
MAX_RATED_MOVES = 25
CONFIDENT = 0.6


def white_to_mover(position: Position, white_value: float) -> float:
    return white_value if position.next_player == WHITE else -white_value


def parse_ownership(response: str, size: int) -> dict[str, float]:
    """`kata-raw-nn`의 `whiteOwnership` 격자(위 행부터) → 좌표별 값(백 +1 … 흑 −1)."""
    lines = [line.strip() for line in response.splitlines()]
    out: dict[str, float] = {}
    if "whiteOwnership" not in lines:
        return out
    start = lines.index("whiteOwnership") + 1
    for row in range(size):
        for col, value in enumerate(lines[start + row].split()):
            try:
                number = float(value)
            except ValueError:
                continue
            if number == number:
                out[vertex(size, row, col)] = number
    return out


def raw(engine: GtpEngine, position: Position) -> dict:
    response = engine.send("kata-raw-nn 0")
    parsed = parse_raw_nn(response, position.size)
    lead = parsed.fields.get("whiteLead")
    win = parsed.fields.get("whiteWin")
    return {
        "lead": None if lead is None else white_to_mover(position, lead),
        "win": None if win is None else (win if position.next_player == WHITE else 1.0 - win),
        "policy": parsed.policy,
        "topPolicy": max(parsed.policy, key=parsed.policy.get) if parsed.policy else None,
        "ownership": parse_ownership(response, position.size),
    }


def ownership_mismatch(candidate: dict[str, float], truth: dict[str, float]) -> float | None:
    """주 모델이 확실하다고 본 자리 가운데, 후보가 주인을 다르게 보거나 확실하지 않다고 본 비율."""
    confident = [point for point, value in truth.items() if abs(value) >= CONFIDENT and point in candidate]
    if not confident:
        return None
    wrong = sum(1 for point in confident if candidate[point] * truth[point] <= 0 or abs(candidate[point]) < 0.2)
    return wrong / len(confident)


def covering_moves(distribution: dict[str, float]) -> list[str]:
    picked, mass = [], 0.0
    for move in sorted(distribution, key=distribution.get, reverse=True):
        picked.append(move)
        mass += distribution[move]
        if mass >= COVER_MASS or len(picked) >= MAX_RATED_MOVES:
            break
    return picked


def expected_loss(distribution: dict[str, float], losses: dict[str, float]) -> dict | None:
    rated = {move: p for move, p in distribution.items() if move in losses}
    mass = sum(rated.values())
    if mass <= 0:
        return None
    return {
        "coveredMass": mass,
        "expLoss": sum(p * losses[m] for m, p in rated.items()) / mass,
        "pLoss3": sum(p for m, p in rated.items() if losses[m] >= 3.0) / mass,
        "pLoss10": sum(p for m, p in rated.items() if losses[m] >= 10.0) / mass,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--positions", default=str(paths.positions_dir() / "selfplay-human-v1.json"))
    parser.add_argument("--top-profiles", default="rank_9d,preaz_9d,proyear_2023", help="사람 모델에서 「가장 센 설정」 후보")
    parser.add_argument("--temperatures", default="1.0,1.5,2.0,3.0", help="주 모델 정책을 흩는 온도")
    parser.add_argument("--sizes", default="", help="쉼표로 고른 판 크기만(비우면 전부)")
    parser.add_argument("--root-visits", type=int, default=800)
    parser.add_argument("--ref-root-visits", type=int, default=800)
    parser.add_argument("--ref-move-visits", type=int, default=300)
    parser.add_argument("--ref-threads", type=int, default=8)
    args = parser.parse_args()
    profiles = [p for p in args.top_profiles.split(",") if p]
    temperatures = [float(t) for t in args.temperatures.split(",") if t]
    sizes = {int(v) for v in args.sizes.split(",") if v}
    positions = [p for p in load_positions(Path(args.positions)) if not sizes or p.size in sizes]

    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    main_gtp = local_gtp(katago=paths.katago_binary(), model=paths.main_model(), config=paths.gtp_config(), overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), label="main")
    human_gtp = local_gtp(
        katago=paths.katago_binary(),
        model=paths.human_model(),  # ⚠️ `-model`로 준다 — 이 프로세스에는 주 모델이 없다
        config=paths.gtp_config(),
        overrides={**ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), "humanSLProfile": profiles[0]},
        label="human-only",
    )
    ref_engine = local_analysis(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.analysis_config(),
        overrides={"numAnalysisThreads": str(args.ref_threads), "numSearchThreads": "2", "logToStderr": "false", "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"},
        label="reference",
    )
    evaluator = ReferenceEvaluator(ref_engine, paths.LAB_ROOT / "cache" / f"reference-b18-r{args.ref_root_visits}-m{args.ref_move_visits}.jsonl", root_visits=args.ref_root_visits, move_visits=args.ref_move_visits)
    run.set_engine_versions({"main": main_gtp.version, "humanOnly": human_gtp.version, "reference": ref_engine.version})

    try:
        roots = ref_engine.query_many([AnalysisEngine.build_query(p, max_visits=args.root_visits) for p in positions])
        for index, (position, root) in enumerate(zip(positions, roots), start=1):
            info = root.get("rootInfo", {})
            ref_lead = to_mover(position, float(info["scoreLead"]))
            ref_win = to_mover_winrate(position, float(info["winrate"]))
            player = position.next_player

            main_gtp.setup(position)
            main_raw = raw(main_gtp, position)
            main_search = main_gtp.search_analyze(player, visits=ap.FAST_BEGINNER_VISITS, time_ms=None, max_candidates=5)
            main_top = next((c.move for c in main_search.candidates), None)

            human_gtp.setup(position)
            by_profile = {}
            for profile in profiles:
                human_gtp.set_param("humanSLProfile", profile)
                by_profile[profile] = raw(human_gtp, position)
            human_gtp.set_param("humanSLProfile", profiles[0])
            human_gtp.clear_cache()
            human_search = human_gtp.search_analyze(player, visits=ap.FAST_BEGINNER_VISITS, time_ms=None, max_candidates=5)
            human_search_top = next((c.move for c in human_search.candidates), None)

            distributions = {t: human.temperature_transform(main_raw["policy"], t, 1.0) for t in temperatures}
            wanted = {main_top, human_search_top, main_raw["topPolicy"], *(r["topPolicy"] for r in by_profile.values())}
            for distribution in distributions.values():
                wanted.update(covering_moves(distribution))
            ref_best, losses = evaluator.point_losses(position, sorted(m for m in wanted if m and m.lower() != "pass"))

            row = {
                "positionId": position.id,
                "size": position.size,
                "phase": position.phase,
                "moveNumber": position.move_number,
                "refLead": ref_lead,
                "refWin": ref_win,
                "refBest": ref_best,
                "main": {"rawLead": main_raw["lead"], "rawWin": main_raw["win"], "topPolicy": main_raw["topPolicy"], "search16Top": main_top},
                "humanOnly": {
                    profile: {
                        "rawLead": r["lead"],
                        "rawWin": r["win"],
                        "topPolicy": r["topPolicy"],
                        "ownershipMismatch": ownership_mismatch(r["ownership"], main_raw["ownership"]),
                    }
                    for profile, r in by_profile.items()
                },
                "humanOnlySearch16Top": human_search_top,
                "losses": {move: losses.get(move) for move in sorted(m for m in wanted if m)},
                "mainPolicyTemperature": {str(t): expected_loss(d, losses) for t, d in distributions.items()},
            }
            run.sample(row)
            if index % 10 == 0:
                print(f"  {index}/{len(positions)}", file=sys.stderr)
    finally:
        main_gtp.close()
        human_gtp.close()
        ref_engine.close()

    summary, markdown = summarize(run.completed_samples(), profiles, temperatures)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def stats(values: list[float]) -> dict:
    values = sorted(v for v in values if v is not None)
    if not values:
        return {}
    return {"n": len(values), "mean": statistics.mean(values), "median": statistics.median(values), "p90": values[min(len(values) - 1, int(len(values) * 0.9))], "max": values[-1]}


def summarize(rows: list[dict], profiles: list[str], temperatures: list[float]) -> tuple[dict, str]:
    fmt = lambda s, key: "–" if not s else f"{s[key]:.1f}"  # noqa: E731
    pct = lambda value: "–" if value is None else f"{value * 100:.0f}%"  # noqa: E731
    summary: dict = {"positions": len(rows), "lead": {}, "moves": {}, "ownership": {}, "mainPolicyTemperature": {}}
    lines = [f"# E6 — 신경망을 하나만 올려도 되나 ({len(rows)}국면)", ""]

    lines += ["## 형세 — 평가 1회의 점수차가 기준(주 모델 깊은 탐색)에서 벗어난 집 수", "", "| 무엇으로 | 평균 | 중앙값 | 열에 아홉은 이내 | 최대 | 우세한 쪽을 반대로 본 비율(기준 3집 이상) |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
    evaluators = [("주 모델(지금 앱)", lambda r: r["main"]["rawLead"])] + [(f"사람 모델만 · {p}", (lambda r, p=p: r["humanOnly"][p]["rawLead"])) for p in profiles]
    for name, pick in evaluators:
        errors = [abs(pick(r) - r["refLead"]) for r in rows if pick(r) is not None]
        decided = [r for r in rows if abs(r["refLead"]) >= 3.0 and pick(r) is not None]
        flipped = sum(1 for r in decided if pick(r) * r["refLead"] < 0) / len(decided) if decided else None
        s = stats(errors)
        summary["lead"][name] = {**s, "flippedRate": flipped, "decided": len(decided)}
        lines.append(f"| {name} | {fmt(s, 'mean')} | {fmt(s, 'median')} | {fmt(s, 'p90')} | {fmt(s, 'max')} | {pct(flipped)} |")
    for size in sorted({r["size"] for r in rows}):
        sized = [r for r in rows if r["size"] == size]
        parts = []
        for name, pick in evaluators:
            s = stats([abs(pick(r) - r["refLead"]) for r in sized if pick(r) is not None])
            summary["lead"].setdefault("bySize", {}).setdefault(str(size), {})[name] = s
            parts.append(f"{name} {fmt(s, 'mean')}")
        lines.append(f"\n- {size}줄({len(sized)}국면) 평균: " + " · ".join(parts))

    lines += ["", "## 추천 수 — 1위로 내놓는 수가 기준으로 몇 집 손해인가", "", "| 무엇으로 | 평균 손해 | 기준 최선수와 같은 비율 | 3집 이상 손해 | 10집 이상 손해 |", "| --- | ---: | ---: | ---: | ---: |"]
    movers = [("주 모델 16방문(지금 앱)", lambda r: r["main"]["search16Top"]), ("주 모델 정책 1위(평가 1회)", lambda r: r["main"]["topPolicy"]), (f"사람 모델만 16방문 · {profiles[0]}", lambda r: r["humanOnlySearch16Top"])]
    movers += [(f"사람 모델만 정책 1위 · {p}", (lambda r, p=p: r["humanOnly"][p]["topPolicy"])) for p in profiles]
    for name, pick in movers:
        rated = [(r, r["losses"].get(pick(r))) for r in rows if pick(r) and str(pick(r)).lower() != "pass"]
        rated = [(r, loss) for r, loss in rated if loss is not None]
        if not rated:
            continue
        losses = [loss for _, loss in rated]
        entry = {"n": len(rated), "meanLoss": statistics.mean(losses), "sameAsBest": sum(1 for r, _ in rated if pick(r) == r["refBest"]) / len(rated), "loss3": sum(1 for v in losses if v >= 3.0) / len(rated), "loss10": sum(1 for v in losses if v >= 10.0) / len(rated)}
        summary["moves"][name] = entry
        lines.append(f"| {name} | {entry['meanLoss']:.2f}집 | {pct(entry['sameAsBest'])} | {pct(entry['loss3'])} | {pct(entry['loss10'])} |")

    lines += ["", f"## 영역 — 주 모델이 확실하다고 본 자리(|값| ≥ {CONFIDENT})에서 주인을 다르게 본 비율", "", "| 무엇으로 | 평균 | 열에 아홉은 이내 | 최대 |", "| --- | ---: | ---: | ---: |"]
    for profile in profiles:
        s = stats([r["humanOnly"][profile]["ownershipMismatch"] for r in rows])
        summary["ownership"][profile] = s
        if s:
            lines.append(f"| 사람 모델만 · {profile} | {s['mean'] * 100:.1f}% | {s['p90'] * 100:.1f}% | {s['max'] * 100:.1f}% |")

    lines += ["", "## 주 모델 하나로 급수를 내기 — 정책을 온도로 흩어 뽑았을 때 한 수 손해", "", "| 온도 | 한 수 손해(기대값) | 3집 이상 | 10집 이상 | 잰 확률 질량 |", "| ---: | ---: | ---: | ---: | ---: |"]
    for t in temperatures:
        picked = [r["mainPolicyTemperature"].get(str(t)) for r in rows]
        picked = [p for p in picked if p]
        if not picked:
            continue
        entry = {key: statistics.mean(p[key] for p in picked) for key in ("expLoss", "pLoss3", "pLoss10", "coveredMass")}
        summary["mainPolicyTemperature"][str(t)] = entry
        lines.append(f"| {t:g} | {entry['expLoss']:.2f}집 | {pct(entry['pLoss3'])} | {pct(entry['pLoss10'])} | {pct(entry['coveredMass'])} |")
    lines += ["", "(비교 — E2의 사람 모델 급수 프로필 한 수 손해: 20급 4.6집 … 3단 2.4집. 온도를 올릴수록 확률이 넓게 퍼져 잰 질량이 줄어든다 — 그때의 손해는 **적게 잡힌** 값이다.)"]
    return summary, "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.exit(main())
