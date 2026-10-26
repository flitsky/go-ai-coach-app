package com.worksoc.goaicoach.platform

import com.worksoc.goaicoach.application.debugreport.UserNoticePort

/** 짧은 알림 한 줄(안드로이드 토스트). [show]가 돌려준 손잡이로 띄운 알림을 거둔다 — 같은 알림이 쌓이지 않게(대국의 「한 번 더 누르면 둔다」). */
interface AppNotice : UserNoticePort {
    fun show(message: String, long: Boolean = false): NoticeHandle

    override fun showShort(message: String) {
        show(message)
    }
}

fun interface NoticeHandle {
    fun cancel()
}
