#!/usr/bin/env python3
"""E9 — 사람 모델 하나에 「센 탐색」까지 맡길 수 있나(백로그 #215, 2026-10-06 사용자 질문).

앱에 **모델 파일을 하나만** 싣는 길은 주 모델을 빼고 사람 모델만 두는 것뿐이다(급수 프로필은 사람 모델의 입력이다 — E6·E7).
그러면 고수·초고수의 탐색(16·32방문)과 추천 수도 사람 모델이 맡는다. E6은 평가 1회와 16방문의 「추천 수 손해」까지만 쟀다 —
이 실험은 **같은 방문 수로 탐색했을 때** 사람 모델(가장 센 프로필)이 주 모델에 얼마나 미치는지를 잰다.

1. `--mode match` — **세기**: 주 모델 N방문 대 사람 모델 M방문, 끝까지 둔다. 둘 다 탐색 1위 수를 둔다(앱의 초고수와 같은 방식).
   같은 시작 수순을 흑백 바꿔 두 번 둔다(탐색 1위만 두면 판이 서로 닮는다 — 시작 몇 수를 정책에서 뽑아 판을 흩는다).
2. `--mode quality` — **추천 수·형세**: 국면 66개에서 탐색 1위 수의 손해와 탐색이 내는 점수차의 오차(기준 = 주 모델 깊은 탐색).

    python3 engine-lab/experiments/e9_human_net_search/run.py --mode quality --label mac-quality-v1
    python3 engine-lab/experiments/e9_human_net_search/run.py --mode match --label mac-13-v1 --pairs 16:16,32:32,16:32
"""
from __future__ import annotations

import argparse
import math
import random
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.analysis import AnalysisEngine, local_analysis, to_mover  # noqa: E402
from lab.board import BLACK, PASS, Position, load_positions  # noqa: E402
from lab.gtp import GtpEngine, local_gtp  # noqa: E402
from lab.reference import ReferenceEvaluator  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e9_human_net_search"
OPENING_PLIES = 6
OPENING_MIN_PRIOR = 0.03
QUIET = {"logToStderr": "false", "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"}


def wilson(wins: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n == 0:
        return (0.0, 1.0)
    p = wins / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def engines(profile: str) -> tuple[GtpEngine, GtpEngine]:
    main = local_gtp(katago=paths.katago_binary(), model=paths.main_model(), config=paths.gtp_config(), overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), label="main")
    human_only = local_gtp(
        katago=paths.katago_binary(),
        model=paths.human_model(),  # ⚠️ `-model`로 준다 — 이 프로세스에는 주 모델이 없다(E6과 같은 띄우기)
        config=paths.gtp_config(),
        overrides={**ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), "humanSLProfile": profile},
        label="human-only",
    )
    return main, human_only


def top_move(engine: GtpEngine, color: str, visits: int) -> tuple[str, float | None]:
    """앱의 「탐색 1위만 둔다」(초고수) — 후보 1개를 요청해 그 수를 둔다. 점수차는 둘 차례 기준이다(cfg 기본 SIDETOMOVE)."""
    result = engine.search_analyze(color, visits=visits, time_ms=None, max_candidates=1)
    if not result.candidates:
        return PASS, None
    return result.candidates[0].move, result.candidates[0].score_lead


# --- 세기: 끝까지 두기 ---------------------------------------------------------------------------


def opening(main: GtpEngine, size: int, rng: random.Random) -> list[tuple[str, str]]:
    """시작 몇 수를 주 모델 정책에서 확률대로 뽑는다(너무 드문 수는 뺀다) — 판을 흩기 위한 것이고 양쪽에 똑같이 준다."""
    position = Position(id="opening", size=size, komi=ap.DEFAULT_KOMI, rules=ap.DEFAULT_RULES)
    main.setup(position)
    for _ in range(OPENING_PLIES):
        policy = {move: p for move, p in main.raw_nn(size).policy.items() if p >= OPENING_MIN_PRIOR}
        if not policy:
            break
        moves = list(policy)
        move = rng.choices(moves, weights=[policy[m] for m in moves], k=1)[0]
        color = position.next_player
        main.play(color, move)
        position.moves.append((color, move))
    return list(position.moves)


