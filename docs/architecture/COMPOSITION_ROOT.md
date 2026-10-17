# 계층 밖 — 조립 루트 (Composition Root)

go-ai-coach에서 이 자리가 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「계층 밖 — 조립 루트」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「5계층」 절에 있던 조립 루트 항목을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 지금 위치

- `app-android/.../ui/shell/GoCoachApp.kt` — 5계층의 모든 컨트롤러를 생성/연결하는 composition root. `LayeringContractTest`의 `lineBudget`/`stateHookBudget`이 예산을 강제한다.
  ⚠️ **2026-10-17 정정**: 예산은 이제 `LayeringContractTest`가 아니라 `ShellStateLedgerContractTest`가 `architecture-budgets.json`(줄 예산 `codeLineBudget`·훅 자리 `sites`)으로 강제한다(refactor backlog #46). `LayeringContractTest`에는 옛 예산의 이력 주석만 남았다. 아래 2026-09-23 수치는 그때 것이다.
  ⚠️ **단위가 raw 줄 수가 아니다.** 2026-09-05(백로그 #102)부터 `codeLinesOf`가 **import·`package`·주석·빈 줄을 빼고** 센다 — *"왜 이 순서여야 하는가"* 를 주석으로 적으면 예산이 깎이던 왜곡을 없애기 위해서다. raw 줄 수와 예산을 나란히 비교하면 틀린 계산이 된다.
  **2026-09-23 기준 실측**: 코드 줄 **733 / 예산 777**(여유 44), 상태 훅 **42 / 예산 42**(**여유 0**). raw는 954줄이고, 같은 날 `b19bdc57`(중복 `EngineBenchmarkController` 제거)로 raw 970→954가 됐다 — 다만 **그 삭제된 지역 변수에는 `remember`/`mutableStateOf`/`LaunchedEffect`가 하나도 없어 훅 예산은 42 그대로, 회수 0**이다(진단서 함정 H).
  ⚠️ **조이는 힘은 훅 쪽에 있다.** 훅이 1차 지표이고 여유가 0이며, 줄 수는 뒷받침이라 **일부러 여유를 뒀다**. 예산을 올릴 때는 그 사유를 `LayeringContractTest` 안의 주석 이력에 남긴다 — 사유 없이 올리는 순간 이 그물은 뜻을 잃는다.
- `app-android/src/main/java/com/worksoc/goaicoach/` 최상위(`MainActivity.kt`, `GoAiCoachApplication.kt`, `AppForegroundEvents.kt`, `AttendanceCheckInCoordinator.kt`, `DeveloperModeResetCoordinator.kt`, `ReleaseResetCoordinator.kt`) — **조립 전용 루트 패키지**. 전 계층을 참조해도 되는 자리다(진단서 §3의 처방을 따라 2026-09-23에 명문화 — 그전엔 이것도 모델 밖에 떠 있었다).

## 지키는 테스트

- `LayeringContractTest.compositionRootFileSetIsAConsciousDecision` — 루트 패키지의 파일 목록을 정확히 못박는다(#26).
- `LayeringContractTest.controllerWiringListMatchesTheRootAndCompositionDoesNotRegrowInUi` 와 `goCoachAppDoesNotOwn…WorkflowBody` 묶음 — 흐름 본문이 셸로 돌아오지 않는다.
- `ShellStateLedgerContractTest.compositionRootStaysWithinItsLedger` — 셸의 줄 예산·상태 훅(`architecture-budgets.json`).
