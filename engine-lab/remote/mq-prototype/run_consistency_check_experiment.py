#!/usr/bin/env python3
"""Consistency-check experiment — Stage F kickoff, section 6 item 2.

Open question from the kickoff plan (section 6, item 2): when the app
compares "local 3-move analysis" against a remote candidate's answer for
the same position, KataGo is not fully deterministic (thread races, batch
ordering), so **exact equality is the wrong comparison**. But how loose
does the tolerance need to be? This script measures it instead of
guessing, by reusing the real `KataGoEngine` from
`scripts/run-katago-remote-analysis-server.py` (imported directly, no
duplicated engine-management code, per the plan's instruction to reuse
that file's KataGo call code):

  1. For the root position and the first 3 moves of a fixed opening,
     run the "local" engine config `--repeats` times on the *identical*
     position and measure how much winrate/scoreLead drifts run-to-run.
     That drift is the nondeterminism floor — no tolerance tighter than
     this can ever pass reliably, even comparing local against itself.
  2. Run a second, differently-configured engine instance (fewer search
     threads, standing in for a phone with weaker hardware than this
     Mac) once per position as the "remote candidate" answer, and compare
     it against the local runs.
  3. Print both numbers side by side so the answer to "exact match or
     tolerance-based, and how wide" is read off real numbers, not
     assumed.

Usage:
    python3 scripts/remote-engine-mq-prototype/run_consistency_check_experiment.py \\
        --visits 200 --repeats 3
"""

from __future__ import annotations

import argparse
import importlib.util
import statistics
import sys
from pathlib import Path
from typing import Any

REFERENCE_SERVER_PATH = Path(__file__).parent.parent / "run-katago-remote-analysis-server.py"


def load_reference_server_module() -> Any:
    spec = importlib.util.spec_from_file_location("run_katago_remote_analysis_server", REFERENCE_SERVER_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# Fixed 9x9 opening so every run analyzes the exact same 4 positions
# (root + after each of the first 3 moves) — "첫 3수" from the plan doc.
FIXED_OPENING_MOVES = [["B", "E5"], ["W", "C3"], ["B", "G7"]]


def build_query(moves: list[list[str]], analyze_turn: int, visits: int) -> dict[str, Any]:
    return {
        "rules": "chinese",
        "komi": 6.5,
        "boardXSize": 9,
        "boardYSize": 9,
        "initialPlayer": "B",
        "initialStones": [],
        "moves": moves,
        "analyzeTurns": [analyze_turn],
        "maxVisits": visits,
        "includePolicy": False,
        "includeOwnership": False,
        "includeMovesOwnership": False,
        "overrideSettings": {},
    }


def extract_root_info(katago_response: dict[str, Any]) -> tuple[float, float]:
    root = katago_response.get("rootInfo") or {}
    return root.get("winrate", float("nan")), root.get("scoreLead", float("nan"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--visits", type=int, default=200, help="maxVisits per query — kept low so the experiment runs quickly; real gameplay uses more")
    parser.add_argument("--repeats", type=int, default=3, help="how many times to re-run the identical local query per position")
    parser.add_argument("--katago", default=None)
    parser.add_argument("--model", default=None)
    parser.add_argument("--config", default=None)
    args = parser.parse_args()

    module = load_reference_server_module()
    katago = args.katago or module.DEFAULT_KATAGO
    model = args.model or module.DEFAULT_MODEL
    config = args.config or module.DEFAULT_CONFIG

    print("[consistency] starting 'local' engine (numSearchThreads=4)...", file=sys.stderr)
    local_engine = module.KataGoEngine(katago=katago, model=model, config=config, search_threads=4, analysis_threads=1)
    print("[consistency] starting 'remote-simulated' engine (numSearchThreads=1, stand-in for weaker hardware)...", file=sys.stderr)
    remote_engine = module.KataGoEngine(katago=katago, model=model, config=config, search_threads=1, analysis_threads=1)

    results = []
    for move_count in range(len(FIXED_OPENING_MOVES) + 1):
        moves = FIXED_OPENING_MOVES[:move_count]
        query = build_query(moves, analyze_turn=move_count, visits=args.visits)

        local_samples = [extract_root_info(local_engine.analyze(query)) for _ in range(args.repeats)]
        remote_sample = extract_root_info(remote_engine.analyze(query))

        local_winrates = [w for w, _ in local_samples]
        local_score_leads = [s for _, s in local_samples]
        local_winrate_spread = max(local_winrates) - min(local_winrates)
        local_score_spread = max(local_score_leads) - min(local_score_leads)

        local_mean_winrate = statistics.mean(local_winrates)
        local_mean_score = statistics.mean(local_score_leads)
        remote_winrate, remote_score = remote_sample
        local_vs_remote_winrate_delta = abs(remote_winrate - local_mean_winrate)
        local_vs_remote_score_delta = abs(remote_score - local_mean_score)

        results.append({
            "move_count": move_count,
            "local_winrate_spread": local_winrate_spread,
            "local_score_spread": local_score_spread,
            "local_vs_remote_winrate_delta": local_vs_remote_winrate_delta,
            "local_vs_remote_score_delta": local_vs_remote_score_delta,
        })

        print(
            f"[consistency] after {move_count} move(s): "
            f"local repeat spread winrate={local_winrate_spread:.4f} scoreLead={local_score_spread:.3f} | "
            f"local-vs-remote delta winrate={local_vs_remote_winrate_delta:.4f} scoreLead={local_vs_remote_score_delta:.3f}",
            file=sys.stderr,
        )

    local_engine.stop()
    remote_engine.stop()

    max_local_spread = max(r["local_winrate_spread"] for r in results)
    max_cross_delta = max(r["local_vs_remote_winrate_delta"] for r in results)
    print("\n[consistency] summary:", file=sys.stderr)
    print(f"  max local repeat-run winrate spread (nondeterminism floor): {max_local_spread:.4f}", file=sys.stderr)
    print(f"  max local-vs-remote winrate delta (different hardware):     {max_cross_delta:.4f}", file=sys.stderr)
    if max_cross_delta <= max_local_spread * 3:
        print(
            "  => local-vs-remote delta is the same order of magnitude as local-vs-local "
            "repeat variance. Tolerance-based comparison is justified; a threshold around "
            f"{max(max_local_spread, max_cross_delta) * 2:.3f} winrate (~2x the larger observed spread) "
            "is a reasonable starting point, not an exact-match check.",
            file=sys.stderr,
        )
    else:
        print(
            "  => local-vs-remote delta is notably larger than local-vs-local repeat variance. "
            "A generous tolerance alone may not distinguish 'different hardware, same answer' "
            "from 'genuinely different/wrong answer' — re-run with more visits before trusting this.",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
