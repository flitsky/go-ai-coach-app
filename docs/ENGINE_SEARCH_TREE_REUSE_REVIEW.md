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
- 이 동작은 `EngineSearchMode.GtpStatefulFast` 정책에만 해당한다.
- `EngineSearchMode.JsonPositionAnalysis`는 아직 기본 대국 경로에 연결하지 않았고, 이후 AI vs AI 실험 모드에서 별도 검증한다.
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

## 엔진 내부에서 B16/B32/B64를 분리 관리할 수 있는가

현재 우리가 쓰는 GTP fast path 기준 답은 "같은 KataGo GTP process 안에서 visits profile별 search tree namespace를 나눠 쓰는 기능은 현재 앱 경계에서는 없다"이다.

현재 구현은 다음 구조다.

1. `KataGoProcessEngineAdapter` 하나가 KataGo GTP process 하나를 소유한다.
2. `playMove()`로 같은 process의 보드 상태를 계속 전진시킨다.
3. `analyzeWithGtp()`는 `kata-set-param maxVisits`, `kata-set-param maxTime`으로 전역 search limit을 바꾼 뒤 `kata-search_analyze <color>`를 호출한다.
4. 다음 턴도 같은 process, 같은 보드 history 위에서 다시 search limit만 바꾼다.

이 구조에서 B16/B32/B64는 "별도 엔진"이 아니라 "같은 엔진 세션에 매 턴 다른 search limit을 설정하는 것"이다. 따라서 B64가 만든 현재 착수 하위 subtree가 다음 B16 턴의 root로 이어지면, B16은 그 subtree의 유효 visits를 자기 `maxVisits=16` 안에 포함할 수 있다.

KataGo 설정에는 `playouts`와 `visits`의 차이가 명시되어 있다. `visits`는 이전 턴에서 여전히 현재 턴에 적용 가능한 search tree를 포함할 수 있고, `playouts`는 이번 턴에 새로 수행한 탐색량에 더 가깝다. 따라서 현재처럼 `maxVisits`만 쓰는 한, 같은 process 안에서 "B16은 무조건 새로 16만 읽는다"는 보장은 없다.

### 가능한 분리 방식

| 방식 | B64 tree가 B16에 넘어가는가 | 장점 | 단점 | 현재 판단 |
| --- | --- | --- | --- | --- |
| 현재 기본: AI vs AI마다 `clear_cache` | 거의 차단 | 레벨 비교가 명확하고 구현 단순 | tree reuse 최적화 포기 | 기본 유지 |
| 흑/백 process 분리 | 상대 진영 tree는 직접 상속하지 않음 | 각 진영이 자기 기억만 유지 | Android 메모리/CPU/수명주기 부담 | 장기 후보 |
| B16/B32/B64 profile별 process pool | profile 간 tree는 분리 가능 | 레벨별 독립성 가장 명확 | process 수가 늘고 동기화 복잡 | 실험 후보, 기본 아님 |
| JSON analysis query 기반 move selection | GTP game tree 상속은 피하기 쉬움 | 요청별 position analysis에 가까움 | 현재 GTP fast path보다 무겁고 앱 orchestration 변경 필요 | 서버/고급 분석 후보 |
| `maxPlayouts` 기반 reuse | 기존 tree는 쓰되 새 탐색량 보장 | reuse 장점 일부 유지 | B16 fresh와 동일하지 않음 | 성능 모드 후보 |

### profile별 process pool은 가능한가

기술적으로는 가능하다. 예를 들어 B16용 KataGo process, B32용 KataGo process, B64용 KataGo process를 각각 띄우고, 매 착수마다 모든 process에 동일한 move history를 replay하거나 `syncToGameState()`를 수행한 뒤 해당 profile process에서만 분석하게 만들 수 있다.

이 방식이면 B64가 만든 tree가 B16 process 안으로 직접 들어오지는 않는다. 그러나 다음 문제가 있다.

- Android에서 KataGo process 여러 개는 메모리와 초기화 비용이 크다.
- 각 process에 board state를 정확히 sync해야 하므로 failure surface가 늘어난다.
- ScoreGraph, final score, dead stone cleanup, human hint 분석을 어느 process에서 할지 routing 정책이 필요하다.
- 원격 서버 엔진까지 고려하면 process pool보다 `EngineSessionClient` 수준의 "analysis session identity" 추상화가 먼저 필요하다.

따라서 지금 당장 기본값으로는 과하다. 다만 나중에 AI 자동대국/벤치마크 품질을 제품 기능으로 올린다면 `EngineSearchReusePolicy.ProfileIsolatedProcessPool` 같은 실험 모드로 검토할 가치가 있다.

### JSON analysis는 분리에 유리한가

상대적으로 유리하다. JSON analysis query는 요청마다 `moves`, `rules`, `boardXSize`, `boardYSize`, `maxVisits`를 명시해서 position analysis를 요청한다. 공식 Analysis Engine의 `clear_cache` 설명도 주로 neural net cache를 비우는 기능으로 설명되어 있으며, GTP 대국 process처럼 `playMove()`로 진행되는 단일 보드 search tree를 그대로 따라가는 모델과는 다르다.

다만 현재 앱의 실시간 AI 착수는 GTP fast path를 사용한다. JSON analysis path는 policy/refine/broad study에 더 가깝고, Android 실시간 대국에서 항상 쓰기에는 비용과 오류 표면을 더 검증해야 한다. 그래서 "profile 분리"의 장기 해법은 가능하지만, 지금 기본값은 `clear_cache`가 더 안전하다.

### JSON position analysis로 B16/B32/B64가 가능한가

