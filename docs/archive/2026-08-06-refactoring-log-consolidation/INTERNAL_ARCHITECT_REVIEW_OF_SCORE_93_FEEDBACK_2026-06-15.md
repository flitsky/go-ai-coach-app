# 93점 외부 평가에 대한 내부 아키텍처 리뷰 - 2026-06-15

작성일: 2026-06-15  
대상 문서: `docs/refactoring/EXTERNAL_REVIEW_2026-06-15_ARCHITECTURE_SCORE_93.md`  
관점: 대규모 플랫폼 확장, 원격/로컬 엔진 이중화, AI Agent 협업, 장기 유지보수 비용을 기준으로 한 냉정한 설계 리뷰.

## 결론

외부 검토의 핵심 지적은 **대체로 수용**한다.

현재 프로젝트는 정책 타입, completion plan, stale result discard, diagnostic schema, KMP 후보 분류 측면에서는 매우 높은 수준까지 왔다. 그러나 외부 검토자가 지적한 것처럼, 아직 `GoCoachApp.kt`가 orchestration hub로 남아 있고 application 루트 package에 파일이 많이 쌓여 있으며 KMP 물리 이동은 실제로 시작하지 않았다.

따라서 내부 평가는 다음처럼 분리한다.

| 평가 축 | 점수 | 의미 |
| --- | ---: | --- |
| 리팩토링 진행률 | 99.72/100 | 최근 목표였던 completion/apply runner 분리는 거의 완료됐다. |
| 플랫폼 아키텍처 완성도 | 93/100 | 물리 package, KMP 이동, adapter 구현은 아직 실행 증명이 부족하다. |
| 제품 릴리즈 관점 안정도 | 90~93/100 | 앱 기능은 안정화됐지만 운영 관측성/원격 전환 실험은 더 필요하다. |

즉, 기존 99점대 평가는 "현재 리팩토링 배치 목표의 달성도"이고, 외부 93점 평가는 "마켓 이후 플랫폼화까지 포함한 구조 완성도"다. 둘은 충돌하지 않는다.

## 현재 재확인 수치

외부 검토 이후 최신 커밋 기준으로 직접 확인한 수치다.

| 항목 | 현재 확인 |
| --- | ---: |
| git commit 수 | 362 |
| `GoCoachApp.kt` 줄 수 | 2,195 |
| Android main Kotlin 파일 수 | 81 |
| Android test Kotlin 파일 수 | 54 |
| `GoCoachApp.kt` 전체 import 수 | 229 |
| `GoCoachApp.kt` application import 수 | 179 |
| `GoCoachApp.kt` `scope.launch` 수 | 6 |
| `GoCoachApp.kt` 전체 `withContext` 수 | 11 |
| `GoCoachApp.kt` IO/runCatching 직접 지점 | 문서상 9 |

수치 해석:

- 줄 수는 큰 문제지만, 더 큰 문제는 import fan-in과 orchestration 소유권이다.
- `withContext` 수가 줄어든 것은 의미가 있으나, `scope.launch`와 effect scheduling 책임이 남아 있다.
- application 파일 수 증가는 무조건 나쁜 것은 아니다. 다만 package 없이 루트에 쌓이면 Agent 협업과 탐색 비용이 빠르게 증가한다.

## 즉시 적용해야 할 부분

### 1. `GoCoachApp.kt` 분리 목표를 줄 수가 아니라 책임 제거로 재정의

수용한다.

다음 리팩토링은 `GoCoachApp.kt`를 단순히 1,000줄 이하로 줄이는 것이 아니라 아래 세 책임을 분리하는 데 집중해야 한다.

1. **Controller**
   - 사용자 이벤트를 application request로 변환한다.
   - 화면 state mutation을 직접 수행하지 않고 holder/reducer로 위임한다.

2. **StateHolder**
   - Compose state와 application state apply bridge를 소유한다.
   - 현재 `GameSessionUiStateHolder`를 실제 UI state 묶음으로 확장한다.

3. **EffectLauncher**
   - `scope.launch`, `runEngineIo`, engine operation lifecycle 실행을 소유한다.
   - UI composable은 effect launcher에 command를 넘긴다.

즉시 실행 기준:

- `runEngineIo()`를 UI local helper에서 effect runner 또는 app-service helper로 이동한다.
- 남은 `scope.launch` 6개를 operation kind별로 분류하고 최소 1~2개를 launcher helper로 이동한다.
- `GoCoachApp.kt` application import 수 179개를 줄이는 facade 또는 controller boundary를 설계한다.

### 2. application 하위 package 도입

수용한다.

