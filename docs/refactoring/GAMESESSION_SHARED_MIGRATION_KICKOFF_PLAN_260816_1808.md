# `GameSessionStateHolder` → `:shared` 본 이전 — 착수 계획서

작성일: 2026-08-16 18:08 (계획 수립) / **갱신: 2026-08-16 웨이브 1·2·3·4 실행 완료 후**

**성격**: `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` "고도화 로드맵" 5번 항목 (2) "본 이전"의 실행 순서를 정하는 착수 계획서다(로드맵 문서 자체가 "착수 순서는 별도 착수 계획서에서 정한다"고 명시한 그 문서). 원래는 `docs/refactoring/REFACTORING_BACKLOG_260816_1744.md` 작업 우선순위 1번의 "계획부터 정리해서 보여달라"는 요청으로 코드 이동 없이 작성됐으나, 같은 날 이어진 세션에서 **웨이브 1 실행까지 완료**했다 — 이 문서는 이제 계획서 겸 실행 기록이다.

**전제 확인**: 스파이크(`application/safety/EngineTurnWatchdog.kt`, 260816)는 이미 완료돼 `shared/src/commonMain/.../application/safety/`에 있다. 이 문서는 나머지 **124개 프로덕션 파일 + 50개 기존 단위테스트 파일**(20개 서브패키지)을 대상으로 한다.

---

## 0. 웨이브 1 실행 결과 요약 (260816)

**계획 대비 가장 큰 차이**: 아래 3절의 최초 계획은 "웨이브 1 = 19개 파일"이었다. 실제로는 파일 단위 컴파일을 시도하자마자 의존 그래프 조사 자체의 결함이 드러났고, 그 결함을 고치고 나니 웨이브 1의 진짜 원자적 단위는 **56개 파일**(11개가 아니라 **26개 파일 순환 클러스터**)이었다. 사용자에게 이 사실을 알리고 확장 여부를 확인받은 뒤 56개 전체로 진행했다. 상세 경위는 2절 끝부분 "260816 실행 중 발견" 참고.

**실제 결과**:
- 프로덕션 파일 **56개**(계획된 19개 + 추가로 필요했던 37개) 전부 `shared/src/commonMain/.../application/`로 이동, `:shared`/`:app-android` 양쪽 컴파일 + `make test` 전체 그린 확인 완료.
- `GameSessionStateHolder.kt` 자체는 아직 이동 안 함(이번 웨이브 1 범위 밖 — 원래 계획의 웨이브 9였고, 교정된 순서에서도 뒤쪽 웨이브에 있다. 5절 참고).
- 기존 단위테스트 11개 중 7개는 계획대로 이동, **1개는 원래 "배치 미확정"이었으나 실제로는 웨이브 1 범위 안이라 이동, 4개는 웨이브 1 범위를 넘어서는 걸 뒤늦게 발견해 원위치로 되돌림**(아래 표).
- `internal` 선언 위젠은 이름 기반 사전 감사가 아니라 **컴파일러가 실제로 지목한 것만** 고치는 방식으로 진행 — 총 4~5라운드에 걸쳐 수렴, 6절에 정리.
- `LayeringContractTest.kt`에서 예상 못 한 실패 3건 발견(하드코딩된 파일 경로가 이동한 파일을 못 찾음) — 8절에서 처리 방식과 재사용 가능한 헬퍼 설명.
- `app-android/ui/FinalScoreJudgementPresentationExtensions.kt`에서 Kotlin의 **모듈 간 스마트캐스트 제약**으로 인한 실제 컴파일 에러 1건 발견 및 수정 — 9절.

**최종 완료 기준 충족**: `:shared`/`:app-android` 양쪽 컴파일 그린, `make test`(`:shared:check :engine-android:testDebugUnitTest :app-android:assembleDebug :app-android:testDebugUnitTest`) 전체 그린. 스모크 테스트(`NewGameBoardTapSmokeTest`/`AppLaunchSmokeTest`) 실기 재확인은 **아직 안 함** — `GameSessionStateHolder.kt` 자체나 세션 배선 컨트롤러들이 아직 이동 전이라 이번 웨이브에서는 필수가 아니라고 판단했다(원래 문서도 "웨이브 9·10 완료 직후"로 못박아 뒀음). 다음 웨이브들(특히 GameSessionStateHolder.kt를 포함하는 웨이브) 완료 시점에 실행할 것.

커밋 `30d6508`(origin/main에 push 완료).

### 웨이브 2 실행 결과 요약 (260816, 이어서)

**웨이브 1과 달리 첫 시도부터 순조로웠다** — 교정된 그래프(2절)와 컴파일러 주도 위젠 방식(6절)이 실제로 검증된 뒤라, 배치 30–45의 18개 파일(17개 이동 + `LocalFileDiagnosticEventExternalSink.kt` 1개 영구 제외)을 계획 그대로 옮겼고 순환 클러스터(auth 2파일, device 2파일)도 예상대로였다.

- **프로덕션 17개** 이동 완료. `internal`→public 컴파일러 수렴: `:shared` 자체는 1라운드 만에 그린(위젠 불필요), `:app-android` 메인 2라운드(24개 심볼), `:app-android` 테스트 2라운드(6개 심볼) — 웨이브 1보다 훨씬 빠르게 수렴(웨이브 1의 26파일 순환 클러스터 같은 밀집도가 없었기 때문으로 보임).
- **테스트 7개 후보 중 6개 이동**(`AuthStateTest`, `DebugReportApplicationRunnerTest`, `DeviceIdentityTest`, `AutoAiCompletionApplierTest`, `AutoAiEndgameRunnerTest`, `AutoAiScheduledTurnRunnerTest`), **`RuntimeEventApplicationTest.kt`는 원위치 유지** — `buildEngineOperationDiscardLogPlan`/`recordEngineOperationDiscardLog`(`engine/operation/EngineOperationResultApplication.kt`·`EngineOperationLifecycleController.kt`, 웨이브 3 예정)까지 테스트하고 있어서. 웨이브 3 완료 후 재확인할 것.
- **새로 발견한 테스트 전환 패턴**: `DeviceIdentityTest.kt`가 `org.junit.Assert.assertThrows(X::class.java) { ... }`를 썼다 — 지금까지의 단순 import 1:1 치환(`assertEquals` 등)과 달리 문법이 다르다. `kotlin.test.assertFailsWith<X> { ... }`로 변환(제네릭 타입 파라미터로, `.java` 클래스 인자가 아님). 앞으로 `assertThrows`가 나오면 이 패턴 적용.
- `make test` **첫 시도에 바로 전체 그린** — `LayeringContractTest.kt` 관련 실패 없음(웨이브 1에서 미리 `applicationFile()` 헬퍼로 고쳐둔 덕분에 회귀 없었음, 이번 웨이브 파일들이 원래 하드코딩 대상도 아니었음).

