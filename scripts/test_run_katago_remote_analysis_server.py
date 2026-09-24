#!/usr/bin/env python3
"""Regression checks for `build_katago_query()` in
`run-katago-remote-analysis-server.py` — no KataGo binary required.

Guards refactor backlog #65-A (commit b8fc9167): a handicap game's opening
(no moves yet) must be analyzed with White to move, and with exactly the N
standard handicap stones as `initialStones` — even after some of those
stones have since been captured and dropped out of the wire state's
`stones` list. Before that fix, `initialPlayer` was hard-coded `"B"` and
handicap stones were re-derived from `state["stones"]` (`infer_initial_stones`),
which silently lost any handicap stone White had captured.

Run directly (no pytest/package install needed):
    python3 scripts/test_run_katago_remote_analysis_server.py -v

Sabotage check (backlog #90's own red/green requirement): reverting
`build_katago_query()`'s `initial_player` assignment back to the literal
`"B"` turns `EvenGameStaticPositionTest` red immediately (White-to-move
static position gets Black's turn instead), without needing a handicap
game at all. Taking the turn from `nextPlayer` even when there are moves
turns `CapturedHandicapStoneTest` red (its first move is White's, its
`nextPlayer` is Black).
"""

from __future__ import annotations

import importlib.util
import os
import unittest
from typing import Any

_HERE = os.path.dirname(os.path.abspath(__file__))
_SERVER_PATH = os.path.join(_HERE, "run-katago-remote-analysis-server.py")

# The module under test has hyphens in its filename (matches the rest of
# scripts/), so it can't be `import`ed by name — load it by path instead.
_spec = importlib.util.spec_from_file_location("run_katago_remote_analysis_server", _SERVER_PATH)
assert _spec is not None and _spec.loader is not None
remote_server = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(remote_server)

build_katago_query = remote_server.build_katago_query

# Independently computed from the production placement rule this script
# ports (`BoardSize.handicapStonePositions`,
# shared/src/commonMain/kotlin/.../domain/BoardModels.kt: near/far/mid offsets
# + `BoardCoordinate.label()`'s "ABCDEFGHJKLMNOPQRSTUVWXYZ" column letters,
# skipping "I") — not derived from the function under test, so a coordinate
# bug in `handicap_stone_positions()` itself would still be caught here.
EXPECTED_HANDICAP_POINTS: dict[int, dict[int, list[str]]] = {
    9: {
        2: ["G7", "C3"],
        3: ["G7", "C3", "G3"],
        4: ["G7", "C3", "G3", "C7"],
        5: ["G7", "C3", "G3", "C7", "E5"],
    },
    13: {
        2: ["K10", "D4"],
        3: ["K10", "D4", "K4"],
        4: ["K10", "D4", "K4", "D10"],
        5: ["K10", "D4", "K4", "D10", "G7"],
    },
    19: {
        2: ["Q16", "D4"],
        4: ["Q16", "D4", "Q4", "D16"],
        6: ["Q16", "D4", "Q4", "D16", "K10", "D10"],
        9: ["Q16", "D4", "Q4", "D16", "K10", "D10", "Q10", "K4", "K16"],
    },
}


def _request(
    *,
    board_size: int,
    ruleset: str = "Chinese",
    komi: float = 6.5,
    handicap_count: int | None = 0,
    moves: list[dict[str, Any]] | None = None,
    next_player: str = "White",
    stones: list[dict[str, str]] | None = None,
    visits: int = 1,
) -> dict[str, Any]:
    state: dict[str, Any] = {
        "boardSize": board_size,
        "ruleset": ruleset,
        "komi": komi,
        "moves": moves or [],
        "nextPlayer": next_player,
        "stones": stones or [],
    }
    if handicap_count is not None:
        state["handicapCount"] = handicap_count
    return {"state": state, "limit": {"visits": visits}}


class HandicapOpeningTest(unittest.TestCase):
    """접바둑 개막(수 없음, 백 차례): initialPlayer=W + 표준 돌 N개, 여러 보드 크기·N."""

    def test_opening_across_board_sizes_and_handicap_counts(self) -> None:
        for board_size, by_count in EXPECTED_HANDICAP_POINTS.items():
            for handicap_count, expected_points in by_count.items():
                with self.subTest(board_size=board_size, handicap_count=handicap_count):
                    handicap_stones = [{"color": "Black", "point": p} for p in expected_points]
                    request_body = _request(
                        board_size=board_size,
                        handicap_count=handicap_count,
                        moves=[],
                        next_player="White",
                        stones=handicap_stones,
                    )

                    query = build_katago_query(request_body)

                    self.assertEqual(
                        query["initialPlayer"], "W",
                        "handicap opening (no moves) must have White to move",
                    )
                    self.assertEqual(len(query["initialStones"]), handicap_count)
                    self.assertEqual(
                        query["initialStones"],
                        [["B", p] for p in expected_points],
                    )


