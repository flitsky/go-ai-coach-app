package com.worksoc.goaicoach.ui.l10n

/**
 * 학습 허브의 하위 분류(백로그 #163, U-16 — 2026-09-20 사용자 결정).
 *
 * ⚠️ **화면의 순서는 선언 순서가 아니다** — 학습 하기 페이지의 줄 표(`StudyHubRows`, `ui/study/StudyScreen.kt`) 한 곳이 정한다(#280 — 분류가 아닌
 * 「기보 보기」가 그 사이에 선다). 새 분류를 열면 그 표에도 줄을 더한다(빠지면 `StudyHubContractTest`가 빨개진다). 아직 없는 분류는 **자리를 먼저
 * 보여 준다**(회색 + 「준비 중」 배지, 비활성).
 * 사용자 결정으로 *"누르면 토스트"* 가 아니라 **눌리지 않는** 쪽을 골랐다 — 앞으로 무엇이
 * 올지 알리되, 누를 수 있는 것처럼 보여 헛손질을 만들지는 않는다.
 *
 * ⚠️ **채울 때 여기에 줄을 더하지 말 것** — 줄은 이미 있다. [available]을 `true`로 돌리고
 * [StudyScreen]의 `when`에 화면을 이어 주면 된다(#164가 [Rules]를 그렇게 열었다).
 */
internal enum class StudyCategory(val available: Boolean) {
    /** 2026-09-20까지 이 목록이 곧 「학습 하기」였다 — 콘텐츠가 통째로 한 칸 내려왔다. */
    YoutubeLessons(available = true),
    Rules(available = true),
    Fundamentals(available = true),
    /** 푸는 학습(백로그 #184, U-51) — 다른 분류와 달리 판에 직접 둔다. */
    LifeAndDeath(available = true),
}
