package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import org.json.JSONObject

/**
 * 메모리 안의 가짜 KataGo 프로세스들 — [EngineProcessRuntime]을 대신한다(refactor backlog #14).
 *
 * [FakeKataGoExecutable](진짜 `sh` 프로세스)로는 결정적으로 못 만드는 상황을 만든다:
 * - **막힌 읽기는 인터럽트로 풀리지 않는다** — 진짜 파이프처럼. 프로세스가 내려가야(EOF) 풀린다. 기기(Linux)를
 *   따른다: [ignoreSigterm]인 프로세스에 `destroy()`를 보내면 **아무것도 풀리지 않는다**(Linux에서는 스트림을 닫아도
 *   막힌 읽기가 안 풀린다 — macOS의 `sh` 가짜로는 이게 재현되지 않는다, [FakeKataGoExecutable] KDoc).
 *   `reader`는 진짜 [BufferedReader]라 막힌 `readLine()`이 그 락을 실제로 쥔다 — 누가 reader를 닫으려 들면
 *   그쪽이 같이 멈춘다(`forceReset`이 그러면 안 된다는 것을 테스트가 잴 수 있다).
 * - 줄마다 답을 멈추거나([Reply.Never]) 늦추거나([Reply.After]) 붙잡아 두고([Reply.WhenReleased]),
 *   답 없이 죽을 수 있다([Reply.Crash]).
 * - SIGTERM([ignoreSigterm])·SIGKILL([ignoreSigkill])을 무시하게 할 수 있다 — 뒤의 것은 "죽였는데도 읽기가
 *   안 풀린다"를 흉내 낸다.
 * - 기동을 [startGateMillis] 동안 열어 두어, 락이 없으면 두 기동이 실제로 겹치게 한다.
 *
 * 프로세스는 종류마다 뜬 순서대로 [FakeEngineProcess.ordinal]을 받는다(1부터) — 어댑터의 세대와 같은 순서다.
 */
internal class FakeEngineProcessRuntime : EngineProcessRuntime {
    enum class Kind { Gtp, Analysis }

    /** 가짜 프로세스가 한 줄을 받았을 때 stdout에 할 일. */
    sealed interface Reply {
        /** 곧바로 [text]를 쓴다(줄바꿈 포함). */
        data class Now(val text: String) : Reply

        /** [delayMillis] 뒤에 쓴다 — 마감을 넘겨 도착하는 늦은 답. */
        data class After(val delayMillis: Long, val text: String) : Reply

        /** [gate]가 열린 뒤에 쓴다. 그때 이미 내려진 프로세스면 아무것도 쓰지 못한다(진짜 파이프처럼). */
        data class WhenReleased(val gate: CountDownLatch, val text: String) : Reply

        /** 쓰지 않는다 — 진짜로 멈춘 KataGo. (응답자가 [FakeEngineProcess.emit]으로 이미 답했을 때도 쓴다.) */
        data object Never : Reply

        /** 답 없이 죽는다(EOF) — 크래시나 저메모리 킬러. */
        data object Crash : Reply
    }

    @Volatile var analysisConfigPath: String? = "fake-analysis.cfg"

    /** 0보다 크면, 기동 하나가 다른 기동이 겹쳐 들어오기를 최대 이만큼(ms) 기다린다. */
    @Volatile var startGateMillis: Long = 0L

    /** 이제부터 뜨는 프로세스가 SIGTERM([EngineProcessPipes.destroy])을 무시한다. */
    @Volatile var ignoreSigterm: Boolean = false

    /** 이제부터 뜨는 프로세스는 SIGKILL을 받아도 stdout이 닫히지 않는다 — 죽였는데 읽기가 안 풀리는 경우. */
    @Volatile var ignoreSigkill: Boolean = false

    /** 줄마다 부른다. `null`이면 기본 답(GTP `=`·`genmove`는 `= pass`, analysis는 후보 없는 최소 응답). */
    @Volatile var responder: (FakeEngineProcess, String) -> Reply? = { _, _ -> null }

    private val started = CopyOnWriteArrayList<FakeEngineProcess>()
    private val gateLock = ReentrantLock()
    private val gateChanged = gateLock.newCondition()
    private val startsInFlight = mutableMapOf<Kind, Int>()
    private val maxStartsInFlight = mutableMapOf<Kind, Int>()

    /** 사람 모델 파일이 있는 기기인가(백로그 #215). 기본은 없다 — 지금까지의 테스트는 주 모델만 안다. */
    @Volatile override var humanNetworkAvailable: Boolean = false

