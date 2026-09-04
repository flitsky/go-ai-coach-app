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
        // preferences가 handicapCount를 명시하지 않아 기본값(호선 = 0)을 그대로 쓴다 —
        // 접바둑이 없으므로 첫 수는 Black이다(2026-08-31 백로그 #52로 기본값 5 → 0).
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
                // UserPreferencesSnapshot 위에서 handicapCount를 명시하지 않았으므로
                // 기본값(호선 = 0)을 그대로 쓴다 — 판 크기를 따라가던 옛 기본값이 아니다(#52).
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

    /**
     * **전달 누락 그물**(#36). 오토세이브는 `UserPreferencesAutosaveRequest` → 빌더 두 오버로드
     * → `toUserPreferencesSnapshot`을 거치는데, 중간 오버로드가 필드 하나를 **전달하지 않으면**
     * 다음 단계의 기본값이 조용히 이긴다. 파라미터에 기본값이 있으니 컴파일도 통과한다.
     *
     * 실제로 `isPlayHapticEnabled`를 추가할 때 두 번째 오버로드가 전달을 빠뜨려, 토글을 꺼도
     * 앱을 껐다 켜면 다시 켜져 있었다(2026-08-30 실기에서 발견).
     *
     * 그래서 **모든 불리언을 스냅샷 기본값의 반대로** 넣고 전부 살아남는지 본다. 전달을
     * 빠뜨리면 그 필드만 기본값으로 돌아오므로 즉시 실패한다 — 필드가 늘어도 여기에 한 줄씩
     * 추가하기만 하면 같은 그물이 유지된다.
     */
    @Test
    fun autosaveForwardsEveryToggleInsteadOfFallingBackToDefaults() {
        val store = RecordingUserPreferencesStore()

        runUserPreferencesAutosave(
            request = UserPreferencesAutosaveRequest(
                settingsState = GameSessionSettingsState(
                    playerSetup = PlayerSetup(),
                    autoPlayDelaySetting = AutoPlayDelaySetting.Short,
                    searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
                    topMovesEnabled = true,
                    boardSize = BoardSize.Nine,
                    handicapCount = 0,
                ),
                ruleset = Ruleset.Chinese,
                komi = 7.5,
                // 아래는 전부 UserPreferencesSnapshot 기본값의 **반대**다.
                showCoordinates = true,
                showMoveNumbers = true,
                showLastMoveRing = false,
                showOwnershipOverlay = false,
                isDirectPlayEnabled = false,
                showMoveReview = true,
                isPlayHapticEnabled = false,
                isBoardMaxSize = false,
                isPlayMagnifierEnabled = false,
            ),
            store = store,
        )

        val saved = store.saved
        val defaults = UserPreferencesSnapshot()
        val reverted = buildList {
            if (saved.showCoordinates == defaults.showCoordinates) add("showCoordinates")
            if (saved.showMoveNumbers == defaults.showMoveNumbers) add("showMoveNumbers")
            if (saved.showLastMoveRing == defaults.showLastMoveRing) add("showLastMoveRing")
            if (saved.showOwnershipOverlay == defaults.showOwnershipOverlay) add("showOwnershipOverlay")
            if (saved.isDirectPlayEnabled == defaults.isDirectPlayEnabled) add("isDirectPlayEnabled")
            if (saved.showMoveReview == defaults.showMoveReview) add("showMoveReview")
            if (saved.isPlayHapticEnabled == defaults.isPlayHapticEnabled) add("isPlayHapticEnabled")
            // ⚠️ 아래 둘은 #39에서 채웠다 — `isBoardMaxSize`는 그물이 생긴 뒤에 추가된
            // 필드인데 목록에 오르지 않아 **2026-08-31까지 검사 대상이 아니었다.**
            if (saved.isBoardMaxSize == defaults.isBoardMaxSize) add("isBoardMaxSize")
            if (saved.isPlayMagnifierEnabled == defaults.isPlayMagnifierEnabled) add("isPlayMagnifierEnabled")
        }

        // `assertEquals(message, expected, actual)`는 List에서 Double 오버로드로 잡혀
        // 컴파일이 깨진다 — assertTrue로 간다.
        assertTrue(
            reverted.isEmpty(),
            "오토세이브 경로에서 기본값으로 되돌아간 필드가 있다 = 빌더 오버로드 어딘가가 " +
                "전달을 빠뜨렸다: " + reverted.joinToString(", "),
        )
    }

    @Test
    fun autosaveRunnerPreservesFieldsItDoesNotManage() {
        // 회귀 방지: 오토세이브는 대국 설정(계가/덤/바둑판 등)만 관리한다. 온보딩 완료 여부나
        // 글꼴 배율처럼 오토세이브가 모르는 필드는, 매번 데이터 클래스 기본값으로 덮어쓰지 않고
        // store에 이미 저장된 현재 값을 그대로 보존해야 한다.
        //
        // ⚠️ **카나리아가 셋에서 둘로 줄었다**(#73이 `gameSetupUxMode`를 삭제). 남은 둘은 종류가
        // 서로 달라서 그물이 얇아지지는 않았다 — `hasSeenOnboarding`은 **한 번만 참이 되는 플래그**,
        // `appFontScale`은 **사용자가 직접 고르는 값**이다. ⚠️ 여기서 더 줄이는 항목은 대체
        // 카나리아를 함께 마련할 것(`UserPreferencesAutosaveApplication`의 `copy` KDoc에도 적었다).
        val store = RecordingUserPreferencesStore()
        store.save(
            UserPreferencesSnapshot(
                hasSeenOnboarding = true,
                // 백로그 #81 — 글꼴 배율도 오토세이브가 모르는 필드다. ⚠️ 이 카나리아가 특히
                // 중요한 이유: 배율은 **사용자가 직접 고르는 설정**이라, 되돌아가면 곧바로 눈에
                // 보이는 회귀가 된다(대국 설정을 한 번 만지면 글자 크기가 제자리로 돌아간다).
                appFontScale = 1.5f,
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
        assertEquals(
            1.5f,
            store.saved.appFontScale,
            "오토세이브가 글꼴 배율을 기본값으로 되돌렸다 — 사용자가 고른 크기가 대국 설정을 " +
                "한 번 만지는 순간 사라진다(#81, 함정 2번).",
        )
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
