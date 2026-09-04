package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.premium.FeatureId

/**
 * 출석 보상표(백로그 #55에서 2026-08-31 사용자와 함께 재확정).
 *
 * | 회차 | 보상 |
 * | --- | --- |
 * | 1 | 형세 30 |
 * | 2 | 추천 수 30 |
 * | 3 | 무르기 무제한(영구) |
 * | 4 | 광고 스킵권 3 |
 * | 5 | 조각 1 (연습생 돌뫼) |
 * | 6 | 조각 1 (사범 묘수) |
 * | 7 | 캐릭터: 도장생 반상 — **소모품 없음** |
 * | 14·21 | 형세 50 · 추천 50 · 스킵 3 |
 * | 28 | 캐릭터: 관장 천원 — **소모품 없음** |
 * | 35~ (7의 배수) | 형세 50 · 추천 50 · 스킵 3 |
 *
 * ## 설계 의도(사용자)
 * 형세·추천을 따로 줘 각각을 인지시키고, **3일차까지 무르기가 유료임을 겪게 한 뒤** 지급해
 * 가치를 체감시킨다. 5·6일차 조각으로 *"캐릭터는 광고를 봐야 모인다"* 를 알려 유입시킨다.
 * 반복 보상은 **한 주가 풍족하면 프리미엄 의미가 없으므로** 갈증이 남게 준다.
 *
 * ⚠️ **조각의 무광고 경로는 의도적으로 없다.** 5·6일차 1개씩이 전부이고, 돌뫼는 광고 4번,
 * 묘수는 광고 9번이 더 필요하다. "반복 회차에 조각을 얹자"는 제안은 2026-08-31에 기각됐다 —
 * 그 시점이면 광고로 이미 확보한 사용자가 많고, 간격이 벌어져 경로 구실을 못 한다.
 *
 * ⚠️ **7·28일차를 반복 회차와 섞지 말 것.** 예전에는 `contentTier = if (tier > 7) 7 else tier`로
 * 접어서 14·21·35…가 7일차 내용을 그대로 썼는데, 지금 7일차는 **캐릭터만**이라 그렇게 접으면
 * 14일차에 이미 가진 캐릭터만 나오고 **소모품이 통째로 사라진다.** 그래서 캐릭터 회차와
 * 반복 회차를 아래처럼 분리해 둔다.
 */

/** 무르기 무제한이 열리는 회차. ⚠️ 1일차가 아니라 **3일차**다(#55) — 유료임을 겪은 뒤에 준다. */
const val UndoUnlimitedRewardTier: Int = 3

/** 7일 주기의 길이. 8일차 이후로는 이 배수 회차에만 보상이 있다. */
const val WeeklyRewardCycleTier: Int = 7

/** 1·2일차 — 형세 보기 / 추천 수 1회권을 각각 이만큼. */
private const val AnalysisTicketAmount: Int = 30

/** 반복 회차(14·21·35…) — 형세 보기 / 추천 수 1회권을 각각 이만큼. */
private const val WeeklyAnalysisTicketAmount: Int = 50

/** 4일차 광고 스킵권. */
private const val EarlySkipTicketAmount: Int = 3

/**
 * 반복 회차 광고 스킵권. ⚠️ 상한이 9인데 4일차 3 + 14일차 3 + 21일차 3 = **정확히 9**다 —
 * 35일차부터는 상한에 걸려 버려진다. **의도된 갈증**이지만, 팝업이 "3개"라고 안내해 놓고 0개가
 * 들어가면 안 되므로 `ConsumableInventory.grantableAmount`로 실제 지급량을 확인해 표시할 것.
 */
private const val WeeklySkipTicketAmount: Int = 3

/** 5·6일차에 주는 조각 개수. */
private const val ShardRewardAmount: Int = 1

sealed class AttendanceReward {
    /** 기능 하나를 **영구히** 열어준다(3일차 무르기 — [UndoUnlimitedRewardTier]). */
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
        return buildList {
            if (tier == UndoUnlimitedRewardTier) {
                add(AttendanceReward.PermanentFeature(FeatureId.Undo))
            }
            addAll(consumablesFor(tier))
            addAll(shardsFor(tier))
            // 캐릭터가 걸린 회차인지는 카탈로그가 안다 — 회차 번호를 여기 또 적으면 두 곳이 어긋난다.
            BotCharacterCatalog.forAttendanceTier(tier).forEach { character ->
                add(AttendanceReward.BotCharacterUnlock(character))
            }
        }
    }

    /**
     * ⚠️ **캐릭터 회차에는 소모품을 얹지 않는다**(확정표). 그래서 "7의 배수면 반복 번들"이 아니라
     * **"7의 배수이면서 캐릭터 회차가 아니면"** 반복 번들이다 — 7·28을 빼는 이 조건이 핵심이다.
     */
    private fun consumablesFor(tier: Int): List<AttendanceReward> = buildList {
        when {
            tier == 1 -> add(consumable(ConsumableCatalog.EvalOnce, AnalysisTicketAmount))
            tier == 2 -> add(consumable(ConsumableCatalog.TopMovesOnce, AnalysisTicketAmount))
            tier == 4 -> add(consumable(ConsumableCatalog.PremiumOnce, EarlySkipTicketAmount))
            isWeeklyRepeatTier(tier) -> {
                add(consumable(ConsumableCatalog.EvalOnce, WeeklyAnalysisTicketAmount))
                add(consumable(ConsumableCatalog.TopMovesOnce, WeeklyAnalysisTicketAmount))
                add(consumable(ConsumableCatalog.PremiumOnce, WeeklySkipTicketAmount))
            }
        }
    }

    /**
     * 5·6일차에 조각 경로 캐릭터를 **카탈로그 순서대로** 하나씩 맛보게 한다. 순서에 기대므로
     * `AttendanceRewardPolicyTest`가 조각 경로가 정확히 둘이고 그 순서인지를 고정한다.
     */
    private fun shardsFor(tier: Int): List<AttendanceReward> {
        val index = when (tier) {
            5 -> 0
            6 -> 1
            else -> return emptyList()
        }
        val character = BotCharacterCatalog.shardPathCharacters().getOrNull(index) ?: return emptyList()
        return listOf(AttendanceReward.BotCharacterShards(character, ShardRewardAmount))
    }

    /** 소모품 반복 번들을 받는 회차 — 7의 배수이면서 **캐릭터가 걸리지 않은** 회차다. */
    private fun isWeeklyRepeatTier(tier: Int): Boolean =
        tier % WeeklyRewardCycleTier == 0 && BotCharacterCatalog.forAttendanceTier(tier).isEmpty()

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
