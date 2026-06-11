# 엔진 search tree 재사용 정책 검토

작성일: 2026-06-11

## 한줄 결론

KataGo search tree 재사용은 좋은 기능이다. 사람 vs AI 대국에서는 같은 AI가 읽기를 이어가는 장점이 크므로 재사용을 유지한다. 다만 AI vs AI 자동대국에서는 `레벨 보정`, `AI vs AI 비교`, `반복 새 판 검증`을 우선해야 하므로 AI 착수 분석 직전 `clear_cache`를 호출한다.

재사용을 다시 켜려면 단순히 random seed를 바꾸는 방식이 아니라, `maxPlayouts` 또는 엔진 세션 분리 같은 별도 정책으로 “새로 수행할 탐색량”을 보장해야 한다.

## 현재 코드 기준

### Engine bootstrap

[EngineBootstrap.kt](/Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/engine/EngineBootstrap.kt)

- KataGo GTP process는 `numSearchThreads=1` override로 시작한다.
- `searchRandSeed`, `nnRandSeed`, `nnRandomize`는 앱 코드에서 직접 override하지 않는다.
- 따라서 seed는 현재 앱 레벨 메뉴나 플레이 레벨 정책에 포함되어 있지 않다.

### 새 판 시작

[EngineSession.kt](/Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/EngineSession.kt)

- `startNewEngineGame()`은 `stop() -> initialize() -> newGame()` 순서다.
- 이유는 `clear_board`만으로는 같은 process 안의 search tree/cache가 충분히 격리되지 않아 반복 자동대국이 즉시 재생되는 현상이 확인됐기 때문이다.

### AI 착수 분석

[MatchPolicy.kt](/Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/match/MatchPolicy.kt)

- 사람 착수 후 AI 응수는 search tree 재사용을 유지한다.
- AI vs AI 자동대국은 AI 착수 분석 직전에 `engineAdapter.clearSearchCache()`를 호출한다.
- 그 다음 `analyze()` 결과의 후보 순서와 `MoveSelectionPolicy`로 착수한다.
- 비최고 단계는 앱 레벨 `Random.nextInt()`로 후보 구간에서 균등 랜덤 선택한다.

### KataGo adapter

[KataGoProcessEngineAdapter.kt](/Users/ryan9kim/worksoc/go-ai-coach/engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProcessEngineAdapter.kt)

- `clearSearchCache()`는 GTP `clear_cache`를 호출한다.
- `analyzeWithGtp()`는 현재 `maxVisits`, `maxTime`만 설정한다.
- `maxPlayouts`는 아직 사용하지 않는다.
- `kata-search_analyze <color> <centiseconds>` fast path를 사용한다.

## KataGo 설정에서 확인한 의미

로컬 `gtp_learning.cfg`의 search limit 주석은 다음 의미를 갖는다.

- `Playouts`: 현재 턴에서 새로 수행한 탐색량에 가깝다.
- `Visits`: 현재 턴의 새 탐색뿐 아니라 이전 턴에서 여전히 현재 국면에 적용 가능한 search tree도 포함한다.
- 예시 주석도 “이전 턴 200 nodes 중 상대 응수 후 50 nodes가 유효하면, visit limit 200은 새로 150 nodes만 더 검색할 수 있다”는 구조를 설명한다.

즉 search tree 재사용은 의도된 성능 최적화다. 한쪽 AI가 같은 대국을 이어가는 일반 엔진 사용에서는 좋은 기능이다.

## 왜 우리 앱에서는 문제가 됐나

문제 로그의 조건은 다음과 같았다.

- 같은 앱 process
- 같은 KataGo GTP process
- 같은 9x9 시작 국면 반복
- 양쪽 모두 AI
- 한쪽은 B64, 다른 쪽은 B16
- 최고 단계는 항상 최상위 후보 선택
- adapter는 `maxVisits`만 사용

이 조건에서는 B64가 깊게 본 search tree가 다음 B16 턴에 그대로 유효한 자식 국면으로 이어질 수 있다. 그러면 B16 요청은 “16 visits를 새로 하라”가 아니라 “이미 적용 가능한 visits가 있으면 그것도 포함해서 16 visits면 충분하다”로 동작할 수 있다.

그 결과 다음 현상이 발생했다.

- B16 또는 B64 분석이 1~4ms에 끝난다.
- 같은 시작 조건의 다음 판이 이전 판과 거의 같은 순서로 재생된다.
- 낮은 레벨이 실제보다 강해지거나, 높은 레벨의 직전 탐색 영향을 받는다.
- `빠른 초급 3단계`와 `중급 5단계` 같은 레벨 비교가 오염된다.

## B16은 B64의 트리를 얼마나 참조하는가

정확한 양은 국면과 B64가 실제로 탐색한 분기 분포에 따라 달라진다. 하지만 구조적으로는 다음처럼 이해하는 것이 맞다.

