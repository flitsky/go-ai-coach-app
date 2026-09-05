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
    // 기본값은 **호선(0)** 이다(2026-08-31, 백로그 #52).
    //
    // 2026-08-18에는 "초심자 진입 난이도를 낮춘다"는 이유로 그 판의 최대 접바둑
    // (`boardSize.maxHandicapCount`, 13x13에서 5)을 기본값으로 뒀다. 그 역할은 이제
    // **첫 실행 랜딩(#51)이 가져갔다** — 실력을 직접 묻고 그 답에 따라 5점/3점/호선/후수를
    // 배정한다. 묻지도 않은 사용자(랜딩을 건너뛴 경우)에게까지 최대 접바둑을 얹는 것은
    // 과했고, 그것이 이 항목의 출발점이었다.
    //
    // ⚠️ **저장소 디코드 폴백과 값이 어긋나 있었다.** `UserPreferencesStore`는 키가 없으면
    // `optInt("handicapCount", 0)`으로 **0**을 쓰는데 이 기본값은 5였다 — "저장 파일이 아예
    // 없으면 5, 키만 빠졌으면 0"이라는 두 기본값이 공존했다. 0으로 맞추면서 그 불일치도
    // 함께 없앴다. **둘은 같은 값이어야 한다**(`UserPreferencesStoreTest`가 고정한다).
    //
    // 참고: handicapCount > 0인 대국은 `GameState.withHandicap()`이 nextPlayer를 White로
    // 시작한다(접바둑은 백이 먼저 둠). 기본값이 0이 되면서 첫 수는 다시 Black이다.
    val handicapCount: Int = 0,
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
    /**
     * 앱 글꼴 배율(백로그 #81). **시스템 배율을 따르지 않고 이 값을 쓴다** — 사유와 접근성 비용은
     * [DefaultAppFontScale]의 KDoc에 있다.
     *
     * ⚠️ **오토세이브가 관리하지 않는 필드다** — `buildUserPreferencesAutosaveSnapshot`이
     * `hasSeenOnboarding`처럼 `current`에서 이어 붙여야 한다. 배선하지 않으면 대국 설정을 한 번만
     * 바꿔도 **다음 저장에서 조용히 1.0으로 돌아간다**(함정 2번, 과거 실제 버그).
     */
    val appFontScale: Float = DefaultAppFontScale,
    val isPlayHapticEnabled: Boolean = true,
    val isBoardMaxSize: Boolean = true,
    val isPlayMagnifierEnabled: Boolean = true,
    /** 돋보기 창 크기 배수(백로그 #85). 값 목록과 기본값은 [MagnifierSettings]가 갖는다. */
    val magnifierSizeScale: Float = MagnifierSettings.defaultSizeScale,
    /** 돋보기 확대 배율(백로그 #85). `1.0`은 판과 같은 크기 — 확대는 없어도 손가락 가림은 해소된다. */
    val magnifierZoom: Float = MagnifierSettings.defaultZoom,
)
