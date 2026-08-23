# 오프라인 출석 보상 · 대국 히스토리 · 업적 화면 — 개발 착수 스펙 (Phase 1: 로그인 없이 로컬 전용)

작성일: 2026-08-23

**이 문서는 새 스레드에 그대로 프롬프트로 넘겨 바로 개발에 착수할 수 있도록 쓴 개발 명세입니다.** `engagement-growth/README.md`의 아이디어 #1(출석 보상)·#4(AI 캐릭터화)·#7(업적)을 구체화한 문서이며, **Phase 1(로그인 없이 로컬 전용)만 다룹니다.** 로그인 연동(Phase 2)은 의도적으로 범위에서 뺐고, 대신 Phase 1의 모든 설계가 나중에 Phase 2로 자연스럽게 확장되도록 아키텍처 제약(3장)을 명시했습니다.

---

## 1. 배경

- 제품 방향성(사용자 지정): **간결성, 빠른 접근 체험을 통한 점진적 허들 넘기기.** 이 원칙이 아래 모든 설계에 적용됩니다 — 스트릭 강제 없음, 페널티 없음, 보상은 자연스럽게 쌓이는 형태.
- 배경 문서: `engagement-growth/README.md`(2~4장, chess.com 참고 사례와 이 앱의 현재 조건 정리) — 이 스펙을 시작하기 전에 먼저 읽으면 맥락이 잡힙니다.

## 2. 스코프

| 구분 | 범위 |
| --- | --- |
| **Phase 1 (이 문서)** | 로그인 없이, 게스트 상태에서 전부 동작. 로컬 저장만 사용(기존 Port + SharedPreferences-JSON 어댑터 패턴 재사용) |
| **Phase 2 (범위 밖, 설계만 고려)** | 로그인 연동 시 로컬 데이터를 계정에 귀속시키는 동기화/마이그레이션. 지금 구현하지 않되, 나중에 같은 Port에 어댑터만 추가하면 되도록 설계할 것 |

재설치 시 로컬 데이터가 초기화될 수 있음을 사용자에게 고지하는 원칙은 이미 `feature-access-principles/README.md` 1장·6장에 있습니다 — 새 기능 3종(출석/히스토리/봇 컬렉션) 모두 이 원칙을 그대로 따릅니다. 구체적인 고지 문구/화면은 구현 단계에서 정합니다.

## 3. 아키텍처 제약 (반드시 지킬 것)

이 앱은 로컬 저장에 이미 확립된 패턴이 있습니다 — **새로 발명하지 말고 그대로 따르세요.**

- 도메인 타입 + `XxxStorePort` 인터페이스는 `shared`에, 실제 저장 어댑터는 `app-android/.../persistence`에 둔다. 참고 예시: `UserPreferencesStorePort`/`UserPreferencesStore`, `SavedGameStorePort`/`GameSessionStore`.
- 각 저장 데이터는 SharedPreferences에 **키 하나당 JSON blob 하나**로 저장하고, `schema: Int` 버전 필드를 포함한다(`UserPreferencesCodec` 참고).
- 아래 3개 신규 기능은 **서로 다른 Port로 분리**한다 — 하나의 거대한 blob에 다 우겨넣지 않는다.
- Phase 2 확장을 위해 도메인 모델(data class)은 플랫폼/저장소 비종속으로 `shared`에 두고, UI·저장 로직과 분리한다(`docs/ARCHITECTURE.md` 7계층 원칙).

**이 원칙을 지키면 Phase 2는 "같은 Port를 구현하는 Firestore 어댑터 추가"만으로 끝납니다 — 지금 이 구조를 어기면 나중에 재설계가 필요합니다.**

---

## 4. 기능 1 — 출석 체크인 & 보상

### 4.1 체크인 규칙

- 앱이 **cold start** 되거나 **foreground로 복귀**할 때마다 트리거된다.
  - ⚠️ 현재 앱에는 `Application` 서브클래스도, lifecycle 옵저버도 전혀 없다(코드베이스 확인 완료, 2026-08-23) — `MainActivity.onCreate`만 있음. 이 훅은 **완전히 새로 추가**해야 한다. 권장: `Application` 서브클래스 신설 + `ProcessLifecycleOwner` 옵저버 등록(`AndroidManifest.xml`에 `android:name` 등록 필요) — cold start와 백그라운드 복귀를 한 곳에서 정확히 잡을 수 있다.
