package com.worksoc.goaicoach.engine.android

import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.file.Files

/**
 * `KataGoProcessEngineAdapter`를 **실제 KataGo 없이** JVM 테스트에서 띄우기 위한 가짜 실행 파일.
 *
 * 어댑터는 [KataGoProcessConfig.executablePath]를 `ProcessBuilder`로 그대로 띄운다. 그래서
 * 그 자리에 POSIX `sh` 스크립트 하나를 놓으면 어댑터 코드는 한 줄도 바꾸지 않고 끝단까지 돈다.
 *
 * - `gtp` 모드: 모든 명령에 빈 성공 응답(`=`)을 돌려준다. 어떤 명령이 갔는지는 보지 않는다.
 * - `analysis` 모드: 들어온 JSON 쿼리 한 줄을 [queries]가 읽는 파일에 **그대로** 적고,
 *   같은 `id`로 후보 없는 최소 응답을 돌려준다. `rootInfo.scoreLead`가 없으므로 어댑터는
 *   policy-refine 쿼리를 더 보내지 않는다 — `analyze()` 한 번에 쿼리 한 줄이다.
 *
 * ⚠️ 이 가짜가 증명하는 것은 **어댑터가 KataGo에 무엇을 보냈는가**뿐이다. KataGo가 그 쿼리를
 * 어떻게 읽는지(점수, 접바둑 보정)는 여기서 알 수 없다 — 그건 실제 KataGo로 잰 값이다.
 */
internal class FakeKataGoExecutable private constructor(
    private val directory: File,
) : Closeable {
    private val queryLog = File(directory, QueryLogName)

    val processConfig: KataGoProcessConfig = KataGoProcessConfig(
        executablePath = File(directory, "katago").path,
        modelPath = File(directory, "model.bin.gz").path,
        configPath = File(directory, "gtp.cfg").path,
        analysisConfigPath = File(directory, "analysis.cfg").path,
    )

    /** 지금까지 `analysis` 프로세스가 받은 쿼리, 받은 순서대로. */
    fun queries(): List<JSONObject> =
        if (queryLog.isFile) {
            queryLog.readLines().filter { it.isNotBlank() }.map(::JSONObject)
        } else {
            emptyList()
        }

    fun lastQuery(): JSONObject =
        queries().lastOrNull() ?: error("The fake KataGo analysis process never received a query")

    override fun close() {
        directory.deleteRecursively()
    }

    companion object {
        private const val QueryLogName = "analysis-queries.jsonl"

        fun create(): FakeKataGoExecutable {
            val directory = Files.createTempDirectory("fake-katago").toFile()
            File(directory, "katago").apply {
                writeText(Script)
                check(setExecutable(true)) { "Could not mark $path executable" }
            }
            listOf("model.bin.gz", "gtp.cfg", "analysis.cfg").forEach { name ->
                File(directory, name).writeText("")
            }
            return FakeKataGoExecutable(directory)
        }

        private const val D = "$"

        private val Script = """
            |#!/bin/sh
            |here=${D}(cd "${D}(dirname "${D}0")" && pwd)
            |case "${D}1" in
            |  analysis)
            |    while IFS= read -r line; do
            |      printf '%s\n' "${D}line" >> "${D}here/$QueryLogName"
            |      rest=${D}{line#*'"id":"'}
            |      id=${D}{rest%%'"'*}
            |      printf '{"id":"%s","turnNumber":0,"moveInfos":[],"rootInfo":{"visits":1}}\n' "${D}id"
            |    done
            |    ;;
            |  *)
            |    while IFS= read -r line; do
            |      printf '=\n\n'
            |      [ "${D}line" = quit ] && exit 0
            |    done
            |    ;;
            |esac
            |""".trimMargin()
    }
}
