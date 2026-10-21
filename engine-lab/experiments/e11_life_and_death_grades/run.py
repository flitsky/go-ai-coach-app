#!/usr/bin/env python3
"""E11 — 사활 문제의 난이도와 생사를 엔진으로 다시 본다(백로그 #277 「중·고급 사활」).

문제와 정답은 앱이 정한다 — 앱의 풀이기(`LifeAndDeathSolver`)가 문제 구역을 흑백 모두 끝까지 읽은 결과(증명)를
`LifeAndDeathCandidateExportTest`가 내보낸다. 여기서는 그것을 **엔진 두 가지로** 다시 잰다:

1. **난이도 — 사람 모델**: 프로필마다(`rank_15k` … `rank_3d`) 처음 국면의 사람 정책을 한 번 평가하고(`kata-raw-human-nn 0`),
   문제 구역의 빈 점으로만 다시 나눠 **정답 첫 수에 걸린 확률**을 잰다 — 「그 급수의 사람이 첫눈에 그 수를 둘 확률」.
   읽기(수읽기)는 재지 않는다 — 첫 수의 직관만이다. 그래서 숫자 자체보다 **기본 사활(실은 문제)과의 차이**로 읽는다.
2. **생사 — 주 모델**: 흑이 먼저 둘 때와 흑이 손을 뺐을 때(백이 먼저) 각각 탐색하고(첫 수는 문제 구역 안으로 묶는다),
   목표 무리 돌의 영역 값이 풀이기의 판정과 같은 쪽인지 본다. 다르면 그 문제는 사람이 다시 본다(엔진이 틀렸을 수도, 판 밖 사정이 있을 수도).

    ./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathCandidateExportTest*' -PexportLifeAndDeathCandidates
    python3 engine-lab/experiments/e11_life_and_death_grades/run.py --label mac-v1
"""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
import time
from pathlib import Path
from statistics import median

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import paths  # noqa: E402
from lab.analysis import AnalysisEngine, local_analysis  # noqa: E402
from lab.gtp import local_gtp  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e11_life_and_death_grades"
CANDIDATES = paths.REPO_ROOT / "app-android" / "build" / "life-and-death-candidates" / "candidates.json"
PROFILES = ("rank_15k", "rank_10k", "rank_5k", "rank_1k", "rank_3d")
COLUMNS = "ABCDEFGHJKLMNOPQRST"
KOMI = 6.5
RULES = "japanese"  # 앱의 문제는 일본식으로 앉힌다(`LifeAndDeathProblem.startState`)
SETTLED = 0.5  # 목표 무리 돌의 평균 영역 값이 이만큼 기울면 그쪽으로 판정한 것으로 본다


def cells(problem: dict) -> list[list[str]]:
    return [row.split() for row in problem["diagram"]]


def vertex(size: int, row: int, column: int) -> str:
    return f"{COLUMNS[column]}{size - row}"


def parse_vertex(size: int, label: str) -> tuple[int, int]:
    return size - int(label[1:]), COLUMNS.index(label[0])


def stones(problem: dict) -> list[tuple[str, str]]:
    grid = cells(problem)
    size = len(grid)
    return [("B" if v == "X" else "W", vertex(size, r, c)) for r, row in enumerate(grid) for c, v in enumerate(row) if v in "XO"]


def region(problem: dict) -> list[str]:
    grid = cells(problem)
    size = len(grid)
    return [vertex(size, r, c) for r, row in enumerate(grid) for c, v in enumerate(row) if v == "."]


def target_group(problem: dict) -> list[tuple[int, int]]:
    grid = cells(problem)
    size = len(grid)
    start = parse_vertex(size, problem["target"])
    color = grid[start[0]][start[1]]
    seen, pending = {start}, [start]
    while pending:
        r, c = pending.pop()
        for nr, nc in ((r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)):
            if 0 <= nr < size and 0 <= nc < size and (nr, nc) not in seen and grid[nr][nc] == color:
                seen.add((nr, nc))
                pending.append((nr, nc))
    return sorted(seen)


def static_sgf(problem: dict) -> str:
    """흑 차례로 시작하는 배치만의 기보. SGF 좌표는 첫 글자가 열, 둘째가 행(a = 맨 위)."""
    grid = cells(problem)
    size = len(grid)

    def points(mark: str) -> str:
        return "".join(f"[{chr(97 + c)}{chr(97 + r)}]" for r, row in enumerate(grid) for c, v in enumerate(row) if v == mark)

    return f"(;GM[1]FF[4]SZ[{size}]KM[{KOMI}]RU[Japanese]AB{points('X')}AW{points('O')}PL[B])"


