# Android Smoke Test Candidate - 2026-06-28

## Current Test Runtime State

- `app-android` uses `androidx.test.runner.AndroidJUnitRunner` as `testInstrumentationRunner`.
- Existing `androidTestImplementation` dependencies cover Compose UI testing, Espresso, and AndroidX JUnit:
  - `androidx.compose.ui:ui-test-junit4`
  - `androidx.test.espresso:espresso-core`
  - `androidx.test.ext:junit`
- `debugImplementation` already includes `androidx.compose.ui:ui-test-manifest`.
- There is no Robolectric dependency or configured Robolectric task today.
- `make test` runs JVM/unit and assemble checks only. It does not run connected instrumentation tests, and should stay that way until the smoke path is stable in CI/local automation.

## First Smoke Candidate

Use an instrumentation Compose smoke test around `MainActivity`:

1. Launch `MainActivity` with `createAndroidComposeRule<MainActivity>()`.
2. Wait for either the engine preparation screen or the main game controls.
3. Prefer stable semantic tags before enabling the test. Current UI text selectors are enough for a skeleton, but they are locale/content-sensitive.
4. After stable tags exist, enable one narrow path first:
   - app starts,
   - the initial board/game controls render,
   - one user event can be dispatched without crashing.

This should become the first executable smoke because it uses existing dependencies and validates the real Android entry point. Robolectric can be added later if we need host-side execution without a device/emulator.

## Not In This Step

- Do not add `connectedDebugAndroidTest` to `make test`.
- Do not require a physical device for default local verification.
- Do not change engine bootstrap, saved-session behavior, UI event behavior, or board tap handling while adding the skeleton.

## Enablement Checklist

- Add stable test tags for the app root, board, and a non-destructive action button.
- Decide whether the smoke should run with bundled debug engine assets or a test-only fake engine entry point.
- Verify `:app-android:connectedDebugAndroidTest` on an emulator in a clean install state.
- Only after repeated local/CI stability, consider adding a separate opt-in Make target such as `make connected-test`.
