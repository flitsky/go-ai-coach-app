"""실험실 공용 패키지 — KataGo 없이 도는 단위 테스트."""
from __future__ import annotations

import random
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lab import app_parity as ap  # noqa: E402
from lab import human, selection  # noqa: E402
from lab.board import Board, Position, parse_vertex, vertex  # noqa: E402
from lab.gtp import attach_point_loss, parse_candidates, parse_raw_nn, root_visits_estimate  # noqa: E402


class BoardTest(unittest.TestCase):
    def test_vertex_skips_i(self) -> None:
        self.assertEqual(vertex(19, 0, 8), "J19")
        self.assertEqual(parse_vertex(13, "D4"), (9, 3))
        self.assertIsNone(parse_vertex(9, "pass"))
        self.assertEqual(vertex(13, *parse_vertex(13, "K10")), "K10")

    def test_capture_and_ko(self) -> None:
        # 흑이 백 한 점을 따낸 뒤, 백이 곧바로 되따내는 것은 패라서 금지
        pos = Position(
            id="ko",
            size=9,
            moves=[("B", "D5"), ("W", "E5"), ("B", "E6"), ("W", "F6"), ("B", "E4"), ("W", "F4"), ("B", "pass"), ("W", "G5"), ("B", "F5")],
        )
        board = pos.board()
        # F5 흑이 E5 백을 따냈다
        r, c = parse_vertex(9, "E5")
        self.assertIsNone(board.grid[r][c])
        self.assertIsNone(board.try_play("W", (r, c)), "곧바로 되따냄은 패")

    def test_suicide_is_illegal(self) -> None:
        board = Board(9)
        for move in ("A2", "B1"):
            board = board.try_play("B", parse_vertex(9, move))
        self.assertIsNone(board.try_play("W", parse_vertex(9, "A1")))

    def test_phase_and_json(self) -> None:
        pos = Position(id="p", size=13, moves=[("B", "D4")] * 0)
        self.assertEqual(pos.phase, "opening")
        again = Position.from_json(pos.to_json())
        self.assertEqual(again, pos)


SEARCH_RESPONSE = """info move D11 visits 11 edgeVisits 11 utility 0.2 winrate 0.598854 scoreMean 0.48 scoreStdev 9.5 scoreLead 0.481035 scoreSelfplay 0.74 prior 0.673509 lcb 0.57 utilityLcb 0.13 weight 27.9 order 0 pv D11 L8 L3 info move C10 visits 2 edgeVisits 2 utility 0.09 winrate 0.55733 scoreMean 0.32 scoreStdev 9.8 scoreLead 0.324851 scoreSelfplay 0.48 prior 0.182424 lcb 0.02 utilityLcb -1.4 weight 5.4 order 1 pv C10 K11 info move D10 visits 2 edgeVisits 2 utility 0.12 winrate 0.573279 scoreMean 0.44 scoreStdev 9.4 scoreLead 0.447469 scoreSelfplay 0.5 prior 0.05 lcb 0.1 utilityLcb -1.0 weight 5.0 order 2 pv D10 info move C10 visits 1 scoreLead 0.1 order 5 pv C10"""


class ParseTest(unittest.TestCase):
    def test_candidates_match_app_rules(self) -> None:
        cands = attach_point_loss(parse_candidates(SEARCH_RESPONSE, max_candidates=8))
        self.assertEqual([c.move for c in cands], ["D11", "C10", "D10"])  # 같은 수는 방문 많은 줄
        self.assertEqual(cands[0].point_loss, 0.0)
        self.assertAlmostEqual(cands[1].point_loss, 0.481035 - 0.324851, places=6)
        self.assertEqual(len(parse_candidates(SEARCH_RESPONSE, max_candidates=1)), 1)

    def test_root_visits_adds_root_own_visit(self) -> None:
        self.assertEqual(root_visits_estimate(SEARCH_RESPONSE), 11 + 2 + 2 + 1)  # #203

    def test_raw_nn_grid(self) -> None:
        response = "symmetry 0\nwhiteWin 0.52\nwhiteScore 2.3\npolicy\n0.1 NAN 0.2\n0.3 0.1 0.1\n0.05 0.05 0.0\npolicyPass 0.1"
        raw = parse_raw_nn(response, 3)
        self.assertEqual(raw.policy["A3"], 0.1)
        self.assertNotIn("B3", raw.policy)
        self.assertEqual(raw.policy["A1"], 0.05)
        self.assertEqual(raw.pass_prob, 0.1)
        self.assertEqual(raw.fields["whiteWin"], 0.52)


class SelectionTest(unittest.TestCase):
    def test_bucket_ranges(self) -> None:
        self.assertEqual(selection.candidate_bucket_range(1, selection.MID), None)
        self.assertEqual(selection.candidate_bucket_range(2, selection.MID), range(1, 2))
        self.assertEqual(selection.candidate_bucket_range(2, selection.WORST), None)
        self.assertEqual(selection.candidate_bucket_range(8, selection.MID), range(1, 7))
        self.assertEqual(selection.candidate_bucket_range(8, selection.WORST), range(7, 8))

    def test_first_move_targets_worst(self) -> None:
        rng = random.Random(1)
        self.assertEqual(selection.target_bucket(ap.tier(2), 0, rng), selection.WORST)

    def test_beginner_plays_second_when_two_candidates(self) -> None:
        # 후보 2개면 최하 버킷이 없다 → 초보도 2위(중간)를 둔다. 후보 1개면 최선.
        self.assertEqual(selection.rank_distribution(ap.tier(1), 2), [0.0, 1.0])
        self.assertEqual(selection.rank_distribution(ap.tier(1), 1), [1.0])
        self.assertEqual(selection.rank_distribution(ap.tier(1), 8)[7], 1.0)

    def test_distribution_is_probability(self) -> None:
        for t in ap.TIERS:
            for n in range(1, 9):
                dist = selection.rank_distribution(t, n)
                self.assertAlmostEqual(sum(dist), 1.0, places=9)

    def test_sampling_matches_distribution(self) -> None:
        t = ap.tier(3)
        n = 6
        rng = random.Random(7)
        counts = [0] * n
        trials = 0
        for _ in range(400):
            for k in range(100):
                counts[selection.select_rank(t, n, k, rng)] += 1
                trials += 1
        expected = selection.rank_distribution(t, n)
        for got, want in zip(counts, expected):
            self.assertAlmostEqual(got / trials, want, delta=0.01)


class HumanTest(unittest.TestCase):
    def test_full_temperature_one_is_identity(self) -> None:
        probs = {"A": 0.5, "B": 0.3, "C": 0.2}
        out = human.temperature_transform(probs, 1.0, 1.0)
        for move, p in probs.items():
            self.assertAlmostEqual(out[move], p)

    def test_tail_suppression_only_touches_rare_moves(self) -> None:
        probs = {"A": 0.6, "B": 0.395, "C": 0.004, "D": 0.001}
        out = human.temperature_transform(probs, 0.7, 0.01)
        # 1% 넘는 수끼리의 비는 그대로, 드문 수는 더 줄어든다
        self.assertAlmostEqual(out["A"] / out["B"], 0.6 / 0.395, places=6)
        self.assertLess(out["D"] / out["A"], 0.001 / 0.6)

    def test_temperature_cools_faster_on_small_boards(self) -> None:
        t19 = human.move_temperature(human.R2_TAIL, 80, 19)
        t9 = human.move_temperature(human.R2_TAIL, 80, 9)
        self.assertAlmostEqual(t19, 0.70 + 0.15 * 0.5)
        self.assertLess(t9, t19)


if __name__ == "__main__":
    unittest.main()