def play_game(main: GtpEngine, human_only: GtpEngine, judge: AnalysisEngine, *, start: list[tuple[str, str]], size: int, human_color: str, main_visits: int, human_visits: int, judge_visits: int) -> dict:
    position = Position(id="game", size=size, komi=ap.DEFAULT_KOMI, rules=ap.DEFAULT_RULES, moves=list(start))
    for engine in (main, human_only):
        engine.setup(position)
        engine.clear_cache()
    limit = int(size * size * 1.6)
    passes, ended_by = 0, "limit"
    while position.move_number < limit:
        color = position.next_player
        mover, visits = (human_only, human_visits) if color == human_color else (main, main_visits)
        move, _lead = top_move(mover, color, visits)
        try:
            mover.play(color, move)
        except RuntimeError:
            move = PASS
            mover.play(color, move)
        (main if mover is human_only else human_only).play(color, move)
        position.moves.append((color, move))
        passes = passes + 1 if move.lower() == PASS else 0
        if passes >= 2:
            ended_by = "pass-pass"
            break
    lead = judge.query(position, max_visits=judge_visits).get("rootInfo", {}).get("scoreLead")
    black_lead = None if lead is None else float(lead)
    human_lead = None if black_lead is None else (black_lead if human_color == BLACK else -black_lead)
    return {
        "humanColor": human_color,
        "blackLead": black_lead,
        "humanLead": human_lead,
        "humanWon": None if human_lead is None else human_lead > 0,
        "moves": len(position.moves),
        "endedBy": ended_by,
        "record": [[c, m] for c, m in position.moves],
    }


def run_match(args) -> int:
    pairs = [tuple(int(v) for v in pair.split(":")) for pair in args.pairs.split(",") if pair]
    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    main, human_only = engines(args.profile)
    judge = local_analysis(katago=paths.katago_binary(), model=paths.main_model(), config=paths.analysis_config(), overrides={"numAnalysisThreads": "1", "numSearchThreads": "4", **QUIET}, label="judge")
    run.set_engine_versions({"main": main.version, "humanOnly": human_only.version, "judge": judge.version})
    rows: list[dict] = []
    started = time.time()
    try:
        starts = [opening(main, args.size, random.Random(args.seed + index)) for index in range(args.openings)]
        for main_visits, human_visits in pairs:
            for index, start in enumerate(starts):
                for human_color in ("B", "W"):
                    row = play_game(main, human_only, judge, start=start, size=args.size, human_color=human_color, main_visits=main_visits, human_visits=human_visits, judge_visits=args.judge_visits)
                    row.update({"pair": f"{main_visits}:{human_visits}", "mainVisits": main_visits, "humanVisits": human_visits, "opening": index, "size": args.size, "profile": args.profile})
                    run.sample(row)
                    rows.append(row)
            done = [r for r in rows if r["pair"] == f"{main_visits}:{human_visits}"]
            print(f"main {main_visits} vs human {human_visits}: human {sum(1 for r in done if r['humanWon'])}/{len(done)} — {time.time() - started:.0f}s", file=sys.stderr, flush=True)
            run.write_summary(*summarize_match(rows, args))
    finally:
        main.close()
        human_only.close()
        judge.close()
    summary, markdown = summarize_match(rows, args)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def summarize_match(rows: list[dict], args) -> tuple[dict, str]:
    pairs = {}
    for row in rows:
        pairs.setdefault(row["pair"], []).append(row)
    summary = {"size": args.size, "profile": args.profile, "pairs": {}}
    lines = [
        f"# E9 결과 — 주 모델 탐색 대 사람 모델({args.profile}) 탐색, {args.size}줄 실제 대국",
        "",
        f"- 둘 다 탐색 1위 수를 둔다. 시작 {OPENING_PLIES}수는 주 모델 정책에서 뽑아 양쪽에 똑같이 주고, 같은 시작을 흑백 바꿔 두 번 둔다.",
        f"- 덤 {ap.DEFAULT_KOMI}, {ap.DEFAULT_RULES}. 판정: 주 모델 {args.judge_visits}방문 점수(계가가 아니라 형세 판정).",
        "",
        "| 주 모델 방문 | 사람 모델 방문 | 판 | 사람 모델 승 | 승률(95% 구간) | 평균 점수차(사람 모델 기준) | 평균 수 |",
        "| ---: | ---: | ---: | ---: | --- | ---: | ---: |",
    ]
    for pair, games in pairs.items():
        scored = [g for g in games if g["humanWon"] is not None]
        wins = sum(1 for g in scored if g["humanWon"])
        low, high = wilson(wins, len(scored))
        mean_lead = statistics.mean(g["humanLead"] for g in scored) if scored else None
        summary["pairs"][pair] = {"games": len(scored), "humanWins": wins, "humanWinRate": wins / len(scored) if scored else None, "ci95": [low, high], "meanHumanLead": mean_lead, "meanMoves": statistics.mean(g["moves"] for g in games)}
        main_visits, human_visits = pair.split(":")
        lines.append(f"| {main_visits} | {human_visits} | {len(scored)} | {wins} | {wins / len(scored) * 100:.0f}% ({low * 100:.0f}~{high * 100:.0f}%) | {mean_lead:+.1f} | {statistics.mean(g['moves'] for g in games):.0f} |")
    lines.append("")
    return summary, "\n".join(lines)


