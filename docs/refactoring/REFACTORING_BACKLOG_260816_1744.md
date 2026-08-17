# 리팩토링 백로그 — 우선순위와 착수 가이드

작성일: 2026-08-16

**성격**: 이 문서는 **리팩토링/코드 부채 정리 전용** 백로그다. 신규 기능 개발은 여기 포함하지 않는다 — 새 기능은 착수 시점에 별도 기준 문서(PDCA plan/design류)로 진행하고, 이 문서는 "리팩토링을 완결"하는 데만 집중한다. 항목이 끝나면 이 문서에서 취소선 처리하고 커밋한다 — 완료 기록을 다른 곳에 흩뿌리지 않고 이 문서 하나가 항상 "지금 뭐가 남았는가"의 답이 되게 한다.

---

## 서두 작업 원칙

새로운 스레드에서는 아래 원칙을 먼저 이해한 뒤, "작업 우선순위"의 항목 중 사용자가 착수를 원하는 것을 맡아서 진행하면 된다.

1. **범위 규율**: 이 문서의 항목만 다룬다. 작업 중 새 기능 아이디어가 떠오르면 이 문서에 추가하지 말고 사용자에게 별도로 제안한다 — "리팩토링 문서 하나, 신규 기능 문서 하나"를 섞지 않는 게 이 백로그의 핵심 원칙이다.
2. **깊은 배경은 로드맵 문서가 원본이다**: 계층/아키텍처와 관련된 항목은 각 항목에서 `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 해당 절을 가리킨다 — 파일 경로·판단 근거를 여기서 다시 베끼지 않는다. 착수 전 그 절을 먼저 읽을 것. 이 문서와 그 문서 내용이 어긋나면 로드맵 문서가 맞다(더 자주 갱신됨).
3. **빌드 환경**: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` 없이는 Gradle이 즉시 실패한다(시스템 기본 JDK가 25). `local.properties`(`sdk.dir=...`)도 필요 — gitignore돼 있어 새 환경에선 없을 수 있다.
4. **검증은 실기로, 가정하지 말 것**: 이 백로그 자체가 그 교훈에서 나왔다 — 2026-08-16 작업 중 "온보딩이 먼저 뜬다"는 가정, "엔진 부트스트랩은 순간적이다"는 가정이 둘 다 실제 에뮬레이터에서 깨졌다(각각 `FeatureFlags.isLoginEnabled=false`와 번들 모델 복사 시간 때문). 코드만 읽고 "될 것 같다"로 끝내지 말고, UI/androidTest가 걸린 항목은 실기(에뮬레이터/기기)로 직접 확인한다.
5. **완료 시 이 문서를 갱신한다**: 항목을 끝내면 아래 목록에서 취소선 처리 + 완료일 기록, 관련이 있으면 로드맵 문서의 해당 절도 함께 갱신 — 이번 세션 내내 지켜온 "문서가 항상 현재 상태와 일치해야 한다" 원칙을 그대로 따른다.
6. **날짜 표기 주의**: 대화가 여러 날에 걸쳐 이어질 수 있다. 문서에 날짜를 적기 전에 실제 오늘 날짜를 확인할 것(예: `date +%Y-%m-%d` 또는 시스템 프롬프트의 currentDate) — 이 문서 자체가 그 실수(이전 턴 날짜를 관성적으로 재사용)를 한 번 겪고 고친 결과다.

---

## 작업 우선순위

효과 표시: **Sonnet 5 엔진이 투입해야 할 노력** 기준(울트라 > 최대 > 엑스트라 > 높음 > 중간 > 낮음, 6단계). 항목 간 실제로 구분되는 만큼만 단계를 썼다 — 억지로 6단계를 다 채우지 않았다.

### ~~1. `GameSessionStateHolder` → `:shared` 모듈 본 이전~~ — **완료 (2026-08-16)**

