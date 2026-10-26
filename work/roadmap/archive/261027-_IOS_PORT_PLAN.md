# iOS 이식 계획 — 안드로이드 앱을 Compose Multiplatform으로 (백로그 #294 · 열림)

> 2026-10-27 — 지도 만들기 워크플로(읽기 전용 에이전트 15 — 패키지 11묶음 · CMP API · 테스트와 계약 · 자원과 앱 조립)가 낸 계획 그대로다. 결정: **X(app-android를 제자리에서 KMP로)**.
> 이 문서는 #294가 끝나면 봉인한다. 진행 · 결정은 활성 백로그 #294 카드가 정본이다 — 여기는 웨이브 · 경계(S1~S34) · 위험표를 찾아보는 곳.
> 「Open questions」(§7)는 사용자가 2026-10-26 밤 「결정을 기다리지 말고 진행」을 맡겼다 — 스레드가 정한 답은 #294 카드에 적는다.

---

# iOS port migration plan: bringing the iOS app up to the current Android app (#294)

Repo root: `/Users/ryan9kim/worksoc/go-ai-coach`. This pass was read-only. I edited nothing, ran no builds and made no commits.

Besides the reports, I read these files directly: `settings.gradle.kts`, `gradle.properties`, `app-android/build.gradle.kts` (plugins, android block, sourceSets, dependencies, scanned trees), `engine-android/build.gradle.kts`, `shared-ui/*`, the Makefile targets `test`, `test-ios` and `ios-*`, `app-ios` (pbxproj build phase and search paths, `GoCoachApp.swift`), `MainActivity.kt`, `RepoPaths.kt`, `ContractSymbols.UI_LAYERS` and `architecture-budgets.json`.

Facts from those reads that shape the plan:
- `engine-android` moved its sources to `src/androidMain` and adds `src/test/java` explicitly. KGP's default handling of `src/*/java` is therefore not something to rely on.
- Shell budgets are: mainActivity 43, goCoachApp 777 code lines / 48 sites, settingsScreen 310, developerTestSection 350.
- `make test` ends with `:app-android:compileDebugAndroidTestKotlin` and `:app-android:lintDebug`.
- `make test-ios` compiles `:shared-ui`.
- The Xcode phase runs `:shared-ui:embedAndSignAppleFrameworkForXcode`.

---

## 1. Strategy decision: **X** (app-android becomes KMP in place)

**Pick X.** Fold `:shared-ui`'s 3 files into `app-android` and delete that module. At AGP 9, split off a thin `androidApp` shell module.

Evidence:

| Point | X | Y (move into `:shared-ui`) |
|---|---|---|
| Visibility | app-android is internal by default: 861 internal and 17 public top-level declarations. X flips **0** of them. | At least **131 to 144** flips, 288 if no test moves, 680 for a blanket flip. The first 45-file wave alone costs 104 flips. |
| Tests | 149 of 211 test files touch internals. All 1,077 `@Test` stay in the same Gradle task, because `androidUnitTest` sees commonMain internals. | 48 tests move to a module `make test` does not run today. 41 tests stay behind and still touch internals. |
| Precedent | The same move was done for engine-android in 9497fc1e / 4574e1d1 (169 + 93 tests green, internal commonMain types used from tests). | None |
| Android-only neighbours | Files not yet moved stay in androidMain and keep `R`, `BuildConfig`, `internal` access and `proguard-rules.pro`. A wave can stop at any file boundary. | Android-only UI moved into a library loses `R`, `BuildConfig` and app build types such as `playInternal`. `ui.monetization`, `ui.account`, `ui.setup` and `ui.vision` become split packages. |
| iOS header | Kotlin/Native exports only public API. The internal UI (including the 237-parameter `UiStrings` initializer) stays out of the ObjC header. | The flipped public API leaks into the header, which then needs `@HiddenFromObjC` everywhere. |
| Contract-test paths | 17 raw `src/main` literals stay valid. RepoPaths must learn more roots in both strategies. | The same RepoPaths work, plus `make test` and `TestAnnotationContractTest` must learn a new module. |
| Toolchain | KGP 2.3.20 warns for KMP + `com.android.application` only on AGP 9 or later. The repo pins AGP 8.13.2. | `:shared-ui` would need the `com.android.kotlin.multiplatform.library` swap at AGP 9. |

Costs of X that we accept:
- **AGP 9 makes KMP + `application` fatal.** At that point the Android entry point (Application, Activity, manifest, build types, signing, asset packs) moves to a thin `androidApp` module. The library stays in `app-android/`, so RepoPaths and the tests stay where they are. All five KMP modules face the AGP 9 plugin swap in either strategy.
- **The compiler no longer enforces the boundary between UI and Android persistence.** LayeringContractTest keeps guarding it, as it does today.
- Makefile task names change, the Xcode framework path changes, and lint-baseline paths churn as files move.

**Working rule for every wave:** edit in place, then move. Behaviour edits are made while the file is still in `src/main/java`, so they are checked on the Android gate and the emulator first. The move to `src/commonMain/kotlin` is a pure `git mv` that keeps the package unchanged. Behaviour changes and moves never share a commit.

---

## 2. Package dependency DAG and the waves

### 2a. DAG

The DAG below comes from the `internalDeps` fields. An edge A → B means A needs B. ⚑ marks an edge that crosses into Android-only code and therefore becomes a seam.

```
N0  presentation → ∅                     middleware → ∅ (stays androidMain)    vision → ∅ (stays androidMain, U-84)
N1  platform ⚑(SDKs, R, BuildConfig)     engine → platform⚑, BuildConfig⚑     root → persistence⚑, platform⚑, engine, presentation, ui.*
N2  persistence ⚑ → middleware          (only the *ports* cross into common code; the Android store classes stay)
U0  l10n → persistence⚑(UiLanguageStore) · designsystem → presentation, R⚑ · foundation → presentation
U1  guide → persistence⚑, l10n, designsystem, BuildConfig⚑ · quickactions → persistence⚑, l10n
U2  monetization → root⚑(PremiumPurchaseGlue, AppForegroundEvents), persistence⚑, platform⚑, designsystem, foundation, guide, l10n
U3  board → platform⚑(PlayHaptics), presentation, designsystem, foundation, l10n, monetization · splash → guide, designsystem, l10n, R⚑
U4  setup → engine⚑, persistence⚑, platform⚑, presentation, board, designsystem, foundation, guide, l10n, monetization, quickactions
    home  → vision⚑, root⚑, persistence⚑, presentation, board, designsystem, foundation, guide, l10n, monetization, quickactions
    study → persistence⚑, presentation, board, designsystem, l10n, quickactions          [ui.vision stays androidMain]
U5  play → persistence⚑, BuildConfig⚑, presentation, board, designsystem, foundation, guide, home, l10n, monetization, quickactions, setup
U6  history → persistence⚑, presentation, board, designsystem, l10n, monetization, play, quickactions
    account → platform⚑, persistence⚑, presentation, board, designsystem, foundation, guide, home, l10n, monetization
U7  gamerecord → presentation, board, designsystem, history, l10n, play, study
    settings → root⚑, persistence⚑, platform⚑, presentation, account, designsystem, foundation, guide, home, l10n, monetization, play, setup, splash
U8  shell → everything ; root wiring/runtime → engine, persistence, platform, presentation
```

There are no cycles, and the order matches `ContractSymbols.UI_LAYERS`. In X a package can be partly moved, but a commonMain file may only depend on code that is already common.

### 2b. Wave protocol (applies to every wave)

**Commits in each wave:**
1. **Seams.** Add the expect/actual pairs and ports. The Android actual is a verbatim copy of the current code. The iOS actual compiles, even where it is a stub.
2. **Rewire in place.** Edit the files under `src/main/java` to use the seams. Update contract-test literals in the same commit, keeping their intent.
3. **Move.** `git mv` the files to `src/commonMain/kotlin`, same package. Update lint-baseline paths in the same commit.

**Gates:**
- Every commit: `make test`.
- Commits 1 and 3: also `make test-ios`. Pitfall 75 applies: commonMain compiled for Android alone still resolves `System.`, `String.format` and `@Volatile`, so only the iOS compile and the W1 source-text JVM-API ban catch them.
- Move commits: the class/method list of the debug APK must be unchanged (`apkanalyzer dex packages --defined-only`, sorted diff, script added in W1).

