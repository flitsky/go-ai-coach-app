package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.engine.android.IosEngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.describe
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory

/**
 * 시뮬레이터에서 **Kotlin 엔진 코드**(안드로이드와 같은 어댑터)가 앱 안 KataGo로 도는지 본다(백로그 #293) — `make ios-engine-probe`.
 *
 * 환경 변수(`KATAGO_PROBE_MODEL` · `KATAGO_PROBE_CONFIG`, 있으면 `KATAGO_PROBE_ANALYSIS_CONFIG` · `KATAGO_PROBE_HUMAN_MODEL`)가 있을 때만 돈다 —
 * 시뮬레이터 앱은 맥의 파일 경로를 읽을 수 있어 모델을 앱에 싣지 않는다(**실기기에서는 안 된다**). 진짜 앱의 엔진 준비(모델 싣기 ·
 * 첫 실행의 설정 파일)는 #293의 다음 단계다 — 그때 이 확인은 앱의 엔진 준비 위에서 다시 하고 이 파일은 지운다.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal object EngineProbe {
    data class Request(
        val model: String,
        val config: String,
        val analysisConfig: String?,
        val humanModel: String?,
        val exitWhenDone: Boolean,
    )

    fun requested(): Request? {
        val env = NSProcessInfo.processInfo.environment
        fun value(key: String): String? = (env[key] as? String)?.takeIf { it.isNotBlank() }
        return Request(
            model = value("KATAGO_PROBE_MODEL") ?: return null,
            config = value("KATAGO_PROBE_CONFIG") ?: return null,
            analysisConfig = value("KATAGO_PROBE_ANALYSIS_CONFIG"),
            humanModel = value("KATAGO_PROBE_HUMAN_MODEL"),
            exitWhenDone = value("KATAGO_PROBE_EXIT") == "1",
        )
    }

    suspend fun run(request: Request, log: suspend (String) -> Unit) {
        val workDirectory = NSTemporaryDirectory() + "katago"
        for (dir in listOf(workDirectory, "$workDirectory/logs", "$workDirectory/home")) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        // 안드로이드의 기동 인자(`EngineBootstrap.kt`의 startupOverrides)와 같게 — 로그 · 홈은 앱의 임시 폴더로.
        val config = KataGoProcessConfig(
            executablePath = "katago",
            modelPath = request.model,
            configPath = request.config,
            analysisConfigPath = request.analysisConfig,
            humanModelPath = request.humanModel,
            startupOverrides = mapOf(
                "numSearchThreads" to "1",
                "logDir" to "$workDirectory/logs",
                "homeDataDir" to "$workDirectory/home",
                "logToStderr" to "false",
                "logAllGTPCommunication" to "false",
                "logSearchInfo" to "false",
                "allowResignation" to "false",
                "startupPrintMessageToStderr" to "false",
            ),
        )
        val engine = IosEngineCoreApiFactory.inProcess(config, workDirectory)
        val board = BoardSize.Nine
        val started = TimeSource.Monotonic.markNow()
        suspend fun step(name: String, block: suspend () -> String) {
            val stepStarted = TimeSource.Monotonic.markNow()
            val result = runCatching { block() }.getOrElse { error -> "FAILED ${error::class.simpleName}: ${error.message}" }
            val elapsed = stepStarted.elapsedNow().inWholeMilliseconds
            log("[+${started.elapsedNow().inWholeMilliseconds}ms] $name (${elapsed}ms) → $result")
        }

        step("initialize") {
            engine.initialize(EngineProfile(mode = EngineMode.LocalProcess, analysisLimit = AnalysisLimit(visits = 32))).message
        }
        step("newGame 9x9") { engine.newGame(board, Ruleset.Japanese, handicapCount = 0, komi = 6.5).message }
        step("genMove B") { engine.genMove(StoneColor.Black).move.describe(board) }
        step("genMove W") { engine.genMove(StoneColor.White).move.describe(board) }
        step("analyze (JSON engine alongside GTP)") {
            val result = engine.analyze(AnalysisLimit(visits = 32, candidateCount = 3, includePolicy = true, refinePolicyMoves = 0))
            val top = result.candidates.take(3).joinToString { candidate -> candidate.move.describe(board) }
            "top=[$top] rootVisits=${result.rootVisits} fallback=${result.fallback?.reason ?: "none"}"
        }
        step("estimateScore") { engine.estimateScore(AnalysisLimit(visits = 1)).let { "whiteLead=${it.whiteScoreLead}" } }
        if (engine.supportsHumanNetwork) {
            step("useNetwork Human (main GTP retires, human GTP starts)") { "swapped=${engine.useNetwork(EngineNetwork.Human)}" }
            step("newGame 9x9 on the human model") { engine.newGame(board, Ruleset.Japanese, handicapCount = 0, komi = 6.5).message }
            step("humanPolicy rank_15k") {
                val policy = engine.humanPolicy("rank_15k")
                val best = policy.moves.maxByOrNull { it.value }
                "best=${best?.key?.label(board)} p=${best?.value}"
            }
            step("useNetwork Main") { "swapped=${engine.useNetwork(EngineNetwork.Main)}" }
        }
        step("forceReset (detach + drain, then restart)") {
            engine.forceReset()
            "reset"
        }
        step("newGame after reset") { engine.newGame(board, Ruleset.Japanese, handicapCount = 0, komi = 6.5).message }
        step("genMove B after reset") { engine.genMove(StoneColor.Black).move.describe(board) }
        step("stop") { engine.stop().message }
        log("done")
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
internal fun EngineProbeScreen(request: EngineProbe.Request) {
    val lines = remember { mutableStateListOf("KataGo in-process probe (backlog #293)") }
    LaunchedEffect(request) {
        withContext(Dispatchers.Default) {
            EngineProbe.run(request) { line ->
                println("[EngineProbe] $line")
                withContext(Dispatchers.Main) { lines += line }
            }
        }
        if (request.exitWhenDone) platform.posix.exit(0)
    }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 48.dp)) {
                items(lines) { line -> Text(text = line, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
}
