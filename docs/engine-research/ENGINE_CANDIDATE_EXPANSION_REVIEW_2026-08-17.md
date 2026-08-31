# 엔진 후보수 확장 검토 — 빠른 초급/초급 더블체크 + 레벨링용 후보 확장 레버

작성일: 2026-08-17

상태: 방향성 검토 문서. 이 문서는 **앱(Kotlin) 코드를 바꾸지 않는다.** 결론은 "다음에 무엇을 실험/구현할지"에 대한 권장안이며, 실제 반영은 별도 착수로 분리한다.

## 배경 질문 (사용자 요청 원문 요약)

1. `빠른 초급`/`초급` 두 모드에 대해 기존 문서를 더블체크하고 재정리한다.
2. 앞으로 AI 난이도 조정이 원활하도록, **후보수(candidate moves) 반환이 많이 되는 방식**을 찾아 정립한다. 단, 너무 느려지면 안 된다.
3. 기억 확인: `빠른 초급`은 엔진의 "빠른 리서치" 기능, `초급`은 "JSON 탐색" 기능을 쓴 것 같다 — 그리고 후보수를 늘리는 데는 JSON 탐색 쪽이 유리할 수 있다.
4. 엔진 분석 시 `16/32/64` 모드와 각각의 시간 제한을 걸 수 있었던 것 같다.
5. 앱을 매번 빌드/실행하지 않고, 터미널의 Python으로 엔진 서치 기능을 먼저 고도화한 뒤 앱에 탑재하는 방법을 고려해달라.

아래는 이 5개 질문에 대한 더블체크 결과와, 그 위에서 새로 실측한 데이터, 그리고 권장 방향이다.

## 1. 더블체크: `빠른 초급`/`초급`은 이미 잘 정리돼 있었다

사용자가 기억하는 "깊이 있게 정리된 문서"는 [`docs/ENGINE.md`](../ENGINE.md)와 그 딥다이브인 [`docs/ENGINE_API_CALL_POLICY.md`](../ENGINE_API_CALL_POLICY.md)다. 오늘 이 두 문서를 실제 코드(`shared/src/commonMain/kotlin/.../PlayLevel.kt`, `EngineAnalysisPolicy.kt`)와 한 줄씩 대조했다.

### 1-1. 레벨별 표는 코드와 정확히 일치한다

| 레벨 그룹 | 탐색 모드 | visits | 기본 time cap | candidateCount 상한 |
| --- | --- | ---: | ---: | ---: |
| 빠른 초급 (1~3단계) | `GtpStatefulFast` | 16 | 1000ms (B16) | 8 |
| 초급 (1~7단계) | `JsonPositionAnalysis` | 32 | 2000ms (B32) | 16 |
| 중급 (1~5단계) | `JsonPositionAnalysis` | 64 | 3000ms (B64) | 20 |
| 고급 (1~5단계) | `JsonPositionAnalysis` | 160 | 1000ms | 24 |

`PlayLevelGroup`(`shared/PlayLevel.kt:16-55`)의 `visits`/`timeMillis`/`candidateCount` 값이 이 표와 완전히 같다. `아이MoveSearchMode()`(`shared/EngineAnalysisPolicy.kt:46-51`)도 문서에 적힌 `if (group == FastBeginner) GtpStatefulFast else JsonPositionAnalysis` 그대로다. **사용자의 기억 3, 4번은 정확하다** — `빠른 초급`은 GTP stateful fast(빠른 리서치), `초급` 이상은 JSON position analysis이고, `16/32/64` 방문수와 그에 딸린 time cap(사용자가 `Search Time` 메뉴에서 조정 가능한 B16/B32/B64 프리셋)이 실제로 존재한다.

### 1-2. 문서에 없던 것 두 가지를 코드에서 발견했다

문서 표만 보면 "빠른 초급도 최대 8개, 초급도 최대 16개 후보를 받는다"처럼 읽히지만, 실제 **AI 착수 선택 경로**는 이보다 더 좁게 동작한다.

**(a) 빠른 초급 3단계(BestOnly)는 8개가 아니라 1개만 요청한다.**

