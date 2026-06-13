# 엔진 search mode 벤치마크 결과

## 조건

- samplesPerVisit: `10`
- visits: `16, 32, 64`
- timeCap: `5000ms`
- deterministic: `False`
- GTP searchThreads: `1`
- JSON analysisThreads/searchThreads: `1` / `4`
- GTP clearCacheBeforeAnalyze: `True`
- basePosition: `B E5, W C4, B E3`

GTP fast path는 JSON `rootInfo.visits`를 제공하지 않으므로 root 값은 후보별 `visits` 합산 추정치다. JSON position analysis의 root 값은 `rootInfo.visits` 원본이다.

## 결과

| Mode | Visits | Samples | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| gtp_stateful_fast_clearcache | 16 | 10 | 10 | 100.846 | 108.353 | 113.753 | 113.114 | 15 | 150ms |
| gtp_stateful_fast_clearcache | 32 | 10 | 10 | 205.203 | 224.661 | 277.788 | 243.228 | 31 | 350ms |
| gtp_stateful_fast_clearcache | 64 | 10 | 10 | 374.984 | 424.623 | 460.235 | 439.578 | 63 | 600ms |
| json_position_analysis | 16 | 10 | 0 | 161.321 | 167.361 | 171.626 | 170.789 | 17 | 250ms |
| json_position_analysis | 32 | 10 | 0 | 136.308 | 173.103 | 418.136 | 152.326 | 34.9 | 550ms |
| json_position_analysis | 64 | 10 | 0 | 225.114 | 282.043 | 536.843 | 265.292 | 67 | 700ms |

## GTP 대비 JSON 비율

| Visits | GTP avg ms | JSON avg ms | JSON/GTP |
| ---: | ---: | ---: | ---: |
| 16 | 108.353 | 167.361 | 1.54x |
| 32 | 224.661 | 173.103 | 0.77x |
| 64 | 424.623 | 282.043 | 0.66x |

## 해석

- 이 결과는 맥북 Homebrew KataGo Metal backend 기준이다.
- GTP fast path는 현재 Android 앱의 AI vs AI 격리 흐름에 맞춰 `clear_cache` 후 측정했다.
- JSON position analysis는 요청 payload에 전체 수순과 `maxVisits`를 넣는 방식이므로 AI vs AI 레벨 오염을 줄이기 쉽다.
- 폰 기본값 전환 여부는 이 결과만으로 결정하지 않고, 실기기 latency와 `rootInfo.visits` fill 수집 후 판단한다.
