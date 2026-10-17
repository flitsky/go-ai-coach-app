# 5계층 — Application / Domain

go-ai-coach에서 이 계층이 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「5계층 — Application / Domain」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「5계층」 절(조립 루트 항목은 `COMPOSITION_ROOT.md`로)과 「알려진 갭」·「고도화 로드맵」의 5계층 항목을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 계층 배정

아래 블록은 `ContractSymbols.kt`의 `LAYER_5_PACKAGES`를 그대로 옮긴 **사본**이다(접두 `com.worksoc.goaicoach.` 생략). 정본은 코드 쪽이고, `LayerDocContractTest`가 둘이 양방향으로 같은지 본다 — 패키지를 새로 만들거나 옮기면 코드 목록을 고치고, 빨개지면 이 블록을 따라 고친다.

<!-- layer-packages: LAYER_5_PACKAGES -->
```text
application.autoai
application.concurrency
application.contract
application.debugreport
application.diagnostic
application.endgame
application.gamehistory
application.guide
application.humanmove
application.movereview
application.orchestration
application.preferences
application.prompt
application.runtime
application.savedgame
application.score
application.session
application.startgame
application.time
application.topmoves
application.undo
match
shared.content
shared.diagnostic
shared.domain
shared.playstyle
shared.policy
shared.scoring
```

## 지금 위치

