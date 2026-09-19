package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.gamehistory.BlunderPointLossThreshold
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 대국 다시보기 화면의 문구(백로그 #156).
 *
 * ⚠️ **[UiStrings]의 `val` 필드로 넣지 않고 여기 표로 둔다.** 그 데이터 클래스는 이미 400줄이 넘는
 * 생성자를 네 파일이 나눠 채우고 있어, 한 기능이 여섯 개를 더하면 네 파일이 함께 커진다 —
 * `UiStringsBoardControls.kt`·`UiStringsStudyVideos.kt`가 먼저 간 길이다.
 *
 * ⚠️ **그 대신 리플렉션 그물 밖이다**(함정 10). `UiStringsTest`는 [UiStrings]의 `String` 필드만
 * 훑으므로 여기 표는 **손으로 짠 그물**(`UiStringsGameReplayTest`)이 지킨다 — 지우지 말 것.
 */
private val Titles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 다시보기",
    UiLanguage.English to "Game replay",
    UiLanguage.Japanese to "対局リプレイ",
    UiLanguage.ChineseSimplified to "对局回放",
)

/** 목록 행의 "다시보기" 꼬리표 — 리플레이 본문이 저장된 기록에만 붙는다. */
private val RowBadges: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "다시보기",
    UiLanguage.English to "Replay",
    UiLanguage.Japanese to "リプレイ",
    UiLanguage.ChineseSimplified to "回放",
)

/**
 * 리플레이 본문이 없는 옛 기록을 눌렀을 때.
 *
 * ⚠️ **"준비 중"이라고 말하지 않는다** — 기다려도 생기지 않는다. 2026-09-18 이전 기록에는
 * 수순이 **저장된 적이 없어서**(#151) 영영 못 연다. 기조: 저절로 풀리지 않는 것은 그렇게 말한다.
 */
private val Unavailable: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이 대국에는 수순 기록이 남아 있지 않아 다시볼 수 없습니다.",
    UiLanguage.English to "This game has no stored move record, so it cannot be replayed.",
    UiLanguage.Japanese to "この対局には手順の記録が残っていないため、リプレイできません。",
    UiLanguage.ChineseSimplified to "该对局没有保存手顺记录，无法回放。",
)

private val StartPosition: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "시작 국면",
    UiLanguage.English to "Start",
    UiLanguage.Japanese to "開始局面",
    UiLanguage.ChineseSimplified to "开局",
)

private val ScoreSectionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "형세 지표",
    UiLanguage.English to "Score trend",
    UiLanguage.Japanese to "形勢グラフ",
    UiLanguage.ChineseSimplified to "形势走势",
)

/**
 * 형세가 한 번도 기록되지 않은 판.
 *
 * ⚠️ **빈 그래프를 0으로 그리지 않기 위한 문구다**(#156 착수 전 경고). 0.0은 "호각"이라는
 * 뜻이 있어서, 재지 않은 구간을 0으로 채우면 그래프가 적극적으로 거짓말을 한다.
 */
private val NoScoreData: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이 대국에는 형세 기록이 없습니다.",
    UiLanguage.English to "No score estimates were recorded in this game.",
    UiLanguage.Japanese to "この対局には形勢の記録がありません。",
    UiLanguage.ChineseSimplified to "该对局没有形势记录。",
)

private val BlunderSectionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "큰 실수",
    UiLanguage.English to "Big mistakes",
    UiLanguage.Japanese to "大きなミス",
    UiLanguage.ChineseSimplified to "重大失误",
)

/** 판 위·구간 칩에 붙는 짧은 표식. */
private val BlunderBadges: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "실착",
    UiLanguage.English to "Blunder",
    UiLanguage.Japanese to "悪手",
    UiLanguage.ChineseSimplified to "恶手",
)

/**
 * 착수 평가가 하나도 안 붙은 판.
 *
 * ⚠️ *"실수가 없었다"* 로 읽히면 안 된다 — 평가가 붙으려면 **그 수를 두기 전에 사전 분석 캐시가
 * 준비돼 있어야** 하고(`MoveReview.kt`), 무료 대국은 그 조건이 잘 맞지 않는다. 그래서 문구는
 * "잘 뒀다"가 아니라 **"잴 자료가 없다"** 고 말한다.
 */
private val NoMoveEvaluations: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이 대국에는 착수 평가 기록이 없어 실수 구간을 표시할 수 없습니다.",
    UiLanguage.English to "This game has no move evaluations, so mistakes cannot be marked.",
    UiLanguage.Japanese to "この対局には着手評価の記録がなく、ミス箇所を示せません。",
    UiLanguage.ChineseSimplified to "该对局没有落子评估记录，无法标出失误。",
)

