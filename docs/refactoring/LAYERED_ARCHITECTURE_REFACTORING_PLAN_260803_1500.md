# 레이어드 아키텍처 리팩토링 계획서 — 260803 15h00m

작성 시각: 2026-08-03 15:00 (KST)

## 0. 이 문서의 성격

`docs/ARCHITECTURE.md`(원칙 문서)와 `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md`(go-ai-coach 매핑 + 알려진 갭)가 2026-07-30에 정립한 7계층(4계층 압축 가능) 모델을, **실제 코드에 단계적으로 반영**하기 위한 착수 계획서다. 이 리포지토리의 "착수 계획서" 관례(`YYMMDD HHhMMm` 타임스탬프, 진행 로그 누적)를 따른다.

이 문서 하나로 전체 리팩토링이 끝나지 않는다 — 특히 마지막 Stage(물리적 분산, 다른 기기에서 연산)는 그 자체로 별도 킥오프 문서가 필요한 대형 신규 기능이다. 이 문서는 "지금부터 거기까지 가는 순서와, 각 지점에서 무엇을 확인해야 하는가"를 정의하는 상위 로드맵이다.

## 1. 배경과 목표

2026-07-30에 아키텍처 문서를 재정립했지만, `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`가 명시하듯 **코드는 아직 옮기지 않았다** — 재정의는 개념적 재배치였다. 이 계획서의 목표는 그 개념적 재배치를 실제 코드/테스트로 하나씩 실현하면서, 최종적으로 "카타고 엔진이 다른 사용자의 폰이나 서버에서 물리적으로 돌아가도 앱 상위 계층은 그대로"라는 원래 비전을 완수하는 것이다.

## 2. 완료 정의 (최종 상태 체크리스트)

- [ ] `LayeringContractTest.kt`가 2026-07-30판 7계층 경계(2/3계층 재편, 4/6계층 신설, 5/7번호 이동)를 기계적으로 강제한다
- [ ] 로컬 구현체(`KataGoProcessEngineAdapter`)와 원격 구현체가 `EngineCoreApi` 전체에 대해 대등한 계약을 만족한다
- [ ] `RemoteEngineSessionClient`가 존재하고, 여러 원격 후보 중 선택·신뢰도 판단을 수행한다
- [ ] 4계층(외부 연동) 중 최소 1개(결제 또는 로그인)가 실제 SDK로 연동 완료된다
- [ ] 6계층(세션/연속성)에 기기 식별자 기반 다중 기기 정책이 존재한다
- [ ] (장기) 실제 다른 물리 기기에서 엔진이 동작하는 PoC가 최소 1건 존재한다

## 3. 단계별 작업 (Stage A가 가장 먼저, 안전도 순)

### Stage A — 안전망 먼저 (문서-코드 정합성, 저위험, 기계적)
- **A-1.** `LayeringContractTest.kt` 전수 감사 — 주석/에러 메시지가 옛 계층 번호(2026-06-27판)를 참조하는 곳이 있는지 확인하고 문구만 정리. 로직(정규식, import 검사)은 이미 유효한 것과 새로 필요한 것을 구분만 하고 아직 강화하지 않는다.
- **A-2.** (선택) 계층 경계가 자주 헷갈리는 패키지(`application/engine`, `application/auth`, `application/premium`)의 KDoc 상단에 "N계층" 라벨을 명시 — 코드 이동 없이 가독성만 개선.

### Stage B — 4계층(External Integration) 서비스 본체 두껍게 하기 (중위험, 이미 진행 중인 트랙)
- **B-1.** `ui/AndroidAuthClient.kt`/`persistence/PremiumStateStore.kt`에 실패/재시도 판단을 추가 — 지금은 SDK 응답을 그대로 감쌀 뿐, 3계층의 `PositionAnalysisCacheResolver`에 해당하는 신뢰도 판단이 없다.
- **B-2.** `premium-mode/README.md` Step 3(광고)/Step 4(결제), `auth-onboarding/README.md` Step 2~3(Google/이메일 로그인) 진행. **각 마스터플랜 문서가 우선 소스**이며, 이 계획서는 "이 작업이 4계층에 속한다"는 배치 확인 역할만 한다 — 내용을 중복 관리하지 않는다.

### Stage C — 6계층(Session & Continuity) 공식화 (중위험)
- **C-1.** `auth-onboarding/README.md` Step 4(익명→실계정 승격, Firestore 동기화) 진행.
- **C-2.** 기기 식별자 기반 다중 기기 정책 설계·구현 — 이전 세션에서 논의만 하고 미착수 상태(구매 아이템 기기 제한 등).

### Stage D — 2계층 로컬/원격 계약 대등화 (중~고위험, 원격 엔진의 전제조건)
- **D-1.** `HttpRemotePositionAnalysisTransport`(현재 position-analysis 단위 read-only 스파이크)를 `EngineCoreApi` 전체(또는 그에 준하는 상위 부분집합 계약)로 확장.
- **D-2.** 실패·타임아웃·재시도가 로컬 구현체(`KataGoProcessEngineAdapter`)와 동일한 방식으로 3계층에 보이는지 계약 테스트로 검증. (참고: 로컬 구현체는 2026-07-30에 뮤텍스 직렬화 + 강제 리셋을 이미 갖췄다 — 원격 구현체도 최소한 이만큼의 견고성이 있어야 대등하다고 볼 수 있다.)

