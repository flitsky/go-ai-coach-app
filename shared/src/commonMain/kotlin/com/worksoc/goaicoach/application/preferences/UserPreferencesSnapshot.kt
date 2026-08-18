package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DefaultKomi
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings

data class UserPreferencesSnapshot(
    val boardSize: BoardSize = BoardSize.Thirteen,
    val playerSetup: PlayerSetup = PlayerSetup(),
    val ruleset: Ruleset = Ruleset.Japanese,
    // 초심자 진입 난이도를 낮추기 위해 기본값을 이 판 크기의 최대 접바둑으로 둔다
    // (2026-08-18 결정) — boardSize 파라미터를 그대로 참조해서, 나중에 boardSize
    // 기본값이 바뀌거나 다른 판 크기로 생성돼도(예: 19x19는 최대 9) 항상 그 판의
    // 실제 최대값을 따라간다. 기본 좌석 배정(흑=사람/백=AI, MatchPolicy.kt)에서는
    // 접바둑을 받는 쪽이 사람이라 첫 대국을 훨씬 유리하게 시작한다. 부작용:
    // handicapCount > 0인 대국은 GameState.withHandicap()이 nextPlayer를 White로
    // 시작한다(접바둑은 백이 먼저 둠) — 이 기본값 변경 이전에는 항상 Black이었다.
    val handicapCount: Int = boardSize.maxHandicapCount,
    val komi: Double = DefaultKomi,
    val topMovesEnabled: Boolean = false,
    val showCoordinates: Boolean = false,
    val showMoveNumbers: Boolean = false,
    val showLastMoveRing: Boolean = true,
    val showOwnershipOverlay: Boolean = true,
    val autoPlayDelayMillis: Long = AutoPlayDelaySetting.Default.millis,
    val searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    val isDirectPlayEnabled: Boolean = true,
    val showMoveReview: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val gameSetupUxMode: GameSetupUxMode = GameSetupUxMode.Compact,
)