    /** GTP 프로세스가 뜬 순서대로, 그때 올린 신경망. */
    val gtpNetworks = CopyOnWriteArrayList<EngineNetwork>()

    /** GTP 프로세스가 뜬 순서대로, 그때 받은 판 크기. 모른 채 떴으면 [UnknownBoardSize](KataGo 기본으로 뜬다). */
    val gtpBoardSizes = CopyOnWriteArrayList<Int>()

    override fun startGtp(profile: EngineProfile, network: EngineNetwork, boardSize: BoardSize?): EngineProcessPipes {
        require(network == EngineNetwork.Main || humanNetworkAvailable) { "KataGo model not found: human" }
        gtpNetworks += network
        gtpBoardSizes += boardSize?.value ?: UnknownBoardSize
        return start(Kind.Gtp)
    }

    override fun analysisConfigPathOrNull(): String? = analysisConfigPath

    override fun startAnalysis(analysisConfigPath: String): EngineProcessPipes = start(Kind.Analysis)

    fun processes(kind: Kind): List<FakeEngineProcess> = started.filter { it.kind == kind }

    fun gtp(ordinal: Int): FakeEngineProcess = processOf(Kind.Gtp, ordinal)

    fun analysis(ordinal: Int): FakeEngineProcess = processOf(Kind.Analysis, ordinal)

    /** 한 순간에 겹쳐 돌던 기동 수의 최댓값. */
    fun maxConcurrentStarts(kind: Kind): Int = gateLock.withLock { maxStartsInFlight[kind] ?: 0 }

    fun awaitStarts(kind: Kind, count: Int, timeoutMillis: Long = 2_000L): Boolean =
        pollUntil(timeoutMillis) { processes(kind).size >= count }

    /** [kind]의 기동이 지금 진행 중(아직 돌려주지 않음)이 될 때까지 기다린다. */
    fun awaitStartInProgress(kind: Kind, timeoutMillis: Long = 2_000L): Boolean =
        pollUntil(timeoutMillis) { gateLock.withLock { (startsInFlight[kind] ?: 0) > 0 } }

    /** 테스트 정리 — 남은 가짜 프로세스를 전부 끝내 막힌 읽기를 모두 푼다. 신호로 적지 않는다. */
    fun releaseAll() {
        started.forEach { it.exit() }
    }

    private fun processOf(kind: Kind, ordinal: Int): FakeEngineProcess =
        processes(kind).getOrNull(ordinal - 1)
            ?: error("No $kind process #$ordinal was started (started: ${processes(kind).size})")

    private fun start(kind: Kind): FakeEngineProcess {
        gateLock.withLock {
            val inFlight = (startsInFlight[kind] ?: 0) + 1
            startsInFlight[kind] = inFlight
            maxStartsInFlight[kind] = maxOf(maxStartsInFlight[kind] ?: 0, inFlight)
            gateChanged.signalAll()
            var remainingNanos = TimeUnit.MILLISECONDS.toNanos(startGateMillis)
            while ((startsInFlight[kind] ?: 0) < 2 && remainingNanos > 0) {
                remainingNanos = gateChanged.awaitNanos(remainingNanos)
            }
        }
        try {
            return synchronized(started) {
                FakeEngineProcess(
                    kind = kind,
                    ordinal = processes(kind).size + 1,
                    ignoresSigterm = ignoreSigterm,
                    ignoresSigkill = ignoreSigkill,
                    respond = { process, line -> responder(process, line) },
                ).also { started += it }
            }
        } finally {
            gateLock.withLock { startsInFlight[kind] = (startsInFlight[kind] ?: 1) - 1 }
        }
    }
}

/** [FakeEngineProcessRuntime.gtpBoardSizes]에서 「판 크기를 모른 채 떴다」는 표시. */
internal const val UnknownBoardSize = 0

