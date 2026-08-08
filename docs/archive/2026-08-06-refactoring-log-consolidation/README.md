# 2026-08-06 리팩토링 로그 통합 아카이브

작성일: 2026-08-06

`docs/refactoring/`에 쌓여 있던 문서 중 완료됐거나 이후 문서에 결론이 흡수된 25개를 이 폴더로 옮겼다. **삭제한 문서는 없다** — `docs/DOCS_INDEX.md`의 "문서를 삭제하지 않는다" 원칙에 따라 전체 내용 그대로 보존했고, `git log --follow`로도 이동 이력을 추적할 수 있다.

최신 기준 문서는 항상 `docs/DOCS_INDEX.md`와 그 안의 `docs/refactoring/`에 남아 있는 활성 문서를 우선한다.

## 이동 기준

- 시점 스냅샷(리뷰/평가/스파이크)으로서 목적을 다했고, 결론이 현재 핵심 문서(`ARCHITECTURE.md`, `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`, `ENGINE_API_CALL_POLICY.md` 등)나 이후 착수 계획서에 흡수된 문서
- 제안한 작업이 이후 세션에서 실제로 구현되어 더 이상 "계획"이 아니게 된 문서
- 8/3 이후 시작된, 지금도 진행 로그가 쌓이고 있는 "진행 중" 계획서(`DOMAIN_SEPARATION_REFACTORING_KICKOFF_PLAN_260729_2315.md`/`_STATUS_260806_1304.md`, `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`, `CODE_QUALITY_REFACTORING_PLAN_260803_2217.md`, `ENGINE_BRIDGE_MODULE_CONSOLIDATION_PLAN_260804_0005.md`, `PLAY_FLOW_UX_REFACTORING_PLAN_260804_0553.md`)는 **이동하지 않았다** — `docs/refactoring/`에 그대로 남아 있다.

## 최초 전략 / 도메인 분리 설계 (2026-06-08 ~ 06-13)

| 파일 | 내용 |
| --- | --- |
| `REFACTORING_STRATEGY_2026-06-08.md` | 당시 구조 평가와 다음 리팩토링 방향 |
| `DOMAIN_SEPARATION_REFACTORING_PLAN.md` | Engine Core API, Middleware Domain, Game UX 계층 분리 원칙과 단계별 절차 최초안 — 이후 `DOMAIN_SEPARATION_REFACTORING_KICKOFF_PLAN_260729_2315.md`/`_STATUS_260806_1304.md`로 재검토·계승됨 |
| `NEXT_REFACTORING_WORKLIST_2026-06-13.md` | 6/13 시점 작업 리스트와 진행 로그 |
| `NEXT_REFACTORING_WORKLIST_2026-06-14.md` | 6/14 시점 작업 리스트와 진행 로그(용량이 큰 상세 로그 원본) |
| `GAME_SESSION_CONTROLLER_CANDIDATES_2026-06-13.md` | `GoCoachApp.kt`의 display plan applier/reducer 이전 후보와 `GameSessionController` 도입 순서 검토 |
| `SESSION_STATE_REFACTORING_WORKLIST_2026-06-13.md` | reducer state holder를 단일 source of truth로 승격하는 작업 리스트와 진행 로그 |
| `ENGINE_SEARCH_MODE_ROADMAP_2026-06-13.md` | GTP stateful fast path와 JSON position analysis 정책 분리 로드맵, 맥북/폰 벤치마크 원본 데이터 — 이후 Stage D(원격 엔진 어댑터)로 실현됨 |
| `REFACTORING_COMPLETION_ASSESSMENT_2026-06-13.md` | 그 시점 계층 분리 완성도 평가, 남은 리스크, 다음 리팩토링 추천 순서 |

## GoCoachApp.kt 분리 조사 (2026-06-15)

| 파일 | 내용 |
| --- | --- |
| `GO_COACH_APP_SPLIT_PLAN_2026-06-15.md` | `GoCoachApp.kt` 2천 줄 초과 상태에 대한 결론과, 파일을 바로 쪼개기 전에 엔진 호출 지연/실패/늦은 결과 폐기 등 안전장치를 먼저 깔기로 한 순서 판단 |
| `ORCHESTRATION_SPLIT_AND_KMP_MAP_2026-06-15.md` | orchestration 분리 후보와 middleware/KMP 이동 후보를 정리한 기준 문서 — 줄 수 절감보다 workflow ownership 분리를 우선하기로 함 |
| `UI_STATE_HOLDER_BOUNDARY_2026-06-15.md` | Compose state mutation이 한 파일에 집중된 현황과, `gameState`/`analysisState`/`scoreState`/`runtimeState`/`settingsState` 분리 경계 설계 |
| `LAUNCHED_EFFECT_INVENTORY_2026-06-15.md` | 당시 `LaunchedEffect`가 남아 있던 위치와 책임 전수 목록, application runner 이관 후보 추적 |
| `KMP_MOVE_SPIKE_2026-06-15.md` | application 계층 순수 정책 파일을 `shared`/KMP middleware로 이동할 수 있는지 점검한 스파이크 — 이동 후보가 플랫폼 구현에 묶이지 않도록 자동화 계약(현재의 `LayeringContractTest`류)을 먼저 확장하는 데 초점 |