```kotlin
// shared/EngineAnalysisPolicy.kt:53-70
EngineSearchMode.GtpStatefulFast -> {
    val count = if (selectionPolicy is MoveSelectionPolicy.BestOnly) 1 else base.candidateCount
    base.fastCandidateAnalysis(candidateCount = count)
}
```

`빠른 초급` 1~2단계(퍼센타일 선택)는 문서대로 최대 8개를 요청하지만, 3단계(`BestOnly`)는 엔진에 후보 1개만 요청한다. 후보수 확장 논의에서 `빠른 초급`을 제외하는 근거 중 하나다.

**(b) `초급` 이상(JSON 경로)의 AI 착수는 `refinePolicyMoves`/`minVisitsPerCandidate`가 항상 0으로 꺼져 있다.**

```kotlin
// shared/EngineAnalysisPolicy.kt:53-70
EngineSearchMode.JsonPositionAnalysis ->
    base.copy(
        includePolicy = true,
        refinePolicyMoves = 0,      // <- 항상 0
        minVisitsPerCandidate = 0,  // <- 항상 0
        minTimeMillis = null,
    )
```

이건 중요한 발견이다. 아래 3절에서 자세히 다루지만, 요약하면: **엔진 어댑터에는 이미 "후보를 늘리는 기능"이 구현돼 있는데, AI 착수(레벨링) 경로에서는 지금 그 기능을 쓰지 않는다.** `TopMovesDisplay`/`HumanMoveReview` 경로도 마찬가지로 `fastCandidateAnalysis()`를 거치며 항상 `refinePolicyMoves=0`으로 정규화된다(`EngineAnalysisPolicy.kt:10-22`). 즉 2026-08-17 기준 이 앱은 **어떤 화면에서도 `refinePolicyMoves > 0`인 요청을 실제로 보내지 않는다.**

이 결론은 코드 전체 grep(`grep -rn "aiMoveAnalysisLimitWith\|fastCandidateAnalysis\|turnAnalysisLimitFor"`)으로 모든 호출부(`AiMoveSelectionPolicy.kt`, `AnalysisSession.kt`, `AutoAiPolicyApplication.kt` 등)를 확인해 검증했다.

## 2. 왜 이런 구조가 됐는지 (과거 기록과 대조)

`docs/engine-research/ENGINE_BEGINNER_VISITS_BENCHMARK.md`(2026-06-08)가 이 문제의 출발점이었다. 당시 실측:

| 국면 | B16 후보수 | B32 후보수 | B64 후보수 |
| --- | ---: | ---: | ---: |
| P0 빈 보드 | 5 | 13 | 25 |
| P1 초반 8수 | 2 | 7 | 6 |
| P2 중반 20수 | 2 | 6 | 7 |

결론: "`Beginner 16`을 학습용 기본값으로 쓰기엔 후보 수가 부족하다"(P1에서 2개뿐). 이 발견이 지금의 `빠른 초급=B16 GTP`, `초급=B32 JSON` 분리로 이어졌다. 그리고 이 문서의 "남은 작업" 4번에 이미 이렇게 적혀 있었다:

> 4. 후보가 너무 적을 때 `64/500ms` 보강 분석을 자동 수행할지 결정한다.

이 결정은 아직 내려지지 않았다. 대신 `refinePolicyMoves`라는, 방문수를 통째로 올리는 것보다 더 정교한 메커니즘이 `engine-android/KataGoJsonPositionAnalysisClient.kt`에 구현됐지만(2026-06-13 전후로 추정), `EngineAnalysisPolicy.kt`가 AI 착수 경로에서 이를 0으로 눌러놓은 채 지금까지 왔다. `docs/engine-research/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md`의 "5. AI 응수와 Top Moves 분석 예산 혼합" 절을 보면, 과거에 `Balanced` 프리셋의 `minVisitsPerCandidate=4`/`refinePolicyMoves=4` 보강이 AI 착수 경로에 실수로 섞여 레벨 강도 실험이 오염된 사고가 있었다. `EngineAnalysisPolicy.kt`의 강제 0-초기화는 그 사고의 재발 방지책으로 보인다 — 즉 "후보 확장 기능이 나빠서" 꺼둔 게 아니라 "AI 착수와 Top Moves 예산이 섞이지 않게" 안전장치로 전부 꺼둔 것이다. 이 문서가 제안하는 방향은 이 안전장치를 없애자는 게 아니라, **AI 착수 전용의 명시적이고 작은 refine 예산을 새로 만들어 정책적으로 통제하자**는 것이다(5절).