가능하다. KataGo Analysis Engine의 query는 `maxVisits`를 요청별로 받을 수 있고, 우리 현재 코드도 `AnalysisLimit.toJsonAnalysisQuery()`에서 다음 값을 넣는다.

- `moves`: 현재까지의 전체 수순
- `analyzeTurns`: 현재 마지막 턴
- `maxVisits`: `AnalysisLimit.visits`
- `overrideSettings.maxTime`: `AnalysisLimit.timeMillis`
- `includePolicy`: policy 후보가 필요한 경우
- `boardXSize`, `boardYSize`, `rules`, `komi`

따라서 B16/B32/B64는 JSON 방식에서도 다음처럼 그대로 표현할 수 있다.

| 레벨 | JSON query `maxVisits` | JSON query `overrideSettings.maxTime` | 비고 |
| --- | ---: | ---: | --- |
| B16 | 16 | Search Time B16 값 | 빠른 초급 |
| B32 | 32 | Search Time B32 값 | 초급 |
| B64 | 64 | Search Time B64 값 | 중급 |

즉 JSON position analysis의 장점은 "visit 레벨 설정이 안 된다"가 아니라, 오히려 `maxVisits`를 요청 payload에 명시하므로 원격 서버/로컬 process/JNI 어디서든 같은 분석 계약으로 표현하기 쉽다는 점이다.

### JSON position analysis 장점

1. **position-scoped 요청**
   - 매 요청에 전체 `moves`, board size, rules, komi가 들어간다.
   - GTP process의 현재 보드 상태가 맞는지에 덜 의존한다.
   - 원격 서버 엔진으로 바꿀 때도 API payload가 자연스럽다.

2. **AI vs AI visit 오염 완화**
   - GTP `playMove()`로 이어지는 단일 game tree를 그대로 상속하는 방식이 아니다.
   - B64가 만든 GTP 하위 subtree가 다음 B16 root로 직접 승격되는 문제를 피하기 쉽다.
   - 다만 같은 analysis process의 NN cache는 공유될 수 있다. 이것은 search tree budget 오염과는 성격이 다르며, 보통 성능 최적화에 가깝다.

3. **분석 기능 확장성**
   - `includePolicy`, `includeOwnership`, `includeMovesOwnership`, `rootPolicyTemperature`, `rootFpuReductionMax`, `overrideSettings`를 요청별로 다룰 수 있다.
   - Top Moves, ownership, 학습용 broad analysis, 서버 분석 모드로 확장하기 좋다.

4. **미들웨어 계층화에 유리**
   - `analyzePosition(state, budget)` 형태로 만들면 local process와 remote server가 같은 계약을 공유한다.
   - 앱의 canonical `GameState`를 payload로 보내므로, "엔진 내부 상태가 지금 맞는가" 문제를 줄일 수 있다.

### JSON position analysis 단점

1. **실시간 대국 latency 검증 필요**
   - 현재 GTP fast path는 이미 가볍게 동작한다.
   - JSON path는 요청 직렬화, analysis process, response parsing 비용이 추가된다.
   - Android 폰에서 B16/B32/B64 실제 지연시간을 다시 측정해야 한다.

2. **tree reuse 장점을 덜 활용할 수 있음**
   - 사람 vs AI에서 같은 AI가 계속 읽기를 이어가는 장점은 GTP stateful search tree가 더 자연스럽다.
   - JSON position analysis는 공정성과 분리에 유리하지만, 매 턴 새 position analysis에 가까워져 기존 GTP reuse의 속도/품질 이점을 일부 잃을 수 있다.

3. **현재 엔진 기능 분리 비용**
   - 현재 final score, dead stone cleanup, GTP `playMove()` sync는 GTP process 경로에 남아 있다.
   - AI 착수만 JSON으로 고르면, 선택된 수를 GTP process에도 동기화해야 한다.
   - 장기적으로는 `EngineSessionClient`가 "analysis engine"과 "game-state engine"을 명확히 분리해야 한다.

4. **분석 엔진 운영 이슈**
   - 로컬 맥북 Homebrew KataGo v1.16.4 Metal에서는 JSON analysis `clear_cache` special action이 SIGSEGV로 죽는 현상이 있었다.
   - Android bundled KataGo에서 동일 문제 여부는 별도 확인이 필요하다.
   - 다만 JSON position analysis를 요청별로 독립 사용한다면 매 턴 `clear_cache`가 필수는 아닐 수 있다.

### 현재 방식 대비 판단

JSON position analysis는 현재 GTP fast path 대비 **구조적 장점이 뚜렷하다**. 특히 서버 엔진 전환, AI vs AI 레벨 오염 완화, Top Moves/ownership/학습 분석 확장 면에서는 더 좋은 방향이다.

하지만 "지금 당장 기본 대국 경로를 모두 JSON으로 바꾸자"는 결론은 아직 이르다. 폰에서 B16/B32/B64 latency, root visits fill, 자동대국 승률 분포를 비교해야 한다.

권장 순서는 다음과 같다.

1. `EngineSearchMode.GtpStatefulFast`와 `EngineSearchMode.JsonPositionAnalysis`를 정책으로 분리한다. (2026-06-13 완료)
2. AI vs AI 자동대국에만 JSON position analysis 실험 모드를 먼저 추가한다.
3. B16 vs B32, B32 vs B64, B16 vs B64를 각 50판 이상 비교한다.
4. 폰에서 B16/B32/B64 latency와 `rootInfo.visits` fill을 수집한다.
5. 결과가 좋으면 AI vs AI 기본값을 JSON으로 전환하고, 사람 vs AI는 GTP reuse 유지 또는 옵션화한다.

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
