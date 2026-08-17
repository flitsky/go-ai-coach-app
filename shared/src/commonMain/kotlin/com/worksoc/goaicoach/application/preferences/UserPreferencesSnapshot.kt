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
    // (2026-08-18 결정). 기본 좌석 배정(흑=사람/백=AI, MatchPolicy.kt)에서는
    // 접바둑을 받는 쪽이 사람이라 첫 대국을 훨씬 유리하게 시작한다.
    val handicapCount: Int = BoardSize.Thirteen.maxHandicapCount,
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
