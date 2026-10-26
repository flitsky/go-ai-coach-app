package com.worksoc.goaicoach.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.worksoc.goaicoach.application.debugreport.ClipboardPort

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
) : AppNotice {
    private val appContext = context.applicationContext

    override fun showShort(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun show(message: String, long: Boolean): NoticeHandle {
        val toast = Toast.makeText(appContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
        toast.show()
        return NoticeHandle { toast.cancel() }
    }
}
