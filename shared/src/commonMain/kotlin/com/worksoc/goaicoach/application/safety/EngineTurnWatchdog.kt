package com.worksoc.goaicoach.application.safety

import com.worksoc.goaicoach.shared.policy.SearchTimeLimit

/**
 * 안전 관리(레프리) 도메인 — AI 차례가 비정상적으로 오래 걸리는지 감지하는 순수 판정 로직.
 * 이전에 고친 KataGo 프로세스 stdin/stdout 레이스 컨디션과는 별개로, 나중에 다른 원인으로
 * 다시 AI 응답이 멎더라도 이 판정으로 감지할 수 있게 한다. 감지에만 집중하며, 자동 복구나
 * 사용자 팝업 여부는 이번 범위에 포함하지 않는다(호출부에서 나중에 선택).
 *
 * 260814: `:shared` 모듈로 이전(GameSessionStateHolder→shared 마이그레이션의 스파이크
 * 대상). 패키지명은 유지했다 — `:shared` 안에서도 `match/`처럼 `application.*` 밖의
 * 패키지가 이미 쓰이고 있어, 모듈 경계와 패키지명은 독립적인 축이라 그대로 둬도 된다.
 * app-android 쪽 호출부(`ui/GamePlaySection.kt`)가 여전히 이 함수를 호출하므로 `internal`을
 * 유지할 수 없다 — Kotlin `internal`은 모듈 스코프라 다른 모듈에서는 안 보인다.
 */

/**
 * 양패스(또는 보드 가득 참) 이후 계가(종국 처리) 엔진 호출의 와치독 한도. 사망돌 판정 +
 * 최종 점수 계산을 순차로 수행하는데, 각 단계의 KataGo 탐색 시간 상한(합계는
 * [com.worksoc.goaicoach.application.engine.AssistantJudgeEndgameTotalTimeCapMillis] = 3초)은
 * KataGo 자체 탐색에만 적용되는 값이라 IPC/파싱 등 부가 지연을 포함한 실제 소요 시간은 이보다
 * 훨씬 길어질 수 있다(실측 사례: 상한 합계 3초인데 실제 8.7초 소요). 일반 착수 시간 제한
 * 기준(예: 4.2초)을 그대로 쓰면 정상적인 계가 처리 도중에도 오탐 팝업이 뜬다.
 */
const val EngineEndgameWatchdogTimeoutMillis: Long = 20_000L

/**
 * 탐색 **밖**에서 드는 몫 — IPC·JSON 파싱·프로세스 스케줄링. KataGo의 탐색 시간 상한은
 * 탐색에만 걸리므로 이만큼은 언제나 더 든다.
 */
const val EngineTurnWatchdogOverheadMillis: Long = 3_000L

/**
 * **기본 엔진 응답 여유**(2026-09-22 사용자 지시) — 「엔진 응답 지연」 팝업이 불필요하게
 * 떴다 사라지는 일을 줄이려고 모든 한도에 한 겹 더 얹는 몫이다.
 *
 * ⚠️ **조절 손잡이는 이것 하나다.** 오탐이 여전하면 여기만 올리면 되고, 판정 로직·호출부·
 * 화면을 건드릴 필요가 없다. 반대로 진짜로 멎은 엔진을 알아채는 데도 그만큼 늦어진다 —
 * 그 둘의 맞바꿈이 이 상수의 전부다.
 * ⚠️ **세 갈래 전부에 붙는다**(계가·시간제한·무제한). 이름이 「기본 여유」인 이유이고,
 * 한 갈래만 붙이면 다른 갈래에서 왜 안 붙는지를 다음 사람이 다시 캐야 한다.
 */
const val EngineResponseGraceMillis: Long = 5_000L

/** 탐색 시간 제한이 꺼져 있을 때(무제한)의 바탕 한도. */
const val UnlimitedSearchWatchdogBaseMillis: Long = 60_000L

/**
 * 대국 설정의 AI 최대 응답 시간에 맞춰 와치독 한도를 계산한다.
 * - [isResolvingEndgame]이면(양패스 이후 계가 처리 중) [EngineEndgameWatchdogTimeoutMillis].
 * - 응답 시간 제한이 설정돼 있으면 그 값의 1.2배 + [EngineTurnWatchdogOverheadMillis].
 * - 응답 시간 제한이 꺼져 있으면(무제한 탐색) [UnlimitedSearchWatchdogBaseMillis].
 *
 * 그리고 **어느 갈래든 마지막에 [EngineResponseGraceMillis]를 더한다.**
 */
fun engineTurnWatchdogTimeoutMillisFor(
    searchTimeLimit: SearchTimeLimit,
    isResolvingEndgame: Boolean = false,
): Long {
    val configuredMillis = searchTimeLimit.maximumMillis
    val baseMillis = when {
        isResolvingEndgame -> EngineEndgameWatchdogTimeoutMillis
        configuredMillis != null -> (configuredMillis * 1.2).toLong() + EngineTurnWatchdogOverheadMillis
        else -> UnlimitedSearchWatchdogBaseMillis
    }
    return baseMillis + EngineResponseGraceMillis
}

/** AI 차례에서 [elapsedSinceTurnStartMillis]가 와치독 한도를 넘겼는지 판정한다. */
fun isEngineTurnWatchdogTriggered(
    isAiTurn: Boolean,
    elapsedSinceTurnStartMillis: Long,
    searchTimeLimit: SearchTimeLimit,
    isResolvingEndgame: Boolean = false,
): Boolean =
    isAiTurn &&
        elapsedSinceTurnStartMillis >= engineTurnWatchdogTimeoutMillisFor(searchTimeLimit, isResolvingEndgame)