internal class FakeEngineProcess(
    val kind: FakeEngineProcessRuntime.Kind,
    val ordinal: Int,
    private val ignoresSigterm: Boolean,
    private val ignoresSigkill: Boolean,
    private val respond: (FakeEngineProcess, String) -> FakeEngineProcessRuntime.Reply?,
) : EngineProcessPipes {
    private val stdout = BlockingPipe()
    private val lines = CopyOnWriteArrayList<String>()
    private val signalLog = CopyOnWriteArrayList<String>()

    @Volatile private var alive = true

    override val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(LineSink(::onLine), Charsets.UTF_8))
    override val reader: BufferedReader = BufferedReader(InputStreamReader(stdout.input, Charsets.UTF_8))
    override val isAlive: Boolean
        get() = alive

    /** 받은 줄, 받은 순서대로 — 어댑터가 쓴 그대로. */
    val received: List<String>
        get() = lines.toList()

    /** 받은 신호, 받은 순서대로 — `TERM`([destroy]) / `KILL`([destroyForcibly]). */
    val signals: List<String>
        get() = signalLog.toList()

    override fun destroy() {
        signalLog += "TERM"
        if (!ignoresSigterm) exit()
    }

    override fun destroyForcibly() {
        signalLog += "KILL"
        if (ignoresSigkill) alive = false else exit()
    }

    /** stdout에 [text]를 곧바로 쓴다 — 응답자가 답을 먼저 내보낸 뒤 다른 일을 하게 할 때. */
    fun emit(text: String) {
        stdout.write(text)
    }

    /** 스스로 끝난다(`quit`·크래시·테스트 정리). 이미 쓴 답은 EOF 전에 다 읽힌다 — 진짜 파이프처럼. */
    fun exit() {
        alive = false
        stdout.close()
    }

    fun awaitReceived(prefix: String, timeoutMillis: Long = 2_000L): Boolean =
        pollUntil(timeoutMillis) { lines.any { it.startsWith(prefix) } }

    override fun toString(): String = "fake $kind #$ordinal"

    private fun onLine(line: String) {
        if (!alive) throw IOException("Broken pipe: $this is not running")
        lines += line
        when (val reply = respond(this, line) ?: defaultReply(line)) {
            is FakeEngineProcessRuntime.Reply.Now -> stdout.write(reply.text)
            is FakeEngineProcessRuntime.Reply.After -> deliverLater { Thread.sleep(reply.delayMillis); reply.text }
            is FakeEngineProcessRuntime.Reply.WhenReleased -> deliverLater { reply.gate.await(); reply.text }
            FakeEngineProcessRuntime.Reply.Never -> Unit
            FakeEngineProcessRuntime.Reply.Crash -> exit()
        }
        if (kind == FakeEngineProcessRuntime.Kind.Gtp && line == "quit") exit()
    }

    private fun deliverLater(text: () -> String) {
        thread(isDaemon = true, name = "$this reply") { stdout.write(text()) }
    }

    private fun defaultReply(line: String): FakeEngineProcessRuntime.Reply =
        FakeEngineProcessRuntime.Reply.Now(
            when (kind) {
                FakeEngineProcessRuntime.Kind.Gtp -> if (line.startsWith("genmove")) "= pass\n\n" else "=\n\n"
                FakeEngineProcessRuntime.Kind.Analysis ->
                    """{"id":"${JSONObject(line).getString("id")}","turnNumber":0,"moveInfos":[],"rootInfo":{"visits":1}}""" + "\n"
            },
        )
}

/** 어댑터가 쓴 바이트를 줄 단위로 넘긴다. */
private class LineSink(
    private val onLine: (String) -> Unit,
) : OutputStream() {
    private val pending = ByteArrayOutputStream()

    override fun write(b: Int) {
        if (b == '\n'.code) {
            val line = pending.toString(Charsets.UTF_8.name()).removeSuffix("\r")
            pending.reset()
            onLine(line)
        } else {
            pending.write(b)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        for (index in off until off + len) write(b[index].toInt() and 0xff)
    }
}

/**
 * 가짜 stdout. 읽기는 데이터가 오거나 닫힐 때까지 막히고, **인터럽트로는 풀리지 않는다**
 * (`awaitUninterruptibly`) — 파이프 읽기가 `Thread.interrupt()`를 무시하는 것과 같다. 닫힌 뒤에도
 * 이미 쓴 바이트는 다 읽히고 그다음에 EOF다.
 */
private class BlockingPipe {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val bytes = ArrayDeque<Byte>()
    private var closed = false

    fun write(text: String) {
        lock.withLock {
            if (closed) return
            text.toByteArray(Charsets.UTF_8).forEach(bytes::addLast)
            changed.signalAll()
        }
    }

    fun close() {
        lock.withLock {
            closed = true
            changed.signalAll()
        }
    }

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            lock.withLock {
                while (bytes.isEmpty() && !closed) changed.awaitUninterruptibly()
                if (bytes.isEmpty()) return -1
                var count = 0
                while (count < len && bytes.isNotEmpty()) {
                    b[off + count] = bytes.removeFirst()
                    count++
                }
                return count
            }
        }

        override fun available(): Int = lock.withLock { bytes.size }
    }
}

/** [condition]이 참이 될 때까지 [timeoutMillis]까지 기다린다(테스트 픽스처용 폴링). */
internal fun pollUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (true) {
        if (condition()) return true
        if (System.nanoTime() >= deadline) return false
        Thread.sleep(5)
    }
}
