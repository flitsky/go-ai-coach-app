package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.shared.domain.Ruleset

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
    val isPlayEffectEnabled: Boolean = true,
    val isBoardMaxSize: Boolean = true,
)

/**
 * [current]에서 읽어온, 이 오토세이브가 관리하지 않는 필드([UserPreferencesSnapshot
 * .hasSeenOnboarding], [UserPreferencesSnapshot.appFontScale])를 그대로 보존한다.
 * 그렇지 않으면(과거 실제 버그였음) 매 오토세이브마다 데이터 클래스 기본값으로 덮어써져,
 * 예를 들어 온보딩을 이미 마친 사용자가 대국 설정을 한 번만 바꿔도 다음 실행 때 온보딩
 * 화면이 다시 뜨는 회귀가 생긴다.
 *
 * ⚠️ **여기 이어 붙이는 필드가 곧 카나리아 목록이다** — `UserPreferencesApplicationTest
 * .autosaveRunnerPreservesFieldsItDoesNotManage`가 이 둘을 지킨다. 새 필드를 스냅샷에 넣고
 * 이 `copy`에 배선하지 않으면 **조용히 기본값으로 돌아간다**(함정 2번). ⚠️ 반대로 이 목록에서
 * 필드를 **빼는** 항목(#73이 `gameSetupUxMode`를 그렇게 지웠다)은 카나리아를 하나 줄이는 것이므로,
 * 남은 수가 둘 이하가 되면 대체 카나리아를 함께 마련할 것.
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
        isPlayEffectEnabled = request.isPlayEffectEnabled,
        isBoardMaxSize = request.isBoardMaxSize,
    ).copy(
        hasSeenOnboarding = current.hasSeenOnboarding,
        // ⚠️ 글꼴 배율도 이 오토세이브가 관리하지 않는다(백로그 #81) — 빼면 사용자가 배율을
        // 바꿔 놓고 대국 설정을 한 번 만지는 순간 조용히 1.0으로 돌아간다.
        appFontScale = current.appFontScale,
        // ⚠️ **설정 세대도 오토세이브가 관리하지 않는다**(백로그 #188) — 빼면 저장할 때마다
        // 세대가 기본값(현재 세대)으로 덮여, **마이그레이션이 이미 돈 것처럼 보인다.** 그러면
        // 아직 안 돈 사용자에게 영영 안 돌거나, 반대로 매번 다시 돌아 사용자가 고른 값을 계속
        // 되돌린다. 이 한 줄이 그 둘을 함께 막는다.
        settingsSchemaGeneration = current.settingsSchemaGeneration,
    )

fun runUserPreferencesAutosave(
    request: UserPreferencesAutosaveRequest,
    store: UserPreferencesStorePort,
) {
    val current = store.load()
    store.save(buildUserPreferencesAutosaveSnapshot(request, current))
}
