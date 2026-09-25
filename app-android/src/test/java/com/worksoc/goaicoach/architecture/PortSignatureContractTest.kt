package com.worksoc.goaicoach.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **포트 시그니처 가드**(refactor backlog #83) — `docs/ARCHITECTURE.md` 4계층 「포트가 아는 것」의 ⓐ를 기계로 잰다.
 *
 * ## 무엇을 막는가
 * 포트는 위 계층의 **값**은 알아도 되지만 **동작**은 몰라야 한다. 그래서 포트 시그니처의 매개변수·반환 타입은
 * 필드 폐포가 값이어야 한다 — 폐포 안에 함수·인터페이스·`var`·가변 컨테이너·포트/클라이언트/컨트롤러가 없어야
 * 한다. 이 규칙은 `#73`이 문서로 정했고 그때 사람이 잰 실측(계약 20개, 폐포 69타입, 위반 0)이 이 가드의 첫
 * 기준선이다(로드맵 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 4계층 「포트가 아는 것」).
 *
 * ## 왜 import 규칙이 아닌가
 * 포트 19개 중 8개가 자기가 싣는 값과 **같은 패키지**에 있어 import 줄이 없다. 패키지 규칙은 같은 패키지의
 * 정책 객체·컨트롤러를 시그니처에 싣는 것까지 허용한다. 그래서 [SourceSymbolIndex.typeDeclarations]로 타입을
 * **선언까지 풀어** 선언 종류와 필드를 본다. 판정은 [PortSignatureAudit], 색인은 [TypeDeclarationIndex]에 있다.
 *
 * ## 대상
 * 이름이 `Port`로 끝나는 인터페이스 전부와, 이름 패턴 밖에서 [ContractSymbols.PORT_CONTRACTS_OUTSIDE_NAME_PATTERN]에
 * 등록한 계약(`PositionAnalysisCacheStore`). 계약 수는 [EXPECTED_CONTRACT_COUNT]로 못박는다 — 포트가 이름을
 * 바꿔 패턴을 벗어나면 조용히 검사 밖으로 나가기 때문이다.
 *
 * ## 이 가드가 조용히 초록이 되지 않게(함정 24·76)
 *  - 계약 수를 못박고, 계약마다 멤버 수를 파서와 따로 센 낱말 수와 대조한다 — 계약이나 멤버 하나가 파서 밖으로
 *    빠지면 그 안의 위반도 빠진다.
 *  - 합성 소스에 위반을 심어 빨강을 보이고, 실제 코드의 요청 객체(클라이언트·람다를 담은)를 받는 가짜 계약을
 *    실제 색인 위에 얹어 빨강을 보인다.
 *  - 본문 저장 프로퍼티까지 따라간 폐포가 주 생성자만 따라간 폐포보다 커야 한다(`#73` 실측 69는 후자다).
 *
 * ## 범위 밖
 * ⓑ(어댑터 구현이 부르는 것 — 위 계층 **동작**을 부르지 않는가, 관측 포트의 쓰기만 예외)는 이 가드가 재지 않는다.
 */
class PortSignatureContractTest {

    /**
     * **기준선 — 지금 코드의 계약 전부가 ⓐ를 지킨다(위반 0).**
     *
     * ⚠️ 여기가 빨개졌다면 위반을 기준선에 적어 초록을 만들지 마라. 값이 아닌 것을 포트가 받거나 돌려주게 된
     * 것이다 — 콜백은 값의 흐름으로, 클라이언트·컨트롤러는 포트 밖으로, `var`는 `val`로 고친다. 규칙은
     * `docs/ARCHITECTURE.md` 4계층 「포트가 아는 것」 ⓐ에 있다.
     */
    @Test
    fun everyPortSignatureCarriesOnlyValues() {
        assertEquals(
            "포트 시그니처가 값이 아닌 것을 싣는다(docs/ARCHITECTURE.md 4계층 「포트가 아는 것」 ⓐ). 경로는 " +
                "`계약.메서드(매개변수) › 타입.필드`다.\n" + realReport.violations.joinToString("\n") { "  - $it" } + baselineSummary(),
            emptyList<String>(),
            realReport.violations,
        )
    }

    /**
     * 스캔이 **계약을 다 봤는가**. 계약이 줄면 이름이 패턴을 벗어났을 수 있고(그러면 초록인 채 검사 밖이다),
     * 늘면 새 포트가 생긴 것이다 — 어느 쪽이든 [EXPECTED_CONTRACT_COUNT]를 의식적으로 고친다.
     */
    @Test
    fun scanFindsEveryContractAndWalksANonTrivialClosure() {
        assertEquals(
            "명시 등록한 계약을 색인에서 인터페이스로 찾지 못했다 — 옮겨졌거나 이름이 바뀌었다(ContractSymbols 갱신).",
            emptyList<String>(),
            realContracts.missingRegistrations,
        )
        assertEquals(
            "계약 수가 기준선과 다르다 — 새 포트를 만들었거나 지웠다면 EXPECTED_CONTRACT_COUNT를 고치고, 이름이 " +
                "Port로 끝나지 않는 계약이면 ContractSymbols.PORT_CONTRACTS_OUTSIDE_NAME_PATTERN에 등록하라.\n" +
                realContracts.contracts.joinToString("\n") { "  - ${it.fqn}" },
            EXPECTED_CONTRACT_COUNT,
            realContracts.contracts.size,
        )
        val memberless = realContracts.contracts.filter { it.functions.isEmpty() && it.bodyProperties.isEmpty() }.map { it.fqn }
        assertEquals("멤버를 하나도 못 읽은 계약이 있다 — 파서가 본문을 놓쳤다.", emptyList<String>(), memberless)
        assertTrue(
            "폐포가 거의 비었다(${realReport.closure.size}타입) — 시그니처 타입을 선언까지 풀지 못하고 있다.",
            realReport.closure.size > 60,
        )
    }

    /**
     * 파서가 계약의 멤버를 **조용히 덜 읽지 않는가**(함정 76). 계약마다 본문 첫 깊이의 `fun`·`val`·`var` 낱말을
     * 파서와 따로 세어, 파서가 읽은 함수·프로퍼티 수와 같아야 한다. 여러 줄 시그니처·애너테이션 줄·기본값에서
     * 파서가 멤버 하나를 흘리면 그 멤버에 든 위반은 초록인 채 검사 밖에 남는다 — 계약 수(20)와 "멤버 0개" 검사는
     * 계약 하나가 통째로 빠지는 것만 잡고 멤버 하나가 빠지는 것은 못 잡는다.
     */
    @Test
    fun parserReadsEveryMemberOfEveryContract() {
        val mismatches = realContracts.contracts.mapNotNull { contract ->
            val file = contract.location.substringBeforeLast(':')
            val line = contract.location.substringAfterLast(':').toInt()
            val code = PackageImportGraph.stripCommentsAndStrings(RepoPaths.root.resolve(file).readText().replace("\r\n", "\n"))
            val counted = memberKeywordCounts(code, line)
            val parsed = contract.functions.size to contract.bodyProperties.size
            if (counted == parsed) {
                null
            } else {
                "${contract.fqn} (${contract.location}): 낱말 fun ${counted.first}·val/var ${counted.second}, " +
                    "파서 함수 ${parsed.first}·프로퍼티 ${parsed.second}"
            }
        }
        assertEquals(
            "파서가 계약의 멤버를 낱말 수와 다르게 읽었다 — TypeDeclarationIndex의 문장 나누기가 이 모양을 모른다.",
            emptyList<String>(),
            mismatches,
        )
        val members = realContracts.contracts.sumOf { it.functions.size + it.bodyProperties.size }
        assertTrue("계약 멤버를 거의 못 읽었다(${members}개)", members >= realContracts.contracts.size * 2)
    }

    /** 함수 타입 매개변수의 등록부가 실제 시그니처와 **같다** — 사라진 자리를 등록부에 남겨 두지 않는다. */
    @Test
    fun registeredPureTransformsMatchTheSignatures() {
        assertEquals(
            "함수 타입 매개변수 등록부가 실제 시그니처와 다르다 — 사라진 자리는 REGISTERED_PURE_TRANSFORMS에서 지워라.",
            REGISTERED_PURE_TRANSFORMS,
            realReport.functionTypeParameters.toSet(),
        )
    }

    /**
     * 본문 저장 프로퍼티(`val x: T = …`)도 필드다(원칙 문서 ⓐ). 실제 코드에서 본문까지 따라간 폐포가 주 생성자만
     * 따라간 폐포보다 **커야** 한다 — 같다면 색인이 본문 프로퍼티를 못 읽고 있는 것이다(`#73` 실측의 69는 주
     * 생성자만 따라간 수였고, `PlayLevelSetting.selectionPolicy` 같은 본문 필드를 세지 않았다).
     */
    @Test
    fun closureFollowsBodyStoredPropertiesOnRealCode() {
        val primaryOnly = PortSignatureAudit(SourceSymbolIndex.typeDeclarations, REGISTERED_PURE_TRANSFORMS, followBodyProperties = false)
            .audit(realContracts.contracts)

        assertTrue(
            "본문 저장 프로퍼티를 따라간 폐포(${realReport.closure.size})가 주 생성자만 따라간 폐포(${primaryOnly.closure.size})보다 " +
                "크지 않다 — 색인이 본문 프로퍼티를 못 읽는다.",
            realReport.closure.keys.containsAll(primaryOnly.closure.keys) && realReport.closure.size > primaryOnly.closure.size,
        )
    }

    /** 색인 자기검증 — 선언 종류·주 생성자 필드·본문 프로퍼티의 저장 방식·멤버 시그니처를 읽는다. */
    @Test
    fun indexReadsDeclarationKindsAndFieldTypes() {
        val pkg = "$ROOT.fixture83.index"
        val declarations = SourceSymbolIndex.typeDeclarationsIn(
            "Index.kt",
            """
            package $pkg

            import kotlinx.coroutines.flow.Flow as Stream

            @Suppress("unused") data class D(val a: Int, var b: String?, c: Long) {
                val stored: Int = 1
                val computed: Int get() = a
                val computedOnNextLine: Int
                    get() = a
                val delegated: Int by lazy { 1 }
                lateinit var late: String
                private val hidden: List<String> = listOf("x")
                val Int.extension: Int get() = this
                companion object { val shared: Int = 0 }
                fun helper(x: Int): Int { val local = x; return local }
                init { val alsoLocal = 1 }
                enum class Nested { A, B }
            }
            enum class E(val label: String) { A("a"), B("b"); val upper: String = label }
            sealed class S { abstract val d: String; data object O : S() { override val d: String = "o" } }
            sealed interface SI
            @JvmInline
            value class V(val raw: Int)
            data object DO
            interface I {
                val p: Int
                fun f(a: Int, b: (Int) -> Int?): Stream<List<Int>>
                suspend fun g(): Result<Unit> = Result.success(Unit)
                fun h() {}
            }
            fun interface FI { fun call() }
            interface ML {
                @Throws(IllegalStateException::class)
                suspend fun save(
                    @Suppress("unused") first: D,
                    rest: Map<
                        String,
                        List<Int>,
                        >? = null,
                    nowMillis: Long = if (0 < 1) 0L else 1L,
                    vararg tags: String,
                ): List<
                    D,
                    >

                val flag: Boolean
            }
            object Obj
            class Plain(val x: Int)
            abstract class Base
            annotation class Ann
            typealias TA = Map<String, D>
            @OptIn(ExperimentalStdlibApi::class) data class Annotated(val x: Int)
            """.trimIndent(),
        )
        val bySimpleName = declarations.associateBy { it.fqn.removePrefix("$pkg.") }

        assertEquals(
            mapOf(
                "D" to DeclarationKind.DATA_CLASS, "D.Companion" to DeclarationKind.OBJECT, "D.Nested" to DeclarationKind.ENUM_CLASS,
                "E" to DeclarationKind.ENUM_CLASS, "S" to DeclarationKind.SEALED_CLASS, "S.O" to DeclarationKind.DATA_OBJECT,
                "SI" to DeclarationKind.SEALED_INTERFACE, "V" to DeclarationKind.VALUE_CLASS, "DO" to DeclarationKind.DATA_OBJECT,
                "I" to DeclarationKind.INTERFACE, "FI" to DeclarationKind.FUN_INTERFACE, "ML" to DeclarationKind.INTERFACE,
                "Obj" to DeclarationKind.OBJECT,
                "Plain" to DeclarationKind.CLASS, "Base" to DeclarationKind.CLASS, "Ann" to DeclarationKind.ANNOTATION_CLASS,
                "TA" to DeclarationKind.TYPEALIAS, "Annotated" to DeclarationKind.DATA_CLASS,
            ),
            bySimpleName.mapValues { it.value.kind },
        )
        val d = bySimpleName.getValue("D")
        assertEquals(listOf("a" to false, "b" to true), d.constructorFields.map { it.name to it.isMutable })
        assertEquals(listOf("Int", "String?"), d.constructorFields.map { it.type?.render() })
        assertEquals(
            mapOf(
                "stored" to PropertyStorage.STORED, "computed" to PropertyStorage.COMPUTED,
                "computedOnNextLine" to PropertyStorage.COMPUTED, "delegated" to PropertyStorage.STORED,
                "late" to PropertyStorage.STORED, "hidden" to PropertyStorage.STORED, "extension" to PropertyStorage.COMPUTED,
            ),
            d.bodyProperties.associate { it.name to it.storage },
        )
        assertEquals(listOf("a", "b", "stored", "delegated", "late", "hidden"), d.storedFields.map { it.name })
        assertEquals("List<String>", d.bodyProperties.single { it.name == "hidden" }.type?.render())
        assertEquals(listOf("helper"), d.functions.map { it.name })
        assertEquals(listOf("upper"), bySimpleName.getValue("E").storedFields.drop(1).map { it.name })
        assertEquals(PropertyStorage.ABSTRACT, bySimpleName.getValue("S").bodyProperties.single().storage)
        assertEquals(listOf("S"), bySimpleName.getValue("S.O").supertypes.map { it.render() })
        assertEquals("생성자 호출이 붙은 상위 타입이 상위 클래스다", "S", bySimpleName.getValue("S.O").superclass?.render())
        assertEquals(null, bySimpleName.getValue("D").superclass)
        assertEquals(listOf("raw"), bySimpleName.getValue("V").storedFields.map { it.name })

        val i = bySimpleName.getValue("I")
        assertEquals(PropertyStorage.ABSTRACT, i.bodyProperties.single().storage)
        val f = i.functions.single { it.name == "f" }
        assertEquals(listOf("Int", "(Int) -> Int?"), f.parameters.map { it.type.render() })
        assertEquals("Stream<List<Int>>", f.returnType?.render())
        assertTrue("suspend를 읽지 못했다", i.functions.single { it.name == "g" }.isSuspend)
        assertEquals("Result<Unit>", i.functions.single { it.name == "g" }.returnType?.render())
        assertEquals("Unit", i.functions.single { it.name == "h" }.returnType?.render())
        assertEquals("Map<String, D>", bySimpleName.getValue("TA").aliasedType?.render())

        // 여러 줄 시그니처 — 애너테이션 줄, 매개변수 애너테이션, 여러 줄 타입 인자와 끝 쉼표, `<`가 든 기본값, vararg.
        val ml = bySimpleName.getValue("ML")
        val save = ml.functions.single()
        assertEquals(
            listOf("first" to "D", "rest" to "Map<String, List<Int>>?", "nowMillis" to "Long", "tags" to "Array<String>"),
            save.parameters.map { it.name to it.type.render() },
        )
        assertEquals("List<D>", save.returnType?.render())
        assertTrue("suspend를 읽지 못했다", save.isSuspend)
        assertEquals(listOf("flag"), ml.bodyProperties.map { it.name })

        // 별칭 import와 둘러싼 선언의 중첩 타입이 풀린다.
        val index = TypeDeclarationIndex(declarations)
        assertEquals(
            Resolution.Standard("kotlinx.coroutines.flow.Flow", StandardTypeCategory.READ_ONLY_FLOW),
            index.resolve(f.returnType as TypeRef.Named, i.memberScope, emptySet()),
        )
        assertEquals(
            "$pkg.D.Nested",
            (index.resolve(TypeRef.parse("Nested") as TypeRef.Named, d.memberScope, emptySet()) as Resolution.Declared).fqn,
        )
        assertEquals(listOf("$pkg.S.O"), index.sealedSubtypes(bySimpleName.getValue("S")).map { it.fqn })
    }

    /**
     * **자기검증(함정 24: 초록은 안전이 아니다)** — 합성 소스에 위반을 심으면 판정기가 빨갛게 보는가.
     *
     * 계약과 값이 **같은 패키지**에 있어 import 줄이 없다 — import 규칙이 못 보는 바로 그 모양이다. 다른 패키지의
     * 값은 import로 풀린다(`ForeignValue`). [CLEAN_PORT]는 문서가 허용한 모양만 모아 초록이어야 하고, 나머지
     * 계약은 저마다 위반 하나씩을 심었다.
     */
    @Test
    fun auditCatchesPlantedViolations() {
        val report = syntheticReport()
        val byContract = report.violations.groupBy { it.substringBefore('.') }

        assertEquals(
            "문서가 허용한 모양만 모은 계약이 빨갛다 — 판정기가 값을 값으로 못 본다.\n" +
                byContract[CLEAN_PORT].orEmpty().joinToString("\n") { "  - $it" },
            emptyList<String>(),
            byContract[CLEAN_PORT].orEmpty(),
        )
        val missed = PLANTED.mapNotNull { (contract, fragments) ->
            val found = byContract[contract].orEmpty()
            val absent = fragments.filter { fragment -> found.none { fragment in it } }
            if (found.isEmpty() || absent.isNotEmpty()) "$contract — 못 찾은 조각 $absent / 실제 $found" else null
        }
        assertEquals("심은 위반을 판정기가 놓쳤다:\n" + missed.joinToString("\n") { "  - $it" }, emptyList<String>(), missed)
        assertEquals(
            "위반을 심지 않은 계약이 빨갛다(또는 모르는 계약이 섞였다).",
            emptySet<String>(),
            byContract.keys - PLANTED.keys - CLEAN_PORT,
        )
        assertFalse(
            "계산 프로퍼티(`get()`만)를 필드로 봤다: ${byContract["BodyFieldPort"]}",
            byContract["BodyFieldPort"].orEmpty().any { "HiddenCallback.doubled" in it },
        )
    }

    /**
     * **실제 코드 음성 대조** — 엔진 클라이언트와 람다 여러 개를 담은 실제 요청 객체
     * ([ContractSymbols.TOP_MOVE_ANALYSIS_RUN_REQUEST])를 받는 가짜 계약을 **실제 색인 위에** 얹으면 빨갛다.
     * 합성 소스가 못 보이는 것 — 실제 코드의 import·같은 패키지 해석이 제대로 도는지 — 을 여기서 본다.
     */
    @Test
    fun realRunRequestIsRejectedWhenAPortTakesIt() {
        val request = ContractSymbols.TOP_MOVE_ANALYSIS_RUN_REQUEST
        val requestName = request.substringAfterLast('.')
        val probe = SourceSymbolIndex.typeDeclarationsIn(
            "NegativeControlProbe.kt",
            "package ${request.substringBeforeLast('.')}\n\ninterface NegativeControlProbePort {\n    fun run(request: $requestName)\n}\n",
        )
        val report = PortSignatureAudit(SourceSymbolIndex.typeDeclarations + probe, emptySet()).audit(probe)
        val direct = report.violations.filter { it.startsWith("NegativeControlProbePort.run(request) › $requestName.") }
        val functionFields = direct.count { "함수 타입 필드" in it }
        val behaviourFields = direct.count { "(interface)" in it }

        assertTrue(
            "실제 요청 객체의 람다 필드를 못 잡았다(함수 타입 필드 $functionFields, 인터페이스 필드 $behaviourFields):\n" +
                report.violations.joinToString("\n") { "  - $it" },
            functionFields > 0 && behaviourFields > 0,
        )
    }

    /**
     * [declarationLine]에서 시작하는 선언의 본문(`{`…`}`) 첫 깊이에 있는 `fun`과 `val`/`var` 낱말 수.
     * 괄호·대괄호도 깊이로 세므로 매개변수 목록 안의 `val`(중첩 선언의 생성자)은 세지 않는다.
     */
    private fun memberKeywordCounts(code: String, declarationLine: Int): Pair<Int, Int> {
        var offset = 0
        repeat(declarationLine - 1) { offset = code.indexOf('\n', offset) + 1 }
        val open = code.indexOf('{', offset)
        check(open >= 0) { "본문을 찾지 못했다 — ${code.substring(offset).take(80)}" }
        var depth = 0
        var functions = 0
        var properties = 0
        var i = open
        while (i < code.length) {
            when (code[i]) {
                '{', '(', '[' -> depth++
                '}', ')', ']' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            if (depth == 1 && code.getOrNull(i - 1)?.let { it.isLetterOrDigit() || it == '_' } != true) {
                val word = WORD.find(code, i)?.takeIf { it.range.first == i }?.value
                when (word) {
                    "fun" -> functions++
                    "val", "var" -> properties++
                }
            }
            i++
        }
        return functions to properties
    }

    private fun baselineSummary(): String {
        val kinds = realReport.closure.keys
            .flatMap { fqn -> SourceSymbolIndex.typeDeclarations.byFqn[fqn].orEmpty().take(1) }
            .groupingBy { it.kind.label }.eachCount()
        return "\n계약 ${realReport.contracts.size}개, 폐포 ${realReport.closure.size}타입 $kinds"
    }

    /** 합성 세계 — 값·동작·계약을 한 패키지에, 다른 패키지 값 하나를 따로 둔다. */
    private fun syntheticReport(): PortSignatureAudit.Report {
        val pkg = "$ROOT.fixture83.ports"
        val foreign = "$ROOT.fixture83.foreign"
        val index = TypeDeclarationIndex.of(
            mapOf(
                "Foreign.kt" to "package $foreign\n\ndata class ForeignValue(val id: String)\n",
                "Values.kt" to """
                    package $pkg

                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.MutableStateFlow
                    import $foreign.ForeignValue

                    data class Snapshot(val moves: List<Int>, val label: String?, val details: Map<String, Detail>, val pair: Pair<Int, Tone>)
                    data class Detail(val elapsed: kotlin.time.Duration, val outcome: Outcome)
                    enum class Tone(val weight: Int) { Good(1), Bad(2) }
                    sealed interface Outcome {
                        data object Done : Outcome
                        data class Failed(val reason: Tone, val detail: String? = null) : Outcome
                    }
                    @JvmInline
                    value class Score(val raw: Int)
                    data class Box<T>(val value: T, val history: List<T> = emptyList())
                    typealias SnapshotAlias = Snapshot

                    interface EngineClient { fun analyze(): Int }
                    class SessionController { fun go() {} }
                    object ScoringPolicy { fun judge(): Boolean = true }

                    data class AnalysisRunRequest(
                        val client: EngineClient,
                        val state: Snapshot,
                        val onUpdate: (Snapshot) -> Unit,
                        val runWork: suspend () -> Snapshot,
                    )
                    data class Counter(var count: Int)
                    data class HiddenCallback(val id: Int) {
                        val doubled: Int get() = id * 2
                        val onChange: () -> Unit = {}
                    }
                    data class LazyHolder(val id: Int) {
                        val controller: SessionController by lazy { SessionController() }
                    }
                    sealed class Mode {
                        object Plain : Mode()
                        data class Custom(val level: Int) : Mode()
                    }
                    data class Anything(val payload: Any)
                    data class Failure(val cause: Throwable)
                    data class Raw(val bytes: IntArray)
                    data class Deferred(val items: Sequence<Int>)
                    data class Streamed(val updates: Flow<Snapshot>)
                    data class Buffer(val items: MutableList<Int>)
                    data class Untyped(val id: Int) { val derived = id + 1 }
                    typealias Callback = (Snapshot) -> Unit
                    data class WithAlias(val callback: Callback)
                    abstract class ListenerHolder { var listener: (() -> Unit)? = null }
                    data class Inherited(val id: Int) : ListenerHolder()
                    sealed class Tagged {
                        val onTag: () -> Unit = {}
                        data class Leaf(val x: Int) : Tagged()
                    }
                    data class Oops(val code: Int) : Exception()

                    interface $CLEAN_PORT {
                        fun save(snapshot: Snapshot)
                        fun load(): Snapshot?
                        fun all(): List<Snapshot>
                        fun outcome(): Outcome
                        fun score(): Score
                        suspend fun result(): Result<Snapshot>
                        fun observe(): Flow<Snapshot>
                        fun boxed(): Box<Snapshot>
                        fun alias(): SnapshotAlias
                        fun foreign(value: ForeignValue)
                        fun tone(nowMillis: Long = 0L): Tone = Tone.Good
                        fun update(transform: (Snapshot) -> Snapshot?): Snapshot?
                        @Throws(IllegalStateException::class)
                        suspend fun saveAll(
                            @Suppress("unused") first: Snapshot,
                            rest: Map<
                                String,
                                List<Score>,
                                >? = null,
                            nowMillis: Long = if (0 < 1) 0L else 1L,
                        ): List<
                            Snapshot,
                            >
                    }
                    interface RunRequestPort { fun run(request: AnalysisRunRequest) }
                    interface ControllerPort { fun attach(controller: SessionController) }
                    interface PolicyPort { fun judgeWith(policy: ScoringPolicy): Boolean }
                    interface CounterPort { fun save(counter: Counter) }
                    interface ProgressPort { suspend fun benchmark(onProgress: suspend (Snapshot) -> Unit): Snapshot }
                    interface PredicatePort { fun loadWhere(predicate: (Snapshot) -> Boolean): List<Snapshot> }
                    interface UnregisteredTransformPort { fun update(transform: (Snapshot) -> Snapshot?): Snapshot? }
                    interface MutableFlowPort { fun observe(): MutableStateFlow<Snapshot> }
                    interface FlowOfControllerPort { fun observe(): Flow<SessionController> }
                    interface NestedFlowPort { fun observeAll(): List<Flow<Snapshot>> }
                    interface BodyFieldPort { fun save(value: HiddenCallback) }
                    interface LazyFieldPort { fun save(value: LazyHolder) }
                    interface SealedObjectPort { fun mode(): Mode }
                    interface AnyPort { fun save(value: Anything) }
                    interface ThrowablePort { fun save(value: Failure) }
                    interface ArrayPort { fun save(value: Raw) }
                    interface SequencePort { fun save(value: Deferred) }
                    interface FlowFieldPort { fun save(value: Streamed) }
                    interface MutableListPort { fun save(value: Buffer) }
                    interface UntypedPort { fun save(value: Untyped) }
                    interface AliasCallbackPort { fun save(value: WithAlias) }
                    interface BoxedControllerPort { fun boxed(): Box<SessionController> }
                    interface GenericMethodPort { fun <T> get(key: String): T }
                    interface FunctionReturnPort { fun listener(): (Snapshot) -> Unit }
                    interface UnresolvedPort { fun save(file: java.io.File) }
                    interface InferredReturnPort { fun load() = Snapshot(emptyList(), null, emptyMap(), 0 to Tone.Good) }
                    interface InheritedFieldPort { fun save(value: Inherited) }
                    interface SealedParentFieldPort { fun save(leaf: Tagged.Leaf) }
                    interface ExceptionValuePort { fun save(value: Oops) }
                    interface VarargPort { fun save(vararg values: Snapshot) }
                    interface MultiLineCallbackPort {
                        @Throws(IllegalStateException::class)
                        suspend fun run(
                            request: Snapshot,
                            nowMillis: Long = 0L,
                            onProgress: suspend (
                                Snapshot,
                            ) -> Unit,
                        ): Snapshot
                    }
                """.trimIndent(),
            ),
        )
        val contracts = PortSignatureAudit.contractsIn(index, emptyList()).contracts
        return PortSignatureAudit(index, setOf("$CLEAN_PORT.update(transform)")).audit(contracts)
    }

    private companion object {
        private val WORD = Regex("""[A-Za-z_]\w*""")

        /** 합성 소스의 패키지 뿌리 — 리터럴 FQN을 적지 않으려고 등록부에서 파생한다. */
        val ROOT: String = ContractSymbols.MAIN_ACTIVITY.substringBeforeLast('.')

        /**
         * 기준선의 계약 수 — `*Port` 인터페이스 19개 + 명시 등록 1개(`PositionAnalysisCacheStore`), `#73` 실측과 같다.
         * 포트를 새로 만들거나 지우면 여기를 고친다.
         */
        const val EXPECTED_CONTRACT_COUNT = 20

        /**
         * 허용한 **함수 타입 매개변수** 자리. ⓐ의 유일한 예외(원자적 갱신의 순수 변환)이고, 모양은 판정기가 재지만
         * *"값만으로는 같은 일을 할 수 없는가"* 는 사람이 판단해 여기 적는다. 지금은 `#21`(`fe48dc55`)이 들여온
         * 출석 저장소의 원자적 갱신 하나뿐이다 — 읽고 고쳐 쓰는 사이에 끼어든 쓰기를 지우면 같은 회차가 다시 지급된다.
         */
        val REGISTERED_PURE_TRANSFORMS: Set<String> = setOf("AttendanceStorePort.update(transform)")

        const val CLEAN_PORT = "CleanPort"

        /** 합성 계약마다 심은 위반과, 판정 메시지에 나와야 할 조각. */
        val PLANTED: Map<String, List<String>> = mapOf(
            "RunRequestPort" to listOf("AnalysisRunRequest.client", "(interface)", "AnalysisRunRequest.onUpdate", "AnalysisRunRequest.runWork", "함수 타입 필드"),
            "ControllerPort" to listOf("SessionController`(class)"),
            "PolicyPort" to listOf("ScoringPolicy`(object)"),
            "CounterPort" to listOf("Counter.count", "`var` 필드"),
            "ProgressPort" to listOf("benchmark(onProgress)", "suspend"),
            "PredicatePort" to listOf("loadWhere(predicate)", "반환 타입이 입력 타입과 다르다"),
            "UnregisteredTransformPort" to listOf("update(transform)", "등록되지 않았다"),
            "MutableFlowPort" to listOf("MutableStateFlow", "가변 흐름"),
            "FlowOfControllerPort" to listOf("SessionController`(class)"),
            "NestedFlowPort" to listOf("흐름 `Flow<Snapshot>`"),
            "BodyFieldPort" to listOf("HiddenCallback.onChange", "함수 타입 필드"),
            "LazyFieldPort" to listOf("LazyHolder.controller", "SessionController`(class)"),
            "SealedObjectPort" to listOf("Mode ⊃ Plain", "Plain`(object)"),
            "AnyPort" to listOf("kotlin.Any"),
            "ThrowablePort" to listOf("kotlin.Throwable"),
            "ArrayPort" to listOf("kotlin.IntArray"),
            "SequencePort" to listOf("kotlin.sequences.Sequence"),
            "FlowFieldPort" to listOf("Streamed.updates", "흐름"),
            "MutableListPort" to listOf("kotlin.collections.MutableList"),
            "UntypedPort" to listOf("Untyped.derived", "타입이 적혀 있지 않다"),
            "AliasCallbackPort" to listOf("WithAlias.callback ≡ Callback", "함수 타입 필드"),
            "BoxedControllerPort" to listOf("SessionController`(class)"),
            "GenericMethodPort" to listOf("타입 매개변수 `T`"),
            "FunctionReturnPort" to listOf("listener: 반환", "함수 타입"),
            "UnresolvedPort" to listOf("java.io.File", "선언을 찾지 못했다"),
            "InferredReturnPort" to listOf("반환 타입이 적혀 있지 않다"),
            "InheritedFieldPort" to listOf("Inherited : ListenerHolder", "ListenerHolder`(class)"),
            "SealedParentFieldPort" to listOf("Leaf : Tagged", "Tagged.onTag", "함수 타입 필드"),
            "ExceptionValuePort" to listOf("Oops : Exception", "kotlin.Exception"),
            "VarargPort" to listOf("save(values)", "kotlin.Array"),
            "MultiLineCallbackPort" to listOf("run(onProgress)", "suspend"),
        )

        /** 실제 코드 — 한 번만 훑는다. */
        val realContracts: PortSignatureAudit.ContractSet by lazy {
            PortSignatureAudit.contractsIn(SourceSymbolIndex.typeDeclarations, ContractSymbols.PORT_CONTRACTS_OUTSIDE_NAME_PATTERN)
        }
        val realReport: PortSignatureAudit.Report by lazy {
            PortSignatureAudit(SourceSymbolIndex.typeDeclarations, REGISTERED_PURE_TRANSFORMS).audit(realContracts.contracts)
        }
    }
}
