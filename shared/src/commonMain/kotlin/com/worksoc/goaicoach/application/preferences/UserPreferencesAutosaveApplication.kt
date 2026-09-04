package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.shared.Ruleset

data class UserPreferencesAutosaveRequest(
    val settingsState: GameSessionSettingsState,
    val ruleset: Ruleset,
    val komi: Double,
    val showCoordinates: Boolean,
    val showMoveNumbers: Boolean,
    val showLastMoveRing: Boolean,
    val showOwnershipOverlay: Boolean,
    val isDirectPlayEnabled: Boolean,
    val showMoveReview: Boolean = false,
    val isPlayHapticEnabled: Boolean = true,
    val isBoardMaxSize: Boolean = true,
    val isPlayMagnifierEnabled: Boolean = true,
)

/**
 * [current]에서 읽어온, 이 오토세이브가 관리하지 않는 필드([UserPreferencesSnapshot
 * .hasSeenOnboarding], [UserPreferencesSnapshot.gameSetupUxMode],
 * [UserPreferencesSnapshot.appFontScale])를 그대로 보존한다.
 * 그렇지 않으면(과거 실제 버그였음) 매 오토세이브마다 데이터 클래스 기본값으로 덮어써져,
 * 예를 들어 온보딩을 이미 마친 사용자가 대국 설정을 한 번만 바꿔도 다음 실행 때 온보딩
 * 화면이 다시 뜨는 회귀가 생긴다 — 설정 화면의 개발자 토글로 바꾼 대국설정 UX 모드도
 * 마찬가지로 다음 오토세이브에 조용히 되돌아갈 수 있었다.
 */
internal fun buildUserPreferencesAutosaveSnapshot(
    request: UserPreferencesAutosaveRequest,
    current: UserPreferencesSnapshot,
): UserPreferencesSnapshot =
    buildUserPreferencesSnapshot(
        settingsState = request.settingsState,
        ruleset = request.ruleset,
        komi = request.komi,
        showCoordinates = request.showCoordinates,
        showMoveNumbers = request.showMoveNumbers,
        showLastMoveRing = request.showLastMoveRing,
        showOwnershipOverlay = request.showOwnershipOverlay,
        isDirectPlayEnabled = request.isDirectPlayEnabled,
        showMoveReview = request.showMoveReview,
        isPlayHapticEnabled = request.isPlayHapticEnabled,
        isBoardMaxSize = request.isBoardMaxSize,
        isPlayMagnifierEnabled = request.isPlayMagnifierEnabled,
    ).copy(
        hasSeenOnboarding = current.hasSeenOnboarding,
        gameSetupUxMode = current.gameSetupUxMode,
        // ⚠️ 글꼴 배율도 이 오토세이브가 관리하지 않는다(백로그 #81) — 빼면 사용자가 배율을
        // 바꿔 놓고 대국 설정을 한 번 만지는 순간 조용히 1.0으로 돌아간다.
        appFontScale = current.appFontScale,
    )

fun runUserPreferencesAutosave(
    request: UserPreferencesAutosaveRequest,
    store: UserPreferencesStorePort,
) {
    val current = store.load()
    store.save(buildUserPreferencesAutosaveSnapshot(request, current))
}
