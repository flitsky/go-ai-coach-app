package com.worksoc.goaicoach.architecture

import com.worksoc.goaicoach.architecture.ContractSymbols.APPLICATION_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_ADAPTER
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_ANDROID_RUNTIME_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_CORE_API
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.LOCAL_CONFIGURE_SYNC_AND_ESTIMATE_GRAPH_SCORE
import com.worksoc.goaicoach.architecture.ContractSymbols.LOCAL_ESTIMATE_SCORE_FOR_STATE
import com.worksoc.goaicoach.architecture.ContractSymbols.LOCAL_SYNC_AND_ESTIMATE_GRAPH_SCORE
import com.worksoc.goaicoach.architecture.ContractSymbols.MAIN_ACTIVITY
import com.worksoc.goaicoach.architecture.ContractSymbols.PERSISTENCE_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.PLATFORM_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.PRESENTATION_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.UI_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.importOf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeringContractTest {
    @Test
    fun uiAndPresentationDoNotImportRawEngineCoreApi() {
        val sourceRoot = RepoPaths.appAndroid()
        val checkedDirs = listOf(
            sourceRoot.resolve("ui"),
            sourceRoot.resolve("presentation"),
        )
        val forbiddenImports = listOf(
            importOf(ENGINE_ADAPTER),
            importOf(ENGINE_CORE_API),
            importOf(ENGINE_ANDROID_RUNTIME_PACKAGE),
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

    /**
     * ⚠️ 260923: 이 가드는 **260816부터 아무것도 검사하지 않고 있었다.** `application/`과 `match/`가
     * :shared로 이사했는데 스캔 경로가 `app-android/.../{application,match}`에 남아 있었고,
     * [ktFilesIn]이 없는 디렉터리를 조용히 빈 목록으로 돌려줘 **무조건 통과**했다.
     * 스캔 대상을 실재하는 트리로 옮긴다 — 본보기는 같은 파일의
     * [engineOperationApplicationPoliciesStayPortable]이다(그 테스트만 함정을 알아챘었다).
     *
     * app-android에 남은 `application/diagnostic/LocalFileDiagnosticEventExternalSink.kt`도 함께
     * 본다 — 그 하나는 일부러 플랫폼에 묶인 어댑터지만, 그렇다고 엔진 런타임 구현체를 직접
     * 참조해도 되는 것은 아니다.
     */
    @Test
    fun applicationAndMatchDoNotDependOnCompatibilityEngineAdapterOrAndroidRuntime() {
        val checkedDirs = listOf(
            RepoPaths.applicationPath(),
            RepoPaths.matchPath(),
            RepoPaths.appAndroid("application"),
        )
        val forbiddenImports = listOf(
            importOf(ENGINE_ADAPTER),
            importOf(ENGINE_ANDROID_RUNTIME_PACKAGE),
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
        // Android/Firebase/SharedPreferences-backed adapters live in platform/ or persistence/
        // (refactor backlog #25 moved them out of ui/).
        // ⚠️ 260923: 셋 다 :shared로 건너간 뒤(260816)에도 스캔 경로가 app-android에 남아
        // **0개 파일을 검사하며 무조건 통과**하고 있었다. 실재하는 트리를 가리키게 고친다.
        val checkedDirs = listOf(
            RepoPaths.applicationPath("auth"),
            RepoPaths.applicationPath("premium"),
            RepoPaths.applicationPath("device"),
        )
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
            importOf(UI_PACKAGE),
            importOf(PLATFORM_PACKAGE),
            importOf(PERSISTENCE_PACKAGE),
            importOf(ENGINE_PACKAGE),
        )

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(*checkedDirs.toTypedArray()),
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "application/auth, application/premium, and application/device must stay platform-free " +
                "ports; put Android/Firebase-specific adapters in platform/ or persistence/ instead:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * `platform/`은 4계층 SDK 어댑터(Billing·UMP·AdMob·Firebase Auth·Credential Manager·Vibrator 등)만
     * 산다(refactor backlog #25). 이 파일들을 `ui/`에서 옮긴 근거가 **"Compose도 ui 심볼도 모른다"** 였으니,
     * 그 근거를 계약으로 굳힌다 — 가드가 없으면 옮긴 순간 이 패키지는 사각지대가 된다.
     *
     *  - Compose·ui·presentation: 어댑터가 화면을 알면 `ui/`에서 꺼낸 의미가 사라진다.
     *    (Compose `AndroidView`로 광고를 그리는 `BannerAdView`는 그래서 `ui/`에 남았다.)
     *  - 엔진(런타임 구현체·조립 인접 engine 패키지·EngineCoreApi): SDK 어댑터가 알 이유가 없다.
     *  - **루트(조립) 패키지**: 어댑터는 조립되는 쪽이지 조립을 부르는 쪽이 아니다. 단, 생성 코드인
     *    `BuildConfig`·`R`은 루트 패키지에 생기므로 **단순 이름으로만** 예외를 둔다 — FQN으로 등록하면
     *    소스 색인이 생성 코드를 못 봐 실존 검사가 영원히 빨개진다.
     *
     * ⚠️ 스캔 대상이 비면 [ktFilesIn]이 터진다 — `platform/`이 이사하거나 사라지면 여기서 드러난다.
     */
    @Test
    fun platformAdaptersDoNotImportComposeUiOrComposition() {
        val files = ktFilesIn(RepoPaths.appAndroid("platform"))
        val forbiddenImports = listOf(
            "import androidx.compose.",
            importOf(UI_PACKAGE),
            importOf(PRESENTATION_PACKAGE),
            importOf(ENGINE_PACKAGE),
            importOf(ENGINE_CORE_API),
            importOf(ENGINE_ADAPTER),
        )

        val offenders = forbiddenReferenceOffenders(files = files, forbiddenImports = forbiddenImports) +
            rootPackageReferenceOffenders(files = files, allowedSimpleNames = GENERATED_ROOT_SYMBOLS)

        assertTrue(
            "platform/ adapters must not know Compose, ui, presentation, the engine, or the composition " +
                "root — keep them SDK-only (refactor backlog #25):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * [rootPackageReferenceOffenders]의 자기검증 — 루트 매처가 **허공을 보지 않는지**.
     * 파일에 위반이 없으면 위 가드는 초록이므로, 매처가 고장나 무엇과도 매치하지 않는 상태와 구분되지
     * 않는다. 그래서 등록부 앵커에서 만든 import 줄로 양성·음성을 나란히 확인한다.
     */
    @Test
    fun rootPackageMatcherFlagsRootImportsButNotGeneratedSymbols() {
        val tempDir = java.nio.file.Files.createTempDirectory("root-matcher").toFile()
        try {
            val root = MAIN_ACTIVITY.substringBeforeLast('.')
            val offending = File(tempDir, "Offending.kt").apply {
                writeText("package x\n\nimport $MAIN_ACTIVITY\nimport $root.*\n")
            }
            val generated = File(tempDir, "Generated.kt").apply {
                writeText(
                    "package x\n\n" + GENERATED_ROOT_SYMBOLS.joinToString("\n") { "import $root.$it" } + "\n",
                )
            }

            val offenders = rootPackageReferenceOffenders(listOf(offending), GENERATED_ROOT_SYMBOLS)
            assertEquals("루트 import(단일 조각·와일드카드)를 둘 다 잡아야 한다: $offenders", 2, offenders.size)
            assertEquals(
                "생성 코드(BuildConfig·R)는 루트 import 예외여야 한다",
                emptyList<String>(),
                rootPackageReferenceOffenders(listOf(generated), GENERATED_ROOT_SYMBOLS),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * 컨트롤러 배선 목록([RepoPaths.controllerWiringFiles])이 **루트의 실제 배선 파일과 정확히 같다**
     * (refactor backlog #26).
     *
     * `goCoachAppDoesNotOwn…` 가드 일곱 개가 이 목록 하나를 공유한다. 여섯 번째 배선 파일이 생겼는데
     * 목록에 안 오르면, 그 파일로 옮겨 간 워크플로 본문은 **일곱 가드 모두에서 조용히 빠진다** —
     * 빨개지는 것이 없으므로 아무도 모른다. 그래서 목록을 디렉터리와 대조한다.
     *
     * 그리고 조립 코드가 `ui/`로 **다시 자라지 않는지** 본다. `*Wiring.kt`·`*Glue.kt`가 `ui/` 아래에
     * 생기면 #26이 되돌려진 것이다.
     */
    @Test
    fun controllerWiringListMatchesTheRootAndCompositionDoesNotRegrowInUi() {
        val root = RepoPaths.compositionFile("")
        val onDisk = root.listFiles().orEmpty()
            .filter { file -> file.isFile && file.name.endsWith("ControllerWiring.kt") }
            .map { file -> file.name }
            .toSet()
        val listed = RepoPaths.controllerWiringFiles.map { file -> file.name }.toSet()
        assertTrue("루트에서 배선 파일을 하나도 못 찾았다 — 경로가 낡았다: ${root.path}", onDisk.isNotEmpty())
        assertEquals(
            "RepoPaths.controllerWiringFiles가 루트의 *ControllerWiring.kt와 다르다 — 빠진 파일은 " +
                "goCoachAppDoesNotOwn… 가드 일곱 개에서 조용히 빠진다(#26).",
            onDisk.sorted(),
            listed.sorted(),
        )

        val regrown = ktFilesIn(RepoPaths.appAndroid("ui"))
            .filter { file -> file.name.endsWith("Wiring.kt") || file.name.endsWith("Glue.kt") }
            .map { file -> file.relativeTo(RepoPaths.root).path }
        assertEquals(
            "조립 코드(*Wiring.kt·*Glue.kt)가 ui/에 다시 생겼다 — 루트(com.worksoc.goaicoach)에 둔다(#26).",
            emptyList<String>(),
            regrown,
        )
    }

    /**
     * 루트 패키지(조립 전용)의 파일 목록을 **정확히** 못박는다(refactor backlog #26).
     *
     * docs/ARCHITECTURE.md는 루트를 "전 계층 참조 허용"인 예외로 두는 대신 **목록을 짧게, 그 길이를
     * 지표로** 삼으라고 한다. 루트의 파일은 계층 가드가 보지 않으므로, 여기에 규칙이 섞여 들어오면
     * **영구히 감시 밖**이 된다. 그래서 루트에 파일을 더하려면 이 목록을 고쳐야 한다 — 그 자체가
     * "의식적인 결정"의 기록이 된다.
     *
     * ⚠️ 260924에 6→13이 됐다. 새 로직이 들어온 것이 아니라 `ui/`에 숨어 있던 배선 7개가 제 자리로
     * 온 것이다(#26). 지표를 읽을 때 그 구분을 볼 것.
     */
    @Test
    fun compositionRootFileSetIsAConsciousDecision() {
        val root = RepoPaths.compositionFile("")
        val actual = root.listFiles().orEmpty()
            .filter { file -> file.isFile && file.extension == "kt" }
            .map { file -> file.name }
            .sorted()
        val expected = listOf(
            // 매니페스트가 이름으로 부른다 — 옮기지 않는다(런처 바로가기가 컴포넌트 이름을 저장한다).
            "GoAiCoachApplication.kt",
            "MainActivity.kt",
            // 프로세스 이벤트 중계와, 판정을 shared 정책에 위임하는 코디네이터.
            "AppForegroundEvents.kt",
            "AttendanceCheckInCoordinator.kt",
            "DeveloperModeResetCoordinator.kt",
            "ReleaseResetCoordinator.kt",
            // #26: ui/에서 옮겨 온 배선.
            "GameExitRecording.kt",
            "GameLifecycleControllerWiring.kt",
            "GoCoachControllerWiring.kt",
            "PremiumPurchaseGlue.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
        ).sorted()

        assertEquals(
            "루트 패키지(조립 전용)의 파일 목록이 바뀌었다. 조립이라면 여기 목록에 더하고 이유를 적고, " +
                "규칙(if/when으로 무엇을 판정하는 코드)이라면 shared application으로 보내라 — 루트는 계층 " +
                "가드가 보지 않는다(docs/ARCHITECTURE.md, #26).",
            expected,
            actual,
        )
    }

    /**
     * `ui/`에 SDK 클라이언트가 다시 자라지 않는다(refactor backlog #25의 유지 장치).
     *
     * #25는 SDK 어댑터를 `platform/`으로 뺐다 — 그래서 지금 `import ui.X`는 "화면"을 뜻한다.
     * 이 가드가 없으면 다음 어댑터가 습관대로 `ui/`에 생기고, 그 뜻은 시간이 지나며 거짓이 된다.
     *
     * 허용 목록은 **(파일, 금지 접두사) 짝**으로 둔다. 길이가 곧 부채의 지표다(docs/ARCHITECTURE.md의
     * 원칙) — 누수가 고쳐지면 [staleAllowances]가 목록에서 지우라고 빨개진다.
     *  - `BannerAdView.kt` × `gms.ads` — Compose `AndroidView`로 광고 뷰를 그린다. 화면이라 정당하다.
     *  - `AccountDeletionFlow.kt`·`OnboardingScreen.kt`·`SettingsScreen.kt` × `firebase.` — **알려진
     *    누수.** Firebase 예외 타입(`FirebaseAuthRecentLoginRequiredException` 등)으로 분기한다. 고치려면
     *    `AndroidAuthClient`가 타입 있는 실패로 매핑해야 하는데 동작 변경이라 순수 이동 밖이다.
     *
     * `Toast`·`ClipboardManager`는 금지하지 않는다 — ui 곳곳(DiagnosticLogDialog 등)이 화면 동작으로 쓴다.
     */
    @Test
    fun uiDoesNotRegrowSdkClients() {
        val forbiddenImports = listOf(
            "import com.android.billingclient.",
            "import com.google.android.ump.",
            "import androidx.credentials.",
            "import com.google.android.libraries.identity.",
            "import com.google.android.gms.",
            "import com.google.firebase.",
            "import android.os.Vibrator",
            "import android.os.VibratorManager",
            "import android.os.VibrationEffect",
        )
        val allowances = setOf(
            "BannerAdView.kt" to "import com.google.android.gms.",
            "AccountDeletionFlow.kt" to "import com.google.firebase.",
            "OnboardingScreen.kt" to "import com.google.firebase.",
            "SettingsScreen.kt" to "import com.google.firebase.",
        )
        check(allowances.all { (_, forbidden) -> forbidden in forbiddenImports }) {
            "허용 목록이 금지 목록에 없는 접두사를 든다 — 허공을 허용하고 있다."
        }

        val hits = ktFilesIn(RepoPaths.appAndroid("ui")).flatMap { file ->
            val lines = file.readLines()
            forbiddenImports.flatMap { forbidden ->
                detectForbiddenReference(lines, forbidden).map { reason -> Triple(file, forbidden, reason) }
            }
        }
        val offenders = hits
            .filterNot { (file, forbidden, _) -> (file.name to forbidden) in allowances }
            .map { (file, _, reason) -> "${file.relativeTo(RepoPaths.root).path}: $reason" }
            .distinct()
        val staleAllowances = allowances
            .filterNot { allowance -> hits.any { (file, forbidden, _) -> (file.name to forbidden) == allowance } }

        assertEquals(
            "ui/에 SDK 클라이언트 import가 생겼다 — 어댑터는 platform/에 두고 포트나 그 어댑터를 부른다(#25):\n" +
                offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
        assertEquals(
            "허용 목록의 누수가 고쳐졌다 — 목록에서 지워 길이를 줄여라(길이가 부채의 지표다).",
            emptyList<Pair<String, String>>(),
            staleAllowances,
        )
    }

    /** ⚠️ 260923: `match/`가 :shared로 건너간 뒤 0개 파일을 검사하고 있었다(위 가드와 같은 사망). */
    @Test
    fun matchPoliciesDoNotImportRawEngineCoreApi() {
        val matchRoot = RepoPaths.matchPath()
        val forbiddenImports = listOf(
            importOf(ENGINE_CORE_API),
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
        val engineSession = RepoPaths.applicationPath("engine/EngineSession.kt")
        val sessionText = codeOnly(engineSession.readContractSource())
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
        val repoRoot = RepoPaths.root
        val benchmarkApplication = RepoPaths.applicationPath("engine/EngineDeviceBenchmarkApplication.kt")
        val benchmarkModels = RepoPaths.applicationPath("engine/EngineBenchmarkModels.kt")
        val benchmarkDisplay = RepoPaths.applicationPath("engine/EngineBenchmarkDisplayApplication.kt")
        val benchmarkDelegate = RepoPaths.applicationPath("engine/LocalEngineBenchmarkDelegate.kt")
        val applicationText = codeOnly(benchmarkApplication.readContractSource())
        val delegateText = codeOnly(benchmarkDelegate.readContractSource())

        val offenders = mutableListOf<String>()
        if (importOf(ENGINE_CORE_API) in applicationText) {
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
        if (benchmarkApplication.readContractSourceLines().size > 220) {
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val controller = RepoPaths.applicationPath("savedgame/SavedSessionController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val controller = RepoPaths.applicationPath("startgame/NewGameController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val controller = RepoPaths.applicationPath("autoai/AutoAiTurnController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val controller = RepoPaths.applicationPath("humanmove/HumanMoveController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val controller = RepoPaths.applicationPath("topmoves/TopMovesController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val controller = RepoPaths.applicationPath("score/ScoreEstimateController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val controller = RepoPaths.applicationPath("debugreport/DebugReportController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        // 260804: 컨트롤러 배선이 GoCoachControllerWiring.kt 하나에서 도메인별 4개 파일로
        // 분리됐다(Stage C-2) — 이 테스트들의 의도("GoCoachApp이 아니라 배선 계층이 이 로직을
        // 소유한다")는 그대로이므로 5개 파일을 전부 합쳐서 확인한다.
        val wiringText = RepoPaths.controllerWiringFiles.joinToString("\n") { file ->
            codeOnly(file.readContractSource())
        }
        val text = codeOnly(goCoachApp.readContractSource()) + "\n" + wiringText
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
        val controller = RepoPaths.applicationPath("analysis/PositionCacheOptimizationController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val controller = RepoPaths.applicationPath("engine/operation/EngineOperationLifecycleController.kt")
        val text = codeOnly(controller.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
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
        val holder = RepoPaths.applicationPath("session/GameSessionStateHolder.kt")
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
        // ⚠️ 260923: `application/score`가 :shared로 건너간 뒤 0개 파일을 검사하고 있었다.
        val scoreRoot = RepoPaths.applicationPath("score")
        val forbiddenImports = listOf(
            importOf(LOCAL_SYNC_AND_ESTIMATE_GRAPH_SCORE),
            importOf(LOCAL_CONFIGURE_SYNC_AND_ESTIMATE_GRAPH_SCORE),
            importOf(LOCAL_ESTIMATE_SCORE_FOR_STATE),
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
        val repoRoot = RepoPaths.root
        val common = RepoPaths.applicationPath("score/ScoreSyncRunnerApplication.kt")
        val expectedSplitFiles = listOf(
            RepoPaths.applicationPath("score/ScoringRuleScoreSyncRunnerApplication.kt"),
            RepoPaths.applicationPath("score/PostUndoScoreSyncRunnerApplication.kt"),
            RepoPaths.applicationPath("score/RestoredGameScoreSyncRunnerApplication.kt"),
        )
        val offenders = mutableListOf<String>()

        expectedSplitFiles
            .filterNot { file -> file.exists() }
            .forEach { file -> offenders += "${file.relativeTo(repoRoot).path}: missing score sync split file" }

        val commonText = codeOnly(common.readContractSource())
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
        if (common.readContractSourceLines().size > 90) {
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
        val repoRoot = RepoPaths.root
        val middlewareRoot = RepoPaths.appAndroid("middleware")
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
            importOf(APPLICATION_PACKAGE),
            importOf(UI_PACKAGE),
            importOf(PLATFORM_PACKAGE),
            importOf(PERSISTENCE_PACKAGE),
            importOf(ENGINE_PACKAGE),
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
            val text = codeOnly(file.readContractSource())
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
        val repoRoot = RepoPaths.root
        val movedFiles = listOf(
            RepoPaths.engineAndroid("HttpRemotePositionAnalysisTransport.kt"),
            RepoPaths.engineAndroid("RemoteEngineCoreApiAdapter.kt"),
        )
        val staleAppAndroidPaths = listOf(
            RepoPaths.appAndroid("middleware/HttpRemotePositionAnalysisTransport.kt"),
            RepoPaths.appAndroid("middleware/RemoteEngineCoreApiAdapter.kt"),
        )

        val missing = movedFiles.filterNot { file -> file.exists() }
        val stillInAppAndroid = staleAppAndroidPaths.filter { file -> file.exists() }

        assertTrue(
            "EngineCoreApi implementations must live in engine-android:\n" +
                "missing:\n${missing.joinToString("\n") { it.relativeTo(repoRoot).path }}\n" +
                "still present in app-android (should have moved):\n" +
                stillInAppAndroid.joinToString("\n") { it.relativeTo(repoRoot).path },
            missing.isEmpty() && stillInAppAndroid.isEmpty(),
        )

        val transportText = codeOnly(RepoPaths.engineAndroid("HttpRemotePositionAnalysisTransport.kt").readContractSource())
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
        val repoRoot = RepoPaths.root
        val engineAndroidRoot = RepoPaths.engineAndroid()
        val concreteAdapters = mapOf(
            engineAndroidRoot.resolve("KataGoProcessEngineAdapter.kt") to "internal class KataGoProcessEngineAdapter(",
            engineAndroidRoot.resolve("StubEngineAdapter.kt") to "internal class StubEngineAdapter",
        )

        val notInternal = concreteAdapters.filterNot { (file, marker) -> codeOnly(file.readContractSource()).contains(marker) }
        assertTrue(
            "EngineCoreApi concrete adapters must stay internal to engine-android:\n" +
                notInternal.keys.joinToString("\n") { file -> file.relativeTo(repoRoot).path },
            notInternal.isEmpty(),
        )

        val factoryText = codeOnly(engineAndroidRoot.resolve("EngineCoreApiFactory.kt").readContractSource())
        assertTrue(
            "engine-android must expose EngineCoreApiFactory as the only public construction seam.",
            factoryText.contains("object EngineCoreApiFactory") && !factoryText.trimStart().startsWith("internal"),
        )

        val bootstrap = RepoPaths.appAndroid("engine/EngineBootstrap.kt")
        val bootstrapText = codeOnly(bootstrap.readContractSource())
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
        val sharedApplicationRoot = RepoPaths.shared("application")
        val platformBoundAdapter = RepoPaths.appAndroid("application/diagnostic/LocalFileDiagnosticEventExternalSink.kt")
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
        val commonMainRoot = RepoPaths.shared()
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
                            "${file.relativeTo(RepoPaths.root).path}: `$forbidden` -> ${line.trim()}"
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
        val sharedRoot = RepoPaths.shared("shared")
        val candidates = listOf(
            sharedRoot.resolve("diagnostic/DiagnosticEventModel.kt"),
            sharedRoot.resolve("engine/EngineOperationPolicy.kt"),
            sharedRoot.resolve("policy/MoveValueDisplay.kt"),
        )
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import org.json.",
            importOf(APPLICATION_PACKAGE),
            importOf(UI_PACKAGE),
            importOf(PLATFORM_PACKAGE),
            importOf(PERSISTENCE_PACKAGE),
            importOf(ENGINE_PACKAGE),
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
                    import com.worksoc.goaicoach.shared.enginecontract.*
                    fun build(api: EngineCoreApi) = api
                    """.trimIndent(),
                )
            }
            // b) Fully-qualified reference inline, with no import at all.
            val inlineOffender = File(tempDir, "InlineOffender.kt").apply {
                writeText(
                    """
                    package sample
                    fun build(api: com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi) = api
                    """.trimIndent(),
                )
            }
            // c) Aliased import still resolves to the forbidden type.
            val aliasedOffender = File(tempDir, "AliasedOffender.kt").apply {
                writeText(
                    """
                    package sample
                    import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi as Engine
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
                    fun describe() = "see com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi for details"
                    """.trimIndent(),
                )
            }
            // f) Negative: a single-line block comment mentions the path.
            val blockCommentMention = File(tempDir, "BlockCommentMention.kt").apply {
                writeText(
                    """
                    package sample
                    fun build() = 1 /* com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi */
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
                forbiddenImports = listOf("import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi"),
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
        // is what keeps every in-game gating call site untouched (FEATURE_ACCESS_PRINCIPLES.md 8.3).
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
        // History (2026-08-31): bumped 866->870 for the first-run Landing screen
        // (backlog #51). Four lines: LandingGate(...) wraps GoCoachScreen inside the
        // existing Surface. The landing deliberately sits OUTSIDE GoCoachScreen —
        // inside it, the preferences autosave overwrote the answers on first
        // composition — so this file gains no state hooks and no destination entry;
        // the screen's own state lives in LandingScreen.kt. stateHookBudget stays 46.
        // History (2026-08-31, 2): bumped 870->874. The attendance claim dialog moved
        // inside the CompositionLocalProvider block — outside it, LocalConsumableUiState
        // resolved to its default, so the refresh that syncs ticket counts after a grant
        // did nothing and My Page showed "day one stamped, zero tickets". The +4 is the
        // comment recording that, so the call does not drift back out. No new state hooks.
        // #39: +1 — 오토세이브 요청에 `isPlayMagnifierEnabled` 한 줄. 착수 돋보기 토글이
        // 영구 저장돼야 하고, 이 셸이 오토세이브 요청을 조립하는 유일한 지점이다. 상태 훅 증가 없음.
        // #63: +5 — 정식 릴리즈 초기화 안내 다이얼로그와, 그것이 떠 있는 동안 출석 Claim 팝업을
        // 미루는 게이트(+ 사유 주석 3줄). ⚠️ 선언 순서로 해결하려다 실패했다 — Compose 다이얼로그는
        // 각자 별도 윈도우라 나중에 선언해도 위로 오지 않아, 안내가 출석 팝업 뒤에 가렸다
        // (2026-09-01 실기 확인). 그 실패 기록이 주석 3줄의 값이다. 다이얼로그 자신의 상태는
        // ReleaseResetNoticeDialog.kt가 들고 있어 **상태 훅은 늘지 않는다**.
        // #85: +2 — 돋보기 창 크기·확대 배율 두 값을 오토세이브 요청에 통과시키는 줄.
        // ⚠️ **이 셸이 오토세이브 요청을 조립하는 유일한 지점**이라 새로 저장되는 UX 옵션은
        // 여기를 지날 수밖에 없다(#39가 `isPlayMagnifierEnabled`로 같은 이유로 +1했다).
        // 상태 훅 증가 없음 — 값은 기존 `uxOptions`에 얹혀 온다.
        // #96: +3 — 홈으로 나가기 직전에 끝난 대국을 기록하는 한 줄 + 사유 주석 두 줄.
        // ⚠️ **주석이 이 예산을 쓰는 값을 한다** — 이 자리의 결함은 `refreshNewGamePreview()`가
        // 판을 갈아엎기 **전에** 기록해야 한다는 순서 의존이고, 그 사유가 코드에 없으면 다음
        // 사람이 두 줄의 순서를 아무렇지 않게 바꾼다(그래도 테스트는 초록이다).
        // ── 2026-09-05: 지표를 바꿨다(사용자 결정). 사유는 아래 셋이다. ──────────────────
        //
        // ⚠️ **① 예산이 재던 것이 예산이 막으려는 것과 달랐다.** 885줄 중 **126줄(14%)이 import**,
        //    43줄이 주석이었다 — 즉 예산의 5분의 1이 복잡도가 아닌 것에 쓰이고 있었다. 더 나쁜 것은
        //    **주석이 세어졌다는 점**이다: *"왜 이 순서여야 하는가"* 를 적으면 예산이 깎였다.
        //    #96이 실제로 그 대가를 치렀다(+3 중 2줄이 사유 주석). **좋은 일에 벌점이 붙는 지표는
        //    오래 가지 못한다.** 그래서 이제 **import·주석·빈 줄을 빼고** 센다.
        //
        // ⚠️ **② 상태 훅 수도 같은 오염이 있었다.** `import ...remember` 같은 줄과 KDoc 한 줄이
        //    훅으로 세어져 **46 중 4가 유령**이었다(실제 **42**). 예산이 4줄어치 헐거웠다는 뜻이다.
        //    ⚠️ 처음엔 43으로 잡았다가 **변이로 걸렸다** — 훅을 하나 더해도 통과했다. 유령을
        //    걷어낸 수를 다시 재서 42로 맞췄다. **여유 0이어야 이 지표가 일을 한다.**
        //
        // ⚠️ **③ 그래서 둘의 역할을 나눴다.** 훅 수가 **1차 지표**이고 여유가 **0**이다 —
        //    *"이 셸이 얼마나 많은 것을 알고 있는가"* 를 직접 재기 때문이다. 줄 수는 **뒷받침**이고
        //    777로 여유를 준다. 조이는 힘은 뜻이 정확한 쪽에 둔다.
        //
        // ⚠️ **원칙은 파일이 아니라 역할에 있다** — *"조립만 하는 셸은 상태를 소유하지 않는다"*.
        //    줄 수는 그 위반의 신호일 뿐이다. 그래서 숫자를 올릴 때는 **무엇을 조립하느라 늘었는지**
        //    를 여기 적는다. 사유 없이 올리는 순간 이 그물은 뜻을 잃는다.
        val lineBudget = 777
        val stateHookBudget = 42

        val goCoachApp = RepoPaths.goCoachApp
        val allLines = goCoachApp.readContractSourceLines()
        val lines = codeLinesOf(allLines)
        val stateHookRegex = Regex("\\b(remember|mutableStateOf|LaunchedEffect)\\b")
        val stateHookCount = lines.count { line -> stateHookRegex.containsMatchIn(line) }

        val offenders = mutableListOf<String>()
        if (lines.size > lineBudget) {
            offenders += "GoCoachApp.kt grew to ${lines.size} code lines (budget $lineBudget, " +
                "imports/comments/blanks excluded; ${allLines.size} raw): " +
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

    /**
     * 예산은 이제 셸 하나가 아니라 **역할 단위**로 붙는다(백로그 #102).
     *
     * ⚠️ **왜 늘렸나**: 예산이 `GoCoachApp.kt` 하나만 지키는 동안 `SettingsScreen.kt`가
     * **1003줄로 더 크게** 자라 있었다. 지표가 지키는 것만 지켜지고 나머지는 무방비였다는 뜻이다.
     *
     * ⚠️ **여기 있는 파일들은 "조립하는 쪽"이다.** 원칙은 파일이 아니라 역할에 있다 —
     * *"조립만 하는 셸은 상태를 소유하지 않는다."* 그래서 조이는 힘은 **훅 수**에 두고
     * (여유 0), 줄 수는 뒷받침으로 여유를 준다. 숫자를 올릴 때는 **무엇 때문에 늘었는지**를
     * 반드시 여기 적을 것 — 사유 없이 올리면 이 그물은 뜻을 잃는다.
     */
    @Test
    fun settingsScreenStaysAShellAndTheDeveloperSectionStaysItsOwnRole() {
        // `SettingsScreen.kt` — 개발자 섹션을 떼어낸 뒤 코드 359줄 / 훅 13.
        // ⚠️ 훅 **여유 0**이 이 항목의 핵심이다. 개발자 섹션의 상태를 이 화면으로 되돌리려는
        // 순간 여기서 걸린다 — #102가 막으려는 것이 정확히 그것이다.
        //
        // `DeveloperTestSection.kt` — 코드 313줄 / 훅 4.
        // ⚠️ 새 개발자 컨트롤은 대부분 저장소를 부르는 버튼이라 훅이 필요 없다. 훅이 필요하다면
        // **정말 이 섹션이 상태를 가져야 하는지** 를 먼저 물을 것.
        val budgets = listOf(
            Triple("ui/SettingsScreen.kt", 400, 13),
            Triple("ui/DeveloperTestSection.kt", 350, 4),
        )
        val stateHookRegex = Regex("\\b(remember|mutableStateOf|LaunchedEffect)\\b")
        val offenders = mutableListOf<String>()

        budgets.forEach { (path, lineBudget, stateHookBudget) ->
            val file = RepoPaths.appAndroid(path)
            val allLines = file.readContractSourceLines()
            val lines = codeLinesOf(allLines)
            val stateHookCount = lines.count { line -> stateHookRegex.containsMatchIn(line) }

            if (lines.size > lineBudget) {
                offenders += "$path grew to ${lines.size} code lines (budget $lineBudget, " +
                    "imports/comments/blanks excluded; ${allLines.size} raw)."
            }
            if (stateHookCount > stateHookBudget) {
                offenders += "$path holds $stateHookCount Compose state hooks (budget " +
                    "$stateHookBudget): move state ownership to the role that uses it."
            }
        }

        assertTrue(
            "Settings must stay a shell and the developer section must stay its own role:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * [dirs] 아래의 `.kt` 파일 전부.
     *
     * ⚠️ **빈 결과는 실패다.** 이 헬퍼가 없는 디렉터리를 조용히 빈 목록으로 돌려주는 바람에
     * 260816~260923 사이 import 규칙 넷이 **0개 파일을 검사하며 무조건 통과**했다
     * (`application/`·`match/`가 :shared로 이사했는데 스캔 경로가 따라오지 않았다).
     * 그 종류의 사망은 아무것도 빨개지지 않아서 **누가 볼 일이 없다** — 유일한 증상이
     * "초록"이기 때문이다. 그래서 여기서 못박는다: 스캔 대상이 비면 그 자리에서 터뜨린다.
     */
    private fun ktFilesIn(vararg dirs: File): List<File> {
        val files = dirs.flatMap { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { file -> file.extension == "kt" }.toList()
            } else {
                emptyList()
            }
        }
        require(files.isNotEmpty()) {
            "스캔 대상이 비었다 — 경로가 낡았다: ${dirs.joinToString { it.path }}"
        }
        return files
    }

    /**
     * Reports forbidden references in [files].
     *
     * Each [forbiddenImports] entry is written the way an import statement reads
     * (e.g. `import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi` for an exact type,
     * or `import android.` for a package prefix). Detection is stronger than a raw
     * `startsWith` on import lines: it also catches the two ways the plain
     * import-string check used to miss a violation —
     *  - a wildcard import of the type's package plus a bare use of the type name, and
     *  - a fully-qualified reference used inline in code with no import at all.
     *
     * ⚠️ **이 저장소 심볼의 FQN은 리터럴로 적지 말고 [ContractSymbols]의 상수를 `importOf`로 감싸
     * 넘긴다**(refactor backlog #68). 문자열로 적으면 심볼이 이사했을 때 그 규칙이 어떤 파일과도
     * 매치하지 않은 채 **초록으로 남는다** — 그 사망은 아무것도 빨개지지 않아 아무도 모른다.
     * [ContractSymbolContractTest]가 등록부의 주소가 실재하는지 검사하고, 등록부 밖의 FQN
     * 리터럴을 막는다. `android.`/`java.` 같은 외부 패키지 접두사는 대상이 아니다.
     */
    private fun forbiddenReferenceOffenders(
        files: List<File>,
        forbiddenImports: List<String>,
    ): List<String> =
        files.flatMap { file ->
            val lines = file.readLines()
            forbiddenImports.flatMap { forbidden ->
                detectForbiddenReference(lines, forbidden)
                    .map { reason -> "${file.relativeTo(RepoPaths.root).path}: $reason" }
            }
        }

    /**
     * 루트 패키지(조립 전용)를 참조하는 import를 찾는다(refactor backlog #25).
     *
     * [forbiddenReferenceOffenders]의 접두사 모델로는 표현할 수 없다 — 루트 접두사는 **모든**
     * 하위 패키지와 매치한다. 그래서 루트 **바로 아래 한 조각**(`import <root>.X`, `import <root>.*`)만
     * 본다. 루트 이름은 등록부의 [MAIN_ACTIVITY]에서 파생시켜 리터럴을 들지 않는다.
     */
    private fun rootPackageReferenceOffenders(files: List<File>, allowedSimpleNames: Set<String>): List<String> {
        val root = MAIN_ACTIVITY.substringBeforeLast('.')
        val rootImport = Regex("""^import\s+${Regex.escape(root)}\.([A-Za-z_]\w*|\*)(?:\s+as\s+\w+)?\s*$""")
        return files.flatMap { file ->
            file.readLines()
                .mapNotNull { line -> rootImport.find(line.trim()) }
                .filterNot { match -> match.groupValues[1] in allowedSimpleNames }
                .map { match -> "${file.relativeTo(RepoPaths.root).path}: root-package import -> ${match.value}" }
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
     * 파일 전체에서 주석·문자열을 걷어낸 **코드만** 남긴다.
     *
     * ⚠️ **여러 줄 블록 주석(KDoc)을 먼저 지우는 것이 핵심이다.** [stripStringsAndTrailingComment]는
     * 줄 단위라 `/* */`도 **한 줄 안에 닫힌 것만** 지운다 — KDoc 본문 줄(` * ...`)은 그대로 남는다.
     * 그래서 이 헬퍼 없이 파일 전체를 훑으면 **주석에 적힌 클래스 이름이 코드로 오인된다.**
     * 실제로 그 오탐 때문에 순서 계약 테스트가 한 번 거짓 통과했다(2026-09-01, #63) —
     * 호출 순서를 뒤집었는데도 KDoc의 언급이 먼저 잡혀 통과했다.
     *
     * 260923: 이 파일의 소스 읽기 **전부**(43곳)를 이 헬퍼로 통과시켰다. 날것 `readText()`는
     * 한 곳만 남아 있어도 그 자리에서 같은 오탐이 되살아나므로, **새 검사를 추가할 때도
     * `codeOnly(file.readContractSource())` 형태를 지킬 것.** 일부러 주석까지 봐야 하는 검사가 생기면
     * 그때는 왜 예외인지 그 자리에 적는다.
     */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .joinToString("\n") { stripStringsAndTrailingComment(it) }

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

    /**
     * 정식 릴리즈 초기화(#63)는 **다른 무엇도 읽거나 쓰기 전에** 끝나야 한다. 출석 체크인이 먼저
     * 돌면 그날 기록이 붙었다가 곧바로 지워져 **사용자가 앱을 켰는데 출석이 안 붙는다.**
     *
     * ⚠️ 순서로만 성립하는 계약이라 **단위 테스트로는 절대 안 잡힌다** — 두 코디네이터는 각자
     * 정상 동작하고, 틀리는 것은 호출 순서뿐이다. 그래서 소스 순서를 직접 못박는다.
     */
    @Test
    fun releaseResetRunsBeforeAnythingElseTouchesStorage() {
        val application = RepoPaths.appAndroid("GoAiCoachApplication.kt")
        val text = codeOnly(application.readContractSource())

        val resetAt = text.indexOf("ReleaseResetCoordinator(this)")
        val checkInAt = text.indexOf("AttendanceCheckInCoordinator(this)")

        assertTrue(
            "GoAiCoachApplication must run ReleaseResetCoordinator (backlog #63) before anything " +
                "else touches storage — found ReleaseResetCoordinator at $resetAt and " +
                "AttendanceCheckInCoordinator at $checkInAt",
            resetAt in 0 until checkInAt,
        )
    }

    /**
     * 초기화가 **권한 넷만** 지우고 사용자 콘텐츠·설정은 남기는지 못박는다
     * (`FEATURE_ACCESS_PRINCIPLES.md` 8.6의 범위표).
     *
     * ⚠️ 이 테스트가 막는 사고는 *"초기화니까 전부 지우자"* 다. 대국 기록과 진행 중 대국은 권한이
     * 아니라 **사용자가 직접 만든 것**이고, 기기 식별자를 지우면 진단 로그의 기기 추적이 끊긴다.
     */
    @Test
    fun releaseResetClearsEntitlementsOnlyAndSparesUserContent() {
        val coordinator = RepoPaths.appAndroid("ReleaseResetCoordinator.kt")
        val text = codeOnly(coordinator.readContractSource())

        val required = listOf(
            "AttendanceStore",
            "BotCollectionStore",
            "ConsumableInventoryStore",
            "PremiumStateStore",
        )
        required.forEach { store ->
            assertTrue(
                "ReleaseResetCoordinator must clear $store — attendance pays each tier once, so " +
                    "clearing the collection without attendance strands characters permanently",
                text.contains("$store(context)"),
            )
        }

        // ⚠️ 이름이 등장하는 것만으로는 부족하다 — 읽기만 하고 지우지 않아도 위 검사는 통과한다.
        // clear() 호출 수를 넷으로 못박아 "하나를 빠뜨렸다"와 "하나를 더 지웠다"를 함께 잡는다.
        // 형태(`Store(context).clear()`)로 세지 않는 이유: 지우기 전에 load()로 내용을 봐야 해서
        // 지역변수를 거치는 것이 정상이고, 형태로 묶으면 그 정상적인 코드가 실패한다.
        assertEquals(
            "ReleaseResetCoordinator must call clear() exactly ${required.size} times — one per " +
                "entitlement store, and nothing else",
            required.size,
            Regex("\\.clear\\(\\)").findAll(text).count(),
        )

        listOf(
            "GameHistoryStore",
            "GameSessionStore",
            "UserPreferencesStore",
            "UiLanguageStore",
            "DeviceIdentityStore",
            // 가이드 기록은 **권한이 아니라 취향**이다(백로그 #128) — 테스트 기간에 안내를 본 사람에게
            // 정식 출시에서 그것을 다시 보여줄 이유가 없다. ⚠️ 이 한 줄이 *"안 했다"* 를
            // *"안 하기로 했다"* 로 승격시킨다: 코디네이터에 이름만 넣어도 이 단언과 위의
            // `clear()` 개수 단언(넷 고정)이 **함께** 빨개진다.
            "GuideProgressStore",
        ).forEach { store ->
            assertFalse(
                "ReleaseResetCoordinator must NOT touch $store — user content, settings and the " +
                    "diagnostic device id survive the release reset (8.6 scope table)",
                text.contains(store),
            )
        }
    }

    /**
     * import·주석·빈 줄을 걷어낸 **코드 줄만** 남긴다(2026-09-05).
     *
     * ⚠️ **이것이 없으면 예산이 결합과 설명을 복잡도로 오해한다.** `GoCoachApp.kt`는 885줄 중
     * 126줄이 import였다 — import는 복잡도가 아니라 **결합의 증상**이고, 그것을 예산으로 막으면
     * 정작 줄여야 할 조립 코드는 그대로 둔 채 import만 줄이는 왜곡이 생긴다.
     */
    private fun codeLinesOf(lines: List<String>): List<String> {
        var inBlockComment = false
        return lines.filter { raw ->
            val line = raw.trim()
            when {
                inBlockComment -> {
                    if (line.contains("*/")) inBlockComment = false
                    false
                }
                line.isEmpty() -> false
                line.startsWith("//") -> false
                line.startsWith("import ") || line.startsWith("package ") -> false
                line.startsWith("/*") -> {
                    if (!line.contains("*/")) inBlockComment = true
                    false
                }
                else -> true
            }
        }
    }

    private companion object {
        /**
         * 루트 패키지에 **생성되는** 심볼 — 소스가 없어 [ContractSymbols]에 FQN으로 등록할 수 없고
         * (실존 검사가 영원히 빨개진다), platform 어댑터가 정당하게 쓴다(`BuildConfig.USE_TEST_ADS`,
         * `R.string.default_web_client_id`). 그래서 루트 매처의 예외를 **단순 이름으로만** 둔다.
         */
        val GENERATED_ROOT_SYMBOLS: Set<String> = setOf("BuildConfig", "R")
    }
}
