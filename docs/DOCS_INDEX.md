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
갱신: 2026-08-17 (4) — `빠른 초급` 1~3단계를 후보 분류(최적수/중급수/최하수) 기반 5단계(`초보`~`초고수`)로 재정립하는 계획서 `engine-research/FAST_BEGINNER_FIVE_TIER_REDESIGN_PLAN_2026-08-17.md`를 신설해 "엔진 검증/연구 리뷰" 표에 편입했다. 승인 대기 중인 계획 문서 — 앱 코드는 아직 바뀌지 않았다. 승인/구현되면 `ENGINE.md`/`ENGINE_API_CALL_POLICY.md`의 `빠른 초급` 표를 갱신해야 한다.
갱신: 2026-08-18 — **전날 삭제 판단 정정.** `refactoring/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`를 2026-08-17 (3) 정리에서 "종료된 리팩토링 로그"로 분류해 지웠으나, 실제로는 원격 엔진(Stage D/E)·물리적 분산(Stage F, DePIN) 로드맵을 담은 **현재도 진행 중인 활성 계획서**였다 — `app-android/engine/RemoteEngineSessionBootstrap.kt`가 지금도 "Stage E-1" 용어로 이 문서를 직접 참조한다. git 히스토리에서 복원하고 `docs/refactoring/`을 이 활성 작업 전용으로 재생성했다. 문서 보존 정책의 판단 기준("지금도 다른 활성 문서/코드가 구체적으로 인용하는가")을 이번 삭제 때 놓쳤던 사례 — 코드 주석까지 확인하지 않고 문서 이름만으로 판단한 게 원인. 상세는 그 문서의 진행 로그 참고.
갱신: 2026-08-18 (2) — Stage E-3(개발용 원격 엔진 HTTP 참조 서버) 실기 검증 후, HTTP를 MQ(또는 Firestore)로 바꾸고 폰↔폰까지 지원하는 후속 요청이 들어와 Stage F 전용 킥오프 문서 `refactoring/REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md`를 신설했다. **결정/설계만 기록한 문서 — 착수 전.** "맥북 파이썬으로 먼저 검증 후 앱 이식" 순서를 확정했고, MQTT/Firestore 두 후보로 전송 수단을 좁혔다.
갱신: 2026-08-23 — 일일 접속량(DAU) 증대 아이디어를 모으는 브레인스토밍 문서 `engagement-growth/README.md`를 신설해 "저장소 루트의 마스터플랜 폴더" 표에 편입했다. chess.com의 재방문 유도 기능(Daily Puzzle, Puzzle Rush, 봇 캐릭터, Game Review 등)을 참고 사례로 정리하고, 우리 앱의 현재 조건(로그인 꺼짐, 서버 없음, 멀티플레이 없음, 푸시 알림 인프라 없음 — 2026-08-23 코드베이스로 재확인)에 맞는 적용 아이디어를 상태 트래킹 표(제안/논의중/채택/보류/기각)로 관리한다. 아직 결정된 것은 없는 초안 단계 — 계속 논의하며 갱신 예정.
갱신: 2026-08-23 (2) — 위 아이디어 중 출석 보상·AI 캐릭터화·업적 화면·대국 히스토리 4개를 "로그인 없이 로컬 전용(Phase 1)" 방향으로 구체화한 착수 계획서 `engagement-growth/OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md`를 신설했다. 리팩토링이 아니라 신규 기능 착수 계획서라 `docs/refactoring/`가 아니라 주제 폴더(`engagement-growth/`) 안에 두는 것으로 배치 기준을 정했다 — 이 문서부터 "착수 계획서" 유형이 반드시 `docs/refactoring/`에만 있는 게 아니라는 점을 명확히 함. 로그인 연동(Phase 2)은 범위 밖으로 명시적으로 분리하되, 기존 Port 인터페이스(`shared`)+SharedPreferences 어댑터(`app-android/persistence`) 패턴을 그대로 따르게 해 나중에 Firestore 어댑터만 추가하면 확장되도록 설계했다. **결정 문서, 착수 전** — 새 스레드로 바로 넘겨 개발을 시작할 수 있는 형태.
갱신: 2026-08-23 (3) — 위 킥오프 플랜을 새 스레드마다 하나씩 순차 착수 가능한 11개 일감(완료/진행중/예정 상태 + AI 모델·노력정도 표기)으로 쪼갠 백로그 `engagement-growth/OFFLINE_ENGAGEMENT_FEATURES_BACKLOG_260823_2059.md`를 신설했다. 사용자 피드백으로 "봇 캐릭터 선택이 곧 AI 레벨(초보~초고수) 선정" 방향이 확정돼 킥오프 플랜 7.1절에 반영, 백로그 #10(진입점 UX 개편)에 노력정도 최대로 표기했다.

## 하위 폴더 한눈에 보기

| 폴더 | 존재 목적 |
| --- | --- |
| `docs/` (최상위) | 2026-06-28 기준, 지금 제품을 이해/운영하는 데 바로 필요한 9개 핵심 문서만 |
| `docs/engine-research/` | 엔진 강도·search tree 정책 검증 실험 리뷰. 결론은 핵심 문서에 반영됐지만 여전히 기술적으로 인용되는 근거 원본만 소수 유지 |
| `docs/refactoring/` | **2026-08-18 재생성.** 지금 진행 중인 활성 리팩토링/로드맵 문서 전용 — 끝나면 다시 지운다(아래 "문서 보존 정책" 절). 현재 2개: `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`(원격 엔진/DePIN 로드맵)와 그 Stage F 전용 킥오프 문서 `REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md` |
| `docs/history/` | 프로젝트 대화/작업 히스토리 누적 기록. 계속 append되는 진행 중인 로그 |
| `docs/engine-match-logs/`, `docs/engine-benchmark-logs/` | KataGo 레벨 매트릭스·기기 성능 측정 raw/summary 로그 |
| `docs/error-cases/` | 계가/사석/패스 관련 버그 재현 케이스 |