- **UTC 캘린더 날짜** 기준으로 "오늘 이미 체크인했는지" 판정한다. 하루 최초 1회만 `attendanceCount`를 증가시키고, 같은 UTC 날짜 내 재실행은 무시한다.
- **연속 출석 요구 없음.** 며칠을 건너뛰어도 다음 방문 시 `attendanceCount`는 그냥 1 증가한다 — 스트릭 리셋 로직 자체가 없다. (제품 원칙 1장에 부합)

**예시** (사용자가 준 원문 예시 문장이 다소 불명확해 아래처럼 재정리했습니다 — 의도와 다르면 알려주세요):

| 실제 방문 순번 | 방문 달력일(UTC) | `attendanceCount` | 지급 보상 |
| --- | --- | --- | --- |
| 1 | 8/1 | 1 | 1일차 보상 |
| — (8/2~8/3 미접속) — | | | |
| 2 | 8/4 | 2 | 2일차 보상 |
| 3 | 8/4 (같은 날 재실행) | 2 (변화 없음) | 지급 없음 |
| 4 | 8/5 | 3 | 3일차 보상 |

보상 티어 매핑: `attendanceCount` 1~7은 해당 회차 보상을 그대로 지급. 8 이상은 **7의 배수(14, 21, 28, ...)에서만** 지급 — 그 사이(8~13, 15~20 등)는 보상 없음.

### 4.2 보상 내용 (Phase 1)

- **1일차: 무르기 무제한 권한 즉시 부여** (기존 `FeatureId.Undo` claim 재사용 — 4.4절 참고)
- 2~7일차 및 14/21/28일차: 구체 보상 내용은 **이 스펙에서 미확정**. 후보는 봇 캐릭터 언락(7장과 연동), 코스메틱 등 — **구현 착수 전 사용자와 확정 필요**(열린 질문, 10장).

### 4.3 저장 데이터

```
AttendanceState(
  schema: Int,
  attendanceCount: Int,
  lastCheckInUtcDate: String,   // ISO date, "yyyy-MM-dd"
  claimedTiers: Set<Int>,
)
```

- 새 Port: `shared/.../application/attendance/AttendancePorts.kt` → `AttendanceStorePort { fun load(): AttendanceState; fun save(state: AttendanceState) }`
- 새 어댑터: `app-android/.../persistence/AttendanceStore.kt` — 기존 두 스토어와 동일한 SharedPreferences-JSON 패턴, 새 키 사용.