def human_grades(gtp, problem: dict) -> dict:
    """프로필마다: 문제 구역 안으로 다시 나눈 사람 정책에서 정답 첫 수들의 확률 · 가장 높은 수."""
    with tempfile.NamedTemporaryFile("w", suffix=".sgf", delete=False) as fh:
        fh.write(static_sgf(problem))
        sgf_path = fh.name
    gtp.send(f"loadsgf {sgf_path}")
    Path(sgf_path).unlink(missing_ok=True)
    inside = set(region(problem))
    correct = set(problem["correct"])
    out = {}
    for profile in PROFILES:
        gtp.set_param("humanSLProfile", profile)
        raw = gtp.raw_nn(len(problem["diagram"]), human=True)
        local = {m: p for m, p in raw.policy.items() if m in inside}
        total = sum(local.values()) or 1.0
        out[profile] = {
            "p": round(sum(p for m, p in local.items() if m in correct) / total, 4),
            "top": max(local, key=local.get) if local else None,
            "outside": round(1.0 - sum(local.values()), 4),  # 정책이 문제 밖(빈 판)에 둔 몫
        }
    return out


def engine_query(problem: dict, first: str, visits: int, *, moves: list[list[str]] | None = None, restrict: bool = True) -> dict:
    size = len(problem["diagram"])
    query = {
        "initialStones": [[color, point] for color, point in stones(problem)],
        "initialPlayer": first,
        "moves": moves or [],
        "rules": RULES,
        "komi": KOMI,
        "boardXSize": size,
        "boardYSize": size,
        "maxVisits": visits,
        "includeOwnership": True,
    }
    if restrict:
        # 첫 수는 문제 구역 안으로 — 판의 나머지가 비어 있어 큰 자리로 손을 빼는 것을 막는다.
        player = first if not moves else ("W" if moves[-1][0] == "B" else "B")
        query["allowMoves"] = [{"player": player, "moves": region(problem), "untilDepth": 1}]
    return query


def target_ownership(problem: dict, response: dict) -> float:
    """목표 무리 돌의 평균 영역 값 — 흑 기준(`reportAnalysisWinratesAs = BLACK`), 1이면 흑의 것."""
    size = len(problem["diagram"])
    own = response["ownership"]
    group = target_group(problem)
    return round(sum(own[r * size + c] for r, c in group) / len(group), 3)


