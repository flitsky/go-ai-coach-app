package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.application.concurrency.sharedLock
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 이 프로세스가 **얼마나 오래 못 돌았으면** 그 기다림을 「중단」으로 보는가(backlog #204).
 *
 * **10초인 이유** — 탐색 마감은 캡 위에 20초를 얹는다(`searchTimeoutMillisFor`). 엔진이 캡 안에 답하는 한, 멈춤이
 * 마감을 넘기게 하려면 적어도 그 20초를 삼켜야 한다 — 그보다 짧은 멈춤은 오탐 팝업을 만들지 못한다. 반대로 살아 있는
 * 프로세스에서 박동이 10초씩 밀리는 일은 없다(GC·CPU 경합은 수백 ms, 메인 스레드가 5초 막히면 이미 ANR이다). 그 사이의
 * 넉넉한 값이다.
 */
const val EngineWaitProcessPauseThresholdMillis: Long = 10_000L

/** 박동 간격 — 대국 시계의 틱과 같은 1초. */
const val EngineWaitHeartbeatIntervalMillis: Long = 1_000L

/**
 * AI 차례가 엔진을 기다리는 동안 **앱이 멈춰 있었는가**(backlog #204). 둘 중 하나라도 참이면 그 기다림의 마감은
 * 믿을 수 없다 — 마감은 멈춘 동안에도 흐르는 단조 시계로 재므로, 멈춘 사이 엔진은 아무것도 못 했는데 시간만 지났다.
 *
 * @property backgroundedDuringWait 기다리는 사이 포그라운드 세대([EngineOperationLifecycleController.foregroundGeneration])가
 *   바뀌었다 — 앱이 화면을 떠났다 돌아왔다.
 * @property processPauseMillis 기다리는 사이 이 프로세스가 가장 오래 **못 돈** 시간(박동이 늦은 만큼). 동결·VM 정지처럼
 *   수명 콜백이 오지 않거나 늦게 오는 멈춤을 잡는다 — 0에 가까우면 계속 돌았다.
 */
data class EngineWaitInterruption(
    val backgroundedDuringWait: Boolean,
    val processPauseMillis: Long,
) {
    val processPausedDuringWait: Boolean get() = processPauseMillis >= EngineWaitProcessPauseThresholdMillis

    /** 이 기다림의 시간 초과를 엔진 탓으로 보지 않는다 — 같은 국면을 조용히 한 번 다시 요청할 근거다. */
    val isInterrupted: Boolean get() = backgroundedDuringWait || processPausedDuringWait

    companion object {
        val None = EngineWaitInterruption(backgroundedDuringWait = false, processPauseMillis = 0L)
    }
}

/**
 * 엔진을 기다리는 사이 **이 프로세스가 멈춰 있었는지** 재기 시작한다(backlog #204). 기다림마다 하나를 연다.
 *
 * 수명 콜백(`ON_STOP`)만으로는 안 되는 이유: 안드로이드의 동결은 콜백을 기다려 주지 않는다. `ProcessLifecycleOwner`는
 * `ON_STOP`을 700ms 늦게 보내고, 액티비티의 stop 자체도 바인더에 쌓였다가 해동 뒤에 온다. 해동 순간에는 마감(엔진 IO
 * 스레드)과 그 콜백(메인)이 누가 먼저랄 것 없이 풀리고, **마감이 먼저면** 차례는 이미 시간 초과로 끝난 뒤라 세대는 아직
 * 그대로다. 에뮬레이터 VM 정지처럼 콜백이 아예 없는 멈춤도 있다. 그래서 콜백과 무관하게, 마감을 재는 것과 **같은 단조
 * 시계**로 박동의 틈을 잰다 — 틈이 곧 「시계는 흘렀는데 우리는 못 돌았다」이다.
 */
fun interface EngineWaitPauseProbe {
    fun start(): EngineWaitPauseMeasurement
}

/** 기다림 하나의 멈춤 측정. */
fun interface EngineWaitPauseMeasurement {
    /** 재는 것을 끝내고, 이 프로세스가 가장 오래 못 돈 시간을 돌려준다. 두 번째부터는 같은 값이다. */
    fun finish(): Long
}

/** 멈춤을 재지 않는다 — 늘 0. 배선하지 않은 자리의 기본값이다(지금과 같은 동작). */
val UnobservedEngineWaitPauseProbe: EngineWaitPauseProbe = EngineWaitPauseProbe { EngineWaitPauseMeasurement { 0L } }

/**
 * [scope]에서 [intervalMillis]마다 박동을 쳐 틈을 잰다. 박동은 `Dispatchers.Default`에서 친다 — 메인 스레드가 바빠
 * 늦는 것을 멈춤으로 읽지 않으려고. 기다림이 끝나면(성공·실패·시간 초과·취소 모두 러너의 `finally`) 멈춘다.
 *
 * 시계는 탐색 마감과 같은 단조 시계다. 그래서 기기가 잠든 동안(단조 시계도 멈춘다)은 틈이 아니고, 마감도 흐르지 않았으니
 * 맞는 판단이다. 동결은 단조 시계를 멈추지 않는다 — 그 틈이 잡으려는 것이다.
 */
class HeartbeatEngineWaitPauseProbe(
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = ::monotonicClockMillis,
    private val intervalMillis: Long = EngineWaitHeartbeatIntervalMillis,
) : EngineWaitPauseProbe {
    override fun start(): EngineWaitPauseMeasurement {
        val meter = EngineWaitPauseMeter(clockMillis = clockMillis, intervalMillis = intervalMillis)
        val heartbeat = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(intervalMillis)
                meter.beat()
            }
        }
        return EngineWaitPauseMeasurement {
            heartbeat.cancel()
            meter.finish()
        }
    }
}

