package com.worksoc.goaicoach.application.gamehistory

/**
 * 대국 기록이 무한히 쌓이지 않도록 막는 **두 겹 안전장치**(백로그 #151, U-4, 2026-09-18 사용자).
 *
 * *"우선 최대 1000개 && 총 저장 용량 100메가바이트 이하로. 안전장치 2개로 갑시다."*
 *
 * ## ⚠️ 왜 두 겹인가 — 하나로는 못 막는다
 * 개수만 걸면 **한 판의 크기가 판 크기·수순 길이에 따라 크게 달라져** 1,000판의 총량을 예측할
 * 수 없다(9x9 50수와 19x19 300수는 자릿수가 다르다). 용량만 걸면 **작은 판이 수만 개** 쌓여
 * 목록 화면이 감당하지 못한다. 그래서 둘 다 건다 — **먼저 걸리는 쪽이 이긴다.**
 *
 * ⚠️ **미는 방향은 언제나 "오래된 것부터"** 다. 최신이 밀리면 방금 둔 판이 사라진다.
 */
object GameHistoryRetentionPolicy {

    /** 보관할 최대 기록 개수. */
    const val MaxEntries: Int = 1_000

    /** 목록 + 리플레이 본문을 모두 합친 최대 바이트. */
    const val MaxTotalBytes: Long = 100L * 1024 * 1024

    /**
     * 한 판이 이 크기를 넘으면 **이례적**이다(백로그 #151의 "100KB 초과 시 경고" 검토 항목).
     *
     * ⚠️ **저장을 막는 값이 아니다.** 대국이 끝나는 순간 모달을 띄워 *"저장할까요?"* 를 묻는 것은
     * 사용자가 답을 고를 근거가 없는 질문이라(용량을 모르고, 지금 못 지우면 영원히 못 지운다)
     * 지금은 **진단 신호로만** 쓴다 — 이 값을 넘긴 기록은 [isOversized]로 가려낼 수 있고,
     * 상한에 먼저 닿는 것도 이들이다.
     */
    const val OversizedEntryBytes: Long = 100L * 1024

    fun isOversized(bytes: Long): Boolean = bytes > OversizedEntryBytes

    /**
     * 상한을 넘는 만큼 **오래된 것부터** 골라낸다.
     *
     * @param entries 최신이 뒤에 오도록 정렬된 목록(저장 순서 그대로).
     * @param bytesOf 각 기록이 차지하는 바이트(메타데이터 + 리플레이 본문).
     * @return 지워야 할 기록의 id. 순서는 오래된 것부터다.
     */
    fun idsToEvict(
        entries: List<GameHistoryEntry>,
        bytesOf: (GameHistoryEntry) -> Long,
    ): List<String> {
        val overCount = (entries.size - MaxEntries).coerceAtLeast(0)
        val evicted = entries.take(overCount).map { it.id }.toMutableList()

        var remaining = entries.drop(overCount)
        var total = remaining.sumOf(bytesOf)
        // ⚠️ 한 판만 남았는데도 상한을 넘으면 더 지우지 않는다 — 방금 둔 판을 지우면
        // "저장했다"고 말해 놓고 아무것도 안 남는다.
        while (total > MaxTotalBytes && remaining.size > 1) {
            val oldest = remaining.first()
            evicted += oldest.id
            total -= bytesOf(oldest)
            remaining = remaining.drop(1)
        }
        return evicted
    }
}