### Stage E — 3계층 DePIN 준비: RemoteEngineSessionClient (고위험, 신규 기능)
- **E-1.** 여러 원격 후보 중 선택/신뢰도 판단 로직 설계 — 이 시점까지는 실제 피어 네트워킹 없이 "고정된 원격 서버 1대"로 시작 가능(피어 탐색은 Stage F).
- **E-2.** `EngineSessionClient` 구현체를 Local/Remote 중 런타임에 전환 가능하게(설정 플래그 또는 자동 판단).

### Stage F — 1계층 + 실제 물리 분산("다른 폰에서 연산") (최고위험, 별도 대형 프로젝트)
- **F-1.** 실행 위치를 나타내는 명시적 값 타입 설계(로컬/지정 서버/피어 기기).
- **F-2.** 피어 디바이스 탐색·인증·신뢰 프로토콜. **이 항목은 이 계획서의 범위를 벗어나는 별도 설계·보안 검토가 필요한 신규 대형 기능이다.** 특히 연산 에너지를 사고파는 마켓플레이스(포인트 적립/차감)는 부정사용 방지, 서비스 약관, 정산 정확성 문제를 동반한다 — 착수 시점에 **전용 킥오프 문서를 새로 작성**해야 하며, 이 계획서는 "여기까지 오면 별도 문서가 필요하다"는 지점만 표시해 둔다.
- **F-3.** PoC: 정산/포인트 없이, 로컬 네트워크 내 2대 기기로 "다른 기기의 분석 결과가 온다"만 최소 검증.

## 4. 실행 원칙

- Stage A/B/C는 서로 독립적이라 병행 가능하다. Stage D 이후부터는 순서대로 진행하는 것을 권장한다(원격 계약 대등화 없이 RemoteEngineSessionClient를 만들면 기반이 없는 것과 같다).
- 각 작업 항목 완료 시 `make test` 통과를 확인하고, 이 문서의 "진행 로그"에 한 줄 기록한다.
- `LayeringContractTest.kt`는 **코드가 실제로 그 경계를 만족한 뒤에만** 강화한다 — 먼저 강화해서 아직 옮기지 않은 코드를 실패시키지 않는다(`GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 기존 원칙 계승).
- Stage E/F는 "이미 있는 것을 정리"가 아니라 "새로 만드는" 성격이 강하다. 착수 전 반드시 별도로 사용자 승인을 받는다 — 이 계획서에 항목이 있다는 것 자체가 승인은 아니다.
- 커밋은 매 작업 항목 단위로 하고, 사용자의 명시적 확인 후에만 push한다(이 리포지토리의 기존 관례).

## 5. 진행 로그

- 260803 15h00m — 계획서 최초 작성. 아직 착수 항목 없음.
- 260803 — A-1 완료: `LayeringContractTest.kt` 전수 감사. `계층`/`Layer`/`layer` 및 2026-06-27판 옛 계층 이름(`Engine Runtime/Transport`, `Engine Core API Domain`, `Core Rules Domain`, `Middleware/Cache Domain`, `Game Domain`, `App Service/Session Orchestration`, `Presentation/Game UX`)을 grep했으나 이 파일 어디에도 옛 계층 번호/이름 텍스트가 없음을 확인 — 모든 테스트가 처음부터 패키지/클래스명(`application/auth`, `EngineCoreApi`, `middleware` 등)으로 경계를 표현하고 있어 번호에 결합돼 있지 않았다. 문구 정리 대상 없음(코드 변경 없음). `make test` 통과 확인(BUILD SUCCESSFUL). 단, 로드맵의 실제 갭(2/3계층 로컬-원격 계약 대등화 등)은 테스트 "로직" 자체가 아직 새 경계를 강제하지 않는다는 뜻이며, 이는 Stage A 범위가 아니라 Stage D 이후에서 다룬다.
- 260803 — A-2 완료: 계층 경계가 헷갈리기 쉬운 대표 파일 5개의 KDoc에 "N계층" 라벨 추가(코드 이동/로직 변경 없음). `application/engine/EngineSessionClient.kt`(3계층 진입점), `application/auth/AuthClientPort.kt`(4계층 α 포트) vs `AuthState.kt`(6계층 상태) — 같은 패키지에서 계층이 갈리는 지점을 서로 참조하도록 명시, `application/premium/PremiumStatePorts.kt`(4계층 α) vs `PremiumState.kt`(6계층)도 동일하게 처리. `make test` 통과 확인(BUILD SUCCESSFUL).

## 6. 관련 문서

- [../ARCHITECTURE.md](../ARCHITECTURE.md) — 레이어 원칙(앱 비종속)
- [../GO_AI_COACH_ARCHITECTURE_ROADMAP.md](../GO_AI_COACH_ARCHITECTURE_ROADMAP.md) — 계층별 현재 매핑, 알려진 갭
- `premium-mode/README.md`, `auth-onboarding/README.md` — Stage B/C의 1차 소스 문서
