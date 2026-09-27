package com.worksoc.goaicoach.engine.android

import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 어느 엔진 프로세스인가 — 진단 줄에 그대로 실린다. */
internal enum class EngineProcessKind(val label: String) {
    Gtp("gtp"),
    Analysis("analysis"),
}

/**
 * 프로세스를 내리는 이유. [forcible]이면 SIGKILL이다 — 멈춘 프로세스(SIGSTOP된 것 포함)도, SIGTERM을 붙잡는
 * 프로세스도 확실히 내려 막힌 파이프 읽기를 EOF로 푼다(기기의 Linux에서는 그것만이 푼다 — 스트림을 닫아도,
 * 스레드를 인터럽트해도 안 풀린다). [Stop]만 오늘처럼 `quit` 뒤의 SIGTERM이다.
 */
internal enum class EngineProcessRetireReason(val forcible: Boolean) {
    /** 왕복이 제 마감을 넘겼다. */
    Timeout(forcible = true),

    /** 「엔진 다시 시작하기」(`forceReset`). */
    ForceReset(forcible = true),

    /** 파이프가 끝났거나(EOF·입출력 오류) 이미 죽어 있었다 — 크래시·저메모리 킬러. */
    Died(forcible = true),

    /** `stop()` — `quit` 뒤의 정상 종료. */
    Stop(forcible = false),
}

/**
 * 띄운 프로세스 하나 — `process`/`input`/`output`을 묶은 값 객체(refactor backlog #14).
 *
 * 엔진 호출 하나는 시작할 때 핸들 하나를 잡고 끝까지 **그 핸들에만** 쓴다. 핸들이 내려가면(retire) 그 뒤의
 * 왕복은 보내지 않고 곧바로 실패한다 — 호출이 도중에 다른 프로세스로 옮겨 가지 않는다(새 프로세스는 판이
 * 비어 있으니, 옮겨 가면 반쪽 설정이 조용히 엉뚱한 판에 들어간다).
 *
 * 한 번 내려간 핸들은 되살아나지 않는다. [generation]은 그 종류에서 몇 번째로 뜬 프로세스인가(1부터).
 */
internal class EngineProcessHandle(
    val kind: EngineProcessKind,
    val generation: Long,
    private val pipes: EngineProcessPipes,
) {
    private val startedAtNanos = System.nanoTime()
    private val retirement = AtomicReference<EngineProcessRetireReason?>(null)

    /**
     * 이 프로세스의 stdin/stdout 왕복을 한 번에 하나로 — 두 호출이 같은 스트림에서 서로의 답 줄을 가로채지
     * 않게. 스트림이 프로세스마다 따로라 락도 프로세스마다 따로다: 내려간 세대에서 멈춘 호출이 이 락을 쥐고
     * 있어도 다음 세대의 호출은 기다리지 않는다.
     */
    val roundTripMutex = Mutex()

    val writer: BufferedWriter
        get() = pipes.writer

    val reader: BufferedReader
        get() = pipes.reader

    val retireReason: EngineProcessRetireReason?
        get() = retirement.get()

    /** 아직 내려가지 않았고 프로세스도 살아 있다. */
    val isUsable: Boolean
        get() = retirement.get() == null && pipes.isAlive

    val ageMillis: Long
        get() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    /**
     * 처음 부른 쪽만 `true`이고 그쪽만 프로세스를 내린다 — 몇 번을 불러도 안전하다.
     *
     * ⚠️ reader/writer는 닫지 않는다([EngineProcessPipes] KDoc — 막힌 `readLine()`과 같은 락을 잡는다).
     * 락·대기·서스펜드가 없어 메인 스레드의 `forceReset`에서 불러도 된다.
     */
    fun retire(reason: EngineProcessRetireReason): Boolean {
        if (!retirement.compareAndSet(null, reason)) return false
        runCatching { if (reason.forcible) pipes.destroyForcibly() else pipes.destroy() }
        return true
    }

    override fun toString(): String = "${kind.label} gen=$generation"
}

/** 프로세스 기동·폐기를 남긴다 — 실기 확인의 "재시작 횟수·세대"(refactor backlog #14). */
internal interface EngineProcessEvents {
    fun started(handle: EngineProcessHandle)

    fun retired(handle: EngineProcessHandle, reason: EngineProcessRetireReason)

    /**
     * 기본값 — 한 줄씩 `System.err`에. Android에서는 로그캣에 `System.err` 태그로 나온다
     * (`adb logcat -s System.err | grep KataGoProcess`). `android.util.Log`은 JVM 단위 테스트(실제 런타임을
     * 도는 골든)에서 못 쓴다.
     */
    object StdErr : EngineProcessEvents {
        override fun started(handle: EngineProcessHandle) {
            System.err.println("KataGoProcess $handle started")
        }

