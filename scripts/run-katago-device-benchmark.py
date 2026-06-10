#!/usr/bin/env python3
"""Measure local KataGo analysis latency for B16/B32/B64 visit targets."""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import subprocess
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_KATAGO = "/opt/homebrew/bin/katago"
DEFAULT_MODEL = "/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
DEFAULT_CONFIG = "app-android/src/friend/assets/katago/analysis_learning.cfg"
LETTERS = "ABCDEFGHJ"
DEFAULT_VISITS = (16, 32, 64)
DEFAULT_POSITIONS = ("b16-best-3-variants", "empty", "random")
BENCHMARK_VARIANT_MOVES = (
    "A9",
    "J1",
    "A1",
    "J9",
    "E1",
    "E9",
    "A5",
    "J5",
    "C3",
    "G7",
    "C7",
    "G3",
    "B8",
    "H2",
    "B2",
)


@dataclass(frozen=True)
class BenchmarkPosition:
    name: str
    moves: list[list[str]]

    @property
    def next_player(self) -> str:
        return "B" if len(self.moves) % 2 == 0 else "W"


def vertex(row: int, col: int) -> str:
    return f"{LETTERS[col]}{9 - row}"


def parse_vertex(raw: str) -> tuple[int, int]:
    col = LETTERS.index(raw[0])
    row = 9 - int(raw[1:])
    return row, col


class RandomGoBoard:
    def __init__(self, size: int = 9) -> None:
        self.size = size
        self.stones: dict[tuple[int, int], str] = {}
        self.ko_point: tuple[int, int] | None = None
        self.ko_forbidden_for: str | None = None

    def neighbors(self, point: tuple[int, int]) -> list[tuple[int, int]]:
        row, col = point
        candidates = ((row - 1, col), (row + 1, col), (row, col - 1), (row, col + 1))
        return [
            (r, c)
            for r, c in candidates
            if 0 <= r < self.size and 0 <= c < self.size
        ]

    def group_and_liberties(self, start: tuple[int, int]) -> tuple[set[tuple[int, int]], set[tuple[int, int]]]:
        color = self.stones[start]
        group = {start}
        liberties: set[tuple[int, int]] = set()
        stack = [start]
        while stack:
            current = stack.pop()
            for neighbor in self.neighbors(current):
                neighbor_color = self.stones.get(neighbor)
                if neighbor_color is None:
                    liberties.add(neighbor)
                elif neighbor_color == color and neighbor not in group:
                    group.add(neighbor)
                    stack.append(neighbor)
        return group, liberties

    def is_legal(self, color: str, point: tuple[int, int]) -> bool:
        snapshot = dict(self.stones)
        ko_point = self.ko_point
        ko_forbidden_for = self.ko_forbidden_for
        legal = self.play(color, point)
        self.stones = snapshot
        self.ko_point = ko_point
        self.ko_forbidden_for = ko_forbidden_for
        return legal

    def legal_points(self, color: str) -> list[tuple[int, int]]:
        return [
            (row, col)
            for row in range(self.size)
            for col in range(self.size)
            if self.is_legal(color, (row, col))
        ]

    def play(self, color: str, point: tuple[int, int]) -> bool:
        if point in self.stones:
            return False
        if self.ko_point == point and self.ko_forbidden_for == color:
            return False

        opponent = "W" if color == "B" else "B"
        self.stones[point] = color
        captured: list[tuple[int, int]] = []
        checked: set[tuple[int, int]] = set()

        for neighbor in self.neighbors(point):
            if self.stones.get(neighbor) != opponent or neighbor in checked:
                continue
            group, liberties = self.group_and_liberties(neighbor)
            checked |= group
            if not liberties:
                captured.extend(group)

        for captured_point in captured:
            self.stones.pop(captured_point, None)

        own_group, own_liberties = self.group_and_liberties(point)
        if not own_liberties:
            self.stones.pop(point, None)
            for captured_point in captured:
                self.stones[captured_point] = opponent
            return False

        if len(captured) == 1 and len(own_group) == 1 and len(own_liberties) == 1:
            self.ko_point = captured[0]
            self.ko_forbidden_for = opponent
        else:
            self.ko_point = None
            self.ko_forbidden_for = None
        return True


