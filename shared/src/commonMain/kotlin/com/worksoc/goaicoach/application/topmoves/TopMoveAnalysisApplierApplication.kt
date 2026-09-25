package com.worksoc.goaicoach.application.topmoves

internal fun applyTopMoveAnalysisCompletionApplication(
    request: TopMoveAnalysisCompletionApplyRunRequest,
) {
    when (val applyPlan = request.applyPlan) {
        is TopMoveAnalysisCompletionApplyPlan.ApplySuccess -> {
            request.applyTopMoveAnalysisUpdate(applyPlan.update, applyPlan.analysisKey)
            applyPlan.update.undoRestoreResult?.let { cached ->
                request.putUndoRestoreCache(applyPlan.analysisKey, cached)
            }
            applyPlan.update.cachedResult?.let { cached ->
                request.putAnalysisCache(applyPlan.analysisKey, cached)
            }
        }

        is TopMoveAnalysisCompletionApplyPlan.ApplyFailure ->
            request.applyFailureDisplay(applyPlan.display)

        is TopMoveAnalysisCompletionApplyPlan.Discard ->
            request.appendEngineOperationDiscardLog(applyPlan.discard)
    }
}
