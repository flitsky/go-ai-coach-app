# 코드 품질 리팩토링 계획서 — 260803 22h17m

작성 시각: 2026-08-03 22:17 (KST)

## 0. 이 문서의 성격

`LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`(Stage A~F)는 7계층 경계 정합성에
집중한 계획서였다. 이 문서는 그 작업을 일시 보류(C-1/B-2/Stage E는 미착수 상태로 남김)하고
착수하는 **별개의** 리팩토링이다: 계층 경계와 무관하게, 상수화·도메인 분리·모듈화·공통 코드
추출 관점에서 코드 품질을 올리는 것이 목표다. 이 리포지토리의 "착수 계획서" 관례(`YYMMDD
HHhMMm` 타임스탬프, 진행 로그 누적)를 따른다.

## 1. 배경과 목표

Explore 조사(260803)로 4개 카테고리에서 구체적인 후보를 확보했다. 이 계획서는 그 후보를
안전도 순으로 단계화한다 — 동작이 바뀌지 않는 것부터, 동작이 바뀔 수 있는 것 순으로.

## 2. 완료 정의

- [x] Stage A(상수화) 항목 전부 반영, `make test` 통과 (260803)
- [x] Stage B(공통 코드 추출) — B-1/B-2/B-5 완료(260803). B-3/B-4는 재검토 후 기각(260804, §진행 로그) — 더 진행할 항목 없음
- [ ] Stage C(도메인 분리 — 파일 분리, 로직 불변) — C-1/C-2 완료(260804), C-3~C-6 남음
- [ ] Stage D(모듈화 — 대형 파일 분리)는 각 항목별로 실제 가치 재검토 후 선택 진행

## 3. 단계별 작업

### Stage A — 상수화 (저위험)
- **A-1.** `ui/UndoController.kt:91`(`delay(100L)`), `ui/GamePlaySection.kt:130`(`delay(200)`),
  `ui/GoBoard.kt:89`(`delay(1_000L)`) — 이름 없는 폴링/애니메이션 간격 상수화.
- **A-2.** `ui/AppColorScheme.kt`와 `ui/GoBoardTheme.kt`에 중복 정의된 브랜드 컬러 hex
  (`0xFF0E8C72`, `0xFF6B6459`)를 공유 토큰으로 통합.
- **A-3.** `shared/.../match/MatchPolicy.kt:412,414`의 `"9x9 ..."` 하드코딩 — 실제
  `boardSize`와 무관하게 고정 문자열이라 13x13/19x19 대국에서 표시가 틀릴 수 있는 버그성
  매직 스트링. 실제 `boardSize` 값을 반영하도록 수정(버그 수정 겸 상수화).
- **A-4.** `persistence/DiagnosticEventLog.kt`/`RuntimeEventLog.kt`의 `DefaultMaxBytes`/
  `DefaultTrimToBytes` 중복 값을 공통 상수로 통합(§Stage B-1의 공통 베이스 추출과 겹치면
  그쪽에서 함께 처리).

### Stage B — 공통 코드 추출 (중위험 — 동작 동일성 검증 필요)
- **B-1.** `JSONObject.optNullableInt/Long/Double/String` 확장 함수가
  `HttpRemotePositionAnalysisTransport.kt`, `EngineBenchmarkStore.kt`,
  `JsonPositionAnalysisCacheStore.kt`, `KataGoJsonAnalysisParser.kt` 4곳에 독립 재정의됨 —
  공용 헬퍼로 통합(단, app-android/engine-android 모듈 경계를 넘는 경우 배치 위치 재검토).
- **B-2.** `DiagnosticEventLog.kt`/`RuntimeEventLog.kt` — append-only trimmed-log 공통
  베이스 클래스로 추출.
- **B-3.** `GameSessionStore.kt`/`JsonPositionAnalysisCacheStore.kt`/`EngineBenchmarkStore.kt`/
  `UserPreferencesStore.kt`의 버전(schema) 있는 JSON 코덱 envelope 패턴(`"schema"` 필드 +
  `runCatching{...}.getOrNull()`) 통합.
- **B-4.** `GameSessionStore.kt`/`JsonPositionAnalysisCacheStore.kt`의 `Move` JSON
  인코딩/디코딩 로직 통합.
- **B-5.** `ui/GoBoard.kt`의 `stoneBrush()`/`drawGhostStone()` 그라디언트 색상 배열 중복 제거.

