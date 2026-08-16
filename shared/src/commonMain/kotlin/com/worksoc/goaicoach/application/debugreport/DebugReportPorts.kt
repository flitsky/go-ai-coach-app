package com.worksoc.goaicoach.application.debugreport

interface DebugReportMirrorPort {
    fun save(report: String)
}

interface ClipboardPort {
    fun setText(label: String, text: String): Boolean
}

interface UserNoticePort {
    fun showShort(message: String)
}
