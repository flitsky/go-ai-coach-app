# 엔진 API 호출 정책

작성일: 2026-06-10

## 한줄 결론

대국 중 착수 판단과 학습 피드백은 모두 `TurnAnalysis`라는 같은 개념의 엔진 분석 결과를 사용한다. UI는 엔진을 직접 호출하지 않고, application 계층이 목적별 분석 예산을 정한 뒤 `EngineAdapter.analyze()` 뒤로 숨긴다.

## 먼저 볼 문서

- 이 문서: 엔진 API 호출 정책, 호출 비용 순서, AI/사람 턴 일관성 기준
- `docs/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md`: 실제 레벨별 visits/time/candidate count와 반복 대국 결과
- `docs/ENGINE_SEARCH_TREE_REUSE_REVIEW.md`: KataGo search tree reuse와 AI vs AI 격리 정책
- `docs/archive/2026-06-docs-consolidation/TOP_MOVES_VALUE_GUIDE.md`: 후보 순위, `pointLoss`, 보드 숫자 표시 기준의 과거 상세 기록
- `docs/archive/2026-06-docs-consolidation/ENGINE_ANALYSIS_CONSISTENCY_REVIEW.md`: KataGo `order`, `scoreLead`, `pointLoss` 해석 기준 상세 기록
- `docs/archive/2026-06-docs-consolidation/ENGINE_ANALYSIS_CACHE_POLICY.md`: 분석 cache 비활성화 이유와 재도입 조건

## 현재 결정

1. AI 차례에는 항상 현재 진영의 플레이 레벨로 `TurnAnalysis`를 요청한다.
2. AI는 반환된 후보를 `MoveSelectionPolicy`로 레벨링해 착수한다.
3. 각 레벨 그룹의 최고 단계는 항상 엔진 후보 순위의 최상위 수를 둔다.
   - `빠른 초급 3단계`: B16 후보 중 최상위 수
   - `초급 7단계`: B32 후보 중 최상위 수
   - `중급 5단계`: B64 후보 중 최상위 수
   - `고급 5단계`: B160 후보 중 최상위 수
4. 사람 차례에도 백그라운드 `TurnAnalysis`를 요청한다.
5. `Top Moves` 토글이 켜져 있으면 같은 분석 snapshot에서 후보수를 보드에 표시한다.
6. `Top Moves` 토글이 꺼져 있으면 후보수는 표시하지 않지만, 사용자가 착수했을 때 방금 둔 수가 best/green/yellow/orange/red/unknown 중 무엇인지 판단하는 리뷰 데이터로 쓴다.
7. 분석 후보에 없는 착점은 실수로 단정하지 않고 `unknown` 또는 회색 계열로 취급한다.
8. `AnalysisResultCache`는 기본 비활성화한다. 같은 fingerprint가 있더라도 이전 분석 결과를 재사용하지 않는다.
9. 사람 vs AI 또는 AI vs 사람 대국에서는 KataGo search tree 재사용을 유지한다. AI vs AI 자동대국에서만 한 진영의 깊은 탐색이 다른 진영의 낮은 레벨에 섞이지 않도록 착수 분석 직전 `clearSearchCache()`를 호출한다.

## 현재 구현 범위

2026-06-11 현재 모바일 기본 구현은 성능을 우선하되, B16/B32/B64 time cap은 사용자가 `Search Time` 메뉴에서 조정할 수 있다.

- AI 응수: 레벨별 visits/candidate count와 `Search Time`의 visits별 time cap으로 `EngineAdapter.analyze()`를 호출한다.
- 사람 착수 리뷰: 사람 차례가 오면 fast best-1 분석을 백그라운드로 요청한다.
- Top Moves 표시: 사용자가 토글을 켜면 같은 fast best-1 snapshot을 보드에 표시한다.
- Analysis cache: 기본 비활성. 현재 턴의 snapshot 표시만 유지하고, 이전 국면 cache hit 재사용은 하지 않는다.
- Broad study analysis: 전체 합법 착점, policy 후보, refine sweep, deep fallback은 기본 대국 경로에서 비활성이다.

