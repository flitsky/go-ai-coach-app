package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.preferences.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class UserPreferencesApplicationTest {
    @Test
    fun buildsInitialGameAndRuntimeFromSavedPreferences() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 3),
            ),
        )
        val preferences = UserPreferencesSnapshot(
            playerSetup = setup,
            ruleset = Ruleset.Chinese,
            topMovesEnabled = false,
            autoPlayDelayMillis = AutoPlayDelaySetting.Study.millis,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
            boardSize = BoardSize.Nine,
        )

        val plan = buildInitialUserPreferencesPlan(
            preferences = preferences,
            defaultPlayLevel = PlayLevelSetting(),
            currentProfile = EngineProfile(),
        )

        assertEquals(Ruleset.Chinese, plan.gameState.ruleset)
        assertEquals(StoneColor.Black, plan.gameState.nextPlayer)
        assertEquals(setup, plan.playerSetup)
        assertEquals(PlayLevelSetting(PlayLevelGroup.Beginner, level = 3), plan.runtime.playLevel)
        assertEquals(5_000L, plan.runtime.engineProfile.analysisLimit.timeMillis)
        assertEquals(false, plan.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Study, plan.autoPlayDelaySetting)
        assertEquals(
            GameSettings(
                ruleset = Ruleset.Chinese,
                topMovesEnabled = false,
                autoPlayDelaySetting = AutoPlayDelaySetting.Study,
                searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
                boardSize = BoardSize.Nine,
                handicapCount = 0,
            ),
            plan.settings,
        )
    }

    @Test
    fun buildsSnapshotFromCurrentUiSettings() {
        val setup = PlayerSetup()

        val snapshot = buildUserPreferencesSnapshot(
            playerSetup = setup,
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            handicapCount = 5,
            komi = 0.5,
            topMovesEnabled = false,
            showCoordinates = false,
            showMoveNumbers = true,
            showLastMoveRing = false,
            showOwnershipOverlay = false,
            autoPlayDelaySetting = AutoPlayDelaySetting.Short,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds),
        )

        assertEquals(setup, snapshot.playerSetup)
        assertEquals(Ruleset.Chinese, snapshot.ruleset)
        assertEquals(5, snapshot.handicapCount)
        assertEquals(0.5, snapshot.komi, 0.0001)
        assertFalse(snapshot.topMovesEnabled)
        assertFalse(snapshot.showCoordinates)
        assertTrue(snapshot.showMoveNumbers)
        assertFalse(snapshot.showLastMoveRing)
        assertFalse(snapshot.showOwnershipOverlay)
        assertEquals(AutoPlayDelaySetting.Short.millis, snapshot.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds), snapshot.searchTimeSettings)
    }

    @Test
    fun buildsSnapshotFromSessionSettingsState() {
        val setup = PlayerSetup(
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 5),
            ),
        )
        val settingsState = GameSessionSettingsState(
            playerSetup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Study,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.Off),
            topMovesEnabled = true,
            boardSize = BoardSize.Nine,
            handicapCount = 5,
        )

        val snapshot = buildUserPreferencesSnapshot(
            settingsState = settingsState,
            ruleset = Ruleset.Chinese,
            komi = 7.5,
            showCoordinates = true,
            showMoveNumbers = false,
            showLastMoveRing = true,
            showOwnershipOverlay = false,
        )

        assertEquals(setup, snapshot.playerSetup)
        assertEquals(Ruleset.Chinese, snapshot.ruleset)
        assertEquals(5, snapshot.handicapCount)
        assertEquals(7.5, snapshot.komi, 0.0001)
        assertTrue(snapshot.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Study.millis, snapshot.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), snapshot.searchTimeSettings)
        assertTrue(snapshot.showCoordinates)
        assertFalse(snapshot.showMoveNumbers)
        assertTrue(snapshot.showLastMoveRing)
        assertFalse(snapshot.showOwnershipOverlay)
    }

    @Test
    fun autosaveRunnerWritesCurrentPreferencesSnapshot() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            ),
        )
        val settingsState = GameSessionSettingsState(
            playerSetup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Short,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
            topMovesEnabled = true,
            boardSize = BoardSize.Nine,
            handicapCount = 3,
        )
        val store = RecordingUserPreferencesStore()

        runUserPreferencesAutosave(
            request = UserPreferencesAutosaveRequest(
                settingsState = settingsState,
                ruleset = Ruleset.Japanese,
                komi = 6.5,
                showCoordinates = true,
                showMoveNumbers = false,
                showLastMoveRing = true,
                showOwnershipOverlay = true,
                isDirectPlayEnabled = true,
            ),
            store = store,
        )

        val saved = store.saved
        assertEquals(setup, saved.playerSetup)
        assertEquals(Ruleset.Japanese, saved.ruleset)
        assertEquals(3, saved.handicapCount)
        assertEquals(6.5, saved.komi, 0.0001)
        assertTrue(saved.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Short.millis, saved.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinOneSecond), saved.searchTimeSettings)
        assertTrue(saved.showCoordinates)
        assertFalse(saved.showMoveNumbers)
        assertTrue(saved.showLastMoveRing)
        assertTrue(saved.showOwnershipOverlay)
        assertTrue(saved.isDirectPlayEnabled)
    }

    @Test
    fun autosaveRunnerPreservesFieldsItDoesNotManage() {
        // 회귀 방지: 오토세이브는 대국 설정(계가/덤/바둑판 등)만 관리한다. 온보딩 완료 여부나
        // 대국설정 UX 모드처럼 오토세이브가 모르는 필드는, 매번 데이터 클래스 기본값으로
        // 덮어쓰지 않고 store에 이미 저장된 현재 값을 그대로 보존해야 한다.
        val store = RecordingUserPreferencesStore()
        store.save(
            UserPreferencesSnapshot(
                hasSeenOnboarding = true,
                gameSetupUxMode = GameSetupUxMode.Simple,
            ),
        )

        runUserPreferencesAutosave(
            request = UserPreferencesAutosaveRequest(
                settingsState = GameSessionSettingsState(
                    playerSetup = PlayerSetup(),
                    autoPlayDelaySetting = AutoPlayDelaySetting.Default,
                    searchTimeSettings = SearchTimeSettings(),
                    topMovesEnabled = false,
                    boardSize = BoardSize.Thirteen,
                    handicapCount = 0,
                ),
                ruleset = Ruleset.Japanese,
                komi = 6.5,
                showCoordinates = false,
                showMoveNumbers = false,
                showLastMoveRing = true,
                showOwnershipOverlay = true,
                isDirectPlayEnabled = true,
            ),
            store = store,
        )

        assertTrue(store.saved.hasSeenOnboarding)
        assertEquals(GameSetupUxMode.Simple, store.saved.gameSetupUxMode)
    }

    private class RecordingUserPreferencesStore : UserPreferencesStorePort {
        lateinit var saved: UserPreferencesSnapshot

        override fun save(snapshot: UserPreferencesSnapshot) {
            saved = snapshot
        }

        override fun load(): UserPreferencesSnapshot =
            if (::saved.isInitialized) saved else UserPreferencesSnapshot()
    }
}
