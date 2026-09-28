package com.worksoc.goaicoach.shared.enginecontract

/**
 * 엔진 호출 하나를 **얼마나 기다리는가** — 로컬(`KataGoProcessEngineAdapter`)과 원격(`RemoteEngineCoreApiAdapter`)이,
 * 그리고 진단이 적는 `timeoutMillis`가 같은 함수를 쓴다(refactor backlog #17). 예전에는 같은 [AnalysisLimit]에
 * 로컬은 캡+20초(캡 없으면 120초), 원격은 늘 33초(연결 3초 + 읽기 30초)였고, 진단은 캡 자체를 적었다.
 */

/** 탐색 시간 상한이 따로 없는 명령(play·undo·komi·kata-raw-nn·final_score …)의 마감. */
const val DefaultCommandTimeoutMillis: Long = 30_000L

/** 탐색 시간 캡 위에 얹는 여유 — KataGo가 캡을 넘겨 하는 뒷정리를 흡수한다. */
private const val SearchTimeoutSlackMillis: Long = 20_000L

/** 캡이 없는("Off") 탐색의 마감 — 그때도 KataGo는 maxVisits로 묶여 있다. */
private const val UnboundedSearchTimeoutMillis: Long = 120_000L

/** 탐색이 걸린 호출(genmove·분석 쿼리)의 마감 — 설정된 탐색 시간 캡 [timeMillis]에서 정한다. */
fun searchTimeoutMillisFor(timeMillis: Long?): Long =
    timeMillis?.let { it + SearchTimeoutSlackMillis } ?: UnboundedSearchTimeoutMillis

/**
 * `analyze()`가 실제로 거는 탐색 시간 캡 — [AnalysisLimit.minTimeMillis]를 바닥으로 한다(캡이 없고 바닥만 있으면
 * 바닥이 캡이 된다). 분석의 마감은 이 값으로 [searchTimeoutMillisFor]를 부른 것이다.
 */
fun AnalysisLimit.analysisSearchTimeMillis(): Long? =
    minTimeMillis?.let { minimum -> timeMillis?.coerceAtLeast(minimum) ?: minimum } ?: timeMillis

/** `analyze()` 한 번의 탐색 마감 — 진단이 적는 `timeoutMillis`가 이것이다. */
fun AnalysisLimit.analysisSearchTimeoutMillis(): Long = searchTimeoutMillisFor(analysisSearchTimeMillis())
