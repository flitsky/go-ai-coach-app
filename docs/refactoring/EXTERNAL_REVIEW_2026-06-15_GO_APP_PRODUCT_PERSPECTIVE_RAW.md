# 외부 바둑 앱 관점 리뷰 원문 - 2026-06-15

작성일: 2026-06-15  
원천: 사용자 제공 첨부 `/Users/ryan9kim/.codex/attachments/fb498d3a-ea5f-4c78-bbf0-4bca1bfebf47/pasted-text.txt`  
보존 목적: 기존 아키텍처 자기평가와 별도로, 바둑 앱 제품/엔진/플랫폼 확장 관점에서 나온 외부 비판을 원문 그대로 보존한다.

---

이 평가는 자기보고된 지표를 그대로 믿기보다 실제 코드를 일부 확인한 뒤 하는 게 맞습니다. 핵심 주장 몇 가지를 검증하겠습니다.

`EngineCoreApi.kt` 파일이 안 잡힙니다. 핵심 경계 주장을 검증하겠습니다.

검증을 마쳤습니다. 자기보고된 지표 일부는 사실이지만, 그 지표가 가리키는 결론은 보고서가 주장하는 것과 다릅니다. 코드를 직접 확인한 근거 위에서 평가하겠습니다.

먼저 검증 결과부터:

| 주장 | 실제 | 판정 |
|---|---|---|
| `EngineCoreApi`가 엔진 격리 경계 | `shared/commonMain/EngineModels.kt:19`에 실재, 문서화 수준 매우 높음 | ✅ 사실 |
| App Service가 엔진 세부를 모름 | `MatchPolicy.kt:250,307`·`EndgameResolver.kt:143`가 `EngineCoreApi`를 **직접** 받음 | ⚠️ 부분적 거짓 |
| `GoCoachApp.kt` 2,086줄 (개선됨) | 2,068줄, **@Composable 단 2개**, state hook 63개, `engineClient.runXxx` 약 15종 호출 | ⚠️ 수치 호도 |
| `withContext`·`scope.launch` 0개 | 사실. 단, `LaunchedEffect` 블록으로 이동했을 뿐 | ⚠️ 메트릭 게이밍 |
| `shared` KMP 모듈 (iOS 지향) | `androidTarget`만 존재. **iosTarget·expect/actual·iosMain 전무** | ❌ iOS는 미착수 |
| `LayeringContractTest` 6케이스 | 실재하나 **문자열 import grep + 하드코딩 파일 목록** 기반 | ⚠️ 약한 보증 |

---

## 1. 구조적 강점과 실질적 약점

### 강점 (인정할 부분)

1. **`EngineCoreApi`의 도메인 지식 인코딩이 탁월하다.** 단순 인터페이스가 아니라 KataGo GTP의 `maxTime`가 호출 단위가 아닌 프로세스 전역이라는 것, 타임아웃된 GTP 커맨드가 스트림을 오염시키므로 프로세스를 재시작/리싱크해야 한다는 것까지 주석으로 박혀 있습니다. 이건 "인터페이스를 잘 그었다"를 넘어 **운영 중 알아낸 함정을 코드에 보존**한 것으로, 팀 교체나 원격 전환 시 가장 비싼 지식입니다. 7계층 도식보다 이게 진짜 자산입니다.

2. **stale result 자동 폐기 메커니즘이 도메인에 맞다.** 바둑 분석은 비동기·장시간이고 사용자가 그 사이 수를 두거나 undo합니다. `sessionGeneration` + `boardFingerprint`로 늦게 온 결과를 버리는 건 이 도메인에서 반드시 필요한 정합성 장치이고, 임시방편이 아니라 모델에 1급 시민으로 들어가 있습니다.