커밋 `4023c09`(origin/main에 push 완료).

### 웨이브 3 실행 결과 요약 (260816, 이어서) — **이 문서 최초 스코프 밖의 파일 1개를 새로 발견해 포함시킴**

배치 46–61(`engine/`의 나머지 16개 파일 — 로컬 delegate·gateway·operation 하위)을 이동. 프로덕션 16개 + 테스트 5개(그중 2개는 웨이브 1·2에서 "아직 범위 밖"으로 미뤄뒀던 `EngineSessionTest.kt`/`RuntimeEventApplicationTest.kt` — 이번 웨이브로 그 파일들이 이동하면서 자동으로 범위 안이 됨, 매 웨이브마다 이전에 미룬 테스트를 재확인하는 게 실제로 유효했다) 이동 완료. `:shared`/`:app-android` 컴파일 + `make test` 전체 그린.

**새로 발견한 것 — `application/` 트리 밖 의존성**: `engine/LocalPositionAnalysisCacheCoordinator.kt`가 `application/`이 아니라 완전히 별개의 최상위 패키지 `com.worksoc.goaicoach.middleware`의 `PositionAnalysisCacheResolver.kt`를 참조하고 있었다. 이 계획서의 조사(2절)는 애초에 `application/` 트리 **내부**만 스캔했으므로 이런 트리 밖 참조는 원천적으로 탐지 범위 밖이었다 — `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`도 `middleware.`는 금지 목록에 없어서(2계층 성격상 애초에 허용되는 방향) 걸러내지 못했다. 확인해보니:
- `middleware/PositionAnalysisCacheResolver.kt` 자체는 android/java/ui/persistence import가 전혀 없어 완전히 이식 가능했고, 자신도 이미 이동한 `application/analysis/*` 타입에만 의존했다.
- `middleware/` 안의 다른 파일(`JsonNullableExtensions.kt`는 `org.json.JSONObject`를 써서 이식 불가, `PositionAnalysisGateway.kt`/`RemotePositionAnalysisGateway.kt`)은 이걸 참조하지 않아 연쇄 확장 위험이 없었다.
- 규모가 파일 1개(+테스트 1개)로 작고 명확히 경계 지어져 있어, 사용자에게 다시 확인받지 않고 바로 `shared/src/commonMain/kotlin/com/worksoc/goaicoach/middleware/`(패키지명 유지, `:shared` 안에 새 최상위 패키지로)로 함께 옮겼다 — 웨이브 1의 "56개로 확장" 같은 큰 스코프 변경과는 성격이 다르다고 판단(단일 파일, 완전히 격리됨, 되돌리기도 쉬움).
- **앞으로의 웨이브에서 주의할 점**: `application/` 파일이 `middleware/`(또는 이론상 다른 app-android 최상위 패키지)를 참조하는 다른 사례가 더 있을 수 있다 — `Unresolved reference 'middleware'` 같은 컴파일 에러가 나오면 이 패턴으로 처리(포터빌리티 확인 후 `shared/.../middleware/`로 함께 이동)할 것.
- 컴파일러 위젠 스크립트(`converge.py`)의 정규식 버그 하나 더 발견/수정: Kotlin의 `fun interface`(함수형 인터페이스) 선언을 `internal fun interface Foo`처럼 쓰면 기존 정규식이 "fun interface"를 통째로 수정자로 잘못 소비해 이름을 못 찾았다 — `kw` 그룹에 `fun\s+interface`를 `fun`보다 먼저 오는 대안으로 추가해 해결.

커밋 `d962a8d`(origin/main에 push 완료).

### 웨이브 4 실행 결과 요약 (260816, 이어서)

배치 62–70(humanmove·preferences 잔여, premium 순환 클러스터 2건, prompt) 12개 파일 + 테스트 5개(`FeatureAccessPolicyTest`, `PremiumStateTest`, `PremiumAdGrantApplicationTest`, `PremiumPurchaseApplicationTest`, `PromptPriorityApplicationTest`) 이동. 이번엔 웨이브 3에서 발견한 "application/ 트리 밖 참조" 패턴을 **이동 전에 미리 검사**했다(`middleware`/`ui`/`persistence`/`engine` import 유무를 12개 파일 전부 grep) — 전부 클린해서 놀랄 일 없이 진행. `internal`→public 컴파일러 수렴: `:shared` 1라운드(위젠 불필요), `:app-android` 메인 3라운드(28개 심볼 — premium이 결제/광고 관련 데이터 모델이 많아 위젠 대상이 좀 더 많았음), 테스트 1라운드(불필요). `make test` 전체 그린.


---

## 1. 핵심 요약 (착수 전 조사 시점 스냅샷 — 웨이브 1 실행 후 달라진 숫자는 0절 참고)

| 항목 | 값 |
|---|---|
| 남은 프로덕션 파일 | 124개, 20개 서브패키지 (스파이크로 이미 이전된 `safety/` 제외) |
| 남은 기존 단위테스트 파일 | 50개 (전부 `app-android/src/test/.../application/`에 평평하게, `package com.worksoc.goaicoach.application`로 존재 — 스파이크의 `EngineTurnWatchdogTest.kt`와 동일한 기존 관례) |
| 파일 단위 순환참조 | **2건만 존재** (2파일 클러스터 1개, 11파일 클러스터 1개 = 13개 파일). 나머지 111개 파일은 완전한 DAG — "서브패키지 전체가 뒤엉켜 있다"는 우려보다 실제로는 훨씬 정돈돼 있음 |
| `internal` 선언 감사 | 총 509개. **424개(83%)가 이미 `ui/`·`persistence/`·`middleware/`·엔진 합성 루트(`engine/`)·테스트 코드에서 참조돼 이동 시점과 무관하게 public 전환 필요**. 53개(10%)는 application/ 내부에서만 참조(이동 순서에 따라 조건부). 32개(6%)는 교차 참조 미발견 |
| 플랫폼 종속 예외 | 1개 파일(`diagnostic/LocalFileDiagnosticEventExternalSink.kt`, `java.io.File` 사용) — **영구히 app-android 잔류**, `LayeringContractTest.kt`가 이미 이 파일을 유일한 예외로 취급 중 |
| 제안 이동 순서 | 리프 → `GameSessionStateHolder.kt` 순으로 **10개 웨이브**로 분할 (아래 3절) |

