# 도메인 분리 리팩토링 착수 계획서 — 260729 23h15m

작성 시각: 2026-07-29 23:15 (KST)

## 0. 이 문서의 성격과 타임스탬프 관례

이 문서부터 리팩토링 착수 계획서는 파일명과 제목에 **`YYMMDD HHhMMm`(연월일 시분)** 타임스탬프를 붙인다. 기존 `docs/refactoring/`의 날짜만(`YYYY-MM-DD`) 붙이는 관례보다 더 세밀한 단위인데, 다음 두 가지를 추적하기 위함이다.

1. **리팩토링이 어느 주기로 계획·착수되었는지** — 같은 날 여러 번 계획이 갱신될 수 있으므로 시분까지 남긴다.
2. **후속 리팩토링 시 참고 지점** — 다음 착수 계획서를 쓸 때 "가장 최근 계획서가 언제, 무엇을 확인했는지"를 파일명만으로 바로 찾을 수 있게 한다.

이 관례는 기존 날짜만 붙이는 `docs/refactoring/*_YYYY-MM-DD.md` 파일들을 대체하지 않는다 — 그 문서들은 각자의 시점 기록으로 그대로 둔다. 앞으로 새로 쓰는 "착수 계획서" 유형의 문서만 이 시분 단위 타임스탬프를 따른다.

## 1. 배경 및 요청 사항

사용자가 "도메인 분리 관점을 더 철저하게 재검토"하며 리팩토링을 요청했다. 구체적으로 다음 두 축을 구분해서 보고 싶어 한다.

1. **엔진의 기능을 그대로 올려주는 연결 브릿지 역할** — 엔진 원시 기능을 1:1로 노출만 하는 얇은 계층.
2. **해당 엔진 기능을 조합해서 기능을 조합·운영하는 모듈(레이어)** — 원시 기능을 유스케이스 단위로 묶어 실제 제품 기능을 만드는 계층.

사용자는 "이미 문서화가 잘 되어 있을 것"이라고 예상했고, **확인 결과 정확했다.** 아래 3절에서 기존 문서와 정확한 기존 용어를 그대로 인용한다 — 이 계획서는 새 용어를 만들지 않고 기존 용어를 재사용한다.

## 2. 검토한 기존 문서 (재사용 근거)

- **`docs/ARCHITECTURE.md`** (2026-06-17 작성, 2026-06-28 최종 갱신) — 7계층 구조의 canonical 현재 문서. 이 계획서의 1차 근거.
- **`docs/refactoring/DOMAIN_SEPARATION_REFACTORING_PLAN.md`** (2026-06-12) — 7계층 분리 원칙의 원문(origin) 문서.
- **`docs/refactoring/REFACTORING_STRATEGY_2026-06-08.md`** — "브릿지 vs 조합" 구분이 최초로 등장한 문서(2026-06-12 addendum).
- **`docs/refactoring/ARCHITECTURE_LAYERS_REVIEW_2026-06-14.md`** — 아카이브된 초안(`archive/2026-06-17-architecture-docs-rewrite/ARCHITECTURE_LAYERS_ANALYSIS.md`)에 대한 검토본. 완성도 과장을 지적한 교정 문서.
- **`docs/refactoring/ARCHITECTURE_IMPLEMENTATION_REVIEW_2026-06-27.md`** — 직전 리팩토링 착수 계획서(날짜 단위). 이번 문서는 이 문서의 **후속(다음 배치) 문서**다 — 3~5절에서 그 문서의 각 항목을 재확인한다.
- **`app-android/src/test/java/com/worksoc/goaicoach/architecture/LayeringContractTest.kt`** (1,260줄, 41개 `@Test`) — 위 문서들이 정의한 경계를 실제로 강제하는 회귀 테스트.

### 2.1. 확정된 용어 (그대로 재사용)

```
Presentation / Game UX                         (7계층)
        ↓ (GameUiEvent)
App Service / Session Orchestration             (6계층)
        ↓ (lambda-injected controllers)
Game Domain  ←→  Middleware / Cache Domain      (5계층 ←→ 4계층)
        ↓                  ↓
Core Rules Domain    Engine Core API Domain     (3계층, 2계층)
                              ↓
                    Engine Runtime / Transport   (1계층)
```

