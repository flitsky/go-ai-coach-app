package com.worksoc.goaicoach.platform

/** 기기 시간대의 지금 시각(시 · 분 · 초) — 화면 모드의 「시간에 따라」와 스플래시의 낮밤이 읽는다(백로그 #294 — S6). */
data class LocalTimeOfDay(val hour: Int, val minute: Int, val second: Int)

expect fun localTimeOfDay(): LocalTimeOfDay
