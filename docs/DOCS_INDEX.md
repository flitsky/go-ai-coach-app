# 문서 인덱스

작성일: 2026-06-14
갱신: 2026-06-17 — `docs/` 최상위를 현재 제품 운영에 바로 쓰는 핵심 문서 9개로 정리했다. 저장소 루트에는 영문 GitHub 소개 파일인 `README.md`만 남기고, `PRD.md`/`ARCHITECTURE.md`/`ENGINE.md`/`OPERATIONS.md`를 포함한 모든 제품/아키텍처 문서는 `docs/` 아래 한글로 둔다. 리팩토링 전략/진행 로그, 엔진 검증 리뷰, 프로젝트 히스토리, 초기 의사결정 문서는 각각 전용 하위 폴더로 분리했다.  
갱신: 2026-06-27 — 현재 코드 기준선과 `make test` 결과를 반영해 `ARCHITECTURE.md`를 갱신하고, 오늘 기준 리팩토링 평가/계획 문서 `refactoring/ARCHITECTURE_IMPLEMENTATION_REVIEW_2026-06-27.md`를 추가했다.
갱신: 2026-06-28 — 줄 수, 패키지 수, remote transport 구현 상태처럼 자주 낡는 현재 지표는 기준일을 붙인 문장으로만 적도록 운영 원칙을 추가했다.
갱신: 2026-07-29 23:15 — 도메인 분리(엔진 브릿지 vs 조합 레이어) 재검토 착수 계획서 `refactoring/DOMAIN_SEPARATION_REFACTORING_KICKOFF_PLAN_260729_2315.md`를 추가했다. 이 문서부터 "착수 계획서" 유형은 `YYMMDD HHhMMm` 시분 단위 타임스탬프를 파일명에 붙이는 관례를 시작한다(기존 날짜만 붙이는 작업 로그류와 구분).
갱신: 2026-07-30 — `ARCHITECTURE.md`를 앱 비종속 원칙 문서로 전면 재작성하고, go-ai-coach 구체 매핑·로드맵을 담은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`를 신설했다(핵심 문서 9개 → 10개). `premium-mode/`, `auth-onboarding/`, `ux-improvement/`, `docs/baas_solutions_comparison.md`, `docs/baduk_app_architecture_recommendation.md`를 이 인덱스에 편입했다. 상세는 "문서 이력" 절 참고.  
갱신: 2026-08-06 — `docs/refactoring/`에서 완료·흡수된 문서 24개와 미인덱스 상태였던 `docs/working-260617/implementation_plan.md`를 `archive/2026-08-06-refactoring-log-consolidation/`으로 이동했다(삭제 아님, 파일명+한 줄 설명 카탈로그는 그 폴더 `README.md`). 동시에 이 인덱스에서 누락돼 있던 활성 문서 2개(`PLAY_FLOW_UX_REFACTORING_PLAN_260804_0553.md`, `DOMAIN_SEPARATION_REFACTORING_STATUS_260806_1304.md`)를 아래 표에 추가해 실제 파일 목록과의 드리프트를 바로잡았다. 상세는 "문서 이력" 절 참고.  
갱신: 2026-08-11 — 외부 기획 디자이너 핸드오프용 export/import 채널 `design-handoff/README.md`를 신설해 "저장소 루트의 마스터플랜 폴더" 표에 편입했다. `export/`에 v0.1.2 기준 최초 스냅샷(유저플로우, 화면별 스펙, 스토어 등록정보 초안 포함) 생성.
갱신: 2026-08-13 — 기능 유/무료 제공 원칙 문서 `feature-access-principles/README.md`를 신설해 "저장소 루트의 마스터플랜 폴더" 표에 편입했다. `premium-mode/README.md`(수익화 구현 로그)·`auth-onboarding/README.md`(계정 구현 로그) 위에서 "왜 그렇게 하기로 했는가"를 담는 상위 원칙 문서 — 두 문서와 계속 교차 참조된다.
갱신: 2026-08-13 (2) — 위 원칙을 초도 발행에 구체적으로 적용하는 `launch-plan/README.md`를 신설해 같은 표에 편입했다. `premium-mode/README.md`의 기능 매트릭스(2장)도 현재 코드 상태(분석 삭제, 무르기 무료)에 맞게 함께 정정했다.
갱신: 2026-08-16 — 리팩토링/코드 부채 정리 전용 우선순위 백로그 `refactoring/REFACTORING_BACKLOG_260816_1744.md`를 신설해 아래 표에 편입했다. 신규 기능 개발과 의도적으로 분리한 문서 — 새 스레드가 아키텍처 로드맵(`GO_AI_COACH_ARCHITECTURE_ROADMAP.md`)을 다시 읽지 않고도 다음에 뭘 할지 바로 고를 수 있게 하는 게 목적.

## 하위 폴더 한눈에 보기

| 폴더 | 존재 목적 |
| --- | --- |
| `docs/` (최상위) | 2026-06-28 기준, 지금 제품을 이해/운영하는 데 바로 필요한 9개 핵심 문서만 |
| `docs/refactoring/` | 구조 리팩토링 전략과 날짜별 작업 로그. 끝난 작업도 지우지 않고 이력으로 쌓아둔다 |
| `docs/engine-research/` | 엔진 강도·search tree 정책 검증 실험 리뷰. 결론은 핵심 문서에 반영됐지만 근거 원본으로 보존 |
| `docs/history/` | 프로젝트 대화/작업 히스토리 누적 기록. 계속 append되는 진행 중인 로그 (archive와 달리 "끝난" 문서가 아니다) |
| `docs/archive/<날짜>-<사유>/` | 더 이상 현재 구조를 대표하지 않는 초기 의사결정·비전·초안 문서. 삭제 대신 보관 |
| `docs/engine-match-logs/`, `docs/engine-benchmark-logs/` | KataGo 레벨 매트릭스·기기 성능 측정 raw/summary 로그 |
| `docs/error-cases/` | 계가/사석/패스 관련 버그 재현 케이스 |

## docs/ 최상위 핵심 문서 (2026-07-30 기준 10개)

| 문서 | 역할 |
| --- | --- |
| `DOCS_INDEX.md` | 이 파일. `docs/` 전체 지도 |
| `PRD.md` | 제품 요구명세, 목표 최종 상태, 로드맵 |
| `APP_IA_AND_UI_SPEC.md` | 앱 정보 구조(IA), 화면별 UI/UX 명세 및 다국어 용어 표준 표기 가이드 |
| `ARCHITECTURE.md` | 7계층(4계층 압축 가능) 원칙 문서 — **앱 비종속**, 특정 제품의 파일/패키지를 담지 않는다 |
| `GO_AI_COACH_ARCHITECTURE_ROADMAP.md` | `ARCHITECTURE.md`의 원칙을 go-ai-coach에 적용한 파생 문서 — 계층별 현재 파일/패키지 매핑, 갭, 고도화 로드맵 |
| `ENGINE.md` | 엔진 탐색 모드 2가지·레벨 정책·벤치마크 결론 요약 |
| `OPERATIONS.md` | 스택/계가 결정, 현재 옵션 화면, 진단/런타임 로그 요약 |
| `ENGINE_API_CALL_POLICY.md` | 엔진 호출 정책, 턴 분석, 캐시, 후보수 처리 기준 — `ENGINE.md`의 딥다이브 |
| `USER_OPTION_MANUAL.md` | 현재 앱 옵션과 사용자 조작 설명 — `OPERATIONS.md`의 딥다이브 |
| `DIAGNOSTIC_EVENT_SCHEMA.md` | 2026-06-28 기준 진단 이벤트 JSONL 스키마 + 런타임 이벤트 로그 20종 — `OPERATIONS.md`의 딥다이브 |
| `SCORE_AND_ENDGAME_DECISION.md` | 중간 형세, 사석 정리, 종국 계가 정책, 부심/주심 SLA — `OPERATIONS.md`의 딥다이브 |

2026-07-30 기준 `PRD`/`ARCHITECTURE`/`ENGINE`/`OPERATIONS` 4개는 압축된 요약 문서이고, `GO_AI_COACH_ARCHITECTURE_ROADMAP`은 `ARCHITECTURE`의 파생 문서이며, 나머지(`ENGINE_API_CALL_POLICY`/`USER_OPTION_MANUAL`/`DIAGNOSTIC_EVENT_SCHEMA`/`SCORE_AND_ENDGAME_DECISION`)는 그 요약이 가리키는 상세 운영 규칙 문서다.

### 문서 이력

| 날짜 | 변경 | 이전 내용은 어디서 |
| --- | --- | --- |
| 2026-07-30 | `ARCHITECTURE.md`에서 go-ai-coach 파일/패키지 매핑 표를 걷어내고 원칙만 남김 | 이전 버전(계층별 구체 매핑 포함)은 `git log -p -- docs/ARCHITECTURE.md`로 확인. 매핑 내용 자체는 신설된 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`로 이전(그대로 복사가 아니라 새 7계층 경계에 맞게 재정리) |
| 2026-08-05 | `docs/history/THREAD_HISTORY.md`를 압축 — 2026-05-31 날짜 헤더 하나 아래로 쌓여 있던 1,622줄 상세 로그를 "지난 히스토리 요약" 절(시기별 핵심만)로 대체하고, 이후로는 날짜 헤더마다 그 날의 요약만 짧게 추가하는 방식으로 전환 | 상세 원문은 `docs/archive/2026-08-05-thread-history-consolidation/THREAD_HISTORY_DETAIL_2026-05-31_to_2026-08-04.md`에 그대로 보존 |
| 2026-08-06 | `docs/refactoring/`(완료된 시점 스냅샷·조사 문서 24개)와 `docs/working-260617/implementation_plan.md`(보드 크기 다중 지원 계획, 이미 구현됨)를 정리 — 아래 "리팩토링 전략/진행 로그" 표를 지금도 진행 로그가 쌓이는 활성 문서 6개만 남도록 축소 | 이동된 문서 전체와 파일별 한 줄 설명은 `docs/archive/2026-08-06-refactoring-log-consolidation/README.md`에 보존. `docs/working-260617/`는 파일 이동 후 빈 폴더라 제거 |

