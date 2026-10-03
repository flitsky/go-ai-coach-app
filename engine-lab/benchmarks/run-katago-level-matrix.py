#!/usr/bin/env python3
"""Run the default B16/B32/B64 level matrix and summarize results."""

from __future__ import annotations

import argparse
import importlib.util
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run-katago-level-match.py"
DEFAULT_MATCHUPS = [
    ("fast_beginner:3", "beginner:7", "B16-vs-B32"),
    ("fast_beginner:3", "intermediate:5", "B16-vs-B64"),
    ("beginner:7", "intermediate:5", "B32-vs-B64"),
]


def load_runner_module():
    spec = importlib.util.spec_from_file_location("katago_level_match", RUNNER)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load runner: {RUNNER}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def summarize_matchup(path: Path, left_label: str, right_label: str) -> dict[str, Any]:
    rows = read_jsonl(path)
    games = [row for row in rows if row.get("type") == "game"]
    turns = [row for row in rows if row.get("type") == "turn"]
    wins = {left_label: 0, right_label: 0, "unknown": 0}
    left_signed_leads = []
    elapsed_by_level: dict[str, list[float]] = {}
    root_visits_by_level: dict[str, list[int]] = {}

    for game in games:
        winner = game.get("estimatedWinner")
        if winner == "B":
            winner_label = game["blackLevel"]
        elif winner == "W":
            winner_label = game["whiteLevel"]
        else:
            winner_label = "unknown"
        wins[winner_label if winner_label in wins else "unknown"] += 1

        lead = game.get("finalEstimateBlackLead")
        if lead is not None:
            left_signed_leads.append(float(lead) if game["blackLevel"] == left_label else -float(lead))

    for turn in turns:
        level = turn.get("level", "unknown")
        elapsed_by_level.setdefault(level, []).append(float(turn.get("elapsedMs") or 0.0))
        root_visits = turn.get("rootVisits")
        if isinstance(root_visits, int):
            root_visits_by_level.setdefault(level, []).append(root_visits)

    def avg(values: list[float | int]) -> float | None:
        return round(sum(values) / len(values), 3) if values else None

    return {
        "log": str(path),
        "games": len(games),
        "leftLevel": left_label,
        "rightLevel": right_label,
        "wins": wins,
        "leftWinRate": round(wins[left_label] / len(games), 3) if games else None,
        "rightWinRate": round(wins[right_label] / len(games), 3) if games else None,
        "leftAverageScoreLead": avg(left_signed_leads),
        "averageElapsedMsByLevel": {level: avg(values) for level, values in elapsed_by_level.items()},
        "averageRootVisitsByLevel": {level: avg(values) for level, values in root_visits_by_level.items()},
    }


def write_markdown(summary: dict[str, Any], path: Path) -> None:
    lines = [
        "# 엔진 레벨 매트릭스 결과",
        "",
        f"- gamesPerMatchup: `{summary['gamesPerMatchup']}`",
        f"- deterministic: `{summary['deterministic']}`",
        f"- cacheIsolation: `{summary['cacheIsolation']}`",
        f"- cacheClearedBeforeQuery: `{summary['cacheClearedBeforeQuery']}`",
        f"- searchThreads: `{summary['searchThreads']}`",
        f"- finalEval: `{summary['finalVisits']} visits / {summary['finalTimeMs']}ms`",
        "",
        "| Matchup | Left wins | Right wins | Left win rate | Avg left lead | Log |",
        "| --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for result in summary["results"]:
        lines.append(
            "| {left} vs {right} | {left_wins} | {right_wins} | {left_rate} | {lead} | {log} |".format(
                left=result["leftLevel"],
                right=result["rightLevel"],
                left_wins=result["wins"].get(result["leftLevel"], 0),
                right_wins=result["wins"].get(result["rightLevel"], 0),
                left_rate=result["leftWinRate"],
                lead=result["leftAverageScoreLead"],
                log=result["log"],
            ),
        )
    lines += [
        "",
        "## 평균 응답 시간",
        "",
    ]
    for result in summary["results"]:
        lines.append(f"### {result['leftLevel']} vs {result['rightLevel']}")
        for level, elapsed in sorted(result["averageElapsedMsByLevel"].items()):
            visits = result["averageRootVisitsByLevel"].get(level)
            lines.append(f"- `{level}`: avg `{elapsed}ms`, avg root visits `{visits}`")
        lines.append("")
    path.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--games-per-matchup", type=int, default=50)
    parser.add_argument("--out-dir", type=Path, default=ROOT / "docs" / "engine-match-logs" / "matrix-latest")
    parser.add_argument("--max-moves", type=int, default=120)
    parser.add_argument("--seed", type=int, default=20260610)
    parser.add_argument("--deterministic", action="store_true")
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
    args = parser.parse_args()
    if args.reuse_cache:
        args.cache_isolation = "none"

    runner = load_runner_module()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    results = []

    for index, (left, right, slug) in enumerate(DEFAULT_MATCHUPS, start=1):
        out = args.out_dir / f"{slug}.jsonl"
        command = [
            sys.executable,
            str(RUNNER),
            "--black",
            left,
            "--white",
            right,
            "--games",
            str(args.games_per_matchup),
            "--max-moves",
            str(args.max_moves),
            "--seed",
            str(args.seed + index),
            "--swap-colors",
            "--search-threads",
            str(args.search_threads),
            "--analysis-threads",
            str(args.analysis_threads),
            "--final-visits",
            str(args.final_visits),
            "--final-time-ms",
            str(args.final_time_ms),
            "--cache-isolation",
            args.cache_isolation,
            "--out",
            str(out),
        ]
        if args.deterministic:
            command.append("--deterministic")
        if args.reuse_cache:
            command.append("--reuse-cache")
        print(f"running {slug}: {left} vs {right}")
        subprocess.run(command, cwd=ROOT, check=True)
        left_label = runner.level_spec(left).label
        right_label = runner.level_spec(right).label
        results.append(summarize_matchup(out, left_label, right_label))

    summary = {
        "gamesPerMatchup": args.games_per_matchup,
        "totalGames": args.games_per_matchup * len(DEFAULT_MATCHUPS),
        "deterministic": args.deterministic,
        "cacheIsolation": args.cache_isolation,
        "cacheClearedBeforeQuery": args.cache_isolation == "clear-cache",
        "searchThreads": args.search_threads,
        "analysisThreads": args.analysis_threads,
        "finalVisits": args.final_visits,
        "finalTimeMs": args.final_time_ms,
        "results": results,
    }
    summary_path = args.out_dir / "summary.json"
    markdown_path = args.out_dir / "summary.md"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_markdown(summary, markdown_path)
    print(f"wrote {summary_path}")
    print(f"wrote {markdown_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