# --- 추천 수·형세: 국면별 ------------------------------------------------------------------------


def run_quality(args) -> int:
    visit_counts = [int(v) for v in args.visits.split(",") if v]
    sizes = {int(v) for v in args.sizes.split(",") if v}
    positions = [p for p in load_positions(Path(args.positions)) if not sizes or p.size in sizes]
    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    main, human_only = engines(args.profile)
    ref_engine = local_analysis(katago=paths.katago_binary(), model=paths.main_model(), config=paths.analysis_config(), overrides={"numAnalysisThreads": str(args.ref_threads), "numSearchThreads": "2", **QUIET}, label="reference")
    evaluator = ReferenceEvaluator(ref_engine, paths.LAB_ROOT / "cache" / f"reference-b18-r{args.ref_root_visits}-m{args.ref_move_visits}.jsonl", root_visits=args.ref_root_visits, move_visits=args.ref_move_visits)
    run.set_engine_versions({"main": main.version, "humanOnly": human_only.version, "reference": ref_engine.version})
    try:
        roots = ref_engine.query_many([AnalysisEngine.build_query(p, max_visits=args.ref_root_visits) for p in positions])
        for index, (position, root) in enumerate(zip(positions, roots), start=1):
            ref_lead = to_mover(position, float(root["rootInfo"]["scoreLead"]))
            searched: dict[str, dict] = {}
            for name, engine in (("main", main), ("human", human_only)):
                engine.setup(position)
                for visits in visit_counts:
                    engine.clear_cache()  # 방문 수마다 새 탐색 — 앞의 트리를 물려받으면 16과 32가 같은 것을 잰다
                    move, lead = top_move(engine, position.next_player, visits)
                    searched[f"{name}{visits}"] = {"top": move, "lead": lead}
            wanted = sorted({s["top"] for s in searched.values() if s["top"].lower() != PASS})
            _best, losses = evaluator.point_losses(position, wanted)
            for entry in searched.values():
                entry["loss"] = losses.get(entry["top"])
                entry["leadError"] = None if entry["lead"] is None else abs(entry["lead"] - ref_lead)
            run.sample({"positionId": position.id, "size": position.size, "phase": position.phase, "moveNumber": position.move_number, "refLead": ref_lead, "searched": searched})
            if index % 10 == 0:
                print(f"  {index}/{len(positions)}", file=sys.stderr, flush=True)
    finally:
        main.close()
        human_only.close()
        ref_engine.close()
    summary, markdown = summarize_quality(run.completed_samples(), visit_counts, args)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def stats(values: list[float]) -> dict:
    values = sorted(v for v in values if v is not None)
    if not values:
        return {}
    return {"n": len(values), "mean": statistics.mean(values), "median": statistics.median(values), "p90": values[min(len(values) - 1, int(len(values) * 0.9))]}


