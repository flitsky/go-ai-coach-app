#!/usr/bin/env python3
"""E13 — 고전 사활의 **원전 정답 수순**을 국면에 맞추고 엔진으로 확인한다(백로그 #277, 사용자 2026-10-21 「고전 정답 활용 싣기」).

원전(공유 저작물 영인본, 국립국회도서관 디지털)의 정답 수순은 판 전체 좌표(예: 기경중묘 부록 「十八ノ二」)로 적혀 있고, 국면은 공개 전사본
(tsumego.tasuki.org — 왼쪽 위로 옮기고 흑 먼저로 맞춤)에서 온다. 둘을 잇는 데 두 가지를 모른다 — 대칭(판을 어떻게 돌리고 뒤집었나)과
번호(전사본은 같은 쪽 안에서 번호를 다르게 매긴 곳이 있다). 그래서:

1. 원전 번호 ±4 안의 국면마다, 대칭 여덟 가지마다 수순을 두어 본다 — 모두 합법이고 그림 안에 놓이는 것만 남는다.
2. 남은 것마다 엔진(주 모델)으로 **수순 끝**(상대가 판 어디에든 받는다)과 **처음에 흑이 손을 뺀 국면**을 재고, 수순에 닿은 목표 색 무리 가운데
   가장 큰 것의 영역 값을 견준다 — 수순 끝에서는 목표를 이루고(+0.5 이상), 손을 빼면 못 이뤄야(0 이하, 차 0.75 이상) 한다. 그런 것이 하나일 때만 받는다.
3. 받은 문제마다 정답의 길 위 흑 차례에서, 그림 안의 다른 빈 점마다 **엔진의 반박**(백이 가장 좋게 받는 수)을 구해 둔다 — 앱의 판정 표가
   「원전의 수는 정답, 다른 수는 오답 + 이 반박」을 싣는다.

    python3 engine-lab/experiments/e13_classic_answers/run.py --label mac-v1 --source <전사본 폴더> --answers <원전 해답 JSON>

⚠️ 이 실험의 표본(`samples.jsonl`)에는 국면을 적지 않는다 — 전사본을 저장소에 싣지 않는다(E12 README). 앱이 읽는 결과는 `app-android/build/`에 쓴다.
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
from lab.board import Board  # noqa: E402
from lab.runs import RunDir  # noqa: E402

EXPERIMENT = "e13_classic_answers"
COLUMNS = "ABCDEFGHJKLMNOPQRST"
SIZE = 19
OUT = paths.REPO_ROOT / "app-android" / "build" / "life-and-death-classic-answers"
REACHES_GOAL = 0.5
# 손을 뺀 국면은 빈 19줄 판이라 영역 값이 흐려진다(죽은 무리도 −0.2 안팎, E12와 같은 까닭) — 부호와 차로 본다.
FAILS_WITHOUT = 0.0
FLIP = 0.75
SYMMETRIES = [
    lambda r, c: (r, c), lambda r, c: (c, r), lambda r, c: (r, 18 - c), lambda r, c: (18 - c, r),
    lambda r, c: (18 - r, c), lambda r, c: (c, 18 - r), lambda r, c: (18 - r, 18 - c), lambda r, c: (18 - c, 18 - r),
]


def vertex(r: int, c: int) -> str:
    return f"{COLUMNS[c]}{SIZE - r}"


def parse_transcript(path: Path) -> dict[str, list[str]]:
    out, current = {}, None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if not line or line.startswith("#") or line.startswith("title "):
            continue
        if line.startswith("problem "):
            current = line.split(" ", 1)[1].strip()
            out[current] = []
        else:
            out[current].append(line)
    return out


def start_board(rows: list[str]) -> Board:
    board = Board(SIZE)
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            if ch in "xb":
                board.grid[r][c] = "B"
            elif ch in "ow":
                board.grid[r][c] = "W"
    return board


def fits(rows: list[str], moves: list[list[int]]) -> list[tuple[int, list[tuple[int, int]]]]:
    # 전사본 그림의 바깥 두 줄까지 받는다 — 그림이 좁게 잘려 원전의 수가 밖으로 나간 문제가 있었다(기경중묘 사의 11 · 15, 2026-10-21).
    h, w = min(len(rows) + 2, SIZE), min(max(len(r) for r in rows) + 2, SIZE)
    found = []
    for k, sym in enumerate(SYMMETRIES):
        board, color, points = start_board(rows), "B", []
        for a, b in moves:
            r, c = sym(a - 1, b - 1)
            if not (0 <= r < h and 0 <= c < w):
                break
            nxt = board.try_play(color, (r, c))
            if nxt is None:
                break
            board, color = nxt, ("W" if color == "B" else "B")
            points.append((r, c))
        else:
            found.append((k, points))
    return found


def region(rows: list[str], board: Board) -> list[tuple[int, int]]:
    """문제 구역 — 그림 안의 빈 점 가운데 처음 돌에서 두 칸(체비쇼프) 안. 앱의 그림에서 `.`이고, 그 밖은 `,`(손 뺌)다."""
    h, w = len(rows), max(len(r) for r in rows)
    first = start_board(rows)
    stones = [(r, c) for r in range(h) for c in range(w) if first.grid[r][c]]
    return [(r, c) for r in range(h) for c in range(w)
            if board.grid[r][c] is None and any(max(abs(r - a), abs(c - b)) <= 2 for a, b in stones)]


def chains(board: Board, color: str, box: tuple[int, int]) -> list[set[tuple[int, int]]]:
    seen, out = set(), []
    for r in range(box[0]):
        for c in range(box[1]):
            if board.grid[r][c] != color or (r, c) in seen:
                continue
            group, _ = board.group((r, c))
            seen |= group
            out.append(group)
    return out


def query(rows: list[str], moves: list[tuple[str, str]], first: str, visits: int, allow: list[str] | None = None) -> dict:
    board = start_board(rows)
    stones = [[board.grid[r][c], vertex(r, c)] for r in range(SIZE) for c in range(SIZE) if board.grid[r][c]]
    q = {"initialStones": stones, "initialPlayer": first, "moves": [list(m) for m in moves], "rules": "japanese", "komi": 6.5,
         "boardXSize": SIZE, "boardYSize": SIZE, "maxVisits": visits, "includeOwnership": True}
    if allow is not None:
        player = first if not moves else ("W" if moves[-1][0] == "B" else "B")
        q["allowMoves"] = [{"player": player, "moves": allow, "untilDepth": 1}]
    return q


def own(response: dict, group: set[tuple[int, int]]) -> float:
    grid = response["ownership"]
    return sum(grid[r * SIZE + c] for r, c in group) / len(group)


def judge_fit(engine, rows, goal, points, visits):
    """수순 끝과 처음 손 뺌 — 목표 색 무리 가운데 **둘 사이에서 영역 값이 가장 크게 뒤집히는 것**이 목표다(E12와 같은 잣대).
    「수순에 닿은 무리 중 가장 큰 것」으로 고르면 잡기 문제에서 살아 있는 바깥 벽을 집었다(끝 값 −0.9 — 2026-10-21 첫 실행)."""
    h, w = len(rows), max(len(r) for r in rows)
    board = start_board(rows)
    target_color = "B" if goal == "Live" else "W"
    touched = {(r + dr, c + dc) for r, c in points for dr in (-1, 0, 1) for dc in (-1, 0, 1)}
    candidates = [g for g in chains(board, target_color, (h, w)) if g & touched]
    if not candidates:
        return None
    colors = ["B" if i % 2 == 0 else "W" for i in range(len(points))]
    moves = [(col, vertex(r, c)) for col, (r, c) in zip(colors, points)]
    inside = [vertex(r, c) for r in range(h) for c in range(w) if board.grid[r][c] is None]
    end, tenuki = engine.query_many([query(rows, moves, "B", visits), query(rows, [], "W", visits, allow=inside)])
    # 흑 기준 영역 값 — 살기는 흑 무리가 흑의 것(+), 잡기는 백 무리가 죽어 흑의 것(+).
    scored = [(own(end, g) - own(tenuki, g), own(end, g), own(tenuki, g), g) for g in candidates]
    flip, end_own, tenuki_own, target = max(scored, key=lambda t: (t[0], len(t[3])))
    return {"target": vertex(*min(target)), "targetSize": len(target), "end": round(end_own, 3), "tenuki": round(tenuki_own, 3),
            "ok": end_own >= REACHES_GOAL and tenuki_own <= FAILS_WITHOUT and flip >= FLIP, "moves": moves}


def refutations(engine, rows, moves, visits):
    """정답의 길 위 흑 차례마다, 문제 구역의 다른 빈 점에 흑이 두었을 때 백의 가장 좋은 응수(구역 안으로 묶는다). 손 뺌도 하나."""
    out = []
    for turn in range(0, len(moves), 2):
        prefix = moves[:turn]
        b = start_board(rows)
        for col, v in prefix:
            b = b.try_play(col, (SIZE - int(v[1:]), COLUMNS.index(v[0])))
        inside = [vertex(r, c) for r, c in region(rows, b)]
        wrong = [v for v in inside if v != moves[turn][1] and b.try_play("B", (SIZE - int(v[1:]), COLUMNS.index(v[0]))) is not None]
        queries = [query(rows, prefix + [("B", v)], "B", visits, allow=inside) for v in wrong]
        queries.append(query(rows, prefix + [("B", "pass")], "B", visits, allow=inside))
        replies = engine.query_many(queries)
        entry = {}
        for v, res in zip(wrong + ["tenuki"], replies):
            info = res.get("moveInfos") or []
            entry[v] = info[0]["move"] if info and info[0]["move"] != "pass" else None
        out.append(entry)
    return out


MIN_LINE = 5
# 전사본 전체에서 찾는 책(발양론)은 후보가 수십 배라, 7수짜리도 열 곳 넘게 맞았다 — 더 긴 수순만 믿는다.
MIN_LINE_WHEN_SEARCHING_ALL = 9


def settle(accepted: list[dict]) -> list[dict]:
    """마지막 거르기 — 짧은 수순(3수)은 여러 국면에 맞아 증거가 약하고(기경중묘 1-6이 1-4의 국면에 맞았다), 두 해답이 같은 국면을 가리키면
    적어도 하나는 틀렸다. 그래서 수순이 [MIN_LINE]수 이상인 것만, 같은 국면을 다투면 더 긴 수순 하나만(길이가 같으면 둘 다 뺀다) 남긴다."""
    long_enough = [a for a in accepted if len(a["line"]) >= (MIN_LINE_WHEN_SEARCHING_ALL if a.get("searchAll") else MIN_LINE)]
    kept = []
    for a in long_enough:
        rivals = [b for b in long_enough if b is not a and b["book"] == a["book"] and b["transcript"] == a["transcript"]]
        if all(len(a["line"]) > len(b["line"]) for b in rivals):
            kept.append(a)
    return kept


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--label")
    parser.add_argument("--source", type=Path, help="전사본 `books/problems/`를 받은 폴더")
    parser.add_argument("--answers", type=Path, help="원전 해답 JSON — {책: {원전 번호: {goal, main: [[a, b], …]}}}")
    parser.add_argument("--visits", type=int, default=400)
    parser.add_argument("--refutation-visits", type=int, default=48, help="반박은 「왜 틀렸나」를 보이는 한 수 — 깊이 읽지 않는다")
    parser.add_argument("--window", type=int, default=4, help="원전 번호에서 앞뒤 몇 문제까지 국면을 찾나")
    parser.add_argument("--settle-only", action="store_true", help="엔진을 돌리지 않고 이미 쓴 결과에 마지막 거르기(`settle`)만 다시 건다")
    args = parser.parse_args()
    if args.settle_only:
        data = json.loads((OUT / "answers.json").read_text(encoding="utf-8"))
        before = len(data["problems"])
        data["problems"] = settle(data["problems"])
        (OUT / "answers.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"settled {before} → {len(data['problems'])}")
        return 0
    if not (args.label and args.source and args.answers):
        parser.error("--label, --source, --answers가 있어야 한다(--settle-only가 아니면)")

    answers = json.loads(args.answers.read_text(encoding="utf-8"))
    recorded = {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()}
    run = RunDir(EXPERIMENT, args.label, recorded)
    engine = local_analysis(
        katago=paths.katago_binary(), model=paths.main_model(), config=paths.analysis_config(),
        overrides={"numAnalysisThreads": "4", "numSearchThreads": "4", "logToStderr": "false",
                   "logAllRequests": "false", "logAllResponses": "false", "logSearchInfo": "false"},
        label="engine",
    )
    run.set_engine_versions({"analysis": engine.version})
    OUT.mkdir(parents=True, exist_ok=True)
    accepted = []
    started = time.time()
    try:
        for book, entries in answers.items():
            transcript = parse_transcript(args.source / f"{book}.txt")
            for book_id, entry in entries.items():
                chapter, number = (book_id.split("-") + [None])[:2] if "-" in book_id else (None, book_id)
                if entry.get("search") == "all":
                    # 원전과 전사본의 번호가 따로 노는 책(발양론 1914년판은 부마다 번호를 새로 매긴다) — 전사본 전체에서 찾는다.
                    keys = list(transcript)
                elif chapter is None:
                    keys = [str(n) for n in range(max(1, int(number) - args.window), int(number) + args.window + 1)]
                else:
                    keys = [f"{chapter}-{n}" for n in range(max(1, int(number) - args.window), int(number) + args.window + 1)]
                tried = []
                for key in keys:
                    if key not in transcript:
                        continue
                    for sym, points in fits(transcript[key], entry["main"]):
                        verdict = judge_fit(engine, transcript[key], entry["goal"], points, args.visits)
                        if verdict:
                            tried.append({"transcript": key, "symmetry": sym, **verdict})
                good = [t for t in tried if t["ok"]]
                row = {"book": book, "bookId": book_id, "goal": entry["goal"], "fits": len(tried),
                       "accepted": None, "tried": [{k: v for k, v in t.items() if k != "moves"} for t in tried]}
                if len(good) == 1:
                    pick = good[0]
                    rows = transcript[pick["transcript"]]
                    refute = refutations(engine, rows, pick["moves"], args.refutation_visits)
                    row["accepted"] = {k: v for k, v in pick.items() if k != "moves"}
                    accepted.append({"book": book, "bookId": book_id, "goal": entry["goal"], "transcript": pick["transcript"],
                                     "target": pick["target"], "line": [v for _, v in pick["moves"]], "rows": rows,
                                     "region": [vertex(r, c) for r, c in region(rows, start_board(rows))],
                                     "refutations": refute, "end": pick["end"], "tenuki": pick["tenuki"],
                                     "searchAll": entry.get("search") == "all"})
                run.sample(row)
                print(f"  {book} {book_id}: fits {len(tried)}, ok {len(good)}" + (f" → {good[0]['transcript']}" if len(good) == 1 else ""),
                      file=sys.stderr)
    finally:
        engine.close()
    settled = settle(accepted)
    (OUT / "answers.json").write_text(json.dumps({"run": run.path.name, "problems": settled}, ensure_ascii=False, indent=1), encoding="utf-8")
    run.finish({"answers": sum(len(v) for v in answers.values()), "accepted": len(accepted), "settled": len(settled), "seconds": round(time.time() - started)},
               f"# E13 — 고전 원전 해답 맞추기\n\n해답 {sum(len(v) for v in answers.values())} 중 엔진이 받은 것 {len(accepted)}, 거른 뒤 {len(settled)}.\n")
    print(run.path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
