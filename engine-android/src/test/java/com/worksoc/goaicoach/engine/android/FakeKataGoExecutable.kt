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
 * - 뜰 때마다 모드를 [starts]가 읽는 파일에 적는다(프로세스를 몇 번 띄웠는지 — refactor backlog #14).
 * - [create]의 `stopSelfOn`을 주면 그 접두어로 시작하는 GTP 명령을 적은 뒤 **스스로 SIGSTOP한다** —
 *   #74 실기 재현법(`kill -STOP`)과 같은, 진짜로 멈춘 KataGo다. 답하지 않고 stdin도 더 읽지 않는다.
 *   셸 내장(`kill`)이라 자식 프로세스를 만들지 않는다 — 자식이 stdout을 물려받아 쥐고 있으면 부모를 죽여도
 *   파이프가 닫히지 않아 재려는 것이 가려진다(`sleep`을 쓰지 않는 이유). 이 모드로 만든 가짜는 [close]가 남은
 *   프로세스를 SIGKILL로 치운다.
 *   ⚠️ 플랫폼 차이(2026-09-28 macOS 실측): Linux(Android)에서는 멈춘 프로세스에 보낸 SIGTERM이 보류되고, 우리
 *   쪽 스트림을 닫아도 막힌 파이프 읽기가 풀리지 않는다. macOS는 둘 다 다르다 — SIGTERM이 멈춘 프로세스를
 *   내리고, `Process.destroy()`가 스트림을 닫으면 막힌 `readLine()`이 `null`로 풀린다. 그래서 이 가짜로는
 *   "SIGTERM으로는 안 풀린다"를 macOS에서 재현할 수 없다 — 그건 [FakeEngineProcessRuntime]이 맡는다.
 *
 * ⚠️ 이 가짜가 증명하는 것은 **어댑터가 KataGo에 무엇을 보냈는가**뿐이다. KataGo가 그 쿼리를
 * 어떻게 읽는지(점수, 접바둑 보정)는 여기서 알 수 없다 — 그건 실제 KataGo로 잰 값이다.
 */
internal class FakeKataGoExecutable private constructor(
    private val directory: File,
    private val stopsItself: Boolean,
) : Closeable {
    private val queryLog = File(directory, QueryLogName)
    private val gtpLog = File(directory, GtpLogName)
    private val startLog = File(directory, StartLogName)

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

    /** 지금까지 뜬 프로세스의 모드(`gtp`·`analysis`), 뜬 순서대로. */
    fun starts(): List<String> = startLines().map { it.substringBefore(' ') }

    override fun close() {
        if (stopsItself) killLeftoverProcesses()
        directory.deleteRecursively()
    }

    private fun startLines(): List<String> =
        if (startLog.isFile) startLog.readLines().filter { it.isNotBlank() } else emptyList()

    /**
     * 스스로 멈춘 가짜가 남지 않게 한다 — 멈춘 프로세스는 SIGTERM을 보류하므로, 오늘의 `forceReset`(SIGTERM)으로는
     * 안 내려간다. pid가 재사용됐을 수 있으니 **명령줄에 이 가짜의 경로가 있는 것만** 죽인다.
     */
    private fun killLeftoverProcesses() {
        startLines().map { it.substringAfter(' ').trim() }.forEach { pid ->
            val command = runCatching {
                ProcessBuilder("ps", "-p", pid, "-o", "command=").start().inputStream.bufferedReader().readText()
            }.getOrDefault("")
            if (directory.path in command) {
                runCatching { ProcessBuilder("kill", "-KILL", pid).start().waitFor() }
            }
        }
    }

    companion object {
        private const val QueryLogName = "analysis-queries.jsonl"
        private const val GtpLogName = "gtp-commands.log"
        private const val StartLogName = "starts.log"
        private val CommandPrefix = Regex("[A-Za-z0-9_-]+")

        /**
         * @param genMoveReply `genmove`에 `= ` 뒤로 돌려줄 토큰. 작은따옴표·줄바꿈은 셸 스크립트를 깨므로 받지 않는다.
         * @param stopSelfOn 이 접두어로 시작하는 GTP 명령을 받으면 스스로 SIGSTOP한다(클래스 KDoc). 기본은 끔 —
         *   끈 스크립트의 GTP·쿼리 기록은 예전과 한 바이트도 다르지 않다.
         */
        fun create(
            genMoveReply: String = "pass",
            stopSelfOn: String? = null,
        ): FakeKataGoExecutable {
            require('\'' !in genMoveReply && '\n' !in genMoveReply) {
                "genMoveReply must not contain a single quote or a newline: $genMoveReply"
            }
            require(stopSelfOn == null || CommandPrefix.matches(stopSelfOn)) {
                "stopSelfOn must be a plain GTP command prefix: $stopSelfOn"
            }
            val directory = Files.createTempDirectory("fake-katago").toFile()
            File(directory, "katago").apply {
                writeText(script(genMoveReply, stopSelfOn))
                check(setExecutable(true)) { "Could not mark $path executable" }
            }
            listOf("model.bin.gz", "gtp.cfg", "analysis.cfg").forEach { name ->
                File(directory, name).writeText("")
            }
            return FakeKataGoExecutable(directory, stopsItself = stopSelfOn != null)
        }

        private const val D = "$"

        private fun script(
            genMoveReply: String,
            stopSelfOn: String?,
        ): String = """
            |#!/bin/sh
            |here=${D}(cd "${D}(dirname "${D}0")" && pwd)
            |printf '%s %s\n' "${D}1" "${D}${D}" >> "${D}here/$StartLogName"
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
            |      case "${D}line" in${stopSelfOn?.let { "\n            |        $it*) kill -STOP ${D}${D} ;;" }.orEmpty()}
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
