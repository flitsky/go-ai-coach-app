package com.worksoc.goaicoach.architecture

/**
 * 계약 테스트가 **문자열로 들고 있는 심볼 주소(FQN)의 유일한 보관소**(refactor backlog #68).
 *
 * ## 왜 한 곳으로 모으는가
 * [LayeringContractTest]의 가드는 `importOf(`[ENGINE_CORE_API]`)`처럼 **FQN 문자열**로 위반을
 * 찾는다. 그런데 그 FQN이 **실재하는지 스스로 확인하지 않는다** —
 * 심볼이 다른 패키지로 이사하면 그 규칙은 어떤 파일과도 매치하지 않고, **초록인 채 아무것도
 * 검사하지 않는다.** 아무것도 빨개지지 않으므로 아무도 모른다.
 *
 * 가설이 아니라 두 번 일어난 일이다.
 *  - **P0(`0c33d32c`)** — 260816에 `application/`·`match/`가 :shared로 이사했는데 스캔 경로가
 *    따라오지 않아 가드 넷이 **0개 파일을 검사하며 무조건 통과**했다. 260923에 되살렸다.
 *  - **`#24`(260924)** — `EngineModels.kt`가 `shared.enginecontract`로 옮겨가며 같은 일이 날 뻔했다.
 *    이동 스레드가 문자열 다섯을 같은 커밋에서 고쳐 막았고 검수자가 사보타주로 확인했다.
 *
 * ⚠️ **두 번째는 사람이 막았다. 그게 결함이다** — 방어가 주의력에 달려 있었다. P3에 남은 이동
 * (`#25` 어댑터 축출 · `#26` 조립 코드 축출 · `#27` ui 분할)이 같은 위험을 또 만든다.
 *
 * 그래서 FQN은 여기 한 곳에만 둔다. [ContractSymbolContractTest]가
 *  - 여기 적힌 주소가 **소스에 실재하는지** 검사하고([SourceSymbolIndex]),
 *  - `architecture/` 아래 **다른 파일에 FQN 리터럴이 새로 생기지 않았는지** 감시한다.
 *
 * ## 픽스처는 원칙적으로 여기 없다
 * [LayeringContractTest]류의 자기검증 픽스처가 탐지력을 검증하려고 **일부러 위반 소스를 만드는
 * 경우가 있다.** 그 안의 FQN이 실재할 필요가 없는 진짜 가짜 주소라면 등록 대상이 아니고,
 * [ContractSymbolContractTest]가 함수 이름으로 그 본문을 통째로 제외한다(제외 목록은
 * [FIXTURE_FUNCTIONS], 그 함수가 실재하는지까지 확인한다). ⚠️ **하지만 실재하는 FQN을 그저
 * 되풀이해 적어 둔 것뿐이라면 이 예외를 쓰지 말고 상수를 문자열 템플릿으로 참조하라** —
 * `detectionCatchesViolationsThatPlainImportStringWouldMiss`가 정확히 그 오분류였다
 * (refactor backlog #69). [ENGINE_CORE_API]는 가짜가 아니라 진짜 주소였고, 리터럴로 여섯 번
 * 되풀이해 적혀 있어 `#24`의 패키지 이동 때 사람이 손으로 여섯 곳을 고쳐야 했다. 지금은
 * 문자열 템플릿으로 그 상수를 참조해 [FIXTURE_FUNCTIONS]에서 빠졌다.
 */
internal object ContractSymbols {

    // ── 실존해야 하는 타입 ────────────────────────────────────────────────
    const val ENGINE_CORE_API = "com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi"
    const val ENGINE_ADAPTER = "com.worksoc.goaicoach.shared.enginecontract.EngineAdapter"

    /**
     * 루트 패키지(조립 전용)의 **앵커**(refactor backlog #25). 가드는 루트 패키지 이름을 리터럴로
     * 들지 않고 이 상수에서 `substringBeforeLast('.')`로 파생한다 — 루트가 이사하거나 이 타입이
     * 사라지면 매처가 허공을 보는 대신 [ContractSymbolContractTest]가 빨개진다.
     * ⚠️ `MainActivity`는 매니페스트·런처 바로가기가 컴포넌트 이름으로 부르므로 옮기지 않는다.
     */
    const val MAIN_ACTIVITY = "com.worksoc.goaicoach.MainActivity"

