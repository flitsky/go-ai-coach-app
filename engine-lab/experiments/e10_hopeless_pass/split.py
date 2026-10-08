#!/usr/bin/env python3
"""E10 보조 — 「끝난 판의 통과」와 「중반의 기권 제안」을 가르면 각각 무엇을 신호로 삼나(백로그 #213, 2026-10-07 사용자).

사용자가 둘을 갈랐다: **통과**는 종국에서만(안: 「승률이 0%이고 판 넓이의 80%를 둔 상태」), **중반**에는 통과하지 않고 AI가 한 번
**기권을 제안**한다(사용자가 받거나 계속 둔다). 이 스크립트는 E10이 남긴 기보별 평가(`samples.jsonl` — 사람 모델 `rank_9d` 평가 1회)를
다시 읽어 두 조건을 건다. 엔진을 띄우지 않는다.

- 「판 넓이의 80%를 둔 상태」는 **수순 길이 ≥ 0.8 × 판의 자리 수**로 읽는다(9줄 65수 · 13줄 136수 · 19줄 289수) — 앱이 형세 값 없이도
  아는 숫자다. 판에 놓인 돌 수(따낸 돌을 뺀)로 읽으면 종국에도 80%에 닿지 않는다(집이 빈 자리로 남는다).
- 조건은 그 진영이 **자기 수를 둔 직후**의 값으로 본다. 그 수가 놓인 뒤의 수순 길이로 종국·중반을 가른다.

    python3 engine-lab/experiments/e10_hopeless_pass/split.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from statistics import median

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab.runs import read_jsonl  # noqa: E402

RUNS = Path(__file__).resolve().parent / "runs"
ENDGAME_SHARE = 0.8


def latest_run() -> Path:
    runs = sorted(path.parent for path in RUNS.glob("*/samples.jsonl"))
    if not runs:
        raise SystemExit("E10 결과가 없다 — 먼저 run.py를 돌린다")
    return runs[-1]


def side_turns(trace: dict):
    """(진영, 그 진영의 수들 — 수마다 (그 수가 놓인 뒤의 수순 길이, 승률, 점수차, 내 집), 그 진영의 최종 점수차)."""
    if trace["finalBlackLead"] is None:
        return
    for color in "BW":
        turns = [
            (index + 1, trace["win"][index], trace["lead"][index], trace["mine"][index])
            for index, c in enumerate(trace["colors"])
            if c == color
        ]
        yield turns, (trace["finalBlackLead"] if color == "B" else -trace["finalBlackLead"])


def first_fire(flags: list[bool], streak: int) -> int | None:
    run = 0
    for index, flag in enumerate(flags):
        run = run + 1 if flag else 0
        if run >= streak:
            return index
    return None


def lost(win, lead, mine) -> bool:
    return win <= 0.01


def far_behind(win, lead, mine) -> bool:
    return win <= 0.01 and lead <= -30


def barren(win, lead, mine) -> bool:
    return far_behind(win, lead, mine) and mine == 0


# 종국의 통과 — (이름, 조건, 연속 수). 수순 길이가 판 넓이의 80%를 넘긴 수에서만 센다.
PASS_RULES = (
    ("승률 ≤ 1% · 2수 (앱)", lost, 2),
    ("승률 ≤ 1% · 1수", lost, 1),
    ("승률 ≤ 1% · 점수 ≤ −30집 · 2수", far_behind, 2),
)

# 중반의 기권 제안 — (이름, [(조건, 연속 수), …] 가운데 하나라도). 수순 길이가 80%에 못 미친 수에서만 센다.
OFFER_RULES = (
    ("집 0 · 승률 ≤ 1% · 점수 ≤ −30집 · 2수", [(barren, 2)]),
    ("승률 ≤ 1% · 점수 ≤ −30집 · 10수", [(far_behind, 10)]),
    ("위 둘 가운데 하나 (앱)", [(barren, 2), (far_behind, 10)]),
    ("승률 ≤ 1% · 점수 ≤ −30집 · 5수", [(far_behind, 5)]),
    ("승률 ≤ 1% · 점수 ≤ −40집 · 10수", [(lambda w, l, m: w <= 0.01 and l <= -40, 10)]),
    ("점수 ≤ −15집 · 10수 (E3의 주 모델 기준을 사람 모델 값에)", [(lambda w, l, m: l <= -15, 10)]),
)


def tally(fired: list[tuple[float, int, float]]) -> dict:
    """fired = (최종 점수차, 걸린 뒤 그 진영이 더 둔 수, 걸린 순간의 점수차)."""
    finals = [final for final, _, _ in fired]
    close_drift = [abs(final - at) for final, _, at in fired if -10 < final <= 0]
    return {
        "fired": len(fired),
        "wrong": sum(1 for final in finals if final > 0),
        "close": sum(1 for final in finals if -10 < final <= 0),
        "finalLead": median(finals) if finals else None,
        "movesLeft": median(left for _, left, _ in fired) if fired else None,
        # 아슬한 판에서 「걸린 순간의 점수차」와 「끝까지 둔 뒤의 점수차」가 얼마나 다른가 — 작으면 남은 수가 승부와 무관했다는 뜻이다.
        "closeDrift": median(close_drift) if close_drift else None,
    }


def row(name: str, result: dict) -> str:
    pct = lambda n: "–" if not result["fired"] else f"{n / result['fired'] * 100:.1f}% ({n})"  # noqa: E731
    number = lambda value, spec: "–" if value is None else format(value, spec)  # noqa: E731
    return (
        f"| {name} | {result['fired']} | {pct(result['wrong'])} | {pct(result['close'])} | {number(result['finalLead'], '+.0f')} "
        f"| {number(result['movesLeft'], '.0f')} | {number(result['closeDrift'], '.1f')} |"
    )


def main() -> int:
    run = latest_run()
    traces = [trace for trace in read_jsonl(run / "samples.jsonl") if "colors" in trace]
    sizes = sorted({trace["size"] for trace in traces})
    summary: dict = {"run": run.name, "endgameShare": ENDGAME_SHARE, "length": {}, "pass": {}, "offer": {}}
    lines = [
        "# E10 보조 — 끝난 판의 통과와 중반의 기권 제안",
        "",
        f"- 원자료: `{run.name}/samples.jsonl`(기보 {len(traces)}판, 사람 모델 `rank_9d` 평가 1회). 엔진을 다시 띄우지 않았다.",
        f"- **종국** = 그 수가 놓인 뒤의 수순 길이 ≥ {ENDGAME_SHARE} × 판의 자리 수. **중반** = 그 전.",
        "- **잘못** = 걸렸는데 그 진영이 실제로 이긴 판 · **아슬** = 걸렸는데 10집 안쪽으로 진 판 · **남은 수** = 걸린 뒤 그 진영이 실제로 더 둔 수(중앙값).",
        "- **아슬한 판의 변화** = 아슬한 판에서 걸린 순간의 점수차와 끝까지 둔 뒤의 점수차의 차(절댓값의 중앙값, 집) — 작으면 남은 수가 승부와 무관했다.",
        "",
        "## 판이 끝날 때의 수순 길이 ÷ 판의 자리 수",
        "",
        "| 판 | 기보 | 가장 짧게 | 중앙값 | 가장 길게 | 80%를 넘긴 판 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for size in sizes:
        shares = [len(trace["colors"]) / (size * size) for trace in traces if trace["size"] == size]
        over = sum(1 for share in shares if share >= ENDGAME_SHARE)
        summary["length"][str(size)] = {"games": len(shares), "min": min(shares), "median": median(shares), "max": max(shares), "over": over}
        lines.append(f"| {size}줄 | {len(shares)} | {min(shares):.2f} | {median(shares):.2f} | {max(shares):.2f} | {over / len(shares) * 100:.0f}% ({over}) |")

    def scoped(size):
        for trace in traces:
            if size == "all" or trace["size"] == size:
                area = trace["size"] * trace["size"]
                for turns, final in side_turns(trace):
                    yield area, turns, final

    for title, key, rules in (("종국의 통과 — 수순 길이가 80%를 넘긴 수에서만", "pass", PASS_RULES), ("중반의 기권 제안 — 수순 길이가 80%에 못 미친 수에서만", "offer", OFFER_RULES)):
        lines += ["", f"## {title}", ""]
        for size in ["all", *sizes]:
            lines += [f"### {'전체' if size == 'all' else f'{size}줄'}", "", "| 조건 | 걸린 판 | 잘못 | 아슬 | 걸린 판의 최종 점수차(중앙값) | 남은 수 | 아슬한 판의 변화 |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
            for rule in rules:
                name = rule[0]
                conditions = [(rule[1], rule[2])] if key == "pass" else rule[1]
                fired = []
                for area, turns, final in scoped(size):
                    in_endgame = lambda length: length >= ENDGAME_SHARE * area  # noqa: E731
                    hits = []
                    for bad, streak in conditions:
                        # 종국의 통과는 **연속 조건을 중반부터 세되** 통과는 종국에 든 수에서만 한다(앱과 같다). 제안은 중반의 수만 본다.
                        flags = [bad(win, lead, mine) and (key == "pass" or not in_endgame(length)) for length, win, lead, mine in turns]
                        run_length = 0
                        for index, ((length, *_), flag) in enumerate(zip(turns, flags)):
                            run_length = run_length + 1 if flag else 0
                            if run_length >= streak and (key == "offer" or in_endgame(length)):
                                hits.append(index)
                                break
                    if hits:
                        at = min(hits)
                        fired.append((final, len(turns) - 1 - at, turns[at][2]))
                result = tally(fired)
                summary[key].setdefault(str(size), {})[name] = result
                lines.append(row(name, result))
            lines.append("")
    (run / "split.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    markdown = "\n".join(lines)
    (run / "split.md").write_text(markdown + "\n", encoding="utf-8")
    print(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
