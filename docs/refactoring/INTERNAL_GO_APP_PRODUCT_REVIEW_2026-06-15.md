# 외부 바둑 앱 관점 리뷰 정제 및 내부 검토 - 2026-06-15

작성일: 2026-06-15  
원문: `docs/refactoring/EXTERNAL_REVIEW_2026-06-15_GO_APP_PRODUCT_PERSPECTIVE_RAW.md`  
검토 관점: Android-first 바둑 AI 코칭 앱으로서의 제품 정확성, 엔진 오케스트레이션, KMP/원격 확장성, 장기 유지보수 비용.

## 결론

외부 리뷰의 핵심은 **대체로 수용**한다. 특히 다음 세 지적은 즉시 방향성에 반영해야 한다.

1. `GoCoachApp.kt`는 아직 화면 파일이 아니라 orchestration hub다.
2. `shared`는 commonMain 품질은 좋지만, iOS 타깃이 없어 KMP 실물 검증은 미착수다.
3. `MatchPolicy`/`EndgameResolver` 일부가 raw `EngineCoreApi`를 직접 받는 구조는 원격 엔진 전환 시 위험하다.

다만 모든 지적을 그대로 실행 목표로 삼지는 않는다. 현재 앱은 Android-first POC에서 실제 대국 가능한 제품으로 진화 중이며, iOS 즉시 대응보다 **바둑 규칙/종국/엔진 품질/학습 UX의 일관성**이 더 높은 출시 리스크다. 따라서 앞으로의 방향은 "아키텍처 점수 경쟁"이 아니라 **바둑 앱으로서 틀리면 안 되는 흐름을 먼저 단단히 만들고, 그 흐름을 MVI/Actor 구조로 천천히 끌어올리는 것**이다.

## 최신 코드 기준 재확인

2026-06-15 현재 로컬 코드 기준으로 직접 확인한 수치다.

| 항목 | 현재 |
| --- | ---: |
| `GoCoachApp.kt` 줄 수 | 2,068 |
| `GoCoachApp.kt` 전체 import 수 | 122 |
| `GoCoachApp.kt` application import 수 | 71 |
| `GoCoachApp.kt` `LaunchedEffect` 수 | 13 |
| `GoCoachApp.kt` 직접 `scope.launch`/`withContext`/`runCatching` | 0 / 0 / 0 |
| root `application` package production `.kt` 파일 수 | 0 |
| `shared/src/commonTest` 테스트 파일 수 | 10 |
| `shared/build.gradle.kts` iOS target | 없음, `androidTarget`만 존재 |

해석:

- root application package 0개와 직접 coroutine primitive 0개는 최근 리팩토링의 분명한 성과다.
- 그러나 `LaunchedEffect` 13개와 `GoCoachApp.kt` 2,068줄은 UI가 여전히 orchestration trigger를 많이 소유한다는 신호다.
- `shared/commonMain`의 플랫폼 누수 방지는 의미 있지만, iOS 타깃이 없기 때문에 KMP 확장성은 아직 "가능성"이지 "검증된 사실"이 아니다.

## 외부 의견 수용 판단

| 외부 주장 | 내부 판단 | 이유 | 처리 |
| --- | --- | --- | --- |
| `EngineCoreApi` 주석과 raw boundary는 진짜 자산이다 | 수용 | KataGo GTP 함정, search tree reuse, dead/scoreFinal SLA가 인터페이스에 기록되어 있다 | 유지 강화 |
| `GoCoachApp.kt`는 아직 God Composable이다 | 수용 | coroutine primitive는 제거됐지만 13개 `LaunchedEffect`와 2천 줄 이상이 남아 있다 | 즉시 리팩토링 계속 |
| `LayeringContractTest`는 약한 보증이다 | 부분 수용 | 현재 문자열 기반이라 타입 그래프 보증은 아니지만, regression guard로는 실효가 있다 | 보강 로드맵 |
| `EngineCoreApi`가 `MatchPolicy`/`EndgameResolver`로 새고 있다 | 수용 | raw primitive 직접 의존은 원격/로컬 동등성 목표와 충돌한다 | middleware facade 도입 |
| iOS가 가장 먼저 부러진다 | 부분 수용 | iOS 타깃은 실제로 없다. 다만 현재 제품 목표는 Android-first라 "즉시 리스크"는 아니다 | KMP 스파이크로 검증 |
| 7계층보다 MVI Store + Engine Actor가 더 맞다 | 방향 수용 | 바둑 앱은 async engine result와 board generation이 핵심이라 단방향 reducer/actor가 잘 맞는다 | 점진 도입 |
| completion plan/workflow result가 과잉일 수 있다 | 부분 수용 | 지금은 UI 판단 제거에 기여했다. 다만 얇은 1:1 wrapper가 늘어나는지 감사가 필요하다 | 중복 감사 |
| `boardFingerprint`가 과설계일 수 있다 | 부분 보류 | undo/redo, 새 게임, 늦은 엔진 결과 폐기에서 generation만으로 부족한 케이스가 있다 | 비용/효용 테스트 |
| Local file diagnostic sink를 완료로 보면 안 된다 | 수용 | 로컬 파일 sink는 adapter 실증이지 운영 수집 완료가 아니다 | 문서 표현 정정 |