        override fun retired(handle: EngineProcessHandle, reason: EngineProcessRetireReason) {
            System.err.println("KataGoProcess $handle retired reason=$reason age=${handle.ageMillis}ms")
        }
    }
}

/**
 * 한 종류(GTP·analysis)의 "지금 프로세스" 자리 — 기동과 폐기는 여기서만 일어난다(refactor backlog #14).
 *
 * ## 기동: [lifecycleMutex] 안에서 double-checked
 * 쓸 수 있는 핸들이 있으면 락 없이 돌려준다(착수마다 도는 경로). 없으면 락을 잡고 다시 본 뒤에만 띄운다 —
 * 동시에 들어온 첫 호출 여럿이 프로세스 하나를 같이 쓴다(예전에는 여럿이 떠 모델을 쥔 고아가 남았다).
 * 락 안에서 하는 일은 죽은 핸들 치우기, 파일 검증과 fork/exec, 세대 번호, 게시(CAS), 진단 한 줄뿐이다.
 * ⚠️ 파이프 입출력(첫 응답·모델 적재 기다리기)이나 왕복 락 기다리기를 이 락 안에 넣지 말 것 — 두 락이
 * 엮이는 순간 교착이 생긴다. 지금은 왕복 락을 쥔 쪽은 이 락을 잡지 않고, 이 락을 쥔 쪽은 왕복 락을 잡지 않는다.
 *
 * ## 폐기: 락 없이, 자기 핸들만(ABA 방지)
 * [retire]는 슬롯이 **아직 그 핸들을 들고 있을 때만** 슬롯을 비우고, 내리는 것은 **넘겨받은 그 핸들**뿐이다.
 * 슬롯은 옛 핸들이 내려간 뒤에만 새 세대로 넘어가므로, 늦게 깨어난 낡은 호출(예: forceReset 뒤에 시간 초과가
 * 난 1세대 호출)이 가진 핸들은 이미 내려가 있고, 그 호출의 폐기는 아무것도 하지 않는다 — 방금 뜬 2세대를
 * 죽이지 않는다. 예전 코드는 "지금 필드가 가리키는 것"을 내려서 바로 그 2세대를 죽였다.
 * 폐기가 락을 잡지 않는 것은 함정 71 때문이다 — `forceReset`(메인 스레드)은 어떤 락도 기다리면 안 된다.
 */
internal class EngineProcessSlot(
    private val kind: EngineProcessKind,
    private val events: EngineProcessEvents,
) {
    private val current = AtomicReference<EngineProcessHandle?>(null)
    private val lifecycleMutex = Mutex()

    /** [lifecycleMutex] 안에서만 읽고 쓴다. 기동이 실패하면 올리지 않는다. */
    private var lastGeneration = 0L

    fun currentOrNull(): EngineProcessHandle? = current.get()

    /** 쓸 수 있는 핸들을 돌려준다 — 없으면 [start]로 하나 띄운다. [start]는 블로킹이지만 짧아야 한다(fork/exec). */
    suspend fun acquire(start: () -> EngineProcessPipes): EngineProcessHandle {
        current.get()?.takeIf { it.isUsable }?.let { return it }
        return lifecycleMutex.withLock {
            current.get()?.let { existing ->
                if (existing.isUsable) return@withLock existing
                retire(existing, EngineProcessRetireReason.Died)
            }
            val handle = EngineProcessHandle(kind, generation = lastGeneration + 1, pipes = start())
            lastGeneration = handle.generation
            if (!current.compareAndSet(null, handle)) {
                // 이 락 밖에서는 슬롯을 비우기만 하므로 일어날 수 없다 — 일어나면 방금 띄운 것을 고아로 두지 않는다.
                handle.retire(EngineProcessRetireReason.Died)
                error("KataGo ${kind.label} slot was filled outside its lifecycle lock")
            }
            events.started(handle)
            handle
        }
    }

    /** [handle]을 내린다 — 슬롯은 아직 **그 핸들**을 들고 있을 때만 비운다. 몇 번을 불러도 안전하다. */
    fun retire(handle: EngineProcessHandle, reason: EngineProcessRetireReason) {
        current.compareAndSet(handle, null)
        if (handle.retire(reason)) events.retired(handle, reason)
    }

    /** `forceReset` 전용 — 지금 슬롯에 있는 것을 무조건 내린다. 락·대기·서스펜드가 없다. */
    fun retireCurrent(reason: EngineProcessRetireReason) {
        current.getAndSet(null)?.let { handle -> if (handle.retire(reason)) events.retired(handle, reason) }
    }
}
