package com.worksoc.goaicoach.architecture

import java.io.File
import org.json.JSONObject

/**
 * **셸 상태 원장**(refactor backlog #46) — *"조립만 하는 셸은 상태를 소유하지 않는다"* 를 **종류**로 적는다.
 *
 * ## 무엇을 바꿨나
 * 전에는 `LayeringContractTest`가 셸 파일의 코드 줄 중 `remember|mutableStateOf|LaunchedEffect`가 든 줄을
 * **세어** 42/42·13/13·4/4로 막았다. 그 개수는 틀린 것을 쟀다 — 올바른 코드가 테스트를 깼고(저장소 넷이
 * `remember` 없이 매 재구성 새로 만들어졌고, 컴포즈 상태 8개가 "훅 예산 절약"을 사유로 프로세스 전역
 * `object`로 이사했다), `rememberSaveable`·`derivedStateOf`·`produceState`·`DisposableEffect`는 안 보였고,
 * 훅을 지우면 여유가 조용히 생겼다. 이제 셸의 **모든 훅 자리**가 `app-android/architecture-budgets.json`에
 * 이름·종류·사유로 적히고 **소스와 양방향으로** 같아야 한다.
 *
 * ## JSON과 이 파일의 역할 분담
 *  - **JSON은 "무엇이 있고 왜"만 적는다** — 자리 키(`소유 함수.바인딩 이름`, 효과는 `효과(첫 키)`),
 *    [Kind], `why`, `since`, 그리고 셸의 `codeLineBudget`. 파일 경로는 없다(셸 id → 파일은 [GUARDED_SHELLS]).
 *  - **무엇이 허용되는지는 이 파일이 정한다** — [Kind]는 닫힌 목록이고, 역할([Role])마다 가질 수 있는 종류,
 *    종류마다의 모양 검사, 부채 동결([SHELL_DEBT]), 효과 수·줄 상한([GuardedShell]), 동결 목록 셋
 *    ([FROZEN_UNREMEMBERED_INSTANCES]·[FROZEN_PROCESS_GLOBAL_STATE]·[PINNED_UNREMEMBERED]), 관용구([COMPOSE_IDIOMS])가 여기 있다.
 *  - 그래서 **허용 종류 항목 하나는 JSON 한 줄(since·why)로** 들어오고(리뷰에 diff로 보인다),
 *    **새 부채·새 효과·줄 상한 상향·새 전역 상태·remember 안 한 저장소는 테스트 코드를 고쳐야만** 늘어난다.
 *  - ⚠️ **이 파일의 정책을 넓히는 것도 옛 "숫자 상향"이다** — 종류 추가, [Kind.roles]·[Kind.constructs] 확장,
 *    모양 검사 완화(초기값·접미사·키 목록), 관용구 추가, 효과 수·줄 상한 상향, 새 부채. 전부 사용자 결정이
 *    필요하고, 사유는 `LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`의 이력 주석에 적는다.
 *    판정이 빨간데 맞는 종류가 없다면 답은 정책을 넓히는 것이 아니라 **그 상태를 쓰는 역할로 옮기는 것**이다.
 *
 * ## 규칙(메시지 앞의 L 번호)
 *  - L1 목록에 없는 훅 자리(또는 이름 없는 자리). 붙여 넣을 줄은 모양 검사까지 통과하는 허용 종류가 있을 때만 준다
 *    (효과에는 수와 상관없이 주지 않는다 — 추가도 맞바꾸기도 사용자 결정이다).
 *  - L2 자리가 없어진 항목. L3 스키마(모르는 키·종류, 빈 why, since 형식). L4 구성과 종류가 안 맞는다.
 *  - L5 역할이 그 종류를 못 가진다. L6 셸 파일의 object·class·최상위에 훅이 있다.
 *  - L7 종류별 모양, 부채 접촉(별칭 포함), 숨은 효과, HOSTED 모양. L8 부채 동결. L9 효과 수. L10 줄 예산·상한.
 *  - L11 셸 목록. L12 파서가 못 읽은 훅(FQN·멤버 호출·`::` 참조·문자열 템플릿·중첩 훅·별칭 import·색인 닻).
 *  - L13 remember 안 한 저장소. L14 프로세스 전역 컴포즈 상태. L15 훅이 아닌 위임. L16 remember 안 한 상태.
 *  - L17 원장이 Gradle 테스트 입력으로 선언됐나. L18 고정 이름(함정 67).
 *
 * ## 파서
 * [PackageImportGraph.stripCommentsAndStrings]를 **고치지 않고** 그대로 쓴다(scc.py 기준선에 묶여 있다).
 * 주석·문자열·import 줄을 지운 뒤 훅 토큰을 찾고, 토큰 뒤의 `<…>`·`(…)`·같은 줄에서 여는 `{…}`까지를
 * 그 자리의 범위로 본다. 앞선 흡수형 자리(`remember` 등) 범위 안의 토큰은 새 자리가 아니라 그 자리에
 * 접힌다 — `var x by remember {⏎ mutableStateOf(…)⏎ }`는 한 자리다. 접혀도 되는 것은 **스냅숏 생산자뿐**이고,
 * 다른 훅·효과가 접히면 L12(숨은 자리)다. 괄호가 어긋나면 범위가 파일 끝까지 가서 뒤의 자리를 삼키고,
 * 그 항목들이 낡아 빨개진다(닫힌 쪽으로 실패). 훅 단어가 훅 호출로 읽히지 않으면(FQN·멤버 호출·참조·식별자)
 * 초록이 아니라 L12로 빨갛다.
 *
 * ## 구성(한 자리가 무엇을 하는가 — 위에서부터 첫 규칙)
 * 효과 → 관용구 → 숨은 효과(흡수형 범위 안의 `launch`/`async`/`.collect…`/`launchIn`/`stateIn`/`shareIn`/`produceIn`,
 * 접힌 효과, `produceState`) → 저장소 `remember*`(HOSTED, 모르면 상태) → 상태(스냅숏 생산자·`rememberSaveable`·
 * `collectAsState`·`by`·`MutableStateFlow`/`MutableSharedFlow`/`Channel`·스냅숏 상태를 든 클래스·상태 팩토리)
 * → 인스턴스(`remember { Ctor(…) }` 생성자 하나, `.also{}`/`.apply{}`만 허용) → 메모.
 * ⚠️ 숨은 효과가 상태보다 먼저다. 그래서 콜백 안에서 `scope.launch`를 부르는 컨트롤러를 `remember { … }` 람다
 * 안에서 조립하면 **구성상 늘 빨갛다** — 그 콜백은 컨트롤러 안으로 옮긴다.
 *
 * ## 남은 위험
 *  1. **종류는 자기 신고다**(구성 안에서). 모양 검사·부채 접촉 검사·역할 제한·`why`/`since` 필수·보이는
 *     JSON diff·줄 브레이크가 좁히지만 없애지는 못한다. 예: SCREEN·SECTION 역할에서 WIRING이 막힌
 *     `remember { Any() }`를 STARTUP_READ로 **이름만 바꿔** 적으면 통과한다(생성자 이름 검사만 막는다) — 리뷰가 볼 몫이다.
 *     부채 종류끼리는 서로 바꿔 적어도 된다(동결 대상은 키다).
 *  2. **훅을 든 도우미는 여전히 세지 않는다** — `build*UiState`, `*Effect`, `Provide*`, 다이얼로그 게이트,
 *     다른 파일의 `fooState()` 같은 도우미. 그 파일들을 쪼개는 곳은 #44다.
 *  3. **SCREEN·SECTION 역할도 이제 허용 종류 상태를 JSON 한 줄로 얻는다**(13/13·4/4는 막았었다). 한계는
 *     역할 제한 종류와 400/350 줄 예산이다(사용자 결정 Q1: 별도 개수 상한은 두지 않는다).
 *  4. **이름 바꾸기는 오류 둘**이다. 효과 id는 첫 키 순서가 바뀌면 달라지고, 같은 키의 `#2`는 소스 순서라
 *     위에 끼우면 번호가 밀린다. 부채 이름을 바꾸거나 소유 함수를 옮기면 [SHELL_DEBT] 키를 1:1로 옮겨 적는다.
 *  5. **파서 가장자리** — 다음 줄로 넘어간 후행 람다, `;`로 이은 문장, 백틱 이름 같은 드문 문법은 L12·L2로
 *     닫힌 쪽으로 실패한다. 한 줄에서 안 닫힌 문자열은 메시지의 줄 번호를 밀 수 있는데, 컴파일 안 되는
 *     코드에서만 생긴다. 모양 검사는 글자만 본다(예: 별칭·지역 함수 한 겹은 따라가지만 그 너머는 못 본다).
 *  6. **L13·L16의 사각지대** — 식 안의 생성(`GoCoachApp.kt`의 `ExperimentalFeaturesStore(context)` 같은 것)과
 *     람다(onClick·content) 안의 생성자는 보지 않는다. 함수 본문의 "문장 층"(if/when/for/while/try 블록은
 *     투명, 람다는 불투명)만 본다. ⚠️ **MainActivity는 합성 전체가 `setContent { CompositionLocalProvider { … } }`
 *     람다 안이라 L13·L16이 거기서는 한 번도 발화할 수 없다** — #45·#47이 MainActivity로 상태를 옮길 때 리뷰가 볼 몫이다.
 *     접미사(`Store|Client|Port|Log|Mirror`) 밖의 인스턴스(`AnalysisResultCache(…)` 등)도 L13에 안 보인다.
 *  7. **리뷰가 짚었고 받아들인 한계** — 부채 접촉 검사는 지역 val/var 별칭만 따라가고 지역 **함수**(`fun alias() = legacy`)
 *     별칭은 못 본다. L17은 람다로 감싼 `.optional()`(`.also { it.optional() }`)을 못 본다. L14와 스냅숏 상태 클래스 색인은
 *     **한 겹**만 본다(상태 클래스를 든 클래스는 안 보인다). 부채 이름을 바꾸고 옛 키를 PAID로 뒤집은 뒤 새 이름을 허용
 *     종류로 적는 세탁 길은 막지 못한다 — 다만 [SHELL_DEBT](테스트 코드) 수정이 필요해 리뷰 diff에 보인다.
 */
internal object ShellStateLedger {

    /** 셸의 역할 — [GUARDED_SHELLS]가 셸마다 하나로 고정한다. JSON은 역할을 바꿀 수 없다. */
    enum class Role { COMPOSITION_ROOT, APP_SHELL, SCREEN, SECTION }

    /** 파서가 자리에 붙이는 구성. 첫 번째로 맞는 규칙이 이긴다(파일 KDoc "구성"). */
    enum class Construct {
        STATE,
        EFFECT,

        /**
         * 코루틴이 셈한 효과 밖에 숨은 자리 — 흡수형 범위 안의 `launch`/`async`/`.collect…`/`launchIn`/`stateIn`/
         * `shareIn`/`produceIn`, 접혀 들어간 효과, 그리고 `produceState`(그 자체가 상태를 쓰는 코루틴이다).
         * JSON이 뭐라 하든 늘 빨갛다(L7).
         */
        HIDDEN_EFFECT,

        /** 컴포즈 관용구([COMPOSE_IDIOMS]와 키 없는 `remember { MutableInteractionSource() }`·`FocusRequester()`) — 원장에 안 적는다. */
        IDIOM,

        /** 저장소의 다른 파일이 정의한 `remember*` — 상태는 그 파일이 소유한다. */
        HOSTED,

        /** 저장소가 정의하지 않은 `remember*`(또는 같은 파일이 정의한 것) — 판정에서는 상태로 본다(닫힌 쪽으로 실패). */
        UNKNOWN_REMEMBER,
        INSTANCE,
        MEMO,
        ;

        /** 판정이 보는 구성 — 숨은 효과는 효과로, 모르는 `remember*`는 상태로. */
        val judged: Construct
            get() = when (this) {
                HIDDEN_EFFECT -> EFFECT
                UNKNOWN_REMEMBER -> STATE
                else -> this
            }
    }

    enum class OwnerKind { FUNCTION, OBJECT, CLASS, TOP_LEVEL }

    /**
     * 자리의 종류 — **닫힌 목록**이다. JSON은 여기 없는 종류를 만들 수 없다(L3).
     * [allowed]가 거짓인 것은 부채 종류로, [SHELL_DEBT]의 동결 목록을 담는 데만 존재한다.
     * [shape]는 L1·L4·L7 메시지가 그대로 인용하는 요구 모양이다(실제 검사는 `verdict`의 모양 검사).
     *
     * ⚠️ **넓히지 말 것.** 새 종류, [roles]·[constructs] 확장, [shape]와 그 검사의 완화는 옛 "숫자 상향"이고
     * 사용자 결정이 필요하다(사유는 LayeringContractTest 이력 주석에). L5("이 역할은 이 종류를 못 가진다")가
     * 뜨면 답은 역할 목록에 더하는 것이 아니라 **상태를 그것을 쓰는 역할로 옮기는 것**이다.
     */
    enum class Kind(val allowed: Boolean, val constructs: Set<Construct>, val roles: Set<Role>, val shape: String) {
        /** 앱 수명 동안 하나면 되는 플랫폼 어댑터(저장소·포트·클라이언트·로그·미러) 인스턴스. 모든 역할. */
        PLATFORM_PORT(
            true, setOf(Construct.INSTANCE), Role.entries.toSet(),
            "`remember(context) { FooStore(context) }` — exactly one *Store/*Port/*Client/*Log/*Mirror constructor " +
                "(optionally .also{}/.apply{}), no controller/holder built in its arguments, remember keys ⊆ {context, applicationContext}",
        ),