**Emulator checks:**
- Tap by text, not coordinates.
- Use `wm density 480` for the user's 360 dp phone.
- Do not run the gate while the emulator is busy (2 GB RAM).
- The 1-hour ad grant is reached by tapping "build info" 10 times → 고급 → 보상 받기.

**iOS checks:** `make ios-run` with the port catalog (see W1), plus `make ios-engine-probe` in the engine waves.

**Concurrent threads:** other threads work on the same `main` tree. Before each move commit, check `git status` and diff the files being moved, so nobody else's uncommitted edits get carried along.

**Shell call sites:** moved composables get platform services from `LocalAppPlatform` internally, so their signatures stay stable. Where a function today takes `context` from GoCoachApp, delete that argument on the same line. That keeps the shell budget and the site keys unchanged until W22.

### 2c. Waves

Seam IDs (Sn) refer to section 3.

**Phase A: build and guardrails**

- **W0 Build conversion.**
  - Real edits:
    - `app-android/build.gradle.kts`:
      - Replace `kotlin.android` with `kotlin.multiplatform` and add `compose.multiplatform`.
      - Add `androidTarget { compilerOptions { jvmTarget = 17 } }` and the gated `iosX64/iosArm64/iosSimulatorArm64` framework with `baseName = "SharedUI"` and `isStatic = true`.
      - Set explicit srcDirs: `androidMain ← src/main/java`, `androidUnitTest ← src/test/java + ../shared/src/commonTestSupport/kotlin`, `androidInstrumentedTest ← src/androidTest/java + commonTestSupport + resources src/test/resources`.
      - Keep the explicit manifest `src/main/AndroidManifest.xml`. The fallback is `git mv` to androidMain and androidDebug, plus one literal in LocalOnlyDataNoticeContractTest.
      - Add `commonMain` dependencies: `:shared`, `:engine-android`, and CMP runtime, foundation, ui and material3.
      - Add `iosMain` dependencies on `:engine-android` (cinterop comes along).
      - Leave the existing top-level `dependencies {}` block untouched; it only feeds the Android configurations.
      - Add `app-android/src/commonMain` and `app-android/src/iosMain` to `architectureContractScannedTrees`.
    - `settings.gradle.kts`: drop `:shared-ui`.
    - `git mv` the 3 `shared-ui` files to `app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ios/`, and change their package to `…ios` because the ui root must stay empty. Swift keeps calling `MainViewControllerKt.MainViewController()`.
    - Makefile `test`: use `…compileDebugAndroidTestKotlinAndroid`. Makefile `test-ios`: use `:app-android:compileKotlinIosSimulatorArm64`.
    - pbxproj: change the build phase to `:app-android:embedAndSignAppleFrameworkForXcode`, and change both `FRAMEWORK_SEARCH_PATHS` to `app-android/build/xcode-frameworks/...`.
  - Seams: none.
  - Android:
    - `make test`.
    - Diff `debug/releaseRuntimeClasspath` resolved versions before and after. They must be identical.
    - Diff the generated aboutlibraries JSON (library count). It must be identical.
    - The APK class list must be identical.
    - Emulator: cold start → home → 9×9 game vs AI, 10 moves → replay → Settings → open-source list.
    - `make test-device` (3 smoke tests).
  - iOS: `make test-ios`, `make ios-run` (skeleton screen), `make ios-engine-probe`.

- **W1 Guardrails and port catalog.** Test and tooling edits only.
  - RepoPaths becomes multi-root:
    - `appAndroid(rel)`, `uiRoot`, `uiSourceFiles`, `uiFile` (unique across roots), `platformFile`, `compositionFile` and `controllerWiringFiles` search `src/main/java`, `src/commonMain/kotlin` and `src/iosMain/kotlin`.
    - Add `app-android/src/commonMain/kotlin` to `KMP_COMMON_MAIN_KOTLIN` and `iosCompiledCommonMainKotlinRoots`, so the JVM-API ban now covers it.
    - Add the new roots to `productionSourceRoots`.
  - Register `com.worksoc.goaicoach.ios` in ContractSymbols as the iOS composition root (allowed to import `ui.*`, outside `UI_LAYERS`).
  - Add scripts/android-class-list.sh(새 파일).
  - Add the iOS port catalog: ios/catalog/PortCatalog.kt(새 파일) (iosMain), started with `SIMCTL_CHILD_GOCOACH_PORT_CATALOG=1` through a `make ios-catalog` target. Delete `SkeletonApp`.
  - Android: `make test`.
  - iOS: the catalog opens (empty list).

**Phase B: non-UI common code and ports**

- **W2 Leaves that need no edits.** Moves only, in two commits.
  - Non-UI: `presentation/*` (7 files), `AppForegroundEvents.kt`.
  - UI: `ui/foundation/{FeatureFlags,TestTags}` and `ui/designsystem/{AppColorScheme,AppDimens,AppMotion,AppPalette,CommonSetupUiElements,GoBoardTheme,PremiumTheme,RankTierBadge,GameActionButtons}`.
  - Board: `ui/board/{BoardFit,BoardGeometryCalculator,BoardLineStyle,BoardPlayDrag}`.
  - Play: `ui/play/{BuildTimeFormatter,GameScreenLayout,ResignAction}`.
  - Others: `ui/history/ReplayAnalysisLedger`, `ui/shell/HolderBackedState`, `ui/settings/AppFontScaleState`.
  - About 35 files and 4.5k lines.
  - Android: gate, class-list diff = 0, and the DesignTokenScreenshotTest manual comparison.
  - iOS: catalog page "Design tokens" showing palettes, RankTierBadge and action buttons in light and dark.

- **W3 Pure engine and platform types** (small edits).
  - Move `engine/EngineIdentity`.
  - Split the `EngineBootstrap` data class into its own common file. The PAD file keeps its name and content.
  - `DeferredEngineCoreApi` and `SessionGenerationRelay`: change to `kotlin.concurrent.Volatile`.
  - `EngineDownloadTracker`: the Android PAD source records a classified store-install failure. Today's tracker calls `isStoreInstallationErrorCode`, which depends on Play Core.
  - `UpdateBeforeEngineDownload`: use S15 `engineSetupLog`.
  - Split `platform/PlayAppUpdate.kt` into a common platform/AppUpdateCheck.kt(새 파일) (holding `AppUpdateStatus` and `AppUpdateCheck`, same FQNs) and an Android-only remainder.
  - Split `BackgroundMusicPlayer.kt` into a common platform/BackgroundMusicDecision.kt(새 파일) (inputs, command, next track). The `R.raw` list stays in the original file.
  - Seams: S15 (`engineSetupLog`).
  - Android:
    - Gate.
    - Arm `files/debug-engine-setup-preview` and check the gate, guide and banner, including retry.
    - Toggle background music, then background and foreground the app.
  - iOS: compile only.

- **W4 AppPlatform and universal seams.**
  - S1 `AppPlatform` / `LocalAppPlatform` in `ui.foundation`.
  - `AndroidAppPlatform` (root package, androidMain): update the LayeringContract root file list deliberately.
  - In MainActivity, `remember { UserPreferencesStore(applicationContext) }` becomes `remember { AndroidAppPlatform(this) }`, and `LocalAppPlatform` is added to the existing `CompositionLocalProvider`. Net code lines: 0.
  - S2, S3, S4, S5 (`OnScreenResume`, `ObserveScreenPauseResume`), S6 (`:shared application.time` / new `application.text`, with a ContractSymbols and LAYER doc assignment).
  - `IosAppPlatform` (iosMain) with stub members.
  - Android: gate. Emulator: cold start, and the font-scale setting still applies on restart.
  - iOS: catalog pages for the notice overlay demo and the back-swipe demo.

- **W5 Store ports.**
  - S7: new ports in `:shared`. The Android `persistence/*` classes add `: Port` and nothing else.
  - Add `clear()` to the four entitlement ports.
  - `RankMeasureLastSetup` and `ReferenceGameHistoryId` move to `:shared`, with values unchanged.
  - Add `AndroidAppStores` (each getter is `XStore(appContext)`, so instance lifetimes stay exactly as today).
  - Android: gate (21 persistence tests). Emulator smoke.
  - iOS: in-memory stores for the catalog.