> **구현 결정(백로그 #3, 2026-08-23)**: `lastCheckInUtcDate: String`(ISO 날짜) 대신 `lastCheckInUtcDay: Long`(UTC 하루 인덱스, `epochMillis / 86_400_000`)으로 구현했다. `shared`는 플랫폼 독립적이어야 하는데(3장) 이 프로젝트엔 날짜 파싱/포맷 라이브러리(kotlinx-datetime 등)가 없어, 문자열 대신 정수 나눗셈만으로 "오늘 이미 체크인했는가"를 판정하게 한 결정 — 동작은 스펙과 동일하다.

### 4.4 기존 "무르기 클레임" 플로우 변경

- **현재 동작**: `app-android/.../ui/GamePlaySection.kt`의 `GameActionButtons`(약 310행)에서 Undo가 Locked 상태일 때 첫 탭 시 `showUndoClaimDialog` → `AlertDialog` 확인 → `premium.claim(FeatureId.Undo)` 호출(`LocalPremiumUiState.current` 경유).
- **변경 목표**: 1일차 출석 보상이 지급되는 시점(=앱 최초 실행 직후, 게임 플레이 이전)에 이 `claim(FeatureId.Undo)`를 미리 호출해 무르기가 항상 Allowed 상태이도록 만든다. 이후 `GamePlaySection.kt`의 클레임 다이얼로그 분기는 도달 불가능해지므로 **제거하거나 방어적 폴백으로만 남긴다**(출석 시스템이 어떤 이유로든 실패했을 때 대비) — 어느 쪽을 택할지는 구현 시 판단.
- ⚠️ **확인 필요**: `claim()`이 지금 `LocalPremiumUiState.current`를 통해서만(Compose UI 컨텍스트) 호출 가능한지, 아니면 application 레이어에 UI 없이도 호출 가능한 함수가 있는지 `application/premium/PremiumState.kt`·`FeatureAccessPolicy.kt`를 먼저 확인할 것. 업적 화면이 뜨는 시점(앱 최초 실행 직후)은 아직 게임 화면 Compose 트리가 없을 수 있으므로, UI 트리에 의존하지 않는 application-layer 진입점이 필요할 가능성이 높다.

> **구현 결정(백로그 #4, 2026-08-23)**: 위 "확인 필요"의 답은 **없었다** — 클레임 규칙은 `ui/GoCoachApp.kt`가 조립하는 `PremiumUiState.claim` 람다 안에만 있었고(Compose `CompositionLocal` 경유), application 계층에는 진입점이 없었다. 그래서 다음과 같이 구현했고, 스펙 문구와 달라진 부분은 아래와 같다.
>
> 1. **UI-비의존 진입점 신설**: `shared/.../application/premium/PremiumFeatureClaimApplication.kt`의 `runPremiumFeatureClaim(featureId, store)` — `PremiumStateStorePort`만 있으면 어디서든(Application 코루틴 포함) 호출 가능하다. UI의 `claim` 람다도 이 함수를 호출하도록 바꿔 규칙이 두 벌로 갈라지지 않게 했다.
> 2. **지급 조건을 이벤트가 아니라 상태로 판정**: "방금 체크인 결과가 `rewardTier == 1`인가"가 아니라 "출석한 적이 있는데(`attendanceCount >= 1`) 1일차 보상이 아직 미지급인가(`!isTierClaimed(1)`)"로 판정한다(`runAttendanceRewardGrant`). 최초 실행 때 지급이 실패해도 다음 실행에서 스스로 복구되게 하려는 것으로, 정상 흐름의 결과(첫 실행에 1회 지급)는 스펙과 동일하다.
> 3. **클레임 다이얼로그는 제거하지 않고 방어적 폴백으로 유지**: (a) 자동 지급이 foreground 이벤트를 타는 비동기 경로라 유실 가능성이 0이 아니고, (b) `PremiumStateStore.load()`가 기기 시계 이상 등으로 기본값 폴백하면 이미 받은 클레임이 사라지는데 출석 쪽은 "지급 완료"로 기록돼 있어 자동 재지급이 안 된다. 이 두 경우에 무르기를 영영 못 쓰는 것보다 도달 확률이 낮은 팝업을 남기는 편이 안전하다고 판단했다(`GamePlaySection.kt`에 같은 주석 표기).
> 4. **클레임 원장 병합 저장**: 구매/광고/복원/QA 토글 저장 시 화면이 메모리에 들고 있던 `claimedFeatures`를 이어붙이면, 그 사이 화면 밖에서 지급된 클레임이 지워진다. `saveMergingClaimedFeatures`로 저장 직전에 저장소 값과 합치도록 바꿨다(기존 구매 복원 경로에 있던 클레임 유실도 함께 해소).
> 5. **부수 리팩토링**: 위 변경으로 `GoCoachApp.kt`가 라인 예산(850, `LayeringContractTest`)을 넘겨, 프리미엄 배선 조립을 `ui/PremiumUiState.kt`의 `buildPremiumUiState(...)`로 옮겼다(`PremiumPurchaseGlue.kt`와 같은 선례). 결과적으로 셸은 850 → 835줄로 줄었다.
>
> **알려진 한계**: 자동 지급이 Compose 첫 구성보다 늦게 도착하면 그 세션 동안 화면은 무르기를 잠김으로 표시한다(다음 실행부터 정상). 이 경우에도 위 3번 폴백으로 즉시 사용 가능하다. 5장 업적 화면(#5)이 붙으면 지급 사실이 첫 실행에 바로 노출되므로 체감 문제는 더 줄어든다.

---

## 5. 기능 2 — 업적/보상 화면 (최초 실행 시 노출)

- 앱 **최초 실행(첫 cold start)** 시, 게임 화면 진입 전에 업적/출석 보상 화면을 먼저 보여준다. 1일차 보상(무르기 무제한)이 **이미 지급된 상태**로 표시한다 — "획득하시겠습니까?" 확인 없이 "획득!" 결과만 보여준다.
- 최초 실행 판정: `AttendanceState`가 아직 없을 때(`attendanceCount == 0`)를 최초 실행으로 볼지, 별도 `hasSeenOnboarding` 플래그를 둘지는 구현 시 결정.
- 삽입 위치: `MainActivity.kt`의 `setContent` 진입점에서, 엔진 부트스트랩(`LaunchedEffect`)과 게임 화면 사이에 조건부로 넣는다.
- Phase 1 UI 범위: 오늘 받은 보상 결과 + 지금까지 획득한 것들의 간단한 목록. 화려한 연출/디테일 UX는 스코프 밖(추후 별도 논의).

> **구현 결정(백로그 #5, 2026-08-24)**:
> 1. **최초 실행 판정에 `hasSeenOnboarding`을 재사용하지 않았다.** 확인해보니 그 플래그는 로그인 온보딩 전용이고, `FeatureFlags.isLoginEnabled = false`인 지금은 `initialDestination()`이 무조건 `ScreenDestination.Home`으로 직행해 그 온보딩 화면 자체가 죽은 경로다. 대신 `AttendanceState.attendanceCount == 0`(출석 기록이 한 번도 없음)을 그대로 최초 실행 판정으로 썼다 — 4장에서 이미 만든 개념이라 새 플래그가 필요 없다.
> 2. **삽입 위치를 `MainActivity.kt`가 아니라 `ui/GoCoachApp.kt`의 `GoCoachScreen`으로 바꿨다.** `MainActivity`는 엔진 부트스트랩만 다루고, 저장소 인스턴스 생성·`ScreenDestination` 라우팅은 전부 `GoCoachScreen`에 있어 그쪽이 자연스러운 자리였다. 다만 이 파일은 `LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`이 라인(850)·상태훅(46) 예산을 강제하고 있어, 실제 로직/상태는 전부 새 파일 `ui/FirstLaunchRewardScreen.kt`의 `rememberFirstLaunchRewardGate(context)`로 옮기고 `GoCoachScreen`에는 호출 한 줄 + 조건부 early return만 남겼다(`buildPremiumUiState`와 동일 패턴).
> 3. **보상 지급을 기다리지 않고 즉시 화면을 보여준다.** 1일차 보상 내용은 이미 확정(무르기 무제한)이라 결과가 결정론적이므로, `runAttendanceRewardGrant` 완료를 기다리는 로딩 상태 없이 바로 표시한다. 그 사이 지급이 실패해도 다음 실행에서 스스로 복구되고, 4.4절의 방어적 폴백 다이얼로그가 남아 있다.

---

## 6. 기능 3 — 대국 히스토리 (대국 기록 목록)

- 목적: 사용자가 뒀던 모든 대국을 체계적으로 저장하고 "최근 대국" 목록으로 탐색 가능하게 한다. **Phase 1은 단순 리스트 표시만** — 기보(수순) 리플레이/재분석은 다음 단계(범위 밖, 데이터만 안 버리게 설계).
- ⚠️ 기존 `SavedGameSnapshot`/`SavedGameStorePort`(`app-android/.../persistence/GameSessionStore.kt`)는 **"진행 중인 대국 1개 이어하기"** 전용이며 이 기능과 다른 개념이다 — **재사용하지 말고 별도의 새 Port를 만들 것.**
- 새 Port: `shared/.../application/gamehistory/GameHistoryPorts.kt` → `GameHistoryStorePort { fun appendCompletedGame(entry: GameHistoryEntry); fun loadAll(): List<GameHistoryEntry> }`
- `GameHistoryEntry` 최소 필드(Phase 1 표시용): `id`, `playedAtMillis`, `boardSize`, `scoringRule`(Area/Territory), `komi`, `handicap`, `opponent`(AI 레벨/봇 캐릭터 id — 7장과 연동), `result`(승/패, 집수 차).
- **미래 확장 대비**: 대국의 수순(move list)은 이미 엔진 통신용 `GameState`(shared)에 존재한다. 저장 시 함께 넣을지 요약만 저장할지는 구현 시 판단하되, 스키마에 `moves: List<Move>?`처럼 나중에 채울 자리를 비워두는 걸 권장한다(Phase 1엔 null/생략) — 나중에 필드를 추가할 때 기존 저장 데이터 마이그레이션 부담을 줄이기 위함.
- **열린 질문**: SharedPreferences 단일 JSON blob에 대국이 무한히 쌓이면 크기가 계속 커진다. 최대 보관 개수 제한(예: 최근 200판)이 필요한지는 구현 시 판단하고 이 문서(또는 후속 결정 로그)에 남길 것.

---

## 7. 기능 4 — AI 봇 캐릭터 & 컬렉션

- `PlayLevel.kt`의 `PlayLevelGroup`/티어 이름(초보~초고수 등)은 현재 순수 기능적 표기이며 시각적 캐릭터 개념이 전혀 없다 — **이번이 최초 도입.**
- 새 도메인 타입(shared): `BotCharacter(id: BotCharacterId, name, avatarRef, linkedPlayLevel: PlayLevelGroup, tierWithinGroup: Int?, unlockSource: BotUnlockSource)` — 기존 `PlayLevelGroup`을 대체하지 않고 그 **위에 프레젠테이션 레이어를 씌우는** 방식이다. 난이도/AI 강도 로직은 그대로 둔다.
- 새 컬렉션 상태: `claimedBots: Set<BotCharacterId>` — `FeatureId`/`claimedFeatures`와 구조는 비슷하지만 도메인이 다르므로(기능 토글이 아니라 캐릭터 수집) **별도 타입으로 분리**한다. `PremiumState`에 얹을지 새 `BotCollectionState`로 분리할지는 `docs/ARCHITECTURE.md` 7계층 원칙에 맞춰 구현 시 판단.
- 획득 경로 (Phase 1):
  1. 출석 보상(4장과 연동)
  2. 광고 시청 — 기존 `AdRewardPort.showRewardedAd()` 트리거는 재사용하되, **결과는 영구 획득(claim)이어야 한다.** 기존 "1시간 임시 활성화" 패턴(`adGrantStartedAtMillis`, `PremiumState.AdGrantDurationMillis`)을 그대로 재사용하지 않도록 주의 — 그건 시간제 기능 해금용이고, 봇은 한 번 얻으면 영구 소유다.
- **범위 밖(설계만 고려, 지금 구현 안 함)**: 봇 캐릭터 개별 구매, 월 구독으로 전체 봇/프리미엄 기능 이용. 나중에 상품이 추가될 걸 감안해 `BotUnlockSource`를 sealed class(예: `Attendance(tier)`, `AdWatch`, `Purchase`(향후), `Subscription`(향후))로 열어두는 정도만 지금 반영한다.

### 7.1 진입점 UX — 캐릭터 선택이 곧 AI 레벨 선택이다 (2026-08-23 갱신)

기존 대국 셋업(`GameSetupLobby`/`PlayerSetupPanel` 등)의 "AI 난이도 선택"과 새 "봇 캐릭터 선택"은 **별개 UI로 공존하지 않는다.** 캐릭터 하나하나가 `PlayLevelGroup` + 티어 하나에 1:1로 대응하므로, **캐릭터를 고르는 행위 자체가 곧 AI 레벨을 정하는 것**이다 — 즉 현재 있는 난이도 dropdown/선택 UI를 캐릭터 픽커로 **대체**한다(위에 얹는 게 아니라 교체). 5단계(초보~초고수) 각각이 캐릭터 하나씩이라, `FastBeginner` 그룹의 5개 티어를 우선 캐릭터화하는 것이 자연스러운 시작점이다(다른 그룹 `초급`/`중급`/`고급`은 코드 보존, 대국장 로드맵 예정 — `docs/DOCS_INDEX.md`의 관련 항목 참고, 지금 이 작업의 범위는 아니다).

---

## 8. Phase 2(로그인 연동) — 지금 하지 않지만 반드시 고려할 것

- 위 3개 Port(출석/히스토리/봇 컬렉션) 모두 인터페이스가 `shared`에 있고 구현체만 `app-android/persistence`에 있으므로, 나중에 로그인이 켜지면 같은 Port를 구현하는 Firestore 어댑터를 추가하거나, 로컬 데이터를 그 시점 계정으로 1회 마이그레이션하는 함수만 추가하면 된다.
- 로그인 재활성화와 Firestore 저장은 항상 같이 간다는 게 이미 내려진 결정이다(`feature-access-principles/README.md` 5장 결론) — Phase 2 착수 시 그 문서부터 다시 읽을 것.

---

## 9. 이번 스코프에서 명시적으로 제외하는 것

- 로그인/계정 연동, 서버 동기화
- 기보 리플레이/재분석 UI (데이터 자리만 마련)
- 봇 캐릭터 구매/구독 상품 (도메인 타입에 자리만 마련)
- 보상 콘텐츠(2~7일차, 14/21/28일차)의 구체 내용 확정
- 알림/스트릭 경고 (별도 트랙 — `engagement-growth/README.md` 4장 #6/#9)

---

## 10. 열린 질문 (착수 전 확인 권장)

- [ ] 4.1절 체크인 예시 재해석이 사용자 의도와 맞는지
- [ ] 2~7일차 및 14/21/28일차 보상 내용
- [ ] `claim()`을 UI 컨텍스트 밖에서도 호출 가능하게 만드는 리팩토링이 필요한지(4.4절)
- [ ] 대국 히스토리 보관 개수 제한 여부(6장)
- [ ] `BotCollectionState`를 `PremiumState`에 통합할지 분리할지(7장)

---

## 관련 문서

- `engagement-growth/README.md` — 이 스펙의 배경이 된 아이디어 브레인스토밍
- `feature-access-principles/README.md` — 로그인 없이/기기 로컬 저장 원칙, 재설치 초기화 고지 원칙
- `premium-mode/README.md` — 기존 광고 보상·클레임 구현 로그
- `docs/ARCHITECTURE.md` — 7계층 원칙(도메인/저장 계층 분리 근거)