### Stage C — 도메인 분리 (중위험 — 파일만 분리, 로직/동작 불변)
- **C-1.** `ui/GoCoachContent.kt` — 종국판정/캐시최적화/벤치마크/저장세션 재개, 서로 무관한
  다이얼로그 4종을 도메인별 파일로 분리.
- **C-2.** `ui/GoCoachControllerWiring.kt` — 컨트롤러 조립 함수를 그룹별(TopMoves/AutoAi/
  SavedGame/Engine) 하위 wiring 함수로 분리.
- **C-3.** `shared/.../match/MatchPolicy.kt` — 좌석/플레이어 데이터 모델과 턴 진행
  오케스트레이션 함수를 별도 파일로 분리.
- **C-4.** `presentation/GameScreenState.kt` — 화면 상태 도출 로직과 `KaTrainUxOptions`(영구
  저장 UX 토글 번들)를 분리.
- **C-5.** `ui/GameMenuActionsPanel.kt` — 스코어링/핸디캡 설정 패널과 로그/진단 도구 패널을
  분리.
- **C-6.** `application/score/ScoreDisplayApplication.kt` — 형세판단 워크플로우와 종국/엔드게임
  워크플로우를 분리.

### Stage D — 모듈화 (고위험 — 대형 파일 분리, 신중히 가치 재검토 후 진행)
- **D-1.** `ui/GoBoard.kt`(694줄) — 상태/제스처, 캔버스 드로잉 primitives, 브러시/컬러 헬퍼를
  분리.
- **D-2.** `engine-android/.../KataGoProcessEngineAdapter.kt`(446줄) — 프로세스 라이프사이클
  관리를 내부 클래스로 추출.

**제외**: `GoCoachApp.kt`(880줄 예산), `EngineDeviceBenchmarkApplication.kt`(220줄 예산),
`ScoreSyncRunnerApplication.kt` 계열(90/180줄 예산)은 이미 `LayeringContractTest`의 라인수
예산으로 관리되고 있어 이 계획서에서 별도로 다루지 않는다. `GamePlaySection.kt`,
`GoCoachHomeScreen.kt`는 길지만 화면 하나에 대한 응집된 위젯 모음이라 분리 가치가 낮다고
판단해 제외.

## 4. 실행 원칙

- Stage A→B→C→D 순서로 진행하되, 각 스테이지 내 항목은 서로 독립적이라 순서를 바꿔도 무방.
- 각 작업 항목 완료 시 `make test` 통과를 확인하고, 이 문서의 "진행 로그"에 한 줄 기록한다.
- Stage B/C/D는 **동작을 바꾸지 않는** 리팩토링이 원칙이다 — 순수 코드 이동/추출이며, 이
  과정에서 버그를 발견하면(예: A-3) 별도로 명시하고 수정한다.
- 커밋은 매 스테이지(또는 스테이지 내 의미 있는 묶음) 단위로 하고, 사용자의 명시적 확인 후에만
  push한다(이 리포지토리의 기존 관례).
- Stage D는 "정말 가치가 있는지" 착수 직전에 재검토한다 — 단순히 길다는 이유만으로 쪼개면
  오히려 탐색성이 떨어질 수 있다.

## 5. 진행 로그

