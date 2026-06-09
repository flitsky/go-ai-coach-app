# 엔진 레벨 매트릭스 결과

- gamesPerMatchup: `50`
- deterministic: `False`
- searchThreads: `4`
- finalEval: `400 visits / 2000ms`

| Matchup | Left wins | Right wins | Left win rate | Avg left lead | Log |
| --- | ---: | ---: | ---: | ---: | --- |
| 빠른 초급 3단계 vs 초급 7단계 | 15 | 35 | 0.3 | -5.766 | docs/engine-match-logs/matrix-20260610/B16-vs-B32.jsonl |
| 빠른 초급 3단계 vs 중급 5단계 | 10 | 40 | 0.2 | -5.62 | docs/engine-match-logs/matrix-20260610/B16-vs-B64.jsonl |
| 초급 7단계 vs 중급 5단계 | 23 | 27 | 0.46 | -0.276 | docs/engine-match-logs/matrix-20260610/B32-vs-B64.jsonl |

## 평균 응답 시간

### 빠른 초급 3단계 vs 초급 7단계
- `빠른 초급 3단계`: avg `32.843ms`, avg root visits `16.984`
- `초급 7단계`: avg `126.339ms`, avg root visits `34.855`

### 빠른 초급 3단계 vs 중급 5단계
- `빠른 초급 3단계`: avg `17.53ms`, avg root visits `16.977`
- `중급 5단계`: avg `189.444ms`, avg root visits `66.867`

### 초급 7단계 vs 중급 5단계
- `중급 5단계`: avg `231.842ms`, avg root visits `66.904`
- `초급 7단계`: avg `61.802ms`, avg root visits `34.907`
