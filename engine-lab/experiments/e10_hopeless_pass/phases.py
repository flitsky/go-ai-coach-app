#!/usr/bin/env python3
"""E10 보조 2 — 국면(초·중·후·종반)과 「상대 착수 뒤 형세」로 기권을 고른다(백로그 #221, 2026-10-08 사용자).

사용자 안: 수순 길이 ÷ 판의 자리 수로 국면을 가르고(초반 < 30% · 중반 30~60% · 후반 60~80% · 종반 ≥ 80%), 중반부터 **상대 착수 뒤**의
형세가 **판의 10% 이상** 뒤지면 형세를 **5회** 모아 선형 추세를 종반까지 그어 보고, 이길 가능성이 보이면 버티고 아니면 기권한다.

이 스크립트는 E10이 남긴 기보별 평가(`samples.jsonl` — 사람 모델 `rank_9d` 평가 1회)를 다시 읽어 그 안과 이웃 안들을 건다.
엔진을 띄우지 않는다. 앱에 들어간 규칙(`HopelessPosition.resignationGrounds`)의 숫자는 `lab/app_parity.py`에서 가져온다.

- 한 진영을 AI로 놓고, 그 진영이 **상대가 둔 직후**에 본 값(= 상대가 자기 수 뒤에 본 값의 부호를 뒤집은 것)을 형세로 쓴다.
- 규칙은 중반·후반(30~80%)에서만 건다. 종반은 통과의 몫이다(`split.py`).

    python3 engine-lab/experiments/e10_hopeless_pass/phases.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from statistics import mean, median

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import app_parity as ap  # noqa: E402
from lab.runs import read_jsonl  # noqa: E402

RUNS = Path(__file__).resolve().parent / "runs"
MIDDLE, LATE, ENDGAME = ap.PHASE_MIDDLE_STARTS_AT, ap.PHASE_LATE_STARTS_AT, ap.PHASE_ENDGAME_STARTS_AT
WINDOW = ap.RESIGN_READINGS_BEFORE_OFFER
USER_EXAMPLE = [(25, -30.0), (27, -25.0), (29, -15.0), (31, -18.0), (33, -19.0)]  # 사용자가 든 9줄의 예(수순, AI 기준 점수차)


def latest_run() -> Path:
    runs = sorted(path.parent for path in RUNS.glob("*/samples.jsonl"))
    if not runs:
        raise SystemExit("E10 결과가 없다 — 먼저 run.py를 돌린다")
    return runs[-1]


def sides(trace: dict):
    """(판 넓이, 그 진영이 상대 착수 직후에 본 형세 [(수순 길이, 내 기준 점수차)], 그 진영의 최종 점수차, 판의 수순 길이) — 판마다 두 진영."""
    area = trace["size"] ** 2
    for me in "BW":
        readings = [(index + 1, -trace["lead"][index]) for index, color in enumerate(trace["colors"]) if color not in (me, "-")]
        final = trace["finalBlackLead"] if me == "B" else -trace["finalBlackLead"]
        yield area, readings, final, len(trace["colors"])


def fit(points: list[tuple[int, float]]) -> tuple[float, float]:
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    mx, my = mean(xs), mean(ys)
    slope = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum((x - mx) ** 2 for x in xs)
    return slope, my - slope * mx


def bar_share(move_count: int, area: int, middle: float, late: float) -> float | None:
    """그 수순에서의 문턱(판 넓이 대비). 초반·종반에는 없다."""
    share = move_count / area
    if share < MIDDLE or share >= ENDGAME:
        return None
    return middle if share < LATE else late


def persistent(middle: float, late: float, decide: str = "streak"):
    """문턱을 넘은 값부터 WINDOW회가 **모두 제 국면의 문턱 밖**일 때 — decide: streak(그것만) · line(직선이 80% 지점에서 0 미만일 때만)."""

    def fires_at(readings: list[tuple[int, float]], area: int) -> int | None:
        run = 0
        for index, (move_count, lead) in enumerate(readings):
            bar = bar_share(move_count, area, middle, late)
            if bar is None:
                run = 0
                continue
            run = run + 1 if lead <= -bar * area else 0
            if run >= WINDOW:
                if decide == "streak":
                    return index
                slope, intercept = fit(readings[index - WINDOW + 1 : index + 1])
                if slope * ENDGAME * area + intercept < 0:
                    return index
        return None

    return fires_at


def sliding_line(entry: float):
    """사용자 안을 글자 그대로: 지금 값이 문턱 밖이면 직전 WINDOW회(중반 이후)에 직선을 긋고, 80% 지점의 예측이 0 미만이면 기권."""

    def fires_at(readings: list[tuple[int, float]], area: int) -> int | None:
        for index in range(WINDOW - 1, len(readings)):
            window = readings[index - WINDOW + 1 : index + 1]
            if window[0][0] < MIDDLE * area or window[-1][0] >= ENDGAME * area or window[-1][1] > -entry * area:
                continue
            slope, intercept = fit(window)
            if slope * ENDGAME * area + intercept < 0:
                return index
        return None

    return fires_at


RULES = (
    ("앱: 중반 15% · 후반 10% · 5회 연속", persistent(ap.RESIGN_MIDDLE_DEFICIT_SHARE, ap.RESIGN_LATE_DEFICIT_SHARE)),
    ("같은 문턱 + 직선이 80% 지점에서 0 미만일 때만", persistent(ap.RESIGN_MIDDLE_DEFICIT_SHARE, ap.RESIGN_LATE_DEFICIT_SHARE, "line")),
    ("10% 하나 · 5회 연속", persistent(0.10, 0.10)),
    ("10% 하나 + 직선(사용자 안 — 예시처럼 문턱을 넘은 값부터 5회)", persistent(0.10, 0.10, "line")),
    ("사용자 안을 글자 그대로(지금 값만 10% 밖 · 직전 5회에 직선)", sliding_line(0.10)),
    ("중반 20% · 후반 10% · 5회 연속", persistent(0.20, 0.10)),
    ("중반 15% · 후반 15% · 5회 연속", persistent(0.15, 0.15)),
)


def tally(fired: list[tuple[float, int]], lost_big: int) -> dict:
    finals = [final for final, _ in fired]
    return {
        "fired": len(fired),
        "wrong": sum(1 for final in finals if final > 0),
        "close": sum(1 for final in finals if -10 < final <= 0),
        "coverage": sum(1 for final in finals if final <= -20) / lost_big if lost_big else None,
        "movesLeft": median(left for _, left in fired) if fired else None,
    }


def main() -> int:
    run = latest_run()
    traces = [trace for trace in read_jsonl(run / "samples.jsonl") if "colors" in trace and trace["finalBlackLead"] is not None]
    sizes = sorted({trace["size"] for trace in traces})
    summary: dict = {"run": run.name, "games": len(traces), "window": WINDOW, "rules": {}, "comeback": {}, "forecast": {}}
    lines = [
        "# E10 보조 2 — 국면과 상대 착수 뒤 형세로 기권을 고른다",
        "",
        f"- 원자료: `{run.name}/samples.jsonl`(기보 {len(traces)}판 · 진영 표본 {len(traces) * 2}, 사람 모델 `rank_9d` 평가 1회). 엔진을 다시 띄우지 않았다.",
        f"- 국면 = 수순 길이 ÷ 판의 자리 수: 초반 < {MIDDLE:.0%} · 중반 ~{LATE:.0%} · 후반 ~{ENDGAME:.0%} · 종반. 형세 = 그 진영이 **상대 착수 직후**에 본 점수차.",
        "- **잘못** = 걸렸는데 그 진영이 끝내 이긴 판 · **아슬** = 10집 안쪽으로 진 판 · **덮음** = 20집 넘게 진 판 가운데 걸린 비율 · **아낀 수** = 걸린 뒤 그 진영이 더 둔 수(중앙값).",
        "",
        "## 1. 뒤진 진영이 끝내 이긴 비율 — 직전 5회 평균이 뒤진 정도(판 넓이 대비) × 국면",
        "",
        "진영·국면·구간마다 한 번씩만 센다.",
        "",
    ]
    buckets = ((0.05, 0.10), (0.10, 0.15), (0.15, 0.20), (0.20, 0.30), (0.30, 9.0))
    phases = (("중반", MIDDLE, LATE), ("후반", LATE, ENDGAME), ("종반", ENDGAME, 9.0))
    lines += ["| 국면 | " + " | ".join(f"{low:.0%}~{'' if high > 1 else f'{high:.0%}'}" for low, high in buckets) + " |", "| --- |" + " ---: |" * len(buckets)]
    for name, start, end in phases:
        cells = []
        for low, high in buckets:
            seen: dict[tuple[int, int], float] = {}
            for game, trace in enumerate(traces):
                for side, (area, readings, final, _) in enumerate(sides(trace)):
                    for index in range(WINDOW - 1, len(readings)):
                        if not start <= readings[index][0] / area < end:
                            continue
                        average = mean(lead for _, lead in readings[index - WINDOW + 1 : index + 1]) / area
                        if -high < average <= -low:
                            seen[(game, side)] = final
            won = sum(1 for final in seen.values() if final > 0)
            summary["comeback"].setdefault(name, {})[f"{low:.2f}"] = {"sides": len(seen), "won": won}
            cells.append(f"{won}/{len(seen)} ({won / len(seen):.0%})" if seen else "–")
        lines.append(f"| {name} | " + " | ".join(cells) + " |")

    lines += ["", "## 2. 직선으로 종반의 형세를 내다볼 수 있나", "", "중반에 문턱(10%) 밖인 5회에 직선을 그어 80% 지점의 값을 내다본 것과, 「지금 5회 평균 그대로」라고 본 것의 오차(그 판이 80%까지 간 진영만).", "",
              "| 판 | 창 | 직선의 오차(중앙값) | 평균 그대로의 오차(중앙값) | 직선이 30집 넘게 틀림 | 평균이 30집 넘게 틀림 |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for size in sizes:
        line_errors, flat_errors = [], []
        for trace in traces:
            if trace["size"] != size:
                continue
            for area, readings, _, _ in sides(trace):
                around_endgame = [lead for move_count, lead in readings if 0.76 * area <= move_count <= 0.84 * area]
                if len(around_endgame) < 2:
                    continue
                actual = mean(around_endgame)
                for index in range(WINDOW - 1, len(readings)):
                    window = readings[index - WINDOW + 1 : index + 1]
                    if window[0][0] < MIDDLE * area or window[-1][0] >= LATE * area or window[-1][1] > -0.10 * area:
                        continue
                    slope, intercept = fit(window)
                    line_errors.append(abs(slope * ENDGAME * area + intercept - actual))
                    flat_errors.append(abs(mean(lead for _, lead in window) - actual))
        over = lambda errors: sum(1 for error in errors if error > 30) / len(errors)  # noqa: E731
        summary["forecast"][str(size)] = {"windows": len(line_errors), "line": median(line_errors), "flat": median(flat_errors)}
        lines.append(f"| {size}줄 | {len(line_errors)} | {median(line_errors):.1f}집 | {median(flat_errors):.1f}집 | {over(line_errors):.0%} | {over(flat_errors):.0%} |")
    slope, intercept = fit(USER_EXAMPLE)
    example_at_endgame = slope * 65 + intercept
    summary["userExample"] = {"slope": slope, "mean": mean(lead for _, lead in USER_EXAMPLE), "atMove65": example_at_endgame}
    lines += ["", f"사용자가 든 예(9줄 · 25수 −30 · 27수 −25 · 29수 −15 · 31수 −18 · 33수 −19): 기울기 {slope:+.2f}집/수 · 평균 {mean(lead for _, lead in USER_EXAMPLE):.1f}집 → "
              f"65수째(9줄의 80%) 예측 **{example_at_endgame:+.1f}집**."]

    lines += ["", "## 3. 규칙 비교 — 중반·후반에서 처음 걸린 판", ""]
    for scope in ["all", *sizes]:
        scoped = [trace for trace in traces if scope == "all" or trace["size"] == scope]
        lost_big = sum(1 for trace in scoped for _, _, final, _ in sides(trace) if final <= -20)
        lines += [f"### {'전체' if scope == 'all' else f'{scope}줄'} — 진영 표본 {len(scoped) * 2}(20집 넘게 진 진영 {lost_big})", "", "| 규칙 | 걸린 판 | 잘못 | 아슬 | 덮음 | 아낀 수 |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
        for name, fires_at in RULES:
            fired = []
            for trace in scoped:
                for area, readings, final, length in sides(trace):
                    index = fires_at(readings, area)
                    if index is not None:
                        fired.append((final, (length - readings[index][0]) // 2))
            result = tally(fired, lost_big)
            summary["rules"].setdefault(str(scope), {})[name] = result
            pct = lambda n: "–" if not result["fired"] else f"{n / result['fired'] * 100:.1f}% ({n})"  # noqa: E731
            coverage = "–" if result["coverage"] is None else f"{result['coverage']:.0%}"
            left = "–" if result["movesLeft"] is None else f"{result['movesLeft']:.0f}"
            lines.append(f"| {name} | {result['fired']} | {pct(result['wrong'])} | {pct(result['close'])} | {coverage} | {left} |")
        lines.append("")
    (run / "phases.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    markdown = "\n".join(lines)
    (run / "phases.md").write_text(markdown + "\n", encoding="utf-8")
    print(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
