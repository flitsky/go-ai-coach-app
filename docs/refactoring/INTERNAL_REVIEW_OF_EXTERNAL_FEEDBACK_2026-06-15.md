# 외부 검토 의견에 대한 내부 추가 리뷰 - 2026-06-15

작성일: 2026-06-15  
대상 문서: `docs/refactoring/EXTERNAL_REVIEW_2026-06-15_PROJECT_EVALUATION.md`  
목적: 외부 개발자 평가를 현재 코드 상태 기준으로 객관적으로 재검토하고, 실제 리팩토링 착수 순서를 정한다.

## 결론

외부 평가는 방향성 면에서 타당하다. 특히 아래 세 가지 지적은 현재 코드에도 여전히 유효하다.

1. `GoCoachApp.kt`가 아직 너무 크고 orchestration 책임을 많이 가진다.
2. middleware 경계는 논리적으로 많이 분리됐지만 물리적 KMP 모듈 분리는 아직 완성되지 않았다.
3. 운영 관측성은 구조화 이벤트가 생겼지만 외부 수집 채널, sampling, warning/critical escalation 정책은 아직 부족하다.

다만 외부 검토 시점 이후 이미 보완된 항목도 있다. `EngineOperationRequest`, operation result guard, structured diagnostic event, remote read-only gateway, protocol client 분리는 현재 코드에 반영되어 있다. 따라서 외부 점수 91점은 “첫 마켓 릴리즈 이후 운영 플랫폼까지 상정한 보수적 점수”로 해석하고, 현재 리팩토링 진행도 자체는 **97/100** 수준으로 유지한다.

## 항목별 내부 판정

| 외부 지적 | 내부 판정 | 근거 | 실행 방향 |
| --- | --- | --- | --- |
| `GoCoachApp.kt` 경량화 | 수용 | 현재 2,166줄이며, coroutine scheduling과 일부 sync completion side effect가 남아 있다. | effect completion plan과 result handler를 application으로 더 이동한다. |
| Middleware 물리적 모듈 분리 | 조건부 수용 | `PositionAnalysisGateway` 등 KMP-ready 계약은 있지만 Gradle 모듈 분리는 비용이 크다. | 먼저 import boundary test와 package 정리를 진행하고, 물리 모듈은 별도 배치로 둔다. |
| 운영 자동 계측 연결 | 수용 | slow/timeout/discarded 이벤트는 있으나 외부 수집 port/sampling/escalation은 초기 단계다. | diagnostic sink port와 warning/critical 정책 문서를 추가한다. |
| 1,000줄 이하 목표 | 방향만 수용 | 단기 목표로 줄 수를 강제하면 오히려 무리한 추상화가 생길 수 있다. | 책임 제거를 우선하고, 라인 수는 후행 지표로 관리한다. |
| 65개 테스트 기반 신뢰성 | 수용 | 현재 테스트 기반은 강점이다. | 리팩토링마다 작은 회귀 테스트를 추가한다. |

## 현재 코드 기준 리스크

### 1. UI-local result handling이 아직 남아 있다

최근 `EngineOperationApplyPlan`과 `HumanEngineSyncCompletionPlan`을 도입했지만, `GoCoachApp.kt`에는 아직 `shouldApplyEngineOperationResult()`가 남아 있다. 이 helper는 application plan을 사용하긴 하지만 discard runtime/diagnostic log side effect를 UI에서 처리한다.

개선 방향:

- result guard 판정은 application에 있다.
- discard 로그 생성 계획도 application 또는 app-service helper로 이동한다.
- UI는 port append만 수행한다.

### 2. sync 계열 completion plan이 human move에만 집중되어 있다

post-undo sync, scoring rule sync, restored game sync도 같은 late result 폐기 정책을 쓰지만, human sync만큼 명확한 success/failure/discard completion plan은 아직 없다.

개선 방향:

- 모든 sync 결과에 같은 completion plan 패턴을 적용한다.
- “stale result이면 success/failure log를 남기지 않는다”는 규칙을 테스트로 고정한다.

### 3. 운영 관측성은 저장은 되지만 전송/분류 정책이 약하다

`diagnostic_events.jsonl`은 로컬 수집에는 충분하지만, 운영자가 warning/critical을 모아 보는 구조는 아직 없다.

개선 방향:

- 외부 전송을 바로 붙이기보다 `DiagnosticEventSink` 또는 export port를 먼저 둔다.
- warning/critical 이벤트를 사용자 동의 기반 전송으로 확장할 수 있게 문서화한다.
- 정상 lifecycle log와 warning/critical diagnostic을 계속 분리한다.

## 이번 리팩토링 배치에서 반영할 항목

이번 배치는 안전성을 우선해 다음 순서로 진행한다.

1. **Operation result discard handler 분리**
   - `EngineOperationApplyPlan.Discard`를 runtime/diagnostic log append command로 바꾸는 helper를 application 계층에 추가한다.
   - `GoCoachApp.kt`의 local helper는 더 얇아진다.

2. **Human sync completion plan 후속 정리**
   - human sync success/failure log 생성 순서를 명확히 유지하고, stale result discard 테스트를 더 읽기 쉽게 정리한다.

3. **Sync completion plan 확장 후보 문서화**
   - post-undo/scoring/restored sync에 같은 패턴을 적용할 다음 배치 항목을 구체화한다.

4. **운영 관측성 로드맵 보강**
   - 외부 피드백의 운영 자동 계측/외부 수집 채널 port 요구를 `DIAGNOSTIC_EVENT_SCHEMA.md`와 refactoring worklist에 연결한다.

5. **GoCoachApp 경량화 지표 유지**
   - 이번 배치 후 줄 수와 남은 책임을 다시 기록한다.
   - 1,000줄 이하 목표는 장기 목표로 유지하되, 단기 목표는 “UI가 engine operation policy를 직접 판단하지 않게 하기”로 둔다.

## 내부 재평가 점수

- 외부 운영 플랫폼 관점: **91/100** 수용 가능.
- 현재 리팩토링 진행도 관점: **97/100**.
- 이번 배치 완료 후 기대치: **97.2/100**.

감점이 남는 이유는 단순하다. 구조적 방향은 좋지만, `GoCoachApp.kt`가 여전히 크고, middleware 물리 모듈 분리와 외부 diagnostic sink가 아직 실제 운영 수준은 아니기 때문이다.
