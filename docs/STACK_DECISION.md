# Stack Decision

Last updated: 2026-05-31

## Question

For a local AI baduk app, should the first implementation prioritize Kotlin Multiplatform or Flutter?

## Final Opinion

Use `Kotlin Multiplatform` for the first Android engine POC.

Keep `Flutter` as the main alternative for the full product if UI reuse across many board-game apps becomes more important than native engine control.

## Why KMP First

KMP is the better first choice for the stated POC because the first risk is not visual polish. The first risk is whether the Android app can reliably communicate with a local engine artifact.

KMP fits this phase because:

- Android and JNI/C++ integration are first-class engineering paths.
- Engine-facing code can stay close to the Android runtime.
- Shared Kotlin modules can later hold game state, engine protocol, SGF logic, and analysis models.
- It avoids proving too many things at once in the first POC.

## Why Not Flutter First

Flutter remains excellent for polished multi-platform UI, especially if this becomes a family of board-game apps.

But for the first Android POC, Flutter adds one more abstraction layer before the engine boundary is proven. That is not fatal, but it is not the shortest path to validating local engine communication.

## Decision Matrix

| Criterion | KMP | Flutter |
| --- | --- | --- |
| Android engine boundary | Stronger | Strong |
| JNI / native runtime fit | Stronger | Good through FFI/platform code |
| UI productivity | Good | Stronger |
| Multi-app UI reuse | Good | Stronger |
| Web product path | Caution | Stronger |
| Long-term native control | Stronger | Good |

## Practical Strategy

Use this sequence:

1. KMP Android POC for engine communication.
2. Keep UI simple until engine transport is proven.
3. Extract game/engine interfaces into shared Kotlin modules.
4. Re-evaluate Flutter after the POC if product-family UI speed becomes the dominant concern.

## 2026-05-31 Implementation Notes

The KMP-first decision still looks sound for this project. The important clarification is that KMP should own shared domain and engine protocol logic first, not force shared UI too early. The Android UI can stay native Compose while the uncertain part remains local engine communication.

The initial skeleton uses:

- Gradle `8.14.3`
- Android Gradle Plugin `8.13.2`
- Kotlin `2.3.20`
- `compileSdk` / `targetSdk` `35`

This is intentionally conservative for the current machine because Android SDK API 35 and Build Tools 35 are installed locally. Newer AndroidX versions pulled API 36 requirements during verification. Android Gradle Plugin 8.13.2 supports Kotlin 2.3 and Build Tools 35, which makes it a better fit for the present local environment than immediately adopting AGP 9.x.

## Engine Build Timing

Not building KataGo before the skeleton is reasonable.

The first POC needs to prove three smaller contracts before adding native complexity:

1. Android app and KMP module structure builds reliably.
2. UI talks only to `EngineAdapter`, not directly to engine transport details.
3. Structured engine DTOs are enough for a move/analyze loop.

After those are working, building or reusing a KataGo artifact and adding a process-based adapter is the right next step. JNI/native packaging should wait until process-level command/response behavior, model asset location, timeout handling, and lifecycle cleanup are understood.

One caveat: Android process execution and native asset packaging need an early spike. A process adapter is still the fastest way to prove the protocol, but do not assume that a desktop-style executable launch maps cleanly to Play-distributed Android packages. Keep JNI and remote fallback as live alternatives until Android ABI packaging and model placement are tested on device.

## Post-Spike Update

The follow-up spike validated the timing decision.

After the KMP skeleton and `EngineAdapter` boundary existed, KataGo could be added as a separate process implementation without rewriting the UI or shared models.

Confirmed on 2026-05-31:

- KataGo v1.16.4 can be built for Android `arm64-v8a` using the Eigen(CPU) backend.
- GTP smoke tests work on the emulator.
- App private data is not a viable executable location for the real app process due to SELinux `execute_no_trans`.
- Packaging the executable as extracted native library content, currently `nativeLibraryDir/libkatago.so`, works for the debug spike.

So the better direction is not “build KataGo immediately before app skeleton.” It is “keep the interface stable, then spike build and packaging early once the move loop exists.” That is the path currently taken.

## References

- Kotlin Multiplatform: https://kotlinlang.org/docs/multiplatform/kmp-overview.html
- Kotlin/Native C interop: https://kotlinlang.org/docs/native-c-interop.html
- KMP platform stability: https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Compose Multiplatform compatibility: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Kotlin 2.3.20 release notes: https://kotlinlang.org/docs/whatsnew2320.html
- Android Gradle Plugin 8.13 release notes: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Gradle compatibility matrix: https://docs.gradle.org/current/userguide/compatibility.html
- Flutter supported platforms: https://docs.flutter.dev/reference/supported-platforms
- Flutter native binding / FFI: https://docs.flutter.dev/platform-integration/bind-native-code
