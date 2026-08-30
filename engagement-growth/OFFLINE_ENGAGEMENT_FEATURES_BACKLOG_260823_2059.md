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

20. 조각 광고 시청 후 캐릭터 픽커가 닫히는 문제 (AI 모델: Sonnet, 노력정도: 중간) [완료]
    - 참고: 킥오프 플랜 7.1절. #11에서 발견해 분리한 항목. **증상**: 픽커에서 조각 캐릭터를 탭 → 광고 시청 → 앱 복귀 시 픽커가 닫혀 있어 조각 10개짜리는 픽커를 열 번 다시 열어야 했다.
    - **실질 수정은 광고 코루틴을 다이얼로그에서 패널로 올린 것**(④의 구조 변경, 커밋 `bc2bd7f`)이다. 다이얼로그 안에서 돌리면 픽커가 닫히는 순간 스코프까지 취소돼 복구조차 시도할 수 없다.
    - **①(`adInProgress` 가드)·③(700ms 유예)은 원리적으로 틀린 접근이었다** — 광고 코루틴이 dismiss보다 **먼저** 재개돼 플래그가 이미 내려간 뒤에 요청이 도착한다. 그래서 시간으로 쫓아가는 대신 경로를 없앴다: `DialogProperties(dismissOnClickOutside = false)`. 닫기 버튼·뒤로 가기는 그대로 남는다.
    - ⚠️ **원 증상은 끝내 재현되지 않았다.** 현재 main에서 광고 5회 이상 연속, ①번 가드를 되살린 대조군 포함해 `onDismissRequest`가 **한 번도** 호출되지 않았다. 위 수정은 "확인된 원인을 고친 것"이 아니라 **그 실패 유형을 불가능하게 만든 것**이다.
    - **④의 기록("대입이 반영되지 않는다")은 사실이 아니었다** — 계측하면 `showPicker = true`는 실행되고 유지된다. 당시엔 ①번 가드가 함께 살아 있어 늦게 온 dismiss가 복구를 덮어쓴 것으로 보인다.
    - **UI 형태는 바꾸지 않았다** — 승인이 필요하다고 적어 뒀던 `ModalBottomSheet`/전용 화면 전환은 불필요했다.
    - 실기 검증(에뮬레이터): 광고를 연속으로 돌려 조각 3 → 10까지 채우고 **사범 묘수가 실제로 해금**될 때까지 픽커를 한 번도 다시 열지 않았다. 바깥 탭 2회(위/아래) 후에도 열려 있고, 뒤로 가기와 닫기 버튼으로는 정상적으로 닫힌다.
    - **실기 검증(실제 단말 Galaxy S22 Ultra / SM-S908N, Android 16)**: 에뮬레이터에서 원 증상이 재현되지 않았으므로 실기에서 다시 확인했다 — 광고 2회 연속 시청에 조각 0 → 1 → 2, 그 사이 픽커가 계속 열려 있었다. 바깥 탭(위/아래) 후에도 열려 있고, 뒤로 가기·닫기 버튼은 정상적으로 닫는다. **실기에서도 원 증상은 나타나지 않았다.**
    - 산출물: `ui/BotCharacterUiState.kt`(`DialogProperties(dismissOnClickOutside = false)`), `ui/PlayerSetupPanel.kt`(광고 코루틴 호이스팅 — 커밋 `bc2bd7f`, 주석 정정). 계측 코드는 전부 제거했다.
    - 사용자 승인 완료(2026-08-29).

21. 조각 획득의 광고 단일 의존성 해소 + 조각 실패 안내 분리 (AI 모델: Opus, 노력정도: 중간) [완료]
    - 참고: 킥오프 플랜 7장. **#20 진행 중 사용자 지적으로 발행된 항목이다** — "광고 시청으로 조각 모으는 것은 구글측 의존성이 들어가므로 항상 성립하지 않을 수 있다. 그 점이 간과된 것인지 체크하라. 그리고 광고 완료 후 리턴값을 받는 것으로 아는데 이 부분도 더블체크."
    - **확인된 사실**: 지적 두 가지 모두 실제 결함이었다. ⓐ 2·4단계는 획득 경로가 `AdShards` 하나뿐이라 광고가 채워지지 않으면 영구히 잠긴다. ⓑ `ad.show(activity) { rewardEarned = true }`가 `RewardItem`을 통째로 버리고 있었다. ⓒ 덤으로, 조각 광고 실패 시 프리미엄용 문구("프리미엄이 활성화되지 않았습니다")가 그대로 나가고 있었다.
    - **범위(사용자 확정)**: ⓐ **출석 장기 보상으로 조각 획득 경로 추가** / ⓑ 리턴값 포착 / ⓒ 실패 문구 분리. **유료 구매로 조각을 파는 안은 보류**(가능성은 열어 둠) — 열게 되면 #18에 붙는다.
    - **작업 중 드러난 기존 결함 2건도 함께 고쳤다**: ⓐ Claim 팝업이 지급 **전** 목록을 정책표에서 직접 읽어, 이미 다 모은 캐릭터의 조각까지 매주 보여줬다(`pendingTiers(state, collection)` 신설). ⓑ 픽커가 저장소를 한 번만 읽어, 출석으로 받은 조각이 앱을 다시 켤 때까지 반영되지 않았다(픽커 열 때 재조회).
    - **실기 검증(에뮬레이터, 2026-08-29)**: 14일차 지급 → `fast_beginner_4` 조각 3→4, 이미 가진 `fast_beginner_2`의 조각 줄은 팝업에서 빠짐. 21일차 지급 → 4→5, 픽커가 같은 실행에서 곧바로 5/10 표시. 네트워크를 끊고 조각 탭 → "광고를 불러오지 못했어요, 잠시 후 다시 시도해 주세요."
    - 산출물: `application/attendance/AttendanceRewardPolicy.kt`(`BotCharacterShards` 보상 + `WeeklyShardAmount`), `AttendanceRewardApplication.kt`(지급 경로 + "알릴 것" 필터), `application/botcharacter/BotCharacterCatalog.kt`(`shardPathCharacters`), `BotCollectionState.kt`(`withAdShards`), `BotCharacterShardApplication.kt`(`amount`), `application/premium/AdRewardPort.kt`(`RewardEarned(type, amount)`), `PremiumAdGrantApplication.kt`(진단 로그), `ui/AndroidRewardedInterstitialAdClient.kt`, `ui/UiStrings.kt`, `ui/BotCharacterUiState.kt`, `ui/AttendanceRewardClaimDialog.kt`, 테스트 3건.
    - 사용자 승인 완료(2026-08-29).

22. 저장된 AI 레벨이 획득 게이트를 우회하는 문제 (AI 모델: Opus, 노력정도: 중간) [완료]
    - 참고: 킥오프 플랜 7장(획득 경로), 백로그 #16·#10. **#20 실기 검증 중 발견해 발행한 항목이다.**
    - **증상**: 앱을 새로 설치해 `go_ai_coach_bot_collection.xml`이 아예 없는(= 아무것도 획득하지 않은) 상태인데 대국 설정의 상대가 **사범 묘수(4단계)** 로 잡혀 있었고, 픽커에서도 그 줄이 선택 상태로 강조됐다. 조각 0/10인 잠긴 캐릭터인데 `대국 시작하기`는 활성 상태였다(2026-08-29 Galaxy S22 Ultra 실기 확인).
    - **원인**: 선택된 상대는 `UserPreferences`에 저장된 **AI 레벨**에서 파생된다. `ui/PlayerSetupPanel.kt`의 `PlayerSetupSideRow`가 `side.playLevel.safeLevel` → `BotCharacterCatalog.forPlayLevel(...)`로 캐릭터를 구하면서 **그 캐릭터가 `isAvailable`인지는 보지 않는다.** 획득 여부는 픽커에서 **새로 고를 때만** 강제된다.
    - ⚠️ **엣지 케이스가 아니라 기존 사용자 전체가 해당된다.** #10(`ac625a5`, 2026-08-29) 이전의 단계 드롭다운은 `(1..PlayLevelGroup.FastBeginner.maxLevel)` 전체를 **아무 게이트 없이** 제공했다(2026-08-18 5단계 개편부터 오늘까지). 그 사이 2~5단계를 골라 둔 사용자는 업그레이드 후에도 그 레벨을 그대로 쓰게 된다.
    - **사용자 결정(2026-08-29)**: ⓐ **회수** — 획득한 최고 단계로 낮춘다(그랜드파더링하지 않는다). ⓑ **한 번 알린다** — 조용히 바꾸지 않는다.
    - **낮추는 기준은 "획득한 최고 단계"가 아니라 "요청 단계 이하에서 가장 높은 단계"다.** 획득 집합은 연속이 아닐 수 있어(출석으로 3단계만 먼저 얻는 경우) 그냥 최고를 쓰면 요청보다 **더 센 상대로 올라가** 버린다. 1단계는 기본 제공이라 대체 상대는 항상 존재한다.
    - **작업 중 드러난 함정 2건**(둘 다 계측으로 확인, 주석에 남김): ⓐ 설정 변경이 `GameSettingsController.changePlayerSetup`의 **엔진 사용 중 게이트에 조용히 버려진다** — 앱 기동 직후에는 KataGo가 아직 뜨는 중이라 한 번만 보내면 반영되지 않는다(유한 재시도로 해결). ⓑ `LaunchedEffect`의 키를 `clamp != null`로 두면 **반영되는 순간 키가 뒤집혀 코루틴이 취소**돼 안내가 영영 안 뜬다(키를 `Unit`으로).
    - 실기 검증(에뮬레이터): 획득 0 + 저장된 레벨 5(관장 천원) → 첫돌이로 낮아지고 저장되며, 토스트 "아직 획득하지 않은 상대라 바꿨어요: 관장 천원 → 첫돌이" 표시. 4단계를 **실제로 획득한** 상태에서는 사범 묘수가 그대로 유지되고 아무 일도 일어나지 않는다(반대 케이스).
    - **실기 검증(Galaxy S22 Ultra / SM-S908N, Android 16)**: 재현 조건(획득 0 + AI 좌석 4단계)을 심고 대국 설정에 들어가니 상대가 첫돌이로 바뀌고 토스트 "아직 획득하지 않은 상대라 바꿨어요: 사범 묘수 → 첫돌이"가 떴다. 저장된 레벨도 4 → 1로 기록됐다. 조각 3/5 진행분이 있는 2단계도 획득으로 쳐 주지 않는다는 것이 같은 상태에서 확인된다.
    - 산출물: `application/botcharacter/BotCharacterLevelClamp.kt` 신규(`clampToOwnedBotCharacter`), `ui/PlayerSetupPanel.kt`(적용·안내·재시도), `ui/UiStrings.kt`(4개 언어 안내 문구), 테스트 7건 신설. `GoCoachApp.kt`는 라인 예산(851/851)이 꽉 차 있어 건드리지 않았다.
    - 사용자 승인 완료(2026-08-29).

