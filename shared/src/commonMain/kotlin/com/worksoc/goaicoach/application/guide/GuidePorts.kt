package com.worksoc.goaicoach.application.guide

/** 첫 실행 안내(첫 돌 · 코치마크)의 진행도 저장소(백로그 #294 — S7). [arm]은 안내를 다시 보이게 한다(개발자 메뉴). */
interface GuideProgressStorePort {
    fun load(): GuideProgress

    fun arm()

    fun markSeen(step: GuideStep)
}

/** 「기본 사활」(#184)에서 푼 문제의 id 모음. [markSolved]는 더한 뒤의 모음을 돌려준다. */
interface LifeAndDeathProgressPort {
    fun solved(): Set<String>

    fun markSolved(problemId: String): Set<String>
}