즉 “항상 분석 snapshot을 만든다”는 정책은 유지하되, 폰 실시간 대국에서는 best-1 경량 snapshot만 자동 생성한다. 여러 색상의 후보 분포나 전체 착점 평가가 필요하면 별도 `StudyBroad` 예산으로 분리해서 켠다.

## 엔진 호출 방식 우선순위

대국 중 기본 경로는 아래 순서를 따른다. 위쪽일수록 가볍고, 아래쪽일수록 분석 정보는 풍부하지만 느리다.

| 우선순위 | 호출 방식 | 상대 비용 | 현재 사용 여부 | 용도 |
| ---: | --- | --- | --- | --- |
| 1 | `playMove()` / `newGame()` / `syncToGameState()` | 매우 낮음 | 항상 사용 | 엔진 상태 동기화. 분석 정보 없음 |
| 2 | `analyze(fastCandidateAnalysis)` | 낮음 | 기본 대국 경로 | AI best move 확보, 사람 착수 리뷰, Top Moves 표시 |
| 3 | `estimateScore(scoreGraphAnalysisLimit)` | 낮음-중간 | 그래프/Eval | Score / Win Rate / ownership 추정 |
| 4 | `deadStones()` + `scoreFinal()` | 중간 | 종국 pass/pass | 사석 정리와 최종 계가 |
| 5 | JSON broad analysis, `includePolicy=true` | 높음 | 기본 경로 비활성 | 여러 후보/정책 후보 확보 |
| 6 | policy refine / sweep / deep fallback | 매우 높음 | 기본 경로 비활성 | 모든 합법 착점 평가, 복기/학습 모드 |

현재 가장 효율적인 기본 호출은 2번이다.

```text
EngineAdapter.analyze(
    visits = 플레이 레벨 visits,
    timeMillis = Search Time의 visits별 time cap,
    candidateCount = 목적별 후보 수,
    includePolicy = false,
    refinePolicyMoves = 0,
    minVisitsPerCandidate = 0,
    minTimeMillis = null,
)
```

KataGo process adapter에서는 이 조건일 때 JSON analysis process를 피하고 GTP `kata-search_analyze` 빠른 경로를 우선 사용한다.

## `candidateCount` 의미

`candidateCount`는 “엔진이 반드시 그 개수만큼 깊게 평가해야 한다”는 강제값이 아니다. 현재 fast path에서는 다음처럼 동작한다.

- `maxVisits`와 `maxTime`이 실제 탐색량과 응답시간의 주된 상한이다.
- `candidateCount`는 앱이 엔진 응답 후보를 최대 몇 개까지 파싱/보관/표시/레벨링에 사용할지 정하는 상한이다.
- GTP `kata-search_analyze` 명령에는 후보 개수를 직접 넘기지 않는다.
- 따라서 `candidateCount=10`을 넣어도 scored 후보가 1개, 3개, 10개 등으로 달라질 수 있다.
- scored 후보가 부족하면 앱은 policy/legal fallback 후보를 채울 수 있지만, `pointLoss`가 없는 fallback 후보는 레벨링/색상 평가에 쓰지 않는다.

현재 fast path에서는 `minVisitsPerCandidate=0`, `minTimeMillis=null`을 사용하므로 `candidateCount=10` 자체가 방문수나 시간 상한을 자동으로 늘리지는 않는다. 다만 더 많은 후보를 의미 있게 받으려면 같은 visits/time 안에서 엔진이 그만큼 후보를 반환해야 하므로, 느린 기기나 낮은 visits에서는 후보 수가 적을 수 있다.

운영 정책은 다음과 같이 둔다.

| 요청 후보 수 | 실제 scored 후보 수 | 운영 방식 |
| ---: | ---: | --- |
| 10 | 1 | 최상위 후보만 사용. 레벨링 불가 |
| 10 | 2 | 최상위/하위 후보 정도로만 약한 레벨링 |
| 10 | 3 | 최적수 / 중간수 / 최하수 구간 레벨링 가능 |
| 10 | 4 이상 | percentile 기반 레벨링 가능 |