private val NoBlunders: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "크게 잃은 수가 없습니다.",
    UiLanguage.English to "No move lost that much.",
    UiLanguage.Japanese to "大きく損した手はありません。",
    UiLanguage.ChineseSimplified to "没有损失很大的着法。",
)

private val NavigationLabels: Map<UiLanguage, List<String>> = mapOf(
    // 차례: 처음 · 이전 · 다음 · 마지막
    UiLanguage.Korean to listOf("처음으로", "이전 수", "다음 수", "마지막으로"),
    UiLanguage.English to listOf("First", "Previous", "Next", "Last"),
    UiLanguage.Japanese to listOf("最初へ", "前の手", "次の手", "最後へ"),
    UiLanguage.ChineseSimplified to listOf("回到开局", "上一手", "下一手", "跳到最后"),
)

internal enum class ReplayNavigation { First, Previous, Next, Last }

internal fun gameReplayTitleFor(language: UiLanguage): String = Titles.getValue(language)

internal fun gameReplayRowBadgeFor(language: UiLanguage): String = RowBadges.getValue(language)

internal fun gameReplayUnavailableFor(language: UiLanguage): String = Unavailable.getValue(language)

internal fun gameReplayStartPositionFor(language: UiLanguage): String = StartPosition.getValue(language)

internal fun gameReplayScoreSectionFor(language: UiLanguage): String = ScoreSectionTitles.getValue(language)

internal fun gameReplayNoScoreDataFor(language: UiLanguage): String = NoScoreData.getValue(language)

internal fun gameReplayBlunderSectionFor(language: UiLanguage): String = BlunderSectionTitles.getValue(language)

internal fun gameReplayBlunderBadgeFor(language: UiLanguage): String = BlunderBadges.getValue(language)

internal fun gameReplayNoMoveEvaluationsFor(language: UiLanguage): String = NoMoveEvaluations.getValue(language)

internal fun gameReplayNoBlundersFor(language: UiLanguage): String = NoBlunders.getValue(language)

internal fun gameReplayNavigationLabelFor(language: UiLanguage, step: ReplayNavigation): String =
    NavigationLabels.getValue(language)[step.ordinal]

/**
 * 「10집 이상」의 **집수를 문구에 박지 않는다** — 임계는
 * [BlunderPointLossThreshold] 하나가 정본이고, 네 언어 문구가 그 값을 따라온다.
 */
internal fun gameReplayBlunderCriterionFor(
    language: UiLanguage,
    thresholdPoints: Double = BlunderPointLossThreshold,
): String {
    val points = pointsText(thresholdPoints)
    return when (language) {
        UiLanguage.Korean -> "최선수보다 $points 집 이상 잃은 수"
        UiLanguage.English -> "Moves that lost $points+ points vs. the best move"
        UiLanguage.Japanese -> "最善手より $points 目以上損した手"
        UiLanguage.ChineseSimplified -> "比最佳着法损失 $points 目以上的着法"
    }
}

/** 실착 칩에 적는 손실 — `48수 · −12.5집`의 뒷조각. */
internal fun gameReplayPointLossFor(language: UiLanguage, pointLoss: Double): String {
    val points = pointsText(abs(pointLoss))
    return when (language) {
        UiLanguage.Korean -> "−$points 집"
        UiLanguage.English -> "−$points pts"
        UiLanguage.Japanese -> "−$points 目"
        UiLanguage.ChineseSimplified -> "−$points 目"
    }
}

/** 기록이 중간에 끊긴 판 — 어디까지 되짚었는지 정직하게 말한다. */
internal fun gameReplayTruncatedFor(language: UiLanguage, moveNumber: Int): String =
    when (language) {
        UiLanguage.Korean -> "${moveNumber}수에서 기록이 어긋나 거기까지만 되짚습니다."
        UiLanguage.English -> "The record breaks at move $moveNumber; replay stops there."
        UiLanguage.Japanese -> "$moveNumber 手目で記録が合わず、そこまでの再生になります。"
        UiLanguage.ChineseSimplified -> "记录在第 $moveNumber 手出现不一致，只能回放到此处。"
    }

/** `12.5` / `10`처럼 소수점 아래 한 자리, 정수면 떼고. */
private fun pointsText(points: Double): String {
    val rounded = (points * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
