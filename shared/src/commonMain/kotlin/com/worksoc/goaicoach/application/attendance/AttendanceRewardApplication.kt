package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardGrant
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
    /**
     * **이번 호출로 새로 영구 획득한 캐릭터**(백로그 #68). 획득 축전 팝업(#69)은 [granted]가 아니라
     * **이 목록으로 구동해야 한다.**
     *
     * ⚠️ **[granted]로 구동하면 두 가지가 어긋난다.**
     * 1. **조각 완료 획득이 안 보인다.** [AttendanceReward.BotCharacterShards]는 [granted]에
     *    *"조각 N개"* 로만 남아, 그 조각이 마지막 한 개여서 **캐릭터가 됐다는 사실**이 결과에서
     *    사라진다. 그 판정은 5계층 안(`BotCharacterShardGrant.unlocked`)에만 있었다.
     * 2. **이미 가진 캐릭터를 축하하게 된다**(유령 보상). 아래 `grant`가 고쳐지기 전까지
     *    [AttendanceReward.BotCharacterUnlock]은 이미 보유해도 무조건 알림 대상이었다.
     *
     * 두 경로(즉시 해금·조각 완료)를 여기서 합치므로 호출부는 획득 경로를 몰라도 된다.
     * 순서는 지급 순서(= 일차 오름차순)를 그대로 따른다 — 밀린 회차가 한 번에 지급되면
     * **캐릭터를 둘 이상 동시에 획득**할 수 있다(7·28일차).
     */
    val acquiredCharacters: List<BotCharacter> = emptyList(),
) {
    val didGrant: Boolean get() = granted.isNotEmpty()

    /** 이번에 지급된 보상 전체를 일차 구분 없이 펼친 목록. */
    val grantedRewards: List<AttendanceReward> get() = granted.flatMap { tier -> tier.rewards }

    /** 이번 호출로 새로 획득한 캐릭터가 있는가(#68). 축전 팝업의 노출 조건이다. */
    val didAcquireCharacter: Boolean get() = acquiredCharacters.isNotEmpty()
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
    val granted = mutableListOf<AttendanceRewardTier>()
    val acquired = mutableListOf<BotCharacter>()
    for (tier in pending) {
        // 흘려보낸 것과 **알릴 것**은 다르다 — 조각은 7일차마다 영원히 반복되므로, 이미 다 모은
        // 캐릭터의 조각까지 팝업에 적으면 그 사용자는 매주 의미 없는 줄을 보게 된다.
        val announced = tier.rewards.filter { reward ->
            val outcome = grant(reward, premiumStore, consumableStore, botStore)
            outcome.acquired?.let(acquired::add)
            outcome.announce
        }
        if (announced.isNotEmpty()) granted += tier.copy(rewards = announced)
        next = next.withTierClaimed(tier.tier)
    }
    attendanceStore.save(next)
    return AttendanceRewardGrantResult(state = next, granted = granted, acquiredCharacters = acquired)
}

/**
 * 보상 한 건을 흘려보낸 결과(백로그 #68).
 *
 * [announce]와 [acquired]는 **다른 질문**이다 — 전자는 *"Claim 팝업의 목록에 적을 것인가"*,
 * 후자는 *"축전 팝업을 띄울 캐릭터가 생겼는가"* 다. 조각 보상은 [announce]가 참이면서
 * [acquired]가 `null`인 경우가 대부분이고(진행도만 올랐다), 마지막 한 개일 때만 둘 다 채워진다.
 */
private data class RewardGrantOutcome(
    val announce: Boolean,
    val acquired: BotCharacter? = null,
)

/**
 * 보상 한 건을 그 종류에 맞는 저장소에 흘려보낸다.
 *
 * ⚠️ **예전에는 `Boolean` 하나만 돌려줬고, 그것이 두 가지를 뭉개고 있었다**(백로그 #68).
 * 1. **조각이 마지막 한 개여서 캐릭터가 됐다는 사실**이 결과에서 사라졌다 —
 *    `BotCharacterShardGrant.unlocked`가 그 자리에서 버려졌기 때문이다. 그래서 화면이 그 판정을
 *    **자기 사본으로 다시 추론**했고(`before + 1 >= required`), 그 사이 출석으로 조각이 들어오면
 *    어긋났다. 지급은 정확한데 **알림만 틀리는**, 로그로도 안 드러나는 종류의 결함이다.
 * 2. **[AttendanceReward.BotCharacterUnlock]이 이미 보유해도 무조건 `true`였다**(유령 보상).
 *    그 회차의 팝업에 *"캐릭터 획득!"* 이 뜨는데 실제로는 아무 일도 일어나지 않는다.
 *
 * @return 알릴 것인지([RewardGrantOutcome.announce])와 새로 획득한 캐릭터가 있는지
 *   ([RewardGrantOutcome.acquired]). 소모품·영구 기능은 반복 회차에 걸려 있지 않아 항상 알린다.
 */
private fun grant(
    reward: AttendanceReward,
    premiumStore: PremiumStateStorePort,
    consumableStore: ConsumableStorePort,
    botStore: BotCollectionStorePort,
): RewardGrantOutcome =
    when (reward) {
        // 이미 클레임돼 있으면(예: 예전에 인게임 클레임 팝업으로 직접 받은 사용자) null을
        // 돌려주지만, 출석 쪽 지급 기록은 그대로 남긴다 — "이 일차는 처리 완료"가 사실이므로
        // 매 실행마다 다시 시도할 이유가 없다.
        is AttendanceReward.PermanentFeature -> {
            runPremiumFeatureClaim(reward.featureId, premiumStore)
            RewardGrantOutcome(announce = true)
        }
        is AttendanceReward.Consumable -> {
            runConsumableGrant(reward.item, reward.amount, consumableStore)
            RewardGrantOutcome(announce = true)
        }
        // ⚠️ **이미 보유하면 알리지 않는다**(#68에서 고친 유령 보상). 오늘은 도달할 수 없지만
        // (출석 해금 캐릭터는 출석 외 획득 경로가 없다) **구매(#74)나 개발자 도구(#70)가 캐릭터를
        // 심을 수 있게 되는 순간 도달 가능해진다.**
        // ⚠️ 그때 **7·28일차는 캐릭터가 유일한 보상이라 그 회차가 통째로 빈손이 된다** — 대체 보상
        // ("동등 가치 소모품으로 대체")은 별도 사용자 결정이다(`launch-plan/README.md` C-2 상세).
        is AttendanceReward.BotCharacterUnlock -> {
            val unlockedNow = runBotCharacterUnlock(reward.character.id, botStore) != null
            RewardGrantOutcome(
                announce = unlockedNow,
                acquired = reward.character.takeIf { unlockedNow },
            )
        }
        // 이미 다 모은 캐릭터면 `null`이 돌아온다 — 그때만 알리지 않는다.
        is AttendanceReward.BotCharacterShards -> {
            val shardGrant = runBotCharacterShardGrant(reward.character, botStore, reward.amount)
            RewardGrantOutcome(
                announce = shardGrant != null,
                // 이번 적립이 필요 수를 채웠을 때만 획득이다. 이 판정은 5계층이 이미 갖고 있다.
                acquired = reward.character.takeIf { shardGrant?.unlocked == true },
            )
        }
    }
