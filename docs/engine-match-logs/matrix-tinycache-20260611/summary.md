# 엔진 레벨 매트릭스 결과

- gamesPerMatchup: `50`
- deterministic: `False`
- cacheIsolation: `tiny-nn-cache`
- cacheClearedBeforeQuery: `False`
- searchThreads: `4`
- finalEval: `400 visits / 2000ms`

| Matchup | Left wins | Right wins | Left win rate | Avg left lead | Log |
| --- | ---: | ---: | ---: | ---: | --- |
| 빠른 초급 3단계 vs 초급 7단계 | 20 | 30 | 0.4 | -1.216 | docs/engine-match-logs/matrix-tinycache-20260611/B16-vs-B32.jsonl |
| 빠른 초급 3단계 vs 중급 5단계 | 8 | 42 | 0.16 | -7.891 | docs/engine-match-logs/matrix-tinycache-20260611/B16-vs-B64.jsonl |
| 초급 7단계 vs 중급 5단계 | 9 | 41 | 0.18 | -5.889 | docs/engine-match-logs/matrix-tinycache-20260611/B32-vs-B64.jsonl |

## 평균 응답 시간

### 빠른 초급 3단계 vs 초급 7단계
- `빠른 초급 3단계`: avg `172.421ms`, avg root visits `16.995`
- `초급 7단계`: avg `285.602ms`, avg root visits `34.961`

### 빠른 초급 3단계 vs 중급 5단계
- `빠른 초급 3단계`: avg `169.114ms`, avg root visits `16.994`
- `중급 5단계`: avg `521.464ms`, avg root visits `66.944`

### 초급 7단계 vs 중급 5단계
- `중급 5단계`: avg `502.051ms`, avg root visits `66.969`
- `초급 7단계`: avg `272.563ms`, avg root visits `34.966`
