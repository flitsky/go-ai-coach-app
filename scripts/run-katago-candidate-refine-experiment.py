#!/usr/bin/env python3
"""Measure how much the app's "refinePolicyMoves" trick actually grows the
scored (pointLoss-bearing) candidate pool, and at what latency cost.

This is a standalone terminal experiment for the JsonPositionAnalysis path
(used by 초급/중급/고급). It reuses the exact same test positions as the
archived docs/archive/2026-06-docs-consolidation/ENGINE_BEGINNER_VISITS_BENCHMARK.md
(P0 empty board, P1 8-move opening, P2 20-move midgame) so results are directly
comparable to that historical baseline.

For each position x visits budget (16/32/64):
  1. Run one JSON analysis query with includePolicy=true to get the natural
     scored `moveInfos` count (baseline candidate coverage).
  2. Rank remaining legal points by policy prior (KataGo's policy net output),
     excluding points already scored or occupied.
  3. For refine budgets {0, 4, 8, 12} (mirrors AnalysisPreset.Learning/Balanced/
     Deep's refinePolicyMoves values), issue that many small follow-up queries
     (maxVisits=8, no policy, no further refine -- same fixed budget as
     KataGoJsonPositionAnalysisClient.JsonRefineLimit) that each append one
     candidate move and read back its rootInfo.scoreLead, mirroring
     KataGoJsonAnalysisQueryFactory.build(refineMove=...) and
     KataGoJsonAnalysisParser.parseRefinedCandidate in engine-android.

Output: a markdown table with scored-candidate-count-before/after and the
latency cost of getting there, so the refinePolicyMoves default for AI move
selection can be picked from measured data instead of guessing.

Caveats (documented, not hidden):
  - Single persistent KataGo analysis process is reused across all queries in
    a run for speed; NN-cache warm-up may make later queries on overlapping
    subtrees a little faster than an isolated cold process would be. This
    matches how the app actually behaves in a live session (no clear_cache
    between AI-move JSON queries), so it is a reasonable approximation, not a
    strict isolation benchmark like scripts/run-katago-level-match.py's
    --cache-isolation modes.
  - Occupied-point tracking for excluding illegal refine candidates does not
    replay captures. None of P0/P1/P2's opening moves produce a capture, so
    this is exact for these three positions but would need a real rules
    engine for arbitrary midgame positions.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

DEFAULT_KATAGO = "/opt/homebrew/bin/katago"
DEFAULT_MODEL = "/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
DEFAULT_CONFIG = "app-android/src/friend/assets/katago/analysis_learning.cfg"
LETTERS = "ABCDEFGHJ"  # matches scripts/run-katago-level-match.py: 9x9 skips "I"
BOARD_SIZE = 9
REFINE_QUERY_VISITS = 8  # mirrors KataGoJsonPositionAnalysisClient.JsonRefineLimit


@dataclass(frozen=True)
class Position:
    name: str
    moves: list[list[str]] = field(default_factory=list)


POSITIONS = [
    Position("P0 빈 9x9", []),
    Position(
        "P1 초반 8수",
        [
            ["B", "E5"], ["W", "C5"], ["B", "G6"], ["W", "F3"],
            ["B", "D4"], ["W", "C7"], ["B", "C4"], ["W", "G4"],
        ],
    ),
    Position(
        "P2 중반 20수",
        [
            ["B", "E5"], ["W", "C5"], ["B", "G6"], ["W", "F3"],
            ["B", "C6"], ["W", "D4"], ["B", "B6"], ["W", "G4"],
            ["B", "H5"], ["W", "F6"], ["B", "F7"], ["W", "F5"],
            ["B", "E7"], ["W", "B5"], ["B", "D5"], ["W", "E4"],
            ["B", "A5"], ["W", "G7"], ["B", "H7"], ["W", "G8"],
        ],
    ),
]


def coordinate_index(vertex: str) -> int:
    column = LETTERS.index(vertex[0])
    row = int(vertex[1:]) - 1
    return row * BOARD_SIZE + column


def index_to_vertex(index: int) -> str:
    row, column = divmod(index, BOARD_SIZE)
    return f"{LETTERS[column]}{row + 1}"


def occupied_vertices(moves: list[list[str]]) -> set[str]:
    # Approximation: tracks played points without replaying captures. Exact
    # for P0/P1/P2 (no captures occur in either opening sequence).
    return {move[1] for move in moves if move[1].lower() not in {"pass", "resign"}}


def next_player(moves: list[list[str]]) -> str:
    if not moves:
        return "B"
    return "W" if moves[-1][0] == "B" else "B"


def start_engine(args: argparse.Namespace) -> subprocess.Popen[str]:
    overrides = [
        "logToStderr=false",
        "logAllRequests=false",
        "logAllResponses=false",
        "logSearchInfo=false",
        f"numAnalysisThreads={args.analysis_threads}",
        f"numSearchThreads={args.search_threads}",
    ]
    command = [
        args.katago, "analysis",
        "-model", args.model,
        "-config", args.config,
        "-override-config", ",".join(overrides),
    ]
    return subprocess.Popen(
        command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL, text=True, bufsize=1,
    )


def query(
    process: subprocess.Popen[str],
    query_id: str,
    moves: list[list[str]],
    max_visits: int,
    include_policy: bool,
    time_cap_ms: int,
) -> tuple[dict[str, Any], float]:
    assert process.stdin is not None
    assert process.stdout is not None
    payload = {
        "id": query_id,
        "rules": "japanese",
        "komi": 6.5,
        "boardXSize": BOARD_SIZE,
        "boardYSize": BOARD_SIZE,
        "initialPlayer": "B",
        "initialStones": [],
        "moves": moves,
        "analyzeTurns": [len(moves)],
        "maxVisits": max_visits,
        "includePolicy": include_policy,
        "includeOwnership": False,
        "includeMovesOwnership": False,
        "overrideSettings": {"maxTime": time_cap_ms / 1000.0},
    }
    start = time.perf_counter()
    process.stdin.write(json.dumps(payload, separators=(",", ":")) + "\n")
    process.stdin.flush()
    while True:
        line = process.stdout.readline()
        if not line:
            raise RuntimeError("KataGo analysis process exited unexpectedly")
        response = json.loads(line)
        if response.get("id") != query_id:
            continue
        if response.get("isDuringSearch"):
            continue
        if "error" in response:
            raise RuntimeError(f"KataGo query failed: {response['error']}")
        return response, (time.perf_counter() - start) * 1000.0


def run_position(
    process: subprocess.Popen[str],
    position: Position,
    visits_list: list[int],
    refine_budgets: list[int],
    time_cap_ms: int,
    query_counter: list[int],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    player = next_player(position.moves)
    occupied = occupied_vertices(position.moves)

    for visits in visits_list:
        query_counter[0] += 1
        baseline, baseline_ms = query(
            process, f"base-{query_counter[0]}", position.moves,
            visits, True, time_cap_ms,
        )
        move_infos = [
            info for info in baseline.get("moveInfos", [])
            if info.get("move", "").lower() not in {"pass", "resign"}
        ]
        scored_before = len(move_infos)
        scored_vertices = {info["move"] for info in move_infos}
        root_scorelead = baseline.get("rootInfo", {}).get("scoreLead")

        policy = baseline.get("policy") or []
        ranked_policy = sorted(
            (
                (index_to_vertex(index), prior)
                for index, prior in enumerate(policy[: BOARD_SIZE * BOARD_SIZE])
                if prior >= 0.0
            ),
            key=lambda item: item[1],
            reverse=True,
        )
        refine_pool = [
            vertex for vertex, _ in ranked_policy
            if vertex not in scored_vertices and vertex not in occupied
        ]

        for refine_budget in refine_budgets:
            candidates = refine_pool[:refine_budget]
            refine_elapsed_ms = 0.0
            refine_results = []
            for vertex in candidates:
                query_counter[0] += 1
                refine_moves = position.moves + [[player, vertex]]
                response, elapsed_ms = query(
                    process, f"refine-{query_counter[0]}", refine_moves,
                    REFINE_QUERY_VISITS, False, time_cap_ms,
                )
                refine_elapsed_ms += elapsed_ms
                child_scorelead = response.get("rootInfo", {}).get("scoreLead")
                refine_results.append((vertex, child_scorelead, elapsed_ms))

            rows.append(
                {
                    "position": position.name,
                    "visits": visits,
                    "baseline_ms": round(baseline_ms, 1),
                    "scored_before": scored_before,
                    "root_scorelead": root_scorelead,
                    "refine_budget": refine_budget,
                    "refine_count_actual": len(candidates),
                    "scored_after": scored_before + len(candidates),
                    "refine_total_ms": round(refine_elapsed_ms, 1),
                    "refine_avg_ms": round(refine_elapsed_ms / len(candidates), 1) if candidates else 0.0,
                    "combined_total_ms": round(baseline_ms + refine_elapsed_ms, 1),
                    "refine_moves": [vertex for vertex, _, _ in refine_results],
                },
            )
    return rows


def write_markdown(rows: list[dict[str, Any]], path: Path) -> None:
    lines = [
        "# KataGo candidate refine experiment",
        "",
        f"생성: `scripts/run-katago-candidate-refine-experiment.py` (자동 생성, 수동 편집 금지)",
        "",
        "| Position | Visits | Baseline ms | Scored before | Refine budget | Scored after | Refine total ms | Refine avg ms/move | Combined ms |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in rows:
        lines.append(
            "| {position} | {visits} | {baseline_ms} | {scored_before} | {refine_budget} "
            "| {scored_after} | {refine_total_ms} | {refine_avg_ms} | {combined_total_ms} |".format(**row)
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--visits", default="16,32,64")
    parser.add_argument("--refine-budgets", default="0,4,8,12")
    parser.add_argument("--time-cap-ms", type=int, default=5_000)
    parser.add_argument("--search-threads", type=int, default=4)
    parser.add_argument("--analysis-threads", type=int, default=1)
    parser.add_argument("--katago", default=os.environ.get("KATAGO_BIN", DEFAULT_KATAGO))
    parser.add_argument("--model", default=os.environ.get("KATAGO_MODEL", DEFAULT_MODEL))
    parser.add_argument("--config", default=os.environ.get("KATAGO_ANALYSIS_CONFIG", DEFAULT_CONFIG))
    parser.add_argument("--out", type=Path, default=Path("docs/engine/measurements/engine-benchmark/candidate-refine-latest.md"))
    parser.add_argument("--json-out", type=Path, default=None)
    args = parser.parse_args()

    visits_list = [int(v) for v in args.visits.split(",") if v]
    refine_budgets = [int(v) for v in args.refine_budgets.split(",") if v != ""]

    process = start_engine(args)
    query_counter = [0]
    try:
        # Warm up model load / first-query JIT cost so it doesn't skew P0's
        # baseline latency (first query on a fresh process is ~2s slower).
        query(process, "warmup", [], 16, False, args.time_cap_ms)
        all_rows: list[dict[str, Any]] = []
        for position in POSITIONS:
            rows = run_position(
                process, position, visits_list, refine_budgets,
                args.time_cap_ms, query_counter,
            )
            all_rows.extend(rows)
            for row in rows:
                print(
                    f"{row['position']:>14} B{row['visits']:<3} "
                    f"refine={row['refine_budget']:<3} "
                    f"scored {row['scored_before']:>2}->{row['scored_after']:<3} "
                    f"baseline={row['baseline_ms']:>7}ms "
                    f"refine_total={row['refine_total_ms']:>7}ms "
                    f"combined={row['combined_total_ms']:>8}ms",
                    file=sys.stderr,
                )
    finally:
        process.terminate()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    write_markdown(all_rows, args.out)
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(all_rows, indent=2), encoding="utf-8")
    print(f"wrote {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
