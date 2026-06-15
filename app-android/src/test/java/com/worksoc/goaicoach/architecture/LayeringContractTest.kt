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
    fun matchPoliciesDoNotImportRawEngineCoreApi() {
        val matchRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/match")
        val forbiddenImports = listOf(
            "import com.worksoc.goaicoach.shared.EngineCoreApi",
        )

        val offenders = matchRoot
            .walkTopDown()
            .filter { file -> file.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                    .map { line -> "${file.relativeTo(repoRoot()).path}: $line" }
            }
            .toList()

        assertTrue(
            "Match policies must depend on small middleware gateways, not raw EngineCoreApi:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun localEngineSessionDelegateOwnsSessionOrchestration() {
        val repoRoot = repoRoot()
        val engineSession = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineSession.kt")
        val sessionText = engineSession.readText()
        val forbiddenCoreExtensions = listOf(
            "startEngineSession",
            "startNewEngineGame",
            "syncAndEstimateGraphScore",
            "configureSyncAndEstimateGraphScore",
            "runAutoAiTurn",
            "syncAfterHumanMove",
            "estimateScoreForState",
            "resolveEndgameForState",
        )
            .filter { name -> "fun EngineCoreApi.$name" in sessionText }

        assertTrue(
            "LocalEngineCoreSessionDelegate should own session orchestration; EngineSession.kt should keep only low-level sync/helpers:\n" +
                forbiddenCoreExtensions.joinToString("\n"),
            forbiddenCoreExtensions.isEmpty(),
        )
    }

    @Test
    fun localEngineBenchmarkDelegateOwnsRawBenchmarkExecution() {
        val repoRoot = repoRoot()
        val benchmarkApplication = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineDeviceBenchmarkApplication.kt")
        val benchmarkDelegate = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/engine/LocalEngineBenchmarkDelegate.kt")
        val applicationText = benchmarkApplication.readText()
        val delegateText = benchmarkDelegate.readText()

        val offenders = mutableListOf<String>()
        if ("import com.worksoc.goaicoach.shared.EngineCoreApi" in applicationText) {
            offenders += "${benchmarkApplication.relativeTo(repoRoot).path}: raw EngineCoreApi import"
        }
        if ("fun EngineCoreApi.runStartupEngineBenchmark" in applicationText) {
            offenders += "${benchmarkApplication.relativeTo(repoRoot).path}: raw startup benchmark extension"
        }
        if ("class LocalEngineBenchmarkDelegate" !in delegateText) {
            offenders += "${benchmarkDelegate.relativeTo(repoRoot).path}: missing local benchmark delegate"
        }

        assertTrue(
            "Benchmark UI/workflow policy must stay separate from local EngineCoreApi benchmark execution:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun scoreRunnersUseEngineSessionClientContractOnly() {
        val scoreRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/score")
        val forbiddenImports = listOf(
            "import com.worksoc.goaicoach.application.engine.syncAndEstimateGraphScore",
            "import com.worksoc.goaicoach.application.engine.configureSyncAndEstimateGraphScore",
            "import com.worksoc.goaicoach.application.engine.estimateScoreForState",
        )

        val offenders = scoreRoot
            .walkTopDown()
            .filter { file -> file.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                    .map { line -> "${file.relativeTo(repoRoot()).path}: $line" }
            }
            .toList()

        assertTrue(
            "Score runners should call EngineSessionClient members, not local EngineCoreApi extension helpers:\n" +
                offenders.joinToString("\n"),
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
        val platformBoundAdapters = setOf(
            applicationRoot.resolve("diagnostic/LocalFileDiagnosticEventExternalSink.kt").canonicalFile,
        )
        val portableCandidates = applicationRoot
            .walkTopDown()
            .filter { file -> file.extension == "kt" }
            .map { file -> file.canonicalFile }
            .filterNot { file -> file in platformBoundAdapters }
            .toList()
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
            "import com.worksoc.goaicoach.ui.",
            "import com.worksoc.goaicoach.persistence.",
            "import com.worksoc.goaicoach.engine.",
        )

        val missingPlatformBoundAdapters = platformBoundAdapters.filterNot { file -> file.exists() }
        assertTrue(
            "Platform-bound application adapters must be explicit and existing:\n" +
                missingPlatformBoundAdapters.joinToString("\n") { file -> file.path },
            missingPlatformBoundAdapters.isEmpty(),
        )

        val offenders = portableCandidates.flatMap { candidate ->
            candidate
                .readLines()
                .filter { line -> forbiddenImports.any { forbidden -> line.startsWith(forbidden) } }
                .map { line -> "${candidate.relativeTo(repoRoot()).path}: $line" }
        }

        assertTrue(
            "Application files are middleware/KMP move candidates by default. Add only explicit adapter exceptions for platform-bound files:\n" +
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