def summarize_quality(rows: list[dict], visit_counts: list[int], args) -> tuple[dict, str]:
    summary: dict = {"positions": len(rows), "profile": args.profile, "byKey": {}}
    lines = [
        f"# E9 결과 — 탐색 1위 수의 손해와 탐색 점수차의 오차, 주 모델 대 사람 모델만({args.profile})",
        "",
        f"- 국면 {len(rows)}개. 기준 = 주 모델 {args.ref_root_visits}방문 점수차 · 수의 손해는 주 모델 {args.ref_root_visits}/{args.ref_move_visits}방문.",
        "",
        "| 탐색 | 1위 수의 손해 — 평균 / 중앙값 | 3집 이상 손해 | 점수차 오차 — 중앙값 / 열에 아홉은 이내 |",
        "| --- | ---: | ---: | ---: |",
    ]
    for visits in visit_counts:
        for name, label in (("main", "주 모델"), ("human", "사람 모델만")):
            key = f"{name}{visits}"
            loss = stats([r["searched"][key]["loss"] for r in rows])
            error = stats([r["searched"][key]["leadError"] for r in rows])
            losses = [r["searched"][key]["loss"] for r in rows if r["searched"][key]["loss"] is not None]
            big = sum(1 for v in losses if v >= 3.0) / len(losses) if losses else None
            summary["byKey"][key] = {"loss": loss, "loss3": big, "leadError": error}
            lines.append(f"| {label} {visits}방문 | {loss.get('mean', float('nan')):.2f} / {loss.get('median', float('nan')):.2f}집 | {(big or 0) * 100:.0f}% | {error.get('median', float('nan')):.1f} / {error.get('p90', float('nan')):.1f}집 |")
    lines.append("")
    return summary, "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", choices=("match", "quality"), required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--profile", default="rank_9d", help="사람 모델의 「가장 센 설정」(E6: 셋 가운데 형세가 가장 덜 틀렸다)")
    parser.add_argument("--size", type=int, default=ap.DEFAULT_BOARD_SIZE)
    parser.add_argument("--pairs", default="16:16,32:32,16:32", help="주 모델 방문:사람 모델 방문, 쉼표로")
    parser.add_argument("--openings", type=int, default=8, help="시작 수순 개수 — 짝마다 이 수의 두 배를 둔다(흑백 바꿔)")
    parser.add_argument("--judge-visits", type=int, default=400)
    parser.add_argument("--seed", type=int, default=215)
    parser.add_argument("--positions", default=str(paths.positions_dir() / "selfplay-human-v1.json"))
    parser.add_argument("--visits", default="16,32", help="quality: 잴 방문 수")
    parser.add_argument("--sizes", default="", help="quality: 쉼표로 고른 판 크기만(비우면 전부)")
    parser.add_argument("--ref-root-visits", type=int, default=800)
    parser.add_argument("--ref-move-visits", type=int, default=300)
    parser.add_argument("--ref-threads", type=int, default=8)
    args = parser.parse_args()
    return run_match(args) if args.mode == "match" else run_quality(args)


if __name__ == "__main__":
    raise SystemExit(main())
