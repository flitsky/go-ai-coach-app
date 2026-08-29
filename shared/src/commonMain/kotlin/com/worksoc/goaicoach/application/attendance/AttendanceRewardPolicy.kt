package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.premium.FeatureId

/** 1일차(첫 출석) 회차 — '무제한 무르기' 영구 활성화가 걸려 있다. */
const val UndoUnlimitedRewardTier: Int = 1

/**
 * 7일 주기의 마지막 회차. 8일차 이후로는 **7의 배수 회차마다 이 회차의 보상이 그대로 반복**된다
 * (4.2절 재확정본, #19). 그래서 정책표에는 1~7일차만 적고 그 위는 조회 시점에 이 값으로 접는다 —
 * 표를 두 벌로 늘리면 한쪽만 고쳐져 어긋난다.
 */
const val WeeklyRewardCycleTier: Int = 7

// 일차별 소모품 지급량(4.2절 표). #13이 쓰던 단일 상수(`ConsumableRewardAmount = 10`)로는
// 표현할 수 없어 나눴다(#19) — 주중 회차와 7일차의 양이 다르고, 분석 1회권과 광고 스킵권의
// 양도 다르기 때문이다.
//
// ⚠️ 한 주기 지급량이 종류별 재고 상한(`ConsumableInventory.MaxPerItem` = 99)을 넘는다
// (형세 보기 30 + 30 + 50 = 110). **의도된 충돌이다** — 넘치는 만큼 버려지게 두어 소모를
// 유도한다(2026-08-24 사용자 확정). 상한을 올리려면 `MaxPerItem` 한 줄만 고치면 된다.

/** 2·5일차 — 형세 보기/추천 수 1회권을 **각각** 이만큼. */
private const val AnalysisTicketAmount: Int = 30

/** 7일차 — 형세 보기/추천 수 1회권을 **각각** 이만큼(주간 마무리라 더 크다). */
private const val WeeklyAnalysisTicketAmount: Int = 50

/** 3일차 광고 스킵권. */
private const val EarlySkipTicketAmount: Int = 3

/** 6일차 광고 스킵권. */
private const val MidSkipTicketAmount: Int = 5

/** 7일차 광고 스킵권. */
private const val WeeklySkipTicketAmount: Int = 10

/**
 * 출석 보상 한 건. 세 종류가 **서로 다른 저장소**에 기록되기 때문에(영구 클레임=`PremiumState`,
 * 소모품=`ConsumableInventory`, 캐릭터=`BotCollectionState`) 지급 경로도 각각 다르다 — 이
 * sealed 타입이 그 분기를 한곳에 모아 준다.
 */
sealed class AttendanceReward {
    /** 기능 하나를 **영구히** 열어준다(1일차 무르기). */
    data class PermanentFeature(val featureId: FeatureId) : AttendanceReward()

    /** 쓰면 줄어드는 소모품 [amount]개(2·3·5·6·7일차). */
    data class Consumable(val item: ConsumableItem, val amount: Int) : AttendanceReward()

    /** AI 봇 캐릭터 한 종을 영구 획득(4일차 — #16 이후 출석 캐릭터는 이 회차 하나뿐이다). */
    data class BotCharacterUnlock(val character: BotCharacter) : AttendanceReward()
}

/** 일차 하나와 그 일차에 걸린 보상 목록. 한 일차에 보상이 여러 개일 수 있다(7일차는 3개). */
data class AttendanceRewardTier(
    val tier: Int,
    val rewards: List<AttendanceReward>,
)

/**
 * 6계층(Session & Continuity) — "몇 일차에 무엇을 주는가" 정책표(킥오프 플랜 4.2절).
 *
 * [isRewardedTier]가 **"보상이 걸린 회차인가"**(1~7일차 매일 + 이후 7의 배수)를 판정하는 반면,
 * 이 객체는 그 회차에 **실제로 무엇을 주는지**를 안다 — 둘은 별개 축이다.
 *
 * **#19 이후로 미확정 회차는 없다** — 1~7일차가 모두 채워졌고 그 위는 7일차 보상의 반복이다.
 * 그래도 "빈 목록인 회차는 지급 기록([AttendanceState.claimedTiers])도 남기지 않는다"는 안전장치는
 * 그대로 둔다(#13 구현 결정 3번): 앞으로 표를 고치다 어떤 회차가 잠시 비더라도, 그 사이 그 회차를
 * 지나간 사용자가 나중에 정해진 보상을 영영 못 받는 일이 없어야 하기 때문이다.
 */
object AttendanceRewardPolicy {

    /** [tier]일차에 지급할 보상 목록. 보상 회차가 아니면 빈 목록. */
    fun rewardsFor(tier: Int): List<AttendanceReward> {
        if (!isRewardedTier(tier)) return emptyList()
        // 8일차 이후 7의 배수 회차는 7일차 보상을 그대로 반복한다(4.2절) — 회차 번호는 그대로
        // 두고 **내용을 조회할 때만** 접는다. `claimedTiers`에는 실제 회차(14, 21, ...)가 들어가야
        // 같은 주기를 두 번 지급하지 않는다.
        val contentTier = if (tier > WeeklyRewardCycleTier) WeeklyRewardCycleTier else tier
        return buildList {
            when (contentTier) {
                UndoUnlimitedRewardTier -> add(AttendanceReward.PermanentFeature(FeatureId.Undo))
                2, 5 -> {
                    add(consumable(ConsumableCatalog.EvalOnce, AnalysisTicketAmount))
                    add(consumable(ConsumableCatalog.TopMovesOnce, AnalysisTicketAmount))
                }
                3 -> add(consumable(ConsumableCatalog.PremiumOnce, EarlySkipTicketAmount))
                6 -> add(consumable(ConsumableCatalog.PremiumOnce, MidSkipTicketAmount))
                WeeklyRewardCycleTier -> {
                    add(consumable(ConsumableCatalog.EvalOnce, WeeklyAnalysisTicketAmount))
                    add(consumable(ConsumableCatalog.TopMovesOnce, WeeklyAnalysisTicketAmount))
                    add(consumable(ConsumableCatalog.PremiumOnce, WeeklySkipTicketAmount))
                }
                // 4일차는 소모품이 없다 — 캐릭터 하나뿐이며 아래에서 카탈로그가 채운다.
            }
            // 캐릭터 보상은 여기에 다시 적지 않는다 — "몇 일차에 열리는가"는 이미 카탈로그의
            // BotUnlockSource.Attendance(tier)에 붙어 있어, 그쪽을 단일 출처로 삼는다(#13).
            // 반복 회차에서 contentTier(=7)로 조회하므로 캐릭터가 다시 지급되지도 않는다.
            BotCharacterCatalog.forAttendanceTier(contentTier).forEach { character ->
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

    private fun consumable(item: ConsumableItem, amount: Int): AttendanceReward =
        AttendanceReward.Consumable(item = item, amount = amount)
}