사용자가 말한 두 축은 정확히 다음과 대응한다.

| 사용자 표현 | 기존 문서의 정식 명칭 | 대표 타입 | 위치 |
| --- | --- | --- | --- |
| "엔진 기능을 그대로 올려주는 연결 브릿지" | **2계층 Engine Core API Domain** | `EngineCoreApi` | `shared/.../EngineModels.kt` |
| (1계층, 브릿지의 실제 구현체) | **Engine Runtime / Transport** | `KataGoProcessEngineAdapter` | `engine-android/.../engine/android/` |
| "엔진 기능을 조합해서 기능 조합 운영하는 모듈" | **4계층 Middleware / Cache Domain** | `EngineSessionClient` | `app-android/.../application/engine/`, `application/analysis/`, `middleware/` |

주의: 4계층(Middleware)과 6계층(App Service / Session Orchestration)을 혼동하지 않는다. 4계층은 "엔진 기능의 조합"이고, 6계층은 "미들웨어 + 게임 규칙 + 영속화를 묶은 UI 유스케이스"(새 게임, 무르기, 자동대국 등)다 — 서로 다른 계층이며 각각 별도 경계 테스트가 있다(2.2절).

### 2.2. 경계를 강제하는 핵심 테스트 (발췌)

| 테스트 함수 | 강제하는 규칙 |
| --- | --- |
| `uiAndPresentationDoNotImportRawEngineCoreApi` | `ui/`·`presentation/`은 raw `EngineCoreApi`/`EngineAdapter`/`engine.android`를 직접 import 금지 — 반드시 미들웨어(4계층)를 거친다. |
| `matchPoliciesDoNotImportRawEngineCoreApi` | `match/`(5계층)도 raw `EngineCoreApi` 직접 import 금지 — application 레이어조차 작은 미들웨어 게이트웨이를 거쳐야 한다. |
| `positionAnalysisGatewayContractsStayKmpReadyAndTransportFree` | `middleware/PositionAnalysisGateway.kt` 등은 `android.`/`java.`/`org.json.` 등 플랫폼 import와 HTTP transport 심볼 금지 — "이식 가능한 계약"과 "실제 transport 구현"을 물리적으로 분리. |
| `engineOperationApplicationPoliciesStayPortable` | **`application/` 전체**(허용 목록 1개 파일 제외)가 `android.`/`androidx.`/`java.`/`org.json.`/`ui.`/`persistence.`/`engine.` import 금지 — 이 규칙은 하위 패키지 전체를 재귀 순회하므로, 새로 추가되는 `application/auth`, `application/premium` 같은 패키지도 **자동으로** 이 규칙의 적용을 받는다(7절에서 실제 검증 결과 확인). |

## 3. 2026-07-29 기준선 재확인

`docs/refactoring/ARCHITECTURE_IMPLEMENTATION_REVIEW_2026-06-27.md`가 남긴 기준선과 비교한다. 검증 명령: `make test` (성공).

| 항목 | 2026-06-27 | 2026-07-29 | 비고 |
| --- | ---: | ---: | --- |
| production Kotlin 파일 (`shared`+`engine-android`+`app-android/main`) | 178 | 204 | 온보딩/Firebase 인증(auth-onboarding), 프리미엄 영속화, 시각 리터치 등 신규 기능 추가분 |
| `app-android/application` 하위 패키지 | 17 | 19 | `auth`, `premium` 2개 신설 (7절 참고) |
| `app-android/application` production 파일 | 107 | 113 | |
| `shared/commonMain` production 파일 | 21 | 26 | 이번 세션 범위 밖(다른 세션에서 늘어난 파일 포함, 재확인 필요 항목 아님) |
| `engine-android` production 파일 | 10 | 10 | 변화 없음 |
| `GoCoachApp.kt` 줄 수 | 791 | 801 | `LayeringContractTest`의 `lineBudget=880` 이내 (여유 79줄) |
| `GoCoachApp.kt` 상태 훅 수 (`remember`/`mutableStateOf`/`LaunchedEffect`) | (미기록) | 46 | `stateHookBudget=47` 이내, **여유 1줄** — 매우 빠듯함 (아래 8.1 참고) |
| production wildcard import 수 | 다수(H-01 대상 9개 파일) | **1개** (`EngineResponsePanel.kt`) | H-01 사실상 완료, 잔여 1개 |

