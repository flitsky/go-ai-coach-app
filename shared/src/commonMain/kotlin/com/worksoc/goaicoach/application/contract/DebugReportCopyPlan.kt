package com.worksoc.goaicoach.application.contract

data class DebugReportCopyPlan(
    val clipboardLabel: String,
    val clipboardReport: String,
    val fileReport: String,
    val engineMessage: String,
    val toastMessage: String,
    val failureToastMessage: String = "Debug report saved to file, but failed to copy to clipboard",
)
