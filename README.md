# go-ai-coach

Android-first local AI Go coaching app.

This repository is separate from `/Users/ryan9kim/worksoc/katago`, which remains the local study workspace for KataGo + KaTrain.

## Current Phase

As of 2026-08-29, this is a playable local AI Go coaching app on 9x9, 13x13 and 19x19, with a full local KataGo engine path (not a stub-only spike anymore).

Implemented baseline as of 2026-08-29:

1. Android Compose UI: board, player setup, search-time controls, score/win-rate graph, top-moves display, debug report copy, saved-game resume.
2. `shared` Kotlin Multiplatform module: board rules, scoring (Area/Territory), engine core API contract, analysis policy, two engine search modes.
3. `engine-android`: local KataGo process adapter (`libkatago.so`) supporting both GTP stateful-fast and JSON position-analysis paths, plus a stub adapter for engine-free UI work.
4. The `application/` tree lives in `:shared` — 27 feature-domain packages as of 2026-08-29 (session, autoai, undo, humanmove, startgame, savedgame, topmoves, engine, analysis, attendance, botcharacter, consumable, gamehistory, ...), each following a small `XxxController` + `XxxApplication.kt` pure-function pattern. Only `diagnostic/` stays in `app-android`, permanently, because its file sink needs the platform. `GoCoachApp.kt` is an 854-line composition root (was 1838 lines before the 2026-06 refactor); `LayeringContractTest` enforces that line count and a 46-state-hook budget.
5. Four AI level groups exist in code (Fast Beginner / Beginner / Intermediate / Advanced — UI labels are in Korean) mapped to different visits/time/search-mode policy, but **only Fast Beginner is exposed to users** since 2026-08-18: it was split into five tiers (초보/하수/중수/고수/초고수) and the level picker collapsed to one dropdown. The other three groups are kept in code and hidden. See [docs/ENGINE.md](./docs/ENGINE.md).
6. Device benchmarking, diagnostic event logging, and a remote engine path that is wired end-to-end but debug-only: `RemoteEngineCoreApiAdapter` + `createRemoteEngineSessionClient` are reachable from `MainActivity` under `BuildConfig.DEBUG` and were verified against a Mac reference server (`scripts/run-katago-remote-analysis-server.py`) in 2026-08. It is off by default.
7. Offline engagement features (daily check-in rewards, consumable items, game history, AI bot characters) — the Phase 1 track closed on 2026-08-30; follow-ups live in `docs/roadmap/`.

Next goal:

1. Ship the initial Google Play release. Code and assets are done; what remains is Play Console paperwork — see `launch-plan/README.md` §0.
2. Work the post-launch enhancement list — `docs/roadmap/260830-_POST_LAUNCH_ENHANCEMENTS.md` is the entry point.
3. Add broader androidTest/Robolectric coverage. Default verification is JVM unit tests plus two emulator smoke tests.

(`GameSessionStateHolder` moved into `:shared` in 2026-08; that goal is done.)

## Documentation

**Taking over development? Start at [`docs/HANDOVER.md`](./docs/HANDOVER.md).** It explains how work is
run in this repository — the single-file backlog method, the one file to open right now, and how to open
the next track when the current one finishes. Written for a new developer pairing with an AI agent.

All product/architecture documentation is written in Korean and lives under [`docs/`](./docs). Start at [`docs/DOCS_INDEX.md`](./docs/DOCS_INDEX.md) — it explains what each document and subfolder under `docs/` is for.

Quick links to the main documents:

- [`docs/HANDOVER.md`](./docs/HANDOVER.md) — how development is handed over and continued (working method, current live track)
- [`docs/PRD.md`](./docs/PRD.md) — product requirements, target end state, roadmap
- [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) — the 7-layer (compressible to 4) architecture principles, app-agnostic
- [`docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md`](./docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md) — the current package map for each layer, gaps, and roadmap, with dated current-code metrics
- [`docs/ENGINE.md`](./docs/ENGINE.md) — the two engine search modes, level→mode mapping, benchmark results
- [`docs/OPERATIONS.md`](./docs/OPERATIONS.md) — stack decision, score/endgame policy, current menu/options, diagnostic + runtime logging

## Working Decision

Use Kotlin Multiplatform first for the Android engine POC.

Keep Flutter as the strongest candidate for the final cross-platform product if the product family expands beyond one Android-first engine app.

## Modules

- `app-android`: Android shell and Compose POC UI
- `shared`: KMP board state, moves, engine interface, and DTOs
- `engine-android`: Android-side engine adapter implementations
- `docs`: product, architecture, and thread notes

## Build And Test

This repository includes a Gradle wrapper. On this machine the verified command uses JDK 17 and the installed Android SDK at `/Users/ryan9kim/Library/Android/sdk`:

Recommended developer shortcuts:

```sh
make doctor
make test
make dev
make install-dev-engine
make friend-apk
```

`make dev` requires a debug engine artifact at `app-android/src/debug/jniLibs/arm64-v8a/libkatago.so`. If the artifact is missing, run `make prebuild-engine` or use `make dev-stub` for stub-only UI work.

`make install-dev-engine` installs the debug APK, seeds the KataGo model/config into app files, and restarts the app. Use `make reinstall-dev-engine` when the emulator reports low storage or when a clean reinstall is needed. Reinstalling removes app files, so the seed step must run again before KataGo mode can work.

`make friend-apk` creates a separate engine-bundled sideload APK at `dist/go-ai-coach-katago-friend.apk`. This target copies the model, GTP config, and analysis config into the `friend` build type only, so normal `make dev` / `assembleDebug` remains fast and model-free.

`make release` requires a prepared release engine artifact at `app-android/src/main/jniLibs/arm64-v8a/libkatago.so` and fails early if it is missing.

Raw Gradle command:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:check :app-android:assembleDebug
```

Optional Android unit-test task:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :app-android:testDebugUnitTest
```

The debug APK is produced at:

```text
app-android/build/outputs/apk/debug/app-android-debug.apk
```

The friend sideload APK with bundled KataGo assets is produced by `make friend-apk` at:

```text
dist/go-ai-coach-katago-friend.apk
```

## KataGo Android Spike

The debug process-adapter spike uses a locally generated native executable named `libkatago.so`. The binary and model are intentionally ignored by git.

Build the Android KataGo artifact:

```sh
ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./scripts/build-katago-android-spike.sh
```

Seed model/config into the installed debug app:

```sh
ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./scripts/seed-katago-model-to-app.sh
```

Then reinstall and launch the app with the usual Gradle/ADB commands. If `nativeLibraryDir/libkatago.so` and the seeded model/config are present, the app uses KataGo; otherwise it falls back to the stub adapter.
