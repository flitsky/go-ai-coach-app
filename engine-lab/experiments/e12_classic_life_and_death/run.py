#!/usr/bin/env python3
"""E12 — 고전 사활집의 문제가 **무엇을 묻는지** 엔진으로 찾는다(백로그 #277 「고전 사활」).

고전(현현기경 1349 · 발양론 1713 · 기경중묘 1812 — 공유 저작물)의 공개 전사본은 **국면만** 있다. 흑을 먼저 두게 맞춰 놓았지만
「잡아라 / 살아라」도 정답도 없다. 앱의 풀이기는 목표 무리 하나를 알아야 읽을 수 있으므로, 여기서 먼저 묻는다:

- 19줄 판 **그대로**(나머지는 빈 판) 흑이 먼저 둘 때와 백이 먼저 둘 때(흑이 손을 뺌) 각각 탐색하고(첫 수는 문제 그림 안으로 묶는다),
  무리마다 돌의 평균 영역 값(흑 기준)을 견준다. **누가 먼저 두느냐로 생사가 뒤집히는 무리**가 그 문제의 목표다 —
  백 무리면 잡기, 흑 무리면 살기. 뒤집힘이 뚜렷하지 않으면(패 · 빅 · 엔진이 못 읽음) 그 문제는 건너뛴다.
- 엔진은 **목표와 첫 수의 짐작**만 준다 — 정답은 그 뒤 앱의 풀이기가 그 무리 둘레를 끝까지 읽어 정한다(증명). 풀이기의 답이 판 전체에서도
  맞는지는 E11의 셋째 검사(정답 뒤 백이 판 어디에든 받게 한다)가 다시 본다.

전사본: tsumego.tasuki.org(Vít Brunner)의 `books/problems/*.txt`(github.com/tasuki/tsumego) — 라이선스 표기가 없어 **저장소에 싣지 않는다.**
받는 법은 README에 있다. 앱에는 고른 문제의 국면만 원전의 이름 · 번호와 함께 싣는다.

    python3 engine-lab/experiments/e12_classic_life_and_death/run.py --label mac-v1 --source <받은 폴더>
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from lab import paths  # noqa: E402
from lab.analysis import local_analysis  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e12_classic_life_and_death"
BOOKS = ("xxqj", "hatsuyoron", "gokyoshumyo")
COLUMNS = "ABCDEFGHJKLMNOPQRST"
SIZE = 19
FLIP = 0.8  # 흑 먼저 − 백 먼저의 영역 값 차가 이만큼은 돼야 「뒤집힌다」
# 흑 먼저면 +0.3 이상, 백 먼저면 −0.3 이하 — 빈 19줄 판에서는 급소에 둔 뒤 다른 곳으로 손을 빼 영역 값이 흐려진다(죽은 무리도 +0.5 안팎).
# 패 · 빅인 문제는 다음 단계에서 풀이기가 걸러 낸다(조건 없는 생사만).
SETTLED = 0.3


def parse(path: Path) -> list[dict]:
    problems, current = [], None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("problem "):
            current = {"book": path.stem, "id": line.split(" ", 1)[1].strip(), "rows": []}
            problems.append(current)
        elif line.startswith("title "):
            current["title"] = line[6:]
        else:
            current["rows"].append(line)
    return problems


def vertex(row: int, column: int) -> str:
    return f"{COLUMNS[column]}{SIZE - row}"


def stones(problem: dict) -> dict[tuple[int, int], str]:
    out = {}
    for r, row in enumerate(problem["rows"]):
        for c, ch in enumerate(row):
            if ch in "xb":
                out[(r, c)] = "B"
            elif ch in "ow":
                out[(r, c)] = "W"
    return out


def box(problem: dict) -> tuple[int, int]:
    """그림의 마지막 줄 · 칸은 「앞뒤를 보여 주는 빈 줄」이다(전사본 README) — 19줄을 다 쓴 그림이면 그 줄이 판의 끝이다."""
    h = len(problem["rows"])
    w = max(len(r) for r in problem["rows"])
    return (SIZE - 1 if h >= SIZE else h - 2), (SIZE - 1 if w >= SIZE else w - 2)


def chains(placed: dict[tuple[int, int], str]) -> list[set[tuple[int, int]]]:
    seen, out = set(), []
    for start in sorted(placed):
        if start in seen:
            continue
        color, group, pending = placed[start], {start}, [start]
        while pending:
            r, c = pending.pop()
            for nb in ((r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)):
                if nb not in group and placed.get(nb) == color:
                    group.add(nb)
                    pending.append(nb)
        seen |= group
        out.append(group)
    return out


def query(problem: dict, first: str, visits: int) -> dict:
    placed = stones(problem)
    r_max, c_max = box(problem)
    inside = [vertex(r, c) for r in range(r_max + 1) for c in range(c_max + 1) if (r, c) not in placed]
    return {
        "initialStones": [[color, vertex(r, c)] for (r, c), color in sorted(placed.items())],
        "initialPlayer": first,
        "moves": [],
        "rules": "japanese",
        "komi": 6.5,
        "boardXSize": SIZE,
        "boardYSize": SIZE,
        "maxVisits": visits,
        "includeOwnership": True,
        "allowMoves": [{"player": first, "moves": inside, "untilDepth": 1}],
    }


def judge(problem: dict, black_first: dict, white_first: dict) -> dict:
    placed = stones(problem)

    def own(response: dict, group: set[tuple[int, int]]) -> float:
        grid = response["ownership"]
        return sum(grid[r * SIZE + c] for r, c in group) / len(group)

    best = None
    for group in chains(placed):
        a, b = own(black_first, group), own(white_first, group)
        flip = a - b
        if a >= SETTLED and b <= -SETTLED and flip >= FLIP and (best is None or (len(group), flip) > (len(best[0]), best[3])):
            best = (group, a, b, flip)
    top = black_first["moveInfos"][0] if black_first.get("moveInfos") else {}
    if best is None:
        return {"goal": None, "top": top.get("move"), "pv": top.get("pv", [])[:8]}
    group, a, b, flip = best
    anchor = min(group)
    return {
        "goal": "Kill" if placed[anchor] == "W" else "Live",
        "target": vertex(*anchor),
        "targetSize": len(group),
        "blackFirst": round(a, 3),
        "whiteFirst": round(b, 3),
        "flip": round(flip, 3),
        "top": top.get("move"),
        "pv": top.get("pv", [])[:8],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label", required=True)
    parser.add_argument("--source", type=Path, required=True, help="전사본 `books/problems/`를 받은 폴더")
    parser.add_argument("--per-book", type=int, default=80, help="책마다 그림이 작은 것부터 몇 문제를 볼지")
    parser.add_argument("--visits", type=int, default=400, help="목표의 짐작일 뿐이라 적게 — 증명은 풀이기, 마지막 확인은 E11(800방문)")
    args = parser.parse_args()

    problems = []
    for book in BOOKS:
        found = parse(args.source / f"{book}.txt")
        found.sort(key=lambda p: len(p["rows"]) * max(len(r) for r in p["rows"]))
        problems += found[: args.per_book]
    recorded = {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()}
    run = RunDir(EXPERIMENT, args.label, recorded)
    print(f"→ {run.path} ({len(problems)} problems)", file=sys.stderr)
    engine = local_analysis(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.analysis_config(),
        overrides={"numAnalysisThreads": "2", "numSearchThreads": "8", "logToStderr": "false",
                   "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"},
        label="engine",
    )
    run.set_engine_versions({"analysis": engine.version})
    started = time.time()
    found = 0
    try:
        for index, problem in enumerate(problems, start=1):
            black_first, white_first = engine.query_many([query(problem, "B", args.visits), query(problem, "W", args.visits)])
            # 국면(`rows`)은 적지 않는다 — 전사본을 저장소에 싣지 않는다(README). 읽는 쪽은 `run.json`의 `source`에서 다시 읽는다.
            row = {"book": problem["book"], "id": problem["id"], **judge(problem, black_first, white_first)}
            found += row["goal"] is not None
            run.sample(row)
            if index % 20 == 0:
                print(f"  {index}/{len(problems)} — goal found {found} ({time.time() - started:.0f}s)", file=sys.stderr)
    finally:
        engine.close()
    run.finish({"problems": len(problems), "goalFound": found, "seconds": round(time.time() - started)},
               f"# E12 — 고전 사활의 목표 찾기\n\n{len(problems)}문제 중 목표가 뚜렷한 것 {found}.\n")
    print(run.path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