이런 식으로 문서를 "재작성"할 때마다(내용을 들어내거나 구조를 바꿀 때) 이 표에 한 줄을 추가한다 — 파일을 지우지 않는다는 원칙과 같은 이유로, git 히스토리를 찾아볼 최소한의 단서(무엇이 언제 어디로 갔는지)를 남기기 위함이다.

## 저장소 루트의 마스터플랜 폴더 (docs/ 밖)

`docs/` 상위 규율(요약/딥다이브 구분, 날짜 표기)과 별개로, 기능별로 계속 갱신되는 "마스터플랜" 문서가 저장소 루트에 따로 있다. 지금까지 `DOCS_INDEX.md`가 이들을 인덱싱하지 않아 파편화의 한 원인이었다 — 2026-07-30부터 여기 편입한다.

| 위치 | 용도 |
| --- | --- |
| `feature-access-principles/README.md` | 기능 유/무료 제공 원칙 — `premium-mode/`·`auth-onboarding/`이 "무엇을 어떻게 만들었는가"를 기록하는 실행 문서라면, 이 문서는 "왜 그렇게 하기로 했는가"를 담는 상위 원칙 문서. 맨 아래 결정사항이 계속 append됨 |
| `launch-plan/README.md` | 위 원칙을 Google Play 초도 버전 발행에 구체적으로 적용한 전략·기능별 정책·체크리스트. 근시일 로드맵 문서 역할도 겸함 |
| `premium-mode/README.md` | 프리미엄/수익화 모드 마스터플랜(광고 1시간 활성화, 영구 결제). Step별 진행 로그가 계속 append됨 |
| `auth-onboarding/README.md` | 최초 실행 온보딩 + 계정 시스템(Firebase 익명/Google/이메일 인증) 마스터플랜. [ARCHITECTURE.md](./ARCHITECTURE.md) 6계층(세션/연속성)의 실행 문서 |
| `ux-improvement/README.md` | UX 개편(보드 스케일링, 패널, 직접 착수 흐름) 마스터플랜 |
| `ux-improvement/wireframes/v1_wireframe.md` | 위 마스터플랜의 v1.0.0 와이어프레임 스펙 |
| `docs/baas_solutions_comparison.md` | Firebase/Supabase/PocketBase/Appwrite/Convex BaaS 비교 조사 — Firebase 채택 근거 원본. 결론은 `auth-onboarding/README.md`에 반영됨 |
| `docs/baduk_app_architecture_recommendation.md` | 백엔드+AdMob 전략 추천 조사 — 결론은 `premium-mode/README.md`/`auth-onboarding/README.md`에 반영됨 |
| `design-handoff/README.md` | 외부 기획 디자이너 핸드오프 export/import 채널. 라운드별 스냅샷(`export/<날짜>-<버전>/`)과 디자이너 회신(`import/<날짜>-<라운드명>/`) 이력을 쌓는다 |