**가장 중요한 발견**: 백로그 문서의 "주의" 항목은 internal 가시성이 "어느 지점에서 갑자기 크게 넓어질 수 있다"는 우려였는데, 실측 결과는 그런 국소적 스파이크가 아니라 **애초에 전체 트리의 83%가 이미 외부(UI/영속성/미들웨어)에서 쓰이고 있어 넓어짐이 전 구간에 고르게 퍼져 있다**는 쪽에 가깝다. 아래 6절 참고.

---

## 2. 조사 방법 (재현 가능하도록 기록)

- **의존 그래프**: `application/` 트리의 모든 `.kt` 파일에서 `package` 선언과 최상위 선언(`class`/`interface`/`object`/`fun`/`val`/`var`/`typealias`, 확장함수 수신 타입 포함)을 파싱해 심볼→소유 파일 맵을 만들고, `import com.worksoc.goaicoach.application.*` 구문을 그 맵에 대응시켜 **파일 단위** 방향 그래프를 구성했다. Tarjan SCC로 강한 연결 요소(순환)를 계산하고, 요소를 위상정렬해 "무엇이 무엇보다 먼저 이동해야 하는가"의 실제 순서를 얻었다. 와일드카드 임포트 0건, 미해결 임포트 0건(확장함수 수신 타입 파싱 보정 후) — 그래프가 코드와 어긋나지 않는다.
- **주의**: 서브패키지 단위로만 보면 거의 모든 서브패키지가 서로 순환하는 것처럼 보인다(예: `session`이 나머지 대부분과 상호 임포트). 그러나 이는 "서브패키지 A의 파일 하나가 B의 파일 하나를 참조하고, B의 다른 파일이 A의 다른 파일을 참조"하는 것이 서브패키지 레벨에서 뭉쳐 보이는 착시였다 — **실제 순환은 파일 단위로 딱 13개 파일에만 존재한다.** 이 문서의 순서는 파일 단위 계산 결과이지, 서브패키지 단위 직관이 아니다.
- **internal 감사**: 각 파일의 최상위 `internal` 선언명을 추출해, (a) application/ 트리 밖(`ui/`, `persistence/`, `middleware/`, 합성 루트 `engine/`, `androidTest`, `app-android/src/test`)에서 이름 매칭으로 사용처를 찾고 (b) application/ 내부 다른 파일에서의 사용처를 찾았다. **이름 기반 텍스트 매칭이라 정밀 타입 리졸버가 아니다** — 흔한 단어는 오탐 가능성이 있고, 반대로 리플렉션/문자열 참조는 놓칠 수 있다. 규모를 가늠하기 위한 조사이며, **실제 실행 시점의 정답은 컴파일러 에러**다(스파이크도 이 방식으로 정확히 2개를 찾았다) — 6절 참고.
- 원본 스크립트와 전체 배치 목록(113단계 세밀 순서, 이 문서의 10웨이브보다 더 잘게 쪼갠 버전)은 이번 세션의 스크래치패드에 있다 — 필요하면 재실행 가능(20줄 내외 파이썬, `application/` 트리 재스캔).

**260816 실행 중 발견 — 이 조사 방법 자체에 결함이 있었다**: 위 그래프는 `import` 구문만 파싱했다. **Kotlin은 같은 패키지 안의 파일끼리는 import 없이도 서로의 최상위 선언을 참조할 수 있다** — 이 "같은 패키지 내부 참조" 엣지가 전부 누락돼 있었다. 웨이브 1의 19개 파일 중 2개(`EngineOperationPolicy.kt`, `ScoreDisplayApplication.kt`)를 실제로 옮겨 컴파일해보고서야 발견했다("Unresolved reference" 에러로). 같은 패키지 내부 참조까지 포함해 그래프를 다시 계산하니(파일 텍스트에서 형제 파일이 선언한 심볼명을 직접 검색하는 방식, `dep_graph2.py`) 순환 클러스터가 13개 파일(2건)이 아니라 **39개 파일(7건)**로 늘었다 — 그중 가장 큰 건 원래 "웨이브 2"라 부르던 11개 파일이 사실은 **26개 파일**이 서로 얽힌 것이었다(`session/GameSessionCoreState.kt`, `undo/` 3개 파일 등 원래 웨이브 6~8로 분류했던 것들이 실제로는 여기 다 물려 있었다). 교훈: **서브패키지 레벨 그래프는 착시를 만들고, import만 보는 파일 레벨 그래프도 같은 패키지 내부 참조를 놓치면 착시를 만든다** — 순환 여부를 판단하려면 반드시 같은 패키지 내부의 무-import 참조까지 스캔해야 한다. 이 교정된 스크립트(`dep_graph2.py`)와 그 결과(`scc_order2.txt`)가 이제 이 문서의 이동 순서 근거다(아래 3절부터는 교정된 결과 기준).

---

## 3. 이동 순서 — 웨이브 1(완료) + 남은 웨이브 2~6

**이 절은 교정된 그래프(2절 끝의 "260816 실행 중 발견" 참고) 기준으로 다시 썼다.** 원래 10웨이브 계획(웨이브 2~10)은 파일 단위 그래프에 같은 패키지 내부 참조가 빠져 있던 상태에서 나온 것이라 순서가 실제로 틀렸다 — 예를 들어 원래 "웨이브 8"이었던 `session/GameSessionCoreState.kt`가 실제로는 웨이브 1의 26파일 순환 클러스터 안에 있었다. 아래는 실제로 실행 가능한 순서다.

### 웨이브 1 — 완료 (56개 파일)

원래 계획된 19개 + 실행 중 발견된 진짜 원자적 단위를 채우는 추가 37개(2파일 순환 1건 + 26파일 순환 클러스터 1건 포함) = 56개, 전부 `shared/src/commonMain/.../application/`로 이동 완료. 정확한 파일 목록은 git 이력(`git log --diff-filter=R -- shared/src/commonMain/kotlin/com/worksoc/goaicoach/application`)이 원본이다 — 이 문서에 다시 나열하지 않는다(교정 전 계획에 있던 파일별 나열은 부분적으로 부정확해졌으므로 삭제).

