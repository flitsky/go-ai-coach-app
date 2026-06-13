# 엔진 API 호출 정책

작성일: 2026-06-10

## 한줄 결론

대국 중 착수 판단과 학습 피드백은 모두 `TurnAnalysis`라는 같은 개념의 엔진 분석 결과를 사용한다. UI는 엔진을 직접 호출하지 않고, middleware/application 계층이 목적별 분석 예산을 정한 뒤 `EngineCoreApi.analyze()` 뒤로 숨긴다.

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
   - `빠른 초급 1~3단계`: B16 GTP 후보 중 최상위 수
   - `초급 7단계`: B32 후보 중 최상위 수
   - `중급 5단계`: B64 후보 중 최상위 수
   - `고급 5단계`: B160 후보 중 최상위 수
4. 사람 차례에도 백그라운드 `TurnAnalysis`를 요청한다.
5. `Top Moves` 토글이 켜져 있으면 같은 분석 snapshot에서 후보수를 보드에 표시한다.
6. `Top Moves` 토글이 꺼져 있으면 후보수는 표시하지 않지만, 사용자가 착수했을 때 방금 둔 수가 best/green/yellow/orange/red/unknown 중 무엇인지 판단하는 리뷰 데이터로 쓴다.
7. 분석 후보에 없는 착점은 실수로 단정하지 않고 `unknown` 또는 회색 계열로 취급한다.
8. 과거 Top Moves용 `AnalysisResultCache`는 기본 비활성화한다. 대신 JSON position analysis 결과 중 `rootInfo.visits`가 요청 visits를 충족한 결과만 별도 `JsonPositionAnalysisCache`에 저장한다.
9. 사람 vs AI 또는 AI vs 사람 대국에서는 KataGo search tree 재사용을 유지한다. AI vs AI 자동대국에서만 한 진영의 깊은 탐색이 다른 진영의 낮은 레벨에 섞이지 않도록 착수 분석 직전 `clearSearchCache()`를 호출한다.
10. 엔진 탐색 방식은 `EngineSearchMode` 정책으로 분리한다. `빠른 초급`은 `GtpStatefulFast`, `초급` 이상 AI 캐릭터 레벨링은 `JsonPositionAnalysis`를 사용한다.

## 계층별 책임 원칙

현재 목표 구조는 “엔진 코어 API는 가능한 한 KataGo가 제공하는 원시 기능을 1:1로 열어두고, 제품 정책은 미들웨어/application 계층에서 감싼다”이다.