1. B64가 현재 국면에서 `Black E5`를 고르기 위해 search tree를 만든다.
2. 그 tree 안에는 `Black E5` 이후 White가 둘 수 있는 여러 응수와 그 하위 변화가 일부 포함될 수 있다.
3. 실제로 `Black E5`가 착수되면, 다음 White 차례의 새 root는 기존 tree의 `Black E5` 하위 subtree가 된다.
4. 같은 KataGo process가 이 subtree를 보존하면, White B16 분석은 이 subtree의 root visits를 자기 `maxVisits=16`에 포함할 수 있다.

따라서 B16이 B64가 본 "모든 tree"를 전부 커닝하는 것은 아니다. 현재 실제 국면으로 이어지는 하위 subtree만 재사용된다. 하지만 이 하위 subtree가 B64의 강한 탐색에서 생성된 것이라면, B16 입장에서는 자기 예산보다 훨씬 좋은 사전 정보를 받은 것과 비슷해진다.

이번 실기기 로그에서 B16이 1~4ms에 끝난 구간은 이 해석과 잘 맞는다. 요청이 B16이어도 이미 유효한 root visits가 충분하면 새 playout을 거의 하지 않고 반환할 수 있기 때문이다.

결론적으로 shared single process에서 B16 vs B64를 돌리면 B16은 "B16만의 독립 AI"가 아니다. 같은 process 안에서 직전 B64 탐색의 하위 subtree를 공유하는, 사실상 "B64가 일부 예습한 B16"에 가까워질 수 있다.

## 문제를 해소하는 선택지

### 선택지 A: AI vs AI에서만 `clear_cache`

현재 적용한 방식이다.

장점:

- B16/B32/B64 레벨 경계가 가장 명확하다.
- AI vs AI에서는 B16은 B16만큼, B64는 B64만큼 새로 탐색한다는 해석이 쉽다.
- AI vs AI 자동대국과 반복 회귀 테스트가 안정적이다.
- 사람 vs AI에서는 KataGo의 search tree reuse 장점을 유지해 속도와 품질을 얻는다.
- 구현이 단순하고 로그 해석이 쉽다.

단점:

- AI vs AI 자동대국에서는 KataGo의 좋은 search tree reuse 최적화를 포기한다.
- AI vs AI 자동대국은 느린 폰에서 체감 진행 속도가 느려질 수 있다.

현재 제품 단계에서는 이 방식을 기본값으로 유지하는 것이 맞다.

### 선택지 B: Black/White 엔진 process 분리

Black AI와 White AI가 별도 KataGo process를 갖는다.

장점:

- B64의 tree가 B16에게 직접 넘어가지 않는다.
- 각 진영 AI는 자기 process 안에서만 search tree reuse를 활용할 수 있다.
- AI vs AI에서 "각 AI가 자기 기억을 가진다"는 모델에 가깝다.

단점:

- 메모리와 CPU 비용이 커진다.
- Android 기기에서 process 2개는 부담이 크다.
- 사람 대국, 2P 분석, ScoreGraph, Endgame cleanup까지 어느 process가 authoritative engine인지 orchestration이 복잡해진다.

이 방식은 장기적으로 의미가 있지만, 현재 모바일 기본값으로 바로 적용하기에는 무겁다.

### 선택지 C: tree reuse + `maxPlayouts`

같은 process를 유지하되 `maxVisits`만 쓰지 않고 `maxPlayouts`를 도입한다.

장점:

- 기존 subtree를 재사용하면서도 매 턴 새로 수행할 탐색량을 보장할 수 있다.
- `clear_cache`보다 KataGo의 장점을 더 살릴 수 있다.

단점:

- 기존 subtree가 더해지므로 B16의 실제 강도는 여전히 B16 fresh보다 높을 수 있다.
- 레벨 정의가 `visits`가 아니라 `fresh playouts + inherited tree`로 바뀐다.
- 실측 없이 레벨 승률을 예측하기 어렵다.

이 방식은 "성능 우선 모드" 또는 "고급 엔진 모드"로 실험할 가치가 있다. 하지만 기본 레벨링 모드로 바로 쓰기에는 아직 검증이 부족하다.

### 선택지 D: random seed만 조정

권장하지 않는다.

seed는 탐색 흔들림과 재현성을 조절하지만, B64 subtree가 B16 root로 승격되는 구조를 막지 못한다. 이미 충분한 visits가 남아 있으면 seed가 달라도 새 탐색이 거의 없을 수 있다.

## 랜덤 seed로 해결 가능한가

부분적으로만 가능하다.

`searchRandSeed`, `nnRandSeed`, `nnRandomize`는 다음 용도에 가깝다.

- 같은 조건에서 결과를 재현하거나 흔들리게 조정
- NN 평가의 board orientation randomization 조정
- search 내부 난수 흐름 조정

