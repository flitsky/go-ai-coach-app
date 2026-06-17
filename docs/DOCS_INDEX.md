# 문서 인덱스

작성일: 2026-06-14
갱신: 2026-06-17 — `docs/` 최상위를 현재 제품 운영에 바로 쓰는 핵심 문서 9개로 정리했다. 저장소 루트에는 영문 GitHub 소개 파일인 `README.md`만 남기고, `PRD.md`/`ARCHITECTURE.md`/`ENGINE.md`/`OPERATIONS.md`를 포함한 모든 제품/아키텍처 문서는 `docs/` 아래 한글로 둔다. 리팩토링 전략/진행 로그, 엔진 검증 리뷰, 프로젝트 히스토리, 초기 의사결정 문서는 각각 전용 하위 폴더로 분리했다.

## 하위 폴더 한눈에 보기

| 폴더 | 존재 목적 |
| --- | --- |
| `docs/` (최상위) | 지금 제품을 이해/운영하는 데 바로 필요한 9개 핵심 문서만 |
| `docs/refactoring/` | 구조 리팩토링 전략과 날짜별 작업 로그. 끝난 작업도 지우지 않고 이력으로 쌓아둔다 |
| `docs/engine-research/` | 엔진 강도·search tree 정책 검증 실험 리뷰. 결론은 핵심 문서에 반영됐지만 근거 원본으로 보존 |
| `docs/history/` | 프로젝트 대화/작업 히스토리 누적 기록. 계속 append되는 진행 중인 로그 (archive와 달리 "끝난" 문서가 아니다) |
| `docs/archive/<날짜>-<사유>/` | 더 이상 현재 구조를 대표하지 않는 초기 의사결정·비전·초안 문서. 삭제 대신 보관 |
| `docs/engine-match-logs/`, `docs/engine-benchmark-logs/` | KataGo 레벨 매트릭스·기기 성능 측정 raw/summary 로그 |
| `docs/error-cases/` | 계가/사석/패스 관련 버그 재현 케이스 |

## docs/ 최상위 핵심 문서 (9개)

| 문서 | 역할 |
| --- | --- |
| `DOCS_INDEX.md` | 이 파일. `docs/` 전체 지도 |
| `PRD.md` | 제품 요구명세, 목표 최종 상태, 로드맵 |
| `ARCHITECTURE.md` | 7계층 구조 한 장 요약, 계층별 현재 패키지 지도 |
| `ENGINE.md` | 엔진 탐색 모드 2가지·레벨 정책·벤치마크 결론 요약 |
| `OPERATIONS.md` | 스택/계가 결정, 현재 옵션 화면, 진단/런타임 로그 요약 |
| `ENGINE_API_CALL_POLICY.md` | 엔진 호출 정책, 턴 분석, 캐시, 후보수 처리 기준 — `ENGINE.md`의 딥다이브 |
| `USER_OPTION_MANUAL.md` | 현재 앱 옵션과 사용자 조작 설명 — `OPERATIONS.md`의 딥다이브 |
| `DIAGNOSTIC_EVENT_SCHEMA.md` | 진단 이벤트 JSONL 스키마 + 런타임 이벤트 로그 20종 — `OPERATIONS.md`의 딥다이브 |
| `SCORE_AND_ENDGAME_DECISION.md` | 중간 형세, 사석 정리, 종국 계가 정책, 부심/주심 SLA — `OPERATIONS.md`의 딥다이브 |

`PRD`/`ARCHITECTURE`/`ENGINE`/`OPERATIONS` 4개는 압축된 요약 문서이고, 나머지(`ENGINE_API_CALL_POLICY`/`USER_OPTION_MANUAL`/`DIAGNOSTIC_EVENT_SCHEMA`/`SCORE_AND_ENDGAME_DECISION`)는 그 요약이 가리키는 상세 운영 규칙 문서다.

## 운영 원칙

`docs/` 최상위에는 **현재 제품에서 바로 참고해야 하는 핵심 문서만** 둔다. 새 문서가 위 9개 범주(요약 4개 + 딥다이브 4개 + 이 인덱스)에 들지 않으면, 아래 중 맞는 폴더로 분류한다.

- 리팩토링 전략·작업 로그 → `docs/refactoring/`
- 엔진 강도/검증 실험 리뷰 → `docs/engine-research/`
- 누적 프로젝트 대화/작업 히스토리 → `docs/history/`
- 더 이상 현재 구조를 대표하지 않는 초기 의사결정·비전 문서 → `docs/archive/<날짜>-<사유>/`

문서를 삭제하지 않는다. 위 폴더 중 하나로 옮기거나, 정말 더 이상 참고 가치가 없으면 `archive/`로 보낸다.

## 리팩토링 전략/진행 로그

