# #40(+#110) 설계 — 판·화면 위치·엔진을 "액티비티보다 오래, 프로세스만큼" 사는 소유자로

> ⏸️ **보류된 일감의 설계 초안이다 — 확정 설계가 아니다**(2026-09-29).
> 리팩토링 백로그 `#40`(+`#110`)을 위해 쓴 초안이고, **비판 두 갈래(수명·동시성 / 가드·iOS)를 돌리기 전에 멈췄다**.
> 멈춘 이유는 사용자 결정(2026-09-29 — *"절반 이상 수행된 게 아니라면 종료하고 보류"*)이다. 그때 구현은 0%였다.
>
> - **이미 쓰인 몫**: `#110`이 이 초안의 **C1·C3**(프로세스 수명 엔진 소유자 `GoCoachProcessRuntime`과 KataGo 수 세기 계기 테스트)만 가져가 구현했다. 결과는 백로그 완료 표의 `#110` 줄이 정본이다.
> - **남은 몫**: C2·C4·C4b·C5·C6(판과 화면 위치 보존 — `RetainedGoCoachSession`)은 `#40`을 다시 열 때의 출발점이다. 다시 열면 **먼저 비판을 돌린다**.
> - **이 초안에서 확인된 사실 — 다시 조사하지 않아도 된다**: ⓐ `ViewModel`은 「액티비티를 유지하지 않음」에서 `onCleared()`된다(`androidx.activity 1.10.1` 바이트코드 확인, §0.1). 그래서 카드의 수단으로는 카드의 인수 기준을 통과할 수 없다. ⓑ 매니페스트 `configChanges`가 구성 변경 재생성을 거의 다 막는다. ⓒ 인접 결함 셋이 있다(§1.3). 1회권 원장이 컴포지션에만 있는 것은 `#44`의 몫이다.
> - 백로그 규칙대로 **흡수되면 지운다**(git이 보관한다 — `docs/DOCS_INDEX.md` 「문서 보존 정책」).


기준: `main` `df307336` (2026-09-29). 읽기 전용 조사 결과이며, 저장소는 한 줄도 바꾸지 않았다.
범위: 리팩토링 백로그 `#40`(ViewModel 얇은 래퍼)과 `#110`(액티비티가 닫혀도 KataGo가 남고 재생성마다 쌓인다)을 한 설계로 합친다.

---

## 0. 결론 먼저

### 0.1 카드의 수단(ViewModel)으로는 카드의 인수 기준을 통과할 수 없다

