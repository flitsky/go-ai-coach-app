package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.engine.android.bridge.KATAGO_BRIDGE_ANALYSIS
import com.worksoc.goaicoach.engine.android.bridge.KATAGO_BRIDGE_GTP
import com.worksoc.goaicoach.engine.android.bridge.katago_bridge_session_running
import com.worksoc.goaicoach.engine.android.bridge.katago_bridge_start
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.TimeSource
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSLock
import platform.posix.EINTR
import platform.posix.F_OK
import platform.posix.R_OK
import platform.posix.access
import platform.posix.close
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.read
import platform.posix.usleep
import platform.posix.write

/**
 * iOS 앱의 엔진 — KataGo를 **앱 안 스레드**로 부른다(백로그 #293). 안드로이드의 [EngineCoreApiFactory]의 짝이다.
 * 어댑터([KataGoProcessEngineAdapter] — 시간 초과 · 재시작 · 갈아 올리기 · 취소 배수)는 안드로이드와 **같은 코드**다.
 *
 * @param workDirectory 앱이 쓸 수 있는 폴더 — 정적 국면 SGF를 여기 쓴다. KataGo의 로그 · 홈 폴더는 [KataGoProcessConfig.startupOverrides]로 준다.
 */
object IosEngineCoreApiFactory {
    fun inProcess(config: KataGoProcessConfig, workDirectory: String): EngineCoreApi =
        KataGoProcessEngineAdapter(runtime = InProcessKataGoRuntime(config, workDirectory))
}

/**
 * [EngineProcessRuntime]의 iOS 구현 — 「프로세스 하나」를 흉내 낸다. 어댑터가 프로세스에 바라는 것은 줄 쓰기 · 줄 읽기 · 살아 있나 ·
 * 정상 종료 · 강제 종료뿐이라, 앱 안 엔진도 그 다섯으로 서면 어댑터는 그대로 돈다.
 *
 * 프로세스와 다른 것 둘(설계 — 백로그 #293 카드):
 * - **강제 종료 = 끊고 비우기.** 스레드는 죽일 수 없다 — 명령 파이프를 닫으면 KataGo는 지금 명령을 끝낸 뒤 EOF를 읽고 스스로 내려간다.
 *   응답 파이프는 뒤에서 EOF까지 읽어 버린다(KataGo가 쓰다 막혀 내려가지 못하는 일이 없게). 닫기 · 비우기는 배경에서 한다 —
 *   `forceReset`은 메인 스레드에서 불리고 아무것도 기다리면 안 된다(함정 71).
 * - **같은 종류는 한 번에 하나.** 새 엔진은 옛 엔진이 내려간 뒤에야 뜬다 — 기동이 [RestartWaitMillis]까지 기다리고, 넘으면 기동 실패다
 *   (지금의 「엔진 아직」 흐름을 탄다). GTP와 analysis는 **함께** 돈다(KataGo의 두 루프가 각자의 스트림을 쓴다 — `scripts/build-katago-ios.sh`).
 *
 * 남는 위험: KataGo가 한 명령에서 영영 끝나지 않으면 그 종류의 엔진은 앱을 다시 켜야 다시 뜬다(착수 · 분석은 방문 수 · 시간 상한이 있다).
 *
 * [KataGoProcessConfig.executablePath]는 쓰지 않는다 — 실행 파일이 없다(앱 안 라이브러리). argv[0] 자리에는 "katago"를 준다.
 */
