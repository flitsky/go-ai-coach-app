package com.worksoc.goaicoach.application.session

data class AutoAiTurnUiState(
    val isPending: Boolean = false,
) {
    fun markScheduled(): AutoAiTurnUiState =
        copy(isPending = true)

    fun clearPending(): AutoAiTurnUiState =
        copy(isPending = false)
}

data class AutoAiTurnFailureDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)
