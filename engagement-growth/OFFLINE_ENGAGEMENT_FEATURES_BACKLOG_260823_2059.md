# 오프라인 참여 기능 개발 일감 백로그

작성일: 2026-08-23

이 문서는 `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md`(설계/스펙)를 **새 스레드 단위로 순차 착수 가능한 일감**으로 쪼갠 진행 관리 문서입니다. 설계 근거나 상세 규칙은 다시 쓰지 않고 킥오프 플랜의 절 번호만 가리킵니다 — 각 스레드는 이 백로그에서 자기 번호를 찾고, 킥오프 플랜의 해당 절만 읽으면 됩니다.

**갱신 규칙**: 아래 "완료 사항 / 진행 중 / 예정사항" 3단 구조와 각 항목의 `(AI 모델: ..., 노력정도: ...)[상태]` 표기 형식을 그대로 유지하면서, 스레드가 하나 끝날 때마다 그 항목을 해당 섹션으로 옮기며 갱신합니다. 번호는 전체를 통틀어 하나의 연속된 시퀀스입니다(섹션마다 새로 시작하지 않음).

---

## 신규 스레드 착수 프로토콜

이 문서를 처음 보는 사람(또는 새 스레드)도 아래만 따라 하면 바로 작업을 시작할 수 있습니다.

**매번 새 스레드에 넣는 고정 프롬프트** (그대로 복사해서 사용):

> 이번 스레드에서는 `engagement-growth/OFFLINE_ENGAGEMENT_FEATURES_BACKLOG_260823_2059.md` 이 파일의 내용을 파악하여 '예정사항'의 첫번째 항목을 파악하여 수행 담당해야합니다. 먼저 항목을 이해하셨다면 진행중으로 변경하고 작업 착수하시고, 결과물을 사용자와 논의 후 사용자가 완료 승인을 하면 문서에 완료 업데이트하고 당신의 스레드 업무가 종료되었다고 명시해주시면 됩니다.

(파일명만 있어도 검색해서 찾을 수 있지만, 위처럼 `engagement-growth/` 경로를 포함해두면 탐색 한 단계를 줄일 수 있습니다.)

이 프롬프트를 받은 스레드가 따라야 할 순서:

1. **읽기** — 이 백로그 파일 전체 + `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md`의 **2장(현재 조건)·3장(아키텍처 제약)은 모든 항목의 공통 전제이므로 항상 같이 읽고**, 여기에 더해 이번에 맡을 항목이 가리키는 절을 읽는다. 이 셋을 읽지 않고 바로 코드부터 작성하지 않는다.
2. **상태 전환** — 예정사항의 **첫 번째** 항목을 진행 중 섹션으로 옮기고 `[진행중]`을 붙인다. 번호·제목·AI 모델·노력정도는 그대로 유지한다.
3. **작업** — 해당 절의 스펙대로 구현한다. 스펙에 없는 범위 확장이나 다음 항목 선착수는 하지 않는다.
4. **막히면** — 스펙에 없는 정보는 먼저 코드를 직접 확인해 스스로 판단한다. 그래도 답이 안 나오는 **제품/설계 결정 사항**(예: 보상 콘텐츠 구체안, 10장 열린 질문류)만 추측하지 말고 사용자에게 확인한다. 완료 처리는 반드시 사용자 승인 이후에만 한다.
5. **완료 처리** — 사용자가 승인하면 그 항목을 완료 사항 섹션으로 옮기고 `[진행중]` → `[완료]`로 바꾼 뒤, 항목 #1처럼 "산출물:" 한 줄을 추가해 실제로 바뀐 파일을 남긴다. 이후 **"이 스레드의 업무가 종료되었습니다"라고 명시**한다.
6. **다음 스레드 안내** — 완료 처리 후에는 다음 예정사항 항목 번호만 짧게 언급하고 스레드를 마친다. 다음 항목은 기본적으로 새 스레드의 몫이지만, **사용자가 "이어서 진행"을 지시하면 같은 스레드에서 다음 항목을 그대로 이어받는다**(2026-08-24에 #9·#12·#13·#14·#15를 한 스레드에서 연속 처리한 선례가 있다). 그때도 2~5단계를 항목마다 처음부터 다시 밟는다 — 특히 **항목별 "착수 전 확인"과 완료 승인은 건너뛰지 않는다.**

**참고사항**

- `AI 모델` 표기는 스레드를 어떤 모델로 실행할지 사용자가 참고하는 값이다 — 스레드가 스스로 모델을 바꾸는 동작이 아니다.
- 이 백로그 갱신과 `docs/DOCS_INDEX.md` 갱신은 별개다. 항목을 완료할 때마다 `DOCS_INDEX.md`를 고칠 필요는 없다 — 그 문서는 새 파일이 생기거나 문서 구조 자체가 바뀔 때만 갱신한다.
- 동시에 여러 스레드를 이 백로그에 붙이지 않는다 — 순차 실행을 전제로 설계됐다.
- **검증 명령**(항목마다 돌릴 것): 이 저장소는 **JDK 17**이 필요하다 — 시스템 기본 JDK로 돌리면 Gradle이 `25` 한 줄만 뱉고 즉시 실패해 원인을 찾기 어렵다.
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` 후 `./gradlew :shared:testDebugUnitTest :app-android:testDebugUnitTest`.
  `shared/`를 건드렸다면 `./gradlew :shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true`까지 확인한다(commonMain 플랫폼 독립성).
- **커밋 시점은 사용자가 정한다** — 스레드가 임의로 커밋하지 않고, 지시가 있을 때 커밋·푸시한다. 저장소 관행은 `main` 직접 커밋이며 메시지는 `feat(scope): ... (backlog #N)` 형식이다.

---

## 주요 포커스해야할 내용 서두 정리

- 스코프는 킥오프 플랜과 동일하게 **Phase 1(로그인 없이 로컬 전용)**만 — 로그인 연동은 이 백로그에 없음.
- 이번 라운드 피드백으로 확정된 것: **AI 봇 캐릭터는 기존 난이도 선택 UI 위에 얹는 장식이 아니라, 그 UI를 완전히 대체한다.** 캐릭터를 고르는 행위 자체가 곧 AI 레벨(`PlayLevelGroup.FastBeginner`의 초보~초고수 5단계) 선정이 된다 — 킥오프 플랜 7.1절 참고. 대국 셋업 진입점을 건드리는 핵심 변경이라 후반부 항목(#10)에 노력정도를 높게 잡았습니다.
- 의존관계 (2026-08-24 갱신, #15까지 완료): **(a) 출석/보상 축** — #2~#5·#12~#15 **전부 완료**(도메인·지급·Claim 팝업·소비 배선까지 닫혔다). / **(b) 대국 히스토리** — #6~#7 완료, 독립적. / **(c) 봇 캐릭터 축** — #8(도메인)·#9(콘텐츠) 완료, 실제 지급 배선은 (a)의 #13에서 끝났다. **남은 일감은 #10(진입점 개편) → #11(광고 획득) 둘뿐이고, #10의 선행 조건(#8·#9·#13)은 모두 해소됐다.**
- **출석 보상 1~5일차 콘텐츠가 확정됐습니다**(2026-08-24, 킥오프 플랜 4.2절 표). 여기서 두 가지가 새로 파생됐고 각각 일감이 되었습니다: ① 한 일차에 **보상이 여러 개** 나올 수 있다(1일차부터 무르기+캐릭터 2개) → #13, ② 2~4일차 보상이 **쓰면 줄어드는 소모품**이라 기존 영구 클레임 구조로는 표현이 안 된다 → #12·#15. 지급 방식도 자동 지급이 아니라 **Claim 버튼** 방식으로 바뀌었습니다(5.1절) → #14.
- 🔴 **보상 구조가 2026-08-24에 전면 재확정됐다 — 미확정 항목은 이제 없다.** 킥오프 플랜 4.2절(출석 보상표)과 7장(캐릭터 획득 경로)이 통째로 새 표로 교체됐고, 그전 초안대로 이미 구현된 **완료 항목 #8·#9·#12·#13·#15의 산출물을 되돌려야 한다**(→ 신규 일감 #16·#17). 요지:
  - 1일차 = 무르기만(캐릭터 제거), **1단계 첫돌이는 기본 제공**, 소모품 지급량 10 → **30/50**, **7일차 보상이 7일 간격 반복**.
  - 캐릭터 획득 경로가 티어 오름차순에서 갈라졌다 — **기본·출석 = 1 → 3 / 광고(조각 누적) = 2 → 4 / 유료 9,900원 = 5**. 3단계가 버거우면 광고로 2단계를 데려오라는 의도다.
  - **광고 획득이 "1회 시청 = 즉시"에서 "조각 5회/10회 누적"으로 바뀌어** #11의 범위가 커졌고, **캐릭터 개별 구매가 Phase 1 범위 안으로 들어와** 신규 일감이 됐다.
- ⚠️ **프리미엄 월 구독 전환은 이 백로그 밖이다**(2026-08-24 결정). 지금의 `premium_lifetime`(비소모성 영구)은 그 방향과 맞지 않지만, 상품 타입·만료/갱신 축·복원 로직이 전부 바뀌는 작업이라 `premium-mode` 트랙에서 별도로 다룬다. 내부 테스트 트랙에만 게시된 상태라 실구매자 마이그레이션 부담은 없다.
- 콘텐츠 확정(캐릭터 이름/설명, 보상 항목)처럼 "코드가 아니라 결정"이 필요한 항목은 일감으로 그대로 남겨두되, AI 스레드가 초안을 제안하고 사용자가 확정하는 흐름으로 표기했습니다.

---

## 일감 정리

순차적으로 다음 스레드에서 바로 인지할 수 있도록 정리 및 분류 가능해야하며, 아래 형식을 꼭 유지하면서 갱신 할것.

### 완료 사항

1. 참여 기능 기획 문서화 — 아이디어 브레인스토밍 + Phase 1 개발 착수 스펙 작성 (AI 모델: Sonnet, 노력정도: 높음) [완료]
   - 산출물: `engagement-growth/README.md`, `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md`

2. 앱 라이프사이클 훅 신설 — `Application` 서브클래스 + `ProcessLifecycleOwner` 옵저버 등록 (AI 모델: Sonnet, 노력정도: 중간) [완료]
   - 참고: 킥오프 플랜 4.1절. cold start/foreground 복귀 감지 인프라 — 현재 앱엔 전혀 없었음(그린필드). #3 이후 항목들의 선행 조건 해소.
   - 산출물: `AppForegroundEvents.kt`(신규, foreground 이벤트 `SharedFlow`), `GoAiCoachApplication.kt`(신규, `ProcessLifecycleOwner` 옵저버 등록), `AndroidManifest.xml`(`android:name` 등록), `gradle/libs.versions.toml`·`app-android/build.gradle.kts`(`androidx.lifecycle:lifecycle-process:2.8.7` 의존성 명시). `compileDebugKotlin`·`processDebugMainManifest`·기존 유닛테스트 전체 통과, 사용자 승인 완료(2026-08-23).

3. 출석 체크인 도메인·저장소 구현 — `AttendanceStorePort`/`AttendanceState`, UTC 날짜 기준 체크인 판정, 보상 티어 매핑 로직 (AI 모델: Sonnet, 노력정도: 높음) [완료]
   - 참고: 4.1~4.3절. **스코프**: "몇 일차인지 판정하고 그 티어 이벤트를 흘려보내는" 메커니즘까지만 — 1일차(무르기 무제한)만 구체 보상이 정해져 있고, 2일차 이후 보상 *내용*은 미정이니 자리만 만들고 채우려 하지 않는다(4.2절).
   - 산출물(shared): `AttendanceState.kt`, `AttendancePorts.kt`, `AttendanceCheckIn.kt`(`utcDayIndex`/`checkIn`/`isRewardedTier`), `AttendanceCheckInApplication.kt`(`runAttendanceCheckIn`), `AttendanceCheckInTest.kt`(8케이스). 산출물(app-android): `persistence/AttendanceStore.kt`+`AttendanceCodec`, `AttendanceCheckInCoordinator.kt`(#2의 `AppForegroundEvents` 구독 → 체크인 실행, `GoAiCoachApplication`에 배선), `AttendanceCodecTest.kt`(5케이스). 스펙의 `lastCheckInUtcDate: String`을 `lastCheckInUtcDay: Long`(UTC 하루 인덱스)로 구현 — 킥오프 플랜 4.3절에 각주로 반영됨. 테스트·컴파일 통과, 사용자 승인 완료(2026-08-23).

4. `claim()` UI-비의존 진입점 확인 및 1일차 "무르기 무제한" 자동 지급 배선 + 기존 클레임 다이얼로그 정리 (AI 모델: Opus, 노력정도: 높음) [완료]
   - 참고: 4.4절. `application/premium/*` 구조를 먼저 파악해 판단해야 하는 부분이 있어 Opus 권장.
   - 산출물: 확인 결과 클레임 진입점은 Compose 람다(`PremiumUiState.claim`)뿐이어서, UI에 의존하지 않는 `runPremiumFeatureClaim`(shared)을 신설하고 UI도 같은 함수를 쓰도록 통일. 출석 1일차 보상 지급은 `runAttendanceRewardGrant`(shared) + `AttendanceCheckInCoordinator` 배선으로 자동화(확인 팝업 없음, `withTierClaimed(1)`로 1회만). 기존 클레임 다이얼로그는 **방어적 폴백으로 유지**(판단 근거는 킥오프 플랜 4.4절 각주). 덤으로 구매 복원 시 `claimedFeatures`가 병합 없이 덮어써지던 기존 버그도 같이 해소됨. 변경 파일: (shared) `PremiumFeatureClaimApplication.kt`·`AttendanceRewardApplication.kt` 신규, `AttendanceCheckIn.kt` 수정, `PremiumFeatureClaimApplicationTest.kt`(5케이스)·`AttendanceRewardGrantTest.kt`(7케이스) 신규 / (app-android) `AttendanceCheckInCoordinator.kt`·`ui/PremiumUiState.kt`·`ui/GoCoachApp.kt`·`ui/GamePlaySection.kt` 수정. 컴파일·테스트 재검증(`--rerun-tasks`) 통과, 사용자 승인 완료(2026-08-23).

5. 업적/보상 화면 UI — 최초 실행 시 노출, 오늘 받은 보상 + 획득 목록 표시 (AI 모델: Sonnet, 노력정도: 중간) [완료]
   - 참고: 5장.
   - 산출물(app-android): `ui/FirstLaunchRewardScreen.kt`(신규 — `rememberFirstLaunchRewardGate` 상태/로직 + `FirstLaunchRewardScreen` 화면), `ui/GoCoachApp.kt`(호출 1줄 + early return), `ui/UiStrings.kt`/4개 언어 파일(`firstLaunchReward*` 3개 문자열 추가). 기존 `hasSeenOnboarding`은 로그인 전용 죽은 경로라 재사용하지 않고 `AttendanceState.attendanceCount == 0`을 최초 실행 판정으로 사용 — 구현 결정 3가지는 킥오프 플랜 5장 각주 참고. `LayeringContractTest`(라인 850/상태훅 46 예산) 통과 확인(841줄). 테스트·컴파일 통과, 사용자 승인 완료(2026-08-24).

6. 대국 히스토리 저장소 구현 — `GameHistoryStorePort`/`GameHistoryEntry` + 대국 종료 시 append 배선 (AI 모델: Sonnet, 노력정도: 중간) [완료]
   - 참고: 6장.
   - 산출물(shared): `GameHistoryEntry.kt`/`GameHistoryPorts.kt`/`GameHistoryAppendApplication.kt`(`runGameHistoryAppendIfCompleted`, 5케이스 테스트). 산출물(app-android): `persistence/GameHistoryStore.kt`+`GameHistoryCodec`(5케이스 테스트), `ui/GoCoachApp.kt`에 기존 `LaunchedEffect` 재사용으로 배선(새 효과 추가 안 함, 라인 예산 848/850). 구현 결정은 킥오프 플랜 6장 각주 참고 — 조사 중 발견한 `SidePlayerSetup.aiCharacterProfile()`은 #8에 참고로 남김. `LayeringContractTest` 포함 전체 테스트·컴파일 통과, 사용자 승인 완료(2026-08-24).

7. 대국 히스토리 목록 UI — 단순 리스트 표시만 (AI 모델: Sonnet, 노력정도: 낮음) [완료]
   - 참고: 6장.
   - 산출물(app-android): `ui/GameHistoryScreen.kt`(신규 — 목록 자체 로드, `StudyScreen`과 같은 헤더 패턴), `ui/GoCoachHomeScreen.kt`(대국 기록 카드 추가), `ui/GoCoachApp.kt`(`ScreenDestination.GameHistory` 추가), `ui/UiStrings.kt`/4개 언어 파일(문자열 3개 + `gameHistoryResultLabel()` 함수). `LayeringContractTest`의 `GoCoachApp.kt` 라인 예산을 850→853으로 올림(Study 화면 추가 때와 같은 이유, 각주 참고) — 상태훅 예산(46)은 그대로. 구현 결정은 킥오프 플랜 6장 각주(백로그 #7) 참고. 전체 테스트·컴파일 통과, 사용자 승인 완료(2026-08-24). 에뮬레이터로 실제 렌더링은 확인하지 못함(도구 제약) — 이후 수동 테스트 중 이슈가 나오면 별도 스레드로 처리.
   - **후속 수정(2026-08-24, 실사용 중 발견)**: 기권 시 대국 기록이 비어 보이던 버그 수정 + 결과 모델을 `winner: StoneColor?`에서 사람 기준 `GameHistoryResult`(Win/Loss/Draw/Resign)로 재설계, 사람 대 AI 대국만 기록하도록 스코프 축소, 행 표시를 `[날짜] [시간] [보드크기] [사람 진영] [접바둑] [결과]` 한 줄로 재구성. 상세는 킥오프 플랜 6장 맨 아래 "버그 수정 및 결과 모델 재설계" 각주 참고. `GameHistoryEntry` 스키마 변경으로 기존 저장 데이터와 비호환(Phase 1 개발 단계라 마이그레이션 없음). 테스트 17개(shared 12 + app-android 5) 전체 통과.

8. 봇 캐릭터 도메인 모델 + 컬렉션 저장소 — `BotCharacter`/`BotCharacterId`/`BotCollectionState`, `PlayLevelGroup` 티어와의 매핑 (AI 모델: Opus, 노력정도: 높음) [완료]
   - 참고: 7장. **#2~#7과 코드 의존관계 없음 — 실제로 #7 스레드와 병렬로 진행됐다.**
   - 산출물(shared): `application/botcharacter/` 신규 패키지 — `BotCharacter.kt`(`BotCharacterId`/`BotCharacter`/`BotUnlockSource` sealed), `BotCollectionState.kt`, `BotCharacterPorts.kt`(`BotCollectionStorePort`), `BotCharacterCatalog.kt`(`FastBeginner` 5티어 ↔ 캐릭터 1:1, `forPlayLevel()`/`toPlayLevelSetting()` 양방향), `BotCharacterCatalogTest.kt`(14케이스). 산출물(app-android): `persistence/BotCollectionStore.kt`+`BotCollectionCodec`(`AttendanceStore`와 동일한 전용 prefs + `schema:1` JSON 패턴), `BotCollectionCodecTest.kt`(6케이스).
   - `PremiumState` 통합 여부(10장 열린 질문)는 **분리**로 해소. #6이 남긴 `SidePlayerSetup.aiCharacterProfile()` 확인 건도 해소 — 수집 개념이 없는 "엔진+난이도" 표시용 라벨이라 재사용하지 않고 혼동 방지 KDoc만 달았다. 구현 결정 7가지는 킥오프 플랜 7장 각주 참고.
   - **사용자 확정(2026-08-24)**: `FastBeginner` 5종을 전부 잠금 — 기존에 조건 없이 고를 수 있던 5단계가 좁아지는 것은 의도된 방향. 1·2번째는 출석 1·5일차 지급, 3~5번째는 경로 미확정(임시 광고 획득). 배선은 하지 않았다(#13·#10·#11 몫).
   - 전체 707+ 테스트 통과(`LayeringContractTest` 43/43 포함), 사용자 승인 완료(2026-08-24).

9. 캐릭터 5종 콘텐츠 초안 — `FastBeginner` 그룹의 초보~초고수 5단계에 대응하는 이름·짧은 설명(아바타는 플레이스홀더) (AI 모델: Sonnet, 노력정도: 낮음) [완료]
   - 참고: 7장. 코드 작업이 아니라 콘텐츠 확정 항목 — 3개 세트를 제안해 **사용자가 "바둑 도장" 콘셉트로 확정**(2026-08-24).
   - **확정 콘텐츠**: 초보=첫돌이 / 하수=연습생 돌뫼 / 중수=도장생 반상 / 고수=사범 묘수 / 초고수=관장 천원. 이름 자체가 서열이라 획득 순서(약한 상대 → 강한 상대)가 그대로 드러난다. 아바타는 여전히 플레이스홀더(`avatarRef`가 전부 `null`).
   - **함께 확정된 결정 2가지**: ① 픽커는 캐릭터 이름 옆에 **기존 티어명(초보/하수/…)을 병기**한다 — 캐릭터 선택이 곧 난이도 선택이라 강함 서열이 즉시 보여야 하기 때문(#10에서 사용). ② 이름/설명은 **한국어 리터럴로 `shared`에 둔다** — 기존 `PlayLevelGroup.label`·`tierName`과 같은 선례이며, 레벨 라벨 전체의 다국어화는 별도 일감으로 미뤘다.
   - 산출물(shared): `BotCharacterCatalog.kt`(`placeholderName`/`placeholderDescription` 2개 함수 삭제 → 확정 문자열을 캐릭터별 인자로 전달, `BotCharacterId`·티어 매핑·`unlockSource`는 불변), `PlayLevel.kt`(**`PlayLevelSetting.tierLabel` 신설** — 티어명 단독 접근자가 없어 5단계 `초고수`의 폴백 리터럴을 #10이 복사해야 하는 문제를 없앴다. `displayLabel`이 이 값을 재사용하도록 정리했고 출력값은 전 그룹 동일 유지), `BotCharacterCatalogTest.kt`(+2케이스 — 확정 이름 고정으로 플레이스홀더 회귀 방지, 캐릭터↔티어명 병기 매핑), `PlayLevelSettingTest.kt`(+1케이스 — `tierLabel` 및 `displayLabel` 회귀).
   - 테스트 720개 전체 통과(`LayeringContractTest` 포함), 사용자 승인 완료(2026-08-24).

12. 단발성(소모성) 아이템 도메인·저장소 — `ConsumableItemId`/`ConsumableInventory`/`ConsumableStorePort` + 지급·차감 순수 로직 (AI 모델: Opus, 노력정도: 높음) [완료]
    - 참고: 4.5절. 쓰면 줄어드는 소모품이라 영구 boolean 원장(`PremiumState.claimedFeatures`)에 얹을 수 없어, 3장 원칙대로 별도 타입 + 별도 Port로 만들었다(#8의 `BotCollectionState`와 같은 구조).
    - **착수 전 확인 2건 해소(사용자 확정 2026-08-24)**: ① **'광고 스킵권' 1장 = 프리미엄 1시간**(`PremiumState.AdGrantDurationMillis` 재사용). 조사 중 **이 앱엔 강제로 뜨는 광고가 아예 없다**는 사실이 드러났다 — 배너(`ui/BannerAdView.kt`)는 정의만 있고 어느 화면에도 붙어 있지 않으며, 유일한 광고는 잠긴 기능을 풀려고 자발적으로 보는 리워드 광고다. 그래서 스킵 대상도 그 리워드 광고 하나뿐이고 "광고 1회분을 대신 내는 표"로 정의됐다. ② **종류별 재고 상한 99개**(출석 주기 반복 시 무한 적립 방지).
    - 산출물(shared): `application/consumable/` 신규 패키지 — `ConsumableItem.kt`(`ConsumableItemId` data class + `ConsumableEffect` sealed + `ConsumableCatalog` 3종), `ConsumableInventory.kt`(상한/오버플로 방어, 0이면 키 삭제 정규형), `ConsumablePorts.kt`(`ConsumableStorePort`), `ConsumableSpendApplication.kt`(`decideConsumableSpend` 순수 판정 + `runConsumableGrant`/`runConsumableSpend` 포트 배선), 테스트 21케이스. 산출물(app-android): `persistence/ConsumableInventoryStore.kt`+`ConsumableInventoryCodec`(`BotCollectionStore`와 동일한 전용 prefs + `schema:1` JSON 패턴), 테스트 5케이스.
    - 구현 결정 6가지는 킥오프 플랜 4.5절 각주 참고. 핵심: **`FeatureAccessPolicy`를 고쳐 소모품을 네 번째 허용 경로로 넣지 않았다** — `resolve`는 부수효과 없는 조회인데 소모품은 보는 순간 줄어드는 자원이라 합치면 "확인만 했는데 재고가 닳는" 구조가 된다. 대신 우선순위 규칙은 `decideConsumableSpend`에 담고 판정은 `resolve`에 위임했다(영구 클레임으로 열린 기능도 통과 대상에 포함). 파생 리팩터링으로 `FeatureAccessPolicy.activeVia()`를 떼어냈다(동작 변화 없음).
    - **배선은 하지 않았다** — `ConsumableInventoryStore`는 아직 어디에서도 생성되지 않는다(지급=#13, 소비=#15).
    - 테스트 746개 전체 통과(기존 720 + 신규 26), `:shared:compileKotlinIosSimulatorArm64` 통과로 commonMain 플랫폼 독립성 확인, 사용자 승인 완료(2026-08-24).

13. 출석 보상 정책 도메인 — 일차별 **다중 보상** 지급으로 확장 + 봇 캐릭터 지급 배선 (AI 모델: Opus, 노력정도: 높음) [완료]
    - 참고: 4.2절 보상 정책표. `AttendanceReward` sealed 3종(`PermanentFeature`/`Consumable`/`BotCharacterUnlock`) + "일차 → 보상 목록" 정책표를 만들고, #4의 `runAttendanceRewardGrant`(1일차 무르기 하나만)를 **다중 보상·다중 일차 지급**으로 재작성했다. 1일차=무르기+첫돌이, 2~4일차=소모품 각 10개, 5일차=연습생 돌뫼 배선 완성.
    - 산출물(shared): `AttendanceRewardPolicy.kt` 신규(`rewardsFor`/`pendingTiers`), `AttendanceRewardApplication.kt` 재작성(포트 4개 — 출석·프리미엄·소모품·봇), `BotCharacterGrantApplication.kt` 신규(`runBotCharacterUnlock` — #11의 광고 획득도 이 함수를 쓴다), `BotCharacterCatalog.forAttendanceTier()` 추가, `AttendanceRewardGrantTest.kt` 재작성(정책 7 + 지급 11 = 18케이스). 산출물(app-android): `AttendanceCheckInCoordinator.kt`·`ui/FirstLaunchRewardScreen.kt`에 소모품·봇 저장소 2개 추가 배선.
    - 구현 결정 7가지는 킥오프 플랜 4.2절 각주 참고. 핵심: ① **캐릭터 보상은 정책표에 적지 않고 카탈로그(`BotUnlockSource.Attendance(tier)`)에서 읽어 온다** — 같은 사실을 두 군데 두지 않기 위함이며, #11에서 3~5번째 획득 경로가 정해지면 카탈로그 한 줄만 고치면 된다. ② **콘텐츠 미확정 회차(6·7, 14/21/28일차)는 `claimedTiers`에 넣지 않는다** — 나중에 콘텐츠가 정해졌을 때 그 사이 지나간 사용자가 영영 못 받는 일이 없어야 한다. ③ 결과 타입을 sealed에서 "지급된 목록"을 든 data class로 교체(#14 팝업이 받은 내역을 그대로 보여줘야 함). ④ 밀린 일차는 한 번에 전부 지급(무기한 보관 — 만료 정책은 #14 확인 사항).
    - ⚠️ **#14에 부채 하나를 남겼다**: 1일차에 캐릭터도 지급되지만 `ui/FirstLaunchRewardScreen.kt`은 여전히 '무르기 무제한' 한 줄만 보여준다(지급 자체는 정상, 표시만 부정확). 그 화면을 Claim 방식으로 개편하는 것이 #14 범위라 곧 갈아엎을 화면에 4개 언어 문자열을 먼저 넣지 않았다 — 4.2절 각주 7번 참고.
    - 테스트 757개 전체 통과, `:shared:compileKotlinIosSimulatorArm64` 통과, 사용자 승인 완료(2026-08-24).

14. 출석 Claim 팝업 UI — 오늘 받을 보상 목록 표시 + `Claim` 버튼으로 지급 (AI 모델: Sonnet, 노력정도: 중간) [완료]
    - 참고: 5.1절. **자동 지급 → Claim 방식 전환**. 핵심 구조 변경은 **`AttendanceCheckInCoordinator`에서 보상 지급을 걷어낸 것** — 백그라운드에서 먼저 지급하면 팝업에 보여줄 게 남지 않는다. 이제 지급 경로는 Claim 버튼 하나뿐이고 코디네이터는 체크인만 한다(앱만 켜고 팝업을 닫아도 출석은 기록돼야 하므로 여전히 필요).
    - **착수 전 확인 3건 해소(사용자 확정 2026-08-24)**: ① 밀린 보상 **무기한 보관**(만료 없음 — #13의 `pendingTiers`가 이미 그렇게 동작해 추가 코드 없음), ② 밀린 일차는 **한 팝업에 모아 한 번의 Claim으로 전부 지급**, ③ 전체화면이 아니라 **다이얼로그**(최초 실행에만 뜨던 화면이 매일 뜨게 되므로).
    - 산출물(app-android): `ui/FirstLaunchRewardScreen.kt` → **`ui/AttendanceRewardClaimDialog.kt`로 개명·재작성**(새 화면을 만들지 않았다 — 노출 조건이 "최초 실행인가"에서 "받을 보상이 남아 있는가"로 바뀌어 파일명이 사실과 어긋났다), `ui/GoCoachApp.kt`(조건부 early return 7줄 → 다이얼로그 호출 1줄), `ui/UiStrings.kt`(`attendanceRewardLabel(reward)` 등 보상 문구 함수 4개 + 기존 `firstLaunchReward*` 3개를 `attendanceReward*`로 정리)/4개 언어 파일, `AttendanceCheckInCoordinator.kt`(지급 제거).
    - **#13이 남긴 부채 해소**: 1일차 캐릭터가 화면에 안 뜨던 문제가 정책표(`AttendanceRewardPolicy`)를 그대로 렌더링하면서 없어졌다.
    - 셸 예산이 오히려 **줄었다**: `GoCoachApp.kt` 853→849줄(상태훅 46 유지). 라인·상태훅 둘 다 한계에 붙어 있던 상태라 4줄 여유가 생겼다. 구현 결정 6가지는 킥오프 플랜 5.1절 각주 참고.
    - 테스트 757개 전체 통과(`LayeringContractTest` 44개 포함), 사용자 승인 완료(2026-08-24). 에뮬레이터로 실제 다이얼로그 렌더링은 확인하지 못함(#7과 같은 도구 제약) — 수동 테스트 중 이슈가 나오면 별도 스레드로 처리.

15. 단발성 아이템 소비 배선 — 형세 보기/추천 수/광고 스킵권 실제 차감 + 잔량 표시 (AI 모델: Opus, 노력정도: 높음) [완료]
    - 참고: 4.5절. #12에서 만든 재고를 실제 기능 사용 지점에 연결했다.
    - ⚠️ **착수 중 범위가 늘었다(사용자 신규 요구)**: 형세 보기/추천 수가 on/off 토글이라 "1회"의 단위를 물었더니, **토글은 프리미엄 편의 기능으로 두고 1회권은 단발성으로 동작**시키라는 결정이 나왔다. 별도 일감으로 떼지 않은 이유는 단발성 모드에 1회권 말고는 진입점이 없어서다(프리미엄 사용자는 토글을 쓴다) — 떼면 실행할 수 없는 일감이 되고 #15는 2·3일차 보상을 못 쓰는 채로 남는다. 판단 근거는 킥오프 플랜 4.5절 각주(#15) 1번.
    - **사용자 확정 3건**: ① 1회권 = **단발성**(그 순간의 결과를 한 번 보여주고 다음 수에 자동 해제), ② 사용 전 **확인 팝업**(재화를 말없이 쓰지 않는다), ③ 잔량은 **사용 확인/업셀 팝업 안에만** 표시(설정 화면 목록·버튼 배지는 미채택).
    - 산출물(app-android): `ui/ConsumableUiState.kt` 신규(재고 상태 + 단발성 추적 + `spend` + 사용 확인 팝업 + `OneShotAnalysisAutoClear`), `ui/GamePlaySection.kt`(`featureGated`에 1회권 경로 추가 — 잠김이어도 재고가 있으면 업셀 대신 사용 확인, 단발성으로 켜 둔 표시를 다시 탭하면 무료로 끔), `ui/PremiumUiState.kt`(업셀 팝업에 '광고 스킵권 사용' 네 번째 선택지 — 보유 0이면 미표시), `ui/GoCoachApp.kt`(배선 2줄 + CompositionLocal 추가), `ui/UiStrings.kt`/4개 언어 파일(문자열 4개 + 함수 2개), `ui/ConsumableUiStateTest.kt` 신규(3케이스).
    - **`FeatureAccessPolicy`는 고치지 않았다** — 4.5절 ⚠️의 "수정 필요"는 #12의 `decideConsumableSpend`가 이미 해소했다(판정은 `resolve`에 위임, 프리미엄·영구 클레임이 재고보다 앞섬). 소비 지점이 그 함수를 거치기만 하면 된다.
    - 셸 라인 예산 853 → **854**(순증 1줄 — #14가 853→849로 줄여 둔 덕). 상태훅 46 유지. 구현 결정 5가지는 킥오프 플랜 4.5절 각주(#15) 참고.
    - 테스트 760개 전체 통과, `:shared:compileKotlinIosSimulatorArm64` 통과. 에뮬레이터 실제 렌더링 미확인(도구 제약).

16. 캐릭터 획득 경로 재구성 — 첫돌이 기본 제공(Default) + 카탈로그 재배치 (AI 모델: Opus, 노력정도: 높음) [완료]
    - 참고: 킥오프 플랜 7장 새 획득 경로 표(2026-08-24 재확정본). **완료 항목(#8·#9)의 산출물을 되돌리는 항목이다** — 완료 기록은 히스토리로 두고 여기서 고친다.
    - 할 일: ① `BotUnlockSource.Default`를 되살려 첫돌이를 기본 제공으로(#8에서 제거했던 타입), ② 카탈로그 획득 경로 재배치(1=Default, 2=광고조각5, 3=Attendance(4), 4=광고조각10, 5=Purchase), ③ `BotCollectionState.isAvailable`이 `isClaimed`와 갈라지게(기본 제공은 획득 없이 사용 가능 — #8 KDoc이 예견한 분기 지점).
    - 조각 구조(#11)와 유료 구매(#18)는 **여기서 구현하지 않는다** — 카탈로그의 획득 경로 표기까지만 새 구조로 맞춰 두고, 실제 배선은 각 항목이 가져간다.
    - **2026-08-24 분할**: 원래 이 항목의 할 일 5개 중 정책표 교체·지급량 일반화 2개는 **#19로 분리**했다 — 이 항목(카탈로그 재구성)이 먼저 끝나야 #19가 다루는 "1일차 캐릭터 중복 지급 자동 소거"가 코드 추가 없이 성립하기 때문이다(#19 참고). 남은 3개(카탈로그 재구성)만 이 항목의 범위다.
   - 산출물(shared): `BotCharacter.kt`(`BotUnlockSource`에 `Default` 부활 + `AdWatch` → `AdShards(required)` + `Purchase` 신설), `BotCharacterCatalog.kt`(7장 표대로 재배치 — 1=Default / 2=AdShards(5) / 3=Attendance(4) / 4=AdShards(10) / 5=Purchase), `BotCollectionState.kt`(`isAvailable`이 `isClaimed`와 분기 — `Default`는 획득 없이 사용 가능). 테스트: `BotCharacterCatalogTest.kt` 갱신 + 신설 2케이스(획득 경로 표 고정, 무료/출석/광고/유료 개수 균형), `AttendanceRewardGrantTest.kt` 8케이스 갱신.
   - **정책 코드는 한 줄도 고치지 않았다** — #13이 캐릭터 보상을 카탈로그(`forAttendanceTier`)에서 읽게 해 둔 덕에, 출석 캐릭터가 1·5일차에서 4일차 하나로 저절로 옮겨졌다. 1일차 캐릭터 중복 지급이 코드 추가 없이 사라졌다(#19의 선행 조건 충족).
   - ⚠️ **파생: 5일차가 콘텐츠 없는 회차가 됐다** — 캐릭터가 4일차로 갔고 소모품은 #19 몫이라 비어 있다. `claimedTiers`에 들어가지 않으므로 그 사이 지나간 사용자도 나중에 받는다(#13의 안전장치). **#19가 반드시 메워야 한다.**
   - **#10에게 미치는 영향**: 빈 상태 처리 부담은 사라졌고(항상 첫돌이가 있음), 대신 **잠긴 4종의 잠금 사유를 셋으로 구분**해 보여줘야 한다(출석 4일차 / 광고 조각 5·10회 / 유료).
   - 구현 결정은 킥오프 플랜 7장 각주(백로그 #16) 참고. `:shared`/`:app-android` 테스트 전체 통과, `:shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true` 통과, 에뮬레이터로 신규 설치 1일차 보상에 캐릭터가 빠진 것과 `bot_collection` 저장 파일이 생기지 않는 것까지 실기 확인. 사용자 승인 완료(2026-08-29).

19. 출석 보상 정책표·지급량 갱신 — 2·3·5·6·7일차 소모품 신설 + 7일 간격 반복 (AI 모델: Opus, 노력정도: 높음) [완료]
    - 참고: 킥오프 플랜 4.2절 새 표(2026-08-24 재확정본). **완료 항목(#13)의 산출물을 되돌리는 항목이다.**
    - **선행 조건: #16(캐릭터 획득 경로 재구성) 완료 후 착수.** `AttendanceRewardPolicy.rewardsFor`는 이미 캐릭터 보상을 카탈로그(`BotCharacterCatalog.forAttendanceTier`)에서 읽어 오므로(#13 구현 결정), #16이 먼저 끝나 1단계가 `Default`로 바뀌어야 1일차의 중복 캐릭터 지급이 이 항목의 코드 추가 없이 저절로 사라진다.
    - 할 일: ① `AttendanceRewardPolicy.rewardsFor`를 새 표로 교체(1일차 무르기만, 2·5일차 형세30+추천30, 3일차 스킵권3, 6일차 스킵권5, 7일차 형세50+추천50+스킵권10, **7의 배수 회차는 7일차 보상 반복**), ② `ConsumableRewardAmount` 상수를 일차별 지급량으로 일반화.
    - **재고 상한 99는 그대로 둔다** — 한 주기 지급량(형세 보기 110개)과 부딪혀 넘치는 만큼 버려지는 것이 **의도**다(소모 유도). 다만 `ConsumableInventory.MaxPerItem` 한 줄만 고치면 999로 올릴 수 있게 유지할 것.
    - **2026-08-24 분할**: 원래 #16에서 분리된 항목이다 — 분할 근거와 배경은 #16 참고.
   - 산출물(shared): `AttendanceRewardPolicy.kt` — `rewardsFor`를 4.2절 재확정본으로 교체(1일차 무르기 / 2·5일차 형세30+추천30 / 3일차 스킵권3 / 4일차 캐릭터 / 6일차 스킵권5 / 7일차 형세50+추천50+스킵권10), `ConsumableRewardAmount`(단일 10)를 일차별 상수 5개로 일반화, `WeeklyRewardCycleTier` 신설. 테스트: `AttendanceRewardGrantTest.kt` 7건 갱신 + 4건 신설(표 전수 고정, 7의 배수 반복 동일성, 비보상 회차, 상한 절삭).
   - **7일 간격 반복은 표를 두 벌로 늘리지 않고 조회 시점에 접었다** — `contentTier = if (tier > 7) 7 else tier`. 회차 번호는 그대로 둬야 `claimedTiers`가 같은 주기를 두 번 지급하지 않는다. 캐릭터 조회도 같은 값을 쓰므로 반복 회차가 캐릭터를 재지급하지 않는다.
   - **#16이 남긴 5일차 공백이 메워져 1~7일차에 빈 회차가 없다.** "빈 회차는 `claimedTiers`에 안 넣는다"는 #13의 안전장치는 지우지 않고 뒀고, 7의 배수가 아닌 8~13일차로 여전히 작동함을 검증했다.
   - **재고 상한 99는 그대로 뒀다**(지시대로). 한 주기 형세 지급량 110과 부딪혀 11개가 버려지는 것이 의도이며, 그 사실을 코드 주석과 테스트 양쪽에 고정했다.
   - 구현 결정은 킥오프 플랜 4.2절 각주(백로그 #19) 참고. 전체 테스트 통과, `:shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true` 통과, 에뮬레이터로 2일차(형세30+추천30)·7일차(형세50+추천50+스킵권10) 팝업과 지급 후 저장 상태(eval 80, top_moves 80, premium_once 10, claimedTiers=[1..7])까지 실기 확인. 사용자 승인 완료(2026-08-29).

17. 소모품 인벤토리 상시 표시 — '형세보기: 30' / '추천 수: 30' / '광고 스킵권: 5' (AI 모델: Sonnet, 노력정도: 중간) [완료]
    - 참고: 4.5절. **#15에서 확정했던 "잔량은 사용 확인/업셀 팝업 안에만"을 대체하는 항목이다**(2026-08-24 사용자 재확정) — 팝업 안 표시는 그대로 두고, 상시 보이는 인벤토리를 **추가**한다.
    - 위치는 상단 등 눈에 띄는 곳. **가장 중요한 요구사항은 "사용처 팝업이 뜰 때 잔여 개수가 확연히 보이면서 차감되는 것이 보이는 것"** — 숫자만 조용히 바뀌지 않게 할 것.
    - 형세 보기와 추천 수는 **개별 사용권으로 운영**한다(이미 `ConsumableCatalog`가 그렇게 나뉘어 있다). **#19 완료 후 착수**(지급량이 바뀌므로) — 2026-08-24에 지급량 갱신이 #16에서 #19로 분리되면서 이 선행 조건도 #16에서 #19로 옮겨졌다.
   - 산출물(app-android): `ui/ConsumableUiState.kt`(`ConsumableInventoryBar` + `ConsumableCountCell` 신규 — 재고 0인 종류는 빼고, 셋 다 0이면 줄 자체를 안 그린다. 차감 시 0.9초 강조), `ui/GamePlaySection.kt`(호출 1줄), `ui/UiStrings.kt`(`consumableShortName` 공개 — "…1회권" 접미사를 뺀 짧은 이름).
   - ⚠️ **위치를 스펙과 다르게 잡았다**: 4.5절은 "상단 등"이라 했으나 **액션 버튼 바로 위**에 뒀다. 같은 항목이 "가장 중요한 요구사항"으로 지목한 것이 위치가 아니라 **차감이 보이는 것**이어서, 방금 누른 버튼 바로 위가 그 요구를 가장 직접 충족한다고 판단했다. 상단으로 옮기려면 `GamePlaySection`의 호출 한 줄만 옮기면 된다(사용자 승인 완료).
   - **전제 변경 반영**: 이 항목이 전제한 "사용 확인 팝업"은 2026-08-29 1회권 재설계로 사라지고 토스트가 대체했다. 역할을 갈랐다 — **차감 알림은 토스트**, **평소 재고 인지 + 차감 순간 시각화는 이 바**.
   - 전체 테스트 통과. `shared/`를 건드리지 않아 iOS 검증은 생략. 에뮬레이터로 바 노출·`80 → 79` 차감·강조색을 픽셀 대조로 확인(`RGB(49,145,123)` 초록 → 3초 후 `RGB(91,87,94)` 회색). 사용자 승인 완료(2026-08-29).
   - 남은 관찰: 세 종류가 모두 세 자릿수가 되면 한 줄이 빡빡해질 수 있다(현재 폰에서는 문제없음).

10. AI 레벨 선택 진입점을 캐릭터 선택 UI로 전면 개편 — 기존 `PlayerSetupPanel.kt`의 `FastBeginner` 난이도 선택 UI를 캐릭터 픽커로 대체 (AI 모델: Opus, 노력정도: 최대) [완료]
    - 참고: 7.1절. 기존 대국 셋업 UX를 직접 건드리는 핵심 변경이라 노력정도 최대. **#8·#9에 더해 #13(봇 지급 배선) 완료 후 착수** — 지급이 안 붙은 상태로 픽커를 만들면 고를 수 있는 캐릭터가 하나도 없다.
    - ~~⚠️ 빈 상태 처리 필수~~ → **완화됨(2026-08-24 재확정)**: 1단계 첫돌이가 기본 제공으로 바뀌어 "고를 수 있는 캐릭터가 하나도 없는" 상태는 사라졌다. 대신 **잠긴 4종을 픽커에서 어떻게 보여줄지**가 남는다 — 획득 경로가 셋으로 갈리므로(출석 4일차 / 광고 조각 N회 / 유료 9,900원) 각 잠금 사유와 진행도를 구분해 안내해야 한다. `BotCollectionState.isAvailable`의 경고 문구도 이에 맞춰 고칠 것.
   - 산출물(app-android): `ui/BotCharacterUiState.kt` 신규(`BotCharacterUiState`+`LocalBotCharacterUiState`+`buildBotCharacterUiState` — #8이 남겨둔 `BotCollectionStore` 배선을 채움, `BotCharacterPickerDialog`+`BotCharacterRow`), `ui/PlayerSetupPanel.kt`(단계 드롭다운 → 캐릭터 버튼+픽커), `ui/GoCoachApp.kt`(배선 2줄 + CompositionLocal 추가), `ui/UiStrings.kt`/4개 언어(`botPickerTitle`·`botPickerCloseAction`·`botCharacterLabel`·`botUnlockHint`).
   - 셸 예산 845→851, 상태훅 45→46(`LayeringContractTest` 이력에 사유 기록). 상태와 픽커 본체는 전부 `ui/BotCharacterUiState.kt`에 있어 셸에는 배선만 남는다.
   - **잠긴 캐릭터를 숨기지 않는다** — 숨기면 존재도 획득 방법도 모른다. 획득 경로 셋을 각각 안내한다(`광고 N회를 보면 열려요` / `출석 N일차에 받을 수 있어요` / `구매로 열려요`). 가격과 조각 진행도는 적지 않았다 — 상품 미등록(#18)이고 진행도 상태가 아직 없다(#11).
   - ⚠️ **잠긴 캐릭터를 탭하면 아무 일도 일어나지 않는다.** 광고 시청(#11)·구매(#18) 진입점이 없어서이며, 탭 시 업셀로 유도할지는 그 항목들이 붙을 때 정한다.
   - 전체 테스트 통과. `shared/` 미변경으로 iOS 검증 생략. 에뮬레이터로 버튼 라벨(`첫돌이 (초보)`)과 픽커 5종·잠금 사유 3종 표시 확인. 사용자 승인 완료(2026-08-29).

11. 광고 시청 → 봇 캐릭터 **조각 누적** 획득 배선 — 기존 `AdRewardPort` 재사용, 시간제 활성화 아님 (AI 모델: Opus, 노력정도: 높음) [완료]
    - 참고: 7장 획득 경로 표(2026-08-24 재확정). **범위가 커졌다**: 원래는 "1회 시청 = 즉시 획득"이라 노력정도 중간이었는데, **2단계는 광고 5회·4단계는 광고 10회를 채워야 활성화**되는 조각 구조로 바뀌었다 — 진행도를 저장할 새 상태(`BotCollectionState`에 조각 카운터)가 필요하다. 자료구조는 #12의 `ConsumableInventory`와 같은 꼴이라 패턴은 그대로 재사용할 수 있다.
    - 조각 진행도를 사용자에게 보여줘야 한다(예: "연습생 돌뫼 3/5") — 픽커(#10)의 잠금 표시와 맞물리므로 **#10 완료 후 착수.**
    - ⚠️ 시간제 활성화 패턴(`adGrantStartedAtMillis`, `PremiumState.AdGrantDurationMillis`)과 섞지 말 것 — 그건 프리미엄 1시간용이고, 봇은 조각을 다 모으면 영구 소유다.
   - 산출물(shared): `BotCollectionState.kt`(`adShards` 진행도 + `shardsFor`/`withAdShard`), `BotCharacterShardApplication.kt` 신규(`runBotCharacterShardGrant` — read-modify-write, `runBotCharacterUnlock`과 같은 패턴). 산출물(app-android): `persistence/BotCollectionStore.kt`(조각 저장), `ui/BotCharacterUiState.kt`(`watchAdForShard` 적립 경로 + 픽커에 광고 시청·진행도), `ui/PremiumPurchaseGlue.kt`(`showRewardedAdOnce` 추출), `ui/UiStrings.kt`/4개 언어(진행도·획득 토스트). 테스트 5건 신설(도메인 3 + 코덱 2).
   - **저장 스키마 번호를 올리지 않았다** — 이 코덱은 번호가 다르면 `null`을 주고 기본 상태로 폴백하므로, 올렸다면 **이미 수집한 캐릭터가 통째로 날아간다.** 새 필드는 없으면 빈 값으로 읽으면 그만이라 하위호환으로 충분하며, 구버전 저장분이 그대로 읽히는 것을 테스트로 고정했다.
   - **광고 노출을 상태 변경과 분리했다**(`showRewardedAdOnce`) — 프리미엄 1시간 활성화와 조각 적립이 같은 광고를 쓰지만 결과의 수명이 정반대다(1시간 뒤 꺼짐 vs 영구 소유).
   - **#10이 남긴 "잠긴 캐릭터를 탭해도 아무 일 없음"이 해소됐다** — 조각 경로 캐릭터는 탭이 곧 광고 시청이다. 유료(#18)는 여전히 진입점이 없다.
   - 전체 테스트 통과, `:shared:compileKotlinIosSimulatorArm64 -PenableIosTargets=true` 통과. 실제 Google 테스트 광고로 실기 검증 — 조각 3→4, 4→5→**획득 전환**(`claimedBots` 추가 + `adShards` 소거), 진행도 표시(3/5·0/10), 구버전 저장분 로드까지 확인. 사용자 승인 완료(2026-08-29).
   - ⚠️ **미해결 문제를 #20으로 분리 발행했다** — 광고를 보고 돌아오면 픽커가 닫힌다.

### 진행 중

20. 조각 광고 시청 후 캐릭터 픽커가 닫히는 문제 (AI 모델: Sonnet, 노력정도: 중간) [진행중]
    - 참고: 킥오프 플랜 7.1절. **#11에서 발견했으나 원인을 못 찾아 분리한 항목이다** — 기능 결함은 아니고 번거로움이지만, 사범 묘수는 조각 10개라 픽커를 열 번 다시 열어야 해서 무시하기 어렵다.
    - **증상**: 픽커에서 조각 경로 캐릭터를 탭 → 광고 시청 → 앱 복귀 시 **픽커가 닫혀 있다.** 조각은 정상 적립된다(기능은 동작). 대국 설정 화면으로 돌아와 버려 연속 시청이 끊긴다.
    - **계측으로 확인된 사실(2026-08-29)**: `DisposableEffect`+로그로 (a)를 갈랐다 — **`PlayerSetupPanel`은 살아남는다**(`panel-dispose`가 한 번도 찍히지 않음). **다이얼로그만 dispose된다.** 광고 종료 직후 `onDismissRequest`가 **두 번** 날아오고, 그 시점에는 이미 `adInProgress=false`다(코루틴이 먼저 재개된다).
    - **이미 시도했고 전부 실패한 것**(같은 것을 반복하지 말 것):
      ① `onDismissRequest`를 `adInProgress` 중에는 무시 → 요청이 플래그 해제 **뒤에** 와서 통과됨
      ② `showPicker`를 `rememberSaveable`로 전환 → 효과 없음(다시 `remember`로 되돌림)
      ③ 광고 종료 후 700ms 동안 dismiss를 무시하는 별도 유예 창(`ignoreDismiss`) → 효과 없음
      ④ 광고 코루틴을 다이얼로그에서 **패널로 올리고** 끝난 뒤 `showPicker = true`로 복구 → **대입이 반영되지 않는다**(왜인지 미상). 이 구조 변경 자체는 스코프 취소를 피하는 개선이라 코드에 남겼고, 복구 줄도 자리가 맞으므로 남겨 뒀다.
    - **가장 유망한 다음 수(제안)**: `AlertDialog`가 Activity 전환에 취약한 것이 근본 원인으로 보이므로, 픽커를 **다이얼로그가 아닌 것**(`ModalBottomSheet` 또는 전용 `ScreenDestination`)으로 바꾸면 문제가 통째로 사라질 가능성이 높다. 다만 이는 #10이 정한 UI 형태를 바꾸는 것이라 착수 전 사용자 확인이 필요하다.
    - 관련 파일: `ui/PlayerSetupPanel.kt`(`showPicker`), `ui/BotCharacterUiState.kt`(`BotCharacterPickerDialog`), `ui/GameSetupLobby.kt`.


21. 조각 획득의 광고 단일 의존성 해소 + 조각 실패 안내 분리 (AI 모델: Opus, 노력정도: 중간) [진행중]
    - 참고: 킥오프 플랜 7장. **#20 진행 중 사용자 지적으로 발행된 항목이다** — "광고 시청으로 조각 모으는 것은 구글측 의존성이 들어가므로 항상 성립하지 않을 수 있다. 그 점이 간과된 것인지 체크하라. 그리고 광고 완료 후 리턴값을 받는 것으로 아는데 이 부분도 더블체크."
    - **확인된 사실**: 지적 두 가지 모두 실제 결함이었다. ⓐ 2·4단계는 획득 경로가 `AdShards` 하나뿐이라 광고가 채워지지 않으면 영구히 잠긴다. ⓑ `ad.show(activity) { rewardEarned = true }`가 `RewardItem`을 통째로 버리고 있었다. ⓒ 덤으로, 조각 광고 실패 시 프리미엄용 문구("프리미엄이 활성화되지 않았습니다")가 그대로 나가고 있었다.
    - **범위(사용자 확정)**: ⓐ **출석 장기 보상으로 조각 획득 경로 추가** / ⓑ 리턴값 포착 / ⓒ 실패 문구 분리. **유료 구매로 조각을 파는 안은 보류**(가능성은 열어 둠) — 열게 되면 #18에 붙는다.
    - **작업 중 드러난 기존 결함 2건도 함께 고쳤다**: ⓐ Claim 팝업이 지급 **전** 목록을 정책표에서 직접 읽어, 이미 다 모은 캐릭터의 조각까지 매주 보여줬다(`pendingTiers(state, collection)` 신설). ⓑ 픽커가 저장소를 한 번만 읽어, 출석으로 받은 조각이 앱을 다시 켤 때까지 반영되지 않았다(픽커 열 때 재조회).
    - **실기 검증(에뮬레이터, 2026-08-29)**: 14일차 지급 → `fast_beginner_4` 조각 3→4, 이미 가진 `fast_beginner_2`의 조각 줄은 팝업에서 빠짐. 21일차 지급 → 4→5, 픽커가 같은 실행에서 곧바로 5/10 표시. 네트워크를 끊고 조각 탭 → "광고를 불러오지 못했어요, 잠시 후 다시 시도해 주세요."
    - 산출물(승인 대기): `application/attendance/AttendanceRewardPolicy.kt`(`BotCharacterShards` 보상 + `WeeklyShardAmount`), `AttendanceRewardApplication.kt`(지급 경로 + "알릴 것" 필터), `application/botcharacter/BotCharacterCatalog.kt`(`shardPathCharacters`), `BotCollectionState.kt`(`withAdShards`), `BotCharacterShardApplication.kt`(`amount`), `application/premium/AdRewardPort.kt`(`RewardEarned(type, amount)`), `PremiumAdGrantApplication.kt`(진단 로그), `ui/AndroidRewardedInterstitialAdClient.kt`, `ui/UiStrings.kt`, `ui/BotCharacterUiState.kt`, `ui/AttendanceRewardClaimDialog.kt`, 테스트 3건.

---

### 예정사항

> ⚠️ **번호는 식별자일 뿐, 실행 순서는 이 목록에 적힌 순서다.** 2026-08-24 피드백으로 들어온
> #12~#15가 기존 #10·#11보다 먼저 처리돼야 했기 때문이다(캐릭터 픽커 #10은 봇이 실제로
> 지급돼야 의미가 있는데, 그 지급 배선이 #13이었다). 기존 번호는 완료 항목과 킥오프 플랜
> 각주에서 참조하고 있어 다시 매기지 않았다 — **새 스레드는 번호가 아니라 이 목록의 맨 위
> 항목을 가져간다.** 2026-08-24 보상 구조 재확정으로 #16~#18이 새로 들어왔고, 같은 날 #16을
> 두 조각(#16 카탈로그 재구성 / #19 정책표·지급량 갱신)으로 나눠 **남은 순서는
> 16 → 19 → 17 → 10 → 11 → 18**이다 — #19가 #17보다 앞선 이유는 #17 자신이 이미
> "지급량이 바뀌므로 그 갱신 이후 착수"라고 못박아 뒀기 때문이다(아래 #17 참고).

18. 봇 캐릭터 개별 구매 배선 — 5단계 관장 천원, 9,900원 단발성 결제·영구 소유 (AI 모델: Opus, 노력정도: 높음)
    - 참고: 7장 획득 경로 표. **Phase 1 범위 확장이다** — 7장이 원래 "범위 밖(설계만 고려)"으로 못박아 뒀던 항목인데 2026-08-24에 범위 안으로 들어왔다.
    - `BotUnlockSource.Purchase`를 신설한다(#8에서 "지금 만들지 않았다"고 미뤄 둔 타입 — sealed라 추가하면 기존 `when`이 컴파일 에러로 빠진 처리를 잡아준다). **비소모성 단발 결제라 이미 검증된 `premium_lifetime`의 `AndroidBillingClient`(INAPP) 패턴을 그대로 재사용할 수 있다.**
    - 착수 전 확인(사용자): **Play Console 신규 상품 등록은 사용자 몫**이다(상품 ID·가격 9,900원 설정, 라이선스 테스터). 등록 전에는 실기 검증이 불가능하므로 착수 시점에 등록 여부를 먼저 확인할 것.
    - ⚠️ **프리미엄 월 구독 전환과 혼동하지 말 것** — 그건 이 백로그 밖(`premium-mode` 트랙)이고, 이 항목은 캐릭터 1종을 파는 별개 상품이다.
    - **구매 특전도 이 항목의 몫이다(2026-08-29 추가)**: 구매자는 **그 캐릭터와 두는 동안** 형세 보기·추천 수를 무제한 쓴다. ⚠️ 지금의 `FeatureAccessPolicy`는 전역 판정이라 이걸 표현할 수 없다 — 착수 전 정해야 할 세 가지가 `feature-access-principles/README.md` 8.3절에 있다.

---

## 관련 문서

- `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` — 이 백로그가 참조하는 설계/스펙 원본
- `README.md` — 아이디어 브레인스토밍, 상태 트래킹