## 4. 2026-06-27 "시급도 높은 항목"(H-01~H-07) 완료 여부 재확인

전수 확인 결과 **7개 중 6개가 이미 완료**되었다(이 세션이 아니라 그 사이의 다른 작업에서). 남은 건 H-01의 잔여 파일 1개뿐이다.

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| H-01 wildcard import 제거 | 🟡 거의 완료 | production wildcard import가 `EngineResponsePanel.kt` 1건만 남음 (원래 9개 파일 대상) |
| H-02 `GoCoachApp.kt` controller wiring 정리 | ✅ 완료 | `ui/GoCoachControllerWiring.kt` 존재 (2026-07-18 생성) |
| H-03 `RuntimeEventApplication.kt` 로그 함수 분리 | ✅ 완료 | `RuntimeAiTurnEventApplication.kt` 분리됨, 본체 487→340줄 |
| H-04 `ScoreDisplayApplication.kt` 타입/포매터 분리 | ✅ 완료 | `ScoreDisplayModels.kt`, `ScoreDisplayFormatterApplication.kt` 존재, 본체 534→387줄 |
| H-05 `DebugReportBuilder.kt` section builder 분리 | ✅ 완료 | `DebugReportSections.kt` 존재, 본체 406→305줄 |
| H-06 `GoBoard.kt` 좌표 계산 순수 함수 테스트 | ✅ 완료 | `GoBoardCoordinateTest.kt` 존재 |
| H-07 문서 지표에 기준일 표기 | ✅ 완료 | `ARCHITECTURE.md`가 이미 "2026-06-28 기준" 형태로 수치에 기준일을 명시 |

## 5. "중장기 고도화 항목"(M-01~M-08) 진행 상황 재확인

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| M-01 `GameSessionStateHolder` shared 이전 | 🟡 1단계만 완료 | `gameSessionStateHolderStaysPlatformFreeForSharedMove` 계약 테스트는 있음. 실제 `shared` 모듈 이전은 미착수 — 여전히 `app-android/.../application/session/GameSessionStateHolder.kt`에 위치. |
| M-02 middleware KMP 물리 모듈 분리 | 🟡 1단계만 완료 | `positionAnalysisGatewayContractsStayKmpReadyAndTransportFree` 계약 테스트는 있음(2절 참고). 물리적으로는 여전히 `app-android/middleware`. |
| M-03 `RemoteEngineSessionClient` 최소 골격 | 🔴 미착수 | 저장소 전체에 `RemoteEngineSessionClient` 클래스/파일 없음. read-only position-analysis 원격 spike(`RemotePositionAnalysisGateway`)만 존재. |
| M-04 androidTest/Robolectric smoke coverage | 🟡 skeleton만 존재 | `app-android/src/androidTest/.../smoke/AppLaunchSmokeTest.kt` 1개만 존재(24줄). "첫 Codex low 단위"는 달성, 전체 목표(저장 세션/새 게임/이벤트 디스패치/보드 탭 경로)는 미달성. |
| M-05 `UiStrings.kt` 카탈로그 분리 | ✅ 사실상 완료(다른 방식으로) | 원래 제안은 "enum/provider vs 카탈로그 2파일 분리"였으나, 실제로는 **언어별 파일 분리**(`UiStringsKo/En/Ja/Zh.kt` + 베이스 `UiStrings.kt`)로 더 실용적으로 해결됨. 목표(문구 변경 비용 절감)는 달성. |
| M-06 engine protocol adapter 추가 분해 | 🔴 미착수 | `KataGoProcessEngineAdapter.kt` 구조 변화 없음(engine-android 파일 수 10개로 06-27과 동일). |
| M-07 cache/remote 일관성 정책 고도화 | 🔴 미착수 | 확인 범위 밖(엔진 세부 정책, 이번 검토에서 우선순위 낮음). |
| M-08 release/native packaging 운영 결정 | 🔴 미착수 | `make dev`/`make release`의 아티팩트 요구사항 표 문서화 안 됨. |