지금은 파일을 잘게 분리했지만 `application` 루트가 너무 밀집해 있다. 다음 단계에서는 물리 package를 도입해야 한다.

추천 package 초안:

| package | 후보 파일 |
| --- | --- |
| `application.engine` | `EngineOperationPolicy.kt`, `EngineOperationResultApplication.kt`, `EngineOperationScope.kt`, `EngineSessionLifecycleApplication.kt`, `EngineStartupApplication.kt` |
| `application.score` | `ScoreDisplayApplication.kt`, `ScoreDisplayFormatterApplication.kt`, `ScoreEstimateRunnerApplication.kt`, `ScoreSyncCompletionApplication.kt`, `ScoreSyncRunnerApplication.kt` |
| `application.topmoves` | `TopMovesApplication.kt`, cache 관련 policy 파일 |
| `application.autoai` | `AutoAiPolicyApplication.kt`, `AutoAiRunnerApplication.kt`, `AutoAiCompletionApplication.kt` |
| `application.diagnostic` | `DiagnosticEventModel.kt`, `DiagnosticEventApplication.kt`, `DiagnosticEventObserverApplication.kt` |
| `application.session` | `GameSessionController*`, `GameSessionUiStateHolderApplication.kt`, startup/restore/session state 관련 파일 |

즉시 실행 기준:

- 한 번에 전체 package 이동을 하지 않는다.
- 먼저 `diagnostic` 또는 `score`처럼 의존이 비교적 분명한 영역부터 이동한다.
- `LayeringContractTest`에 package별 금지 import 규칙을 추가한다.

### 3. KMP 물리 이동은 "실제 이동 1건"을 다음 증명 목표로 둔다

부분 수용한다.

외부 검토의 "최소 2개 파일 이동" 제안은 방향은 맞지만, 숫자를 우선하면 잘못된 이동이 생길 수 있다. 다음 목표는 최소 1건의 실제 이동을 통해 Gradle/KMP 비용을 검증하는 것이다.

1순위 후보:

- `DiagnosticEventModel.kt`
  - 외부 의존이 거의 없다.
  - commonMain 이동 가능성이 가장 높다.

2순위 후보:

- `EngineOperationPolicy.kt`
  - `GameState`, `Ruleset`, `analysisFingerprint` 의존이 shared에 있으므로 이동 가능성이 높다.

조건부 후보:

- `ScoreSyncCompletionApplication.kt`
  - `ScoreEstimateDisplayPlan`, `EngineOperationRequest`, `EngineOperationResultGuard` 등 application 내부 타입 의존이 있어 package 정리 후 이동하는 편이 안전하다.

즉시 실행 기준:

- KMP 이동 후보 1개를 실제 shared/common 또는 별도 middleware-common 후보 위치로 옮기는 미니 스파이크를 수행한다.
- 성공하면 두 번째 파일을 이동한다.
- 실패하면 의존성 목록과 차단 사유를 문서화한다.

### 4. 외부 피드백을 점수 경쟁이 아니라 실행성 지표로 수용

수용한다.

외부 93점은 현재 구조가 나쁘다는 뜻이 아니다. 문서화된 비전 대비 실행된 물리 구조가 아직 부족하다는 지적이다. 앞으로 문서에는 "검토됨", "정책 설계됨", "테스트됨", "물리 이동됨", "운영 adapter 구현됨"을 구분해야 한다.

즉시 실행 기준:

- refactoring worklist의 각 항목에 "문서화", "코드 반영", "테스트", "물리 이동", "운영 adapter" 상태를 구분한다.

## 앞으로 계획으로 남길 부분

### 1. diagnostic 외부 수집 adapter 구현

로드맵으로 둔다.

port와 export policy는 이미 생겼지만, 지금 Firebase나 서버 전송 adapter를 붙이면 운영/개인정보/사용자 동의 UX가 같이 커진다. 우선은 다음 순서가 맞다.

1. warning/critical event 종류를 더 정리한다.
2. debug report와 diagnostic event 연결을 안정화한다.
3. 사용자 동의 UX를 설계한다.
4. 이후 Firebase Crashlytics, Firestore, 자체 API 중 하나를 adapter로 붙인다.

### 2. 원격 엔진 driver spike

로드맵으로 둔다.

`PositionAnalysisGateway`와 remote protocol spike가 있으므로 방향은 맞다. 다만 현재는 로컬 엔진 UX와 캐시 정책이 더 중요하다. 원격 엔진은 아래 조건 이후가 맞다.

- local engine search mode 정책 안정화
- cache key/range normalization 정책 정리
- diagnostic latency/timeout event 안정화
- 최소 1개 KMP 물리 이동 완료

