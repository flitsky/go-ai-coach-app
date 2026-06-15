# GoCoachApp Split Plan - 2026-06-15

## 결론

`GoCoachApp.kt`가 2천 줄 이상인 상태는 최종 구조로는 괜찮지 않다. 다만 지금까지는 무작정 파일을 쪼개기보다, 엔진 호출 지연/실패/늦은 결과 폐기, operation lifecycle, diagnostic event, app-service runner, domain package zero-root 같은 안전장치를 먼저 깔았다.

즉, `GoCoachApp.kt`는 방치된 것이 아니라 마지막 큰 UI orchestration shell로 남겨둔 상태였다. 이제는 전제 조건이 충분히 갖춰졌으므로 줄 수를 직접 줄이는 단계로 들어갈 수 있다.

## 현재 상태

- 2026-06-15 2nd phase.3 기준 `GoCoachApp.kt`: 2,068줄
- application import fan-in: 71개
- 직접 `scope.launch`/`withContext`/`Dispatchers`/`runCatching`: 0개
- root `application` production 파일 수: 0개

현재 `GoCoachApp.kt`에 남은 주된 책임은 다음이다.

1. Compose state holder 조립
2. startup/restore/benchmark/autosave/auto-AI/Top-Moves `LaunchedEffect` trigger
3. 엔진 operation 시작/완료 busy lifecycle callback 연결
4. 각 runner completion plan을 실제 mutable state에 apply
5. `GoCoachContent` 렌더링 입력 생성

렌더링 자체보다 “언제 어떤 app-service runner를 호출하고, 결과를 어떤 state에 반영할지”가 남아 있어 파일이 크다.

## 왜 지금까지 급하게 줄이지 않았나

- 엔진 호출은 local process든 remote server든 늦게 도착하거나 실패할 수 있다. 먼저 stale result guard와 operation lifecycle을 넣지 않고 파일만 쪼개면, race condition이 여러 파일에 흩어질 수 있었다.
- AI 자동대국, Top Moves, post-undo sync, score estimate는 서로 후속 호출을 만든다. runner 경계가 없을 때 분리하면 오히려 순서 의존이 더 숨겨질 수 있었다.
- 최근 단계에서 `EngineOperationRequest`, `runEngineOperationInScope`, structured diagnostic, domain package split이 안정화되었으므로 이제 분리 비용이 낮아졌다.

## 축소 로드맵

### 2nd phase.4: Auto AI 실행 본문 분리

- `requestAiTurnForCurrentState()` 안의 delay 이후 validation, operation token 생성, begin/complete log, engine runner 호출을 `AutoAiTurnEffectLauncher` 또는 `AutoAiTurnOrchestrator`로 옮긴다.
- UI에는 `AutoAiTurnCompletionPlan` apply callback과 follow-up Top Moves 요청만 남긴다.
- 예상 감소: 80~140줄
- 목표: `GoCoachApp.kt` 1,900줄대 진입

### 2nd phase.5: Top Moves 실행 본문 분리

- `requestTopMoveAnalysisForState()`의 launch plan 적용, cache hit/run-engine 분기, completion apply runner 연결을 `TopMoveAnalysisEffectLauncher`로 옮긴다.
- UI에는 apply plan 반영만 남긴다.
- 예상 감소: 70~120줄

### 2nd phase.6: startup/restore/benchmark/persistence effect 묶음 분리

- engine startup, saved-session restore, benchmark auto-run, autosave trigger를 각각 launcher로 옮긴다.
- 이미 이번 phase.3에서 autosave runner와 trigger helper를 도입했으므로 다음 단계는 callback contract를 더 좁히는 작업이다.
- 예상 감소: 80~150줄

### 2nd phase.7: screen composition facade

- `buildGameScreenStateInput(...)` 조립을 `GoCoachScreenStateAssembler`로 분리한다.
- UI mutable state read는 snapshot 객체로 묶고, `GoCoachApp.kt`는 `GoCoachContent` 호출과 event dispatch만 담당하게 한다.
- 예상 감소: 120~200줄

## 목표 수치

- 단기 목표: 1,800줄 이하
- 중기 목표: 1,500줄 이하
- 장기 목표: 1,200줄 이하

1,000줄 이하는 가능하지만, 무리하게 수치만 맞추기보다 engine/app-service state apply 책임이 충분히 runner로 이동한 뒤 달성하는 편이 안전하다.

## 원칙

- 단순 파일 쪼개기만으로 점수를 올리지 않는다.
- state apply 책임과 engine call 책임을 섞지 않는다.
- remote engine이 느리거나 실패해도 늦은 결과 폐기와 diagnostic event가 한 경계에서 유지되게 한다.
- `GoCoachApp.kt`는 최종적으로 “Compose state wiring + content rendering + event dispatch”만 담당하게 한다.