즉 앱 정책은 “항상 10개가 온다”가 아니라 “최대 10개를 요청하고, 실제 scored 후보 수에 맞춰 안전하게 축소 운영한다”로 둔다.

## 호출 목적별 예산

| 목적 | 현재 예산 | 사용처 |
| --- | --- | --- |
| `AiMoveSelection` | 플레이 레벨 visits/candidate count + Search Time time cap, `policy=false`, `refine=0` | AI 착수 선택 |
| `HumanMoveReview` | fast best-1, `policy=false`, `refine=0` | 사용자가 둔 수의 사후 평가 |
| `TopMovesDisplay` | fast best-1, `policy=false`, `refine=0` | 보드 위 후보수 표시 |
| `ScoreGraph` | 후보 1개 score estimate | Score / Win Rate 그래프 |
| `Benchmark` | 사용자 설정과 무관한 고정 B16/B32/B64 | 기기별 엔진 성능 측정 |

`Benchmark`는 사용자 Player Setup, Top Moves 토글, 현재 계가 규칙, analysis cache를 절대 참조하지 않는다.

### Benchmark와 실제 대국 속도

현재 startup benchmark는 실제 대국 속도를 그대로 예측하는 기능이 아니다. 기기 진단을 위해 B16/B32/B64를 모두 `timeCapMs=5000`으로 측정한다. 반면 실제 대국은 `Search Time`에서 사용자가 고른 time cap을 사용한다. 기본값은 B16 `1000ms`, B32 `2000ms`, B64 `3000ms`이다.

따라서 benchmark 평균은 “이 기기가 장시간 진단 호출에서 visits를 얼마나 채우는가”를 보는 보조 지표다. 메뉴의 `추천[...]`에는 이 평균값을 보여주지만, 실제 대국 체감은 사용자가 선택한 `Search Time` 값과 목적별 `TurnAnalysis` 예산을 기준으로 판단한다.

`Search Time` 기본 선택지는 다음과 같다.

| 타입 | 기본값 | 선택지 |
| --- | ---: | --- |
| B16 | 1000ms | 500ms, 1000ms, 1500ms, 2000ms, 2500ms |
| B32 | 2000ms | 1000ms, 2000ms, 3000ms, 4000ms, 5000ms |
| B64 | 3000ms | 3000ms, 4500ms, 6000ms, 7500ms, 9000ms |

## 턴별 일관성 정책

AI 차례와 사람 차례는 같은 `TurnAnalysis` snapshot 개념을 사용한다.

### AI 차례

1. 현재 AI 진영의 플레이 레벨로 fast `TurnAnalysis`를 요청한다.
2. AI vs AI 자동대국이면 착수 분석 직전에 `EngineAdapter.clearSearchCache()`를 호출한다. 사람과 AI가 두는 대국이면 search tree 재사용을 유지한다.
3. 반환된 후보의 `engineOrder` 순서를 신뢰한다.
4. AI 레벨링은 이 order 순서 후보 리스트에서 선택 구간을 정해 수행한다.
5. 최고 단계는 항상 order 최상위 후보를 선택한다.
6. AI가 착수한 수는 같은 snapshot에서 찾아 색상 dot을 남긴다.
7. 후보에 없거나 `pointLoss`가 없으면 평가를 단정하지 않고 `unknown`으로 둔다.

`clearSearchCache()`는 앱 레벨 분석 cache와 다른 경계다. 앱 cache는 이전 국면의 결과 재사용 여부를 다루고, `clearSearchCache()`는 KataGo process 내부 검색 트리/NN cache가 직전 턴 또는 이전 판의 국면을 과도하게 재사용하지 못하게 막기 위한 엔진 경계다. 현재 기본 정책은 사람 대국에서는 이 재사용을 장점으로 보고 유지하며, AI vs AI 자동대국에서만 shared process의 진영 간 오염을 막기 위해 호출한다.