## 운영 원칙

`docs/` 최상위에는 **현재 제품에서 바로 참고해야 하는 핵심 문서만** 둔다. 새 문서가 2026-07-30 기준 위 10개 범주(요약 4개 + 딥다이브 4개 + 파생 로드맵 1개 + 이 인덱스)에 들지 않으면, 아래 중 맞는 폴더로 분류한다. 특정 기능이 계속 갱신되는 마스터플랜 성격이면 `docs/` 밖 전용 폴더(위 표)에 두는 것도 허용한다 — 단 반드시 이 인덱스에 등록한다.

현재 코드의 줄 수, 패키지/파일 수, remote transport 구현 상태, 테스트 통과 여부처럼 자주 낡는 지표는 반드시 "YYYY-MM-DD 기준"을 붙인 문장으로만 쓴다. 날짜가 붙은 refactoring 로그와 archive 문서의 historical 수치는 이 규칙을 이유로 덮어쓰지 않는다.

- 리팩토링 전략·작업 로그 → `docs/refactoring/`
- 엔진 강도/검증 실험 리뷰 → `docs/engine-research/`
- 누적 프로젝트 대화/작업 히스토리 → `docs/history/`
- 더 이상 현재 구조를 대표하지 않는 초기 의사결정·비전 문서 → `docs/archive/<날짜>-<사유>/`

