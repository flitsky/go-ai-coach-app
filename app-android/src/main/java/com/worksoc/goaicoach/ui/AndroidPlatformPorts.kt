package com.worksoc.goaicoach.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort

internal class AndroidClipboardPort(
    context: Context,
) : ClipboardPort {
    private val appContext = context.applicationContext

    override fun setText(label: String, text: String): Boolean {
        return try {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (e: Throwable) {
            System.err.println("Failed to copy text to clipboard: ${e.message}")
            false
        }
    }
}

internal class AndroidUserNoticePort(
    context: Context,
) : UserNoticePort {
    private val appContext = context.applicationContext

    override fun showShort(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }
}
