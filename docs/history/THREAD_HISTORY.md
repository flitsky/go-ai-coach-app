# 스레드 히스토리

이 파일은 프로젝트 대화와 결정사항을 짧게 누적 기록하기 위한 문서입니다.
앞으로 주요 커뮤니케이션 문서는 한글로 작성합니다.

## 지난 히스토리 요약 (2026-05-31 ~ 2026-08-04)

2026-08-05에 정리한 요약입니다. 그전까지는 이 문서가 2026-05-31 날짜 헤더 하나 아래로 1,622줄이 계속 이어붙여져 있었고(날짜 헤더가 그 이후로 한 번도 새로 추가되지 않았음) 나중에 훑어보기 어려운 상태였습니다. 당시엔 원문을 지우지 않고 별도 아카이브 파일에 그대로 보존했으나, 2026-08-17 문서 보존 정책 전환(`docs/DOCS_INDEX.md` "문서 보존 정책" 참고)으로 그 아카이브 파일 자체를 저장소에서 제거했습니다 — 원문이 필요하면 `git log --all --diff-filter=D -- 'docs/archive/**/THREAD_HISTORY_DETAIL*'`로 삭제 커밋을 찾아 복원할 수 있습니다. 아래는 시기별 핵심만 압축한 것입니다.

### 초기 스택/엔진 POC (2026-05-31)
- Android-first, Kotlin Multiplatform 채택(Flutter는 크로스플랫폼 UI 재사용이 더 중요해지면 재검토하는 후순위) — 핵심 이유는 엔진 통신을 `EngineAdapter` 뒤에 숨겨 stub/process/JNI/원격 전환 시 UI/도메인 코드를 다시 안 써도 되게 하는 것.
- `shared`/`engine-android`/`app-android` 3모듈 구조 확립. `StubEngineAdapter`로 시작해, 이후 Android NDK/CMake로 실제 KataGo arm64 바이너리 빌드에 성공(SELinux 실행 제약 발견 → native lib 경로로 우회).
- 바둑 규칙(포획/자살수/패)은 KataGo에 그대로 맡기지 않고 `shared`에 직접 구현하기로 결정했다 — KataGo GTP가 안정적인 JSON 보드 상태를 안 주기 때문. KaTrain 소스는 설계 참고만 하고 코드는 복사하지 않았다.
- `Makefile` 기반 워크플로우(`make test`/`dev`/`prebuild-engine`/`install-dev-engine` 등)를 확립했다 — 지금까지 이어지는 표준 진입점.
- 환경 메모: 로컬 셸 기본 `java`가 25라 Gradle Kotlin DSL이 즉시 실패한다 — 이후 모든 검증은 `JAVA_HOME=$(/usr/libexec/java_home -v 17)` 명시가 필수라는 점이 문서 전체에서 반복 확인된다(현재도 유효).

### 대국 UX 반복 (2026-05-31 ~ 06월 초)
- 무르기, Top Moves/후보수 오버레이, ownership heatmap, 실시간 score/win-rate 그래프, 좌표/수순 번호 등 KaTrain 스타일 UX를 옵션 기반으로 순차 도입했다(당시 추적 문서 `docs/KATRAIN_UX_BACKLOG.md` — 항목이 모두 흡수된 뒤 삭제됐다).
- 계가 로직을 여러 차례 실사용 버그로 다듬었다 — 사석 미정리 상태에서 pass 종료 시 점수가 뒤집히는 문제를 다수 실제 로그로 재현(`docs/engine/error-cases/`)해 `DeadStoneCleaner`/`DeadStoneDetector`/`EndgameScoreSelector`로 해결했다. 기본 계가 규칙도 중국식(Area)에서 한국/일본식(Territory)로 전환하고 Area/Territory 토글을 추가했다.
- 배포 형태를 debug APK(~12MB, 엔진 미포함)와 `friend` 빌드 타입(엔진 내장 ~105MB, `make friend-apk`)으로 분리했다 — 평소 개발 루프 속도를 지키기 위함.

