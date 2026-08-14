package com.worksoc.goaicoach.application.premium

/** [FeatureAccess.Locked]일 때 사용자가 선택할 수 있는 잠금 해제 수단. */
internal enum class UnlockOption {
    AdGrant,
    Purchase,
    Claim,
}

/** [FeatureAccess.Allowed]일 때, 그 허용이 어느 경로로 성립했는지. */
internal enum class AllowedVia {
    Purchase,
    AdGrant,
    Claimed,
}

/** [FeatureAccessPolicy.resolve]의 판정 결과. */
internal sealed interface FeatureAccess {
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
 * 함수의 `when` 분기 하나로 좁히는 것이 이 타입의 목적이다 — `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md`
 * "5/6계층 — 기능 엔타이틀먼트 정책 도입" 항목 참고.
 *
 * [UnlockOption.Purchase]는 [com.worksoc.goaicoach.ui.FeatureFlags.isPurchaseEnabled] 여부와
 * 무관하게 항상 반환한다 — 그 플래그는 7계층(ui) 소속이라 6계층이 알면 안 된다. 구매 버튼을
 * 실제로 숨길지는 지금처럼 프레젠테이션(`PremiumUpsellDialog`)이 계속 판단한다.
 */
internal object FeatureAccessPolicy {
    fun resolve(featureId: FeatureId, state: PremiumState, nowMillis: Long): FeatureAccess {
        if (state.isActive(nowMillis)) {
            val via = if (state.source == PremiumSource.Purchase) AllowedVia.Purchase else AllowedVia.AdGrant
            return FeatureAccess.Allowed(via)
        }
        if (featureId in state.claimedFeatures) {
            return FeatureAccess.Allowed(AllowedVia.Claimed)
        }
        return FeatureAccess.Locked(unlockOptionsFor(featureId))
    }

    private fun unlockOptionsFor(featureId: FeatureId): Set<UnlockOption> =
        when (featureId) {
            FeatureId.Undo -> setOf(UnlockOption.AdGrant, UnlockOption.Purchase, UnlockOption.Claim)
            FeatureId.Eval, FeatureId.TopMoves, FeatureId.MoveReview -> setOf(UnlockOption.AdGrant, UnlockOption.Purchase)
        }
}
