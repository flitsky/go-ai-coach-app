package com.worksoc.goaicoach.application.engine

/**
 * 엔진이 다른 오퍼레이션을 하고 있어 **기다리지 않고 포기했다**(refactor backlog #15).
 *
 * 던지는 것은 둘뿐이다 — 추천 수 분석([EngineAnalysisClient.analyzePosition])과 형세 추정
 * ([EngineScoringClient.estimateScoreForState]). 그 둘은 기다리지 않는 쪽이 사용자에게 낫다: 추천 수는 기존
 * "잠시 뒤" 흐름(`TopMoveAnalysisDeferral`)으로 미루고, 형세는 지금의 "엔진이 바쁘다" 문구를 보인다. 재동기화·AI 착수·
 * 새 대국 같은 나머지 오퍼레이션은 기다린다. 무엇이 기다리고 무엇이 포기하는지는 `LocalEngineSessionClient`의
 * 오퍼레이션 락 KDoc에 있다.
 *
 * 받는 쪽이 이것을 **실패로 보이면 안 된다** — 실패 문구("Top Moves analysis failed.")를 띄우거나 지난 결과를 지우면
 * 사용자에게는 오늘 없던 오류가 생긴 것이다. 기존의 미루기·바쁨 경로로 보낸다. 대국 밖에서 부르는 보드 스캔 화면은
 * 미룰 곳이 없어 잠시 뒤 다시 누르라는 문구를 보인다(`boardScanAnalysisErrorMessage`).
 *
 * ⚠️ **`CancellationException`으로 만들지 말 것.** 취소는 "호출자가 그만뒀다"는 뜻이라 여러 자리가 결과 없이 흘려보낸다
 * — `launch`는 조용히 끝난 것으로 삼키고, 5계층의 AI 차례(#74)는 호출자가 살아 있는데 올라온 취소를 시간 초과로
 * 읽는다. 포기는 결과다 — 받는 쪽이 보고 미루거나 알린다.
 * ⚠️ **재시도 대상이 아니다**(#17의 Transport 재시도 같은 것) — 다시 하는 것은 받는 쪽의 미루기 경로가 한다.
 */
class EngineOperationBusy(
    /** 포기한 오퍼레이션의 이름(`analyzePosition`·`estimateScoreForState`) — 진단용. */
    val operation: String,
) : RuntimeException("Engine is busy with another operation; `$operation` gave up without waiting.")