## 6. 신규 발견 — 이번 세션에 추가된 auth/premium 도메인의 계층 적합성 검증

이번 사용자 요청의 핵심("도메인 분리 관점을 더 철저히")에 맞춰, 최근 추가된 `application/auth`, `application/premium` 패키지가 기존 원칙을 지키는지 직접 검증했다.

- **포트/구현체 분리 원칙 준수 확인**: `application/auth/AuthClientPort.kt`(순수 인터페이스) + `ui/AndroidAuthClient.kt`(Firebase 구현체), `application/premium/PremiumStatePorts.kt`(순수 인터페이스) + `persistence/PremiumStateStore.kt`(SharedPreferences 구현체) — 이는 정확히 2계층(Engine Core API)/1계층(Runtime) 분리와 같은 패턴이다. **`engineOperationApplicationPoliciesStayPortable` 테스트가 `application/` 전체를 재귀 순회하므로, 이 두 신규 패키지도 이미 자동으로 검증되고 있다** (별도 테스트 추가 없이 `make test` 통과로 확인됨).
- **미정리 컨벤션 발견**: 기존에는 사소한 Android 플랫폼 포트(`AndroidClipboardPort`, `AndroidUserNoticePort`)가 `ui/AndroidPlatformPorts.kt` 한 파일에 모여 있었다. 이번에 추가한 `AndroidAuthClient`는 Firebase Auth라는 무거운 외부 SDK를 쓰기 때문에 의도적으로 **별도 파일**로 뺐는데, 이 판단 기준(가벼운 포트는 공용 파일에, 외부 SDK가 붙는 포트는 전용 파일에)이 **문서화되어 있지 않다.** 앞으로 Google 로그인, Play Billing 등 외부 SDK 포트가 계속 늘어날 것이므로, 이 컨벤션을 명시적으로 문서화할 필요가 있다 (8.2절 작업 항목).
- **결론**: 신규 도메인은 기존 원칙을 위반하지 않는다. 다만 그 판단이 "테스트가 우연히 넓게 잡혀 있어서 통과했다"이지 "의도적으로 설계해서 통과했다"가 아니라는 점 — 즉 **이 프로젝트의 도메인 분리 원칙이 엔진 외의 신규 기능에도 적용된다는 것을 문서가 아직 명시하지 않는다**는 게 이번 재검토의 핵심 발견이다.

## 7. 이번 착수 계획 — 다음 배치 우선순위

6절의 발견을 반영해, 이번 배치는 "엔진에 한정됐던 도메인 분리 문서를 엔진 외 도메인까지 일반화"하는 데 집중한다. 기존 H-*/M-* 넘버링을 이어간다 (H-08부터).

### H-08. `ARCHITECTURE.md`에 "포트/어댑터 분리 원칙"을 엔진 외 도메인까지 일반화하는 절 추가

- 추천 엔진: Codex (low)
- 파일 범위: `docs/ARCHITECTURE.md` 1개 파일만
- 작업 지시: 4계층 설명 아래에, "이 포트/어댑터 분리 원칙은 엔진에만 적용되는 게 아니라 `application/` 전체의 표준 패턴"이라는 절을 추가한다. `application/auth`(`AuthClientPort`), `application/premium`(`PremiumStateStorePort`)을 예시로 인용한다.
- 완료 기준: 문서에 "엔진이 아닌 도메인도 같은 원칙을 따른다"는 문장과 최소 2개 이상의 코드 예시가 추가된다.
- 검증: 문서 리뷰만 (코드 변경 없음)
- 금지: 7계층 구조 자체를 바꾸지 않는다. 기존 엔진 계층 설명 문구를 지우지 않는다.

### H-09. Android SDK-backed 포트 파일 컨벤션 명문화

