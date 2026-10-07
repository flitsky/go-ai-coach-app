#!/usr/bin/env python3
"""E10 — 가망 없는 판에서 AI가 통과(또는 기권)하게 하려면 무엇을 신호로 삼나(백로그 #213, 2026-10-07 사용자 제보).

급수 캐릭터가 **이미 끝난 판에서 상대 집 안에 계속 둔다.** 사용자 안: AI의 집이 0이고 상대 집이 크며 상대 승률이 99% 이상인 상태가
2수 이상 이어지면 통과하거나 기권한다. 이 실험은 그 조건과 이웃 조건들을 **기보에 걸어** 본다 — 얼마나 자주 잘못 걸리고(걸렸는데
실제로는 그 진영이 이긴 판), 진 판을 얼마나 덮고, 몇 수를 아끼나.

신호는 앱이 급수 캐릭터와 두는 동안 실제로 가진 것이다: **사람 모델만 올린 프로세스**의 평가 1회(`kata-raw-nn`, 프로필 `rank_9d`) —
점수차·승률·영역. 주 모델은 그동안 올라가 있지 않다(#215: 신경망은 한 번에 하나).

기보: E4(급수 대 급수, 9·13·19줄 270판)와 E2b(캐릭터 대 사람 프로필, 13줄 144판 — 크게 무너지는 판이 많다). 두 진영을 모두 본다.

    python3 engine-lab/experiments/e10_hopeless_pass/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from statistics import median

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.board import BLACK, WHITE, Board, Position, parse_vertex, vertex  # noqa: E402
from lab.gtp import local_gtp, parse_raw_nn  # noqa: E402
from lab.runs import RunDir, read_jsonl  # noqa: E402

EXPERIMENT = "e10_hopeless_pass"
JUDGE_PROFILE = "rank_9d"
CONFIDENT = 0.6  # 이 값 이상으로 기울어야 「그 진영의 집」으로 센다
SOURCES = ("e4_kgs_rank_matches", "e2b_tier_vs_human")

# (이름, 조건(둔 진영 기준 승률·점수차·자기 집 수), 연속 수) — 「user」가 사용자 안이다.
RULES = (
    ("user: 승률≤1% · 내 집 0 · 2수", lambda w, l, t: w <= 0.01 and t == 0, 2),
    ("승률≤1% · 내 집 0 · 3수", lambda w, l, t: w <= 0.01 and t == 0, 3),
    ("승률≤1% · 2수", lambda w, l, t: w <= 0.01, 2),
    ("승률≤1% · 점수≤−20 · 2수", lambda w, l, t: w <= 0.01 and l <= -20, 2),
    ("승률≤1% · 점수≤−30 · 2수", lambda w, l, t: w <= 0.01 and l <= -30, 2),
    ("승률≤1% · 점수≤−30 · 3수", lambda w, l, t: w <= 0.01 and l <= -30, 3),
    ("승률≤1% · 점수≤−40 · 2수", lambda w, l, t: w <= 0.01 and l <= -40, 2),
    ("점수≤−30 · 2수", lambda w, l, t: l <= -30, 2),
    ("점수≤−40 · 2수", lambda w, l, t: l <= -40, 2),
)


def latest_samples(experiment: str) -> Path:
    runs = sorted((paths.experiments_dir() / experiment / "runs").glob("*/samples.jsonl"))
    if not runs:
        raise SystemExit(f"{experiment} 결과가 없다")
    return runs[-1]


def ownership_grid(response: str, size: int) -> list[list[float]] | None:
    lines = [line.strip() for line in response.splitlines()]
    if "whiteOwnership" not in lines:
        return None
    start = lines.index("whiteOwnership") + 1
    grid = []
    for row in range(size):
        grid.append([float(v) if v not in ("NAN", "nan") else 0.0 for v in lines[start + row].split()])
    return grid


def territory(board: Board, grid: list[list[float]], color: str) -> int:
    """빈 자리 가운데 [color] 쪽으로 확실히 기운 자리의 수 — 그 진영의 집(어림)."""
    sign = 1.0 if color == WHITE else -1.0
    return sum(1 for r in range(board.size) for c in range(board.size) if board.grid[r][c] is None and grid[r][c] * sign >= CONFIDENT)


def pack(moves: list[dict | None]) -> dict:
    """수마다 사전을 적으면 414판에 6MB가 된다 — 열 단위로 묶어 적는다(값이 없는 수는 `null`)."""
    column = lambda key: [None if m is None else m[key] for m in moves]  # noqa: E731
    flags = lambda key: "".join("-" if m is None else ("1" if m[key] else "0") for m in moves)  # noqa: E731
    return {"colors": "".join("-" if m is None else m["c"] for m in moves), "lead": column("lead"), "win": column("win"),
            "mine": column("mine"), "theirs": column("theirs"), "pass": flags("pass"), "nextPass": flags("nextPass")}


def unpack(trace: dict) -> list[dict | None]:
    return [
        None if color == "-" else {"c": color, "lead": trace["lead"][i], "win": trace["win"][i], "mine": trace["mine"][i],
                                   "theirs": trace["theirs"][i], "pass": trace["pass"][i] == "1", "nextPass": trace["nextPass"][i] == "1"}
        for i, color in enumerate(trace["colors"])
    ]


def trigger_index(flags: list[bool], streak: int) -> int | None:
    run = 0
    for i, flag in enumerate(flags):
        run = run + 1 if flag else 0
        if run >= streak:
            return i
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--limit", type=int, default=0, help="출처마다 앞의 N판만(빠른 확인용)")
    args = parser.parse_args()

    games = []
    for experiment in SOURCES:
        rows = [row for row in read_jsonl(latest_samples(experiment)) if "record" in row]
        games += [(experiment, row) for row in (rows[: args.limit] if args.limit else rows)]
    run = RunDir(EXPERIMENT, args.label, vars(args))
    print(f"→ {run.path} ({len(games)} games)", file=sys.stderr)
    engine = local_gtp(
        katago=paths.katago_binary(),
        model=paths.human_model(),  # ⚠️ `-model`로 준다 — 급수 캐릭터와 두는 동안 앱에 올라가 있는 것은 이 망뿐이다
        config=paths.gtp_config(),
        overrides={**ap.gtp_overrides(ap.FAST_BEGINNER_VISITS), "humanSLProfile": JUDGE_PROFILE},
        label="human-only",
    )
    run.set_engine_versions({"humanOnly": engine.version})
    traces = []
    started = time.time()
    try:
        for index, (source, game) in enumerate(games, start=1):
            size = int(game["size"])
            engine.setup(Position(id=f"g{index}", size=size))
            board = Board(size)
            moves = []
            for color, move in game["record"]:
                engine.play(color, move)
                point = parse_vertex(size, move)
                if point is not None:
                    board = board.try_play(color, point) or board
                response = engine.send("kata-raw-nn 0")
                parsed = parse_raw_nn(response, size)
                fields = parsed.fields
                grid = ownership_grid(response, size)
                white_lead, white_win = fields.get("whiteLead"), fields.get("whiteWin")
                if white_lead is None or white_win is None or grid is None:
                    moves.append(None)
                    continue
                sign = 1.0 if color == WHITE else -1.0
                moves.append({
                    "c": color,
                    "pass": point is None,
                    "lead": round(white_lead * sign, 2),
                    "win": round(white_win if color == WHITE else 1.0 - white_win, 4),
                    "mine": territory(board, grid, color),
                    "theirs": territory(board, grid, WHITE if color == BLACK else BLACK),
                    # 이 수 다음에 둘 진영에게 심판(9단 정책)이 「통과가 1위」라고 하는가 — 앱의 `HumanMoveSampler.shouldPass`와 같은 판정.
                    "nextPass": parsed.pass_prob is not None and parsed.pass_prob > max(parsed.policy.values(), default=0.0),
                })
            trace = {"source": source, "game": index, "size": size, "finalBlackLead": game["blackLead"], **pack(moves)}
            run.sample(trace)
            traces.append(trace)
            if index % 40 == 0:
                print(f"[{index}/{len(games)}] {time.time() - started:.0f}s", file=sys.stderr, flush=True)
                run.write_summary(*summarize(traces))
    finally:
        engine.close()
    summary, markdown = summarize(traces)
    run.finish(summary, markdown)
    print(markdown)
    return 0


def side_samples(traces: list[dict]):
    """(판 크기, 그 진영이 둔 수들의 평가, 그 진영의 최종 점수차) — 판마다 두 진영."""
    for trace in traces:
        if trace["finalBlackLead"] is None:
            continue
        record = unpack(trace)
        for color in (BLACK, WHITE):
            own = [m for m in record if m is not None and m["c"] == color]
            final = trace["finalBlackLead"] if color == BLACK else -trace["finalBlackLead"]
            yield trace["size"], own, final


def moves_after_judge_said_pass(traces: list[dict]) -> dict[int, list[int]]:
    """판 크기별로, 심판이 처음 「이 진영은 통과가 1위」라고 한 뒤 그 진영이 **더 둔 돌**의 수(통과는 세지 않는다)."""
    out: dict[int, list[int]] = {}
    for trace in traces:
        record = unpack(trace)
        for color in (BLACK, WHITE):
            extra, asked = 0, False
            for index, move in enumerate(record):
                if move is None or move["c"] != color:
                    continue
                before = record[index - 1] if index > 0 else None
                if not asked and before is not None and before.get("nextPass"):
                    asked = True
                if asked and not move.get("pass"):
                    extra += 1
            if asked:
                out.setdefault(trace["size"], []).append(extra)
    return out


def summarize(traces: list[dict]) -> tuple[dict, str]:
    samples = list(side_samples(traces))
    sizes = sorted({size for size, _, _ in samples})
    summary: dict = {"games": len(traces), "rules": {}}
    lines = [
        "# E10 결과 — 가망 없는 판의 신호(사람 모델 `rank_9d` 평가 1회)",
        "",
        f"- 기보 {len(traces)}판(E4 급수 대 급수 9·13·19줄 + E2b 캐릭터 대 사람 프로필 13줄), 진영 표본 {len(samples)}.",
        "- 조건은 그 진영이 **자기 수를 둔 직후**의 값으로 본다(앱에서 AI 차례가 끝날 때의 자리). 「내 집」 = 빈 자리 중 그 진영 쪽으로 0.6 이상 기운 자리.",
        "- **잘못 걸림** = 걸렸는데 그 진영이 실제로 이긴 판 · **아슬** = 걸렸는데 10집 안쪽으로 진 판 · **덮음** = 20집 넘게 진 판 가운데 걸린 비율 · **남은 수** = 걸린 뒤 그 진영이 더 둔 수(중앙값).",
        "",
    ]
    for scope in ["all", *sizes]:
        scoped = [s for s in samples if scope == "all" or s[0] == scope]
        lost_big = [s for s in scoped if s[2] <= -20]
        lines += [f"## {'전체' if scope == 'all' else f'{scope}줄'} — 진영 표본 {len(scoped)}(20집 넘게 진 표본 {len(lost_big)})", "", "| 조건 | 걸린 판 | 잘못 걸림 | 아슬 | 덮음 | 남은 수 | 걸린 판의 최종 점수차(중앙값) |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
        for name, bad, streak in RULES:
            fired = []
            for _size, own, final in scoped:
                at = trigger_index([bad(m["win"], m["lead"], m["mine"]) for m in own], streak)
                if at is not None:
                    fired.append((final, len(own) - 1 - at))
            wrong = sum(1 for final, _ in fired if final > 0)
            close = sum(1 for final, _ in fired if -10 < final <= 0)
            covered = sum(1 for final, _ in fired if final <= -20)
            row = {
                "fired": len(fired),
                "wrong": wrong,
                "close": close,
                "coverage": covered / len(lost_big) if lost_big else None,
                "movesLeft": median(left for _, left in fired) if fired else None,
                "finalLead": median(final for final, _ in fired) if fired else None,
            }
            summary["rules"].setdefault(str(scope), {})[name] = row
            pct = lambda n: "–" if not fired else f"{n / len(fired) * 100:.1f}% ({n})"  # noqa: E731
            coverage = "–" if row["coverage"] is None else f"{row['coverage'] * 100:.0f}%"
            moves_left = "–" if row["movesLeft"] is None else f"{row['movesLeft']:.0f}"
            final_lead = "–" if row["finalLead"] is None else f"{row['finalLead']:+.0f}"
            lines.append(f"| {name} | {len(fired)} | {pct(wrong)} | {pct(close)} | {coverage} | {moves_left} | {final_lead} |")
        lines.append("")
    late = moves_after_judge_said_pass(traces)
    lines += ["## 끝났는데 더 둔 수 — 심판(9단 정책)이 처음 「통과가 1위」라고 한 뒤 그 진영이 더 둔 돌", "", "| 판 | 진영 표본 | 0수 | 1~5수 | 6수 이상 | 중앙값 | 가장 많이 |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for size in sorted(late):
        values = late[size]
        summary.setdefault("lateMoves", {})[str(size)] = {"n": len(values), "median": median(values), "max": max(values)}
        share = lambda pred: f"{sum(1 for v in values if pred(v)) / len(values) * 100:.0f}%"  # noqa: E731
        lines.append(f"| {size}줄 | {len(values)} | {share(lambda v: v == 0)} | {share(lambda v: 1 <= v <= 5)} | {share(lambda v: v >= 6)} | {median(values):.0f} | {max(values)} |")
    lines.append("")
    return summary, "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
