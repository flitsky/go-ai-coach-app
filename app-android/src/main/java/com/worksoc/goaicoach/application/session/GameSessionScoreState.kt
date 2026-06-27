package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.application.score.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateFailureDisplayPlan
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class GameSessionScoreState(
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val finalScoreJudgement: FinalScoreJudgement? = null,
) {
    fun applyScoreEstimateDisplayPlan(score: ScoreEstimateDisplayPlan): GameSessionScoreState =
        copy(
            scoreText = score.scoreText,
            scoreEstimate = score.scoreEstimate,
            scoreSnapshots = score.scoreSnapshots,
            finalScoreJudgement = null,
        )

    fun applyScoreEstimateFailureDisplayPlan(
        @Suppress("UNUSED_PARAMETER") failure: ScoreEstimateFailureDisplayPlan,
    ): GameSessionScoreState =
        copy(scoreEstimate = null)

    fun applyFinalScoreDisplayPlan(final: FinalScoreDisplayPlan): GameSessionScoreState =
        copy(
            scoreText = final.scoreText,
            scoreEstimate = final.scoreEstimate,
            scoreSnapshots = final.scoreSnapshots,
            endgameLog = final.endgameLog,
            finalScoreJudgement = final.judgement,
        )

    fun applyEndgameFailureDisplayPlan(failure: EndgameFailureDisplayPlan): GameSessionScoreState =
        copy(endgameLog = failure.endgameLog)

    fun replaceSnapshots(snapshots: List<ScoreSnapshot>): GameSessionScoreState =
        copy(scoreSnapshots = snapshots)

    companion object {
        fun reset(
            scoreText: String,
            scoreSnapshots: List<ScoreSnapshot>,
            endgameLog: String,
        ): GameSessionScoreState =
            GameSessionScoreState(
                scoreText = scoreText,
                scoreEstimate = null,
                scoreSnapshots = scoreSnapshots,
                endgameLog = endgameLog,
                finalScoreJudgement = null,
            )
    }
}