def engine_check(engine: AnalysisEngine, problem: dict, visits: int) -> dict:
    """셋을 본다: 흑이 먼저 · 흑이 손을 뺌(백이 먼저) · **흑이 의도한 첫 수를 둔 뒤 백이 판 어디에든 받을 때**.
    셋째가 풀이기의 구역 가정을 판 전체에서 되묻는다 — 문제 밖(`,`)을 아무도 두지 않는다는 가정 없이도 그 수가 목표를 이루는가."""
    after = [["B", problem["answer"]]]
    black_first, white_first, after_answer = engine.query_many([
        engine_query(problem, "B", visits),
        engine_query(problem, "W", visits),
        engine_query(problem, "B", visits, moves=after, restrict=False),
    ])
    b, w, a = (target_ownership(problem, r) for r in (black_first, white_first, after_answer))
    # 흑이 이루면 목표 무리는 흑의 것이다 — 잡기는 백 무리가 죽어 흑의 집, 살기는 흑 무리가 산다. 손을 빼면 그 반대여야 한다.
    top = black_first["moveInfos"][0]["move"] if black_first.get("moveInfos") else None
    return {
        "blackFirst": b,
        "whiteFirst": w,
        "afterAnswer": a,
        "agrees": b >= SETTLED and w <= -SETTLED and a >= SETTLED,
        "top": top,
        "topCorrect": top in problem["correct"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--candidates", type=Path, default=CANDIDATES)
    parser.add_argument("--visits", type=int, default=800, help="주 모델 탐색 방문 수(흑 먼저 · 백 먼저 각각)")
    parser.add_argument("--limit", type=int, default=0, help="후보 앞의 N개만(빠른 확인용)")
    parser.add_argument("--only", type=Path, help="이 파일에 한 줄에 하나씩 적은 id만 잰다(둘째 단계 — 고른 후보만 엔진으로)")
    parser.add_argument("--no-engine", action="store_true", help="사람 모델만 잰다(첫 단계 — 후보 전부를 싸게 훑는다)")
    args = parser.parse_args()

    data = json.loads(args.candidates.read_text(encoding="utf-8"))
    problems = [("shipped", p) for p in data.get("shipped", [])]
    candidates = data["candidates"][: args.limit] if args.limit else data["candidates"]
    problems += [("candidate", p) for p in candidates]
    if args.only:
        wanted = {line.strip() for line in args.only.read_text(encoding="utf-8").splitlines() if line.strip()}
        problems = [(source, p) for source, p in problems if source == "shipped" or p["id"] in wanted]
    recorded = {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()}
    run = RunDir(EXPERIMENT, args.label, {**recorded, "seed": data.get("seed"), "tries": data.get("tries")})
    print(f"→ {run.path} ({len(problems)} problems)", file=sys.stderr)

    gtp = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.gtp_config(),
        overrides={"logToStderr": "false", "logSearchInfo": "false"},
        human_model=paths.human_model(),
        label="human",
    )
    engine = None if args.no_engine else local_analysis(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.analysis_config(),
        overrides={"numAnalysisThreads": "2", "numSearchThreads": "8", "logToStderr": "false",
                   "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"},
        label="engine",
    )
    run.set_engine_versions({"gtp": gtp.version, "analysis": engine.version if engine else None})
    started = time.time()
    rows = []
    try:
        for index, (source, problem) in enumerate(problems, start=1):
            row = {
                "source": source,
                "id": problem["id"],
                "unit": problem.get("unit"),
                "goal": problem["goal"],
                "region": problem["region"],
                "nodes": problem.get("nodes"),
                "answerLine": len(problem["answerLine"]),
                "correct": problem["correct"],
                "human": human_grades(gtp, problem),
                "engine": engine_check(engine, problem, args.visits) if engine else None,
            }
            run.sample(row)
            rows.append(row)
            if index % 25 == 0:
                print(f"  {index}/{len(problems)} ({time.time() - started:.0f}s)", file=sys.stderr)
    finally:
        gtp.close()
        if engine:
            engine.close()

    write_summary(run, rows, time.time() - started)
    print(run.path)
    return 0


def write_summary(run: RunDir, rows: list[dict], seconds: float) -> None:
    def med(source: str, profile: str) -> float | None:
        values = [r["human"][profile]["p"] for r in rows if r["source"] == source]
        return round(median(values), 3) if values else None

    lines = [
        f"# E11 — 사활 문제의 난이도 · 생사 ({len(rows)}문제, {seconds:.0f}초)",
        "",
        "## 정답 첫 수에 걸린 사람 정책 확률(문제 구역 안으로 다시 나눔) — 가운데 값",
        "",
        "| 묶음 | " + " | ".join(PROFILES) + " | 엔진 생사 일치 |",
        "|---|" + "---|" * (len(PROFILES) + 1),
    ]
    for source in ("shipped", "candidate"):
        subset = [r for r in rows if r["source"] == source]
        if not subset:
            continue
        agree = sum(1 for r in subset if r["engine"] and r["engine"]["agrees"])
        lines.append(f"| {source} ({len(subset)}) | " + " | ".join(str(med(source, p)) for p in PROFILES) + f" | {agree}/{len(subset)} |")
    lines += ["", "## 후보 — rank_5k 확률이 낮은 순(어려운 것부터)", "",
              "| id | 목표 | 구역 | 수순 | " + " | ".join(PROFILES) + " | 엔진 흑먼저/백먼저/정답 뒤 | 일치 |",
              "|---|---|---|---|" + "---|" * (len(PROFILES) + 2)]
    for r in sorted((r for r in rows if r["source"] == "candidate"), key=lambda r: r["human"]["rank_5k"]["p"]):
        lines.append(
            f"| {r['id']} | {r['goal']} | {r['region']} | {r['answerLine']} | "
            + " | ".join(f"{r['human'][p]['p']:.2f}" for p in PROFILES)
            + (f" | {r['engine']['blackFirst']:+.2f} / {r['engine']['whiteFirst']:+.2f} / {r['engine']['afterAnswer']:+.2f} | {'✓' if r['engine']['agrees'] else '✗'} |"
               if r["engine"] else " | — | — |")
        )
    run.finish({"problems": len(rows), "seconds": round(seconds)}, "\n".join(lines) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