        /** 컨트롤러·홀더·배선 컨텍스트 — 조립하는 쪽(조립 루트·앱 셸)만. 함정 67 때문에 키는 보지 않는다. */
        WIRING(
            true, setOf(Construct.INSTANCE, Construct.MEMO), setOf(Role.COMPOSITION_ROOT, Role.APP_SHELL),
            "`remember(…) { FooController(…) }` or the wiring-context `remember(…) { object : … { … } }` — no snapshotFlow; " +
                "any other memo must not read OWED debt",
        ),

        /** 컴포지션에 들어올 때 한 번 읽는 값(디스크 스냅숏·상수·계획). 컨트롤러를 만들지 않는다. */
        STARTUP_READ(
            true, setOf(Construct.INSTANCE, Construct.MEMO), Role.entries.toSet(),
            "`remember(keys) { store.load() }` — a value read once; builds no *Controller/*Holder/*Coordinator/*Applier/*ViewModel " +
                "and reads no OWED debt",
        ),

        /** 현재 목적지 — 앱 셸의 본업. */
        NAVIGATION(
            true, setOf(Construct.STATE), setOf(Role.APP_SHELL),
            "`var destination by remember { mutableStateOf(initialDestination(…) | ScreenDestination.X | null) }` — the lambda is exactly that one call",
        ),

        /** 다이얼로그·팝업·섹션을 여닫는 불리언(또는 null) 게이트. */
        VISIBILITY_GATE(
            true, setOf(Construct.STATE), Role.entries.toSet(),
            "`var showX by remember { mutableStateOf(false | null | port.read()) }` — the lambda is exactly that one call " +
                "and port is a PLATFORM_PORT site of the same function",
        ),

        /** 탭 횟수 같은 제스처 카운터 — 화면·섹션만(앱 셸은 안 된다). */
        GESTURE_COUNTER(
            true, setOf(Construct.STATE), setOf(Role.SCREEN, Role.SECTION),
            "`var taps by remember { mutableStateOf(0) }` (or mutableIntStateOf(0)) — the lambda is exactly that one call",
        ),

        /** 홀더 상태의 컴포즈 사본과 그 수집 효과 — 조립하는 쪽만. 수신자당 사본 하나. */
        HOLDER_MIRROR(
            true, setOf(Construct.STATE, Construct.EFFECT), setOf(Role.COMPOSITION_ROOT, Role.APP_SHELL),
            "state `remember { mutableStateOf(H.current) }` with H a WIRING site and `LaunchedEffect(H)` listed, " +
                "or `H.state.collectAsState()` with H a WIRING/PLATFORM_PORT site; effect `LaunchedEffect(H) { H.state.collect { … } }`; " +
                "one mirror state per receiver",
        ),

        /** 다른 상태에서 파생한 읽기 전용 값. */
        DERIVED_VIEW(
            true, setOf(Construct.STATE), Role.entries.toSet(),
            "`val x by remember { derivedStateOf { … } }` with nothing else inside the remember lambda",
        ),

        /** 컴포지션 시작에 한 번 도는 읽기·기록(로그 한 줄, 전역 적재 1회) — 조립하는 쪽만. */
        STARTUP_EFFECT(
            true, setOf(Construct.EFFECT), setOf(Role.COMPOSITION_ROOT, Role.APP_SHELL),
            "`LaunchedEffect(Unit | port | wiring) { … }` run once — writes no var of its function (directly, through .value, " +
                "++/--/op= or a local function) and has no collect/snapshotFlow/while/for/repeat/launchIn/onEach",
        ),

        /** 조립 루트의 엔진 부트스트랩 — null로 시작하는 상태와 그것을 채우는 효과 하나. */
        BOOTSTRAP(
            true, setOf(Construct.STATE, Construct.EFFECT), setOf(Role.COMPOSITION_ROOT),
            "state `remember { mutableStateOf<T?>(null) }` plus the one effect that assigns it",
        ),

        /** 다른 파일의 `rememberX()`가 소유한 상태를 셸은 읽기만 한다. */
        HOSTED_ROLE_STATE(
            true, setOf(Construct.HOSTED), Role.entries.toSet(),
            "`val x = rememberX(…)` where rememberX lives in another file that owns the state",
        ),
        DOMAIN_STATE(false, setOf(Construct.STATE), Role.entries.toSet(), DEBT_SHAPE),
        ENGINE_STATE(false, setOf(Construct.STATE), Role.entries.toSet(), DEBT_SHAPE),
        PERSISTED_PREFERENCE(false, setOf(Construct.STATE), Role.entries.toSet(), DEBT_SHAPE),
        WORKFLOW_STATE(false, setOf(Construct.STATE), Role.entries.toSet(), DEBT_SHAPE),
        CHILD_UI_STATE(false, setOf(Construct.STATE), Role.entries.toSet(), DEBT_SHAPE),
        FOREIGN_ROLE_STATE(false, setOf(Construct.STATE), Role.entries.toSet(), DEBT_SHAPE),
        WORKFLOW_EFFECT(false, setOf(Construct.EFFECT), Role.entries.toSet(), DEBT_SHAPE),
    }

    private const val DEBT_SHAPE = "debt — only the frozen keys in ShellStateLedger.SHELL_DEBT"

    /**
     * 부채를 갚으면 같은 커밋에서 OWED→PAID로 뒤집는다. **PAID 줄은 지우지 않는다** — 갚은 부채는 이 파일에서
     * 어떤 소유 함수·종류로도 못 돌아온다. PAID는 **상태가 이 파일을 떠났을 때만**이다(이름만 바꾼 것은 갚은 게 아니다).
     */
    enum class DebtState { OWED, PAID }

    /**
     * 지키는 셸 하나. [lineBudgetCeiling]은 JSON `codeLineBudget`의 상한이고(내리는 것은 JSON, 올리는 것은
     * 테스트 코드), [effectCount]는 효과 자리 수와 **정확히** 같아야 한다.
     */
    data class GuardedShell(val file: () -> File, val role: Role, val lineBudgetCeiling: Int, val effectCount: Int)

    /**
     * 지키는 셸 전부 — JSON `shells`의 키와 정확히 같아야 한다(L11).
     *
     * ⚠️ **줄기만 한다.** 상한·효과 수를 올리는 것은 옛 "숫자 상향"이고 사용자 결정이 필요하다.
     * 효과를 지웠으면 그 셸의 효과 수를 내린다. 바꾼 사유는 LayeringContractTest 이력 주석에 적는다.
     */
    val GUARDED_SHELLS: Map<String, GuardedShell> = mapOf(
        "mainActivity" to GuardedShell({ RepoPaths.compositionFile("MainActivity.kt") }, Role.COMPOSITION_ROOT, 43, 2),
        "goCoachApp" to GuardedShell({ RepoPaths.goCoachApp }, Role.APP_SHELL, 777, 8),
        "settingsScreen" to GuardedShell({ RepoPaths.uiFile("SettingsScreen.kt") }, Role.SCREEN, 400, 0),
        "developerTestSection" to GuardedShell({ RepoPaths.uiFile("DeveloperTestSection.kt") }, Role.SECTION, 350, 0),
    )

    /**
     * 셸마다 동결한 부채 — JSON의 부채 종류 항목은 여기 OWED와 **양방향으로 정확히** 같아야 한다(L8).
     *
     * ⚠️ **줄기만 한다.** 세 경우를 가른다.
     *  - **갚기**: 상태가 이 파일을 떠났다(그것을 쓰는 역할로 내려갔다) → 같은 커밋에서 OWED→PAID, JSON 항목은 지운다.
     *  - **옮겨 적기(허용)**: 같은 상태의 이름을 바꿨거나 소유 함수를 옮겼다(예: GoCoachScreen을 쪼갬) → 키를 1:1로
     *    바꿔 적고 JSON도 같은 부채 종류로 옮긴다. 부채 수는 그대로다. 이름을 바꾸고 PAID로 뒤집는 것은 갚기가 아니다.
     *  - **새 부채·맞바꾸기**: 다른 상태를 넣는 것(하나 빼고 다른 것 넣기 포함)은 옛 "숫자 상향"이고 사용자 결정이 필요하다.
     * 사유는 LayeringContractTest 이력 주석에.
     */
    val SHELL_DEBT: Map<String, Map<String, DebtState>> = mapOf(
        "mainActivity" to emptyMap(),
        "goCoachApp" to mapOf(
            "GoCoachScreen.premiumState" to DebtState.OWED,
            "GoCoachScreen.isEngineBusy" to DebtState.OWED,
            "GoCoachScreen.isEngineBlockingBusy" to DebtState.OWED,
            "GoCoachScreen.engineActivityIndicator" to DebtState.OWED,
            "GoCoachScreen.engineTurnWaitCompletionSeq" to DebtState.OWED,
            "GoCoachScreen.isEngineReady" to DebtState.OWED,
            "GoCoachScreen.uxOptions" to DebtState.OWED,
            "GoCoachScreen.isScoreGraphExpanded" to DebtState.OWED,
            "GoCoachScreen.hasCompletedEngineStartup" to DebtState.OWED,
            "GoCoachScreen.undoEngineInterventionQuietUntil" to DebtState.OWED,
            "GoCoachScreen.isPendingUndoSync" to DebtState.OWED,
            "GoCoachScreen.LaunchedEffect(engineClient)" to DebtState.OWED,
            "GoCoachScreen.LaunchedEffect(sessionStore)" to DebtState.OWED,
            "GoCoachScreen.LaunchedEffect(preferencesStore)" to DebtState.OWED,
            "GoCoachScreen.LaunchedEffect(savedSessionUiState)" to DebtState.OWED,
            "GoCoachScreen.LaunchedEffect(isEngineReady)" to DebtState.OWED,
            "GoCoachScreen.LaunchedEffect(isGameEnded)" to DebtState.OWED,
        ),
        "settingsScreen" to mapOf(
            "SettingsScreen.authState" to DebtState.OWED,
            "SettingsScreen.showEmailDialog" to DebtState.OWED,
            "SettingsScreen.isEmailSubmitting" to DebtState.OWED,
            "SettingsScreen.showDeleteAccountDialog" to DebtState.OWED,
            "SettingsScreen.isDeletingAccount" to DebtState.OWED,
        ),
        "developerTestSection" to emptyMap(),
    )

    /**
     * 셸 함수 본문에서 `remember` 없이 매 재구성 새로 만들어지는 저장소·클라이언트(L13).
     *
     * ⚠️ **줄기만 한다**(#45가 넷을 `remember`로 감싸 0으로 만든다). 여기에 이름을 더하는 것은 옛 "숫자 상향"이고
     * 사용자 결정이 필요하다 — 새로 생겼으면 더하지 말고 `remember(context) { … }`로 감싼다. 사유는 이력 주석에.
     */
    val FROZEN_UNREMEMBERED_INSTANCES: Set<String> = setOf(
        "GoCoachScreen.authClient",
        "GoCoachScreen.deviceIdentityStore",
        "GoCoachScreen.credentialManagerClient",
        "GoCoachScreen.premiumStateStore",
    )

    /**
     * 프로덕션 소스 전체에서 `object`/`companion object`/`enum class`/최상위에 사는 컴포즈 상태(L14) — 한 겹 건너
     * 스냅숏 상태를 든 클래스의 인스턴스를 쥔 것도 포함한다.
     *
     * ⚠️ **줄기만 한다.** 여기에 이름을 더하는 것은 옛 "숫자 상향"이고 사용자 결정이 필요하다 — 전역으로 옮기는
     * 것은 원장을 피하는 길이 아니다. #45가 넷(또는 여섯)을 셸 상태·공급자로 되돌린다. 사유는 이력 주석에.
     */
    val FROZEN_PROCESS_GLOBAL_STATE: Set<String> = setOf(
        "SplashVisibility.isShowing",
        "FinishedGameFlow.pendingMoveCount",
        "FinishedGameFlow.dismissedJudgementKey",
        "AppFontScaleState.scale",
        "AttendanceClaimReplaySignal.revision",
        "AttendanceClaimReplaySignal.lastCheckedInDay",
        "GuideBlockingOverlays.count",
        "GuideTargetSpots.spots",
    )

    /**
     * **절대 훅 자리가 되면 안 되는** 이름(L18) — `GoCoachApp.kt`의 엔진 정체 세 줄. 함정 67: `remember`로
     * 감싸는 순간 준비 전 답이 그 자리에서 굳는다. #43(WiringContextFreezeContractTest)과 별개로 여기서도 막는다.
     *
     * ⚠️ **늘기만 한다** — 여기서 빼는 것이 이 그물을 푸는 쪽이다. 빼려면 사용자 결정이 필요하고 사유는 이력 주석에.
     */
    val PINNED_UNREMEMBERED: Set<String> = setOf(
        "GoCoachScreen.identity",
        "GoCoachScreen.engineName",
        "GoCoachScreen.engineDiagnostic",
    )

    /**
     * 원장에 적지 않는 컴포즈 관용구(허용 목록). 저장소가 같은 이름을 정의하면 관용구가 아니다(가림 방지).
     *
     * ⚠️ **줄기만 한다** — 허용 목록이라 늘리는 것이 그물을 푸는 쪽이다. 더하는 것은 옛 "숫자 상향"이고
     * 사용자 결정이 필요하다. 사유는 이력 주석에.
     */
    val COMPOSE_IDIOMS: Set<String> = setOf(
        "rememberCoroutineScope",
        "rememberScrollState",
        "rememberLazyListState",
        "rememberUpdatedState",
    )

    // ── 파서 ──────────────────────────────────────────────────────────────────────────────

