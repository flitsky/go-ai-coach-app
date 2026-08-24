package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterUnlock
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.consumable.runConsumableGrant
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.runPremiumFeatureClaim

/**
 * [runAttendanceRewardGrant]의 결과. [state]는 지급 사실이 기록된(=저장된) 최신 출석 상태이고,
 * [granted]는 **이번 호출로 실제 지급된** 일차별 보상 목록이다(지급할 게 없었으면 빈 목록).
 *
 * #4 시절에는 보상이 1일차 하나뿐이라 `Granted`/`NothingToGrant` sealed로 충분했지만, 한 일차에
 * 보상이 여러 개 걸리고(4.2절) 여러 일차가 한 번에 밀려 지급될 수 있게 되면서 "지급된 것들의
 * 목록"이 결과의 본체가 됐다 — Claim 팝업(#14)이 무엇을 받았는지 그대로 보여줘야 하기 때문이다.
 */
data class AttendanceRewardGrantResult(
    val state: AttendanceState,
    val granted: List<AttendanceRewardTier>,
) {
    val didGrant: Boolean get() = granted.isNotEmpty()

    /** 이번에 지급된 보상 전체를 일차 구분 없이 펼친 목록. */
    val grantedRewards: List<AttendanceReward> get() = granted.flatMap { tier -> tier.rewards }
}

/**
 * 5계층(App Service) — 출석 상태를 보고 **아직 지급하지 않은 보상을 전부** 지급한다.
 * `runAttendanceCheckIn` 직후에 호출하도록 설계됐다(체크인 판정과 보상 지급은 별개 축 —
 * [AttendanceState.claimedTiers] 참고).
 *
 * 무엇을 줄지는 [AttendanceRewardPolicy]가 알고, 이 함수는 그 목록을 **종류별 지급 경로로
 * 흘려보내고 지급 기록을 남기는 것**만 한다. 보상 3종이 서로 다른 저장소에 기록되므로 포트도
 * 세 개를 받는다.
 *
 * 지급 기록([AttendanceState.withTierClaimed])은 그 일차의 보상을 **전부 흘려보낸 뒤에만** 남고,
 * 출석 저장은 마지막에 한 번만 한다. 중간에 프로세스가 죽으면 그 일차는 미지급으로 남아 다음
 * 실행에서 다시 지급되는데, 지급 경로 3종이 모두 멱등이라(이미 클레임된 기능/이미 가진 캐릭터는
 * 재저장하지 않는다) 중복 지급으로 이어지지 않는다. 소모품만은 멱등이 아니지만(개수가 늘어난다)
 * 사용자에게 유리한 방향이고, 상한 99가 폭주를 막는다.
 *
 * 각 저장소를 UI 없이 다룰 수 있는 진입점(`runPremiumFeatureClaim`/`runConsumableGrant`/
 * `runBotCharacterUnlock`)에만 의존하므로, Compose 트리가 아직 없는 앱 시작 시점
 * (`Application.onCreate` 계열)에서도 호출할 수 있다.
 */
fun runAttendanceRewardGrant(
    state: AttendanceState,
    attendanceStore: AttendanceStorePort,
    premiumStore: PremiumStateStorePort,
    consumableStore: ConsumableStorePort,
    botStore: BotCollectionStorePort,
): AttendanceRewardGrantResult {
    val pending = AttendanceRewardPolicy.pendingTiers(state)
    if (pending.isEmpty()) return AttendanceRewardGrantResult(state = state, granted = emptyList())

    var next = state
    for (tier in pending) {
        for (reward in tier.rewards) {
            grant(reward, premiumStore, consumableStore, botStore)
        }
        next = next.withTierClaimed(tier.tier)
    }
    attendanceStore.save(next)
    return AttendanceRewardGrantResult(state = next, granted = pending)
}

/** 보상 한 건을 그 종류에 맞는 저장소에 흘려보낸다. */
private fun grant(
    reward: AttendanceReward,
    premiumStore: PremiumStateStorePort,
    consumableStore: ConsumableStorePort,
    botStore: BotCollectionStorePort,
) {
    when (reward) {
        // 이미 클레임돼 있으면(예: 예전에 인게임 클레임 팝업으로 직접 받은 사용자) null을
        // 돌려주지만, 출석 쪽 지급 기록은 그대로 남긴다 — "이 일차는 처리 완료"가 사실이므로
        // 매 실행마다 다시 시도할 이유가 없다.
        is AttendanceReward.PermanentFeature -> runPremiumFeatureClaim(reward.featureId, premiumStore)
        is AttendanceReward.Consumable -> runConsumableGrant(reward.item, reward.amount, consumableStore)
        is AttendanceReward.BotCharacterUnlock -> runBotCharacterUnlock(reward.character.id, botStore)
    }
}
