# 96점 외부 평가에 대한 내부 아키텍처 리뷰 - 2026-06-15

작성일: 2026-06-15  
대상 원문: `docs/refactoring/EXTERNAL_REVIEW_2026-06-15_ARCHITECTURE_SCORE_96_RAW.md`  
기준 커밋: `b4fd879 Refine preferences and engine session boundaries`  
관점: 대규모 플랫폼 확장, 원격/로컬 엔진 병행, KMP 이동 가능성, AI Agent 협업 안정성, 운영 관측성.

## 결론

외부 평가의 **96/100 판정은 조건부로 수용**한다.

93점 평가 이후 실제로 의미 있는 개선이 있었다. `GoCoachApp.kt`에서 `withContext`, `scope.launch`, `runCatching` 직접 사용이 0개가 됐고, application 하위 package가 도입됐으며, `DiagnosticEventModel`과 `EngineOperationPolicy`가 shared/common으로 이동했다. 또한 `LocalFileDiagnosticEventExternalSink`가 생겨 외부 수집 port가 단순 설계에만 머물지는 않게 됐다.

다만 96점은 "방향성과 리팩토링 진척" 점수로 해석해야 한다. 제품 플랫폼 완성도 관점에서는 아직 `GoCoachApp.kt`가 2,086줄이고, `LaunchedEffect`가 UI 파일에 남아 있으며, 실제 네트워크 diagnostic sink와 원격 엔진 driver는 아직 production path가 아니다.

## 현재 재검증 수치

현 repo에서 직접 확인한 수치다.

| 항목 | 현재 값 | 판단 |
| --- | ---: | --- |
| 기준 커밋 | `b4fd879` | ext.11 완료 직후 |
| `GoCoachApp.kt` 줄 수 | 2,086 | 여전히 큼 |
| `GoCoachApp.kt` 전체 import 수 | 127 | 외부 평가와 일치 |
| `GoCoachApp.kt` application import 수 | 82 | 더 줄일 여지 큼 |
| `GoCoachApp.kt` shared import 수 | 13 | 직접 domain model 접근이 아직 있음 |
| `withContext` / `scope.launch` / `runCatching` 직접 사용 | 0 | 핵심 개선 |
| `LaunchedEffect` 텍스트 출현 | 13 | import/return 포함, call site는 별도 정리 필요 |
| `shared/src/commonTest` 파일 수 | 8 | 보강 여지 있음 |
| `shared/src/commonMain` 파일 수 | 20 | KMP 확장 시작됨 |
| root `application` 파일 수 | 8 | 개선됐지만 5개 이하 목표 가능 |

## 외부 피드백 판단표

| 의견 | 판단 | 이유 | 반영 위치 |
| --- | --- | --- | --- |
| 93점 이후 96점으로 상승 | 부분 채택 | 개선 폭은 맞다. 다만 96은 리팩토링 진척 점수로 봐야 하며 플랫폼 완성도 절대 점수는 조금 보수적으로 보는 것이 낫다. | 본 문서 결론 |
| application 하위 package 16개 도입 | 채택 | 실제로 도메인별 package가 생겼고 Agent 협업 단위가 명확해졌다. | 완료 항목 |
| `withContext`/`scope.launch` 0개 | 채택 | UI가 engine IO scheduling을 직접 소유하지 않는 구조가 됐다. | 완료 항목 |
| `GoCoachApp` import 229 -> 127 | 채택 | 의미 있는 진전이다. 다만 application import 82개가 남아 facade/action boundary가 더 필요하다. | ext.12 후보 |
| KMP 물리 이동 2건 | 채택 | `DiagnosticEventModel`, `EngineOperationPolicy` 이동은 실제 성과다. 다음은 commonTest와 추가 이동이 필요하다. | ext.12 후보 |
| External Sink adapter 구현 완료 | 부분 채택 | `LocalFileDiagnosticEventExternalSink`는 adapter로 인정한다. 그러나 네트워크/운영 수집 adapter는 아직 아니다. | 로드맵 |
| `LaunchedEffect` 이관, import 40개대 | 부분 채택 | 방향은 맞지만 import 40개대는 숫자 목표가 앞서면 wrapper만 늘 수 있다. effect lifecycle 책임 제거를 우선한다. | 즉시 적용 |
| KMP 이동 파일 `commonTest` 추가 | 채택 | shared/common으로 이동한 모델의 회귀 테스트가 부족하면 이동 안정성이 낮다. 난이도도 낮다. | 즉시 적용 |
| shared 바둑 규칙 테스트 보강 | 채택 | 앱의 핵심 신뢰도는 바둑 규칙이다. shared rule regression은 점수보다 제품 안정성 측면에서 중요하다. | 즉시 적용 |
| 실제 네트워크 diagnostic sink adapter | 보류 | 개인정보, 사용자 동의, 전송 정책, 실패 재시도 정책이 필요하다. 지금은 설계와 local adapter 안정화가 우선이다. | 로드맵 |
| "100점까지 4점" 표 | 보류 | 표의 기여도 합은 2.5+1.0+0.5+1.0=5.0이다. 점수 산식은 엄밀하지 않으므로 우선순위 신호로만 사용한다. | 본 문서 주석 |