| 계층 | 책임 | 하지 않는 일 |
| --- | --- | --- |
| `EngineCoreApi` / `EngineAdapter` | `newGame`, `playMove`, `syncToGameState`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache` 같은 엔진 원시 기능을 노출 | AI 성격, 레벨링, UI 표시 색상, 사용자 설정 정책을 결정하지 않음 |
| `EngineSessionClient` / application middleware | search mode 선택, 목적별 analysis budget, AI character/level, Top Moves snapshot, 착수 리뷰, 종국 처리, 로컬/원격 엔진 routing을 조합 | Compose UI 상태에 직접 의존하지 않음 |
| match/referee/settings domain | 규칙 적용, 차례 전환, pass/pass 종국, 플레이어 설정, 자동대국 delay, search time 같은 게임 정책 관리 | KataGo process 명령을 직접 호출하지 않음 |
| UI / presentation | 미들웨어가 만든 상태와 표시 DTO를 렌더링하고 사용자 이벤트를 전달 | 원시 엔진 API를 직접 호출하지 않음 |

이 원칙의 이유는 나중에 engine implementation이 local GTP process, JNI/native library, remote server 중 무엇이 되더라도 상위 게임 UX와 학습 정책을 유지하기 위해서다. 원격 서버 전환 시에도 UI는 같은 `EngineSessionClient` 계약을 호출하고, middleware가 local/remote의 차이를 숨긴다.

## GTP fast와 JSON position analysis 운영 방향

앞으로 두 탐색 모드를 모두 제품 정책으로 유지한다.

| 모드 | 용도 | 장점 | 현재 상태 |
| --- | --- | --- | --- |
| `EngineSearchMode.GtpStatefulFast` | B16 빠른 대국 모드 | stateful GTP process와 search tree reuse로 체감이 빠름 | `빠른 초급` AI 착수 경로 |
| `EngineSearchMode.JsonPositionAnalysis` | B32 이상 AI 캐릭터 레벨링, 서버 호환, 다양한 후보/착수 리뷰 spot, 학습 분석 | 요청마다 `moves`, `maxVisits`, `maxTime`을 명시하는 position-scoped 분석 | `초급` 이상 AI 착수 경로 |

JSON 기반 운영의 목표는 다음과 같다.

1. AI 차례에는 JSON position analysis로 받은 `moveInfos.order` 후보를 AI character/level 정책에 넘긴다.
2. 각 레벨의 최고 단계는 항상 `order=0` 후보를 선택한다.
3. 비최고 단계는 반환된 후보 개수에 맞춰 percentile/range 기반으로 안전하게 축소 선택한다.
4. 사람 차례에도 같은 snapshot을 확보한다.
5. `Top Moves`가 켜져 있으면 snapshot 후보를 보드 위에 표시한다.
6. 사용자가 착수하면 직전 snapshot에서 해당 위치를 찾아 green/yellow/orange/red spot을 남긴다.
7. 직전 snapshot에 없는 착점은 추가 즉시 분석을 강제하지 않고 gray/unknown spot으로 남긴다.

이 방식의 핵심은 AI와 사람이 같은 `TurnAnalysis` snapshot을 공유한다는 점이다. 후보 표시, AI 착수, 착수 후 평가는 모두 같은 분석 데이터에서 파생되므로 일관성이 높다. 현재 구현은 먼저 AI 착수 경로에 적용했다. 사람 차례의 Top Moves/착수 리뷰를 JSON broad snapshot으로 전환하는 작업은 별도 단계로 둔다.

## 현재 구현 범위

2026-06-13 현재 모바일 기본 구현은 성능을 우선하되, B16/B32/B64 time cap은 사용자가 `Search Time` 메뉴에서 조정할 수 있다.

- AI 응수: `빠른 초급`은 B16/GTP/best-only로 빠르게 둔다. `초급` 이상은 B32 이상 visits/time과 JSON position analysis를 사용해 후보군을 받고 `MoveSelectionPolicy`로 레벨링한다.
- 사람 착수 리뷰: 사람 차례가 오면 fast best-1 분석을 백그라운드로 요청한다.
- Top Moves 표시: 사용자가 토글을 켜면 같은 fast best-1 snapshot을 보드에 표시한다.
- Analysis cache: 과거 Top Moves용 `AnalysisResultCache`는 기본 비활성이다. JSON position analysis 결과는 root visits 품질을 기록해 별도 디스크 cache에 최대 10개 저장한다.
- Broad study analysis: 전체 합법 착점, policy 후보, refine sweep, deep fallback은 기본 대국 경로에서 비활성이다.

즉 “항상 분석 snapshot을 만든다”는 정책은 유지하되, 폰 실시간 사람 차례에서는 아직 best-1 경량 snapshot만 자동 생성한다. 여러 색상의 후보 분포나 전체 착점 평가가 필요하면 별도 `StudyBroad` 예산으로 분리해서 켠다.

`EngineSearchMode`는 visits/time/candidate count 같은 search budget이 아니라, 엔진을 어떤 방식으로 orchestration할지 정하는 정책이다. `GtpStatefulFast`는 현재의 stateful GTP process와 search tree reuse를 활용하는 빠른 경로이고, `JsonPositionAnalysis`는 명시적 board position을 JSON query로 분석하는 경로다. 2026-06-13 현재 제품 정책은 레벨에 따라 두 경로를 함께 사용한다.

## Visit의 의미와 탐색 원리

### 핵심 요약

`visit`는 KataGo가 현재 루트 국면 또는 어떤 후보 수 하위 노드에 검색량을 얼마나 배정했는지 나타내는 단위다. `maxVisits=32`는 “정확히 모든 합법수 32개를 한 번씩 본다”가 아니라 “현재 검색 트리 기준 루트 방문수를 최대 32 수준으로 제한한다”에 가깝다.

KataGo는 신경망이 처음부터 모든 수를 같은 확률로 보지 않는다. 신경망 policy prior가 유망해 보이는 수를 제안하고, MCTS가 그 prior와 현재까지의 value, uncertainty, exploration 항을 조합해 다음 visit을 어느 수/변화도에 쓸지 계속 갱신한다. 따라서 낮은 visit에서는 높은 prior의 후보가 주로 탐색되고, 낮은 prior 수나 바깥 영역은 meaningful score를 받지 못할 수 있다.

### KataGo 공식 문서 기준 필드

KataGo JSON analysis 결과에는 크게 두 종류의 visit 값이 있다.

| 필드 | 의미 | 앱에서의 사용 |
| --- | --- | --- |
| `rootInfo.visits` | 분석 요청 턴의 루트 국면 전체 visits | 요청한 `maxVisits`가 실제로 채워졌는지 `fill=OK/SHORT` 진단에 사용 |
| `moveInfos[].visits` | 특정 후보 수의 child node가 받은 visits | 후보가 얼마나 실제 검색되었는지, 후보 품질 신뢰도 참고 |
| `moveInfos[].order` | KataGo가 매긴 후보 순위. `0`이 최상위 | AI 착수와 Top Moves 정렬의 1차 기준 |
| `moveInfos[].prior` | 신경망 policy prior | 초반 후보 탐색의 강한 힌트. 단, 최종 순위 자체는 아님 |
| `moveInfos[].scoreLead`, `winrate`, `utility` | 해당 후보의 평가값 | 그래프, 후보 표시, pointLoss 계산의 원천 |

공식 `Analysis_Engine.md`는 `moveInfos[].order`를 KataGo의 ordinal ranking으로 설명하고, `0`을 best로 둔다. 또한 `rootInfo`에는 요청 턴 자체의 `winrate`, `scoreLead`, `utility`, `visits`가 들어간다. 우리 앱이 “order를 신뢰한다”는 정책은 이 문서와 맞다.

### Visits와 playouts 차이

KataGo GTP config 주석 기준으로 `playouts`는 이번 턴에 새로 수행한 검색량이고, `visits`는 이번 턴에 새로 수행한 검색량뿐 아니라 이전 턴에서 현재 국면에도 여전히 유효한 search tree 일부를 포함할 수 있다.

예를 들어 직전 턴에 200개 노드를 검색했고 상대 응수 후 그중 50개가 현재 국면에도 유효하다면, `maxVisits=200`은 새로 150개 정도만 더 검색해 최종 tree size 200 수준이 될 수 있다. 반대로 `maxPlayouts=200`이라면 새 검색 200개를 더 수행해 최종 tree size는 250 수준이 될 수 있다.

현재 앱은 local KataGo 경로에서 `maxVisits`를 사용한다. 그래서 사람 vs AI에서는 search tree 재사용이 속도와 품질에 유리하지만, AI vs AI 레벨 비교에서는 한쪽의 깊은 search tree가 다른 쪽 B16/B32/B64 visit 레벨에 섞이는 오염이 생길 수 있다. 이 때문에 현재 정책은 AI vs AI 자동대국 직전만 `clearSearchCache()`를 호출한다.

### visit이 늘면 왜 더 강해지는가

visit이 늘면 보통 다음 효과가 생긴다.

1. 초반 policy prior가 높았던 후보의 주요 변화도가 더 깊게 검증된다.
2. 처음에는 애매하거나 낮게 보였던 후보도 exploration 항, root noise, uncertainty, policy 설정에 따라 추가로 검색될 기회를 얻는다.
3. 후보별 `scoreLead`, `winrate`, `pointLoss`의 분산이 줄고, tactical blind spot이 줄어들 수 있다.
4. 후보 순위 `order`가 낮은 visit 결과보다 안정될 가능성이 높다.

하지만 visit 증가는 선형 또는 지수적으로 강도를 보장하지 않는다. 특히 B16에서 B32로 2배 늘린다고 항상 “실력 2배”가 되지는 않는다. 이유는 다음과 같다.

- MCTS는 유망 후보에 비대칭적으로 visits를 배분한다. 이미 확실한 후보에는 추가 visit의 한계효용이 작을 수 있다.
- 9x9 초반처럼 유력 후보가 몇 개로 좁혀지는 국면에서는 16 visits만으로도 충분히 좋은 수를 찾는 경우가 있다.
- 반대로 전투/사활/패/종국 정리처럼 깊은 reading이 필요한 국면에서는 16과 32 차이가 크게 나타날 수 있다.
- time cap이 너무 짧으면 requested visits를 채우지 못해 `fill=SHORT`가 되고, visit 설정의 의미가 약해진다.
- 여러 후보를 학습용으로 넓게 보고 싶다면 visit만 늘리는 것보다 `includePolicy`, `wideRootNoise`, human policy exploration, refine sweep 같은 별도 broad analysis 설정이 더 직접적일 수 있다. 다만 이런 설정은 top move 정확도와 응답속도에 비용을 준다.

결론적으로 `visits`는 “엔진 사고량의 핵심 축”이지만, 성능은 대체로 sublinear하게 좋아진다고 보는 것이 현실적이다. 즉 방문수를 늘릴수록 평균적으로 더 좋은 수를 둘 가능성은 높아지지만, 증가분마다 얻는 개선폭은 줄어들 수 있고, 기기 속도와 time cap이 같은 수준으로 따라와야 한다.

### “초반 최적수 밖에서 더 좋은 수가 발견되는가?”

가능하다. 다만 조건이 있다.

MCTS는 처음부터 신경망 policy prior가 높은 수를 많이 본다. 이후 search 중 어떤 후보의 value가 예상보다 나쁘거나, 다른 후보가 예상보다 좋거나, exploration 항이 충분히 커지면 visits가 다른 후보로 이동한다. 이 과정에서 초반 top prior 밖의 수가 더 좋은 수로 올라올 수 있다.

그러나 낮은 visits에서는 매우 낮은 prior의 수가 의미 있게 검색되지 않을 수 있다. 그래서 “visit을 늘리면 바깥 영역에서 숨은 최적수를 찾는다”는 표현은 절반만 맞다. 더 정확히는 “visit을 늘리면 MCTS가 policy prior 밖의 후보를 검증할 기회가 증가하지만, 넓은 후보 분포를 보장하려면 broad analysis용 설정이 필요할 수 있다”이다.

KataGo analysis config에는 `wideRootNoise`가 있으며, 이 값을 크게 하면 top move를 덜 깊고 정확하게 보더라도 더 다양한 수를 평가하게 된다. 공식 주석도 분석 엔진의 기본값은 분석 용도에 맞춰 다양성을 주는 반면, 대국에서 playing strength를 극대화하려면 `wideRootNoise=0.0`이 적합하다고 설명한다. 따라서 실시간 대국과 학습용 광범위 후보 평가는 같은 설정으로 밀어붙이지 않는 것이 맞다.

### 현재 앱의 visit 적용 구조

현재 앱의 주요 경로는 두 가지다.

| 경로 | 코드 | visit 주입 방식 | 용도 |
| --- | --- | --- | --- |
| GTP fast path | `KataGoProcessEngineAdapter.analyzeWithGtp` | `kata-set-param maxVisits`, `kata-set-param maxTime`, `kata-search_analyze` | 기본 AI 응수, 빠른 Top Moves/리뷰 |
| JSON analysis path | `KataGoProcessEngineAdapter.analyzeWithJson` | query JSON의 `maxVisits`, `overrideSettings.maxTime` | policy 포함, refine, broad study 후보 |

`AnalysisLimit.effectiveAnalysisLimit()`는 `candidateCount * minVisitsPerCandidate`와 `minTimeMillis`가 설정된 경우 요청 visits/time을 올릴 수 있다. 기본 실시간 대국 preset인 `Lite`와 현재 `Learning`은 `minVisitsPerCandidate=0`, `minTimeMillis=null`이므로 후보 수를 늘린다고 자동으로 visit/time이 늘지는 않는다. 반면 `Balanced` 이상이나 향후 broad study preset에서는 후보당 최소 visits를 요구해 더 무거운 분석으로 승격될 수 있다.

JSON analysis path도 B16/B32/B64 같은 visit 레벨 설정을 그대로 표현할 수 있다. KataGo Analysis Engine query는 요청별 `maxVisits`를 받고, 우리 adapter도 `AnalysisLimit.visits`를 query JSON의 `maxVisits`에 넣는다. `timeMillis`는 `overrideSettings.maxTime`으로 들어간다. 따라서 JSON position analysis 전환의 쟁점은 "visit 설정 가능 여부"가 아니라, GTP stateful tree reuse를 포기하거나 줄이는 대신 position-scoped 요청, 원격 서버 호환성, AI vs AI 레벨 오염 완화라는 장점을 얻을지 여부다. 자세한 장단점은 `ENGINE_SEARCH_TREE_REUSE_REVIEW.md`의 JSON position analysis 섹션을 따른다.

현재 레벨별 기본 visits는 다음과 같다.

| 레벨 그룹 | visits | 기본 time cap | 후보 상한 | 의미 |
| --- | ---: | ---: | ---: | --- |
| 빠른 초급 | 16 | B16 Search Time 기본 1000ms | 8 | 폰 실시간 대국 우선 |
| 초급 | 32 | B32 Search Time 기본 2000ms | 16 | 더 안정적인 초급/학습 후보 |
| 중급 | 64 | B64 Search Time 기본 3000ms | 20 | 후보 순위 안정성 강화 |
| 고급 | 160 | 1000ms 현재 기본 | 24 | 추후 time cap 재검토 필요 |

주의할 점은 `visits=32`가 `candidateCount=16`개 후보를 모두 2 visits씩 본다는 뜻이 아니라는 점이다. 실제 scored 후보 수는 KataGo가 어떤 후보에 visits를 배정했는지에 따라 달라진다.

### 운영 판단

현재 정책은 다음과 같이 유지한다.

1. AI 착수 순위는 `order=0,1,2...`를 신뢰한다.
2. `pointLoss`는 order를 뒤집는 기준이 아니라 표시/학습 annotation이다.
3. 낮은 visit에서 후보 수가 적거나 `fill=SHORT`면 레벨링 구간을 안전하게 축소한다.
4. 사람 대국은 `maxVisits` + tree reuse로 빠르고 자연스럽게 둔다.
5. AI vs AI 비교/테스트는 `clearSearchCache()`로 search tree 오염을 막는다.
6. 향후 더 엄격한 레벨 비교를 원하면 `maxPlayouts` 기반 모드를 별도 실험한다.
7. 다양한 후보 spot이 필요한 학습 모드는 기본 대국 경로와 분리해 `StudyBroad`로 둔다.

### 근거 링크

- KataGo 공식 Analysis Engine 문서: `maxVisits`, `moveInfos`, `rootInfo`, `order`, `visits` 필드 설명
  https://github.com/lightvector/KataGo/blob/master/docs/Analysis_Engine.md
- KataGo 공식 GTP example config: `Playouts`와 `Visits` 차이, tree reuse 설명
  https://raw.githubusercontent.com/lightvector/KataGo/master/cpp/configs/gtp_example.cfg
- KataGo 공식 analysis example config: `wideRootNoise`, analysis thread, `maxVisits`, `includePolicy` 관련 설명
  https://raw.githubusercontent.com/lightvector/KataGo/master/cpp/configs/analysis_example.cfg

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

## JSON position analysis cache 정책

`JsonPositionAnalysisCache`는 기존 `AnalysisResultCache`와 별도다. 기존 cache는 Top Moves 표시/무르기 편의 중심의 임시 메모리 cache였고, 현재 기본 비활성이다. 새 cache는 JSON position analysis 결과의 root visit 품질을 함께 저장하는 제한적 position cache다.

저장 조건:

1. `EngineSearchMode.JsonPositionAnalysis`로 호출한 결과만 저장한다.
2. `AnalysisResult.rootVisits > 0`이고 후보 리스트가 비어 있지 않으면 저장할 수 있다.
3. cache key는 `GameState.analysisFingerprint()`, `EngineSearchMode`, `AnalysisLimit` 전체 값이다.
4. 저장 시 `createdAtMillis`, `requestedRootVisits`, `rootVisits`, 후보 리스트, summary를 함께 기록한다.
5. 같은 key의 기존 entry보다 `rootVisits`가 낮은 결과로는 덮어쓰지 않는다.

품질 등급:

| 등급 | 기준 | 자동 재사용 |
| --- | --- | --- |
| `complete` | `rootVisits >= requestedRootVisits` | 예 |
| `partial` | `rootVisits / requestedRootVisits >= 50%` | 예 |
| `diagnostic` | root visit은 있으나 50% 미만 | 아니오 |

`diagnostic` entry는 “이 기기/설정에서는 해당 time cap으로 충분히 채우지 못했다”는 판단 자료다. 자동 착수나 Top Moves 응답에는 재사용하지 않고, Copy Log의 `positionAnalysisCache` 통계와 cache hit summary에서 품질을 확인하는 용도로 둔다.

보관 정책:

| 항목 | 값 |
| --- | ---: |
| 최대 entry 수 | 10개 |
| 무효화 기간 | 30일 |
| 저장 위치 | app private `files/json_position_analysis_cache.json` |

이 cache는 “특정 국면의 JSON 분석 결과와 그 품질”을 재사용하기 위한 것이다. 따라서 GTP search tree cache, KataGo NN cache, Top Moves 임시 메모리 cache와 혼동하면 안 된다.

주의점:

- reusable cache hit이 있으면 같은 국면/같은 budget의 JSON 분석 호출은 생략할 수 있다.
- cache miss 또는 만료 시 기존처럼 엔진을 호출한다.
- `fill=SHORT`라도 50% 이상 채워진 결과는 `partial`로 재사용할 수 있다.
- 50% 미만 결과는 저장하더라도 `diagnostic`으로 분류해 자동 재사용하지 않는다.
- 모델/설정이 바뀌었을 때의 cache 무효화는 아직 파일 schema/version 갱신으로 처리할 예정이다. 엔진 모델 hash 기반 무효화는 다음 단계 후보로 둔다.

### 종국 후 cache 최적화

대국이 끝났고 현재 Player Setup에 `초급` 이상 JSON level AI가 포함되어 있으면, 앱은 사용자에게 “이번 판을 분석해 다음 플레이를 더 쾌적하게 할지” 묻는 prompt를 띄울 수 있다.

동의 시 application layer는 이번 판의 주요 포지션을 최대 10개 샘플링하고, 실제 플레이 cache key는 유지하되 실행 limit에서는 `timeMillis=null`을 사용한다. 즉 다음 플레이의 동일 budget key로 cache hit이 나도록 저장하면서, 최적화 실행 자체는 maxVisits를 채우는 쪽을 우선한다.

이 기능은 UI가 엔진 호출을 직접 조합하지 않는다. UI는 prompt accept/dismiss만 전달하고, `EngineSessionClient.optimizePositionAnalysisCache()`가 position sync, JSON analysis, cache write 정책을 캡슐화한다. 나중에 엔진이 remote server로 이동해도 같은 middleware API로 대체할 수 있어야 한다.

## 원격 폰 엔진 벤치마크 표준

원격 폰에서 엔진 모드별 순수 성능 데이터를 수집할 때는 ADB `run-as` 기반 benchmark를 기본 방식으로 사용한다.

이 방식은 앱 UI를 터치하지 않고, 설치된 debug 앱의 app-private 영역에서 실제 bundled KataGo binary/model/config를 같은 app uid로 직접 실행한다. 따라서 local Mac binary가 아니라 “그 폰에 설치된 앱이 실제로 쓰는 엔진 산출물”을 기준으로 GTP fast와 JSON position analysis를 비교할 수 있다.

표준 실행 예시는 다음과 같다.

```bash
ENGINE_PHONE_BENCHMARK_SERIAL=192.168.35.3:45513 \
ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES=3 \
ENGINE_PHONE_SEARCH_MODE_BENCHMARK_OUT=docs/engine-benchmark-logs/search-mode-phone-YYYYMMDD \
make engine-search-mode-benchmark-phone
```

직접 실행할 수도 있다.

```bash
python3 scripts/run-katago-search-mode-benchmark.py \
  --samples 3 \
  --time-cap-ms 10000 \
  --adb-serial 192.168.35.3:45513 \
  --out-dir docs/engine-benchmark-logs/search-mode-phone-YYYYMMDD