**위치**:
- `core/domain/src/commonMain/kotlin/com/worksoc/goaicoach/shared/domain/` · `core/enginecontract/src/commonMain/kotlin/com/worksoc/goaicoach/shared/scoring/` · `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/policy/`(2026-09-24 refactor backlog #24로 하위 패키지 분리, 2026-09-28 #49로 `domain`은 `:core:domain`·`scoring`은 `:core:enginecontract` 모듈로 — `GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 「Gradle 모듈 지도」) — 순수 바둑 규칙(KMP `commonMain`). `domain/BoardModels.kt`, `domain/BoardRules.kt`, `domain/LegalMoveGenerator.kt`, `scoring/BoardScorer.kt`(+`scoring/BoardAreaScorer.kt`/`scoring/BoardTerritoryScorer.kt`, 두 계가기가 함께 쓰는 빈 점 소유 판정 `scoring/BoardRegionAnalyzer.kt` — 2026-09-25 refactor backlog #38), `domain/DeadStoneDetector.kt`/`domain/DeadStoneCleaner.kt`, `policy/EndgameScoreSelector.kt`, `domain/GameStateReplayer.kt`, `scoring/ScoreTimeline.kt` 등
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/diagnostic/DiagnosticEventModel.kt` — 진단 이벤트 순수 모델(`DiagnosticEvent`/`DiagnosticSeverity`). 실제 기록은 4계층 어댑터가 한다(2026-09-23 매핑 편입)
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/match/MatchReferee.kt`, `AiMoveSelectionPolicy.kt`, `MatchPolicy.kt`, `MatchTurnOrchestration.kt` — 대국 정책(참여 주체, 턴 권한, AI 레벨링). **260804 경로 정정**: 이전엔 `app-android/.../match/`였으나 "도메인별 파일 분리" 작업(커밋 `5278c12`)으로 `shared`로 이동했다 — 순수 로직이라 KMP 이식 대상이었다. 지금 `app-android`에는 `match/` 디렉터리가 없다.
- `shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/` — 유스케이스 오케스트레이션. **2026-09-25 기준 하위 디렉터리 33개, `package` 선언으로는 37개**(#85의 `profile` 포함)(`auth`·`premium`·`engine`이 하위 패키지로 갈린다). 그중 **5계층은 21개**다: `autoai`·`concurrency`·`contract`·`debugreport`·`diagnostic`·`endgame`·`gamehistory`·`guide`·`humanmove`·`movereview`·`orchestration`·`preferences`·`prompt`·`runtime`·`savedgame`·`score`·`session`·`startgame`·`time`·`topmoves`·`undo`. 나머지 15개는 3계층(`engine`·`engine.operation`·`safety`·`analysis`·`cacheoptimization` 5개), 4계층 α(`auth.port`·`premium.port` 2개), 6계층(`auth.state`·`premium.state`·`premium.app`·`attendance`·`consumable`·`botcharacter`·`lifecycle`·`device`·`profile` 9개)이다 — 각 계층 파일 참고. 배정은 패키지 단위라, 5계층 패키지 안에 선언된 4계층 α 포트(`gamehistory`·`diagnostic`의 `*Port` 등)는 파일 단위 예외로 본다. ⚠️ 2026-09-23판의 *"29개 서브패키지"* 는 그날 기준으로 맞았다 — 그 뒤 2026-09-24(refactor backlog #32)에 `cacheoptimization`·`contract`·`orchestration`이 새로 생겨 32개가 됐다. **260816**: 웨이브 1~6으로 `app-android`에서 `shared`로 물리 이전 완료(영구 예외 1개, 아래 "핵심 갭" 참고). `session/GameSessionStateHolder.kt`가 세션 상태의 단일 source of truth
- 조립 루트(`ui/shell/GoCoachApp.kt`와 루트 패키지)는 계층 밖이다 — `COMPOSITION_ROOT.md`로 옮겼다(2026-10-17).

**260814 정정**: 이 절에 한때 "기능별 접근 정책 판정"(`FeatureAccessPolicy`)을 5계층으로 적어뒀으나 오기였다 — `PremiumState`(6계층)를 파라미터로 받는 함수는 5계층이 아니라 6계층 소속이다(5계층은 6계층을 몰라야 하므로). 실제 구현은 `LAYER_6_SESSION_CONTINUITY.md` 참고.

**2026-09-25 재분류 — 엔타이틀먼트 클러스터는 6계층이다(refactor backlog #84)**: 2026-09-24까지 이 절은 `application/` 하위 패키지를 한데 적고, 6계층 위치에는 auth/premium의 상태만 적었다. 그 매핑으로 재면 5계층 파일 8개가 6계층 `premium.state`를 **import 16줄**로 참조했다(`premium.app` 4파일 8줄, `consumable` 2파일 6줄, `attendance/AttendanceRewardPolicy.kt`·`lifecycle/ReleaseResetPolicy.kt` 각 1줄. inline FQN은 0이다). 16줄 모두 6계층 상태를 받아 다음 값을 계산·저장하거나 6계층 값(`FeatureId`)을 필드로 두는 코드였다. 260814 기준(*"`PremiumState`를 받으면 6계층"*)대로라면 처음부터 6계층이었던 셈이다. 코드 KDoc도 이미 둘로 갈라져 있었다. 같은 패키지 안에서 상태·정책·순수 판정은 *"6계층"*(12줄)으로, 저장소를 도는 흐름은 *"5계층(App Service)"*(11줄)으로 적혀 있었다. 뒤쪽의 *App Service*는 2026-06-27판 옛 6계층 이름(App Service/Session Orchestration)이 역할 이름으로 남은 것이다. 그래서 **규칙은 그대로 두고 매핑을 고쳤다.** `premium.app`·`attendance`·`consumable`·`botcharacter`·`lifecycle`·`device` 6개 패키지(28파일)를 통째로 6계층으로 옮겨 적었다. **코드 이동·`package` 선언 변경·동작 변경은 0이다.** 안의 `*Ports.kt` 4개는 종전대로 4계층 α다.
- **재분류 뒤**: 5→6 import는 **0줄**, 6→5는 **13줄**(7파일 — `botcharacter`→`shared.policy`·`match` 7줄, `premium.app`→`shared.diagnostic` 6줄)이다. 6→5는 원칙이 허용한 방향이다(6계층은 5계층의 도메인 개념을 참조할 수 있다). 5계층 본체가 클러스터를 참조하는 코드는 재분류 전에도 0이었다(KDoc 1줄 `time/AppClock.kt`뿐). 클러스터는 5계층 안에서 닫힌 섬이었다. 두 6계층 상태 패키지(`premium.state`·`auth.state`)에는 import가 한 줄도 없고, 동작 선언(`var`·포트 필드·IO)도 없다. 두 상태를 실제로 쥔 것은 7계층이다(`ui/shell/GoCoachApp.kt`의 `premiumState`, `ui/settings/SettingsScreen.kt`의 `authState`).
- **기각한 대안**: 규칙을 *"5계층도 6계층의 값과 순수 규칙은 안다"* 로 넓히는 안이다. 큰 그림이 위 계층의 값을 아는 것을 계약과 그 구현체에만 허용한 근거(#73 — *"계약은 싣는 값의 모양을 알아야 계약이다"*)가 유스케이스에는 성립하지 않는다. 6계층에 동작 선언이 하나도 없으니 *"동작만 금지"* 는 오늘 아무것도 막지 못한다. 5·6계층이 서로의 값을 알게 돼 둘을 모듈로 가를 여지도 없어진다. `premium.app`만 옮기는 안(백로그 문면)도 기각했다. 값 간선 네 파일이 남고, `attendance/AttendanceRewardApplication.kt`→`runPremiumFeatureClaim`이 5→6 동작 간선(포트 IO)으로 오히려 나빠진다.
- **`#39`와의 관계**: 백로그 `#39` 제목의 첫 절 *"5→6 방향 뒤집기"* 는 이 재분류로 코드 없이 닫혔다(간선 0). 둘째 절 *"과금 게이트 판정 단일화"* 는 남는다. 다만 7계층 밖의 게이트 판정(`FeatureAccessPolicy`, `decideConsumableSpend`, `PremiumState.isSubscriptionActive`)이 이제 전부 6계층 한 곳에 있다. 그래서 할 일은 *"6계층 안에서 하나로"* 와 7계층 쪽(`ui/monetization/PremiumUiState.kt`·`ui/play/GamePlaySection.kt`)으로 좁혀졌다.
- **가드**: `LayeringContractTest.layerFivePackagesDoNotReferenceLayerSixPackages`가 이 경계를 처음으로 코드에서 본다. 5계층 패키지를 정확히 선언한 프로덕션 파일(2026-09-25 기준 137개 — :shared 세 소스셋과 app-android의 영구 예외 하나)이 6계층 패키지를 import하거나 inline FQN으로 쓰면 빨갛다. 계층 배정 목록은 `ContractSymbols`의 `LAYER_5_PACKAGES`(27)·`LAYER_6_PACKAGES`(8)·`SHARED_PACKAGES_BELOW_LAYER_5`(9 — 2·3계층과 4계층 α)에 있고, 이 문서의 매핑과 같다. 매핑을 바꾸면 두 곳을 함께 고친다. `everySharedPackageIsAssignedToExactlyOneLayerList`는 :shared의 패키지가 어느 목록에도 없거나, 목록에 사라진 패키지가 남거나, 두 번 적히면 빨개진다. 가드가 못 보는 것도 있다. 4계층 포트(`premium.port`·`auth.port`)가 돌려준 6계층 값이 import 없이 타입 추론으로만 흐르는 경우다. 오늘 그 두 포트를 쥐는 5계층 파일은 0이다.
- ⚠️ **남는 위험**: 훗날 5계층 대국 흐름이 `botcharacter` 카탈로그(상대 정의)를 필요로 하면, 그때 카탈로그만 5계층으로 내려야 한다. 그 순간을 위 가드가 알려 준다.

**재편 여부**: 기존 3계층(Core Rules)+5계층(Game Domain)+6계층(App Service/Session Orchestration)을 하나로 통합. 순수 규칙과 오케스트레이션은 성격이 다르지만 "이 앱만의 것"이라는 공통점으로 묶었다 — `ARCHITECTURE.md`의 5계층 정의를 따른다. 위 3계층(엔진 서비스)과 4계층(외부 연동 서비스)을 동등하게 소비한다.

**핵심 갭**: 예외 1개만 남았다 — `app-android/.../application/diagnostic/LocalFileDiagnosticEventExternalSink.kt`(`java.io.File` 직접 사용)는 포트/어댑터 분리 원칙에 따라 영구히 `app-android`에 잔류(포트 `DiagnosticEventExternalSinkPort`는 `shared`로 이전됨). `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`이 이 예외 하나만 명시적으로 허용하고 나머지는 전부 `shared`에서 이식성을 상시 검증한다.

## 지키는 테스트

- `LayeringContractTest.layerFivePackagesDoNotReferenceLayerSixPackages` — 5계층은 6계층을 모른다.
- `LayeringContractTest.everySharedPackageIsAssignedToExactlyOneLayerList` — `:shared` 패키지마다 계층이 하나.
- `LayeringContractTest.sharedCommonMainAvoidsImplicitlyImportedJvmApis` — commonMain이 JVM API에 기대지 않는다.
- `DomainModuleBuildScriptContractTest` — `:core:domain`은 의존이 없다. `PackageCycleRatchetTest` — 패키지 순환 기준선.

## 갭과 남은 일

### 닫힌 것

5. ~~**5계층 — `GameSessionStateHolder` → `:shared` KMP 이전.**~~ — **완료(260816)**. 이식 가능성(플랫폼 비종속)은 `engineOperationApplicationPoliciesStayPortable`이 사전에 검증했고, `GameSessionCoreState`/`GameSessionController`가 `autoai/engine/humanmove/savedgame/score/startgame/topmoves/undo/debugreport/analysis/movereview/preferences` 12개 서브패키지를 전이적으로 끌어들여 "일부만 이전"이 불가능하다는 예측대로 `application/`(124개 프로덕션 파일) 전체를 한 단위로 이전했다.
   - ~~**(1) 스파이크**~~ — **완료(260816)**. `application/safety/EngineTurnWatchdog.kt`(팬인/팬아웃 최소, `SearchTimeLimit` 하나만 의존)를 `shared/commonMain`으로, 테스트를 `shared/commonTest`로 옮겨 전체 절차를 실제로 검증했다. 확인된 것: (a) 패키지명(`com.worksoc.goaicoach.application.safety`)은 그대로 유지 가능 — `:shared` 안에 이미 `match/`처럼 비-`shared` 패키지가 있어 모듈 경계와 패키지명이 독립적이므로, app-android 호출부(`ui/play/GamePlaySection.kt`)의 import 문은 **한 줄도 안 바뀜**. (b) `internal fun` 2개를 public으로 바꿔야 했다 — 예상대로. (c) **새로 발견한 것**: 테스트도 함께 옮겨야 하고, `shared/commonTest`는 `org.junit.*`가 아니라 `kotlin.test.*`(멀티플랫폼 API)를 쓰는 게 기존 컨벤션이라 import 전환이 필요했다. (d) `:shared`가 `androidMain` 없이도 문제없이 받아들였고, `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`까지 깨끗이 통과.
   - ~~**(2) 본 이전**~~ — **완료(웨이브 1~6, 260816)**. **계획 수립 중 정정한 예상**: "`internal`→기본 가시성 확대는 실제 app-android 호출부가 있는 지점만"이라고 예상했었는데, 스파이크(파일 1개) 결과를 트리 전체로 낙관적으로 외삽한 것이었다 — 실측하니 509개 중 424개(83%)가 이미 `ui/`/`persistence/`/`middleware/`/테스트에서 참조되고 있어 이동 순서와 무관하게 public 전환이 필요했다. 도중에 발견된 함정들 — `LayeringContractTest.kt`의 하드코딩 경로 문제(`applicationFile()` 헬퍼로 해결), Kotlin 크로스모듈 스마트캐스트 제약(로컬 val 캡처로 해결), `middleware/`처럼 `application/` 트리 밖 패키지를 참조하는 파일(포터빌리티 확인 후 함께 이전), 테스트가 `persistence/`·`ui/` 같은 비이식 레이어를 직접 테스트하는 경우(영구 잔류로 원위치), `kotlin.test.assertNotNull`/`assertFailsWith`가 `Unit`이 아니라 값을 반환해 표현식-바디 테스트 함수의 추론 반환 타입을 깨는 경우(명시적 `: Unit` 반환 타입으로 해결) — 도 전부 처리했다. 웨이브별 실행 기록 원문은 2026-08-17 문서 정리로 제거됐다(`docs/DOCS_INDEX.md` "문서 보존 정책" 참고, 필요시 `git log`로 복원).
   - ~~**(3) `LayeringContractTest.kt` 갱신**~~ — **완료(260816)**. `engineOperationApplicationPoliciesStayPortable`의 스캔 대상을 `app-android/.../application`(이전 완료 후 사실상 공집합)에서 실제 파일들이 있는 `shared/src/commonMain/.../application`으로 변경. `ui.`/`persistence.`/`engine.`(composition-root) 임포트 금지 체크는 제거 — `shared`가 `app-android`에 대한 Gradle 의존성 자체가 없어 어기면 텍스트 검사 없이도 그냥 컴파일 에러가 난다. `android.`/`androidx.`/`java.`/`org.json.` 금지 체크는 유지 — `shared`의 `androidTarget`은 이 API들에 실제 접근 가능해서 컴파일은 통과하지만 iOS 등 다른 KMP 타깃을 조용히 깨뜨릴 수 있기 때문. ⚠️ **다만 이때 나머지 넷의 스캔 경로는 따라오지 않았다** — 그 결과가 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 「알려진 갭」의 "죽은 안전망" 항목이고, 2026-09-23 `0c33d32c`로 되살렸다.
   - ~~**(4) 회귀 확인**~~ — **완료(260816, 2회)**. `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt`를 웨이브 5(키스톤 이동 직후)와 웨이브 6(세션 배선에 손대는 마지막 웨이브) 완료 직후 각각 에뮬레이터(`emulator-5554`)에서 `connectedDebugAndroidTest`로 재확인 — 둘 다 2/2 green.
   - ~~**(5) 플랫폼 누수 회귀 복구**~~ — **완료(260824)**. (1)(d)에서 확인했던 "iOS 컴파일 깨끗이 통과"는 그 뒤 어느 시점에 깨져 있었다 — `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`가 에러 49개. iOS 타깃은 `-PenableIosTargets=true`로만 켜지므로 평소 안드로이드 빌드/테스트는 계속 그린이었고, 아무도 모르는 채 누적됐다. **원인은 (3)에서 유지하기로 한 텍스트 검사의 사각지대**: `import java.`를 막아도 `java.lang.*`은 JVM 자동 임포트라 `System.currentTimeMillis()`가 import 한 줄 없이 통과한다(`kotlin.synchronized`도 동일). 실제 내역은 시간 39건(20개 파일) + `synchronized` 4건 + `Dispatchers.IO` 1건이었다.
     - **시간(39건)**: `application/time/AppClock.kt`의 `currentEpochMillis()` 하나로 모았다. 호출부가 이미 갖고 있던 `nowMillis` 주입 시임(기본값 파라미터/람다)은 그대로 두고 그 **기본값**만 이 함수를 거치게 했으므로 테스트가 시간을 고정하는 방법은 전과 동일하다. 구현은 stdlib `kotlin.time.Clock`이라 새 의존성이 아니다. 경과 시간 측정(`System.nanoTime()`)은 `kotlin.time.TimeSource.Monotonic`으로 바꿨다.
     - **`synchronized`(4건) / `Dispatchers.IO`(1건)**: 이 둘은 진짜로 플랫폼마다 답이 다르다 — `expect`/`actual` 2개로 처리하고 `androidMain`/`iosMain` 소스셋을 신설했다. `application/concurrency/SharedLock.kt`(android=모니터 락, ios=`NSLock`), `application/engine/EngineIoDispatcher.kt`(android=`Dispatchers.IO`, ios=`newFixedThreadPoolContext(8, "engine-io")`). **안드로이드 런타임 동작은 전부 동일하게 유지**했다.
     - **coroutines 업그레이드는 답이 아니었다(260824 실측)**: 최신 안정판 1.11.0으로 올려서 확인해도 네이티브에는 여전히 `internal` 선언뿐이라 iosMain에서 `Dispatchers.IO`에 접근할 수 없다. `Dispatchers.Default.limitedParallelism(n)`도 답이 아니다(Default의 스레드를 나눠 쓰는 뷰일 뿐 스레드가 늘지 않아 블로킹 문제가 그대로다). **업그레이드 없이 1.8.0에서 해결됐다.** 확인 과정에서 두 군데(`shared`, `engine-android`)에 하드코딩돼 있던 `1.8.0`은 버전 카탈로그(`kotlinxCoroutines`)로 옮겨 놨다.
     - **재발 방지**: `LayeringContractTest.sharedCommonMainAvoidsImplicitlyImportedJvmApis` 신설 — import 문이 아니라 `System.`/`System::`/`Thread.`/`Runtime.`/`synchronized(` **이름 자체**를 `shared/commonMain` 전체에서 막는다. 기본 테스트 루프에 포함되므로 iOS 타깃을 켜지 않아도 걸린다.

- ~~`GameSessionStateHolder`(5계층)는 여전히 `app-android`에 있다~~ — **260816 해소**. `application/safety/` 스파이크로 이전 절차(물리 이동·`internal`→public·JUnit→kotlin.test·양쪽 모듈 컴파일·iOS 타깃 컴파일까지)를 먼저 검증한 뒤, 같은 날 웨이브 1~6으로 `application/` 트리 124개 프로덕션 파일 전부(영구 예외 1개 제외)를 `shared`로 물리 이전 완료. `:shared`/`:app-android` 컴파일 + `make test` 전체 그린, `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 에뮬레이터 실기 재확인도 통과 — 상세는 위 옛 로드맵 5번 참고.
