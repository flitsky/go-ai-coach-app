# 엔진 레벨 강도 검토

작성일: 2026-06-10

## 질문

`빠른 초급 3단계(B16 best)`가 `초급 7단계(B32 best)`를 자주 이기는 현상이 있다.

확인할 내용:

1. `16 visits`와 `32 visits`의 기대 차이는 무엇인가?
2. 엔진 응답시간을 강제할 수 있는가?
3. KataGo 공식 설명에 최적 품질을 위한 최소 시간 제안이 있는가?
4. 우리 설정에서 놓친 부분은 무엇인가?

## 현재 앱 구현

현재 AI 응수는 `MatchPolicy.selectAiMoveFromAnalysis()`에서 다음 흐름으로 동작한다.

1. 현재 진영의 `PlayLevelSetting.analysisLimit`로 `EngineAdapter.analyze()` 호출
2. `pointLoss`가 있는 searched 후보만 선택 대상으로 사용
3. `MoveSelectionPolicy`가 지정한 후보 index 범위에서 착수 선택
4. `BestOnly`는 후보 range가 `0..0`이므로 엔진이 반환한 첫 번째 searched 후보만 선택

현재 주요 레벨:

| 레벨 | 요청 | 선택 |
| --- | --- | --- |
| 빠른 초급 3단계 | `16 visits / 250ms` | `BestOnly` |
| 초급 1~7단계 | `32 visits / 500ms` | 1~6단계는 percentile window, 7단계는 `BestOnly` |
| 중급 | `64 visits / 500ms` | 단계별 percentile window 또는 `BestOnly` |

따라서 `빠른 초급 3단계`와 `초급 7단계`의 차이는 현재 다음 하나다.

- 같은 `BestOnly` 선택 정책
- 단, search budget이 `16 visits`에서 `32 visits`로 증가

## KataGo 공식 동작 기준

KataGo analysis engine의 query는 `maxVisits`를 받을 수 있고, 지정하지 않으면 analysis config의 기본값을 사용한다. 우리 JSON query도 `maxVisits`를 직접 넣는다.

KataGo config의 `maxTime`은 "이만큼은 반드시 생각하라"가 아니라 검색 시간을 제한하는 cap이다. `maxVisits`, `maxPlayouts`, `maxTime`은 모두 search limit이다.

따라서 `maxVisits=32`, `maxTime=0.5`를 같이 넣으면 실무적으로는 다음처럼 이해해야 한다.

- `32 visits`에 먼저 도달하면 500ms를 다 쓰기 전에 멈출 수 있다.
- 500ms에 먼저 도달하면 32 visits를 채우기 전에 멈출 수 있다.
- 즉 `maxTime`은 품질을 높이는 최소 시간 보장이 아니라 과도한 지연을 막는 상한이다.

KataGo 공식 config는 `numSearchThreads`에 대해 benchmark로 튜닝하라고 설명한다. 공식 문서에 “최소 몇 ms 이상이면 최적 품질” 같은 고정 제안은 없다. 하드웨어, backend, board size, search threads, batch 상황에 따라 달라진다.

## 16과 32 visits의 기대 차이

기대 효과:

- `32 visits`는 `16 visits`보다 root 후보를 조금 더 검증한다.
- 후보별 `scoreLead`, `winrate`, `pointLoss` 추정이 조금 더 안정될 가능성이 있다.
- 같은 `BestOnly`라면 장기적으로는 B32가 B16보다 더 좋은 수를 고를 가능성이 높다.

하지만 현재 수준에서는 기대 차이가 작고, 승률 차이가 선형으로 커진다고 보면 안 된다.