    // ── 실존해야 하는 패키지(접두사) ──────────────────────────────────────
    /** 엔진 런타임 구현체가 사는 곳. 접미 `.` 없이 쓴다 — 마지막 조각이 소문자라 타입이 아니다. */
    const val ENGINE_ANDROID_RUNTIME_PACKAGE = "com.worksoc.goaicoach.engine.android"
    const val UI_PACKAGE = "com.worksoc.goaicoach.ui."
    const val PERSISTENCE_PACKAGE = "com.worksoc.goaicoach.persistence."
    const val ENGINE_PACKAGE = "com.worksoc.goaicoach.engine."
    const val APPLICATION_PACKAGE = "com.worksoc.goaicoach.application."

    /**
     * 4계층 SDK 어댑터(Billing·UMP·AdMob·Firebase Auth·Credential Manager·Vibrator 등)가 사는 곳
     * (refactor backlog #25). 이 접두사를 공유하는 다른 패키지가 없어서 — [ENGINE_PACKAGE]와 달리 —
     * 실존 검사가 "이 패키지가 비었다/이사했다"를 실제로 잡는다.
     */
    const val PLATFORM_PACKAGE = "com.worksoc.goaicoach.platform."

    /** UI 상태·표시 모델. platform 어댑터가 이것을 알면 4계층이 1계층을 거슬러 오른다(#25). */
    const val PRESENTATION_PACKAGE = "com.worksoc.goaicoach.presentation."

    /**
     * 원격 분석 게이트웨이가 사는 곳(refactor backlog #69). [LayeringContractTest]의 자기검증
     * 픽스처가 "이 패키지와 무관한 wildcard import"의 음성 대조군으로 쓴다 — 예전엔 이 패키지
     * 이름도 리터럴로 박혀 있었다.
     */
    const val MIDDLEWARE_PACKAGE = "com.worksoc.goaicoach.middleware."

    // ── 되살아나면 안 되는 최상위 함수 ────────────────────────────────────
    // `application/engine`에 있던 `EngineCoreApi` 확장 헬퍼들. EngineSessionClient의 멤버로
    // 흡수됐고, score 러너가 그 시절 헬퍼를 다시 import 하는 것을 가드가 막는다.
    // 즉 **지금 없는 것이 정상**이라 MUST_EXIST로 검사하면 안 된다.
    const val LOCAL_SYNC_AND_ESTIMATE_GRAPH_SCORE =
        "com.worksoc.goaicoach.application.engine.syncAndEstimateGraphScore"
    const val LOCAL_CONFIGURE_SYNC_AND_ESTIMATE_GRAPH_SCORE =
        "com.worksoc.goaicoach.application.engine.configureSyncAndEstimateGraphScore"
    const val LOCAL_ESTIMATE_SCORE_FOR_STATE =
        "com.worksoc.goaicoach.application.engine.estimateScoreForState"

    // ── 색인 판정기의 양성 표본 ──────────────────────────────────────────
    // 위의 셋은 전부 ABSENT_BY_DESIGN이라, 스위트는 `topLevelFunctionExists`가 **false를 내는 것만**
    // 본다. 판정기가 false만 뱉도록 고장나면 그 셋의 "되살아남" 감시가 초록인 채 죽는다(backlog #76).
    // 그래서 **같은 패키지·같은 모양**(suspend 수식어 + `EngineCoreApi` 확장)의 실재 함수를 표본으로
    // 둔다 — 판정기가 이것을 "있다"고 못 하면 그 셋이 되살아나도 못 알아챈다.
    const val LIVE_SYNC_TO_GAME_STATE =
        "com.worksoc.goaicoach.application.engine.syncToGameState"

    /**
     * 루트(조립) 패키지의 실재하는 **소문자** 최상위 함수 표본(refactor backlog #79). 옛 루트
     * 매처는 대문자로 시작하는 이름만 정규식으로 잡아, 이런 소문자 top-level 함수를 inline FQN으로
     * 부르면 놓쳤다. [LayeringContractTest]의 자기검증이 이 주소로 회귀를 막는다.
     */
    const val ROOT_LOWERCASE_TOP_LEVEL_FUNCTION_SAMPLE =
        "com.worksoc.goaicoach.wipeToFreshInstall"

