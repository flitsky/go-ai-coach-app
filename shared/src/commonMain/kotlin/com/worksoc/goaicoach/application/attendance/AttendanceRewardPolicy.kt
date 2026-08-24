package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.premium.FeatureId

/** 1일차(첫 출석) 회차 — '무제한 무르기' 영구 활성화와 첫 캐릭터가 여기 걸려 있다. */
const val UndoUnlimitedRewardTier: Int = 1

/** 2~4일차 소모품 보상의 지급 개수(4.2절 정책표 — 각 10개). */
const val ConsumableRewardAmount: Int = 10

/**
 * 출석 보상 한 건. 세 종류가 **서로 다른 저장소**에 기록되기 때문에(영구 클레임=`PremiumState`,
 * 소모품=`ConsumableInventory`, 캐릭터=`BotCollectionState`) 지급 경로도 각각 다르다 — 이
 * sealed 타입이 그 분기를 한곳에 모아 준다.
 */
sealed class AttendanceReward {
    /** 기능 하나를 **영구히** 열어준다(1일차 무르기). */
    data class PermanentFeature(val featureId: FeatureId) : AttendanceReward()

    /** 쓰면 줄어드는 소모품 [amount]개(2~4일차). */
    data class Consumable(val item: ConsumableItem, val amount: Int) : AttendanceReward()

    /** AI 봇 캐릭터 한 종을 영구 획득(1·5일차). */
    data class BotCharacterUnlock(val character: BotCharacter) : AttendanceReward()
}

/** 일차 하나와 그 일차에 걸린 보상 목록. 한 일차에 보상이 여러 개일 수 있다(1일차부터 2개). */
data class AttendanceRewardTier(
    val tier: Int,
    val rewards: List<AttendanceReward>,
)

/**
 * 6계층(Session & Continuity) — "몇 일차에 무엇을 주는가" 정책표(킥오프 플랜 4.2절).
 *
 * [isRewardedTier]가 **"보상이 걸린 회차인가"**(1~7일차 매일 + 이후 7의 배수)를 판정하는 반면,
 * 이 객체는 그 회차에 **실제로 무엇을 주는지**를 안다 — 둘은 별개 축이다. 6·7일차와 14/21/28
 * 일차는 보상 회차이지만 **콘텐츠가 아직 미확정**이라 빈 목록을 돌려준다(4.2절 미확정 행).
 *
 * ⚠️ 빈 목록인 회차는 지급 기록([AttendanceState.claimedTiers])도 남기지 않는다 — 나중에 6일차
 * 보상이 정해졌을 때 그 사이 6일차를 지나간 사용자가 영영 못 받는 일이 없어야 하기 때문이다.
 */
object AttendanceRewardPolicy {

    /** [tier]일차에 지급할 보상 목록. 보상 회차가 아니거나 콘텐츠 미확정이면 빈 목록. */
    fun rewardsFor(tier: Int): List<AttendanceReward> {
        if (!isRewardedTier(tier)) return emptyList()
        return buildList {
            when (tier) {
                UndoUnlimitedRewardTier -> add(AttendanceReward.PermanentFeature(FeatureId.Undo))
                2 -> add(consumable(ConsumableCatalog.EvalOnce))
                3 -> add(consumable(ConsumableCatalog.TopMovesOnce))
                4 -> add(consumable(ConsumableCatalog.PremiumOnce))
            }
            // 캐릭터 보상은 여기에 다시 적지 않는다 — "몇 일차에 열리는가"는 이미 카탈로그의
            // BotUnlockSource.Attendance(tier)에 붙어 있어, 그쪽을 단일 출처로 삼는다. 미확정인
            // 3~5번째 캐릭터가 나중에 출석 보상으로 정해지면 카탈로그 한 줄만 고치면 된다(#11).
            BotCharacterCatalog.forAttendanceTier(tier).forEach { character ->
                add(AttendanceReward.BotCharacterUnlock(character))
            }
        }
    }

    /**
     * 지금까지 출석한 회차 중 **아직 지급되지 않은** 것들을 오름차순으로 돌려준다.
     *
     * "방금 체크인한 회차"만 보지 않고 1일차부터 훑는 이유는 #4와 같다 — 지급 경로가 어떤 이유로든
     * 실패해도(프로세스 조기 종료 등) 다음 실행에서 스스로 복구되게 하기 위함이다. 밀린 보상을
     * 무기한 보관할지 만료시킬지는 아직 미확정이며(5.1절), 만료 정책이 정해지면 이 함수에 붙는다.
     */
    fun pendingTiers(state: AttendanceState): List<AttendanceRewardTier> =
        (1..state.attendanceCount)
            .filterNot(state::isTierClaimed)
            .map { tier -> AttendanceRewardTier(tier = tier, rewards = rewardsFor(tier)) }
            .filter { it.rewards.isNotEmpty() }

    private fun consumable(item: ConsumableItem): AttendanceReward =
        AttendanceReward.Consumable(item = item, amount = ConsumableRewardAmount)
}
