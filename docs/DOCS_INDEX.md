# 문서 인덱스

작성일: 2026-06-12

## 운영 원칙

`docs/` 최상위에는 현재 의사결정과 개발 운영에 바로 쓰는 문서만 둔다. 오래된 실험 기록, 과거 계획, 중간 논의 문서는 삭제하지 않고 `docs/archive/` 아래로 이동한다.

현재 최상위 Markdown 문서는 이 파일을 포함해 10개 이내로 유지한다.

## 활성 문서

| 문서 | 역할 |
| --- | --- |
| `THREAD_HISTORY.md` | Codex 대화와 주요 작업 히스토리 누적 기록 |
| `STACK_DECISION.md` | KMP/Android-first 기술 스택 최상위 결정 |
| `USER_OPTION_MANUAL.md` | 현재 앱 옵션과 사용자 조작 설명 |
| `ENGINE_API_CALL_POLICY.md` | 엔진 호출 정책, 턴 분석, 캐시, 후보수 처리 기준 |
| `ENGINE_SEARCH_TREE_REUSE_REVIEW.md` | KataGo search tree 재사용/격리 정책 검토 |
| `ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` | B16/B32/B64 레벨 강도 실험과 결론 |
| `SCORE_AND_ENDGAME_DECISION.md` | 중간 형세, 사석 정리, 종국 계가 정책 |
| `KATRAIN_UX_BACKLOG.md` | KaTrain에서 참고할 UX 후보 backlog |
| `REFACTORING_STRATEGY_2026-06-08.md` | 현재 구조 평가와 다음 리팩토링 방향 |

## 아카이브

| 위치 | 용도 |
| --- | --- |
| `archive/2026-06-docs-consolidation/` | 2026-06-12 문서 정리 때 최상위에서 내린 참조 문서 |
| `archive/2026-06-engine-policy-superseded/` | 엔진 호출 정책 정리 전의 superseded 분석 문서 |

## 데이터 로그

대량 실험 로그는 최상위 Markdown 문서로 세지 않는다.

| 위치 | 용도 |
| --- | --- |
| `engine-match-logs/` | 맥북 KataGo 레벨 매트릭스 raw/summary 로그 |
| `engine-benchmark-logs/` | 실기기/에뮬레이터 엔진 성능 및 자동대국 진단 로그 |
| `error-cases/` | 계가/사석/패스 관련 재현 케이스 |

## 세부 설계 문서

최상위 문서는 의사결정 요약만 유지하고, 실행 단계가 긴 설계/리팩토링 절차는 하위 폴더에서 관리한다.

| 위치 | 용도 |
| --- | --- |
| `refactoring/DOMAIN_SEPARATION_REFACTORING_PLAN.md` | Engine Core API, Middleware Domain, Game UX 계층 분리 원칙과 단계별 리팩토링 절차 |
| `refactoring/NEXT_REFACTORING_WORKLIST_2026-06-13.md` | 다음 2시간 이상 리팩토링 상세 작업 리스트와 진행 로그 |
| `refactoring/GAME_SESSION_CONTROLLER_CANDIDATES_2026-06-13.md` | `GoCoachApp.kt`의 display plan applier/reducer 이전 후보와 `GameSessionController` 도입 순서 |
| `refactoring/SESSION_STATE_REFACTORING_WORKLIST_2026-06-13.md` | reducer state holder를 단일 source of truth로 승격하는 작업 리스트와 진행 로그 |
| `refactoring/ENGINE_SEARCH_MODE_ROADMAP_2026-06-13.md` | GTP stateful fast path와 JSON position analysis를 정책으로 분리하고 단계적으로 실험하는 로드맵 |
