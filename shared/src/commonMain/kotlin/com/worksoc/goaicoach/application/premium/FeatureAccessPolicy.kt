package com.worksoc.goaicoach.application.premium

/** [FeatureAccess.Locked]일 때 사용자가 선택할 수 있는 잠금 해제 수단. */
enum class UnlockOption {
    AdGrant,
    Purchase,

    /**
     * ⚠️ **지금 이 수단으로 열리는 기능은 하나도 없다**(백로그 #66, 2026-09-03).
     * 무르기가 유일한 사용처였는데 그 경로를 닫았다 — 사유는 [FeatureAccessPolicy] 안의
     * `unlockOptionsFor` KDoc에 있다.
     *
     * **그런데 상수는 남겨 뒀다.** `PremiumState.claimedFeatures` 원장은 그대로 살아 있고
     * ([AllowedVia.Claimed]가 그것을 읽는다), "한 번 받으면 영구"라는 축이 필요한 기능이 다시
     * 생기면 그때 이 자리를 쓴다. 지운다고 저장 포맷이 깨지지는 않지만(이 enum은 영속되지
     * 않는다) 되살릴 때 축을 다시 발명하게 된다.
     */
    Claim,
}

/** [FeatureAccess.Allowed]일 때, 그 허용이 어느 경로로 성립했는지. */
enum class AllowedVia {
    Purchase,
    AdGrant,
    Claimed,

    /**
     * 유료로 산 **봇 캐릭터와 두는 동안**만 성립하는 허용(백로그 #18,
     * `FEATURE_ACCESS_PRINCIPLES.md` 8장). 다른 셋과 결정적으로 다른 점은 **대국 컨텍스트에 의존한다**는 것이다 — 같은 사용자가
     * 같은 순간에도 상대가 누구냐에 따라 켜지고 꺼진다.
     *
     * 1회권과 반드시 구분해야 한다(8.3절 3번): 특전은 **차감이 없으므로** 소모품의 단발성
     * 추적과 섞이면 쓰지도 않은 표가 줄어든 것처럼 보인다.
     */
    CharacterPerk,
}

/** [FeatureAccessPolicy.resolve]의 판정 결과. */
sealed interface FeatureAccess {
    data class Allowed(val via: AllowedVia) : FeatureAccess
    data class Locked(val unlockOptions: Set<UnlockOption>) : FeatureAccess
}

/**
 * 6계층(Session & Continuity) — 기능별 접근 정책 판정. [PremiumState]("이 세션이 지금 뭘
 * 가졌는가")를 입력으로 받아 [FeatureId] 하나에 대해 "지금 쓸 수 있는가, 없다면 무엇으로
 * 풀 수 있는가"를 순수 함수로 판정한다. [PremiumState]를 파라미터로 받는 판정 로직은 5계층이
 * 아니라 6계층이다 — 5계층은 6계층을 몰라야 하기 때문에([docs/ARCHITECTURE.md] 5계층 경계
 * 원칙), [PremiumState.isActive]가 이미 같은 이유로 [PremiumState] 자신에 있는 것과 같은
 * 선례를 따른다.
 *
 * 정책이 바뀔 때(무료/광고/구매/클레임 중 어느 조합을 어느 기능에 허용할지) 고칠 곳을 이
 * 함수의 `when` 분기 하나로 좁히는 것이 이 타입의 목적이다 — `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`
 * "5/6계층 — 기능 엔타이틀먼트 정책 도입" 항목 참고.
 *
 * [UnlockOption.Purchase]는 [com.worksoc.goaicoach.ui.FeatureFlags.isPurchaseEnabled] 여부와
 * 무관하게 항상 반환한다 — 그 플래그는 7계층(ui) 소속이라 6계층이 알면 안 된다. 구매 버튼을
 * 실제로 숨길지는 지금처럼 프레젠테이션(`PremiumUpsellDialog`)이 계속 판단한다.
 */