- 추천 엔진: Codex (low)
- 파일 범위: `docs/ARCHITECTURE.md` 또는 `docs/DOCS_INDEX.md` 1개 파일
- 작업 지시: "가벼운 플랫폼 포트(클립보드, 토스트 등)는 `ui/AndroidPlatformPorts.kt`에 모으고, 외부 SDK(Firebase, Google Sign-In, Play Billing 등)에 의존하는 포트는 전용 파일로 분리한다"는 규칙을 문서화한다. `AndroidAuthClient.kt`를 첫 사례로 인용.
- 완료 기준: 향후 Google 로그인/Play Billing 어댑터를 추가할 때 참고할 명시적 규칙 한 단락이 생긴다.
- 검증: 문서 리뷰만
- 금지: 기존 `AndroidPlatformPorts.kt` 파일을 지금 쪼개거나 합치지 않는다 — 규칙만 먼저 세운다.

### H-10. wildcard import 완전 제거 (H-01 마무리)

- 추천 엔진: Codex (low)
- 파일 범위: `app-android/src/main/java/com/worksoc/goaicoach/ui/EngineResponsePanel.kt` 1개
- 작업 지시: 남은 wildcard import 1건을 명시 import로 교체한다. 로직 변경 없음.
- 완료 기준: `rg "import .*\.\*" app-android/src/main/java shared/src/commonMain/kotlin engine-android/src/main/java`가 0건을 반환한다.
- 검증: 위 명령 + `make test`
- 금지: 이 파일의 다른 로직을 같이 건드리지 않는다.

### H-11. `application/auth`/`application/premium`을 위한 명시적 경계 테스트 추가

- 추천 엔진: Codex (low)
- 파일 범위: `app-android/src/test/java/com/worksoc/goaicoach/architecture/LayeringContractTest.kt` 1개
- 작업 지시: 현재는 `engineOperationApplicationPoliciesStayPortable`의 넓은 재귀 규칙에 "우연히" 포함되어 검증되고 있을 뿐, `auth`/`premium`을 지목하는 전용 테스트가 없다. `ui`/`persistence`가 `AuthClientPort`/`PremiumStateStorePort`의 실제 구현체(`AndroidAuthClient`, `PremiumStateStore`)를 통해서만 접근하고, `application/auth`·`application/premium`이 플랫폼 import 없이 유지되는지 확인하는 이름 붙은 테스트를 1개 추가한다(기존 `uiAndPresentationDoNotImportRawEngineCoreApi`와 대구를 이루는 스타일).
- 완료 기준: 새 테스트 함수가 실패하도록 일부러 깨뜨려보고(로컬에서만, 커밋하지 않음) 통과 확인 후 되돌리는 방식으로 실효성 검증.
- 검증: 신규 테스트, `make test`
- 금지: 기존 `engineOperationApplicationPoliciesStayPortable` 테스트의 범위/allowlist를 좁히지 않는다(그 테스트는 그대로 두고 이름 붙은 테스트를 "추가"만 한다).

### M-09 (신규). auth/premium 이후 늘어날 "외부 계정/결제 도메인"의 6계층 배치 가이드

- 추천 엔진: Codex (medium)
- 첫 Codex (low) 단위: `premium-mode/README.md`·`auth-onboarding/README.md`의 로드맵(Step 3/4: 실 광고, 실 결제, Google 로그인)이 각각 어느 계층(App Service vs Middleware)에 놓일지 표로 정리한다 — 예를 들어 "Play Billing 구매 검증"은 Middleware(외부 결제 서버와의 신뢰도/캐시 조율)에 가깝고, "구매 완료 후 UI 반영"은 App Service에 가깝다.
- 전체 목표: 향후 Google 로그인/Play Billing 구현 시 어느 파일이 어느 계층에 속하는지 매번 재논의하지 않도록 미리 배치를 정해둔다.
- 완료 기준: `auth-onboarding/README.md`·`premium-mode/README.md`에 "이 Step은 몇 계층에 해당하는지" 표기가 추가된다.
- 검증: 문서 리뷰만
- 금지: 이번 배치에서 실제 Google 로그인/Play Billing 코드를 작성하지 않는다.

## 8. 실행 규칙 (기존 관례 그대로 승계)

- 한 커밋/PR에는 원칙적으로 항목 하나만 넣는다.
- 동작 변경이 목표가 아닌 항목(H-08, H-09, M-09)은 문서만 바꾸고 코드를 바꾸지 않는다.
- 코드가 바뀌는 항목(H-10, H-11)은 변경 전후 `make test`를 실행한다.
- `LayeringContractTest`의 downward-ratchet 예산(`lineBudget`, `stateHookBudget`)을 건드리는 작업은 이 문서의 범위 밖이다 — 별도 계획서에서 다룬다.