```

전제 조건:

1. `com.worksoc.goaicoach` debug/dev 앱이 폰에 설치되어 있어야 한다.
2. `run-as com.worksoc.goaicoach`가 가능한 debuggable build여야 한다.
3. 앱 private files 아래에 `files/katago/model.bin.gz`, `gtp_learning.cfg`, `analysis_learning.cfg`가 seed되어 있어야 한다.
4. ADB USB 또는 Wi-Fi debugging 연결이 `device` 상태여야 한다.

수집 기준:

- script는 `dumpsys package`에서 `legacyNativeLibraryDir`를 찾아 `libkatago.so`를 실행한다.
- GTP path는 app-private `gtp_learning.cfg`를 사용한다.
- JSON path는 app-private `analysis_learning.cfg`를 사용한다.
- `logDir`와 `homeDataDir`는 app-private `files/katago/...` 아래로 override한다.
- 출력은 `samples.jsonl`, `summary.json`, `summary.md`로 저장한다.

역할 구분:

| 방식 | 보는 것 | 보지 못하는 것 |
| --- | --- | --- |
| ADB `run-as` 원격 엔진 benchmark | 폰에 설치된 실제 엔진 binary/model/config의 GTP/JSON latency, root visits fill, mode별 상대 비용 | Compose UI, coroutine scheduling, 앱 lifecycle, benchmark popup, 실제 대국 UX |
| 앱 UX 기반 benchmark | 사용자가 실제로 체감하는 초기화, 팝업, 엔진 busy, 화면 반응성, 저장/복원과의 상호작용 | 엔진 모드만 떼어낸 순수 latency 비교가 어려움 |

따라서 엔진 모드 비교, B16/B32/B64 fill 확인, remote server/local engine 전환 전 데이터 수집은 `run-as` 방식을 우선한다. 실제 사용자 체감 품질 검증은 별도 UX benchmark와 debug report/logcat으로 보완한다.

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

1. `빠른 초급`은 B16 `GtpStatefulFast`로 후보 1개만 요청하고 최상위 수를 둔다.
2. `초급` 이상은 B32/B64/B160 `JsonPositionAnalysis`로 후보군을 요청한다.
3. AI vs AI 자동대국에서 GTP mode를 쓸 때만 착수 분석 직전에 `EngineAdapter.clearSearchCache()`를 호출한다. JSON mode는 position-scoped 요청이므로 별도 GTP tree cache 격리가 필요하지 않다.
4. 반환된 후보의 `engineOrder` 순서를 신뢰한다.
5. AI 레벨링은 이 order 순서 후보 리스트에서 선택 구간을 정해 수행한다.
6. 최고 단계는 항상 order 최상위 후보를 선택한다.
7. AI가 착수한 수는 같은 snapshot에서 찾아 색상 dot을 남긴다.
8. 후보에 없거나 `pointLoss`가 없으면 평가를 단정하지 않고 `unknown`으로 둔다.

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

- `shared`: `EngineCoreApi`, 호환 이름인 `EngineAdapter`, `AnalysisLimit`, `CandidateMove`, `MoveAnalysisSnapshot`, `PlayLevel`, `MoveSelectionPolicy` 같은 순수 모델과 정책을 둔다.
- `application`: `EngineSessionClient`를 통해 현재 화면 상태와 사용자 설정을 보고 어떤 목적의 `TurnAnalysis`가 필요한지 결정한다.
- `engine-android`: GTP process, JSON analysis process, 향후 JNI/remote 구현을 `EngineCoreApi`/`EngineAdapter` 뒤에 숨긴다.
- `ui`: 분석 요청을 직접 만들지 않는다. 버튼/토글 이벤트를 application 계층에 전달하고, 반환된 snapshot과 표시 DTO만 렌더링한다.

이 경계를 유지해야 process KataGo, JNI native engine, remote server로 바꿔도 상위 학습 UX가 흔들리지 않는다.

2026-06-12 기준으로 Compose UI는 저수준 `EngineCoreApi`/`EngineAdapter`를 직접 받지 않고 `EngineSessionClient`를 받는다. `EngineCoreApi`는 엔진 원시 기능의 최하위 계약이고, `EngineAdapter`는 기존 구현체를 위한 호환 이름이다. `EngineSessionClient`는 앱 대국 흐름에 필요한 고수준 미들웨어 계약이다.

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
