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
갱신: 2026-08-17 — `refactoring/GAMESESSION_SHARED_MIGRATION_KICKOFF_PLAN_260816_1808.md`(`application/` 트리 124개 파일 → `:shared` 이전, 웨이브 1~6)가 이 인덱스에 누락돼 있던 것을 아래 표에 추가하며 완료로 표기. 같은 날 `REFACTORING_BACKLOG_260816_1744.md`의 번호 매긴 항목(1~4)도 전부 사용자 결정대로 마무리(1~3 완료, 4는 문서 정정만) — 리팩토링/코드부채 축이 사실상 소진되고, 다음 단계가 **초도 시장 발행**으로 넘어갔다. `launch-plan/README.md`에 0절(최종 출시 체크리스트 종합)을 신설해 그 단계의 진입점으로 삼았다 — 아래 "마스터플랜 폴더" 표의 해당 행 참고.
갱신: 2026-08-17 (2) — `빠른 초급`/`초급` 레벨링용 후보수(candidate moves) 확장 방향을 검토한 `engine-research/ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md`를 신설해 "엔진 검증/연구 리뷰" 표에 편입했다. `ENGINE.md`/`ENGINE_API_CALL_POLICY.md`의 레벨 표를 코드와 대조해 정확함을 재확인하는 한편, `refinePolicyMoves`(엔진 어댑터에 이미 구현돼 있으나 AI 착수 경로에서는 항상 0으로 꺼져 있던 후보 확장 기능)의 비용/효과를 새 스크립트(`scripts/run-katago-candidate-refine-experiment.py`)로 실측했다. 앱 코드 변경은 없음 — 다음 실험으로 넘기는 방향 검토 문서.
갱신: 2026-08-17 (3) — **"삭제 대신 보관" 문서 보존 원칙을 뒤집었다.** `docs/archive/`(5개 하위 폴더, 55개 파일, 1.2MB)와 `docs/refactoring/`(리팩토링 축이 260817에 종료 확인됨, 8개 파일)를 저장소에서 완전히 제거하고 git 히스토리로만 남겼다 — 사유와 복원 방법은 아래 "문서 보존 정책" 절 참고. 유일한 예외로 실측 데이터가 계속 인용되던 `ENGINE_BEGINNER_VISITS_BENCHMARK.md`는 `docs/engine-research/`로 이동 보존했다. 삭제된 경로를 가리키던 모든 교차 참조(`ENGINE_API_CALL_POLICY.md`, `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`, `docs/history/THREAD_HISTORY.md`, `launch-plan/README.md`, `premium-mode/README.md`, `auth-onboarding/README.md`)를 함께 정리했다. 아래 "리팩토링 전략/진행 로그"·"아카이브" 두 섹션은 이 갱신으로 제거됐다.

## 하위 폴더 한눈에 보기

| 폴더 | 존재 목적 |
| --- | --- |
| `docs/` (최상위) | 2026-06-28 기준, 지금 제품을 이해/운영하는 데 바로 필요한 9개 핵심 문서만 |
| `docs/engine-research/` | 엔진 강도·search tree 정책 검증 실험 리뷰. 결론은 핵심 문서에 반영됐지만 여전히 기술적으로 인용되는 근거 원본만 소수 유지 |
| `docs/history/` | 프로젝트 대화/작업 히스토리 누적 기록. 계속 append되는 진행 중인 로그 |
| `docs/engine-match-logs/`, `docs/engine-benchmark-logs/` | KataGo 레벨 매트릭스·기기 성능 측정 raw/summary 로그 |
| `docs/error-cases/` | 계가/사석/패스 관련 버그 재현 케이스 |

