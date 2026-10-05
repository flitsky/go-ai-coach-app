#!/usr/bin/env python3
"""E8 — 사람 모델만 올린 채로 두면 판이 제대로 끝나나(백로그 #215 ②b).

급수 캐릭터 대국 중에는 신경망을 사람 모델 하나만 올린다. 그런데 지금까지의 대국 실험(E2b·E4)은 **통과를 주 모델이 정했다**
(주 모델 정책 1위가 통과일 때만 통과 — 약한 프로필은 너무 일찍 통과하려 든다). 주 모델이 없는 프로세스에서는 그 자리를
**가장 센 프로필(`rank_9d`)의 정책**이 맡는다. 이 실험은 그 규칙으로 판이 끝나는지, 너무 일찍·늦게 끝나지 않는지를 본다.

규칙(앱이 쓸 것): 급수 프로필의 통과 확률이 `--pass-check`(기본 1%) 이상일 때만 `rank_9d` 정책을 한 번 더 보고, 거기서 통과가 1위면 통과한다.
견줄 것: E4의 같은 짝(주 모델이 통과를 정함)의 수순 길이, 그리고 끝난 국면을 주 모델 400방문으로 본 형세.

    python3 engine-lab/experiments/e8_human_only_pass/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import json
import random
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import human, paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.board import BLACK, PASS, Position  # noqa: E402
from lab.gtp import local_gtp, parse_raw_nn  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e8_human_only_pass"
JUDGE_PROFILE = "rank_9d"


def raw(engine, profile: str, size: int):
    engine.set_param("humanSLProfile", profile)
    return parse_raw_nn(engine.send("kata-raw-nn 0"), size)


def choose(engine, position: Position, profile: str, pass_check: float, rng: random.Random) -> tuple[str, dict]:
    own = raw(engine, profile, position.size)
    info = {"ownPass": own.pass_prob}
    if own.pass_prob is not None and own.pass_prob >= pass_check:
        judge = raw(engine, JUDGE_PROFILE, position.size)
        info["judgePass"] = judge.pass_prob
        if judge.pass_prob is not None and judge.pass_prob > max(judge.policy.values(), default=0.0):
            return PASS, info
    if not own.policy:
        return PASS, info
    distribution = human.choice_distribution(own.policy, human.R2_TAIL, position.move_number, position.size)
    return human.sample(distribution, rng), info


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--pairs", default="rank_17k:rank_14k,rank_11k:rank_8k,rank_5k:rank_2k")
    parser.add_argument("--sizes", default="13,9,19")
    parser.add_argument("--games", type=int, default=4)
    parser.add_argument("--pass-check", type=float, default=0.01)
    parser.add_argument("--seed", type=int, default=20261005)
    args = parser.parse_args()
    pairs = [tuple(pair.split(":")) for pair in args.pairs.split(",") if pair]
    sizes = [int(v) for v in args.sizes.split(",") if v]

    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path}", file=sys.stderr)
    engine = local_gtp(katago=paths.katago_binary(), model=paths.human_model(), config=paths.gtp_config(), overrides={**ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), "humanSLProfile": JUDGE_PROFILE}, label="human-only")
    judge = local_analysis(katago=paths.katago_binary(), model=paths.main_model(), config=paths.analysis_config(), overrides={"numAnalysisThreads": "2", "numSearchThreads": "4", "logToStderr": "false", "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"}, label="judge")
    try:
        for size in sizes:
            for weak, strong in pairs:
                for index in range(args.games):
                    rng = random.Random(f"{args.seed}|{size}|{weak}|{strong}|{index}")
                    black, white = (strong, weak) if index % 2 == 0 else (weak, strong)
                    position = Position(id="game", size=size, komi=ap.DEFAULT_KOMI, rules=ap.DEFAULT_RULES)
                    engine.setup(position)
                    limit = int(size * size * 1.6)
                    passes, ended_by, judge_checks = 0, "limit", 0
                    while position.move_number < limit:
                        color = position.next_player
                        move, info = choose(engine, position, black if color == BLACK else white, args.pass_check, rng)
                        judge_checks += 1 if "judgePass" in info else 0
                        try:
                            engine.play(color, move)
                        except RuntimeError:
                            move = PASS
                            engine.play(color, move)
                        position.moves.append((color, move))
                        passes = passes + 1 if move.lower() == PASS else 0
                        if passes >= 2:
                            ended_by = "pass-pass"
                            break
                    # 끝난 국면을 주 모델로 본다 — 더 둘 데가 남았으면 최선수의 득이 크다(통과가 일렀다는 뜻).
                    final = judge.query(position, max_visits=400)
                    infos = sorted(final.get("moveInfos", []), key=lambda i: i.get("order", 999))
                    best = infos[0] if infos else {}
                    root = final.get("rootInfo", {})
                    gain = None
                    if best and best.get("move", "").lower() != PASS:
                        pass_info = next((i for i in infos if i.get("move", "").lower() == PASS), None)
                        if pass_info is not None:
                            gain = abs(float(best["scoreLead"]) - float(pass_info["scoreLead"]))
                    run.sample({"size": size, "weak": weak, "strong": strong, "game": index + 1, "moves": len(position.moves), "endedBy": ended_by, "judgeChecks": judge_checks, "blackLead": root.get("scoreLead"), "bestAtEnd": best.get("move"), "gainOverPassAtEnd": gain, "record": position.moves})
                print(f"  {size}줄 {weak} vs {strong} done", file=sys.stderr)
    finally:
        engine.close()
        judge.close()

    rows = run.completed_samples()
    lines = ["# E8 — 사람 모델만 올린 채로 두면 판이 제대로 끝나나", "", "| 판 | 짝 | 판 수 | 통과-통과로 끝남 | 수순 길이(중앙값, 최소~최대) | 끝난 뒤 주 모델 최선수가 통과인 판 | 9단 정책을 더 본 횟수(판당) |", "| ---: | --- | ---: | ---: | --- | ---: | ---: |"]
    summary = {"rows": []}
    for size in sizes:
        for weak, strong in pairs:
            picked = [r for r in rows if r["size"] == size and r["weak"] == weak]
            if not picked:
                continue
            lengths = [r["moves"] for r in picked]
            entry = {"size": size, "pair": f"{weak}:{strong}", "games": len(picked), "passPass": sum(1 for r in picked if r["endedBy"] == "pass-pass"), "medianMoves": statistics.median(lengths), "minMoves": min(lengths), "maxMoves": max(lengths), "bestIsPassAtEnd": sum(1 for r in picked if (r.get("bestAtEnd") or "").lower() == PASS), "judgeChecksPerGame": statistics.mean(r["judgeChecks"] for r in picked)}
            summary["rows"].append(entry)
            lines.append(f"| {size} | {weak} · {strong} | {entry['games']} | {entry['passPass']} | {entry['medianMoves']:.0f} ({entry['minMoves']}~{entry['maxMoves']}) | {entry['bestIsPassAtEnd']} | {entry['judgeChecksPerGame']:.1f} |")
    run.finish(summary, "\n".join(lines) + "\n")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