## 3. 후보수를 늘리는 4가지 방법 비교

KataGo 문서(`ENGINE_API_CALL_POLICY.md` "Visit의 의미와 탐색 원리" 절)와 코드를 근거로 실제 후보수를 늘리는 레버는 4가지가 있다.

| 레버 | 방식 | 후보수 증가 | 속도 비용 | `order=0`(최상위 정확도) 영향 | 현재 상태 |
| --- | --- | --- | --- | --- | --- |
| A. `maxVisits` 자체를 올림 (16→32→64→…) | 더 오래 탐색 | 불확실 — 국면에 따라 오히려 줄기도 함(B64가 B32보다 적은 사례 실측됨) | 큼, sublinear 개선 | 좋아짐(더 안정) | 이미 레벨 정의 자체 |
| B. `wideRootNoise` 상향 | MCTS 탐색을 억지로 넓힘 | 늘어남 | 중간 | **나빠짐** — top move 정확도를 깎는 대가로 다양성을 얻는 KataGo 공식 설계 | 미사용 |
| C. `includePolicy=true`만 켜서 policy-only fallback 채움 | 검색 안 한 나머지를 policy prior만으로 채움 | 후보 리스트 길이는 늘지만 `pointLoss`가 없어 레벨링(퍼센타일 선택)에는 못 씀 | 거의 0 | 없음 | **이미 켜져 있음**(JSON 경로는 항상 `includePolicy=true`)이지만 레벨링에 기여 못함 |
| D. `refinePolicyMoves` (policy 상위 후보를 개별 소예산 재검색) | policy 상위 미탐색 후보 N개에 대해 각각 8-visit 추가 쿼리를 날려 실제 `pointLoss`를 채움 | **요청한 만큼 정확히 늘어남**(아래 실측 참고) | 조건부 — 아래 4절 실측 참고 | 없음 — 항상 기존 `order=0` 뒤에 덧붙여짐(코드 구조상 원천적으로 안전) | **이미 구현됐지만 AI 착수 경로에서 강제로 0으로 꺼짐** |

**B(wideRootNoise)를 배제하는 이유**: 이 앱의 불변식은 "각 레벨 그룹의 최고 단계는 항상 엔진 후보 순위의 최상위 수를 둔다"(`ENGINE_API_CALL_POLICY.md` 결정 3번)이다. `wideRootNoise`는 KataGo 공식 문서 기준으로 "top move를 덜 깊고 정확하게 보더라도 다양성을 높이는" 옵션이라, 같은 분석 결과를 최상위 단계의 착수 판단에도 쓰는 현재 구조에서는 최상위 수 자체가 흔들릴 위험이 있다. 별도 분석 인스턴스로 완전히 분리하지 않는 한 채택하지 않는 게 안전하다.

**D(`refinePolicyMoves`)가 유력한 이유**: 이미 구현돼 있고, 구조적으로 `order=0`을 건드리지 않으며(기존 scored 후보 뒤에만 덧붙임), 요청한 refine 개수만큼 결정론적으로 후보가 늘어난다(운에 좌우되는 A와 다름). 남은 질문은 "얼마나 비싼가"였고, 이건 실측이 필요했다.

## 4. 터미널 실측: `refinePolicyMoves`의 실제 비용

기존 `scripts/run-katago-level-match.py`류는 raw visits/time만 바꿀 수 있고 `refinePolicyMoves`를 흉내내지 못해서, 새 스크립트를 추가했다.

**신규: [`scripts/run-katago-candidate-refine-experiment.py`](../../scripts/run-katago-candidate-refine-experiment.py)**