**2026-08-17부로 `docs/refactoring/`(완료된 리팩토링 작업 로그)와 `docs/archive/<날짜>-<사유>/`(더 이상 유효하지 않은 문서 보관)는 저장소에 두지 않는다.** 완료되고 결론이 흡수된 문서는 이제 옮기지 않고 삭제한다 — 근거와 복원 방법은 아래 "문서 보존 정책" 절 참고.

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
| 2026-08-05 | `docs/history/THREAD_HISTORY.md`를 압축 — 2026-05-31 날짜 헤더 하나 아래로 쌓여 있던 1,622줄 상세 로그를 "지난 히스토리 요약" 절(시기별 핵심만)로 대체하고, 이후로는 날짜 헤더마다 그 날의 요약만 짧게 추가하는 방식으로 전환 | 상세 원문은 당시 `docs/archive/`에 보존했으나 2026-08-17에 그 아카이브 자체가 제거됨 — `git log --all --diff-filter=D -- 'docs/archive/**/THREAD_HISTORY_DETAIL*'`로 복원 |
| 2026-08-06 | `docs/refactoring/`(완료된 시점 스냅샷·조사 문서 24개)와 `docs/working-260617/implementation_plan.md`(보드 크기 다중 지원 계획, 이미 구현됨)를 정리 — 당시엔 "리팩토링 전략/진행 로그" 표를 지금도 진행 로그가 쌓이는 활성 문서 6개만 남도록 축소 | 이동된 문서 전체와 파일별 한 줄 설명은 당시 `docs/archive/2026-08-06-refactoring-log-consolidation/README.md`에 보존했으나 2026-08-17에 그 아카이브 자체가 제거됨 — `git log --all --diff-filter=D -- 'docs/archive/2026-08-06-refactoring-log-consolidation/**'`로 복원 |
| 2026-08-17 | **"삭제 대신 보관" 원칙을 뒤집음.** `docs/archive/` 전체(55개 파일)와 리팩토링 축이 종료된 `docs/refactoring/` 전체(8개 파일)를 저장소에서 제거 — 완료된 문서를 archive로 옮기는 대신 이제 그냥 삭제하고 git 히스토리로만 남긴다. 사유: 프로젝트가 리팩토링 단계를 끝내고 초도 발행 단계로 넘어가며, 과거 서사가 이제는 매 세션 코드베이스 탐색(Explore agent, grep 스윕) 시 토큰만 소모하는 부채로 판단됨(사용자 요청) | `git log --diff-filter=D --summary -- docs/archive docs/refactoring`로 삭제 커밋을 찾은 뒤 `git show <commit>^:<경로>`로 개별 파일 복원. 유일한 예외 `ENGINE_BEGINNER_VISITS_BENCHMARK.md`는 삭제하지 않고 `docs/engine-research/`로 이동(현재도 실측 비교 기준으로 인용됨) |

이런 식으로 문서를 "재작성"할 때마다(내용을 들어내거나 구조를 바꿀 때) 이 표에 한 줄을 추가한다 — git 히스토리를 찾아볼 최소한의 단서(무엇이 언제 어디로 갔는지)를 남기기 위함이다. **2026-08-17부터는 "파일을 지우지 않는다"가 아니라 "지우더라도 이 표에 사유와 복원 경로를 남긴다"가 원칙이다.**

## 저장소 루트의 마스터플랜 폴더 (docs/ 밖)

`docs/` 상위 규율(요약/딥다이브 구분, 날짜 표기)과 별개로, 기능별로 계속 갱신되는 "마스터플랜" 문서가 저장소 루트에 따로 있다. 지금까지 `DOCS_INDEX.md`가 이들을 인덱싱하지 않아 파편화의 한 원인이었다 — 2026-07-30부터 여기 편입한다.