def random_position(name: str, rng: random.Random, move_count: int) -> BenchmarkPosition:
    board = RandomGoBoard()
    moves: list[list[str]] = []
    for turn in range(move_count):
        color = "B" if turn % 2 == 0 else "W"
        legal = board.legal_points(color)
        if not legal:
            moves.append([color, "pass"])
            continue
        point = rng.choice(legal)
        if not board.play(color, point):
            moves.append([color, "pass"])
            continue
        moves.append([color, vertex(*point)])
    return BenchmarkPosition(name=name, moves=moves)


def start_engine(args: argparse.Namespace) -> subprocess.Popen[str]:
    overrides = [
        "logToStderr=false",
        "logAllRequests=false",
        "logAllResponses=false",
        "logSearchInfo=false",
        f"numAnalysisThreads={args.analysis_threads}",
        f"numSearchThreads={args.search_threads}",
    ]
    if args.deterministic:
        overrides.append("nnRandomize=false")
    command = [
        args.katago,
        "analysis",
        "-model",
        args.model,
        "-config",
        args.config,
        "-override-config",
        ",".join(overrides),
    ]
    return subprocess.Popen(
        command,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        bufsize=1,
    )


def query_engine(
    process: subprocess.Popen[str],
    query_id: str,
    position: BenchmarkPosition,
    visits: int,
    time_cap_ms: int,
) -> tuple[dict[str, Any], float]:
    assert process.stdin is not None
    assert process.stdout is not None
    query = {
        "id": query_id,
        "rules": "japanese",
        "komi": 6.5,
        "boardXSize": 9,
        "boardYSize": 9,
        "initialPlayer": "B",
        "initialStones": [],
        "moves": position.moves,
        "analyzeTurns": [len(position.moves)],
        "maxVisits": visits,
        "includePolicy": False,
        "includeOwnership": False,
        "includeMovesOwnership": False,
        "overrideSettings": {"maxTime": time_cap_ms / 1000.0},
    }
    start = time.perf_counter()
    process.stdin.write(json.dumps(query, separators=(",", ":")) + "\n")
    process.stdin.flush()
    while True:
        line = process.stdout.readline()
        if not line:
            raise RuntimeError("KataGo analysis process exited before returning a response")
        response = json.loads(line)
        if response.get("id") != query_id:
            continue
        if response.get("isDuringSearch"):
            continue
        if "error" in response:
            raise RuntimeError(f"KataGo query failed: {response['error']}")
        return response, (time.perf_counter() - start) * 1000.0


def root_visits(response: dict[str, Any]) -> int | None:
    root = response.get("rootInfo") or {}
    value = root.get("visits")
    return int(value) if value is not None else None


def best_move(response: dict[str, Any]) -> str | None:
    move_infos = response.get("moveInfos") or []
    ordered = sorted(
        (
            move_info
            for move_info in move_infos
            if isinstance(move_info, dict) and move_info.get("move")
        ),
        key=lambda move_info: int(move_info.get("order", 999999)),
    )
    for move_info in ordered:
        move = str(move_info["move"])
        if move.lower() not in {"pass", "resign"}:
            return move
    return None


def b16_best_three_position(
    process: subprocess.Popen[str],
    args: argparse.Namespace,
) -> BenchmarkPosition:
    position = BenchmarkPosition(name="b16-best-3-variants", moves=[])
    for ply in range(1, 4):
        response, _ = query_engine(
            process,
            f"b16-best-3-seed-{ply}",
            position,
            visits=16,
            time_cap_ms=args.time_cap_ms,
        )
        move = best_move(response)
        if move is None:
            break
        position = BenchmarkPosition(
            name="b16-best-3-variants",
            moves=position.moves + [[position.next_player, move]],
        )
    return position


def board_from_position(position: BenchmarkPosition) -> RandomGoBoard:
    board = RandomGoBoard()
    for color, move in position.moves:
        if move.lower() in {"pass", "resign"}:
            continue
        if not board.play(color, parse_vertex(move)):
            raise RuntimeError(f"generated illegal benchmark move: {color} {move}")
    return board


