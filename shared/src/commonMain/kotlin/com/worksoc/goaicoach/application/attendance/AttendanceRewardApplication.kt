package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.runPremiumFeatureClaim

/** 1일차(첫 출석) 보상 회차 — 이 회차의 보상은 "무르기 무제한"(`FeatureId.Undo` 영구 클레임)이다. */
const val UndoUnlimitedRewardTier: Int = 1

sealed class AttendanceRewardGrantResult {
    /** 이번 호출로 [tier] 보상을 실제로 지급했다. [state]는 지급 사실이 기록된(=저장된) 출석 상태. */
    data class Granted(val tier: Int, val state: AttendanceState) : AttendanceRewardGrantResult()

    /** 지급할 게 없었다(아직 해당 회차에 도달하지 않았거나, 이미 지급 완료). */
    data class NothingToGrant(val state: AttendanceState) : AttendanceRewardGrantResult()
}

/**
 * 5계층(App Service) — 출석 상태를 보고 **아직 지급하지 않은 보상**을 지급한다.
 * `runAttendanceCheckIn` 직후에 호출하도록 설계됐다(체크인 판정과 보상 지급은 별개 축 —
 * `AttendanceState.claimedTiers` 참고).
 *
 * Phase 1에서 내용이 확정된 보상은 1일차(무르기 무제한)뿐이다
 * (`OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 4.2절) — 2일차 이후 보상은
 * 콘텐츠 미확정이라 여기서 다루지 않는다(자리만 비워둔다).
 *
 * 지급 조건을 "방금 체크인 결과가 1일차인가"가 아니라 "출석한 적이 있는데 1일차 보상이 아직
 * 미지급인가"로 판정한다 — 최초 실행 때 지급 경로가 어떤 이유로든 실패해도(프로세스 조기 종료
 * 등) 다음 실행에서 스스로 복구되게 하기 위함이다. 지급 후에는
 * [AttendanceState.withTierClaimed]로 기록해 매일 다시 지급되지 않는다.
 *
 * 클레임 자체는 UI에 의존하지 않는 `runPremiumFeatureClaim`에 위임하므로, 이 함수는 Compose
 * 트리가 아직 없는 앱 시작 시점(`Application.onCreate` 계열)에서도 호출할 수 있다.
 */
fun runAttendanceRewardGrant(
    state: AttendanceState,
    attendanceStore: AttendanceStorePort,
    premiumStore: PremiumStateStorePort,
): AttendanceRewardGrantResult {
    val hasReachedFirstDay = state.attendanceCount >= UndoUnlimitedRewardTier
    if (!hasReachedFirstDay || state.isTierClaimed(UndoUnlimitedRewardTier)) {
        return AttendanceRewardGrantResult.NothingToGrant(state)
    }

    // 이미 클레임돼 있으면(예: 예전에 인게임 클레임 팝업으로 직접 받은 사용자)
    // runPremiumFeatureClaim이 null을 돌려주지만, 출석 쪽 지급 기록은 그대로 남긴다 —
    // "1일차 보상은 처리 완료"가 사실이므로 매 실행마다 다시 시도할 이유가 없다.
    runPremiumFeatureClaim(FeatureId.Undo, premiumStore)

    val granted = state.withTierClaimed(UndoUnlimitedRewardTier)
    attendanceStore.save(granted)
    return AttendanceRewardGrantResult.Granted(tier = UndoUnlimitedRewardTier, state = granted)
}
