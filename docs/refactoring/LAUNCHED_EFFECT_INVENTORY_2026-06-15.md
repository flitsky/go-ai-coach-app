# LaunchedEffect Inventory - 2026-06-15

이 문서는 Compose `LaunchedEffect`가 아직 남아 있는 위치와 책임을 기록한다. 목표는 UI 컴포저블이 비동기 생명주기를 직접 소유하는 범위를 계속 줄이고, 앱 서비스 또는 application runner로 이관할 후보를 명확히 추적하는 것이다.

## 현재 목록

| 위치 | 현재 책임 | 판단 | 다음 분리 후보 |
|---|---|---|---|
| `app-android/src/main/java/com/worksoc/goaicoach/MainActivity.kt:44` | 엔진 bootstrap 생성 및 앱 진입점 연결 | Android lifecycle에 묶인 초기화라 즉시 유지 가능 | `EngineBootstrapLauncher` 또는 DI bootstrapper |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt:66` | `Thinking...` 프레임 애니메이션 | 순수 UI animation이므로 유지 | 없음. 단, board animation spec 분리 가능 |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:432` | 앱 시작 runtime log 기록 | UI 소유 필요가 낮다 | startup event runner |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:445` | 엔진 startup workflow 실행 | 이미 tracked operation 경계가 있으나 trigger는 UI에 남아 있다 | `EngineStartupEffectLauncher` |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:479` | 저장 세션 확인 및 이어하기 prompt 준비 | persistence/app-service 책임 | `SavedSessionRestoreEffectLauncher` |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:579` | 최초 benchmark 자동 실행 gate | benchmark application 책임 | `EngineBenchmarkEffectLauncher` |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:611` | 사용자 설정 autosave trigger | persistence effect 책임 | 2nd phase.3에서 `runUserPreferencesAutosave()` 도입 완료. 다음은 trigger 자체 분리 |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:632` | 진행 중 대국 autosave/clear trigger | persistence effect 책임 | 2nd phase.3에서 `runSavedGamePersistence()` 도입 완료. 다음은 trigger 자체 분리 |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:1984` | 자동 AI 턴 scheduling trigger | match/application orchestration 책임 | 2nd phase.3에서 `runAutoAiTurnTriggerEffect()` 도입 완료. 다음은 실행 본문 분리 |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:2000` | 사용자 턴 Top Moves 자동 분석 scheduling trigger | analysis/application orchestration 책임 | 2nd phase.3에서 `runTopMoveAnalysisTriggerEffect()` 도입 완료. 다음은 실행 본문 분리 |
| `app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt:2018` | 종국 후 cache optimization prompt 계산 | prompt/application 책임 | `PostGameCachePromptEffectLauncher` |

## 우선순위

1. `GoCoachApp.kt:1984`, `GoCoachApp.kt:2000`  
   자동 AI와 Top Moves는 엔진 호출 빈도가 높고 stale result guard와 직접 연결된다. effect trigger를 application runner로 이동하면 원격 엔진 전환 시 UI와 engine orchestration 충돌을 더 줄일 수 있다.

2. `GoCoachApp.kt:611`, `GoCoachApp.kt:632`  
   autosave는 UI 렌더링과 무관하다. 저장 실패 정책, debounce, background write 정책을 별도 runner에서 관리하는 편이 장기적으로 안전하다.

3. `GoCoachApp.kt:445`, `GoCoachApp.kt:579`  
   startup/benchmark는 앱 진입점과 맞물려 있어 안정화 비용이 조금 더 크다. runner 경계는 이미 일부 있으므로 다음 배치에서 trigger만 옮기는 방식이 적절하다.

## 완료 기준

- UI 파일은 `LaunchedEffect`에서 직접 engine/persistence 세부 API를 호출하지 않는다.
- UI effect는 application runner에 필요한 snapshot과 callback만 넘긴다.
- 늦게 도착한 결과는 runner 또는 completion plan에서 폐기되고, UI는 apply/discard plan만 반영한다.
- 신규 effect가 추가될 때 이 문서 또는 후속 worklist에 책임과 이동 후보가 함께 기록된다.