동반 이동한 테스트: 원래 웨이브 1로 지목했던 11개 중 **7개는 그대로 이동, 1개는 "배치 미확정"이었다가 실제로는 범위 안임이 확인돼 이동**(`PositionAnalysisCachePolicyTest.kt`), **4개는 웨이브 1 범위를 넘어서는 프로덕션 파일(주로 아직 안 옮긴 `score`/`preferences`/`diagnostic`/`engine` 파일)까지 테스트하고 있어서 원위치로 되돌림**:

| 테스트 파일 | 결과 | 이유 |
|---|---|---|
| `AnalysisSessionTest.kt`, `EndgameResolverTest.kt`, `EngineOperationPolicyTest.kt`, `GameSessionApplicationTest.kt`, `GameSessionSettingsStateTest.kt`, `MoveReviewTest.kt`, `SavedSessionPromptApplicationTest.kt` | 이동 완료 | 계획대로, 범위 안 |
| `PositionAnalysisCachePolicyTest.kt` | 이동 완료 | 원래 "배치 미확정"이었으나 실제 참조가 전부 웨이브 1 범위 안 |
| `ScoreDisplayApplicationTest.kt` | **원위치로 되돌림** | `RestoredGameScoreSyncRunnerApplication.kt` 등 아직 안 옮긴 score 파일까지 테스트 |
| `UserPreferencesApplicationTest.kt` | **원위치로 되돌림** | `preferences/UserPreferencesPorts.kt`·`UserPreferencesAutosaveApplication.kt`(아직 안 옮김) 테스트 |
| `DiagnosticEventApplicationTest.kt` | **원위치로 되돌림** | `LocalFileDiagnosticEventExternalSink.kt`(영구 잔류 예정 파일!) 직접 테스트 |
| `EngineSessionTest.kt` | **원위치로 되돌림** | `LocalEngineCoreSessionDelegate.kt`·`LocalEngineSessionClient.kt`(아직 안 옮김) 테스트 |

**교훈**: 테스트 파일 이름이 `<ProductionFile>Test.kt` 패턴을 따른다고 해서 그 파일 하나만 테스트한다고 가정하면 안 된다 — 특히 `score`/`engine`/`preferences`/`diagnostic`처럼 서로 얽힌 서브패키지에서는 테스트가 관련 파일 여러 개를 함께 검증하는 경우가 흔하다. **웨이브를 옮길 때마다 그 웨이브의 production 파일에 대응하는 테스트가 실제로 그 웨이브 범위 안의 것만 참조하는지 `make test`로 직접 확인하고, 범위 밖 참조가 나오면 그 테스트만 원위치로 되돌리는 방식**이 실용적이다(전수 사전 조사보다 빠르고 정확했다).

### 남은 68개 파일 — 웨이브 2~6

교정된 그래프에서 웨이브 1(배치 1~29) 이후 순서를 그대로 세션 크기로 묶었다. 웨이브 1과 달리 추가 순환 클러스터가 크지 않아(최대 3파일) 각 웨이브가 계획대로 실행될 가능성이 높지만, **웨이브 1의 교훈대로 각 웨이브 착수 시 파일 이동 후 컴파일러가 지목하는 대로 처리하고, 세밀 순서가 필요하면 스크래치패드의 `scc_order2.txt`(배치 30~92)를 직접 참고할 것** — 이 표는 그 배치들을 세션 단위로 묶은 요약이다.

| 웨이브 | 파일 수 | 배치 범위 | 주요 내용 | 상태 |
|---|---|---|---|---|
| 2 | 18(1개 영구 제외, 17개 이동) | 30–45 | analysis 잔여, auth(2파일 순환), runtime, autoai 잔여, debugreport 잔여, device(2파일 순환), diagnostic 잔여(**`LocalFileDiagnosticEventExternalSink.kt` 포함 — 4절대로 이동 제외**) | **완료(260816)** |
| 3 | 16(+`middleware/PositionAnalysisCacheResolver.kt` 1개 추가 발견분) | 46–61 | `engine/`의 나머지 전체(로컬 엔진 delegate·gateway·operation 하위) | **완료(260816)** |
| 4 | 12 | 62–70 | humanmove·preferences 잔여, premium(3파일 순환 1건 + 2파일 순환 1건 포함), prompt | **완료(260816)** |
| 5 | 11 | 71–81 | savedgame·score 잔여 + **`session/GameSessionStateHolder.kt` 본체(배치 81)** | 다음 차례 |
| 6 | 11 | 82–92 | topmoves·session·startgame 잔여 컨트롤러 — 홀더에 의존하는 마지막 그룹 | 대기 |

합계: 17+16+12+11+11 = 67 이동 + 1 영구 제외 = 68(+ 웨이브 3에서 발견된 `application/` 트리 밖 파일 1개 별도). 웨이브 4 완료 시점 기준 남은 것: 웨이브 5~6, 22개 파일 — **웨이브 5에 `GameSessionStateHolder.kt` 본체가 있다.**

