# go-ai-coach

Android-first local AI Go coaching app.

This repository is separate from `/Users/ryan9kim/worksoc/katago`, which remains the local study workspace for KataGo + KaTrain.

## Current Phase

Phase 0: architecture and POC planning.

Immediate goal:

1. Build a minimal Android POC.
2. Load or launch a local engine artifact.
3. Verify app-to-engine communication.
4. Render a simple baduk board UX.
5. Use the POC result to decide the full product architecture.

## Key Documents

- [PRD.md](./PRD.md): app product requirements and roadmap
- [docs/STACK_DECISION.md](./docs/STACK_DECISION.md): KMP vs Flutter final opinion

## Working Decision

Use Kotlin Multiplatform first for the Android engine POC.

Keep Flutter as the strongest candidate for the final cross-platform product if the product family expands beyond one Android-first engine app.