**완료 기록**: 웨이브 1~6, 전체 124개 프로덕션 파일 + 대응 테스트가 전부 `shared/src/commonMain`·`commonTest`로 이전됐다(영구 예외 1개 프로덕션 파일 + 그걸 직접 테스트하는 3개 테스트 파일만 app-android 잔류, 설계상 정상 — 아래 "왜" 문단이 언급하는 4절 방침). 상세 실행 기록·교훈은 `docs/refactoring/GAMESESSION_SHARED_MIGRATION_KICKOFF_PLAN_260816_1808.md`(0절)이 원본. `:shared`/`:app-android` 컴파일 그린, `make test` 전체 그린, `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 에뮬레이터 실기 재확인 2회(웨이브5·6 완료 직후 각각) 전부 통과. `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`도 스캔 대상을 app-android에서 `:shared`로 갱신 완료(같은 날, 이 문서 자체가 명시했던 "이동 완료 후에만" 타이밍).

<details>
<summary>원래 항목 내용 (참고용)</summary>

**왜**: `application/` 트리(현재 125개 파일, 21개 서브패키지) 전체가 KMP(`:shared`)로 옮겨져야 iOS 등 다른 플랫폼에서도 도메인 로직을 재사용할 수 있다. 플랫폼 비종속성 자체는 `LayeringContractTest.kt`의 `engineOperationApplicationPoliciesStayPortable`이 이미 상시 검증하고 있어 "될까?"는 답이 났다 — 남은 건 실제 이동과 그 과정에서 나오는 설계 판단뿐.

**스파이크 완료(2026-08-16)**: `application/safety/EngineTurnWatchdog.kt`(가장 작은 서브패키지) 하나로 전체 절차 — 물리 이동, `internal`→public 가시성 조정, 테스트의 `org.junit.*`→`kotlin.test.*` 전환, 양쪽 모듈 컴파일, iOS 시뮬레이터 타깃 컴파일까지 — 를 실제로 검증했다. 전부 통과. 패키지명을 유지하면 app-android 호출부 import가 안 바뀐다는 것도 확인됨.

**범위**: 나머지 ~124개 파일. `GameSessionCoreState`/`GameSessionController`가 `autoai/engine/humanmove/savedgame/score/startgame/topmoves/undo/debugreport/analysis/movereview/preferences` 12개 서브패키지를 전이적으로 끌어들여 "일부만 이전"이 불가능하다 — 사실상 `application/` 전체가 한 단위.

**진행 방법 / 완료 기준**: `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` "고도화 로드맵" 5번 항목 참고 — 리프 파일부터 `GameSessionStateHolder`까지 순서, `internal` 가시성 감사 기준, `LayeringContractTest.kt` 갱신 타이밍(이동 완료 후에만)이 이미 적혀 있다. 완료 기준: `:shared`/`:app-android` 양쪽 컴파일 + `make test` 전체 그린 + `NewGameBoardTapSmokeTest.kt`/`AppLaunchSmokeTest.kt` 실기 재확인.

**주의**: 한 세션에 끝나는 규모가 아니다. `internal`→public 전환 범위가 파일마다 다를 수 있어, 큰 폭으로 넓어지는 지점이 나오면 착수 전에 사용자와 다시 상의할 것(되돌리기 번거로운 설계 결정이라 스파이크 계획서도 같은 이유로 조심스럽게 다뤘다).

</details>

### 2. androidTest 커버리지 확장 — **중간, saved-session-prompt는 완료 (2026-08-17)**

**완료 기록 (saved-session-prompt)**: `app-android/src/androidTest/java/com/worksoc/goaicoach/smoke/SavedSessionPromptSmokeTest.kt` 신설. `@Before`에서 `shared_prefs/` 초기화 후 `GameSessionStore(context).save(snapshot)`로 이동 1수짜리(양쪽 다 Human — AI 시트가 있으면 엔진 미준비 상태에서 auto-AI-turn이 걸릴 위험이 있어 회피) `SavedGameSnapshot`을 실제 저장소에 미리 심어둔다. 검증: Home에서 깜빡이는 "▶ 이전 대국 이어하기" 필 노출 → 탭하면 `ResumeSavedSessionDialog`("이전 대국 이어하기" 타이틀) 노출 → "예" 탭 → 보드 화면 전환 + 이동수 표시가 정확히 "1"(새 대국이 아니라 저장된 스냅샷이 실제로 복원됐음을 증명 — 탭 없이 곧바로 1수). 새로 발견한 것: `NewGameBoardTapSmokeTest.kt`가 이미 파일-private `FakeUnavailableEngineSessionClient`를 갖고 있는데, Kotlin 최상위 `private class`는 파일 스코프가 아니라 **같은 패키지 안에서 이름이 충돌**한다(가시성만 제한할 뿐 이름공간은 공유) — 새 테스트의 페이크는 `FakeNeverReadyEngineSessionClient`로 다른 이름을 써야 했다. 에뮬레이터(`emulator-5554`)에서 3회 연속 통과(1.4s/1.4s/1.2s), 무한 반복 깜빡임 애니메이션(`rememberInfiniteTransition`)이 Compose 테스트 idle-sync를 막을까 우려했으나 실측 결과 문제 없었다. `make test`에는 여전히 안 묶음(M-04 의도적 제약, 유지).

**왜(남은 범위)**: M-04 목표(app-launch/saved-session-prompt/new-game/event-dispatch/board-tap) 중 app-launch·new-game·board-tap·saved-session-prompt 4개는 끝났다. 더 넓은 이벤트 디스패치 커버리지만 아직 없다.

**진행 방법(남은 범위)**: 위 세 스모크 테스트를 템플릿으로 삼아 이어서 진행 — 어떤 이벤트들을 "더 넓은 디스패치"로 볼지(예: undo, 새 대국 설정 변경, 프리미엄 다이얼로그 등) 범위부터 사용자와 정하고 시작할 것.

**완료 기준(남은 범위)**: 새 스모크 테스트가 에뮬레이터에서 3회 연속 통과(이 세션의 검증 관례). `make test`에는 여전히 안 묶는다(M-04 의도적 제약, 유지).

### ~~3. `GoCoachApp.kt` 상태훅 예산 여유 확보~~ — **완료 (2026-08-17)**

**완료 기록**: 사용자에게 동작 변화(메뉴 열림 상태가 화면 이동 후 리셋됨)를 먼저 확인받고 진행(수용). `isDisplayMenuExpanded`를 `GoCoachApp.kt`에서 제거하고 `GoCoachContent.kt`로 내림(`var isDisplayMenuExpanded by remember { mutableStateOf(false) }`를 그 파일 안으로, `onDisplayMenuExpandedChange` 파라미터 스레딩 제거, 3곳의 콜백을 직접 대입으로 교체). `GoCoachApp.kt` 819→816줄, 상태훅 47→46개(둘 다 `LayeringContractTest.kt`의 `goCoachAppStaysWithinShrinkingUiShellBudget`에 실측값으로 다운시프트, 이 저장소 기존 관례). `make test` 전체 그린.

에뮬레이터(`emulator-5554`)에서 실기 검증: Home→대국 설정→InGame→메뉴 열기→닫기(뒤로가기)→기권 확인(뒤로가기 두 번째 누르면 기권 확인 다이얼로그가 뜸, 곧바로 Home으로 안 나가짐)→기권 확정→Home→다시 대국 시작→InGame 재진입 시 메뉴가 자동으로 열려있지 않음, 재오픈도 정상 동작. **뜻밖의 발견**: Compose `AlertDialog`는 뒤로가기를 항상 자기 자신을 먼저 닫는 데 쓴다(다이얼로그가 열린 채로 화면을 떠나는 경로 자체가 시스템 back으로는 도달 불가) — 즉 R13이 우려했던 "메뉴 열어둔 채 화면 이동" 시나리오는 이 앱의 실제 UI에서는 애초에 도달 불가능한 경우였다(탭으로 다이얼로그 바깥을 눌러도 동일하게 먼저 닫힘). 사용자가 승인한 동작 변화는 실질적으로 관찰 가능한 경우가 없어, 이번 변경은 겉보기보다 더 안전했다.

### 4. 4계층(외부 연동) 서비스 본체 하드닝 — **중간**, 우선순위는 낮음 (문서 정정만 완료, 260817)

**260817 진행 상황**: 사용자에게 범위를 먼저 확인 — 재시도/캐시/신뢰도 판단 설계·구현은 보류하고, "진행 방법"의 선행 작업이던 로드맵 문서 "핵심 갭" 문구 정정만 완료했다. 정정 내용: AdMob은 완전히 라이브, Play Billing은 코드 완성이나 Play Console 등록이 사용자 쪽에서 안 끝나 실사용 미확인, Google/이메일 로그인은 기기 검증까지 끝났지만 `isLoginEnabled = false`로 진입 경로 자체가 꺼져 있음 — 셋 다 "스텁"이 아니라는 게 핵심. 상세는 로드맵 문서 4계층 절 참고(이 백로그의 원칙 2번대로 여기서 다시 베끼지 않음). **재시도/캐시/신뢰도 판단 설계 자체는 여전히 미착수** — 우선순위가 가장 낮고 실제 문제 보고도 없어서 사용자가 명시적으로 보류를 선택했다.

**왜**: 실제 남은 일은 "포트만 있고 재시도/캐시/신뢰도 판단이 없다"는 부분 — `AndroidAuthClient`/`PremiumStateStore`가 SDK 호출을 그대로 감싸는 수준이라, 3계층의 `PositionAnalysisCacheResolver` 같은 안정화 계층이 없다. 로그인 쪽은 `isLoginEnabled`가 켜지기 전까지 이 하드닝을 실기로 검증할 방법이 없다는 제약도 있다(로드맵 문서 참고).

**진행 방법**: 광고/결제 호출 실패 시 재시도 정책, 네트워크 일시 장애와 실제 미가입을 구분하는 판단 등을 설계.

**완료 기준**: 딱히 사용자 불만이나 장애 리포트로 이어진 적 없는 항목이라(로드맵 문서 자체가 "아직 실제 문제로 확인된 적 없음"이라고 적어둠) — 4개 항목 중 가장 후순위. 착수하고 싶어지면 그때 다시 범위를 정하고 시작할 것.

---

## 우선순위 미부여 (참고용, 지금은 손대지 않음)

- **1계층 물리 실행 환경 추상화**(원격/DePIN 준비): 가리킬 실제 원격 서버가 없어 지금 착수해도 검증할 방법이 없다. 로드맵 문서 자체가 "별도 승인 필요"로 명시.
- **`docs/refactoring/`·`docs/archive/` 통폐합**: 순수 문서 정리, 기능/코드에 영향 없음. 로드맵 문서가 "필요해지면"으로 명시한 후순위 — 이 백로그의 "간결한 포커싱" 원칙과도 맞지 않아 뺐다.

---

## 다음 세션 시작 프롬프트 (항목 1 기준 예시)

```
docs/refactoring/REFACTORING_BACKLOG_260816_1744.md를 읽고 "서두 작업 원칙"을
먼저 이해한 뒤, "작업 우선순위" 1번(GameSessionStateHolder → :shared 본 이전)을
진행해줘. docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md의 "고도화 로드맵" 5번 항목에
스코핑이 이미 있으니 그것부터 확인하고, 큰 작업이니 실제 코드를 옮기기 전에
계획(어떤 순서로 파일을 옮길지, internal 가시성을 얼마나 넓혀야 할지)부터
정리해서 보여줘.
```

다른 항목부터 시작하려면 "1번" 대신 원하는 번호로 바꾸면 된다 — 각 항목이 서로 독립적이라 순서를 바꿔도 문제없다(단, 3번은 착수 전 사용자 결정이 먼저 필요하다는 점만 유의).