    // ── 패키지 사이클 래칫의 기준선(refactor backlog #33) ─────────────────
    // [PackageCycleRatchetTest]가 `shared/src/commonMain`의 패키지 import 그래프에서 구한 SCC·상호
    // 참조 쌍을 **아래 두 목록과 정확히 같을 때만** 초록으로 둔다.
    // ⭐ **지금은 사이클이 없다 — 두 목록이 비어 있고, SCC나 상호 참조 쌍이 하나라도 생기면 빨갛다.**
    // `#32`(사이클 절단)의 마지막 걸음 ⑩이 SCC를 0으로 만들었다. 새 사이클을 여기 적어 초록을
    // 만들지 마라 — 기준선을 늘리는 것이 아니라 의존 방향을 고치는 것이 답이다.
    // 측정 기준은 커밋 7c928f55의 설계 스레드 실측(scc.py)과 같은 규칙이다([PackageImportGraph]).
    // ⚠️ 여기 적힌 패키지는 [GUARDED]에도 자동으로 올라가 실존 검사를 받는다.

    /**
     * 크기 2 이상인 SCC(강한 연결 요소)의 기준선. **비어 있다** — SCC가 하나라도 생기면 실패한다.
     */
    val CYCLE_BASELINE_SCCS: List<Set<String>> = emptyList()

    /**
     * 서로 import 하는 패키지 쌍(사이클의 씨앗)의 기준선. **비어 있다** — 두 패키지가 서로를
     * import 하는 순간 실패한다. 쌍 안의 순서는 무관하다.
     */
    val CYCLE_BASELINE_MUTUAL_PAIRS: List<Pair<String, String>> = emptyList()

    /** 가드가 쓰는 `forbiddenImports` 표기(`import <fqn>`)로 감싼다. */
    fun importOf(fqn: String): String = "import $fqn"

    /**
     * 가짜 FQN을 일부러 쓰는 자기검증 픽스처의 이름. [ContractSymbolContractTest]가 이 함수들의
     * **본문을 통째로 제외**하고 FQN 리터럴을 센다. 이름이 바뀌면 제외가 조용히 넓어지므로, 그
     * 테스트는 여기 적힌 함수가 소스에 실재하는지도 함께 못박는다.
     *
     * ⚠️ refactor backlog #69 — `detectionCatchesViolationsThatPlainImportStringWouldMiss`가 여기
     * 있었다. 그 픽스처는 실은 **가짜 FQN이 아니라 실재하는 [ENGINE_CORE_API]를 리터럴로 여섯 번
     * 되풀이해 적어 둔 것**이었다 — `#24`가 그 패키지를 옮겼을 때 이 여섯 자리를 전부 손으로
     * 고쳐야 했다. 이제 그 픽스처는 리터럴 대신 [ENGINE_CORE_API]를 문자열 템플릿으로 참조하므로
     * (등록부가 이사를 자동으로 따라간다) 더는 예외가 필요 없어 목록이 비었다. **다음에 정말
     * 가짜 FQN이 필요한 픽스처가 생기면 그때 여기 채운다** — 목록이 비어 있다고 이 메커니즘
     * 자체를 지우면 안 된다.
     */
    val FIXTURE_FUNCTIONS: List<String> = emptyList()