- 260803 22h17m — 계획서 최초 작성. Explore 조사로 4개 카테고리 후보 확보. 아직 착수 항목 없음.
- 260803 — Stage A 완료 + B-2 조기 착수:
  - A-1: `UndoController.kt`(→`UndoApplication.kt`에 `UndoEngineBusyPollIntervalMillis=100L` 신설), `GamePlaySection.kt`(`TurnTimerTickIntervalMillis=200L`), `GoBoard.kt`(`EngineActivityFrameIntervalMillis=1_000L`) — 이름 없는 delay() 상수화.
  - A-2: `AppColorScheme.kt`에 `BrandPrimaryColor`/`BrandSecondaryColor` 신설, `GoBoardTheme.kt`의 `engineActivityText`/`lastMoveNeutral`가 참조하도록 통합 — 두 파일에 동일 hex가 각각 박혀있던 것 제거.
  - A-3: 조사 중 `shared/.../match/MatchPolicy.kt`의 `modeSummary()`(하드코딩된 "9x9" 매직 스트링 포함)가 **호출자가 전혀 없는 죽은 코드**임을 발견 — 버그를 고치는 대신 함수 자체를 삭제(다른 곳에서 쓰는 `PlayerSetup.summary()`는 그대로 유지, 영향 없음 확인).
  - A-4→B-2로 승격: `DiagnosticEventLog.kt`/`RuntimeEventLog.kt`가 `trimIfNeeded`/`readText`/`clear`/companion 상수(`DefaultMaxBytes`=1_048_576, `DefaultTrimToBytes`=921_600)까지 거의 동일한 것을 확인, 단순 상수 통합보다 공통 베이스 클래스 추출이 맞다고 판단해 범위를 넓힘. 신규 `persistence/TrimmedAppendOnlyLog.kt`(append-only 회전 로그 공통 구현, trim marker/empty message만 하위 클래스가 주입)로 추출 — 인터페이스(`DiagnosticEventLogPort`/`RuntimeEventLogPort`)의 `readText`/`clear`는 상위 클래스의 구현을 별도 `override` 없이 그대로 충족(Kotlin의 인터페이스 구현 규칙), `append`만 포맷이 달라 하위 클래스에 남김. 기존 `DiagnosticEventLogTest`/`RuntimeEventLogTest`(회전 동작을 검증하는 테스트, 커스텀 maxBytes/trimToBytes 사용) 회귀 없이 통과.
  - `make test` 통과 확인(BUILD SUCCESSFUL, `shared`/`engine-android`/`app-android` 전체).
- 260803 — B-1 완료(부분— 모듈 경계 존중): `JSONObject.optNullable{Int,Long,Double,String}`/`putNullable`을 신규 `middleware/JsonNullableExtensions.kt`로 통합(값이 `has(name) && !isNull(name)` 방식과 `isNull(name) || !has(name)` 방식으로 표현만 다르고 논리적으로 동일함을 확인). `persistence/EngineBenchmarkStore.kt`(Int/Long/Double 사용)와 `persistence/JsonPositionAnalysisCacheStore.kt`(Int/Long/Double/String 전부 사용)의 로컬 중복 정의 제거 후 import로 교체 — `persistence`가 `middleware`를 참조하는 방향은 기존 `LayeringContractTest`의 어떤 제약과도 충돌하지 않음(반대 방향, middleware→persistence만 금지돼 있음을 확인). `engine-android/.../KataGoJsonAnalysisParser.kt`의 동일 헬퍼는 **의도적으로 그대로 둠** — `engine-android`는 `shared`에만 의존하고 `app-android`(middleware가 속한 모듈)에는 의존하지 않아, 공유하려면 `shared`에 새 `androidMain` 소스셋을 만들어야 하는데(현재 `commonMain`만 존재) 2줄짜리 헬퍼 하나 때문에 빌드 구성을 늘리는 건 과함. `EngineBenchmarkStoreTest`/`JsonPositionAnalysisCacheStoreTest` 회귀 없이 통과.
- 260803 — B-5 완료: `ui/GoBoard.kt`의 `stoneBrush()`(진행 중 그라디언트)와 `drawGhostStone()`(반투명 미리보기)에 완전히 동일한 흑/백 4단 그라디언트 hex 리스트가 각각 박혀 있던 것을 확인, `activeStoneGradientColors(stone)` 함수로 추출해 두 곳이 공유하도록 통합(색상 값 변경 없음, 리스트 내용 동일함을 diff로 확인 후 추출). Compose UI라 시각적 확인은 이 세션에 Android 에뮬레이터 도구가 없어 못했음 — 다만 순수 값 이동이라 회귀 위험은 낮다고 판단.
- 260804 — B-3/B-4 재검토 후 기각(진행하지 않기로 결정): 4개 스토어를 실제로 나란히 읽어보니 애초 Explore 조사가 표면적 코드 모양만 보고 후보로 올렸던 것으로 드러났다.
  - B-3(스키마 envelope 통합): `GameSessionStore`/`JsonPositionAnalysisCacheStore`/`EngineBenchmarkStore` 3곳은 "스키마 불일치 시 거부"(`optInt("schema", Current) != Current → null`)지만, `UserPreferencesStore`만 "스키마 필드가 없으면 레거시 v1로 간주하고 실제 마이그레이션 경로(`decodeLegacySearchTimeSettings`)를 탄다" — 정책 자체가 다르다. 공유 가능한 코드는 사실상 2줄(`put("schema", N)` / `optInt(...) != N` 체크)뿐이라 공통 추출의 가치가 없고, 억지로 추출하면 오히려 각 스토어의 실제 마이그레이션 의도를 가리는 간접 계층만 추가된다.
  - B-4(Move JSON 인코딩 통합): `GameSessionStore`는 좌표를 GTP 라벨 문자열로(`coordinate: "D4"`, 디코딩 시 `boardSize` 필요), `JsonPositionAnalysisCacheStore`는 row/column 정수로(`row`/`column` 필드, `boardSize` 불필요) 각각 인코딩한다 — 포맷 자체가 다르다. 통일하면 둘 중 한쪽의 기존 저장 데이터(사용자의 저장된 대국 또는 포지션 분석 캐시)와 호환이 깨진다.
  - 결론: 두 항목 모두 진행하지 않는다. 계획서의 "Stage B 완료 정의" 원칙("동작을 바꾸지 않는 리팩토링")과 실제로 상충하는 후보였다.