@OptIn(ExperimentalForeignApi::class)
internal class InProcessKataGoRuntime(
    private val config: KataGoProcessConfig,
    private val workDirectory: String,
) : EngineProcessRuntime {
    override fun startGtp(profile: EngineProfile, network: EngineNetwork, boardSize: BoardSize?): EngineProcessPipes {
        val model = config.modelPathFor(network)
        require(isReadable(model)) { "KataGo model not found: $model" }
        require(isReadable(config.configPath)) { "KataGo config not found: ${config.configPath}" }
        return start(KATAGO_BRIDGE_GTP, config.buildGtpCommand(profile, network, boardSize))
    }

    override val humanNetworkAvailable: Boolean
        get() = config.humanModelPath?.let(::isReadable) == true

    override fun analysisConfigPathOrNull(): String? = config.analysisConfigPath?.takeIf(::isReadable)

    override fun startAnalysis(analysisConfigPath: String): EngineProcessPipes =
        start(
            KATAGO_BRIDGE_ANALYSIS,
            config.buildAnalysisCommand(analysisConfigPath = analysisConfigPath, analysisSearchThreads = DefaultAnalysisSearchThreads),
        )

    override fun writeStaticPosition(sgf: String): String {
        val path = "$workDirectory/static-position.sgf"
        val file = fopen(path, "w") ?: throw IllegalStateException("Cannot write the static position file: $path (errno=$errno)")
        try {
            check(fputs(sgf, file) >= 0) { "Cannot write the static position file: $path (errno=$errno)" }
        } finally {
            fclose(file)
        }
        return path
    }

    private fun start(kind: Int, command: KataGoProcessCommand): EngineProcessPipes {
        val label = if (kind == KATAGO_BRIDGE_GTP) "gtp" else "analysis"
        val args = listOf(ProgramName) + command.arguments
        val started = TimeSource.Monotonic.markNow()
        memScoped {
            val argv = allocArray<CPointerVar<ByteVar>>(args.size)
            args.forEachIndexed { index, arg -> argv[index] = arg.cstr.ptr }
            val toEngine = alloc<IntVar>()
            val fromEngine = alloc<IntVar>()
            while (true) {
                val session = katago_bridge_start(kind, args.size, argv, toEngine.ptr, fromEngine.ptr)
                when {
                    session > 0 -> return InProcessPipes(session, toEngine.value, fromEngine.value)
                    // 같은 종류의 옛 엔진이 아직 내려가는 중이다 — 지금 명령을 끝내고 EOF를 읽으면 물러난다.
                    session == -1 && started.elapsedNow().inWholeMilliseconds < RestartWaitMillis -> usleep(RestartPollMicros)
                    session == -1 -> throw IllegalStateException(
                        "KataGo $label is still finishing its previous engine after ${RestartWaitMillis}ms",
                    )
                    else -> throw IllegalStateException("KataGo $label could not start in-process (bridge=$session)")
                }
            }
        }
    }

    private fun isReadable(path: String): Boolean = access(path, R_OK) == 0 && access(path, F_OK) == 0

    private companion object {
        const val ProgramName = "katago"

        /**
         * 옛 엔진이 물러나기를 기다리는 상한 — 옛 엔진은 지금 명령(착수 · 분석, 시간 상한이 있다)을 끝내야 EOF를 읽는다.
         * 30초는 스레드가 정한 어림이다(백로그 #293 — 아이폰 실측 전).
         */
        const val RestartWaitMillis = 30_000L
        const val RestartPollMicros = 50_000u
    }
}

/**
 * 앱 안 엔진 하나의 파이프 — 프로세스의 stdin/stdout과 같은 쓰임이다.
 * ⚠️ fd를 닫는 것은 그 fd의 락 안에서만 — 다른 스레드가 쓰던 · 읽던 fd 번호가 닫힌 뒤 다른 파일에 다시 쓰이면 엉뚱한 곳에 쓰고 읽는다.
 */
