package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.attendance.AttendanceReward
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.premium.FeatureId

/**
 * 출석 도장판(백로그 #55)이 새로 쓰는 문구. 구조는 `UiStringsLanding.kt`와 같다 — 화면 하나가
 * 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 *
 * 회차 이름·보상 이름은 기존 `UiStrings.attendanceRewardDayLabel` / `attendanceRewardLabel`을
 * 그대로 쓴다. 여기 있는 것은 **도장판 때문에 새로 필요해진 세 가지**뿐이다.
 */
private val BoardBeyondNotices: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "28일차 이후에는 7일마다 보상이 반복돼요.",
    UiLanguage.English to "After day 28, rewards repeat every seven days.",
    UiLanguage.Japanese to "28日目以降は7日ごとに報酬が繰り返されます。",
    UiLanguage.ChineseSimplified to "第28天之后，奖励每七天重复一次。",
)

/**
 * ⚠️ 상한에 걸려 **실제로는 들어가지 않는** 보상에 붙인다. 이 표기가 없으면 팝업이 "광고
 * 스킵권 3개"라고 해 놓고 0개를 주는 셈이 된다(#55 ⓑ).
 */
private val AtStockCapNotices: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "보유 상한",
    UiLanguage.English to "at cap",
    UiLanguage.Japanese to "所持上限",
    UiLanguage.ChineseSimplified to "已达上限",
)

/** 아직 닿지 않은 회차의 칸에 붙이는 안내 — 도장이 없는 칸이 "빈 칸"으로만 보이지 않게 한다. */
private val UpcomingNotices: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "출석하면 받아요",
    UiLanguage.English to "Check in to earn",
    UiLanguage.Japanese to "出席すると獲得",
    UiLanguage.ChineseSimplified to "签到即可获得",
)

private val BoardSectionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "출석 현황",
    UiLanguage.English to "Check-in progress",
    UiLanguage.Japanese to "出席状況",
    UiLanguage.ChineseSimplified to "签到进度",
)

internal fun attendanceBoardSectionTitleFor(language: UiLanguage): String =
    BoardSectionTitles.getValue(language)

internal fun attendanceBoardBeyondNoticeFor(language: UiLanguage): String =
    BoardBeyondNotices.getValue(language)

internal fun attendanceAtStockCapNoticeFor(language: UiLanguage): String =
    AtStockCapNotices.getValue(language)

internal fun attendanceUpcomingNoticeFor(language: UiLanguage): String =
    UpcomingNotices.getValue(language)

/**
 * 6칸 행에 들어갈 **짧은 표기**. `UiStrings.attendanceRewardLabel`의 전체 문구
 * ("형세 보기 1회권 30개")는 여섯 칸 폭에서 말줄임으로 뭉개진다 — 그 행의 주인공은 도장이고
 * 보상은 곁들임이라, 무엇인지만 알아보면 된다.
 *
 * ⚠️ 넓은 4칸 행에는 이걸 쓰지 않는다. 거기는 주 단위 보상이라 전체 문구를 담을 폭이 있고,
 * 담아야 "왜 넓은지"가 설명된다.
 */
internal fun attendanceRewardShortLabelFor(language: UiLanguage, reward: AttendanceReward): String =
    when (reward) {
        is AttendanceReward.PermanentFeature -> when (reward.featureId) {
            FeatureId.Undo -> shortWord(language, "무르기", "Undo", "待った", "悔棋")
            FeatureId.Eval -> shortWord(language, "형세", "Eval", "形勢", "形势")
            FeatureId.TopMoves -> shortWord(language, "추천", "Moves", "推奨", "推荐")
            FeatureId.MoveReview -> shortWord(language, "착수 평가", "Review", "着手評価", "着手评价")
        }
        is AttendanceReward.Consumable -> {
            val name = when (reward.item) {
                ConsumableCatalog.EvalOnce -> shortWord(language, "형세", "Eval", "形勢", "形势")
                ConsumableCatalog.TopMovesOnce -> shortWord(language, "추천", "Moves", "推奨", "推荐")
                else -> shortWord(language, "스킵", "Skip", "スキップ", "跳过")
            }
            "$name ${reward.amount}"
        }
        // 조각은 개수보다 "조각이구나"가 먼저 읽혀야 한다 — 어느 캐릭터인지는 상세 영역이 말한다.
        is AttendanceReward.BotCharacterShards ->
            shortWord(language, "조각", "Shard", "かけら", "碎片") + " ${reward.amount}"
        is AttendanceReward.BotCharacterUnlock -> botCharacterNameFor(language, reward.character.id)
    }

private fun shortWord(language: UiLanguage, ko: String, en: String, ja: String, zh: String): String =
    when (language) {
        UiLanguage.Korean -> ko
        UiLanguage.English -> en
        UiLanguage.Japanese -> ja
        UiLanguage.ChineseSimplified -> zh
    }