| 위치 | 용도 |
| --- | --- |
| `refactoring/REFACTORING_STRATEGY_2026-06-08.md` | 현재 구조 평가와 다음 리팩토링 방향 |
| `refactoring/DOMAIN_SEPARATION_REFACTORING_PLAN.md` | Engine Core API, Middleware Domain, Game UX 계층 분리 원칙과 단계별 리팩토링 절차 |
| `refactoring/NEXT_REFACTORING_WORKLIST_2026-06-13.md`, `..._2026-06-14.md` | 날짜별 작업 리스트와 진행 로그 |
| `refactoring/GAME_SESSION_CONTROLLER_CANDIDATES_2026-06-13.md` | `GoCoachApp.kt`의 display plan applier/reducer 이전 후보와 `GameSessionController` 도입 순서 |
| `refactoring/SESSION_STATE_REFACTORING_WORKLIST_2026-06-13.md` | reducer state holder를 단일 source of truth로 승격하는 작업 리스트와 진행 로그 |
| `refactoring/ENGINE_SEARCH_MODE_ROADMAP_2026-06-13.md` | GTP stateful fast path와 JSON position analysis 정책 분리 로드맵, 맥북/폰 벤치마크 원본 데이터 |
| `refactoring/REFACTORING_COMPLETION_ASSESSMENT_2026-06-13.md` | 계층 분리 완성도 평가, 남은 리스크, 다음 리팩토링 추천 순서 |
| `refactoring/ARCHITECTURE_LAYERS_REVIEW_2026-06-14.md` | 7계층 모델 채택 검토본. 원문 초안은 `archive/2026-06-17-architecture-docs-rewrite/`로, 현재 canonical 요약은 `ARCHITECTURE.md` |
| `refactoring/INTERNAL_GO_APP_PRODUCT_REVIEW_2026-06-15.md` | 외부 바둑 앱 관점 리뷰를 제품 정확성, 엔진 오케스트레이션, KMP 확장성 기준으로 재검토한 내부 판단 |
| `refactoring/EXTERNAL_REVIEW_*`, `INTERNAL_*_REVIEW_*`, `INTERNAL_ARCHITECT_REVIEW_*` (2026-06-15) | 외부 아키텍처 점수 리뷰 원문과 내부 대응 판단 |
| `refactoring/GO_COACH_APP_SPLIT_PLAN_2026-06-15.md`, `ORCHESTRATION_SPLIT_AND_KMP_MAP_2026-06-15.md`, `UI_STATE_HOLDER_BOUNDARY_2026-06-15.md`, `LAUNCHED_EFFECT_INVENTORY_2026-06-15.md`, `KMP_MOVE_SPIKE_2026-06-15.md` | `GoCoachApp.kt` 분리 작업의 세부 계획/조사 문서 |

이 폴더는 날짜가 붙은 작업 로그가 계속 쌓이는 곳이다. 작업이 끝났다고 지우지 않고, 다음 리팩토링에 참고할 이력으로 남긴다.

## 엔진 검증/연구 리뷰

대국 강도, search tree 정책처럼 결론이 `ENGINE_API_CALL_POLICY.md`/`ENGINE.md`에 반영된 뒤에도 근거 자료로 남겨야 하는 실험 리뷰.

| 위치 | 용도 |
| --- | --- |
| `engine-research/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` | B16/B32/B64 레벨 강도 실험과 결론 |
| `engine-research/ENGINE_SEARCH_TREE_REUSE_REVIEW.md` | KataGo search tree 재사용/격리 정책 검토 |

## 프로젝트 히스토리

| 위치 | 용도 |
| --- | --- |
| `history/THREAD_HISTORY.md` | 프로젝트 대화와 작업 히스토리 누적 기록. 계속 append되는 진행 중인 로그이며, archive(=더 이상 유효하지 않음)와는 성격이 다르다 |

## 아카이브

더 이상 현재 구조/결정을 대표하지 않는 문서. 삭제 대신 보관한다.

| 위치 | 용도 |
| --- | --- |
| `archive/2026-06-docs-consolidation/` | 2026-06-12 문서 정리 때 최상위에서 내린 참조 문서 |
| `archive/2026-06-engine-policy-superseded/` | 엔진 호출 정책 정리 전의 superseded 분석 문서 |
| `archive/2026-06-17-architecture-docs-rewrite/` | `ARCHITECTURE.md` 신설로 대체된 `ARCHITECTURE_LAYERS_ANALYSIS.md` 초안 |
| `archive/2026-06-17-early-decisions/` | 프로젝트 초기(2026-05-31~06-10) 의사결정/비전 문서. `STACK_DECISION.md`(KMP 최초 선택), `FUTURE_ARCHITECTURE_VISION.md`(초기 장기 비전 초안), `KATRAIN_UX_BACKLOG.md`(미착수 UX 후보 목록) — 모두 현재 핵심 문서(`ARCHITECTURE.md`/`OPERATIONS.md`)가 핵심 결론만 흡수했고, 원문은 근거 자료로 남긴다 |

## 데이터 로그

대량 실험 로그는 최상위 Markdown 문서로 세지 않는다.

| 위치 | 용도 |
| --- | --- |
| `engine-match-logs/` | 맥북 KataGo 레벨 매트릭스 raw/summary 로그 |
| `engine-benchmark-logs/` | 실기기/에뮬레이터 엔진 성능 및 자동대국 진단 로그 |
| `error-cases/` | 계가/사석/패스 관련 재현 케이스 |
