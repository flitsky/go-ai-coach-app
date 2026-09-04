package com.worksoc.goaicoach

import android.content.Context
import com.worksoc.goaicoach.application.lifecycle.ReleaseResetPolicy
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.persistence.BotCollectionStore
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.persistence.PremiumStateStore
import com.worksoc.goaicoach.persistence.ReleaseResetStore

/**
 * **정식 릴리즈 초기화**를 앱 시작 시 한 번 적용한다(백로그 #63).
 *
 * 비공개 테스트 기간에 쌓인 권한은 테스트용이었으므로 정식 릴리즈에서 민다(2026-09-01 사용자
 * 확정). 판정은 `shared`의 [ReleaseResetPolicy]가 갖고, 여기서는 안드로이드 저장소를 연결하는
 * **배선만** 한다 — `AttendanceCheckInCoordinator`와 같은 구조다.
 *
 * ### ⚠️ 지우는 것과 남기는 것 — 넷은 반드시 함께 지워야 한다
 *
 * 출석은 회차별 수령 기록(`AttendanceState.claimedTiers`)으로 **한 회차를 한 번만** 지급한다.
 * 그래서 컬렉션만 지우고 출석을 남기면 **7·28일차를 이미 받은 테스터는 캐릭터를 잃고 다시 받을
 * 길도 없다** — 초기화도 유지도 아닌 최악이 된다. 소모품과 프리미엄도 같은 이유로 묶인다
 * (1회권은 출석 보상이고, 무르기 영구 해제는 **3일차** 보상이다).
 *
 * **남기는 것**: 대국 기록·진행 중 대국(사용자 콘텐츠라 권한이 아니다), 설정·언어(취향),
 * 기기 식별자(지우면 진단 로그의 기기 추적이 끊긴다). 근거표는
 * `feature-access-principles/README.md` 8.3-2.
 *
 * ### ⚠️ 반드시 다른 무엇보다 먼저 돌아야 한다
 *
 * [GoAiCoachApplication]에서 `AttendanceCheckInCoordinator`보다 **앞에** 호출한다. 순서가 뒤집히면
 * 그날의 체크인이 먼저 기록됐다가 곧바로 지워져, 사용자가 앱을 켰는데 출석이 안 붙는다.
 */
internal class ReleaseResetCoordinator(private val context: Context) {

    /**
     * 필요하면 초기화하고, **실제로 지운 것이 있었는지** 돌려준다.
     *
     * ⚠️ 반환값이 "초기화가 돌았는가"가 아닌 것이 중요하다 — 초기화는 신규 설치에서도 돌기
     * 때문에(지울 것이 없어 무해하다) 그걸 기준으로 안내하면 **처음 설치한 사람에게 "테스트
     * 기록이 초기화됐다"고 알리는** 꼴이 된다. 안내는 잃은 사람에게만 간다.
     */
    fun applyIfNeeded(): Boolean {
        val marker = ReleaseResetStore(context)
        if (!ReleaseResetPolicy.shouldReset(marker.lastAppliedGeneration())) return false

        val attendance = AttendanceStore(context)
        val collection = BotCollectionStore(context)
        val consumables = ConsumableInventoryStore(context)
        val premium = PremiumStateStore(context)

        // 지우기 **전에** 물어야 한다. 지운 뒤에는 전부 기본값이라 구분이 사라진다.
        val hadAnything = ReleaseResetPolicy.hasEntitlementData(
            attendance = attendance.load(),
            collection = collection.load(),
            consumables = consumables.load(),
            premium = premium.load(),
        )

        attendance.clear()
        collection.clear()
        consumables.clear()
        premium.clear()

        if (hadAnything) marker.markNoticePending()

        // 마커는 지우기가 끝난 뒤에 쓴다. 먼저 쓰면 그 사이에 프로세스가 죽었을 때 초기화가
        // 통째로 건너뛰어진다 — 반대 순서의 실패(한 번 더 미는 것)가 훨씬 덜 나쁘다.
        marker.markApplied(ReleaseResetPolicy.CurrentResetGeneration)
        return hadAnything
    }
}
