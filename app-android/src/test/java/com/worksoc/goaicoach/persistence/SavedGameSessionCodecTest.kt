package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameSessionCodecTest {
    @Test
    fun roundTripRestoresMoveHistoryAndPlayerSetup() {
        val gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.Black))
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Human,
                humanGameType = HumanGameType.Teaching,
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            ),
        )
        val snapshot = SavedGameSnapshot(
            gameState = gameState,
            playerSetup = setup,
            playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            topMovesEnabled = true,
            savedAtMillis = 1234L,
        )

        val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(snapshot))

        assertEquals(gameState, restored?.gameState)
        assertEquals(setup, restored?.playerSetup)
        assertEquals(PlayLevelSetting(PlayLevelGroup.Beginner, level = 4), restored?.playLevel)
        assertEquals(true, restored?.topMovesEnabled)
        assertEquals(1234L, restored?.savedAtMillis)
    }

    /**
     * 덤은 점수 계산의 입력이다 — 이어하기에서 유실되면 승패가 뒤집힌다. 기본값(6.5)이
     * 아닌 값을 양쪽으로 하나씩 골라 왕복을 단언한다(2026-09-23 회귀).
     */
    @Test
    fun roundTripPreservesNonDefaultKomi() {
        for (komi in listOf(0.5, 7.5)) {
            val gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese, komi = komi)
                .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            val snapshot = SavedGameSnapshot(
                gameState = gameState,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            )

            val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(snapshot))

            assertEquals(komi, restored?.gameState?.komi)
            assertEquals(gameState, restored?.gameState)
        }
    }

    /** 접바둑(핸디캡) 경로도 같은 replay 호출을 타므로 함께 고정한다. */
    @Test
    fun roundTripPreservesKomiOnHandicapGame() {
        val gameState = GameState.withHandicap(BoardSize.Nine, Ruleset.Japanese, handicapCount = 2, komi = 0.5)
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val snapshot = SavedGameSnapshot(
            gameState = gameState,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 1L,
        )

        val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(snapshot))

        assertEquals(0.5, restored?.gameState?.komi)
        assertEquals(gameState, restored?.gameState)
    }

    /**
     * komi 키가 없던 **옛 저장분**은 지금까지 6.5로 복원돼 왔다. 스키마 번호를 올리지 않고
     * 흡수하기로 했으므로, 키가 없는 JSON은 여전히 6.5여야 한다.
     */
    @Test
    fun legacyJsonWithoutKomiFallsBackToDefault() {
        val gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val encoded = JSONObject(
            SavedGameSessionCodec.encode(
                SavedGameSnapshot(
                    gameState = gameState,
                    playerSetup = PlayerSetup(),
                    playLevel = PlayLevelSetting(),
                    topMovesEnabled = false,
                    savedAtMillis = 1L,
                ),
            ),
        ).apply { remove("komi") }.toString()

        val restored = SavedGameSessionCodec.decode(encoded)

        assertEquals(DefaultKomi, restored?.gameState?.komi)
    }

    // ------------------------------------------- 종국 판정의 접바둑 보정(refactor backlog #89)

    /** 면적계가 접바둑 판정의 보정 N이 이어하기(종국 팝업 복원)에서 유실되지 않는다. */
    @Test
    fun finalScoreJudgementRoundTripKeepsTheWhiteHandicapBonus() {
        val judgement = chineseHandicapJudgement(whiteHandicapBonus = 2.0)

        val restored = SavedGameSessionCodec.decode(SavedGameSessionCodec.encode(finishedSnapshot(judgement)))

        assertEquals(judgement, restored?.finalScoreJudgement)
        assertEquals(2.0, restored?.finalScoreJudgement?.whiteHandicapBonus ?: -1.0, 0.0)
    }

    /**
     * **옛 저장본 → 새 코드**: 키가 없던 판정은 0으로 읽힌다. 그 판정은 보정 없이 계가됐으므로
     * 0이어야 백 합계(`whiteAreaWithKomi`)와 맞는다 — 결과 팝업이 없던 보정을 지어내지 않는다.
     *
     * 판정은 **기록된 그대로**다(이관하지 않는다, #89) — 같은 국면을 고치기 전 코드가 저장한
     * 흑 0.5 승(백 합계 43.5)은 흑 0.5 승으로 돌아온다.
     */
    @Test
    fun legacyFinalScoreJudgementWithoutTheBonusKeyReadsAsZeroAndKeepsItsRecordedResult() {
        val recordedBeforeTheFix = chineseHandicapJudgement(whiteHandicapBonus = 0.0)
            .copy(winner = StoneColor.Black, margin = 0.5)
        val encoded = JSONObject(SavedGameSessionCodec.encode(finishedSnapshot(recordedBeforeTheFix)))
        encoded.getJSONObject("finalScoreJudgement").remove("whiteHandicapBonus")

        val restored = SavedGameSessionCodec.decode(encoded.toString())

        assertEquals(0.0, restored?.finalScoreJudgement?.whiteHandicapBonus ?: -1.0, 0.0)
        assertEquals(recordedBeforeTheFix, restored?.finalScoreJudgement)
        assertEquals(43.5, restored?.finalScoreJudgement?.whiteAreaWithKomi ?: -1.0, 0.0)
    }

    /**
     * **새 저장본 → 옛 코드**: 필드를 더했을 뿐 **스키마 번호는 그대로**이고(함정 69), 판정 JSON은
     * 예전 키를 하나도 빼거나 바꾸지 않고 `whiteHandicapBonus` 하나만 **더했다**. 옛 decode는 키를
     * 이름으로 `optXxx`해 꺼낼 뿐 모르는 키를 보지 않으므로 이 저장본을 그대로 읽는다(보정만 모른 채).
     * 그 성질 — "모르는 키가 섞여도 판정을 읽는다" — 을 지금의 decode로 함께 고정한다.
     */
    @Test
    fun theBonusKeyIsAPureAdditionThatAnOlderDecoderCanIgnore() {
        val encoded = JSONObject(SavedGameSessionCodec.encode(finishedSnapshot(chineseHandicapJudgement(whiteHandicapBonus = 2.0))))

        assertEquals(1, encoded.getInt("schema"))
        val judgementKeys = encoded.getJSONObject("finalScoreJudgement").keys().asSequence().toSet()
        assertEquals(
            setOf(
                "winner", "margin", "ruleset", "isEstimatedDisplay", "removedBlack", "removedWhite",
                "blackArea", "whiteAreaWithKomi", "capturedByBlack", "capturedByWhite", "komi", "handicapCount",
            ) + "whiteHandicapBonus",
            judgementKeys,
        )

        encoded.getJSONObject("finalScoreJudgement").put("someKeyFromTheFuture", 42)
        val restored = SavedGameSessionCodec.decode(encoded.toString())
        assertEquals(chineseHandicapJudgement(whiteHandicapBonus = 2.0), restored?.finalScoreJudgement)
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(SavedGameSessionCodec.decode("{broken"))
    }

    @Test
    fun onlyUnfinishedGamesAreResumable() {
        assertFalse(
            SavedGameSnapshot(
                gameState = GameState.empty(),
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            ).isResumable,
        )

        val unfinished = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        assertTrue(
            SavedGameSnapshot(
                gameState = unfinished,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            ).isResumable,
        )

        val endedByPasses = unfinished
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
        assertFalse(
            SavedGameSnapshot(
                gameState = endedByPasses,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                savedAtMillis = 1L,
            ).isResumable,
        )
    }

    /**
     * #89 재현 국면의 판정 — 흑 44 대 백 37 + 덤 6.5 + 보정 [whiteHandicapBonus], 백 1.5 승.
     * 보정 0이면 백 합계는 43.5다(승패 필드는 호출부가 필요하면 `copy`로 바꾼다).
     */
    private fun chineseHandicapJudgement(whiteHandicapBonus: Double): FinalScoreJudgement =
        FinalScoreJudgement(
            winner = StoneColor.White,
            margin = 1.5,
            ruleset = Ruleset.Chinese,
            isEstimatedDisplay = false,
            removedBlack = 0,
            removedWhite = 0,
            blackArea = 44.0,
            whiteAreaWithKomi = 43.5 + whiteHandicapBonus,
            capturedByBlack = 0,
            capturedByWhite = 0,
            komi = 6.5,
            handicapCount = 2,
            whiteHandicapBonus = whiteHandicapBonus,
        )

    private fun finishedSnapshot(judgement: FinalScoreJudgement): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = GameState.withHandicap(BoardSize.Nine, Ruleset.Chinese, handicapCount = 2, komi = 6.5)
                .play(Move.Pass(StoneColor.White))
                .play(Move.Pass(StoneColor.Black)),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 1L,
            finalScoreJudgement = judgement,
        )
}