- `16 -> 32`는 두 배이지만 절대량은 여전히 매우 낮다.
- MCTS는 "방문수 두 배 = 수 품질 두 배"가 아니다. 이미 policy prior가 강하게 찍은 후보를 확인하는 데 방문수가 쓰일 수 있고, 실제로 대안 후보를 충분히 넓게 탐색하지 못할 수도 있다.
- `16 visits`에서도 policy prior가 좋은 수를 정확히 찍으면 강한 수를 둘 수 있다. 반대로 `32 visits`도 잘못된 초기 prior 또는 저예산 score 흔들림을 완전히 제거하지 못한다.
- 9x9는 한 수의 swing이 크고 판 수가 짧아, 한두 번의 후보 평가 흔들림이 최종 승패로 바로 이어질 수 있다.
- 9x9 초반/중반은 한두 수 swing이 커서 10판 표본으로 강약을 단정하기 어렵다.
- KataGo `order`는 점수 손실 하나만이 아니라 `playSelectionValue` 기준이다.
- 저예산에서는 후보별 평가가 흔들릴 수 있다.
- 현재 `analysis_learning.cfg`는 `nnRandomize = true`다. 저예산에서는 이 랜덤화가 결과 흔들림을 더 크게 만들 수 있다.

즉 `초급 7단계(B32 best)`가 `빠른 초급 3단계(B16 best)`보다 항상 이긴다고 기대하면 안 된다. 다만 반복 실험에서 계속 역전된다면 설정 또는 실험 조건을 더 통제해야 한다.

## 놓친 가능성이 큰 설정

### 1. `maxTime`을 최소 thinking time으로 오해

현재 가장 큰 혼선 포인트다.

`500ms`는 “최소 500ms 동안 더 좋은 수를 찾는다”가 아니다. `32 visits`가 빨리 끝나면 품질은 사실상 `32 visits` 품질이다.

초급을 B32로 고정하겠다는 제품 결정이 있다면, 초급 7단계의 품질을 더 올리는 방법은 `500ms`를 늘리는 것이 아니라 아래 항목을 조정해야 한다.

### 2. analysis randomization

현재 bundled `analysis_learning.cfg`:

```text
nnRandomize = true
```

공식 config도 `nnRandomize`와 `nnRandSeed`를 제공한다. 강도 비교와 재현 테스트 목적이라면 다음을 별도 실험 모드에서 검토할 수 있다.

```text
nnRandomize = false
```

또는 seed 고정:

```text
nnRandSeed = <fixed seed>
searchRandSeed = <fixed seed>
```

주의: 실제 사용자 대국에서는 약간의 랜덤성이 자연스럽게 느껴질 수 있다. 하지만 레벨 강도 검증에는 방해가 된다.

### 3. search thread 수

우리 analysis process는 runtime override로 `numAnalysisThreads=1`, `numSearchThreads=4`를 사용한다.

KataGo example config는 같은 MCTS tree 안에서 여러 thread를 쓰면 고정 playout 기준으로 검색 품질이 약간 약해질 수 있다고 설명한다. 빠른 응답에는 유리하지만, 아주 낮은 visits에서는 이 영향이 상대적으로 커질 수 있다.

강도 검증 실험에서는 다음 두 조건을 비교할 가치가 있다.

| 목적 | numSearchThreads |
| --- | ---: |
| 실사용 응답성 | 4 |
| 고정 visits 품질/재현성 검증 | 1 |

### 4. 너무 낮은 절대 visits

B16과 B32는 둘 다 매우 낮은 search다. 초급/중급 구분을 `16/32/64`로 유지한다면, B32의 기대 강도 차이는 “명확히 더 안정적”이라기보다 “조금 더 검증한다”에 가깝다.

따라서 B16 best와 B32 best 사이 승률 격차가 작게 나오는 것은 이상 현상이라기보다 가능한 현상이다.

### 5. AI 응수와 Top Moves 분석 예산 혼합

중급 5단계의 표면 설정은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| 그룹 | `중급` |
| difficulty label | `Casual` |
| visits | `64` |
| time cap | `500ms` |
| candidate count | `20` |
| 선택 정책 | `BestOnly` |

하지만 기존 구현에서는 `중급`이 `Balanced` preset을 공유하면서 AI 응수에도 다음 보강이 같이 들어갔다.