/**
 * 박동 사이의 틈을 모은다. 틈 = 앞 박동부터 지금까지 − 박동 간격(늦은 만큼). **끝낼 때도 마지막 박동부터 지금까지를
 * 잰다** — 해동 순간에는 밀린 박동보다 시간 초과의 처리가 먼저 올 수 있고, 그때 틈은 아직 어느 박동에도 안 잡혀 있다.
 * 이것이 있어 박동과 판정의 순서에 기대지 않는다.
 */
internal class EngineWaitPauseMeter(
    private val clockMillis: () -> Long,
    private val intervalMillis: Long,
) {
    private val lock = sharedLock()
    private var lastBeatMillis = clockMillis()
    private var longestPauseMillis = 0L
    private var finishedPauseMillis: Long? = null

    fun beat() {
        lock.withLock {
            if (finishedPauseMillis != null) return@withLock
            val now = clockMillis()
            record(now)
            lastBeatMillis = now
        }
    }

    fun finish(): Long = lock.withLock {
        finishedPauseMillis ?: run {
            record(clockMillis())
            longestPauseMillis.also { finishedPauseMillis = it }
        }
    }

    private fun record(nowMillis: Long) {
        val pause = (nowMillis - lastBeatMillis - intervalMillis).coerceAtLeast(0L)
        if (pause > longestPauseMillis) longestPauseMillis = pause
    }
}

/**
 * AI 차례 하나가 엔진을 기다리는 동안의 관찰(backlog #204) — 시작할 때의 포그라운드 세대와 멈춤 측정을 든다.
 * [EngineOperationLifecycleController.startEngineWaitWatch]가 연다.
 */
class EngineWaitWatch(
    private val foregroundGenerationAtStart: Long,
    private val currentForegroundGeneration: () -> Long,
    private val pause: EngineWaitPauseMeasurement,
) {
    private var result: EngineWaitInterruption? = null

    /**
     * 기다림을 끝내고 판정을 굳힌다 — 러너는 엔진 호출 바로 뒤(`finally`)에 부른다. 두 번째부터는 굳힌 값을 돌려준다
     * (그 뒤의 전환은 이 기다림의 일이 아니다).
     */
    fun finish(): EngineWaitInterruption =
        result ?: EngineWaitInterruption(
            backgroundedDuringWait = currentForegroundGeneration() != foregroundGenerationAtStart,
            processPauseMillis = pause.finish(),
        ).also { result = it }

    companion object {
        /** 아무것도 재지 않는 관찰 — 늘 「중단 없음」. */
        fun unobserved(): EngineWaitWatch =
            EngineWaitWatch(
                foregroundGenerationAtStart = 0L,
                currentForegroundGeneration = { 0L },
                pause = EngineWaitPauseMeasurement { 0L },
            )
    }
}

private val MonotonicOrigin = TimeSource.Monotonic.markNow()

/** 탐색 마감과 같은 단조 시계(안드로이드 `CLOCK_MONOTONIC`)의 밀리초 — 원점은 의미가 없고 차이만 쓴다. */
internal fun monotonicClockMillis(): Long = MonotonicOrigin.elapsedNow().inWholeMilliseconds