@OptIn(ExperimentalForeignApi::class)
private class InProcessPipes(
    private val session: Int,
    toEngineFd: Int,
    fromEngineFd: Int,
) : EngineProcessPipes {
    private val detached = AtomicBoolean(false)
    private val fdWriter = FdLineWriter(toEngineFd)
    private val fdReader = FdLineReader(fromEngineFd)

    override val writer: EngineLineWriter = fdWriter
    override val reader: EngineLineReader = fdReader
    override val isAlive: Boolean
        get() = !detached.load() && katago_bridge_session_running(session) == 1

    /** `quit` 뒤 — KataGo는 이미 내려가는 중이다. 남은 것을 정리한다. */
    override fun destroy() = detach()

    /** 끊고 비우기 — 스레드는 죽일 수 없다(위 [InProcessKataGoRuntime] KDoc). */
    override fun destroyForcibly() = detach()

    private fun detach() {
        if (!detached.compareAndSet(false, true)) return
        // 배경에서 — 호출자(메인 스레드의 forceReset일 수 있다)는 아무것도 기다리지 않는다(함정 71).
        DetachScope.launch {
            fdWriter.close() // KataGo는 지금 명령을 끝낸 뒤 EOF를 읽고 내려간다
            fdReader.drainToEofAndClose() // 그동안 쓰는 것은 버린다 — 막혀 내려가지 못하는 일이 없게
        }
    }

    private companion object {
        val DetachScope = CoroutineScope(SupervisorJob() + enginePipeIoDispatcher)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FdLineWriter(private val fd: Int) : EngineLineWriter {
    private val lock = NSLock()
    private var closed = false

    override fun writeLine(line: String) {
        val bytes = (line + "\n").encodeToByteArray()
        lock.lock()
        try {
            if (closed) throw EnginePipeBrokenException("KataGo command pipe is closed")
            var offset = 0
            bytes.usePinned { pinned ->
                while (offset < bytes.size) {
                    val written = write(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
                    when {
                        written > 0 -> offset += written.toInt()
                        errno == EINTR -> continue
                        else -> throw EnginePipeBrokenException("KataGo command pipe write failed (errno=$errno)")
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    fun close() {
        lock.lock()
        try {
            if (!closed) {
                closed = true
                close(fd)
            }
        } finally {
            lock.unlock()
        }
    }
}

/** `BufferedReader.readLine()`과 같은 뜻 — 줄바꿈(`\n`, 앞의 `\r`까지) 빼고, 끝이면 `null`, 끝의 줄바꿈 없는 조각은 한 줄로. */
@OptIn(ExperimentalForeignApi::class)
private class FdLineReader(private val fd: Int) : EngineLineReader {
    private val lock = NSLock()
    private var pending = ByteArray(0)
    private var endOfStream = false
    private var closed = false
    private val chunk = ByteArray(4096)

    override fun readLine(): String? {
        lock.lock()
        try {
            while (true) {
                val newline = pending.indexOf('\n'.code.toByte())
                if (newline >= 0) {
                    val end = if (newline > 0 && pending[newline - 1] == '\r'.code.toByte()) newline - 1 else newline
                    val line = pending.decodeToString(0, end)
                    pending = pending.copyOfRange(newline + 1, pending.size)
                    return line
                }
                if (endOfStream || closed) {
                    if (pending.isEmpty()) return null
                    val line = pending.decodeToString()
                    pending = ByteArray(0)
                    return line
                }
                fill()
            }
        } finally {
            lock.unlock()
        }
    }

    /** 끊긴 엔진의 남은 출력을 EOF까지 버리고 fd를 닫는다 — 읽던 호출이 있으면 그 줄을 다 받은 뒤에 이어 한다(락). */
    fun drainToEofAndClose() {
        lock.lock()
        try {
            while (!endOfStream && !closed) {
                runCatching { fill() }.onFailure { endOfStream = true }
                pending = ByteArray(0)
            }
            if (!closed) {
                closed = true
                close(fd)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun fill() {
        while (true) {
            val count = chunk.usePinned { pinned -> read(fd, pinned.addressOf(0), chunk.size.toULong()) }
            when {
                count > 0 -> {
                    pending += chunk.copyOf(count.toInt())
                    return
                }
                count == 0L -> {
                    endOfStream = true
                    return
                }
                errno == EINTR -> continue
                else -> throw EnginePipeBrokenException("KataGo response pipe read failed (errno=$errno)")
            }
        }
    }
}