`KataGoJsonAnalysisQueryFactory.build()`/`KataGoJsonPositionAnalysisClient.refineJsonPolicyCandidates()`의 refine 쿼리(policy 상위 후보에 수를 하나 얹어 `analyzeTurns`를 한 수 미루고 `maxVisits=8`로 재검색)를 Python으로 그대로 포팅했다. 기존 `ENGINE_BEGINNER_VISITS_BENCHMARK.md`와 같은 3개 국면(P0 빈 보드, P1 초반 8수, P2 중반 20수)을 그대로 재사용해 과거 데이터와 비교 가능하게 했다.

실행(맥북 M-시리즈, Metal backend, `numSearchThreads=4`, `numAnalysisThreads=1`, `analysis_learning.cfg`, time cap 5000ms로 visits가 먼저 걸리게 설정):

```bash
python3 scripts/run-katago-candidate-refine-experiment.py \
  --visits 16,32,64 --refine-budgets 0,4,8,12 --time-cap-ms 5000 \
  --out docs/measurements/engine-benchmark/candidate-refine-mac-20260817.md
```

원본 결과: [`docs/measurements/engine-benchmark/candidate-refine-mac-20260817.md`](../measurements/engine-benchmark/candidate-refine-mac-20260817.md) (36행 전체)

### 발췌 — refine 0 vs 8 (현재 `Balanced`=4, `Deep`=12와 비교용으로 8도 추가 측정)

| Position | Visits | Scored (refine=0) | Scored (refine=8) | Baseline ms | Refine 8개 추가 비용 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 빈 9x9 | 16 | 5 | 13 | 0.5 | 334.2 |
| P0 빈 9x9 | 32 | 13 | 21 | 435.9 | 306.9 |
| P0 빈 9x9 | 64 | 13 | 21 | 248.7 | 4.7 |
| P1 초반 8수 | 16 | 2 | 10 | 170.5 | 343.5 |
| P1 초반 8수 | 32 | 3 | 11 | 126.5 | 37.3 |
| P1 초반 8수 | 64 | 4 | 12 | 233.2 | 4.9 |
| P2 중반 20수 | 16 | 3 | 11 | 170.0 | 350.0 |
| P2 중반 20수 | 32 | 4 | 12 | 150.0 | 15.7 |
| P2 중반 20수 | 64 | 5 | 13 | 234.9 | 13.8 |

### 관찰 1 — refine은 정확히 요청한 만큼 후보를 늘린다

`refine_budget=4`면 항상 정확히 +4, `8`이면 +8, `12`면 +12다(예: P1-B16 `2→6→10→14`). `maxVisits`를 올리는 것과 달리 **국면에 좌우되지 않는 결정론적 증가**다. 이게 "레벨링이 원활하려면 후보가 몇 개 이상 필요하다"는 제품 요구사항에 정확히 맞는 성질이다.

### 관찰 2 — refine 비용은 base visits에 크게 좌우된다 (B16에서 특히 비쌈)

B16에서는 refine 4~12개에 **310~350ms**가 붙는다(후보 1개당 대략 30~80ms). 반면 B32/B64에서는 대체로 **몇 ms~40ms**로 거의 공짜다(단, P0-B32의 refine=8/12처럼 정책 후보가 baseline 탐색과 안 겹치면 다시 300ms대로 튈 때도 있다). 원인은 NN 캐시 재사용으로 보인다 — 방문수가 큰 baseline 탐색(32/64)이 내부적으로 이미 policy 상위 후보 주변을 평가해 놓기 때문에, 그 다음 refine 쿼리는 이미 계산된 값을 캐시에서 재사용할 확률이 높다. 반대로 16 visits짜리 얕은 탐색은 refine이 요청하는 이웃 후보들을 거의 새로 계산해야 한다.

**이것이 "빠른 초급(B16)에는 후보 확장 레버를 얹지 말자"는 3-2절 권장을 실측으로 뒷받침한다.** 같은 개수의 추가 후보를 얻는 비용이 B16에서는 B32/B64보다 훨씬 크다 — 구조(GTP vs JSON)뿐 아니라 순수 latency 관점에서도 `빠른 초급`은 후보 확장에 불리하다.

### 관찰 3 — B64가 항상 B32보다 후보가 많은 건 아니다