## 즉시 적용해야 할 부분

### 1. shared/commonTest 보강

채택한다.

KMP로 옮긴 파일은 Android unit test가 아니라 commonTest에서 기본 계약을 가져야 한다. 지금 당장 효과가 큰 테스트 후보는 다음이다.

- `DiagnosticEventModelTest`
  - severity, category, payload 기본 생성 규칙 확인
  - warning/critical event model의 직렬화 전 안정성 확인
- `EngineOperationPolicyTest`의 common 이동 또는 보강
  - timeout policy
  - discard/apply decision
  - stale result guard의 shared policy 불변식

목표는 "테스트 파일 수 증가"가 아니라 shared model이 Android app module 없이도 검증되는 구조다.

### 2. shared 바둑 규칙 regression 테스트 보강

채택한다.

우리 앱은 UI 리팩토링보다 바둑 규칙 신뢰도가 더 중요하다. 엔진 점수와 로컬 규칙이 충돌했던 히스토리가 있으므로, 다음을 shared/commonTest 또는 shared Android/common 양쪽에 보강할 가치가 있다.

- 단수/따냄/연쇄 사석 제거
- 자살수 금지
- ko 금지
- pass/pass 상태 표현
- Japanese/Chinese scorer의 전제 차이 문서화 및 regression

단, 일본식 계가에서 죽은 돌 판정은 엔진 final_status_list와 결합되는 영역이므로 local scorer 테스트와 engine-assisted endgame 테스트를 구분해야 한다.

### 3. `LaunchedEffect` 책임 분류

부분 채택한다.

`withContext`와 `scope.launch`가 0이 된 뒤 남은 UI side-effect primitive는 `LaunchedEffect`다. 이것을 무조건 없애기보다 다음 세 유형으로 분류해야 한다.

| 유형 | 처리 방향 |
| --- | --- |
| Compose lifecycle bridge | UI에 남겨도 됨 |
| app startup / restore / benchmark trigger | effect launcher 또는 app-service lifecycle runner로 이동 |
| engine/session event reaction | controller/action boundary로 이동 |

즉시 목표는 `LaunchedEffect` 개수 감소가 아니라 "UI가 어떤 도메인 이벤트를 왜 실행하는지"를 application layer request로 표현하는 것이다.

### 4. root application 잔여 파일 축소

채택한다.

현재 root application 파일은 8개다. ext.12에서는 5개 이하까지 낮추는 것이 합리적이다.

우선 후보:

- `ScoringRuleApplication.kt` -> `application/score`
- `PromptPriorityApplication.kt` -> `application/prompt`
- `GameSessionApplication.kt` -> `application/session` 또는 `application/game`

engine operation facade 계열은 shared facade/type compatibility 때문에 바로 없애기보다 마지막에 정리한다.

## 앞으로 계획으로 남길 부분