### 3. 1,000줄 이하 목표

장기 지표로 둔다.

`GoCoachApp.kt` 1,000줄 이하 목표는 유용한 압박 지표지만, 단기 목표로 강제하면 의미 없는 wrapper와 premature abstraction이 늘 수 있다. 우선 목표는 다음과 같이 잡는다.

- 1차: engine operation scheduling 책임 제거
- 2차: player setup/menu state holder 분리
- 3차: board interaction handler 분리
- 4차: 1,500줄 이하
- 5차: 1,000~1,200줄 도달

### 4. application package 전면 재배치

로드맵이지만 빠르게 착수한다.

전면 이동은 import churn이 크다. 다음 리팩토링에서 한 영역만 이동하고, 테스트와 IDE import 안정성을 확인한 뒤 반복하는 것이 낫다.

추천 순서:

1. `diagnostic` package
2. `engine` policy package
3. `score` package
4. `topmoves` package
5. `autoai` package

## 폐기하거나 보류할 부분

### 1. "파일 수 증가 자체가 악화"라는 단정

폐기한다.

파일 수 증가는 책임 분리를 진행할 때 자연스럽게 발생한다. 문제는 파일 수가 아니라 package/ownership 없이 루트에 쌓이는 것이다. 따라서 파일 수를 줄이는 방향이 아니라 package charter와 boundary test로 해결한다.

### 2. "KMP 후보 2개를 즉시 옮겨 점수를 올린다"는 접근

부분 폐기한다.

물리 이동은 중요하지만 점수 획득을 위해 이동하면 잘못된 모듈 경계가 굳어진다. 최소 1개 파일로 Gradle/KMP 비용을 검증한 뒤, 의존성 정리 결과에 따라 추가 이동한다.

### 3. "GoCoachApp 줄 수를 즉시 1,000줄 이하로 만든다"는 목표

폐기한다.

현재 단계에서 1,000줄 이하를 단기 목표로 잡으면 거대한 facade 또는 callback object가 생길 위험이 높다. 줄 수는 후행 지표로 두고, 우선 orchestration ownership 제거를 목표로 한다.

### 4. "외부 수집 adapter를 바로 구현한다"는 접근

보류한다.

운영 관측성 adapter는 개인정보, 사용자 동의, 전송 실패 재시도, 네트워크 비용이 같이 따라온다. 지금은 port와 schema를 더 안정화하는 것이 우선이다.

## 다음 리팩토링 권장 순서

1. **EffectLauncher 미니 도입**
   - `runEngineIo()`와 남은 engine operation launch 중 하나를 UI 밖으로 이동한다.
   - 목표: `GoCoachApp.kt`의 직접 `withContext`/`scope.launch` 책임 축소.

2. **application package 1차 이동**
   - `diagnostic` 또는 `score` 중 하나를 하위 package로 이동한다.
   - 목표: application 루트 밀집 해소 시작.

3. **KMP 물리 이동 1차 스파이크**
   - `DiagnosticEventModel.kt`를 shared/common 또는 별도 common 후보 위치로 이동 가능한지 실제 확인한다.
   - 목표: 문서상 후보를 실행 가능한 증명으로 전환.

4. **GoCoachApp import fan-in 축소**
   - application import 179개를 줄이기 위한 facade/controller boundary를 도입한다.
   - 목표: AI Agent가 UI, score, engine, diagnostic을 독립적으로 작업하기 쉬운 구조로 전환.

5. **문서 상태 라벨 도입**
   - refactoring worklist 항목에 `문서화됨`, `코드 반영`, `테스트됨`, `물리 이동`, `운영 adapter` 상태를 기록한다.

## 최종 판단

외부 검토는 냉정하고 유효하다. 특히 "설계했으나 실행하지 않은 것"이라는 지적은 반드시 수용해야 한다.

다만 다음 단계는 급격한 대수술이 아니라, 실행 가능한 증명을 작은 단위로 쌓는 방식이어야 한다.

- `GoCoachApp.kt`는 줄 수보다 orchestration ownership을 먼저 줄인다.
- application 루트 package 밀집은 즉시 해소를 시작한다.
- KMP 물리 이동은 최소 1개 파일로 실제 비용을 검증한다.
- diagnostic 외부 adapter는 지금 구현하지 않고 schema/consent/export policy를 더 고정한다.

이 방향이면 외부 93점 지적을 수용하면서도, 지금까지 쌓은 completion/apply runner 구조를 무너뜨리지 않고 플랫폼 아키텍처 점수를 실질적으로 끌어올릴 수 있다.