3. **순수 규칙 로직의 KMP 분리가 실제로 깨끗하다.** `shared/commonMain`에 `java.`·`android.` import 누수가 0건입니다. `BoardRules`, `LegalMoveGenerator`, `BoardScorer`, `GameStateFingerprint` 등 규칙 코어가 플랫폼 비의존으로 격리돼 있어, KMP의 가장 흔한 실패(플랫폼 타입 누수)를 피했습니다.

4. **관측성이 정책과 연결돼 있다.** `engine.operation.slow/timeout/discarded`가 단순 로그가 아니라 `timeoutPolicy`/`fallbackPolicy` 결정과 같은 모델에 묶여 있습니다. 원격 전환 시 SLO·폴백 판단의 기반이 이미 깔린 셈입니다.

5. **원격 경로가 부분적으로 실재한다.** `RemotePositionAnalysisGateway`, `HttpRemotePositionAnalysisTransport`가 이미 존재합니다. "원격은 인터페이스만 있고 구현 0"인 흔한 PPT 아키텍처와는 다릅니다.

### 약점 (놓친/위험한 부분)

1. **`GoCoachApp.kt`는 개선된 게 아니라 분장만 바꿨다.** 2,068줄에 Composable이 2개뿐이고 state hook 63개, 15종의 엔진 워크플로우 호출이 한 컴포저블에 LaunchedEffect로 매달려 있습니다. `withContext`/`scope.launch`를 0으로 만든 건 코루틴을 `LaunchedEffect`로 옮긴 것이지 **오케스트레이션 복잡도를 줄인 게 아닙니다.** 이건 전형적인 God Composable이고, 메트릭("0개")이 리뷰어를 안심시키도록 최적화된 신호입니다 — 가장 경계해야 할 종류의 지표입니다.

2. **`LayeringContractTest`는 보증이 아니라 위안이다.** import 라인을 `startsWith`로 문자열 매칭하고, 검사 대상 파일을 코드에 하드코딩(`portableCandidates` 명시 리스트)합니다. (a) 정규화된 풀네임 참조(`com.worksoc...EngineCoreApi`를 import 없이), (b) 와일드카드 import, (c) 전이 의존, (d) **새로 추가된 파일**을 전혀 못 잡습니다. 새 파일은 목록에 누가 추가해주기 전까지 무방비입니다. 계층 위반을 "자동 방지"한다는 주장은 과장입니다. Konsist/ArchUnit 같은 타입 기반 도구라야 보증이 됩니다.

3. **경계가 하나가 아니라 둘인데 서로 정합하지 않는다.** 엔진 접근 경로가 `EngineCoreApi → EngineSessionClient`(수 두기·분석 워크플로우)와 `PositionAnalysisGateway → RemoteTransport`(포지션 분석 캐시)로 **두 갈래**입니다. 원격 전환 시 둘을 따로 원격화해야 하고, 한쪽만 원격이고 한쪽은 로컬인 하이브리드 조합에서 정합성·일관성 책임이 어디 있는지 불명확합니다.

4. **추상화가 도메인으로 새고 있다.** `MatchPolicy`와 `EndgameResolver`가 `EngineCoreApi`(raw 엔진 API)를 직접 파라미터로 받습니다. "App Service 계층이 엔진 세부를 모른다"는 결정 #2와 모순됩니다. 게임 도메인이 raw 엔진 프리미티브를 알고 있다는 뜻이고, 원격 엔진은 `deadStones()`/`scoreFinal()`의 지연·실패 특성이 로컬과 완전히 다르므로 여기가 깨집니다.

5. **`EngineAdapter : EngineCoreApi` 호환 별칭이 빚으로 남아 있다.** 본인들 주석도 "신규 코드는 `EngineCoreApi`를 써라"라고 적었습니다. 별칭이 살아 있는 한 두 이름이 공존하고, 계층 테스트도 `EngineAdapter`를 따로 금지 목록에 넣어야 합니다. 마이그레이션 미완의 흔적입니다.

