#!/usr/bin/env python3
"""Run repeatable KataGo level-vs-level matches and write JSONL logs.

This script mirrors the app's current play-level budgets and move-selection
windows closely enough for engine-strength experiments. It uses KataGo's
analysis engine directly, so it is intended for local benchmarking rather than
Android UI testing.
"""

from __future__ import annotations

import argparse
import json
import os
import random
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_KATAGO = "/opt/homebrew/bin/katago"
DEFAULT_MODEL = "/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
DEFAULT_CONFIG = "app-android/src/friend/assets/katago/analysis_learning.cfg"
LETTERS = "ABCDEFGHJ"


@dataclass(frozen=True)
class LevelSpec:
    label: str
    visits: int
    time_seconds: float
    candidate_count: int
    policy: str
    window: tuple[int, int] | None = None
    exclude_best: bool = False


def level_spec(raw: str) -> LevelSpec:
    try:
        group, level_text = raw.replace("-", "_").lower().split(":", maxsplit=1)
        level = int(level_text)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            "level must look like fast_beginner:3, beginner:7, intermediate:5",
        ) from exc

    if group in {"fast", "fast_beginner", "fb"}:
        if level == 1:
            return LevelSpec("빠른 초급 1단계", 16, 0.25, 8, "percentile", (50, 100))
        if level == 2:
            return LevelSpec("빠른 초급 2단계", 16, 0.25, 8, "percentile", (0, 60), True)
        return LevelSpec("빠른 초급 3단계", 16, 0.25, 8, "best")

    if group in {"beginner", "learning_beginner", "lb"}:
        windows = {
            1: (70, 100),
            2: (50, 100),
            3: (40, 70),
            4: (30, 60),
            5: (10, 50),
            6: (0, 30),
        }
        if level >= 7:
            return LevelSpec("초급 7단계", 32, 0.5, 16, "best")
        return LevelSpec(f"초급 {level}단계", 32, 0.5, 16, "percentile", windows[level])

    if group in {"intermediate", "im"}:
        windows = {
            1: (50, 100),
            2: (40, 80),
            3: (20, 60),
            4: (0, 40),
        }
        if level >= 5:
            return LevelSpec("중급 5단계", 64, 0.5, 20, "best")
        return LevelSpec(f"중급 {level}단계", 64, 0.5, 20, "percentile", windows[level])

    if group in {"advanced", "ad"}:
        windows = {
            1: (30, 70),
            2: (20, 50),
            3: (10, 40),
            4: (0, 20),
        }
        if level >= 5:
            return LevelSpec("고급 5단계", 160, 1.0, 24, "best")
        return LevelSpec(f"고급 {level}단계", 160, 1.0, 24, "percentile", windows[level])

    raise argparse.ArgumentTypeError(f"unknown level group: {group}")


def candidate_range(spec: LevelSpec, count: int) -> range:
    if count <= 0:
        return range(0)
    if spec.policy == "best":
        return range(0, 1)
    assert spec.window is not None
    start_percent, end_percent = spec.window
    start = int(count * start_percent / 100)
    end = max(start + 1, int((count * end_percent + 99) / 100))
    start = min(start, count - 1)
    end = min(end, count)
    if spec.exclude_best and count > 1:
        start = max(start, 1)
        if start >= end:
            end = start + 1
    return range(start, end)


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
        overrides += [
            "nnRandomize=false",
        ]
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
    moves: list[list[str]],
    spec: LevelSpec,
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
        "moves": moves,
        "analyzeTurns": [len(moves)],
        "maxVisits": spec.visits,
        "includePolicy": False,
        "includeOwnership": False,
        "includeMovesOwnership": False,
        "overrideSettings": {"maxTime": spec.time_seconds},
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


def choose_move(
    response: dict[str, Any],
    spec: LevelSpec,
    rng: random.Random,
) -> tuple[str, dict[str, Any]]:
    move_infos = sorted(response.get("moveInfos", []), key=lambda item: item.get("order", 999999))
    if not move_infos:
        return "pass", {}
    if move_infos[0].get("move", "").lower() == "pass":
        return "pass", move_infos[0]

    play_infos = [item for item in move_infos if item.get("move", "").lower() != "pass"]
    play_infos = play_infos[: spec.candidate_count]
    if not play_infos:
        return "pass", move_infos[0]
    selectable = [play_infos[index] for index in candidate_range(spec, len(play_infos))]
    selected = rng.choice(selectable or play_infos[:1])
    return selected["move"], selected


def black_score_lead(response: dict[str, Any]) -> float | None:
    root = response.get("rootInfo") or {}
    value = root.get("scoreLead")
    return float(value) if value is not None else None


def expected_winner(score_lead: float | None) -> str:
    if score_lead is None:
        return "unknown"
    return "B" if score_lead > 0 else "W"


