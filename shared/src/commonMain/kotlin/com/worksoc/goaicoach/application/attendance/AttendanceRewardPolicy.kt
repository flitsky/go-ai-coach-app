package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
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
 * 7일차마다 조각 경로 캐릭터 **각각**에게 지급하는 조각 수.
 *
 * 이 보상이 있는 이유는 순전히 **의존성 때문이다**: 조각 캐릭터의 획득 경로가 광고 하나뿐이면,
 * 광고가 채워지지 않는 상황(노필, 지역/연령 제한, 오프라인, 구글 계정 문제)에서 그 캐릭터는
 * 영영 잠긴 채로 남는다 — 앱이 스스로 통제할 수 없는 외부 사정으로 콘텐츠가 사라지는 셈이다
 * (2026-08-29 사용자 지적). 그래서 **앱 안에서만으로 완결되는 경로**를 하나 붙였다.
 *
 * 하필 7일차(= 영원히 반복되는 회차)인 이유도 같다 — 유한한 회차에 걸면 그 회차를 지나친
 * 사용자에게는 여전히 경로가 없다. 반복 회차에 걸어야 "언젠가는 반드시 열린다"가 성립한다.
 *
 * 주당 1개로 둔 것은 광고를 **여전히 빠른 길로 남기기 위해서다**: 광고는 앉은 자리에서 5번이면
 * 2단계가 열리지만, 이 경로만으로는 5회차(누적 35일 출석)가 걸린다. 출석은 연속이 아니라
 * 누적이므로(`AttendanceState.attendanceCount`) 며칠 빠져도 진행분은 사라지지 않는다.
 */
private const val WeeklyShardAmount: Int = 1

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

    /** AI 봇 캐릭터 한 종을 영구 획득(3단계는 4일차, 5단계는 28일차 — 2026-08-29 기준 둘뿐이다). */
    data class BotCharacterUnlock(val character: BotCharacter) : AttendanceReward()

    /**
     * 조각 경로 캐릭터의 조각 [amount]개(7일차). [BotCharacterUnlock]과 저장소는 같지만
     * (`BotCollectionState`) **즉시 획득이 아니라 진행도**라는 점이 다르다 — 필요 수를 채우는
     * 회차에 가서야 획득으로 넘어간다.
     */
    data class BotCharacterShards(val character: BotCharacter, val amount: Int) : AttendanceReward()
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
                    // 누구에게 줄지는 카탈로그가 안다 — 캐릭터 보상과 같은 이유로 여기에 이름을
                    // 다시 적지 않는다. 이미 다 모은 캐릭터의 몫은 지급 단계에서 걸러진다.
                    BotCharacterCatalog.shardPathCharacters().forEach { character ->
                        add(AttendanceReward.BotCharacterShards(character, WeeklyShardAmount))
                    }
                }
                // 4일차는 소모품이 없다 — 캐릭터 하나뿐이며 아래에서 카탈로그가 채운다.
            }
            // 캐릭터 보상은 여기에 다시 적지 않는다 — "몇 일차에 열리는가"는 이미 카탈로그의
            // BotUnlockSource.Attendance(tier)에 붙어 있어, 그쪽을 단일 출처로 삼는다(#13).
            //
            // ⚠️ **캐릭터만 `contentTier`가 아니라 실제 [tier]로 조회한다**(2026-08-29). 소모품은
            // 8일차 이후 7일차 내용을 반복하지만 캐릭터는 **한 번뿐인 영구 획득**이라 반복 축과
            // 성질이 다르다 — 접어서 조회하면 28일차에 걸린 5단계 캐릭터에 영영 닿지 못한다.
            // 중복 지급 걱정은 없다: 14·21처럼 캐릭터가 정의되지 않은 회차는 빈 목록이고,
            // 이미 가진 캐릭터는 지급 단계에서 걸러진다.
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

    /**
     * [pendingTiers]에서 **이미 뜻을 잃은 보상을 걷어낸** 목록. Claim 팝업이 "받을 것"을 미리
     * 보여줄 때 쓴다.
     *
     * 조각은 [WeeklyRewardCycleTier]마다 영원히 반복되므로, 이 필터가 없으면 이미 다 모은
     * 캐릭터의 조각이 **매주 팝업에 실린다.** 지급 쪽(`runAttendanceRewardGrant`)은 저장소가
     * 돌려주는 `null`로 같은 판정을 더 정확하게 하지만(밀린 회차를 지나며 도중에 획득할 수도
     * 있다), 팝업은 지급 **전에** 목록을 그려야 해서 상태를 직접 받아 판정한다.
     *
     * 한 회차의 보상이 전부 걸러지면 그 회차는 목록에서 빠진다 — 알릴 것이 없으면 팝업도 뜨지
     * 않는 것이 맞다. 그 회차는 미지급으로 남지만, 남은 보상이 없으므로 잃는 것도 없다.
     */
    fun pendingTiers(state: AttendanceState, collection: BotCollectionState): List<AttendanceRewardTier> =
        pendingTiers(state)
            .map { tier -> tier.copy(rewards = tier.rewards.filterNot { it.isAlreadySatisfied(collection) }) }
            .filter { it.rewards.isNotEmpty() }

    /** 지금 상태에서 지급해 봐야 아무것도 바뀌지 않는 보상인가. 조각만 판정한다. */
    private fun AttendanceReward.isAlreadySatisfied(collection: BotCollectionState): Boolean =
        this is AttendanceReward.BotCharacterShards && collection.isClaimed(character.id)

    private fun consumable(item: ConsumableItem, amount: Int): AttendanceReward =
        AttendanceReward.Consumable(item = item, amount = amount)
}
