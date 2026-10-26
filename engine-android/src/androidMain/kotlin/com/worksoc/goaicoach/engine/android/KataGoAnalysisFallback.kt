package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisFallbackRecord
import kotlinx.coroutines.CancellationException

/** JSON analysis 엔진 경로의 이름 — 진단 이벤트에 그대로 실린다. */
internal const val JsonPositionAnalysisPath = "json-position-analysis"

/** GTP `kata-analyze` 경로의 이름. */
internal const val GtpAnalysisPath = "gtp-analyze"

/**
 * JSON 분석 한 번의 결과 — 값이 나왔는지, 폴백을 타야 하는지.
 *
 * [result]가 `null`이면 GTP로 내려간다. [fallback]이 붙어 있으면 그 내려감이 **사고 때문**이고,
 * `null`이면 애초에 JSON 경로가 구성돼 있지 않았다는 뜻이다(설정 상태이지 사고가 아니므로
 * 진단 이벤트를 남기지 않는다 — 남기면 그런 빌드에서는 분석마다 경고가 찍힌다).
 */
internal class JsonAnalysisAttempt<out T>(
    val result: T?,
    val fallback: AnalysisFallbackRecord?,
)

/**
 * JSON 분석을 시도하되 **취소는 삼키지 않는다**(refactor backlog #16ⓐ).
 *
 * ## 왜 `runCatching`이면 안 되는가
 * 예전 코드는 `runCatching { ... }.getOrNull()`이었다. [Throwable]을 전부 잡으므로
 * [CancellationException]도 잡혔고, 그 결과 두 가지가 동시에 망가졌다:
 *
 * 1. **취소가 전파되지 않는다.** 사용자가 수를 물리거나 대국을 떠나 코루틴이 취소돼도, 분석은
 *    "실패했네"라며 **GTP 경로로 한 번 더 내려가** 엔진을 계속 붙잡았다.
 * 2. **타임아웃이 예산을 두 번 쓴다.** `sendAnalysisQuery`의 타임아웃은
 *    [kotlinx.coroutines.TimeoutCancellationException] — 즉 [CancellationException]이다.
 *    예산을 다 써서 끊긴 요청을 **같은 예산으로 GTP에서 또 돌렸다**. 느린 기기에서 한 수가
 *    두 배로 느려지는 자리가 정확히 여기다. 그래서 타임아웃은 폴백 대상에서 **제외**한다.
 *
 * 남은 진짜 실패(설정 파일 문제, 프로세스 기동 실패, 깨진 응답 등)만 폴백을 타고, **그 사실은
 * [AnalysisFallbackRecord]로 남는다** — 지금까지는 완전 무음이었다.
 */
internal suspend fun <T> attemptJsonAnalysis(
    jsonAnalysis: suspend () -> T?,
): JsonAnalysisAttempt<T> =
    try {
        JsonAnalysisAttempt(jsonAnalysis(), fallback = null)
    } catch (cancellation: CancellationException) {
        // 취소·타임아웃은 "실패"가 아니라 "그만두라"는 지시다. 폴백하지 않고 그대로 올려보낸다.
        throw cancellation
    } catch (error: Exception) {
        JsonAnalysisAttempt(
            result = null,
            fallback = AnalysisFallbackRecord(
                fromPath = JsonPositionAnalysisPath,
                toPath = GtpAnalysisPath,
                reason = "${error::class.simpleName ?: "Exception"}: ${error.message ?: "no message"}",
            ),
        )
    }