class CapturedHandicapStoneTest(unittest.TestCase):
    """따낸 접바둑 돌 국면: 현재 판(stones)에서 사라졌어도 initialStones는 N개 그대로.

    The position the #65-A measurement used (commit b8fc9167: "9x9 2점에서
    G7을 따낸 국면"): 9x9, 2-stone handicap, then W G8, B E3, W F7, B C5,
    W H7, B D6, W G6 — White's four stones take the G7 handicap stone. The
    state below is that position in the shape the app's
    `RemotePositionAnalysisJsonCodec.encodeState()` puts on the wire
    (engine-android `HttpRemotePositionAnalysisTransport.kt`). Before the
    fix, `infer_initial_stones()` read the *current* board
    (`state["stones"]`) to guess the initial stones, so the captured G7
    vanished from the count (N became N-1). The fix rebuilds handicap
    stones from `handicapCount` directly and never looks at `stones` for a
    handicap game, so G7 must still be there.

    It is also the only case here with moves, so it guards the other
    `initialPlayer` rule: with moves, the side to move at turn 0 is the
    first move's color (White), not `nextPlayer` (Black).
    """

    def test_captured_stone_still_counts_full_handicap(self) -> None:
        request_body = {
            "state": {
                "boardSize": 9,
                "ruleset": "Japanese",
                "komi": 0.5,
                "handicapCount": 2,
                "nextPlayer": "Black",
                "capturedByBlack": 0,
                "capturedByWhite": 1,
                "koPoint": None,
                "koForbiddenFor": None,
                # Current board: G7 is gone, only the C3 handicap stone is left.
                "stones": [
                    {"point": "G8", "color": "White"},
                    {"point": "F7", "color": "White"},
                    {"point": "H7", "color": "White"},
                    {"point": "D6", "color": "Black"},
                    {"point": "G6", "color": "White"},
                    {"point": "C5", "color": "Black"},
                    {"point": "C3", "color": "Black"},
                    {"point": "E3", "color": "Black"},
                ],
                "moves": [
                    {"player": "White", "type": "play", "point": "G8"},
                    {"player": "Black", "type": "play", "point": "E3"},
                    {"player": "White", "type": "play", "point": "F7"},
                    {"player": "Black", "type": "play", "point": "C5"},
                    {"player": "White", "type": "play", "point": "H7"},
                    {"player": "Black", "type": "play", "point": "D6"},
                    {"player": "White", "type": "play", "point": "G6"},
                ],
            },
            "limit": {"visits": 1},
        }

        query = build_katago_query(request_body)

        self.assertEqual(len(query["initialStones"]), 2, "a captured handicap stone must not drop the count to N-1")
        self.assertEqual(query["initialStones"], [["B", "G7"], ["B", "C3"]])
        self.assertEqual(
            query["initialPlayer"], "W",
            "with moves, initialPlayer is the first move's color, not nextPlayer",
        )
        self.assertEqual(
            query["moves"],
            [["W", "G8"], ["B", "E3"], ["W", "F7"], ["B", "C5"], ["W", "H7"], ["B", "D6"], ["W", "G6"]],
        )


class EvenGameStaticPositionTest(unittest.TestCase):
    """맞바둑(handicapCount 0 또는 부재)·정적 국면(수 없음)의 차례가 nextPlayer를 따른다.

    This is the general-case counterpart of the handicap-opening check
    above, and the one backlog #90's sabotage check targets directly:
    reverting `initial_player` to the old hard-coded `"B"` fails
    `test_white_to_move_no_moves` even though there is no handicap involved
    at all.
    """

    def test_white_to_move_no_moves(self) -> None:
        request_body = _request(board_size=19, handicap_count=0, moves=[], next_player="White")

        query = build_katago_query(request_body)

        self.assertEqual(query["initialPlayer"], "W")
        self.assertEqual(query["initialStones"], [])

    def test_black_to_move_no_moves(self) -> None:
        # Same static-position path with the other color, so a hard-coded
        # constant of *either* color would be caught by one of these two.
        request_body = _request(board_size=19, handicap_count=0, moves=[], next_player="Black")

        query = build_katago_query(request_body)

        self.assertEqual(query["initialPlayer"], "B")

    def test_handicap_count_absent_falls_back_to_static_inference(self) -> None:
        # An old, pre-#19 client that never sends `handicapCount` at all
        # (not even as 0) must not be treated as a handicap game.
        request_body = _request(board_size=9, handicap_count=None, moves=[], next_player="White")

        query = build_katago_query(request_body)

        self.assertEqual(query["initialPlayer"], "W")
        self.assertEqual(query["initialStones"], [])


if __name__ == "__main__":
    unittest.main()