def play_game(
    process: subprocess.Popen[str],
    game_index: int,
    black: LevelSpec,
    white: LevelSpec,
    rng: random.Random,
    max_moves: int,
    final_eval: LevelSpec,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    moves: list[list[str]] = []
    turn_logs: list[dict[str, Any]] = []
    consecutive_passes = 0
    last_response: dict[str, Any] | None = None

    for turn in range(max_moves):
        player = "B" if turn % 2 == 0 else "W"
        spec = black if player == "B" else white
        response, elapsed_ms = query_engine(process, f"g{game_index}-t{turn}", moves, spec)
        last_response = response
        move, selected = choose_move(response, spec, rng)
        moves.append([player, move])
        consecutive_passes = consecutive_passes + 1 if move.lower() == "pass" else 0
        turn_logs.append(
            {
                "game": game_index,
                "turn": turn + 1,
                "player": player,
                "level": spec.label,
                "visitsRequest": spec.visits,
                "timeRequestMs": int(spec.time_seconds * 1000),
                "elapsedMs": round(elapsed_ms, 1),
                "move": move,
                "engineOrder": selected.get("order"),
                "candidateVisits": selected.get("visits"),
                "candidateWinrate": selected.get("winrate"),
                "candidateScoreLead": selected.get("scoreLead"),
                "rootVisits": (response.get("rootInfo") or {}).get("visits"),
                "rootScoreLeadBlack": black_score_lead(response),
                "moveInfoCount": len(response.get("moveInfos", [])),
            },
        )
        if consecutive_passes >= 2:
            break

    final_response, final_elapsed_ms = query_engine(
        process,
        f"g{game_index}-final",
        moves,
        final_eval,
    )
    final_lead = black_score_lead(final_response or last_response or {})
    return (
        {
            "game": game_index,
            "blackLevel": black.label,
            "whiteLevel": white.label,
            "moves": len(moves),
            "endedByPassPass": consecutive_passes >= 2,
            "finalEstimateBlackLead": final_lead,
            "finalEvalVisits": final_eval.visits,
            "finalEvalTimeMs": int(final_eval.time_seconds * 1000),
            "finalEvalElapsedMs": round(final_elapsed_ms, 1),
            "estimatedWinner": expected_winner(final_lead),
        },
        turn_logs,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--black", type=level_spec, default=level_spec("fast_beginner:3"))
    parser.add_argument("--white", type=level_spec, default=level_spec("beginner:7"))
    parser.add_argument("--games", type=int, default=10)
    parser.add_argument("--max-moves", type=int, default=120)
    parser.add_argument("--seed", type=int, default=20260610)
    parser.add_argument("--swap-colors", action="store_true")
    parser.add_argument("--deterministic", action="store_true")
    parser.add_argument("--no-warmup", action="store_true")
    parser.add_argument("--search-threads", type=int, default=4)
    parser.add_argument("--analysis-threads", type=int, default=1)
    parser.add_argument("--final-visits", type=int, default=400)
    parser.add_argument("--final-time-ms", type=int, default=2_000)
    parser.add_argument("--katago", default=os.environ.get("KATAGO_BIN", DEFAULT_KATAGO))
    parser.add_argument("--model", default=os.environ.get("KATAGO_MODEL", DEFAULT_MODEL))
    parser.add_argument("--config", default=os.environ.get("KATAGO_ANALYSIS_CONFIG", DEFAULT_CONFIG))
    parser.add_argument("--out", type=Path, default=Path("docs/engine-match-logs/latest.jsonl"))
    args = parser.parse_args()

    process = start_engine(args)
    rng = random.Random(args.seed)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    final_eval = LevelSpec(
        label="final evaluator",
        visits=args.final_visits,
        time_seconds=args.final_time_ms / 1000.0,
        candidate_count=1,
        policy="best",
    )

    summaries: list[dict[str, Any]] = []
    try:
        if not args.no_warmup:
            query_engine(
                process,
                "warmup",
                [],
                LevelSpec("warmup", 1, 2.0, 1, "best"),
            )
        with args.out.open("w", encoding="utf-8") as handle:
            handle.write(json.dumps({"type": "run", "seed": args.seed, "deterministic": args.deterministic}) + "\n")
            for game in range(1, args.games + 1):
                black, white = args.black, args.white
                if args.swap_colors and game % 2 == 0:
                    black, white = args.white, args.black
                summary, turns = play_game(process, game, black, white, rng, args.max_moves, final_eval)
                summaries.append(summary)
                handle.write(json.dumps({"type": "game", **summary}, ensure_ascii=False) + "\n")
                for turn in turns:
                    handle.write(json.dumps({"type": "turn", **turn}, ensure_ascii=False) + "\n")
                handle.flush()
                print(
                    f"game {game}: {summary['blackLevel']} vs {summary['whiteLevel']} "
                    f"winner={summary['estimatedWinner']} lead={summary['finalEstimateBlackLead']}",
                )
    finally:
        if process.stdin:
            process.stdin.close()
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()

    black_wins = sum(1 for summary in summaries if summary["estimatedWinner"] == "B")
    white_wins = sum(1 for summary in summaries if summary["estimatedWinner"] == "W")
    print(f"wrote {args.out}")
    print(f"estimated result: B {black_wins} / W {white_wins} / unknown {len(summaries) - black_wins - white_wins}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