def benchmark_variant_position(
    base: BenchmarkPosition,
    sample_index: int,
) -> BenchmarkPosition:
    if sample_index <= 0:
        return base

    board = board_from_position(base)
    moves = list(base.moves)
    added = 0
    for move in BENCHMARK_VARIANT_MOVES:
        if added >= sample_index:
            break
        color = "B" if len(moves) % 2 == 0 else "W"
        point = parse_vertex(move)
        if not board.is_legal(color, point):
            continue
        if not board.play(color, point):
            continue
        moves.append([color, move])
        added += 1
    return BenchmarkPosition(name=base.name, moves=moves)


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = math.ceil(len(ordered) * pct) - 1
    return ordered[max(0, min(index, len(ordered) - 1))]


def summarize(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[(row["position"], row["visitsRequest"])].append(row)

    results = []
    for (position, visits), group in sorted(grouped.items(), key=lambda item: (item[0][0], item[0][1])):
        elapsed_values = [float(row["elapsedMs"]) for row in group]
        root_values = [
            int(row["rootVisits"])
            for row in group
            if row.get("rootVisits") is not None
        ]
        short_count = sum(1 for row in group if row["fill"] == "SHORT")
        max_elapsed = max(elapsed_values)
        recommended = int(math.ceil((max_elapsed * 1.25) / 50.0) * 50)
        results.append(
            {
                "position": position,
                "visits": visits,
                "samples": len(group),
                "shortCount": short_count,
                "elapsedMinMs": round(min(elapsed_values), 3),
                "elapsedAvgMs": round(mean(elapsed_values), 3),
                "elapsedMaxMs": round(max_elapsed, 3),
                "elapsedP90Ms": round(percentile(elapsed_values, 0.90), 3),
                "rootVisitsMin": min(root_values) if root_values else None,
                "rootVisitsAvg": round(mean(root_values), 3) if root_values else None,
                "rootVisitsMax": max(root_values) if root_values else None,
                "recommendedTimeCapMs": recommended,
            },
        )
    return results


def write_markdown(summary: dict[str, Any], path: Path) -> None:
    lines = [
        "# 엔진 디바이스 벤치마크 결과",
        "",
        f"- samplesPerVisit: `{summary['samples']}`",
        f"- positions: `{', '.join(summary['positions'])}`",
        f"- visits: `{', '.join(str(value) for value in summary['visits'])}`",
        f"- timeCap: `{summary['timeCapMs']}ms`",
        f"- deterministic: `{summary['deterministic']}`",
        f"- searchThreads: `{summary['searchThreads']}`",
    ]
    if summary.get("generatedPositions"):
        lines.append("- generatedPositions:")
        for name, moves in summary["generatedPositions"].items():
            rendered_moves = ", ".join(f"{color} {move}" for color, move in moves) or "none"
            lines.append(f"  - `{name}`: `{rendered_moves}`")
    lines.extend(
        [
            "",
            "| Position | Visits | Samples | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        ],
    )
    for result in summary["results"]:
        lines.append(
            "| {position} | {visits} | {samples} | {short} | {min_ms} | {avg_ms} | {max_ms} | {p90_ms} | {root_avg} | {recommended}ms |".format(
                position=result["position"],
                visits=result["visits"],
                samples=result["samples"],
                short=result["shortCount"],
                min_ms=result["elapsedMinMs"],
                avg_ms=result["elapsedAvgMs"],
                max_ms=result["elapsedMaxMs"],
                p90_ms=result["elapsedP90Ms"],
                root_avg=result["rootVisitsAvg"],
                recommended=result["recommendedTimeCapMs"],
            ),
        )
    path.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", type=int, default=10)
    parser.add_argument("--visits", default="16,32,64")
    parser.add_argument("--positions", default="b16-best-3-variants")
    parser.add_argument("--random-moves", type=int, default=24)
    parser.add_argument("--time-cap-ms", type=int, default=5_000)
    parser.add_argument("--seed", type=int, default=20260610)
    parser.add_argument("--deterministic", action="store_true")
    parser.add_argument("--search-threads", type=int, default=4)
    parser.add_argument("--analysis-threads", type=int, default=1)
    parser.add_argument("--katago", default=os.environ.get("KATAGO_BIN", DEFAULT_KATAGO))
    parser.add_argument("--model", default=os.environ.get("KATAGO_MODEL", DEFAULT_MODEL))
    parser.add_argument("--config", default=os.environ.get("KATAGO_ANALYSIS_CONFIG", DEFAULT_CONFIG))
    parser.add_argument("--out-dir", type=Path, default=ROOT / "docs" / "engine-benchmark-logs" / "local-latest")
    args = parser.parse_args()

    visits = [int(value.strip()) for value in args.visits.split(",") if value.strip()]
    positions = [value.strip() for value in args.positions.split(",") if value.strip()]
    rng = random.Random(args.seed)
    for position in positions:
        if position not in DEFAULT_POSITIONS:
            raise ValueError(f"unsupported position: {position}")

    args.out_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = args.out_dir / "samples.jsonl"
    summary_path = args.out_dir / "summary.json"
    markdown_path = args.out_dir / "summary.md"

    process = start_engine(args)
    rows: list[dict[str, Any]] = []
    generated_positions: dict[str, BenchmarkPosition] = {}
    try:
        query_engine(
            process,
            "warmup",
            BenchmarkPosition(name="warmup", moves=[]),
            visits=1,
            time_cap_ms=args.time_cap_ms,
        )
        if "b16-best-3-variants" in positions:
            generated_positions["b16-best-3-variants"] = b16_best_three_position(process, args)
        with jsonl_path.open("w", encoding="utf-8") as handle:
            handle.write(
                json.dumps(
                    {
                        "type": "run",
                        "seed": args.seed,
                        "samples": args.samples,
                        "visits": visits,
                        "positions": positions,
                        "timeCapMs": args.time_cap_ms,
                        "deterministic": args.deterministic,
                        "searchThreads": args.search_threads,
                        "generatedPositions": {
                            name: position.moves
                            for name, position in generated_positions.items()
                        },
                    },
                    ensure_ascii=False,
                ) + "\n",
            )
            for position_name in positions:
                for sample in range(1, args.samples + 1):
                    if position_name == "b16-best-3-variants":
                        position = benchmark_variant_position(
                            generated_positions["b16-best-3-variants"],
                            sample - 1,
                        )
                    elif position_name == "empty":
                        position = BenchmarkPosition(name="empty", moves=[])
                    else:
                        position = random_position("random", rng, args.random_moves)
                    for visit_target in visits:
                        query_id = f"{position.name}-v{visit_target}-s{sample}"
                        response, elapsed_ms = query_engine(
                            process,
                            query_id,
                            position,
                            visits=visit_target,
                            time_cap_ms=args.time_cap_ms,
                        )
                        actual_root_visits = root_visits(response)
                        row = {
                            "type": "sample",
                            "position": position.name,
                            "sample": sample,
                            "visitsRequest": visit_target,
                            "timeCapMs": args.time_cap_ms,
                            "elapsedMs": round(elapsed_ms, 3),
                            "rootVisits": actual_root_visits,
                            "fill": "OK" if actual_root_visits is not None and actual_root_visits >= visit_target else "SHORT",
                            "moveInfoCount": len(response.get("moveInfos", [])),
                            "nextPlayer": position.next_player,
                            "moves": len(position.moves),
                            "positionMoves": position.moves,
                        }
                        rows.append(row)
                        handle.write(json.dumps(row, ensure_ascii=False) + "\n")
                        handle.flush()
                        print(
                            f"{position.name} B{visit_target} sample {sample}/{args.samples}: "
                            f"{row['elapsedMs']}ms root={actual_root_visits} fill={row['fill']}",
                        )
    finally:
        if process.stdin:
            process.stdin.close()
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()

    summary = {
        "samples": args.samples,
        "positions": positions,
        "visits": visits,
        "timeCapMs": args.time_cap_ms,
        "deterministic": args.deterministic,
        "searchThreads": args.search_threads,
        "analysisThreads": args.analysis_threads,
        "randomMoves": args.random_moves,
        "generatedPositions": {
            name: position.moves
            for name, position in generated_positions.items()
        },
        "results": summarize(rows),
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_markdown(summary, markdown_path)
    print(f"wrote {jsonl_path}")
    print(f"wrote {summary_path}")
    print(f"wrote {markdown_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
