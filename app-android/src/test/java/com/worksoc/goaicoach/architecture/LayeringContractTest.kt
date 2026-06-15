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

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(*checkedDirs.toTypedArray()),
            forbiddenImports = forbiddenImports,
        )

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

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(*checkedDirs.toTypedArray()),
            forbiddenImports = forbiddenImports,
        )

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

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(matchRoot),
            forbiddenImports = forbiddenImports,
        )

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
        val benchmarkModels = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineBenchmarkModels.kt")
        val benchmarkDisplay = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineBenchmarkDisplayApplication.kt")
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
        if (!benchmarkModels.exists()) {
            offenders += "${benchmarkModels.relativeTo(repoRoot).path}: missing benchmark model split"
        }
        if (!benchmarkDisplay.exists()) {
            offenders += "${benchmarkDisplay.relativeTo(repoRoot).path}: missing benchmark display split"
        }
        if (benchmarkApplication.readLines().size > 220) {
            offenders += "${benchmarkApplication.relativeTo(repoRoot).path}: workflow shell grew past 220 lines"
        }

        assertTrue(
            "Benchmark model/display/workflow policy must stay split from local EngineCoreApi benchmark execution:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnBenchmarkWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "runStartupBenchmarkWorkflowResult(",
            "engineBenchmarkWaitingDisplayPlan(",
            "engineBenchmarkRunningDisplayPlan(",
            "engineBenchmarkCompletedDisplayPlan(",
            "engineBenchmarkFailureDisplayPlan(",
            "EngineBenchmarkStartupSettleDelayMillis",
            "evaluateEngineBenchmarkGate(",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should request benchmark execution through runEngineBenchmarkApplication, not own benchmark workflow details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnTopMovesWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "topMoveAnalysisOperationToken(",
            "runTopMoveAnalysisEffectApplyPlan(",
            "TopMoveAnalysisEffectLaunchRequest(",
            "TopMoveAnalysisExecutionContext(",
            "toTopMoveAnalysisLaunchPlan(",
            "applyTopMoveAnalysisLaunchPlan(",
            "shouldRequestTopMoveAnalysis(",
            "toShowTopMovesPlan(",
            "ShowTopMovesPlan.",
            "settingsState = settingsState.hideTopMoves()",
            "Top Moves hidden. Background move review keeps using fast best-1 analysis.",
            "clearTopMoveSpots(",
            "Search time changed. Analysis cache will rebuild with the new time cap.",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should request Top Moves through runTopMoveAnalysisApplication, not own launch/token/effect details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnHumanMoveSyncWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "HumanEngineSyncCompletionRequest(",
            "HumanEngineSyncEffectLaunchRequest(",
            "HumanEngineSyncRunPlan(",
            "buildHumanEngineSyncCompletionPlan(",
            "runHumanEngineSyncWorkflowResult(",
            "EngineOperationKind.HumanMoveSync",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run human move engine sync through runHumanEngineSyncApplication, not own launch/effect/completion details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnPostUndoScoreSyncWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "PostUndoScoreSyncEffectLaunchRequest(",
            "runPostUndoScoreSyncApplyPlan(",
            "EngineOperationKind.PostUndoSync",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run post-undo score sync through runPostUndoScoreSyncApplication, not own operation/effect details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnUndoWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "buildUndoRequestPlan(",
            "buildLocalTwoPlayerUndoPlan(",
            "buildEngineUndoCompletionPlan(",
            "GameSessionEffect.UndoEngineMoves(",
            "EngineUndoCompletionPlan.",
            "EngineOperationKind.EngineUndo",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run undo through runUndoLastTurnApplication/runEngineUndoApplication, not own engine undo workflow details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnScoringRuleSyncWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "ScoringRuleSyncEffectLaunchRequest(",
            "runScoringRuleSyncApplyPlan(",
            "EngineOperationKind.ScoringRuleSync",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run scoring-rule score sync through runScoringRuleSyncApplication, not own operation/effect details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnRestoredGameSyncWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "RestoredGameSyncEffectLaunchRequest(",
            "RestoredGameSyncExecutionContext(",
            "runRestoredGameSyncApplyPlan(",
            "GameSessionEffect.SyncRestoredGame(",
            "EngineOperationKind.RestoredGameSync",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run restored-game score sync through runRestoredGameSyncApplication, not own operation/effect details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnSavedGameWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "SavedGamePersistenceRequest(",
            "SavedGameRestoreRequestPlan",
            "SavedSessionPromptPlan",
            "loadSavedSessionPromptPlan(",
            "buildSavedGameRestoreRequestPlan(",
            "runSavedGamePersistence(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "runSavedSessionPromptApplication(",
            "runSavedGamePersistenceApplication(",
            "runSavedGameRestoreApplication(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run saved-game prompt/persistence/restore through application runners, not own saved-game request-plan details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnScoreEstimateWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "scoreEstimateOperationToken(",
            "ScoreEstimateEffectLaunchRequest(",
            "runScoreEstimateEffectApplyPlan(",
            "GameSessionEffect.RunScoreEstimate(",
            "toScoreEstimateLaunchStateUpdate(",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run score estimate through runScoreEstimateApplication, not own operation/effect/completion details:\n" +
                forbiddenFragments.joinToString("\n"),
            forbiddenFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppCollectsSessionStateHolderAndUsesDisplayApplierNaming() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "GameSessionUiStateHolder",
            "uiStateHolder",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "sessionHolder.state.collect",
            "sessionSnapshot = snapshot",
            "GameSessionDisplayStateApplier",
            "displayStateApplier",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should observe GameSessionStateHolder changes and reserve display-applier naming for display-plan application:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
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

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(scoreRoot),
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "Score runners should call EngineSessionClient members, not local EngineCoreApi extension helpers:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun scoreSyncRunnersStaySplitByTriggerDomain() {
        val repoRoot = repoRoot()
        val scoreRoot = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/score")
        val common = scoreRoot.resolve("ScoreSyncRunnerApplication.kt")
        val expectedSplitFiles = listOf(
            scoreRoot.resolve("ScoringRuleScoreSyncRunnerApplication.kt"),
            scoreRoot.resolve("PostUndoScoreSyncRunnerApplication.kt"),
            scoreRoot.resolve("RestoredGameScoreSyncRunnerApplication.kt"),
        )
        val offenders = mutableListOf<String>()

        expectedSplitFiles
            .filterNot { file -> file.exists() }
            .forEach { file -> offenders += "${file.relativeTo(repoRoot).path}: missing score sync split file" }

        val commonText = common.readText()
        val forbiddenCommonFragments = listOf(
            "ScoringRuleSyncEffectLaunchRequest",
            "PostUndoScoreSyncEffectLaunchRequest",
            "RestoredGameSyncEffectLaunchRequest",
            "runScoringRuleSyncApplication(",
            "runPostUndoScoreSyncApplication(",
            "runRestoredGameSyncApplication(",
        ).filter { fragment -> fragment in commonText }
        forbiddenCommonFragments.forEach { fragment ->
            offenders += "${common.relativeTo(repoRoot).path}: common runner still owns $fragment"
        }
        if (common.readLines().size > 90) {
            offenders += "${common.relativeTo(repoRoot).path}: common score sync helper grew past 90 lines"
        }
        expectedSplitFiles
            .filter { file -> file.exists() && file.readLines().size > 180 }
            .forEach { file -> offenders += "${file.relativeTo(repoRoot).path}: split runner grew past 180 lines" }

        assertTrue(
            "Score sync runners must stay split by trigger domain so restored/post-undo/scoring-rule policies can evolve independently:\n" +
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

        val offenders = forbiddenReferenceOffenders(
            files = contracts,
            forbiddenImports = forbiddenImports,
        )

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

        val offenders = forbiddenReferenceOffenders(
            files = portableCandidates,
            forbiddenImports = forbiddenImports,
        )

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

        val offenders = forbiddenReferenceOffenders(
            files = candidates,
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "Shared diagnostic/engine policy models must remain KMP-ready:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun detectionCatchesViolationsThatPlainImportStringWouldMiss() {
        val tempDir = java.nio.file.Files.createTempDirectory("layering-contract").toFile()
        try {
            // a) Wildcard import of the package + bare use of the forbidden type.
            val wildcardOffender = File(tempDir, "WildcardOffender.kt").apply {
                writeText(
                    """
                    package sample
                    import com.worksoc.goaicoach.shared.*
                    fun build(api: EngineCoreApi) = api
                    """.trimIndent(),
                )
            }
            // b) Fully-qualified reference inline, with no import at all.
            val inlineOffender = File(tempDir, "InlineOffender.kt").apply {
                writeText(
                    """
                    package sample
                    fun build(api: com.worksoc.goaicoach.shared.EngineCoreApi) = api
                    """.trimIndent(),
                )
            }
            // c) Aliased import still resolves to the forbidden type.
            val aliasedOffender = File(tempDir, "AliasedOffender.kt").apply {
                writeText(
                    """
                    package sample
                    import com.worksoc.goaicoach.shared.EngineCoreApi as Engine
                    fun build(api: Engine) = api
                    """.trimIndent(),
                )
            }
            // d) Negative: only a prose comment mentions it; unrelated wildcard import.
            val clean = File(tempDir, "Clean.kt").apply {
                writeText(
                    """
                    package sample
                    import com.worksoc.goaicoach.middleware.*
                    // EngineCoreApi is intentionally not referenced here.
                    fun build() = 1
                    """.trimIndent(),
                )
            }
            // e) Negative: the forbidden path appears only inside a string literal
            //    (e.g. a diagnostic/doc message), which is not a real reference.
            val stringMention = File(tempDir, "StringMention.kt").apply {
                writeText(
                    """
                    package sample
                    fun describe() = "see com.worksoc.goaicoach.shared.EngineCoreApi for details"
                    """.trimIndent(),
                )
            }
            // f) Negative: a single-line block comment mentions the path.
            val blockCommentMention = File(tempDir, "BlockCommentMention.kt").apply {
                writeText(
                    """
                    package sample
                    fun build() = 1 /* com.worksoc.goaicoach.shared.EngineCoreApi */
                    """.trimIndent(),
                )
            }

            val offenders = forbiddenReferenceOffenders(
                files = listOf(
                    wildcardOffender,
                    inlineOffender,
                    aliasedOffender,
                    clean,
                    stringMention,
                    blockCommentMention,
                ),
                forbiddenImports = listOf("import com.worksoc.goaicoach.shared.EngineCoreApi"),
            )

            assertTrue(
                "Wildcard-import + bare-use violation must be detected:\n${offenders.joinToString("\n")}",
                offenders.any { it.contains("WildcardOffender.kt") },
            )
            assertTrue(
                "Inline fully-qualified violation must be detected:\n${offenders.joinToString("\n")}",
                offenders.any { it.contains("InlineOffender.kt") },
            )
            assertTrue(
                "Aliased import violation must be detected:\n${offenders.joinToString("\n")}",
                offenders.any { it.contains("AliasedOffender.kt") },
            )
            assertTrue(
                "A prose-only mention must not be flagged:\n${offenders.joinToString("\n")}",
                offenders.none { it.contains("Clean.kt") },
            )
            assertTrue(
                "A path inside a string literal must not be flagged:\n${offenders.joinToString("\n")}",
                offenders.none { it.contains("StringMention.kt") },
            )
            assertTrue(
                "A path inside a block comment must not be flagged:\n${offenders.joinToString("\n")}",
                offenders.none { it.contains("BlockCommentMention.kt") },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun ktFilesIn(vararg dirs: File): List<File> =
        dirs.flatMap { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { file -> file.extension == "kt" }.toList()
            } else {
                emptyList()
            }
        }

    /**
     * Reports forbidden references in [files].
     *
     * Each [forbiddenImports] entry is written the way an import statement reads
     * (e.g. `import com.worksoc.goaicoach.shared.EngineCoreApi` for an exact type,
     * or `import android.` for a package prefix). Detection is stronger than a raw
     * `startsWith` on import lines: it also catches the two ways the plain
     * import-string check used to miss a violation —
     *  - a wildcard import of the type's package plus a bare use of the type name, and
     *  - a fully-qualified reference used inline in code with no import at all.
     */
    private fun forbiddenReferenceOffenders(
        files: List<File>,
        forbiddenImports: List<String>,
    ): List<String> =
        files.flatMap { file ->
            val lines = file.readLines()
            forbiddenImports.flatMap { forbidden ->
                detectForbiddenReference(lines, forbidden)
                    .map { reason -> "${file.relativeTo(repoRoot()).path}: $reason" }
            }
        }

    private fun detectForbiddenReference(lines: List<String>, forbidden: String): List<String> {
        val path = forbidden.removePrefix("import ").trim()
        val results = mutableListOf<String>()

        val importLines = lines.filter { line -> line.trimStart().startsWith("import ") }
        val codeLines = lines.filterNot { raw ->
            val trimmed = raw.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("package ") ||
                trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }

        // String literals and trailing comments are not real references; strip
        // them so a diagnostic/doc message that merely mentions a forbidden path
        // is not flagged.
        val scanLines = codeLines.map { line -> stripStringsAndTrailingComment(line) }

        // 1) Direct import. Covers exact types, package prefixes, and `... as Alias`.
        importLines.firstOrNull { line -> line.trimStart().startsWith(forbidden) }
            ?.let { line -> results += "forbidden import -> ${line.trim()}" }

        // 2) Wildcard import of an exact type's package + a bare use of its simple name.
        val isExactType = !path.endsWith(".") &&
            path.substringAfterLast('.').firstOrNull()?.isUpperCase() == true
        if (isExactType) {
            val simpleName = path.substringAfterLast('.')
            val packageName = path.substringBeforeLast('.')
            val hasWildcardImport = importLines.any { line -> line.trim() == "import $packageName.*" }
            if (hasWildcardImport) {
                val bareUse = Regex("(?<![\\w.])${Regex.escape(simpleName)}(?![\\w])")
                if (scanLines.any { line -> bareUse.containsMatchIn(line) }) {
                    results += "wildcard import `$packageName.*` with bare use of `$simpleName`"
                }
            }
        }

        // 3) Fully-qualified reference used inline in code (no import required).
        val inlineUse = if (path.endsWith(".")) {
            Regex("(?<![\\w.])${Regex.escape(path)}[A-Za-z_]")
        } else {
            Regex("(?<![\\w.])${Regex.escape(path)}(?![\\w])")
        }
        if (scanLines.any { line -> inlineUse.containsMatchIn(line) }) {
            results += "fully-qualified reference -> $path"
        }

        return results.distinct()
    }

    /**
     * Blanks out string-literal and single-line block-comment contents, plus a
     * trailing line comment, so forbidden-reference detection looks only at
     * actual code. Multi-line raw strings/comments spanning lines are out of
     * scope; triple- and double-quoted single-line strings are handled.
     */
    private fun stripStringsAndTrailingComment(line: String): String =
        line
            .replace(Regex("\"\"\".*?\"\"\""), "\"\"")
            .replace(Regex("\"(\\\\.|[^\"\\\\])*\""), "\"\"")
            .replace(Regex("/\\*.*?\\*/"), "")
            .substringBefore("//")

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
