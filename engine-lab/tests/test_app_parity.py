"""실험실의 앱 대응표(`lab/app_parity.py`)가 **지금 앱 소스와 같은가** — 앱 소스 글자를 읽어 비교한다.

빨개지면: 앱이 바뀌었다. 실험실 표를 앱에 맞추고, 그 값으로 잰 옛 실험 결과는 「옛 앱 기준」임을 실험 README에 적는다.
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402


def source(key: str) -> str:
    return (paths.REPO_ROOT / ap.SOURCES[key]).read_text(encoding="utf-8")


class AppParityTest(unittest.TestCase):
    def test_sources_exist(self) -> None:
        for key, rel in ap.SOURCES.items():
            self.assertTrue((paths.REPO_ROOT / rel).is_file(), f"{key}: {rel} 없음 — 앱 파일이 옮겨졌다")

    def test_fast_beginner_budget(self) -> None:
        text = source("play_level")
        match = re.search(r"FastBeginner\(\s*label = .*?visits = (\d+),.*?candidateCount = (\d+),", text, re.S)
        self.assertIsNotNone(match)
        self.assertEqual(int(match.group(1)), ap.FAST_BEGINNER_VISITS)
        self.assertEqual(int(match.group(2)), ap.FAST_BEGINNER_CANDIDATE_COUNT)

    def test_fast_beginner_tiers(self) -> None:
        text = source("play_level")
        marker = "FastBeginner -> when (safeLevel)"
        start = text.index(marker)
        # ⚠️ "Beginner -> when"은 "FastBeginner -> when" 안에도 있다 — 그 표지 **뒤**에서 찾는다.
        block = text[start : text.index("Beginner -> when (safeLevel)", start + len(marker))]
        found = re.findall(
            r"worstPercent = (\d+),\s*midPercent = (\d+),\s*bestPercent = (\d+),\s*tierName = \"(.+?)\"", block
        )
        expected = [(str(t.worst_percent), str(t.mid_percent), str(t.best_percent), t.name) for t in ap.TIERS if not t.best_only]
        self.assertEqual(found, expected)
        self.assertIn("else -> MoveSelectionPolicy.BestOnly", block)
        self.assertTrue(ap.TIERS[-1].best_only)

    def test_bucket_rule_is_unchanged(self) -> None:
        text = source("play_level")
        # 최하 목표 = ceil(worst% × k) 경계 통과, 폴백 = 최하 → 중간 → 최선
        self.assertIn("ceil(worstPercent * ownMoveIndex / 100.0)", text)
        self.assertIn("ceil(worstPercent * (ownMoveIndex + 1) / 100.0)", text)
        self.assertIn(
            "CandidateBucket.Worst -> listOf(CandidateBucket.Worst, CandidateBucket.Mid, CandidateBucket.Best)", text
        )

    def test_search_time_limits(self) -> None:
        text = source("search_time")
        values = re.findall(r"\b\w+\(maximumMillis = (null|[\d_]+)L?\)", text)
        parsed = tuple(None if v == "null" else int(v.replace("_", "")) for v in values)
        self.assertEqual(parsed, ap.SEARCH_TIME_LIMITS_MS)
        default = re.search(r"DefaultSearchTimeLimit: SearchTimeLimit = SearchTimeLimit\.(\w+)", text).group(1)
        names = re.findall(r"\b(\w+)\(maximumMillis = (?:null|[\d_]+)L?\)", text)
        self.assertEqual(parsed[names.index(default)], ap.DEFAULT_SEARCH_TIME_MS)

    def test_gtp_startup_overrides(self) -> None:
        bootstrap = source("engine_bootstrap")
        runtime = source("process_runtime")
        for key, value in ap.GTP_STARTUP_OVERRIDES.items():
            needle = f'"{key}" to "{value}"'
            self.assertTrue(needle in bootstrap or needle in runtime, f"{needle} 가 앱에 없다")
        self.assertIn(f"const val DefaultAnalysisSearchThreads = {ap.ANALYSIS_SEARCH_THREADS}", runtime)
        self.assertIn(f'"numAnalysisThreads" to "{ap.ANALYSIS_NUM_ANALYSIS_THREADS}"', runtime)

    def test_game_defaults(self) -> None:
        prefs = source("preferences")
        self.assertIn(f"boardSize: BoardSize = BoardSize.{ {9: 'Nine', 13: 'Thirteen', 19: 'Nineteen'}[ap.DEFAULT_BOARD_SIZE] }", prefs)
        self.assertIn(f"ruleset: Ruleset = Ruleset.{ap.DEFAULT_RULES.capitalize()}", prefs)
        self.assertIn(f"const val DefaultKomi = {ap.DEFAULT_KOMI}", source("board_models"))

    def test_gtp_search_command(self) -> None:
        protocol = source("protocol")
        self.assertIn("kata-search_analyze ${player.toGtpColor()} $centiseconds", protocol)
        self.assertIn("((timeMillis + 9) / 10).coerceAtLeast(1)", protocol)
        self.assertIn('"kata-set-param maxTime"', protocol)
        parser = source("parser")
        self.assertIn("private const val RootOwnVisit = 1", parser)
        self.assertIn("val rawPointLoss = topScoreLead - scoreLead", parser)

    def test_selection_uses_scored_plays(self) -> None:
        text = source("selection_policy")
        self.assertIn("candidate.pointLoss != null", text)
        self.assertIn("candidate.move is Move.Play", text)
        self.assertIn("if (playLevel.selectionPolicy is MoveSelectionPolicy.BestOnly) 1 else baseLimit.candidateCount", text)

    def test_lost_game_conduct(self) -> None:
        """국면의 경계와 기권 제안·통과의 숫자 — E10 보조(`phases.py`)가 이 값으로 기보를 건다."""
        text = source("hopeless_position")

        def constant(name: str) -> float:
            match = re.search(rf"const val {name}: (?:Double|Int) = ([0-9.]+)", text)
            self.assertIsNotNone(match, f"{name} 상수가 없다 — 이름이 바뀌었다")
            return float(match.group(1))

        self.assertEqual(constant("MiddleStartsAt"), ap.PHASE_MIDDLE_STARTS_AT)
        self.assertEqual(constant("LateStartsAt"), ap.PHASE_LATE_STARTS_AT)
        self.assertEqual(constant("EndgameStartsAt"), ap.PHASE_ENDGAME_STARTS_AT)
        self.assertEqual(constant("MiddleDeficitShare"), ap.RESIGN_MIDDLE_DEFICIT_SHARE)
        self.assertEqual(constant("LateDeficitShare"), ap.RESIGN_LATE_DEFICIT_SHARE)
        self.assertEqual(constant("ReadingsBeforeOffer"), ap.RESIGN_READINGS_BEFORE_OFFER)
        self.assertEqual(constant("MaxOwnWinRate"), ap.PASS_MAX_OWN_WIN_RATE)
        self.assertEqual(constant("LostTurnsBeforePass"), ap.PASS_LOST_TURNS)
        # 근거는 **상대가 둔 뒤**의 형세이고, 값마다 제가 재어진 국면의 문턱을 쓴다 — 실험실도 그렇게 건다.
        self.assertIn("state.moves[index].player != player", text)
        self.assertIn("deficitBar(GamePhase.at(moveNumber, state.boardSize), state.boardSize)", text)


if __name__ == "__main__":
    unittest.main()