object FeatureAccessPolicy {
    /**
     * [characterPerkActive]는 "지금 두고 있는 상대가 **내가 유료로 산 캐릭터인가**"다(#18).
     * 참이면 인게임 프리미엄 기능이 차감 없이 열린다.
     *
     * **기본값이 `false`인 것이 중요하다** — 대국 컨텍스트가 없는 호출부(설정 화면, 업셀 팝업)는
     * 이 축을 몰라도 되고, 몰라도 기존과 똑같이 동작한다. 그래서 호출부를 전부 고치지 않았다
     * (8.3절 1번의 두 선택지 중 "판정을 넓히되 기본값으로 기존 호출부를 지킨다" 쪽).
     */
    fun resolve(
        featureId: FeatureId,
        state: PremiumState,
        nowMillis: Long,
        characterPerkActive: Boolean = false,
    ): FeatureAccess {
        activeVia(state, nowMillis)?.let { via -> return FeatureAccess.Allowed(via) }
        if (characterPerkActive && featureId in CharacterPerkFeatures) {
            return FeatureAccess.Allowed(AllowedVia.CharacterPerk)
        }
        if (featureId in state.claimedFeatures) {
            return FeatureAccess.Allowed(AllowedVia.Claimed)
        }
        return FeatureAccess.Locked(unlockOptionsFor(featureId))
    }

    /**
     * 캐릭터 구매 특전이 열어 주는 기능. 사용자 확정 문구가 **"인게임 프리미엄 기능('형세 보기',
     * '추천 수')"** 로 이 둘을 명시했으므로(2026-08-29) 그대로 둘만 넣는다 — 무르기는 별도 클레임
     * 축이고, 착수 리뷰는 그 문장에 없다. 넓히려면 그 결정부터 다시 받아야 한다.
     */
    val CharacterPerkFeatures: Set<FeatureId> = setOf(FeatureId.Eval, FeatureId.TopMoves)

    /**
     * 기능과 무관하게 **"지금 프리미엄 자체가 켜져 있는가"**만 묻고, 켜져 있으면 그 경로를 준다.
     * [resolve]의 첫 분기를 그대로 떼어낸 것이라 판정 기준이 갈라지지 않는다 — 특정
     * [FeatureId]에 묶이지 않는 판정이 필요한 곳(소모품 '광고 스킵권'은 기능이 아니라 프리미엄
     * 자체를 켠다 — `application/consumable`)이 이 함수를 쓴다.
     */
    fun activeVia(state: PremiumState, nowMillis: Long): AllowedVia? =
        if (!state.isActive(nowMillis)) {
            null
        } else if (state.source == PremiumSource.Purchase) {
            AllowedVia.Purchase
        } else {
            AllowedVia.AdGrant
        }

    /**
     * ⚠️ **무르기가 여기서 [UnlockOption.Claim]을 받던 것을 뺐다**(백로그 #66, 2026-09-03).
     * 그 한 줄이 인게임 "영구 활성화" 팝업의 **유일한 방아쇠**였고, 그 팝업의 확인 버튼이
     * 무르기를 **1일차부터 아무 때나 영구 지급**하고 있었다.
     *
     * 무르기의 영구 해금은 **3일차 출석 보상**이다(`AttendanceRewardPolicy.UndoUnlimitedRewardTier`,
     * #55). 3일차에 둔 이유가 *"이틀 동안 '이건 유료구나'를 겪은 뒤에 받아야 값나가는 것을 받은
     * 것이 된다"* (`ATTENDANCE_REWARD_POLICY.md`)인데, 더 쉬운 경로가 열려 있는 한 그 설계는
     * 성립하지 않았다. **같은 엔타이틀먼트에 지급 경로가 둘이면 쉬운 쪽이 이긴다.**
     *
     * ⚠️ **닫은 것은 '지급' 경로뿐이고 '판정' 경로는 그대로다** — [resolve]의
     * `featureId in state.claimedFeatures` 분기는 살아 있어, 이미 무르기를 획득한 사용자는
     * 계속 [AllowedVia.Claimed]로 무료다. 그 분기를 함께 지우면 기존 보유자가 전원 잠긴다.
     */
    private fun unlockOptionsFor(featureId: FeatureId): Set<UnlockOption> =
        when (featureId) {
            FeatureId.Undo, FeatureId.Eval, FeatureId.TopMoves, FeatureId.MoveReview ->
                setOf(UnlockOption.AdGrant, UnlockOption.Purchase)
        }
}