search tree 재사용 자체는 나쁜 기능이 아니다. KataGo의 원래 성능 최적화이며, 한쪽 AI가 같은 대국을 이어갈 때는 유용하다. 현재 AI vs AI 자동대국에서만 격리하는 이유는 `maxVisits`가 이전 턴에서 유효한 tree visits를 포함할 수 있어, B16/B32/B64 같은 visit 기반 레벨링이 오염되기 때문이다. 사람 vs AI에서는 같은 AI가 자기 읽기를 이어가는 구조이므로 재사용을 유지하는 편이 속도와 품질 측면에서 유리하다.

### 랜덤 시드와 search cache

KataGo config에는 `nnRandSeed`, `searchRandSeed`, `nnRandomize`가 있다. 이 값들은 검색/NN 평가의 랜덤성 또는 재현성을 조정하는 도구다. 그러나 search cache isolation의 대체재는 아니다.

KataGo GTP config 주석 기준으로 `visits`는 현재 턴에서 새로 수행한 playout뿐 아니라 이전 턴에서 여전히 현재 국면에 적용 가능한 search tree도 포함할 수 있다. 즉 같은 process 안에서 직전 검색 트리가 살아 있으면, 랜덤 시드가 달라도 “이미 유효한 검색량”이 남아 빠르게 반환될 수 있다.

현재 정책은 다음과 같이 둔다.

- 사용자 대국 다양성: 앱 레벨 후보 구간 랜덤 선택과 KataGo 기본 search randomness를 활용한다.
- 강도 검증/회귀 테스트: 필요하면 `searchRandSeed` 또는 `nnRandSeed`를 고정하는 별도 실험 모드를 둔다.
- 공정한 AI vs AI 레벨 비교: `clearSearchCache()`를 유지한다.
- 사람 vs AI 대국: search tree 재사용을 유지한다.
- 새 판 반복 테스트: `startNewEngineGame()`의 fresh process 정책을 유지한다.

따라서 “매판 random seed를 넣으면 프로세스 재시작이나 `clear_cache`가 불필요한가?”에 대한 현재 결론은 아니오다. 랜덤 시드는 다양성/재현성 제어 수단이고, `clear_cache`/fresh process는 엔진 내부 상태 격리 수단이다.

재사용을 다시 살릴 후보는 random seed보다 `maxPlayouts` 기반 정책이다. `maxPlayouts`는 이전 tree visits를 포함하는 `maxVisits`와 달리 새로 수행할 탐색량을 더 직접적으로 제한할 수 있다. 다만 기존 tree 위에 새 playout을 더하는 방식이므로, 강도 보정 모드와는 별도인 “성능/품질 우선 모드”로 검증해야 한다. 자세한 검토는 `ENGINE_SEARCH_TREE_REUSE_REVIEW.md`를 따른다.

### 사람 차례

1. 사람 차례가 오면 fast `TurnAnalysis`를 요청해 best move snapshot을 확보한다.
2. `Top Moves` 또는 향후 `Best Move` 표시 옵션이 켜져 있으면, 이 snapshot의 후보를 보드 위에 표시한다.
3. 옵션이 꺼져 있으면 후보는 표시하지 않지만 snapshot은 착수 리뷰용으로 유지한다.
4. 사람이 둔 위치가 snapshot 후보에 있으면 `pointLoss` 기준으로 green/yellow/orange/red dot을 남긴다.
5. 사람이 둔 위치가 snapshot 후보에 없으면 회색 `unknown` dot을 남긴다.

회색 dot은 “나쁜 수”가 아니라 “현재 경량 분석에서 점수 평가가 없는 수”라는 의미로 고정한다. 이 정책은 추가 엔진 호출을 줄여 대국 리듬을 유지하는 데 유리하다.

임의 착점까지 정확히 평가해야 하는 학습 모드에서는 별도 `StudyBroad` 또는 “착수 후 단일 후보 재분석”을 추가할 수 있다. 다만 이 기능은 기본 대국 경로에 섞지 않는다.

## 계층 경계

