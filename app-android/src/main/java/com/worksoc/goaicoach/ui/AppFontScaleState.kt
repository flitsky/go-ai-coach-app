package com.worksoc.goaicoach.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.nextAppFontScale

/**
 * 앱 글꼴 배율의 화면 쪽 보유자(백로그 #81).
 *
 * ## ⚠️ 세션 한정이 아니라 **저장되는 설정**이다
 * 처음에는 개발자 도구용 세션 한정 오버라이드였다. 2026-09-04에 사용자가 *"나중에 이게 실제
 * 기능으로 반영될 여지가 있으니 설정한 값이 유지되게"* 로 바꿨다 — 그래서 값은
 * `UserPreferencesStore`에 남고, 기본값은 [DefaultAppFontScale](**1.0**)이다.
 * · 저장 위치를 `UserPreferencesStore`로 고른 이유: 그것이 "설정·취향"의 자리이고,
 *   **정식 릴리즈 초기화가 건드리지 않는** 저장소다(`ReleaseResetCoordinator`는 권한 저장소 넷만
 *   지운다 — 배율은 권한이 아니다).
 *
 * ## ⚠️ 왜 모듈 내 object인가
 * 배율은 **컴포지션 전체**에 적용돼야 해서 `MainActivity`가 읽고, 바꾸는 곳은 **설정 화면**이다.
 * 그 둘 사이에 `GoCoachApp`이 있는데 그 파일은 라인 예산 880/880으로 여유가 0이라 상태를 통과
 * 시킬 수 없다(함정 3번). `AttendanceClaimReplaySignal`과 같은 처방이다.
 *
 * ⚠️ **`load`를 부르지 않으면 저장값이 무시된다.** `MainActivity`가 컴포지션 시작에서 한 번
 * 부른다 — 그 호출이 빠지면 앱이 매번 1.0으로 뜨고, 저장은 되는데 반영이 안 되는 것처럼 보인다.
 */
internal object AppFontScaleState {

    var scale by mutableStateOf(DefaultAppFontScale)
        private set

    /** 저장값을 화면 상태로 끌어온다. 앱 시작에 한 번. */
    fun load(store: UserPreferencesStorePort) {
        scale = store.load().appFontScale
    }

    /**
     * 다음 배율로 넘기고 **저장한다**.
     *
     * ⚠️ 저장은 read-modify-write다 — 메모리에 든 스냅샷을 덮어쓰면 그 사이 오토세이브가 쓴
     * 대국 설정이 조용히 사라진다(이 저장소가 실제로 그 사고를 낸 자리다, 함정 2번).
     */
    fun cycle(store: UserPreferencesStorePort) {
        val next = nextAppFontScale(scale)
        scale = next
        store.save(store.load().copy(appFontScale = next))
    }
}
