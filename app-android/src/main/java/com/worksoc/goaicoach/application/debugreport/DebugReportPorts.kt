package com.worksoc.goaicoach.application.debugreport

internal interface DebugReportMirrorPort {
    fun save(report: String)
}

internal interface ClipboardPort {
    fun setText(label: String, text: String)
}

internal interface UserNoticePort {
    fun showShort(message: String)
}