하지만 seed는 search cache isolation이 아니다. 이미 이전 search tree가 현재 국면에 유효하고, `maxVisits`가 그 tree visits를 포함한다면, 새 seed가 있어도 새 탐색량이 거의 없을 수 있다. 새 탐색이 거의 없으면 다양성도 거의 발생하지 않는다.

따라서 “매판 random seed를 넣으면 프로세스 재시작이나 `clear_cache`가 필요 없다”는 결론은 현재 근거로는 위험하다.

## 좋은 재사용을 살리는 대안

### 1. 현재 기본: hybrid reuse policy

- 사람 vs AI: search tree reuse 유지
- AI vs AI: AI 착수 분석 직전 `clear_cache`
- `maxVisits`, `maxTime`으로 분석
- 장점: 사람 대국 성능을 유지하면서 AI vs AI 레벨 비교와 테스트가 명확하다.
- 단점: AI vs AI에서는 KataGo의 search tree reuse 이점을 버린다.

현재 사용자 테스트에서 착수 안정성과 레벨별 승률 기대가 가장 좋아졌으므로 기본값으로 유지한다.

### 2. 후보: reuse tree + maxPlayouts

- `clear_cache`를 끄거나 선택적으로 사용한다.
- `maxVisits` 대신 또는 함께 `maxPlayouts`를 설정한다.
- 목표는 이전 tree를 재사용하되, 매 턴 최소한 일정량의 새 탐색을 수행하게 하는 것이다.

주의점:

- `maxPlayouts=16`은 “B16 fresh search”와 같지 않다. 기존 tree가 더해지므로 실제 강도는 B16보다 높을 수 있다.
- AI vs AI에서 같은 process를 공유하면 한 진영의 깊은 탐색이 다른 진영에도 영향을 줄 수 있다.
- 이 모드는 “강도 보정 모드”가 아니라 “성능/품질 우선 모드”로 취급해야 한다.

### 3. 후보: side별 engine process 분리

- Black AI process와 White AI process를 분리한다.
- 각 진영은 자기 search tree를 유지하되, 상대 진영의 깊은 search tree를 직접 상속하지 않는다.

장점:

- AI vs AI 자동대국에서 레벨 오염이 줄어든다.
- 각 AI가 자기 관점에서 tree reuse를 활용할 수 있다.

단점:

- 메모리와 process lifecycle 비용이 증가한다.
- human vs AI, 2P 분석, ScoreGraph, Endgame cleanup까지 세션 orchestration이 복잡해진다.

### 4. 후보: seed experiment mode

- `searchRandSeed`, `nnRandSeed`, `nnRandomize`를 명시 제어한다.
- 반복 실험에서는 seed 고정으로 재현성을 얻고, 사용자 대국에서는 seed를 process 또는 game 단위로 바꾼다.

주의점:

- seed 제어는 다양성/재현성 실험이다.
- search tree 재사용에 따른 visit 오염을 직접 해결하지 않는다.

## 권장 결정

### 지금 유지할 기본값

현재는 다음 정책을 유지한다.

- 새 판 시작: fresh process
- 사람 vs AI 착수 분석: search tree reuse 유지
- AI vs AI 착수 분석: `clear_cache`
- 레벨링: 현재 `maxVisits` 기반 B16/B32/B64/B160
- 후보 다양성: 앱 레벨 후보 구간 랜덤 선택

이 정책은 느린 폰에서 조금 무겁지만, 레벨별 기대 승률과 디버깅 가능성이 가장 높다.

### 다음 실험

재사용을 다시 살리고 싶다면 바로 기본값을 바꾸지 말고, 다음 실험을 별도 모드로 진행한다.

1. `EngineSearchReusePolicy`를 둔다.
   - `IsolatedTurnSearch`
   - `ReuseTreeWithPlayoutBudget`
   - `ReuseTreeSameSideOnly`
   - `DeterministicTest`
2. `AnalysisLimit`에 `playouts` 또는 별도 `SearchBudget`을 추가한다.
3. `KataGoProcessEngineAdapter.applySearchLimit()`에서 `maxPlayouts`를 지원한다.
4. runtime log에 `searchReusePolicy`, `maxVisits`, `maxPlayouts`, `clearCache`, `seedPolicy`를 남긴다.
5. `B16 vs B32`, `B16 vs B64`, `B32 vs B64`를 각 모드로 50판 이상 비교한다.

## 최종 판단

이번에 꺼둔 것은 “엔진의 좋은 기능” 그 자체라기보다, 현재 앱이 아직 그 기능을 정확히 제어할 추상화가 없기 때문에 기본 대국 경로에서 잠시 격리한 것이다.

장기적으로는 search tree reuse를 다시 가져올 가치가 있다. 다만 그때의 핵심은 random seed가 아니라 `maxPlayouts`, side별 session, search reuse policy, 로그 기반 검증이다.
