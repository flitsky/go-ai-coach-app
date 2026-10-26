# go-ai-coach 아키텍처 현황과 고도화 로드맵

작성일: 2026-07-30
갱신: 2026-09-23 — 260816 `:shared` 대이동을 매핑에 반영해 **아래 경로를 전부 실재 경로로 되돌렸고**, 매핑 밖에 떠 있던 패키지(`persistence/`·`vision/`·`shared/shared/` 하위)를 계층에 편입했으며, 2026-09-23 실측(import 방향 위반·`application/` 내부 SCC)을 「알려진 갭」에 편입했다.
갱신: 2026-09-28 — refactor backlog #49·#81: `shared.domain`을 `:core:domain`, `shared.enginecontract`·`shared.scoring`을 `:core:enginecontract` Gradle 모듈로 뗐다(패키지 이름 그대로). 아래 경로와 「Gradle 모듈 지도」에 반영했다.
레이어 순서 갱신: 2026-07-30 — External Integration이 4계층(3계층과 대등한 서비스 계층)으로 재배치되며 Application(5)/Session & Continuity(6)/Presentation(7) 번호가 한 칸씩 밀렸다. `ARCHITECTURE.md`의 "레이어 순서 확정" 항목 참고.
기능 엔타이틀먼트 정책 배치: 2026-08-14 — "무료/광고/구매/클레임" 같은 기능별 정책이 앞으로도 계속 바뀔 것을 전제로, 그 정책 판정을 6계층에 `FeatureAccessPolicy`로 명문화하고(설계 초안엔 5계층으로 잘못 적었다가 착수 시점에 정정 — 6계층 `PremiumState`를 파라미터로 받으므로 5계층일 수 없다) 6계층 `PremiumState`를 단일 플래그(`isUndoClaimed`)에서 기능별 원장(`claimedFeatures: Set<FeatureId>`)으로 일반화, 프레젠테이션 3곳(`ui/play/GamePlaySection.kt` 2곳, `ui/setup/KaTrainUxPanels.kt` 1곳)에 하드코딩돼 있던 판정을 이걸로 교체했다 — 구현 완료. 상세는 "알려진 갭"·"고도화 로드맵" 절, 그리고 `docs/spec/FEATURE_ACCESS_PRINCIPLES.md` 2장(같은 결론을 정책 문서 쪽에서 먼저 제안해 뒀던 것).
5·6계층 재분류: 2026-09-25(refactor backlog #84) — 엔타이틀먼트 클러스터(`premium.app`·`attendance`·`consumable`·`botcharacter`·`lifecycle`·`device`)를 5계층에서 6계층으로 다시 매핑했다. 5계층 규칙(*"6계층을 모른다"*)은 그대로이고 코드 이동·동작 변경은 없다. 5→6 import가 16줄에서 0줄이 됐다. 상세는 5계층 절 「2026-09-25 재분류」.
갱신: 2026-10-17 — **계층별 절(1~7계층·조립 루트)과 「알려진 갭」·「고도화 로드맵」의 계층 항목을 `docs/architecture/`의 계층 파일 8개(`LAYER_1_…`~`LAYER_7_…`·`COMPOSITION_ROOT.md`)로 문장 그대로 옮기고**, 이 문서를 지도(계층 → 파일 표·모듈 지도·계층을 가로지르는 갭)로 줄였다. 이 문서는 `docs/spec/`에서, `ARCHITECTURE.md`는 `docs/`에서 이 폴더로 옮겼다 (사용자 요청 — 기준 문서를 계층 단위로 추적). 옮기기 전의 계층 절 원문은 이 파일의 `git log -p`에 있다.

---

## 어떻게 읽는가 — 원칙·지도·계층 파일은 `docs/architecture/` 한 곳에 있다

1. `ARCHITECTURE.md` — **원칙**. 7계층의 정의와 그 이유. 앱 비종속이라 코드 이동과 무관하게 유효하다.
2. `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`(**이 문서**) — **지도**. 계층 → 파일 표, Gradle 모듈 지도, 계층을 가로지르는 갭과 로드맵.
3. `LAYER_1_PHYSICAL_COMPUTE.md` ~ `LAYER_7_PRESENTATION.md`, `COMPOSITION_ROOT.md` — **계층마다 하나씩, 정본 매핑**. *"그 계층이 지금 어디 있고, 무엇이 지키며, 무엇이 남았는가"*. 코드가 움직이면 그 계층 파일이 따라 움직인다. `:shared`·`:core:*` 패키지의 계층 배정과 ui 층은 코드(`ContractSymbols.kt`)가 정본이고, 계층 파일의 배정 블록은 `LayerDocContractTest`가 코드와 대조하는 사본이다.
4. `work/plans/REMOTE_ENGINE_AND_LAYERING.md` — **실행 Stage 로그**. Stage A~E의 착수·완료 기록이 시간 순으로 쌓인다(Stage F는 `work/roadmap/archive/260818-_REMOTE_ENGINE_MQ_TRANSPORT.md`로 분리 — 이 파일 자체도 2026-09-23에 `REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md`에서 개명됐다). ⚠️ **2026-09-23에 개명·이동했다** — 옛 이름 `work/roadmap/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`로 찾으면 없다. 계층 정렬(Stage A~C)의 정본은 아래 4번으로 넘어갔고, 그 문서에 살아 있는 축은 원격 엔진(Stage D~F)이다.
5. `work/roadmap/archive/260923-260928_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` — **2026-09-23 실측 기록·처방·함정 A~J**. *"그날 재어 보니 이랬다"* 를 남기는 문서라 **본문은 그 시점 그대로 두고 갱신하지 않는다.** 실측 결론만 이 문서로 흡수한다. 함정 A~J는 `docs/spec/PITFALLS.md`에 **67~76번으로 편입**됐고 번호 쪽이 정본이다.

⚠️ **2026-10-17 전에는 1·2가 `docs/`·`docs/spec/`에 따로 있었고, 계층 하나를 보려면 이 문서의 계층 절과 4의 Stage 절을 함께 열어야 했다** — 같은 로드맵 항목(2계층 대등화·3계층 원격 세션·4계층 본체·6계층 공식화)이 두 문서에 적혀 있었다. 지금은 원칙·지도·계층 파일이 이 폴더에 모였고, 4·5만 `work/` 아래다(4는 기능축 문서, 5는 봉인 문서).

**성격**: 이 문서는 `ARCHITECTURE.md`(원칙 문서, 앱 비종속)의 7계층 모델을 go-ai-coach 코드베이스에 적용한 **파생 문서**다. 레이어 정의 자체나 그 이유는 여기서 반복하지 않고 원칙 문서를 따른다.

기존 `ARCHITECTURE.md`가 갖고 있던 계층별 파일/패키지 표는 이 문서로 이전됐다 — 이전 버전은 git 히스토리로 확인 가능하다(`git log -p -- ARCHITECTURE.md`).

---

## 계층별 문서

| 계층 | 파일 | 코드의 배정 목록(`ContractSymbols.kt`) |
| --- | --- | --- |
| 1 Physical Compute | `LAYER_1_PHYSICAL_COMPUTE.md` | — (`engine-android`뿐) |
| 2 Middleware / Bridge | `LAYER_2_MIDDLEWARE_BRIDGE.md` | `LAYER_2_PACKAGES` |
| 3 Extended API | `LAYER_3_EXTENDED_API.md` | `LAYER_3_PACKAGES` |
| 4 External Integration | `LAYER_4_EXTERNAL_INTEGRATION.md` | `LAYER_4_ALPHA_PACKAGES`(α 포트만) |
| 5 Application / Domain | `LAYER_5_APPLICATION_DOMAIN.md` | `LAYER_5_PACKAGES` |
| 6 Session & Continuity | `LAYER_6_SESSION_CONTINUITY.md` | `LAYER_6_PACKAGES` |
| 7 Presentation | `LAYER_7_PRESENTATION.md` | `UI_LAYERS`(ui 하위 L0~L8) |
| 계층 밖 — 조립 루트 | `COMPOSITION_ROOT.md` | — (`LayeringContractTest`의 루트 파일 목록) |

> "재편 여부" 항목은 2026-06-27판 7계층(Engine Runtime/Transport → Engine Core API Domain → Core Rules Domain → Middleware/Cache Domain → Game Domain → App Service/Session Orchestration → Presentation/Game UX) 대비 2026-07-30 재정의에서 경계가 바뀐 지점을 표시한다.
>
> ✅ **260816 이전 완료 — 아래 경로는 전부 실재 경로다**(2026-09-23 `find`로 재확인). 2026-07-30판에 있던 *"코드는 아직 옮기지 않았다, 아래는 개념적 재배치"* 경고는 더 이상 사실이 아니라 걷어냈다. 260816 웨이브 1~6으로 `application/` 트리 124개 프로덕션 파일과 `match/`가 `app-android`에서 `:shared`로 물리 이전됐고, 지금 `app-android/.../application/`에 남은 것은 영구 예외 `diagnostic/LocalFileDiagnosticEventExternalSink.kt` **하나뿐**이며 `app-android/.../match/`는 **디렉터리 자체가 없다.**

> 🧱 **Gradle 모듈 지도**(2026-09-28, refactor backlog #49·#81). 패키지 이름은 하나도 바뀌지 않았다 — 모듈만 갈랐다.
>
> | 모듈 | 폴더 | 담은 패키지 | 본 소스셋 의존 |
> |---|---|---|---|
> | `:core:domain` | `core/domain/` | `shared.domain`(바둑 규칙 커널, 7파일) | **없음** — `DomainModuleBuildScriptContractTest`가 빌드 스크립트를 읽어 막는다 |
> | `:core:enginecontract` | `core/enginecontract/` | `shared.enginecontract`(2계층 계약) · `shared.scoring`(로컬 계가기) | `api(:core:domain)` |
> | `:shared` | `shared/` | `application.*` 37개 · `match` · `shared.policy`·`playstyle`·`diagnostic`·`content`·`vision` | `api(:core:enginecontract)` + 코루틴 |
> | `:engine-android` | `engine-android/` | `engine.android` | `api(:core:enginecontract)` — 앱 계층(`:shared`)은 **테스트만** 본다 |
> | `:app-android` | `app-android/` | 나머지 전부 | `:shared` + `:engine-android` |
>
> - **컴파일러가 새로 지키는 것**: 도메인은 라이브러리·계약·앱 계층을 모른다. 계약·계가기는 앱 계층(`application.*`·`match`·`shared.policy` 등)을 모른다. `engine-android` 본 코드도 앱 계층을 모른다. 아래 모듈의 `internal`(`BoardRegionAnalyzer` 등)은 앱 계층에서 안 보인다.
> - **여전히 테스트가 지키는 것**: `:shared` **안**의 계층(3·4α·5·6계층이 한 모듈에 있다) — `LayeringContractTest`·`PackageCycleRatchetTest`(세 모듈의 commonMain을 한 그래프로 본다).
> - ⚠️ `shared.scoring`은 원칙상 5계층(순수 규칙)이지만 모듈은 `:core:enginecontract`다 — 계가기가 전부 계약 타입(`FinalScoreResult`·`ScoreEstimate`·`EngineStatus`)을 돌려주기 때문이다. 모듈과 계층이 1:1이 아니다.
> - ⚠️ 모듈 사이 테스트 픽스처(골든 판 파서 등)는 `core/domain/src/commonTestFixtures/`에 있고 위 모듈들이 srcDir로 함께 컴파일한다(`shared/build.gradle.kts`의 commonTestSupport와 같은 수단).
> - `:shared` → `:core:application` 개명(설계 C4)은 하지 않았다 — 새로 지켜지는 것 없이 300여 파일만 옮겨진다.

---

## 알려진 갭 — 계층을 가로지르는 것

계층 하나에 속한 갭은 그 계층 파일의 「갭과 남은 일」로 옮겼다(2026-10-17). 여기에는 여러 계층에 걸친 것만 남는다.

### 이미 닫힌 것

- ~~`LayeringContractTest.kt`가 아직 2026-06-27판 옛 계층 이름 기준으로 작성돼 있다~~ — **사실이 아니었다(2026-09-23 확인).** 260803 Stage A-1이 옛 계층 이름 7종(`Engine Runtime/Transport` … `Presentation/Game UX`)을 전수 grep해 *"이 파일 어디에도 없다"* 를 확인하고 이 항목을 닫았다(`work/plans/REMOTE_ENGINE_AND_LAYERING.md` 진행 로그 — 2026-09-23 개명 전 이름은 `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`였다). 이 테스트들은 처음부터 계층 번호가 아니라 패키지/클래스명(`application/auth`, `EngineCoreApi`, `middleware`)으로 경계를 표현한다. 오늘 `0c33d32c`로 스캔 경로까지 `:shared` 실재 경로로 갱신됐다.

### 열려 있는 것

- **계층 강제 수단이 문자열 스캔이라는 것 자체가 갭이다**(2026-09-23 실측, 진단서 §1.2). `LayeringContractTest.kt`의 import 규칙 10개 중 **4개가 0개 파일을 검사하며 무조건 통과**하고 있었다 — 260816에 코드가 `shared`로 이사했는데 스캔 경로가 따라오지 않았고, `ktFilesIn`이 없는 디렉터리를 조용히 빈 목록으로 돌려줬기 때문이다. 즉 **초록이 "위반 없음"이 아니라 "검사 안 함"이었다.** 오늘 `0c33d32c`(경로 갱신)와 `01a85479`(`ktFilesIn`에 `require(files.isNotEmpty())`)로 **빈 디렉터리**는 봉했지만, *"스캔은 되는데 금지 문자열이 낡아 아무것도 못 잡는다"* 는 **여전히 조용히 통과한다.** 금지 조각이 저장소에 실재하는지 보는 메타 검사가 필요하다.
- **import 방향 위반이 730/2,004개(36%)다**(2026-09-23 실측). 내역: L7→L5 **389**, L3→L5 **169**(엔진 서비스가 세션/도메인을 안다 — 방향이 거꾸로다), L2→L5 66, L7→L3·L4·L2 92, 기타 14. 즉 UI가 계층을 건너뛰어 유스케이스를 직접 부르고, 아래 계층이 위 계층을 아는 구간이 실재한다.
- **`application/` 안에 17개 패키지 강결합 사이클(SCC)이 있다**(2026-09-23 실측, Tarjan). `analysis, autoai, debugreport, diagnostic, endgame, engine, engine.operation, humanmove, preferences, runtime, savedgame, score, session, startgame, topmoves, undo` + `middleware`. **3계층으로 선언된 `application/engine`과 5계층으로 선언된 `application/session`이 같은 사이클 안에 있다** — 사이클 안에서는 어느 쪽이 상위인지 정의되지 않으므로, 이 구간에 대해 7계층 서사는 참도 거짓도 아니고 **성립하지 않는다.**
  ✅ 2026-09-28(refactor backlog #49): 사이클이 0이 된 뒤(`#32`) **사이클 아래쪽**의 두 모듈(`:core:domain`·`:core:enginecontract`)만 뗐다 — 아래 경고가 말하는 `application/` 안쪽 분리가 아니다. `application/` 안은 여전히 한 모듈이다.
  ⚠️ **그래서 지금 "모듈 분리"를 제안하면 안 된다.** `application/engine`만 떼어내는 순간 session·score·match·runtime·endgame이 딸려오고, 그것들이 다시 engine을 참조해 **Gradle 순환 의존으로 빌드가 멈춘다. 사이클을 먼저 끊지 않은 모듈화는 컴파일에서 죽는다.** 모듈 경계는 이 문제를 **원리적으로 보지 못한다**(목표 그래프에서 17패키지가 전부 한 모듈 안에 들어가기 때문).
  ⚠️ 위 두 줄은 **2026-09-23 실측값**이고, 엣지 추출·SCC 계산 **방법과 원자료는 `work/roadmap/archive/260923-260928_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` §1에 있다.** 수치가 의심스러우면 거기 적힌 방법으로 다시 잰다 — 재생산 절차를 이 문서에 복사하지 않는다(낡으면 두 곳이 어긋난다).
- androidTest(Robolectric/계측) 커버리지가 기본 검증 경로에 없다(`make test`에 안 묶여 있음 — 의도적, M-04 제약). 컴파일+JVM 단위 테스트가 기본 검증이다. **260816**: `AppLaunchSmokeTest.kt`(실제 `MainActivity`→`createEngineBootstrap`→`GoCoachApp` 경로)가 `@Ignore` 스켈레톤에서 활성 테스트로 전환됐다. **260817**: `SavedSessionPromptSmokeTest.kt` 신설로 "saved-session-prompt" 경로도 완료. 더 넓은 이벤트 디스패치 커버리지만 여전히 열려 있다.

## 고도화 로드맵 — 계층을 가로지르는 것

우선순위 순서가 아니라 계층별로 정리한 것이며, 착수 순서는 별도 착수 계획서에서 정한다.

⚠️ **착수 계획서는 `work/roadmap/`에 `시작일-완결일_이름.md` 형태로 둔다**(완결 전에는 뒤를 비운다 — 예: `261026-_ACTIVE_BACKLOG.md`, 완결 후 예: `260919-260922_STUDY_CONTENT_AND_1_0_RELEASE.md`). 2026-10-08부터 그 폴더의 맨 위에는 활성 백로그 하나만 두고, 완결·보류 문서는 `work/roadmap/archive/`에 둔다. 2026-07-30판이 지시하던 *"`refactoring/`에 `YYMMDD HHhMMm` 타임스탬프 관례로 추가"* 는 **따르면 안 된다** — `docs/refactoring/` 폴더는 2026-08-17에 삭제됐고 `docs/DOCS_INDEX.md`가 **"되살리지 말 것"** 으로 못박았다. 시분 타임스탬프 관례도 그때 함께 폐기됐다.

계층별 항목(옛 1~7번)은 각 계층 파일의 「갭과 남은 일」로 옮겼다 — 번호는 그대로다(1→2계층, 2→3계층, 3→1계층, 4→4계층, 5→5계층, 6·7→6계층).

8. **계층 강제 수단을 문자열 스캔에서 컴파일러 쪽으로 옮기기**: 위 「알려진 갭」의 세 항목(죽은 안전망, import 방향 위반 730건, 17패키지 SCC)이 같은 뿌리를 가리킨다 — 규칙이 소스 텍스트를 훑는 형태라 코드보다 빨리 낡는다. 처방(패키지 FQN으로 계층을 드러내기 → PSI 기반 규칙 → 최후에 모듈)과 그 순서·함정은 `work/roadmap/archive/260923-260928_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` §3·§5에 있다. **코드가 실제로 옮겨지기 전까지 테스트를 먼저 조이지 않는다** — 아직 분리되지 않은 것을 분리된 것처럼 강제하면 오탐만 늘어난다.
9. ~~**문서 정리 후속 작업**~~ — **완료(260817)**. `docs/refactoring/`(리팩토링 축이 이미 종료됨)과 `docs/archive/` 전체(55개 파일, 1.2MB)를 저장소에서 제거했다. "삭제 대신 보관" 원칙을 뒤집는 결정이라 `docs/DOCS_INDEX.md` "문서 보존 정책" 절에 사유와 복원 방법을 기록했다. 유일한 예외는 실측 데이터로 계속 인용되던 `ENGINE_STRENGTH_RESEARCH.md`로, `docs/engine/`로 이동 보존했다.

## 관련 문서

- 레이어 원칙 자체(앱 비종속): `ARCHITECTURE.md`
- 계층별 정본 매핑: `LAYER_1_PHYSICAL_COMPUTE.md` ~ `LAYER_7_PRESENTATION.md`, `COMPOSITION_ROOT.md`(위 「계층별 문서」 표)
- 2026-09-23 실측 기록·처방·함정 A~J: `work/roadmap/archive/260923-260928_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` — **봉인 문서다**(그 시점 기록). 실측 결론은 이 문서가 흡수하되 그쪽 본문은 갱신하지 않는다.
- 엔진 탐색 방식·레벨 정책·캐시 운영 상세: `docs/ENGINE.md`
- 프리미엄/결제 로드맵: `work/plans/PREMIUM_MODE.md`
- 인증/온보딩 로드맵: `work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md`
- 기능 유/무료 정책 원칙("무엇을 무료/광고/구매/클레임으로 줄지"의 근거): `docs/spec/FEATURE_ACCESS_PRINCIPLES.md`
- 그 원칙을 초도 발행에 구체 적용한 전략/체크리스트: `work/plans/GOOGLE_PLAY_LAUNCH_PLAN.md`
- 함정 번호 정본(영구불변, 재사용 없음): `docs/spec/PITFALLS.md` — 진단서 §4의 함정 A~J가 **67~76번**으로 들어가 있다
- 원격 엔진 Stage 로그(계층 정렬 Stage A~C는 위 진단서로 이관): `work/plans/REMOTE_ENGINE_AND_LAYERING.md`
- 지금 무엇을 할 것인가(활성 백로그): `work/roadmap/261026-_ACTIVE_BACKLOG.md`
- 이 7계층 모델이 정착하기까지의 리팩토링 과정: 날짜별 작업 로그는 2026-08-17 문서 보존 정책 전환으로 삭제됐다(git 히스토리로만 보존). **진행 중인 로드맵은 `work/roadmap/`(보류·완결분은 그 아래 `archive/`)에 있다** — `docs/refactoring/` 폴더는 없어졌고 되살리지 않는다(`docs/DOCS_INDEX.md` 「문서 보존 정책」).
