package com.worksoc.goaicoach.application.premium

/**
 * 5계층(App Service) — **UI 트리 없이도 호출 가능한** 기능 클레임 진입점.
 *
 * 기존에는 클레임을 하는 길이 `ui/PremiumUiState.kt`의 `claim` 람다(=Compose
 * `CompositionLocal`) 하나뿐이라, 앱 최초 실행 직후(아직 Compose 트리가 없을 수 있는 시점)
 * 자동 지급하는 출석 보상 같은 경로에서는 쓸 수 없었다
 * (`260823-260830_OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN.md` 4.4절). 이 함수는 그 판정/저장
 * 로직만 떼어낸 것으로, [PremiumStateStorePort]만 있으면 어디서든(Application onCreate,
 * 백그라운드 코루틴 등) 호출할 수 있다. UI 쪽 `claim` 람다도 이 함수를 그대로 호출하도록
 * 바꿔, 클레임이 성립하는 규칙이 두 벌로 갈라지지 않게 한다.
 *
 * 저장소를 **read-modify-write** 한다(메모리에 들고 있던 상태를 덮어쓰지 않는다) — 화면
 * 밖에서 지급된 클레임과 화면 안에서 지급된 클레임이 서로를 지우지 않게 하기 위함이다.
 *
 * @return 이번 호출로 새로 클레임이 추가됐다면 저장된 새 상태, 이미 클레임돼 있어 저장이
 *   필요 없었다면 `null`.
 */
fun runPremiumFeatureClaim(featureId: FeatureId, store: PremiumStateStorePort): PremiumState? {
    val current = store.load()
    if (featureId in current.claimedFeatures) return null
    val next = current.copy(claimedFeatures = current.claimedFeatures + featureId)
    store.save(next)
    return next
}

/**
 * [PremiumState.claimedFeatures]는 [PremiumState.source](구매/광고 활성화)와 **별개 축의
 * 영구 원장**이므로, 소스가 바뀌는 저장(구매 완료, 광고 시청 부여, 복원, QA 토글)에서 절대
 * 유실되면 안 된다. 이 함수는 저장 직전에 저장소에 남아 있는 클레임을 합쳐 저장한다 —
 * 호출부가 메모리에 들고 있던 클레임 집합만 이어붙이면, 그 사이 화면 밖(출석 보상 자동 지급
 * 등)에서 추가된 클레임을 조용히 지워버린다.
 *
 * @return 실제로 저장된(합쳐진) 상태.
 */
fun PremiumStateStorePort.saveMergingClaimedFeatures(state: PremiumState): PremiumState {
    val merged = state.copy(claimedFeatures = state.claimedFeatures + load().claimedFeatures)
    save(merged)
    return merged
}
