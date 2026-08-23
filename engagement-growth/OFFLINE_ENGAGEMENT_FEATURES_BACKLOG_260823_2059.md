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
6. **다음 스레드 안내** — 완료 처리 후에는 다음 예정사항 항목 번호만 짧게 언급하고 스레드를 마친다. 다음 항목 작업은 새 스레드의 몫이다.

**참고사항**

- `AI 모델` 표기는 스레드를 어떤 모델로 실행할지 사용자가 참고하는 값이다 — 스레드가 스스로 모델을 바꾸는 동작이 아니다.
- 이 백로그 갱신과 `docs/DOCS_INDEX.md` 갱신은 별개다. 항목을 완료할 때마다 `DOCS_INDEX.md`를 고칠 필요는 없다 — 그 문서는 새 파일이 생기거나 문서 구조 자체가 바뀔 때만 갱신한다.
- 동시에 여러 스레드를 이 백로그에 붙이지 않는다 — 순차 실행을 전제로 설계됐다.

---

## 주요 포커스해야할 내용 서두 정리

- 스코프는 킥오프 플랜과 동일하게 **Phase 1(로그인 없이 로컬 전용)**만 — 로그인 연동은 이 백로그에 없음.
- 이번 라운드 피드백으로 확정된 것: **AI 봇 캐릭터는 기존 난이도 선택 UI 위에 얹는 장식이 아니라, 그 UI를 완전히 대체한다.** 캐릭터를 고르는 행위 자체가 곧 AI 레벨(`PlayLevelGroup.FastBeginner`의 초보~초고수 5단계) 선정이 된다 — 킥오프 플랜 7.1절 참고. 대국 셋업 진입점을 건드리는 핵심 변경이라 후반부 항목(#10)에 노력정도를 높게 잡았습니다.
- 의존관계 (2026-08-24 갱신): **(a) 출석/보상 축** — #2~#5(완료) 이후 **#12(소모품 도메인) → #13(다중 보상 지급 + 봇 지급 배선) → #14(Claim 팝업) → #15(소모품 소비 배선)**로 이어진다. / **(b) 대국 히스토리** — #6~#7 완료, 독립적. / **(c) 봇 캐릭터 축** — #8(도메인, 완료) → #9(콘텐츠) → #10(진입점 개편) → #11(광고 획득). ⚠️ **(a)와 (c)가 이제 얽힌다**: 캐릭터를 실제로 지급하는 곳이 (a)의 #13이라, **#10은 #13 이후에만 의미가 있다.** 그래서 예정사항 목록은 9 → 12 → 13 → 14 → 15 → 10 → 11 순서로 배열돼 있다.
- **출석 보상 1~5일차 콘텐츠가 확정됐습니다**(2026-08-24, 킥오프 플랜 4.2절 표). 여기서 두 가지가 새로 파생됐고 각각 일감이 되었습니다: ① 한 일차에 **보상이 여러 개** 나올 수 있다(1일차부터 무르기+캐릭터 2개) → #13, ② 2~4일차 보상이 **쓰면 줄어드는 소모품**이라 기존 영구 클레임 구조로는 표현이 안 된다 → #12·#15. 지급 방식도 자동 지급이 아니라 **Claim 버튼** 방식으로 바뀌었습니다(5.1절) → #14.
- 아직 **미확정**인 것: 6·7일차와 14/21/28일차 보상, 남은 캐릭터 3종(중수·고수·초고수)의 획득 경로, '광고 스킵권' 1회의 범위, Claim 안 한 보상의 만료 여부. 해당 항목에 "착수 전 확인(사용자)"로 표기해 뒀으니 **스레드가 대신 정하지 말고 그 시점에 사용자에게 확인하세요.**
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

### 진행 중

(없음)

### 예정사항

> ⚠️ **번호는 식별자일 뿐, 실행 순서는 이 목록에 적힌 순서다.** 2026-08-24 사용자 피드백으로
> #12~#15가 새로 들어오면서 기존 #10·#11보다 먼저 처리해야 할 일이 생겼다(캐릭터 픽커 #10은
> 봇이 실제로 지급돼야 의미가 있는데, 그 지급 배선이 #13이다). 기존 번호는 완료 항목과 킥오프
> 플랜 각주에서 참조하고 있어 다시 매기지 않았다 — **새 스레드는 번호가 아니라 이 목록의 맨 위
> 항목을 가져간다**(현재 순서: 9 → 12 → 13 → 14 → 15 → 10 → 11).

9. 캐릭터 5종 콘텐츠 초안 — `FastBeginner` 그룹의 초보~초고수 5단계에 대응하는 이름·짧은 설명(아바타는 플레이스홀더) (AI 모델: Sonnet, 노력정도: 낮음)
   - 코드 작업이 아니라 콘텐츠 초안 제안 — 스레드가 후보를 제시하면 **사용자가 최종 확정**. #8 완료(2026-08-24)로 선행 조건 해소, #10 착수 전에 필요.
   - 확정된 전제: 5종은 전부 잠겨 있고 **1번째는 출석 1일차, 2번째는 5일차 보상**으로 열린다(4.2절). 이름/설명은 이 획득 순서(약한 상대 → 강한 상대)가 드러나게 잡는 편이 자연스럽다. 갈아끼울 자리는 `BotCharacterCatalog.kt`의 `placeholderName`/`placeholderDescription` 호출 5줄.

12. 단발성(소모성) 아이템 도메인·저장소 — `ConsumableItemId`/`ConsumableInventory`/`ConsumableStorePort` + 지급·차감 순수 로직 (AI 모델: Opus, 노력정도: 높음)
    - 참고: 4.5절(2026-08-24 신설). 2~4일차 보상('형세 보기'·'추천 수'·'광고 스킵권' 각 10개)이 쓰면 줄어드는 **소모품**이라, 영구 boolean 원장인 `PremiumState.claimedFeatures`/`FeatureId`에 얹을 수 없다 — 3장 원칙대로 별도 타입 + 별도 Port로 만든다. #8의 `BotCollectionState`와 같은 구조를 따르면 된다.
    - 착수 전 확인(사용자): **'광고 스킵권' 1회의 범위**(대국 1판인지 광고 1회인지)와 스킵 대상 광고 노출 지점. 이게 정해져야 차감 단위가 결정된다.

13. 출석 보상 정책 도메인 — 일차별 **다중 보상** 지급으로 확장 + 봇 캐릭터 지급 배선 (AI 모델: Opus, 노력정도: 높음)
    - 참고: 4.2절 보상 정책표(2026-08-24 확정). 한 일차에 보상이 여러 개 나올 수 있으므로(1일차부터 2개) `AttendanceReward` sealed 타입 + "일차 → 보상 목록" 정책표를 만들고, #4에서 만든 `runAttendanceRewardGrant`(1일차 무르기 하나만 지급)를 그 위로 확장한다.
    - 보상 3종을 모두 지급할 수 있어야 하므로 **#8(봇 캐릭터)·#12(소모품) 완료 후 착수.** 1일차=무르기+첫 캐릭터, 5일차=두 번째 캐릭터 배선이 여기서 완성된다.

14. 출석 Claim 팝업 UI — 오늘 받을 보상 목록 표시 + `Claim` 버튼으로 지급 (AI 모델: Sonnet, 노력정도: 중간)
    - 참고: 5.1절(2026-08-24 갱신). **자동 지급 → Claim 방식으로 전환**하는 항목이라, #4의 "확인 팝업 없는 자동 지급"과 #5의 최초 실행 화면(`ui/FirstLaunchRewardScreen.kt`)을 이 흐름으로 개편한다(새로 만들지 말고 기존 화면을 살릴 것).
    - Claim 하지 않고 닫으면 미지급으로 남아 다음 실행에 다시 떠야 한다. **#13 완료 후 착수.**
    - 착수 전 확인(사용자): 밀린 과거 일차 보상의 만료 여부와, 여러 일차가 밀렸을 때 한 팝업에 모아 보여줄지.

15. 단발성 아이템 소비 배선 — 형세 보기/추천 수/광고 스킵권 실제 차감 + 잔량 표시 (AI 모델: Opus, 노력정도: 높음)
    - 참고: 4.5절. #12에서 만든 재고를 실제 기능 사용 지점에 연결한다 — 이게 없으면 2~4일차 보상이 지급만 되고 쓸 수 없다.
    - ⚠️ **프리미엄 게이팅과의 우선순위**: 프리미엄이 활성인 동안에는 소모품을 쓰지 않고 통과시켜야 잔량이 억울하게 닳지 않는다(`FeatureAccessPolicy` 수정 필요). **#12·#13 완료 후 착수.**

10. AI 레벨 선택 진입점을 캐릭터 선택 UI로 전면 개편 — 기존 `PlayerSetupPanel.kt`의 `FastBeginner` 난이도 선택 UI를 캐릭터 픽커로 대체 (AI 모델: Opus, 노력정도: 최대)
    - 참고: 7.1절. 기존 대국 셋업 UX를 직접 건드리는 핵심 변경이라 노력정도 최대. **#8·#9에 더해 #13(봇 지급 배선) 완료 후 착수** — 지급이 안 붙은 상태로 픽커를 만들면 고를 수 있는 캐릭터가 하나도 없다.
    - ⚠️ **빈 상태 처리 필수**: 5종이 전부 잠금이라(2026-08-24 확정) 아직 아무것도 획득하지 않은 사용자에게 픽커가 어떻게 보일지 반드시 설계할 것. `BotCollectionState.isAvailable`에 같은 경고가 달려 있다.

11. 광고 시청 → 봇 캐릭터 영구 획득 배선 — 기존 `AdRewardPort` 재사용, 시간제 활성화 아님 (AI 모델: Sonnet, 노력정도: 중간)
    - 참고: 7장. #8·#10 완료 후 착수.
    - 현재 카탈로그에서 **3~5번째 캐릭터(중수·고수·초고수)가 임시로 `AdWatch`로 잡혀 있다** — 이들을 광고로 줄지 6일차 이후/14·21·28일차 출석 보상으로 줄지 확정되면 이 항목의 범위가 정해진다(4.2절 미확정 행).

---

## 관련 문서

- `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` — 이 백로그가 참조하는 설계/스펙 원본
- `README.md` — 아이디어 브레인스토밍, 상태 트래킹
