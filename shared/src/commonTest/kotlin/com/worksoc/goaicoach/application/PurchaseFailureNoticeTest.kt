package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.app.PurchaseFailureNotice
import com.worksoc.goaicoach.application.premium.app.failureNotice
import com.worksoc.goaicoach.application.premium.port.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 결제 실패 안내 계약(백로그 #178). */
class PurchaseFailureNoticeTest {

    private fun notice(reason: PurchaseFailureReason) =
        PurchaseOutcome.NotPurchased(reason).failureNotice()

    /**
     * ⚠️ **이 한 줄이 #178의 이유다.** 결제 시트를 열었다가 스스로 닫은 사람에게 *"구매가
     * 완료되지 않았습니다"* 라는 오류 문구가 뜨고 있었다 — **가장 흔한 경우이면서 가장 잘못된 안내**다.
     */
    @Test
    fun cancellingOnPurposeSaysNothingAtAll() {
        assertEquals(PurchaseFailureNotice.Silent, notice(PurchaseFailureReason.UserCancelled))
    }

    /**
     * ⚠️ **처리 중은 실패가 아니다.** 실패라고 말하면 사용자가 **한 번 더 결제한다**
     * — 계좌이체처럼 나중에 확정되는 결제가 그렇다.
     */
    @Test
    fun aPaymentStillProcessingIsNeverCalledAFailure() {
        assertEquals(PurchaseFailureNotice.Pending, notice(PurchaseFailureReason.Pending))
    }

    /**
     * 사용자가 **한 일이 없고 고칠 것도 없는** 경우들 — 시도가 실패한 게 아니라 **시작조차 못 했다**.
     * (2026-09-19 실측: 에뮬레이터에서 `BillingClient`가 *"In-app billing API version 3 is not
     * supported on this device"* 로 연결을 거부한다.)
     */
    @Test
    fun beingUnableToStartAPurchaseReadsDifferentlyFromFailing() {
        listOf(
            PurchaseFailureReason.BillingUnavailable,
            PurchaseFailureReason.ProductUnavailable,
            PurchaseFailureReason.Unavailable,
        ).forEach { reason ->
            assertEquals(PurchaseFailureNotice.Unavailable, notice(reason), reason.name)
        }
    }

    @Test
    fun anActualErrorStillReadsAsAFailure() {
        assertEquals(PurchaseFailureNotice.Failed, notice(PurchaseFailureReason.PurchaseError))
    }

    @Test
    fun aCompletedPurchaseHasNothingToSay() {
        assertNull(PurchaseOutcome.Purchased.failureNotice())
    }

    /**
     * ⚠️ **새 사유가 생기면 여기서 걸린다.** `when`이 전수라 컴파일은 강제하지만, 새 사유를
     * 아무 갈래에나 넣고 지나가는 것은 막지 못한다 — 갈래마다 **뜻이 다르다**는 것을 못 박는다.
     */
    @Test
    fun everyReasonMapsToExactlyOneNotice() {
        val mapped = PurchaseFailureReason.entries.associateWith { notice(it) }
        assertEquals(PurchaseFailureReason.entries.size, mapped.size)
        assertTrue(
            mapped.values.distinct().size >= 3,
            "모든 사유가 한 갈래로 몰렸다 — 갈래를 나눈 이유가 사라진다(#178).",
        )
    }
}
