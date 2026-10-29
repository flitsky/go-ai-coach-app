package com.worksoc.goaicoach.ui.monetization

/**
 * 보상형 광고를 보는 단추 앞의 표시(백로그 #299, 사용자 2026-10-29) — 글을 읽기 전에 광고를 보는 단추임을 알게 한다.
 * 광고 없이 되는 단추(첫 운세 뽑기 · 구독자의 운 바꾸기 · 뽑기)에는 아무것도 달지 않는다 — 예전의 「▶」(재생)는 무료 단추까지 광고처럼 읽혀 뺐다.
 * 쓰는 곳: 실험실 「오늘의 운세」의 운 바꾸기 · 「1일 1회 뽑기」. 표시를 바꾸려면 이 한 줄만 고친다.
 */
internal const val RewardedAdMark = "📺"

/** [label] 앞에 [RewardedAdMark]를 단다 — 광고를 볼 때([withAd])만. 사이는 두 칸이다 — 한 칸이면 넓은 이모지에 글자가 붙어 보인다(사용자 2026-10-29). */
internal fun rewardedAdButtonText(label: String, withAd: Boolean): String = if (withAd) "$RewardedAdMark  $label" else label