- `minVisitsPerCandidate = 4`
- `minTimeMillis = 800ms`
- `refinePolicyMoves = 4`

그 결과 실제 adapter의 effective limit은 `64/500`이 아니라 최소 `80 visits / 800ms`가 될 수 있고, policy refine query까지 추가될 수 있었다.

조치:

- AI 대국 응수는 Top Moves용 보강을 사용하지 않는다.
- AI 응수 분석 요청은 `includePolicy=false`, `refinePolicyMoves=0`, `minVisitsPerCandidate=0`, `minTimeMillis=null`로 정규화한다.
- 따라서 중급 5단계 AI 응수는 다시 `64 visits / 500ms` 요청이 된다.

Top Moves/힌트 분석은 학습 UI 품질을 위해 기존 `Balanced` 보강을 유지할 수 있다.

## 제안

### 1차: 설정 변경 전 검증부터

바로 레벨 값을 바꾸기보다, 먼저 실험 조건을 고정한다.

1. AI-vs-AI 자동 대전 테스트 harness를 만든다.
2. 같은 매치업을 흑백 교대 20~50판 이상 실행한다.
3. `B16 best` vs `B32 best` 승률, 평균 집 차이, pass 시점, 선택 후보 order, raw visits를 기록한다.
4. `nnRandomize=true/false`, `numSearchThreads=4/1`을 비교한다.

초기 smoke 결과:

- script: `scripts/run-katago-level-match.py`
- log: `docs/engine-match-logs/fb3-vs-lb7-det-20260610.jsonl`
- 조건: deterministic, `numSearchThreads=1`, warm-up 후 4판, 흑백 교대
- 결과: `초급 7단계` 3승, `빠른 초급 3단계` 1승

이 결과는 표본이 작아 결론으로 쓰기에는 부족하지만, 적어도 deterministic/warm-up 조건에서는 B32 best가 B16 best보다 우세한 경향을 보였다.

### 1차 150판 매트릭스 결과

요청한 3개 조합을 각각 50판씩 실행했다.

- command: `make engine-level-benchmark ENGINE_MATCH_GAMES=50 ENGINE_MATCH_OUT=docs/engine-match-logs/matrix-20260610`
- script: `scripts/run-katago-level-matrix.py`
- summary: `docs/engine-match-logs/matrix-20260610/summary.md`
- raw logs:
  - `docs/engine-match-logs/matrix-20260610/B16-vs-B32.jsonl`
  - `docs/engine-match-logs/matrix-20260610/B16-vs-B64.jsonl`
  - `docs/engine-match-logs/matrix-20260610/B32-vs-B64.jsonl`
- 조건: 실사용에 가까운 non-deterministic, `numSearchThreads=4`, 흑백 교대, warm-up, final evaluator `400 visits / 2000ms`
- 소요 시간: 약 23분 41초

| Matchup | Left wins | Right wins | Left win rate | Avg left lead |
| --- | ---: | ---: | ---: | ---: |
| 빠른 초급 3단계(B16 best) vs 초급 7단계(B32 best) | 15 | 35 | 30% | -5.766 |
| 빠른 초급 3단계(B16 best) vs 중급 5단계(B64 best) | 10 | 40 | 20% | -5.620 |
| 초급 7단계(B32 best) vs 중급 5단계(B64 best) | 23 | 27 | 46% | -0.276 |

평균 응답 시간과 root visits:

| Matchup | Level | Avg elapsed | Avg root visits |
| --- | --- | ---: | ---: |
| B16 vs B32 | 빠른 초급 3단계 | 32.843ms | 16.984 |
| B16 vs B32 | 초급 7단계 | 126.339ms | 34.855 |
| B16 vs B64 | 빠른 초급 3단계 | 17.530ms | 16.977 |
| B16 vs B64 | 중급 5단계 | 189.444ms | 66.867 |
| B32 vs B64 | 초급 7단계 | 61.802ms | 34.907 |
| B32 vs B64 | 중급 5단계 | 231.842ms | 66.904 |

해석:

