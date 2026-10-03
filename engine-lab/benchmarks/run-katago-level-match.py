#!/usr/bin/env python3
"""Run repeatable KataGo level-vs-level matches and write JSONL logs.

This script mirrors the app's current play-level budgets and move-selection
windows closely enough for engine-strength experiments. It uses KataGo's
analysis engine directly, so it is intended for local benchmarking rather than
Android UI testing.

⚠️ 빠른 초급(캐릭터 5명)은 지금 **5단계 버킷**(최하·중간·최선, `PlayLevel.kt` `BucketedTierSelection`)이다 —
실험실의 `lab/selection.py`(앱 규칙을 옮긴 것)로 고른다. 2026-08-18 이전의 3단계 백분위 정의는 백로그 #214에서 걷어냈다.
⚠️ 앱의 빠른 초급은 **GTP 경로**(`kata-search_analyze`, 1스레드)를 쓰는데 이 러너는 **JSON 분석 엔진**으로 돈다 —
후보 수가 조금 다르게 나올 수 있다(맥 16방문 GTP 평균 3.9개 · JSON 5.6개, `docs/engine/measurements/README.md`).
후보 수 자체를 재려면 `experiments/e1_low_visit_candidates/run.py`를 쓴다.
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

LAB_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LAB_ROOT))
from lab import app_parity as lab_parity  # noqa: E402
from lab import paths as lab_paths  # noqa: E402
from lab import selection as lab_selection  # noqa: E402


DEFAULT_KATAGO = str(lab_paths.katago_binary())
DEFAULT_MODEL = str(lab_paths.main_model())
DEFAULT_CONFIG = str(lab_paths.analysis_config())
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
    tier_level: int | None = None  # 빠른 초급 버킷 단계(1~4) — policy == "bucket"일 때


def level_spec(raw: str) -> LevelSpec:
    try:
        group, level_text = raw.replace("-", "_").lower().split(":", maxsplit=1)
        level = int(level_text)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            "level must look like fast_beginner:3, beginner:7, intermediate:5",
        ) from exc

    if group in {"fast", "fast_beginner", "fb"}:
        tier = lab_parity.tier(max(1, min(level, len(lab_parity.TIERS))))
        label = f"빠른 초급 {tier.level}단계 {tier.name}"
        visits = lab_parity.FAST_BEGINNER_VISITS
        if tier.best_only:
            return LevelSpec(label, visits, 1.0, 1, "best")
        return LevelSpec(label, visits, 1.0, lab_parity.FAST_BEGINNER_CANDIDATE_COUNT, "bucket", tier_level=tier.level)

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
            return LevelSpec("초급 7단계", 32, 2.0, 16, "best")
        return LevelSpec(f"초급 {level}단계", 32, 2.0, 16, "percentile", windows[level])

    if group in {"intermediate", "im"}:
        windows = {
            1: (50, 100),
            2: (40, 80),
            3: (20, 60),
            4: (0, 40),
        }
        if level >= 5:
            return LevelSpec("중급 5단계", 64, 3.0, 20, "best")
        return LevelSpec(f"중급 {level}단계", 64, 3.0, 20, "percentile", windows[level])

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


def with_time_override(spec: LevelSpec, time_ms: int | None) -> LevelSpec:
    if time_ms is None:
        return spec
    return LevelSpec(
        label=f"{spec.label} ({time_ms}ms cap)",
        visits=spec.visits,
        time_seconds=time_ms / 1000.0,
        candidate_count=spec.candidate_count,
        policy=spec.policy,
        window=spec.window,
        exclude_best=spec.exclude_best,
        tier_level=spec.tier_level,
    )


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
    if args.cache_isolation == "tiny-nn-cache":
        # The analysis-engine clear_cache action crashes on the local
        # Homebrew KataGo v1.16.4 Metal binary, so benchmark isolation uses a
        # near-empty NN cache by default. This avoids cross-query cache reuse
        # without restarting the model for every move.
        overrides += [
            "nnCacheSizePowerOfTwo=0",
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


def clear_engine_cache(
    process: subprocess.Popen[str],
    query_id: str,
) -> None:
    assert process.stdin is not None
    assert process.stdout is not None
    query = {"id": query_id, "action": "clear_cache"}
    process.stdin.write(json.dumps(query, separators=(",", ":")) + "\n")
    process.stdin.flush()
    while True:
        line = process.stdout.readline()
        if not line:
            raise RuntimeError("KataGo analysis process exited before clearing cache")
        response = json.loads(line)
        if response.get("id") != query_id:
            continue
        if "error" in response:
            raise RuntimeError(f"KataGo clear_cache failed: {response['error']}")
        return


def query_engine(
    process: subprocess.Popen[str],
    query_id: str,
    moves: list[list[str]],
    spec: LevelSpec,
    clear_cache_before_query: bool = False,
) -> tuple[dict[str, Any], float]:
    assert process.stdin is not None
    assert process.stdout is not None
    if clear_cache_before_query:
        clear_engine_cache(process, f"{query_id}-clear")
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
    own_move_index: int = 0,
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
    if spec.policy == "bucket":
        tier = lab_parity.tier(spec.tier_level or 1)
        rank = lab_selection.select_rank(tier, len(play_infos), own_move_index, rng)
        selected = play_infos[rank if rank is not None else 0]
        return selected["move"], selected
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
    cache_isolation: str,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    moves: list[list[str]] = []
    turn_logs: list[dict[str, Any]] = []
    consecutive_passes = 0
    last_response: dict[str, Any] | None = None

    for turn in range(max_moves):
        player = "B" if turn % 2 == 0 else "W"
        spec = black if player == "B" else white
        response, elapsed_ms = query_engine(
            process,
            f"g{game_index}-t{turn}",
            moves,
            spec,
            clear_cache_before_query=cache_isolation == "clear-cache",
        )
        last_response = response
        own_move_index = sum(1 for color, mv in moves if color == player and mv.lower() != "pass")
        move, selected = choose_move(response, spec, rng, own_move_index)
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
                "cacheIsolation": cache_isolation,
                "cacheClearedBeforeQuery": cache_isolation == "clear-cache",
            },
        )
        if consecutive_passes >= 2:
            break

    final_response, final_elapsed_ms = query_engine(
        process,
        f"g{game_index}-final",
        moves,
        final_eval,
        clear_cache_before_query=cache_isolation == "clear-cache",
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
            "cacheIsolation": cache_isolation,
            "cacheClearedBeforeQuery": cache_isolation == "clear-cache",
            "estimatedWinner": expected_winner(final_lead),
        },
        turn_logs,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--black", type=level_spec, default=level_spec("fast_beginner:3"))
    parser.add_argument("--white", type=level_spec, default=level_spec("beginner:7"))
    parser.add_argument("--black-time-ms", type=int)
    parser.add_argument("--white-time-ms", type=int)
    parser.add_argument("--games", type=int, default=10)
    parser.add_argument("--max-moves", type=int, default=120)
    parser.add_argument("--seed", type=int, default=20260610)
    parser.add_argument("--swap-colors", action="store_true")
    parser.add_argument("--deterministic", action="store_true")
    parser.add_argument("--no-warmup", action="store_true")
    parser.add_argument(
        "--reuse-cache",
        action="store_true",
        help="Deprecated alias for --cache-isolation none.",
    )
    parser.add_argument(
        "--cache-isolation",
        choices=("tiny-nn-cache", "clear-cache", "none"),
        default="tiny-nn-cache",
        help=(
            "Cache isolation for local benchmarks. tiny-nn-cache is the default "
            "because the local KataGo v1.16.4 Metal analysis clear_cache action crashes."
        ),
    )
    parser.add_argument("--search-threads", type=int, default=4)
    parser.add_argument("--analysis-threads", type=int, default=1)
    parser.add_argument("--final-visits", type=int, default=400)
    parser.add_argument("--final-time-ms", type=int, default=2_000)
    parser.add_argument("--katago", default=os.environ.get("KATAGO_BIN", DEFAULT_KATAGO))
    parser.add_argument("--model", default=os.environ.get("KATAGO_MODEL", DEFAULT_MODEL))
    parser.add_argument("--config", default=os.environ.get("KATAGO_ANALYSIS_CONFIG", DEFAULT_CONFIG))
    parser.add_argument("--out", type=Path, default=LAB_ROOT / "benchmarks" / "runs" / f"level-match-{time.strftime('%Y%m%d-%H%M')}.jsonl")
    args = parser.parse_args()
    if args.reuse_cache:
        args.cache_isolation = "none"
    args.black = with_time_override(args.black, args.black_time_ms)
    args.white = with_time_override(args.white, args.white_time_ms)

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
            handle.write(
                json.dumps(
                    {
                        "type": "run",
                        "seed": args.seed,
                        "deterministic": args.deterministic,
                        "cacheIsolation": args.cache_isolation,
                        "cacheClearedBeforeQuery": args.cache_isolation == "clear-cache",
                    },
                ) + "\n",
            )
            for game in range(1, args.games + 1):
                black, white = args.black, args.white
                if args.swap_colors and game % 2 == 0:
                    black, white = args.white, args.black
                summary, turns = play_game(
                    process,
                    game,
                    black,
                    white,
                    rng,
                    args.max_moves,
                    final_eval,
                    cache_isolation=args.cache_isolation,
                )
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