**`docs/refactoring/`(완료된 리팩토링 작업 로그)와 `docs/archive/<날짜>-<사유>/`(더 이상 유효하지 않은 문서 보관)는 기본적으로 저장소에 두지 않는다.** 완료되고 결론이 흡수된 문서는 옮기지 않고 삭제한다. `docs/refactoring/`은 지금처럼 **실제로 진행 중인** 로드맵이 있을 때만 예외적으로 존재하고, 그 작업이 끝나면 다시 삭제한다 — 근거와 복원 방법은 아래 "문서 보존 정책" 절 참고.

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
| `engagement-growth/README.md` | 일일 접속량(DAU) 증대 아이디어 브레인스토밍 — chess.com 참고 사례와 우리 앱 조건에 맞춘 적용 아이디어를 상태 트래킹 표로 관리. 2026-08-23 초안 단계, 계속 논의하며 갱신 |
| `engagement-growth/OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` | 위 아이디어 중 출석 보상·업적 화면·대국 히스토리·봇 컬렉션을 "로그인 없이 로컬 전용(Phase 1)"으로 구체화한 개발 착수 스펙. 새 스레드에 그대로 넘겨 바로 개발 가능한 형태 |
| `engagement-growth/OFFLINE_ENGAGEMENT_FEATURES_BACKLOG_260823_2059.md` | 위 스펙을 새 스레드마다 하나씩 순차 착수 가능한 일감으로 쪼갠 진행 관리 백로그. 완료/진행중/예정 상태와 AI 모델·노력정도를 표기하며 스레드가 끝날 때마다 갱신. 매번 새 스레드에 넣을 고정 프롬프트와 착수 절차("신규 스레드 착수 프로토콜")를 문서 자체에 포함 |

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

**2026-08-18 정정**: 위 목록 중 `refactoring/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`는 삭제 판단이 틀렸다 — 원격 엔진/DePIN 로드맵이 아직 진행 중이었다(아래 "리팩토링 전략/진행 로그" 절). git 히스토리에서 복원했다. 이 사례가 남긴 교훈: 삭제 전에는 문서 이름/날짜만 보지 말고, **코드 주석이 그 문서를 아직 참조하는지**(`grep -r "문서파일명" --include="*.kt"`)까지 확인한다.

## 리팩토링 전략/진행 로그

`docs/refactoring/`은 기본적으로 비어 있다(문서 보존 정책 참고). 지금은 실제로 진행 중인 로드맵이 있어 예외적으로 1개를 담고 있다.

| 위치 | 용도 |
| --- | --- |
| `refactoring/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md` | 7계층 원칙을 실제 코드에 단계적으로 반영하는 로드맵. Stage D(로컬/원격 `EngineCoreApi` 계약 대등화)·Stage E(`RemoteEngineSessionClient`, 원격 후보 선택)는 260804에 완료. Stage E-3(개발용 HTTP 참조 서버 + 디버그 토글 배선, 260818)까지 실제 대국 e2e 검증 완료. Stage F(물리적 분산·DePIN)는 전용 킥오프 문서로 분리(아래 행) |
| `refactoring/REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md` | Stage F 전용 킥오프 — HTTP→MQ/Firestore 전환, 폰↔폰 지원, 세션 토픽 기반 정합성 체크·보상 점수 감사로그 설계. **결정 문서, 착수 전** — "맥북 파이썬으로 먼저 검증 후 앱 이식" 결정과 근거만 기록, 구현은 별도 승인 대기 |

이 문서의 작업이 전부 끝나면(또는 Stage F까지 갈 경우 그 전용 킥오프 문서로 넘어가면) 이 문서도 다시 삭제한다.

## 엔진 검증/연구 리뷰

대국 강도, search tree 정책처럼 결론이 `ENGINE_API_CALL_POLICY.md`/`ENGINE.md`에 반영된 뒤에도 근거 자료로 남겨야 하는 실험 리뷰.

| 위치 | 용도 |
| --- | --- |
| `engine-research/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` | B16/B32/B64 레벨 강도 실험과 결론 |
| `engine-research/ENGINE_SEARCH_TREE_REUSE_REVIEW.md` | KataGo search tree 재사용/격리 정책 검토 |
| `engine-research/ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md` | `빠른 초급`/`초급` 더블체크 + 레벨링용 후보수 확장 레버(`refinePolicyMoves`) 비용/효과 실측. 아직 앱에는 미반영, 방향 검토 문서 |
| `engine-research/ENGINE_BEGINNER_VISITS_BENCHMARK.md` | B16/B32/B64 후보수 최초 실측(2026-06-08). 위 CANDIDATE_EXPANSION_REVIEW가 같은 P0/P1/P2 국면을 재사용 — 2026-08-17에 `docs/archive/`에서 이 폴더로 이동 |
| `engine-research/FAST_BEGINNER_FIVE_TIER_REDESIGN_PLAN_2026-08-17.md` | `빠른 초급` 1~3단계 → 5단계(`초보`~`초고수`) 재정립 계획. 승인 대기, 앱 코드 미반영 |

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