이 5개 문서가 다룬 "GoCoachApp.kt 축소" 자체는 이후 여러 세션(R1~R13, `[[state-holder-refactor]]` 메모리 참고)을 거쳐 계속 진행됐고, 최신 상태는 `docs/refactoring/`에 남아 있는 활성 문서들이 아니라 `LayeringContractTest.kt`의 `goCoachAppStaysWithinShrinkingUiShellBudget` 테스트 자체(코드가 진실)로 확인한다.

## 외부/내부 아키텍처 리뷰 (2026-06-15)

외부 개발자에게 두 차례(93점, 96점) 아키텍처 평가와 한 차례 "바둑 앱 제품 관점" 평가를 받고, 그에 대한 내부 대응 판단을 기록한 묶음.

| 파일 | 내용 |
| --- | --- |
| `EXTERNAL_REVIEW_2026-06-15_ARCHITECTURE_SCORE_93.md` | 외부 개발자의 1차 아키텍처 실행성 재평가(93점) 원문 |
| `EXTERNAL_REVIEW_2026-06-15_ARCHITECTURE_SCORE_96_RAW.md` | 외부 개발자의 2차 아키텍처 평가(96점) 원문, 기준 커밋 `b4fd879` |
| `EXTERNAL_REVIEW_2026-06-15_GO_APP_PRODUCT_PERSPECTIVE_RAW.md` | 바둑 앱 제품/엔진/플랫폼 확장 관점에서 나온 별도 외부 비판 원문 |
| `EXTERNAL_REVIEW_2026-06-15_PROJECT_EVALUATION.md` | 외부 개발자의 프로젝트 구조 검토 의견 원문 |
| `INTERNAL_ARCHITECT_REVIEW_OF_SCORE_93_FEEDBACK_2026-06-15.md` | 93점 평가에 대한 내부 아키텍처 리뷰(대규모 확장·원격/로컬 엔진 이중화·AI Agent 협업·유지보수 비용 기준) |
| `INTERNAL_ARCHITECT_REVIEW_OF_SCORE_96_FEEDBACK_2026-06-15.md` | 96점 평가에 대한 내부 아키텍처 리뷰(KMP 이동 가능성, 운영 관측성 포함) |
| `INTERNAL_REVIEW_OF_EXTERNAL_FEEDBACK_2026-06-15.md` | `PROJECT_EVALUATION` 원문에 대한 내부 재검토, 실제 착수 순서 결정 |
| `INTERNAL_GO_APP_PRODUCT_REVIEW_2026-06-15.md` | `GO_APP_PRODUCT_PERSPECTIVE` 원문을 제품 정확성/엔진 오케스트레이션/KMP 확장성 기준으로 재검토한 내부 판단 |

## 이후 재평가 및 후보 제안 (2026-06-14, 06-27, 06-28)

| 파일 | 내용 |
| --- | --- |
| `ARCHITECTURE_LAYERS_REVIEW_2026-06-14.md` | 7계층 모델 채택 검토본. 결론은 현재 `ARCHITECTURE.md`에 흡수됨(원문 초안은 `archive/2026-06-17-architecture-docs-rewrite/`에 별도 보존) |
| `ARCHITECTURE_IMPLEMENTATION_REVIEW_2026-06-27.md` | 2026-06-27 기준 코드/문서 기준선, 구현 상태 평가, 시급/중장기 리팩토링 계획 — 이후 `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`/`CODE_QUALITY_REFACTORING_PLAN_260803_2217.md`로 계승됨 |
| `ANDROID_SMOKE_TEST_CANDIDATE_2026-06-28.md` | androidTest 스모크 테스트 후보 절차 제안(앱 실행/새 게임/보드 탭 검증, 테스트 태그 설계). 2026-08-06 세션에서 `NewGameBoardTapSmokeTest.kt`로 실제 구현됨 |

## 기타 — 기능 구현 완료

| 파일 | 내용 |
| --- | --- |
| `implementation_plan.md` (원래 `docs/working-260617/`) | 바둑판 크기 9x9/13x13/19x19 다중 지원 구현 계획. 현재 앱에 이미 구현되어 있음(설정 화면의 바둑판 크기 옵션) |
