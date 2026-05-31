# KataGo App PRD

Last updated: 2026-05-31

## 1. Goal

Build an Android-first baduk app that can run local AI analysis and eventually provide a KaTrain-like learning experience.

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

## 9. Roadmap

Phase 0: Repository and documents.

Phase 1: Android KMP skeleton with stub engine.

Phase 2: Local engine bridge POC.

Phase 3: 9x9 playable loop.

Phase 4: 13x13 and 19x19 support.

Phase 5: KaTrain-inspired review UX.

Phase 6: Optional server fallback.

## 10. Open Risks

- mobile engine packaging size
- thermal throttling and battery drain
- native build complexity
- model distribution and update path
- app store policy for large engine assets
- keeping UX simple while exposing enough learning value

## 11. Next Thread Handoff

Recommended next task:

Create the initial KMP Android project skeleton in this repository, with a stubbed `EngineAdapter` and a simple 9x9 board screen.

