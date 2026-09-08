package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.guide.GuideProgress
import com.worksoc.goaicoach.application.guide.GuideStep

/**
 * 4계층 — **첫돌이 가이드가 어디까지 갔는지**만 담는다(백로그 #128).
 *
 * ## ⚠️ 왜 `UserPreferencesSnapshot`에 필드를 더하지 않았는가
 *
 * 그쪽 자동저장은 스냅샷을 **통째로 새로 만든다**(`UserPreferencesAutosaveApplication`) — 그 흐름에
 * 배선되지 않은 필드는 저장 시점에 **조용히 초기값으로 되돌아간다.** `hasSeenOnboarding`이 실제로
 * 그렇게 샜고(설정을 건드리면 온보딩이 다시 떴다), `DeveloperModeStore`·`UiLanguageStore`가 각자
 * **착수 사유로 같은 문장을 KDoc에 적어 두었다.** 이 저장소도 그 셋과 같은 이유로 따로 선다.
 *
 * ## ⚠️ prefs 파일명의 `go_ai_coach_` 접두사는 필수다
 *
 * `DeveloperModeResetCoordinator`가 **접두사로 훑어** 초기화한다(기기 식별자만 남긴다). 접두사 밖에
 * 두면 개발자 초기화가 가이드를 지우지 않아 **최초 설치 상태를 다시 만들 길이 사라진다** — 계측
 * 테스트가 없는 이 기능에서 그 경로가 유일한 재검증 수단이다.
 *
 * ## ⚠️ 정식 릴리즈 초기화(#63)는 이 저장소를 지우지 않는다
 *
 * 가이드 기록은 **권한이 아니라 취향**이다(8.3-2의 가르는 기준). 테스트 기간에 가이드를 본 사람에게
 * 정식 출시에서 그것을 다시 보여줄 이유가 없다. `LayeringContractTest`의 spared 목록에 이름을
 * 올려 두었으니, 나중에 누가 `ReleaseResetCoordinator`에 이 이름을 넣으면 **두 단언이 함께 빨개진다.**
 *
 * ## `apply()`를 쓴다 — `ReleaseResetStore`와 반대다
 *
 * 그쪽은 초기화 마커라 프로세스가 죽으면 초기화가 한 번 더 돌아 `commit()`을 썼다. 여기서 유실의
 * 대가는 **안내를 한 번 더 보는 것**뿐이라 메인 스레드를 막을 이유가 없다.
 */
internal class GuideProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun load(): GuideProgress = GuideProgress(
        armed = prefs.getBoolean(ArmedKey, false),
        // ⚠️ `getStringSet`이 돌려주는 집합을 그대로 들고 있지 말 것 — 안드로이드 문서가 그 인스턴스를
        // 수정하지 말라고 못박고, 구현이 내부 집합을 그대로 돌려주는 버전도 있다. 사본을 만든다.
        seenSteps = prefs.getStringSet(SeenStepsKey, emptySet())?.toSet() ?: emptySet(),
    )

    /** 랜딩을 **끝냈다**(완료·건너뛰기 두 갈래 모두). 이 시점부터 자동 재생이 무장된다. */
    fun arm() {
        prefs.edit().putBoolean(ArmedKey, true).apply()
    }

    fun markSeen(step: GuideStep) {
        val next = load().seenSteps + step.id
        prefs.edit().putStringSet(SeenStepsKey, next).apply()
    }

    /**
     * 개발자 도구가 최초 설치 상태를 다시 만들 때 쓴다.
     *
     * ⚠️ **설정·개발자 섹션 파일에서 `GuideProgressStore(context).clear()`를 직접 부르지 말 것** —
     * `DeveloperSectionTierContractTest`가 그 두 파일에서 `Store(context).clear()` 문자열을 금지한다
     * (저장소를 화면이 직접 비우는 것을 막는 그물이다). 부를 일이 있으면 가이드 쪽이 노출하는
     * `resetFirstRunGuide(context)`를 통할 것.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_guide"
        const val ArmedKey = "armed"
        const val SeenStepsKey = "seen_steps"
    }
}
