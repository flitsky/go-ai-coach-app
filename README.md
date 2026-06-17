# go-ai-coach

Android-first local AI Go coaching app.

This repository is separate from `/Users/ryan9kim/worksoc/katago`, which remains the local study workspace for KataGo + KaTrain.

## Current Phase

Playable local AI Go coaching app, 9x9, with a full local KataGo engine path (not a stub-only spike anymore).

Implemented baseline:

1. Android Compose UI: board, player setup, search-time controls, score/win-rate graph, top-moves display, debug report copy, saved-game resume.
2. `shared` Kotlin Multiplatform module: board rules, scoring (Area/Territory), engine core API contract, analysis policy, two engine search modes.
3. `engine-android`: local KataGo process adapter (`libkatago.so`) supporting both GTP stateful-fast and JSON position-analysis paths, plus a stub adapter for engine-free UI work.
4. `app-android/application/`: 17 feature-domain packages (session, autoai, undo, humanmove, startgame, savedgame, topmoves, engine, analysis, ...), each following a small `XxxController` + `XxxApplication.kt` pure-function pattern. `GoCoachApp.kt` is now a thin ~790-line composition root (was 1838 lines before the 2026-06 refactor).
5. Four AI level groups (Fast Beginner / Beginner / Intermediate / Advanced — UI labels are in Korean) mapped to different visits/time/search-mode policy. See [docs/ENGINE.md](./docs/ENGINE.md).
6. Device benchmarking, diagnostic event logging, and a remote-engine-ready cache/gateway scaffold (not yet a full remote `EngineSessionClient`).

Next goal:

1. Move `GameSessionStateHolder` into the `shared` module for cross-platform reuse.
2. Add androidTest/Robolectric coverage (currently JVM unit tests only).
3. Decide on JNI/native-library packaging and remote-engine fallback once a `RemoteEngineSessionClient` is built.

## Documentation

All product/architecture documentation is written in Korean and lives under [`docs/`](./docs). Start at [`docs/DOCS_INDEX.md`](./docs/DOCS_INDEX.md) — it explains what each document and subfolder under `docs/` is for.

Quick links to the main documents:

- [`docs/PRD.md`](./docs/PRD.md) — product requirements, target end state, roadmap
- [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) — the 7-layer structure and current package map
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