### 1. 실제 네트워크 diagnostic sink

로드맵으로 둔다.

`LocalFileDiagnosticEventExternalSink`가 이미 있으므로 다음은 곧바로 Firebase/HTTP를 붙이는 것이 아니라, 전송 가능한 event taxonomy와 사용자 동의 UX를 먼저 정해야 한다.

추천 순서:

1. warning/critical event code 목록 확정
2. debug report와 diagnostic event export payload 통합
3. 사용자 동의 팝업 문구/재시도 정책 설계
4. `DiagnosticEventExternalSinkPort`의 HTTP/Firebase adapter spike
5. 네트워크 실패 시 local spool 정책 추가

### 2. import 40개대 목표

장기 지표로 둔다.

import 수는 구조 악취를 드러내는 좋은 지표지만, 자체가 목적은 아니다. import 40개대는 다음 조건이 갖춰진 후 자연스럽게 달성해야 한다.

- UI event handler facade
- game screen state holder facade
- lifecycle effect launcher
- debug/report/benchmark action object
- player setup menu controller

### 3. KMP 물리 이동 2차

로드맵이지만 비교적 빨리 착수한다.

다음 후보는 다음 순서가 적절하다.

1. `MoveReview` / `MoveValueDisplay`
2. `UserPreferencesSnapshot`
3. `AnalysisSession`

다만 `UserPreferencesSnapshot`은 `PlayerSetup` 의존이 있어 match package의 common화 상태를 같이 봐야 한다.

## 보류 또는 폐기할 부분

### 1. 점수 확보 목적의 테스트 추가

폐기한다.

"과제 2+3으로 +1.5점 확보"라는 표현은 동기 부여로는 좋지만, 우리는 점수가 아니라 제품 안정성과 계층 품질을 기준으로 움직여야 한다. 테스트는 숫자 상승을 위해서가 아니라 실제 회귀 가능성이 높은 규칙과 shared policy를 보호하기 위해 추가한다.

### 2. 네트워크 sink 즉시 구현

보류한다.

실제 사용자 데이터 전송은 동의 UX와 개인정보 정책이 먼저다. 개발자 로컬 수집 목적이라면 local JSONL, `run-as`, debug report copy가 아직 더 안전하고 빠르다.

### 3. `LaunchedEffect` 무조건 제거

폐기한다.

Compose lifecycle을 application layer로 억지로 밀어내면 오히려 불투명해질 수 있다. 남겨도 되는 UI lifecycle bridge와 제거해야 할 domain side-effect를 구분해야 한다.

## 다음 실행 제안

외부 평가를 반영한 다음 리팩토링 순서는 다음이 가장 합리적이다.

1. shared/commonTest 보강
   - `DiagnosticEventModelTest`
   - shared engine policy 테스트 이동/보강

2. shared 바둑 규칙 regression 보강
   - capture/suicide/ko/pass-pass 중심

3. root application 잔여 package 이동
   - scoring/prompt/game session 순서

4. `LaunchedEffect` inventory 문서화 및 1차 이관
   - startup/restore/benchmark 중 하나만 먼저 effect launcher로 이동

5. `LocalEngineSessionClient` delegate 분리
   - cache resolver
   - diagnostic recorder
   - core sync executor

## 내부 평가 업데이트

| 평가 축 | 점수 | 비고 |
| --- | ---: | --- |
| 리팩토링 배치 진행도 | 99.995/100 | ext.11 기준 매우 높음 |
| 외부 평가 기준 플랫폼 아키텍처 완성도 | 96/100 | 조건부 수용 |
| 보수적 내부 플랫폼 완성도 | 95.5/100 | 네트워크 sink/LaunchedEffect/KMP tests 미완료 반영 |
| 제품 릴리즈 안정도 | 92~94/100 | 기능은 안정적이나 종국/엔진/로그 정책은 계속 보강 필요 |

최종 판단: **외부 96점은 방향성 있게 수용하되, 다음 작업은 점수 상승보다 shared/common test와 lifecycle effect boundary를 우선한다.**
