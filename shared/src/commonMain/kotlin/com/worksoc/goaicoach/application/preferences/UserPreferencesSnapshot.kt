package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DefaultKomi
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings

/**
 * 착수 이펙트(#145)가 도는 시간 — 커졌다 돌아오는 **왕복 전체**다.
 *
 * ⚠️ **잠정값이다** — 사용자가 *"구현 확인 후 시간을 좀 더 늘릴 여지가 있음"* 을 남겼다. 여기 한 곳만 고친다.
 */
const val PlayEffectMillis: Long = 600L

/** 착수 이펙트가 부풀어 오르는 최대 배율(#145) — 135%. ⚠️ 잠정값: 20%→50%→35%로 실기 피드백을 따라 조정 중이다. */
const val PlayEffectPeakScale: Float = 1.35f

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
    // ⚠️ **#140(2026-09-11)이 랜딩을 없앴다 — 이제 이 값이 곧 신규 사용자의 첫 판이다.**
    // 첫 실행은 묻지 않고(`completeFirstRun`), 첫 판은 이 기본값 그대로 **첫돌이와 호선·집 계가**다.
    // 바꾸면 신규 사용자의 첫 판이 함께 바뀐다 — `FirstRunSetupApplicationTest`가 사용자 결정을 못박는다.
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
    /**
     * "형세 보기" 활성 상태 겸 대국 메뉴의 "매 수마다 형세"(`UiStrings.everyMoveEval`).
     * **기본값은 꺼짐**(2026-09-20 사용자 결정 — 이전엔 켜짐이었다). 대국 시작과 동시에 판 위에
     * 소유권 히트맵이 항상 깔려 있던 것을 끄고, 원하는 사용자만 버튼으로 켜게 한다.
     */
    val showOwnershipOverlay: Boolean = false,
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
    /**
     * **착수 이펙트**(백로그 #145) — 돌이 확정되는 순간 [PlayEffectMillis] 동안 [PlayEffectPeakScale]까지
     * 커졌다가 100%로 돌아온다. **사람 착수와 AI 착수**에 붙고, 무르기·기보 탐색·저장 대국 이어받기에는
     * 붙지 않는다.
     *
     * ⚠️ **기본값은 켜짐**이다 — 없던 것이 생기는 쪽이다.
     */
    val isPlayEffectEnabled: Boolean = true,
    val isBoardMaxSize: Boolean = true,
    /**
     * 이 스냅샷이 **어느 설정 세대에 저장됐는가**(백로그 #188).
     *
     * ## ⚠️ 왜 필요했나 — 기본값을 바꿔도 기존 사용자에게 안 미친다
     * 저장된 값이 늘 이기므로, *"최대 탐색 시간 기본값을 3초 → 10초로 올린다"* 같은 결정이
     * **이미 한 번이라도 저장한 사용자에게는 아무 일도 하지 않는다.** 이 값이 그 구멍을 메운다 —
     * 읽을 때 세대가 낮으면 **그 세대에서 뜻이 바뀐 필드만** 기본값으로 덮고 세대를 올린다.
     *
     * ## ⚠️ 전체 초기화가 아니다
     * 사용자가 의도적으로 고른 언어·글꼴 배율·판 크기는 **지킨다.** 되돌리는 것은
     * [settingsMigrationsFor]가 세대별로 이름을 적어 둔 필드뿐이다.
     *
     * ## ⚠️ `ReleaseResetCoordinator`와 **다른 축**이다(함정 6)
     * 그쪽은 **권한 저장소 넷**을 지운다. 이것은 설정 마이그레이션이다 — 하나로 묶으면 설정을
     * 고칠 때마다 권한이 날아간다.
     *
     * ## ⚠️⚠️ 자동저장에 반드시 배선할 것(함정 2)
     * `buildUserPreferencesAutosaveSnapshot`이 스냅샷을 **새로 만든다.** 여기서 `current`의 값을
     * 이어 붙이지 않으면 저장할 때마다 세대가 초기값으로 돌아가 **마이그레이션이 매번 다시 돌고,
     * 사용자가 고른 값이 계속 되돌려진다.** `appFontScale`·`hasSeenOnboarding`이 같은 자리다.
     */
    val settingsSchemaGeneration: Int = CurrentSettingsSchemaGeneration,
)
