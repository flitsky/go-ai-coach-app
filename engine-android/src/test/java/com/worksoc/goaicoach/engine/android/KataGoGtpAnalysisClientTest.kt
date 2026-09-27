package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GTP 분석의 `finally`(탐색 한도 되돌리기)가 **원래 예외를 가리지 않는지**(refactor backlog #74, 설계 F1).
 *
 * `sendCommand`는 시간 초과 때 프로세스를 내리고(`input = null`) 시간 초과를 던진다. 그 직후 `finally`가
 * `kata-set-param`을 **내려간 프로세스에** 보내면 `IllegalArgumentException("KataGo process input is not
 * initialized")`가 나고, 예전에는 그것이 시간 초과를 **대신했다** — 그래서 5계층은 GTP 경로의 시간 초과를
 * 한 번도 시간 초과로 보지 못했고(진단 `engine.operation.timeout`도 GTP 경로에서는 안 찍혔다) 실패로 보고
 * `genMove`를 불렀다.
 */
class KataGoGtpAnalysisClientTest {
    /** T7 — 탐색은 시간 초과, 되돌리기는 IAE. 올라가는 것은 시간 초과이고, IAE는 suppressed로 남는다. */
    @Test
    fun searchTimeoutSurvivesARestoreFailureOnTheTornDownProcess() = runBlocking {
        val appliedLimits = mutableListOf<AnalysisLimit>()
        val client = KataGoGtpAnalysisClient(
            sendCommand = { _, _ -> withTimeout(1L) { awaitCancellation() } },
            applySearchLimit = { limit ->
                appliedLimits += limit
                if (appliedLimits.size > 1) {
                    throw IllegalArgumentException("KataGo process input is not initialized")
                }
            },
            restoreSearchLimit = { RestoreLimit },
            contextProvider = { emptyNineByNineContext() },
        )

        val thrown = runCatching { client.analyze(effectiveLimit = SearchLimit, requestedLimit = SearchLimit) }
            .exceptionOrNull()

        assertTrue(
            "되돌리기 실패(IAE)가 시간 초과를 가리면 5계층이 시간 초과를 실패로 오판한다: $thrown",
            thrown is TimeoutCancellationException,
        )
        assertTrue(
            "되돌리기 실패는 버리지 않고 suppressed로 남긴다: ${thrown?.suppressed?.toList()}",
            thrown?.suppressed.orEmpty().any { it is IllegalArgumentException },
        )
        assertEquals("되돌리기는 여전히 시도한다", listOf(SearchLimit, RestoreLimit), appliedLimits)
    }

    private fun emptyNineByNineContext(): KataGoAnalysisContext =
        KataGoAnalysisContext(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.Black,
            playedMoves = emptyList(),
            handicapCount = 0,
            initialPlayer = StoneColor.Black,
        )

    private companion object {
        val SearchLimit = AnalysisLimit(visits = 16, timeMillis = 1_000L, candidateCount = 5)
        val RestoreLimit = AnalysisLimit(visits = 64, timeMillis = 10_000L, candidateCount = 20)
    }
}