**웨이브 5에 `GameSessionStateHolder.kt`가 있다** — 원래 계획의 "웨이브 9" 프레이밍과 거의 일치(리프부터 시작해 뒤쪽에서 홀더가 나오는 흐름 자체는 교정 후에도 유지됨). 웨이브 5·6 완료 직후 `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 실기 재확인 필수(0절 완료 기준 참고).

### (참고용, 더 이상 정확하지 않음) 원래 10웨이브 계획

웨이브 경계는 전부 실제 위상정렬 경계에서 끊었다 — 즉 **어떤 웨이브도 그 안에서 참조하는 파일을 이후 웨이브에 남겨두지 않는다**(순환 클러스터 제외, 아래 명시). 웨이브 크기는 세션 하나로 감당할 만한 규모(10~20개 파일)로 묶은 것이지 강제 단위는 아니다 — 여러 웨이브를 한 세션에 합쳐도 되고, 5번 웨이브(엔진, 20개)처럼 큰 것은 더 잘게 쪼개도 된다(세밀 순서는 스크래치패드에 있음). **각 웨이브는 그 자체로 컴파일+테스트가 그린이어야 하는 독립 체크포인트**로 설계했다.

| 웨이브 | 파일 수 | 주요 서브패키지 | 비고 |
|---|---|---|---|
| 1 | 19 | analysis, engine, savedgame, session, preferences, diagnostic, endgame, score, debugreport, movereview | 순수 리프. 2파일 순환 1건 포함(아래) |
| 2 | 11 | analysis, autoai, debugreport, engine, humanmove, score, session, topmoves | **11파일 순환 클러스터 — 반드시 한 커밋으로 통째 이동** |
| 3 | 12 | analysis, auth, autoai, session, runtime, startgame | |
| 4 | 12 (13 중 1개 영구 제외) | undo, autoai, debugreport, device, diagnostic, engine | 예외 파일 위치(4절) |
| 5 | 20 | engine (전부) | `engine/` 서브패키지의 나머지 전체 |
| 6 | 13 | session, humanmove, preferences, premium | |
| 7 | 12 | premium, prompt, savedgame, score | |
| 8 | 10 | score, session | |
| 9 | **1** | session | **`GameSessionStateHolder.kt` 본체** |
| 10 | 13 | topmoves, session, startgame, undo | 홀더에 의존하는 나머지 컨트롤러들 |

합계: 19+11+12+12+20+13+12+10+1+13 = **123개 이동 + 1개 영구 잔류 = 124개**.

### 웨이브 1 (19개) — 리프

```
analysis/AnalysisFormatter.kt
analysis/AnalysisSession.kt
analysis/PositionAnalysisCache.kt
engine/operation/EngineOperationPolicy.kt
savedgame/SavedSessionPromptApplication.kt
engine/EngineEffectLauncherApplication.kt
session/GameSessionApplication.kt
preferences/UserPreferencesApplication.kt   ┐ 2파일 순환 — 같이 이동
session/GameSessionSettingsState.kt         ┘
diagnostic/DiagnosticEventApplication.kt
diagnostic/DiagnosticEventPorts.kt
diagnostic/DiagnosticEventObserverApplication.kt
endgame/EndgameResolver.kt
engine/EngineSession.kt
endgame/EndgameLogFormatter.kt
score/ScoreDisplayApplication.kt
engine/EngineBenchmarkModels.kt
debugreport/DebugReportPorts.kt
movereview/MoveReview.kt
```
동반 테스트(11개): `AnalysisSessionTest, DiagnosticEventApplicationTest, EndgameResolverTest, EngineOperationPolicyTest, EngineSessionTest, GameSessionApplicationTest, GameSessionSettingsStateTest, MoveReviewTest, SavedSessionPromptApplicationTest, ScoreDisplayApplicationTest, UserPreferencesApplicationTest`

### 웨이브 2 (11개) — **순환 클러스터, 원자적 이동 필수**

```
analysis/PositionAnalysisCacheOptimization.kt
autoai/AutoAiPolicyApplication.kt
autoai/AutoAiRunnerApplication.kt
debugreport/DebugReportBuilder.kt
engine/EngineSessionClient.kt
humanmove/HumanMoveApplication.kt
score/ScoreDisplayModels.kt
score/ScoreEstimateApplication.kt
session/GameSessionAnalysisState.kt
session/GameSessionController.kt
topmoves/TopMovesModels.kt
```
이 11개는 서로 실제로 상호 참조한다(예: `EngineSessionClient` ↔ `GameSessionController` 계열). 부분 이동 시 양쪽 모듈 어느 쪽도 컴파일이 안 된다 — 반드시 한 커밋.
동반 테스트(5개): `DebugReportBuilderTest, GameSessionAnalysisStateTest, GameSessionControllerTest, HumanMoveApplicationTest, PositionAnalysisCacheOptimizationTest`

### 웨이브 3 (12개)

```
analysis/PositionAnalysisCacheOptimizationRunnerApplication.kt
analysis/PositionCacheOptimizationController.kt
auth/AuthClientPort.kt
auth/AuthState.kt
autoai/AutoAiCompletionApplication.kt
session/GameSessionTurnTimeState.kt
runtime/RuntimeAiTurnEventApplication.kt
session/GameSessionRuntimeState.kt
startgame/StartGameApplication.kt
runtime/RuntimeEventApplication.kt
runtime/RuntimeEventPorts.kt
autoai/AutoAiCompletionApplierApplication.kt
```
동반 테스트(6개): `AuthStateTest, GameSessionRuntimeStateTest, GameSessionTurnTimeStateTest, RuntimeEventApplicationTest, StartGameApplicationTest, AutoAiCompletionApplierTest`

### 웨이브 4 (13개 중 12개 이동, 1개 영구 제외)

```
undo/UndoApplication.kt
autoai/AutoAiEffectLauncherApplication.kt
autoai/AutoAiEndgameRunnerApplication.kt
autoai/AutoAiScheduledTurnRunnerApplication.kt
autoai/AutoAiTurnController.kt
debugreport/DebugReportApplicationRunner.kt
debugreport/DebugReportController.kt
debugreport/DebugReportSections.kt
device/DeviceIdentity.kt
device/DeviceIdentityPorts.kt
diagnostic/DiagnosticEventExternalSinkApplication.kt
[diagnostic/LocalFileDiagnosticEventExternalSink.kt  ← 이동 안 함, 4절 참고]
engine/EngineAnalysisDiagnosticRecorder.kt
```
동반 테스트(3개+2개): `DebugReportApplicationRunnerTest, DeviceIdentityTest, UndoApplicationTest` + (naming이 축약된) `AutoAiEndgameRunnerTest.kt`(→`AutoAiEndgameRunnerApplication.kt` 테스트), `AutoAiScheduledTurnRunnerTest.kt`(→`AutoAiScheduledTurnRunnerApplication.kt` 테스트)

### 웨이브 5 (20개) — `engine/`의 나머지 전체

```
engine/EngineAssistantJudgePolicy.kt
engine/operation/EngineOperationScope.kt
engine/EngineBenchmarkController.kt
engine/EngineBenchmarkDisplayApplication.kt
engine/EngineBenchmarkPorts.kt
engine/EngineClock.kt
engine/EngineDeviceBenchmarkApplication.kt
engine/EngineSessionLifecycleApplication.kt
engine/EngineStartupApplication.kt
engine/LocalAiMoveEngineGateway.kt
engine/LocalEndgameJudgeGateway.kt
engine/LocalEngineBenchmarkDelegate.kt
engine/LocalEngineCoreSessionDelegate.kt
engine/LocalEngineSessionClient.kt
engine/LocalPositionAnalysisCacheCoordinator.kt
engine/RemoteEngineCandidate.kt
engine/operation/EngineOperationLifecycle.kt
engine/operation/EngineOperationLifecycleController.kt
engine/operation/EngineOperationPolicyAdapter.kt
engine/operation/EngineOperationResultApplication.kt
```
동반 테스트(5개): `EngineDeviceBenchmarkApplicationTest, EngineOperationLifecycleTest, EngineSessionLifecycleApplicationTest, EngineStartupApplicationTest, RemoteEngineCandidateTest`

### 웨이브 6 (13개)

```
session/GameSessionScoreState.kt
session/GameSessionMoveReviewState.kt
humanmove/HumanMoveController.kt
preferences/GameSetupUxMode.kt
preferences/UserPreferencesAutosaveApplication.kt
preferences/UserPreferencesPorts.kt
preferences/UserPreferencesSnapshot.kt
premium/AdRewardPort.kt
premium/FeatureAccessPolicy.kt
premium/PremiumAdGrantApplication.kt
premium/PremiumDeactivationApplication.kt
premium/PremiumPurchaseApplication.kt
premium/PremiumState.kt
```
동반 테스트(6개): `FeatureAccessPolicyTest, GameSessionMoveReviewStateTest, GameSessionScoreStateTest, PremiumAdGrantApplicationTest, PremiumPurchaseApplicationTest, PremiumStateTest`

### 웨이브 7 (12개)

```
premium/PremiumStatePorts.kt
premium/PurchasePort.kt
prompt/PromptPriorityApplication.kt
savedgame/SavedGameApplicationRunner.kt
savedgame/SavedGamePersistence.kt
savedgame/SavedGamePersistenceRunner.kt
savedgame/SavedGamePorts.kt
savedgame/SavedGameRestoreApplication.kt
savedgame/SavedGameSnapshot.kt
score/ScoreSyncCompletionApplication.kt
score/RestoredGameScoreSyncRunnerApplication.kt
savedgame/SavedSessionController.kt
```
동반 테스트(3개): `PromptPriorityApplicationTest, SavedGameApplicationRunnerTest, SavedGamePersistenceTest`

### 웨이브 8 (10개)

```
score/PostUndoScoreSyncRunnerApplication.kt
score/ScoreDisplayFormatterApplication.kt
score/ScoreEstimateController.kt
score/ScoreEstimateRunnerApplication.kt
score/ScoreSyncRunnerApplication.kt
score/ScoringRuleApplication.kt
score/ScoringRuleController.kt
score/ScoringRuleScoreSyncRunnerApplication.kt
session/GameSessionCoreState.kt
session/GameSessionDisplayStateApplierApplication.kt
```
동반 테스트(3개): `GameSessionCoreStateTest, GameSessionDisplayStateApplierApplicationTest, ScoringRuleApplicationTest`

### 웨이브 9 (1개) — **키스톤**

```
session/GameSessionStateHolder.kt
```
동반 테스트(1개): `GameSessionStateHolderTest.kt`

### 웨이브 10 (13개) — 홀더에 의존하는 나머지 컨트롤러

```
topmoves/ShowHideTopMovesApplication.kt
session/GameSettingsController.kt
session/TurnAutomationTriggerApplication.kt
startgame/NewGameController.kt
startgame/StartEngineBackedGameRunnerApplication.kt
topmoves/TopMoveAnalysisApplierApplication.kt
topmoves/TopMoveAnalysisEngine.kt
topmoves/TopMoveAnalysisGuard.kt
topmoves/TopMovesApplication.kt
topmoves/TopMovesController.kt
topmoves/TopMovesEffectLauncherApplication.kt
undo/UndoController.kt
undo/UndoRunnerApplication.kt
```
동반 테스트(2개+1개): `GameSettingsControllerTest, TopMovesApplicationTest` + `StartEngineBackedGameRunnerTest.kt`(naming 축약형)

### 배치 미확정 테스트 2개

`GameAutomationApplicationTest.kt`(engine.operation/analysis/savedgame/endgame/engine에 와일드카드 임포트 — 여러 서브패키지를 넘나드는 통합 성격 테스트)와 `PositionAnalysisCachePolicyTest.kt`(analysis 전역 와일드카드)는 이름 매칭으로 단일 파일에 대응시킬 수 없었다. 두 웨이브 다 각자의 임포트 대상 서브패키지가 이동하는 시점에 맞춰 이동하되, 정확한 웨이브는 실제 이동 시 무엇을 테스트하는지 열어서 확인할 것.

---

## 4. 예외: 영구히 app-android에 남는 파일 1개

`diagnostic/LocalFileDiagnosticEventExternalSink.kt`는 `java.io.File`/`System.currentTimeMillis()`를 직접 쓰는 **JVM 전용 어댑터**다(diagnostic 이벤트를 로컬 파일에 append). `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`이 이미 이 파일 하나만 `platformBoundAdapters`로 예외 처리하고 있다 — 즉 **애초부터 "이식 대상이 아닌 어댑터"로 설계돼 있었다**, 이번에 새로 발견한 제약이 아니다.

**처리 방침**: 이 파일이 구현하는 포트 인터페이스(`DiagnosticEventExternalSinkPort`, `diagnostic/DiagnosticEventPorts.kt` 안에 있으며 웨이브 1에서 이동)는 정상적으로 `:shared`로 옮긴다. 구현체만 app-android에 남긴다 — 이미 이 저장소가 4계층(외부 연동)에서 쓰고 있는 포트/어댑터 패턴 그대로다(`application/auth/AuthClientPort.kt`(포트, 이동) ↔ `ui/AndroidAuthClient.kt`(어댑터, 잔류)와 동일 구조). `expect`/`actual`은 필요 없다 — app-android가 이미 `:shared`에 의존하는 방향이라, public이 된 포트 인터페이스를 app-android 쪽 구현체가 참조하는 것은 자연스러운 순방향 참조다.

선택사항(강제 아님): 위생상 이 파일을 `application/diagnostic/`에서 `ui/` 또는 그에 준하는 곳으로 옮겨 "여긴 이제 application 트리가 아니다"를 물리적으로도 드러낼 수 있다 — 원한다면 웨이브 4 처리 시 같이 할 것을 제안.

---

## 5. `:shared` 착지 구조

`shared/build.gradle.kts` 확인 결과: `commonMain`/`commonTest` + `androidTarget()`(JVM 17) + `-PenableIosTargets=true`로 게이팅된 iOS 타깃 3종. 커스텀 `androidMain` 소스셋은 현재 없다(4절의 포트/어댑터 방침으로 처리하면 새로 만들 필요도 없다). 기존 최상위 패키지는 `application/`(스파이크로 `safety/`만 존재), `match/`, `shared/` 세 개 — 이번 이동으로 `application/` 아래 나머지 20개 서브패키지가 채워지는 구조다. 패키지명은 스파이크에서 확인했듯 그대로 유지하면 된다(모듈 경계와 패키지명은 독립적 축).

---

## 6. `internal` 가시성 정책

### 실측 (서브패키지별)

| 서브패키지 | internal 총계 | 외부(hard) public 필요 | application 내부(soft) | 미사용/자기참조 |
|---|---:|---:|---:|---:|
| analysis | 43 | 38 | 3 | 2 |
| auth | 4 | 4 | 0 | 0 |
| autoai | 47 | 44 | 1 | 2 |
| debugreport | 24 | 15 | 8 | 1 |
| device | 2 | 2 | 0 | 0 |
| diagnostic | 15 | 14 | 1 | 0 |
| endgame | 7 | 2 | 4 | 1 |
| engine | 95 | 70 | 14 | 11 |
| humanmove | 22 | 22 | 0 | 0 |
| movereview | 8 | 7 | 0 | 1 |
| preferences | 12 | 10 | 0 | 2 |
| premium | 22 | 20 | 0 | 2 |
| prompt | 2 | 1 | 0 | 1 |
| runtime | 32 | 20 | 10 | 2 |
| savedgame | 25 | 24 | 0 | 1 |
| score | 61 | 53 | 5 | 3 |
| session | 21 | 19 | 1 | 1 |
| startgame | 7 | 7 | 0 | 0 |
| topmoves | 49 | 41 | 6 | 2 |
| undo | 11 | 11 | 0 | 0 |
| **합계** | **509** | **424 (83%)** | **53 (10%)** | **32 (6%)** |

### 실행 규칙 (권장)

1. **웨이브 이동 시 `internal` 수정자를 미리 고치지 않는다.** 파일만 옮기고 컴파일한다.
2. `:shared`/`:app-android` 양쪽 컴파일 에러가 정확히 어떤 심볼이 public이어야 하는지 알려준다 — 이게 스파이크가 실제로 썼던 방식이고("2개 internal fun을 public으로" — 사전 조사가 아니라 컴파일 에러로 찾음), 이름 기반 grep 추정보다 정확하다(타입 정보 없이 텍스트만 본 추정이라 오탐/누락 가능).
3. 위 표는 "이 범위가 스파이크처럼 한두 개 수준일 거라고 기대하지 말라"는 규모 감각을 위한 것이다 — 424/509(83%)가 이미 `ui/`·`persistence/`·`middleware/`·테스트에서 쓰이고 있어, **웨이브 대부분에서 "옮기는 파일 대다수의 internal을 public으로 바꾼다"가 기본값이 될 가능성이 높다.** "일부만 넓어질 것"을 전제로 한 최소 변경 접근은 이 데이터와 맞지 않는다.
4. 53개(soft, application 내부 전용)는 그 심볼을 참조하는 다른 파일이 **같은 웨이브 안에 있으면** internal 유지 가능, 아직 이동 안 한 뒤 웨이브에 있으면 그 시점엔 public 전환 필요(추후 마지막 웨이브까지 다 옮기고 나서 narrow-back하는 건 선택적 후속 정리로 남겨둘 것 — 이번 범위 아님).
5. 32개(미사용/자기참조)는 진짜 internal 유지 후보지만, 흔한 이름(예: 길이가 짧은 헬퍼명)이면 이름 매칭이 놓쳤을 수 있다 — 역시 컴파일러가 최종 확인.

### 260816 웨이브 1 실행 검증 — 위 규칙 그대로 통했다, 단 2단계 캐스케이드가 있었다

컴파일러 주도 방식(1~2번 규칙)을 실제로 4~5라운드에 걸쳐 돌렸고, 정확히 예상대로 작동했다:

1. **1차 라운드**: "Cannot access 'X': it is internal in file" — 이 문서가 예상한 그대로. `:shared` 자체 컴파일(같은 모듈 내부라 관대함)은 바로 통과했지만, `:app-android` 컴파일(모듈 경계를 실제로 넘는 지점)에서 대량으로 터졌다. 웨이브 1(56개 파일) 기준 최종적으로 **약 200개 심볼**을 public으로 바꿨다 — 표의 509개 예상치보다는 적지만(이동한 게 56/124라서 비례), "한두 개"가 아니라는 예측은 정확히 맞았다.
2. **2차 캐스케이드(이 문서가 예상 못 한 것)**: 1차에서 함수/클래스를 public으로 바꾸고 나면, Kotlin이 **"'public' 함수가 'internal' 타입을 노출한다"는 별도 에러 범주**를 낸다 — 함수 시그니처에 쓰인 파라미터/리턴 타입도 함수 자신과 같은 가시성이어야 한다는 Kotlin 자체 규칙. 이것도 컴파일 에러로 정확히 지목되므로 대응은 동일(그 타입 선언도 public으로), 다만 **한 번의 컴파일로 안 끝나고 라운드가 여러 번 필요하다**는 걸 미리 알아두면 좋다 — "컴파일 → 나온 에러만큼 고침 → 재컴파일"을 에러가 0이 될 때까지 자동으로 반복하는 작은 스크립트(스크래치패드의 `converge.py`)를 짜서 처리했다. 다음 웨이브에도 같은 스크립트를 그대로 재사용할 수 있다.
3. **컴파일 타깃이 여러 개라 순서대로 다 돌려야 한다**: `:shared:compileDebugKotlinAndroid`(shared 자체) → `:app-android:compileDebugKotlin`(app-android 메인) → `:app-android:compileDebugUnitTestKotlin`(app-android **테스트** 소스셋도 shared의 internal에 별도로 접근한다 — UI에서 안 쓰지만 기존 테스트가 화이트박스로 접근하던 함수들이 여기서 걸림) 순으로 각각 별도 라운드가 필요했다. 하나만 그린이라고 다음 것도 그린이라고 가정하지 말 것.

---

## 7. 웨이브별 완료 기준

각 웨이브 커밋마다:
1. `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` 후 `:shared:compileDebugKotlinAndroid` → `:app-android:compileDebugKotlin` → `:app-android:compileDebugUnitTestKotlin` **순서대로** 그린(6절 "260816 웨이브 1 실행 검증" 참고 — 셋 다 별도 라운드가 필요할 수 있다)
2. 이동한 테스트 파일의 `org.junit.*` → `kotlin.test.*` 임포트 전환(스파이크에서 확인된 필수 기계적 작업)
3. **이동한 프로덕션 파일에 대응하는 각 테스트가 실제로 그 웨이브 범위 안의 것만 참조하는지 확인** — 범위를 넘어서면(예: 아직 안 옮긴 다른 파일도 함께 테스트) 그 테스트 파일만 원위치로 되돌린다(3절 "웨이브 1" 표의 4개 사례가 실례).
4. `make test` 전체 그린 — 컴파일뿐 아니라 `LayeringContractTest.kt`의 **다른 테스트들**도 확인할 것(아래 참고, 이번에 새로 발견된 함정).
5. `GameSessionStateHolder.kt`가 포함된 웨이브(교정된 순서로는 웨이브 5)와 그 다음 웨이브 완료 직후에는 `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt`를 에뮬레이터에서 실기 재확인(백로그 문서 완료 기준 그대로) — 이 두 파일이 세션 상태 배선의 실제 진입점이라 회귀 위험이 가장 크다.

**전체 웨이브가 다 끝난 뒤에만** `LayeringContractTest.kt`의 **계층 규칙 자체**(`engineOperationApplicationPoliciesStayPortable`의 스캔 대상을 `:shared`로 바꾸는 등)를 갱신한다(로드맵 문서 5-(3), 백로그 문서 "서두 원칙" 2번 그대로 — 코드가 실제로 옮겨지기 전에 강제 규칙부터 고치지 않는다). `engineOperationApplicationPoliciesStayPortable` 자체는 `applicationRoot.walkTopDown()`으로 **app-android에 남아있는 파일만** 스캔하므로, 웨이브가 진행될수록 보는 파일 수가 자연히 줄어들 뿐 에러는 안 난다 — 이 부분은 여전히 손댈 필요 없다.

**260816 새로 발견 — 위와는 다른 종류의 `LayeringContractTest.kt` 문제**: 그 파일 안에는 계층 규칙과 무관하게 **특정 파일 하나를 하드코딩된 경로로 직접 읽어서** 내용을 검사하는 테스트가 여럿 있다(`localEngineSessionDelegateOwnsSessionOrchestration` 등). 이런 테스트는 참조하는 파일이 `app-android`에서 `shared`로 이동하는 순간 `FileNotFoundException`으로 **하드하게** 깨진다(포터빌리티 스캔처럼 우아하게 줄어들지 않는다) — 웨이브 1에서 실제로 3건 발생. 이건 "계층 규칙을 미리 강화하지 말라"는 위 원칙과는 다른 문제다 — 규칙 내용은 그대로고 파일을 어디서 찾을지만 틀어졌을 뿐이므로, **발생 즉시 고쳐야 한다**(다음 웨이브까지 미루면 계속 빨간불). 재사용 가능한 해결책을 만들어 뒀다: `LayeringContractTest.kt`에 `applicationFile(relativePath: String): File` 헬퍼를 추가해 `shared/src/commonMain/.../application/$relativePath`에 있으면 그걸, 없으면 `app-android/src/main/java/.../application/$relativePath`를 반환하게 했다 — **웨이브 1에서 발견된 3건뿐 아니라, 그 시점까지 파일 하나를 하드코딩 경로로 참조하던 나머지 10곳도 전부 이 헬퍼로 미리 바꿔놨다**(아직 안 옮긴 파일이라 지금은 안 깨지지만, 나중에 그 파일이 이동하면 똑같이 깨질 게 뻔했으므로). 앞으로 새 웨이브에서 `FileNotFoundException`이 나면 먼저 `applicationFile(...)`로 바꿔서 해결되는지 확인할 것 — 디렉터리 전체를 스캔하는 테스트(`ktFilesIn(root)` 패턴, 예: `scoreRunnersUseEngineSessionClientContractOnly`)는 포터빌리티 스캔과 같은 이유로 건드릴 필요 없다.

---

## 9. 크로스 모듈 컴파일 시 새로 나타날 수 있는 별종 에러 — 스마트캐스트

`internal`/`FileNotFoundException` 두 범주와 별개로, 웨이브 1에서 **Kotlin의 모듈 간 스마트캐스트 제약**으로 인한 실제 컴파일 에러가 1건 나왔다: `app-android/ui/FinalScoreJudgementPresentationExtensions.kt`가 `FinalScoreJudgement`(이번에 `:shared`로 이동)의 nullable 프로퍼티 `winner`를 `if (winner == null) ... else ...`로 null 체크한 뒤 스마트캐스트해서 쓰고 있었는데, **다른 모듈에 선언된 프로퍼티는 Kotlin이 스마트캐스트를 허용하지 않는다**(커스텀 getter가 없다는 걸 컴파일러가 모듈 경계 너머로 보장 못 하기 때문 — 흔한 Kotlin 제약이지 이번 이전 작업의 버그가 아니다). 고치는 방법은 표준적이다: 프로퍼티를 지역 `val`에 먼저 담고("`val winner = winner`") 그 지역 변수로 null 체크·스마트캐스트를 하면 된다. 앞으로 웨이브에서 `ui/`쪽에 "Smart cast to 'X' is impossible, because 'Y' is a public API property declared in different module" 에러가 나면 이 패턴으로 고칠 것 — internal 가시성 문제가 아니므로 6절의 컴파일러 주도 위젠 방식으로는 안 잡힌다(별도로 코드 자체를 고쳐야 함).

---

## 10. 다음 단계

**웨이브 1~4는 완료됐다**(0절, 커밋 `30d6508`/`4023c09`/`d962a8d`/다음 커밋). 다음은 웨이브 5(3절 표, 배치 71–81, savedgame·score 잔여 + **`session/GameSessionStateHolder.kt` 본체**, 11개 파일)부터 — 이 웨이브가 끝나면 키스톤이 이동하므로, 완료 후 `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 실기 재확인이 필요하다(0절·7절 참고):
- 6절의 규칙대로 internal은 미리 안 건드리고 컴파일 에러로 확정(순서: `:shared` 메인 → `:app-android` 메인 → `:app-android` 테스트)
- 이동하는 각 프로덕션 파일의 대응 테스트가 웨이브 범위를 넘는지 확인(7절 3번)
- `make test`에서 `LayeringContractTest.kt`가 새로운 `FileNotFoundException`을 내면 `applicationFile()` 헬퍼로 해결되는지 우선 확인(7절 마지막 문단)
- 웨이브 5(`GameSessionStateHolder.kt` 포함)·6 완료 후 스모크 테스트 실기 재확인

`docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 고도화 로드맵 5번 항목 (2)는 이 문서를 가리키도록 이미 갱신해 뒀다(같은 세션).