- 260804 — C-1 완료: `ui/GoCoachContent.kt`(347줄)에서 서로 무관한 다이얼로그 4종을 도메인별 파일로 분리 — `FinalJudgementDialog.kt`(종국판정 + `dialogKey()`), `CacheOptimizationPromptDialog.kt`, `EngineBenchmarkDialogs.kt`(결과+진행 다이얼로그 + `toResultDialogText()`, 벤치마크라는 한 도메인이라 한 파일로 묶음), `ResumeSavedSessionDialog.kt`(원래도 `internal`이라 `GoCoachApp.kt`에서 직접 호출 중이던 것 확인). `GoCoachContent.kt`는 173줄로 축소, 오케스트레이터(`GoCoachContent` composable)만 남음. 로직/동작 변경 없음(순수 이동), `make test` 통과.
- 260804 — C-2 완료: `ui/GoCoachControllerWiring.kt`(494줄)의 12개 컨트롤러 조립을 도메인별 4개 파일로 분리 — `TurnFlowControllerWiring.kt`(TopMoves/Undo/AutoAiTurn/HumanMove), `GameLifecycleControllerWiring.kt`(NewGame/SavedSession), `ScoringControllerWiring.kt`(CacheOpt/ScoreEstimate/ScoringRule), `SettingsAndDiagnosticsControllerWiring.kt`(Settings/DebugReport/Benchmark). 착수 전 확인한 실제 제약: 6개 컨트롤러가 `topMovesController`를(후속 분석 트리거로), `settingsController`가 `undoController`를 생성 시점에 참조해 순서가 고정돼 있음 — 이 교차참조를 암묵적 클로저 대신 **명시적 함수 매개변수**로 만들어 분리했다(예: `wireScoringRuleController(context, topMovesController)`). `GoCoachControllerWiring.kt`는 데이터 클래스/인터페이스/오케스트레이터(`wireGoCoachControllers`, 의존 순서대로 12개 `wireXxx` 호출 후 조립)만 남아 149줄로 축소.
  - 발견한 테스트 결함: `LayeringContractTest`의 7개 `goCoachAppDoesNotOwn*WorkflowBody` 테스트가 `GoCoachApp.kt`+`GoCoachControllerWiring.kt` 두 파일만 하드코딩해서 합쳐 읽고 있었다 — 분리 후 일부는 `wireTopMovesController(` 같은 새 함수 이름이 우연히 필요 문자열(`"TopMovesController("`)을 부분 문자열로 포함해 **우연히** 통과했고, 2개(`goCoachAppDoesNotOwnPositionCacheOptimizationWorkflowBody`, `goCoachAppDoesNotOwnAutoAiTurnCompletionApplyBody`)는 진짜로 실패했다. 7개 테스트 전부를 새 wiring 파일 5개를 합쳐 읽도록 고쳐 이 우연성을 제거했다.
  - `make test` 통과 확인(BUILD SUCCESSFUL, `LayeringContractTest` 43개 전부).

## 6. 관련 문서

- `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md` — 계층 경계 리팩토링(일시 보류 중,
  이 문서와 별개)
- [../ARCHITECTURE.md](../ARCHITECTURE.md), [../GO_AI_COACH_ARCHITECTURE_ROADMAP.md](../GO_AI_COACH_ARCHITECTURE_ROADMAP.md)
