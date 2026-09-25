package com.worksoc.goaicoach.engine.android

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

/**
 * `KataGoProcessEngineAdapter`를 **실제 KataGo 없이** JVM 테스트에서 띄우기 위한 가짜 실행 파일.
 *
 * 어댑터는 [KataGoProcessConfig.executablePath]를 `ProcessBuilder`로 그대로 띄운다. 그래서
 * 그 자리에 POSIX `sh` 스크립트 하나를 놓으면 어댑터 코드는 한 줄도 바꾸지 않고 끝단까지 돈다.
 *
 * - `gtp` 모드: 들어온 명령 한 줄을 [gtpCommands]가 읽는 파일에 **그대로** 적는다. `genmove`에는
 *   `= pass`를, 나머지 명령에는 빈 성공 응답(`=`)을 돌려준다 — 어느 국면에서든 늘 합법인
 *   수는 패스뿐이라서다(refactor backlog #91). `genmove`가 돌려줄 토큰은 [create]의 `genMoveReply`로
 *   바꿀 수 있다 — 어댑터가 KataGo의 착수 응답을 **어떻게 읽는지**(못 읽는 토큰이면 무엇을 던지는지)를
 *   재는 테스트용이다(refactor backlog #36).
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
    private val gtpLog = File(directory, GtpLogName)

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

    /** 지금까지 `analysis` 프로세스가 받은 쿼리 줄, 받은 순서대로 — 어댑터가 쓴 바이트 그대로. */
    fun rawQueryLines(): List<String> =
        if (queryLog.isFile) queryLog.readLines().filter { it.isNotBlank() } else emptyList()

    /** 지금까지 `gtp` 프로세스가 받은 명령 줄, 받은 순서대로 — 어댑터가 쓴 바이트 그대로. */
    fun gtpCommands(): List<String> =
        if (gtpLog.isFile) gtpLog.readLines().filter { it.isNotBlank() } else emptyList()

    override fun close() {
        directory.deleteRecursively()
    }

    companion object {
        private const val QueryLogName = "analysis-queries.jsonl"
        private const val GtpLogName = "gtp-commands.log"

        /** @param genMoveReply `genmove`에 `= ` 뒤로 돌려줄 토큰. 작은따옴표·줄바꿈은 셸 스크립트를 깨므로 받지 않는다. */
        fun create(genMoveReply: String = "pass"): FakeKataGoExecutable {
            require('\'' !in genMoveReply && '\n' !in genMoveReply) {
                "genMoveReply must not contain a single quote or a newline: $genMoveReply"
            }
            val directory = Files.createTempDirectory("fake-katago").toFile()
            File(directory, "katago").apply {
                writeText(script(genMoveReply))
                check(setExecutable(true)) { "Could not mark $path executable" }
            }
            listOf("model.bin.gz", "gtp.cfg", "analysis.cfg").forEach { name ->
                File(directory, name).writeText("")
            }
            return FakeKataGoExecutable(directory)
        }

        private const val D = "$"

        private fun script(genMoveReply: String): String = """
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
            |      printf '%s\n' "${D}line" >> "${D}here/$GtpLogName"
            |      case "${D}line" in
            |        genmove*) printf '= %s\n\n' '$genMoveReply' ;;
            |        *) printf '=\n\n' ;;
            |      esac
            |      [ "${D}line" = quit ] && exit 0
            |    done
            |    ;;
            |esac
            |""".trimMargin()
    }
}