    /** `(start, end)` — end는 닫는 괄호 **다음** 위치(범위 밖). */
    data class Span(val start: Int, val end: Int)

    /** 선언 하나. [composable]은 `@Composable` 함수인가(조립 층 코루틴 검사가 쓴다). */
    data class Decl(val kind: OwnerKind, val name: String, val start: Int, val end: Int, val composable: Boolean = false)

    /** 훅 자리 하나. [name]이 null이면 이름 없는 자리(명명 인자·구조 분해·맨 식) — 효과가 아니면 L1. */
    data class Site(
        val line: Int,
        val hook: String,
        val folded: List<String>,
        val ownerKind: OwnerKind,
        val owner: String,
        val name: String?,
        val construct: Construct,
        val binding: String?,
        val statement: String,
        val start: Int,
        val end: Int,
        val args: Span?,
        val lambda: Span?,
    ) {
        val key: String? get() = name?.let { "$owner.$it" }
    }

    class Scan(val text: String, val sites: List<Site>, val parseErrors: List<String>, val decls: List<Decl>)

    /**
     * 저장소 색인 — 저장소가 정의한 `remember*`(이름 → 정의 파일), 컴포즈 스냅숏 상태를 직접 멤버로 든 클래스,
     * 상태를 돌려주는 팩토리 함수.
     */
    class RepoIndex(
        val rememberFunctions: Map<String, Set<String>>,
        val snapshotStateClasses: Set<String>,
        val stateFactories: Set<String>,
    ) {
        /** 스캔 중인 파일 **말고** 다른 파일이 정의했으면 HOSTED. [scannedFile]이 없으면(심은 소스) 정의만 있으면 된다. */
        fun isHosted(name: String, scannedFile: File?): Boolean {
            val files = rememberFunctions[name] ?: return false
            val self = scannedFile?.canonicalPath
            return files.any { it != self }
        }
    }

    data class ShellPolicy(
        val role: Role,
        val lineBudgetCeiling: Int,
        val effectCount: Int,
        val debt: Map<String, DebtState>,
        val pinned: Set<String> = PINNED_UNREMEMBERED,
    )

    private fun rx(pattern: String, vararg options: RegexOption): Regex = Regex("(?U)$pattern", options.toSet())

    /**
     * 스냅숏 상태 생산자 — **한 목록**에서 모든 규칙(훅 토큰·접기·상태 판정·L16·클래스 색인·L14)이 파생된다.
     * 함수(`mutable*StateOf`·`mutableState{List,Map,Set}Of`·`derivedStateOf`·`toMutableState{List,Map}`)와
     * 생성자(`SnapshotState{List,Map,Set}(…)`). `toMutableState*`(컴포즈 런타임 1.8)는 수신자에 붙여 부르므로
     * `.` 뒤에서도 훅 토큰이다([HOOK_EXTENSION]).
     */
    private const val PRODUCER_EXTENSIONS = "toMutableState(?:List|Map)"
    private const val PRODUCER_FUNCTIONS =
        "mutable(?:Int|Long|Float|Double)?StateOf|mutableState(?:List|Map|Set)Of|derivedStateOf|$PRODUCER_EXTENSIONS"
    private const val PRODUCER_CLASSES = "SnapshotState(?:List|Map|Set)"
    private const val EFFECT_WORDS = "LaunchedEffect|DisposableEffect|SideEffect"

    /** 타입 인자(있으면) 뒤에 `(`가 오는가 — 타입 자리의 `SnapshotStateList<Int>`는 호출이 아니다. */
    private const val CALL_AFTER_TYPE_ARGS = """(?=\s*(?:<[^(){};=\n]*>)?\s*\()"""

    private val HOOK = rx("""(?<![\w@$.:])(remember\w*|$PRODUCER_FUNCTIONS|produceState|$EFFECT_WORDS)(?=\s*[({<])""")
    private val HOOK_CLASS = rx("""(?<![\w@$.:])($PRODUCER_CLASSES)$CALL_AFTER_TYPE_ARGS""")
    private val COLLECT = rx("""\.(collectAsState(?:WithLifecycle)?)(?=\s*[({<])""")
    private val HOOK_EXTENSION = rx("""\.($PRODUCER_EXTENSIONS)(?=\s*[({<])""")

    /** 느슨한 훅 단어 — 이 중 훅 토큰으로 읽히지 않은 것은 전부 L12다(FQN·멤버 호출·`::` 참조·식별자). `@라벨`은 뺀다. */
    private val LOOSE = rx(
        """(?<![\w@])(remember\w*|mutable\w*StateOf|mutableState(?:List|Map|Set)Of|derivedStateOf|$PRODUCER_EXTENSIONS|produceState""" +
            """|$EFFECT_WORDS|collectAsState(?:WithLifecycle)?)\b|(?<![\w@])($PRODUCER_CLASSES)$CALL_AFTER_TYPE_ARGS""",
    )
    private val TEMPLATE_HOOK = rx(
        """\b(remember\w*|mutable\w*StateOf|mutableState(?:List|Map|Set)Of|$EFFECT_WORDS|derivedStateOf|$PRODUCER_EXTENSIONS|produceState""" +
            """|collectAsState\w*|$PRODUCER_CLASSES)\b""",
    )
    private val PRODUCER_NAME = rx("""(?:$PRODUCER_FUNCTIONS|$PRODUCER_CLASSES)""")
    private val PRODUCER_AT = rx("""^($PRODUCER_FUNCTIONS|$PRODUCER_CLASSES)\b""")
    private val STATE_HOOKS = setOf("produceState", "rememberSaveable", "collectAsState", "collectAsStateWithLifecycle")
    private val EFFECTS = setOf("LaunchedEffect", "DisposableEffect", "SideEffect")
    private val ABSORBING = EFFECTS + setOf("remember", "rememberSaveable", "produceState")
    private val FLOW_CONTAINERS = rx("""\b(MutableStateFlow|MutableSharedFlow|Channel)\s*[(<]""")

    /** 클래스 색인·전역 스캔의 생산자 — FQN(`androidx.compose.runtime.mutableStateOf`)도 잡는다. */
    private val GLOBAL_PRODUCER = rx("""(?<![\w@$:])(?:$PRODUCER_FUNCTIONS)\s*[(<{]|(?<![\w@$:])$PRODUCER_CLASSES\s*[(<]""")
    private val HIDDEN_EFFECT_CALL = rx("""\b(?:launch|async)\s*[({]|\.collect\w*\s*[({]|\b(?:launchIn|stateIn|shareIn|produceIn)\s*\(""")
    private val LAUNCH_CALL = rx("""\b(?:launch|async)\s*[({]|\b(?:launchIn|stateIn|shareIn|produceIn)\s*\(""")
    private val IDIOM_INSTANCE_LAMBDA = rx("""\{\s*(MutableInteractionSource|FocusRequester)\(\s*\)\s*\}""")
    private val ALSO_APPLY = rx("""^\.(?:also|apply)\s*\{""")
    private val BIND = rx(
        """^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|override|lateinit|const)\s+)*""" +
            """(val|var)\s+(\w+)(?:\s*:\s*[^=]+?)?\s*(=|by)(?=\s|$)""",
    )
    private val FUN_DECL = rx("""\bfun\s+(?:<[^>]*>\s+)?(?:[\w.]+\.)?(\w+)\s*\(""")
    private val DECLARATION_NAME = rx("""\bfun\s+(?:<[^>]*>\s+)?(?:[\w.]+\.)?$""")
    private val COMPOSABLE_PREFIX = rx(
        """@Composable\b(?:\s*\([^)]*\))?(?:\s+@\w+(?:\s*\([^)]*\))?|\s+(?:private|internal|public|protected|override|inline""" +
            """|suspend|operator|infix|tailrec|open|final|abstract|actual|expect))*\s+$""",
    )
    private val TYPE_DECL = rx("""\b(?:(companion)\s+object\b(?:\s+(\w+))?|(enum\s+class|object|class|interface)\s+(\w+))""")

    /** 클래스 이름 뒤 주 생성자의 여는 `(` — 타입 인자·`@Ann`·가시성 `constructor`를 건너뛴다(여러 줄도). */
    private val PRIMARY_CONSTRUCTOR = rx(
        """(?<![\w:])class\s+(\w+)\s*(?:<[^(){};=]*>)?\s*(?:(?:@\w+\s*|(?:private|internal|public|protected)\s+)*constructor\s*)?\(""",
    )
    private val BY_AT_END = rx("""\bby$""")

    /** 프로퍼티 문장 — 줄 머리 또는 같은 줄의 `{`/`;` 바로 뒤(한 줄 `object X { var v by … }`도 본다). */
    private val PROP = rx(
        """(?:^|(?<=[{;]))[ \t]*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|protected|override|lateinit|const|open|final)\s+)*""" +
            """(val|var)\s+(\w+)""",
        RegexOption.MULTILINE,
    )
    private val UNREMEMBERED_INSTANCE = rx(
        """(?:^|(?<=[{;]))[ \t]*(val|var)\s+(\w+)(?:\s*:\s*[^=\n]+?)?\s*=\s*([A-Z]\w*(?:Store|Client|Port|Log|Mirror))\s*\(""",
        RegexOption.MULTILINE,
    )
    private val UNREMEMBERED_FLOW = rx(
        """(?:^|(?<=[{;]))[ \t]*(val|var)\s+(\w+)(?:\s*:\s*[^=\n]+?)?\s*=\s*(MutableStateFlow|MutableSharedFlow|Channel)\s*[(<]""",
        RegexOption.MULTILINE,
    )

    /** 위임 — 줄 어디서든(주석·수식어·`{` 뒤) 잡는다. */
    private val DELEGATE = rx("""(?<![\w.$])(val|var)\s+(\w+)(?:\s*:\s*[^=\n]+?)?\s+by\s+((?:\w+\.)*)(\w+)\s*[({<]""")
    private val LOCAL_BINDING = rx("""(?<![\w.$])(?:val|var)\s+(\w+)(?:\s*:\s*[^=\n]+?)?\s*(?:=|\bby\b)""")
    private val REMEMBER_FUN = rx("""\bfun\s+(?:<[^>]*>\s+)?(remember\w+)\s*\(""")
    private val STATE_RETURN_TYPE = rx(
        """\b(?:Mutable)?(?:Int|Long|Float|Double)?State<|\b(?:Mutable)?(?:Int|Long|Float|Double)State\b|\bMutableState\b""" +
            """|\bSnapshotState(?:List|Map|Set)\b""",
    )
    private val RETURNS_PRODUCER = rx("""\breturn\s+(?:[\w.]+\.)?(?:$PRODUCER_FUNCTIONS|$PRODUCER_CLASSES)\s*[(<{]""")
    private val GLOBAL_STATE_WORD = rx("""\bMutableStateFlow\b|\bmutableStateOf\b""")
    private val GLOBAL_STATE_TYPE = rx(
        """:\s*(?:[\w.]+\.)?(?:(?:Mutable)?(?:Int|Long|Float|Double)?State<|(?:Mutable)?(?:Int|Long|Float|Double)State\b|MutableState\b)""",
    )
    private val GLOBAL_SNAPSHOT_TYPE = rx("""\bSnapshotState(?:List|Map|Set)\b""")
    private val ALIAS_RUNTIME = rx("""^\s*import\s+androidx\.compose\.runtime\.\w+\s+as\s""")
    private val ALIAS_HOOK = rx(
        """^\s*import\s+[\w.]*\.(remember\w*|mutable\w*StateOf|mutableState(?:List|Map|Set)Of|$EFFECT_WORDS""" +
            """|derivedStateOf|produceState|collectAsState\w*|$PRODUCER_CLASSES)\s+as\s""",
    )
    // ⚠️ 아래 모양 상수(접미사·키·생성자 이름·초기값)를 푸는 것은 옛 "숫자 상향"이다 — 사용자 결정 없이 넓히지 말 것.
    private val PORT_SUFFIX = rx("""(Store|Port|Client|Log|Mirror)$""")
    private val PORT_READ = rx("""(\w+)\.\w+\(\)""")
    private val CURRENT_READ = rx("""(\w+)\.current""")
    private val WIRING_CTOR = rx("""\b[A-Z]\w*(Controller|Holder|Coordinator|Applier|ViewModel)\s*[(<]""")
    private val WIRING_OBJECT = rx("""^\{\s*object\s*:""")
    private val SNAPSHOT_FLOW = rx("""\bsnapshotFlow\b""")
    private val STARTUP_WORKFLOW = rx("""\.collect\w*\s*[({]|\bsnapshotFlow\b|\b(?:while|for)\s*\(|\brepeat\s*\(|\.(?:launchIn|onEach)\b""")
    private val VAR_DECL = rx("""\bvar\s+(\w+)""")
    private val VAL_DECL = rx("""\bval\s+(\w+)""")
    private val VAL_START = rx("""^\s*val\b""")
    private val LEADING_WORD = rx("""^\s*(\w+)""")
    private val SINCE = rx("""^#\d+$""")
    private val WHITESPACE = rx("""\s+""")
    private val CONTROL_KEYWORD = rx("""\b(?:else|try|finally|do)$""")
    private val WHEN_KEYWORD = rx("""\bwhen$""")
    private val CONTROL_CALL = rx("""\b(if|for|while|catch|when)$""")
    private val TEST_CONFIGURE_BLOCK = rx("""tasks\.withType<Test>\(\)\.configureEach\s*\{""")
    private val GRADLE_INPUT = rx("""inputs\.file\(\s*rootDir\.resolve\("app-android/architecture-budgets\.json"\)\s*\)""")
    private val GRADLE_CHAIN_CALL = rx("""^\.\s*(\w+)\s*\(""")
    private val PORT_KEYS = setOf("context", "applicationContext")
    private val ENTRY_KEYS = setOf("kind", "why", "since")
    private val SHELL_KEYS = setOf("codeLineBudget", "sites")
    private val TOP_LEVEL_KEYS = setOf("_readme", "schema", "shells")
    private val CONTINUATION_STARTS = listOf("?:", "?.", ".", "&&", "||")
    private val CONTINUATION_ENDS = listOf("=", "?:", ".", "&&", "||")