- 이번 150판 데이터에서는 `B32 best`가 `B16 best`에게 열세라는 현상은 재현되지 않았다. 오히려 B32가 35승 15패로 우세했다.
- `B64 best`는 `B16 best`를 40승 10패로 이겨, B16/B64 차이는 충분히 드러났다.
- 더 중요한 발견은 `B32 best`와 `B64 best`가 23승 27패로 거의 비슷하게 나온 점이다. 즉 현재 저예산 9x9 BestOnly 조건에서는 `32 -> 64 visits` 차이가 기대보다 약하게 분리된다.
- 따라서 사용자가 폰에서 본 `B16 > B32` 현상은 작은 표본, 흑백 순서, 랜덤성, 구버전 빌드, 또는 이전 AI 응수 경로에서 Top Moves 보강이 섞였던 문제의 영향일 가능성이 높다.
- 다만 `B32`와 `B64`의 강도 차이가 약한 것은 실제 제품 레벨링 관점에서 계속 추적해야 한다. 중급이 확실히 더 강하게 느껴져야 한다면 `B64`의 visits/time만이 아니라 selection policy, 랜덤성, 엔진 config, 후반 pass 정책까지 함께 조정해야 한다.

주의:

- 이번 승패 판정은 대국 종료 후 final evaluator analysis estimate를 사용한다. 실제 앱의 사석 정리/계가 최종 로직과 완전히 동일한 검증은 아니다.
- 상대 강도 비교용 데이터로는 충분히 의미가 있지만, 마켓 릴리즈 전에는 앱 내 `final_score/final_status_list` 기반 종료 결과와 자동 대전 harness 결과를 한 번 더 결합하는 것이 좋다.

### B16/B32 time cap 1000ms 추가 실험

`maxTime` 부족으로 B32가 충분히 32 visits를 채우지 못하는지 확인하기 위해, 방문수는 그대로 두고 B16/B32의 time cap만 모두 `1000ms`로 늘린 50판 실험을 추가했다.

- command: `python3 scripts/run-katago-level-match.py --black fast_beginner:3 --white beginner:7 --black-time-ms 1000 --white-time-ms 1000 --games 50 --swap-colors --seed 20260611 --out docs/engine-match-logs/b16-vs-b32-time1000-20260610.jsonl`
- summary: `docs/engine-match-logs/b16-vs-b32-time1000-20260610-summary.md`
- raw log: `docs/engine-match-logs/b16-vs-b32-time1000-20260610.jsonl`
- 조건: 실사용에 가까운 non-deterministic, `numSearchThreads=4`, 흑백 교대, warm-up, final evaluator `400 visits / 2000ms`
- 소요 시간: 약 6분 26초

| 조건 | B16 wins | B32 wins | B32 win rate | B32 avg lead | B16 avg root visits | B32 avg root visits |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 기존 B16 250ms / B32 500ms | 15 | 35 | 70% | +5.766 | 16.984 | 34.855 |
| B16 1000ms / B32 1000ms | 24 | 26 | 52% | +2.564 | 16.981 | 34.899 |

방문수 미달은 기존 조건에서 3453턴 중 1턴, 1000ms 조건에서 3367턴 중 0턴이었다.

해석:

- 맥북에서는 기존 B16/B32 조건에서도 대부분 요청 visits를 이미 채우고 있다.
- time cap을 `1000ms`로 늘려도 평균 root visits는 거의 변하지 않았다. 따라서 맥북 기준으로는 `maxTime`이 아니라 `maxVisits`가 먼저 search를 끝낸다.
- 이번 추가 50판에서 B32 우세가 약해졌지만, root visits가 동일하므로 시간 증가 효과라기보다 저방문수, `nnRandomize=true`, search thread, 9x9 swing에 따른 표본 흔들림으로 봐야 한다.
- 폰에서는 500ms 안에 B32 visits를 못 채울 가능성이 있으므로, time cap 확대는 “강도 향상”보다는 “느린 기기에서 목표 visits를 안정적으로 채우는 안전장치”로 의미가 있다.

