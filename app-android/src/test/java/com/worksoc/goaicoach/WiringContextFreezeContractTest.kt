package com.worksoc.goaicoach

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 함정 67의 **원래 자리** — `GoCoachApp`의 익명 `object : GoCoachAppWiringContext` — 를 지키는 소스 계약
 * (refactor backlog #43).
 *
 * ## 왜 소스 계약인가
 * 그 객체의 멤버 일부는 컴포지션마다 새로 계산되는 **평범한 지역 `val`** 을 그대로 돌려준다
 * (`override fun playerSetup(): PlayerSetup = playerSetup`). 그 값이 낡지 않는 이유는 둘뿐이다 —
 * `val wiringContext = remember(키…)`의 키가 바뀔 때마다 객체가 새로 만들어지고,
 * `val controllers = remember(wiringContext) { … }`가 그때마다 컨트롤러를 통째로 다시 배선한다.
 * 키를 지우면(함정 67이 *"네 개의 독립 설계안 모두"* 에서 나왔다고 적은 바로 그 변경) 설정이 첫 컴포지션
 * 값에 영구히 얼어붙는데, **컴파일도 단위 테스트도 전부 초록이다.** `GoCoachControllerWiringTest`의
 * 페이크는 언제나 지금 값을 읽고, 컴포즈 안의 객체는 JVM 단위 테스트가 만들 수 없다(#43 검토에서 키를
 * 지운 채 `:app-android:testDebugUnitTest` 639개가 전부 통과했다). 그래서
 * `EngineReadinessWiringContractTest`처럼 소스를 읽어 모양을 잰다.
 *
 * ## 1차 그물 — 못박은 키는 조건 없이 지킨다
 * `val wiringContext = remember(…)`는 [PinnedWiringContextKeys] 일곱 개를 전부 들고 있어야 하고, 컨트롤러는
 * `remember(wiringContext)`로 다시 배선돼야 한다. **파서가 무엇을 찾든 이 검사는 돈다.** 키를 지우는 길은
 * 이 테스트의 목록을 리뷰를 거쳐 고치는 것 하나뿐이다([PinnedWiringContextKeys]의 KDoc).
 *
 * 예전 모양은 파서가 얼 수 있는 멤버를 0건 찾으면 키 검사를 스스로 건너뛰었다. 그러면 파서가 잘못 읽는
 * 모양 하나(키 달린 `remember`로 감싸기·줄을 넘는 식·객체 안 도우미 함수)가 곧 *"고쳤으니 목록에서 지울
 * 것"* 이라는 메시지와 키 검사 해제로 이어졌다(#43 재검토). 그 갈래는 없앴다.
 *
 * ## 2차 그물 — 파서가 잰 목록
 * 객체의 **모든 멤버**를 읽어([WiringContextSource]) 멤버마다 그것이 붙잡는 **컴포지션 지역 값**을 모으고
 * [FreezeProneMembers]와 맞대 본다. **더** 찾으면 새 동결이다. **덜** 찾으면 *"고쳐졌다"* 가 아니다 — 정말
 * 지연 읽기로 바꿨는지(함정 67 ⓑ 감사로 확인) 파서가 새 모양을 놓쳤는지 둘 중 하나다. 어느 쪽이든 키는
 * 1차 그물이 따로 지킨다.
 * - 멤버 모양: `override fun x(): T = 식`, `override fun x() = 식`, `override fun x(): T { return 식 }`,
 *   `override val x = 식`(초기화식), `override val x get() = 식`/`get() { … }`(게터). 식 본문은 **다음 멤버가
 *   시작하는 자리(또는 객체의 닫는 괄호)까지**다 — `&&`로 끝나는 줄·`else`로 시작하는 줄도 놓치지 않는다.
 *   파서가 못 읽은 멤버가 있으면 인터페이스의 멤버 목록과 어긋나 빨개진다.
 * - 식 안의 이름은 코틀린처럼 푼다: 멤버의 매개변수 → `GoCoachScreen`의 지역 선언(지역 함수면 그 몸체로 따라
 *   들어간다 — `= currentRuntimeLogContext()`, 함정 67 ⓑ의 위임 경로) → 객체 안의 도우미 함수·`val`과 다른
 *   멤버(`this.`로 부르든 그냥 부르든 따라 들어간다).
 * - 얼 수 있는 것: **평범한 `val`**, `GoCoachScreen`의 **매개변수**, 위임 없는 **지역 `var`**(그 컴포지션의
 *   변수 하나를 붙잡는다), 선언을 못 찾은 뿌리 이름(보수적으로). `by` 위임(`by remember { mutableStateOf(…) }`·
 *   `by HolderBackedState(…)`)은 읽을 때마다 **지금 값**을 읽으므로 얼지 않는다 — 단 초기화식(객체 안 도우미
 *   `val` 포함)은 객체를 만들 때 **한 번** 읽으므로 위임이어도 언다.
 * - `remember…(키) { … }`로 붙든 값은 **키가 없거나 키가 전부 remember된 값·[CapturedByDesign]일 때만** 얼지
 *   않는다. 평범한 값을 키로 받으면 그 값만큼 자주 바뀌므로 평범한 `val`과 같고, 뒤에 `.value` 같은 멤버
 *   접근이 붙으면(`rememberUpdatedState(x).value`) 언제나 평범한 값이다. 지역 선언은 `GoCoachScreen` 본문에
 *   바로 놓인 것만 센다(구조 분해 `val (a, b) = …` 포함) — 안쪽 블록의 같은 이름이 바깥 선언을 가리지 않는다.
 * - [CapturedByDesign]은 붙잡혀도 되는 값과 그 이유다. 그것을 뺀 나머지를 맞댄다.
 *
 * ## 그 밖에 지키는 것
 * - 조용한 구간 닫기: 멤버 `clearUndoEngineInterventionQuietWindow`가 (지역 함수를 거치든 직접이든)
 *   `cancelUndoSync()`에 닿고, 배선 뒤에 `cancelUndoSync = controllers.undoController::cancelPendingSync`가 있다.
 * - 알려진 동결 둘째: `val lifecycleController = remember { … }`의 로그 문맥 —
 *   [theLifecycleControllerRuntimeLogContextIsTheKnownFirstCompositionFreeze].
 *
 * ⚠️ 이 계약은 **이름을 읽는** 근사다. 문자열 안의 낱말도 읽기로 센다(템플릿 `$x`가 곧 읽기다 — 보수적),
 * 람다 매개변수가 지역 이름을 가리는 경우는 구분하지 않는다. 지역 함수의 식 본문은 줄 끝의 이항 연산자·다음
 * 줄의 연산자/`else`·`if (…)` 뒤를 이어 읽는 휴리스틱이다(객체 멤버는 위의 "다음 멤버까지" 규칙). 괄호·선언은
 * 문자열·문자 리터럴을 지운 사본에서 찾는다 — 문자열 속 `{`·`override fun`을 구조로 읽지 않는다.
 */
class WiringContextFreezeContractTest {

    private val source = WiringContextSource(
        stripKotlinComments(RepoPaths.goCoachApp.readContractSource()),
        stableRoots = CapturedByDesign.keys,
    )

    @Test
    fun theWiringContextKeepsItsPinnedRememberKeysWhateverTheParserFinds() {
        val keys = source.wiringContextKeys()
        assertEquals(
            "`val wiringContext = remember(…)`의 키가 빠졌다. 익명 컨텍스트 객체의 멤버 일부(FreezeProneMembers — " +
                "playerSetup·matchMode·engineName…)가 컴포지션 지역 값을 붙잡으므로, 키가 없으면 첫 컴포지션 값에 영구히 " +
                "얼어붙는다 — 설정이 안 먹고 디버그 리포트가 거짓말하는데 CI는 전부 초록이다. 이 검사는 파서가 무엇을 " +
                "찾든 돈다. 키를 지우려면 함정 67(docs/spec/PITFALLS.md #67) 착수 조건을 순서대로 끝낸 뒤 이 테스트의 " +
                "PinnedWiringContextKeys를 고칠 것: ⓐ 붙잡는 멤버를 람다/지연 읽기로 바꾸고 ⓑ GoCoachAppWiringContext " +
                "멤버를 전수 감사하고 ⓒ 그때만 키를 지운다. 지금 키: $keys",
            emptySet<String>(),
            PinnedWiringContextKeys - keys.toSet(),
        )
    }

    @Test
    fun theControllersAreRewiredWheneverTheWiringContextIsRebuilt() {
        // ⚠️ 키를 지우면 컨트롤러가 첫 컨텍스트 객체를 영원히 쥔다 — wiringContext의 키가 멀쩡해도 소용없다.
        // 디버그 리포트의 engineName/engineDiagnostic 잠복 동결(GoCoachControllerWiringTest)이 사용자에게
        // 안 보이는 것도 이 재배선 덕이다. 파서와 무관하게 돈다.
        assertTrue(
            "`val controllers = remember(wiringContext) { wireGoCoachControllers(wiringContext) }`가 아니다 — " +
                "컨트롤러가 첫 컨텍스트에 얼어붙는다(함정 67, #43).",
            source.containsCode(
                Regex("""val controllers = remember\(\s*wiringContext\s*\)\s*\{\s*wireGoCoachControllers\(\s*wiringContext\s*\)\s*}"""),
            ),
        )
    }

    @Test
    fun theFreezeProneMemberListStillMatchesWhatTheParserMeasures() {
        assertEquals(
            "익명 컨텍스트 객체에서 읽은 멤버가 GoCoachAppWiringContext의 멤버 목록과 다르다 — 파서가 모르는 " +
                "모양의 멤버가 있다(그 멤버는 재지 않고 통과하게 된다). WiringContextSource를 넓힐 것.",
            interfaceMemberNames(),
            source.memberNames().toSet(),
        )
        val captures = source.capturesByMember()
        // cancelUndoSync는 theQuietWindowClearIsBoundToTheWiredUndoController가 제 메시지("무르기 재동기화가
        // 살아남는다")로 지킨다 — 그 길이 끊겼을 때 여기서 "목록에서 지울 것"으로 먼저 읽히지 않게 뺀다.
        assertEquals(
            "CapturedByDesign에 적힌 값을 이제 어떤 멤버도 붙잡지 않는다. 정말 그 붙잡기를 없앴으면 거기서 지우고, " +
                "아니라면 파서가 새 모양을 놓친 것이다(WiringContextSource를 고치고 SyntheticScreen에 그 모양을 더할 것).",
            emptySet<String>(),
            CapturedByDesign.keys - QuietWindowCapture - captures.values.flatten().toSet(),
        )
        val measured = captures.pairs().withoutLocals(CapturedByDesign.keys)
        val pinned = FreezeProneMembers.pairs()
        assertEquals(
            "익명 컨텍스트 객체에서 컴포지션 지역 값을 새로 붙잡는 멤버(→ 값)가 생겼다. 컴포지션 하나에 얼어붙는다 — " +
                "람다/위임으로 바꾸거나, 그것을 새로 만들게 하는 remember 키를 정해 FreezeProneMembers와 CoveringKey에 " +
                "적을 것(함정 67).",
            emptySet<Pair<String, String>>(),
            measured - pinned,
        )
        assertEquals(
            "파서가 이 멤버(→ 값)를 더는 붙잡는 것으로 보지 않는다. '고쳐졌다'는 뜻이 아니다 — 둘 중 하나다. " +
                "(1) 정말 람다/지연 읽기로 바꿨다: 함정 67 ⓑ 감사(docs/spec/PITFALLS.md #67)로 확인한 뒤 " +
                "FreezeProneMembers·CoveringKey를 고칠 것. (2) 파서가 새 모양을 놓쳤다: WiringContextSource를 고치고 " +
                "SyntheticScreen에 그 모양을 더할 것. 키 없는(또는 안정된 값만 키로 받는) remember로 감싸 사라졌다면 고친 게 " +
                "아니라 remember 자리의 동결이다(engineName·engineDiagnostic은 함정 67이 감싸지 말라고 이름을 든 값이다). 어느 쪽이든 " +
                "remember 키는 PinnedWiringContextKeys가 따로 지킨다.",
            emptySet<Pair<String, String>>(),
            pinned - measured,
        )
        assertEquals(
            "CoveringKey는 FreezeProneMembers가 붙잡는 값마다 정확히 하나씩 있어야 한다.",
            FreezeProneMembers.values.flatten().toSet(),
            CoveringKey.keys,
        )
        assertEquals(
            "CoveringKey의 키는 PinnedWiringContextKeys 안에 있어야 한다 — 못박지 않은 키로는 덮을 수 없다.",
            emptySet<String>(),
            CoveringKey.values.toSet() - PinnedWiringContextKeys,
        )
    }

    @Test
    fun theQuietWindowClearIsBoundToTheWiredUndoController() {
        // 컨텍스트의 clearUndoEngineInterventionQuietWindow()(착수·새 대국·이어하기가 부른다)가 대기 중인
        // 무르기 재동기화까지 끊는 길은 둘이다. FakeGoCoachAppWiringContext가 이 길을 그대로 흉내 낸다.
        val reached = source.reachedCode("clearUndoEngineInterventionQuietWindow")
        assertTrue(
            "익명 컨텍스트의 clearUndoEngineInterventionQuietWindow()가 (지역 함수를 거치든 직접이든) cancelUndoSync()에 " +
                "닿지 않는다 — 착수·새 대국·이어하기 뒤에도 무르기 재동기화가 살아남는다(#43). 닿은 몸체: $reached",
            reached.any { CancelUndoSyncCall.containsMatchIn(it) },
        )
        assertTrue(
            "GoCoachApp이 배선된 무르기 컨트롤러의 cancelPendingSync를 cancelUndoSync에 묶지 않는다 — 착수·새 대국 " +
                "뒤에도 무르기 재동기화가 살아남는다(#43).",
            source.containsCode(Regex("""\bcancelUndoSync = controllers\.undoController::cancelPendingSync\b""")),
        )
    }

    /**
     * ⚠️ **알려진 동결 둘째 — 고치지 않고 못박는다**(#43 재검토, 프로덕션 0줄). `val lifecycleController =
     * remember { EngineOperationLifecycleController(currentRuntimeLogContext = { currentRuntimeLogContext() }, …) }`는
     * 키가 없어 첫 컴포지션에서 한 번만 돈다. 그 람다가 붙잡는 지역 함수는 **첫 컴포지션의 것**이고, 그 함수가
     * 읽는 평범한 `val` engineName·engineDiagnostic도 첫 값(엔진 준비 전이면 `Unresolved`)에 머문다 — 엔진 작업
     * 생명주기의 런타임 로그가 프로세스 내내 그 이름·진단을 적는다. 진단 로그에만 보인다.
     *
     * 고치면(지연 읽기 등) 여기가 빨개진다 — 그때 이 테스트와 [KnownFrozenLifecycleLocals]를 지우고
     * `GoCoachControllerWiringTest`의 디버그 리포트 특성 테스트 KDoc 문단을 줄인다.
     */
    @Test
    fun theLifecycleControllerRuntimeLogContextIsTheKnownFirstCompositionFreeze() {
        val block = source.rememberedBlock("lifecycleController")
        assertEquals(
            "알려진 동결의 모양이 바뀌었다 — `val lifecycleController = remember { … }`(키 없음)의 블록이 첫 컴포지션 " +
                "값 $KnownFrozenLifecycleLocals 를 붙잡는 모양이 아니다. 고쳤다면 이 테스트와 KnownFrozenLifecycleLocals를 " +
                "지우고 GoCoachControllerWiringTest의 디버그 리포트 특성 테스트 KDoc 문단을 줄일 것. ⚠️ remember에 키를 " +
                "달아 고치면 작업 상태(lifecycleState·activeJobs)를 쥔 컨트롤러가 새로 만들어진다 — 지연 읽기가 낫다. " +
                "engineName·engineDiagnostic을 키 없는(또는 안정된 값만 키로 받는) remember로 감싸 사라졌다면 고친 게 " +
                "아니라 동결을 한 칸 앞당긴 것이다(함정 67). 새로 붙잡힌 값이 생긴 것이라면 그것도 같은 동결이다. 지금: $block",
            RememberedBlock(keys = emptyList(), captured = KnownFrozenLifecycleLocals),
            block?.let { it.copy(captured = it.captured - CapturedByDesign.keys) },
        )
    }

    /**
     * 파서 자기 시험 — 멤버 모양 다섯(식 본문 타입 있음/없음·블록 본문·초기화식·게터), 지역 함수 위임, 매개변수,
     * 지역 `var`, 선언 없는 뿌리, `remember` 키, 줄을 넘는 식, 객체 안 도우미를 **붙잡는 쪽**(평범한 값)과
     * **안 붙잡는 쪽**(위임·remember)으로 하나씩 잰다. 여기가 빨개지면 실제 앱을 재는 위 테스트도 믿을 수 없다.
     */
    @Test
    fun theMemberParserMeasuresEveryMemberForm() {
        val snippet = WiringContextSource(stripKotlinComments(SyntheticScreen), stableRoots = setOf("stableInput"))

        assertEquals(
            "합성 조각의 멤버를 전부 읽어야 한다 — 안 붙잡는 쪽도 '읽고 나서 걸러야' 하고, 도우미는 멤버가 아니다.",
            listOf(
                "typedPlain", "typedDelegated", "untypedPlain", "untypedRemembered", "blockPlain", "blockDelegated",
                "initializerPlain", "initializerDelegated", "initializerRemembered", "getterPlain", "getterBlockPlain",
                "getterDelegated", "viaLocalFunction", "viaDelegatedLocalFunction", "parameter", "unknownRoot",
                "labelAndMemberAccess", "commentedOut", "setter", "localVarReader",
                "keyedByPlainReader", "keyedByDelegatedReader", "keyedByStableReader", "updatedValueReader",
                "destructuredReader", "trailingAnd", "nextLineElse", "viaObjectHelper", "viaThisHelper",
                "viaHelperSnapshot", "viaMultilineLocalFunction",
            ),
            snippet.memberNames(),
        )
        assertEquals(
            mapOf(
                "typedPlain" to setOf("plainA"),
                "untypedPlain" to setOf("plainB"),
                // 안쪽 블록의 `val plainC = remember { … }`가 바깥의 평범한 plainC를 가리지 않는다.
                "blockPlain" to setOf("plainC"),
                "initializerPlain" to setOf("plainE"),
                // 초기화식은 객체를 만들 때 한 번 읽는다 — 위임이어도 그 순간의 값에 언다.
                "initializerDelegated" to setOf("delegated"),
                "getterPlain" to setOf("plainF"),
                "getterBlockPlain" to setOf("plainH"),
                "viaLocalFunction" to setOf("plainG"),
                "parameter" to setOf("paramInput"),
                "unknownRoot" to setOf("declaredNowhere"),
                "localVarReader" to setOf("localVar"),
                // 평범한 값·위임을 키로 받은 remember는 그 키만큼 자주 바뀐다 — 평범한 val과 같다.
                "keyedByPlainReader" to setOf("keyedByPlain"),
                "keyedByDelegatedReader" to setOf("keyedByDelegated"),
                // rememberUpdatedState(x).value — 뒤에 멤버 접근이 붙으면 remember가 아니다.
                "updatedValueReader" to setOf("updatedValue"),
                "destructuredReader" to setOf("destructuredB"),
                "trailingAnd" to setOf("plainTrailing"),
                "nextLineElse" to setOf("plainElse"),
                "viaObjectHelper" to setOf("plainHelper"),
                "viaThisHelper" to setOf("plainHelper"),
                // 객체 안 도우미 val의 초기화식도 객체를 만들 때 한 번 읽는다.
                "viaHelperSnapshot" to setOf("delegated"),
                // `&&`로 끝나는 줄·`||`·`if (…)` 다음 줄·`else`로 시작하는 줄을 이어 읽고, 그 뒤의 선언(plainAfter)에서 멈춘다.
                "viaMultilineLocalFunction" to setOf("plainLocalTrailing", "plainLocalIf", "plainLocalElse"),
            ),
            snippet.capturesByMember(),
        )
        assertEquals(listOf("delegated", "remembered"), snippet.wiringContextKeys())
        assertEquals(listOf("readsPlain()", "return plainG"), snippet.reachedCode("viaLocalFunction").map { it.trim() })
        assertEquals(listOf("this.readHelper()", "plainHelper"), snippet.reachedCode("viaThisHelper").map { it.trim() })
        assertEquals(RememberedBlock(emptyList(), setOf("plainG")), snippet.rememberedBlock("frozenBlock"))
        assertEquals(RememberedBlock(listOf("plainA"), setOf("plainA")), snippet.rememberedBlock("keyedByPlain"))
        assertFalse("코드 검색은 문자열 속 글자를 코드로 읽지 않는다.", snippet.containsCode(Regex("""override fun fake""")))
    }

    /** 주석 걷기 자기 시험 — 문자열(URL·원시 문자열·템플릿 속 문자열)과 문자 리터럴은 그대로 둔다. */
    @Test
    fun theCommentStripperKeepsStringLiteralsAndLineStructure() {
        val input = listOf(
            "val url = \"https://example.com/a\" // line comment",
            "val raw = \"\"\"http://x/*y*/\"\"\" /* block /* nested */ end */ val z = 1",
            "val quote = '\"' // quote char",
            "val template = \"\${ \"//\" }\" // string inside a template",
            "val escaped = \"\\\"//\\\"\" // escaped quotes",
            "val spans = 1 /* first",
            "second */ val after = 2",
        ).joinToString("\n")
        val expected = listOf(
            "val url = \"https://example.com/a\" ",
            "val raw = \"\"\"http://x/*y*/\"\"\"  val z = 1",
            "val quote = '\"' ",
            "val template = \"\${ \"//\" }\" ",
            "val escaped = \"\\\"//\\\"\" ",
            "val spans = 1 ",
            " val after = 2",
        ).joinToString("\n")

        assertEquals(expected, stripKotlinComments(input))
    }

    /** 인터페이스의 멤버 이름(프로퍼티는 `getX` → `x`). 파서가 읽은 멤버와 맞대 본다. */
    private fun interfaceMemberNames(): Set<String> =
        GoCoachAppWiringContext::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { method ->
                if (method.name.startsWith("get") && method.parameterCount == 0 && method.name.length > 3) {
                    method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                } else {
                    method.name
                }
            }
            .toSet()

    /** 멤버 → 값 목록을 (멤버, 값) 쌍으로 편다. */
    private fun Map<String, Set<String>>.pairs(): Set<Pair<String, String>> =
        flatMap { (member, locals) -> locals.map { member to it } }.toSet()

    private fun Set<Pair<String, String>>.withoutLocals(locals: Set<String>): Set<Pair<String, String>> =
        filterNot { it.second in locals }.toSet()

    private companion object {
        /**
         * `val wiringContext = remember(…)`가 **언제나** 들고 있어야 하는 키(GoCoachApp.kt의 지금 목록).
         *
         * ⚠️ **여기서 키를 지우는 것이 곧 함정 67의 변경이다.** 지우려면 함정 67(docs/spec/PITFALLS.md #67)
         * 착수 조건 ⓐ~ⓒ를 먼저 끝내야 한다: ⓐ [FreezeProneMembers]의 멤버(평범한 지역 `val`을 그대로 돌려주는
         * 것 — playerSetup·matchMode·topMovesEnabled·shouldShowResumePrompt·engineName·engineDiagnostic, 지역
         * 함수로 위임된 currentRuntimeLogContext까지)를 람다/지연 읽기로 바꾸고 ⓑ `GoCoachAppWiringContext`
         * 멤버를 전수 감사해(함정 67은 64개라 적었고 지금 65개다) 값을 붙잡는 멤버가 0건임을 확인하고
         * ⓒ 그다음에만 키를 지운다. 파서의 목록([FreezeProneMembers])이 비었다는 것만으로는 ⓑ가 아니다 —
         * 파서는 근사이고, 이 목록은 그래서 파서와 떨어져 있다.
         */
        val PinnedWiringContextKeys = setOf(
            "sessionSnapshot",
            "undoEngineInterventionQuietUntil",
            "isPendingUndoSync",
            "isEngineReady",
            "isEngineBusy",
            "isEngineBlockingBusy",
            "uxOptions",
        )

        /** 얼 수 있는 멤버 → 그 멤버가 붙잡는 컴포지션 지역 값([CapturedByDesign]을 뺀 것). 2차 그물이다. */
        val FreezeProneMembers: Map<String, Set<String>> = mapOf(
            "playerSetup" to setOf("playerSetup"),
            "matchMode" to setOf("matchMode"),
            "topMovesEnabled" to setOf("topMovesEnabled"),
            "shouldShowResumePrompt" to setOf("shouldShowResumePrompt"),
            "engineName" to setOf("engineName"),
            "engineDiagnostic" to setOf("engineDiagnostic"),
            // `= currentRuntimeLogContext()` — 지역 함수가 평범한 val 둘을 읽는다(위임 경로, 함정 67 ⓑ).
            "currentRuntimeLogContext" to setOf("engineName", "engineDiagnostic"),
        )

        /** 붙잡힌 지역 값 → 그것을 새로 만들게 하는 remember 키([PinnedWiringContextKeys] 가운데 하나). */
        val CoveringKey: Map<String, String> = mapOf(
            // settingsState·savedSessionUiState는 sessionSnapshot을 읽는 HolderBackedState다.
            "playerSetup" to "sessionSnapshot",
            "matchMode" to "sessionSnapshot",
            "topMovesEnabled" to "sessionSnapshot",
            "shouldShowResumePrompt" to "sessionSnapshot",
            // ⚠️ **우연히만** 덮인다: 엔진 정체는 키가 아니고, 준비 완료(isEngineReady)와 같은 재구성에서
            // 바뀔 때만 객체가 새로 만들어진다. 함정 67이 "remember로 감싸지 말라"고 이름을 든 두 값이다.
            "engineName" to "isEngineReady",
            "engineDiagnostic" to "isEngineReady",
        )

        /** 붙잡혀도 되는 지역 값 → 그 이유. 여기 올리려면 "왜 컴포지션 내내 같은가"를 적어야 한다. */
        val CapturedByDesign: Map<String, String> = mapOf(
            "context" to "androidContext = context.applicationContext — Application은 프로세스에 하나다.",
            "engineClient" to "GoCoachScreen 매개변수 — MainActivity가 remember로 한 번 만든 인스턴스다(엔진 기동이 그 정체에 묶여 있다).",
            "diagnosticEventLog" to "GoCoachScreen 매개변수 — MainActivity가 remember(applicationContext)로 한 번 만든다.",
            "cancelUndoSync" to "지역 var — 객체를 만든 바로 그 컴포지션에서 `cancelUndoSync = controllers.undoController::" +
                "cancelPendingSync`로, 이 객체로 배선한 무르기 컨트롤러를 가리키게 된다. 객체와 함께 새로 태어난다.",
        )

        /** [CapturedByDesign] 가운데 조용한 구간 테스트가 제 메시지로 지키는 값. */
        val QuietWindowCapture = setOf("cancelUndoSync")

        val CancelUndoSyncCall = Regex("""\bcancelUndoSync\s*(?:\(\s*\)|\.invoke\(\s*\))""")

        /** 알려진 동결 둘째: lifecycleController의 remember 블록이 첫 컴포지션에 붙잡는 값([CapturedByDesign]을 뺀 것). */
        val KnownFrozenLifecycleLocals = setOf("engineName", "engineDiagnostic")

        /** 파서 자기 시험용 합성 조각. 이름이 곧 기대다: plain* = 붙잡는다, delegated/remembered/stable = 안 붙잡는다. */
        val SyntheticScreen = """
            @Composable
            private fun GoCoachScreen(
                paramInput: Input,
                stableInput: Stable,
                onEvent: (Event) -> Unit,
            ) {
                val plainA = settings.a
                val plainB = settings.b
                val plainC = settings.c
                val plainE = settings.e
                val plainF = settings.f
                val plainG = settings.g
                val plainH = settings.h
                val plainTrailing = settings.trailing
                val plainElse = settings.otherwise
                val plainHelper = settings.helper
                val plainUnused = settings.unused
                val plainLocalTrailing = settings.localTrailing
                val plainLocalIf = settings.localIf
                val plainLocalElse = settings.localElse
                var delegated by remember { mutableStateOf(0) }
                val remembered = remember { Thing() }
                var localVar: () -> Unit = {}
                val keyedByPlain = remember(plainA) { Thing(plainA) }
                val keyedByDelegated = remember(delegated) { Thing() }
                val keyedByStable = remember(stableInput, remembered) { Thing() }
                val updatedValue = rememberUpdatedState(plainB).value
                val (destructuredA, destructuredB) = pairOf(plainA, plainB)
                val frozenBlock = remember { Holder(read = { readsPlain() }) }
                val notCode = "override fun fake() = plainA"
                SideEffect {
                    val plainC = remember { Thing() }
                }
                fun readsPlain(): G { return plainG }
                fun readsAcrossLines(): Boolean = remembered.ok &&
                    plainLocalTrailing ||
                    if (delegated > 0)
                        plainLocalIf
                    else plainLocalElse
                val plainAfter = settings.after
                fun readsDelegated() = delegated
                val wiringContext = remember(delegated, remembered) {
                    object : GoCoachAppWiringContext {
                        override fun typedPlain(): A = plainA
                        override fun typedDelegated(): D = delegated
                        override fun untypedPlain() = plainB
                        override fun untypedRemembered() = remembered.value
                        override fun blockPlain(): C {
                            return plainC
                        }
                        override fun blockDelegated(): D { return delegated }
                        override val initializerPlain = plainE
                        override val initializerDelegated: D = delegated
                        override val initializerRemembered: R = remembered
                        override val getterPlain get() = plainF
                        override val getterBlockPlain: H
                            get() {
                                return plainH
                            }
                        override val getterDelegated: D
                            get() = delegated
                        override fun viaLocalFunction(): G = readsPlain()
                        override fun viaDelegatedLocalFunction() = readsDelegated()
                        override val parameter = paramInput
                        override fun unknownRoot(): U = declaredNowhere
                        override fun labelAndMemberAccess(): L = build(plainA = remembered.plainB)
                        override fun commentedOut(): D = delegated // plainA
                        override fun setter(value: D) { delegated = value }
                        override fun localVarReader() = localVar()
                        override fun keyedByPlainReader() = keyedByPlain
                        override fun keyedByDelegatedReader() = keyedByDelegated
                        override fun keyedByStableReader() = keyedByStable
                        private fun unusedHelper() = plainUnused
                        override fun updatedValueReader() = updatedValue
                        override fun destructuredReader() = listOf(destructuredB)
                        override fun trailingAnd(): Boolean = remembered.ok &&
                            plainTrailing
                        override fun nextLineElse(): E = if (delegated > 0) remembered.e
                            else plainElse
                        override fun viaObjectHelper() = readHelper()
                        private fun readHelper() = plainHelper
                        private val helperSnapshot = delegated
                        override fun viaThisHelper() = this.readHelper()
                        override fun viaHelperSnapshot() = helperSnapshot
                        override fun viaMultilineLocalFunction() = readsAcrossLines()
                    }
                }
            }
        """.trimIndent()
    }
}

/** `remember…(키) { 블록 }` 하나 — 키 목록과, 블록이 붙잡는 컴포지션 지역 값. */
private data class RememberedBlock(val keys: List<String>, val captured: Set<String>)

/**
 * 주석을 걷어 낸 GoCoachApp.kt(또는 합성 조각)에서 익명 컨텍스트 객체와 그 앞의 지역 선언을 읽는다.
 * 컴파일러가 아니라 **이름을 읽는 근사**다 — 계약 KDoc의 ⚠️를 볼 것.
 *
 * [stableRoots]는 컴포지션 내내 같다고 사람이 정한 이름(계약의 `CapturedByDesign`)이다 — 그것만 키로 받는
 * `remember`는 붙든 값으로 본다.
 */
private class WiringContextSource(code: String, private val stableRoots: Set<String> = emptySet()) {

    /** 원문(주석만 걷은 것). 몸체 글자는 여기서 잘라 온다 — 템플릿 `$x`도 읽기로 세게. */
    private val text: String = code

    /** [text]와 길이가 같고 문자열·문자 리터럴 자리만 공백으로 지운 사본. 괄호·선언·줄 구조는 여기서 읽는다. */
    private val mask: String = maskLiterals(code)

    private val wiringContextStart: Int = mask.indexOf(WIRING_CONTEXT_DECLARATION).also { start ->
        check(start >= 0) { "`$WIRING_CONTEXT_DECLARATION`을 찾지 못했다 — 이 계약의 전제가 무너졌다(함정 67)." }
    }

    private val screenParametersOpen: Int = mask.indexOf("fun GoCoachScreen(").let { start ->
        check(start in 0 until wiringContextStart) { "`fun GoCoachScreen(`을 찾지 못했다 — 전제가 무너졌다." }
        mask.indexOf('(', start)
    }

    private val screenParameters: Set<String> =
        parameterNames(mask.substring(screenParametersOpen + 1, matchingClose(mask, screenParametersOpen)))

    /** `GoCoachScreen` 본문 가운데 wiringContext보다 **앞선** 구간 — 지역 값은 쓰이기 전에 선언된다. */
    private val screen: Region = run {
        val bodyOpen = mask.indexOf('{', matchingClose(mask, screenParametersOpen))
        check(bodyOpen in 0 until wiringContextStart) { "`GoCoachScreen`의 본문을 찾지 못했다 — 전제가 무너졌다." }
        Region(text, mask, bodyOpen + 1, wiringContextStart)
    }

    private val objectRegion: Region = run {
        val objectStart = mask.indexOf("object : GoCoachAppWiringContext", wiringContextStart)
        check(objectStart >= 0) { "wiringContext 안에서 `object : GoCoachAppWiringContext`를 찾지 못했다 — 전제가 무너졌다." }
        val open = mask.indexOf('{', objectStart)
        Region(text, mask, open + 1, matchingClose(mask, open))
    }

    /** 본문에 바로 놓인 지역 선언의 종류. 선언 순서대로 읽는다 — remember 키는 앞선 선언으로 판정하고, 다시 선언되면 뒤의 것이 이긴다. */
    private val locals: Map<String, Declaration> = run {
        val found = mutableMapOf<String, Declaration>()
        screenParameters.forEach { found[it] = Declaration.Parameter }
        LocalDeclaration.findAll(screen.mask).filter { screen.isTopLevel(it) }.forEach { match ->
            val (keyword, destructured, name, operator) = match.destructured
            val kind = when {
                operator == "by" -> Declaration.Delegated
                keyword == "val" && isStableRemember(match.range.last + 1, found) -> Declaration.Remembered
                keyword == "val" -> Declaration.PlainVal
                else -> Declaration.LocalVar
            }
            val names = if (name.isNotEmpty()) {
                listOf(name)
            } else {
                destructured.split(',').map { it.substringBefore(':').trim() }.filter { it.isNotEmpty() && it != "_" }
            }
            names.forEach { found[it] = kind }
        }
        found
    }

    private val localFunctions: Map<String, Body> =
        LocalFunction.findAll(screen.mask)
            .filter { screen.isTopLevel(it) }
            .associate { match -> match.groupValues[1] to functionBody(screen, match.range.last, expressionEnd = null) }

    private val objectDeclarations: List<ObjectDeclaration> = run {
        val starts = ObjectDeclarationStart.findAll(objectRegion.mask).filter { objectRegion.isTopLevel(it) }.toList()
        starts.mapIndexed { index, match ->
            // 식 본문은 다음 선언이 시작하는 자리(또는 객체의 닫는 괄호)까지다 — 줄을 넘는 식을 줄 규칙으로 자르지 않는다.
            val next = starts.getOrNull(index + 1)?.range?.first ?: objectRegion.mask.length
            val (modifiers, keyword, name) = match.destructured
            val isOverride = "override" in modifiers.split(Whitespace)
            if (keyword == "fun") {
                val body = functionBody(objectRegion, objectRegion.mask.indexOf('(', match.range.last + 1), expressionEnd = next)
                ObjectDeclaration(name, isOverride, body, snapshot = false)
            } else {
                propertyDeclaration(name, isOverride, match.range.last + 1, next)
            }
        }
    }

    private val members: List<ObjectDeclaration> = objectDeclarations.filter { it.isOverride }

    private val membersByName: Map<String, ObjectDeclaration> = members.associateBy { it.name }

    private val objectHelpers: Map<String, ObjectDeclaration> = objectDeclarations.filterNot { it.isOverride }.associateBy { it.name }

    fun memberNames(): List<String> = members.map { it.name }

    /** 멤버 → 그 멤버가 붙잡는 컴포지션 지역 값. 아무것도 안 붙잡는 멤버는 빠진다. */
    fun capturesByMember(): Map<String, Set<String>> =
        members.associate { member -> member.name to capturedLocals(member.body, member.snapshot, mutableSetOf()) }
            .filterValues { it.isNotEmpty() }

    /** `val wiringContext = remember(` 뒤의 키들. 키 없는 `remember {`이거나 `remember`가 아닌 이름이면 빈 목록. */
    fun wiringContextKeys(): List<String> {
        var index = wiringContextStart + WIRING_CONTEXT_DECLARATION.length
        while (index < mask.length && mask[index].isWhitespace()) index++
        if (index >= mask.length || mask[index] != '(') return emptyList()
        return splitTopLevel(mask.substring(index + 1, matchingClose(mask, index))).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 멤버 [name]의 몸체와, 거기서 따라 들어간 지역 함수·객체 도우미·다른 멤버의 몸체(들어간 순서). */
    fun reachedCode(name: String): List<String> {
        val member = membersByName[name] ?: return emptyList()
        val visited = mutableSetOf<String>()
        val reached = mutableListOf<String>()
        fun visit(body: Body) {
            reached += body.text
            Identifier.findAll(body.text).forEach { token ->
                val reference = resolve(body, token)
                if (reference is Reference.Callee && visited.add(reference.key)) visit(reference.body)
            }
        }
        visit(member.body)
        return reached
    }

    /** 본문의 `val [name] = remember…(키) { 블록 }` → 키와 블록이 붙잡는 값. 그런 선언이 없거나 모양이 다르면 null. */
    fun rememberedBlock(name: String): RememberedBlock? {
        val match = LocalDeclaration.findAll(screen.mask)
            .lastOrNull { screen.isTopLevel(it) && it.groupValues[3] == name && it.groupValues[4] == "=" }
            ?: return null
        val from = match.range.last + 1
        val call = rememberCall(screen.mask, from, expressionEnd(screen.mask, from)) ?: return null
        val block = call.block ?: return null
        return RememberedBlock(call.keys, capturedLocals(Body(screen.text.substring(block.first, block.last + 1), emptySet()), snapshot = false, mutableSetOf()))
    }

    /** 문자열·문자 리터럴을 뺀 **코드**에 [pattern]이 있는가. */
    fun containsCode(pattern: Regex): Boolean = pattern.containsMatchIn(mask)

    private fun capturedLocals(body: Body, snapshot: Boolean, visited: MutableSet<String>): Set<String> {
        val captured = mutableSetOf<String>()
        // `= 이름(.속성)*`처럼 값 하나를 돌려주는 멤버의 뿌리를 어디서도 못 찾으면 붙잡힌 것으로 본다(보수적).
        // 리터럴·키워드(`true`·`null`·`this`…)와 대문자로 시작하는 이름(타입·object)은 지역 값이 아니다.
        ValueExpression.matchEntire(body.text.trim())?.groupValues?.get(1)?.let { root ->
            val isLocalCandidate = root.first().isLowerCase() && root !in NotLocalNames
            val isKnown = root in locals || root in localFunctions || root in body.parameters || root in objectHelpers || root in membersByName
            if (isLocalCandidate && !isKnown) captured += root
        }
        Identifier.findAll(body.text).forEach { token ->
            when (val reference = resolve(body, token)) {
                is Reference.Callee ->
                    if (visited.add(reference.key)) captured += capturedLocals(reference.body, snapshot || reference.snapshot, visited)
                is Reference.Local -> when (reference.declaration) {
                    Declaration.PlainVal, Declaration.Parameter, Declaration.LocalVar -> captured += reference.name
                    Declaration.Delegated -> if (snapshot) captured += reference.name
                    Declaration.Remembered -> Unit
                }
                null -> Unit
            }
        }
        return captured
    }

    /** [body] 안의 이름 하나가 가리키는 것 — 코틀린처럼 지역 선언이 암시적 `this`의 멤버보다 먼저다. */
    private fun resolve(body: Body, token: MatchResult): Reference? {
        val name = token.value
        val before = body.text.substring(0, token.range.first)
        val after = body.text.substring(token.range.last + 1)
        if (name in body.parameters) return null
        if (ThisReceiver.containsMatchIn(before)) return objectReference(name)
        if (before.endsWith(".") || MemberReference.containsMatchIn(before)) return null
        if (LabelOrTarget.containsMatchIn(after)) return null
        val isCall = CallStart.containsMatchIn(after) || before.endsWith("::")
        val localFunction = localFunctions[name]
        if (localFunction != null && isCall) return Reference.Callee("local:$name", localFunction, snapshot = false)
        locals[name]?.let { return Reference.Local(name, it) }
        return objectReference(name)
    }

    private fun objectReference(name: String): Reference? =
        (objectHelpers[name] ?: membersByName[name])?.let { Reference.Callee("object:$name", it.body, it.snapshot) }

    /** `val x = …`의 초기화식이 통째로 `remember…(키) { … }`이고 키가 없거나 전부 remember된 값·[stableRoots]인가. */
    private fun isStableRemember(from: Int, known: Map<String, Declaration>): Boolean {
        val call = rememberCall(screen.mask, from, expressionEnd(screen.mask, from)) ?: return false
        return call.keys.all { key ->
            val root = RootedName.matchEntire(key)?.groupValues?.get(1)
            root != null && (known[root] == Declaration.Remembered || root in stableRoots)
        }
    }

    /** `override val|var 이름` 뒤: 초기화식(`= 식`)·위임(`by 식`)은 한 번 읽기, 게터(`get() = 식`/`{ … }`)는 매번 읽기. */
    private fun propertyDeclaration(name: String, isOverride: Boolean, afterName: Int, end: Int): ObjectDeclaration {
        val head = PropertyHead.find(objectRegion.mask.substring(afterName, end))
            ?: error("`val $name` 뒤에서 `=`·`by`·`get()`를 찾지 못했다 — 파서가 모르는 모양이다.")
        val bodyStart = afterName + head.range.last + 1
        return if (head.groupValues[1].startsWith("get")) {
            ObjectDeclaration(name, isOverride, bodyAfterSignature(objectRegion, bodyStart, emptySet(), end), snapshot = false)
        } else {
            ObjectDeclaration(name, isOverride, Body(objectRegion.text.substring(bodyStart, end).trim(), emptySet()), snapshot = true)
        }
    }

    private enum class Declaration { Parameter, Delegated, Remembered, PlainVal, LocalVar }

    private class Body(val text: String, val parameters: Set<String>)

    private class ObjectDeclaration(val name: String, val isOverride: Boolean, val body: Body, val snapshot: Boolean)

    private sealed interface Reference {
        class Callee(val key: String, val body: Body, val snapshot: Boolean) : Reference

        class Local(val name: String, val declaration: Declaration) : Reference
    }

    private class RememberCall(val keys: List<String>, val block: IntRange?)

    /** 원문과 가림 사본의 같은 구간. [depths]는 구간 안에서의 중괄호 깊이다(0 = 그 구간에 바로 놓인 자리). */
    private class Region(text: String, mask: String, start: Int, end: Int) {
        val text: String = text.substring(start, end)
        val mask: String = mask.substring(start, end)
        private val depths: IntArray = run {
            val depths = IntArray(this.mask.length + 1)
            var depth = 0
            this.mask.forEachIndexed { index, char ->
                depths[index] = depth
                if (char == '{') depth++ else if (char == '}') depth--
            }
            depths[this.mask.length] = depth
            depths
        }

        fun isTopLevel(match: MatchResult): Boolean = depths[match.range.first] == 0
    }

    private companion object {
        const val WIRING_CONTEXT_DECLARATION = "val wiringContext = remember"
        val LocalDeclaration = Regex("""\b(val|var)(?:\s*\(([^)]*)\)|\s+(\w+))\s*(?::[^=\n]*?)?\s*(\bby\b|=(?!=))""")
        val LocalFunction = Regex("""\bfun\s+(?:<[^>]*>\s*)?(\w+)\s*\(""")
        val ObjectDeclarationStart = Regex(
            """(?<![\w.@])((?:(?:override|private|internal|protected|public|final|open|inline|suspend|operator|infix|tailrec|lateinit|const)\s+)*)(fun|val|var)\s+(?:<[^>]*>\s*)?(\w+)""",
        )
        val PropertyHead = Regex("""^\s*(?::[^=\n]*?)?\s*(=(?!=)|\bby\b|\bget\s*\(\s*\))""")
        val RememberName = Regex("""^remember\w*""")
        val RootedName = Regex("""^([A-Za-z_]\w*)(?:\s*\.\s*\w+)*$""")
        val NotLocalNames = setOf("true", "false", "null", "this", "super")
        val ValueExpression = Regex("""^(?:return\s+)?([A-Za-z_]\w*)(?:\.\w+)*;?$""")
        val Identifier = Regex("""\b[A-Za-z_]\w*\b""")
        val ParameterName = Regex("""^\s*(?:@\w+\s+)*(?:(?:vararg|noinline|crossinline)\s+)?(\w+)\s*:""")
        val ThisReceiver = Regex("""\bthis\s*(?:\.|::)\s*$""")
        val MemberReference = Regex("""\w\s*::$""")
        val LabelOrTarget = Regex("""^\s*(=(?![=>])|->)""")
        val CallStart = Regex("""^\s*[({]""")
        val Whitespace = Regex("""\s+""")

        /** 줄 끝이 이러면 식이 다음 줄로 이어진다. */
        val TrailingContinuation = Regex("""(&&|\|\||\?:|\?\.|[-+*/%<>.]|==|!=|\b(?:else|in|is|as|to|and|or|xor|until|downTo|step)\b)$""")

        /** 다음 줄이 이렇게 시작하면 식이 이어진다. */
        val LeadingContinuation = Regex("""^(\.|\?\.|\?:|&&|\|\||[-+*/%]|==|!=|<|>|\b(?:else|as|in|is|and|or|until|downTo|step)\b)""")

        val ControlHead = Regex("""\b(?:if|when|for|while)\s*$""")

        /** [openParen]가 가리키는 `(` 뒤의 매개변수 목록과 몸체(`= 식` 또는 `{ … }`). */
        fun functionBody(region: Region, openParen: Int, expressionEnd: Int?): Body {
            val close = matchingClose(region.mask, openParen)
            return bodyAfterSignature(region, close + 1, parameterNames(region.mask.substring(openParen + 1, close)), expressionEnd)
        }

        /**
         * 시그니처 끝(반환 타입 앞) 뒤의 몸체. `: 타입`을 건너뛰고 `=` 식이나 `{` 블록을 읽는다. 식은
         * [expressionEnd]가 있으면 거기까지(객체 멤버: 다음 선언까지), 없으면 [expressionEnd] 휴리스틱(지역 함수)이다.
         */
        fun bodyAfterSignature(region: Region, from: Int, parameters: Set<String>, expressionEnd: Int?): Body {
            var index = from
            var parenDepth = 0
            while (index < region.mask.length) {
                val char = region.mask[index]
                when {
                    char == '(' -> parenDepth++
                    char == ')' -> parenDepth--
                    parenDepth == 0 && char == '=' -> {
                        val end = expressionEnd ?: expressionEnd(region.mask, index + 1)
                        return Body(region.text.substring(index + 1, end).trim(), parameters)
                    }
                    parenDepth == 0 && char == '{' ->
                        return Body(region.text.substring(index + 1, matchingClose(region.mask, index)), parameters)
                }
                index++
            }
            error("몸체를 찾지 못했다: ${region.mask.substring(from).take(80)}")
        }

        /**
         * `=` 뒤 식의 끝(지역 선언·지역 함수). 괄호 밖에서 줄이 끝나도 그 줄이 이항 연산자·`else`·`if (…)`로
         * 끝나거나 다음 줄이 연산자·`else`로 시작하면 이어 읽는다. 더 읽는 쪽이 보수적이다.
         */
        fun expressionEnd(mask: String, from: Int): Int {
            var index = from
            while (index < mask.length && mask[index].isWhitespace()) index++
            val start = index
            var depth = 0
            while (index < mask.length) {
                val char = mask[index]
                when {
                    char in "([{" -> depth++
                    char in ")]}" -> if (depth == 0) return index else depth--
                    char == ';' && depth == 0 -> return index
                    char == '\n' && depth == 0 -> if (!continuesOnNextLine(mask, start, index)) return index
                }
                index++
            }
            return mask.length
        }

        private fun continuesOnNextLine(mask: String, start: Int, lineBreak: Int): Boolean {
            val soFar = mask.substring(start, lineBreak).trimEnd()
            val next = mask.substring(lineBreak + 1).trimStart()
            if (TrailingContinuation.containsMatchIn(soFar) || LeadingContinuation.containsMatchIn(next)) return true
            // `if (조건)`·`when (x)`·`while (…)`으로 줄이 끝나면 몸체가 다음 줄에 온다.
            if (soFar.endsWith(")")) {
                val open = matchingOpen(soFar, soFar.length - 1)
                if (open >= 0 && ControlHead.containsMatchIn(soFar.substring(0, open))) return true
            }
            return false
        }

        /** [from, end) 가 통째로 `remember…(키) { 블록 }` 호출 하나이면 그 키와 블록 자리. 뒤에 `.value`·연산자가 붙으면 null. */
        fun rememberCall(mask: String, from: Int, end: Int): RememberCall? {
            var index = from
            fun skipSpace() {
                while (index < end && mask[index].isWhitespace()) index++
            }
            skipSpace()
            val name = RememberName.find(mask.substring(index, end)) ?: return null
            index += name.value.length
            skipSpace()
            if (index < end && mask[index] == '<') {
                index = matchingAngle(mask, index) + 1
                skipSpace()
            }
            var keys = emptyList<String>()
            var block: IntRange? = null
            var called = false
            if (index < end && mask[index] == '(') {
                val close = matchingClose(mask, index)
                keys = splitTopLevel(mask.substring(index + 1, close)).map { it.trim() }.filter { it.isNotEmpty() }
                index = close + 1
                called = true
                skipSpace()
            }
            if (index < end && mask[index] == '{') {
                val close = matchingClose(mask, index)
                block = index + 1 until close
                index = close + 1
                called = true
                skipSpace()
            }
            return if (called && index >= end) RememberCall(keys, block) else null
        }

        fun parameterNames(list: String): Set<String> =
            splitTopLevel(list).mapNotNull { ParameterName.find(it)?.groupValues?.get(1) }.toSet()

        fun splitTopLevel(list: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var start = 0
            list.forEachIndexed { index, char ->
                when (char) {
                    '(', '[', '{', '<' -> depth++
                    ')', ']', '}', '>' -> if (!(char == '>' && index > 0 && list[index - 1] == '-')) depth--
                    ',' -> if (depth == 0) {
                        parts += list.substring(start, index)
                        start = index + 1
                    }
                }
            }
            parts += list.substring(start)
            return parts
        }

        /** [open]의 여는 괄호(`(`·`{`·`[`)에 짝이 맞는 닫는 괄호의 위치. [mask]는 리터럴을 지운 사본이다. */
        fun matchingClose(mask: String, open: Int): Int {
            var depth = 0
            for (index in open until mask.length) {
                when (mask[index]) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
            error("짝이 맞는 닫는 괄호를 찾지 못했다: ${mask.substring(open).take(80)}")
        }

        /** [close]의 닫는 괄호에 짝이 맞는 여는 괄호의 위치(없으면 -1). */
        fun matchingOpen(mask: String, close: Int): Int {
            var depth = 0
            for (index in close downTo 0) {
                when (mask[index]) {
                    ')', '}', ']' -> depth++
                    '(', '{', '[' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
            return -1
        }

        /** [open]의 `<`에 짝이 맞는 `>`의 위치(`->`는 세지 않는다). */
        fun matchingAngle(mask: String, open: Int): Int {
            var depth = 0
            for (index in open until mask.length) {
                when {
                    mask[index] == '<' -> depth++
                    mask[index] == '>' && mask[index - 1] != '-' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
            error("짝이 맞는 `>`를 찾지 못했다: ${mask.substring(open).take(80)}")
        }
    }
}

/** [source]와 길이가 같고, 문자열(템플릿 포함)·문자 리터럴 자리만 공백으로 바꾼 사본 — 문자열 속 `{`·`fun`에 속지 않게. */
private fun maskLiterals(source: String): String {
    val out = StringBuilder(source)
    var index = 0
    while (index < source.length) {
        val end = when (source[index]) {
            '"' -> scanString(source, index).second
            '\'' -> charLiteralEnd(source, index)
            else -> {
                index++
                continue
            }
        }
        for (position in index until end) out.setCharAt(position, ' ')
        index = end
    }
    return out.toString()
}

/**
 * 코틀린 소스에서 주석(`//…`·중첩되는 `/* … */`)만 걷어 낸다. 문자열(`"…"`·`"""…"""`, 그 안의 `${…}`
 * 템플릿과 템플릿 속 문자열 포함)과 문자 리터럴(`'"'`)은 그대로 둔다 — `"https://…"`의 `//`를 주석으로
 * 잘라 내면 뒤의 코드가 사라진다. 블록 주석 안의 줄바꿈은 남겨 줄 구조를 지킨다.
 */
private fun stripKotlinComments(source: String): String {
    val out = StringBuilder(source.length)
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("//", index) -> {
                while (index < source.length && source[index] != '\n') index++
            }
            source.startsWith("/*", index) -> {
                var depth = 0
                while (index < source.length) {
                    when {
                        source.startsWith("/*", index) -> {
                            depth++
                            index += 2
                        }
                        source.startsWith("*/", index) -> {
                            depth--
                            index += 2
                            if (depth == 0) break
                        }
                        else -> {
                            if (source[index] == '\n') out.append('\n')
                            index++
                        }
                    }
                }
            }
            source[index] == '"' -> {
                val (text, end) = scanString(source, index)
                out.append(text)
                index = end
            }
            source[index] == '\'' -> {
                val end = charLiteralEnd(source, index)
                out.append(source, index, end)
                index = end
            }
            else -> out.append(source[index++])
        }
    }
    return out.toString()
}

/**
 * [quote]에서 시작하는 문자열 리터럴 하나를 읽는다 → (주석을 걷은 리터럴 원문, 바로 뒤의 위치).
 * 템플릿 `${…}` 안은 코드이므로 그 안의 주석은 걷고 문자열은 다시 이 함수로 읽는다.
 */
private fun scanString(source: String, quote: Int): Pair<String, Int> {
    val raw = source.startsWith("\"\"\"", quote)
    val delimiter = if (raw) "\"\"\"" else "\""
    val out = StringBuilder(delimiter)
    var index = quote + delimiter.length
    while (index < source.length) {
        when {
            !raw && source[index] == '\\' -> {
                out.append(source, index, minOf(index + 2, source.length))
                index += 2
            }
            source.startsWith(delimiter, index) -> {
                out.append(delimiter)
                return out.toString() to index + delimiter.length
            }
            source.startsWith("\${", index) -> {
                val close = templateEnd(source, index + 2)
                out.append("\${").append(stripKotlinComments(source.substring(index + 2, close))).append('}')
                index = close + 1
            }
            else -> out.append(source[index++])
        }
    }
    return out.toString() to source.length
}

/** 템플릿 `${` 바로 뒤([from])에서 짝이 맞는 `}`의 위치. 템플릿 속 문자열·문자 리터럴은 건너뛴다. */
private fun templateEnd(source: String, from: Int): Int {
    var depth = 0
    var index = from
    while (index < source.length) {
        when (source[index]) {
            '"' -> {
                index = scanString(source, index).second
                continue
            }
            '\'' -> {
                index = charLiteralEnd(source, index)
                continue
            }
            '{' -> depth++
            '}' -> if (depth == 0) return index else depth--
        }
        index++
    }
    return source.length
}

/** [quote]에서 시작하는 문자 리터럴(`'a'`·`'\''`·`'"'`) 바로 뒤의 위치. */
private fun charLiteralEnd(source: String, quote: Int): Int {
    var index = quote + 1
    while (index < source.length && source[index] != '\'') {
        index += if (source[index] == '\\') 2 else 1
    }
    return minOf(index + 1, source.length)
}
