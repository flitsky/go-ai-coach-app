package com.worksoc.goaicoach.ui

/**
 * 출석 도장판(백로그 #55)이 새로 쓰는 문구. 구조는 `UiStringsLanding.kt`와 같다 — 화면 하나가
 * 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 *
 * 회차 이름·보상 이름은 기존 `UiStrings.attendanceRewardDayLabel` / `attendanceRewardLabel`을
 * 그대로 쓴다. 여기 있는 것은 **도장판 때문에 새로 필요해진 문구**뿐이다.
 *
 * ⚠️ **#57에서 `attendanceRewardShortLabelFor`를 걷어냈다.** 칸의 보상을 글자 대신 글리프로
 * 그리게 되면서 쓰이는 곳이 없어졌다 — 짧은 표기를 되살리려 하지 말 것. 그 표기가 존재했던
 * 이유(여섯 칸 폭에 전체 문구가 안 들어간다)를 글리프가 이미 해결한다.
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

/**
 * 도장이 찍힌 칸에 붙는 말. **화면에는 안 보이고 스크린 리더만 읽는다**(#57) — 인장 그림에는
 * 글자가 없으므로, 이것이 없으면 받아 간 칸과 아직인 칸이 소리로 구분되지 않는다.
 */
private val StampedNotices: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "받았어요",
    UiLanguage.English to "Claimed",
    UiLanguage.Japanese to "受取済み",
    UiLanguage.ChineseSimplified to "已领取",
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

internal fun attendanceStampedNoticeFor(language: UiLanguage): String =
    StampedNotices.getValue(language)
