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
    fun authPremiumAndDeviceApplicationPackagesStayPlatformFree() {
        // application/auth, application/premium, and application/device follow the same
        // port/adapter split as the engine layers (EngineCoreApi vs
        // KataGoProcessEngineAdapter): the port interfaces (AuthClientPort,
        // PremiumStateStorePort, DeviceIdentityStorePort) must stay pure Kotlin, while the real
        // Android/Firebase/SharedPreferences-backed adapters live in ui/ or persistence/.
        val sourceRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach")
        val checkedDirs = listOf(
            sourceRoot.resolve("application/auth"),
            sourceRoot.resolve("application/premium"),
            sourceRoot.resolve("application/device"),
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

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(*checkedDirs.toTypedArray()),
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "application/auth, application/premium, and application/device must stay platform-free " +
                "ports; put Android/Firebase-specific adapters in ui/ or persistence/ instead:\n" +
                offenders.joinToString("\n"),
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
        val engineSession = applicationFile("engine/EngineSession.kt")
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
        val benchmarkApplication = applicationFile("engine/EngineDeviceBenchmarkApplication.kt")
        val benchmarkModels = applicationFile("engine/EngineBenchmarkModels.kt")
        val benchmarkDisplay = applicationFile("engine/EngineBenchmarkDisplayApplication.kt")
        val benchmarkDelegate = applicationFile("engine/LocalEngineBenchmarkDelegate.kt")
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
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "topMoveAnalysisOperationToken(",
            "runTopMoveAnalysisEffectApplyPlan(",
            "TopMoveAnalysisEffectLaunchRequest(",
            "TopMoveAnalysisExecutionContext(",
            "applyTopMoveAnalysisCompletionApplyPlan(",
            "TopMoveAnalysisCompletionApplyPlan.",
            "toTopMoveAnalysisLaunchPlan(",
            "applyTopMoveAnalysisLaunchPlan(",
            "shouldRequestTopMoveAnalysis(",
            "toShowTopMovesPlan(",
            "ShowTopMovesPlan.",
            "settingsState = settingsState.hideTopMoves()",
            "Top Moves hidden. Background move review keeps using fast best-1 analysis.",
            "clearTopMoveSpots(",
            "Search time changed. Analysis cache will rebuild with the new time cap.",
            "runTopMoveAnalysisApplication(",
            "TopMoveAnalysisRunRequest(",
            "runShowTopMovesApplication(",
            "ShowTopMovesRunRequest(",
            "runHideTopMovesApplication(",
            "HideTopMovesRunRequest(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "TopMovesController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate Top Moves to TopMovesController, not own launch/token/effect/runner details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
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
            "buildUndoLocalStatePlan(",
            "runApplyLocalUndoApplication(",
            "UndoRequestPlan.ApplyLocalUndo(",
        )
            .filter { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run undo through runUndoLastTurnApplication/UndoController.applyLocalUndo, not own undo workflow details:\n" +
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
            "runSavedGameRestoreApplication(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "runSavedSessionPromptApplication(",
            "runSavedGamePersistenceApplication(",
            "savedSessionController.restore(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should run saved-game prompt/persistence through application runners and restore through SavedSessionController:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun savedSessionControllerDelegatesToApplicationRunners() {
        val controller = applicationFile("savedgame/SavedSessionController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runSavedGameRestoreApplication(",
            "runRestoredGameSyncApplication(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "SavedSessionController should delegate to application runners:\nmissing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnEngineBackedNewGameWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "GameSessionEffect.StartEngineBackedGame(",
            "runEngineBackedNewGameWorkflowResult(",
            "EngineStartupWorkflowResult.Success",
            "EngineStartupWorkflowResult.Failure",
            "EngineOperationKind.EngineNewGame",
            "runtimeEngineGameStartSuccessLog(",
            "runtimeEngineGameStartFailureLog(",
            "runStartEngineBackedGameApplication(",
            "StartEngineBackedGameRunRequest(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "newGameController::startConfiguredGame",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate new-game to NewGameController, not own engine operation/effect/workflow details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun newGameControllerDelegatesToApplicationRunners() {
        val controller = applicationFile("startgame/NewGameController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runStartEngineBackedGameApplication(",
            "buildStartConfiguredGamePlan(",
            "buildNewLocalGameSessionPlan(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "NewGameController should delegate to application runners:\nmissing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnScheduledAutoAiTurnWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "autoAiTurnOperationToken(",
            "GameSessionEffect.RunAutoAiTurn(",
            "AutoAiTurnRunExecutionContext(",
            "runAutoAiTurnWorkflowResult(",
            "buildAutoAiTurnCompletionPlan(",
            "runtimeAiTurnBeginLog(",
            "runtimeAiTurnCompleteLog(",
            "runtimeAiTurnScheduleCancelledLog(",
            "runScheduledAutoAiTurnApplication(",
            "AutoAiScheduledTurnRunRequest(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "autoAiTurnController::requestAiTurn",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate scheduled Auto-AI turns to AutoAiTurnController, not own operation/workflow/completion details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnAutoAiTurnCompletionApplyBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "fun applyAutoAiTurnSuccessCompletion(",
            "fun applyAutoAiTurnFailureCompletion(",
            "runtimeAiTurnSuccessLog(",
            "runtimeAiTurnFailureLog(",
            "buildAutoAiTurnEndgamePlan(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "recordTurnMove =",
            "applyTurnDisplay =",
            "applyTurnFailureDisplay =",
            "AutoAiTurnController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate Auto-AI completion display/log/endgame decision to AutoAiTurnController:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnAutoAiEndgameResolveWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "autoAiEndgameOperationToken(",
            "GameSessionEffect.ResolveAutoAiEndgame(",
            "runAutoAiEndgameEffect(",
            "buildAutoAiEndgameCompletionPlan(",
            "AutoAiEndgameCompletionPlan.",
            "runtimeAiTurnEndgameDetectedLog(",
            "runtimeAiTurnEndgameSuccessLog(",
            "runtimeAiTurnEndgameFailureLog(",
            "runAutoAiEndgameApplication(",
            "AutoAiEndgameRunRequest(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "AutoAiTurnController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should resolve Auto-AI pass/pass endgame through AutoAiTurnController, not own token/effect/completion/log details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun autoAiTurnControllerDelegatesToApplicationRunners() {
        val controller = applicationFile("autoai/AutoAiTurnController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runScheduledAutoAiTurnApplication(",
            "AutoAiScheduledTurnRunRequest(",
            "runAutoAiEndgameApplication(",
            "AutoAiEndgameRunRequest(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "AutoAiTurnController must delegate to application runners:\nmissing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun humanMoveControllerDelegatesToApplicationRunners() {
        val controller = applicationFile("humanmove/HumanMoveController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "applyHumanMoveLocally(",
            "runHumanEngineSyncApplication(",
            "HumanEngineSyncRunRequest(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "HumanMoveController must delegate to application runners:\nmissing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun topMovesControllerDelegatesToApplicationRunners() {
        val controller = applicationFile("topmoves/TopMovesController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runTopMoveAnalysisApplication(",
            "TopMoveAnalysisRunRequest(",
            "runShowTopMovesApplication(",
            "ShowTopMovesRunRequest(",
            "runHideTopMovesApplication(",
            "HideTopMovesRunRequest(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "TopMovesController must delegate to application runners:\nmissing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnScoreEstimateWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "scoreEstimateOperationToken(",
            "ScoreEstimateEffectLaunchRequest(",
            "runScoreEstimateEffectApplyPlan(",
            "GameSessionEffect.RunScoreEstimate(",
            "toScoreEstimateLaunchStateUpdate(",
            "runScoreEstimateApplication(",
            "ScoreEstimateRunRequest(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "ScoreEstimateController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate score estimate to ScoreEstimateController, not own operation/effect/completion details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun scoreEstimateControllerDelegatesToApplicationRunner() {
        val controller = applicationFile("score/ScoreEstimateController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runScoreEstimateApplication(",
            "ScoreEstimateRunRequest(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "ScoreEstimateController must delegate to runScoreEstimateApplication, missing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnDebugReportCopyWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "DebugReportCopyActionRequest(",
            "runDebugReportCopyAction(",
            "runtimeEventLog.readText()",
            "diagnosticEventLog.readText()",
            "runDebugReportCopyApplication(",
            "DebugReportCopyRunRequest(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "DebugReportController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate debug report copy to DebugReportController, not own runner details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun debugReportControllerDelegatesToApplicationRunner() {
        val controller = applicationFile("debugreport/DebugReportController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runDebugReportCopyApplication(",
            "DebugReportCopyRunRequest(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "DebugReportController must delegate to runDebugReportCopyApplication:\nmissing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnPositionCacheOptimizationWorkflowBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringFileNames = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        )
        val wiringText = wiringFileNames.joinToString("\n") { fileName ->
            repoRoot()
                .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/$fileName")
                .readText()
        }
        val text = goCoachApp.readText() + "\n" + wiringText
        val forbiddenFragments = listOf(
            "GameSessionEffect.RunPositionCacheOptimization(",
            "PositionAnalysisCacheOptimizationWorkflowResult.",
            "runPositionAnalysisCacheOptimizationWorkflowResult(",
            "EngineOperationKind.PositionCacheOptimization",
            "EngineFallbackPolicy.CachedAnalysis",
            "position-cache-optimization",
            "runPositionAnalysisCacheOptimizationApplication(",
            "PositionAnalysisCacheOptimizationRunRequest(",
            "buildPositionAnalysisCacheOptimizationPlan(",
            "refreshPositionAnalysisCacheOptimizationPrompt(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "PositionCacheOptimizationController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate position-cache-optimization to PositionCacheOptimizationController, not own plan/runner/prompt details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun positionCacheOptimizationControllerDelegatesToApplicationRunner() {
        val controller = applicationFile("analysis/PositionCacheOptimizationController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "runPositionAnalysisCacheOptimizationApplication(",
            "PositionAnalysisCacheOptimizationRunRequest(",
            "buildPositionAnalysisCacheOptimizationPlan(",
            "refreshPositionAnalysisCacheOptimizationPrompt(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "PositionCacheOptimizationController must delegate to the application runner, missing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppDoesNotOwnEngineOperationLifecycleBody() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "applyEngineOperationLifecycleTransition(",
            "EngineOperationLifecycleTransition.",
            "EngineOperationLifecycleState(",
            "runEngineOperationInScope(",
            "recordEngineOperationDiscardLog(",
            "runtimeEngineOperationStartedLog(",
            "runtimeEngineOperationCompletedLog(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "EngineOperationLifecycleController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate engine-operation lifecycle tracking to EngineOperationLifecycleController, not own transition/scope/log details:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
        )
    }

    @Test
    fun engineOperationLifecycleControllerOwnsTransitionAndScope() {
        val controller = applicationFile("engine/operation/EngineOperationLifecycleController.kt")
        val text = controller.readText()
        val requiredFragments = listOf(
            "applyEngineOperationLifecycleTransition(",
            "runEngineOperationInScope(",
            "recordEngineOperationDiscardLog(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "EngineOperationLifecycleController must own the lifecycle transition/scope/discard wiring, missing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
        )
    }

    @Test
    fun goCoachAppUsesScreenStateAssemblerInsteadOfDirectScreenStateBuilders() {
        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val text = goCoachApp.readText()
        val forbiddenFragments = listOf(
            "buildGameScreenStateInput(",
            "buildGameScreenState(",
        )
            .filter { fragment -> fragment in text }
        val requiredFragments = listOf(
            "GoCoachScreenStateAssembler.assemble(",
            "GoCoachScreenStateAssembler.Input(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should assemble final screen state through GoCoachScreenStateAssembler, not call presentation builders directly:\n" +
                "forbidden:\n${forbiddenFragments.joinToString("\n")}\nmissing:\n${requiredFragments.joinToString("\n")}",
            forbiddenFragments.isEmpty() && requiredFragments.isEmpty(),
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
    fun gameSessionStateHolderStaysPlatformFreeForSharedMove() {
        val holder = applicationFile("session/GameSessionStateHolder.kt")
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.compose.",
            "import java.",
            "import org.json.",
        )

        val offenders = forbiddenReferenceOffenders(
            files = listOf(holder),
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "GameSessionStateHolder must stay free of Android/Compose/JVM JSON imports before moving to shared:\n" +
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
        val common = applicationFile("score/ScoreSyncRunnerApplication.kt")
        val expectedSplitFiles = listOf(
            applicationFile("score/ScoringRuleScoreSyncRunnerApplication.kt"),
            applicationFile("score/PostUndoScoreSyncRunnerApplication.kt"),
            applicationFile("score/RestoredGameScoreSyncRunnerApplication.kt"),
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
    fun positionAnalysisGatewayContractsStayKmpReadyAndTransportFree() {
        val repoRoot = repoRoot()
        val middlewareRoot = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/middleware")
        val contracts = listOf(
            middlewareRoot.resolve("PositionAnalysisGateway.kt"),
            middlewareRoot.resolve("RemotePositionAnalysisGateway.kt"),
        )
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import javax.",
            "import java.",
            "import org.json.",
            "import com.worksoc.goaicoach.application.",
            "import com.worksoc.goaicoach.ui.",
            "import com.worksoc.goaicoach.persistence.",
            "import com.worksoc.goaicoach.engine.",
        )
        val forbiddenTransportFragments = listOf(
            "HttpRemotePositionAnalysisTransport",
            "RemotePositionAnalysisHttpConfig",
            "RemotePositionAnalysisHttpConnectionFactory",
            "HttpURLConnection",
            "JSONObject",
            "JSONArray",
            "java.net.URL",
        )

        val missingContracts = contracts.filterNot { file -> file.exists() }
        assertTrue(
            "KMP-ready position analysis gateway contract files must exist before the middleware module split:\n" +
                missingContracts.joinToString("\n") { file -> file.relativeTo(repoRoot).path },
            missingContracts.isEmpty(),
        )

        val importOffenders = forbiddenReferenceOffenders(
            files = contracts,
            forbiddenImports = forbiddenImports,
        )
        val transportOffenders = contracts.flatMap { file ->
            val text = file.readText()
            forbiddenTransportFragments
                .filter { fragment -> fragment in text }
                .map { fragment -> "${file.relativeTo(repoRoot).path}: transport detail -> $fragment" }
        }
        val offenders = importOffenders + transportOffenders

        assertTrue(
            "Position analysis gateway contract files are the KMP move candidates. " +
                "Keep them limited to shared DTOs/coroutines and leave HTTP/JVM JSON transport in HttpRemotePositionAnalysisTransport.kt:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun engineImplementationsLiveInEngineAndroidNotAppAndroid() {
        // 260804 정리: EngineCoreApi의 로컬/원격 구현체를 전부 engine-android 모듈로 물리적으로
        // 모았다 — app-android(3~7계층) 작업 시 엔진 내부를 아예 안 봐도 되게 하고, 실수로도
        // app-android 쪽에 엔진 구현 세부사항이 다시 새어 들어오지 않았는지 기계적으로 보장한다.
        val repoRoot = repoRoot()
        val movedFiles = listOf(
            "engine-android/src/main/java/com/worksoc/goaicoach/engine/android/HttpRemotePositionAnalysisTransport.kt",
            "engine-android/src/main/java/com/worksoc/goaicoach/engine/android/RemoteEngineCoreApiAdapter.kt",
        )
        val staleAppAndroidPaths = listOf(
            "app-android/src/main/java/com/worksoc/goaicoach/middleware/HttpRemotePositionAnalysisTransport.kt",
            "app-android/src/main/java/com/worksoc/goaicoach/middleware/RemoteEngineCoreApiAdapter.kt",
        )

        val missing = movedFiles.filterNot { path -> repoRoot.resolve(path).exists() }
        val stillInAppAndroid = staleAppAndroidPaths.filter { path -> repoRoot.resolve(path).exists() }

        assertTrue(
            "EngineCoreApi implementations must live in engine-android:\n" +
                "missing:\n${missing.joinToString("\n")}\n" +
                "still present in app-android (should have moved):\n${stillInAppAndroid.joinToString("\n")}",
            missing.isEmpty() && stillInAppAndroid.isEmpty(),
        )

        val transportText = repoRoot
            .resolve("engine-android/src/main/java/com/worksoc/goaicoach/engine/android/HttpRemotePositionAnalysisTransport.kt")
            .readText()
        assertTrue(
            "HTTP transport is intentionally JVM/Android-bound and should remain in its own file.",
            transportText.contains("java.net.HttpURLConnection") && transportText.contains("org.json.JSONObject"),
        )
    }

    @Test
    fun engineCoreApiConcreteAdaptersStayInternalBehindFactory() {
        // 260804 가시성 강화: KataGoProcessEngineAdapter/StubEngineAdapter는 engine-android
        // 모듈 밖(app-android 포함)에서 이름조차 보이면 안 된다 — Kotlin `internal`이 컴파일
        // 타임에 강제하지만, 이 테스트는 그 modifier가 실수로 지워지지 않았는지 소스 레벨에서도
        // 확인하고, app-android가 실제로 EngineCoreApiFactory(공개 생성 지점)만 쓰는지 본다.
        val repoRoot = repoRoot()
        val engineAndroidRoot = repoRoot
            .resolve("engine-android/src/main/java/com/worksoc/goaicoach/engine/android")
        val concreteAdapters = mapOf(
            engineAndroidRoot.resolve("KataGoProcessEngineAdapter.kt") to "internal class KataGoProcessEngineAdapter(",
            engineAndroidRoot.resolve("StubEngineAdapter.kt") to "internal class StubEngineAdapter",
        )

        val notInternal = concreteAdapters.filterNot { (file, marker) -> file.readText().contains(marker) }
        assertTrue(
            "EngineCoreApi concrete adapters must stay internal to engine-android:\n" +
                notInternal.keys.joinToString("\n") { file -> file.relativeTo(repoRoot).path },
            notInternal.isEmpty(),
        )

        val factoryText = engineAndroidRoot.resolve("EngineCoreApiFactory.kt").readText()
        assertTrue(
            "engine-android must expose EngineCoreApiFactory as the only public construction seam.",
            factoryText.contains("object EngineCoreApiFactory") && !factoryText.trimStart().startsWith("internal"),
        )

        val bootstrap = repoRoot
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/engine/EngineBootstrap.kt")
        val bootstrapText = bootstrap.readText()
        val forbiddenDirectConstruction = listOf("KataGoProcessEngineAdapter(", "StubEngineAdapter(")
            .filter { fragment -> fragment in bootstrapText }
        assertTrue(
            "EngineBootstrap must construct engines through EngineCoreApiFactory, not the concrete classes directly:\n" +
                forbiddenDirectConstruction.joinToString("\n"),
            forbiddenDirectConstruction.isEmpty() && bootstrapText.contains("EngineCoreApiFactory."),
        )
    }

    @Test
    fun engineOperationApplicationPoliciesStayPortable() {
        // 260816: application/ 트리 전체가 shared/commonMain으로 물리적으로 이전됐다
        // (GAMESESSION_SHARED_MIGRATION_KICKOFF_PLAN_260816_1808.md 웨이브 1~6). 스캔 대상을
        // app-android/.../application(이제 LocalFileDiagnosticEventExternalSink.kt 하나만
        // 남아 사실상 공집합)에서 실제로 파일들이 있는 shared/commonMain/.../application으로
        // 옮긴다. ui./persistence./engine.(composition-root) 임포트는 shared가 app-android에
        // 대한 Gradle 의존성 자체가 없어(shared/build.gradle.kts 확인 — commonMain은
        // kotlinx-coroutines-core만 의존) 더 이상 텍스트 검사가 필요 없다(어기면 그냥
        // Unresolved reference 컴파일 에러). 여전히 텍스트 검사가 필요한 건
        // android./androidx./java./org.json. — shared의 androidTarget은 이 API들에 실제
        // 접근 가능해서 컴파일은 통과하지만 iOS 등 다른 KMP 타깃을 조용히 깨뜨릴 수 있다.
        val sharedApplicationRoot = repoRoot()
            .resolve("shared/src/commonMain/kotlin/com/worksoc/goaicoach/application")
        val platformBoundAdapter = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/application/diagnostic/LocalFileDiagnosticEventExternalSink.kt")
        val portableCandidates = sharedApplicationRoot
            .walkTopDown()
            .filter { file -> file.extension == "kt" }
            .toList()
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
        )

        assertTrue(
            "Platform-bound application adapter must stay explicit and existing in app-android:\n" +
                platformBoundAdapter.path,
            platformBoundAdapter.exists(),
        )

        val offenders = forbiddenReferenceOffenders(
            files = portableCandidates,
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "shared/.../application files must stay KMP-portable (no Android/JVM-only imports):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun sharedCommonMainAvoidsImplicitlyImportedJvmApis() {
        // 260824: iOS 타깃(-PenableIosTargets=true)이 49개 에러로 깨져 있던 걸 고치면서 추가.
        // engineOperationApplicationPoliciesStayPortable은 `import java.` 같은 **import 문**을
        // 본다. 그런데 `java.lang.*`은 JVM에서 자동 임포트라 `System.currentTimeMillis()`는
        // import 한 줄 없이 androidTarget에서 그냥 컴파일된다 — 그래서 텍스트 검사도 컴파일도
        // 아무 말이 없는 채로 commonMain 20개 파일에 번졌고, 기본 빌드에서 제외되는 iOS
        // 컴파일만 조용히 깨졌다. `kotlin.synchronized`도 같은 부류(자동 임포트 + JVM 전용).
        //
        // 이 테스트는 그 "import 없이 새는" 부류만 이름으로 직접 막는다. 대안(시간을 읽는 지점)은
        // application/time/AppClock.kt의 currentEpochMillis(), 경과 시간은
        // kotlin.time.TimeSource.Monotonic, 잠금은 application/concurrency/SharedLock.kt.
        val commonMainRoot = repoRoot()
            .resolve("shared/src/commonMain/kotlin/com/worksoc/goaicoach")
        val forbiddenBareReferences = listOf(
            "System.",
            "System::",
            "Thread.",
            "Runtime.",
            "synchronized(",
        )

        val offenders = commonMainRoot
            .walkTopDown()
            .filter { file -> file.extension == "kt" }
            .flatMap { file ->
                val scanLines = file.readLines()
                    .filterNot { raw ->
                        val trimmed = raw.trimStart()
                        trimmed.startsWith("import ") || trimmed.startsWith("package ") ||
                            trimmed.startsWith("//") || trimmed.startsWith("*") ||
                            trimmed.startsWith("/*")
                    }
                    .map { line -> stripStringsAndTrailingComment(line) }
                forbiddenBareReferences.mapNotNull { forbidden ->
                    val bareUse = Regex("(?<![\\w.])${Regex.escape(forbidden)}")
                    scanLines
                        .firstOrNull { line -> bareUse.containsMatchIn(line) }
                        ?.let { line ->
                            "${file.relativeTo(repoRoot()).path}: `$forbidden` -> ${line.trim()}"
                        }
                }
            }
            .toList()

        assertTrue(
            "shared/commonMain must not use JVM-only APIs that need no import " +
                "(they compile on androidTarget but break iOS):\n" + offenders.joinToString("\n"),
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

    @Test
    fun goCoachAppStaysWithinShrinkingUiShellBudget() {
        // Downward ratchet: GoCoachApp is being reduced from a workflow-owning
        // god file to a thin UI shell. These budgets only ever move down — when
        // a refactor lowers them, tighten the numbers here in the same change.
        //
        // History (2026-08-12): bumped 849->858 to add a Study destination and
        // wire screenState/selectedLanguage into SettingsScreen(...) (pure
        // routing/parameter-passing, no new state hooks). Then dropped 858->816
        // in the same day by extracting the last two inline workflow bodies this
        // file still owned: changeBoardSize/changeHandicapCount/changeKomi moved
        // into GameSettingsController (it already owned the sibling
        // changePlayerSetup/changeSearchTimeSettings/changeAutoPlayDelay — these
        // three just hadn't been moved yet), and the premium-deactivation
        // LaunchedEffect's diagnostic-event decision moved into the pure
        // application.premium.buildPremiumDeactivatedDiagnosticEvent. Net: lower
        // than the pre-Study-screen 849, despite the Study/Settings routing added
        // the same day.
        //
        // History (2026-08-13): bumped 816->819 to wire the "무르기" claim/
        // grandfathering fields (isUndoClaimed/claimUndo) into PremiumUiState(...)
        // and preserve isUndoClaimed across the three existing PremiumState
        // transitions (setPurchased/purchasePremium/activateAdGrant), which would
        // otherwise silently clear it. Reuses the existing premiumState state hook
        // (no new remember/mutableStateOf/LaunchedEffect) — stateHookBudget stays 47.
        //
        // History (2026-08-17): dropped 819->816, stateHookBudget 47->46. Un-hoisted
        // isDisplayMenuExpanded from this file into GoCoachContent.kt (it was only
        // ever read/written by GoCoachContent's own subtree). User explicitly
        // accepted the one behavior change this causes: the display-options menu's
        // open/closed state now resets when leaving and returning to the InGame
        // destination, instead of surviving the round trip (backlog item 3's own
        // "착수 전 사용자 결정 필요" gate).
        //
        // History (2026-08-18): bumped 816->833. The `scoreState` HolderBackedState
        // setter (the single choke point every scoreSnapshots write passes through,
        // from any of the ~8 application-layer sites that can touch it) now diffs
        // previous vs next snapshots and appends a runtimeScoreSnapshotsChangedLog
        // line when they differ. This closed a real bug: a B+157.5 flood-fill
        // misdisplay was traced to a site (engine-startup bootstrap) with no log
        // coverage at all, so from now on any such site is diagnosable directly from
        // RuntimeEventLog instead of code archaeology. No new remember/mutableStateOf/
        // LaunchedEffect — stateHookBudget stays 46.
        //
        // History (2026-08-19): bumped 833->843. Fixed the final-judgement popup
        // disappearing when the OS killed the backgrounded process: the cold-start
        // saved-session prompt effect now also restores an ended-game snapshot's
        // FinalScoreDisplayPlan and jumps to InGame so it's visible (skipping the
        // "resume?" prompt for it — see buildSavedSessionCheckPlan), and the
        // persistence effect now threads scoreState.finalScoreJudgement through so
        // such a snapshot gets saved instead of cleared. No new remember/
        // mutableStateOf/LaunchedEffect — stateHookBudget stays 46.
        //
        // History (2026-08-19): bumped 843->850. Fixed activateEndgameJudgementReview()
        // silently turning on the premium-only 형세보기(Eval) toggle for non-premium
        // users too — it's called from several auto endgame-detection paths (game end,
        // consecutive-pass detection) that predate the premium entitlement system, and
        // nothing ever reset it on a new game, so the button stayed visually "on" with
        // no working feature behind it. Now it checks FeatureAccessPolicy.resolve first
        // and no-ops without access. No new remember/mutableStateOf/LaunchedEffect —
        // stateHookBudget stays 46.
        //
        // History (2026-08-24): bumped 850->853 to add a GameHistory destination
        // (backlog item 7, offline engagement track) — one ScreenDestination entry, one
        // `when` branch delegating to GameHistoryScreen (which owns its own state/data
        // loading, same as StudyScreen), and one onGameHistoryClick wire-up into
        // GoCoachHomeScreen(...). Same shape as the 2026-08-12 Study destination bump.
        // No new remember/mutableStateOf/LaunchedEffect — stateHookBudget stays 46.
        // History (2026-08-24): 853 -> 849 (backlog item 14, Claim popup): the first-launch
        // reward screen became a dialog, so a 7-line conditional early return collapsed into
        // a single AttendanceRewardClaimDialog(context) call that owns its own stores.
        //
        // History (2026-08-24): bumped 853->854 for consumable wiring (backlog item 15) —
        // one buildConsumableUiState line, one OneShotAnalysisAutoClear line, and a second
        // CompositionLocal on the existing provider. All state (inventory, one-shot tracking)
        // and the auto-clear LaunchedEffect live in ui/ConsumableUiState.kt, so the shell only
        // holds wiring. Net across items 14 and 15 is +1 line over the previous 853.
        // No new remember/mutableStateOf/LaunchedEffect — stateHookBudget stays 46.
        //
        // History (2026-08-29): dropped 854->845, stateHookBudget 46->45. The premium
        // expiry/deactivation LaunchedEffect moved into ui/PremiumUiState.kt as
        // PremiumExpiryAutoDisableEffect, leaving a single call line in the shell. It had to
        // grow (it now waits out the ad grant with delay() and re-checks on toggle changes,
        // and must skip toggles a one-shot ticket turned on), so hoisting it was the only way
        // to take that fix without regrowing the shell — same move as buildPremiumUiState and
        // OneShotAnalysisAutoClear before it.
        //
        // History (2026-08-29, backlog #10): bumped 845->851, stateHookBudget 45->46. Wiring the
        // bot collection store for the character picker: one buildBotCharacterUiState line plus a
        // third CompositionLocal, which pushed the provider call onto its own lines. The state and
        // the picker dialog itself live in ui/BotCharacterUiState.kt, so the shell only holds
        // wiring — same split as buildPremiumUiState and buildConsumableUiState.
        // History (2026-08-29, backlog #18): bumped 851->855. The character purchase perk needs the
        // opponent and the collection at buildPremiumUiState, so the bot-collection wiring moved above
        // the premium wiring and one argument was added. Folding the perk into PremiumUiState.resolve
        // is what keeps every in-game gating call site untouched (feature-access-principles 8.3-1).
        // 856 rather than 855 because the perk value is shared with PremiumExpiryAutoDisableEffect —
        // computing it twice would let the gating and the auto-disable disagree.
        // History (2026-08-30, backlog #24): bumped 856->861 for the My Page destination — one enum
        // value, one home-card callback, one routing branch. Same shape as the Study destination that
        // bumped 849->858 in 2026-08-12; the screen body lives in MyPageScreen.kt, not here.
        // No new remember/mutableStateOf/LaunchedEffect — stateHookBudget stays 46.
        // History (2026-08-30): 861->865 (net +4 after #34 removed 2). 실제 부팅된
        // 백엔드(EngineMode)를 GoCoachApp -> GoCoachScreen -> 초기 EngineProfile까지
        // 흘려보내는 순수 파라미터 배선이다. 이전에는 여기서 맨 `EngineProfile()`을 넘겨
        // 디버그 리포트의 engineProfile이 영원히 데이터 클래스 기본값을 찍었고, 진짜
        // KataGo가 도는 빌드가 `stub/Stub/Beginner`로 보여 스텁 엔진으로 오독됐다.
        // 새 상태 훅은 없다 — stateHookBudget은 46 그대로.
        // History (2026-08-30): 865->866. 보드 크기 모드(#38)의 새 uxOptions 필드를
        // 오토세이브 요청에 넘기는 한 줄이다. 새 상태 훅 없음.
        val lineBudget = 866
        val stateHookBudget = 46

        val goCoachApp = repoRoot()
            .resolve("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        val lines = goCoachApp.readLines()
        val stateHookRegex = Regex("\\b(remember|mutableStateOf|LaunchedEffect)\\b")
        val stateHookCount = lines.count { line -> stateHookRegex.containsMatchIn(line) }

        val offenders = mutableListOf<String>()
        if (lines.size > lineBudget) {
            offenders += "GoCoachApp.kt grew to ${lines.size} lines (budget $lineBudget): " +
                "hoist wiring into a screen presenter, do not regrow the shell."
        }
        if (stateHookCount > stateHookBudget) {
            offenders += "GoCoachApp.kt holds $stateHookCount Compose state hooks (budget $stateHookBudget): " +
                "move state ownership out of the composable."
        }

        assertTrue(
            "GoCoachApp must keep shrinking toward a thin UI shell:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
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

    /**
     * `application/` is migrating file-by-file from app-android to :shared (see
     * docs/refactoring/GAMESESSION_SHARED_MIGRATION_KICKOFF_PLAN_260816_1808.md). Tests that read
     * one specific file by hardcoded path must resolve it wherever it currently lives, or they
     * break with FileNotFoundException the moment that one file crosses over — even though the
     * policy the test enforces hasn't changed. relativePath is the part after ".../application/",
     * e.g. "engine/EngineSession.kt".
     */
    private fun applicationFile(relativePath: String): File {
        val repoRoot = repoRoot()
        val sharedPath = repoRoot.resolve("shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/$relativePath")
        if (sharedPath.exists()) return sharedPath
        return repoRoot.resolve("app-android/src/main/java/com/worksoc/goaicoach/application/$relativePath")
    }
}
