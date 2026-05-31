# go-ai-coach

Android-first local AI Go coaching app.

This repository is separate from `/Users/ryan9kim/worksoc/katago`, which remains the local study workspace for KataGo + KaTrain.

## Current Phase

Phase 1: Android KMP skeleton with stub engine. Phase 2 process-adapter spike is now partially verified on emulator.

Implemented POC baseline:

1. Minimal Android app shell.
2. `shared` Kotlin Multiplatform module for board state and engine DTO/interface.
3. `engine-android` module with stub and KataGo process `EngineAdapter` implementations.
4. Simple 9x9 Jetpack Compose board.
5. Engine response, candidate display area, and simple engine tuning controls.
6. Android `arm64-v8a` KataGo Eigen(CPU) build/run smoke test.

Next goal:

1. Harden the local KataGo process adapter with timeouts, stderr capture, and lifecycle cleanup.
2. Turn the current debug-only KataGo build/seed flow into a repeatable Gradle or developer workflow.
3. Keep JNI/native-library and remote fallback decisions open until packaging and device compatibility are better understood.

## Key Documents

- [PRD.md](./PRD.md): app product requirements and roadmap
- [docs/STACK_DECISION.md](./docs/STACK_DECISION.md): KMP vs Flutter final opinion
- [docs/ANDROID_KATAGO_SPIKE_RUNBOOK.md](./docs/ANDROID_KATAGO_SPIKE_RUNBOOK.md): Android KataGo build and emulator smoke-test notes

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