### 엔진 강도/레벨링/벤치마크 (06월 중순 전후)
- KataGo 후보수(visits: 16/32/64)별 체감 난이도를 실제 자동 대국 매트릭스(50~150판 단위, `docs/measurements/engine-match/`)로 검증하며 `빠른 초급`/`초급`/`중급`/`고급` `PlayLevelGroup` 체계로 정착시켰다. 색상/손실(`pointLoss`) 표시 기준을 KaTrain 공식과 여러 차례 맞대조해 정정했다.
- 기기별 실행 시간 편차가 커서 로컬 스크립트(`scripts/run-katago-device-benchmark.py`)와 인앱 최초 실행 벤치마크(진행 팝업 + 결과 팝업, 메뉴에서 재실행 가능)를 만들어 체감 속도와 실측을 분리했다.
- 핵심 버그 하나: KataGo 프로세스가 살아있는 동안 이전 탐색의 search tree/NN cache를 다음 탐색이 재사용해, AI 대 AI 자동대국에서 약한 레벨(B16)이 직전 강한 레벨(B64)의 탐색 결과를 몰래 물려받아 실력 경계가 오염되는 현상을 발견하고 해결했다(`docs/engine/ENGINE_SEARCH_TREE_REUSE_REVIEW.md`) — 최종 정책은 "AI vs AI는 착수 직전 `clear_cache`, 사람 vs AI는 재사용 유지"다(사람 상대는 이어지는 탐색이 정상적인 엔진 활용이므로).

### 아키텍처 리팩토링 대장정 (06월 중순 ~ 07월, 지금도 이어지는 방법론)
- `GoCoachApp.kt` 한 파일이 ~2,000줄까지 비대해진 문제를 인식하고 완성도(68% → 82% → 86%...)를 추적하며 장기간 단계별 추출 리팩토링을 진행했다.
- `EngineAdapter`(구) → `EngineCoreApi`(순수 엔진 계약) → `EngineSessionClient`(미들웨어)로 계층을 분리한 뒤, **현재 `docs/ARCHITECTURE.md`의 근간이 된 7계층 모델**(Engine Runtime/Transport, Engine Core API, Core Rules, Middleware/Cache, Game Domain, App Service/Session Orchestration, Presentation)을 확립했다.
- `GameSessionCoreState`와 analysis/score/runtime/moveReview 하위 state를 Compose 개별 `remember` 상태에서 단일 source of truth reducer로 승격했다. `LayeringContractTest`를 도입해 계층 경계 위반(UI가 raw 엔진 API를 직접 참조하는 등)을 자동으로 막기 시작했다 — 이후 이 테스트가 계속 확장되어 지금은 1,000줄 넘는 회귀 방지 스위트가 되었다(2026-08-05 이메일/Google 로그인 작업 중에도 이 테스트를 직접 확인했다).
- 이후 수십 라운드에 걸쳐 undo/Top Moves/score sync/benchmark/saved game/debug report/auto AI turn 등 개별 기능을 "전용 Application Runner 추출 + 테스트 추가 + `LayeringContractTest` 가드 추가 + `make test` 검증 + 커밋" 패턴으로 반복 분리했다 — 이 패턴 자체가 이 저장소의 표준 리팩토링 방법론으로 자리잡았다. 엔진 구현체(`KataGoProcessEngineAdapter` 등)도 `engine-android` 모듈로 물리적으로 이전되고 `internal`+`EngineCoreApiFactory` 뒤로 숨겨졌다.
- 문서가 늘어나며 `docs/DOCS_INDEX.md`(문서 지도)와 `docs/archive/<날짜>-<사유>/` 보관 정책을 확립했다 — "삭제 대신 보관"이 이 저장소의 문서 운영 원칙이었다. **2026-08-17에 이 원칙을 뒤집었다**: 프로젝트가 초도 발행 단계로 넘어가며 과거 리팩토링/의사결정 서사가 매 세션 코드베이스 탐색 시 토큰만 소모하는 부채로 판단돼, `docs/archive/`와 종료된 `docs/refactoring/`을 저장소에서 완전히 제거했다(git 히스토리로만 보존). 사유와 복원 방법은 `docs/DOCS_INDEX.md` "문서 보존 정책" 절 참고.

### 프리미엄/인증/UX 개편
이 구간의 상세 진행 로그는 이 파일이 아니라 각 기능 전용 마스터플랜 문서에 있다(`docs/DOCS_INDEX.md`에 등록된 관례) — 이 파일에는 자세히 기록되지 않았다.
- 수익화(광고 시청 기반 1시간 프리미엄, 영구 구매): [`premium-mode/README.md`](../../premium-mode/README.md)
- 최초 실행 온보딩 + Firebase 계정 시스템(Google/이메일/익명): [`auth-onboarding/README.md`](../../auth-onboarding/README.md)
- 보드/패널/UX 개편: [`ux-improvement/README.md`](../../ux-improvement/README.md)

## 2026-08-05

