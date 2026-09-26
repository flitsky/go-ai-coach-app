package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

internal fun FinalScoreJudgement.resultText(strings: UiStrings): String {
    val winner = winner
    return if (winner == null) {
        strings.drawLabel
    } else {
        margin?.let { m ->
            strings.winnerMarginLabel(strings.colorLabel(winner), m)
        } ?: strings.winnerWithoutMarginLabel(strings.colorLabel(winner))
    }
}

internal fun FinalScoreJudgement.scoringRuleLine(strings: UiStrings): String =
    strings.scoringRuleLabel(strings.rulesetLabel(ruleset))

internal fun FinalScoreJudgement.gameModeLine(strings: UiStrings): String =
    strings.gameModeLabel(handicapCount)

internal fun FinalScoreJudgement.removedStonesLine(strings: UiStrings): String =
    strings.removedStonesLabel(removedBlack, removedWhite)

internal fun FinalScoreJudgement.blackLine(strings: UiStrings): String? {
    val area = blackArea ?: return null
    return when (ruleset) {
        Ruleset.Japanese -> {
            val prisoners = capturedByBlack.toDouble()
            val territory = area - prisoners
            strings.scoreTextDetailTerritory(strings.colorLabel(StoneColor.Black), territory, prisoners, area)
        }
        Ruleset.Chinese ->
            strings.scoreTextDetailArea(strings.colorLabel(StoneColor.Black), area)
    }
}

internal fun FinalScoreJudgement.whiteLine(strings: UiStrings): String? {
    val area = whiteAreaWithKomi ?: return null
    val kValue = komi ?: 0.0
    return when (ruleset) {
        // 합계에는 룰셋의 접바둑 보정이 들어 있을 수 있다(#106 — 집계가는 지금 0이라 예전 줄 그대로).
        Ruleset.Japanese -> {
            val prisoners = capturedByWhite.toDouble()
            val territory = area - prisoners - kValue - whiteHandicapBonus
            strings.scoreTextDetailTerritoryKomi(territory, prisoners, kValue, area, whiteHandicapBonus)
        }
        // 면적계가 접바둑이면 합계에 보정 N이 들어 있다(#89) — 줄에도 그 항을 밝힌다.
        // 옛 저장본은 보정 없이 계가됐고 0으로 읽히므로 예전 줄 그대로다.
        Ruleset.Chinese ->
            strings.scoreTextDetailAreaKomi(kValue, area, whiteHandicapBonus)
    }
}

internal fun FinalScoreJudgement.note(strings: UiStrings): String? =
    if (isEstimatedDisplay) {
        strings.scoreEstimateNotice
    } else {
        null
    }