### B16/B32 time cap 1000ms 2차 50판

동일 조건을 새 seed로 50판 더 실행했다.

- command: `python3 scripts/run-katago-level-match.py --black fast_beginner:3 --white beginner:7 --black-time-ms 1000 --white-time-ms 1000 --games 50 --swap-colors --seed 20260612 --out docs/engine-match-logs/b16-vs-b32-time1000-r2-20260610.jsonl`
- summary: `docs/engine-match-logs/b16-vs-b32-time1000-r2-20260610-summary.md`
- raw log: `docs/engine-match-logs/b16-vs-b32-time1000-r2-20260610.jsonl`

| 조건 | B16 wins | B32 wins | B32 win rate | B32 avg lead | B16 avg root visits | B32 avg root visits |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| B16 1000ms / B32 1000ms, 1차 | 24 | 26 | 52% | +2.564 | 16.981 | 34.899 |
| B16 1000ms / B32 1000ms, 2차 | 24 | 26 | 52% | +4.302 | 16.961 | 34.742 |
| B16 1000ms / B32 1000ms, 누적 100판 | 48 | 52 | 52% | +3.433 | 16.971 | 34.820 |

누적 방문수 미달:

| Level | total turns | root visits below request |
| --- | ---: | ---: |
| B16 | 3380 | 5 |
| B32 | 3381 | 18 |

해석:

- 1000ms 조건 100판 누적에서도 B32는 근소 우세에 그쳤다.
- 평균 root visits는 B16 약 `17`, B32 약 `35`로 기존과 거의 같다.
- 맥북 기준으로는 time cap을 더 늘리는 것이 B32 강도 차이를 뚜렷하게 만든다는 근거가 약하다.
- 이제 핵심은 폰에서 실제로 B32/B64가 request visits를 못 채우는지 확인하는 것이다. 앱 debug report에는 `Visit diagnostics: request=..., root=..., elapsedMs=..., timeCapMs=..., fill=OK/SHORT`가 남도록 반영했다.

### 2차: 검증용 deterministic mode 추가

사용자 대국 기본값은 유지하되, 개발/검증용으로 deterministic analysis config를 추가한다.

후보:

```text
nnRandomize = false
numSearchThreads = 1
```

이 모드는 레벨 강도 비교용이며, 실사용 기본값으로 바로 쓰지는 않는다.

### 3차: 제품 레벨링 재판단

검증 후에도 `B32 best`가 `B16 best`를 안정적으로 이기지 못한다면, 다음 중 하나를 선택해야 한다.

1. `빠른 초급 3단계`를 BestOnly가 아니라 상위 후보 랜덤으로 낮춘다.
2. `초급 7단계`는 B32 유지하되 deterministic settings를 적용한다.
3. 초급/중급 경계 정책을 재검토한다.
4. `16/32/64`를 엔진 search 강도만이 아니라 `MoveSelectionPolicy` 중심의 제품 레벨로 재정의한다.

현재 제품 원칙이 `빠른 초급=16`, `초급=32`, `중급=64`라면 1번 또는 2번이 가장 보수적이다.

## 현재 결론

놓친 가능성이 가장 큰 것은 `maxTime`보다 `랜덤성`과 `저방문수에서의 불안정성`이다.

`500ms`를 늘리는 것만으로는 `B32` 품질이 반드시 좋아지지 않는다. `maxVisits=32`가 먼저 걸리면 search는 끝난다. 따라서 레벨 강도를 검증하려면 deterministic analysis 조건과 충분한 반복 대전 로그가 먼저 필요하다.

## 참고 근거

- KataGo Analysis Engine: https://github.com/lightvector/KataGo/blob/master/docs/Analysis_Engine.md
- KataGo GTP example config: https://github.com/lightvector/KataGo/blob/master/cpp/configs/gtp_example.cfg
- KataGo analysis example config: https://github.com/lightvector/KataGo/blob/master/cpp/configs/analysis_example.cfg
