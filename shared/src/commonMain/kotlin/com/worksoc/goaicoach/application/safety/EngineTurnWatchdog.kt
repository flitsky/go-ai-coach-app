package com.worksoc.goaicoach.application.safety

import com.worksoc.goaicoach.application.engine.operation.EngineWaitProcessPauseThresholdMillis
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
 * app-android 쪽 호출부(`ui/play/GamePlaySection.kt`)가 여전히 이 함수를 호출하므로 `internal`을
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

/**
 * 「엔진 응답 지연」 팝업 하나가 두 순간을 맡는다(refactor backlog #74, 설계 C-8·C-9).
 * - 상태 A — 와치독 한도를 넘긴 순간([isWatchdogTriggered], 화면 지역 상태). 차례 대기 작업이 끝나면 저절로 닫힌다.
 * - 상태 B — 탐색이 시간 초과로 **끝난** 뒤 사용자의 선택을 기다리는 동안([isAwaitingTimeoutChoice], 세션 상태).
 *   같은 국면에서 진짜 실패가 잇달아 나도 이 상태다(refactor backlog #109).
 *   그 끝남 자체가 차례 대기 작업의 완료라, 완료로 닫히는 것은 A의 지역 표시뿐이다 — B는 사용자가 고를 때까지 남는다.
 * 둘 중 하나면 **한 벌만** 뜬다(둘이 겹쳐도 팝업은 하나다).
 */
fun isEngineStuckDialogVisible(
    isWatchdogTriggered: Boolean,
    isAwaitingTimeoutChoice: Boolean,
): Boolean = isWatchdogTriggered || isAwaitingTimeoutChoice

/** 「한 번 더 기다리기」가 무엇을 하는가(refactor backlog #74, 설계 C-10). */
enum class EngineStuckWaitAction {
    /**
     * 상태 A — 팝업을 닫고 **도는 요청을 그대로 둔 채** 와치독을 지금부터 다시 건다. 막힌 GTP 탐색은 프로세스를
     * 죽이지 않고는 다시 요청할 수 없으므로(그것은 「엔진 다시 시작하기」의 몫) 여기서는 기다리기만 한다.
     */
    KeepWaitingAndRearm,

    /** 상태 B — 끝난 차례를 같은 국면·같은 예산으로 다시 요청하고 와치독을 다시 건다. */
    RetryTimedOutTurnAndRearm,
}

fun engineStuckWaitActionFor(isAwaitingTimeoutChoice: Boolean): EngineStuckWaitAction =
    if (isAwaitingTimeoutChoice) {
        EngineStuckWaitAction.RetryTimedOutTurnAndRearm
    } else {
        EngineStuckWaitAction.KeepWaitingAndRearm
    }

/**
 * 와치독이 경과 시간을 재는 기준 시각(refactor backlog #74). 차례가 시작된 시각이고, 「한 번 더 기다리기」·
 * 「엔진 다시 시작하기」를 누르면 그 순간으로 **다시 건다** — 그래야 같은 차례에서도 한도만큼 더 지난 뒤 팝업이
 * 다시 뜬다. 새 차례가 시작되면 그 차례의 시작 시각으로 돌아간다(누른 시각이 더 이르면 차례 시작이 이긴다).
 */
fun engineTurnWatchdogBaseMillis(
    turnStartedAtMillis: Long,
    rearmedAtMillis: Long?,
): Long = maxOf(turnStartedAtMillis, rearmedAtMillis ?: turnStartedAtMillis)

/**
 * 와치독이 재는 **한 번의 시도**(refactor backlog #109). 한 차례 안에도 시도는 여럿일 수 있다 — 진짜 실패 뒤의 조용한
 * 재시도가 같은 차례의 새 시도다.
 *
 * 예전에는 와치독이 **차례마다** 한 번만 보고했다. 첫 시도에서 팝업이 뜨고 그 시도가 실패로 끝나 팝업이 닫히면(완료
 * 순번), 같은 차례의 재시도가 멎어도 팝업도 「엔진 다시 시작하기」도 다시 없었다 — 보고 표시가 남고, 차례 시작 시각이
 * 키라 바뀌지 않았다. 그래서 **차례 대기 작업이 끝날 때마다**(완료 순번 [completionSeq]가 바뀔 때마다) 그 순간부터 다시
 * 잰다 — 다음 시도가 곧 그 뒤에 시작된다. 순번은 바뀌었는지만 본다(엔진 수명 리셋으로 0이 돼도 바뀐 것이다).
 *
 * @property baseMillis 이 시도의 경과를 재는 기준 — 처음은 [engineTurnWatchdogBaseMillis], 다시 걸면 그 순간.
 * @property isReported 이 시도에서 이미 팝업을 띄웠는가 — 한 시도에 한 번만 띄운다.
 */
