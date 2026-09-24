package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KataGoProcessRuntimeTest {
    @Test
    fun buildsGtpCommandWithProfileSearchLimitAndStartupOverrides() {
        val config = KataGoProcessConfig(
            executablePath = "/bin/katago",
            modelPath = "/model.bin.gz",
            configPath = "/gtp_learning.cfg",
            startupOverrides = mapOf(
                "homeDataDir" to "/tmp/katago-home",
                "allowResignation" to "false",
            ),
        )
        val profile = EngineProfile(
            analysisLimit = AnalysisLimit(
                visits = 32,
                timeMillis = 2_000L,
            ),
        )

        val command = config.buildGtpCommand(profile).commandLine

        assertEquals("/bin/katago", command.first())
        assertEquals("gtp", command[1])
        assertEquals("/model.bin.gz", command[3])
        assertEquals("/gtp_learning.cfg", command[5])
        val overrides = command.last()
        assertTrue(overrides.contains("homeDataDir=/tmp/katago-home"))
        assertTrue(overrides.contains("allowResignation=false"))
        assertTrue(overrides.contains("maxVisits=32"))
        assertTrue(overrides.contains("logToStderr=false"))
    }

    @Test
    fun buildsAnalysisCommandWithOnlyRuntimeSafeStartupOverrides() {
        val config = KataGoProcessConfig(
            executablePath = "/bin/katago",
            modelPath = "/model.bin.gz",
            configPath = "/gtp_learning.cfg",
            startupOverrides = mapOf(
                "homeDataDir" to "/tmp/katago-home",
                "logDir" to "/tmp/katago-log",
                "maxVisits" to "999",
                "logAllRequests" to "true",
                "allowResignation" to "false",
            ),
        )

        val command = config
            .buildAnalysisCommand(
                analysisConfigPath = "/analysis_learning.cfg",
                analysisSearchThreads = 4,
            )
            .commandLine

        assertEquals("/bin/katago", command.first())
        assertEquals("analysis", command[1])
        assertEquals("/model.bin.gz", command[3])
        assertEquals("/analysis_learning.cfg", command[5])
        val overrides = command.last()
        assertTrue(overrides.contains("homeDataDir=/tmp/katago-home"))
        assertTrue(overrides.contains("logDir=/tmp/katago-log"))
        assertTrue(overrides.contains("numAnalysisThreads=1"))
        assertTrue(overrides.contains("numSearchThreads=4"))
        assertTrue(overrides.contains("logAllRequests=false"))
        assertTrue(overrides.contains("logAllResponses=false"))
        assertTrue(overrides.contains("logSearchInfo=false"))
        assertFalse(overrides.contains("maxVisits=999"))
        assertFalse(overrides.contains("allowResignation=false"))
        assertFalse(overrides.contains("logAllRequests=true"))
    }

    /**
     * 백로그 #92 — 두 엔진(gtp·analysis) 모두 기동 인자로 `assumeMultipleStartingBlackMovesAreHandicap=false`를
     * 받는다. KataGo 기본값(true)은 백이 아직 돌을 놓지 않은 동안 이어진 흑 수를 접바둑 돌로 센다.
     * 백의 `pass`도 그 연속을 끊지 않는다.
     *
     * ⚠️ cfg가 아니라 여기에 두는 이유: 앱은 `filesDir/katago/`에 cfg가 이미 있으면 다시 풀지
     * 않는다(`EngineBootstrap.seedAssetIfMissing`). cfg를 고쳐도 이미 설치한 기기에는 닿지 않는다.
     */
    @Test
    fun gtpCommandTurnsOffHandicapGuessFromLeadingBlackMoves() {
        val command = plainConfig().buildGtpCommand(EngineProfile())

        assertEquals(
            listOf(HandicapGuessKey to "false"),
            command.overrideEntries().filter { (key, _) -> key == HandicapGuessKey },
        )
    }

    /** analysis 쪽은 `AnalysisStartupOverrideAllowList`가 호출자 값을 거르므로 따로 확인한다. */
    @Test
    fun analysisCommandTurnsOffHandicapGuessFromLeadingBlackMoves() {
        val command = plainConfig().buildAnalysisCommand(
            analysisConfigPath = "/analysis_learning.cfg",
            analysisSearchThreads = 1,
        )

        assertEquals(
            listOf(HandicapGuessKey to "false"),
            command.overrideEntries().filter { (key, _) -> key == HandicapGuessKey },
        )
    }

    @Test
    fun startupOverridesCannotTurnHandicapGuessBackOn() {
        val config = plainConfig().copy(
            startupOverrides = mapOf(HandicapGuessKey to "true"),
        )

        val gtp = config.buildGtpCommand(EngineProfile())
        val analysis = config.buildAnalysisCommand(
            analysisConfigPath = "/analysis_learning.cfg",
            analysisSearchThreads = 1,
        )

        assertEquals(
            listOf(HandicapGuessKey to "false"),
            gtp.overrideEntries().filter { (key, _) -> key == HandicapGuessKey },
        )
        assertEquals(
            listOf(HandicapGuessKey to "false"),
            analysis.overrideEntries().filter { (key, _) -> key == HandicapGuessKey },
        )
    }

    private fun plainConfig() = KataGoProcessConfig(
        executablePath = "/bin/katago",
        modelPath = "/model.bin.gz",
        configPath = "/gtp_learning.cfg",
    )

    /** `-override-config` 뒤의 값을 순서와 중복을 살려 `key to value`로 푼다. */
    private fun KataGoProcessCommand.overrideEntries(): List<Pair<String, String>> {
        val flagIndex = arguments.indexOf("-override-config")
        return arguments[flagIndex + 1]
            .split(",")
            .map { entry -> entry.substringBefore("=") to entry.substringAfter("=") }
    }

    private companion object {
        const val HandicapGuessKey = "assumeMultipleStartingBlackMovesAreHandicap"
    }
}
