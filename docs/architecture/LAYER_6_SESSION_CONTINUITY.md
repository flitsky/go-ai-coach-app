# 6계층 — Session & Continuity

go-ai-coach에서 이 계층이 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「6계층 — Session & Continuity」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「6계층」 절과 「알려진 갭」·「고도화 로드맵」의 6계층 항목을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 계층 배정

아래 블록은 `ContractSymbols.kt`의 `LAYER_6_PACKAGES`를 그대로 옮긴 **사본**이다(접두 `com.worksoc.goaicoach.` 생략). 정본은 코드 쪽이고, `LayerDocContractTest`가 둘이 양방향으로 같은지 본다 — 패키지를 새로 만들거나 옮기면 코드 목록을 고치고, 빨개지면 이 블록을 따라 고친다.

<!-- layer-packages: LAYER_6_PACKAGES -->
```text
application.auth.state
application.premium.state
application.premium.app
application.attendance
application.consumable
application.botcharacter
application.lifecycle
application.device
application.profile
application.rankmeasure
application.dailyquote
application.dailydraw
```

## 지금 위치

**위치**: 배정은 **패키지 단위**이고, 이 계층의 패키지는 아래 12개가 전부다 — `auth.state`·`premium.state`·`premium.app`·`attendance`·`consumable`·`botcharacter`·`lifecycle`·`device`·`profile`·`rankmeasure`·`dailyquote`·`dailydraw`(2026-09-25 기준 32파일, `rankmeasure`는 2026-10-06 백로그 #219로, `dailyquote`는 2026-10-21 백로그 #273으로, `dailydraw`는 2026-10-23 백로그 #283으로 더했다). 포트는 여기 없다. `auth/port/AuthClientPort.kt`와 `premium/port/*`는 `#29`(2026-09-24)부터 따로 떨어진 4계층 α 패키지다(`LAYER_4_EXTERNAL_INTEGRATION.md`). 클러스터 패키지 안에 선언된 `attendance/AttendancePorts.kt`·`consumable/ConsumablePorts.kt`·`botcharacter/BotCharacterPorts.kt`·`device/DeviceIdentityPorts.kt` 4개도 4계층 α다(파일 단위 예외). `rankmeasure/RankMeasurePorts.kt`·`dailydraw/DailyDrawPorts.kt`도 같은 결이다. 2026-09-24까지 이 목록에는 앞의 두 상태 패키지만 있었고, 2026-09-25에 나머지 여섯을 더했다(`LAYER_5_APPLICATION_DOMAIN.md` 「2026-09-25 재분류」).
- `shared/.../application/auth/state/AuthState.kt` — 로그인 상태 순수 모델
- `shared/.../application/premium/state/PremiumState.kt` — 프리미엄 활성화 상태. 필드는 `source`(None/AdGrant/Purchase) + `adGrantStartedAtMillis` + `claimedFeatures: Set<FeatureId>` + `lastSubscriptionVerifiedAtMillis`. ~~`matchGeneration`으로 5계층의 `sessionGeneration`과 **의도적으로 분리**돼 있다~~ — **2026-09-25 확인: 사실이 아니다.** `PremiumState`에는 세대 필드가 없다. 광고 보상의 수명은 `adGrantStartedAtMillis`부터 1시간(그 사이 대국을 몇 판 하든 무관)이라 어떤 세대 카운터에도 묶이지 않는다. 2026-08-04에 광고 보상이 *"대국 1판 한정"* 에서 순수 1시간 타이머로 바뀌면서 `PremiumState`의 `adGrantMatchGeneration`이 지워졌다(`work/plans/PREMIUM_MODE.md` 「결정 번복」). 2026-07-30에 *"무르기 시 프리미엄이 풀리는 버그"* 를 고친 그 분리가 이때 필요 없어졌다. `matchGeneration` 자체는 부기 값으로 5계층 `application/contract/GameSessionRuntimeState.kt`에 남겼다. 새 대국마다 1씩 오르기만 하고 프로덕션에서 읽는 곳은 0이다(테스트 `GameSessionCoreStateTest` 1파일만 본다). 그래서 원칙 문서 6계층 절의 *"세대 카운터를 섞지 말라"* 는 이 앱에서 지금 적용할 자리가 없다. **260814**: 예전엔 무르기 하나만을 위한 `isUndoClaimed: Boolean`이었으나, 앞으로 다른 기능도 같은 방식(초도 클레임+그랜드파더링)으로 무료 제공할 때 새 불리언을 또 추가하지 않도록 기능별 원장(`claimedFeatures`)으로 일반화했다. `source`/`adGrantStartedAtMillis`(구독형 축)와 `claimedFeatures`(1회 클레임형 축)는 여전히 서로 다른 축으로 분리돼 있다 — 위 문단이 이미 겪은 "축 혼동 버그"를 반복하지 않기 위함.
- **(260814 신설)** `shared/.../application/premium/state/FeatureAccessPolicy.kt` — 기능별 접근 정책 판정. `PremiumState`(바로 위 항목, 6계층 — "지금 무엇을 갖고 있는가")를 입력으로 받아, 기능 하나(`FeatureId`)에 대해 "지금 쓸 수 있는가, 없다면 무엇으로 풀 수 있는가"를 순수 함수로 판정한다 — `fun resolve(featureId: FeatureId, state: PremiumState, nowMillis: Long): FeatureAccess`(`FeatureAccess` = `Allowed(via: AllowedVia)` | `Locked(unlockOptions: Set<UnlockOption>)`). `PremiumState`를 입력으로 받으므로 6계층 소속이다(5계층은 6계층을 몰라야 함) — `PremiumState.isActive()`가 이미 같은 이유로 `PremiumState` 자신에 있는 선례를 따름. `ui/play/GamePlaySection.kt`의 `featureGated(access, action)`·`ui/setup/KaTrainUxPanels.kt`의 `moveReviewAllowed`가 이 판정을 소비한다(둘 다 더 이상 `isActive`/클레임 여부를 직접 조합하지 않는다).
- **(2026-09-25 편입)** 엔타이틀먼트 클러스터 — 6계층 상태를 전이·저장하거나 6계층 값을 싣는 흐름과 정책. 전부 `shared/.../application/` 아래다.
  - `premium/app/`(5파일) — 광고 보상(`runPremiumAdGrantApplication`), 구매(`runPremiumPurchaseApplication`), 해지 진단(`buildPremiumDeactivatedDiagnosticEvent`), 기능 클레임(`runPremiumFeatureClaim`), 구매 실패 안내(`PurchaseFailureNotice`). `PremiumState`를 받아 다음 값을 돌려주거나 저장한다.
  - `attendance/`(7파일) — 출석 상태·체크인·보상 정책표·보상 지급. 보상 지급(`runAttendanceRewardGrant`)이 `premium.app`·`consumable`·`botcharacter`의 지급 흐름을 부른다.
  - `consumable/`(4파일) — 소모품 재고·카탈로그와 차감 판정(`decideConsumableSpend` — `FeatureAccessPolicy`로 이미 열린 기능이면 차감하지 않는다)·지급·차감 흐름.
  - `botcharacter/`(8파일) — 봇 캐릭터 카탈로그·수집 상태·획득·조각 적립·레벨 클램프·유료 캐릭터 혜택. 대국 설정 값(`shared.policy`의 `PlayLevelSetting`, `match`의 `PlayerSetup`)을 참조한다 — 6→5, 허용 방향이다.
  - `lifecycle/`(2파일) — 정식 릴리즈 초기화(`ReleaseResetPolicy` — 권한 데이터가 있는지 본다)와 개발자 모드 주기 초기화(`DeveloperModeResetPolicy`) 판정. 이 설치에 쌓인 상태를 언제 지울지 정한다.
  - `device/`(2파일) — 이 설치의 로컬 식별자 `DeviceIdentity`와 그 포트. 소비하는 다중 기기 정책은 아직 없다.
  - `rankmeasure/`(2파일, 2026-10-06 백로그 #219로 신설) — 승급 대국: 내 기력(= 다음 판의 상대 급수)·최고 기력·연패·고르지 않은 승급과 그 전이(사용자 규칙 — 숫자와 조건은 `RankMeasure.kt`의 `adjustRankAfterResult`와 상수 한 곳에만 둔다, 지금은 2026-10-15 #268), 급수대별 판 크기, 최초 1회의 기력 선택. "이 설치의 사용자가 어디까지 왔는가"를 이어 가는 상태라 6계층이다. 대국 기록(`gamehistory`, 5계층)·좌석 배치(`match`)를 참조한다 — 6→5, 허용 방향이다. 급수 자체(`KgsRank`)와 그 급수로 두는 법, 그리고 "이 판이 승급 대국인가"(`PlayerSetup.isRankMeasure`)는 5계층(`shared.policy`·`shared.playstyle`·`match`)에 있다.
  - `dailyquote/`(1파일, 2026-10-21 백로그 #273으로 신설) — 오늘의 한 줄 명언: 설치마다 한 번 섞은 차례 · 이번 바퀴에 본 것 · 오늘의 것 · ✕로 닫은 날과 그 전이(`pickDailyQuote`). "이 설치의 사용자가 어디까지 보았는가"를 이어 가는 상태라 6계층이다. 하루는 출석의 UTC 하루(`attendance`의 `utcDayIndex`)를 그대로 쓴다. 명언 글은 화면 쪽(`ui/l10n/DailyQuoteCatalog.kt`)에, 저장은 4계층 어댑터 `DailyQuoteStore`에 있다.
  - `profile/`(1파일, 2026-09-25 refactor backlog #85로 신설) — 닉네임 규칙 `UserNicknamePolicy`(앞뒤 공백 제거·12자·빈 값은 없음). 원래 4계층 어댑터 `UserProfileStore`에만 있던 제품 규칙을 올렸다(4계층 위반 ④ 해소). 소비자는 그 어댑터와 `ui/account/UserNicknameDialog.kt`뿐이고 5계층 참조는 0이다.

**재편 여부**: 신규 계층(번호만 5→6으로 이동, 정의는 그대로). 기존 7계층 모델에는 없었고, `application/auth`/`application/premium`이 사실상 이 자리를 채우고 있었지만 명문화된 계층은 아니었다. 2026-09-25에 엔타이틀먼트 클러스터 6개 패키지가 5계층에서 이 계층으로 옮겨 매핑됐다(코드 이동 없음 — `LAYER_5_APPLICATION_DOMAIN.md` 「2026-09-25 재분류」).

**핵심 갭**: 아직 "세션/연속성"이라는 이름에 걸맞은 범용 개념(게스트→실계정 승격, 다중 기기 정책)이 없다 — 지금은 로그인·과금·보유(출석·소모품·캐릭터)가 각자 필요한 만큼만 자기 상태를 갖고 있고, 기기 식별자(`device/`)는 모델과 포트만 있다. ⚠️ **`work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md`의 "익명 인증 → 실계정 승격" 로드맵을 그대로 집어오면 안 된다 — 익명 로그인은 2026-08-05에 영구 폐기됐다**("재설치마다 허수 계정이 쌓이는 문제를 이전 앱에서 실제로 겪음"). 목표를 "게스트(로컬 ID)→실계정 승격"처럼 익명 인증을 전제하지 않는 형태로 다시 정의하는 것이 이 계층을 채우는 다음 작업이다(아래 로드맵 7번). 이 계층의 포트(`auth.port`·`premium.port`, 그리고 클러스터 안 `*Ports.kt` 4개)는 4계층 α이고, 실제 Firebase/SharedPreferences에 닿는 어댑터(`LAYER_4_EXTERNAL_INTEGRATION.md` 참고)와는 구분해서 봐야 한다. ~~`claimedFeatures` 일반화 전 수동 이어붙이기 부담~~ — 260814에 해소(바로 위 `PremiumState` 항목 참고).

## 지키는 테스트

- `LayeringContractTest.layerFivePackagesDoNotReferenceLayerSixPackages` — 이 계층을 5계층이 참조하지 않는다(반대 방향은 허용).

## 갭과 남은 일

### 열려 있는 것

7. **6계층 — 세션/연속성 공식화**: `work/plans/LOGIN_AND_ACCOUNT_SYSTEM.md` Step 4(실계정 승격, Firestore 동기화)를 이 계층의 정식 구현으로 진행 — **단, 익명 로그인 자체가 2026-08-05에 영구 폐기 결정됐으므로("재설치마다 허수 계정이 쌓이는 문제를 이전 앱에서 실제로 겪음") 그 문서의 "익명→실계정 승격" 경로 자체가 성립하지 않는다. 이 항목은 착수 전에 목표를 다시 정의해야 한다** — 예를 들어 "게스트(로컬 ID)→실계정 승격"처럼 익명 인증을 전제하지 않는 형태로. 기기 식별자 기반 다중 기기 정책도 이 재정의와 함께 결정.

- 6계층(세션/연속성)이 로그인·과금·보유(출석·소모품·캐릭터) 각자의 필요만 채우고 있고, 범용 개념(게스트→실계정 승격·다중 기기 정책)이 없다.

### 닫힌 것

6. ~~**6계층 — 기능 엔타이틀먼트 정책 도입**~~ — 완료(260814). `application/premium/state/PremiumState.kt`의 `isUndoClaimed: Boolean`을 `claimedFeatures: Set<FeatureId>`로 일반화하고(`persistence/PremiumStateStore.kt`에 구버전 불리언 하위호환 마이그레이션 포함), `application/premium/state/FeatureAccessPolicy.kt`를 신설해 `ui/play/GamePlaySection.kt`(형세보기/추천수/무르기)·`ui/setup/KaTrainUxPanels.kt`(착수평가)에 각자 하드코딩돼 있던 3곳의 판정을 이 함수 하나로 통합했다. **의도적으로 남겨둔 것**: 클레임 전용 다이얼로그(`ui/play/GamePlaySection.kt`의 `showUndoClaimDialog`)를 `PremiumUpsellDialog`에 `Claim` 선택지로 통합하는 UI 단순화는 이번 범위에서 제외 — 클레임 가능 기능이 아직 무르기 하나뿐이라 지금 합치는 건 과설계로 판단, 두 번째 클레임형 기능이 생기면 재검토.

- ~~기능 엔타이틀먼트 판정이 6계층이 아니라 7계층에 있다~~ — 260814에 해소. 세 곳(`ui/play/GamePlaySection.kt` 2곳, `ui/setup/KaTrainUxPanels.kt` 1곳)에 하드코딩돼 있던 OR 조합을 `application/premium/state/FeatureAccessPolicy.kt` 하나로 통합했다.