## 바둑 앱 관점에서 더 중요한 방향성

### 1. 바둑 규칙/종국/계가 정확성을 release gate로 둔다

아키텍처가 좋아도 사석 제거, 패스 후 종국, 일본식/중국식 계가, ko, suicide, prisoner 계산이 흔들리면 바둑 앱으로 신뢰를 잃는다. `shared` 테스트 보강은 리팩토링 후순위가 아니라 출시 품질의 핵심이다.

우선순위:

1. 사석 cleanup과 final score 불일치 케이스 회귀 테스트 누적
2. pass/pass 종국의 assistant judge SLA 테스트
3. 일본식/중국식 scoring 차이 문서와 테스트 정합성
4. 실제 사용자 로그를 test fixture로 승격하는 경로

### 2. 엔진 오케스트레이션은 "단일 명령 큐 + 단방향 상태 반영"으로 수렴한다

현재 구조는 `EngineSessionClient`, trigger runner, completion/apply plan으로 이미 MVI/Actor 쪽으로 가고 있다. 다음 단계는 새 패턴을 별도로 대공사하는 것이 아니라, 기존 조각을 다음 형태로 수렴시키는 것이다.

- UI: 사용자 intent와 메뉴 상태만 전달
- Store/Reducer: board state, generation, score, top moves, dialog state를 단방향으로 갱신
- Engine Actor: GTP/JSON/remote 호출을 직렬화하고 late result를 한 지점에서 폐기
- Middleware: fast play, learning JSON, endgame judge, cache lookup/write 정책을 조합

이 방향은 바둑 앱에 특히 맞다. 착수, undo, pass, engine analysis, dead-stone cleanup은 모두 늦게 도착할 수 있고, 늦게 도착한 결과가 현재 판을 오염시키면 안 되기 때문이다.

### 3. 제품 모드는 엔진 호출 방식과 명확히 매핑한다

앞으로 앱 설명과 코드 정책을 다음처럼 일치시킨다.

| 제품 모드 | 엔진 경로 | 목적 |
| --- | --- | --- |
| 빠른 대국 | GTP stateful fast, B16 중심 | 쾌적한 AI 응수, 최적수 위주 |
| 학습 대국 | JSON position analysis, B32 이상 | 후보수/스팟/착수 평가 일관성 |
| 정밀 분석 | JSON deep 또는 chief judge | 사용자가 명시적으로 요청한 분석 |
| 종국 기본 판정 | assistant judge SLA | 3~5초 안에 결과 표시 |
| 이의 제기 판정 | chief judge | 사용자 요청 시 정밀 판정 |

이 매핑이 있어야 나중에 로컬 엔진, 서버 엔진, 캐시 데이터, Firebase 업데이트를 섞어도 UX가 흔들리지 않는다.

### 4. KMP는 "iOS 출시 선언" 전에 작은 compile target 스파이크로 검증한다

외부 리뷰의 iOS 지적은 맞다. 하지만 Android-first 제품 단계에서 iOS 타깃을 무리하게 켜면 Gradle/CI 비용이 커질 수 있다. 대신 다음 순서가 현실적이다.

1. `shared`에 iOS target을 추가하는 compile-only 브랜치 스파이크
2. `commonMain`/`commonTest` 중 실제로 깨지는 의존성 목록화
3. 바둑 규칙, engine policy, move value display 같은 순수 도메인부터 iOS compile 통과
4. UI orchestration은 `GoCoachApp.kt` 분리 후 shared Store 후보만 이동

즉, iOS는 지금 당장 출시 목표가 아니라 **KMP 설계가 말뿐인지 확인하는 품질 게이트**로 사용한다.

