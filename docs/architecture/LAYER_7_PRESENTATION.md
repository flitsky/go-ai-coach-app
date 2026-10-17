# 7계층 — Presentation

go-ai-coach에서 이 계층이 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「7계층 — Presentation」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「7계층」 절을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 계층 배정

`ui` 하위 패키지의 **층**(L0~L8)은 `ContractSymbols.kt`의 `UI_LAYERS`가 정본이고, 아래 블록은 그 사본이다(줄마다 층과 패키지, 접두 `com.worksoc.goaicoach.ui.` 생략). `LayerDocContractTest`가 둘이 같은지 본다.

<!-- ui-layers: UI_LAYERS -->
```text
L0 l10n designsystem foundation
L1 guide
L2 monetization
L3 board splash
L4 setup study vision home
L5 play
L6 history account
L7 settings
L8 shell
```

## 지금 위치

**위치**: `app-android/src/main/java/com/worksoc/goaicoach/ui/` — **하위 패키지 16개·9층**(아래), `app-android/src/main/java/com/worksoc/goaicoach/presentation/`(`GameUiEvent.kt`, `GameScreenState.kt`, `GoCoachScreenStateAssembler.kt`, `GameMenuEventPolicy.kt`, `KaTrainUxOptions*.kt`, `PlayerSetupUiState.kt`)

**`ui` 하위 패키지(2026-09-28, refactor backlog #27 — `490e17ca`…`4ec33a4e`)**: 한 패키지에 몰려 있던 107파일을 16개 패키지로 나눴다. 같은 패키지라 import가 없어 **파일 사이 의존을 읽을 수조차 없던** 것이, 이제 전부 import로 보인다. 간선은 **더 낮은 층으로만** 간다.

| 층 | 패키지(파일 수) |
| --- | --- |
| L8 | `shell`(11) — `GoCoachApp`(조립 루트)·`GoCoachSessionFactory`·`InitialDestination`·셸 다이얼로그 |
| L7 | `settings`(10) |
| L6 | `history`(2) · `account`(10) |
| L5 | `play`(16) — 대국 화면(`GoCoachContent`·`GamePlaySection`·`GameMenuSection`·`ScoreGraphPanel` 등) |
| L4 | `setup`(4) · `study`(3) · `vision`(4) · `home`(1) |
| L3 | `board`(4) — `GoBoard` · `splash`(3) |
| L2 | `monetization`(9) — 프리미엄·광고·출석·봇 캐릭터 UI(`#84`의 6계층 클러스터와 같은 묶음) |
| L1 | `guide`(3) — 첫돌이 가이드 |
| L0 | `l10n`(23) — `UiStrings*`·위성 Map · `designsystem`(6) · `foundation`(2) — `FeatureFlags`·`TestTags` |

- **실측(C17 커밋 본문)**: 패키지 16 · 간선 70 · 참조 311 · SCC 0 · 상호 참조 쌍 0 · 같은 층·위층 간선 0 · L0에서 나가는 간선 0. 가장 많이 참조되는 곳은 `l10n`(13개 패키지에서 153건)·`designsystem`(11/40)·`monetization`(9/36)·`guide`(8/24). 최장 사슬은 7간선(`shell → settings → play → setup → board → monetization → guide → l10n`).
- **지키는 것**: `UiPackageCycleRatchetTest` — 순환 0, 간선은 낮은 층으로만(같은 층 금지), `ui/` 루트 0파일, 파일마다 package = 디렉터리, 층 지도(`ContractSymbols.UI_LAYERS`) = 실재 패키지(양방향). 새 패키지는 층 지도에 먼저 적어야 한다.
- **모듈 승격(`#49`)의 근거**가 이 표다 — 측정 전에 목표 그래프를 정하지 않는다는 원칙대로, 모듈 경계는 이 간선 표를 보고 정한다. 참고로 `l10n`·`designsystem`·`foundation`(L0)은 ui 안의 다른 것을 전혀 모르므로 가장 먼저 떼어 낼 수 있는 후보다.
- ⚠️ `app-android/src/test/.../ui/`의 테스트 약 65파일은 아직 루트 `com.worksoc.goaicoach.ui` 패키지다(하위 패키지를 import해서 쓴다). 래칫은 main만 본다 — 옮길지는 비용 대비 이득이 작아 기본은 안 한다(`#31`).
- `MainActivity`·`GoAiCoachApplication`은 런처 컴포넌트 이름이라 루트 패키지에 남는다(`#26`).

✅ **2026-09-24 해소(refactor backlog #25, `1df59e2c`)**: `ui/`에 섞여 있던 4계층 SDK 어댑터가 `app-android/.../platform/`으로 갔다. 진단서 §1.4는 **6개**로 셌는데, #25가 *"`androidx.compose` import 0 · `ui` 심볼 참조 0"* 을 기준으로 다시 골라 **9개**를 옮겼다(`AndroidAuthClient.kt`, `AndroidBillingClient.kt` 등 — 광고 동의·광고 단위 ID·햅틱·빌드 스탬프가 이 기준에 더 걸렸다). 이제 `ui/`에 SDK 어댑터 파일은 없다. ⚠️ SDK import가 0은 아니다 — `BannerAdView.kt`(`gms.ads`)와 `AccountDeletionFlow.kt`·`OnboardingScreen.kt`·`SettingsScreen.kt`(Firebase 예외 타입으로 분기)는 **알려진 누수**로 허용 목록에 있다(고치려면 동작 변경이라 순수 이동 밖). `LayeringContractTest`의 `uiDoesNotRegrowSdkClients`가 그 밖의 재발을, `platformAdaptersDoNotImportComposeUiOrComposition`(G1)이 반대 방향(`platform`이 Compose·`ui`·조립 코드를 아는 것)을 막는다. `LAYER_4_EXTERNAL_INTEGRATION.md` 참고.

**재편 여부**: 기존 7계층과 동일한 정의, 번호도 그대로 최상위(7번) 유지.

## 지키는 테스트

- `UiPackageCycleRatchetTest` — 순환 0, 간선은 낮은 층으로만, `ui/` 루트 0파일.
- `DesignTokenLiteralRatchetTest` — 색 리터럴은 `designsystem`에만.
- `ShellStateLedgerContractTest` — 셸(`GoCoachApp`·`SettingsScreen` 등)의 줄 예산과 상태 훅 원장(`architecture-budgets.json`).