| 위치 | 용도 |
| --- | --- |
| `feature-access-principles/README.md` | 기능 유/무료 제공 원칙 — `premium-mode/`·`auth-onboarding/`이 "무엇을 어떻게 만들었는가"를 기록하는 실행 문서라면, 이 문서는 "왜 그렇게 하기로 했는가"를 담는 상위 원칙 문서. 맨 아래 결정사항이 계속 append됨 |
| `launch-plan/README.md` | 위 원칙을 Google Play 초도 버전 발행에 구체적으로 적용한 전략·기능별 정책·체크리스트. **0절이 260817 기준 최종 출시 체크리스트 종합** — 리팩토링이 끝난 뒤 다음 단계(시장 발행)를 시작하는 새 스레드는 이 문서부터 읽을 것 |
| `premium-mode/README.md` | 프리미엄/수익화 모드 마스터플랜(광고 1시간 활성화, 영구 결제). Step별 진행 로그가 계속 append됨 |
| `auth-onboarding/README.md` | 최초 실행 온보딩 + 계정 시스템(Firebase 익명/Google/이메일 인증) 마스터플랜. [ARCHITECTURE.md](./ARCHITECTURE.md) 6계층(세션/연속성)의 실행 문서 |
| `ux-improvement/README.md` | UX 개편(보드 스케일링, 패널, 직접 착수 흐름) 마스터플랜 |
| `ux-improvement/wireframes/v1_wireframe.md` | 위 마스터플랜의 v1.0.0 와이어프레임 스펙 |
| `docs/baas_solutions_comparison.md` | Firebase/Supabase/PocketBase/Appwrite/Convex BaaS 비교 조사 — Firebase 채택 근거 원본. 결론은 `auth-onboarding/README.md`에 반영됨 |
| `docs/baduk_app_architecture_recommendation.md` | 백엔드+AdMob 전략 추천 조사 — 결론은 `premium-mode/README.md`/`auth-onboarding/README.md`에 반영됨 |
| `design-handoff/README.md` | 외부 기획 디자이너 핸드오프 export/import 채널. 라운드별 스냅샷(`export/<날짜>-<버전>/`)과 디자이너 회신(`import/<날짜>-<라운드명>/`) 이력을 쌓는다 |

## 운영 원칙

`docs/` 최상위에는 **현재 제품에서 바로 참고해야 하는 핵심 문서만** 둔다. 새 문서가 2026-07-30 기준 위 10개 범주(요약 4개 + 딥다이브 4개 + 파생 로드맵 1개 + 이 인덱스)에 들지 않으면, 아래 중 맞는 폴더로 분류한다. 특정 기능이 계속 갱신되는 마스터플랜 성격이면 `docs/` 밖 전용 폴더(위 표)에 두는 것도 허용한다 — 단 반드시 이 인덱스에 등록한다.

현재 코드의 줄 수, 패키지/파일 수, remote transport 구현 상태, 테스트 통과 여부처럼 자주 낡는 지표는 반드시 "YYYY-MM-DD 기준"을 붙인 문장으로만 쓴다.

- 엔진 강도/검증 실험 리뷰(현재도 인용되는 것만) → `docs/engine-research/`
- 누적 프로젝트 대화/작업 히스토리 → `docs/history/`
- 리팩토링 전략·작업 로그, 완료된 의사결정 서사 → **저장소에 두지 않는다** (아래 "문서 보존 정책" 참고). 지금 진행 중인 리팩토링이 있다면 `docs/refactoring/`을 다시 만들어 그 활성 로그만 담고, 끝나면 지운다.

## 문서 보존 정책 (2026-08-17부터)

**2026-08-17 이전**: "삭제 대신 보관" — 완료되거나 superseded된 문서는 `docs/archive/<날짜>-<사유>/`로 옮기고 저장소에 영구히 남겼다.

**2026-08-17 이후**: 완료되고 결론이 상위 문서(로드맵/`ENGINE.md` 등)에 흡수된 리팩토링/의사결정 서사 문서는 archive로 옮기지 않고 **그냥 삭제한다.** git 히스토리 자체가 아카이브 역할을 한다.