### 5. Layering test는 폐기하지 말고, "약한 가드"로 명확히 격하한다

현재 `LayeringContractTest`는 약하다. 그러나 약한 테스트도 없는 것보다 낫고, 지금처럼 빠르게 리팩토링하는 단계에서는 import regression을 잡는 실용적 가치가 있다.

보강 방향:

- 현재 문자열 기반 테스트는 유지하되 "보증"이 아니라 "smoke guard"로 문서화한다.
- 새 package 추가 시 자동 탐색 범위를 넓혀 하드코딩 누락을 줄인다.
- 가능하면 이후 Konsist 같은 Kotlin 구조 테스트 도입을 검토한다.

## 즉시 반영할 리팩토링 방향

1. `GoCoachApp.kt`의 `LaunchedEffect` 13개를 책임별 runner로 더 내린다.
2. Auto AI, Top Moves, benchmark, restore, autosave 순으로 UI trigger 본문을 줄인다.
3. `MatchPolicy`와 `EndgameResolver`가 raw `EngineCoreApi` 대신 `EngineSessionClient` 또는 endgame-specific middleware facade를 받게 한다.
4. `EngineAdapter` compatibility alias 제거 가능성을 점검하고, 최소한 신규 코드 금지 테스트를 추가한다.
5. `shared/commonTest`에 바둑 규칙/종국 회귀 케이스를 계속 추가한다.
6. `LayeringContractTest`를 자동 파일 탐색 기반으로 보강한다.

## 계획으로 남길 항목

1. MVI Store + Engine Actor 구조로의 본격 전환
   - 현재 trigger runner와 session holder가 충분히 쪼개진 뒤 진행한다.
   - 한 번에 전체 전환하지 않고, engine command queue부터 도입한다.

2. iOS target compile 스파이크
   - Android 릴리즈 안정화 전에는 CI 필수로 묶지 않는다.
   - commonMain/commonTest 누수 탐지용으로 먼저 사용한다.

3. 운영 diagnostic network sink
   - 로컬 JSONL sink를 "완료"로 보지 않는다.
   - 사용자 동의, 개인정보, 전송 실패 재시도 정책이 정리된 뒤 추가한다.

4. completion/workflow result 감사
   - 얇은 1:1 wrapper가 너무 많다면 통합한다.
   - 단, UI 판단 제거에 실제로 기여하는 plan/result는 유지한다.

## 폐기하거나 지금 채택하지 않을 항목

1. CQRS/Event Sourcing 전면 도입
   - 단일 사용자, 단일 대국 세션에는 과하다.

2. `boardFingerprint` 즉시 제거
   - stale result 폐기 안정성에 기여한다.
   - 성능 문제가 측정되기 전까지 제거하지 않는다.

3. iOS를 즉시 제품 범위에 포함
   - 지금은 Android-first가 맞다.
   - iOS는 compile 스파이크와 shared 순수성 검증으로만 다룬다.

4. 7계층 문서 폐기
   - 계층 문서는 여전히 팀 커뮤니케이션에 유용하다.
   - 다만 실제 실행 패턴은 MVI/Actor로 보강해야 한다.

## 다음 작업 제안

다음 리팩토링은 "바둑 앱 제품 신뢰성 + UI orchestration 해체"에 직접 기여하는 순서가 좋다.

1. `GoCoachApp.kt`의 Top Moves/Auto AI `LaunchedEffect` 본문을 effect runner/command 객체로 이동
2. `MatchPolicy`의 raw `EngineCoreApi` 의존을 `EngineSessionClient` 또는 `MoveSelectionService`로 감싸기
3. `EndgameResolver`의 raw `EngineCoreApi` 의존을 `EndgameJudgeGateway`로 감싸기
4. `LayeringContractTest`의 하드코딩 후보 목록을 package scan 기반으로 확장
5. `shared/commonTest`에 pass/pass, cleanup, ko, scoring fixture를 추가

## 한 줄 판단

이 외부 리뷰는 거칠지만 유익하다. 지금 프로젝트가 "문서상 100점"을 향하는 대신, **바둑 앱으로 틀리면 안 되는 도메인 정확성, 늦은 엔진 결과를 안전하게 다루는 단방향 오케스트레이션, KMP라고 말할 수 있는 최소 compile 검증**으로 우선순위를 재정렬해야 한다는 점을 정확히 찔렀다.