23. 5단계 관장 천원을 28일차 출석 장기 보상으로 전환 (AI 모델: Opus, 노력정도: 낮음) [완료]
    - 참고: 킥오프 플랜 7장, `feature-access-principles/README.md` 8.3-1절. **#18이 콘솔 게이트에 막혀 발행된 항목이다.**
    - **배경**: Play Console "수익 창출"이 *"대시보드에서 앱 설정을 완료하세요"* 로 잠겨 4,900원 상품 등록이 불가능했다. 비공개 테스트 때문이 아니라 **앱 설정 대시보드 미완료 항목(개인정보처리방침 필드·IARC·데이터 보안)이 선행 조건**인 것으로 보이며, 판매자 설정이 추가로 필요할 가능성도 남아 있다. 그 셋은 `launch-plan/README.md` §0의 남은 콘솔 작업과 같은 목록이다.
    - **결정(사용자)**: 유료 구매는 뒤로 미루고 5단계를 **28일차 출석 보상**으로 연다. 최상위 상대에 닿는 길이 아예 없는 것보다 낫다.
    - ⚠️ **정책표가 캐릭터만 실제 회차로 조회하도록 바꿨다**(`contentTier`가 아니라 `tier`). 소모품은 8일차 이후 7일차를 반복하지만 캐릭터는 한 번뿐인 영구 획득이라 반복 축과 성질이 다르다 — 접어서 조회하면 28일차 캐릭터에 영영 닿지 못한다.
    - **결제·특전 배선(#18)은 지우지 않았다** — 카탈로그에 `Purchase` 캐릭터가 없으면 특전이 항상 거짓이라 조용히 잠든다. 유료를 다시 열면 `BotCharacterPerkTest.theCatalogCurrentlyHasNoPurchasableCharacterSoThePerkLiesDormant`가 먼저 깨져서 알려 준다.
    - **실기 검증(에뮬레이터)**: 28일차 Claim 팝업에 "새 캐릭터 · 관장 천원 (초고수)"가 실리고, 받기 후 `claimedBots`에 `fast_beginner_5`가 들어간다. 픽커 안내는 "출석 28일차에 받을 수 있어요". 7·14·21에는 캐릭터가 없다(테스트로 고정).
    - 산출물: `application/botcharacter/BotCharacterCatalog.kt`(`Attendance(28)` + `TopCharacterAttendanceTier`), `application/attendance/AttendanceRewardPolicy.kt`(캐릭터만 실제 회차 조회), `feature-access-principles/README.md` 8.3-1절, 테스트 갱신 5건.
    - 사용자 승인 완료(2026-08-29).

24. 1회권 재고를 대국 화면 밖으로 빼고 버튼 자체에 남은 수를 녹이기 (AI 모델: Opus, 노력정도: 중간) [완료]
    - 참고: 킥오프 플랜 4.5절(소모품), `ui/ConsumableUiState.kt`의 `ConsumableInventoryBar`, `ui/GamePlaySection.kt`의 형세 보기·추천 수 버튼. **2026-08-30 스토어 스크린샷을 보다 나온 사용자 피드백이다.**
    - **문제**: 대국 화면 하단에 `형세 보기 3 / 추천 수 3 / 광고 스킵권 2` 재고 바가 **상시** 떠 있다. 대국 중에 계속 필요한 정보가 아닌데 자리를 차지하고, 바로 아래 버튼과 같은 말을 두 번 한다.
    - **원하는 모습(사용자)**:
      · 재고 바를 대국 화면에서 **없앤다.**
      · 남은 수를 **버튼 텍스트 옆 괄호**로 녹인다 — `형세 보기 (3)`, `추천 수 받기 (3)`.
      · **무제한일 때는 ∞** 로 표기해 "안 줄어든다"가 한눈에 보이게 한다 — `형세 보기 (∞)`.
      · 전체 재고는 **마이 페이지 같은 별도 자리**에서 본다.
    - ⚠️ **∞ 조건을 "프리미엄"으로 좁히지 말 것.** 차감 없이 쓸 수 있는 경로가 넷이다 — 프리미엄 구매/광고 1시간, 영구 클레임(무르기), 그리고 캐릭터 구매 특전(#18). 판정은 이미 `FeatureAccessPolicy.resolve`가 `FeatureAccess.Allowed`로 돌려주므로 **그 결과를 그대로 쓰면 된다**. 프리미엄만 보면 특전 사용자가 줄지도 않는 숫자를 보게 된다.
    - ⚠️ **광고 스킵권(`premium_once`)은 인게임 버튼이 없다.** 그건 업셀 팝업에서 쓰이므로 버튼에 녹일 자리가 없고, 재고 바를 없애면 **어디에서도 안 보이게 된다** — 마이 페이지 자리가 정해져야 이 항목이 완결된다.
    - **결정(2026-08-30 사용자)**:
      · 마이 페이지는 **ⓒ 새 목적지 신설**. 출석 현황·캐릭터 컬렉션이 나중에 붙을 자리이기도 하다.
      · **표기는 상태마다 다르다** — 영구 프리미엄은 `(∞)`, **광고 1시간 활성은 시계 기호**로 "시간 한정 무제한"이 드러나게, 1회권 보유는 `(3)`.
      · **영구 클레임(무르기)에는 무제한 표시를 붙이지 않는다.**
      · ⚠️ **범위를 넘지 말 것**: 손대는 것은 **프리미엄 또는 소모품이 필요한 버튼**(형세 보기·추천 수)뿐이다. 기권·통과·무르기 등 나머지 버튼은 **간결한 텍스트 그대로 둔다.**
    - ⚠️ **캐릭터 구매 특전(#18)의 표기는 아직 정하지 않았다.** 그것은 "상대 한정 무제한"이라 `∞`(무조건)와도 시계(시간 한정)와도 성격이 다르다. 지금은 카탈로그에 유료 캐릭터가 없어 잠들어 있으므로(#23) 이 항목에서는 정하지 않고, 유료를 다시 열 때 함께 정한다.
    - 관련: 문구는 4개 언어가 필요하다. `∞`와 `⏱`는 번역 대상이 아니라 기호이므로 언어별로 같다.
    - ⚠️ **#17이 넣었던 차감 강조 애니메이션이 함께 사라졌다.** 재고 바(`ConsumableInventoryBar`)와 셀(`ConsumableCountCell`)이 죽은 코드가 돼 지웠고, 그 안의 "줄어드는 순간 잠시 강조" 연출도 같이 없어졌다. 대신 차감은 **방금 누른 그 버튼의 숫자**가 바뀌는 것으로 보인다 — 눈에 띄는 자리로는 오히려 낫다는 판단이지만, #17의 명시적 요구를 접은 것이므로 기록해 둔다.
    - **실기 검증(에뮬레이터)**: 1회권 보유 → `형세 보기 (3)` / `추천 수 받기 (5)`, 광고 1시간 활성 → `(⏱)`, 영구 프리미엄 → `(∞)`. 재고 바는 사라졌고, 마이 페이지에 3종 재고(0 포함)가 뜬다. 홈에 진입 카드가 붙는다.
    - ⚠️ **작업 중 사용자 피드백으로 드러난 버그 2건을 같이 고쳤다(2026-08-30). 둘 다 1회권이 조용히 사라지는 문제다.**
      · **① 코칭 버튼의 활성 조건이 갈려 있었다.** 추천 수는 `!isGameEnded && isEngineReady`인데 형세는 `isEngineReady || LocalTwoPlayer`뿐이라, **대국이 끝났거나 새 대국을 준비하는 동안 형세 버튼만 눌렸다.** 누르면 `featureGated`가 확인 팝업 없이 바로 차감한다 — 실기에서 **9 → 8 → 7**로 실제 소모를 재현했다.
      · **핵심 규칙**: 버튼은 **요청이 받아들여질 때만** 눌려야 한다. 요청을 받는 쪽(`buildScoreEstimateRequestPlan`)이 **`isEngineBusy`에서 거절**하므로, 게이트도 `isEngineBlockingBusy`(느슨)가 아니라 `isEngineBusy`(엄격)를 봐야 한다. 처음엔 느슨하게 잡았다가 조사에서 그 틈을 지적받아 조였다. 배경 분석이 도는 짧은 창에서도 잠기는 것은 의도된 후퇴다 — 잠깐 못 누르는 불편이 표를 잃는 것보다 낫다.
      · **② 끄는 탭도 표를 먹었다.** `featureGated`의 Locked+재고 분기가 `turningOn`을 보지 않아, 1회성 표시가 만료된 뒤 토글만 켜져 있는 상태에서 **끄려고 누르면 한 장이 나갔다.** 켜는 동작이 아닌 탭은 게이팅을 건너뛰게 했다.
      · **기존 결정 두 가지는 지켰다**: 켜진 토글은 게이트가 닫혀도 끌 수 있고(`|| isFilled`), 그래야 사용자가 갇히지 않는다. 회귀 테스트 3건을 새로 넣었다.
    - **버그 수정 실기 검증**: 같은 "준비 중" 상태에서 3번 탭해도 재고가 **9 그대로**(수정 전 9→8→7), 두 버튼이 같은 상태로 잠긴다.
    - **버튼 텍스트도 함께 정리했다(사용자 3안 제시 → 더 나은 방향으로 조정)**: 비대칭의 정체는 "동사 유무"가 아니라 **형세는 '보기', 추천 수는 '받기'라는 서로 다른 동사**였다. 그래서 `topMovesAction`만 **"추천 수 받기" → "추천 수 보기"** 로 바꿔 머리동사를 맞췄다 — **한 줄, 파급 0, 폭 변화 없음**.
      · 사용자의 "1번이 개수 표현엔 낫다"는 직관은 기본 글꼴에서는 성립하지 않는다(버튼 텍스트 영역 144dp 중 가장 긴 조합도 89dp). 다만 글꼴 배율이 커지면 `ActionButtonText`가 `maxLines=1, softWrap=false`라 **끝에서부터 잘리고, 그 끝이 하필 `(3)`/`(∞)`/`(⏱)`** 이다 — 짧은 쪽이 유리하다는 감각 자체는 맞다.
      · **'형세 보기 → 형세 판단' 개명은 하지 않았다.** 일/중은 이미 `形勢判断`이라 용어 통일 이득이 있지만, 그 문자열은 버튼(`UiStrings.eval`)과 1회권 이름(`featureRewardName`)에 **서로 다른 리터럴로 두 번** 박혀 있고 마이 페이지·출석 보상·토스트·업셀·로비 배지가 거기서 파생되며, **2026-08-30에 확정한 Play 등록정보 본문과 스크린샷 한 장까지** 걸린다. 별건으로 남긴다.
      · 죽은 필드 `UiStrings.topMoves`(4개 언어에 값만 있고 읽는 곳 없음)를 함께 지웠다 — 1안이 쓰려던 값이 정확히 이것이라, 남겨 두면 다음 사람이 `topMovesAction` 대신 집는다.
    - 산출물: `ui/MyPageScreen.kt` 신규, `ui/UiStrings.kt`(`featureButtonLabel`·마이 페이지 문구·죽은 `consumableShortName` 제거), `ui/GamePlaySection.kt`(재고 바 제거 + 버튼 라벨), `ui/ConsumableUiState.kt`(재고 바·셀 제거), `ui/GoCoachHomeScreen.kt`(카드), `ui/GoCoachApp.kt`(목적지·라우팅), `presentation/GameScreenState.kt`(코칭 버튼 게이트), `ui/UiStringsKo.kt`(추천 수 보기), 죽은 `topMoves` 필드 제거, `LayeringContractTest` 라인 예산 856→861, 회귀 테스트 4건.
    - 사용자 승인 완료(2026-08-30).

25. 하위 화면 헤더가 상태 표시줄 아래에 깔리는 문제 (AI 모델: Sonnet, 노력정도: 낮음) [완료]
    - **증상**: 대국 기록·마이 페이지 등 하위 화면의 제목과 뒤로가기 버튼이 **상태 표시줄(시계·배터리·알림 아이콘)과 겹쳐** 그려진다. 2026-08-30 #24 작업 중 실기에서 확인했다.
    - **전제 정정(착수 후 확인)**: "앱 전체가 인셋을 안 다룬다"가 아니었다. **설정·학습·홈·온보딩·인게임은 이미 처리돼 있고**, 빠진 것은 **대국 기록과 마이 페이지 둘뿐**이었다. 마이 페이지는 `GameHistoryScreen`을 본떠 만들면서 그 누락까지 물려받았다.
    - **그래서 공통 자리를 새로 만들 필요가 없었다.** 이 저장소에는 이미 확립된 패턴이 있다 — 헤더 `Row`에 `.statusBarsPadding()`을 붙인다(`SettingsScreen`·`StudyScreen`과 같은 자리·같은 방식). 두 화면에 한 줄씩 넣는 것으로 끝났다.
    - ⚠️ **출시 품질 문제다.** 스토어 스크린샷은 상태 표시줄을 잘라내서 티가 안 나지만, 실제 사용자는 매번 본다.
    - 실기 검증(에뮬레이터): 두 화면 모두 제목과 뒤로가기가 상태 표시줄 아래로 내려왔다.
    - ⚠️ **작업 중 본 별개 문제 — #28로 발행.** 홈 화면에서 "이전 대국 이어하기" 버튼이 뜰 때 그 버튼이 **"Go AI Coach" 제목 위에 겹쳐** 그려진다. 인셋과 무관한 레이아웃 문제다.
    - 산출물: `ui/GameHistoryScreen.kt`, `ui/MyPageScreen.kt` (각 한 줄 + 근거 주석).
    - 사용자 승인 완료(2026-08-30).

27. 큰 글꼴 배율에서 대국 버튼의 괄호 표기가 잘리는 문제 (AI 모델: Sonnet, 노력정도: 낮음) [완료]
    - `ActionButtonText`(`ui/GameActionButtons.kt`)가 `maxLines = 1, softWrap = false`에 overflow/autosize가 없어, 넘치면 **끝에서부터 잘린다.** 하필 그 끝이 #24가 재고 바를 없애고 대신 심은 `(3)`/`(∞)`/`(⏱)`다.
    - 기본 글꼴에서는 여유가 있다(버튼 텍스트 영역 144dp, 최장 조합 89dp). 문제는 시스템 글꼴 배율 1.4배 이상 + 좁은 폭(320dp, 분할화면·폴더블 커버)이다. 안드로이드 14+의 비선형 스케일링은 11sp 같은 작은 텍스트를 **더** 키우므로 체감은 더 이르다.
    - ⚠️ **`TextOverflow.Ellipsis`는 답이 아니다** — 괄호를 먼저 먹는다. `TextAutoSize`로 축소하거나, 괄호를 별도 `Text`로 떼어 우선순위를 주는 쪽이 맞다.
    - **원인은 overflow 미지정보다 한 겹 깊었다**: `softWrap = false` + 기본 `TextOverflow.Clip` 조합이면 foundation의 `finalMaxWidth`가 폭 제약을 **무시하고**(`Constraints.Infinity`) 측정한 뒤 부모가 오른쪽을 잘라낸다. 그래서 문자열 꼬리인 잔량이 먼저 죽었다.
    - **`TextAutoSize`는 실재하지만 탈락**: foundation 1.8.0에 `BasicText(autoSize=)`가 opt-in 없이 있다(바이트코드로 확인). 그러나 material3 `Text`에는 그 오버로드가 없어 `BasicText`로 내려가야 하고 — 그러면 버튼이 주는 `LocalContentColor`/`LocalTextStyle`을 직접 넘겨야 한다 — min/max가 sp라 배율을 그대로 먹어 3분할 행을 구하지도 못한다.
    - **채택안**: 잔량을 별도 `Text`로 떼고, `Row`가 **가중치 없는 자식을 먼저 측정**하는 성질을 이용해 표기가 항상 자리를 잡게 했다. 모자란 폭은 이름 쪽이 `Ellipsis`로 흡수한다. 표기가 없는 버튼(기권·통과·무르기)도 `Clip` → `Ellipsis`로 바꿨다 — 글자가 뭉텅 잘리는 것보다 "…"이 낫다.
    - ⚠️ **백로그의 서술 하나가 틀렸다(정정)**: "안드로이드 14+ 비선형 스케일링이 11sp 같은 작은 텍스트를 더 키운다"는 사실이 아니다. Compose의 변환 테이블은 8~12sp 구간이 **정확히 선형**이라 labelSmall(11sp)은 어떤 배율에서도 `11 × 배율`이다. 압축은 14sp 이상에서만 걸린다.
    - 실기 검증(에뮬레이터): 글꼴 배율 **2.0배**에서 `형세 보기 (3)` / `추천 수 보기 (5)`의 잔량이 온전히 렌더된다. 같은 화면 아래 3분할 행의 `새 대국 시작`은 `새 대국 …`으로 말줄임된다 — overflow가 실제로 바뀐 증거다. 정상 배율에서도 그대로다.
      · ⚠️ **정정(2026-08-30, #29)**: 위 문장의 뒷부분은 HEAD에서 더 이상 재현되지 않는다. #29가 그 라벨을 `새 대국`으로 줄이고 가용폭을 96dp로 넓혔기 때문이다. 당시 관측 자체는 사실이었고, overflow 변경의 증거라는 취지도 유효하다.
    - 산출물: `ui/UiStrings.kt`(`featureButtonMark` 신설, `featureButtonLabel`은 낭독기용으로 유지), `ui/GameActionButtons.kt`(`ActionButtonText` 분리 + Ellipsis), `ui/GamePlaySection.kt`(이름·표기 분리 전달).
    - 사용자 승인 완료(2026-08-30).

28. 홈에서 "이전 대국 이어하기" 버튼이 앱 제목을 가리는 문제 (AI 모델: Sonnet, 노력정도: 낮음) [완료]
    - **증상**: 저장된 대국이 있으면 홈 상단에 "▶ 이전 대국 이어하기" 버튼이 뜨는데, 그 버튼이 **"Go AI Coach" 제목 글자 위에 겹쳐** 그려진다. 2026-08-30 #25 작업 중 실기에서 확인했다.
    - **#25(상태 표시줄 인셋)와는 다른 문제다** — 홈 화면은 인셋을 이미 제대로 처리하고 있다. 이쪽은 로고/제목 블록과 이어하기 버튼이 같은 세로 공간을 두고 겹치는 순수 레이아웃 문제다.
    - 저장된 대국이 없으면 버튼이 없어 안 보인다. 그래서 **스토어 스크린샷(신규 사용자 상태)에는 나타나지 않지만**, 두 번째 실행부터의 실제 사용자는 매번 본다.
    - 관련 파일: `ui/GoCoachHomeScreen.kt`.
    - **착수 후 확인된 원인(2026-08-30)**: 홈은 **스크롤이 전혀 없는 고정 높이 `Column`** 이다(`verticalScroll` 없음). 그 안에서 로고/제목 블록이 `Modifier.weight(1f)` + `Arrangement.Center`로 **남은 공간**을 먹는데, 카드가 4장이 되고(#24가 마이 페이지 카드를 추가) 이어하기 버튼까지 붙으면 남는 공간이 내용보다 작아진다. Compose는 기본적으로 클립하지 않으므로 **내용이 경계 밖으로 새어 나가** 이웃과 겹친다.
    - **실기 재현(에뮬레이터)**: 이어하기 버튼이 "Go AI Coach" 제목을 덮고, **태그라인은 아예 보이지 않으며**, "대국 하기" 카드 제목까지 잘린다 — 백로그에 적었던 것보다 심하다.
    - ⚠️ **신규 사용자 화면(이어하기 없음)은 지금 이미 여유가 0이다.** 태그라인이 카드에 거의 닿아 있다 — 카드를 하나만 더 늘려도 이어하기 없이도 깨진다. 세로가 짧은 기기에서는 **저장된 대국이 없어도 이미 제목·부제가 사라진다.**
    - **"겹침"이 아니라 세로 공간 고갈이었다.** Column은 부족분을 100% 가중치 자식에게서 깎는다. 로고 블록이 유일한 가중치 자식이라 그 안의 제목 `Text`가 몇 dp로 측정되고, Compose는 그 높이로 **`clipRect`** 한다 — 잘린 그 선에서 이어하기 버튼이 시작하니 "제목 위에 겹친" 것으로 보였다.
    - ⚠️ **수정은 두 편집이 반드시 함께 가야 한다.** `verticalScroll`만 넣으면 가중치 자식이 붕괴할 여지가 있고, `Spacer` 전환만 하면 부족분이 마지막 카드로 옮겨가 카드가 찌그러진다. 그래서 ⓐ 바깥 Column에 스크롤을 걸고 ⓑ `weight(1f)`를 블록에서 **위·아래 `Spacer`로 옮겼다** — 모자랄 때 0이 되는 쪽이 여백이 되게.
    - **실기 검증(에뮬레이터)**: 이어하기 버튼이 있어도 제목·태그라인·카드 4장이 모두 온전하다. **신규 사용자 화면은 수정 전후로 1px만 움직였다**(초록 카드 상단 0px, 로고 1px) — 스토어 스크린샷 제약을 만족한다. 폭 308dp + 글꼴 2.0배 스트레스 조건에서도 겹침이 없고 스크롤로 흡수된다.
    - 산출물: `ui/GoCoachHomeScreen.kt`(import 2줄, `verticalScroll`, `weight`→`Spacer` 전환).
    - 사용자 승인 완료(2026-08-30).

29. 큰 글꼴 배율에서 잘리는 나머지 자리들 (AI 모델: Sonnet, 노력정도: 낮음) [완료]
    - **#27이 코칭 버튼만 고쳤다.** 같은 계통의 잘림이 두 군데 더 있고, 둘 다 #27 작업 중 실기·코드로 확인했다.
    - ⓐ **대국 화면 3분할 행**(기권·통과·무르기·새 대국 시작). 가용폭이 1행의 절반쯤이라 한국어 `새 대국 시작`은 배율 **1.5배 근처**부터, 일본어 `新しい対局を開始`는 **배율 1.0배·좁은 폭에서 이미** 넘친다. #27로 `Clip` → `Ellipsis`가 돼 "…"이 보이긴 하지만 근본 해결은 아니다.
    - ⓑ **홈의 `MenuCard` 부제**. 카드가 `height(120.dp)` 고정이라 부제가 잘린다.
      · **접수 당시 "배율 2.0배에서"라고 적었지만 실제로는 기본 배율에서 이미 잘리고 있었다.** 일본어는 411dp(=스토어 스크린샷을 찍은 바로 그 구성), 한국어는 360dp에서 부제 마지막 줄이 썰린다. 4장 모두 해당.
    - **가용폭 재측정 결과: #27의 144dp/88dp가 맞다. 152dp/96dp 지적이 틀렸다.**
      · material3 1.3.2 바이트코드로 확인 — `Button` 내부는 `defaultMinSize(58,40).padding(contentPadding)`뿐이고, `defaultMinSize`는 `minWidth == 0`일 때만 개입하는데 `weight(1f)`가 min=max 고정 제약을 준다. 즉 M3가 더 붙이는 가로 여백은 **0**이다. 152/96은 좌우 중 한쪽 8dp만 뺀 산수 실수였다.
      · 공식: `텍스트 가용폭 = (W − 32 − 8×(n−1)) / n − 16` → 360dp에서 1행 144dp / 2행 **88dp**.
      · 이 모델이 증상 두 개를 정확히 예측한다: 일본어 `新しい対局を開始`는 전각 8자 = **92dp**라 88dp를 1.0배에서 이미 초과, 한국어 `새 대국 시작`은 64.2dp라 1.37배 = **1.5배 눈금에서** 초과. 96dp였다면 둘 다 통과했어야 하므로 증상 자체가 88dp를 확정해 준다.
      · **문제는 2행 `newGameAction` 하나뿐**이다. 1행은 가장 긴 일본어 `候補手を見る`(69dp)도 1.72배까지 버틴다.
    - **수정(2026-08-30)** — ⓐ는 상수와 문자열만, ⓑ는 `MenuCard` 한 컴포저블 안에서 끝냈다:
      · ⓐ-1 `ActionButtonContentPadding` 가로 **8dp → 4dp**. 2행 88→96dp, 1행 144→152dp. 짧은 라벨의 겉모습은 **바뀌지 않는다** — M3 `Button` 내부가 `Arrangement.Center`라 남는 여백이 어차피 양쪽으로 갈리기 때문이다. 실제로 넓어지는 건 넘치던 라벨뿐.
      · ⓐ-2 `newGameAction` 단축: `새 대국 시작`→**`새 대국`**, `新しい対局を開始`→**`新規対局`**, `开始新对局`→**`新对局`**. 같은 뜻의 긴 문구는 `overwriteWarningTitle`(다이얼로그 제목, 폭 여유 있음)이 그대로 갖고 있다.
      · ⚠️ **영어는 일부러 `New Game`으로 되돌렸다.** 폭만 보면 `Rematch`(48dp)가 유리했지만, 이 버튼이 쏘는 `StartConfiguredGame`은 **그 시점의 현재 설정**으로 시작한다 — 대국이 끝난 뒤 헤더에서 상대나 판 크기를 바꿔 놓고 눌러도 되므로 "같은 상대와 다시"라는 `Rematch`는 거짓이 될 수 있다. 나머지 세 언어가 전부 "새 대국"인데 영어만 뜻이 갈리는 것도 나쁘다. 대가로 영어만 1.8배부터 `New Ga…`가 되는데, 일본어가 **기본 배율에서** 잘리던 것에 비하면 가벼운 손해다.
      · ⓑ `MenuCard`의 고정 높이를 **배경을 칠하는 `Box`의 `heightIn(min = 120.dp)`** 로 바꿨다.
    - **ⓑ의 진짜 원인은 글꼴 메트릭이 아니라 상속된 줄 높이였다.** 두 `Text`가 `fontSize`만 덮고 `lineHeight`는 M3 `bodyLarge`의 **24sp**를 상속한다 — 13sp 부제도 한 줄에 24dp를 먹는다(실측 줄 피치 정확히 24.0dp). 120dp − 패딩 48dp = 72dp 안에서 제목 24 + 간격 4 + 부제 1줄 24 = 52dp는 들어가지만, **부제가 두 줄이 되는 순간 76dp**라 넘친다.
    - ⚠️ **하한을 `Card` modifier에 달면 안 된다.** M3 `Card`는 내용을 modifier 없는 `Column`으로 감싸서 maxHeight가 Infinity가 되고, 배경 `Box`만 내용 높이로 줄어 **카드 아래에 칠하지 않은 띠**가 남는다. 실제로 그렇게 짰다가 실기에서 보고 고쳤다.
    - **실기 검증(에뮬레이터, 360dp)**: ⓐ 일본어 기본 배율에서 `新規対局`이 46dp로 온전히 렌더(이전엔 `新しい対局を…`), 한국어 1.5배에서 5개 버튼 전부 말줄임 없음. 접근성 트리 실측폭이 예측과 일치했다(`候補手を見る` 207px=69.0dp vs 예측 69.0dp). ⓑ 한국어·일본어 기본 배율에서 부제 2줄이 온전하고, 2.0배 한국어에서 3줄까지 온전하다. 부제 1줄이면 카드는 여전히 120dp라 기존 화면은 1px도 안 바뀐다.
    - 산출물: `ui/GameActionButtons.kt`(패딩 1줄), `ui/UiStringsKo.kt`·`UiStringsJa.kt`·`UiStringsZh.kt`(라벨 3줄), `ui/GoCoachHomeScreen.kt`(`heightIn`+`contentAlignment`), `test/ui/UiStringsTest.kt`(고정값 갱신).
    - ⚠️ **스토어 스크린샷 재캡처가 필요하다.** `01_home.png`는 카드가 **3장**인데 HEAD는 4장을 렌더한다(마이 페이지, be152cc). 대국 화면 스샷도 버튼 텍스트가 옛것이다.
    - 사용자 승인 완료(2026-08-30).

31. 판정 결과 다이얼로그가 **일본어·중국어에서 한국어를 그대로 노출한다** (AI 모델: Sonnet, 노력정도: 낮음) [완료]
    - #29 커밋 전 검토에서 발견(2026-08-30). **#29와 무관한 기존 결함**이지만 같은 다이얼로그(`FinalJudgementDialog`)라 함께 걸렸다.
    - `UiStringsJapanese`·`UiStringsChineseSimplified`가 `UiStringsKorean.copy(...)`인데 `finalJudgementTitle`·`reviewJudgement` 두 필드를 **덮지 않는다**. `copy()`는 지정 안 한 필드를 원본 값으로 유지하므로, 일본어·중국어 사용자에게 `판정 결과` / `판정 검토`가 한글 그대로 뜬다. 영어(`Judgement` / `Review`)는 정상이다.
    - 관련 파일: `ui/UiStringsJa.kt`, `ui/UiStringsZh.kt`(각 2줄 추가), 선언은 `ui/UiStrings.kt:111-112`.
    - ⚠️ **같은 계통이 더 있는지 전수로 훑을 것.** `copy()` 누락은 컴파일 에러가 나지 않아 조용히 샌다. 검토 스크립트가 "ja/zh 미덮음 8개 중 6개는 `when (language)` getter라 정상"이라고 봤지만, 그 판정 자체를 다시 검증하고 회귀 테스트로 고정하는 편이 낫다 — `UiStringsTest`에 "모든 언어가 모든 생성자 필드를 자기 언어로 갖는다"를 넣을 수 있다.
    - **전수 조사 결과(2026-08-30, 착수 시점)** — 새는 곳은 정확히 그 둘뿐이다:
      · 생성자 필드 **182개**. 미덮음은 En 1개(`appTitle`), Ja·Zh 각 3개(`appTitle`, `finalJudgementTitle`, `reviewJudgement`).
      · `appTitle = "Go AI Coach"`는 브랜드명이라 한글이 없어 정당하게 상속된다.
      · `when (language)` 게터의 비한국어 분기에 한글이 섞인 줄은 **0건**(`UiStrings.kt` 전수 스캔). 즉 게터 쪽은 깨끗하다.
    - **`reviewJudgement`의 번역어는 "검토"가 아니어야 한다.** `onReview`는 `GoCoachApp.kt:312 activateEndgameJudgementReview`로 **반상에 집계 오버레이를 켜고**(`showOwnershipOverlay = true`) 다이얼로그를 닫는다(`GoCoachContent.kt:104-107`). 다시 들여다보는 "검토"가 아니라 "결과를 반상에서 확인"이다.
    - ⚠️ **착수 중 적었던 메모 하나를 정정한다.** "권한이 없으면 이 버튼이 다이얼로그만 닫는다"고 적었지만 **틀렸다.** `activateEndgameJudgementReview`에 early return이 있는 건 맞으나, 종국 시점에는 `GamePlaySection.kt:103-118`이 `screenState.isGameEnded`만으로 오버레이를 통과시킨다(`mayShow`와 ownership `takeIf` 양쪽 모두). 게다가 그 함수는 종국 처리에서 이미 호출된 뒤다(`TurnFlowControllerWiring.kt:124,150`). 즉 **프리미엄이든 아니든 반상에는 이미 집계가 깔려 있고**, 이 버튼의 실제 효과는 "다이얼로그를 치워서 그 반상을 보게 해 주는 것"이다 — 그래서 라벨이 정확히 `盤面で確認` / `查看盘面`이다.
    - **수정(2026-08-30)** — 두 파일에 2줄씩, 그리고 회귀 그물 하나:
      · ja `finalJudgementTitle = "対局結果"`, `reviewJudgement = "盤面で確認"`
      · zh `finalJudgementTitle = "终局结果"`, `reviewJudgement = "查看盘面"`
    - **번역어를 직역하지 않은 이유**(원어민 관점 검토 + 반박 검증 통과):
      · ja `判定結果` 탈락 — 일본어 `判定`은 심판 판정 어감이라 계가 결과에 딱딱하다. 파일 전체가 `対局` 계열로 일관돼(`新規対局`·`対局設定`·`対局記録`·`対局形式`) `対局結果`가 그대로 얹힌다.
      · zh `判定结果` 탈락 — **같은 파일의 `eval = "形势判断"`과 겹쳐 "형세판단 기능의 결과"로 오독될 위험**이 크다. 이 다이얼로그는 대국 중 형세판단이 아니라 종국 결과다. `终局`은 围棋 표준 용어이고, 기권은 판정을 남기지 않으므로(`GameHistoryAppendApplication.kt`) 이 다이얼로그가 뜨는 시점을 정확히 가리킨다.
      · ja `検討` / zh `复盘` 탈락 — **둘 다 "복기(수순 되짚기)"라는 확립된 전문 용어**인데 이 버튼에는 그런 기능이 없다. 누르면 배신당한다.
      · zh `查看盘面`의 `盘面`은 **같은 다이얼로그 본문**의 `由于盘面状况不明确`와 맞물리고, `查看`는 `查看推荐手`로 이미 확립된 동사다.
    - **회귀 그물**: `UiStringsTest`에 자바 리플렉션 기반 테스트를 넣었다 — **"비한국어 인스턴스의 어떤 String 필드도 한글을 포함하지 않는다."**
      · 182개를 손으로 나열하지 않으므로 필드가 늘어도 따라간다. `appTitle`처럼 일부러 상속하는 필드는 한글이 없어 예외 목록 없이 통과한다.
      · ⚠️ `field.isAccessible = true`가 없으면 안 된다 — 코틀린은 생성자 프로퍼티 백킹 필드를 `internal`이어도 `private final`로 낸다. 같은 패키지여도 소용없고, 없으면 `IllegalAccessException`으로 **엉뚱한 이유로** 실패해 메시지가 번역 누락을 한마디도 안 알려준다(검토에서 실제로 걸렸다).
      · 필터가 조용히 0개를 집는 사고를 막으려 **자기검증**을 넣었다(한국어 인스턴스에서 한글 필드가 100개 넘게 잡히는지 먼저 확인).
      · red/green 확인: 번역을 되돌리면 2건 실패하고 메시지가 `Japanese.reviewJudgement = "판정 검토"`처럼 언어·필드·샌 값을 그대로 짚는다.
      · 한계: `when (language)` 게터는 이 그물이 못 잡는다(별도 전수 스캔으로 0건 확인). 값이 `List<String>`인 필드가 생겨도 못 잡는다.
    - **실기 검증(에뮬레이터)**: 일본어·중국어로 각각 사람 대 사람 대국을 두 번 패스로 종국시켜 다이얼로그를 띄웠다. 수정 전 일본어는 제목 `판정 결과` + 버튼 `판정 검토`가 한글 그대로였고, 수정 후 일본어는 `対局結果`/`新規対局`/`盤面で確認`, 중국어는 `终局结果`/`新对局`/`查看盘面`으로 전부 자국어다.
    - 산출물: `ui/UiStringsJa.kt`·`ui/UiStringsZh.kt`(각 2줄), `test/ui/UiStringsTest.kt`(테스트 2건 + 헬퍼 신설).
    - 사용자 승인 완료(2026-08-30).

30. 큰 글꼴 배율에서 대국 화면의 **점수 바와 헤더가 잘린다** (AI 모델: Sonnet, 노력정도: 중간) [완료]
    - #29 실기 검증 중 발견(2026-08-30). #29가 고친 것은 버튼 라벨의 **가로** 잘림인데, 이건 고정 높이·`maxLines`에 걸린 **다른 컴포넌트들**이다.
    - ⚠️ **접수 초안의 진단이 틀려 다시 썼다(2026-08-30).** 처음엔 "버튼 두 행이 화면 밖으로 밀려나는데 대국 화면에는 스크롤이 없다"고 적었는데 **둘 다 사실이 아니다.** `GoCoachContent.kt:120`의 루트 Column에는 2026-06-09부터 `verticalScroll`이 있고(커밋 7d1a278), 실기에서 스와이프하면 `기권·통과·무르기`에 닿으며 배율 2.0배에서도 세 라벨 모두 온전하다(42.0 / 42.0 / 63.3dp). 첫 관측은 화면 밖 노드가 접근성 트리 덤프에 안 잡힌 것을 "존재하지 않는다"로 오독한 결과다. **홈(#28)식 스크롤 해법을 검토할 필요가 없다 — 이미 있다.** 다만 접수 초안의 알맹이 하나는 살아 있다: 스크롤이 있어도 **버튼을 보려면 정사각 판이 화면 밖으로 나간다**. 그래서 이 항목의 답은 스크롤이 아니라, 판 크기를 남은 공간에 맞추거나 잘리는 컴포넌트를 개별로 고치는 쪽이다. (제스처 충돌은 의심할 필요 없다 — `GoBoard`는 `detectTapGestures`뿐이라 판 위에서도 스크롤이 정상 동작한다.)
    - ⓐ **점수 바가 진짜 결함이다.** `ui/GameStatusPanel.kt:88`의 `.height(48.dp)` 고정 때문에, 배율 2.0배·360dp에서 둘째 줄(`흑 100% · 백 0%`)이 반토막 나고 오른쪽 `백 사석 0`이 `백`으로 잘린다. **#29 ⓑ와 정확히 같은 계통**이므로 같은 처방(`heightIn(min = …)`)이 먹힐 가능성이 높다 — 다만 이건 `Card`가 아니라 `Row`라 배경 소유자가 누구인지 먼저 볼 것.
    - ⓑ **헤더 제목이 잘린다.** `ui/GameMenuSection.kt`의 `GameHeaderSection` 제목 `Text`가 `maxLines = 2`라, 2.0배에서 `흑: 유저 / 백: KataGo 초고수`가 두 줄에 못 들어가 **상대 이름이 통째로 사라진다**(일본어 `黒: プレイヤー / 白:`에서 끊김). 지금 상대가 누구인지가 대국 화면의 핵심 정보다.
    - ⓒ 1행 코칭 버튼은 2.0배에서 `추천 수 보…(5)`로 말줄임된다. **이건 의도된 동작이다**(#27이 잔량을 별도 `Text`로 떼어 언제나 살아남게 했고, 이름 쪽이 Ellipsis로 흡수한다). 고칠 거리로 착각하지 말 것 — 굳이 손대려면 #29가 탈락시킨 수단들(줄바꿈·autoSize)의 근거를 먼저 다시 읽어라.
    - 곁가지: 영어·360dp에서 점수 바 텍스트가 붙어 렌더된다(`White 0%Captures 0`). ⓐ와 같은 컴포넌트라 함께 볼 것.
    - ⚠️ **접수 시 적어 둔 파일 위치가 또 틀렸다(정정).** `GameStatusPanel.kt:88`의 `.height(48.dp)`는 점수 바가 아니라 **가운데 `착수` 버튼**이었다. 진짜 점수 바는 `ui/ScoreGraphPanel.kt`의 `ScoreTimelineGraph`이고, 접힌 높이는 `:56`의 `targetHeight = ... else 44.dp`가 `:113`의 `.height(heightDp)`로 걸린다. `PlayerSeatCard`에는 고정 높이가 아예 없다 — 실기에서 `사석: 0`이 잘려 보였던 것은 **뷰포트 밖이라 접근성 트리가 잘린 높이로 보고**한 것이고, 스크롤하면 온전하다.
    - **수정(2026-08-30)**:
      · ⓐ-1 접힌 요약 바를 `heightIn(min = heightDp)`으로. 44dp 고정일 때 가운데 두 줄(`bodySmall` 14sp + `labelSmall` 11sp)이 배율 2.0배에서 약 72dp를 요구해 아랫줄이 썰렸다.
      · ⚠️ **양쪽을 `heightIn`으로 통일하면 안 된다.** 펼친 그래프는 maxHeight가 Infinity가 돼 안쪽 `Canvas(fillMaxSize)`가 무너진다 — `isExpanded`로 갈라 고정/하한을 나눴다.
      · ⓐ-2 세 칸에 모두 `weight`를 줬다. 가중치 없는 자식 셋 + `SpaceBetween`이라 글꼴이 커지면 남는 공간이 사라지며 글자가 맞붙었다(영어 `White 0%Captures 0`). 이제 칸 경계가 고정되고 넘치면 자기 칸 안에서 말줄임된다.
      · ⓐ-3 승률 줄만 `maxLines = 2`. 바가 하한 높이가 됐으므로 말줄임으로 한쪽 승률을 지우는 것보다 두 줄로 다 보여주는 편이 낫다.
      · ⓑ 헤더 제목 `maxLines = 2` → **4** + `TextOverflow.Ellipsis`. 이 칸은 화면 폭의 절반(가중치 1:2:1)뿐인데 `흑: 유저 / 백: KataGo 초고수`는 2.0배에서 약 380dp라 3줄이 필요하다. overflow 미지정(=Clip)이라 **잘렸다는 신호조차 없었다.**
    - **실기 검증(에뮬레이터 360dp)**: 배율 2.0배에서 헤더가 3줄로 상대 이름까지 온전하고(112dp), 점수 바가 자라 `W +1.6` + `흑 29% · 백 71%`가 다 보이며 좌우 `사석` 표기가 같은 폭(90dp)으로 잘림 없이 렌더된다. 기본 배율(1.0배)에서는 바가 여전히 44dp 한 줄이라 겉모습이 사실상 그대로다.
    - ⓒ(1행 코칭 버튼 말줄임)는 **손대지 않았다** — #27이 의도한 동작이다.
    - 산출물: `ui/ScoreGraphPanel.kt`, `ui/GameMenuSection.kt`.
    - 사용자 승인 완료(2026-08-30).

34. 메인 화면 UX 정리 — 언어를 설정 안으로, 마이 페이지를 좌상단으로, 유료 노출 정리 (AI 모델: Opus, 노력정도: 높음) [완료]
    - **2026-08-30 사용자 지시. 최우선 목표는 "비공개 테스트용 AAB를 새로 빌드해 등록하는 것"이고, 이 항목이 그 앞에 선다.**
    - ⓐ **언어 선택을 홈 우상단에서 설정 화면 안으로 옮긴다.**
      · 설정에 이미 언어 절이 있지만 `SettingChoiceRow`로 **4개 언어를 나열**하는 방식이라, 언어가 늘면 유지보수가 어렵다. 홈 우상단의 **드롭다운 방식을 그대로 가져가 재사용**한다(사용자 지시).
      · 관련 파일: `ui/GoCoachHomeScreen.kt`의 `HomeLanguageSelector`(드롭다운 원본), `ui/GameMenuSection.kt:159 LanguageSettingsPanel`(나열형 대체 대상), `ui/SettingsScreen.kt:219`(호출부).
      · ⚠️ **`LanguageSettingsPanel`은 대국 중 메뉴(`ExpandedGameMenuSection`)도 쓴다.** 한 곳을 고치면 두 곳이 같이 바뀐다 — 대국 중 다이얼로그 폭에서도 드롭다운이 멀쩡한지 확인할 것.
    - ⓐ-2 ⚠️ **언어가 저장되지 않는다 — 이 이동의 숨은 전제다.**
      · `UiStrings.kt:50`이 `var language by remember { mutableStateOf(UiLanguage.Korean) }`라, **앱을 껐다 켜면 항상 한국어로 돌아간다**(실기 확인. 글꼴 배율을 바꿔 액티비티가 재생성돼도 초기화된다).
      · 홈 상단의 임시 칩일 때는 그나마 넘어갔지만, **설정 화면에 들어가는 순간 "저장되지 않는 설정"이 되어 더 나쁘다.** 그래서 이동과 함께 영속화한다.
      · ⚠️ `UiLanguage`는 `app-android`의 UI 타입이고 `UserPreferencesSnapshot`은 `shared`다 — **enum을 그대로 넣으면 계층 위반**이다. 이름 문자열로 저장하고 app-android에서 매핑할 것.
      · ⚠️ **자동저장은 스냅샷을 매번 새로 조립한다** — 새 필드를 조립부에 끼워 넣지 않으면 저장은 되는데 다음 저장 때 조용히 기본값으로 덮인다. 인코드/디코드/조립 세 곳을 모두 손댈 것.
    - ⓑ **마이 페이지를 홈 좌상단 버튼으로 올린다.**
      · 지금은 홈 하단 카드 4장 중 하나다(#24에서 신설). 그 카드를 **없애고** 좌상단에 버튼으로 둔다 — 기존 상단 버튼 스타일(`RoundedCornerShape(18.dp)` + `surfaceVariant` 칩) 유지, **사람 상반신 이모지** 포함(사용자 지시).
      · 좌상단이 마이 페이지가 되므로 **설정은 우상단(언어 칩이 비운 자리)으로 간다.** 사용자 지시에 명시되진 않았으나, 상단 Row가 `SpaceBetween` 2칸이고 ⓐ가 우측을 비우므로 이 배치가 자연스럽다.
      · 부수 효과: 홈 카드가 3장으로 줄어 **#28에서 빠듯해졌던 세로가 다시 여유로워진다.** 그리고 스토어 `01_home.png`(3카드로 찍힌 낡은 세트)와 **다시 맞아떨어진다** — 다만 상단 버튼이 바뀌었으므로 재캡처는 여전히 필요하다.
    - ⓒ **유료 노출 정리 — 비공개 테스트가 끝나기 전에는 결제 경로를 언급조차 하지 않는다.**
      · 현황 확인(2026-08-30): `FeatureFlags.isPurchaseEnabled`·`isBotCharacterPurchaseEnabled` **둘 다 이미 `false`** 라 결제 버튼은 노출되지 않고, `store_listing.txt`의 "앱 내 결제 없음"도 현재 빌드와 일치한다. 로스터에 유료 전용 캐릭터도 없다(#23).
      · **그래서 남은 일은 문구다.** `undoClaimSuccessMessage = "구매 완료! 기능 획득! 축하합니다."` — 무르기 **무료** 클레임에서 뜨는데 "구매 완료"라고 말한다. 결제가 없는 빌드에서 결제를 암시하므로 4개 언어 모두 고친다.
      · ⚠️ 착수 시 **결제를 암시하는 문구가 더 없는지 전수로 훑을 것.** 플래그로 가려진 것과 문구로 남은 것은 다르다.
    - **수정(2026-08-30)**:
      · ⓐ 홈의 드롭다운을 `GameMenuSection.kt`로 옮겨 `LanguageDropdownChip`으로 공개하고, `LanguageSettingsPanel`의 나열형 `SettingChoiceRow`를 그것으로 교체했다. 홈 `GoCoachHomeScreen`은 이제 언어 파라미터 자체를 받지 않는다.
      · ⓐ-2 **언어를 영속화했다 — 다만 `UserPreferencesSnapshot`이 아니라 별도 스토어(`persistence/UiLanguageStore.kt`)다.** 착수해 보니 자동저장 조립부(`buildUserPreferencesSnapshot` → `toUserPreferencesSnapshot`)가 `hasSeenOnboarding`과 `gameSetupUxMode`를 **이미 떨어뜨리고 있었다** — 거기 끼워 넣었으면 같은 함정에 빠진다. `DeveloperModeStore`가 정확히 같은 이유로 이미 독립 저장소를 쓰고 있어 그 선례를 따랐다.
      · ⓑ 상단 Row가 좌 `🧑 마이 페이지` / 우 `⚙ 설정`이 되고 하단 마이 페이지 카드를 없앴다. 설정 전용이던 `HomeSettingsButton`을 이모지·라벨만 받는 `HomeTopChip`으로 일반화해 두 칩이 같은 모양을 쓴다.
      · ⓒ 결제 노출은 **플래그로 이미 다 꺼져 있었고**(`isPurchaseEnabled`·`isBotCharacterPurchaseEnabled` 둘 다 `false`), `store_listing.txt`의 "앱 내 결제 없음"도 현재 빌드와 일치한다. 남은 건 문구 하나였다 — 한국어 `undoClaimSuccessMessage`가 **무료** 클레임에서 "구매 완료!"라고 말하고 있었다(영·일·중은 이미 "획득/解锁/Unlocked"로 정상). `"무르기를 얻었어요! 앞으로 계속 쓸 수 있습니다."`로 교체.
      · ⓒ 전수 확인: 결제를 암시하는 나머지 문구는 전부 게이트 뒤다 — `premiumUpsellPurchaseOption`은 `isPurchaseEnabled`, `settingsDeleteAccountConfirmMessage`는 `isLoginEnabled`, `settingsDevPremiumToggleSubtitle`은 개발자 모드, `premiumPurchaseFailedMessage`는 결제 시도 경로.
    - **실기 검증(에뮬레이터)**: 홈이 상단 칩 2개 + 카드 3장으로 바뀌었고 이어하기 버튼과 함께도 여유가 있다. 설정 화면과 대국 중 메뉴 모두 언어 행이 드롭다운으로 뜬다. **일본어로 바꾸고 앱을 강제 종료 후 재실행해도 일본어가 유지된다**(수정 전에는 항상 한국어로 초기화됐다).
    - 부수 효과: 홈 카드가 3장으로 줄어 스토어 `01_home.png`의 3카드 구성과 **다시 맞아떨어진다.** 다만 상단 칩이 바뀌었으므로 재캡처는 여전히 필요하다.
    - 산출물: `ui/GoCoachHomeScreen.kt`, `ui/GameMenuSection.kt`, `ui/UiStrings.kt`, `ui/UiStringsKo.kt`, `ui/GoCoachApp.kt`, `persistence/UiLanguageStore.kt`(신규).
    - 사용자 승인 완료(2026-08-30).

### 진행 중

18. 봇 캐릭터 개별 구매 배선 — 5단계 관장 천원, **4,900원** 단발성 결제·영구 소유 (AI 모델: Opus, 노력정도: 높음) [진행중]
    - 참고: 7장 획득 경로 표. **Phase 1 범위 확장이다** — 7장이 원래 "범위 밖(설계만 고려)"으로 못박아 뒀던 항목인데 2026-08-24에 범위 안으로 들어왔다.
    - `BotUnlockSource.Purchase`를 신설한다(#8에서 "지금 만들지 않았다"고 미뤄 둔 타입 — sealed라 추가하면 기존 `when`이 컴파일 에러로 빠진 처리를 잡아준다). **비소모성 단발 결제라 이미 검증된 `premium_lifetime`의 `AndroidBillingClient`(INAPP) 패턴을 그대로 재사용할 수 있다.**
    - 착수 전 확인(사용자): **Play Console 신규 상품 등록은 사용자 몫**이다(상품 ID·가격 9,900원 설정, 라이선스 테스터). 등록 전에는 실기 검증이 불가능하므로 착수 시점에 등록 여부를 먼저 확인할 것.
    - **구현 완료(2026-08-29) — 다만 결제 자체는 검증 불가**:
      · 결제 배선: `AndroidBillingClient(activity, productId)`가 이미 상품 ID로 매개변수화돼 있어 그대로 재사용했다. 상품 ID는 `local.properties`의 **`billing.botCharacterProductId`** 키로 주입하며, 없으면 플레이스홀더로 폴백해 "상품 없음"으로 안전하게 실패한다(프리미엄 상품과 같은 패턴).
      · 플래그: **`FeatureFlags.isBotCharacterPurchaseEnabled`(기본 `false`)** 를 신설했다. `isPurchaseEnabled`와 **일부러 분리**했다 — 그건 프리미엄 영구 구매를 가리는데, 프리미엄은 월 구독으로 가기로 확정돼 있어 캐릭터를 팔자고 그것까지 되살릴 이유가 없다.
      · 특전: `AllowedVia.CharacterPerk` 신설 + `PremiumUiState.resolve` 한 곳에서 접었다. 8.3절이 걱정한 "호출부 전부가 바뀐다"는 일어나지 않았다 — 인게임 게이팅이 이미 그 람다 하나로 모여 있었다.
      · ⚠️ **1회권 차감 방지가 이 항목의 숨은 핵심이다.** 특전을 소모품 판정(`alreadyAllowedVia`)까지 흘려보내지 않으면, 특전으로 이미 열려 있는데 표가 조용히 줄어든다. 테스트로 고정했다.
      · 재설치 복원: 산 캐릭터가 재설치로 사라지지 않도록 시작 시 복원 조회를 넣었다(`buildBotCharacterUiState` 안 — `GoCoachApp.kt` 라인 예산을 쓰지 않으려는 이유도 있다).
    - **실기 검증(에뮬레이터)**: 관장 천원을 소유로 심고 프리미엄 없이 대국 → 형세 보기가 열리고 영역 오버레이가 렌더되며 **1회권 3장이 그대로**. 상대를 첫돌이로 바꾸면 잠금 테두리가 돌아온다(특전이 새지 않는다).
    - ⚠️ **결제 부분은 Play Console 게이트에 막혀 보류됐다(2026-08-29)**. 콘솔의 "수익 창출"이 **"대시보드에서 앱 설정을 완료하세요"** 로 막혀 상품 등록 자체가 불가능하다 — 비공개 테스트 중이라서가 아니라, **앱 설정 대시보드의 미완료 항목(개인정보처리방침 필드·IARC 콘텐츠 등급·데이터 보안 양식)이 수익 창출의 선행 조건**이기 때문으로 보인다. 판매자(결제 프로필) 설정이 별도로 필요할 가능성도 남아 있다. 그 셋은 이미 `launch-plan/README.md` §0의 남은 콘솔 작업과 같은 목록이라, 하나를 끝내면 둘이 같이 열린다.
    - **그래서 결제는 미루고 5단계를 출석 보상으로 돌린다(2026-08-29 사용자 결정)** — 별도 항목으로 발행. 이 항목의 **특전·결제 배선 코드는 지우지 않고 그대로 둔다**: 카탈로그에 `BotUnlockSource.Purchase` 캐릭터가 없어지면 `isBotCharacterPerkActive`가 항상 `false`라 조용히 잠들 뿐이고, 유료 상품을 다시 열 때 그대로 깨어난다.
    - 남은 것(콘솔이 열린 뒤): ⓐ 4,900원 상품 등록 + 라이선스 테스터, ⓑ 상품 ID를 `local.properties`의 `billing.botCharacterProductId`에 기입, ⓒ `isBotCharacterPurchaseEnabled`를 `true`로.
    - 산출물(승인 대기): (shared) `application/botcharacter/BotCharacterPerk.kt` 신규, `application/premium/FeatureAccessPolicy.kt`(`CharacterPerk`·`characterPerkActive`), `application/consumable/ConsumableSpendApplication.kt`(특전 반영). (app-android) `build.gradle.kts`·`ui/FeatureFlags.kt`·`ui/PremiumPurchaseGlue.kt`·`ui/PremiumUiState.kt`·`ui/BotCharacterUiState.kt`·`ui/PlayerSetupPanel.kt`·`ui/UiStrings.kt`·`ui/GoCoachApp.kt`. 테스트 9건 신설, `LayeringContractTest` 라인 예산 851→856(사유는 그 주석에).
    - ⚠️ **프리미엄 월 구독 전환과 혼동하지 말 것** — 그건 이 백로그 밖(`premium-mode` 트랙)이고, 이 항목은 캐릭터 1종을 파는 별개 상품이다.
    - **구매 특전도 이 항목의 몫이다(2026-08-29 추가)**: 구매자는 **그 캐릭터와 두는 동안** 형세 보기·추천 수를 무제한 쓴다. ⚠️ 지금의 `FeatureAccessPolicy`는 전역 판정이라 이걸 표현할 수 없다 — 착수 전 정해야 할 세 가지가 `feature-access-principles/README.md` 8.3절에 있다.

### 예정사항

> ⚠️ **번호는 식별자일 뿐, 실행 순서는 이 목록에 적힌 순서다.** 2026-08-24 피드백으로 들어온
> #12~#15가 기존 #10·#11보다 먼저 처리돼야 했기 때문이다(캐릭터 픽커 #10은 봇이 실제로
> 지급돼야 의미가 있는데, 그 지급 배선이 #13이었다). 기존 번호는 완료 항목과 킥오프 플랜
> 각주에서 참조하고 있어 다시 매기지 않았다 — **새 스레드는 번호가 아니라 이 목록의 맨 위
> 항목을 가져간다.** 2026-08-24 보상 구조 재확정으로 #16~#18이 새로 들어왔고, 같은 날 #16을
> 두 조각(#16 카탈로그 재구성 / #19 정책표·지급량 갱신)으로 나눴다.
>
> **2026-08-30 현황**: #16·#19·#17·#10·#11·#20~#23이 모두 완료됐고, #18은 Play Console
> **2026-08-30 현황**: #24·#25·#27~#31·#34가 끝나 진행 중은 #18(콘솔 게이트) 하나뿐이다.
> 같은 날 대국 화면 UX 일감 다섯(#35~#39)이 사용자 지시로 들어왔고, **영향도가 낮은 순서로**
> 예정사항 맨 위에 놓았다 — #35(수순 이동) → #36(햅틱) → #37(착수 모드 스위치) → #38(보드 크기)
> → #39(돋보기, 출시 후순위). 스크린샷 재캡처와 AAB 빌드(#40)는 **그 뒤**다(사용자 지시) —
> 화면이 계속 바뀌는 중에 찍으면 또 낡기 때문이다. #26·#32·#33은 그 아래로 내려갔다.
>
> ⚠️ **#35는 #37의 선행 작업이다** — #37이 쓸 자리를 #35가 비운다. 순서를 바꾸지 말 것.

35. 대국 화면의 `수순 N수`를 **상단 타이틀 영역으로** 옮기기 (AI 모델: Sonnet, 노력정도: 낮음)
    - 2026-08-30 사용자 지시. **영향도가 가장 낮아 맨 앞에 둔다.**
    - 지금 `수순 2수`는 `ui/GameStatusPanel.kt`의 가운데 Column에서 **`착수` 버튼 바로 위**에 있다. 이걸 `ui/GameMenuSection.kt`의 `GameHeaderSection`(현재 빌드시각 / 대국 요약 / 메뉴 버튼 3칸)으로 옮긴다.
    - ⚠️ **#37의 선행 작업이다.** #37이 비우는 자리(`착수` 버튼 위)를 착수 모드 스위치가 차지하므로, 이 항목이 먼저 끝나야 한다.
    - ⚠️ 헤더 3칸은 가중치 `1:2:1`이고 가운데는 이미 배율 2.0배에서 3줄을 쓴다(#30). 수순을 그냥 얹으면 다시 빠듯해진다 — **좌측 빌드시각 칸(개발/QA용 9sp)** 과 자리를 나누는 쪽을 먼저 검토할 것.

36. **착수 이펙트** — 터치 다운 시 약한 진동 + 설정 on/off (AI 모델: Sonnet, 노력정도: 낮음)
    - 2026-08-30 사용자 지시. 진동만 다루는 **작은 항목**이라 앞쪽에 둔다(돋보기 확대는 #39로 분리).
    - 반상을 누르는 순간(터치 **다운**, 착수 확정이 아니라) 아주 약한 햅틱을 준다. 대국 메뉴의 설정 토글로 켜고 끌 수 있어야 한다.
    - 관련 파일: `ui/GoBoard.kt:146-158`(현재 `detectTapGestures`만 쓴다 — 다운 시점을 잡으려면 `onPress`/`awaitPointerEventScope`가 필요), 토글은 `ui/KaTrainUxPanels.kt`(`바로 착수`·`착수 표시` 등이 있는 그 격자), 상태는 `uxOptions`.
    - ⚠️ **`uxOptions`가 어디에 저장되는지 먼저 확인할 것.** `UserPreferencesSnapshot`의 자동저장 조립부에 배선되지 않은 필드는 저장 시점에 조용히 기본값으로 되돌아간다(#34에서 확인 — `hasSeenOnboarding`·`gameSetupUxMode`가 실제로 그렇다). 배선이 없으면 `UiLanguageStore`처럼 독립 저장소를 쓸 것.
    - ⚠️ 접근성/시스템 설정에서 햅틱을 끈 기기를 존중해야 한다. `HapticFeedbackType`(Compose) 또는 `view.performHapticFeedback`을 쓰면 시스템 설정을 자동으로 따른다 — `Vibrator`를 직접 부르면 그 존중이 깨진다.

37. **`바로 착수`를 착수 버튼 자리에서 직접 켜고 끄기** (AI 모델: Opus, 노력정도: 중간)
    - 2026-08-30 사용자 지시. **#35가 끝난 뒤 착수한다**(그 항목이 자리를 비워 준다).
    - 지금은 대국 메뉴(☰) 안에 들어가야 `바로 착수`를 켜고 끌 수 있다. 이걸 **`착수` 버튼 바로 위**에서 위/아래 슬라이드로 전환하는 스위치로 만든다. 전환 시 **반 바퀴 도는 듯한 이펙트**를 준다(사용자 예시).
    - 상태 대응: **바로 착수 모드** → 반상 탭이 곧 착수, `착수` 버튼은 **비활성**. **확인 착수 모드** → 탭은 자리만 고르고 `착수` 버튼이 **활성**.
    - 문구는 착수 시 확정한다(사용자 위임). 후보: `바로 착수` ↔ `확인 후 착수` / `선택 착수`. ⚠️ **`바로 착수`는 이미 메뉴에 있는 용어라 그대로 살리는 편이 낫다** — 같은 기능을 두 이름으로 부르면 안 된다. 4개 언어 모두 추가할 것.
    - 관련 파일: `ui/GameStatusPanel.kt`(가운데 Column), `ui/GamePlaySection.kt:126`(탭 분기), `ui/KaTrainUxPanels.kt:76`(기존 토글 — **남길지 없앨지 정할 것**. 두 곳에서 같은 값을 조작하게 되면 동기화는 자동이지만 UI가 중복된다).
    - ⚠️ 판 크기에 따라 권장 모드를 안내하는 `DirectPlayRecommendationDialog`가 이미 있다(9로는 바로 착수, 19로는 확인 착수 권장). 스위치를 노출하면 그 팝업과 말이 겹치는지 확인할 것.

38. **보드 크기 모드 2종** — 여백 있는 현재 크기 / 폭을 꽉 채우는 최대 크기 (AI 모델: Opus, 노력정도: 중간)
    - 2026-08-30 사용자 지시. 보드 **우측 상단**에 두 모드를 고르는 컨트롤을 둔다.
    - 지금은 한 가지뿐이다. `ui/GoBoard.kt:128-136`의 `BoxWithConstraints`가 `min(maxWidth, maxHeight)`로 정사각 변을 잡는데, 호출부가 `Modifier.fillMaxWidth()`이고 **화면 최상위 Column이 `padding(16.dp)`** 라(`ui/GoCoachContent.kt:121`) 좌우 16dp가 늘 빠진다 — 이것이 "외곽 여백 적용 사이즈"다.
    - ⚠️ **"최대 크기"는 그 16dp를 되찾는 것이라 간단하지 않다.** 보드가 부모의 패딩 안에 있으므로 음수 오프셋을 주거나(`offset`/`layout`), 보드만 패딩 바깥으로 끌어올려야 한다. 어느 쪽이든 **형세 오버레이·좌표 표시·탭 좌표 변환(`boardTapGeometry`)이 같은 폭을 봐야** 한다 — 하나만 어긋나면 돌이 손가락에서 밀린다.
    - 문구는 착수 시 확정한다(사용자 위임). 후보: ko `기본`/`최대`, en `Fit`/`Full`, ja `標準`/`最大`, zh `标准`/`最大`. 보드 모서리에 얹는 작은 칩이라 **짧아야 하고**, #29에서 확인했듯 전각 4자면 배율 2.0배에서도 안전하다.
    - ⚠️ 저장 위치는 #36과 같은 함정을 확인할 것(자동저장 조립부 배선 여부).
    - ⚠️ **스토어 스크린샷에 직접 영향을 준다** — 보드가 화면의 주인공이라 모드가 바뀌면 `03`·`04` 스샷이 통째로 달라진다. 기본값을 무엇으로 둘지 먼저 정할 것.

39. 바로 착수 모드의 **돋보기 드래그 착수** (AI 모델: Opus, 노력정도: 최대) — **출시 후순위(사용자 승인)**
    - 2026-08-30 사용자 지시. **난이도가 높으면 출시 뒤로 미뤄도 된다고 명시하셨다.**
    - 바로 착수 모드는 탭이 곧 착수라 오착수 위험이 있다. 손가락을 **누른 채 이동**하다 떼는 순간 착수하고, 누르는 동안에는 터치점 기준 **5×5 셀 반경이 확대**돼 어디에 놓이는지 보이게 한다. 떼면 확대 화면도 사라진다.
    - ⚠️ 지금 `GoBoard`는 `detectTapGestures`뿐이라(`ui/GoBoard.kt:153`) 드래그 추적 자체가 없다. `awaitPointerEventScope`로 다시 짜야 하고, 확대 레이어는 보드 Canvas 위에 별도로 그려야 한다.
    - ⚠️ **대국 화면 루트에 `verticalScroll`이 있다**(`ui/GoCoachContent.kt:120`). 보드에서 세로 드래그를 소비하기 시작하면 스크롤과 제스처가 충돌한다 — 지금은 탭만 쓰므로 문제가 없지만(#30에서 확인), 이 항목이 그 균형을 깬다.
    - #36(햅틱)과 짝이지만 **분리해서 발행했다** — 햅틱은 몇 줄이고 이쪽은 입력 파이프라인 재작성이다.

40. 스토어 스크린샷 재캡처 + 비공개 테스트 AAB 빌드·등록 (AI 모델: Sonnet, 노력정도: 중간)
    - **위 항목들이 정리된 뒤에 한다(2026-08-30 사용자 지시).** 화면이 계속 바뀌는 중에 찍으면 또 낡는다 — 실제로 8/17·8/29·8/30 세 번 반복됐다.
    - 현재 낡은 지점: `01_home.png`는 카드 3장이라 **개수는 다시 맞지만** 상단 칩이 `설정`/`언어`에서 `마이 페이지`/`설정`으로 바뀌었다(#34). 대국 화면 스샷은 걷어낸 1회권 재고 바(#24)와 옛 버튼 문구(#29)를 담고 있다. #38이 들어가면 보드 크기까지 달라진다.
    - 빌드는 `make play-internal-aab`(version.properties 자동 증가). 2026-08-30 기준 `VERSION_CODE=803`/`VERSION_NAME=0.8.3`.
    - 사용자 몫: Play Console 대시보드 3항목(개인정보처리방침 URL·IARC 등급·데이터 보안), AAB 업로드, 스크린샷·등록정보 교체, 테스터 12명 유지.

26. 프리미엄을 영구 구매에서 **월간 구독(3,900원)** 으로 전환 (AI 모델: Opus, 노력정도: 높음)
    - 참고: `feature-access-principles/README.md` 8장(이미 "프리미엄 = 월 구독"이 기조), `premium-mode/README.md`. **2026-08-30 사용자 확정** — 금액이 5,000~10,000원에서 **3,900원**으로 좁혀졌고, "기능이 늘면서 가치가 오르면 그때 가격도 조정" 방침.
    - **핵심 난점**: "구매 = 영구"가 타입 하나가 아니라 **상태·판정·복원·저장·문구 다섯 곳에 각각 하드코딩**돼 있다. `PremiumState.isActive`는 `Purchase -> true`라 만료를 표현할 수 없고, `PurchaseOutcome.Purchased`는 페이로드가 없어 유효기간을 실어 나를 수 없다.
    - ⚠️ **착수 순서를 지켜야 한다.** `runPremiumPurchaseApplication`은 "복원에서 미소유가 나와도 절대 강등하지 않는다"를 의도적 설계로 못박아 뒀다 — 영구 구매에서는 옳았지만 구독에서는 만료자가 영원히 프리미엄을 유지하는 결함이 된다. **그런데 그 강등을 그냥 켜면 안 된다**: `AndroidBillingClient`가 "조회 성공+미소유"와 "조회 실패"를 똑같이 `null`로 뭉개므로, 강등을 켜는 순간 **일시적 네트워크 오류가 유료 구독자의 접근권을 박탈한다.** 그 conflation을 쪼개는 것이 1번이다.
    - ⚠️ `AndroidBillingClient`는 프리미엄(SUBS 예정)과 봇 캐릭터(INAPP, #18)가 **공유**한다. ProductType을 클래스 안에서 전역 치환하면 캐릭터 구매가 조용히 깨진다 — 지금 그 플래그가 꺼져 있어 테스트로도 안 드러난다. 생성자 파라미터로 올릴 것.
    - ⚠️ `PremiumSource.Purchase`를 **개명하지 말 것.** 저장소 디코드가 모르는 이름을 소리 없이 `None`으로 떨어뜨려 기존 사용자의 프리미엄이 사라진다. 개명 없이 의미만 바꾸면 이 위험이 통째로 사라진다.
    - **버튼 표기(#24)**: 구독도 `(∞)` 유지를 권한다 — 체감이 무제한이고, `(⏱)`는 로비 카운트다운과 짝이라 구독에 주면 "1시간 남음"으로 오독된다. 다만 사용자 확정 사안이므로 착수 시 승인받을 것.
    - **신규 UI가 필요하다**: 구독 관리/해지 딥링크가 지금 앱에 **전혀 없다**(구글 정책 요구). 마이 페이지(#24로 신설됨)가 그 자리다.
    - **ⓐ는 해소됐다(2026-08-30 사용자 확인): 영구 프리미엄 보유자가 아무도 없다.** 지금까지 비공개 테스트만 했고, 설령 누가 갖고 있더라도 **비공개 테스트 중에 얻은 권한이라 실제 오픈 시 초기화까지 고려 대상**이라는 방침이다. → **기존 보유자 마이그레이션을 설계할 필요가 없다.** 이 항목에서 가장 까다로울 뻔했던 축이 통째로 사라진 셈이라, `PremiumSource.Purchase`의 의미를 "영구"에서 "구독 유효"로 **그대로 바꿔도 안전하다**(개명만 하지 않으면 된다).
      · ⚠️ 다만 **"초기화를 고려한다"와 "초기화한다"는 다르다.** 실제 오픈 시 테스트 기간 권한을 밀 것인지는 아직 확정이 아니다 — 밀기로 한다면 그 시점에 별도 일감이 필요하다(저장소의 `PremiumState`를 어떤 조건으로 리셋할지, 무르기 클레임 같은 **별개 축**은 남길지).
    - 착수 전 확인(사용자): **ⓑ Play Console 수익 창출 잠금**(개인정보처리방침·IARC·데이터 보안)이 풀려야 구독 SKU를 만들 수 있다 — 그 전에는 실기 검증 불가.
    - ⚠️ **스토어 등록정보는 배포 시점에 바꾼다.** `store_listing.txt`가 "앱 내 결제 없음"을 세 번 단언하는데, 코드가 머지돼도 플래그가 꺼져 있는 동안은 그 문장이 **참**이다. 미리 바꾸면 스토어가 거짓을 말하고, 배포 때 잊으면 정책 위반이 된다.

32. 봇 캐릭터의 **이름·설명이 한국어 하드코딩**이라 모든 외국어 사용자가 한글을 본다 (AI 모델: Opus, 노력정도: 중간)
    - #31 작업 중 발견(2026-08-30). #31이 `UiStrings`의 `copy()` 누락이었다면 이건 **도메인 데이터**가 애초에 한 언어뿐인 경우다.
    - `shared/.../botcharacter/BotCharacterCatalog.kt`의 캐릭터 5종이 `name`·`description`을 한국어 리터럴로 갖는다(`첫돌이`, `연습생 돌뫼`, `도장생 반상`, `사범 묘수`, `관장 천원` + 설명 5줄). `UiStrings`가 이 값을 4개 언어 문장에 그대로 끼워 넣으므로(`botCharacterLabel`, `botShardProgressLabel`, `botPurchasedToast`, 출석 보상 목록 등 8곳 이상) **영어 사용자도 한글 이름을 본다.** 실기 확인: 일본어 대국 설정에서 `관장 천원 (達人)`.
    - ⚠️ **번역을 `BotCharacterCatalog`에 넣지 마라 — 계층 위반이다.** 그 파일은 `shared`의 도메인이고 `UiLanguage`는 `app-android`의 UI 개념이다. `UiStrings` 쪽에 `botCharacterName(id)` / `botCharacterDescription(id)` 조회를 두고, 도메인의 `name`은 **디버그·로그용 식별자로 격하**하는 편이 맞는지부터 따져라.
    - ⚠️ #31이 넣은 회귀 그물은 이걸 **못 잡는다.** 한글이 `UiStrings` 필드가 아니라 도메인 데이터에서 흘러들기 때문이다. 고칠 때 그물을 어디까지 넓힐지 함께 정할 것.
    - 분량: 5종 × (이름 + 설명) × 3개 언어 = 30개 문자열. 캐릭터 이름은 말맛이 있는 고유명사라 **기계적 번역으로는 안 되고**(`첫돌이`, `사범 묘수`) 언어별로 새로 지어야 한다.

33. 학습 화면의 강좌 소개 3줄이 한국어 하드코딩 (AI 모델: Sonnet, 노력정도: 낮음)
    - #31 작업 중 발견(2026-08-30). `ui/StudyScreen.kt:59,64,69`의 `studyVideoEntries[].description`이 `UiStrings`를 거치지 않는다.
    - ⚠️ **단순 번역이 답인지가 먼저다.** 이 셋은 **한국어 유튜브 강의**(바둑에듀)라, 소개만 일본어로 바꾸면 못 알아들을 영상을 일본어로 소개하는 셈이 된다. 선택지: ⓐ 소개만 번역하고 "한국어 강의"임을 명시, ⓑ 언어별로 다른 영상을 고른다, ⓒ 비한국어에서는 학습 카드를 숨긴다. **제품 판단이 필요하다.**
    - 참고: 홈의 `homeStudySubtitle`("유튜브 기초 강좌로 바둑을 배워보세요")은 4개 언어가 이미 번역돼 있어, 카드를 눌러 들어가면 갑자기 한국어가 나오는 낙차가 있다.

---

## 관련 문서

- `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` — 이 백로그가 참조하는 설계/스펙 원본
- `README.md` — 아이디어 브레인스토밍, 상태 트래킹
