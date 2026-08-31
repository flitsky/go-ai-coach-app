package com.worksoc.goaicoach.ui

import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 백로그 #53 — 업데이트 상태 판정.
 *
 * ⚠️ **이 그물이 다른 항목보다 중요하다.** Play In-App Update는 **Play로 설치된 앱에서만**
 * 동작해서, 에뮬레이터로는 [AppUpdateStatus.Unknown] 경로밖에 볼 수 없다. "새 버전 있음"이
 * 뜨는 모습을 실기로 확인할 방법이 없으므로, **판정만이라도 여기서 고정해 둔다.**
 */
class AppUpdateUiStateTest {

    @Test
    fun anAvailableUpdateIsReportedAsAvailable() {
        assertEquals(
            AppUpdateStatus.Available,
            appUpdateStatusOf(UpdateAvailability.UPDATE_AVAILABLE),
        )
    }

    /**
     * ⚠️ 이 값을 "업데이트 없음"으로 접으면 안 된다. **"이미 시작된 업데이트가 진행 중"** 이라는
     * 뜻이라 새 버전이 있다는 사실은 그대로 참이고, 스토어로 보내면 그 진행 상황이 보인다.
     */
    @Test
    fun anUpdateAlreadyInProgressStillCountsAsAvailable() {
        assertEquals(
            AppUpdateStatus.Available,
            appUpdateStatusOf(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS),
        )
    }

    @Test
    fun noUpdateMeansUpToDate() {
        assertEquals(
            AppUpdateStatus.UpToDate,
            appUpdateStatusOf(UpdateAvailability.UPDATE_NOT_AVAILABLE),
        )
    }

    /**
     * ⚠️ **모르는 값을 "최신"으로 단정하지 않는다.** 업데이트가 있는데 없다고 말하는 쪽이
     * 반대보다 나쁘다 — 모르면 스토어 링크로 폴백해 사용자가 직접 보게 한다.
     */
    @Test
    fun anyUnrecognisedValueFallsBackToUnknownRatherThanUpToDate() {
        assertEquals(AppUpdateStatus.Unknown, appUpdateStatusOf(UpdateAvailability.UNKNOWN))
        // 라이브러리가 나중에 상수를 늘려도 이 갈래로 떨어져야 한다.
        listOf(-1, 4, 99).forEach { unexpected ->
            assertEquals(
                "알 수 없는 값 $unexpected",
                AppUpdateStatus.Unknown,
                appUpdateStatusOf(unexpected),
            )
        }
    }

    /** 화면이 그리는 갈래가 넷이라는 것 자체를 고정한다 — 하나 늘면 `AppUpdateRow`도 같이 봐야 한다. */
    @Test
    fun theRowHasExactlyFourStatesToDraw() {
        assertEquals(4, AppUpdateStatus.entries.size)
    }
}