P0에서 B32/B64 모두 `refine=0` 기준 13개로 동일했다. `ENGINE_BEGINNER_VISITS_BENCHMARK.md`가 이미 지적했던 "B64가 항상 B32보다 후보 다양성이 좋은 건 아니다"가 오늘 다른 실행 조건(단일 프로세스 재사용, time cap 5000ms)에서도 재현됐다.

### 실측의 한계 (다음 실험에서 보완할 점)

- 표본 1회(n=1)다. `run-katago-level-match.py`처럼 반복 샘플링하지 않았다 — latency 숫자는 경향 확인용이지 SLA 근거로 쓰면 안 된다.
- 맥북 Metal 기준이다. `ENGINE.md`가 보여주듯 폰 실기기(Eigen CPU)에서는 JSON이 GTP보다 훨씬 유리해지는 역전이 이미 확인된 바 있다(B32에서 JSON 3067ms vs GTP 7603ms) — refine 비용도 폰에서 다시 재보는 게 맞다. 기존 `scripts/run-katago-search-mode-benchmark.py`의 ADB `run-as` 경로를 재사용하면 앱과 100% 동일한 바이너리/모델로 측정할 수 있다.
- 후보 증가가 색상/승률 레벨링 품질까지 실제로 개선하는지는 확인하지 않았다(이번 실험은 "몇 개 늘릴 수 있고 얼마나 드는가"만 측정). 이건 5절의 다음 단계다.

## 5. 권장 방향 (확신 있는 부분은 결정, 나머지는 다음 실험으로 분리)

### 결정해도 되는 것 (근거 충분, 확신 높음)

1. **`빠른 초급`(GTP, B16)에는 후보 확장 레버를 넣지 않는다.** 구조적으로(BestOnly면 애초에 후보 1개만 요청) + 실측으로(refine 비용이 B32/B64 대비 훨씬 큼) 이중으로 부적합하다. `빠른 초급`은 지금처럼 "느린 기기에서도 쾌적한 대국"이라는 원래 목적에 집중한다.
2. **후보 확장 노력은 `초급` 이상(JSON position analysis)에 집중한다.** 사용자의 기억("JSON 탐색이 후보 늘리기에 유리")이 정확했다 — 구조적으로도(policy 배열을 이미 받고 있음), 실측으로도(refine 비용이 훨씬 낮음) JSON 경로가 유리하다.
3. **레버는 `wideRootNoise`가 아니라 `refinePolicyMoves`다.** `wideRootNoise`는 최상위 후보 정확도를 깎는 대가로 다양성을 사는 옵션이라 "최고 단계는 항상 최상위 수" 불변식과 충돌한다. `refinePolicyMoves`는 이미 구현돼 있고 이 불변식을 구조적으로 해치지 않는다.

### 다음 실험으로 넘기는 것 (지금 결정하기엔 이름)

1. **AI 착수 전용 refine 예산값.** `Balanced`(4)/`Deep`(12)은 원래 Top Moves/학습 분석용으로 설계된 값이라 AI 착수에 그대로 쓰기엔 검증이 안 됐다. 오늘 실측 기준으로는 `초급`(B32)에서 refine 4~8 정도가 latency 대비 이득이 커 보이지만, 이건 레벨링 품질(퍼센타일 구간이 실제로 색깔/승률 차이를 만드는지)까지 봐야 확정할 수 있다.
2. **`run-katago-level-match.py`에 refine 로직 이식 후 AI vs AI 매치 재실행.** 지금 이 스크립트는 `includePolicy=False`만 쓰고 refine을 모른다. `run-katago-candidate-refine-experiment.py`의 refine 쿼리 로직을 이식해서, "초급 7단계(refine 포함) vs 초급 7단계(refine 없음)" 또는 "초급 3단계(refine 포함, 퍼센타일 구간이 더 세밀해진 상태) vs 초급 3단계(현재)" 같은 승률/집차이 매트릭스를 50판 이상 돌려야 실제 레벨링 개선 여부를 판단할 수 있다.
3. **폰 실기기 재검증.** `make engine-search-mode-benchmark-phone` 경로로 ADB `run-as` 기반 실측을 폰에서도 반복한다.
4. 위 실험들이 만족스러우면 그때 `EngineAnalysisPolicy.aiMoveAnalysisLimitWith()`의 JSON 분기에 `refinePolicyMoves`를 (Top Moves용 `Balanced`/`Deep`과는 별개의 값으로) 명시적으로 채우는 앱 변경을 별도로 착수한다. **이 문서 범위에는 포함하지 않는다.**

