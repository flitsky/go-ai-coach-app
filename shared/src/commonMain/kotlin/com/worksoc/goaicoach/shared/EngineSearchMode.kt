package com.worksoc.goaicoach.shared

enum class EngineSearchMode(
    val label: String,
    val description: String,
) {
    GtpStatefulFast(
        label = "GTP Stateful Fast",
        description = "Use the current stateful GTP process path with maxVisits/maxTime and optional search-cache isolation.",
    ),
    JsonPositionAnalysis(
        label = "JSON Position Analysis",
        description = "Analyze an explicit board position with a JSON analysis query. Experimental for AI-vs-AI isolation.",
    ),
}
