package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.DebugReportMirrorPort

internal class DebugReportMirrorStore(
    context: Context,
) : DebugReportMirrorPort {
    private val appContext = context.applicationContext

    override fun save(report: String) {
        appContext.openFileOutput(FileName, Context.MODE_PRIVATE).use { output ->
            output.write(report.toByteArray(Charsets.UTF_8))
        }
    }

    companion object {
        const val FileName: String = "last_debug_report.txt"
    }
}