data class EngineTurnWatchdogAttempt(
    val baseMillis: Long,
    val completionSeq: Int,
    val isReported: Boolean = false,
) {
    /** 지금의 완료 순번을 본다. 바뀌었으면 앞 시도가 끝난 것이다 — [nowMillis]부터 새 시도로 다시 건다. */
    fun observe(nowMillis: Long, completionSeq: Int): EngineTurnWatchdogAttempt =
        if (completionSeq == this.completionSeq) {
            this
        } else {
            EngineTurnWatchdogAttempt(baseMillis = nowMillis, completionSeq = completionSeq)
        }

    fun elapsedMillis(nowMillis: Long): Long = (nowMillis - baseMillis).coerceAtLeast(0L)

    fun reported(): EngineTurnWatchdogAttempt = copy(isReported = true)

    /**
     * 대국 시계가 멈췄다가(앱이 화면을 떠났다가) 다시 걸린 첫 시도를 고친다(backlog #202). 와치독 루프는 멈춘 동안
     * 돌지 않아 그사이 끝난 시도를 [observe]로 못 본다 — 그래서 멈춘 순간의 완료 순번 [completionSeqAtPause]와
     * 지금 순번을 맞대, 다르면 [nowMillis]부터 새 시도로 잰다.
     *
     * 그 사이에 시도가 끝나는 경우가 곧 앱이 백그라운드로 가며 AI 차례를 **취소**한 경우다 — 돌아와 새로 요청한
     * 탐색을 차례 시작부터 재면, 나가기 전에 흐른 시간이 얹혀 멀쩡한 탐색에 「엔진 응답 지연」이 뜬다(2026-10-01 에뮬레이터
     * 실측: 복귀 2.3초 만에 `elapsedMillis=31898`). 순번이 그대로면(멈춘 사이 끝난 것이 없다 — 광고 화면 등으로
     * 가려졌을 뿐 탐색은 계속됐다) 그대로 둔다. 멈춘 적이 없으면([completionSeqAtPause]가 null) 그대로다.
     */
    fun resumedAfterPause(completionSeqAtPause: Int?, nowMillis: Long): EngineTurnWatchdogAttempt =
        if (completionSeqAtPause == null || completionSeqAtPause == completionSeq) {
            this
        } else {
            EngineTurnWatchdogAttempt(baseMillis = maxOf(baseMillis, nowMillis), completionSeq = completionSeq)
        }

    /**
     * 와치독 루프의 두 틱 사이에 **이 프로세스가 화면에 있는 채로 멈춰 있었으면**(수명 콜백 없는 동결·에뮬레이터 VM 정지 —
     * backlog #204) 이 시도를 [nowMillis]부터 다시 잰다. 멈춘 동안은 엔진도(같은 동결에 묶인 KataGo 자식) 아무것도 못
     * 했으니 그 시간을 멎음으로 세면, 풀리는 첫 틱에 멀쩡한 엔진에 「엔진 응답 지연」이 뜬다.
     *
     * 틈은 틱 간격을 뺀 만큼이고, 문턱은 차례 시간 초과의 판정과 같다([EngineWaitProcessPauseThresholdMillis]) — 살아
     * 있는 메인 스레드는 틱을 10초씩 밀리지 않는다(5초면 이미 ANR). 앱이 화면을 떠나는 경우(`ON_PAUSE`)는 대국 시계가 멈춰
     * 루프도 멈추므로 여기에 오지 않는다 — 그쪽은 [resumedAfterPause]의 몫이다. 이미 보고한 시도는 그대로 보고한 채다.
     */
    fun resumedAfterProcessPause(
        previousTickMillis: Long,
        nowMillis: Long,
        tickIntervalMillis: Long,
    ): EngineTurnWatchdogAttempt =
        if (nowMillis - previousTickMillis - tickIntervalMillis >= EngineWaitProcessPauseThresholdMillis) {
            copy(baseMillis = maxOf(baseMillis, nowMillis))
        } else {
            this
        }
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
