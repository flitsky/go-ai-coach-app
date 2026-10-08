package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.gamehistory.WinRatePeriod
import com.worksoc.goaicoach.application.gamehistory.WinRateTally

/**
 * 대국 기록 화면의 **승률**(백로그 #227, 사용자 피드백 2026-10-08) 문구 — 기간 선택(최근 10판 · 최근 한 달 · 누적)과 전적 한 줄.
 *
 * `UiStrings` 생성자 슬롯이 255/255로 꽉 차 있어(함정 61) 다른 화면들처럼 사이드 테이블에 둔다.
 */
internal fun winRatePeriodLabelFor(language: UiLanguage, period: WinRatePeriod): String =
    when (period) {
        WinRatePeriod.LastTenGames -> when (language) {
            UiLanguage.Korean -> "최근 10판"
            UiLanguage.English -> "Last 10"
            UiLanguage.Japanese -> "直近10局"
            UiLanguage.ChineseSimplified -> "最近10局"
        }
        WinRatePeriod.LastMonth -> when (language) {
            UiLanguage.Korean -> "최근 한 달"
            UiLanguage.English -> "Last month"
            UiLanguage.Japanese -> "直近1か月"
            UiLanguage.ChineseSimplified -> "最近一个月"
        }
        WinRatePeriod.AllTime -> when (language) {
            UiLanguage.Korean -> "누적"
            UiLanguage.English -> "All time"
            UiLanguage.Japanese -> "通算"
            UiLanguage.ChineseSimplified -> "累计"
        }
    }

/** 이 줄이 무엇의 승률인지 — 캐릭터와 둔 일반 대국. (기력 측정 대국의 줄은 그 메뉴 이름 [rankMeasureTitleFor]을 쓴다.) */
internal fun winRateRegularGamesLabelFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "대국"
        UiLanguage.English -> "Games"
        UiLanguage.Japanese -> "対局"
        UiLanguage.ChineseSimplified -> "对局"
    }

/**
 * 전적 한 줄 — `승률 60% · 6승 4패`. 무승부가 있을 때만 무를 붙인다. 센 판이 없으면 `null`(그 줄을 그리지 않는다).
 */
internal fun winRateSummaryFor(language: UiLanguage, tally: WinRateTally): String? {
    val percent = tally.winRatePercent ?: return null
    return when (language) {
        UiLanguage.Korean -> "승률 $percent% · ${tally.wins}승 ${tally.losses}패" + if (tally.draws > 0) " ${tally.draws}무" else ""
        UiLanguage.English -> "$percent% wins · ${tally.wins}W ${tally.losses}L" + if (tally.draws > 0) " ${tally.draws}D" else ""
        UiLanguage.Japanese -> "勝率$percent% · ${tally.wins}勝${tally.losses}敗" + if (tally.draws > 0) "${tally.draws}分" else ""
        UiLanguage.ChineseSimplified -> "胜率$percent% · ${tally.wins}胜${tally.losses}负" + if (tally.draws > 0) "${tally.draws}和" else ""
    }
}

/** 고른 기간에 센 판이 하나도 없을 때. */
internal fun winRateNoGamesFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "이 기간에 집계할 대국이 없습니다"
        UiLanguage.English -> "No games to count in this period"
        UiLanguage.Japanese -> "この期間に集計できる対局がありません"
        UiLanguage.ChineseSimplified -> "这段时间没有可统计的对局"
    }

/** 무엇을 세는지 밝히는 작은 글 — 승률이 목록의 판 수와 달라 보일 때 이유를 말한다. */
internal fun winRateWhatCountsFor(language: UiLanguage, shortGameMaxMoveCount: Int): String =
    when (language) {
        UiLanguage.Korean -> "사람 대 AI 대국만 · ${shortGameMaxMoveCount}수 이하 제외"
        UiLanguage.English -> "Player vs AI games only · games of $shortGameMaxMoveCount moves or fewer left out"
        UiLanguage.Japanese -> "人対AIの対局のみ · ${shortGameMaxMoveCount}手以下は除外"
        UiLanguage.ChineseSimplified -> "仅统计人机对局 · 不含${shortGameMaxMoveCount}手及以下"
    }