- 인수 기준은 「액티비티를 유지하지 않음(Don't keep activities, 이하 DKA)」 ON → 20수 → 홈 → 복귀 시 판과 화면 위치가 살아 있는 것이다.
- DKA는 화면을 떠나는 순간 액티비티를 **설정 변경이 아닌 이유로** 파괴한다. `isFinishing=false`, `isChangingConfigurations=false`이고, 복귀하면 저장된 `Bundle`로 **새 인스턴스**를 만든다.
- `androidx.activity:activity:1.10.1`(이 앱이 쓰는 버전)의 `ComponentActivity`는 `ON_DESTROY`에서 `isChangingConfigurations()`가 거짓이면 `ViewModelStore.clear()`를 부른다. Gradle 캐시의 aar를 `javap`로 열어 바이트코드로 확인했다(`ComponentActivity._init_$lambda$3`: `ON_DESTROY` → `isChangingConfigurations` → `ifne` → `getViewModelStore().clear()`).
- 그래서 **ViewModel은 DKA에서 `onCleared()`되고 함께 죽는다.** ViewModel이 살아남는 것은 설정 변경뿐이다. 그런데 이 앱은 매니페스트의 `configChanges` 10개로 설정 변경 재생성을 이미 거의 다 막고 있다(함정 41, 진단서 §3.2). 즉 ViewModel이 지켜 줄 수 있는 경우는 이미 막혀 있고, 인수 기준이 요구하는 경우는 지켜 주지 못한다.
- ViewModel에 판을 넣고 DKA 대신 `ActivityScenario.recreate()`로 검사하면 **초록이 나온다.** `recreate()`는 설정 변경 경로라 `ViewModelStore`가 남기 때문이다. 이것이 함정 67과 같은 모양, 곧 "그럴듯한 설계를 틀린 시험이 통과시키는" 함정이다(7절 참고).

### 0.2 제안(조정안) — ViewModel을 두지 않고, 프로세스 수명 소유자 하나를 둔다

| 무엇이 | 어디로 | 수명 |
| --- | --- | --- |
| 엔진 묶음 전체(부트스트랩·`Deferred`·중계기·클라이언트·원격 후보·캐시 저장소·진단 로그) | 새 `GoCoachProcessRuntime`(루트 패키지, `GoAiCoachApplication`이 `by lazy`로 하나를 든다) | 프로세스. 프로덕션에서는 놓지 않는다 |
| 판(`GameSessionStateHolder`)과 화면 위치(`currentDestination`) | 새 `RetainedGoCoachSession`(`ui.shell`, 위 런타임이 든다) | 프로세스. 테스트만 갈아 끼운다 |
| 그 밖의 **모든 것**: 코루틴 스코프, `lifecycleController`, 컨트롤러, `wiringContext`, 엔진 플래그, 캐시, 저장소, 효과 | 지금처럼 컴포지션 | 화면 |

- **잡은 하나도 옮기지 않는다.** `viewModelScope`에 해당하는 것을 만들지 않는다. 카드가 경고한 유령 갱신을 설계 단계에서 피한다(3절).
- shared(`commonMain`)는 **한 줄도 바꾸지 않는다.** 홀더는 공개 API(`update`·`current`·`state`)만 쓴다. iOS 타깃은 영향이 없다.
- 엔진 파일(`LocalEngineSessionClient`, `KataGoProcessEngineAdapter`, `EngineProcessSlot`)도 **바꾸지 않는다.** 그래서 `#110`의 "동시 금지 `#111`"는 이 설계에서는 실질적으로 풀린다. P5 동시 금지(`ui/`·`GoCoachApp.kt`)는 그대로다.
- 수동 DI다. Hilt·Koin을 쓰지 않는다(「서 있는 답」·진단서 §3.1).

### 0.3 사용자 결정이 필요한 것 (구현 전에)

| # | 정할 것 | 권고 |
| --- | --- | --- |
| D1 | 카드의 수단 변경: "ViewModel 얇은 래퍼" → "프로세스 수명 소유자(`GoCoachProcessRuntime`·`RetainedGoCoachSession`)". 카드 제목·진단서 §3.2 진행 메모를 고친다 | 채택. 근거는 0.1 |
| D2 | 엔진을 프로덕션에서 **멈추지 않는다**(프로세스가 죽을 때 함께 죽는다). 액티비티 `finish`(API 26–30의 홈 화면 뒤로가기)에서 멈추는 안은 뒤로 미룬다(4.5) | 채택. 쌓임(#110)은 이것만으로 없어진다 |
| D3 | 런타임이 든 Compose 스냅숏 상태 2개(`engineBootstrap`·`destination`)는 원장 L14의 눈에 안 보인다(클래스 인스턴스를 통해 닿기 때문). 원장 정책을 넓히지 않고, 대신 새 소스 계약 `ProcessRuntimeContractTest`로 멤버 목록을 고정한다 | 채택. 정책 확대는 옛 "숫자 상향"이라 피한다 |
| D4 | `#110`의 통과 기준 "KataGo 프로세스가 늘 1개 이하"를 **종류별**(gtp ≤ 1, analysis ≤ 1)로 읽는다. 어댑터 하나가 두 종류를 하나씩 띄우므로, 합계 1은 정상 동작에서도 틀린 기준이다 | 채택 |
| D5 | DKA 자동화 수단: `settings put global always_finish_activities 1`이 에뮬레이터에서 실제로 먹는지 C2에서 먼저 잰다. 안 먹으면 자동 게이트는 `recreate()` 변형으로 두고, DKA 자체는 카드의 🧪(사용자 실기)로 확인한다 | 7.1 참고 |

---

## 1. 지금의 소유 표

범례
- **(a) 재생성**: 같은 프로세스에서 액티비티 인스턴스가 새로 만들어진다. 해당 경우는 셋이다.
  - DKA에서 떠났다가 돌아올 때(`Bundle` 복원).
  - `configChanges`에 없는 설정 변경. 매니페스트에는 `orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode|density|fontScale|locale|layoutDirection`만 있다. 예를 들어 `fontWeightAdjustment`(API 31+ 굵은 글꼴 토글), `keyboard`(물리 키보드 연결), `mcc|mnc`, `navigation`, `touchscreen`, `colorMode`, `grammaticalGender`(API 34)는 재생성한다. 이 목록은 실기로 확인할 것.
  - **(a′)** `finish` 뒤 같은 프로세스에서 다시 실행: minSdk 26이라 **API 26–30에서 홈 화면 뒤로가기**가 여기에 해당하고, 계기 테스트가 `ActivityScenario`를 여러 번 띄울 때도 그렇다. `Bundle`은 없고 결과는 (a)와 같다.
- **(b) 프로세스 사망**: 저메모리 킬러, `am kill`. `Bundle`은 작업 복원 시 돌아온다.
- **[A]** = 인수 시험(DKA → 판·화면 위치)이 지금 실패하는 원인. **[110]** = KataGo가 쌓이는 원인.

### 1.1 `MainActivity.onCreate` → `setContent { … }` (컴포지션)

| 자리 (줄) | 지금 수명 | (a) 재생성 | (b) 사망 | 표시 |
| --- | --- | --- | --- | --- |
| `preferencesStore` `remember(applicationContext)` (L81) | 컴포지션 | 새로 만든다(상태 없는 래퍼) | 같음 | – |
| `LaunchedEffect(preferencesStore)` → `AppFontScaleState.load` (L82) | 컴포지션 효과, 전역 `object`에 쓴다 | 다시 적재(멱등) | 같음 | – |
| `positionAnalysisCacheStore` `remember` (L92) | 컴포지션 | 새 인스턴스. 새 엔진 클라이언트에 물린다 | 같음 | [110] 새 클라이언트의 재료 |
| `diagnosticEventLog` `remember` (L95) | 컴포지션 | 새 인스턴스(파일 기반) | 같음 | – |
| `coreApiDeferred` `remember` (L99) | 컴포지션 | **새 `Deferred`**라 새 부트스트랩을 기다린다 | 같음 | [110] |
| `sessionGenerationRelay` `remember` (L102) | 컴포지션 | 새 중계기 | 같음 | – |
| `engineBootstrap` `remember { mutableStateOf(null) }` + `LaunchedEffect(Unit)` 부트스트랩 (L103–115) | 컴포지션 상태와 효과 | **`createEngineBootstrap`을 다시 돈다.** 결과로 새 `KataGoProcessEngineAdapter`와 새 슬롯이 생긴다. 첫 실행의 100MB 복사 도중이면 옛 IO 블록과 새 IO 블록이 같은 `model.bin.tmp`에 **동시에** 쓴다(옛 블록은 취소돼도 블로킹 복사를 끝까지 한다) | 새로(정상) | **[110] 뿌리** |
| `remoteClient` `remember` (L126) | 컴포지션(디버그 전용) | 새 인스턴스 | 같음 | – |
| `engineClient` `remember(remoteClient, …)` (L141) | 컴포지션 | **새 `LocalEngineSessionClient`**: 새 오퍼레이션 락과 새 어댑터가 생기고, 옛 어댑터의 gtp·analysis 프로세스는 **아무도 내리지 않는다**(`stop()`을 부르는 곳은 새 대국의 `startNewGame`뿐이다) | 자식 프로세스도 프로세스 그룹과 함께 죽는다(stdin EOF로도 끝난다) | **[110]** |
| `engineIdentity` 람다 (L168) | 값이 아니라 공급자 | 새 부트스트랩을 읽는다 | – | – |
| `AppSplash`의 `rememberSaveable finished` | `Bundle` | **살아남는다** | 작업이 복원되면 살아남는다 | – (앱에 하나뿐인 `rememberSaveable`) |
| `applyRotationPolicy`, `lifecycleScope.launch { AdsConsentManager.refresh }` | 액티비티 | 다시 돈다(규격대로) | 같음 | – |

### 1.2 `GoCoachApp` → `GoCoachScreen` (컴포지션)

| 자리 (줄) | 지금 수명 | (a) 재생성 | (b) 사망 | 표시 |
| --- | --- | --- | --- | --- |
| `identity`/`engineName`/`engineDiagnostic` (L183–185, 평범한 val, `PINNED_UNREMEMBERED`) | 재구성마다 | 다시 읽는다 | – | – |
| `scope = rememberCoroutineScope()` (L186) | 컴포지션 | **취소된다.** 이 스코프로 띄운 잡이 전부 끝난다 | – | – |
| `preferencesStore`·`initialPreferences` (L188–189) | 컴포지션 | 디스크에서 다시 읽는다(자동저장된 값) | 같음 | – |
| `authClient`·`deviceIdentityStore`·`credentialManagerClient`·`premiumStateStore` (remember 안 함, `FROZEN_UNREMEMBERED_INSTANCES`) | 재구성마다 | 새로 만든다 | 같음 | – |
| **`currentDestination`** `remember { mutableStateOf(initialDestination(…)) }` (L200) | 컴포지션 | **사라진다.** `initialDestination`, 곧 홈으로 돌아간다 | 홈(의도대로) | **[A] 화면 위치** |
| `showResignConfirmFromBack`·`showResumeDialog` (L203–204) | 컴포지션 | 사라진다(닫힌다) | 같음 | – |
| `premiumState` `remember { mutableStateOf(store.load()) }` (L209) | 컴포지션(부채 DOMAIN_STATE) | 저장소에서 복원한다(동등) | 같음 | – |
| `sessionStore`·`benchmarkStore`·`debugReportMirror`·`clipboardPort`·`userNoticePort`·`runtimeEventLog` (L211–218) | 컴포지션 | 새로 만든다(파일·시스템 래퍼) | 같음 | – |
| `defaultPlayLevel`·`initialPlan` (L219–226) | 컴포지션 | 다시 계산한다 | 같음 | – |
| **`sessionHolder`** `remember { GameSessionStateHolder(…).also { relay.bind } }` (L227–240) | 컴포지션(키 없음) | **사라진다.** 설정에서 새 미리보기 판(`isGameEnded=true`)을 만든다 | 새로(의도대로. 이어하기가 맡는다) | **[A] 판** |
| `sessionSnapshot` 사본과 `LaunchedEffect(sessionHolder)` 수집 (L241–247) | 홀더를 따른다 | 새 홀더를 따른다 | – | [A]의 결과 |
| `HolderBackedState` 13개(`gameState` … `turnTimeState`) | 홀더를 보는 창 | 새 홀더를 본다 | – | [A]의 결과 |
| `ObserveTimerLifecycle` (L323, 도우미 안의 `DisposableEffect`) | 컴포지션 | `ON_PAUSE`에 멈춘 시계를 **옛 홀더에** 적고, 새 홀더의 시계는 처음부터 | – | [A]의 일부 |
| `isEngineBusy`·`isEngineBlockingBusy`·`engineActivityIndicator`·`engineTurnWaitCompletionSeq`·`isEngineReady`·`hasCompletedEngineStartup` (L324–333, 부채 ENGINE_STATE) | 컴포지션 | `false`/`Preparing`으로 다시 시작한다. 기동 효과와 수명 장부가 다시 채운다 | 같음 | – |
| `analysisCache`·`undoAnalysisRestoreCache` (L329–330) | 컴포지션 | 비워진다(캐시를 못 맞출 뿐 옳다) | 같음 | – |
| `uxOptions` (L331, 부채 PERSISTED_PREFERENCE) | 컴포지션 | 설정에서 다시 읽는다(자동저장됨) | 같음 | – |
| `isScoreGraphExpanded` (L332) | 컴포지션 | 접힌다 | 같음 | – |
| `undoEngineInterventionQuietUntil`·`isPendingUndoSync` (L340–341) | 컴포지션 | 0/false로 돌아간다 | – | – |
| `cancelUndoSync`·`exitToHome` (지역 var) | 컴포지션마다 | 다시 묶인다 | – | – |
| `lifecycleController` `remember {}` 키 없음 (L376) | 컴포지션 | 새 장부. 옛 잡은 스코프와 함께 취소됐다 | – | – |
| `displayStateApplier` (L397) | 컴포지션 | 새로 | – | – |
| `LaunchedEffect(Unit)` 앱 시작 로그 (L405) | 컴포지션 효과 | 다시 적는다 | 같음 | – |
| `LaunchedEffect(engineClient)` 엔진 기동 (L408) | 컴포지션 효과 | **새 클라이언트로** `startSession` → `initialize` → 새 어댑터가 gtp 프로세스를 띄운다. 이 순간 옛 프로세스와 함께 **2개**가 된다 | 새로 | **[110] 발화점** |
| `LaunchedEffect(sessionStore)` 저장된 대국 확인 (L433) | 컴포지션 효과 | 새 홀더에 자동저장본을 싣고 **홈에 「이어하기」를 띄운다.** 20수는 이어하기를 눌러야만 보인다 | 이어하기(의도대로) | **[A]** |
| `LaunchedEffect(preferencesStore, settingsState, uxOptions)` 설정 자동저장 (L451) | 컴포지션 효과 | 다시 돈다(멱등) | – | – |
| `LaunchedEffect(savedSessionUiState, …)` 대국 저장·히스토리 (L473) | 컴포지션 효과 | 다시 돈다(히스토리는 저장소가 중복을 거른다) | – | – |
| `deferredTopMoveAnalysis`·`postUndoSync` `remember {}` 키 없음 (L507–509, #107) | 컴포지션 | 새 자리. 옛 잡은 이미 취소됐다 | – | – |
| `wiringContext` `remember(7 키)` · `controllers` `remember(wiringContext)` (L515, L598) | 컴포지션 | 다시 배선한다 | – | – |
| `LaunchedEffect(isEngineReady, …)` AI 차례·추천 수 트리거 (L676) | 컴포지션 효과 | 다시 돈다 | – | – |
| `LaunchedEffect(isGameEnded, …)` 캐시 최적화 안내 (L696) | 컴포지션 효과 | 다시 돈다 | – | – |
| 도우미(`buildBotCharacterUiState`·`buildPremiumUiState`·`PremiumPurchaseRestoreEffect`·`buildConsumableUiState`·`PremiumExpiryAutoDisableEffect`·`OneShotAnalysisAutoClear`·`AttendanceRewardClaimDialog`·알림 다이얼로그들) | 도우미 안 컴포지션 | 저장소에서 다시 계산한다. ⚠️ 예외 하나는 1.3 참고 | 같음 | – |
| 전역 `object`(`SplashVisibility`·`FinishedGameFlow`·`AppFontScaleState`·`AttendanceClaimReplaySignal`·`GuideBlockingOverlays`·`GuideTargetSpots`) | 프로세스 | **살아남는다** | 사라진다 | – (`#45`의 몫) |

### 1.3 조사하다 드러난 인접 결함 — 이 설계의 범위 밖

- `ConsumableUiState.kt:149` `var ledger by remember { mutableStateOf(OneShotLedger()) }`: 1회권의 "지불 원장·지금 보이는 것"이 컴포지션에만 있다. 재생성되면 **이미 차감된 1회권의 표시가 사라진다.** `#40`과 같은 계열이지만 `#44`(ConsumableUiState 재설계)의 파일이다. 새 카드를 만들거나 `#44`에 넣을 것을 권한다. `RetainedGoCoachSession`이 그 자리의 자연스러운 새 집이다.
- DKA에서는 **다른 액티비티를 띄우는 흐름**(결제 UI, 보상형 광고, 카메라 권한, Credential Manager)의 콜백이 죽은 스코프로 간다. DKA를 켠 개발자 기기에서만 재현되고 원래 있던 문제다. 범위 밖이다.
- `#110`은 테스트만의 문제가 아니다. **(a′) API 26–30 사용자가 홈에서 뒤로가기로 나갔다가 다시 켜면** 같은 프로세스에서 KataGo 한 쌍이 더 쌓인다. 굵은 글꼴 토글 같은 (a)의 설정 변경도 대국을 날리고(홈 + 이어하기) 프로세스를 쌓는다.

---

## 2. 목표 소유

### 2.1 그림

```
프로세스 ─ GoAiCoachApplication
  └─ processRuntime: GoCoachProcessRuntime            (by lazy — 처음 화면이 붙을 때 만든다. 프로덕션에서는 놓지 않는다)
       ├─ 엔진(#110): diagnosticEventLog · positionAnalysisCacheStore · sessionGenerationRelay
       │              · coreApiDeferred · engineBootstrap(스냅숏 상태) · 부트스트랩 잡(프로세스에 1회)
       │              · remoteClient? · engineClient · engineIdentity()
       └─ session: RetainedGoCoachSession               (테스트만 갈아 끼운다)
            ├─ GameSessionStateHolder                   (첫 attach에서 만든다 — FirstRunGate 뒤라야 한다)
            └─ destination (스냅숏 상태)                 (첫 attach에서 initialDestination()을 한 번)

액티비티 ─ MainActivity         processRuntime·retainedSession을 인스턴스마다 한 번 읽어 고정한다(by lazy)
  └─ 컴포지션 ─ GoCoachApp/GoCoachScreen   나머지 전부(스코프·수명 컨트롤러·컨트롤러·wiringContext·플래그·캐시·저장소·효과)
```

### 2.2 `GoCoachProcessRuntime` — 새 파일 `app-android/src/main/java/com/worksoc/goaicoach/GoCoachProcessRuntime.kt`

- **무엇을 하나**: `MainActivity.kt` L92–157의 엔진 조립을 **그대로 옮겨 온다**(한 줄씩, 문구와 KDoc 포함). 새 계산은 없다. "소유만 하고 계산하지 않는다"는 조건이 여기서 성립한다.
- **왜 루트 패키지인가**: 조립 전용 층이고, `ui.shell.RetainedGoCoachSession`을 만든다. `engine/` 패키지에 두면 `engine → ui.shell`, `engine → persistence` 간선이 생긴다. 루트 → `ui.shell` 간선은 이미 있다(`MainActivity` → `GoCoachApp`). `LayeringContractTest.compositionRootFileSetIsAConsciousDecision`의 목록에 한 줄을 더하고 사유를 적는다: "#40·#110 — 프로세스 수명 소유자. 조립만 한다".
- **왜 Application이고 ViewModel이 아닌가**: 0.1이 근거다. 하나 더 있다. `onCleared()`는 DKA에서도 불리므로, 거기서 엔진을 멈추면 복귀할 때마다 모델 약 100MB를 다시 적재한다(에뮬레이터에서 수십 초).
- 모양(스케치. JVM 테스트를 위해 생성자에 이음새를 둔다 — Application 없이 만들 수 있게):

```kotlin
internal class GoCoachProcessRuntime(
    val diagnosticEventLog: DiagnosticEventLogPort,
    positionAnalysisCacheStore: PositionAnalysisCacheStore,
    private val createBootstrap: () -> EngineBootstrap,          // 블로킹 IO. 프로덕션: createEngineBootstrap(app, nativeLibraryDir)
    private val remoteEngineUrl: String?,                         // null = 원격 없음(BuildConfig.DEBUG && 비어 있지 않을 때만 값)
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val sessionGenerationRelay = SessionGenerationRelay()
    private val coreApiDeferred = CompletableDeferred<EngineCoreApi>()
    private var engineBootstrap by mutableStateOf<EngineBootstrap?>(null)   // 준비 전에는 null(#101)
    private val remoteClient: EngineSessionClient? = /* MainActivity L126–137 그대로 */
    val engineClient: EngineSessionClient = remoteClient ?: LocalEngineSessionClient(/* L144–156 그대로 */)
    var session: RetainedGoCoachSession = RetainedGoCoachSession(sessionGenerationRelay); private set

    private val bootstrapScope = CoroutineScope(SupervisorJob() + mainDispatcher + CoroutineName("EngineBootstrap"))
    private var bootstrapStarted = false

    fun engineIdentity(): EngineIdentity = /* MainActivity L168–175 그대로 — 값이 아니라 매번 읽는다 */

    /** 프로세스에 한 번. ⚠️ 엔진을 먼저 풀고 정체를 나중에 알린다(#101 — 순서 그대로). */
    fun startEngineBootstrap() {
        if (bootstrapStarted) return
        bootstrapStarted = true
        bootstrapScope.launch {
            val ready = withContext(ioDispatcher) { createBootstrap() }
            coreApiDeferred.complete(ready.coreApi)
            engineBootstrap = ready
        }
    }

    /** 계기 테스트 전용 — 떠 있는 화면은 제 세션을 이미 고정해 뒀으므로 영향을 받지 않는다(7.4). */
    @VisibleForTesting
    fun replaceSessionForTest() { session = RetainedGoCoachSession(sessionGenerationRelay) }

    companion object {
        fun forApplication(app: Application): GoCoachProcessRuntime = /* 파일·BuildConfig를 읽어 위 생성자로 */
    }
}
```

- `GoAiCoachApplication`에는 `internal val processRuntime: GoCoachProcessRuntime by lazy { GoCoachProcessRuntime.forApplication(this) }` 한 줄을 더한다. **`onCreate`에서는 만들지 않는다.** 처음 화면이 붙을 때 만들어지므로 `onCreate`의 초기화 순서 계약(#63 릴리즈 초기화, #99 개발자 모드 초기화가 저장소보다 먼저)보다 항상 늦다. `DeveloperModeResetContractTest`는 `onCreate` 본문만 읽으므로 그대로다.
- 부트스트랩을 **시작하는 자리**는 지금처럼 `MainActivity`의 `LaunchedEffect(Unit)`이다. 본문만 `processRuntime.startEngineBootstrap()`으로 바뀐다. 시점(첫 컴포지션 직후)이 오늘과 같고, #123(UMP)과 #125(스플래시와 함께 부팅)가 전제한 순서도 그대로다. 런타임 생성자에는 부수효과가 없다(테스트 리셋이 부트스트랩을 건드리지 않는다).
- 부트스트랩 잡은 컴포지션과 무관한 **유일한 잡**이다. 쓰는 것이 런타임의 `Deferred`와 `engineBootstrap`뿐이라 유령 갱신이 없다. 오늘 DKA가 첫 실행 복사 도중에 오면 생기던 **이중 시딩 경합**도 사라진다. 선례: `AttendanceCheckInCoordinator`가 이미 자기 프로세스 스코프를 든다.
- 부트스트랩 실패 의미는 바꾸지 않는다. 오늘 `LaunchedEffect` 안의 예외는 앱을 죽인다. `SupervisorJob` 아래 `launch`의 예외도 기본 처리기로 앱을 죽인다. 조용히 삼키지 않는다.

### 2.3 `RetainedGoCoachSession` — 새 파일 `app-android/src/main/java/com/worksoc/goaicoach/ui/shell/RetainedGoCoachSession.kt`

```kotlin
/**
 * 액티비티가 다시 만들어져도 살아남아야 하는 것 — 판(세션 홀더)과 화면 위치, 둘뿐이다(refactor backlog #40).
 * 프로세스가 죽으면 함께 죽는다 — 그때는 자동저장 + 「이어하기」가 맡는다(둘은 다른 사건이다).
 * ⚠️ 잡을 들지 않는다. ⚠️ 함수형 필드를 들지 않는다 — attach의 람다는 그 자리에서 부르고 버린다
 *   (들면 첫 컴포지션의 지역 값을 프로세스 내내 붙잡는다 — 함정 67·#108과 같은 모양).
 */
internal class RetainedGoCoachSession(private val sessionGenerationRelay: SessionGenerationRelay) {
    private var holder: GameSessionStateHolder? = null
    private val destinationState = mutableStateOf<ScreenDestination?>(null)
    private var engineBoardUnverified = false

    /** 재부착 뒤 한 번만 참 — 트리거 효과가 엔진이 준비된 첫 회에 소비해 재동기화를 건다(4.3, C4b). */
    fun takeEngineBoardResync(): Boolean = engineBoardUnverified.also { engineBoardUnverified = false }

    var destination: ScreenDestination
        get() = checkNotNull(destinationState.value) { "attach() 전에 목적지를 읽었다" }
        set(value) { destinationState.value = value }

    /** 화면 하나가 붙는다. 처음이면 만들고, 다시 붙는 것이면 떠난 화면의 잡이 남긴 표시를 지운다. */
    fun attach(
        initialState: () -> GameSessionControllerState,
        initialDestination: () -> ScreenDestination,
    ): GameSessionStateHolder {
        holder?.let { existing ->
            existing.update(GameSessionControllerState::withoutJobOwnedMarkers)
            engineBoardUnverified = true            // C4b — 떠난 화면의 잡이 엔진 판을 어디에 두고 갔는지 모른다
            return existing
        }
        return GameSessionStateHolder(initialState()).also { created ->
            holder = created
            destinationState.value = initialDestination()
            // 홀더가 생기는 바로 이 자리에서 잇는다(#18) — 홀더가 새로 생길 때만 다시 돈다.
            sessionGenerationRelay.bind { created.current.core.runtimeState.sessionGeneration }
        }
    }
}

/** 화면과 함께 죽은 잡만 세우는 표시 — 잡이 남지 않으므로 표시도 남으면 안 된다(3.3). */
internal fun GameSessionControllerState.withoutJobOwnedMarkers(): GameSessionControllerState =
    withAutoAiTurn(autoAiTurn.clearPending())
        .withBenchmark(benchmark.copy(progress = null))
        .withPositionCacheOptimization(positionCacheOptimization.finishRunning())
```

- 전부 shared의 **공개** API(`update`, `withAutoAiTurn`, `clearPending`, `finishRunning`, data class `copy`)다. shared 홀더는 손대지 않는다.
- `ScreenDestination`이 `ui.shell`에 있으므로 이 클래스도 `ui.shell`(L8, 맨 위층)에 둔다. import는 `application.session`(shared)과 `engine.SessionGenerationRelay`(ui 밖, `GoCoachApp.kt`가 이미 쓴다)뿐이라 `UiPackageCycleRatchetTest`의 ui 간선은 하나도 늘지 않는다.

### 2.4 `GoCoachScreen`에서 바뀌는 것 (나머지는 그대로)

```kotlin
internal fun GoCoachApp(
    engineClient: EngineSessionClient,
    engineIdentity: () -> EngineIdentity,          // 그대로 — EngineReadinessWiringContractTest
    diagnosticEventLog: DiagnosticEventLogPort,
    retainedSession: RetainedGoCoachSession,       // sessionGenerationRelay 매개변수를 대신한다
)
...
val sessionHolder = remember(retainedSession) {                         // WIRING(원장 키 그대로)
    retainedSession.attach(
        initialState = { buildInitialSessionState(initialPlan, engineDiagnostic, benchmarkStore, benchmarkStore.loadText()) },
        initialDestination = { initialDestination(deviceIdentityStore, initialPreferences.hasSeenOnboarding) },
    )
}
var currentDestination by HolderBackedState(                           // 훅이 아니다 — 원장 L15가 허용하는 위임
    { retainedSession.destination },
    { value -> retainedSession.destination = value },
)
...
LaunchedEffect(sessionStore) {
    // 보존 세션마다 한 번 — 다시 붙은 화면이 대국 도중에 「이어하기」를 다시 걸면 shouldShowResumePrompt=true가 되어
    // 자동저장이 멈추고(planSavedGamePersistence가 Skip) AI 트리거가 막힌다.
    if (savedSessionUiState.hasCheckedSavedSession) return@LaunchedEffect
    runSavedSessionPromptApplication(/* 그대로 */)
}
...
LaunchedEffect(isEngineReady, isEngineBusy, …) {                       // 기존 트리거 효과(키 그대로)
    // C4b — 재부착 뒤 한 번, 엔진 판을 살아남은 판에 다시 맞춘다(기존 무르기 재동기화 경로).
    if (isEngineReady && retainedSession.takeEngineBoardResync()) {
        controllers.undoController.schedulePostUndoSync(gameState, quietUntilMillis = 0L)
    }
    controllers.topMovesController.resumeDeferredAnalysisIfIdle()
    runTurnAutomationTriggerEffect(/* 그대로 */)
}
```

- `currentDestination` 선언은 `sessionHolder` **아래로** 내린다(attach가 목적지를 먼저 세운다). 쓰는 곳 20여 군데는 한 줄도 안 바뀐다.
- `.also { sessionGenerationRelay.bind … }`는 `attach` 안으로 들어간다.
- 컴포지션에 **남는 것**과 그 이유:

| 남는 것 | 이유 |
| --- | --- |
| `scope`, `lifecycleController`, `displayStateApplier`, `controllers`, `wiringContext`, `postUndoSync`, `deferredTopMoveAnalysis` | 잡과 그 장부다. 잡이 화면과 함께 죽으므로 장부도 화면과 함께 죽어야 맞다(3절). 옮기면 ENGINE_STATE·WORKFLOW_STATE 부채 전부와 수명 컨트롤러가 따라가야 한다 — `#41`/`#42`의 일이다 |
| 엔진 플래그 6개, `undoEngineInterventionQuietUntil`, `isPendingUndoSync` | 위 장부가 쓰는 값이다. 새 화면에서는 기동 효과와 장부가 다시 세운다. 옛 잡은 이미 끝났으므로 "안 바쁨"이 새 화면에서 맞는 값이다 |
| `uxOptions`, `premiumState`, 설정·저장소 읽기 | 디스크가 원본이고 재생성에서 다시 읽으면 같은 값이다 |
| 다이얼로그 게이트, `isScoreGraphExpanded` | 인수 기준 밖이다. 닫혀서 돌아오는 것은 수용한다. 원하면 나중에 화면 쪽 `rememberSaveable`로(원장 결정 필요) |
| 캐시 2개 | 비어서 돌아와도 옳다(다시 계산할 뿐). 선택: 나중에 보존 세션으로 옮길 수 있다 |

### 2.5 화면 위치: ViewModel이냐 `rememberSaveable`이냐 — 둘 다 아니고 보존 세션

- **ViewModel**: DKA에서 죽는다(0.1).
- **`rememberSaveable`**: DKA에서는 산다. 그런데 **(b) 프로세스 사망에서도 산다**(작업 복원 `Bundle`). 그러면 사망 뒤 복귀 화면이 `InGame`인데 판은 새 미리보기 판이 되어 서로 어긋난다. 지금의 올바른 동작(홈 + 이어하기)이 깨진다. 막으려면 "목적지는 복원됐는데 홀더가 새것이면 홈으로"라는 보정이 필요하다. 게다가 원장의 NAVIGATION 모양 검사(`remember { mutableStateOf(initialDestination(…)) }` 정확히)가 `rememberSaveable`을 받지 않으므로 **정책 확대(사용자 결정)**가 된다.
- **보존 세션(선택)**: 목적지와 판이 **같은 객체에 같은 수명으로** 산다. DKA·설정 변경에서는 둘 다 살고, 프로세스 사망에서는 둘 다 죽는다. 그래서 "사망 = 홈 + 이어하기"가 따로 보정하지 않아도 저절로 유지된다.
- "& co."의 범위: 이 설계가 지키는 것은 **어느 화면인가**(`ScreenDestination`)다. 화면 안의 세부 위치(기록 화면에서 연 다시보기, 공부 화면의 쪽, 스크롤)는 각 화면의 `remember`라 첫 상태로 돌아간다. 범위 밖이다. 「복기 하기」 진입은 이미 `FinishedGameFlow`(전역)가 들고 있어 산다.

---

## 3. 잡 수명 표

### 3.1 규칙 (한 문장)

> **어떤 잡도 제 화면보다 오래 살지 않는다.** 모든 코루틴은 컴포지션 스코프에 남아 화면과 함께 취소되고, 화면보다 오래 사는 것은 객체(엔진·홀더·목적지)뿐이다. 새 화면은 살아남은 상태에서 진행 중이던 일을 **다시 끌어낸다**(재부착이 잡 소유 표시를 지우고, 트리거 효과가 다시 요청한다). 예외는 런타임 소유 상태에만 쓰는 프로세스당 1회 엔진 부트스트랩 하나다.

- 오늘의 평범한 "홈 버튼"(DKA 꺼짐)에서는 액티비티가 멈출 뿐 파괴되지 않는다. 그래서 스코프도 살고 AI 차례도 뒤에서 계속 돈다. DKA·재생성에서는 그 잡이 취소되고, 복귀하면 같은 국면에서 **다시 요청된다.** 이 비대칭은 오늘과 같다. 달라지는 것은 판이 살아남는다는 것 하나다.
- 그래서 카드가 경고한 유령 갱신, 곧 "화면이 없는데 UI 상태를 쓴다"는 새로 생기지 않는다. 대신 **반대 방향의 위험**이 새로 생긴다. 잡은 죽었는데 그 잡이 세운 표시가 살아남은 홀더에 남는 것이다. 3.3이 그것을 다룬다.

### 3.2 자리별

| 자리 | 지금 스코프 | 이후 | 재생성 때 무슨 일이 있나 / 유령 위험 |
| --- | --- | --- | --- |
| **M-1** `MainActivity` `LaunchedEffect(preferencesStore)` 글꼴 배율 적재 | 컴포지션 | 유지 | 전역에 멱등하게 쓴다. 위험 없음 |
| **M-2** `MainActivity` `LaunchedEffect(Unit)` 부트스트랩 | 컴포지션(본문이 IO) | **본문을 런타임의 1회 잡으로**(효과는 시작만) | 오늘은 재생성마다 부트스트랩과 새 어댑터가 생긴다([110]). 이후 프로세스에 1회. 쓰는 것은 런타임 소유 상태뿐 |
| **M-3** `lifecycleScope.launch { AdsConsentManager.refresh }` | 액티비티 | 유지 | UMP 규격(기동마다 조회) |
| **G-1** `LaunchedEffect(sessionHolder)` 수집 → `sessionSnapshot` | 컴포지션 | 유지 | 새 화면은 살아남은 홀더의 `current`로 사본을 시작한다(StateFlow가 곧바로 현재값을 준다) |
| **G-2** `LaunchedEffect(Unit)` 앱 시작 로그 | 컴포지션 | 유지 | 재생성마다 한 줄 더 적힌다. 로그에서 재생성을 알아보는 표지로 수용한다 |
| **G-3** `LaunchedEffect(engineClient)` 엔진 기동 | 컴포지션 | 유지(재생성마다 다시 돈다) | 따뜻한 엔진이라 `initialize` → 프로세스 재사용 → `configure` 명령 몇 개로 끝난다. 옛 AI 차례의 배수(#15)가 GTP 왕복 락을 쥐고 있으면 그 탐색이 끝날 때까지 기다린다. 그동안 "준비 중"이 보인다. 홀더에는 `engineMessage`와 프로필 도장만 쓴다. `startSession`의 점수 스냅숏은 `null`이라 형세 그래프를 덮지 않는다(`applyEngineStartupDisplayPlan`). 산 화면 안에서 쓰므로 위험 없음 |
| **G-4** `LaunchedEffect(sessionStore)` 저장된 대국 확인 | 컴포지션 | 유지 + **`hasCheckedSavedSession`이면 건너뛴다** | 막지 않으면: 대국 도중 재부착 → `applyPrompt` → `shouldShowResumePrompt=true` → 자동저장 Skip + AI 트리거 막힘. 이 표시는 홀더 안에 있으므로 보존 세션마다 한 번만 돈다 |
| **G-5** 설정 자동저장 | 컴포지션 | 유지 | 멱등 |
| **G-6** 대국 저장 + 히스토리 | 컴포지션 | 유지 | 멱등(히스토리는 저장소가 중복을 거른다) |
| **G-7** AI 차례·추천 수 트리거 | 컴포지션 | 유지 + 재부착 뒤 첫 준비에서 엔진 판 재동기화를 한 번 건다(C4b) | **이것이 "다시 끌어내기"다.** 준비 → AI 차례면 `requestAiTurn`. `isPending`은 재부착이 지웠다. 재동기화 잡도 이 화면 스코프에서 돈다(규칙 그대로) |
| **G-8** 캐시 최적화 안내 | 컴포지션 | 유지 | 안내(`prompt`)는 홀더에 살아 있다가 다시 뜬다(옳다) |
| **W-1** `lifecycleController.launchTracked` — 새 대국, 이어하기 복원, 추천 수, 착수 동기화, 캐시 최적화, 형세 추정, 계가 규칙 재동기화 | 컴포지션(배선 `context.scope`) | 유지 | 화면과 함께 취소된다(오늘과 같다). 취소 뒤 홀더에 쓰는 것은 `finally`의 정리뿐이어야 한다(3.3·3.4). ⚠️ 캐시 최적화의 `finishRunning()`은 `finally`가 아니다(`PositionAnalysisCacheOptimizationRunnerApplication.kt:71`) → 재부착이 지운다 |
| **W-2** `AutoAiTurnController` → `launchAutoAiEffect` | 컴포지션 | 유지 | 러너의 `finally`가 busy와 `pending`을 푼다(#74, `AutoAiScheduledTurnRunnerApplication.kt:82·168`). 재부착도 한 번 더 지운다. `timedOut`(상태 B)은 **남긴다** — 사용자의 선택을 아직 기다리므로 팝업이 다시 뜨는 것이 옳다 |
| **W-3** `UndoController` 대기 재동기화(`launchUiEffect`, 자리는 `postUndoSync`) | 컴포지션 | 유지 | 잡과 자리가 함께 사라진다. AI 차례·착수 동기화·추천 수는 `syncToGameState`(newGame + 수순 재생)로 시작하므로 **스스로 맞춘다.** ⚠️ 예외가 하나 있다. AI 대국의 형세 추정은 `syncFirst=false`(`ScoreEstimateApplication.kt:132`)라 엔진 판을 믿는다. 그래서 복귀 뒤 첫 오퍼레이션이면 **옛 판으로 추정한다** → C4b(4.3)가 재부착 뒤 한 번 다시 맞춘다. 정숙 구간(1초)도 0으로 돌아가, 복귀 직후 AI가 기다림 없이 둘 수 있다(수용) |
| **W-4** `EngineBenchmarkController`(`launchUiEffect` ×3) | 컴포지션 | 유지 | 진행 표시 `progress`는 성공·실패 분기에서만 지워진다(`EngineDeviceBenchmarkApplication.kt`) → 재부착이 지운다. 결과는 잃는다(다시 돌리면 된다). 엔진 판은 다음 오퍼레이션이 맞춘다 |
| **S-1** 화면별 `rememberCoroutineScope`(설정 인증, 좌석 설정, 판 스캔, 온보딩, 프리미엄 결제·광고, 캐릭터 페이저, 광고 개인정보) | 화면 | 유지 | 화면에 묶인 일이다. DKA에서 외부 액티비티 콜백을 잃는 것은 원래 있던 문제다(1.3) |
| **S-2** 도우미·화면의 효과(`ObserveTimerLifecycle`, 와치독·가착수(`GamePlaySection`), `GoCoachContent` 판정 키, 패스 알림, 출석, 프리미엄 만료, 1회권 자동 해제, 스플래시) | 화면 | 유지 | 시계: 떠날 때 `ON_PAUSE`가 **살아남는 홀더에** 멈춘 시계를 적고, 새 화면의 관찰자가 붙으면 `ON_RESUME`이 재생돼 다시 간다 — 이제 맞게 이어진다 |

### 3.3 잡 소유 표시 — 살아남은 홀더에 남으면 안 되는 것

| 필드 | 세우는 잡 | 취소에서 지워지나 | 남으면 |
| --- | --- | --- | --- |
| `autoAiTurn.isPending` | AI 차례 | 예(`finally`, 디스패치 전 취소는 완료 콜백) | AI가 다시는 두지 않는다(#74가 막은 것) |
| `benchmark.progress` | 기기 벤치마크 | **아니오** | 진행 팝업이 영영 떠 있다. `savedSessionToPrompt`가 `progress == null`을 요구하므로 이어하기도 가려진다 |
| `positionCacheOptimization.isRunning` | 캐시 최적화 | **아니오** | 최적화가 "도는 중"으로 영영 남는다 |

- `withoutJobOwnedMarkers()`는 이 셋을 **재부착에서 한 번에** 지운다. 러너마다 `finally`로 고치는 안(shared 수정)은 뿌리를 고치는 것이지만 범위가 넓어진다. 그래서 `#41`(GameSessionScope — "무엇이 언제 정리되나")로 넘긴다.
- 목록이 낡지 않게 7.3의 **분류 계약**을 둔다. 하위 상태 data class의 `Boolean`·nullable 필드는 전부 "잡 소유(지운다)" 아니면 "보존(사유)" 둘 중 하나로 분류돼야 한다. 새 필드가 생기면 빨갛다.
- 늦게 도는 옛 `finally`(설정 변경 재생성처럼 파괴와 생성이 붙어 올 때)는 **지우는 쪽**으로만 쓰므로 재부착과 겹쳐도 멱등이다. #15 이후 엔진 대기는 취소 가능한 `await`라 취소된 잡은 몇 ms 안에 끝난다. 그 `finally`는 새 화면의 첫 프레임보다 먼저 메인 큐에서 돈다. 드문 경우로 새 AI 예약 뒤에 옛 `finally`가 `pending`을 지워 차례가 두 번 예약돼도, 늦은 쪽은 결과 가드(국면·세대)가 버린다.

### 3.4 취소 뒤의 쓰기 감사 — `runCatching`의 자리

- 오늘은 취소된 잡이 쓰는 홀더도 함께 버려진다. 이후에는 **홀더가 산다.** 그래서 "취소를 `Failure`로 바꿔 실패 문구를 쓰는" 경로가 새로 보이게 된다.
- 안전한 모양: `runEngineIo { runCatching { … } }`, 곧 `withContext` **안에서** 삼키는 것. 메인으로 돌아오는 재디스패치가 취소 가능하므로(프롬프트 취소) 결과가 버려지고 `CancellationException`이 난다.
- 위험한 모양: `runCatching { runEngineIo { … } }`, 곧 메인에서 `withContext`를 감싸는 것. 되돌아올 때 난 `CancellationException`을 삼키고 실패 문구를 살아남은 홀더에 쓴다.
- shared/application에 `runCatching`이 19곳 있다(#74가 AI 경로는 이미 고쳤다, `AutoAiRunnerApplication.kt:206`). C3b(8절)는 오퍼레이션 종류마다 특성 테스트로 "취소 뒤 홀더에 쓰이는 것은 3.3의 지우기뿐"을 잰다. 현 코드에서 빨가면 그것은 진짜 결함이다. 그때는 멈추고 보고한다.

---

## 4. 엔진 수명 (#110)

### 4.1 누가 소유하나

- `GoCoachProcessRuntime` 하나가 엔진 클라이언트 **한 개**와, 그 아래 `KataGoProcessEngineAdapter` **한 개**를 든다. 부트스트랩이 프로세스에 1회이기 때문이다.
- 어댑터는 gtp·analysis 슬롯(`EngineProcessSlot`)을 하나씩 가진다. 슬롯은 수명 락 안의 double-checked 기동과 CAS 게시로 **종류당 프로세스 1개**를 보장한다(#14).
- 그래서 "KataGo가 종류당 1개 이하"는 **구조로** 성립한다. 액티비티를 몇 번 만들든 새 어댑터가 생기지 않는다. 오늘 쌓이는 원인(재생성마다 부트스트랩·어댑터·클라이언트를 새로 만들고 옛것을 안 내림)을 없앤다.
- 원격 클라이언트(디버그)도 프로세스에 하나다. 원격일 때 로컬 어댑터는 만들어져도 프로세스를 띄우지 않는다(오늘과 같다).

### 4.2 언제 멈추나 — 선택: 프로덕션에서는 멈추지 않는다

| 사건 | 엔진 |
| --- | --- |
| 홈 버튼(DKA 꺼짐) | 계속 돈다(오늘과 같다) |
| DKA 파괴·설정 변경 재생성 | **계속 돈다** — 복귀한 화면이 따뜻한 엔진을 다시 쓴다 |
| API 26–30 홈 화면 뒤로가기(`finish`) | 계속 돈다. 다시 켜면 **다시 쓴다**(오늘은 한 쌍 더 쌓인다) |
| 프로세스 사망 | 프로세스 그룹과 함께 죽는다(stdin EOF로도 끝난다) |
| 「엔진 다시 시작하기」(#74) | 잠금 없는 `forceReset` → 슬롯 폐기 → 다음 `acquire`가 하나를 띄운다(종류당 1개 유지) |

- 뒤로가기 뒤 같은 프로세스에서 다시 켜면 보존 세션도 그대로다. 사용자에게 보이는 차이는 거의 없다.
  - 뒤로가기로 나가는 자리는 홈이나 온보딩뿐이다. 대국 중 뒤로가기는 기권 확인 → `exitToHome`이 판을 미리보기로 되돌린다.
  - 그래서 홈으로 돌아오고, 이어하기 안내 상태도 디스크에서 새로 읽은 것과 같다. 엔진이 따뜻해서 더 빠를 뿐이다.

### 4.3 재생성이 AI 차례·분석 도중에 오면

- **AI 차례(상태 A, 탐색 중)**
  - 옛 스코프가 취소되면 AI 잡이 취소된다. `cancelInFlightAutoAiTurn`을 거치지 않지만 효과는 같다. 러너의 `finally`가 busy와 `pending`을 푼다(#74).
  - 호출자는 곧바로 돌아가고 오퍼레이션 락을 놓는다(#15). GTP 응답은 어댑터의 배수가 받는다. 배수는 그 핸들의 왕복 락을 명령 마감까지 쥔다. 프로세스는 새로 뜨지 않는다.
  - 새 화면에서는 기동이 다시 돈다(배수가 끝날 때까지 기다린다). 준비되면 트리거가 **같은 국면으로** AI 차례를 다시 요청한다. 이 차례는 `configure` + 전체 동기화 + `genmove`로 돈다.
  - 사용자는 "준비 중 → AI 생각 중"을 한 번 더 본다.
- **AI 차례(상태 B, 시간 초과로 선택을 기다림)**: `autoAiTurn.timedOut`이 홀더에 살아 있으므로 팝업이 다시 뜬다. 트리거는 `isAwaitingTimeoutChoice` 때문에 건너뛴다(옳다).
- **추천 수·형세·착수 평가 분석**: 취소되고, 새 화면에서 켜져 있으면 트리거가 다시 요청한다. `serializedOrBusy` 경로는 배수가 끝날 때까지 "잠시 뒤"로 미룬다(오늘의 바쁨 흐름).
- **무르기 대기 재동기화 · 엔진 판이 홀더와 어긋난 채 남는 경우**
  - 무르기 뒤 1초 안, 벤치마크 도중, 사람 착수 동기화 도중에 화면이 죽으면 엔진 판이 홀더와 다를 수 있다.
  - 동기화로 시작하는 오퍼레이션(AI 차례·착수·추천 수·그래프 추정)은 스스로 고친다. **AI 대국의 형세 추정만** 엔진 판을 믿는다(`syncFirst = matchMode == LocalTwoPlayer`).
  - 이 틈은 오늘도 있다(무르기 뒤 1초 정숙 구간 동안 형세 추정을 누르면 된다. `isPendingUndoSync`는 추천 수만 막는다). #40 뒤에는 홀더가 살아남아, 이 틈이 다음 착수 때까지 늘어난다.
  - **C4b**: 재부착하면 `RetainedGoCoachSession`이 1회용 표시(`Boolean` 하나, 잡이 아니다)를 세운다. 기존 트리거 효과 `LaunchedEffect(isEngineReady, …)`가 준비된 첫 회에 `controllers.undoController.schedulePostUndoSync(gameState, quietUntilMillis = 0L)`를 한 번 부른다.
    - 기존 경로(동기화 + 그래프 추정, `isPendingUndoSync` 게이트 포함)를 그대로 쓴다. 새 효과·shared 변경은 0이다.
    - 같은 효과에서 AI 차례가 먼저 둬도 재동기화 잡은 목표 국면이 바뀐 것을 보고 스스로 물러난다(`UndoController` 기존 검사).
  - 뿌리("AI 대국 형세 추정이 엔진 판을 믿는다")는 `#111` ⓑ(도중에 프로세스를 잃은 뒤의 오퍼레이션)와 같은 계열이라 그쪽에 적는다.
- **새 대국 도중**: `startNewGame`은 `stop()`(Stop 폐기) → `initialize`(새로 띄움) → `newGame`이다. 그 사이에 취소되면 다음 오퍼레이션이 `acquire`할 때까지 프로세스가 0개다. 이후는 종류당 1개다.
- **벤치마크 도중**: 3.3(진행 표시는 지운다). 엔진 판은 다음 오퍼레이션이 맞춘다.
- **#15 오퍼레이션 락**: 이제 클라이언트가 하나라 **프로세스 전체에 락이 하나**다. 옛 화면의 배수와 새 화면의 오퍼레이션이 이 락으로 줄을 선다. 오늘은 새 클라이언트가 제 락과 제 어댑터를 가져, 옛 배수와 **나란히** 새 프로세스를 띄웠다. 이것이 쌓임의 기전이다.
- **#14 세대 CAS**: 바꾸지 않는다. 슬롯이 하나뿐이라 늦게 깬 옛 호출의 폐기가 새 세대를 죽이지 않는다는 성질을 그대로 쓴다.

### 4.4 1개를 넘을 수 있는 유일한 창

- `forceReset` 직후, SIGKILL된 옛 프로세스가 좀비로 거둬지기 전에 새 프로세스가 뜨는 수십 ms가 있다(오늘도 같다).
- 7.2의 계수는 상태 `Z`를 빼고, "결국 ≤ 1"을 5초 폴링으로 본다.

### 4.5 뒤로 미룬 안: `finish`에서 엔진 멈추기 (D2의 반대편)

- 하려면 틈이 없어야 한다. 순서는 다음과 같다.
  1. `forceReset()`.
  2. **오퍼레이션 락을 잡는** 새 `EngineLifecycleClient.shutdown()` 안에서 닫힘 표시를 세우고 `forceReset()`을 한 번 더 한다.
  3. 이후 모든 `serialized*`는 곧바로 실패한다.
- 이렇게까지 하는 이유: 모든 기동이 오퍼레이션 락 안에서 일어나므로, 락을 잡은 뒤의 두 번째 `forceReset`만이 "fork/exec 도중이던 잡"이 띄운 것까지 거둔다.
- shared 인터페이스와 `LocalEngineSessionClient` 변경, 참조 계수(옛 `finish`의 `onDestroy`가 새 인스턴스의 `onCreate`보다 늦게 오는 경우)가 필요하다. 반면 사용자에게 보이는 이득은 "앱을 나간 뒤 KataGo가 캐시 프로세스에 남지 않는다"뿐이다. 오늘의 평범한 홈 버튼과 같은 상태다. `#111`과 함께 볼 후속 카드로 권한다.

---

## 5. 함정 67 · #43 · #46 · #107 · 기타 가드에 미치는 영향

### 5.1 함정 67 / `WiringContextFreezeContractTest` (#43)

- **`wiringContext`의 키 7개(`PinnedWiringContextKeys`)는 그대로다.**
  - 키 `sessionSnapshot`은 여전히 **컴포지션의 사본**이다. 홀더가 오래 살 뿐, 홀더가 내보낼 때마다 사본이 바뀌고 사본이 바뀔 때마다 객체와 컨트롤러가 다시 배선된다.
  - 새 화면의 첫 컴포지션은 살아남은 `holder.current`로 사본을 시작해 그 위에 컨트롤러를 배선한다. 그래서 키는 의미를 그대로 가진다.
  - 나머지 여섯(`undoEngineInterventionQuietUntil`, `isPendingUndoSync`, `isEngineReady`, `isEngineBusy`, `isEngineBlockingBusy`, `uxOptions`)은 컴포지션에 남으므로 바뀌는 것이 없다.
- 익명 컨텍스트 객체의 멤버는 **한 줄도** 바뀌지 않는다. `FreezeProneMembers`·`CoveringKey`·`RewireSurvivingSlots`·`QuietWindowCapture`·`latestEngineIdentity` 검사는 그대로다.
- `CapturedByDesign`의 **사유 문자열 두 개만** 고친다(정책이 아니라 사실 기술이다).
  - `engineClient`: "MainActivity가 remember로 한 번 만든" → "GoCoachProcessRuntime이 프로세스에 한 번 만든(#110)".
  - `diagnosticEventLog`: 같은 식으로 고친다.
- **새로 생기는 같은 계열의 위험**: 오래 사는 객체가 첫 컴포지션의 클로저를 쥐는 것(#108의 모양). `RetainedGoCoachSession`은 `attach`의 람다를 그 자리에서 부르고 버린다. 런타임의 람다는 런타임 자기 상태(`engineBootstrap`·중계기)만 읽는다. 둘 다 7.3의 `ProcessRuntimeContractTest`가 "함수형 필드 0"으로 못박는다.
- `PINNED_UNREMEMBERED`(`identity`·`engineName`·`engineDiagnostic`)도 그대로다.

### 5.2 #107 소스 계약

- `val postUndoSync = remember { PostUndoSyncSlot() }`와 `val deferredTopMoveAnalysis = remember { TopMoveAnalysisDeferral() }`는 키 없이 한 번 만들어 그대로 넘기는 모양 그대로다. **바꾸지 않는다.**
- 이 자리들은 "다시 배선되는 것보다 오래" 살고, "화면보다 오래" 살지는 않는다. 그 잡이 화면 스코프에 있기 때문이다. 둘의 KDoc에 이 구분을 한 줄 적는다. 보존 세션으로 옮기면 #107 계약을 깨고, 죽은 `Job`을 드는 자리만 남긴다.

### 5.3 #46 원장(`architecture-budgets.json` · `ShellStateLedger.kt`)

| 셸 | 변경 | 규칙상 |
| --- | --- | --- |
| `mainActivity` | `onCreate.positionAnalysisCacheStore`·`diagnosticEventLog`·`coreApiDeferred`·`sessionGenerationRelay`·`engineBootstrap`·`remoteClient`·`engineClient` **7줄 삭제**(자리가 없어진다) | 줄이는 쪽이다(L2가 요구한다) |
| `mainActivity` | `onCreate.LaunchedEffect(Unit)`: 종류 **BOOTSTRAP → STARTUP_EFFECT**, why "엔진 부트스트랩을 프로세스 런타임에서 한 번 시작(#110)", since `#110` | STARTUP_EFFECT 모양(첫 키 `Unit`, 제 함수의 var에 쓰지 않음)을 만족한다. 종류는 닫힌 목록 안에서 고른다 |
| `mainActivity` | `GUARDED_SHELLS` 효과 수 **2 그대로**. `codeLineBudget` 95 → 재측정 값으로 **내린다** | 내리는 것만 JSON 편집 |
| `goCoachApp` | `GoCoachScreen.currentDestination`(NAVIGATION) **삭제** — `HolderBackedState` 위임이라 훅 자리가 아니다(L15 허용) | 줄이는 쪽 |
| `goCoachApp` | `GoCoachScreen.sessionHolder`(WIRING) 키 그대로, why "보존 세션에서 받는다(처음이면 만든다) — 액티비티 재생성에도 산다, 세대 중계 결속은 attach 안(#40·#18)", since `#40` | WIRING은 MEMO를 허용한다(OWED 부채를 읽지 않는다). HOLDER_MIRROR 검사("H가 WIRING 자리이고 `LaunchedEffect(H)`가 있다")도 그대로 만족한다 |
| `goCoachApp` | `GoCoachScreen.premiumState`의 why에서 "홀더/ViewModel로(#40·#44)" → "(#44)" | 사실 정정. 부채는 그대로 OWED다 |
| 정책 | 종류·역할·모양·관용구·`SHELL_DEBT`·`FROZEN_*`·효과 수 **변경 0** | 사용자 결정 불필요 |

- **갚는 부채는 없다.** ENGINE_STATE·WORKFLOW 부채는 잡과 함께 컴포지션에 남는다. `SHELL_DEBT`의 OWED 목록이 그대로라 L8도 그대로다.
- **원장의 사각지대(D3)**: 런타임과 보존 세션이 든 스냅숏 상태는 L14(`object`·최상위만 본다)에도, 셸 파일 검사에도 잡히지 않는다. 이것은 "UI 상태를 전역으로 옮겨 원장을 피한다"(#45가 되돌리는 모양)와 겉보기가 같다. 차이는 이것이 **수명 요구(DKA)로** 생긴 것이고 멤버가 둘뿐이라는 점이다. 그래서 7.3의 계약이 멤버 목록을 고정한다. `#45`가 되돌릴 전역(`SplashVisibility` 등)을 여기로 옮기지 못하게 하는 장치이기도 하다.

### 5.4 그 밖

- **`EngineReadinessWiringContractTest`**
  - 세 검사의 **대상 파일을 `GoCoachProcessRuntime.kt`로 옮긴다.**
    - "클라이언트를 부트스트랩으로 다시 키잉하지 않는다": 이제 한 번 초기화하는 `val`이다. `engineBootstrap`은 `capabilitiesProvider` 람다 안에서만 읽는다.
    - "`coreApiDeferred.complete(`가 `engineBootstrap = ready`보다 앞": 그대로 옮겨 잰다.
  - "`engineIdentity: () -> EngineIdentity` in GoCoachApp"는 그대로다.
  - `sessionHolder`의 키 설명 주석을 고친다.
- **`StartupOrderContractTest`**: `super.onCreate`와 `setContent` 사이는 여전히 비어 있다. 런타임은 `setContent` 람다 안에서 `by lazy`로 처음 닿는다. `AppSplashContractTest`도 그대로다(`AppSplash()`는 `setContent` 안, `GoCoachApp` 뒤).
- **`LayeringContractTest`**: `sessionHolder.state.collect`와 `sessionSnapshot = snapshot` 문구는 그대로다. 루트 파일 목록에 `GoCoachProcessRuntime.kt`를 더한다(2.2 사유).
- **`UiPackageCycleRatchetTest` / `ContractSymbols.UI_LAYERS`**: ui 간선의 변화는 0이다(2.3).
- **`GoCoachControllerWiringTest` / `FakeGoCoachAppWiringContext`**: `GoCoachAppWiringContext` 인터페이스가 그대로라 변경 0이다.
- **컨트롤러 재배선 모델(요약)**
  - 컨트롤러는 계속 **컴포지션에서, 보존된 상태 위에** 배선된다.
  - 오래 사는 것은 데이터(홀더·목적지)뿐이고, 데이터를 움직이는 것(컨트롤러·잡·장부)은 화면과 함께 태어나고 죽는다.
  - 고정 키 7개는 "화면 안에서 언제 다시 배선하나"를 정하고, 보존 세션은 "화면을 넘어 무엇을 남기나"를 정한다. 두 축이 겹치지 않아 함정 67의 조건(ⓐ 지연 읽기, ⓑ 64멤버 감사)을 건드리지 않는다.

---

## 6. 의존성

- **이 설계는 새 의존성이 없다.** `ViewModel`·`viewModel()`·`SavedStateHandle`을 쓰지 않는다. `libs.versions.toml`은 바뀌지 않는다.
- 참고(D1을 뒤집어 ViewModel을 쓰게 될 경우):
  - 이미 전이로 들어와 있다. `activity-compose:1.10.1` → `activity-ktx:1.10.1`(`by viewModels()`) → `lifecycle-viewmodel ≥ 2.6.1`. lifecycle 계열은 서로 버전 제약을 걸어 **가장 높은 선언**(`lifecycle-process:2.8.7`)으로 정렬된다.
  - Compose BOM 2025.04.01(Compose 1.8)도 `lifecycle-runtime-compose 2.8.x`를 쓴다. 이 저장소는 이미 `androidx.lifecycle.compose.LocalLifecycleOwner`를 import한다.
  - 컴포저블 안에서 `viewModel()`이 필요하면 `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` 하나를 더한다. `lifecycleProcess` 버전 참조를 `lifecycle`로 바꿔 둘을 한 값으로 묶는다.
  - 2.9/2.10으로 올리지 않는다. 2.9는 ViewModel을 KMP 아티팩트로 재편하고 lifecycle 계열 전체(`process`·`runtime-compose`)를 끌어올린다. 묻지 않은 툴체인 변화다(`#55`의 영역).
  - 확인 명령(구현 스레드에서): `./gradlew :app-android:dependencies --configuration debugRuntimeClasspath | grep lifecycle`. 이 조사는 읽기 전용이라 돌리지 않았다.
- AGP 8.13.2, Kotlin 2.3.20, compileSdk 36, coroutines 1.8.0에서 새 코드는 표준 API(`CompletableDeferred`·`SupervisorJob`·`mutableStateOf`)만 쓴다.
- JVM 테스트 의존(`testImplementation`: junit + org.json)에는 `kotlinx-coroutines-test`가 없다. 그래서 런타임은 디스패처를 생성자로 받는다. 테스트는 `Dispatchers.Unconfined` + `runBlocking`으로 돌린다.

---

## 7. 테스트 계획

### 7.1 (a) 인수 시험 — `app-android/src/androidTest/.../smoke/ActivityRecreationSurvivalSmokeTest.kt` (새로)

**무엇이 DKA를 진짜로 재현하나**

| 수단 | 재현하는 것 | 판정 |
| --- | --- | --- |
| `ActivityScenario.recreate()` | 설정 변경 경로다. `isChangingConfigurations=true`라 **`ViewModelStore`가 남는다** | DKA가 아니다. ViewModel 안을 **거짓 초록**으로 통과시킨다. 보조 시험으로만 둔다 |
| 실제 DKA(`always_finish_activities=1`) + 홈 + 런처 인텐트로 복귀 | 설정 변경이 아닌 파괴 → `Bundle`로 새 인스턴스 | **이것이 인수 기준이다** |

⚠️ `settings put global always_finish_activities 1`은 전역 설정만 쓴다. AMS가 이 값을 부팅 때 읽고, 개발자 옵션 토글은 `setAlwaysFinish`로 메모리 값까지 바꾼다고 기억한다. 그래서 **즉시 먹는지는 확실하지 않다.** 그래서 이 시험은 효과를 **스스로 검증**한다. 먹지 않으면 초록이 아니라 빨강이고, 그 메시지가 수단을 알려 준다.

**흐름**

1. `@Before`
   - `resetToFreshInstallState()`(7.4의 세션 교체를 포함한다).
   - 원래 값을 `settings get global always_finish_activities`로 읽어 둔다.
   - `settings put … 1`. `UiAutomation.executeShellCommand`의 `ParcelFileDescriptor`는 끝까지 읽고 닫는다.
   - 9줄 판·**흑백 모두 사람**인 20수 스냅숏을 `GameSessionStore`에 심는다. 따내기 없는 고정 수순이다. `SavedSessionPromptSmokeTest`와 같은 방식이다.
   - 엔진이 필요 없으므로 KataGo 모델 없이도 돈다.
2. `ActivityLifecycleMonitorRegistry`에 콜백을 걸어 `MainActivity` 인스턴스별로 (단계, `isFinishing`, `isChangingConfigurations`)를 기록한다.
3. `targetContext.startActivity(packageManager.getLaunchIntentForPackage(pkg))`로 연다(런처 탭과 같은 루트 인텐트).
   - 첫 실행 뒤의 이어하기 흐름: 「▶ 이어하기」 → 「예」 → `InGame` · 「20수」가 보이는지 단언한다.
   - `ActivityScenario`를 쓰지 않는 이유: 그 부트스트랩 액티비티와 인스턴스 추적이 DKA 파괴와 얽힌다.
4. 홈으로 간다: `startActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME).addFlags(NEW_TASK))`.
5. **전제 단언**: 첫 인스턴스가 `DESTROYED`에 이르렀고 `isFinishing == false && isChangingConfigurations == false`. 10초 안에 아니면 실패한다.
   - 메시지: "DKA가 먹지 않았다 — 이 이미지에서는 `settings put`이 즉시 반영되지 않는다. 개발자 옵션에서 켜거나 C2의 대체 수단을 쓸 것."
   - 이것이 **거짓 초록 방지**다(「건너뛴 초록은 아무것도 재지 않는다」).
6. 같은 런처 인텐트로 돌아온다. **다른 인스턴스**가 `RESUMED`에 이른 것을 확인한다.
7. **단언**
   - `TestTags.GoBoard`가 보인다.
   - 「20수」가 보인다.
   - 「▶ 이어하기」가 **없다**.
   - `BackHandler` 경로로 기권 확인이 뜬다(InGame에 있다는 두 번째 증거. 선택).
8. `@After`(`finally`)
   - 설정을 원래 값으로 돌린다.
   - 떠 있는 인스턴스를 `finish()`하고 파괴를 기다린다.
   - `resetRetainedSessionForTest()`.

**RED/GREEN**
- 현 코드(`df307336`)에서는 7번에서 실패한다. 새 인스턴스가 홈에 「▶ 이어하기」를 띄우고, 판과 20수가 없다. 커밋 본문에 실패 메시지를 옮겨 적는다.
- C4 뒤에는 초록이다.
- ViewModel 안으로 구현했다면 **빨강**이다. 이 시험이 0.1의 판단을 앞으로도 지킨다.

**보조 변형 `…WhenRecreated`**: 같은 흐름에서 4–6 대신 `ActivityScenario.recreate()`를 쓴다. 현 코드에서 빨갛고(`remember`가 사라진다) C4 뒤에 초록이다. D5가 "DKA 자동화 불가"로 끝나면 이것이 자동 게이트다. DKA는 카드의 🧪로 사용자가 확인한다.

**사망 경로 회귀 방지(이어하기 유지)**
- 기존 `SavedSessionPromptSmokeTest`는 새 `RetainedGoCoachSession`을 받으므로 "새 프로세스 = 이어하기"를 그대로 잰다.
- 7.3의 JVM 시험 "새 세션은 확인을 돌리고, 보존 세션은 안 돌린다"가 거기에 더해진다.
- 🧪: 배경에서 `adb shell am kill <applicationId>` → 다시 실행 → 홈에 「이어하기」.

### 7.2 (b) #110 — `app-android/src/androidTest/.../smoke/EngineProcessCountSmokeTest.kt` (새로)

- **세는 법**
  - `FreshAppState`의 `/proc` 훑기를 `kataGoProcessCensus(): List<KataGoProcess(pid, kind, state)>`로 꺼낸다.
    - `kind`: `cmdline`의 두 번째 NUL 필드(`gtp`/`analysis`).
    - `state`: `/proc/<pid>/stat`의 셋째 필드. `Z`는 뺀다.
  - `stopLeftoverKataGoProcesses()`는 그 위로 다시 짠다.
  - ⚠️ `UiAutomation`의 `pidof`나 `ps -A | grep libkatago`를 쓰지 않는다. 셸 uid는 **다른 앱의 KataGo까지** 본다(개발 변형과 릴리스 변형을 함께 깔았을 때 applicationId가 다르다). 그러면 거짓 빨강이 난다. 앱 uid의 `/proc`는 hidepid 때문에 자기 uid만 보이므로 정확하다.
- **전제**: 진짜 KataGo 모델이 있어야 한다(`EngineStallRecoverySmokeTest`처럼 없으면 실패). 앞뒤로 `stopLeftoverKataGoProcesses()`를 부른다. 루프 **도중에는 부르지 않는다.**
- **루프 1(쌓이던 원래 모양)**
  - `ActivityScenario.launch(MainActivity)` → 엔진 준비(AI가 앉은 로비의 시작 버튼이 열리는 것) → `close()`를 N=4번.
  - 매번 준비 뒤와 닫은 뒤에 `gtp ≤ 1 && analysis ≤ 1`을 5초 폴링으로 단언한다.
- **루프 2(재생성)**: 한 번 띄워 AI 대국을 시작하고 AI의 한 수를 기다린다(가능하면 analysis 프로세스도 뜨게). 그 뒤 `recreate()` ×4. 각 회차 뒤 같은 단언.
- **(선택) 루프 3**: 7.1의 DKA 수단이 먹으면 DKA 홈·복귀 ×3.
- **RED**: 현 코드에서 루프 1의 두 번째 준비 뒤 `gtp=2`, N번째에 `gtp=N`이다. 그 숫자를 커밋 본문에 적는다. C3 뒤 초록이다.
- **덤**: 루프 1은 #110을 낳은 바로 그 모양(한 프로세스에서 액티비티를 여러 번)이다. C3 뒤에는 `EngineStallRecoverySmokeTest` 등의 KataGo 정리가 **안전망**으로만 남는다. 백로그 「(함정 아님, 환경)」 줄을 고친다.

### 7.3 (c) JVM 시험 (`make test`에 들어간다)

- **`GoCoachProcessRuntimeTest`**(`app-android/src/test/.../GoCoachProcessRuntimeTest.kt`). 가짜 부트스트랩 팩토리, `Dispatchers.Unconfined`.
  - `startEngineBootstrap()`을 N번 불러도 팩토리가 **1번** 불린다.
  - 팩토리가 끝나기 전에는 `engineIdentity()`가 `Unresolved`다. 끝난 뒤에는 `Deferred`가 먼저 완료되고 그다음에 정체가 보인다. 팩토리가 돌려준 가짜 `coreApi`가 `engineClient` 호출에 닿는 순서로 잰다.
  - `engineClient`는 `session`이 몇 번 바뀌어도 **같은 인스턴스**다.
  - `replaceSessionForTest()`는 세션만 바꾼다. 부트스트랩·클라이언트·중계기 인스턴스는 그대로다.
  - `capabilities.supportsDeviceBenchmark`는 준비 전 `false`, 로컬 부트스트랩 뒤 `true`, 스텁이면 `false`(#101 계약을 옮겨 온다).
- **`RetainedGoCoachSessionTest`**(`app-android/src/test/.../ui/shell/`)
  - 첫 `attach`는 `initialState`와 `initialDestination`을 각각 1번 부른다. 두 번째 `attach`는 **0번** 부르고 같은 홀더를 돌려준다.
  - 재부착은 `isPending`·`benchmark.progress`·`positionCacheOptimization.isRunning`을 지운다. `gameState`·설정·`timedOut`·`savedSession`(특히 `hasCheckedSavedSession`)·`resultToConfirm`·`prompt`·시계는 **그대로** 둔다. 셋을 모두 세운 상태로 만들어 한 번에 잰다.
  - 중계기: 생성 직후 `relay.current()`가 홀더의 세대이고, 홀더를 `update`하면 따라간다. `replaceSessionForTest` 뒤의 새 세션이 attach하면 새 홀더를 가리킨다.
  - `destination`은 attach 전에 읽으면 크게 실패한다. 쓰면 `mutableStateOf`로 읽힌다(스냅숏 상태가 JVM에서 동작한다).
- **`JobOwnedMarkerClassificationTest`**(소스 계약)
  - 하위 상태 data class 파일(`AutoAiTurnSessionModels`·`EngineBenchmarkModels`·`PositionAnalysisCacheOptimization`·`SavedSessionPromptApplication`·`GameSessionCoreState`와 하위 상태 5개)에 선언된 `Boolean` 필드와 nullable 필드의 집합을 잰다.
  - 그 집합이 `JobOwnedMarkers`(재부착이 지운다: 3개) ∪ `DurableByDesign`(필드 → 사유: 예를 들어 `timedOut` "사용자의 선택을 기다린다")와 **정확히 같아야** 한다.
  - 새 필드가 생기면 분류될 때까지 빨갛다.
- **`SavedSessionPromptOncePerSessionTest`**: `GoCoachApp.kt`의 `LaunchedEffect(sessionStore)` 본문이 `hasCheckedSavedSession` 검사로 시작하는지 소스 계약으로 잰다(컴포즈 안이라 JVM으로는 못 만든다 — `#43`과 같은 이유).
- **`ProcessRuntimeContractTest`**(D3의 대체 장치, 소스 계약)
  - ⓐ `GoCoachProcessRuntime`의 공개·내부 멤버 목록이 고정 목록과 같다.
  - ⓑ 두 클래스에 **함수형 타입 필드가 0**이다(`() ->`·`(…) ->`를 타입으로 가진 `val`/`var`가 없다. 생성자 매개변수 `createBootstrap`은 `private val`이지만 런타임 자신의 팩토리라 이름으로 예외를 둔다).
  - ⓒ `CoroutineScope(`는 런타임에 하나(`bootstrapScope`)뿐이고, `RetainedGoCoachSession`에는 0이다.
  - ⓓ `GoCoachApp.kt`·`MainActivity.kt`에 `viewModelScope`·`GlobalScope`·`CoroutineScope(`가 없다.
  - ⓔ `MainActivity`가 `processRuntime`과 `retainedSession`을 `by lazy`로 고정한다(7.4).
- **C3b 특성 테스트** `CancelledEngineWorkLeavesOnlyMarkerClearsTest`
  - `FakeGoCoachAppWiringContext` + 영영 멈추는 가짜 엔진으로, 종류마다(새 대국, 이어하기, 추천 수, 착수 동기화, 캐시 최적화, 형세, 계가 규칙, 벤치마크, AI 차례, 무르기 재동기화) 잡을 띄운 뒤 스코프를 취소한다.
  - 그 뒤 홀더의 변화가 3.3의 지우기(와 `finally` 정리)뿐인지 잰다(3.4).
  - 현 코드에서 초록이어야 한다. 빨가면 진짜 결함이다. 멈추고 보고한다.

### 7.4 계기 테스트 격리 — 새 함정 (함정 81과 짝)

- 프로세스 수명 소유자가 생기면 **같은 계측 프로세스의 다음 테스트가 앞 테스트의 판과 목적지를 물려받는다.**
  - `SharedPreferences` 캐시(함정 81)와 같은 모양이다.
  - 단독 실행은 초록이고, `make test-device`로 함께 돌리면 빨강이 된다.
- 게다가 `AppLaunchSmokeTest`는 `createAndroidComposeRule<MainActivity>()`라 **`@Before`보다 먼저** 액티비티를 띄운다. 그래서 `@Before`의 리셋은 늦다.
- 처방
  - ⓐ `FreshAppState.resetRetainedSessionForTest()` = `processRuntime.replaceSessionForTest()`. `resetToFreshInstallState()`가 부르고, `MainActivity`를 띄우는 테스트의 `@After`도(닫은 뒤) 부른다.
  - ⓑ `MainActivity`는 `processRuntime`과 `retainedSession`을 인스턴스마다 `by lazy`로 **고정**한다. 그래서 떠 있는 화면 밑에서 세션이 바뀌어도(`AppLaunchSmokeTest`의 늦은 `@Before`) 그 화면은 영향이 없다.
  - ⓒ `GoCoachApp`을 직접 컴포즈하는 3개(`SavedSessionPromptSmokeTest`, `NewGameBoardTapSmokeTest`, `DesignTokenScreenshotTest`)는 `sessionGenerationRelay = SessionGenerationRelay()` 대신 `retainedSession = RetainedGoCoachSession(SessionGenerationRelay())`를 넘긴다. 테스트마다 새것이다.
- 엔진은 테스트 사이에 **따뜻하게 남는다.** 테스트가 `stopLeftoverKataGoProcesses()`로 프로세스를 SIGKILL하면 다음 `acquire`가 `Died`로 거두고 다시 띄운다(#14).
- ⚠️ 부트스트랩도 프로세스에 1회다. 한 실행 안에서 모델을 넣거나 빼도 반영되지 않는다. `make test-device`는 실행 전후로 모델 유무가 같으므로 문제없다. 스텁 진단 문구는 이미 "넣고 앱을 다시 시작하라"고 말한다.
- `PITFALLS.md`에 새 항목(85: "ViewModel은 DKA를 견디지 못한다", 86: "프로세스 수명 소유자는 계기 테스트 사이에 되감는다")을 올린다.

### 7.5 🧪 사용자 실기 (카드 그대로 + 보강)

1. 수정 전 빌드, 개발자 옵션 「액티비티를 유지하지 않음」 ON, 9줄 AI 대국 20수 → 홈 버튼 → 앱으로 복귀.
   - **홈 화면과 「이어하기」가 보이면 재현**이다.
   - `adb shell ps -A | grep libkatago`로 gtp가 2개 이상인지도 본다. `#110`의 실사용 재현이다.
   - 이 확인은 앱 uid와 관계없이 셸로 본다. 변형을 하나만 깐 기기에서 할 것.
2. 새 빌드를 덮어 설치(`make install-dev-engine TARGET=phone` — 진짜 KataGo), 같은 순서.
   - **대국 화면·20수·같은 판**이면 통과다.
   - AI 차례 도중에 떠났다면 복귀 뒤 "준비 중 → 생각 중"을 거쳐 AI가 둔다.
   - 홈/복귀를 3번 반복한 뒤 gtp ≤ 1, analysis ≤ 1.
3. DKA를 끄고, 대국 중 홈 → `adb shell am kill <applicationId>` → 다시 실행: **홈에 「이어하기」**가 뜨면 통과다(사망 경로가 그대로다).
4. (가능하면) 대국 중 기기 설정 → 접근성 → 굵은 글꼴 토글 → 앱으로 복귀: 새 빌드에서 판과 화면이 그대로면 통과다. 수정 전 빌드에서는 홈으로 튕기고 「이어하기」가 뜰 것으로 예상하므로 먼저 확인한다(1절 (a) 목록은 추정이다).

---

## 8. 커밋 단계 · 위험 · 범위 밖

### 8.1 커밋 (태스크당 하나, 각 커밋 뒤 `make test TARGET=emu` 초록)

- `make test`는 `:app-android:compileDebugAndroidTestKotlin`까지 돈다. 계기 시험은 **컴파일만** 게이트에 걸리고, 빨간 계기 시험은 게이트를 깨지 않는다(`test-device`는 별도 타깃).
- 커밋은 경로를 지정한다(`git commit <경로> -m`, 함정 80·83). `spotlessApply`는 격리 워크트리에서만 돌린다(함정 84).

| # | 종류 | 내용 | 계기 시험 상태 |
| --- | --- | --- | --- |
| C0 | docs | 백로그: `#40`·`#110`을 「진행 중」으로. D1–D5 사용자 결정을 카드에 `✅ 사용자 결정(날짜)`로 적는다. `#110` "동시 금지 `#111`" 해제 사유(엔진 파일을 안 건드린다) | – |
| C1 | test(device) | `kataGoProcessCensus` 추출(`FreshAppState`), `EngineProcessCountSmokeTest`(7.2). 현 코드 RED 수치를 본문에 적는다 — `refactor backlog #110` | #110 RED |
| C2 | test(device) | `ActivityRecreationSurvivalSmokeTest`(7.1, DKA + `recreate()` 변형)와 DKA 도우미(설정 복원, 인스턴스 추적). **여기서 D5를 잰다** — `settings put`이 먹는지 본문에 적는다. 현 코드 RED 메시지를 적는다 — `#40` | #40 RED |
| C3 | refactor(engine) | `GoCoachProcessRuntime` + `GoAiCoachApplication.processRuntime`. `MainActivity`의 엔진 조립을 옮긴다(문구·KDoc 포함, 계산 변경 0). 원장 mainActivity 7줄 삭제 + `LaunchedEffect(Unit)` 종류 변경 + 줄 예산 하향. `EngineReadinessWiringContractTest` 대상 이동. `CapturedByDesign` 사유 2개. 루트 파일 목록 +1. `GoCoachProcessRuntimeTest`. `FreshAppState`에 런타임 접근. — `#110` | #110 **GREEN**, #40 RED |
| C3b | test(wiring) | `CancelledEngineWorkLeavesOnlyMarkerClearsTest`(3.4). 현 코드 초록(특성) — `#40` | – |
| C4 | fix(shell) | `RetainedGoCoachSession` + `withoutJobOwnedMarkers`. `GoCoachApp` 매개변수 교체. `sessionHolder`는 attach로, `currentDestination`은 `HolderBackedState`로. `LaunchedEffect(sessionStore)`에 1회 검사. `MainActivity`가 세션을 `by lazy`로 고정. androidTest 3개 서명. `resetRetainedSessionForTest`와 `@After` 리셋. 원장 goCoachApp 2줄. `RetainedGoCoachSessionTest`·`JobOwnedMarkerClassificationTest`·`SavedSessionPromptOncePerSessionTest` — `#40` | #40 **GREEN** |
| C4b | fix(shell) | 재부착 뒤 한 번 엔진 판 다시 맞추기(4.3). `RetainedGoCoachSession`의 1회용 표시와 트리거 효과 안 호출 한 줄(새 효과 없음). JVM: 표시가 재부착에서만 서고 한 번만 소비되는지. 소스 계약: 트리거 효과가 `isEngineReady`일 때만 소비하는지 — `#40` | – |
| C5 | test(contract) | `ProcessRuntimeContractTest`(7.3) — `#40` | – |
| C6 | docs | `PITFALLS.md` 85·86. `#107` 두 자리 KDoc에 "재배선보다 오래, 화면보다 짧게" 한 줄. 진단서 「진행 상태」에 §3.2 결론 정정 메모. `#41`·`#45`·`#47` 카드 갱신(8.3). 백로그 「(함정 아님, 환경)」 KataGo 줄 | – |
| C7 | docs | 자체 검수(`make test-device` 전체 + 7.2) + 사용자 🧪(7.5) 뒤 `#40`·`#110`을 완료 표로(한 줄 + 해시) | – |

- C3과 C4를 나누는 이유: C3만으로 `#110`이 닫히고 동작이 안전하다. 새 화면은 새 홀더를 받고, 공유 엔진은 다음 오퍼레이션이 전체 동기화한다. 문제가 나면 원인을 "엔진 수명"과 "세션 수명"으로 나눠 볼 수 있다.
- 순수 이동(C3의 조립 이동)과 동작 변경(C4)을 섞지 않는다(진단서 §5 원칙).

### 8.2 위험

| # | 위험 | 대비 |
| --- | --- | --- |
| R1 | 카드·진단서와 다른 수단(ViewModel 없음) | D1. 0.1의 바이트코드 근거와 7.1 시험이 판단을 고정한다 |
| R2 | 잡 소유 표시 목록이 낡는다(새 in-flight 필드) | `JobOwnedMarkerClassificationTest`. 뿌리 해법(러너 `finally`)은 `#41` |
| R3 | 취소를 `Failure`로 바꾸는 경로가 살아남은 홀더에 실패 문구를 쓴다 | C3b 특성 테스트(3.4). 빨가면 그 자리를 `runEngineIo` 안쪽 `runCatching`으로 |
| R4 | 재생성 뒤 기동이 옛 배수(최대 명령 마감, 캡 없는 탐색 120초)를 기다려 "준비 중"이 길다 | 수용(DKA·드문 설정 변경에서만). 필요하면 후속으로 "따뜻한 엔진이면 기동 생략" |
| R5 | 원장 밖의 프로세스 수명 Compose 상태(D3) — `#45`가 되돌리는 전역과 겉보기가 같다 | `ProcessRuntimeContractTest`의 멤버 고정 목록. 늘리려면 테스트를 고쳐야 하므로 리뷰 diff에 보인다 |
| R6 | 계기 테스트 사이의 상태 누수(7.4) | 세션 교체 + 인스턴스별 고정 + `@After` 리셋. 함정 86으로 기록 |
| R7 | 두 화면이 동시에 붙으면 재부착이 산 화면의 표시를 지운다 | 가정: 한 번에 한 화면. standard launchMode이고 다중 인스턴스 선언(`PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI`)이 없다. 지원하게 되면 `RememberObserver` 기반 부착 토큰으로 |
| R8 | 엔진이 앱을 나간 뒤에도 캐시 프로세스에 남는다(API 26–30 뒤로가기) | 오늘의 평범한 홈 버튼과 같은 상태이고 쌓이지 않는다. 저메모리 킬러가 프로세스째 거둔다. 멈춤 안은 4.5 |
| R9 | 부트스트랩이 프로세스에 1회라 스텁 폴백이 그 프로세스 동안 굳는다 | 새 프로세스 기동과 같다(오늘도 콜드 스타트에서 그렇다). 진단 문구가 이미 재시작을 안내한다 |
| R10 | `settings put`만으로는 DKA가 반영되지 않을 수 있다 | 7.1 전제 단언(거짓 초록 불가) + `recreate()` 변형 + 사용자 🧪(D5) |
| R11 | 원장 HOLDER_MIRROR·WIRING 검사가 MEMO 모양 `sessionHolder`를 예상과 다르게 판정 | C4에서 `ShellStateLedgerContractTest`로 먼저 확인한다. 빨가면 attach를 `remember(retainedSession) { retainedSession.attach(…) }` 한 문장 모양으로 맞추고, 정책은 넓히지 않는다 |
| R12 | 살아남은 홀더와 어긋난 엔진 판에서 AI 대국 형세 추정(`syncFirst=false`) | C4b의 재부착 1회 재동기화. 뿌리는 `#111` ⓑ에 적는다 |

### 8.3 명시적으로 범위 밖

- **`#41` GameSessionScope**: 대국 하나의 수명(끝났을 때 무엇을 정리하나). 이 설계는 "화면"과 "프로세스"만 정한다. `#41`은 `RetainedGoCoachSession` 안에 대국 스코프를 둔다. 재부착의 표시 지우기를 러너의 `finally`/스코프 취소로 옮기는 것도 `#41`에서 한다.
- **`#42` WiringContext 5분할**: 인터페이스·키·멤버 변경 0.
- **`#45` 전역 `object` 회수**: `SplashVisibility`·`FinishedGameFlow`·`AppFontScaleState`는 그대로다.
  - 카드 갱신 제안: ⓒ `FinishedGameFlow`의 닫힌 판정 키와 대기 수는 "세션 홀더로" 대신 **`RetainedGoCoachSession`**이 자연스러운 집이다. 둘 다 "컴포지션을 떠났다 돌아와도 남아야 하는" 값이라, 수명이 정확히 같다.
  - ⓓ `MainActivity` 효과 수는 이 설계 뒤에도 2라 카드의 "2→1"이 그대로 맞는다.
- **`#47` AppContainer**: `GoCoachProcessRuntime`은 엔진·보존 세션만 든다. 저장소·포트는 컴포지션에 남는다. `#47`이 올 때 이 런타임을 흡수한다.
- **`#44`**: `ConsumableUiState`의 1회권 원장 소실(1.3).
- **`#111`/`#109`/`#98`**: 엔진 파일을 건드리지 않는다. `finish`에서 엔진을 멈추는 후속(4.5)은 `#111`과 함께 본다.
- 화면 안 세부 위치(다시보기 선택·스크롤·공부 쪽), 다이얼로그 게이트의 `rememberSaveable`화(원장 정책 결정이 필요).
- `configChanges`에 `fontWeightAdjustment|keyboard|…`를 더해 재생성을 더 가리는 것. 진단서가 "가려 주고 있을 뿐"이라고 한 방향이라 권하지 않는다. DKA와 API 26–30 뒤로가기 경로는 그대로 남는다.
- iOS 래퍼. shared 변경이 0이라 영향이 없다.

---

## 부록 A. 기각한 대안 (다시 올라오기 쉬운 순서)

| 안 | 기각 이유 |
| --- | --- |
| ViewModel이 홀더와 `viewModelScope`를 소유(카드 원안) | DKA에서 `ViewModelStore.clear()`(바이트코드 확인). 인수 기준 불통과. `recreate()` 시험은 거짓 초록 |
| ViewModel + `SavedStateHandle`에 판 JSON(자동저장 코덱 재사용) | 프로세스 사망에서도 복원되어 "사망 = 이어하기"가 "자동 복원"으로 바뀐다(사용자 결정 사항). `Bundle` 1MB 트랜잭션 한도 위험(형세 스냅숏이 실린다). 코덱 이중화. 엔진은 여전히 VM마다 |
| 재생성 때 디스크 자동저장본을 물음 없이 복원 | `savedInstanceState != null`은 프로세스 사망 뒤 작업 복원에서도 참이라 둘을 가를 수 없다. 저장하지 않는 부분(분석·시계·무르기 상태)을 잃는다 |
| 목적지만 `rememberSaveable`, 판은 프로세스 소유 | 사망 뒤 `InGame` 목적지와 새 판이 어긋난다. 원장 NAVIGATION 모양 검사가 받지 않는다(정책 확대) |
| Compose `retain {}`(RetainedValuesStore) | BOM 2025.04.01(Compose 1.8)에 없다. 수명이 설정 변경 기준이라 DKA를 못 견딘다 |
| 잡을 프로세스/VM 스코프로 옮김 | 잡이 쓰는 ENGINE_STATE·WORKFLOW 상태가 컴포지션에 있어 유령 갱신이 난다. 화면 없이 AI가 계속 둔다. 옮기려면 부채 전부와 수명 컨트롤러를 함께 옮겨야 한다(`#41`·`#42`) |
| `ViewModel.onCleared()`에서 엔진 멈춤 | DKA 복귀마다 모델 약 100MB 재적재. 판은 여전히 못 지킨다. `finish`와 새 `onCreate`가 겹치면 틈 없는 종료가 필요하다(4.5) |
| Hilt·Koin | 「서 있는 답」·진단서 §3.1 |