    private fun isProducerName(name: String): Boolean = PRODUCER_NAME.matches(name)

    private fun word(name: String): Regex = rx("""(?<![\w$])${Regex.escape(name)}(?![\w$])""")

    /** 주석·문자열을 지우고(줄바꿈 유지) `import`/`package` 줄을 빈 줄로 덮는다. */
    fun prepare(source: String): String =
        PackageImportGraph.stripCommentsAndStrings(source).split('\n').joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) "" else line
        }

    /** `fun rememberX(`처럼 선언 이름 자리인가 — 선언은 훅 호출이 아니다. */
    private fun isDeclarationName(t: String, start: Int): Boolean =
        DECLARATION_NAME.containsMatchIn(t.substring(maxOf(0, start - 160), start))

    /**
     * 원문의 문자열 템플릿 `${…}`(중괄호 균형으로 끝까지) 안의 훅 단어 — 스캐너는 문자열을 지우므로 그 훅은
     * 안 보인다(L12). 균형이 안 맞으면 그 줄 끝까지만 본다.
     */
    private fun templateHooks(source: String): List<String> {
        val errors = mutableListOf<String>()
        var from = 0
        while (true) {
            val i = source.indexOf("\${", from)
            if (i < 0) break
            var depth = 0
            var end = -1
            var j = i + 1
            while (j < source.length && j < i + 4000) {
                when (source[j]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = j
                            break
                        }
                    }
                }
                j++
            }
            if (end < 0) end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
            TEMPLATE_HOOK.find(source.substring(i + 2, end))?.let { hit ->
                val line = source.substring(0, i).count { it == '\n' } + 1
                errors += "hook word '${hit.value}' inside a string template at line $line — the scanner erases strings, so this hook " +
                    "would be invisible: bind it to a named val outside the string"
            }
            from = i + 2
        }
        return errors
    }

    /** [source]의 훅 자리 전부(관용구 포함). [scannedFile]은 HOSTED 판정("다른 파일이 정의했나")에만 쓴다. */
    fun scan(source: String, index: RepoIndex, scannedFile: File? = null): Scan {
        val errors = templateHooks(source).toMutableList()
        val t = prepare(source)
        val lines = LineIndex(t)
        val decls = declarations(t)
        fun isIdiomName(name: String): Boolean = name in COMPOSE_IDIOMS && name !in index.rememberFunctions

        class Token(val start: Int, val end: Int, val name: String)
        val tokens = (HOOK.findAll(t) + HOOK_CLASS.findAll(t) + COLLECT.findAll(t) + HOOK_EXTENSION.findAll(t))
            .map { m -> val g = m.groups[1]!!; Token(g.range.first, g.range.last + 1, g.value) }
            .filterNot { isDeclarationName(t, it.start) }
            .sortedWith(compareBy<Token>({ it.start }, { it.end }, { it.name }))
            .toList()

        class RawSite(val start: Int, val end: Int, val hook: String, val args: Span?, val lambda: Span?, val absorbing: Boolean) {
            val folded = mutableListOf<String>()
        }
        val raws = mutableListOf<RawSite>()
        for (token in tokens) {
            val container = raws.firstOrNull { it.absorbing && it.start < token.start && token.start < it.end }
            if (container != null) {
                container.folded += token.name
                continue
            }
            val extent = extent(t, token.end)
            val absorbing = token.name in ABSORBING || isProducerName(token.name) ||
                (token.name.startsWith("remember") && !isIdiomName(token.name))
            raws += RawSite(token.start, extent.end, token.name, extent.args, extent.lambda, absorbing)
        }

        // 느슨한 훅 단어는 전부 훅 토큰(자리이거나 접힌 것)이어야 한다 — 아니면 파서가 못 읽은 훅이다.
        val tokenStarts = tokens.map { it.start }.toSet()
        for (m in LOOSE.findAll(t)) {
            val g = m.groups[1] ?: m.groups[2] ?: continue
            val p = g.range.first
            if (p in tokenStarts || isDeclarationName(t, p)) continue
            errors += "hook word '${g.value}' at line ${lines.lineOf(p)} is not read as a hook call (fully-qualified name, member call, " +
                ":: reference or plain identifier) — call the hook by its imported simple name; if it is not a Compose hook, rename the identifier"
        }

        val sites = raws.map { raw ->
            val owner = ownerOf(decls, raw.start)
            val statement = t.substring(statementStart(t, raw.start), raw.start)
            val bind = BIND.find(statement)
            val body = t.substring(raw.start, raw.end)
            val lambdaText = raw.lambda?.let { t.substring(it.start, it.end) }
            val construct = when {
                raw.hook in EFFECTS -> Construct.EFFECT
                isIdiomName(raw.hook) -> Construct.IDIOM
                raw.hook == "remember" && raw.args == null && raw.folded.isEmpty() && lambdaText != null &&
                    IDIOM_INSTANCE_LAMBDA.matches(lambdaText) -> Construct.IDIOM
                raw.hook == "produceState" || raw.folded.any { it in EFFECTS || it == "produceState" } ||
                    (raw.absorbing && HIDDEN_EFFECT_CALL.containsMatchIn(body)) -> Construct.HIDDEN_EFFECT
                raw.hook.startsWith("remember") && raw.hook != "remember" && raw.hook != "rememberSaveable" ->
                    if (index.isHosted(raw.hook, scannedFile)) Construct.HOSTED else Construct.UNKNOWN_REMEMBER
                raw.hook in STATE_HOOKS || isProducerName(raw.hook) || raw.folded.any { it in STATE_HOOKS || isProducerName(it) } ||
                    FLOW_CONTAINERS.containsMatchIn(body) || bind?.groupValues?.get(3) == "by" ||
                    index.snapshotStateClasses.any { callsClass(body, it) } || index.stateFactories.any { callsFunction(body, it) } ->
                    Construct.STATE
                raw.hook == "remember" && raw.lambda != null && instanceCall(t, raw.lambda) != null -> Construct.INSTANCE
                else -> Construct.MEMO
            }
            val name = when {
                bind != null -> bind.groupValues[2]
                construct.judged == Construct.EFFECT && raw.hook in EFFECTS -> "${raw.hook}(${firstArg(t, raw.args)})"
                else -> null
            }
            Site(
                line = lines.lineOf(raw.start),
                hook = raw.hook,
                folded = raw.folded.toList(),
                ownerKind = owner?.kind ?: OwnerKind.TOP_LEVEL,
                owner = owner?.name ?: "<top>",
                name = name,
                construct = construct,
                binding = bind?.groupValues?.get(3),
                statement = statement.trim(),
                start = raw.start,
                end = raw.end,
                args = raw.args,
                lambda = raw.lambda,
            )
        }
        return Scan(t, sites, errors, decls)
    }

    private fun callsClass(body: String, name: String): Boolean = rx("""(?<![\w$])${Regex.escape(name)}\s*[(<]""").containsMatchIn(body)

    private fun callsFunction(body: String, name: String): Boolean = rx("""(?<![\w.$])${Regex.escape(name)}\s*[(<]""").containsMatchIn(body)

    /** 줄 번호(1부터) — 위치 앞의 줄바꿈 수 + 1. */
    private class LineIndex(text: String) {
        private val newlines: IntArray = text.indices.filter { text[it] == '\n' }.toIntArray()

        fun lineOf(pos: Int): Int {
            var lo = 0
            var hi = newlines.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (newlines[mid] < pos) lo = mid + 1 else hi = mid
            }
            return lo + 1
        }
    }

    /** [i]의 여는 괄호에 맞는 닫는 괄호 **다음** 위치. 어긋나거나 안 닫히면 파일 끝(닫힌 쪽으로 실패). */
    private fun matchBracket(t: String, i: Int): Int {
        val stack = ArrayDeque<Char>()
        var j = i
        while (j < t.length) {
            when (val c = t[j]) {
                '(' -> stack.addLast(')')
                '{' -> stack.addLast('}')
                '[' -> stack.addLast(']')
                ')', '}', ']' -> {
                    if (stack.isEmpty() || stack.last() != c) return t.length
                    stack.removeLast()
                    if (stack.isEmpty()) return j + 1
                }
            }
            j++
        }
        return t.length
    }

    /** `<…>` 타입 인자 목록의 끝 다음 위치. `->`의 `>`는 무시하고, `{ } ; = 줄바꿈`이 먼저 오면 타입 인자가 아니다([i] 그대로). */
    private fun matchAngle(t: String, i: Int): Int {
        var depth = 0
        var j = i
        while (j < t.length) {
            val c = t[j]
            if (c == '<') {
                depth++
            } else if (c == '>' && t[j - 1] != '-') {
                depth--
                if (depth == 0) return j + 1
            } else if (c in "{};\n=") {
                return i
            }
            j++
        }
        return i
    }

    private class Extent(val end: Int, val args: Span?, val lambda: Span?)

    /** 이름 뒤의 `<…>`?, `(…)`?, 같은 줄에서 여는 `{…}`?. */
    private fun extent(t: String, nameEnd: Int): Extent {
        val n = t.length
        var j = nameEnd
        while (j < n && (t[j] == ' ' || t[j] == '\t')) j++
        if (j < n && t[j] == '<') {
            val k = matchAngle(t, j)
            if (k > j) j = k
        }
        while (j < n && (t[j] == ' ' || t[j] == '\t')) j++
        var args: Span? = null
        var lambda: Span? = null
        var end = nameEnd
        if (j < n && t[j] == '(') {
            val k = matchBracket(t, j)
            args = Span(j, k)
            end = k
            j = k
            while (j < n && (t[j] == ' ' || t[j] == '\t')) j++
        }
        if (j < n && t[j] == '{') {
            val k = matchBracket(t, j)
            lambda = Span(j, k)
            end = k
        }
        return Extent(end, args, lambda)
    }

    /** `remember { Ctor<…>(…) }`의 생성자 — 람다가 **정확히** 생성자 호출 하나(+`.also{}`/`.apply{}`)일 때만. */
    private class InstanceCall(val constructor: String, val args: Span)

    private fun instanceCall(t: String, lambda: Span): InstanceCall? {
        val end = lambda.end - 1
        var i = lambda.start + 1
        fun skipWhitespace() {
            while (i < end && t[i].isWhitespace()) i++
        }
        skipWhitespace()
        if (i >= end || !t[i].isUpperCase()) return null
        val nameStart = i
        while (i < end && (t[i].isLetterOrDigit() || t[i] == '_')) i++
        val name = t.substring(nameStart, i)
        skipWhitespace()
        if (i < end && t[i] == '<') {
            val k = matchAngle(t, i)
            if (k == i) return null
            i = k
            skipWhitespace()
        }
        if (i >= end || t[i] != '(') return null
        val close = matchBracket(t, i)
        if (close > end) return null
        val args = Span(i, close)
        i = close
        skipWhitespace()
        if (i < end && t[i] == '.') {
            val chain = ALSO_APPLY.find(t.substring(i, end)) ?: return null
            val open = i + chain.range.last
            val closeLambda = matchBracket(t, open)
            if (closeLambda > end) return null
            i = closeLambda
            skipWhitespace()
        }
        return if (i == end) InstanceCall(name, args) else null
    }

    /** 괄호 목록의 첫 인자(깊이 0의 첫 `,`까지), 공백은 하나로. */
    private fun firstArg(t: String, args: Span?): String {
        if (args == null) return ""
        val inner = t.substring(args.start + 1, maxOf(args.start + 1, args.end - 1))
        var depth = 0
        val out = StringBuilder()
        for (c in inner) {
            if (c in "([{") depth++ else if (c in ")]}") depth--
            if (c == ',' && depth == 0) break
            out.append(c)
        }
        return WHITESPACE.replace(out.toString(), " ").trim()
    }

    private fun rememberKeys(t: String, site: Site): List<String> {
        val args = site.args ?: return emptyList()
        val inner = t.substring(args.start + 1, maxOf(args.start + 1, args.end - 1))
        return WHITESPACE.replace(inner, " ").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 자리의 **바깥쪽** 생산자 — 자리 자체가 생산자이거나, `remember { P(…) }`/`rememberSaveable { P(…) }`의 람다가 정확히 그 호출 하나. */
    private class Producer(val name: String, val firstArg: String?, val text: String)

    private fun outermostProducer(t: String, site: Site): Producer? {
        if (isProducerName(site.hook)) {
            return Producer(site.hook, site.args?.let { firstArg(t, it) }, WHITESPACE.replace(t.substring(site.start, site.end), " "))
        }
        if (site.hook != "remember" && site.hook != "rememberSaveable") return null
        val lambda = site.lambda ?: return null
        val end = lambda.end - 1
        var i = lambda.start + 1
        while (i < end && t[i].isWhitespace()) i++
        val m = PRODUCER_AT.find(t.substring(i, end)) ?: return null
        val call = extent(t, i + m.range.last + 1)
        var j = call.end
        while (j < end && t[j].isWhitespace()) j++
        if (j != end) return null
        return Producer(m.groupValues[1], call.args?.let { firstArg(t, it) }, WHITESPACE.replace(t.substring(i, call.end), " "))
    }

    /** 괄호 목록 뒤의 반환형을 건너뛰어 블록 본문 `{`나 식 본문 `=`(또는 선언 끝) 위치. */
    private fun signatureEnd(t: String, from: Int): Int {
        val n = t.length
        var depth = 0
        var j = from
        while (j < n) {
            val c = t[j]
            if (c == '(' || c == '<') {
                depth++
            } else if ((c == ')' || c == '>') && !(c == '>' && t[j - 1] == '-')) {
                depth--
            } else if (depth <= 0 && (c == '{' || c == '=')) {
                break
            } else if (depth <= 0 && c == '\n') {
                val rest = t.substring(j + 1, minOf(n, j + 200)).trimStart()
                if (!(rest.startsWith(":") || rest.startsWith("=") || rest.startsWith("{") || rest.startsWith("where"))) break
            }
            j++
        }
        return j
    }

    /** 함수(블록·식 본문)와 object/class/interface/enum/companion 본문. enum 본문은 싱글턴이라 OBJECT로 본다. */
    private fun declarations(t: String): List<Decl> {
        val n = t.length
        val decls = mutableListOf<Decl>()
        for (m in FUN_DECL.findAll(t)) {
            val composable = COMPOSABLE_PREFIX.containsMatchIn(t.substring(maxOf(0, m.range.first - 200), m.range.first))
            val j = signatureEnd(t, matchBracket(t, m.range.last))
            if (j < n && t[j] == '{') {
                decls += Decl(OwnerKind.FUNCTION, m.groupValues[1], j, matchBracket(t, j), composable)
            } else if (j < n && t[j] == '=') {
                var k = j + 1
                while (k < n && t[k] != '\n') {
                    if (t[k] in "({[") {
                        k = matchBracket(t, k)
                        continue
                    }
                    k++
                }
                decls += Decl(OwnerKind.FUNCTION, m.groupValues[1], j, k, composable)
            }
        }
        val companions = mutableListOf<Decl>()
        for (m in TYPE_DECL.findAll(t)) {
            val isCompanion = m.groups[1] != null
            val keyword = m.groupValues[3]
            val kind = if (isCompanion || keyword == "object" || keyword.startsWith("enum")) OwnerKind.OBJECT else OwnerKind.CLASS
            val name = m.groups[2]?.value ?: m.groups[4]?.value ?: "Companion"
            var j = m.range.last + 1
            var depth = 0
            while (j < n) {
                val c = t[j]
                if (c == '(' || c == '<') {
                    depth++
                } else if ((c == ')' || c == '>') && !(c == '>' && t[j - 1] == '-')) {
                    depth--
                } else if (depth <= 0 && c == '{') {
                    break
                } else if (depth <= 0 && c == '\n') {
                    val line = t.substring(t.lastIndexOf('\n', j - 1) + 1, j).trimEnd()
                    val rest = t.substring(j + 1, minOf(n, j + 200)).trimStart()
                    val continues = line.endsWith(",") || line.endsWith(":") || line.endsWith("(") ||
                        rest.startsWith(":") || rest.startsWith(",") || rest.startsWith("{")
                    if (!continues) {
                        j = n
                        break
                    }
                }
                j++
            }
            if (j < n && t[j] == '{') {
                val decl = Decl(kind, name, j, matchBracket(t, j))
                if (isCompanion) companions += decl else decls += decl
            }
        }
        // companion은 `Enclosing.Companion`(이름이 있으면 `Enclosing.Name`)으로 부른다.
        for (companion in companions) {
            val enclosing = decls.filter { it.kind != OwnerKind.FUNCTION && it.start < companion.start && companion.start < it.end }
                .maxByOrNull { it.start }
            decls += companion.copy(name = enclosing?.let { "${it.name}.${companion.name}" } ?: companion.name)
        }
        return decls
    }

    /** 가장 안쪽 본문이 [pos]를 품는 선언. */
    private fun ownerOf(decls: List<Decl>, pos: Int): Decl? =
        decls.filter { it.start <= pos && pos < it.end }.maxByOrNull { it.start }

    /**
     * [pos]에서 문장 시작까지 거슬러 간다. 짝 없는 여는 괄호(그러면 자리는 인자다)·`;`·
     * 이어지지 않는 줄바꿈에서 멈춘다. 줄바꿈은 다음 줄이 `?: ?. . && ||`로 시작하거나 앞 줄이
     * `= ?: . && || by`로 끝나면 문장을 잇는다.
     */
    private fun statementStart(t: String, pos: Int): Int {
        var depth = 0
        var j = pos - 1
        while (j >= 0) {
            val c = t[j]
            if (c == ')' || c == '}' || c == ']') {
                depth++
            } else if (c == '(' || c == '{' || c == '[') {
                if (depth == 0) return j + 1
                depth--
            } else if (c == ';' && depth == 0) {
                return j + 1
            } else if (c == '\n' && depth == 0) {
                val current = t.substring(j + 1, pos).trimStart()
                val previousLine = t.substring(t.lastIndexOf('\n', j - 1) + 1, j).trimEnd()
                val continues = CONTINUATION_STARTS.any { current.startsWith(it) } ||
                    CONTINUATION_ENDS.any { previousLine.endsWith(it) } || BY_AT_END.containsMatchIn(previousLine)
                if (!continues) return j + 1
            }
            j--
        }
        return 0
    }

    private enum class Brace { CONTROL, WHEN, OPAQUE }

    /** [i]의 `{`가 무슨 블록인가 — if/for/while/catch/else/try/finally/do/when과 when 가지는 투명, 나머지(람다·본문)는 불투명. */
    private fun braceKind(t: String, i: Int, enclosing: Brace?): Brace {
        var j = i - 1
        while (j >= 0 && t[j].isWhitespace()) j--
        if (j < 0) return Brace.OPAQUE
        val before = t.substring(maxOf(0, j - 20), j + 1)
        if (t[j] == '>' && j > 0 && t[j - 1] == '-') return if (enclosing == Brace.WHEN) Brace.CONTROL else Brace.OPAQUE
        if (CONTROL_KEYWORD.containsMatchIn(before)) return Brace.CONTROL
        if (WHEN_KEYWORD.containsMatchIn(before)) return Brace.WHEN
        if (t[j] == ')') {
            var depth = 0
            var k = j
            while (k >= 0) {
                if (t[k] == ')') depth++ else if (t[k] == '(') {
                    depth--
                    if (depth == 0) break
                }
                k--
            }
            var w = k - 1
            while (w >= 0 && t[w].isWhitespace()) w--
            if (w < 0) return Brace.OPAQUE
            val keyword = CONTROL_CALL.find(t.substring(maxOf(0, w - 10), w + 1))?.groupValues?.get(1)
            return when (keyword) {
                null -> Brace.OPAQUE
                "when" -> Brace.WHEN
                else -> Brace.CONTROL
            }
        }
        return Brace.OPAQUE
    }

    /** [pos]가 [owner] 본문의 "문장 층"에 있는가 — 제어 블록은 투명하고 람다·지역 함수·object 본문은 불투명하다. */
    private fun isBodyLevel(t: String, owner: Decl, pos: Int): Boolean {
        val stack = ArrayDeque<Brace>()
        for (i in owner.start + 1 until pos) {
            when (t[i]) {
                '{' -> stack.addLast(braceKind(t, i, stack.lastOrNull()))
                '}' -> if (stack.isNotEmpty()) stack.removeLast()
            }
        }
        return stack.all { it != Brace.OPAQUE }
    }

    private class BodyHit(val owner: Decl, val match: MatchResult, val line: Int)

    /** 함수 본문의 "문장 층"에서 [pattern]에 맞는 것([composableOnly]면 `@Composable` 함수만). */
    private fun bodyLevelMatches(t: String, decls: List<Decl>, pattern: Regex, composableOnly: Boolean = false): List<BodyHit> {
        val lines = LineIndex(t)
        return pattern.findAll(t).mapNotNull { m ->
            val pos = (if (m.groups.size > 1) m.groups[1] else null)?.range?.first ?: m.range.first
            val owner = ownerOf(decls, pos)?.takeIf { it.kind == OwnerKind.FUNCTION && (!composableOnly || it.composable) }
            if (owner != null && t[owner.start] == '{' && isBodyLevel(t, owner, pos)) BodyHit(owner, m, lines.lineOf(pos)) else null
        }.toList()
    }

    /** 프로퍼티 문장의 끝 — 줄 끝, `;`, 또는 짝 없는 닫는 괄호(한 줄 `object X { val a = … }`의 `}`). 그 줄에서 여는 괄호 묶음은 따라간다. */
    private fun propertyStatementEnd(t: String, from: Int): Int {
        var k = from
        while (k < t.length && (t[k] == ' ' || t[k] == '\t')) k++
        if (k < t.length && t[k] == '\n') {
            k++
            while (k < t.length && t[k].isWhitespace()) k++
        }
        while (k < t.length && t[k] != '\n' && t[k] != ';') {
            when (t[k]) {
                '(', '{', '[' -> {
                    k = matchBracket(t, k)
                    continue
                }
                ')', '}', ']' -> return k
            }
            k++
        }
        return k
    }

    // ── 저장소 색인·전역 스캔 ──────────────────────────────────────────────────────────────

    private class ProductionSource(val file: File, val raw: String) {
        val text: String by lazy { prepare(raw) }
        val decls: List<Decl> by lazy { declarations(text) }
    }

    private val productionSources: List<ProductionSource> by lazy {
        RepoPaths.productionSourceRoots
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sortedBy { it.path }
            .map { ProductionSource(it, it.readContractSource()) }
    }

    /** 프로덕션 소스 전체의 색인. ⚠️ `rememberAppUpdateStatus`가 없으면 "색인이 아무것도 안 봤다"로 터진다(빈 스캔의 교훈). */
    val repoIndex: RepoIndex by lazy {
        val index = buildRepoIndex(productionSources.map { it.file to it.raw })
        check("rememberAppUpdateStatus" in index.rememberFunctions) {
            "ShellStateLedger 저장소 색인이 아무것도 안 봤다 — rememberAppUpdateStatus를 못 찾았다. " +
                "RepoPaths.productionSourceRoots가 낡았거나 그 함수가 옮겨졌다."
        }
        index
    }

    /**
     * [sources](파일, 원문)로 색인을 만든다 — 자기검증은 심은 소스로 부른다.
     *  - 스냅숏 상태 클래스: 클래스 본문 바로 아래 프로퍼티(한 줄 선언 포함)의 초기값·위임에 생산자(FQN 포함)가 있거나,
     *    주 생성자 괄호 안(`class C(val on: MutableState<Boolean> = mutableStateOf(false))`, 여러 줄 포함)에 생산자가 있다.
     *  - 상태 팩토리: `@Composable`도 `remember*`도 아닌 함수가 상태 타입을 돌려준다고 선언했거나, 식 본문에 생산자가
     *    있거나, 블록 본문이 생산자를 `return`한다.
     */
    fun buildRepoIndex(sources: List<Pair<File?, String>>): RepoIndex {
        val remembers = mutableMapOf<String, MutableSet<String>>()
        val snapshotClasses = mutableSetOf<String>()
        val factories = mutableSetOf<String>()
        for ((file, raw) in sources) {
            val stripped = PackageImportGraph.stripCommentsAndStrings(raw)
            for (m in REMEMBER_FUN.findAll(stripped)) {
                remembers.getOrPut(m.groupValues[1]) { mutableSetOf() } += (file?.canonicalPath ?: "<planted>")
            }
            val t = prepare(raw)
            val decls = declarations(t)
            for (m in PROP.findAll(t)) {
                val owner = ownerOf(decls, m.groups[1]!!.range.first)
                if (owner == null || owner.kind != OwnerKind.CLASS) continue
                if (GLOBAL_PRODUCER.containsMatchIn(t.substring(m.range.first, propertyStatementEnd(t, m.range.last + 1)))) {
                    snapshotClasses += owner.name
                }
            }
            for (m in PRIMARY_CONSTRUCTOR.findAll(t)) {
                if (GLOBAL_PRODUCER.containsMatchIn(t.substring(m.range.last, matchBracket(t, m.range.last)))) snapshotClasses += m.groupValues[1]
            }
            for (m in FUN_DECL.findAll(t)) {
                val name = m.groupValues[1]
                if (name.startsWith("remember")) continue
                if (COMPOSABLE_PREFIX.containsMatchIn(t.substring(maxOf(0, m.range.first - 200), m.range.first))) continue
                val paramsEnd = matchBracket(t, m.range.last)
                val j = signatureEnd(t, paramsEnd)
                val returnType = t.substring(minOf(paramsEnd, j), j)
                val declaresState = returnType.contains(':') && STATE_RETURN_TYPE.containsMatchIn(returnType)
                val producesState = when {
                    j >= t.length -> false
                    t[j] == '=' -> GLOBAL_PRODUCER.containsMatchIn(t.substring(j + 1, propertyStatementEnd(t, j + 1)))
                    t[j] == '{' -> RETURNS_PRODUCER.containsMatchIn(t.substring(j, matchBracket(t, j)))
                    else -> false
                }
                if (declaresState || producesState) factories += name
            }
        }
        return RepoIndex(remembers, snapshotClasses, factories)
    }

    /**
     * `object`/`companion object`/`enum class` 본문 바로 아래와 최상위의 프로퍼티(한 줄 선언 포함) 중 컴포즈 상태를
     * 든 것 — 스냅숏 생산자(FQN 포함)·`MutableStateFlow`·상태 타입 선언, 또는 스냅숏 상태 클래스([snapshotClasses])의
     * 인스턴스. `MutableSharedFlow`·`Channel`(이벤트 흐름)과 평범한 값은 뺀다. 이름은 `Object.field`,
     * `Enclosing.Companion.field`, 최상위는 `<파일 이름>Kt.field`. [snapshotClasses]가 없으면 [source] 자체로 색인한다.
     */
    fun processGlobalStateIn(source: String, fileStem: String, snapshotClasses: Set<String>? = null): List<String> {
        val t = prepare(source)
        val classes = snapshotClasses ?: buildRepoIndex(listOf(null to source)).snapshotStateClasses
        return processGlobalState(t, declarations(t), fileStem, classes)
    }

    private fun processGlobalState(t: String, decls: List<Decl>, fileStem: String, snapshotClasses: Set<String>): List<String> {
        val found = mutableListOf<String>()
        val classCall = snapshotClasses.takeIf { it.isNotEmpty() }
            ?.let { names -> rx("""(?<![\w$])(?:${names.joinToString("|") { Regex.escape(it) }})\s*[(<]""") }
        for (m in PROP.findAll(t)) {
            val owner = ownerOf(decls, m.groups[1]!!.range.first)
            if (owner != null && owner.kind != OwnerKind.OBJECT) continue
            val statement = t.substring(m.range.first, propertyStatementEnd(t, m.range.last + 1))
            val holdsState = GLOBAL_PRODUCER.containsMatchIn(statement) || GLOBAL_STATE_WORD.containsMatchIn(statement) ||
                GLOBAL_STATE_TYPE.containsMatchIn(statement) || GLOBAL_SNAPSHOT_TYPE.containsMatchIn(statement) ||
                classCall?.containsMatchIn(statement) == true
            if (!holdsState) continue
            found += if (owner == null) "${fileStem}Kt.${m.groupValues[2]}" else "${owner.name}.${m.groupValues[2]}"
        }
        return found
    }

    /** L14 — 프로덕션 소스 전체의 프로세스 전역 컴포즈 상태. */
    fun processGlobalState(): Set<String> {
        val classes = repoIndex.snapshotStateClasses
        return productionSources.flatMap { processGlobalState(it.text, it.decls, it.file.nameWithoutExtension, classes) }.toSet()
    }

    /** 컴포즈 런타임·훅 단어를 별칭으로 들여오는 import 줄. */
    fun aliasedHookImportsIn(source: String): List<String> =
        source.lines().withIndex()
            .filter { (_, line) -> ALIAS_RUNTIME.containsMatchIn(line) || ALIAS_HOOK.containsMatchIn(line) }
            .map { (i, line) -> "line ${i + 1}: ${line.trim()}" }

    /** L12b — 프로덕션 소스 전체. */
    fun aliasedHookImports(): List<String> =
        productionSources.flatMap { source ->
            aliasedHookImportsIn(source.raw).map { "${source.file.relativeTo(RepoPaths.root).path} $it" }
        }

    /** 한 셸 소스의 함수 본문 "문장 층"에서 remember 없이 만드는 저장소·클라이언트(L13). */
    fun unrememberedPlatformInstancesIn(source: String): List<String> {
        val t = prepare(source)
        return bodyLevelMatches(t, declarations(t), UNREMEMBERED_INSTANCE).map { "${it.owner.name}.${it.match.groupValues[2]}" }
    }

    /** L13 — 지키는 셸 전부의 합집합. */
    fun unrememberedPlatformInstances(): Set<String> =
        GUARDED_SHELLS.values.flatMap { unrememberedPlatformInstancesIn(it.file().readContractSource()) }.toSet()

    // ── 판정 ──────────────────────────────────────────────────────────────────────────────

    /** 원장 파일. org.json은 중복 키에서 던진다(자기검증이 못박는다). */
    fun budgetFile(): JSONObject = JSONObject(RepoPaths.architectureBudgets.readContractSource())

    /**
     * 키 목록. ⚠️ `keySet()`을 쓰지 않는다 — 단위 테스트 **컴파일** 클래스패스에서는 android.jar의 org.json이
     * 먼저 보이고 거기엔 `keySet()`이 없다. 두 구현 모두에 있는 `keys()`만 쓴다.
     */
    private fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

    /** 셸 하나를 감사한다 — 위반 전부를 한꺼번에 돌려준다(비면 통과). */
    fun auditShell(shellId: String): List<String> {
        val shell = GUARDED_SHELLS[shellId] ?: return listOf("[$shellId] L11 not a guarded shell: ${GUARDED_SHELLS.keys}")
        val jsonShell = budgetFile().optJSONObject("shells")?.optJSONObject(shellId)
            ?: return listOf("[$shellId] L11 shell is missing from architecture-budgets.json")
        val file = shell.file()
        val policy = ShellPolicy(shell.role, shell.lineBudgetCeiling, shell.effectCount, SHELL_DEBT[shellId].orEmpty())
        return verdict(
            source = file.readContractSource(),
            jsonShell = jsonShell,
            policy = policy,
            index = repoIndex,
            scannedFile = file,
            codeLineCount = codeLinesOf(file.readContractSourceLines()).size,
        ).map { "[$shellId] $it" }
    }

    /** L3(최상위) + L11 + 모든 셸의 L10 상한. */
    fun budgetFileOffenders(json: JSONObject): List<String> {
        val off = mutableListOf<String>()
        val keys = json.keyList().toSortedSet()
        if (keys != TOP_LEVEL_KEYS.toSortedSet()) off += "L3 top-level keys must be exactly $TOP_LEVEL_KEYS, found $keys"
        if (json.opt("schema") != 1) off += "L3 schema must be 1, found ${json.opt("schema")}"
        val shells = json.optJSONObject("shells") ?: return off + "L11 'shells' is missing or not an object"
        val jsonShells = shells.keyList().toSortedSet()
        if (jsonShells != GUARDED_SHELLS.keys.toSortedSet()) {
            off += "L11 JSON shells $jsonShells != ShellStateLedger.GUARDED_SHELLS ${GUARDED_SHELLS.keys.sorted()}"
        }
        for ((id, shell) in GUARDED_SHELLS) {
            val budget = shells.optJSONObject(id)?.opt("codeLineBudget")
            when {
                budget !is Int -> off += "L10 [$id] codeLineBudget must be an int, found $budget"
                budget > shell.lineBudgetCeiling ->
                    off += "L10 [$id] codeLineBudget $budget > ceiling ${shell.lineBudgetCeiling} — lowering a budget is a JSON edit; " +
                        "raising a ceiling is the old 숫자 상향 and needs a user decision"
            }
        }
        return off
    }

    /** 빌드 스크립트의 주석만 지운다(문자열은 남긴다 — 입력 경로가 문자열이다). 블록 주석은 중첩까지. */
    private fun gradleCode(src: String): String {
        val out = StringBuilder(src.length)
        val n = src.length
        var i = 0
        while (i < n) {
            when {
                src.startsWith("/*", i) -> {
                    var depth = 1
                    i += 2
                    while (i < n && depth > 0) {
                        when {
                            src.startsWith("/*", i) -> { depth++; i += 2 }
                            src.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> { if (src[i] == '\n') out.append('\n'); i++ }
                        }
                    }
                }
                src.startsWith("//", i) -> while (i < n && src[i] != '\n') i++
                src.startsWith("\"\"\"", i) -> {
                    val close = src.indexOf("\"\"\"", i + 3)
                    val end = if (close < 0) n else close + 3
                    out.append(src, i, end)
                    i = end
                }
                src[i] == '"' -> {
                    var j = i + 1
                    while (j < n && src[j] != '"' && src[j] != '\n') {
                        if (src[j] == '\\') j++
                        j++
                    }
                    val end = minOf(n, j + 1)
                    out.append(src, i, end)
                    i = end
                }
                else -> {
                    out.append(src[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * L17 — 원장이 Gradle 테스트 입력으로 선언돼 있는가. 주석(`//`·중첩 `/* */`)을 걷어낸 뒤,
     * `tasks.withType<Test>().configureEach { … }` 블록 **바로 아래 문장**으로 `inputs.file(…architecture-budgets.json…)`가
     * 있고 그 사슬에 `.withPropertyName("architectureBudgets")`가 있고 `.optional()`이 없어야 한다.
     * (경로 문자열이 지워지므로 stripCommentsAndStrings는 쓰지 않는다.)
     */
    fun gradleInputOffenders(buildScript: String): List<String> {
        val code = gradleCode(buildScript)
        val blocks = TEST_CONFIGURE_BLOCK.findAll(code).map { m -> Span(m.range.last, matchBracket(code, m.range.last)) }.toList()
        if (blocks.isEmpty()) {
            return listOf("L17 app-android/build.gradle.kts has no tasks.withType<Test>().configureEach { … } block to declare the ledger input in")
        }
        class Declaration(val chain: List<Pair<String, String>>)
        val declarations = mutableListOf<Declaration>()
        for (block in blocks) {
            for (m in GRADLE_INPUT.findAll(code)) {
                if (m.range.first <= block.start || m.range.first >= block.end) continue
                var depth = 0
                for (i in block.start + 1 until m.range.first) {
                    when (code[i]) {
                        '{', '(', '[' -> depth++
                        '}', ')', ']' -> depth--
                    }
                }
                val lineStart = maxOf(code.lastIndexOf('\n', m.range.first - 1), block.start) + 1
                if (depth != 0 || code.substring(lineStart, m.range.first).isNotBlank()) continue
                val chain = mutableListOf<Pair<String, String>>()
                var k = m.range.last + 1
                while (true) {
                    var s = k
                    while (s < code.length && code[s].isWhitespace()) s++
                    val call = GRADLE_CHAIN_CALL.find(code.substring(s, minOf(code.length, s + 200))) ?: break
                    val open = s + call.range.last
                    val close = matchBracket(code, open)
                    chain += call.groupValues[1] to WHITESPACE.replace(code.substring(open + 1, maxOf(open + 1, close - 1)), "")
                    k = close
                }
                declarations += Declaration(chain)
            }
        }
        if (declarations.isEmpty()) {
            return listOf(
                "L17 app-android/build.gradle.kts must declare inputs.file(rootDir.resolve(\"app-android/architecture-budgets.json\")) " +
                    "as a statement directly inside tasks.withType<Test>().configureEach { … } (not commented out, not moved to another task, " +
                    "not wrapped in a condition) — otherwise a ledger-only edit is skipped as UP-TO-DATE (#103)",
            )
        }
        val off = mutableListOf<String>()
        if (declarations.none { d -> d.chain.any { it == ("withPropertyName" to "\"architectureBudgets\"") } }) {
            off += "L17 the ledger input must carry .withPropertyName(\"architectureBudgets\")"
        }
        if (declarations.any { d -> d.chain.any { it.first == "optional" } }) {
            off += "L17 the ledger input must not be .optional() — a missing ledger has to fail Gradle input validation"
        }
        return off
    }

    /**
     * 셸 하나의 판정(L1–L10·L12·L15–L18). 입력을 전부 인자로 받는 순수 함수라, 자기검증이 심은
     * 소스·JSON·부채로 같은 판정기를 시험한다.
     */
    fun verdict(
        source: String,
        jsonShell: JSONObject,
        policy: ShellPolicy,
        index: RepoIndex,
        scannedFile: File? = null,
        codeLineCount: Int = codeLinesOf(source.lines()).size,
    ): List<String> {
        val off = mutableListOf<String>()
        val scan = scan(source, index, scannedFile)
        val t = scan.text
        off += scan.parseErrors.map { "L12 $it" }

        // L10 줄
        val budget = jsonShell.opt("codeLineBudget")
        if (budget !is Int) {
            off += "L10 codeLineBudget must be an int, found $budget"
        } else {
            if (budget > policy.lineBudgetCeiling) {
                off += "L10 codeLineBudget $budget > ShellStateLedger ceiling ${policy.lineBudgetCeiling} — " +
                    "lowering a budget is a JSON edit; raising a ceiling is the old 숫자 상향 and needs a user decision"
            }
            if (codeLineCount > budget) {
                off += "L10 $codeLineCount code lines > codeLineBudget $budget (imports/comments/blanks excluded) — " +
                    "hoist wiring into a presenter; do not regrow the shell"
            }
        }
        for (key in jsonShell.keyList().sorted()) if (key !in SHELL_KEYS) off += "L3 unknown shell key '$key'"
        val entries = jsonShell.optJSONObject("sites") ?: JSONObject().also { off += "L3 'sites' is missing or not an object" }

        // 자리 → 키
        val sites = LinkedHashMap<String, Site>()
        for (site in scan.sites) {
            if (site.construct == Construct.IDIOM) continue
            val nested = site.folded.filterNot { isProducerName(it) }
            if (nested.isNotEmpty()) {
                off += "L12 hook(s) $nested nested inside ${site.hook} at line ${site.line} — every hook site is listed on its own; " +
                    "a remembered lambda (movableContentOf, a composable lambda) or an effect key cannot hide another hook or effect: hoist it"
            }
            if (site.ownerKind != OwnerKind.FUNCTION) {
                off += "L6 ${site.hook} at line ${site.line} is owned by ${site.ownerKind} ${site.owner} — " +
                    "a shell's hook sites live in its composable functions; process-lifetime state is not a way around the ledger"
                continue
            }
            if (site.construct == Construct.HIDDEN_EFFECT) {
                val how = when {
                    site.hook == "produceState" -> "produceState runs a coroutine that writes its own state"
                    site.folded.any { it in EFFECTS || it == "produceState" } -> "it hides ${site.folded.filter { it in EFFECTS || it == "produceState" }} in its lambda"
                    else -> "a coroutine (launch/async/collect/launchIn/stateIn/shareIn/produceIn) runs inside it"
                }
                off += "L7 hidden effect: ${site.hook} at line ${site.line} — $how. A coroutine outside a counted effect is never green; " +
                    "move the work to the role that owns it (a controller/holder), or into a LaunchedEffect (a new effect needs a user decision)"
            }
            val siteKey = site.key
            if (siteKey != null && siteKey in policy.pinned) {
                off += "L18 $siteKey became a hook site — it must stay a plain read every recomposition (pitfall 67)"
            }
            if (siteKey == null) {
                off += "L1 anonymous ${site.hook} at line ${site.line} — bind it to a named val so the ledger can list it"
                continue
            }
            var key: String = siteKey
            var i = 2
            while (key in sites) key = "$siteKey#${i++}"
            sites[key] = site
            if (isProducerName(site.hook)) {
                off += "L16 unremembered state $key at line ${site.line} — it is recreated every recomposition; wrap it in remember { }"
            }
        }

        // 조립 층의 코루틴 — 콜백·효과 밖에서 launch하면 재구성마다 도는, 셈하지 않은 효과다.
        for (hit in bodyLevelMatches(t, scan.decls, LAUNCH_CALL, composableOnly = true)) {
            off += "L7 hidden effect: a coroutine is started at composition level in ${hit.owner.name} at line ${hit.line} " +
                "(outside any callback or effect) — it reruns on every recomposition and is an uncounted effect; " +
                "move it into a callback, or into a LaunchedEffect (a new effect needs a user decision)"
        }

        val kindOf: Map<String, Kind?> = entries.keyList().associateWith { k ->
            val name = entries.optJSONObject(k)?.opt("kind")
            Kind.entries.firstOrNull { it.name == name }
        }

        fun localsOf(owner: String, vararg kinds: Kind): Set<String> =
            kindOf.filter { (k, kind) -> kind in kinds && k.substringBefore('.') == owner }.keys
                .map { it.substringAfter('.') }.toSet()
        val owedKeys = policy.debt.filterValues { it == DebtState.OWED }.keys
        val paidKeys = policy.debt.filterValues { it == DebtState.PAID }.keys
        fun boundName(key: String): String = key.substringAfter('.').substringBefore('#')
        val owedStateNames = (owedKeys + kindOf.filterValues { it != null && !it.allowed }.keys)
            .map { boundName(it) }.filter { '(' !in it }.toSet()
        val siteNames = sites.values.mapNotNull { it.name }.toSet()
        val orphanedOwed = owedKeys.filter { it !in sites }.sorted()

        // 부채 이름 + 그것을 한 겹씩 옮겨 담은 지역 별칭(문장 층의 val/var, 자리는 빼고).
        val debtNamesByOwner = mutableMapOf<String, Set<String>>()
        fun debtNamesFor(owner: String): Set<String> = debtNamesByOwner.getOrPut(owner) {
            val tainted = owedStateNames.toMutableSet()
            val candidates = scan.decls.filter { it.kind == OwnerKind.FUNCTION && it.name == owner && t[it.start] == '{' }
                .flatMap { decl ->
                    LOCAL_BINDING.findAll(t, decl.start).takeWhile { it.range.first < decl.end }
                        .filter { isBodyLevel(t, decl, it.range.first) && it.groupValues[1] !in siteNames }
                        .map { it.groupValues[1] to t.substring(it.range.last + 1, propertyStatementEnd(t, it.range.last + 1)) }
                        .toList()
                }
            var changed = tainted.isNotEmpty()
            while (changed) {
                changed = false
                val reach = rx("""(?<![\w$])(?:${tainted.joinToString("|") { Regex.escape(it) }})(?![\w$])""")
                for ((name, initializer) in candidates) {
                    if (name !in tainted && reach.containsMatchIn(initializer)) {
                        tainted += name
                        changed = true
                    }
                }
            }
            tainted
        }

        // 소유 함수의 var(직접·.value·++/--/op=)와 그것을 쓰는 지역 함수 — STARTUP_EFFECT가 쓰면 안 된다.
        fun ownerWrites(ownerDecl: Decl?, text: String): List<String> {
            if (ownerDecl == null) return emptyList()
            val ownerBody = t.substring(ownerDecl.start, ownerDecl.end)
            val vars = VAR_DECL.findAll(ownerBody).map { it.groupValues[1] }.toSet()
            val vals = VAL_DECL.findAll(ownerBody).map { it.groupValues[1] }.toSet()
            // `x = …`가 괄호 안이면 이름 붙인 인자다(코틀린에서 대입은 식이 아니다) — `++`/`--`/`op=`/`.value =`는 늘 쓰기다.
            val stepRx = vars.associateWith { v ->
                val e = Regex.escape(v)
                rx("""(?<![.\w$])$e\s*(?:[-+*/%]=|\+\+|--)|(?:\+\+|--)\s*$e(?![\w$])""")
            }
            val assignRx = vars.associateWith { v -> rx("""(?<![.\w$])${Regex.escape(v)}\s*=(?!=)""") }
            val valueRx = vals.associateWith { v -> rx("""(?<![.\w$])${Regex.escape(v)}\.value\s*(?:[-+*/%]?=(?!=)|\+\+|--)""") }
            fun direct(s: String): List<String> =
                vars.filter { v -> stepRx.getValue(v).containsMatchIn(s) || assignRx.getValue(v).findAll(s).any { !insideCallArguments(s, it.range.first) } }
                    .sorted() + valueRx.filterValues { it.containsMatchIn(s) }.keys.sorted().map { "$it.value" }
            val locals = scan.decls.filter {
                it.kind == OwnerKind.FUNCTION && it !== ownerDecl && it.start > ownerDecl.start && it.end <= ownerDecl.end
            }
            val writers = linkedMapOf<String, List<String>>()
            for (f in locals) direct(t.substring(f.start, f.end)).takeIf { it.isNotEmpty() }?.let { writers[f.name] = it }
            fun calls(s: String, name: String): Boolean = rx("""(?<![.\w$])${Regex.escape(name)}\s*\(|::${Regex.escape(name)}(?![\w$])""").containsMatchIn(s)
            var changed = true
            while (changed) {
                changed = false
                for (f in locals) {
                    if (f.name in writers) continue
                    val callee = writers.keys.firstOrNull { calls(t.substring(f.start, f.end), it) } ?: continue
                    writers[f.name] = writers.getValue(callee)
                    changed = true
                }
            }
            return direct(text) + writers.keys.sorted().filter { calls(text, it) }.map { "$it() → ${writers.getValue(it)}" }
        }

        // L7 — 종류마다의 모양 검사와 부채 접촉 검사. L1의 붙여 넣을 줄도 이것으로 후보 종류를 고른다.
        // ⚠️ 여기 모양(초기값·접미사·키 목록·생성자 이름)을 푸는 것은 옛 "숫자 상향"이다 — 사용자 결정 없이 넓히지 말 것.
        fun shapeProblems(key: String, site: Site, kind: Kind): List<String> {
            val problems = mutableListOf<String>()
            val construct = site.construct.judged
            val body = t.substring(site.start, site.end)
            val lambdaText = site.lambda?.let { t.substring(it.start, it.end) }.orEmpty()
            val receiver = if (site.hook == "collectAsState" || site.hook == "collectAsStateWithLifecycle") {
                BIND.find(site.statement)?.let { site.statement.substring(it.range.last + 1) } ?: site.statement
            } else {
                ""
            }
            // 부채 접촉 — 허용 종류는 OWED 상태 부채(와 그 지역 별칭)를 범위 안에서 읽으면 안 된다.
            // WIRING은 컨트롤러 인스턴스와 배선 컨텍스트 object만 빠진다(콜백이 부채를 쓰는 것이 그 일이다).
            val wiringExempt = kind == Kind.WIRING && (site.construct == Construct.INSTANCE || WIRING_OBJECT.containsMatchIn(lambdaText))
            if (kind.allowed && !wiringExempt) {
                val reach = "$receiver $body"
                val touched = debtNamesFor(site.owner).filter { word(it).containsMatchIn(reach) }.sorted()
                if (touched.isNotEmpty()) {
                    problems += "L7 $key ($kind) reads OWED debt or a local alias of it $touched — an allowed kind cannot lean on debt"
                }
            }
            when (kind) {
                Kind.PLATFORM_PORT -> if (construct == Construct.INSTANCE) {
                    val call = site.lambda?.let { instanceCall(t, it) }
                    if (call == null || !PORT_SUFFIX.containsMatchIn(call.constructor)) {
                        problems += "L7 $key PLATFORM_PORT needs ${kind.shape}; found constructor ${call?.constructor}"
                    }
                    if (call != null && WIRING_CTOR.containsMatchIn(t.substring(call.args.start, call.args.end))) {
                        problems += "L7 $key PLATFORM_PORT builds a controller/holder/coordinator/applier/ViewModel in its arguments — that is wiring, not a port"
                    }
                    val keys = rememberKeys(t, site)
                    if (!PORT_KEYS.containsAll(keys)) problems += "L7 $key PLATFORM_PORT remember keys must be ⊆ $PORT_KEYS, found $keys"
                }
                Kind.VISIBILITY_GATE, Kind.GESTURE_COUNTER, Kind.NAVIGATION, Kind.BOOTSTRAP -> if (construct == Construct.STATE) {
                    val producer = outermostProducer(t, site)
                    val arg = producer?.firstArg
                    val ok = producer != null && when (kind) {
                        Kind.VISIBILITY_GATE -> producer.name == "mutableStateOf" && (
                            arg == "false" || arg == "null" ||
                                PORT_READ.matchEntire(arg.orEmpty())?.groupValues?.get(1)?.let { it in localsOf(site.owner, Kind.PLATFORM_PORT) } == true
                            )
                        Kind.GESTURE_COUNTER -> (producer.name == "mutableStateOf" || producer.name == "mutableIntStateOf") && arg == "0"
                        Kind.NAVIGATION -> producer.name == "mutableStateOf" && arg != null &&
                            (arg == "null" || "initialDestination(" in arg || "ScreenDestination." in arg)
                        else -> producer.name == "mutableStateOf" && arg == "null"
                    }
                    if (!ok) {
                        val found = producer?.text ?: "no single outermost producer (the remember lambda must be exactly one mutableStateOf(…) call)"
                        problems += "L7 $key $kind needs ${kind.shape}; found $found"
                    }
                }
                else -> Unit
            }
            if (kind == Kind.BOOTSTRAP && construct == Construct.EFFECT) {
                val bootstrapStates = localsOf(site.owner, Kind.BOOTSTRAP).filter { '(' !in it }
                if (bootstrapStates.none { rx("""(?<![.\w])${Regex.escape(it)}\s*=(?!=)""").containsMatchIn(body) }) {
                    problems += "L7 $key BOOTSTRAP effect writes no BOOTSTRAP state (${kind.shape})"
                }
            }
            if (kind == Kind.WIRING && SNAPSHOT_FLOW.containsMatchIn(body)) {
                problems += "L7 $key WIRING derives a flow from Compose state (snapshotFlow) — that is a state mirror, not wiring"
            }
            if (kind == Kind.HOLDER_MIRROR) {
                val wiring = localsOf(site.owner, Kind.WIRING)
                if (construct == Construct.STATE) {
                    if (receiver.isNotEmpty()) {
                        val root = LEADING_WORD.find(receiver)?.groupValues?.get(1)
                        if (root == null || root !in wiring + localsOf(site.owner, Kind.PLATFORM_PORT)) {
                            problems += "L7 $key HOLDER_MIRROR needs ${kind.shape}; the collected receiver $root is not a WIRING/PLATFORM_PORT site"
                        }
                    } else {
                        val producer = outermostProducer(t, site)
                        val holder = producer?.takeIf { it.name == "mutableStateOf" }?.firstArg?.let { CURRENT_READ.matchEntire(it)?.groupValues?.get(1) }
                        if (holder == null || holder !in wiring || !entries.has("${site.owner}.LaunchedEffect($holder)")) {
                            problems += "L7 $key HOLDER_MIRROR needs ${kind.shape}; found ${producer?.text ?: "no single outermost mutableStateOf"}"
                        }
                    }
                } else {
                    val holder = firstArg(t, site.args)
                    if (holder !in wiring || !rx("""\b${Regex.escape(holder)}\.state\.collect\b""").containsMatchIn(body)) {
                        problems += "L7 $key HOLDER_MIRROR effect needs ${kind.shape} (first key $holder)"
                    }
                }
            }
            if (kind == Kind.STARTUP_EFFECT) {
                val firstKey = firstArg(t, site.args)
                if (firstKey != "Unit" && firstKey !in localsOf(site.owner, Kind.PLATFORM_PORT, Kind.WIRING)) {
                    problems += "L7 $key STARTUP_EFFECT first key must be Unit or a PLATFORM_PORT/WIRING site (found $firstKey)"
                }
                val lambda = site.lambda?.let { t.substring(it.start, it.end) }.orEmpty()
                val written = ownerWrites(ownerOf(scan.decls, site.start), lambda)
                if (written.isNotEmpty()) problems += "L7 $key STARTUP_EFFECT writes $written — that is a workflow, not a startup read"
                if (STARTUP_WORKFLOW.containsMatchIn(lambda)) {
                    problems += "L7 $key STARTUP_EFFECT collects, observes or loops (collect/snapshotFlow/while/for/repeat/launchIn/onEach) — " +
                        "that is a workflow effect, not a one-shot startup read"
                }
            }
            if (kind == Kind.STARTUP_READ && WIRING_CTOR.containsMatchIn(body)) {
                problems += "L7 $key STARTUP_READ constructs a controller/holder/coordinator/applier/ViewModel (${kind.shape})"
            }
            if (kind == Kind.HOSTED_ROLE_STATE && (site.binding != "=" || !VAL_START.containsMatchIn(site.statement))) {
                problems += "L7 $key HOSTED_ROLE_STATE needs ${kind.shape}"
            }
            if (kind == Kind.DERIVED_VIEW) {
                val shaped = site.hook == "remember" && site.folded == listOf("derivedStateOf") &&
                    outermostProducer(t, site)?.name == "derivedStateOf" && site.binding == "by" && VAL_START.containsMatchIn(site.statement)
                if (!shaped) problems += "L7 $key DERIVED_VIEW needs ${kind.shape}"
            }
            return problems
        }

        // L1·L2 — 양방향 일치.
        val stale = entries.keyList().filter { it !in sites }.sorted()
        val effects = sites.values.count { it.construct.judged == Construct.EFFECT }
        fun constructOfDebt(key: String): Construct = kindOf[key]?.constructs?.singleOrNull()
            ?: if ('(' in boundName(key)) Construct.EFFECT else Construct.STATE
        for ((key, site) in sites) {
            if (entries.has(key)) continue
            val where = "L1 unlisted hook site $key [${site.construct}] at line ${site.line}"
            val construct = site.construct.judged
            val owedTwin = orphanedOwed.firstOrNull { boundName(it) == boundName(key) }
            val owedShadow = owedKeys.firstOrNull { it != key && it !in orphanedOwed && boundName(it) == boundName(key) }
            val paidTwin = paidKeys.firstOrNull { boundName(it) == boundName(key) }
            val carriedFrom = orphanedOwed.filter { constructOfDebt(it) == construct }
            off += when {
                policy.debt[key] == DebtState.OWED -> {
                    val debtKinds = Kind.entries.filter { !it.allowed && construct in it.constructs }
                    "$where — it is OWED debt in ShellStateLedger.SHELL_DEBT: restore its entry under a debt kind " +
                        "(${debtKinds.joinToString()}); an allowed kind stays red (L8)"
                }
                paidTwin != null -> "$where — its name was PAID as $paidTwin in ShellStateLedger.SHELL_DEBT: paid debt cannot come back " +
                    "in this file under any owner or kind — remove it"
                owedTwin != null -> "$where — its name is OWED debt $owedTwin under another owner: a move carries the debt. Rename $owedTwin " +
                    "to $key in ShellStateLedger.SHELL_DEBT and move its JSON entry to the new key with the same debt kind, in the same commit " +
                    "(a 1:1 carry is allowed; it is not new debt, and it is not paid)"
                owedShadow != null -> "$where — its name shadows OWED debt $owedShadow in another function: rename it (a same-named " +
                    "state elsewhere in this file reads as the debt moving)"
                site.construct == Construct.HIDDEN_EFFECT -> "$where — a hidden effect (L7) has no JSON line that makes it green"
                // 목록에 없는 효과는 수와 상관없이 붙여 넣을 줄을 받지 않는다 — 수가 그대로면 맞바꾸기다(L9가 못 본다).
                construct == Construct.EFFECT -> {
                    val gone = (stale + orphanedOwed).filter { '(' in boundName(it) }.distinct().sorted()
                    val swap = when {
                        gone.isNotEmpty() -> " — effect $gone disappeared in the same change: swapping one effect for another also " +
                            "needs that user decision"
                        effects <= policy.effectCount -> " — the effect count did not grow, so another effect disappeared in the same " +
                            "change: swapping one effect for another also needs that user decision"
                        else -> ""
                    }
                    "$where — a new effect: first ask whether the role that uses it should own it (a controller/holder). Effects are " +
                        "frozen per shell (L9) — adding or replacing an effect is the old 숫자 상향 and needs a user decision, so no " +
                        "JSON line is offered for it$swap"
                }
                carriedFrom.isNotEmpty() -> "$where — OWED debt $carriedFrom has no site any more: if this is that state renamed or moved, " +
                    "carry the debt 1:1 (rename the key in ShellStateLedger.SHELL_DEBT and in the JSON, keep its debt kind) — giving it an " +
                    "allowed kind launders debt, and a debt is PAID only when the state has left this file. Only if it is genuinely new: " +
                    "first ask whether the role that uses it should own it"
                else -> {
                    val fits = Kind.entries.filter { it.allowed && construct in it.constructs && policy.role in it.roles }
                    val candidate = fits.firstOrNull { shapeProblems(key, site, it).isEmpty() }
                    unlistedMessage(where, key, site, policy.role, fits, candidate, stale, kindOf)
                }
            }
        }
        for (key in stale) {
            val kind = kindOf[key]
            off += if (key in owedKeys || (kind != null && !kind.allowed)) {
                "L2 stale debt entry $key — no such hook site any more. If the state left this file (moved to the role that owns it), " +
                    "delete the entry and flip $key to PAID in ShellStateLedger.SHELL_DEBT in the same commit. If it was renamed or moved " +
                    "within this file, carry the debt: rename the key 1:1 in the JSON and in SHELL_DEBT, keeping its debt kind"
            } else {
                "L2 stale entry $key — no such hook site any more: delete the entry (the ledger shrinks with the code)" +
                    if ('#' in key) " — #n keys are positional: a site added above an existing one renumbers the rest" else ""
            }
        }

        // L9 효과 수
        if (effects > policy.effectCount) {
            off += "L9 $effects effect sites > frozen effectCount ${policy.effectCount} — a new effect is the old 숫자 상향 and needs a " +
                "user decision; do not raise the count yourself: move the work into the controller/holder that owns it"
        } else if (effects < policy.effectCount) {
            off += "L9 $effects effect sites < frozen effectCount ${policy.effectCount} — an effect was removed: lower this shell's " +
                "effectCount in ShellStateLedger.GUARDED_SHELLS to $effects (it only shrinks)"
        }

        val mirrorRoots = sortedMapOf<String, MutableList<String>>()
        val paidByName = paidKeys.associateBy { boundName(it) }
        for (key in entries.keyList().sorted()) {
            val entry = entries.optJSONObject(key)
            if (entry == null) {
                off += "L3 entry $key must be an object { kind, why, since }"
                continue
            }
            for (field in entry.keyList().sorted()) if (field !in ENTRY_KEYS) off += "L3 unknown entry key $key.$field"
            val kind = kindOf[key]
            if (kind == null) {
                off += "L3 unknown kind $key=${entry.opt("kind")} — kinds are ShellStateLedger.Kind (closed); the JSON cannot add one"
                continue
            }
            val why = entry.opt("why")
            if (why !is String || why.isBlank()) off += "L3 blank why for $key — say why this shell owns it"
            val since = entry.opt("since")
            if (since !is String || !SINCE.matches(since)) off += "L3 bad since for $key — the backlog card of this change, like #45"
            when (policy.debt[key]) {
                DebtState.PAID -> off += "L8 $key is PAID debt — paid debt cannot come back under any kind"
                DebtState.OWED -> Unit
                null -> {
                    val paidTwin = paidByName[boundName(key)]
                    if (paidTwin != null) {
                        off += "L8 $key reuses the name of PAID debt $paidTwin — paid debt cannot come back in this file under any owner or kind"
                    } else if (!kind.allowed) {
                        off += if (orphanedOwed.isNotEmpty()) {
                            "L8 new debt $key ($kind) — if it is OWED $orphanedOwed renamed or moved, rename that key 1:1 in " +
                                "ShellStateLedger.SHELL_DEBT (a carry: same state, same debt kind, same commit). Anything else — a new debt " +
                                "or a debt swap — is the old 숫자 상향 and needs a user decision"
                        } else {
                            "L8 new debt $key ($kind) — debt only shrinks; a new debt (or a debt swap) is the old 숫자 상향 and needs a user decision"
                        }
                    }
                }
            }
            if (policy.role !in kind.roles) {
                off += "L5 kind $kind is not allowed in role ${policy.role}: $key — role limits are closed (ShellStateLedger.Kind.roles); " +
                    "do not widen them: move the state to the role that owns it"
            }
            val site = sites[key] ?: continue
            if (site.construct.judged !in kind.constructs) {
                off += "L4 $key is ${site.construct}, but kind $kind takes ${kind.constructs.sorted()} (${kind.shape})"
            }
            off += shapeProblems(key, site, kind)
            if (kind == Kind.HOLDER_MIRROR && site.construct.judged == Construct.STATE) {
                val root = if (site.hook == "collectAsState" || site.hook == "collectAsStateWithLifecycle") {
                    val receiver = BIND.find(site.statement)?.let { site.statement.substring(it.range.last + 1) } ?: site.statement
                    LEADING_WORD.find(receiver)?.groupValues?.get(1)
                } else {
                    outermostProducer(t, site)?.firstArg?.let { CURRENT_READ.matchEntire(it)?.groupValues?.get(1) }
                }
                if (root != null) mirrorRoots.getOrPut("${site.owner}.$root") { mutableListOf() } += key
            }
        }
        for ((root, keys) in mirrorRoots) if (keys.size > 1) off += "L7 more than one HOLDER_MIRROR state for receiver $root: $keys"

        // L8 OWED는 JSON에 부채 종류로 그대로 있어야 한다.
        for ((key, state) in policy.debt.toSortedMap()) {
            if (state != DebtState.OWED) continue
            val kind = kindOf[key]
            if (kind == null || kind.allowed) {
                off += "L8 OWED debt $key is missing from the JSON or relabelled to an allowed kind. It flips to PAID in " +
                    "ShellStateLedger.SHELL_DEBT only when the state has left this file (moved to the role that owns it); a rename or " +
                    "a move within this file carries the debt (rename the key 1:1 in SHELL_DEBT and the JSON, keep the debt kind)"
            }
        }

        // L15 위임, L16 함수 본문 문장 층의 날것 흐름 컨테이너
        val lines = LineIndex(t)
        for (m in DELEGATE.findAll(t)) {
            val call = m.groupValues[4]
            if (call != "HolderBackedState" && call != "lazy" && LOOSE.matchEntire(call) == null) {
                off += "L15 unrecognised state delegate ${m.groupValues[2]} by $call at line ${lines.lineOf(m.groups[1]!!.range.first)} " +
                    "— a delegate that hides state must be a listed hook"
            }
        }
        for (hit in bodyLevelMatches(t, scan.decls, UNREMEMBERED_FLOW)) {
            off += "L16 unremembered flow container ${hit.owner.name}.${hit.match.groupValues[2]} at line ${hit.line} — " +
                "it is recreated every recomposition"
        }
        return off
    }

    /** [pos]를 품는 가장 안쪽 괄호가 `(`/`[`인가 — 그러면 `x = …`는 대입이 아니라 이름 붙인 인자다. */
    private fun insideCallArguments(s: String, pos: Int): Boolean {
        var depth = 0
        var j = pos - 1
        while (j >= 0) {
            when (s[j]) {
                ')', ']', '}' -> depth++
                '(', '[', '{' -> {
                    if (depth == 0) return s[j] != '{'
                    depth--
                }
            }
            j--
        }
        return false
    }

    private fun unlistedMessage(
        where: String,
        key: String,
        site: Site,
        role: Role,
        fits: List<Kind>,
        candidate: Kind?,
        stale: List<String>,
        kindOf: Map<String, Kind?>,
    ): String {
        val construct = site.construct.judged
        val ask = "first ask whether the role that uses this state should own it (push it down to that composable, holder or " +
            "controller) — this shell is an assembler"
        val positional = if ('#' in key) " (#n keys are positional: a site added above an existing one renumbers the rest)" else ""
        if (candidate == null) {
            val shapes = if (fits.isEmpty()) {
                "no allowed kind takes $construct in role $role"
            } else {
                "no allowed kind's shape fits: " + fits.joinToString("; ") { "$it needs ${it.shape}" }
            }
            return "$where — $ask. $shapes. Either it belongs to another role or file, or it is new debt, which is frozen and needs a " +
                "user decision — there is no JSON line for it$positional"
        }
        val paste = "\"$key\": { \"kind\": \"${candidate.name}\", \"since\": \"#NNN\", \"why\": \"\" }"
        val renamedFrom = stale.filter { entry ->
            entry.substringBefore('.') == site.owner && kindOf[entry]?.let { it.allowed && construct in it.constructs } == true
        }
        val hint = if (renamedFrom.isEmpty()) "" else " — renamed from $renamedFrom? then rename that JSON key instead"
        return "$where — $ask. Only if this shell really owns it, add to architecture-budgets.json: $paste " +
            "(since = this change's backlog card, never a copy of #46; why = why the shell owns it; the placeholders stay red). " +
            "Allowed kinds for $construct in $role: ${fits.joinToString()}$hint$positional"
    }
}

/**
 * import·주석·빈 줄을 걷어낸 **코드 줄만** 남긴다(2026-09-05). #46에서 `LayeringContractTest`의 private 함수를
 * 동작 그대로 옮겼다 — 셸 원장의 `codeLineBudget`과 루트 패키지 매처가 함께 쓴다.
 *
 * ⚠️ **이것이 없으면 예산이 결합과 설명을 복잡도로 오해한다.** `GoCoachApp.kt`는 885줄 중
 * 126줄이 import였다 — import는 복잡도가 아니라 **결합의 증상**이고, 그것을 예산으로 막으면
 * 정작 줄여야 할 조립 코드는 그대로 둔 채 import만 줄이는 왜곡이 생긴다.
 */
internal fun codeLinesOf(lines: List<String>): List<String> {
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
