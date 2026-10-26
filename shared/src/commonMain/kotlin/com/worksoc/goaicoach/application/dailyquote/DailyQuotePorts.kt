package com.worksoc.goaicoach.application.dailyquote

/** 오늘의 명언 진행도(#273)의 저장소 — 섞은 차례와 본 것(백로그 #294 — S7). */
interface DailyQuoteStorePort {
    fun load(): DailyQuoteProgress

    fun save(progress: DailyQuoteProgress)
}
