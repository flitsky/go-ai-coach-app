package com.worksoc.goaicoach.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeringContractTest {
    @Test
    fun uiAndPresentationDoNotImportRawEngineCoreApi() {
        val sourceRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach")
        val checkedDirs = listOf(
            sourceRoot.resolve("ui"),
            sourceRoot.resolve("presentation"),
        )
        val forbiddenImports = listOf(
            "import com.worksoc.goaicoach.shared.EngineAdapter",
            "import com.worksoc.goaicoach.shared.EngineCoreApi",
            "import com.worksoc.goaicoach.engine.android",
        )

        val offenders = checkedDirs
            .flatMap { dir -> dir.walkTopDown().filter { file -> file.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                    .map { line -> "${file.relativeTo(repoRoot()).path}: $line" }
            }

        assertTrue(
            "UI/presentation must call middleware APIs instead of raw engine APIs:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun applicationAndMatchDoNotDependOnCompatibilityEngineAdapterOrAndroidRuntime() {
        val sourceRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach")
        val checkedDirs = listOf(
            sourceRoot.resolve("application"),
            sourceRoot.resolve("match"),
        )
        val forbiddenImports = listOf(
            "import com.worksoc.goaicoach.shared.EngineAdapter",
            "import com.worksoc.goaicoach.engine.android",
        )

        val offenders = checkedDirs
            .flatMap { dir -> dir.walkTopDown().filter { file -> file.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                    .map { line -> "${file.relativeTo(repoRoot()).path}: $line" }
            }

        assertTrue(
            "Application/match must depend on EngineCoreApi or middleware ports, not compatibility aliases/runtime implementations:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun positionAnalysisGatewayContractStaysKmpReady() {
        val middlewareRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/middleware")
        val contracts = listOf(
            middlewareRoot.resolve("PositionAnalysisGateway.kt"),
            middlewareRoot.resolve("RemotePositionAnalysisGateway.kt"),
        )
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
            "import com.worksoc.goaicoach.application.",
            "import com.worksoc.goaicoach.ui.",
            "import com.worksoc.goaicoach.persistence.",
            "import com.worksoc.goaicoach.engine.",
        )

        val offenders = contracts.flatMap { contract ->
            contract
                .readLines()
                .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                .map { line -> "${contract.relativeTo(repoRoot()).path}: $line" }
        }

        assertTrue(
            "Position analysis middleware gateway contracts must remain KMP-ready:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun httpRemoteAnalysisTransportStaysOutOfKmpReadyGatewayContracts() {
        val transport = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/middleware/HttpRemotePositionAnalysisTransport.kt")
        val text = transport.readText()

        assertTrue(
            "HTTP transport is intentionally JVM/Android-bound and should remain in its own file.",
            text.contains("java.net.HttpURLConnection") && text.contains("org.json.JSONObject"),
        )
    }

    @Test
    fun engineOperationApplicationPoliciesStayPortable() {
        val applicationRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application")
        val portableCandidates = listOf(
            applicationRoot.resolve("autoai/AutoAiCompletionApplication.kt"),
            applicationRoot.resolve("autoai/AutoAiEffectLauncherApplication.kt"),
            applicationRoot.resolve("autoai/AutoAiPolicyApplication.kt"),
            applicationRoot.resolve("autoai/AutoAiRunnerApplication.kt"),
            applicationRoot.resolve("engine/operation/EngineOperationLifecycle.kt"),
            applicationRoot.resolve("engine/operation/EngineOperationPolicyAdapter.kt"),
            applicationRoot.resolve("engine/operation/EngineOperationPolicy.kt"),
            applicationRoot.resolve("engine/operation/EngineOperationResultApplication.kt"),
            applicationRoot.resolve("engine/operation/EngineOperationScope.kt"),
            applicationRoot.resolve("diagnostic/DiagnosticEventApplication.kt"),
            applicationRoot.resolve("diagnostic/DiagnosticEventExternalSinkApplication.kt"),
            applicationRoot.resolve("diagnostic/DiagnosticEventObserverApplication.kt"),
            applicationRoot.resolve("diagnostic/DiagnosticEventPorts.kt"),
            applicationRoot.resolve("prompt/PromptPriorityApplication.kt"),
            applicationRoot.resolve("engine/EngineAssistantJudgePolicy.kt"),
            applicationRoot.resolve("engine/EngineDeviceBenchmarkApplication.kt"),
            applicationRoot.resolve("engine/EngineBenchmarkPorts.kt"),
            applicationRoot.resolve("engine/EngineEffectLauncherApplication.kt"),
            applicationRoot.resolve("engine/EngineAnalysisDiagnosticRecorder.kt"),
            applicationRoot.resolve("engine/EngineSessionClient.kt"),
            applicationRoot.resolve("engine/EngineSessionLifecycleApplication.kt"),
            applicationRoot.resolve("engine/EngineStartupApplication.kt"),
            applicationRoot.resolve("engine/LocalPositionAnalysisCacheCoordinator.kt"),
            applicationRoot.resolve("analysis/AnalysisSession.kt"),
            applicationRoot.resolve("analysis/AnalysisFormatter.kt"),
            applicationRoot.resolve("analysis/PositionAnalysisCache.kt"),
            applicationRoot.resolve("analysis/PositionAnalysisCacheOptimization.kt"),
            applicationRoot.resolve("debugreport/DebugReportBuilder.kt"),
            applicationRoot.resolve("debugreport/DebugReportPorts.kt"),
            applicationRoot.resolve("endgame/EndgameLogFormatter.kt"),
            applicationRoot.resolve("endgame/EndgameResolver.kt"),
            applicationRoot.resolve("movereview/MoveReview.kt"),
            applicationRoot.resolve("preferences/UserPreferencesApplication.kt"),
            applicationRoot.resolve("preferences/UserPreferencesAutosaveApplication.kt"),
            applicationRoot.resolve("preferences/UserPreferencesPorts.kt"),
            applicationRoot.resolve("preferences/UserPreferencesSnapshot.kt"),
            applicationRoot.resolve("session/GameSessionAnalysisState.kt"),
            applicationRoot.resolve("session/GameSessionController.kt"),
            applicationRoot.resolve("session/GameSessionCoreState.kt"),
            applicationRoot.resolve("session/GameSessionMoveReviewState.kt"),
            applicationRoot.resolve("session/GameSessionRuntimeState.kt"),
            applicationRoot.resolve("session/GameSessionScoreState.kt"),
            applicationRoot.resolve("session/GameSessionSettingsState.kt"),
            applicationRoot.resolve("session/GameSessionTurnTimeState.kt"),
            applicationRoot.resolve("session/GameSessionUiStateHolderApplication.kt"),
            applicationRoot.resolve("session/GameSessionApplication.kt"),
            applicationRoot.resolve("humanmove/HumanMoveApplication.kt"),
            applicationRoot.resolve("runtime/RuntimeEventApplication.kt"),
            applicationRoot.resolve("runtime/RuntimeEventPorts.kt"),
            applicationRoot.resolve("savedgame/SavedGamePorts.kt"),
            applicationRoot.resolve("savedgame/SavedGamePersistence.kt"),
            applicationRoot.resolve("savedgame/SavedGamePersistenceRunner.kt"),
            applicationRoot.resolve("savedgame/SavedGameRestoreApplication.kt"),
            applicationRoot.resolve("savedgame/SavedSessionPromptApplication.kt"),
            applicationRoot.resolve("score/ScoreDisplayApplication.kt"),
            applicationRoot.resolve("score/ScoreDisplayFormatterApplication.kt"),
            applicationRoot.resolve("score/ScoreEstimateRunnerApplication.kt"),
            applicationRoot.resolve("score/ScoreSyncRunnerApplication.kt"),
            applicationRoot.resolve("score/ScoreSyncCompletionApplication.kt"),
            applicationRoot.resolve("score/ScoringRuleApplication.kt"),
            applicationRoot.resolve("startgame/StartGameApplication.kt"),
            applicationRoot.resolve("topmoves/TopMovesEffectLauncherApplication.kt"),
            applicationRoot.resolve("topmoves/TopMovesApplication.kt"),
            applicationRoot.resolve("undo/UndoApplication.kt"),
        )
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
            "import com.worksoc.goaicoach.ui.",
            "import com.worksoc.goaicoach.persistence.",
            "import com.worksoc.goaicoach.engine.",
        )

        val offenders = portableCandidates.flatMap { candidate ->
            candidate
                .readLines()
                .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                .map { line -> "${candidate.relativeTo(repoRoot()).path}: $line" }
        }

        assertTrue(
            "Engine operation policy files are middleware/KMP move candidates and must stay platform-free:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun sharedPolicyModelsStayKmpReady() {
        val sharedRoot = repoRoot()
            .resolve("shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared")
        val candidates = listOf(
            sharedRoot.resolve("diagnostic/DiagnosticEventModel.kt"),
            sharedRoot.resolve("engine/EngineOperationPolicy.kt"),
            sharedRoot.resolve("MoveValueDisplay.kt"),
        )
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
            "import com.worksoc.goaicoach.application.",
            "import com.worksoc.goaicoach.ui.",
            "import com.worksoc.goaicoach.persistence.",
            "import com.worksoc.goaicoach.engine.",
        )

        val offenders = candidates.flatMap { candidate ->
            candidate
                .readLines()
                .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                .map { line -> "${candidate.relativeTo(repoRoot()).path}: $line" }
        }

        assertTrue(
            "Shared diagnostic/engine policy models must remain KMP-ready:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    private fun repoRoot(): File {
        var current = File(".").canonicalFile
        while (true) {
            if (File(current, "settings.gradle.kts").exists()) {
                return current
            }
            current = current.parentFile ?: break
        }
        error("Could not locate repository root from ${File(".").canonicalPath}")
    }
}
