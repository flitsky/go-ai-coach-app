package com.worksoc.goaicoach.architecture

/**
 * 계약 테스트가 **문자열로 들고 있는 심볼 주소(FQN)의 유일한 보관소**(refactor backlog #68).
 *
 * ## 왜 한 곳으로 모으는가
 * [LayeringContractTest]의 가드는 `"import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi"`
 * 같은 **FQN 문자열**로 위반을 찾는다. 그런데 그 FQN이 **실재하는지 스스로 확인하지 않는다** —
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
 * ## 픽스처는 여기 없다
 * [LayeringContractTest.detectionCatchesViolationsThatPlainImportStringWouldMiss]는 탐지력을
 * 검증하려고 **일부러 위반 소스를 만든다.** 그 안의 FQN은 실재하지 않아도 정상이므로 등록 대상이
 * 아니고, 대신 [ContractSymbolContractTest]가 함수 이름으로 그 본문을 통째로 제외한다
 * (제외 목록은 [FIXTURE_FUNCTIONS], 그 함수가 실재하는지까지 확인한다).
 */
internal object ContractSymbols {

    // ── 실존해야 하는 타입 ────────────────────────────────────────────────
    const val ENGINE_CORE_API = "com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi"
    const val ENGINE_ADAPTER = "com.worksoc.goaicoach.shared.enginecontract.EngineAdapter"

    // ── 실존해야 하는 패키지(접두사) ──────────────────────────────────────
    /** 엔진 런타임 구현체가 사는 곳. 접미 `.` 없이 쓴다 — 마지막 조각이 소문자라 타입이 아니다. */
    const val ENGINE_ANDROID_RUNTIME_PACKAGE = "com.worksoc.goaicoach.engine.android"
    const val UI_PACKAGE = "com.worksoc.goaicoach.ui."
    const val PERSISTENCE_PACKAGE = "com.worksoc.goaicoach.persistence."
    const val ENGINE_PACKAGE = "com.worksoc.goaicoach.engine."
    const val APPLICATION_PACKAGE = "com.worksoc.goaicoach.application."

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

    /** 가드가 쓰는 `forbiddenImports` 표기(`import <fqn>`)로 감싼다. */
    fun importOf(fqn: String): String = "import $fqn"

    /**
     * 가짜 FQN을 일부러 쓰는 자기검증 픽스처. [ContractSymbolContractTest]가 이 함수들의 **본문을
     * 통째로 제외**하고 FQN 리터럴을 센다. 이름이 바뀌면 제외가 조용히 넓어지므로, 그 테스트는
     * 여기 적힌 함수가 소스에 실재하는지도 함께 못박는다.
     */
    val FIXTURE_FUNCTIONS: List<String> = listOf(
        "detectionCatchesViolationsThatPlainImportStringWouldMiss",
    )

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
    )
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