### 8.1. 특히 주의할 점 — `GoCoachApp.kt` 상태 훅 예산

3절에서 확인했듯 `stateHookBudget`(47) 대비 실사용이 46으로 **여유가 1줄뿐**이다. 이번 배치(H-08~H-11, M-09)는 모두 `GoCoachApp.kt`를 건드리지 않도록 의도적으로 설계했다 — 이 파일에 손대는 리팩토링은 상태 훅을 늘리지 않는 방법(예: 초기값 계산식 재사용, `remember` 없는 무상태 어댑터)을 반드시 먼저 검토해야 한다.

### 8.2. 권장 작업 순서

1. H-08 (문서, 무위험)
2. H-09 (문서, 무위험)
3. H-10 (wildcard import 1건, 무위험)
4. H-11 (경계 테스트 추가)
5. M-09 첫 단위 (문서, 무위험)

문서 항목을 코드 항목보다 먼저 배치한 이유: 이번 배치의 핵심 요청이 "재검토와 계획 문서화"이므로, 코드를 건드리기 전에 원칙을 문서로 먼저 고정해 다음 세션(또는 다른 엔진)이 같은 재논의를 반복하지 않게 한다.

## 진행 로그

- **260729 23h19m** — H-08 완료. `docs/ARCHITECTURE.md`의 4계층 설명 아래에 "포트/어댑터 분리 원칙은 엔진에만 적용되지 않는다" 절을 추가하고, `application/auth`/`application/premium` 예시를 인용했다. 코드 변경 없음, 7계층 구조·기존 엔진 설명 문구는 그대로 유지.
- **260729 23h31m** — H-09 완료. 같은 절 바로 아래에 "어댑터 구현체를 어느 파일에 둘지 정하는 기준"을 추가 — 가벼운 플랫폼 포트는 `ui/AndroidPlatformPorts.kt`에, 외부 SDK 의존 포트(`AndroidAuthClient.kt` 등)는 전용 파일로 분리한다는 규칙을 명문화했다. 코드 변경 없음.
- **260729 23h42m** — H-10 완료. `EngineResponsePanel.kt`의 남은 wildcard import(`com.worksoc.goaicoach.shared.*`)를 실제 사용 중인 9개 심볼(`SideAnalysisDebugText`, `StoneColor`, `buildSideAnalysisDebugState`, `extractAiSelectedRank`, `extractScoreLead`, `extractSearchedCount`, `extractVisitDiagnostics`, `formatCandidateLineCompact`, `formatOneDecimal`)의 명시 import로 교체했다. 로직 변경 없음. `rg "import .*\.\*"`가 production 코드 전체에서 0건, `make test` 통과 확인.
- **260729 23h58m** — H-11 완료. `LayeringContractTest.kt`에 `authAndPremiumApplicationPackagesStayPlatformFree` 테스트를 추가 — `application/auth`·`application/premium`이 `android.`/`androidx.`/`java.`/`org.json.`/`ui.`/`persistence.`/`engine.`를 import하지 않는지 `forbiddenReferenceOffenders`(와일드카드+bare 사용, 정규화된 인라인 참조까지 잡는 기존 탐지기)로 검증한다. `uiAndPresentationDoNotImportRawEngineCoreApi`와 대구를 이루는 스타일로 작성. `make test` 통과(신규 테스트 포함).

## 9. 참고 — 이번 문서가 답하지 않는 것

- M-01(shared 이전)·M-02(middleware 물리 분리)·M-03(RemoteEngineSessionClient)의 전체 구현은 이번 배치 범위 밖이다. 이 문서는 그 항목들의 **진행 상황만 재확인**했다(5절) — 실제 착수는 별도의, 더 큰 착수 계획서가 필요하다(그때도 이 문서의 타임스탬프 관례를 따른다).
- 엔진 프로토콜 어댑터 추가 분해(M-06), 캐시/원격 정책 고도화(M-07), release 패키징(M-08)은 이번 재검토에서 우선순위가 낮다고 판단해 다음 배치로 미뤘다.