문서를 삭제하지 않는다. 위 폴더 중 하나로 옮기거나, 정말 더 이상 참고 가치가 없으면 `archive/`로 보낸다.

## 리팩토링 전략/진행 로그

2026-08-06 기준, 지금도 진행 로그가 쌓이는 **활성** 착수 계획서만 이 표에 남긴다. 완료됐거나 이후 문서에 결론이 흡수된 로그는 `archive/2026-08-06-refactoring-log-consolidation/README.md`에 파일명+한 줄 설명으로 정리돼 있다(삭제 아님, 이동만 함).

| 위치 | 용도 |
| --- | --- |
| `refactoring/DOMAIN_SEPARATION_REFACTORING_KICKOFF_PLAN_260729_2315.md` | 2026-06-27 계획의 H-*/M-* 항목 완료 여부 재확인, 엔진 브릿지(Engine Core API)/조합(Middleware) 계층 구분 재검토, 신규 auth/premium 도메인의 계층 적합성 검증, 다음 배치(H-08~) 계획. "착수 계획서" 시분 타임스탬프 관례의 첫 문서 |
| `refactoring/DOMAIN_SEPARATION_REFACTORING_STATUS_260806_1304.md` | 위 KICKOFF_PLAN의 M-01/M-04 상태 재확인 + 정정(이식성 가드레일이 처음부터 `application/` 전체 범위였음, M-04 스모크 테스트 1개 실동작 추가). M-02/03/05~08은 재검증 안 됨 — KICKOFF_PLAN이 여전히 그 항목들의 유일한 상세 소스 |
| `refactoring/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md` | 2026-07-30 재정립한 7계층 모델(`ARCHITECTURE.md`/`GO_AI_COACH_ARCHITECTURE_ROADMAP.md`)을 실제 코드에 단계적으로 반영하는 착수 계획서. Stage A(테스트 정합성)~F(물리적 분산·다른 기기 연산)까지 안전도 순 로드맵. Stage B-2(premium/auth 남은 단계)는 AdMob/Play Console 콘솔 설정이 선행돼야 해서 보류 중, 진행 로그 누적 중 |
| `refactoring/CODE_QUALITY_REFACTORING_PLAN_260803_2217.md` | 계층 경계와 무관한 일반 코드 품질 리팩토링 착수 계획서(위 문서와 별개, 일시 병행). 상수화/공통 코드 추출/도메인 분리/모듈화 4개 카테고리를 Stage A~D 안전도 순으로 정리, 진행 로그 누적 중 |
| `refactoring/ENGINE_BRIDGE_MODULE_CONSOLIDATION_PLAN_260804_0005.md` | `EngineCoreApi`의 로컬/원격 구현체를 `engine-android` 모듈 하나로 물리적으로 통합(완료) — 향후 원격 서버/DePIN 확장의 근간. `RemotePositionAnalysisTransport` 계약을 `:shared`로 이동해 순환 의존 없이 로컬/원격이 같은 계약 공유. 완료됐지만 위 LAYERED_ARCHITECTURE 계획의 하위 문서라 부모와 함께 유지 |
| `refactoring/PLAY_FLOW_UX_REFACTORING_PLAN_260804_0553.md` | 플레이 흐름 UX 개편 착수 계획서 — 로그인 반복 버그 수정, 프리미엄 팝업 타이밍 정리, 대국설정 심플/콤팩트 화면 체계(`GameSetupUxMode`). 진행 로그 누적 중 |
| `refactoring/REFACTORING_BACKLOG_260816_1744.md` | 리팩토링/코드 부채 정리 전용 우선순위 백로그(신규 기능 제외). 항목마다 효과 등급(Sonnet 5 투입 노력 6단계)·완료 기준·다음 세션 시작 프롬프트 포함 — 새 스레드가 이 문서 하나만 읽고 바로 착수 가능하도록 설계됨 |

