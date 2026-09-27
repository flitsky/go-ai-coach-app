package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.HandicapKomi
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **새 대국의 판 조건(판 크기·접바둑·덤)은 설정 한 곳에서 온다**(refactor backlog #94).
 *
 * ## 무엇이 새던가
 * 판 크기와 접바둑은 늘 설정 상태에서 읽었는데 **덤만** 지금 판(`gameState.komi`)에서 읽었다.
 * 로비에서 시작한 판이면 두 값이 같아 드러나지 않는다. 드러나는 것은 지금 판이 로비를 거치지 않았을
 * 때다 — **이어하기·기록에서 분기한 판·앱 시작 때 되살린 끝난 판**. 그 판을 끝까지 두고 「재 대국」을
 * 누르거나 「대국 설정」으로 로비에 가서 시작하면 *"설정의 판 크기·접바둑 + 앞 판의 덤"* 이 섞여,
 * 설정이 19줄·3점·덤 0.5(#93 자동)여도 **3점 + 덤 6.5**가 시작됐다.
 *
 * 앱과 **같은 배선**([wireGoCoachControllers])으로 돌린다 — 컨트롤러가 덤을 어느 게터에서 읽는지는
 * 배선이 정하므로, 컨트롤러만 따로 만들면 이 결함이 보이지 않는다.
 */
class NewGameSettingsSourceTest {

    /**
     * 이어한 판(9줄 호선 덤 6.5)을 끝까지 둔 뒤 새 대국 — 설정(19줄·3점·0.5)대로 시작해야 한다.
     * 이어하기와 분기 대국은 같은 길(`SavedSessionController.restore`)을 탄다(#172).
     */
    @Test
    fun theGameAfterAResumedGameStartsWithTheSettingsKomiNotTheResumedGames() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = TwoHumans, boardSize = BoardSize.Nineteen))
        context.changeSettings { it.applyHandicap(3) }
        val controllers = wireGoCoachControllers(context)

        controllers.savedSessionController.restore(ResumedNineByNineEvenGame)

        // 전제 — 이어한 판은 **그 판의** 조건으로 두고, 설정은 건드리지 않는다(지금도 초록).
        assertEquals(BoardSize.Nine, context.holder.current.gameState.boardSize)
        assertEquals(DefaultKomi, context.holder.current.gameState.komi, 0.0)
        val settings = context.holder.current.settings
        assertEquals(BoardSize.Nineteen, settings.boardSize)
        assertEquals(3, settings.handicapCount)
        assertEquals("접바둑 3점을 고르면 덤이 0.5가 된다(#93)", HandicapKomi, settings.komi, 0.0)

        context.changeCore { it.copy(isGameEnded = true) } // 끝까지 둔 셈 — 「재 대국」·로비 시작이 닿는 상태
        controllers.newGameController.startConfiguredGame()

        val started = context.coreWrites.last().gameState
        assertEquals(BoardSize.Nineteen, started.boardSize)
        assertEquals(3, started.handicapCount)
        assertEquals(
            "이어한 판의 덤(6.5)으로 새 대국이 시작됐다 — 설정은 3점·0.5다. 새 대국은 덤을 설정에서 읽어야 한다(#94).",
            HandicapKomi,
            started.komi,
            0.0,
        )
    }

    /**
     * **지켜야 할 동작** — 로비에서 고른 덤(접바둑 자동 0.5, 손으로 고친 7.5)은 그대로 새 대국에 간다.
     * 고치기 전에도 초록이다(로비에서 바꾸면 미리보기 판의 덤도 함께 바뀌었으므로).
     */
    @Test
    fun theKomiChosenInTheLobbyStillReachesTheNextGame() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = TwoHumans, boardSize = BoardSize.Nineteen))
        context.changeCore { it.copy(isGameEnded = true) }
        val controllers = wireGoCoachControllers(context)

        controllers.settingsController.changeHandicapCount(3)
        controllers.newGameController.startConfiguredGame()

        val handicapGame = context.coreWrites.last().gameState
        assertEquals(3, handicapGame.handicapCount)
        assertEquals("로비에서 접바둑을 고르면 새 대국의 덤이 0.5다(#93)", HandicapKomi, handicapGame.komi, 0.0)

        context.changeCore { it.copy(isGameEnded = true) }
        controllers.settingsController.changeKomi(7.5)
        controllers.newGameController.startConfiguredGame()

        assertEquals("로비에서 고친 덤이 새 대국에 가지 않았다", 7.5, context.coreWrites.last().gameState.komi, 0.0)
    }

    private companion object {
        val TwoHumans = PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = SidePlayerSetup(SeatController.Human))

        /** 설정(19줄·3점·0.5)과 판 크기·접바둑·덤이 전부 다른 판. */
        val ResumedNineByNineEvenGame = SavedGameSnapshot(
            gameState = GameState.withHandicap(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = DefaultKomi)
                .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))),
            playerSetup = TwoHumans,
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 0L,
        )
    }
}