**Phase C: UI, bottom-up**

- **W6 l10n** (47 files, 12.7k lines; 4 files edited, the rest moved).
  - `UiStrings.ProvideUiLanguage` reads `stores.uiLanguage` internally (the GoCoachApp call site is unchanged), and `savedUiLanguage(store: UiLanguageStorePort)`. `BoardPhotoShareActivity` keeps `UiLanguageStore(this)`.
  - Delete the dead `gameHistoryResultLabel`.
  - `WordWrap`: S8.
  - `UiStringsBoardScan` and `UiStringsBoardControlsGuide`: use S6 `formatDeviceLocale`.
  - Android:
    - Switch the language ko → en → ja → zh across home, game and settings.
    - Run the `files/debug-quote-sweep` line-count check in ja.
    - Board controls guide text.
    - Board-scan editor percentages with the device locale set to German (the comma must stay).
  - iOS: catalog "Strings" page in 4 languages, quote with `LineBreak.Simple`. Check the U+2060 glyph and the zh-CN Han glyphs.

- **W7 designsystem remainder and images.**
  - `BotCharacterAvatar` uses S9 `AppImage`.
  - `GoBoardPalette` ARGB ints become Compose `Color`. GoBoard, still on Android, calls `.toArgb()`.
  - Add the iOS resource-bundling script and Xcode phase.
  - Move the `BotCharacterAvatarTest` and `AppSplashContractTest` `R.drawable` pins to the androidMain actual.
  - Android: roster avatars (locked greyscale shards), splash coins, screenshot comparison.
  - iOS: catalog shows the 5 avatars (webp), the 5 reward vectors (XML decode) and the light/dark palettes.