- `shared`: `EngineAdapter`, `AnalysisLimit`, `CandidateMove`, `MoveAnalysisSnapshot`, `PlayLevel`, `MoveSelectionPolicy` 같은 순수 모델과 정책을 둔다.
- `application`: `EngineSessionClient`를 통해 현재 화면 상태와 사용자 설정을 보고 어떤 목적의 `TurnAnalysis`가 필요한지 결정한다.
- `engine-android`: GTP process, JSON analysis process, 향후 JNI/remote 구현을 `EngineAdapter` 뒤에 숨긴다.
- `ui`: 분석 요청을 직접 만들지 않는다. 버튼/토글 이벤트를 application 계층에 전달하고, 반환된 snapshot과 표시 DTO만 렌더링한다.

이 경계를 유지해야 process KataGo, JNI native engine, remote server로 바꿔도 상위 학습 UX가 흔들리지 않는다.

2026-06-12 기준으로 Compose UI는 저수준 `EngineAdapter`를 직접 받지 않고 `EngineSessionClient`를 받는다. `EngineAdapter`는 로컬 process/JNI/remote transport의 최하위 계약이고, `EngineSessionClient`는 앱 대국 흐름에 필요한 고수준 계약이다.

서버 엔진을 고려한 중요한 차이는 다음과 같다.

- local process는 내부 보드 상태를 sync한 뒤 분석한다.
- remote server는 매 요청에 보드 상태를 payload로 받을 수 있다.
- 따라서 UI에서 발생하는 Top Moves 분석은 `analyzePosition(state, limit)`처럼 명시적인 `GameState`를 포함한다.
- local 구현인 `AdapterEngineSessionClient`는 `syncToGameState(state)` 후 `EngineAdapter.analyze(limit)`를 호출한다.
- future `RemoteEngineSessionClient`는 같은 계약을 HTTP/gRPC 요청으로 변환하면 된다.
- engine-specific feature gate는 문자열 진단문이 아니라 `EngineSessionCapabilities`로 판단한다. 예를 들어 기기 benchmark는 local process capability가 있을 때만 실행하고, remote server 엔진에서는 기본적으로 비활성화한다.

## 후보 순위와 평가값

- 후보 순위는 KataGo `moveInfos.order`를 우선한다.
- `pointLoss`는 후보 순서를 뒤집는 기준이 아니라 색상/숫자 annotation이다.
- `scoreLead`는 그래프와 진단용 값이다.
- `pointLoss`가 없는 후보는 표시나 리뷰에서 `unknown`으로 다룬다.
- `pointLoss`는 앱 내부에서 0 이상 손실값으로 유지한다. 보드 숫자는 KaTrain식으로 `-pointLoss`를 표시할 수 있지만, 내부 모델에는 음수 이득 의미를 섞지 않는다.

자세한 값 해석의 과거 상세 기록은 `archive/2026-06-docs-consolidation/TOP_MOVES_VALUE_GUIDE.md`와 `archive/2026-06-docs-consolidation/ENGINE_ANALYSIS_CONSISTENCY_REVIEW.md`를 따른다.

## 오래된 문서 처리

다음 문서는 당시 분석 근거로는 유효하지만, 현재 기본 호출 정책과 다를 수 있어 아카이브로 이동했다.

- `docs/archive/2026-06-engine-policy-superseded/GREEN_SPOT_HINT_DECISION.md`
- `docs/archive/2026-06-engine-policy-superseded/KATRAIN_TOP_MOVES_ANALYSIS.md`

새 구현이나 의사결정은 이 문서를 우선 기준으로 삼는다. 아카이브 문서는 KaTrain 분석 히스토리와 broad study analysis 후보를 볼 때만 참고한다.

## 다음 리팩토링 방향

1. `GoCoachApp.kt`의 coroutine orchestration을 controller/service로 옮긴다.
2. `TurnAnalysis` 요청, cache hit/miss, snapshot apply를 독립 application service로 분리한다.
3. `TopMovesDisplay`와 `HumanMoveReview`를 같은 snapshot에서 파생하되 UI 표시 정책만 분리한다.
4. broad study analysis를 별도 메뉴/모드로 만들 때도 기존 `MoveAnalysisSnapshot`에 merge하는 방식으로 확장한다.
5. 기기 벤치마크 결과를 바탕으로 fast best-1 분석의 time cap을 기기별로 조정한다.
