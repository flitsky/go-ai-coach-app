package com.worksoc.goaicoach.architecture

import com.worksoc.goaicoach.architecture.ShellStateLedger.Construct
import com.worksoc.goaicoach.architecture.ShellStateLedger.DebtState
import com.worksoc.goaicoach.architecture.ShellStateLedger.Role
import com.worksoc.goaicoach.architecture.ShellStateLedger.ShellPolicy
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 셸 상태 원장(refactor backlog #46)의 나머지 그물과 **자기검증**.
 *
 * `GoCoachApp`·`SettingsScreen`·`DeveloperTestSection`의 감사는 옛 이름 그대로
 * `LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`/
 * `settingsScreenStaysAShellAndTheDeveloperSectionStaysItsOwnRole`가 부른다(네 파일이 그 이름을 인용한다).
 * 여기는 조립 루트(MainActivity), 원장 파일 자체, 전역 상태처럼 셸 하나에 묶이지 않는 규칙과,
 * **판정기가 "항상 통과"로 고장나지 않았는지**를 심은 소스로 확인하는 자리다(빈 스캔의 교훈).
 */
class ShellStateLedgerContractTest {

    @Test
    fun compositionRootStaysWithinItsLedger() {
        val offenders = ShellStateLedger.auditShell("mainActivity")
        assertTrue(
            "MainActivity(조립 루트)의 훅 자리가 원장과 어긋났다 — 셸에서 밀어낸 상태가 감시 없는 파일로 가지 않게 " +
                "여기도 같은 원장으로 지킨다(#102의 교훈):\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun everyGuardedShellIsInTheBudgetFileAndBackAgain() {
        val offenders = ShellStateLedger.budgetFileOffenders(ShellStateLedger.budgetFile())
        assertTrue(
            "architecture-budgets.json의 셸 목록·스키마·줄 상한이 ShellStateLedger와 어긋났다:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theBudgetFileIsADeclaredTestInput() {
        // 자기검증 — 선언을 무력화하는 모양(주석·블록 주석·다른 태스크·조건문·optional·속성 이름 없음)은 전부 빨갛다.
        val declaration = "    inputs.file(rootDir.resolve(\"app-android/architecture-budgets.json\"))\n" +
            "        .withPropertyName(\"architectureBudgets\")\n        .withPathSensitivity(PathSensitivity.RELATIVE)\n"
        fun script(body: String, tail: String = ""): String =
            "tasks.withType<Test>().configureEach {\n    maxHeapSize = \"2g\" // https://example.test/a\n$body}\n$tail"
        assertEquals("L17 자기검증: 살아 있는 선언을 못 알아봤다", emptyList<String>(), ShellStateLedger.gradleInputOffenders(script(declaration)))
        val defeated = listOf(
            "line comments" to script(declaration.lines().filter { it.isNotBlank() }.joinToString("\n", postfix = "\n") { "    // ${it.trim()}" }),
            "a block comment" to script("    /*\n$declaration    */\n"),
            "another task" to script("", "tasks.named(\"preBuild\") {\n$declaration}\n"),
            "a condition" to script("    if (false) {\n$declaration    }\n"),
            "optional()" to script(declaration.trimEnd('\n') + "\n        .optional()\n"),
            "no property name" to script("    inputs.file(rootDir.resolve(\"app-android/architecture-budgets.json\"))\n"),
        )
        val missed = defeated.filter { (_, text) -> ShellStateLedger.gradleInputOffenders(text).none { it.startsWith("L17 ") } }.map { it.first }
        assertTrue("L17 자기검증: 무력화된 선언을 선언으로 봤다 — $missed", missed.isEmpty())

        val offenders = ShellStateLedger.gradleInputOffenders(RepoPaths.appAndroidBuildScript.readContractSource())
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun noUnrememberedPlatformInstanceIsAdded() {
        // 자기검증 — 문장 층(if 블록 포함)의 생성자는 잡고, remember 안·콜백 람다 안은 보지 않는다(빈 스캔의 교훈).
        val planted = ShellStateLedger.unrememberedPlatformInstancesIn(
            "package x\n\n@Composable\nfun Shell(context: Context) {\n    val store = FooStore(context)\n" +
                "    val kept = remember(context) { BarStore(context) }\n    if (context != null) {\n        val nested = BazClient()\n    }\n" +
                "    val onTap = { val inCallback = QuxStore(context) }\n}\n",
        )
        assertEquals("L13 자기검증: remember 안 한 저장소 스캐너가 고장났다", listOf("Shell.store", "Shell.nested"), planted)

        val found = ShellStateLedger.unrememberedPlatformInstances()
        val frozen = ShellStateLedger.FROZEN_UNREMEMBERED_INSTANCES
        assertEquals(
            "셸 함수 본문에서 remember 없이 만드는 저장소·클라이언트가 동결 목록과 다르다. 새로 생겼으면 동결 목록에 " +
                "더하지 말고(그것은 옛 '숫자 상향'이라 사용자 결정이 필요하다) remember(context) { … }로 감싸 PLATFORM_PORT 항목으로 " +
                "적는다. 감싸서 사라졌으면 FROZEN_UNREMEMBERED_INSTANCES에서 지운다(줄기만 한다).\n" +
                "  새로 생김: ${(found - frozen).sorted()}\n  사라짐: ${(frozen - found).sorted()}",
            frozen,
            found,
        )
    }

    @Test
    fun noNewProcessGlobalComposeStateAppears() {
        val found = ShellStateLedger.processGlobalState()
        val frozen = ShellStateLedger.FROZEN_PROCESS_GLOBAL_STATE
        assertEquals(
            "프로세스 전역(object·companion·enum·최상위) 컴포즈 상태가 동결 목록과 다르다 — 전역으로 옮기는 것은 원장을 " +
                "피하는 길이 아니다. 새로 생겼으면 동결 목록에 더하지 말 것(옛 '숫자 상향'이라 사용자 결정이 필요하다): 그 상태를 " +
                "쓰는 역할(셸 상태·공급자·홀더)로 옮긴다. 없어졌으면 FROZEN_PROCESS_GLOBAL_STATE에서 지운다(줄기만 한다).\n" +
                "  새로 생김: ${(found - frozen).sorted()}\n  사라짐: ${(frozen - found).sorted()}",
            frozen,
            found,
        )
    }

    @Test
    fun noComposeHookIsImportedUnderAnAlias() {
        assertTrue(
            "별칭 검출기가 고장났다 — 심은 별칭 import를 못 잡는다.",
            ShellStateLedger.aliasedHookImportsIn("import androidx.compose.runtime.remember as keep").isNotEmpty() &&
                ShellStateLedger.aliasedHookImportsIn("import androidx.compose.runtime.snapshots.SnapshotStateList as Bag").isNotEmpty() &&
                ShellStateLedger.aliasedHookImportsIn("import androidx.compose.runtime.remember").isEmpty(),
        )
        val offenders = ShellStateLedger.aliasedHookImports()
        assertTrue(
            "컴포즈 훅을 별칭으로 들여오면 셸 원장의 파서가 그 훅을 못 본다:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun shellHookParserNamesEveryHookSiteOnce() {
        val scan = ShellStateLedger.scan(PLANTED_PARSER_SOURCE, EMPTY_INDEX)
        val described = scan.sites.map { "${it.owner}.${it.name ?: "<anonymous>"} ${it.construct} ${it.ownerKind}" }
        assertEquals(
            "파서가 심은 소스의 훅 자리를 정확히 한 번씩 이름 붙이지 못했다(주석·KDoc·문자열의 훅 단어는 자리가 아니다).",
            listOf(
                "Leaked.flag STATE OBJECT",
                "Shell.scope IDIOM FUNCTION",
                "Shell.port INSTANCE FUNCTION",
                "Shell.destination STATE FUNCTION",
                "Shell.saved STATE FUNCTION",
                "Shell.derived STATE FUNCTION",
                "Shell.collected STATE FUNCTION",
                "Shell.fallback MEMO FUNCTION",
                "Shell.raw STATE FUNCTION",
                "Shell.LaunchedEffect(destination) EFFECT FUNCTION",
                "Shell.wiring STATE FUNCTION",
                "Shell.<anonymous> IDIOM FUNCTION",
                "Shell.<anonymous> MEMO FUNCTION",
                "Shell.s IDIOM FUNCTION",
            ),
            described,
        )
        assertEquals("심은 소스에 파서가 못 읽은 훅 단어가 있다고 한다", emptyList<String>(), scan.parseErrors)
        val destination = scan.sites.single { it.name == "destination" }
        assertEquals("세 줄에 걸친 선언은 한 자리다", listOf("mutableStateOf"), destination.folded)
        assertEquals(listOf("mutableIntStateOf"), scan.sites.single { it.name == "saved" }.folded)

        // 가장자리 — 집합·스냅숏 컬렉션 생성자는 상태, 타입 자리의 SnapshotStateList는 자리가 아니고, FQN 생산자는
        // 접히지 않고 L12, 효과를 숨긴 remember는 숨은 효과, 선언 이름(`fun rememberX`)은 훅이 아니다.
        val edges = ShellStateLedger.scan(PLANTED_PARSER_EDGES, EMPTY_INDEX)
        assertEquals(
            "파서 가장자리 표본이 기대와 다르다",
            listOf(
                "Edges.typed STATE [mutableStateListOf]",
                "Edges.list STATE []",
                "Edges.set STATE [mutableStateSetOf]",
                "Edges.fq MEMO []",
                "Edges.panel HIDDEN_EFFECT [LaunchedEffect]",
            ),
            edges.sites.map { "${it.owner}.${it.name} ${it.construct} ${it.folded}" },
        )
        assertTrue(
            "FQN 생산자를 못 읽은 훅으로 보고하지 않았다(또는 선언 이름을 훅으로 봤다): ${edges.parseErrors}",
            edges.parseErrors.size == 1 && "'mutableStateOf' at line 8" in edges.parseErrors.single(),
        )

        // 실제 트리 닻 — 스캔이 헛돌지 않는다.
        val real = ShellStateLedger.GUARDED_SHELLS.mapValues { (_, shell) ->
            ShellStateLedger.scan(shell.file().readContractSource(), ShellStateLedger.repoIndex, shell.file()).sites
        }
        real.forEach { (id, sites) -> assertTrue("$id 에서 훅 자리를 하나도 못 찾았다 — 스캔이 헛돈다", sites.isNotEmpty()) }
        val app = real.getValue("goCoachApp")
        assertEquals(Construct.STATE, app.single { it.key == "GoCoachScreen.currentDestination" }.construct)
        assertEquals(Construct.MEMO, app.single { it.key == "GoCoachScreen.wiringContext" }.construct)
    }

    @Test
    fun shellLedgerVerdictCatchesPlantedViolations() {
        // 색인(스냅숏 상태 클래스·상태 팩토리)도 심은 소스로 만든다 — 색인이 늘 비는 고장을 여기서 잡는다.
        assertEquals(
            "스냅숏 상태 클래스 색인(여러 줄·한 줄 선언·주 생성자 프로퍼티)",
            setOf("FooUiState", "BarUiState", "CtorFlags"),
            plantedIndex.snapshotStateClasses,
        )
        assertEquals("상태 팩토리 색인(선언된 반환형·식 본문)", setOf("fooState", "boxOf"), plantedIndex.stateFactories)

        assertEquals(
            "깨끗한 심은 셸이 통과하지 못한다 — 판정기가 무엇이든 빨갛게 고장났다",
            emptyList<String>(),
            plantedVerdict(),
        )

        val scope = "    val scope = rememberCoroutineScope()\n"
        val cases = listOf(
            Triple("L1", "new state without an entry", plantedVerdict(insert = "    var extra by remember { mutableStateOf(false) }\n")),
            Triple("L1", "anonymous state in a named argument", plantedVerdict(insert = "    Foo(value = remember { mutableStateOf(0) })\n")),
            Triple("L1", "a set state without an entry", plantedVerdict(insert = "    val tags = remember { mutableStateSetOf<Int>() }\n")),
            Triple("L2", "entry without a site", plantedVerdict(json = { it.put("Shell.ghost", entry("VISIBILITY_GATE")) })),
            Triple("L3", "typo in a shell key", plantedVerdict(shellJson = { it.put("codeLineBuget", 1) })),
            Triple("L3", "unknown kind", plantedVerdict(json = { it.put("Shell.showDialog", entry("SHELL_VISIBILITY")) })),
            Triple("L3", "since is not #NNN", plantedVerdict(json = { it.put("Shell.showDialog", entry("VISIBILITY_GATE", since = "today")) })),
            Triple("L3", "blank why", plantedVerdict(json = { it.put("Shell.showDialog", entry("VISIBILITY_GATE", why = " ")) })),
            Triple("L4", "state labelled PLATFORM_PORT", plantedVerdict(json = { it.put("Shell.showDialog", entry("PLATFORM_PORT")) })),
            Triple("L4", "remember { MutableStateFlow } labelled WIRING", listed("    val flow = remember { MutableStateFlow(0) }\n", "Shell.flow", "WIRING")),
            Triple("L4", "a multi-line snapshot-state class labelled WIRING", listed("    val ui = remember { FooUiState() }\n", "Shell.ui", "WIRING")),
            Triple("L4", "a one-line snapshot-state class labelled WIRING", listed("    val bar = remember { BarUiState() }\n", "Shell.bar", "WIRING")),
            Triple("L4", "a state factory labelled STARTUP_READ", listed("    val made = remember { fooState() }\n", "Shell.made", "STARTUP_READ")),
            Triple("L4", "a run { } factory labelled STARTUP_READ", listed("    val boxed = remember { boxOf(false) }\n", "Shell.boxed", "STARTUP_READ")),
            Triple("L4", "remember { mutableStateSetOf } labelled WIRING", listed("    val tags = remember { mutableStateSetOf<Int>() }\n", "Shell.tags", "WIRING")),
            Triple("L4", "remember { SnapshotStateList() } labelled WIRING", listed("    val bag = remember { SnapshotStateList<Int>() }\n", "Shell.bag", "WIRING")),
            Triple("L4", "remember { toMutableStateList() } labelled WIRING", listed("    val items = remember { listOf(1).toMutableStateList() }\n", "Shell.items", "WIRING")),
            Triple("L4", "a constructor-property snapshot-state class labelled WIRING", listed("    val flags = remember { CtorFlags() }\n", "Shell.flags", "WIRING")),
            Triple("L4", "a chained port load labelled PLATFORM_PORT", listed("    val loaded = remember(context) { FooStore(context).load() }\n", "Shell.loaded", "PLATFORM_PORT")),
            Triple("L5", "GESTURE_COUNTER in the app shell", listed("    var taps by remember { mutableStateOf(0) }\n", "Shell.taps", "GESTURE_COUNTER")),
            Triple("L6", "state in an object inside the shell file", plantedVerdict(append = "\nprivate object Flags {\n    var x by mutableStateOf(false)\n}\n")),
            Triple("L7", "gate initialised to true", plantedVerdict(replace = listOf("mutableStateOf(false) }" to "mutableStateOf(true) }"))),
            Triple("L7", "gate initialised false then overwritten", listed("    var applied by remember { mutableStateOf(false).apply { value = true } }\n", "Shell.applied", "VISIBILITY_GATE")),
            Triple("L7", "gate initialised from debt", listed("    var isFoo by remember { mutableStateOf(legacy > 0) }\n", "Shell.isFoo", "VISIBILITY_GATE")),
            Triple("L7", "a startup read of a local alias of debt", listed("    val alias = legacy\n    val copied = remember { alias + 1 }\n", "Shell.copied", "STARTUP_READ")),
            Triple("L7", "a wiring memo that reads debt", listed("    val legacyText = remember { legacy.toString() }\n", "Shell.legacyText", "WIRING")),
            Triple("L7", "wiring that mirrors state through snapshotFlow", listed("    val dialogFlow = remember { snapshotFlow { showDialog } }\n", "Shell.dialogFlow", "WIRING")),
            Triple("L7", "a mirror collected from a non-wiring flow", listed("    val mirrored by snapshotFlow { legacy }.collectAsState(0)\n", "Shell.mirrored", "HOLDER_MIRROR")),
            Triple("L7", "a port that builds a controller", listed("    val port2 = remember(context) { FooStore(BarController(context)) }\n", "Shell.port2", "PLATFORM_PORT")),
            Triple("L7", "launch hidden inside remember", listed("$scope    val job = remember(holder) { scope.launch { } }\n", "Shell.job", "WIRING")),
            Triple("L7", "launchIn hidden inside remember", listed("$scope    val job = remember { holder.state.onEach { }.launchIn(scope) }\n", "Shell.job", "WIRING")),
            Triple("L7", "stateIn hidden inside remember", listed("$scope    val shared = remember { holder.state.stateIn(scope, SharingStarted.Eagerly, 0) }\n", "Shell.shared", "WIRING")),
            Triple("L7", "launch hidden inside rememberSaveable", listed("$scope    var saved by rememberSaveable { scope.launch { }; mutableStateOf(false) }\n", "Shell.saved", "VISIBILITY_GATE")),
            Triple("L7", "produceState with a decoy gate value", listed("    val produced by produceState(false) {\n        mutableStateOf(false)\n        value = true\n    }\n", "Shell.produced", "VISIBILITY_GATE")),
            Triple("L7", "an effect hidden in remember { movableContentOf { } }", listed("    val panel = remember { movableContentOf { SideEffect { } } }\n", "Shell.panel", "STARTUP_READ")),
            Triple("L7", "a coroutine launched at composition level", plantedVerdict(insert = "$scope    if (showDialog) scope.launch { }\n")),
            Triple("L7", "startup effect that writes shell state", plantedVerdict(replace = listOf("store.warmUp()" to "legacy = store.warmUp()"))),
            Triple("L7", "startup effect that increments shell state", plantedVerdict(replace = listOf("store.warmUp()" to "legacy++"))),
            Triple("L7", "startup effect that writes through a local function", plantedVerdict(insert = "    fun reset() { legacy = 0 }\n", replace = listOf("store.warmUp()" to "reset()"))),
            Triple("L7", "startup effect that writes a state's value", plantedVerdict(insert = "    val box = holder.box\n", replace = listOf("store.warmUp()" to "box.value = 1"))),
            Triple("L7", "startup effect that collects", plantedVerdict(replace = listOf("store.warmUp()" to "store.flow.collect { }"))),
            Triple("L7", "DERIVED_VIEW that is not a derivedStateOf", listed("    var total by remember { mutableStateOf(0) }\n", "Shell.total", "DERIVED_VIEW")),
            Triple("L8", "OWED debt relabelled to an allowed kind", plantedVerdict(json = { it.put("Shell.legacy", entry("VISIBILITY_GATE")) })),
            Triple("L8", "OWED debt deleted from the JSON", plantedVerdict(json = { it.remove("Shell.legacy") })),
            Triple("L8", "new debt", listed("    var fresh by remember { mutableStateOf(0) }\n", "Shell.fresh", "ENGINE_STATE")),
            Triple("L8", "PAID debt comes back under an allowed kind", listed("    var gone by remember { mutableStateOf(false) }\n", "Shell.gone", "VISIBILITY_GATE")),
            Triple("L8", "PAID debt comes back under another owner", plantedVerdict(append = OTHER_OWNER.format("gone"), json = { it.put("Other.gone", entry("VISIBILITY_GATE")) })),
            Triple("L9", "new effect even with an entry", listed("    LaunchedEffect(Unit) { }\n", "Shell.LaunchedEffect(Unit)", "STARTUP_EFFECT")),
            Triple("L9", "a removed effect left the count high", plantedVerdict(effectCount = 3)),
            Triple("L10", "JSON budget above the Kotlin ceiling", plantedVerdict(shellJson = { it.put("codeLineBudget", 31) })),
            Triple("L10", "more code lines than the budget", plantedVerdict(shellJson = { it.put("codeLineBudget", 10) })),
            Triple("L12", "a ::remember reference the parser cannot read", plantedVerdict(insert = "    val f = listOf(1).map(::remember)\n")),
            Triple("L12", "a fully-qualified producer inside remember", listed("    val fq = remember { androidx.compose.runtime.mutableStateOf(false) }\n", "Shell.fq", "WIRING")),
            Triple("L12", "a hook inside a string template", plantedVerdict(insert = "    val s = \"\${remember { 1 }}\"\n")),
            Triple("L12", "a hook inside a string template after a }", plantedVerdict(insert = "    val s = \"\${listOf(1).map { it }.run { remember { mutableStateOf(0) } }.value}\"\n")),
            Triple(
                "L12",
                "states and an effect hidden in remember { movableContentOf { } }",
                listed(
                    "    val panel = remember {\n        movableContentOf {\n            var open by remember { mutableStateOf(false) }\n" +
                        "            LaunchedEffect(open) { }\n        }\n    }\n",
                    "Shell.panel", "VISIBILITY_GATE",
                ),
            ),
            Triple("L12", "a remembered state hidden in an effect key", plantedVerdict(replace = listOf("LaunchedEffect(store) {" to "LaunchedEffect(store, remember { mutableStateOf(0) }) {"))),
            Triple("L15", "state hidden behind a non-hook delegate", plantedVerdict(insert = "    var built by buildFooState()\n")),
            Triple("L15", "an annotated non-hook delegate", plantedVerdict(insert = "    @Suppress(\"x\") var annotated by buildFooState()\n")),
            Triple("L16", "unremembered mutableStateOf", plantedVerdict(insert = "    val raw = mutableStateOf(0)\n")),
            Triple("L16", "unremembered mutableStateSetOf", plantedVerdict(insert = "    val tags = mutableStateSetOf<Int>()\n")),
            Triple("L16", "unremembered SnapshotStateList()", plantedVerdict(insert = "    val bag = SnapshotStateList<Int>()\n")),
            Triple("L16", "unremembered toMutableStateMap()", plantedVerdict(insert = "    val byId = mapOf(1 to 2).toMutableStateMap()\n")),
            Triple("L16", "unremembered MutableStateFlow", plantedVerdict(insert = "    val flow = MutableStateFlow(0)\n")),
            Triple("L16", "unremembered MutableStateFlow inside an if block", plantedVerdict(insert = "    if (showDialog) {\n        val nestedFlow = MutableStateFlow(0)\n    }\n")),
            Triple(
                "L18",
                "a pinned read becomes a hook site",
                plantedVerdict(
                    replace = listOf("val engineName = identity.name" to "val engineName = remember { identity.name }"),
                    json = { it.put("Shell.engineName", entry("STARTUP_READ")) },
                ),
            ),
        )
        val misses = cases.filter { (rule, _, offenders) -> offenders.none { it.startsWith("$rule ") } }
            .map { (rule, name, offenders) -> "$rule ($name) not raised; got $offenders" }
        assertTrue("판정기가 심은 위반을 놓쳤다:\n${misses.joinToString("\n")}", misses.isEmpty())

        // 저장소가 관용구 이름을 정의하면 관용구가 아니다(가림 방지).
        val shadowIndex = ShellStateLedger.RepoIndex(mapOf("rememberScrollState" to setOf("<planted>")), emptySet(), emptySet())
        assertTrue(
            "저장소가 정의한 rememberScrollState를 관용구로 넘겼다",
            plantedVerdict(insert = "    var sneaky by rememberScrollState()\n", index = shadowIndex).any { it.startsWith("L1 ") },
        )
        // 같은 파일에 선언한 remember* 함수의 이름은 훅 자리가 아니다(엉뚱한 L6 메시지 방지).
        val sameFileHelper = plantedVerdict(append = "\n@Composable\nprivate fun rememberShellFlag(): Boolean = true\n")
        assertEquals("선언 이름을 훅으로 봤다", emptyList<String>(), sameFileHelper)

        // 메시지가 가리키는 길 — 붙여 넣을 줄은 모양 검사까지 통과하는 종류가 있을 때만, 빚은 옮겨 적기로.
        val unlisted = plantedVerdict(insert = "    var extra by remember { mutableStateOf(false) }\n").single { it.startsWith("L1 ") }
        assertTrue(
            "L1은 먼저 역할을 묻고, 모양 검사를 통과하는 종류로 붙여 넣을 줄을 준다: $unlisted",
            "first ask whether the role that uses this state should own it" in unlisted &&
                "\"Shell.extra\": { \"kind\": \"VISIBILITY_GATE\", \"since\": \"#NNN\", \"why\": \"\" }" in unlisted,
        )
        val noFit = plantedVerdict(insert = "    var counter by remember { mutableStateOf(5) }\n").single { it.startsWith("L1 ") }
        assertTrue("맞는 종류가 없으면 붙여 넣을 줄을 주지 않는다: $noFit", "\"kind\"" !in noFit && "there is no JSON line" in noFit)
        val renamed = plantedVerdict(replace = listOf("var legacy by" to "var legacyRenamed by"))
        assertTrue(
            "부채 이름을 바꾸면 L1은 옮겨 적기를 말하고 허용 종류를 권하지 않으며, L2는 PAID/옮겨 적기를 함께 말한다: $renamed",
            renamed.any { it.startsWith("L1 ") && "carry the debt 1:1" in it && "\"kind\"" !in it } &&
                renamed.any { it.startsWith("L2 ") && "PAID" in it && "rename the key 1:1" in it },
        )
        val moved = plantedVerdict(replace = listOf("    var legacy by remember { mutableStateOf(0) }\n" to ""), append = OTHER_OWNER.format("legacy"))
        assertTrue(
            "부채의 소유 함수를 옮기면 L1은 SHELL_DEBT 키를 1:1로 옮기라고 한다: $moved",
            moved.any { it.startsWith("L1 ") && "Rename Shell.legacy to Other.legacy in ShellStateLedger.SHELL_DEBT" in it },
        )
        val relabelled = plantedVerdict(json = { it.put("Shell.legacy", entry("VISIBILITY_GATE")) }).single { it.startsWith("L8 ") }
        assertTrue("L8은 PAID가 '이 파일을 떠났을 때만'이라고 말한다: $relabelled", "only when the state has left this file" in relabelled)
        val newEffect = plantedVerdict(insert = "    LaunchedEffect(Unit) { }\n").single { it.startsWith("L1 ") }
        assertTrue("새 효과에는 붙여 넣을 줄을 주지 않는다(L9 사용자 결정): $newEffect", "\"kind\"" !in newEffect && "needs a user decision" in newEffect)
        val swapped = plantedVerdict(replace = listOf("LaunchedEffect(store) {" to "LaunchedEffect(Unit) {")).single { it.startsWith("L1 ") }
        assertTrue(
            "효과를 맞바꾸면(수 그대로) 붙여 넣을 줄 없이 사용자 결정과 맞바꾸기를 말한다: $swapped",
            "\"kind\"" !in swapped && "needs a user decision" in swapped && "swapping one effect for another" in swapped,
        )
        val effectAdded = plantedVerdict(insert = "    LaunchedEffect(Unit) { }\n", json = { it.put("Shell.LaunchedEffect(Unit)", entry("STARTUP_EFFECT")) })
            .single { it.startsWith("L9 ") }
        assertTrue("L9는 새 효과가 사용자 결정이라고 말한다: $effectAdded", "needs a user decision" in effectAdded)
        assertTrue("L9는 줄어든 효과 수를 숫자로 알려 준다", plantedVerdict(effectCount = 3).single { it.startsWith("L9 ") }.contains("to 2"))
        val roleClosed = listed("    var taps by remember { mutableStateOf(0) }\n", "Shell.taps", "GESTURE_COUNTER").single { it.startsWith("L5 ") }
        assertTrue("L5는 역할 제한이 닫혀 있다고 말한다: $roleClosed", "move the state to the role that owns it" in roleClosed)

        val greens = listOf(
            "a new gate with a reviewed JSON line" to listed("    var isFoo by remember { mutableStateOf(false) }\n", "Shell.isFoo", "VISIBILITY_GATE", since = "#45"),
            "a gate with a type argument" to listed("    var isBar by remember { mutableStateOf<Boolean>(false) }\n", "Shell.isBar", "VISIBILITY_GATE"),
            "a derived view" to listed("    val total by remember { derivedStateOf { 1 + 1 } }\n", "Shell.total", "DERIVED_VIEW"),
            "a mirror collected from a port" to listed("    val stored by store.flow.collectAsState(0)\n", "Shell.stored", "HOLDER_MIRROR"),
            "idioms are not listed" to plantedVerdict(insert = "    val scroll = rememberScrollState()\n$scope"),
            "a coroutine launched from a callback" to plantedVerdict(insert = "$scope    val onTap: () -> Unit = { scope.launch { } }\n"),
            "a debt carried 1:1 to a new name" to plantedVerdict(
                replace = listOf("var legacy by" to "var legacyRenamed by"),
                json = {
                    it.remove("Shell.legacy")
                    it.put("Shell.legacyRenamed", entry("DOMAIN_STATE"))
                },
                debt = mapOf("Shell.legacyRenamed" to DebtState.OWED, "Shell.gone" to DebtState.PAID),
            ),
            "a hook word in a comment, a string and a return label" to plantedVerdict(
                replace = listOf(
                    "store.warmUp()" to "store.warmUp()\n        if (false) return@LaunchedEffect\n        // remember { mutableStateOf(1) }\n" +
                        "        val note = \"remember(x) LaunchedEffect(y)\"",
                ),
            ),
        )
        greens.forEach { (name, offenders) -> assertEquals("$name must stay green", emptyList<String>(), offenders) }

        val missingShell = JSONObject(ShellStateLedger.budgetFile().toString()).apply { getJSONObject("shells").remove("mainActivity") }
        assertTrue(
            "L11: 셸이 빠진 원장을 통과시켰다",
            ShellStateLedger.budgetFileOffenders(missingShell).any { it.startsWith("L11 ") },
        )

        val duplicate = runCatching { JSONObject("{\"a\":1,\"a\":2}") }.exceptionOrNull()
        assertTrue("org.json이 중복 키에서 던지지 않는다 — 같은 자리를 두 번 적어도 조용히 덮인다", duplicate is JSONException)
    }

    @Test
    fun processGlobalScannerCatchesPlantedGlobals() {
        fun globals(source: String, stem: String = "Planted"): List<String> = ShellStateLedger.processGlobalStateIn(source, stem)
        val red = listOf(
            "G1" to globals("internal object FooSignal { var v by mutableStateOf(0) }\n") to listOf("FooSignal.v"),
            "G1 over lines" to globals("internal object FooSignal {\n    var v by mutableStateOf(0)\n}\n") to listOf("FooSignal.v"),
            "G1 mixed" to globals("object Mixed { val a = 1; val b = mutableStateOf(0) }\n") to listOf("Mixed.b"),
            "G2" to globals("class Host {\n    companion object {\n        val s = mutableStateListOf<Int>()\n    }\n}\n") to listOf("Host.Companion.s"),
            "G3" to globals("var leak: MutableState<Int>? = null\n", "Leak") to listOf("LeakKt.leak"),
            "G3 primitive" to globals("object T { var s: MutableIntState? = null }\n") to listOf("T.s"),
            "G4" to globals("object Bus {\n    private val _s = MutableStateFlow(0)\n}\n") to listOf("Bus._s"),
            "G8" to globals("val LocalFoo = compositionLocalOf { mutableStateOf(0) }\n", "Foo") to listOf("FooKt.LocalFoo"),
            "set" to globals("object SetBus {\n    val tags = mutableStateSetOf<Int>()\n}\n") to listOf("SetBus.tags"),
            "set type" to globals("object SetT {\n    var s: SnapshotStateSet<Int>? = null\n}\n") to listOf("SetT.s"),
            "snapshot list constructor" to globals("object ListT {\n    val s = SnapshotStateList<Int>()\n}\n") to listOf("ListT.s"),
            "fully-qualified producer" to globals("object FqnLeak { val v = androidx.compose.runtime.mutableIntStateOf(0) }\n") to listOf("FqnLeak.v"),
            "one step removed" to globals("object AppSignals {\n    val splash = SplashFlags()\n}\n\nclass SplashFlags {\n    var isShowing by mutableStateOf(false)\n}\n") to
                listOf("AppSignals.splash"),
            "one step removed, one line" to globals("object Signals { val flags = Flags() }\nclass Flags { var on by mutableStateOf(false) }\n") to
                listOf("Signals.flags"),
            "one step removed, constructor property" to
                globals("object S { val f = CtorFlags() }\nclass CtorFlags(val on: MutableState<Boolean> = mutableStateOf(false))\n") to listOf("S.f"),
            "enum singleton" to globals("enum class EnumLeak {\n    INSTANCE;\n\n    var on by mutableStateOf(false)\n}\n") to listOf("EnumLeak.on"),
        )
        red.forEach { (case, expected) ->
            assertEquals("${case.first}: 전역 상태를 못 잡았다", expected, case.second)
        }
        val green = listOf(
            "G5" to globals("object Once {\n    var played = false\n}\n"),
            "G6" to globals("object Ev {\n    private val e = MutableSharedFlow<Unit>()\n}\n"),
            "G7" to globals("object Holder {\n    fun f() {\n        val s = mutableStateOf(0)\n    }\n}\n"),
            "plain instance" to globals("object Plain { val helper = PlainHelper() }\nclass PlainHelper { val x = 1 }\n"),
            "class member" to globals("class Owner {\n    var s by mutableStateOf(0)\n}\n"),
        )
        green.forEach { (case, found) -> assertEquals("$case: 전역 상태가 아닌 것을 잡았다", emptyList<String>(), found) }
    }

    private fun entry(kind: String, since: String = "#46", why: String = "planted"): JSONObject =
        JSONObject().put("kind", kind).put("since", since).put("why", why)

    /** 한 줄을 심고 그 자리를 [kind]로 적어 판정한다. */
    private fun listed(insert: String, key: String, kind: String, since: String = "#46"): List<String> =
        plantedVerdict(insert = insert, json = { it.put(key, entry(kind, since = since)) })

    /** 깨끗한 심은 셸에 변이 하나를 걸어 판정한다. */
    private fun plantedVerdict(
        insert: String = "",
        append: String = "",
        replace: List<Pair<String, String>> = emptyList(),
        json: (JSONObject) -> Unit = {},
        shellJson: (JSONObject) -> Unit = {},
        effectCount: Int = 2,
        debt: Map<String, DebtState> = mapOf("Shell.legacy" to DebtState.OWED, "Shell.gone" to DebtState.PAID),
        index: ShellStateLedger.RepoIndex = plantedIndex,
    ): List<String> {
        var source = PLANTED_SHELL_SOURCE.replace(PLANTED_ANCHOR, PLANTED_ANCHOR + insert) + append
        for ((old, new) in replace) {
            check(old in source) { "planted replace anchor missing: $old" }
            source = source.replaceFirst(old, new)
        }
        val sites = JSONObject()
            .put("Shell.store", entry("PLATFORM_PORT"))
            .put("Shell.holder", entry("WIRING"))
            .put("Shell.destination", entry("NAVIGATION"))
            .put("Shell.showDialog", entry("VISIBILITY_GATE"))
            .put("Shell.legacy", entry("DOMAIN_STATE"))
            .put("Shell.mirror", entry("HOLDER_MIRROR"))
            .put("Shell.LaunchedEffect(holder)", entry("HOLDER_MIRROR"))
            .put("Shell.LaunchedEffect(store)", entry("STARTUP_EFFECT"))
        json(sites)
        val shell = JSONObject().put("codeLineBudget", 20).put("sites", sites)
        shellJson(shell)
        val policy = ShellPolicy(
            role = Role.APP_SHELL,
            lineBudgetCeiling = 30,
            effectCount = effectCount,
            debt = debt,
            pinned = setOf("Shell.engineName"),
        )
        return ShellStateLedger.verdict(source, shell, policy, index)
    }

    private val plantedIndex: ShellStateLedger.RepoIndex = ShellStateLedger.buildRepoIndex(PLANTED_INDEX_SOURCES.map { null to it })

    private companion object {
        val EMPTY_INDEX = ShellStateLedger.RepoIndex(emptyMap(), emptySet(), emptySet())

        /** 파서 표본 — 앱 FQN 없이, 주석·KDoc·문자열의 훅 단어까지 담는다. */
        val PLANTED_PARSER_SOURCE = """
            package x

            internal object Leaked {
                var flag by mutableStateOf(false)
            }

            @Composable
            internal fun Shell(store: Port) {
                val scope = rememberCoroutineScope()
                val port = remember(context) { FooStore(context) }
                var destination by remember {
                    mutableStateOf(initialDestination(port, false))
                }
                var saved by rememberSaveable { mutableIntStateOf(0) }
                val derived by remember { derivedStateOf { saved + 1 } }
                val collected by holder.state.collectAsState()
                val fallback = signal
                    ?: remember(context) { load(context) }
                val raw = mutableStateOf(0)
                LaunchedEffect(
                    destination,
                    saved,
                ) {
                    if (saved == 0) return@LaunchedEffect
                }
                val wiring = remember(saved) {
                    object : Api { override val s = mutableStateOf(1) }
                }
                Row(modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
                Foo(value = remember { 3 })
                // remember { mutableStateOf(9) }
                /** KDoc: LaunchedEffect(x) { } */
                val text = "remember(1) LaunchedEffect(2)"
                val s = rememberScrollState()
            }
        """.trimIndent() + "\n"

        /** 파서 가장자리 표본 — 8번째 줄이 FQN 생산자다(메시지의 줄 번호를 못박는다). */
        val PLANTED_PARSER_EDGES = """
            package x

            @Composable
            internal fun Edges() {
                val typed: SnapshotStateList<Int> = remember { mutableStateListOf() }
                val list = SnapshotStateList<Int>()
                val set = remember { mutableStateSetOf<Int>() }
                val fq = remember { androidx.compose.runtime.mutableStateOf(0) }
                val panel = remember { movableContentOf { LaunchedEffect(Unit) { } } }
            }

            @Composable
            private fun rememberEdgeFlag(): Boolean = true
        """.trimIndent() + "\n"

        /** 판정 자기검증의 깨끗한 셸(APP_SHELL, 효과 2, OWED 1·PAID 1, 고정 이름 engineName). */
        val PLANTED_SHELL_SOURCE = """
            package x

            @Composable
            internal fun Shell(identity: Identity) {
                val context = LocalContext.current
                val store = remember(context) { FooStore(context) }
                val holder = remember { FooHolder() }
                var destination by remember { mutableStateOf(ScreenDestination.Home) }
                var showDialog by remember { mutableStateOf(false) }
                var legacy by remember { mutableStateOf(0) }
                var mirror by remember { mutableStateOf(holder.current) }
                LaunchedEffect(holder) {
                    holder.state.collect { mirror = it }
                }
                LaunchedEffect(store) {
                    store.warmUp()
                }
                val engineName = identity.name
            }
        """.trimIndent() + "\n"

        const val PLANTED_ANCHOR = "    var mirror by remember { mutableStateOf(holder.current) }\n"

        /** 같은 셸 파일의 다른 소유 함수 — `%s`에 바인딩 이름. */
        const val OTHER_OWNER = "\n@Composable\nprivate fun Other() {\n    var %s by remember { mutableStateOf(0) }\n}\n"

        /** 색인 자기검증용 저장소 — 스냅숏 상태 클래스(여러 줄·한 줄·여러 줄 주 생성자)와 상태 팩토리(선언된 반환형·식 본문). */
        val PLANTED_INDEX_SOURCES = listOf(
            "package x\n\nclass FooUiState {\n    var x by mutableStateOf(0)\n}\n",
            "package x\n\nclass BarUiState { var y by mutableStateOf(0) }\n",
            "package x\n\nclass CtorFlags(\n    val on: MutableState<Boolean> = mutableStateOf(false),\n)\n",
            "package x\n\nfun fooState(): MutableState<Int> = mutableStateOf(0)\n",
            "package x\n\nfun <T> boxOf(v: T) = run { mutableStateOf(v) }\n",
            "package x\n\nclass PlainUiState(val x: Int)\n\nfun plainValue(): Int = 0\n",
        )
    }
}
