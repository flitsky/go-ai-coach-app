package com.worksoc.goaicoach.application.gamehistory

interface GameHistoryStorePort {
    /**
     * 끝난 대국 하나를 붙인다. [replay]가 `null`이 아니고 비어 있지 않으면 **본문은 따로**
     * 저장하고 [GameHistoryEntry.hasReplay]가 참이 된다.
     *
     * ⚠️ 구현은 [GameHistoryRetentionPolicy]의 두 상한을 지켜야 한다 — 상한을 저장소 밖에
     * 두면 호출부마다 다르게 적용된다.
     */
    fun appendCompletedGame(entry: GameHistoryEntry, replay: GameReplayData? = null)

    /** 목록용 메타데이터만. ⚠️ 리플레이 본문은 읽지 않는다 — [loadReplay]를 쓸 것. */
    fun loadAll(): List<GameHistoryEntry>

    /** 한 판의 리플레이 본문. 저장돼 있지 않으면 `null`. */
    fun loadReplay(id: String): GameReplayData? = null

    /**
     * [id] 기록의 한 줄 평을 바꿔 쓴다(2026-09-20). [note]가 `null`/빈 문자열이면 지운다.
     * 그 id가 목록에 없으면 조용히 아무 일도 하지 않는다 — 번들 참고 기보처럼 애초에 이
     * 저장소를 거치지 않는 기록에 대고 불러도 안전해야 한다.
     */
    fun updateNote(id: String, note: String?) {}
}