이유: 이 정책은 원래 "히스토리를 절대 잃지 않는다"는 원칙에서 나왔고 당시엔 맞았다. 하지만 리팩토링 축이 사실상 종료되고(`GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 참고) 프로젝트가 초도 발행 단계로 넘어간 지금은, 그 서사 문서들이 실제로 다시 읽히는 일 없이 `docs/archive/`에 55개 파일·1.2MB로 쌓여만 있었다 — 매 세션 새 에이전트 스레드가 코드베이스를 탐색(Explore agent, `grep -r` 스윕)할 때 이 분량을 함께 스캔하며 토큰만 소모하고, "이게 지금도 유효한 문서인가?"라는 혼동까지 유발할 수 있다는 판단(사용자 요청, 2026-08-17)에 따라 정책을 뒤집었다.

**예외 — 삭제 대신 이동 보존한 경우**: 기술적으로 여전히 인용/비교되는 실측 데이터는 삭제하지 않는다. 예: `ENGINE_BEGINNER_VISITS_BENCHMARK.md`는 `docs/engine-research/ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md`가 그 P0/P1/P2 실험 국면과 수치를 직접 재사용/비교하므로 `docs/archive/`가 아니라 `docs/engine-research/`로 이동했다. 판단 기준: "이 파일 이름이 지금도 다른 활성 문서에서 구체적으로 인용되는가" — 그렇다면 옮겨서 유지, 아니면 삭제.

**복원 방법**: 삭제된 문서는 git에 그대로 남아 있다.

```bash
# 어떤 커밋이 무엇을 지웠는지 찾기
git log --diff-filter=D --summary -- docs/archive docs/refactoring

# 특정 파일을 그 삭제 직전 상태로 복원
git show <커밋해시>^:docs/archive/<경로>/<파일명>.md > <파일명>.md
```

**2026-08-17에 제거한 것**: `docs/archive/`(5개 하위 폴더, 55개 파일) 전체, `docs/refactoring/`(8개 파일, 리팩토링 축 종료 확인) 전체. 삭제된 경로를 가리키던 교차 참조(`ENGINE_API_CALL_POLICY.md`, `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`, `docs/history/THREAD_HISTORY.md`, `launch-plan/README.md`, `premium-mode/README.md`, `auth-onboarding/README.md`)도 같은 날 함께 정리했다.

## 엔진 검증/연구 리뷰

대국 강도, search tree 정책처럼 결론이 `ENGINE_API_CALL_POLICY.md`/`ENGINE.md`에 반영된 뒤에도 근거 자료로 남겨야 하는 실험 리뷰.

| 위치 | 용도 |
| --- | --- |
| `engine-research/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` | B16/B32/B64 레벨 강도 실험과 결론 |
| `engine-research/ENGINE_SEARCH_TREE_REUSE_REVIEW.md` | KataGo search tree 재사용/격리 정책 검토 |
| `engine-research/ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md` | `빠른 초급`/`초급` 더블체크 + 레벨링용 후보수 확장 레버(`refinePolicyMoves`) 비용/효과 실측. 아직 앱에는 미반영, 방향 검토 문서 |
| `engine-research/ENGINE_BEGINNER_VISITS_BENCHMARK.md` | B16/B32/B64 후보수 최초 실측(2026-06-08). 위 CANDIDATE_EXPANSION_REVIEW가 같은 P0/P1/P2 국면을 재사용 — 2026-08-17에 `docs/archive/`에서 이 폴더로 이동 |

## 프로젝트 히스토리

| 위치 | 용도 |
| --- | --- |
| `history/THREAD_HISTORY.md` | 프로젝트 대화와 작업 히스토리 누적 기록. 계속 append되는 진행 중인 로그이며, archive(=더 이상 유효하지 않음)와는 성격이 다르다 |

## 데이터 로그

대량 실험 로그는 최상위 Markdown 문서로 세지 않는다.

| 위치 | 용도 |
| --- | --- |
| `engine-match-logs/` | 맥북 KataGo 레벨 매트릭스 raw/summary 로그 |
| `engine-benchmark-logs/` | 실기기/에뮬레이터 엔진 성능 및 자동대국 진단 로그 |
| `error-cases/` | 계가/사석/패스 관련 재현 케이스 |