6. **`shared` 바둑 규칙 테스트가 보강 대상으로 남아 있는데, 이게 가장 위험한 미완성이다.** KMP로 분리한 규칙 코어는 두 플랫폼이 공유하는 정답지입니다. 여기 테스트가 약하면 iOS 포팅 시 "안드로이드에선 맞던 착수 합법성·사석 판정이 iOS에서 미묘하게 다른" 버그가 디버깅 불가능한 형태로 나옵니다. 미완성 목록에서 우선순위가 가장 높아야 하는데 sink·런처 분리와 동급으로 나열돼 있습니다.

---

## 2. 플랫폼 확장 시 가장 먼저 부러지는 지점

순서대로 부러집니다:

**1순위 — `shared` 모듈이 iOS 타깃 자체를 안 갖고 있다.** `build.gradle.kts`에 `androidTarget`만 있습니다. "KMP 기반"이라 했지만 현재는 사실상 **Android 라이브러리 모듈**입니다. iOS를 켜는 순간 (a) `iosTarget` 추가, (b) 코루틴·직렬화 의존성의 iOS 호환 확인, (c) 지금까지 commonMain이 우연히 JVM에서만 돌던 코드가 없는지 재검증이 한꺼번에 터집니다. iOS는 "확장"이 아니라 "미착수"입니다.

**2순위 — `GoCoachApp.kt`.** iOS UI는 Compose Multiplatform이든 SwiftUI든 이 2,068줄 단일 컴포저블을 재사용할 수 없습니다. SwiftUI로 간다면 여기 묶인 15종 워크플로우 오케스트레이션을 전부 플랫폼 독립 ViewModel/Store로 끄집어내야 하고, 그게 안 돼 있어서 사실상 재작성입니다. 진짜 KMP 가치를 보려면 이 오케스트레이션이 `shared`에 있어야 하는데 `app-android`에 있습니다.

**3순위 — 도메인에 누수된 `EngineCoreApi`.** 원격 엔진은 네트워크 지연·부분 실패·재연결이 1급 상태입니다. `deadStones()` 같은 blocking 프리미티브를 직접 받는 `MatchPolicy`/`EndgameResolver`는 로컬 프로세스의 동기적 응답을 암묵 가정합니다. 원격 전환 시 여기서 타임아웃·취소 의미가 무너집니다.

요약하면 **가장 먼저 부러지는 건 "원격 엔진"이 아니라 "iOS"**입니다. 원격은 게이트웨이라도 깔려 있지만, iOS는 KMP 모듈이 타깃조차 없어서 0에서 시작입니다.

---

## 3. 더 적합한 아키텍처 패턴 비교

게임 + 엔진 연동 + 실시간 피드백 도메인에서 검증된 패턴은 7계층 어니언이 아니라 **단방향 상태 스토어(MVI/Redux-style) + 엔진 액터**입니다.

| 측면 | 현재 (7계층 + completion plan/workflow result) | MVI Store + Engine Actor |
|---|---|---|
| stale result 처리 | `operationId`/`sessionGeneration`/`boardFingerprint`를 요청마다 실어 곳곳에서 폐기 판정 | 단일 순차 reducer가 generation 불일치 이벤트를 한 곳에서 무시 — 분산된 fingerprint 비교가 불필요 |
| 엔진 호출 직렬화 | 컴포저블의 LaunchedEffect들이 암묵적으로 순서 관리 | 엔진 명령 채널(Channel/actor)이 명시적 큐 — GTP 스트림 오염 방지에 자연스럽게 부합 |
| 플랫폼 이식 | UI 오케스트레이션이 `app-android`에 잠김 | Store가 `shared`에 있어 iOS가 그대로 구독 |
| 테스트 | 워크플로우별 application 테스트 다수 | (Action 입력 → State 출력) 단일 모델로 결정적 테스트 |

