package com.worksoc.goaicoach.architecture

import com.worksoc.goaicoach.architecture.ContractSymbols.APPLICATION_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_ANDROID_RUNTIME_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_CORE_API
import com.worksoc.goaicoach.architecture.ContractSymbols.ENGINE_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.LOCAL_CONFIGURE_SYNC_AND_ESTIMATE_GRAPH_SCORE
import com.worksoc.goaicoach.architecture.ContractSymbols.LOCAL_ESTIMATE_SCORE_FOR_STATE
import com.worksoc.goaicoach.architecture.ContractSymbols.LOCAL_SYNC_AND_ESTIMATE_GRAPH_SCORE
import com.worksoc.goaicoach.architecture.ContractSymbols.MAIN_ACTIVITY
import com.worksoc.goaicoach.architecture.ContractSymbols.MIDDLEWARE_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.PERSISTENCE_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.PLATFORM_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.PRESENTATION_PACKAGE
import com.worksoc.goaicoach.architecture.ContractSymbols.ROOT_LOWERCASE_TOP_LEVEL_FUNCTION_SAMPLE
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
     * ⚠️ 260925(refactor backlog #95): `application/`·`match/`가 이제 전부 :shared로 건너간 뒤,
     * 이 가드가 그 둘에 대해서도 검사하던 두 절 중 하나는 **삭제 전 위반을 실제로 넣어 확인**하니
     * 이미 Gradle이 막고 있었다 — `:shared`는 `:app-android`·`:engine-android`에 대한 Gradle
     * 의존성 자체가 없어(commonMain은 kotlinx-coroutines-core만 의존), `engine.android` 임포트도
     * (삭제된) `EngineAdapter` 임포트도 두 경우 다 `Unresolved reference`로 컴파일이 즉시 잡는다
     * (android 타깃 컴파일로 실측, iOS 게이트를 켤 필요조차 없었다). 그래서 `application/`·`match/`
     * 스캔은 걷어내고, app-android에 남은 유일한 파일
     * (`application/diagnostic/LocalFileDiagnosticEventExternalSink.kt`)만 본다 — app-android는
     * `:engine-android`에 실제로 의존해 그 임포트가 컴파일을 통과하므로 텍스트 검사가 여전히
     * 필요하다. `match/`는 :shared로 통째로 건너가 app-android에 남은 파일이 없다(비면
     * [ktFilesIn]이 스스로 터진다). `EngineAdapter`(호환 별칭)는 죽은 코드라 삭제됐고(테스트
     * 페이크 2개는 [com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi]를 직접 구현) 그
     * 절은 세 가드(이 파일의 [uiAndPresentationDoNotImportRawEngineCoreApi]·이 테스트·
     * [platformAdaptersDoNotImportComposeUiOrComposition]) 전부에서 함께 뺐다.
     */
    @Test
    fun applicationDiagnosticSinkDoesNotDependOnEngineAndroidRuntime() {
        val checkedDirs = listOf(
            RepoPaths.appAndroid("application"),
        )
        val forbiddenImports = listOf(
            importOf(ENGINE_ANDROID_RUNTIME_PACKAGE),
        )

        val offenders = forbiddenReferenceOffenders(
            files = ktFilesIn(*checkedDirs.toTypedArray()),
            forbiddenImports = forbiddenImports,
        )

        assertTrue(
            "app-android's remaining application-layer file (the diagnostic sink) must not depend on the concrete engine runtime implementation:\n${offenders.joinToString("\n")}",
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
     *
     * ⚠️ refactor backlog #77 — import 줄뿐 아니라 **inline FQN**(import 없이 `<root>.X`를 코드에서
     * 직접 쓰는 것)도 같은 방식으로 나란히 확인한다. `Inline.kt`가 그 사각지대의 회귀 방지다.
     *
     * ⚠️ refactor backlog #79 — #77이 붙인 정규식은 **대문자로 시작하는 이름만** 잡아, 루트의
     * **소문자 최상위 함수**를 inline FQN으로 부르면 여전히 지나갔다. `LowercaseInline.kt`가 그
     * 사각지대의 회귀 방지다. 동시에 `SubpackageInline.kt`로 **하위 패키지 참조는 여전히 루트
     * 매처의 몫이 아님**(각자의 forbidden import가 맡는다)을 함께 못박는다 — 그렇지 않으면
     * "소문자도 잡는다"는 수정이 "루트 접두사로 시작하는 모든 것을 잡는다"로 과잉 일반화됐는지
     * 구분할 수 없다.
     *
     * ⚠️ #79 검수 — 생성 코드 inline 예외는 열거 목록에 `BuildConfig`/`R`을 **일부러 섞어** 넘겨
     * 확인한다. 실제 색인에는 원래 안 나타나므로, 섞지 않으면 예외를 지워도 이 단언이 초록이다.
     * `BlockComment.kt`는 `*`로 시작하지 않는 여러 줄 블록 주석 속 언급이 위반이 아님을 못박는다
     * (`9f81a124`의 구현은 이것을 잡았다).
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
            val offendingInline = File(tempDir, "OffendingInline.kt").apply {
                writeText("package x\n\nval probe = $MAIN_ACTIVITY\n")
            }
            val generatedInline = File(tempDir, "GeneratedInline.kt").apply {
                writeText(
                    "package x\n\n" +
                        GENERATED_ROOT_SYMBOLS.joinToString("\n") { symbol -> "val probe_$symbol = $root.$symbol.hashCode()" } +
                        "\n",
                )
            }
            val lowercaseInline = File(tempDir, "LowercaseInline.kt").apply {
                writeText("package x\n\nval probe = $ROOT_LOWERCASE_TOP_LEVEL_FUNCTION_SAMPLE\n")
            }
            val subpackageInline = File(tempDir, "SubpackageInline.kt").apply {
                writeText("package x\n\nval probe = ${UI_PACKAGE}SomeScreen\n")
            }
            val blockComment = File(tempDir, "BlockComment.kt").apply {
                writeText("package x\n\n/*\n$MAIN_ACTIVITY is only mentioned here\n*/\nval ok = 1\n")
            }

            val offenders = rootPackageReferenceOffenders(listOf(offending), GENERATED_ROOT_SYMBOLS)
            assertEquals("루트 import(단일 조각·와일드카드)를 둘 다 잡아야 한다: $offenders", 2, offenders.size)
            assertEquals(
                "생성 코드(BuildConfig·R)는 루트 import 예외여야 한다",
                emptyList<String>(),
                rootPackageReferenceOffenders(listOf(generated), GENERATED_ROOT_SYMBOLS),
            )

            val inlineOffenders = rootPackageReferenceOffenders(listOf(offendingInline), GENERATED_ROOT_SYMBOLS)
            assertEquals(
                "import 없이 쓴 inline FQN(`$MAIN_ACTIVITY`)도 잡아야 한다(#77): $inlineOffenders",
                1,
                inlineOffenders.size,
            )
            assertEquals(
                "생성 코드(BuildConfig·R)는 inline FQN으로 써도 예외여야 한다(#77) — 열거 목록에 섞여 들어와도",
                emptyList<String>(),
                rootPackageReferenceOffenders(
                    listOf(generatedInline),
                    GENERATED_ROOT_SYMBOLS,
                    rootDeclaredNames = SourceSymbolIndex.topLevelDeclaredSimpleNames(root) + GENERATED_ROOT_SYMBOLS,
                ),
            )

            val lowercaseOffenders = rootPackageReferenceOffenders(listOf(lowercaseInline), GENERATED_ROOT_SYMBOLS)
            assertEquals(
                "루트의 소문자 최상위 함수(`$ROOT_LOWERCASE_TOP_LEVEL_FUNCTION_SAMPLE`)도 inline FQN으로 " +
                    "부르면 잡아야 한다(#79): $lowercaseOffenders",
                1,
                lowercaseOffenders.size,
            )
            assertEquals(
                "하위 패키지 참조(`ui` 등)는 루트 매처가 아니라 각자의 forbidden import가 맡는다 — " +
                    "루트 매처가 과잉 일반화되면 안 된다(#79)",
                emptyList<String>(),
                rootPackageReferenceOffenders(listOf(subpackageInline), GENERATED_ROOT_SYMBOLS),
            )
            assertEquals(
                "여러 줄 블록 주석 속 루트 FQN 언급은 참조가 아니다(#79 검수)",
                emptyList<String>(),
                rootPackageReferenceOffenders(listOf(blockComment), GENERATED_ROOT_SYMBOLS),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * [rootPackageReferenceOffenders]의 자기검증 둘째 — **선언 모양**이 가리던 사각지대(refactor
     * backlog #79 검수).
     *
     * #79의 첫 구현(`9f81a124`)은 import와 inline을 모두 [SourceSymbolIndex]가 나열한 이름으로만
     * 찾았는데, 그 열거는 칼럼 0의 class/interface/object/typealias/fun만 봤다. 그래서 검수가 쓴
     * 탐침 셋 — `internal val`, `internal const val`, 같은 줄에 `@Suppress`가 붙은 `internal fun` —
     * 은 platform이 import해도 inline FQN으로 불러도 초록이었다. 옛 매처(import 한 조각 정규식 +
     * 대문자 inline 정규식)는 그 import 둘과 대문자 inline 하나를 잡았으니 **가드 회귀**였다.
     *
     * 합성 루트 소스에 그 셋과 중첩 제네릭 경계 fun을 두고 네 갈래를 따로 못박는다.
     *  1. 열거기가 그 네 이름과 타입 하나를 **정확히** 나열한다 — 파일 애너테이션·멤버 프로퍼티·
     *     애너테이션 이름·타입 인자는 섞이지 않는다.
     *  2. import는 열거와 **무관하게** 잡힌다 — 열거 결과를 비워서 넘겨도 네 줄이 다 잡혀야 한다.
     *     같은 줄을 두 번 세지도 않는다(열거 결과를 넘겨도 넷).
     *  3. inline FQN은 열거가 나열한 이름으로 잡힌다(넷 다).
     *  4. 루트 선언의 멤버를 가리키는 import(`<root>.Type.Member`)는 잡되, 하위 패키지 import는
     *     루트 매처의 몫이 아니다.
     */
    @Test
    fun rootPackageMatcherCatchesPropertiesAnnotatedAndGenericBoundDeclarations() {
        val root = MAIN_ACTIVITY.substringBeforeLast('.')
        val probeNames = setOf("rootProbeValue", "RootProbeConst", "annotatedProbe", "genericBoundProbe")
        val rootSource = listOf(
            "@file:Suppress(\"unused\")",
            "",
            "package $root",
            "",
            "internal val rootProbeValue = 1",
            "internal const val RootProbeConst = 2",
            "@Suppress(\"FunctionOnlyReturningConstant\") internal fun annotatedProbe(): Int = 3",
            "internal fun <T : Comparable<T>> genericBoundProbe(value: T): T = value",
            "",
            "internal class RootProbeHolder {",
            "    val memberProbe = 4",
            "}",
        ).joinToString("\n", postfix = "\n")

        val declared = SourceSymbolIndex.topLevelDeclaredSimpleNamesIn(rootSource)
        assertEquals(
            "루트 열거기가 최상위 프로퍼티·const·앞머리 애너테이션·중첩 제네릭 경계 선언을 나열해야 한다(#79 검수)",
            probeNames + "RootProbeHolder",
            declared,
        )

        val tempDir = java.nio.file.Files.createTempDirectory("root-matcher-shapes").toFile()
        try {
            val importProbe = File(tempDir, "ImportProbe.kt").apply {
                writeText("package x\n\n" + probeNames.sorted().joinToString("\n") { "import $root.$it" } + "\n")
            }
            val inlineProbe = File(tempDir, "InlineProbe.kt").apply {
                writeText(
                    "package x\n\n" +
                        "val a = $root.rootProbeValue\n" +
                        "val b = $root.RootProbeConst\n" +
                        "val c = $root.annotatedProbe()\n" +
                        "val d = $root.genericBoundProbe(1)\n",
                )
            }
            val memberImport = File(tempDir, "MemberImport.kt").apply {
                writeText("package x\n\nimport $root.RootProbeHolder.Nested\nimport ${UI_PACKAGE}SomeScreen\n")
            }

            val importWithoutEnumeration =
                rootPackageReferenceOffenders(listOf(importProbe), GENERATED_ROOT_SYMBOLS, rootDeclaredNames = emptySet())
            assertEquals(
                "루트 import는 열거 결과와 무관하게 잡아야 한다 — 열거기가 모르는 선언 모양이 생겨도 " +
                    "import 쪽은 회귀하지 않는다(#79 검수): $importWithoutEnumeration",
                probeNames,
                probeNames.filter { name -> importWithoutEnumeration.any { it.endsWith("$root.$name") } }.toSet(),
            )
            assertEquals(
                "루트 import 한 줄은 한 번만 보고한다: $importWithoutEnumeration",
                probeNames.size,
                importWithoutEnumeration.size,
            )
            val importWithEnumeration =
                rootPackageReferenceOffenders(listOf(importProbe), GENERATED_ROOT_SYMBOLS, rootDeclaredNames = declared)
            assertEquals(
                "열거 결과를 넘겨도 import 한 줄을 두 번 세면 안 된다: $importWithEnumeration",
                probeNames.size,
                importWithEnumeration.size,
            )

            val inlineOffenders =
                rootPackageReferenceOffenders(listOf(inlineProbe), GENERATED_ROOT_SYMBOLS, rootDeclaredNames = declared)
            assertEquals(
                "루트의 프로퍼티·const·애너테이션 fun·제네릭 경계 fun을 inline FQN으로 불러도 잡아야 한다" +
                    "(#79 검수): $inlineOffenders",
                probeNames,
                probeNames.filter { name -> inlineOffenders.any { it.endsWith("$root.$name") } }.toSet(),
            )
            assertEquals("inline 참조 하나는 한 번만 보고한다: $inlineOffenders", probeNames.size, inlineOffenders.size)

            // 열거기가 못 읽는 모양이라도 대문자 이름이면 잡아야 한다 — 옛 매처가 잡던 것(#79 3차 검수).
            // ⓐ 여러 줄 애너테이션의 닫는 줄에 붙은 선언(열거 목록에 없다), ⓑ `*`로 시작하는 spread 이음 줄
            // (detectForbiddenReference는 이 줄을 KDoc으로 보고 버린다).
            val unreadableShapes = File(tempDir, "UnreadableShapes.kt").apply {
                writeText(
                    "package x\n\n" +
                        "val e = $root.ClosingLineObject\n" +
                        "val f = listOf(\n" +
                        "    *$root.SpreadArray,\n" +
                        ")\n",
                )
            }
            val unreadableOffenders =
                rootPackageReferenceOffenders(listOf(unreadableShapes), GENERATED_ROOT_SYMBOLS, rootDeclaredNames = declared)
            assertEquals(
                "열거되지 않은 대문자 루트 심볼의 inline FQN(닫는 줄 애너테이션 선언·spread 줄)도 잡아야 한다: $unreadableOffenders",
                setOf("ClosingLineObject", "SpreadArray"),
                setOf("ClosingLineObject", "SpreadArray").filter { name -> unreadableOffenders.any { it.endsWith("$root.$name") } }.toSet(),
            )

            val memberOffenders =
                rootPackageReferenceOffenders(listOf(memberImport), GENERATED_ROOT_SYMBOLS, rootDeclaredNames = declared)
            assertEquals(
                "루트 선언의 멤버 import는 잡고 하위 패키지 import는 두어야 한다: $memberOffenders",
                listOf("import $root.RootProbeHolder.Nested"),
                memberOffenders.map { it.substringAfter(" -> ") },
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

    /**
     * 저장 포트 둘이 **저장 형식(직렬화 원문)과 매체(파일 경로)를 돌려주지 않는다**(refactor backlog #86).
     *
     * 예전에는 `SavedGameStorePort.readRawJson(): String?`·`EngineBenchmarkStorePort.loadText(): String`·
     * `path(): String`이 있었다 — 타입으로는 문자열이라 값이지만, 포트를 쥔 흐름 코드가 저장 형식을 읽을
     * 수 있게 만든다(`docs/ARCHITECTURE.md` 4계층 ⓐ *"타입이 값이어도 저장 형식 자체는 싣지 않는다"*).
     * 디버그 리포트용 원문은 이제 어댑터의 포트 밖 메서드를 조립 루트가 텍스트 공급자로 꽂는다.
     *
     * ⚠️ 이 둘에만 거는 **회귀 고정**이다. 원칙 문서가 적었듯 *"문자열인데 저장 형식인가"* 는 타입으로
     * 가를 수 없어 일반 규칙은 리뷰가 맡는다 — 이 두 포트에는 문자열을 돌려줄 정당한 메서드가 없어서
     * 문자열 반환 자체를 막는다. 문자열이 필요한 메서드가 정말 생기면 이 테스트를 고치며 그 이유를 적는다.
     */
    @Test
    fun savedGameAndBenchmarkStorePortsDoNotExposeStorageFormat() {
        val repoRoot = RepoPaths.root
        val ports = mapOf(
            RepoPaths.applicationPath("savedgame/SavedGamePorts.kt") to "interface SavedGameStorePort",
            RepoPaths.applicationPath("engine/EngineBenchmarkPorts.kt") to "interface EngineBenchmarkStorePort",
        )
        val stringMember = Regex("""\b(?:fun\s+\w+\s*\([^)]*\)|va[lr]\s+\w+)\s*:\s*String\b""")

        val offenders = ports.flatMap { (port, declaration) ->
            val text = codeOnly(port.readContractSource())
            val path = port.relativeTo(repoRoot).path
            if (declaration !in text) {
                listOf("$path: `$declaration` not found — update this contract if the port moved")
            } else {
                stringMember.findAll(text).map { match -> "$path: ${match.value}" }.toList()
            }
        }

        assertTrue(
            "Saved-game/benchmark store ports must not return raw stored text or file paths — expose them " +
                "from the adapter outside the port and plug them in at the assembly root:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
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
        // #96: 러너·플랜·요청 타입 조각 17개는 `:shared`에서 `internal`이 되어 컴파일러가 막는다
        // (app-android 전체에서 보이지 않는다). 죽은 조각 3개도 지웠다 — 로그 문구 둘은 codeOnly가
        // 문자열을 비워 매치될 수 없었고, `applyTopMoveAnalysisCompletionApplyPlan`은 저장소에 없었다.
        val forbiddenFragments = listOf(
            "settingsState = settingsState.hideTopMoves()",
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
        // #96: 요청·완료 플랜·워크플로 조각 4개는 `:shared`에서 `internal`이다. `HumanEngineSyncRunPlan`은
        // public으로 남는다 — sealed interface `GameSessionEffect`의 중첩 `SyncHumanMove`가 노출하는데,
        // 인터페이스 안의 중첩 선언에는 `internal`을 붙일 수 없다. `EngineOperationKind`는 #97 몫.
        val forbiddenFragments = listOf(
            "HumanEngineSyncRunPlan(",
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
        // #96: `runPostUndoScoreSyncApplyPlan`은 이미 `internal`이라 지웠다. 남은 둘은 app-android 테스트가
        // 참조해 테스트 이관(#97) 뒤에 internal로 바꾼다.
        val forbiddenFragments = listOf(
            "PostUndoScoreSyncEffectLaunchRequest(",
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
        // #96: `runRestoredGameSyncApplyPlan`은 이미 `internal`이라 지웠다. 나머지는 app-android 테스트가
        // 참조해 테스트 이관(#97) 뒤의 몫이다.
        val forbiddenFragments = listOf(
            "RestoredGameSyncEffectLaunchRequest(",
            "RestoredGameSyncExecutionContext(",
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
        // #96: 복원 플랜·프롬프트 로드·복원 러너 조각 4개는 `:shared`에서 `internal`이다.
        // `SavedSessionPromptPlan`은 public으로 남는다 — GoCoachApp이 `applyPrompt = { prompt -> … }`
        // 람다로 이름 없이 받으므로(타입 추론) 가시성으로는 못 막고, 이름을 쓰지 말라는 텍스트 규칙만 선다.
        // 나머지 둘은 app-android 테스트가 참조해 #97 몫.
        val forbiddenFragments = listOf(
            "SavedGamePersistenceRequest(",
            "SavedSessionPromptPlan",
            "runSavedGamePersistence(",
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
        // #96: 워크플로·로그·러너·요청 조각 5개는 `:shared`에서 `internal`이다.
        // `GameSessionEffect.*`는 sealed interface의 중첩이라 하나씩 `internal`로 만들 수 없다(#97 뒤 타입째 검토).
        val forbiddenFragments = listOf(
            "GameSessionEffect.StartEngineBackedGame(",
            "EngineStartupWorkflowResult.Success",
            "EngineStartupWorkflowResult.Failure",
            "EngineOperationKind.EngineNewGame",
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
        // #96: 토큰·실행 문맥·워크플로·완료 플랜·로그·러너 조각 9개는 `:shared`에서 `internal`이다.
        // `GameSessionEffect.*`는 sealed interface의 중첩이라 하나씩 `internal`로 만들 수 없다(#97 뒤 타입째 검토).
        val forbiddenFragments = listOf(
            "GameSessionEffect.RunAutoAiTurn(",
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
        // #96: 로그·엔드게임 플랜 조각 3개는 `:shared`에서 `internal`이라 컴파일러가 막는다 — 남은 것은
        // "배선 계층이 완료 적용 함수를 직접 선언하지 않는다"는 모양 규칙이다.
        val forbiddenFragments = listOf(
            "fun applyAutoAiTurnSuccessCompletion(",
            "fun applyAutoAiTurnFailureCompletion(",
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
        // #96: 토큰·효과·완료 플랜·로그·러너 조각 9개는 `:shared`에서 `internal`이다.
        // `GameSessionEffect.*`는 sealed interface의 중첩이라 하나씩 `internal`로 만들 수 없다(#97 뒤 타입째 검토).
        val forbiddenFragments = listOf(
            "GameSessionEffect.ResolveAutoAiEndgame(",
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
        // #96: 요청·러너 조각 4개는 `:shared`에서 `internal`이라 컴파일러가 막는다 — 남은 것은
        // "배선 계층이 로그 파일 원문을 직접 읽지 않는다"는 모양 규칙이다.
        val forbiddenFragments = listOf(
            "runtimeEventLog.readText()",
            "diagnosticEventLog.readText()",
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
        // #96: 워크플로·러너·요청·플랜·프롬프트 조각 6개는 `:shared`에서 `internal`이다. 죽은 조각
        // `position-cache-optimization`도 지웠다(codeOnly가 문자열을 비워 매치될 수 없었다).
        // `GameSessionEffect.*`는 sealed interface의 중첩이라 하나씩 `internal`로 만들 수 없고(#97 뒤 타입째 검토),
        // 두 enum 항목은 app-android 테스트가 타입을 참조해 #97 몫이다.
        val forbiddenFragments = listOf(
            "GameSessionEffect.RunPositionCacheOptimization(",
            "EngineOperationKind.PositionCacheOptimization",
            "EngineFallbackPolicy.CachedAnalysis",
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
        val controller = RepoPaths.applicationPath("cacheoptimization/PositionCacheOptimizationController.kt")
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

    /**
     * #96: 금지 조각 7개(전이·상태·스코프·로그)는 전부 `:shared`에서 `internal`이라 app-android가 부를 수
     * 없다 — "GoCoachApp이 수명 주기를 소유하지 않는다"는 이제 컴파일러가 지킨다. 남은 것은
     * "컨트롤러를 만든다"는 필수 조각 하나이고, 행동 기반 배선 테스트(#43)로 넘길 몫이다.
     */
    @Test
    fun goCoachAppDoesNotOwnEngineOperationLifecycleBody() {
        val goCoachApp = RepoPaths.goCoachApp
        val text = codeOnly(goCoachApp.readContractSource())
        val requiredFragments = listOf(
            "EngineOperationLifecycleController(",
        )
            .filterNot { fragment -> fragment in text }

        assertTrue(
            "GoCoachApp should delegate engine-operation lifecycle tracking to EngineOperationLifecycleController:\n" +
                "missing:\n${requiredFragments.joinToString("\n")}",
            requiredFragments.isEmpty(),
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
            sharedRoot.resolve("policy/EngineOperationPolicy.kt"),
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

    /**
     * **5계층 파일은 6계층 패키지를 모른다**(refactor backlog #84). 원칙 문서의 5계층 경계 원칙
     * (*"6계층 이상은 모른다"*)을 처음으로 코드에서 본다. 그전까지 계층 배정은 로드맵 문장과 KDoc에만
     * 있었고, 5↔6 방향을 보는 가드가 없었다. 그 사이 5계층으로 적혀 있던 파일 8개가 6계층을 import
     * 16줄로 참조하고 있었다(2026-09-25 재분류로 0).
     *
     * ## 무엇을 보는가
     * [ContractSymbols.LAYER_5_PACKAGES]를 **정확히** 선언한 프로덕션 파일 전부를 본다. :shared 세
     * 소스셋과 app-android의 영구 예외 하나까지 [SourceSymbolIndex.filesDeclaring]이 모은다. 그 파일이
     * [ContractSymbols.LAYER_6_PACKAGES] 아래를 import하거나 코드 속 inline FQN으로 쓰면 빨갛다.
     * 판정은 [forbiddenReferenceOffenders]→[detectForbiddenReference]를 그대로 탄다. 와일드카드·별칭·
     * inline FQN을 잡고, 주석·문자열 속 언급은 잡지 않는다.
     *
     * ## 값도 막는 이유
     * 큰 그림은 위 계층의 값을 아는 것을 **계약과 그 구현체**에만 허용한다. 5계층 유스케이스는 6계층의
     * 계약이 아니다. 여기가 빨개지면 규칙을 넓히기 전에 먼저 묻는다 — 그 코드는 6계층 상태를 전이·
     * 저장하는 6계층의 흐름이 아닌가? #84의 16줄은 전부 그랬고, 매핑만 고쳐 코드 이동 없이 0이 됐다.
     *
     * ## 못 보는 것(알고 둔다)
     * 이름 없이 타입 추론으로만 흐르는 6계층 값이다. 예를 들어 4계층 포트(`premium.port`·`auth.port`)가
     * 돌려준 6계층 값을 import 없이 다른 곳에 넘기면 여기 안 걸린다. 2026-09-25 기준 그 두 포트를 쥐는
     * 5계층 파일은 0이다.
     *
     * ⚠️ 목록이 낡아 파일이 0개가 되면 가드는 초록인 채 아무것도 안 본다(함정 76). [filesDeclaringEach]가
     * 목록의 패키지마다 파일이 하나 이상인지 `require`로 못박는다.
     */
    @Test
    fun layerFivePackagesDoNotReferenceLayerSixPackages() {
        val layerFiveFiles = filesDeclaringEach(ContractSymbols.LAYER_5_PACKAGES)
        // 금지 쪽도 실재해야 한다 — 낡은 6계층 이름은 어떤 import와도 매치하지 않는다.
        filesDeclaringEach(ContractSymbols.LAYER_6_PACKAGES)
        require(layerFiveFiles.size > 100) {
            "5계층 파일을 거의 못 모았다(${layerFiveFiles.size}개) — 색인이나 목록이 낡았다."
        }

        val offenders = forbiddenReferenceOffenders(
            files = layerFiveFiles,
            forbiddenImports = layerSixForbiddenImports(),
        )

        assertTrue(
            "5계층 파일이 6계층 패키지를 참조한다 — 5계층은 6계층의 동작도 값도 모른다(원칙 문서 5계층 경계 " +
                "원칙). 규칙을 넓히기 전에 그 코드가 6계층 상태를 전이·저장하는 흐름인지 먼저 본다. 그렇다면 " +
                "그 패키지를 ContractSymbols.LAYER_6_PACKAGES로 옮기고 로드맵 5·6계층 절을 함께 고친다" +
                "(refactor backlog #84):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * [layerFivePackagesDoNotReferenceLayerSixPackages]의 자기검증(함정 24: 초록은 안전이 아니다).
     * 5계층 파일에 위반이 없으면 그 가드는 초록이라, 금지 목록이 고장나 무엇과도 매치하지 않는 상태와
     * 구분되지 않는다. 그래서 두 가지를 나란히 본다.
     *  - 6계층 패키지마다 import 한 줄, inline FQN 하나를 심은 합성 파일이 **전부** 잡히는가.
     *  - 5계층 패키지 전부를 import하고 6계층은 주석·문자열에서만 언급하는 파일은 **안** 잡히는가
     *    (6계층 접두사가 5계층 패키지를 삼키지 않는가).
     * 패키지 이름은 목록에서 가져온다 — 리터럴 FQN을 적지 않는다.
     */
    @Test
    fun layerSixRuleCatchesPlantedReferencesAndSparesLayerFive() {
        val tempDir = java.nio.file.Files.createTempDirectory("layer-five-six").toFile()
        try {
            val planted = ContractSymbols.LAYER_6_PACKAGES.flatMapIndexed { index, layerSix ->
                listOf(
                    File(tempDir, "PlantedImport$index.kt").apply {
                        writeText("package sample\nimport $layerSix.Probe\nfun f(p: Probe) = p\n")
                    },
                    File(tempDir, "PlantedInline$index.kt").apply {
                        writeText("package sample\nfun f(p: $layerSix.Probe) = p\n")
                    },
                )
            }
            val mentioned = ContractSymbols.LAYER_6_PACKAGES.first()
            val clean = File(tempDir, "LayerFiveOnly.kt").apply {
                writeText(
                    "package sample\n" +
                        ContractSymbols.LAYER_5_PACKAGES.joinToString("\n") { "import $it.Probe" } +
                        "\n// $mentioned.Probe is only mentioned in a comment\n" +
                        "fun f() = \"$mentioned.Probe\"\n",
                )
            }

            val offenders = forbiddenReferenceOffenders(
                files = planted + clean,
                forbiddenImports = layerSixForbiddenImports(),
            )

            val missed = planted.map { it.name }.filter { name -> offenders.none { it.contains("$name:") } }
            assertEquals(
                "심은 6계층 참조를 놓쳤다 — 금지 목록이 고장났다:\n${offenders.joinToString("\n")}",
                emptyList<String>(),
                missed,
            )
            assertTrue(
                "5계층 import나 주석·문자열 속 언급을 6계층 참조로 잡았다 — 접두사가 너무 넓다:\n" +
                    offenders.joinToString("\n"),
                offenders.none { it.contains(clean.name) },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * **:shared의 모든 패키지가 계층 목록 하나에 정확히 한 번 들어 있는가**(refactor backlog #84).
     *
     * 위 가드는 목록에 적힌 패키지만 본다. 새 5계층 패키지가 목록에 안 들어가면 그 패키지는 조용히
     * 감시 밖에 남는다. 2026-09-23 로드맵이 `persistence/`를 두고 적은 *"매핑에 없는 패키지에는 계층
     * 규칙이 적용되지 않는다"* 와 같은 사각지대다. 그래서 목록이 현실과 어긋나는 세 경우를 다 막는다.
     *  - **미배정**: :shared commonMain이나 `application.*`로 선언된 패키지가 세 목록 어디에도 없다.
     *  - **낡은 항목**: 목록에 있는데 그 패키지를 선언한 프로덕션 파일이 없다(이사했거나 사라졌다).
     *  - **중복**: 한 패키지가 두 번 적혀 있다(두 목록에, 또는 한 목록에 두 번).
     * 새 패키지를 만들었으면 로드맵 계층 절에서 자리를 정하고 [ContractSymbols]의 목록에 적는다.
     */
    @Test
    fun everySharedPackageIsAssignedToExactlyOneLayerList() {
        val assignments = ContractSymbols.LAYER_5_PACKAGES + ContractSymbols.LAYER_6_PACKAGES +
            ContractSymbols.SHARED_PACKAGES_BELOW_LAYER_5
        val declared = layerAssignablePackages()
        val duplicated = assignments.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        val unassigned = (declared - assignments.toSet()).sorted()
        val stale = (assignments.toSet() - declared).sorted()

        assertEquals(
            "같은 패키지가 계층 목록에 두 번 적혀 있다 — 패키지 하나는 계층 하나다(refactor backlog #84).",
            emptyList<String>(),
            duplicated,
        )
        assertEquals(
            "계층이 배정되지 않은 패키지가 있다 — 로드맵(docs/spec/GO_AI_COACH_ARCHITECTURE_ROADMAP.md) 계층 " +
                "절에서 자리를 정하고 ContractSymbols의 LAYER_5_PACKAGES·LAYER_6_PACKAGES·" +
                "SHARED_PACKAGES_BELOW_LAYER_5 중 하나에 적어라(refactor backlog #84).",
            emptyList<String>(),
            unassigned,
        )
        assertEquals(
            "계층 목록에 있는데 선언한 파일이 없는 패키지가 있다 — 이사했거나 사라졌다. 목록과 로드맵을 " +
                "함께 고쳐라(refactor backlog #84).",
            emptyList<String>(),
            stale,
        )
    }

    /**
     * ⚠️ refactor backlog #69 — 이 픽스처는 예전엔 `EngineCoreApi`의 FQN을 **리터럴로 여섯 번**
     * 되풀이해 적어 두고 있었다. `#24`가 `shared.enginecontract`로 그 패키지를 옮겼을 때 이 여섯
     * 자리를 전부 손으로 고쳐야 했다(자기검증 픽스처 몫). 그래서 [ContractSymbols.ENGINE_CORE_API]를
     * 문자열 템플릿으로 끼워 넣는다 — [rootPackageMatcherFlagsRootImportsButNotGeneratedSymbols]가
     * [MAIN_ACTIVITY]로 이미 하는 것과 같은 패턴이다. 이제 소스에 FQN **리터럴이 하나도 없어**
     * [ContractSymbols.FIXTURE_FUNCTIONS] 예외 등록도 필요 없다.
     */
    @Test
    fun detectionCatchesViolationsThatPlainImportStringWouldMiss() {
        val tempDir = java.nio.file.Files.createTempDirectory("layering-contract").toFile()
        try {
            val enginecontractPackage = ENGINE_CORE_API.substringBeforeLast('.')
            // a) Wildcard import of the package + bare use of the forbidden type.
            val wildcardOffender = File(tempDir, "WildcardOffender.kt").apply {
                writeText(
                    """
                    package sample
                    import $enginecontractPackage.*
                    fun build(api: EngineCoreApi) = api
                    """.trimIndent(),
                )
            }
            // b) Fully-qualified reference inline, with no import at all.
            val inlineOffender = File(tempDir, "InlineOffender.kt").apply {
                writeText(
                    """
                    package sample
                    fun build(api: $ENGINE_CORE_API) = api
                    """.trimIndent(),
                )
            }
            // c) Aliased import still resolves to the forbidden type.
            val aliasedOffender = File(tempDir, "AliasedOffender.kt").apply {
                writeText(
                    """
                    package sample
                    import $ENGINE_CORE_API as Engine
                    fun build(api: Engine) = api
                    """.trimIndent(),
                )
            }
            // d) Negative: only a prose comment mentions it; unrelated wildcard import.
            val clean = File(tempDir, "Clean.kt").apply {
                writeText(
                    """
                    package sample
                    import $MIDDLEWARE_PACKAGE*
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
                    fun describe() = "see $ENGINE_CORE_API for details"
                    """.trimIndent(),
                )
            }
            // f) Negative: a single-line block comment mentions the path.
            val blockCommentMention = File(tempDir, "BlockCommentMention.kt").apply {
                writeText(
                    """
                    package sample
                    fun build() = 1 /* $ENGINE_CORE_API */
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
                forbiddenImports = listOf(importOf(ENGINE_CORE_API)),
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
     * 목록의 패키지마다 그 패키지를 **정확히** 선언한 프로덕션 파일을 모은다(refactor backlog #84).
     * 파일이 0개인 패키지가 하나라도 있으면 그 자리에서 터뜨린다 — [ktFilesIn]의 빈 디렉터리 검사와
     * 같은 이유다. 목록이 낡아 헛도는 가드는 초록인 채 죽는다(함정 76).
     */
    private fun filesDeclaringEach(packages: List<String>): List<File> {
        require(packages.isNotEmpty()) { "계층 패키지 목록이 비었다 — 가드가 아무것도 안 본다." }
        return packages.flatMap { packageName ->
            val files = SourceSymbolIndex.filesDeclaring(packageName)
            require(files.isNotEmpty()) {
                "계층 목록의 패키지 `$packageName`을 선언한 프로덕션 파일이 없다 — 이사했거나 사라졌다. " +
                    "ContractSymbols의 계층 목록을 고쳐라(refactor backlog #84)."
            }
            files
        }
    }

    /** 6계층 패키지 각각을 접두사 금지(`import <패키지>.`)로 만든다 — 그 패키지의 선언 전부를 막는다. */
    private fun layerSixForbiddenImports(): List<String> =
        ContractSymbols.LAYER_6_PACKAGES.map { packageName -> importOf("$packageName.") }

    /**
     * 계층을 배정받아야 하는 패키지(refactor backlog #84) — :shared commonMain이 선언한 패키지 전부와,
     * 어느 소스 루트에서든 `application.*`로 선언된 패키지다. androidMain·iosMain은 commonMain의
     * `expect`와 같은 패키지라 따로 세지 않아도 들어오고, app-android의 `application.diagnostic`(영구
     * 예외 하나)은 뒤쪽 조건이 잡는다.
     */
    private fun layerAssignablePackages(): Set<String> {
        val commonMain = RepoPaths.sharedCommonMainKotlin
        val packages = SourceSymbolIndex.knownPackages.filter { packageName ->
            packageName.startsWith(APPLICATION_PACKAGE) ||
                SourceSymbolIndex.filesDeclaring(packageName).any { file -> file.startsWith(commonMain) }
        }.toSet()
        require(packages.size > 20) {
            "계층을 배정할 패키지를 거의 못 찾았다(${packages.size}개) — 색인이나 경로가 낡았다."
        }
        return packages
    }

    /**
     * Reports forbidden references in [files].
     *
     * Each [forbiddenImports] entry is written the way an import statement reads
     * (e.g. `importOf(`[ContractSymbols.ENGINE_CORE_API]`)` for an exact type — refactor
     * backlog #69 spells the FQN out via the registry, not as prose text, so a symbol move
     * doesn't leave this KDoc quoting a dead address — or `import android.` for a package
     * prefix). Detection is stronger than a raw
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
     * 루트 패키지(조립 전용)를 참조하는 import와 inline FQN을 찾는다(refactor backlog #25, #77, #79).
     *
     * [forbiddenReferenceOffenders]의 접두사 모델은 그대로 **못 쓴다** — 루트 접두사(`import <root>.`)는
     * 모든 하위 패키지(`ui.`·`platform.`·`engine.` 등)와도 매치한다. 그 하위 패키지들은 각자의
     * forbidden import가 맡으므로 여기서 또 잡으면 이중 판정이다. 그래서 두 갈래로 나눈다.
     *
     * **import 줄**
     *  - `import <root>.<한 조각>`(`as` 별칭·와일드카드 `*` 포함, 줄 끝 앵커)은 **이름을 몰라도** 잡는다.
     *    #25부터 쓰던 정규식 그대로다. 코틀린은 패키지를 import하지 않으니 한 조각 import는 선언일
     *    수밖에 없고, 줄 끝 앵커라 하위 패키지 import(`<root>.ui.X`)와 겹치지 않는다. 그래서 선언
     *    모양(프로퍼티·const·애너테이션 붙은 fun 등)과 무관하게 전부 잡는다.
     *  - `import <root>.<선언>.<멤버>`는 첫 조각이 아래 열거 목록에 있을 때만 잡는다(하위 패키지
     *    이름이면 두 조각 이상이어도 아니다).
     *
     * **inline FQN**(import 없이 코드에서 `<root>.X`를 직접 씀)은 이름을 알아야 하위 패키지와 가를 수
     * 있다. [SourceSymbolIndex.topLevelDeclaredSimpleNames]가 루트 소스에서 나열한 이름 하나하나를
     * [detectForbiddenReference]에 넘긴다 — #79의 인수 기준 "루트 매처도 `detectForbiddenReference`를
     * 탄다". 열거가 읽는 선언 모양과 못 읽는 모양은 [SourceSymbolIndex.topLevelDeclaredSimpleNamesIn]에
     * 적었고, 못 읽는 모양의 선언은 **inline 쪽에서만** 놓친다. 넘기는 줄은 [codeLinesOf]로 import·
     * 주석을 걷어낸 코드 줄이다. import는 위에서 이미 봤으니 두 번 세지 않고, 여러 줄 블록 주석 속
     * 언급도 여기서 걸러진다.
     *
     * ⚠️ 이력 — #79의 첫 구현(`9f81a124`)은 import 정규식을 걷어내고 import까지 열거 목록으로만
     * 찾으면서, 그 목록이 "루트에 실제로 선언된 최상위 심볼 이름 전부"라고 적었다. 사실이 아니었다.
     * 열거는 칼럼 0의 class/interface/object/typealias/fun만 봐서 최상위 `val`/`const val`, 같은 줄
     * 애너테이션 선언, 중첩 제네릭 경계 fun을 놓쳤고, 그 결과 옛 매처가 잡던 import를 놓쳤다. 그래서
     * import 정규식을 되살리고 열거를 넓혔다. [rootPackageMatcherCatchesPropertiesAnnotatedAndGenericBoundDeclarations]가
     * 그 회귀를 막는다.
     *
     * 루트 선언 이름이 하위 패키지 이름과 **같으면**(예: 루트 fun `ui`) inline·멤버 import 쪽이 그
     * 하위 패키지 참조를 잘못 잡는다 — 빨강 쪽 오차다.
     *
     * 루트 이름은 등록부의 [MAIN_ACTIVITY]에서 파생시켜 리터럴을 들지 않는다. `BuildConfig`/`R`은
     * [allowedSimpleNames]로 뺀다. import 정규식은 이름을 가리지 않으므로 이 예외가 실제로 일하고,
     * inline 쪽에도 같은 예외를 적용한다(생성 코드는 소스 색인에 원래 안 나타난다).
     * [rootDeclaredNames]는 자기검증이 합성 선언 목록을 넘길 때만 바꾼다.
     */
    private fun rootPackageReferenceOffenders(
        files: List<File>,
        allowedSimpleNames: Set<String>,
        rootDeclaredNames: Set<String> =
            SourceSymbolIndex.topLevelDeclaredSimpleNames(MAIN_ACTIVITY.substringBeforeLast('.')),
    ): List<String> {
        val root = MAIN_ACTIVITY.substringBeforeLast('.')
        val singleSegmentImport = Regex("""^import\s+${Regex.escape(root)}\.([A-Za-z_]\w*|\*)(?:\s+as\s+\w+)?\s*$""")
        val memberImport = Regex("""^import\s+${Regex.escape(root)}\.([A-Za-z_]\w*)\.""")
        val declaredNames = rootDeclaredNames - allowedSimpleNames
        return files.flatMap { file ->
            val path = file.relativeTo(RepoPaths.root).path
            val lines = file.readLines()

            val importHits = lines
                .map { line -> line.trim() }
                .filter { line ->
                    val single = singleSegmentImport.find(line)?.groupValues?.get(1)
                    val owner = memberImport.find(line)?.groupValues?.get(1)
                    (single != null && single !in allowedSimpleNames) || (owner != null && owner in declaredNames)
                }
                .map { line -> "$path: root-package import -> $line" }

            val codeLines = codeLinesOf(lines)
            // ⚠️ inline은 **합집합**이다(#79 3차 검수). 열거 이름만 보면 열거기가 못 읽는 선언 모양(여러 줄
            // 애너테이션의 닫는 줄에 붙은 선언, 들여쓴 최상위 선언 등)과 `*`로 시작하는 이음 줄(spread·곱셈)을
            // 놓쳐, 이름을 몰라도 대문자 조각이면 잡던 옛 정규식보다 약해진다. 그래서 옛 대문자 정규식을
            // 그대로 함께 돌린다 — 대문자 이름은 옛 매처를 정의상 전부 포함하고, 소문자 top-level 함수는
            // 열거 이름으로 새로 잡는다.
            val uppercaseInline = Regex("""(?<![\w.])${Regex.escape(root)}\.([A-Z]\w*)(?![\w])""")
            val uppercaseNames = codeLines
                .map { line -> stripStringsAndTrailingComment(line) }
                .flatMap { line -> uppercaseInline.findAll(line).map { it.groupValues[1] }.toList() }
                .filterNot { name -> name in allowedSimpleNames }
            val enumeratedNames = declaredNames.filter { name ->
                detectForbiddenReference(codeLines, "import $root.$name").isNotEmpty()
            }
            val inlineHits = (enumeratedNames + uppercaseNames).toSortedSet()
                .map { name -> "$path: fully-qualified reference -> $root.$name" }

            importHits + inlineHits
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
