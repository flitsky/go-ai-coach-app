package com.worksoc.goaicoach.application.dailyfortune

/** 오늘의 운세(#276)의 저장소 — 고른 띠 · 별자리와 그날 쓴 갱신 횟수(백로그 #294 — S7). */
interface DailyFortuneStorePort {
    fun load(): DailyFortuneProgress

    fun save(progress: DailyFortuneProgress)
}