- **W8 guide, quickactions, splash** (about 1.9k lines).
  - `FirstDolGuide`: S7 `guideProgress` and S2 `versionCode`.
  - Extract `GuideBlockingOverlays` into its own file in the same package (a move, not a copy).
  - `FirstDolGuideCoachMark`: `kotlin.math.PI`.
  - `AppQuickActions`: background-music setting port.
  - `ThemeModeQuickToggle`: theme port, S3 handle replace.
  - `AppSplash`: S6 local hour.
  - `SplashVariants`: S9.
  - Android:
    - Fresh install via developer wipe → guide coach marks.
    - Tap the theme quick toggle 5 times; the toast must be replaced, not queued (#275).
    - Background-music toggle.
    - Day and night splash (change the emulator clock), and the dev splash candidate row.
  - iOS: catalog shows the guide card and bubble, the quick-actions row (theme switch) and the splash player.

- **W9 Monetization state and ports.**
  - S12. The pure flows (`resolvePremiumPurchase`, `performPremiumAdGrant`, `simulatePremiumAdGrant`) move to a new common root file `PremiumPurchaseFlows.kt`.
  - `PremiumPurchaseGlue.kt` keeps the Android clients and the single `AndroidRewardedInterstitialAdClient(` inside `whileFullScreenAdShows {`.
  - Files: `PremiumUiState`, `ConsumableUiState`, `BotCharacterUiState`, `AttendanceBoardView` (S9 glyphs), `BotCharacterAcquiredDialog`.
  - Android:
    - Use the 1-hour ad grant: premium becomes active and the roster stays locked (policy).
    - Get a character shard via the test rewarded ad.
    - Attendance board folded and unfolded.
    - Restore on foreground after a process kill.
  - iOS: catalog shows the roster pager, attendance board and upsell dialog (purchase unavailable).

- **W10 Monetization surfaces.**
  - `PremiumSubscriptionNotice` (S12 product info and manage-subscription link).
  - `DailyDrawCard` (draw store, S12 rewarded ad, S3, `formatRootLocale`).
  - `AttendanceRewardClaimDialog`.
  - `AdPrivacyOptionsRow` (S12 consent).
  - `BannerAdView`: S13 split; `SubscriptionAwareBannerAd` stays common.
  - Update the allowlist in `LayeringContract.uiDoesNotRegrowSdkClients`.
  - Android:
    - My Page subscription card; the manage link opens Play.
    - Subscribe dialog shows the price (needs a Play-signed-in AVD or the internal-track phone).
    - Daily draw with test ad and the cooldown text.
    - Attendance claim (rewind the day in dev tools).
    - Replay banner.
    - Privacy row with `FORCE_EEA_CONSENT_DEBUG`.
  - iOS: catalog shows these cards with the unavailable paths.

- **W11 Board.**
  - `GoBoard` and `BoardPlayStoneButton`: S10 haptics and S11 `drawPlatformText` (the Android actual is the current `Paint` code verbatim).
  - Android:
    - DesignTokenScreenshotTest `08_board_only` before and after.
    - Tap, drag with the lifted ghost, hold-to-enlarge, invalid point (check the double pulse in `dumpsys vibrator_manager`).
    - Coordinates on and off, move numbers on both colours, candidate labels, premium review marks; 9, 13 and 19 boards.
  - iOS: interactive catalog board. Check the touch-slop and long-press thresholds, text centring and 3× line widths.

- **W12 Setup A: engine setup, notices, quote.**
  - Files: `EngineDownloadGuideSection`, `EngineDownloadStatusBanner` (S16 cellular handler expect, S14), `EngineSetupGate` (S4), `HomeTopNotice` (what's-new port, S14 update check, S15 arm files), `WhatsNewDialog`, `HomeDailyQuote` (quote port, S15).
  - Android:
    - `debug-engine-setup-preview` → gate and banner states.
    - `debug-whats-new-preview` → home line and dialog.
    - Quote dismiss.
    - `adb logcat -s DailyQuoteSweep`.
  - iOS: catalog shows the gate with fake tracker states, the what's-new dialog and the quote line.

- **W13 Setup B.**
  - Files: `GameSetupLobby` (clock, format), `PlayerSetupPanel` (S3 long), `KaTrainUxPanels` (S17 `screenWidthDp`, S10), `CompactScoringAndBoardSettingsPanel`, `BoardControlsGuide` (`roundToInt`, same tie rule), `BoardControlsGuideDialog` (S17).
  - Android:
    - Lobby premium-mode timer.
    - Bot-level clamp toast.
    - KaTrain menu column gap at 360 dp.
    - Board controls guide shown full-screen, edge to edge.
  - iOS: catalog lobby, menu and guide dialog (safe areas).

- **W14 Home.**
  - `RankMeasureUiState` (ports, S3 long), `DailyFortuneCard`, `HomeLabPage`.
  - `GoCoachHomeScreen`: S18 board-scan entry (`HomePagesContractTest` literals stay inside `if (boardScan != null)`), S4 predictive back, S5 `OnScreenResume`, history port.
  - Android:
    - Three-page pager.
    - Predictive back gesture to the centre page.
    - Share a photo from Gallery → board scan opens.
    - Camera button and Lab tile.
    - Rank-measure setup and change dialogs.
    - Fortune refresh via test ad.
  - iOS: catalog home with no camera button and no board-scan tile.

- **W15 Play** (21 files, one unit).
  - S3 handles (confirm-play cancel-then-show, merged LONG hint).
  - `currentEpochMillis`, S2 `buildTime`, `formatDeviceLocale` (`formatMillis`).
  - S19 keep-screen-on (Android actual verbatim).
  - `ScoreGraphPanel`: S11 and S17.
  - `ScoreRecordRemeasureEffect`: history port.
  - Icons.
  - Android:
    - Full games vs AI (9×9, 19×19): confirm-play toggle, illegal-tap toast with hint, free undo, pass notice and score-now, resign, final judgement and review badges, AI resignation offer.
    - `debug-engine-stall` → watchdog.
    - AI-vs-AI: the screen stays on.
    - Timer pauses in background (#202).
    - Tablet wide layout.
  - iOS: catalog shows GameStatusPanel, FinalResultBadge (🏆), PassNoticeHost, FinalJudgementDialog and the review overlay with fake state.

- **W16 Study and game-record core.**
  - `StudyScreen`, `StudyLessonScreen`, `LifeAndDeathScreen` (L&D port).
  - `StudyVideoListScreen`: `LocalUriHandler`, S9 thumbnails; `StudyVideoEntry.thumbnail: AppImage`.
  - `GameRecordCatalog` (S22), `GameRecordEvaluation` (common little-endian cursor, keeping the byte wrap).
  - Android:
    - Hub → lesson; back twice.
    - L&D progress survives a restart.
    - Video opens YouTube.
    - Record list shows EUC-KR titles.
  - iOS: catalog shows the hub, a lesson and a playable L&D problem.

- **W17 History and game-record screens.**
  - `GameHistoryScreen`: history port, S20 reference replay, `formatHistoryMonthDay` expect (Android actual is the `SimpleDateFormat` code verbatim).
  - `GameReplayScreen`, `ReplayAnalysis` (S3, `runEngineIo`), `ReplayScoreGraph` (S11), `ReplayScoreRemeasure`, `GameRecordViewer` (S19), `GameRecordsScreen` (S20 assets, S21 picker).
  - Android:
    - List dates in all 4 languages, win-rate panel, note edit, reference game.
    - Replay analysis: a ticket is charged only after a result arrives.
    - Score-graph scrub, banner.
    - Record autoplay.
    - SGF import from Files.
  - iOS: catalog history list, replay of fake data, bundled records, document picker.

- **W18 Account (no login) and settings A.**
  - Account: `MyPageScreen`, `MyPageProfileHeader` (delete the dead imports), `MyCharactersRow`, `UserAvatar` (31-polynomial hash fold, surrogate-aware initial), `UserNicknameDialog`, `LocalOnlyDataNotice`, `FirstDolGuideReplay`.
  - The login files stay in androidMain behind S24.
  - Settings: `AppTheme` (S23, S6), `ThemeModeSettingsPanel`, `BackgroundMusicSettingsPanel`, `FontScaleSettingsPanel`, `SettingsHelpLinks`, `AppUpdateUiState`, `OpenSourceLicenseEntries`, `OpenSourceLicensesScreen` (S20 `licensesJson`, `LocalUriHandler`, S4), `DeveloperAutoPlayDelayControl`, `DeveloperEngineBenchmarkControl`.
  - Android:
    - Avatar colour unchanged; emoji nickname initial.
    - Theme ×4 including status-bar icon colour.
    - Font 1.0 / 1.3.
    - Open-source list links.
  - iOS: catalog My Page, theme switching, open-source screen.

- **W19 Settings B.**
  - `SettingsScreen`: dev-mode port, S3, `LocalUriHandler`, S24 slots, `DiagnosticLogDialog(log)`.
  - `DeveloperTestSection`: stores, S2 `isDebugBuild` top-level actual so R8 still folds tier 2.
  - `DeveloperFreshInstallControl`, `DeveloperStaleSubscriptionControl`, `DiagnosticLogDialog` (clipboard port).
  - S25.
  - Update the `settingsScreen` and `developerTestSection` ledger entries.
  - Android:
    - 7 taps → countdown toasts.
    - Tier-2 menu (debug build).
    - Wipe → onboarding/guide; restart.
    - Run the release reset again → notice.
    - Copy the diagnostic log (check the clipboard label).
    - Privacy link.
    - Release build: the tier-2 classes must be absent from the class list.
  - iOS: catalog settings screen.

**Phase D: composition root**

- **W20 Root logic.**
  - Wiring files: `androidContext` becomes S26, still read lazily. `currentEpochMillis`.
  - `GameExitRecording` (history port; stays before `refreshNewGamePreview()`).
  - `AttendanceCheckInCoordinator` (port, injected dispatcher).
  - `ReleaseResetCoordinator` (marker port and the four `clear()` calls).
  - `DeveloperModeResetCoordinator` (lambda core common; S25 Android factory verbatim).
  - `BackgroundMusicCoordinator` (S27, S28 ports).
  - S29 `runAppStartup` (called by `GoAiCoachApplication`).
  - Update the root file list and the StartupOrder, DeveloperModeReset and BackgroundMusic contract-test paths.
  - Android:
    - Cold start with attendance check-in after a day rewind.
    - Run the release reset again.
    - Music pauses in background and during an ad.
    - The debug report has the `[Haptics]` section and the build stamp.
    - A finished game is recorded before the preview refreshes.
  - iOS: compile only.

- **W21 Engine runtime.**
  - `GoCoachProcessRuntime` class becomes common with an injected factory (S30). The Android factory is the `forApplication` body.
  - Bootstrap orchestration is split. PAD stays in `engine/EngineBootstrap.kt` verbatim, so the BundledEngineAssetContractTest regex is unaffected.
  - `EngineSetupRequirement` goes behind S16.
  - `DebugEngineStallInjector` becomes common (`AtomicInt`, arm lambda).
  - `engine-android`: a common stub factory and one `katagoStartupOverrides()` definition, used by EngineProbe. Update LayeringContract L1264.
  - Add `createIosProcessRuntime()`.
  - Android:
    - `make test-device`: EngineProcessCount, EngineStallRecovery, AiVsAiSoak.
    - Setup preview and stall arm files.
    - The "AI" label stays Unresolved until the engine is ready.
  - iOS: catalog "Engine" page: the AI plays a move through `LocalEngineSessionClient`, and analysis runs. `make ios-engine-probe`.

**Phase P: iOS persistence.** This track is independent of Phase C. It can start any time after W5, can run in a parallel worktree thread, and must finish before W22.

- **P1 Key-value stores.**
  - S32 `KeyValueStore`, plus in-memory and `NSUserDefaults` implementations.
  - Make `sharedLock()` public in `:shared`.
  - Common `kv.*` stores (theme, music, draw, fortune, quote, dev mode, device id, guide, L&D, release reset, UI language, profile, what's new) implementing the S7 ports.
  - Parity tests run the same scripts against the Android store and the common store.
- **P2 JSON stores.** S34 helpers; attendance (process-wide lock), bot collection, consumables, premium, rank measure, user prefs, session; GameSetup, PlayerSetup and Replay codecs. Parity tests use Android-written fixtures in both directions.
- **P3 File stores.** S33; game history (index, replay, atomic write with fsync), logs, benchmark, debug mirror; position cache is a no-op on iOS. Parity tests against `GameHistoryStore` fixtures.

**Phase E: shell and iOS completion**

- **W22 Shell.**
  - `GoCoachApp.kt` becomes common:
    - Stores come from the platform, keeping the binding names so the ledger keys hold.
    - S18 and S24 slots.
    - `recordFinishedGameOnExit(store)`.
    - S16 `isSetupRequired`.
    - S5 `ObserveAppForeground` (Android actual is the `ProcessLifecycleOwner` code verbatim).
    - S4.
  - Also moved: `AppForegroundLifecycleObserver`, `TimerLifecycleObserver`, `FirstRunGate`, `ReleaseResetNoticeDialog`, `EngineUnavailableNoticeDialog`, `GoCoachSessionFactory`, `InitialDestination`, and the rest.
  - S31 `GoCoachRoot` is extracted from MainActivity; UMP refresh and rotation stay after `setContent`.
  - `MainViewController` calls `runAppStartup` once, then `GoCoachRoot(IosAppPlatform)`. The catalog stays behind its environment variable.
  - Android: the full regression tour (below) and `make test-device`.
  - iOS: end-to-end app on the simulator.

- **W23 iOS platform quality.**
  - Notice overlay, CoreHaptics patterns, ref-counted keep-awake, status-bar style (Swift host view controller).
  - Foreground notifications → `AppForegroundEvents` and the session-clock pause.
  - Developer wipe + root re-key; debug arm files through `simctl get_app_container`; build stamp.

- **W24 iOS engine and app packaging.**
  - Configs in the bundle; model delivery per Q2.
  - Work directory under Application Support, excluded from backup.
  - Same `startupOverrides` (`numSearchThreads=1`).
  - Replay analysis keeps the one-engine `boardLeftByAnalysis` protection.
  - App icon, launch colours, `InfoPlist.strings` names.

- **W25 iOS background music.** Per Q4.

- **W26 Parity tour.**
  - Side-by-side: Android emulator at 360 dp and iOS on iPhone SE (375 pt) and iPhone 17.
  - Add U+FE0E to ⏮ ⏭ ◀ ▶ if they render as emoji.
  - Check Dynamic Type against the app font scale, dialog stacking and Korean line breaks.

**Android full regression tour** (W0, W22, and before any release cut during the migration):
1. Splash, then the three home pages.
2. A 9×9 game vs the bot, played to the final judgement.
3. Resume after `am kill`.
4. History, then replay with analysis.
5. Game records autoplay.
6. Study and L&D.
7. My Page: attendance, characters, subscription.
8. Settings in 4 languages, 4 themes, both font sizes, background music, dev mode, open-source list.
9. Daily draw with a test ad, checking the music pause.
10. Board-scan camera entry.
11. Phone rotation lock.
12. Background and foreground (timer pause, check-in).

---

## 3. Seam catalog

Rule for every seam: the Android actual is today's code verbatim. Actual files use the `.android.kt` / `.ios.kt` suffix, so `uiFile` stays unique. No expect classes are used.

| # | Seam | Common sketch | Android impl (existing) | iOS impl | Wave |
|---|---|---|---|---|---|
| S1 | AppPlatform | `interface AppPlatform { val buildInfo: AppBuildInfo; val stores: AppStores; val notice: UserNoticePort; val clipboard: ClipboardPort; val haptics: PlayHapticsPort; val monetization: MonetizationPorts; val appUpdate: AppUpdateCheck; val storeListing: StoreListingLauncher; val assets: BundledAssets; val debug: DebugTools; val engineSetup: EngineSetupUi; val boardScan: BoardScanEntry?; val authUi: AuthUi? }` and `val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { error("AppPlatform not provided") }` (in `ui.foundation`; members typed only with `:shared` or foundation types) | `AndroidAppPlatform(activity)` (root, androidMain), remembered in `setContent` | `IosAppPlatform(bridges)` (`…ios`) | W4, grows each wave |
| S2 | AppBuildInfo | `data class AppBuildInfo(versionName: String, versionCode: Int, buildTime: String, buildType: String, isDebug: Boolean, useTestAds: Boolean, forceEeaConsentDebug: Boolean)` plus a top-level `expect val isDebugBuild: Boolean` | `BuildConfig.*`; `actual val isDebugBuild = BuildConfig.DEBUG` | `NSBundle` `CFBundleShortVersionString`/`CFBundleVersion`, `Platform.isDebugBinary`, a build-time Info.plist key | W4 |
| S3 | UserNoticePort (extended) | `interface UserNoticePort { fun showShort(message: String); fun show(message: String, long: Boolean = false): NoticeHandle }` and `fun interface NoticeHandle { fun cancel() }` | `Toast.makeText(…, SHORT/LONG)`; `cancel()` → `toast.cancel()` | root overlay host: one message at a time, newest replaces current, about 2 s / 3.5 s | W4 |
| S4 | Back handlers | `@Composable expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)` and `@Composable expect fun PlatformPredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<Float>) -> Unit)` | `androidx.activity.compose.BackHandler` / `PredictiveBackHandler` (`map { it.progress }`) | CMP `androidx.compose.ui.backhandler.*` (iosMain only) | W4 |
| S5 | Lifecycle events | `@Composable expect fun OnScreenResume(onResume: () -> Unit)`, `@Composable expect fun ObserveScreenPauseResume(onPause: () -> Unit, onResume: () -> Unit)`, `@Composable expect fun ObserveAppForeground(onBackground: () -> Unit, onForeground: () -> Unit)` | `LifecycleEventEffect(ON_RESUME)`; `LocalLifecycleOwner` observer; `ProcessLifecycleOwner` observer | JetBrains lifecycle `LocalLifecycleOwner`; `UIApplication` notifications | W4 / W22 |
| S6 | Clock and format | `:shared` `application.time`: `expect fun localTimeOfDay(): LocalTimeOfDay(hour, minute, second)`; `application.text`: `expect fun formatDeviceLocale(format: String, vararg args: Any): String`, `expect fun formatRootLocale(…)`; `ui.history`: `expect fun formatHistoryMonthDay(epochMillis: Long, language: UiLanguage): String` | `LocalTime.now()`; `String.format(format, *args)` / `(Locale.ROOT, …)`; `SimpleDateFormat` patterns verbatim | `NSCalendar`; Kotlin mini-formatter (`%d`, `%0Nd`, `%.Nf`, `%s`, `%n$s`), ASCII; `NSDateFormatter` with the same patterns | W4 / W17 |
| S7 | Store ports and AppStores | New `:shared` ports (non-Compose): `ThemeModeStorePort { load(); save(ThemeMode); modeChanges(): Flow<ThemeMode> }`, `BackgroundMusicSettingsPort { isEnabled(); setEnabled(Boolean); enabledChanges(): Flow<Boolean> }`, `DailyQuoteStorePort`, `DailyFortuneStorePort`, `WhatsNewStorePort { lastSeenVersionCode(); markSeen(Int); isFreshInstall() }`, `DeveloperModeStorePort`, `GuideProgressStorePort { load(); markSeen(GuideStep); arm() }`, `UserProfileStorePort`, `ReleaseResetMarkerPort`, `LifeAndDeathProgressPort`, `UiLanguageStorePort`, rank-measure last-setup load/save, and `fun clear()` on the 4 entitlement ports. `interface AppStores { val userPreferences: UserPreferencesStorePort; …all ports… }` | existing `persistence/*` classes add `: Port`; `AndroidAppStores` getters return `XStore(appContext)` per access | P1–P3 `kv.*` stores (process-wide singletons) | W5 |
| S8 | `quoteLineBreak` | `internal expect fun quoteLineBreak(): LineBreak` | `LineBreak(Simple, Normal, WordBreak.Phrase)` | `LineBreak.Simple` | W6 |
| S9 | AppImage | `enum class AppImage { BotFastBeginner1..5, SplashSpotlightNight, SplashHanokDojoDay1, SplashHanokDojoDay2, StudyThumbBadukIntro1..3, RewardEval, RewardTopMoves, RewardAdSkip, RewardUndo, RewardMoveReview }` and `@Composable internal expect fun painterFor(image: AppImage): Painter` | exhaustive `when` → `painterResource(R.drawable.x)` | bundle bytes → `Image.makeFromEncoded(...).toComposeImageBitmap()` / `decodeToImageVector`, remembered per image | W7 |
| S10 | PlayHapticsPort | `interface PlayHapticsPort { fun play(); fun playInvalid() }` | `PlayHaptics(context)` (Vibrator) | `UIImpactFeedbackGenerator(Heavy)` / CoreHaptics pattern | W11 |
| S11 | `drawPlatformText` | `internal expect fun DrawScope.drawPlatformText(text: String, anchor: Offset, sizePx: Float, color: Color, align: TextAnchor = Center, bold: Boolean = true, outline: Color? = null, outlineWidthPx: Float = 0f)` | `android.graphics.Paint` + `nativeCanvas.drawText`, verbatim per call site (GoBoard ×3, ScoreGraphPanel, ReplayScoreGraph) | skia `Font(FontMgr.default.matchFamilyStyle(null, BOLD))`, `TextLine`, same baseline maths | W11 |
| S12 | MonetizationPorts (in `:shared` `application.premium.port`) | `interface MonetizationPorts { val isPurchaseAvailable: Boolean; fun premiumPurchase(): PurchasePort?; fun botCharacterPurchase(): PurchasePort?; val rewardedAds: RewardedAdGateway; val adConsent: AdConsentPort; fun openSubscriptionManagement() }`, `fun interface RewardedAdGateway { suspend fun showRewardedAdOnce(): AdRewardOutcome }`, `interface AdConsentPort { suspend fun refresh(); fun isPrivacyOptionsRequired(): Boolean; suspend fun showPrivacyOptionsForm(); fun resetForDebug() }`. The pure flows move to `PremiumPurchaseFlows.kt`. | `AndroidBillingClient(activity, PREMIUM_PRODUCT_ID, SUBS / BOT_CHARACTER_PRODUCT_ID, INAPP)` per call (null without an Activity); `showRewardedAdOnce` funnel (consent → music pause → ad); `AdsConsentManager`; Play subscriptions URL with `packageName` | stubs (section 6); later Swift bridges | W9 |
| S13 | Banner | `@Composable expect fun BannerAdView(modifier: Modifier = Modifier)` (`SubscriptionAwareBannerAd` stays common) | current `AndroidView(AdView)` body | empty; later `UIKitView(GADBannerView)` | W10 |
| S14 | Store listing and update | `fun interface StoreListingLauncher { fun open() }`; common process singleton `processAppUpdateCheck()` with `fun installAppUpdateRequest(request: suspend () -> AppUpdateStatus)` called at startup | `openStoreListing(context)` (market:// → https, `packageName`); Play In-App Update | no-op / hidden; `{ AppUpdateStatus.Unknown }` | W12 |
| S15 | DebugTools | `interface DebugTools { val armFiles: DebugArmFiles; fun log(tag: String, message: String); val resets: DeveloperResetActions; val reportInfo: DebugReportPlatformInfo }`, `fun interface DebugArmFiles { fun consume(name: String): Boolean }`, `expect fun engineSetupLog(message: String, error: Throwable?)` | `BuildConfig.DEBUG && File(filesDir, name).let { it.isFile && it.delete() }`; `Log.i`; `padLog` (still swallows `RuntimeException`) | Documents directory in debug, false in release; `NSLog` / stderr | W3 / W12 |
| S16 | EngineSetupUi | `interface EngineSetupUi { fun isSetupRequired(): Boolean; val tracker: EngineDownloadTracker; fun isOfficialPlatformPermanentFailure(): Boolean }` and `@Composable expect fun RegisterCellularConfirmationHandler()` | `isEngineSetupRequired(context)`, `currentEngineDownloadTracker()`, `AssetPackManager.showConfirmationDialog` | false / Idle tracker / false / no-op | W12 / W22 |
| S17 | Window helpers | `@Composable expect fun screenWidthDp(): Int`; `expect fun fullScreenDialogProperties(): DialogProperties` | `LocalConfiguration.screenWidthDp`; `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` | `LocalWindowInfo.containerSize` → dp; `DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false)` | W13 |
| S18 | BoardScanEntry | `interface BoardScanEntry { fun hasPendingSharedPhoto(): Boolean; @Composable fun Screen(engineClient: EngineSessionClient, onBack: () -> Unit, onStartGameWithState: (GameState) -> Unit) }` | wraps `SharedBoardPhotoInbox` and `BoardScanScreen` (all of `vision/` and `ui/vision/` stays androidMain) | `null`, which hides the camera button, the Lab tile, the what's-new photo bullets and the OpenCV credit | W14 |
| S19 | `KeepScreenOnWhile` | `@Composable expect fun KeepScreenOnWhile(active: Boolean)` | `LocalView.keepScreenOn` verbatim | ref-counted `UIApplication.idleTimerDisabled` | W15 |
| S20 | BundledAssets | `interface BundledAssets { suspend fun readBytes(path: String): ByteArray?; fun licensesJson(): String?; suspend fun referenceGameReplay(): GameReplayData? }` (paths relative to the Android `assets/` root) | `context.assets` on `Dispatchers.IO`; `R.raw.aboutlibraries`; `loadReferenceGameReplay(context)` | `NSBundle` `appassets/<path>`; iOS license JSON; P2 replay codec | W16–W18 |
| S21 | SGF file picker | `@Composable expect fun rememberSgfFilePicker(maxBytes: Int, onResult: (SgfPick) -> Unit): () -> Unit`, where `SgfPick` is `Cancelled`, `Failed` or `Bytes(ByteArray)` | `OpenDocument("*/*")` plus a capped `contentResolver` read on IO | `UIDocumentPickerViewController(asCopy = true)`, capped `NSData` | W17 |
| S22 | Charset decode | `expect fun decodeTextOrNull(bytes: ByteArray, charsetName: String): String?` | `String(bytes, Charset.forName(name))` | `CFStringConvertIANACharSetNameToEncoding` → `NSString`, falling back to UTF-8 | W16 |
| S23 | System bars | `@Composable expect fun ApplySystemBarsAppearance(dark: Boolean, windowBackground: Color)` | current `SideEffect` (`WindowCompat`, window background) | no-op; later the host view controller's status-bar style | W18 |
| S24 | AuthUi | `interface AuthUi { @Composable fun Onboarding(…); @Composable fun AccountSection(log: DiagnosticEventLogPort) }` | the existing login files (Firebase, Credential Manager), unchanged | `null` (login is off) | W18 / W19 |
| S25 | Developer resets | `interface DeveloperResetActions { fun wipeToFreshInstall(); fun restartToFreshInstall(); fun runReleaseResetAgain(): Boolean }` | `wipeToFreshInstall` / `restartToFreshInstall(context)` / `runReleaseResetAgain(context)` verbatim | wipe `go_ai_coach_*` suites (except device identity) and `game_history`, then re-run startup and re-key `GoCoachRoot`; no `exit()` | W19 / W20 |
| S26 | DebugReportPlatformInfo | `interface DebugReportPlatformInfo { fun hapticDiagnostic(): String; fun buildStamp(): String }` (replaces `GoCoachAppWiringContext.androidContext`; read lazily) | `PlayHaptics(app).diagnosticReport()`, `debugReportBuildStamp()` | `UIDevice` / `utsname` / CoreHaptics capability | W20 |
| S27 | App foreground | `expect fun appForegroundState(): StateFlow<Boolean>`; the platform calls `AppForegroundEvents.notifyForegrounded()` | the exact `ProcessLifecycleOwner…currentStateFlow.map { … }` expression (contract-grepped); `onStart` observer | `MutableStateFlow` driven by `UIApplication` notifications; fires once at cold start | W20 |
| S28 | Background music | `fun interface BackgroundMusicPlayerPort { fun update(inputs: BackgroundMusicInputs) }` (the settings port is in S7) | `BackgroundMusicPlayer` (MediaPlayer, audio focus, `R.raw`) | `AVAudioPlayer` with `AVAudioSession` ambient and interruption handling | W20 / W25 |
| S29 | Startup | `fun runAppStartup(isDebugBuild: Boolean, nowMillis: Long, devReset: …, releaseReset: …, startForeground: …, attendance: …, music: …)`, called once per process | `GoAiCoachApplication.onCreate` (`applyAdContentRating` stays first) | `MainViewController`, guarded by a process flag | W20 |
| S30 | Engine runtime | `class GoCoachProcessRuntime(…injected…)`; `interface EngineBootstrapPlatform { val workDir: String; val configPaths: Pair<String, String>; fun locateModels(): EngineModelFiles; suspend fun fetchMissing(…): List<String>; val engineBinaryAvailable: Boolean; fun createLocal(config: KataGoProcessConfig): EngineCoreApi; fun createStub(): EngineCoreApi }`; `engine-android` common `katagoStartupOverrides(logDir, homeDir)` and stub factory | `forApplication` body; PAD, `seedAssetIfMissing`, `EngineCoreApiFactory.local/stub` | `IosEngineCoreApiFactory.inProcess(config, workDir)`; models from env vars (simulator) or the bundle; `remoteClientFactory = null` | W21 |
| S31 | GoCoachRoot | `@Composable fun GoCoachRoot(platform: AppPlatform, runtime: GoCoachProcessRuntime)` (font-scale density, bootstrap trigger, `AppTheme`, `Box { GoCoachApp; AppSplash }`) | `MainActivity.setContent` body | `ComposeUIViewController { GoCoachRoot(…) }` | W22 |
| S32 | KeyValueStore | `interface KeyValueStore { getString / putString(key, String?) / getInt / getLong / getBoolean / getStringSet / remove / clear(durable: Boolean) / keyChanges(key): Flow<Unit> }` and `interface KeyValueStoreFactory { fun open(name: String): KeyValueStore; fun names(): List<String>; fun delete(name: String) }` | not used by Android stores (they stay on SharedPreferences) | `NSUserDefaults(suiteName)`, a registry of opened names, a process-wide change bus | P1 |
| S33 | AppFileStore | `interface AppFileStore { readTextOrNull; writeText; writeAtomically(path, bytes): Boolean; appendText; length; list; delete; deleteRecursively; mkdirs }` | (JVM implementation for tests only) | `NSFileManager`; POSIX `open` / `write` / `fsync` / `rename` | P3 |
| S34 | JSON helpers | common kotlinx-serialization tree helpers with org.json leniency (`optString` of a number, `isNull(missing) == true`, numeric-string coercion) | test-only use (parity tests) | the P2 codecs | P2 |

No seam is needed for: URLs (`LocalUriHandler`), clipboard (the existing `ClipboardPort` in S1) or the wall clock (the existing `currentEpochMillis()`).

---

## 4. Resources and Gradle

**Resources.** Android packaging stays identical, so composeResources are not adopted yet.
- **16 images:** `res/drawable*` stay where they are and Android keeps `R.drawable` (S9). A new Xcode run-script phase calls scripts/ios-bundle-resources.sh(새 파일), which keeps one explicit list. It copies:
  - the 8 webp, 3 jpg and 5 vector XML images from `app-android/src/main/res/drawable{,-nodpi}` into `GoCoach.app/appres/`;
  - `assets/game-records/*` (66 files, 2.86 MB) and `assets/reference_games/*.json` into `appassets/`;
  - `src/friend/assets/katago/*.cfg` when present (gitignored; `make prepare-friend-assets`).
- No files are duplicated. The engine-lab output path and `GameRecordCatalogContractTest` are untouched.
- **Background music:** the `.ogg` files stay in `res/raw` for Android. iOS needs AAC copies (per Q4) in `app-ios/GoCoach/Resources/bgm/`, and the CC BY 4.0 credits must ship too.
- **Open-source licences:** Android keeps the generated `R.raw.aboutlibraries`. iOS gets its own JSON (KataGo, the models, CMP, kotlinx, the music tracks; no Play services, Firebase or OpenCV) in `app-ios/GoCoach/Resources/`. First try exporting it with the aboutlibraries plugin for the iOS configuration; if that doesn't work, keep it by hand in `config/aboutlibraries-ios/`.
- **Launcher, theme, backup, FileProvider** stay Android-only. iOS needs:
  - `Assets.xcassets` with a 1024 px `AppIcon` (Q6) and a launch colour set (#F6F1E7 light, #1A1815 dark);
  - `InfoPlist.strings` for ko, en, ja and zh-Hans (AppNameContractTest is extended to check them);
  - the model directory excluded from backup.
- **Later option, after parity:** move to composeResources with a single `Res` class. That would change Android packaging, so it is a separate decision.

**Gradle**

| Where | Change |
|---|---|
| `libs.versions.toml` | `compose-mp-ui-backhandler = org.jetbrains.compose.ui:ui-backhandler:1.9.3` · `compose-mp-components-resources = org.jetbrains.compose.components:components-resources:1.9.3` · `jetbrains-lifecycle-runtime-compose = org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6` · `compose-mp-material-icons-core = org.jetbrains.compose.material:material-icons-core:1.7.3` |
| app-android plugins | `kotlin.android` → `kotlin.multiplatform`; add `compose.multiplatform` (keep `android.application`, `kotlin.compose`, `spotless`, `aboutlibraries.android`, `google.services` `apply false`) |
| app-android `commonMain` | `project(":shared")`, `project(":engine-android")`, `compose.mp.{runtime,foundation,ui,material3}` (1.9.3 / 1.9.0), `compose.mp.material.icons.core` (W1; fallback: copy the 11 icons into `ui.designsystem`, Apache-2.0), `kotlinx-coroutines-core`, `aboutlibraries-core:13.2.1` (W18), `kotlinx-serialization-json:1.10.0` (P1) |
| app-android `iosMain` | `ui-backhandler`, `components-resources` (decoders only), JetBrains `lifecycle-runtime-compose`. These stay **iOS-only** so that Android does not move from androidx lifecycle 2.8.7 to 2.9.4. |
| app-android Android | the existing `dependencies {}` block is unchanged; `androidUnitTest` / `androidInstrumentedTest` source dirs as in W0 |
| `:shared` | new ports (W5, S12), `application.time` / `application.text` expects (W4), `sharedLock()` made public (P1) |
| `:engine-android` | common stub factory and `katagoStartupOverrides` (W21) |
| removed | `:shared-ui` module (W0) |

---

## 5. Android behaviour risks per wave and their guards

| Wave | Risk | Guard |
|---|---|---|
| W0 | Plugin swap changes resolved dependency versions or the OSS list (JetBrains redirect artifacts appearing in aboutlibraries). `src/main/java` is compiled twice or not at all. The manifest moves. aboutlibraries or google-services misbehave under KMP. The iOS Xcode build now configures app-android (`local.properties`, `google-services.json`). | Classpath version diff = ∅. Generated aboutlibraries JSON diff = ∅ (use `exclusionPatterns` if needed). Class list = ∅. Explicit srcDirs and manifest path. Run the full tour plus smoke tests. Check that configuration works when the gitignored files are missing. Any unavoidable bump → stop and ask (Q8). |
| W1 | RepoPaths misses a root, so contract tests pass vacuously. `uiFile` duplicates. | Strict roots (must exist). Uniqueness check across roots. Add one deliberate negative test (a temporary bad import in commonMain fails the JVM-API ban). |
| W2 | A "pure" file leaks a JVM API that only iOS would catch. | `make test-ios` on the move commit. Class list = ∅. |
| W3 | `padLog` stops swallowing `RuntimeException`, breaking the JVM tests. Losing the store-install classification changes the stub dialog wording. `@Volatile` resolves to the JVM annotation only. | The `engineSetupLog` actual is verbatim. Keep the -13/-15/-11 classification in the Android PAD source. Use `kotlin.concurrent.Volatile`. |
| W4 | MainActivity budget (43) or the StartupOrder contract (nothing may come between `super.onCreate` and `setContent`). `AppPlatform` rebuilt on recomposition. | Net-zero line swap inside `setContent`. `remember {}` without keys. StartupOrderContractTest stays green. |
| W5 | Store file names, keys, value types (Int / Long / StringSet) or the durable (`commit`) vs `apply` choice changed by mistake. | Classes gain `: Port` only. The 21 persistence tests plus `DeviceGameSetupStorageGoldenTest` stay green. Getters create a new instance per access (today's lifetime). |
| W6 | `UiLanguage` enum names are the stored value. `QuoteLineBreak` falls back to `Simple`. The `String.format` locale behaviour changes. `StudyHubContractTest` checks the 255-slot `UiStrings` text. Same-layer import (l10n → foundation). | Do not rename enums. The Android actual uses the exact `LineBreak`. `formatDeviceLocale` is verbatim. Keep the `internal data class UiStrings(` text. Formatting lives in `:shared`. |
| W7 | Avatar `when` ordering and refs. Splash first frame blank. Palette ARGB drift. | `AppImage` actual kept as an exhaustive `when`. `R.drawable` stays (synchronous). `.toArgb()` gives the same ints; screenshot comparison. |
| W8 | `GuideBlockingOverlays` / `SplashVisibility` duplicated (the gate silently splits). Toast queueing (#275). | Move, never copy (ShellStateLedger freezes globals). Use the S3 handle `cancel()`. |
| W9 | Purchase port built from `applicationContext` → every purchase becomes Unavailable. Restore downgrades a subscriber (#158). Ad path bypasses consent or music pause. Persisted `PremiumSource` / `FeatureId` names. | Resolve the Activity per call, as today. `currentState` is passed through unchanged. One funnel, with the single `AndroidRewardedInterstitialAdClient(` contract kept. No enum renames. Emulator purchase / restore / ad-grant tour. |
| W10 | Product info queried on screen open. Banner gate (`isPurchased` vs `isActive`). | Query only while the dialog is shown. `SubscriptionAwareBannerAd` logic unchanged. Update the SDK allowlist. |
| W11 | Haptics routed through `LocalHapticFeedback` (#36). Haptics instance unstable or added as a `pointerInput` key. Text pixels change. | The S10 Android implementation is `PlayHaptics`. Remembered once and captured, not a key. Keep the `pointerInput` keys byte-identical. S11 verbatim, plus the `08_board_only` comparison. |
| W12 | `AppUpdateCheck` stops being one per process (the dismiss state lasts only until the next cold start, by design). The debug arm file is consumed twice. `applicationId ≠ namespace`. | Common process singleton. `remember(store)` with a stable store. The listing uses `packageName`. |
| W13 | `LocalConfiguration` vs window width drift. Edge-to-edge guide dialog. | S17 Android actuals verbatim. |
| W14 | Shared-photo check moved into the shell (hook budget 0). `HomePagesContractTest` literals. Predictive-back cancellation. | Keep it in Home's `ON_RESUME`. Gate around the unchanged literals. Emulator gesture check. |
| W15 | Turn-timer clock base. Toast merge / LONG rules. `FinishedGameFlow` must stay a single object. Contract literals in `GamePlaySection` / `GameStatusPanel`. | `currentEpochMillis` (wall clock). S3 semantics. Move it once. Update the literals with their intent in commit 2. |
| W16 | L&D progress format. Evaluation byte wrap. | Port over the unchanged Android store. Little-endian cursor plus `GameRecordEvaluationTest`. |
| W17 | Ticket charged with no result shown. Date-string drift. Remeasure writes to a different store. | Keep `LaunchedEffect` keys and charge-after-result. S6 `SimpleDateFormat` verbatim. Same file-backed store. |
| W18 | Avatar colour changes. Theme-flow listener lifetime. | Bit-identical 31-polynomial fold. `modeChanges()` unchanged on Android. |
| W19 | Tier-2 developer code reaches release (R8 can no longer fold). Ledger keys. The `DeveloperSectionTier` grep. | Top-level `isDebugBuild` actual val, then check the release class list. Keep binding names. Update the grep target with the same intent. |
| W20 | Startup order (attendance before resets wipes the check-in). Release-reset atomicity (`hasEntitlementData` before `clear`, marker written last with `commit`). Pitfall 67 wiring keys. `androidContext` read laziness (0 reads at wiring, 1 per copy). `GameExitRecording` order. | `runAppStartup` with a fixed order, plus the StartupOrder and DeveloperModeReset tests. The four ports clear together. `remember` keys untouched. `GoCoachControllerWiringTest` semantics. `GameExitRecordingContractTest`. |
| W21 | `engineClient` re-derived, which restarts the engine. `coreApiDeferred` completed after the bootstrap is published. PAD timing invariants. Engine shown as failed when it is only not ready yet. | `EngineReadinessWiringContractTest`. PAD code untouched in androidMain. `Unresolved` until ready. Device smoke tests. |
| W22 | Shell budget (777 / 48 sites). Back-press priority vs `BoardScanScreen`. Dialog gate chain. Process-scoped lifecycle (#202). | Same binding names; update the JSON only when lowering. S4 uses the same dispatcher. Keep the Boolean gate chain. S5 Android actual is `ProcessLifecycleOwner`. Full tour plus `make test-device`. |
| P1–P3 | iOS stores drift from Android semantics. | Parity tests (same script, both implementations; Android fixtures decoded by the common codecs). Android stores untouched. |
| All | R8 / proguard. Lint-baseline path errors. | Same module, so `proguard-rules.pro` is unchanged (`CvException` keep rule). Move baseline path entries in the same commit; never grow the baseline. |

---

## 6. What iOS still lacks after all waves, and the minimal stubs

| Area | Missing | Stub used so the simulator runs end to end |
|---|---|---|
| Billing | StoreKit 2 (Swift-only; needs a callback bridge passed into `MainViewController`) | `premiumPurchase()` / `botCharacterPurchase()` return `null` → `NotPurchased(Unavailable)`. `queryPremiumProductInfo` returns `null`. Restore is never authoritative, so nothing is downgraded. `isPurchaseAvailable = false` hides the purchase entry points (pending Q1). The Android `FeatureFlags` constants stay unchanged. |
| Ads | GMA iOS, UMP, ATT | `RewardedAdGateway`: release returns `NotRewarded(Unavailable)`; debug builds simulate a reward after about 1 s so the reward flows can be tested. Banner is empty. `AdConsent` is never required, so the privacy row is hidden. |
| Premium | depends on the above | Premium is off. The debug "1-hour ad grant" works through the simulated reward. |
| Engine | release model delivery (Q2) | `IosEngineCoreApiFactory.inProcess(KataGoProcessConfig(...), workDir = Application Support/katago)`. On the simulator, models come from env vars (as `ios-engine-probe` does); configs come from the bundle script. Setup is never required, the download tracker stays Idle, no remote client, no position cache. |
| Board scan, camera, share-in (U-84) | everything in `vision/` and `ui/vision/` | `boardScan = null`, which hides the camera button, the Lab tile, the 1.5.0 photo bullets and the OpenCV credit. `FeatureId.BoardScan` stays in the enum. |
| Login | Firebase iOS, GoogleSignIn | `authUi = null`, `AuthClientPort = null` (login is off on both platforms). |
| In-app update and store page | iTunes lookup, numeric App Store ID | `AppUpdateStatus.Unknown`, which never blocks the engine; the store link is hidden. |
| Background music | AAC assets (Q4) | Toggle hidden and the player is a no-op until W25. |
| Developer restart | no relaunch API | Wipe, then re-key `GoCoachRoot` and re-run `runAppStartup`. |
| Persistence | — | P1–P3 stores (NSUserDefaults plus Application Support files). |
| Haptics, clipboard, notices, keep-awake | — | `UIImpactFeedbackGenerator`, `UIPasteboard`, overlay host, `idleTimerDisabled`. |
| Store wording | "Google Play" strings | Unchanged until Q5; only account deletion and the local-data notice are affected. |

End-to-end check: `make ios-run` with the model env vars → splash → home → 9×9 game where the in-process KataGo replies → final judgement → history → replay with analysis (one engine at a time) → settings → force-quit → relaunch, and the game resumes.

---

## 7. Open questions for the user

1. **iOS v1 without StoreKit or AdMob.** For premium features and ad-gated rewards (daily draw, fortune refresh, character shards, the 1-hour ad grant), should iOS (a) hide them, (b) grant them without an ad, or (c) show them as unavailable? Plan default: (a) in release, simulated in debug. The question is about what the screen looks like, so I'll show screenshots of the options rather than describe them.
2. **Model delivery for an iOS release.** Bundle both models (about 197 MB, which brings the App Store cellular-download prompt) or use On-Demand Resources, which keeps the download UI? Development uses host paths either way. This decides whether the engine-setup gate and banners matter on iOS.
3. **Temporary dual persistence.** Android stores stay as they are, and iOS uses new common stores guarded by parity tests. The alternative is converting Android to the common codecs now, which risks changing the format of user data. Plan default: the dual implementation; moving Android onto it is a later, separate decision.
4. **Background music on iOS.** Commit AAC transcodes of the 4 CC BY tracks (about 11 MB; sources from `/assets/audio/bgm` if they exist, otherwise from the `.ogg` files), or ship iOS v1 without music?
5. **What's New and store wording on iOS.** Show the Android release history (CFBundleVersion already follows the versionCode numbering, 10804), or start iOS with empty or iOS-specific notes? Switch "Google Play" copy to App Store wording now, or wait for StoreKit?
6. **iOS app icon.** The largest source is 512 px (`work/play-store-assets/app_icon/app_icon_512x512_board.png`). Is there a 1024 px original, or is an upscale acceptable?
7. **AGP version.** Confirm AGP stays on 8.13.x until #294 ends. Strategy X relies on it; if AGP 9 is needed sooner, the thin `androidApp` split comes first.
8. **Only if W0 finds it:** if some Android dependency bump cannot be avoided (for example androidx lifecycle 2.8.7 → 2.9.x, or new entries in the open-source list), accept it or keep pinning?

**Rules this plan introduces (my decisions, not yours):**
- the edit-in-place-then-move commit pattern;
- the class-list diff guard on move commits;
- the iOS port catalog behind an env var;
- `AppImage` and the bundle script instead of composeResources;
- formatting through `formatDeviceLocale` instead of `padStart`;
- JetBrains lifecycle and backhandler kept iOS-only;
- Android stores kept with separate iOS stores (Q3);
- iOS notice durations of about 2 s / 3.5 s;
- simulated rewarded ads in iOS debug builds;
- the wave boundaries and the P-track parallelism.