# 엔진 search mode 벤치마크 결과

## 조건

- samplesPerVisit: `3`
- visits: `16, 32, 64`
- timeCap: `10000ms`
- deterministic: `False`
- transport: `adb`
- adbSerial: `192.168.35.3:45513`
- adbPackage: `com.worksoc.goaicoach`
- GTP searchThreads: `1`
- JSON analysisThreads/searchThreads: `1` / `4`
- GTP clearCacheBeforeAnalyze: `True`
- basePosition: `B E5, W C4, B E3`

GTP fast path는 JSON `rootInfo.visits`를 제공하지 않으므로 root 값은 후보별 `visits` 합산 추정치다. JSON position analysis의 root 값은 `rootInfo.visits` 원본이다.

## 결과

| Mode | Visits | Samples | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| gtp_stateful_fast_clearcache | 16 | 3 | 3 | 3795.388 | 3815.258 | 3847.406 | 3847.406 | 15 | 4850ms |
| gtp_stateful_fast_clearcache | 32 | 3 | 3 | 7488.675 | 7602.83 | 7673.222 | 7673.222 | 31 | 9600ms |
| gtp_stateful_fast_clearcache | 64 | 3 | 3 | 10061.645 | 10167.884 | 10255.742 | 10255.742 | 47 | 12850ms |
| json_position_analysis | 16 | 3 | 0 | 4563.296 | 4759.967 | 5049.033 | 5049.033 | 17 | 6350ms |
| json_position_analysis | 32 | 3 | 0 | 2894.954 | 3067.418 | 3154.911 | 3154.911 | 35 | 3950ms |
| json_position_analysis | 64 | 3 | 0 | 4659.762 | 4994.955 | 5174.066 | 5174.066 | 67 | 6500ms |

## GTP 대비 JSON 비율

| Visits | GTP avg ms | JSON avg ms | JSON/GTP |
| ---: | ---: | ---: | ---: |
| 16 | 3815.258 | 4759.967 | 1.25x |
| 32 | 7602.83 | 3067.418 | 0.40x |
| 64 | 10167.884 | 4994.955 | 0.49x |

## 해석

- 이 결과는 Android ADB 실기기 `192.168.35.3:45513`의 app-private bundled KataGo Eigen(CPU) backend 기준이다.
- GTP fast path는 현재 Android 앱의 AI vs AI 격리 흐름에 맞춰 `clear_cache` 후 측정했다.
- JSON position analysis는 요청 payload에 전체 수순과 `maxVisits`를 넣는 방식이므로 AI vs AI 레벨 오염을 줄이기 쉽다.
- 폰 기본값 전환 여부는 이 결과만으로 결정하지 않고, 실기기 latency와 `rootInfo.visits` fill 수집 후 판단한다.