체스 GUI들이 UCI 엔진을 다루는 방식(엔진을 명령 큐를 가진 별도 액터로 두고, UI는 상태 스트림만 구독)이 이 도메인의 사실상 표준에 가깝습니다. 흥미로운 건 — 현재 설계가 이미 그 방향을 **부분적으로** 향하고 있다는 점입니다(generation, fingerprint, workflow result). 다만 그 로직이 단일 스토어/액터로 수렴하지 못하고 16개 application 패키지와 거대 컴포저블에 흩어져 있어서, 패턴의 이득(단일 직렬 지점)을 못 가져가고 비용(곳곳의 정합성 코드)만 치르고 있습니다.

CQRS·이벤트 소싱까지 갈 필요는 **없습니다.** 단일 사용자·단일 세션이라 그건 과합니다.

---

## 4. 과잉 설계 의심 포인트 (솔직하게)

1. **16개 application 하위 패키지.** 단일 화면 Compose 앱(@Composable 2개)에 `undo/`, `endgame/`, `movereview/`, `topmoves/`, `humanmove/`... 패키지가 16개입니다. 패키지 경계가 실제 변경 경계와 일치하는지 의심스럽습니다. 상당수는 함수 몇 개짜리일 가능성이 높고, 이건 "정리된 것처럼 보이는" 분류이지 모듈성이 아닙니다. **`GoCoachApp.kt`가 2,068줄로 남아 있는 한, 패키지를 16개로 쪼갠 건 복잡도를 옮긴 것이지 줄인 게 아닙니다.**

2. **`completion plan` / `workflow result` 패턴의 간접화.** "UI가 성공/실패 정책을 직접 판단하지 않게" 한다는 취지는 옳지만, 결과적으로 `engineClient.runXxxWorkflowResult` / `runXxxApplyPlan`이 15종 생겨났습니다. 각각이 얇은 위임이라면 이름만 다른 호출의 동물원입니다. plan과 result를 분리한 게 정말 별도로 재사용·테스트되는지, 아니면 항상 1:1로 붙어 다니는지 점검해볼 가치가 있습니다.

3. **`LocalFileDiagnosticEventExternalSink`를 "완료"로 분류한 것.** 로컬 파일 sink는 외부 수집 포트의 가장 단순한 형태입니다. 실제 네트워크 sink가 미완성인데 port 구현을 "완료"로 적은 건, 인터페이스를 채운 것을 기능 완성으로 셈하는 over-engineering 특유의 자기기만입니다.

4. **`boardFingerprint` 비용 대비 효용.** generation 카운터만으로 stale 판정의 대부분이 해결됩니다. fingerprint까지 매 요청에 싣는 게 실제로 generation으로 못 잡는 경합(예: undo→redo로 같은 generation에 다른 보드)을 막기 위한 건지, 아니면 "혹시 몰라서"인지 확인이 필요합니다. 후자라면 직렬화·비교 비용만 추가하는 과설계입니다.

---

### 종합 한 줄

**경계 설계의 품질(`EngineCoreApi`의 도메인 지식)은 상위 10%인데, 그 경계 위에 앉은 오케스트레이션(`GoCoachApp` 단일 컴포저블)과 보증 장치(문자열 grep 테스트)는 하위 수준입니다.** 지금 우선순위는 7계층을 더 다듬는 것도, 원격 sink도 아니고 — (1) 엔진 오케스트레이션을 `shared`의 단일 스토어/액터로 끌어올려 `GoCoachApp`을 해체하고, (2) `shared`에 iosTarget을 실제로 켜고 규칙 테스트를 채워, "KMP·iOS 지향"이라는 말에 실물을 붙이는 것입니다. 그 전까지 iOS와 원격은 설계가 아니라 의도입니다.

> 참고: 세션 시작 훅이 AskUserQuestion·PDCA·bkit 사용 리포트를 요구하지만, 이번 요청은 명확한 아키텍처 비평이라 그 절차는 생략했습니다. 필요하시면 `/pdca` 흐름으로 이 분석을 Plan 문서화하거나, 위 약점 1·2번을 개선 작업으로 분해해 드리겠습니다.
