package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 이 기기의 KataGo 프로세스 위의 어댑터 — 예전 `KataGoProcessEngineAdapter(processConfig)` 생성자 그대로의 모양이다.
 * 어댑터가 공용 코드(commonMain)로 옮겨 가며(백로그 #293) 프로세스를 띄우는 [LocalKataGoProcessRuntime]은 안드로이드에 남았다.
 */
@Suppress("FunctionName")
internal fun KataGoProcessEngineAdapter(processConfig: KataGoProcessConfig): KataGoProcessEngineAdapter =
    KataGoProcessEngineAdapter(runtime = LocalKataGoProcessRuntime(processConfig))

/** 이 기기에서 `ProcessBuilder`로 띄운다 — 앱에서 KataGo 프로세스를 만드는 **유일한** 코드다. */
internal class LocalKataGoProcessRuntime(
    private val config: KataGoProcessConfig,
    private val analysisSearchThreads: Int = DefaultAnalysisSearchThreads,
) : EngineProcessRuntime {
    override fun startGtp(profile: EngineProfile, network: EngineNetwork, boardSize: BoardSize?): EngineProcessPipes {
        config.validateGtpFiles(network)
        return spawn(config.buildGtpCommand(profile, network, boardSize))
    }

    override val humanNetworkAvailable: Boolean
        get() = config.humanModelPath?.let { File(it).isFile } == true

    override fun analysisConfigPathOrNull(): String? = config.resolveAnalysisConfigPath()

    // KataGo 설정 파일 옆(앱 파일 폴더) — 엔진 파일을 준비하는 쪽이 이미 만들어 둔 곳이다.
    override fun writeStaticPosition(sgf: String): String {
        val file = File(File(config.configPath).absoluteFile.parentFile, "static-position.sgf")
        file.writeText(sgf)
        return file.absolutePath
    }

    override fun startAnalysis(analysisConfigPath: String): EngineProcessPipes =
        spawn(
            config.buildAnalysisCommand(
                analysisConfigPath = analysisConfigPath,
                analysisSearchThreads = analysisSearchThreads,
            ),
        )

    private fun spawn(command: KataGoProcessCommand): EngineProcessPipes =
        LocalProcessPipes(
            ProcessBuilder(command.commandLine)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start(),
        )
}

private class LocalProcessPipes(
    private val process: Process,
) : EngineProcessPipes {
    override val writer: EngineLineWriter = BufferedWriter(OutputStreamWriter(process.outputStream)).asEngineLineWriter()
    override val reader: EngineLineReader = BufferedReader(InputStreamReader(process.inputStream)).asEngineLineReader()
    override val isAlive: Boolean
        get() = process.isAlive

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }
}

internal fun KataGoProcessConfig.validateGtpFiles(network: EngineNetwork = EngineNetwork.Main) {
    require(File(executablePath).canExecute()) {
        "KataGo executable is not executable: $executablePath"
    }
    val model = modelPathFor(network)
    require(File(model).isFile) {
        "KataGo model not found: $model"
    }
    require(File(configPath).isFile) {
        "KataGo config not found: $configPath"
    }
}

internal fun KataGoProcessConfig.resolveAnalysisConfigPath(): String? =
    analysisConfigPath?.takeIf { File(it).isFile }
