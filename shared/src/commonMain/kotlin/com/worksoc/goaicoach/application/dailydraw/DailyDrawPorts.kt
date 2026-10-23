package com.worksoc.goaicoach.application.dailydraw

/**
 * 실험실 「1일 1회 뽑기」(백로그 #283)의 저장소 포트 — 마지막으로 뽑은 때. 어댑터는 app-android `persistence/DailyDrawStore.kt`.
 * 같은 계층의 다른 패키지처럼 포트는 이 파일에 따로 둔다(`ConsumablePorts.kt` · `RankMeasurePorts.kt`와 같은 결).
 */
interface DailyDrawStorePort {
    /** 마지막으로 뽑은 때(epoch ms). 한 번도 뽑지 않았으면 `null`. */
    fun lastDrawnAtMillis(): Long?

    fun markDrawn(atMillis: Long)
}
