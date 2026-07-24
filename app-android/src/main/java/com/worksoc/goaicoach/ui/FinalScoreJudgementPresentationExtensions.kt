package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

internal fun FinalScoreJudgement.resultText(strings: UiStrings): String =
    if (winner == null) {
        strings.drawLabel
    } else {
        margin?.let { m ->
            strings.winnerMarginLabel(strings.colorLabel(winner), m)
        } ?: strings.winnerWithoutMarginLabel(strings.colorLabel(winner))
    }

internal fun FinalScoreJudgement.scoringRuleLine(strings: UiStrings): String =
    strings.scoringRuleLabel(strings.rulesetLabel(ruleset))

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
        Ruleset.Japanese -> {
            val prisoners = capturedByWhite.toDouble()
            val territory = area - prisoners - kValue
            strings.scoreTextDetailTerritoryKomi(territory, prisoners, kValue, area)
        }
        Ruleset.Chinese ->
            strings.scoreTextDetailAreaKomi(kValue, area)
    }
}

internal fun FinalScoreJudgement.note(strings: UiStrings): String? =
    if (isEstimatedDisplay) {
        strings.scoreEstimateNotice
    } else {
        null
    }
