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

## References

- Kotlin Multiplatform: https://kotlinlang.org/docs/multiplatform/kmp-overview.html
- Kotlin/Native C interop: https://kotlinlang.org/docs/native-c-interop.html
- KMP platform stability: https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Compose Multiplatform compatibility: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Flutter supported platforms: https://docs.flutter.dev/reference/supported-platforms
- Flutter native binding / FFI: https://docs.flutter.dev/platform-integration/bind-native-code

