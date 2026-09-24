package com.worksoc.goaicoach.engine.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * `analyze()`의 폴백 판정(refactor backlog #16ⓐ).
 *
 * 이 파일이 검사하는 것은 **정책**이다 — 무엇을 삼키고 무엇을 올려보내는가.
 * `KataGoProcessEngineAdapter` 전체는 실제 KataGo 프로세스가 있어야 돌므로 단위 테스트로
 * 덮을 수 없고, 그래서 판정만 [attemptJsonAnalysis]로 떼어 내 여기서 고정한다.
 * ⚠️ "연속 착수 중 취소가 실제로 전파되는가"는 여기서 증명되지 않는다 — 실기 확인 항목이다.
 */
class KataGoAnalysisFallbackTest {
    @Test
    fun successfulJsonAnalysisCarriesNoFallback() = runBlocking {
        val attempt = attemptJsonAnalysis { "json-result" }

        assertEquals("json-result", attempt.result)
        assertNull(attempt.fallback)
    }

    @Test
    fun unconfiguredJsonPathFallsBackSilentlyBecauseItIsNotAnIncident() = runBlocking {
        // JSON 분석 설정이 없는 빌드는 매 분석마다 이 길로 간다. 사고가 아니므로 진단
        // 이벤트를 남기지 않는다 — 남기면 그런 빌드에서는 경고가 끝없이 찍힌다.
        val attempt = attemptJsonAnalysis<String> { null }

        assertNull(attempt.result)
        assertNull(attempt.fallback)
    }

    @Test
    fun realFailureFallsBackAndSaysWhy() = runBlocking {
        val attempt = attemptJsonAnalysis<String> { throw IOException("analysis process died") }

        assertNull(attempt.result)
        val fallback = requireNotNull(attempt.fallback)
        assertEquals(JsonPositionAnalysisPath, fallback.fromPath)
        assertEquals(GtpAnalysisPath, fallback.toPath)
        assertEquals("IOException: analysis process died", fallback.reason)
    }

    /**
     * ⚠️ **이 일감의 본체.** 사용자가 수를 물리거나 대국을 떠나 코루틴이 취소됐는데도
     * 폴백을 타면, 엔진은 아무도 기다리지 않는 분석을 한 번 더 돈다.
     */
    @Test
    fun cancellationIsRethrownInsteadOfBecomingAFallback() = runBlocking {
        try {
            attemptJsonAnalysis<String> { throw CancellationException("user left the game") }
            fail("Cancellation must propagate, not turn into a GTP fallback")
        } catch (cancellation: CancellationException) {
            assertEquals("user left the game", cancellation.message)
        }
    }

    /**
     * ⚠️ 타임아웃도 취소다. **예산을 다 써서 끊긴 요청을 같은 예산으로 또 태우는 것**이
     * 문제의 핵심이라, 이것만큼은 폴백 대상에서 빠져야 한다.
     */
    @Test
    fun timeoutIsRethrownSoTheSameBudgetIsNotSpentTwice() = runBlocking {
        try {
            attemptJsonAnalysis<String> {
                withTimeout(1L) {
                    delay(10_000L)
                    "never"
                }
            }
            fail("A timed-out analysis must not be retried on the GTP path")
        } catch (timeout: TimeoutCancellationException) {
            assertTrue(timeout is CancellationException)
        }
    }
}
