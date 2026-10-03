#!/usr/bin/env python3
"""E2 — 사람 모델(Human SL)로 1방문 추출하면 수의 손해는 어떤가, 캐릭터 5단계와 비교하면 어느 프로필쯤인가(백로그 #214 → #215).

국면마다 프로필을 바꿔 가며 `kata-raw-human-nn 0`(사람 정책 평가 1회)을 받고, 레시피(R2 꼬리 누르기 · R3 전체 온도)로
**뽑힐 확률 분포**를 만든다. 확률 질량의 97%(최대 25수)를 맥 기준 평가로 재서 기대 손해·최선 비율·큰 실수 비율을 낸다.
E1 맥 결과(`--e1-summary`)가 있으면 16방문 캐릭터 5단계와 나란히 놓는다.

⚠️ 이것은 **세기 보정이 아니다** — 손해 분포가 비슷하다는 것이지 승률이 같다는 것이 아니다. 승률은 대국으로 잰다(E2b, 리서치 §6).

    python3 engine-lab/experiments/e2_human_sl/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
from collections import defaultdict
from pathlib import Path
from statistics import mean, median

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import human, paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.board import load_positions  # noqa: E402
from lab.evaluation import fmt_num, fmt_pct  # noqa: E402
from lab.gtp import local_gtp  # noqa: E402
from lab.reference import ReferenceEvaluator  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e2_human_sl"
COVER_MASS = 0.97
MAX_RATED_MOVES = 25


def covering_moves(distribution: dict[str, float]) -> list[str]:
    ordered = sorted(distribution, key=distribution.get, reverse=True)
    picked, mass = [], 0.0
    for move in ordered:
        picked.append(move)
        mass += distribution[move]
        if mass >= COVER_MASS or len(picked) >= MAX_RATED_MOVES:
            break
    return picked


def outcome(distribution: dict[str, float], losses: dict[str, float], ref_best: str) -> dict:
    rated = {m: p for m, p in distribution.items() if m in losses}
    mass = sum(rated.values())
    if mass <= 0:
        return {}
    return {
        "coveredMass": mass,
        "expLoss": sum(p * losses[m] for m, p in rated.items()) / mass,
        "pRefBest": rated.get(ref_best, 0.0) / mass,
        "pLoss3": sum(p for m, p in rated.items() if losses[m] >= 3.0) / mass,
        "pLoss10": sum(p for m, p in rated.items() if losses[m] >= 10.0) / mass,
    }


def entropy(distribution: dict[str, float]) -> float:
    return -sum(p * math.log(p) for p in distribution.values() if p > 0)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--positions", default=str(paths.positions_dir() / "selfplay-human-v1.json"))
    parser.add_argument("--sizes", default="9,13,19")
    parser.add_argument("--profiles", default=",".join(human.DEFAULT_PROFILES))
    parser.add_argument("--recipes", default="R2-tail,R3-full")
    parser.add_argument("--e1-summary", default="", help="비교할 E1 맥 summary.json(비우면 가장 최근 맥 실행)")
    parser.add_argument("--ref-root-visits", type=int, default=800)
    parser.add_argument("--ref-move-visits", type=int, default=300)
    parser.add_argument("--ref-threads", type=int, default=8)
    args = parser.parse_args()

    if not paths.human_model().exists():
        print(f"사람 모델이 없다: {paths.human_model()}", file=sys.stderr)
        return 1
    sizes = {int(s) for s in args.sizes.split(",")}
    positions = [p for p in load_positions(Path(args.positions)) if p.size in sizes]
    profiles = [p for p in args.profiles.split(",") if p]
    recipes = [human.RECIPES[name] for name in args.recipes.split(",") if name]

    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    gtp = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.gtp_config(),
        human_model=paths.human_model(),
        overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS),
        label="human-gtp",
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
        paths.LAB_ROOT / "cache" / f"reference-b18-r{args.ref_root_visits}-m{args.ref_move_visits}.jsonl",
        root_visits=args.ref_root_visits,
        move_visits=args.ref_move_visits,
    )
    run.set_engine_versions({"gtp": gtp.version, "reference": ref_engine.version})

    samples: list[dict] = []
    started = time.time()
    try:
        for index, position in enumerate(positions, start=1):
            gtp.setup(position)
            per_profile = []
            wanted: set[str] = set()
            for profile in profiles:
                gtp.set_param("humanSLProfile", profile)
                raw = gtp.raw_nn(position.size, human=True)
                policy = {m: p for m, p in raw.policy.items()}
                distributions = {
                    r.name: human.choice_distribution(policy, r, position.move_number, position.size) for r in recipes
                }
                for dist in distributions.values():
                    wanted.update(covering_moves(dist))
                per_profile.append((profile, raw, policy, distributions))
            ref_best, losses = evaluator.point_losses(position, sorted(wanted))
            for profile, raw, policy, distributions in per_profile:
                top_human = max(policy, key=policy.get) if policy else None
                row = {
                    "positionId": position.id,
                    "size": position.size,
                    "phase": position.phase,
                    "moveNumber": position.move_number,
                    "profile": profile,
                    "rawHumanNnMs": round(raw.elapsed_ms, 1),
                    "humanPassProb": raw.pass_prob,
                    "humanTopMove": top_human,
                    "humanTopIsRefBest": top_human == ref_best,
                    "humanTopLoss": losses.get(top_human),
                    "policyEntropy": entropy(policy),
                    "refBest": ref_best,
                    "recipes": {name: outcome(dist, losses, ref_best) for name, dist in distributions.items()},
                    "topPolicy": dict(sorted(policy.items(), key=lambda kv: -kv[1])[:10]),
                }
                run.sample(row)
                samples.append(row)
            print(f"[{index}/{len(positions)}] {position.id} — {len(wanted)} moves rated, {time.time() - started:.0f}s", file=sys.stderr, flush=True)
    finally:
        gtp.close()
        ref_engine.close()

    e1 = load_e1_summary(args.e1_summary)
    summary, markdown = summarize(samples, profiles, [r.name for r in recipes], e1, args)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def load_e1_summary(path_text: str) -> dict | None:
    if path_text:
        path = Path(path_text)
    else:
        runs = sorted((paths.experiments_dir() / "e1_low_visit_candidates" / "runs").glob("*/summary.json"))
        runs = [p for p in runs if not json.loads(p.read_text())["phone"]]
        if not runs:
            return None
        path = runs[-1]
    data = json.loads(path.read_text())
    data["_path"] = str(path.relative_to(paths.REPO_ROOT)) if path.is_relative_to(paths.REPO_ROOT) else str(path)
    return data


def summarize(samples: list[dict], profiles: list[str], recipes: list[str], e1: dict | None, args) -> tuple[dict, str]:
    table: dict[str, dict] = {}
    for profile in profiles:
        rows = [s for s in samples if s["profile"] == profile]
        entry = {
            "samples": len(rows),
            "humanTopIsRefBest": mean(1.0 if r["humanTopIsRefBest"] else 0.0 for r in rows),
            "medianRawHumanNnMs": median(r["rawHumanNnMs"] for r in rows),
            "recipes": {},
        }
        for recipe in recipes:
            outs = [r["recipes"][recipe] for r in rows if r["recipes"].get(recipe)]
            entry["recipes"][recipe] = {
                "expLoss": mean(o["expLoss"] for o in outs),
                "pRefBest": mean(o["pRefBest"] for o in outs),
                "pLoss3": mean(o["pLoss3"] for o in outs),
                "pLoss10": mean(o["pLoss10"] for o in outs),
                "coveredMass": mean(o["coveredMass"] for o in outs),
                "bySize": {
                    str(size): mean(o["expLoss"] for r, o in ((r, r["recipes"].get(recipe)) for r in rows if r["size"] == size) if o)
                    for size in sorted({r["size"] for r in rows})
                },
            }
        table[profile] = entry
    summary = {"profiles": table}
    tiers16 = None
    if e1:
        group = e1["groups"].get(f"all|{ap.FAST_BEGINNER_VISITS}v")
        if group:
            tiers16 = group["tiers"]
            summary["e1Tiers16v"] = {"source": e1["_path"], "tiers": tiers16}
    return summary, render(summary, recipes, tiers16, e1, args)


def render(summary: dict, recipes: list[str], tiers16: dict | None, e1: dict | None, args) -> str:
    lines = [
        "# E2 결과 — 사람 모델 1방문 추출의 손해(프로필 × 레시피)",
        "",
        f"- 국면: `{Path(args.positions).name}`, 판 크기 {args.sizes}",
        f"- 사람 정책: `kata-raw-human-nn 0`(평가 1회). 레시피: " + " · ".join(human.RECIPES[r].describe() for r in recipes) + ". 통과는 뽑지 않는다.",
        f"- 손해: 맥 기준 평가(주 모델, {args.ref_root_visits}/{args.ref_move_visits}방문). 뽑힐 확률 질량의 {int(COVER_MASS * 100)}%(최대 {MAX_RATED_MOVES}수)를 잰다.",
        "- ⚠️ 손해 분포가 비슷하다고 **승률이 같은 것은 아니다**(세기 보정은 대국으로 — E2b).",
        "",
        "## 1. 프로필별",
        "",
        "| 프로필 | 레시피 | 평균 손해(집) | 기준 최선 | 3집 이상 | 10집 이상 | 사람 1위 = 기준 최선 | 평가 시간(맥, 중앙값) |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for profile, entry in summary["profiles"].items():
        for recipe in recipes:
            r = entry["recipes"][recipe]
            lines.append(
                f"| {profile} | {recipe} | {fmt_num(r['expLoss'])} | {fmt_pct(r['pRefBest'])} | {fmt_pct(r['pLoss3'])} | {fmt_pct(r['pLoss10'])} | "
                f"{fmt_pct(entry['humanTopIsRefBest'])} | {fmt_num(entry['medianRawHumanNnMs'], 0)}ms |"
            )
    if tiers16:
        lines += [
            "",
            f"## 2. 비교 — 지금 캐릭터 5단계(16방문, E1 `{e1['_path']}`)",
            "",
            "| 단계 | 평균 손해(집) | 기준 최선 | 3집 이상 | 10집 이상 |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
        for name, t in tiers16.items():
            lines.append(f"| {name} | {fmt_num(t['expLoss'])} | {fmt_pct(t['pRefBest'])} | {fmt_pct(t['pLoss3'])} | {fmt_pct(t['pLoss10'])} |")
        lines += ["", "### 평균 손해가 가장 가까운 프로필(R2)", "", "| 단계 | 가까운 프로필 | 그 프로필의 평균 손해 |", "| --- | --- | ---: |"]
        for name, t in tiers16.items():
            best = min(
                summary["profiles"].items(),
                key=lambda kv: abs(kv[1]["recipes"].get("R2-tail", kv[1]["recipes"][recipes[0]])["expLoss"] - t["expLoss"]),
            )
            r = best[1]["recipes"].get("R2-tail", best[1]["recipes"][recipes[0]])
            lines.append(f"| {name} | {best[0]} | {fmt_num(r['expLoss'])} |")
    lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