    /** 가드가 실제로 들고 있는 주소 전부와, 각 주소에 기대하는 바. */
    val GUARDED: List<GuardedSymbol> = listOf(
        GuardedSymbol(
            ENGINE_CORE_API,
            SymbolKind.TYPE,
            SymbolExpectation.MUST_EXIST,
            "ui/presentation·match·벤치마크 가드가 이 타입의 직접 참조를 금지한다",
        ),
        GuardedSymbol(
            ENGINE_ADAPTER,
            SymbolKind.TYPE,
            SymbolExpectation.MUST_EXIST,
            "ui/presentation·application/match 가드가 호환 별칭 참조를 금지한다",
        ),
        GuardedSymbol(
            MAIN_ACTIVITY,
            SymbolKind.TYPE,
            SymbolExpectation.MUST_EXIST,
            "루트(조립) 패키지 이름의 앵커 — platform 어댑터의 루트 import 금지 매처가 여기서 파생한다(#25)",
        ),
        GuardedSymbol(
            ENGINE_ANDROID_RUNTIME_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "ui/presentation·application/match가 엔진 런타임 구현체를 직접 쓰는 것을 금지한다",
        ),
        GuardedSymbol(
            UI_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "포트(auth/premium/device)·게이트웨이 계약·shared 정책 모델의 플랫폼 격리",
        ),
        GuardedSymbol(
            PERSISTENCE_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "포트(auth/premium/device)·게이트웨이 계약·shared 정책 모델의 플랫폼 격리",
        ),
        GuardedSymbol(
            ENGINE_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "포트(auth/premium/device)·게이트웨이 계약·shared 정책 모델의 플랫폼 격리",
        ),
        GuardedSymbol(
            PLATFORM_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "platform 어댑터가 Compose·ui·presentation·엔진 런타임을 모르게 한다(#25); " +
                "게이트웨이 계약·shared 정책 모델이 어댑터를 거슬러 참조하지 못하게 한다",
        ),
        GuardedSymbol(
            PRESENTATION_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "platform 어댑터가 presentation을 모르게 한다(#25)",
        ),
        GuardedSymbol(
            MIDDLEWARE_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "자기검증 픽스처의 무관 패키지 음성 대조군(#69)",
        ),
        GuardedSymbol(
            APPLICATION_PACKAGE,
            SymbolKind.PACKAGE,
            SymbolExpectation.MUST_EXIST,
            "게이트웨이 계약·shared 정책 모델이 application 계층을 거슬러 올라가지 못하게 한다",
        ),
        GuardedSymbol(
            LOCAL_SYNC_AND_ESTIMATE_GRAPH_SCORE,
            SymbolKind.TOP_LEVEL_FUNCTION,
            SymbolExpectation.ABSENT_BY_DESIGN,
            "score 러너는 EngineSessionClient 멤버를 쓴다 — 옛 최상위 확장 헬퍼는 되살리지 않는다",
        ),
        GuardedSymbol(
            LOCAL_CONFIGURE_SYNC_AND_ESTIMATE_GRAPH_SCORE,
            SymbolKind.TOP_LEVEL_FUNCTION,
            SymbolExpectation.ABSENT_BY_DESIGN,
            "score 러너는 EngineSessionClient 멤버를 쓴다 — 옛 최상위 확장 헬퍼는 되살리지 않는다",
        ),
        GuardedSymbol(
            LOCAL_ESTIMATE_SCORE_FOR_STATE,
            SymbolKind.TOP_LEVEL_FUNCTION,
            SymbolExpectation.ABSENT_BY_DESIGN,
            "score 러너는 EngineSessionClient 멤버를 쓴다 — 옛 최상위 확장 헬퍼는 되살리지 않는다",
        ),
        GuardedSymbol(
            LIVE_SYNC_TO_GAME_STATE,
            SymbolKind.TOP_LEVEL_FUNCTION,
            SymbolExpectation.MUST_EXIST,
            "색인 판정기의 양성 표본 — ABSENT_BY_DESIGN 셋의 되살아남 감시가 살아 있음을 보인다(#76)",
        ),
        GuardedSymbol(
            ROOT_LOWERCASE_TOP_LEVEL_FUNCTION_SAMPLE,
            SymbolKind.TOP_LEVEL_FUNCTION,
            SymbolExpectation.MUST_EXIST,
            "G1 루트 매처의 소문자 top-level 함수 탐지 회귀 방지 표본(#79)",
        ),
    ) + (CYCLE_BASELINE_SCCS.flatten() + CYCLE_BASELINE_MUTUAL_PAIRS.flatMap { it.toList() })
        .distinct()
        .map { packageName ->
            GuardedSymbol(
                packageName,
                SymbolKind.PACKAGE,
                SymbolExpectation.MUST_EXIST,
                "패키지 사이클 래칫 기준선의 구성원(#33) — 사라졌다면 기준선을 줄여야 한다",
            )
        }
}

/** 가드가 들고 있는 주소 하나. */
internal data class GuardedSymbol(
    val fqn: String,
    val kind: SymbolKind,
    val expectation: SymbolExpectation,
    val why: String,
)

internal enum class SymbolKind {
    /** 클래스/인터페이스/오브젝트/타입별칭. 마지막 조각이 대문자로 시작한다. */
    TYPE,

    /** 패키지(또는 그 접두사). 접미 `.` 이 붙어 있을 수 있다. */
    PACKAGE,

    /** 패키지 바로 아래에 선언된 함수(확장 함수 포함). */
    TOP_LEVEL_FUNCTION,
}

internal enum class SymbolExpectation {
    /** 이 주소는 **실재해야 한다.** 없으면 가드가 죽은 주소를 들고 초록이 된 것이다. */
    MUST_EXIST,

    /**
     * 이 주소는 **없는 것이 정상이다** — 가드의 목적이 "되살리지 마라"이기 때문이다.
     * 대신 그 주소의 **패키지**는 실재해야 하고(주소가 통째로 허공을 가리키면 안 된다),
     * 심볼이 되살아나면 여기 분류를 고쳐야 한다.
     */
    ABSENT_BY_DESIGN,
}
