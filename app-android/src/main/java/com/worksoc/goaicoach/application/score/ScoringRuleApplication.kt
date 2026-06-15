package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.engine.localScoreSnapshot

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline

internal data class ScoringRuleChangePlan(
    val gameState: GameState,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val scoreText: String,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val engineMessage: String?,
    val requiresEngineSync: Boolean,
)

internal fun buildScoringRuleChangePlan(
    currentState: GameState,
    nextRuleset: Ruleset,
    isGameEnded: Boolean,
    matchMode: MatchMode,
    isEngineReady: Boolean,
    previousSnapshots: List<ScoreSnapshot>,
): ScoringRuleChangePlan {
    val nextState = currentState.copy(ruleset = nextRuleset)
    val shouldShowLocalScore = isGameEnded || (matchMode == MatchMode.LocalTwoPlayer && !isEngineReady)
    val scoreText = if (shouldShowLocalScore) {
        buildLocalScoreEstimateDisplayPlan(
            state = nextState,
            previousSnapshots = previousSnapshots,
            engineMessage = "",
        ).scoreText
    } else {
        "Score estimate not current."
    }

    return ScoringRuleChangePlan(
        gameState = nextState,
        candidateText = "Scoring rule changed.",
        reviewAnalysis = MoveAnalysisSnapshot.empty(nextState),
        scoreText = scoreText,
        scoreSnapshots = ScoreTimeline.record(
            ScoreTimeline.trimAfter(previousSnapshots, nextState.moves.size),
            localScoreSnapshot(nextState),
        ),
        endgameLog = "Scoring rule changed to ${nextRuleset.scoringLabel}. No endgame result recorded for the new scoring rule yet.",
        engineMessage = if (isEngineReady) {
            null
        } else {
            "Scoring rule changed to ${nextRuleset.scoringLabel}. Local scoring is active."
        },
        requiresEngineSync = isEngineReady,
    )
}