- Google 로그인(Step 2, 커밋 `d4bd90b`)과 이메일/비밀번호 로그인(Step 3, 커밋 `debe374`) 실연동을 완료했다. `AuthClientPort`에 `signInWithGoogle`/`linkGoogleCredential`/`signInWithEmail`/`linkEmailCredential`/`currentAuthState`를 플랫폼 비종속으로 추가하고, `AndroidAuthClient`가 실제 Firebase Auth를 호출한다. Credential Manager 호출은 `GoogleCredentialManagerClient`로, 이메일 폼/공유 시도 흐름은 `EmailSignInDialog`+`EmailSignInFlow`로 분리했다. 에뮬레이터(`Pixel_7_API_35`)에서 Google 실계정, 이메일 신규 가입/기존 로그인/오답 비밀번호 세 경로를 모두 실측했다. 상세 근거는 `auth-onboarding/README.md`의 "Step 2/3 구현" 절.
- 이메일 로그인은 계정 생성을 먼저 시도하고, 이미 가입된 이메일이면(`FirebaseAuthUserCollisionException`) 로그인으로 폴백하는 순서를 택했다 — 반대 순서(로그인 먼저, 실패 시 가입)는 최신 Firebase Auth가 "미가입 이메일"과 "비밀번호 오류"를 계정 열거(enumeration) 방지 목적으로 같은 예외(`FirebaseAuthInvalidCredentialsException`)로 합칠 수 있어 그 구분을 신뢰할 수 없기 때문이다. 이메일 중복(충돌)은 여전히 명확히 구분되는 신호라 이 순서가 더 견고하다.
- Email Link(비밀번호 없는 로그인)는 콘솔에서 이미 켜져 있었지만 구현하지 않았다 — Firebase Dynamic Links가 2025-08-25에 완전히 셧다운되면서, 그 공식 대체 경로(Firebase Hosting 기본 도메인 + Android App Links 딥링크)가 셧다운 이후 새로 만든 이 프로젝트에서 추가 마이그레이션 없이 바로 동작하는지가 문서상 불명확했다. 잘못 붙이면 "이메일 링크를 눌러도 앱이 안 열리는" 방식으로 조용히 깨질 위험이 있어, 자체완결적인 Email/Password를 먼저 선택하고 Email Link는 그 불확실성이 해소되면 이어 붙이기로 보류했다.
- **파이어베이스 Auth의 익명(Anonymous) 로그인은 이 프로젝트에서 켜지 않기로 확정했다 — 구조적 한계 때문이다.** 사용자가 이전(장기) 앱에서 이를 활성화했다가, 앱을 지웠다 다시 깐 사용자마다 새 익명 계정이 만들어져 콘솔에 허수 유저만 계속 쌓이는 문제를 겪었음을 확인해줬다. 원인은 설정 실수가 아니라 기능 자체의 설계다: 파이어베이스 익명 로그인은 "기기 로컬에 캐시된 세션이 없으면 새 익명 사용자를 생성"하는 구조라, 재설치(=로컬 캐시 소실)마다 이전 익명 계정은 고아로 남고 새 계정이 또 생긴다. 공식 완화책(휴면 익명 계정 자동 삭제)도 Identity Platform 프로젝트 업그레이드가 전제라 간단한 설정 변경이 아니다. 이 앱의 게스트 기능("계정 없이 시작하기", `DeviceIdentityStorePort`의 로컬 UUID)은 파이어베이스 익명 로그인과 완전히 무관하게 이미 동작하므로, 이 결정으로 잃는 기능은 없다. Step 4(Firestore 동기화) 설계 시에도 파이어베이스 익명 로그인이 켜진다는 전제를 깔지 않는다.
- 용어를 정리했다 — **'계정 없이 시작하기'**(앱 기능, 로컬 UUID 기반, 파이어베이스와 무관)와 **'파이어베이스 Auth'의 익명 로그인 활성화 여부**(파이어베이스 콘솔 설정, 현재 비활성)는 서로 다른 개념이며 앞으로 혼용하지 않는다. 이전에 "Step 1~3이 완료됐다"는 요약에서 이 둘을 "익명"이라는 한 단어로 뭉뚱그려 사용자에게 혼선을 준 적이 있다.
- `AndroidAuthClient.signInAnonymously()` 호출은 여전히 코드에 남아 게스트 버튼에서 fire-and-forget으로 시도되지만, 파이어베이스 익명 로그인이 꺼져 있어 계속 조용히 실패만 한다. 실질적 영향은 없고, 제거는 아직 요청받지 않아 그대로 뒀다.
