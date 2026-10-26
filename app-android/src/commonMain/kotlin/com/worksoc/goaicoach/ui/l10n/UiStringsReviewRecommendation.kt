package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.gamehistory.ReviewRecommendationMistakeThreshold

/**
 * 대국 뒤 「복기 하기」 추천(백로그 #200)의 문구 — 판정 결과 팝업의 배지와, 팝업을 닫은 뒤 판 옆에 뜨는 캐릭터 말풍선.
 *
 * ⚠️ **[UiStrings]의 필드로 넣지 않는다** — 생성자 여유가 거의 없다(함정 61, 리팩토링 보류 `#48`).
 * `UiStringsBoardControls.kt`·`UiStringsGameFlow.kt`가 먼저 간 위성 표의 길이다.
 *
 * ⚠️ **"당신이 실착했다"가 아니다.** 실착은 **누가 두었든** 센다(U-57, 사람·AI 모두) — 그래서 문장의 주어는
 * 사람이 아니라 *"이번 대국"* 이다. 영어 `You made …`처럼 사람에게 돌리면 AI의 실착까지 사용자 탓이 된다.
 *
 * ⚠️ **집수를 문구에 박지 않는다** — 임계는 [ReviewRecommendationMistakeThreshold] 하나가 정본이다
 * (다시보기 `gameReplayScoreSwingSectionFor`의 개수와 같은 원칙). 함수로 만든 문구라 리플렉션 그물 밖이다(함정 10) —
 * `UiStringsReviewRecommendationTest`가 손 그물이다.
 */
internal fun reviewRecommendationMessageFor(
    language: UiLanguage,
    mistakeCount: Int,
    thresholdPoints: Double = ReviewRecommendationMistakeThreshold,
): String {
    val points = pointsText(thresholdPoints)
    return when (language) {
        UiLanguage.Korean -> "이번 대국에서 ${points}점 이상 실착한 수가 ${mistakeCount}개 있어요. 복기하기를 추천드려요."
        UiLanguage.English -> {
            val moves = if (mistakeCount == 1) "1 move" else "$mistakeCount moves"
            "This game had $moves that lost $points or more points. I recommend reviewing it."
        }
        UiLanguage.Japanese -> "この対局では${points}目以上損をした手が${mistakeCount}手ありました。検討をおすすめします。"
        UiLanguage.ChineseSimplified -> "本局有${mistakeCount}手亏损${points}目以上的失误，建议复盘。"
    }
}

/**
 * 「복기 하기」 버튼 배지를 스크린 리더가 읽는 말 — 화면에는 숫자(`3`·`9+`)만 보이지만, 소리로 `3`만 들으면 무엇의
 * 개수인지 알 수 없다(`boardSizeSubjectFor`와 같은 이유). 개수는 자르지 않은 **실제 값**을 읽는다.
 */
internal fun reviewMistakeBadgeDescriptionFor(
    language: UiLanguage,
    mistakeCount: Int,
    thresholdPoints: Double = ReviewRecommendationMistakeThreshold,
): String {
    val points = pointsText(thresholdPoints)
    return when (language) {
        UiLanguage.Korean -> "${points}점 이상 실착 ${mistakeCount}개"
        UiLanguage.English -> if (mistakeCount == 1) "1 move lost $points+ points" else "$mistakeCount moves lost $points+ points"
        UiLanguage.Japanese -> "${points}目以上の損が${mistakeCount}手"
        UiLanguage.ChineseSimplified -> "${mistakeCount}手亏损${points}目以上"
    }
}