## 6. 터미널 기반 엔진 실험 인프라 현황

사용자가 "앱을 매번 빌드하지 않고 터미널 파이썬으로 먼저 고도화"를 요청했는데, 실제로 이미 상당한 인프라가 갖춰져 있었다. 오늘 그 위에 실험 1개를 더 추가했다.

| 스크립트 | 역할 | Makefile 타겟 |
| --- | --- | --- |
| `scripts/run-katago-level-match.py` | 레벨 A vs 레벨 B 1:1 반복 대국, 승률/집차이 JSONL 로그 | (직접 실행) |
| `scripts/run-katago-level-matrix.py` | 여러 레벨 조합을 한 번에 매트릭스로 실행 | `make engine-level-benchmark` |
| `scripts/run-katago-device-benchmark.py` | 기기별 B16/B32/B64 순수 성능(맥북) | `make engine-device-benchmark` |
| `scripts/run-katago-search-mode-benchmark.py` | GTP fast vs JSON position analysis latency 비교, 맥북/폰(ADB `run-as`) 둘 다 지원 | `make engine-search-mode-benchmark[-phone]` |
| **`scripts/run-katago-candidate-refine-experiment.py`(신규)** | `refinePolicyMoves` 후보 확장 레버의 후보수 증가량과 latency 비용 측정 | 없음(직접 실행) |

전부 앱 바이너리 없이 로컬 Homebrew KataGo(`/opt/homebrew/bin/katago`)와 번들 모델/config를 직접 구동한다. 폰 실측이 필요할 때만 ADB `run-as`로 실제 설치된 앱의 KataGo 산출물을 그대로 실행한다(`ENGINE_API_CALL_POLICY.md` "원격 폰 엔진 벤치마크 표준" 절). 이 경로는 앱을 빌드/설치/재시작할 필요 없이 반복 실험할 수 있다 — 사용자가 요청한 방향과 정확히 일치하는 기존 관례다. 새 스크립트도 이 관례(같은 옵션 이름, 같은 국면 데이터, `docs/measurements/engine-benchmark/`에 결과 저장)를 그대로 따랐다.

새 스크립트는 아직 Makefile 타겟이 없다 — 반복 사용 가치가 확인되면(5절의 다음 실험들이 이 스크립트를 계속 쓰게 되면) 타겟을 추가하는 게 자연스럽다. 지금은 보류한다.

## 참고 문서

- [`docs/ENGINE.md`](../ENGINE.md) — 이 문서가 더블체크한 원본 요약
- [`docs/ENGINE_API_CALL_POLICY.md`](../ENGINE_API_CALL_POLICY.md) — 딥다이브, `candidateCount 의미`/`Visit의 의미와 탐색 원리` 절
- [`docs/engine-research/ENGINE_BEGINNER_VISITS_BENCHMARK.md`](ENGINE_BEGINNER_VISITS_BENCHMARK.md) — 이 문제의 최초 발견, 오늘 실험이 재사용한 P0/P1/P2 국면 출처(2026-08-17: `docs/archive/`에서 이 폴더로 이동 — 근거는 5절 하단 정책 각주 참고)
- [`docs/engine-research/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md`](ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md) — `Balanced` 프리셋이 AI 응수에 실수로 섞였던 과거 사고 기록
- [`docs/engine-research/ENGINE_SEARCH_TREE_REUSE_REVIEW.md`](ENGINE_SEARCH_TREE_REUSE_REVIEW.md) — GTP tree reuse/JSON position-scoped 분석의 구조적 차이
- [`docs/measurements/engine-benchmark/candidate-refine-mac-20260817.md`](../measurements/engine-benchmark/candidate-refine-mac-20260817.md) — 오늘 실측 원본 데이터(36행)
- `scripts/run-katago-candidate-refine-experiment.py` — 오늘 추가한 실험 스크립트
