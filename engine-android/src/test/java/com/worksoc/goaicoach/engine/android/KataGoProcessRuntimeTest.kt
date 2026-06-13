package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.EngineProfile
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
}
