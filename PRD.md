# Go AI Coach PRD

Last updated: 2026-05-31

## 1. Goal

Build an Android-first Go learning app that can run local AI analysis and eventually provide a KaTrain-like coaching experience.

The first milestone is not a full app. It is a technical POC:

- minimal Android UX
- local engine communication
- 9x9 board test
- basic move request / response loop

## 2. Product Direction

Target end state:

- local AI play and analysis
- 9x9, 13x13, 19x19 support
- beginner-friendly difficulty control
- post-game review
- mistake highlighting
- candidate move display
- score / winrate trend
- SGF import and export
- optional server engine fallback for low-end devices

Reference quality bar:

- KataGo strength and analysis quality
- KaTrain-style learning UX
- mobile-first interaction quality

## 3. Initial Platform Decision

Use Kotlin Multiplatform first.

Reason:

- the first unknown is engine communication, not UI expressiveness
- Android local engine integration is the shortest useful path
- shared Kotlin modules can later hold game state and engine protocol logic

Flutter remains a serious future candidate if the project expands into a family of similar board-game apps where shared UI velocity becomes more important.

## 4. POC Scope

The POC should prove only the minimum:

1. Android app launches.
2. Simple board screen renders.
3. Engine adapter initializes.
4. App sends a command to the engine layer.
5. Engine layer returns a structured response.
6. UI displays the response.

The first engine response may be a stub if the native build is not ready. The app boundary should be designed so the stub can be replaced by a real engine without rewriting the UX.

## 5. Proposed Architecture

Modules:

- `app-android`: Android shell and Compose UI
- `shared`: board state, rules, engine interface, DTOs
- `engine-android`: Android engine adapter, JNI/process bridge
- `docs`: product and architecture notes

Core interfaces:

- `EngineAdapter`
- `GameState`
- `BoardCoordinate`
- `Move`
- `AnalysisResult`
- `DifficultyProfile`

## 6. Engine Adapter Contract

The UI should not know whether the engine is native, process-based, stubbed, or remote.

Minimum API:

```kotlin
interface EngineAdapter {
    suspend fun initialize(profile: EngineProfile): EngineStatus
    suspend fun configure(profile: EngineProfile): EngineStatus
    suspend fun newGame(boardSize: BoardSize, rules: Ruleset): EngineStatus
    suspend fun playMove(move: Move): EngineStatus
    suspend fun genMove(player: StoneColor): MoveResult
    suspend fun analyze(limit: AnalysisLimit): AnalysisResult
    suspend fun stop(): EngineStatus
}
```

## 7. Learning UX Requirements

MVP learning UX:

- board size selector: 9x9, 13x13, 19x19
- play mode and review mode
- AI move suggestion
- last-move marker
- simple score estimate
- mistake severity labels

Later learning UX:

- weak AI profiles
- retry bad move
- top candidate comparison
- score/winrate graph
- review queue by biggest mistakes
- SGF annotations

## 8. Difficulty Strategy

Do not expose raw engine settings first.

Expose user-facing profiles:

- Beginner
- Casual
- Intermediate
- Strong
- Full Analysis

Each profile can map internally to visits, time limit, model, and server/local mode.

## 9. Current Implementation

As of 2026-05-31, Phase 1 baseline exists:

- Gradle/KMP project skeleton
- `shared` module with board state, moves, ruleset, engine DTOs, and `EngineAdapter`
- `engine-android` module with `StubEngineAdapter`
- `app-android` module with a simple 9x9 Compose board, engine response area, and live engine profile / visits controls
- verified `:shared:check`, `:app-android:assembleDebug`, and `:app-android:testDebugUnitTest` commands

The current board state intentionally does not implement full Go rules yet. It validates turn order, board bounds, and occupied points only. Captures, ko, suicide, scoring, SGF, and territory logic belong after the engine communication boundary is proven.

## 10. Roadmap

Phase 0: Repository and documents.

Phase 1: Android KMP skeleton with stub engine. Complete baseline created on 2026-05-31.

Phase 2: Local engine bridge POC. In progress. Android `arm64-v8a` KataGo Eigen(CPU) build and emulator process-adapter smoke test succeeded on 2026-05-31.

Phase 3: 9x9 playable loop.

Phase 4: 13x13 and 19x19 support.

Phase 5: KaTrain-inspired review UX.

Phase 6: Optional server fallback.

## 11. Open Risks

- mobile engine packaging size
- thermal throttling and battery drain
- native build complexity
- model distribution and update path
- app store policy for large engine assets
- keeping UX simple while exposing enough learning value
- process lifecycle and timeout handling once KataGo is launched from Android

## 12. Next Thread Handoff

Recommended next task after the current skeleton:

Build or reuse a local KataGo artifact, then add a process-based `EngineAdapter` behind the same shared interface. Prefer proving process stdio/GTP-style command flow first before moving to JNI/native packaging.

Current local finding: app private data cannot be used as the executable location for the actual app process on the tested Android 15 emulator. The debug spike works when the executable is packaged/extracted through the native library path as `libkatago.so`, with model/config seeded into app files.