이 폴더는 날짜가 붙은 작업 로그가 계속 쌓이는 곳이다. 작업이 끝났다고 지우지 않고, 다음 리팩토링에 참고할 이력으로 남긴다 — 단, 위 표가 무한정 길어지지 않도록 진행 로그가 멈추고 결론이 흡수된 문서는 주기적으로 `archive/<날짜>-<사유>/`로 옮기고 이 표에서 뺀다(2026-08-06에 처음 적용, "문서 이력" 절 참고).

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
| `archive/2026-08-05-thread-history-consolidation/` | `docs/history/THREAD_HISTORY.md` 압축 전 원문(2026-05-31~08-04, 1,622줄) 전체 보존. 압축본은 `THREAD_HISTORY.md`의 "지난 히스토리 요약" 절 |
| `archive/2026-08-06-refactoring-log-consolidation/` | `docs/refactoring/`에서 완료·흡수된 문서 24개 + `docs/working-260617/implementation_plan.md`(구현 완료된 보드 크기 다중 지원 계획) 보존. 폴더 자체 `README.md`에 파일명+한 줄 설명 전체 카탈로그 |

## 데이터 로그

대량 실험 로그는 최상위 Markdown 문서로 세지 않는다.

| 위치 | 용도 |
| --- | --- |
| `engine-match-logs/` | 맥북 KataGo 레벨 매트릭스 raw/summary 로그 |
| `engine-benchmark-logs/` | 실기기/에뮬레이터 엔진 성능 및 자동대국 진단 로그 |
| `error-cases/` | 계가/사석/패스 관련 재현 케이스 |
