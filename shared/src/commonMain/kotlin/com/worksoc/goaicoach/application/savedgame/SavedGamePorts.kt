package com.worksoc.goaicoach.application.savedgame

/**
 * 진행 중 대국의 저장 계약.
 *
 * ⚠️ **저장 원문(JSON 텍스트)을 돌려주는 메서드를 여기 두지 않는다**(refactor backlog #86).
 * 원문은 문자열이라 타입으로는 값이지만 저장 형식 그 자체다 — 포트에 실리면 흐름 코드가
 * 형식을 읽을 수 있게 된다. 디버그 리포트가 원문을 싣는 길은 어댑터(`GameSessionStore`)의
 * 포트 밖 메서드를 조립 루트가 텍스트 공급자로 꽂는 것이다(`docs/ARCHITECTURE.md` ⓐ).
 */
interface SavedGameStorePort {
    fun save(snapshot: SavedGameSnapshot)
    fun load(): SavedGameSnapshot?
    fun clear()
}
