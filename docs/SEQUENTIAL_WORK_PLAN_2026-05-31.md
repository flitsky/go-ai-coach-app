# 순차 작업 계획

작성일: 2026-05-31

## 요청 사항

사용자가 다음 작업을 순차적으로 수행하고, 각 단계마다 커밋/푸시한 뒤 최종 요약 보고를 요청했다.

1. 도메인 분리, 관심사 분리, 인터페이스화 적용 리팩토링
2. 한수 무르기 기능 추가
3. KaTrain처럼 착수 전 최적수/그린스팟 표시 검토 후 적용
4. 현재 AI 설정이 가장 낮은 설정인지 설정 관련 문서화

## 작업 원칙

- 각 단계는 빌드/테스트가 통과한 뒤 별도 커밋/푸시한다.
- 앱 UI는 엔진 구현 세부사항을 직접 알지 않도록 유지한다.
- 바둑 규칙과 move history는 `shared` 도메인에 둔다.
- 엔진 통신은 `EngineAdapter` 뒤에 둔다.
- KaTrain은 구조 참고 대상으로만 사용하고 코드는 복사하지 않는다.

## 단계별 계획

| 단계 | 상태 | 목표 |
| --- | --- | --- |
| 0 | 완료 | 이 작업 계획 문서화 및 커밋 |
| 1 | 완료 | `MainActivity` 비대화 해소, engine bootstrap/UI/board 컴포넌트 분리, shared 규칙 코드 분리 |
| 2 | 완료 | undo API와 UI 추가, local 2P/AI 모드 undo 정책 정리 |
| 3 | 완료 | analysis candidate를 착수 전 힌트로 보드에 표시, stub/KataGo process 한계 정리 |
| 4 | 예정 | AI difficulty/profile 설정값과 최저 설정 여부 문서화 |
| 5 | 예정 | 최종 검증 및 요약 보고 |

## 세부 설계 메모

### 1단계 리팩토링

- `MainActivity`는 engine bootstrap과 Compose entry만 담당한다.
- `EngineBootstrap`은 `engine-android` 구현 선택과 diagnostic 생성을 담당한다.
- 화면 컴포넌트는 `app-android/ui` 하위로 나눈다.
- `shared`의 `BoardModels.kt`에서 private rules helpers를 분리해 `BoardRules.kt`로 이동한다.

진행 결과:

- `MainActivity`는 `createEngineBootstrap()` 호출과 `GoCoachApp()` 진입점만 유지하도록 축소했다.
- 실제 KataGo/Stub 선택과 diagnostic 문구 생성은 `app-android/engine/EngineBootstrap.kt`로 이동했다.
- 대국 정책(`HumanPlayer`, `AiPlayer`, 모드별 입력 가능 여부, AI 응수 적용)은 `app-android/match/MatchPolicy.kt`로 분리했다.
- Compose 화면, 보드 렌더링, 엔진 패널, analysis 텍스트 포맷을 `app-android/ui` 하위로 분리했다.
- `shared`의 바둑 규칙 projection은 `BoardRules`로 분리해 도메인 모델과 규칙 실행 책임을 나눴다.
- 검증 명령: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:check :app-android:assembleDebug :app-android:testDebugUnitTest`
- 검증 결과: 성공.

### 2단계 undo

- `shared`에 move history replay 기반 undo helper를 둔다.
- local 2P는 마지막 1수를 되돌린다.
- AI 대국은 기본적으로 마지막 AI 응수와 직전 사람 착수까지 2수를 되돌린다.
- engine sync를 위해 `EngineAdapter.undoMove()`를 추가하고, KataGo process는 GTP `undo`를 사용한다.

진행 결과:

- `EngineAdapter.undoMove()`를 추가했고, stub/process adapter가 각각 구현한다.
- `KataGoProcessEngineAdapter`는 GTP `undo` 명령으로 엔진 내부 히스토리를 되돌린다.
- `shared`에 `GameStateReplayer`와 `GameState.replayWithoutLastMoves()`를 추가해 로컬 보드 상태를 move history에서 재계산한다.
- Android UI에 `Undo` 버튼을 추가했다.
- 2P 테스트 모드는 마지막 1수를 되돌리고, AI 대국 모드는 기본적으로 마지막 AI 응수와 직전 사람 착수 2수를 함께 되돌린다.
- capture가 포함된 상태에서 undo가 잡힌 돌과 prisoner count를 복원하는 테스트를 추가했다.
- 검증 명령: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:check :app-android:assembleDebug :app-android:testDebugUnitTest`
- 검증 결과: 성공.

### 3단계 힌트/그린스팟

- 우선 `AnalysisResult.candidates`를 보드 overlay로 표시한다.
- 현재 KataGo process adapter의 `analyze`는 미구현이므로, 1차는 stub analysis와 UI 표시를 연결한다.
- KataGo 실제 후보수 표시에는 `kata-analyze` streaming parser가 필요하다.

진행 결과:

- `GoBoard`가 `candidateMoves`를 받아 비어 있는 교차점에 초록 spot overlay를 표시하도록 확장했다.
- 첫 번째 후보수는 더 크고 진하게 표시해 best move로 구분한다.
- `Analyze` 성공 시 `AnalysisResult.candidates`를 보드에 반영한다.
- 사람이 착수하거나 AI 응수가 완료되거나 새 판/undo를 수행하면 stale 후보수를 지운다.
- `docs/GREEN_SPOT_HINT_DECISION.md`에 KaTrain식 후보수 표시 방향과 현재 한계를 정리했다.
- 현재 KataGo process adapter는 `kata-analyze` 미구현이므로, 실제 KataGo 후보수 spot은 후속 parser 작업이 필요하다.
- 검증 명령: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/ryan9kim/Library/Android/sdk ./gradlew :shared:check :app-android:assembleDebug :app-android:testDebugUnitTest`
- 검증 결과: 성공.

### 4단계 설정 문서

- 현재 기본값은 `DifficultyProfile.Beginner`, visits 16, time 250ms다.
- 이것이 현재 정의된 profile 중 가장 낮은 설정인지 확인하고, engine profile 조절 기준을 문서화한다.
