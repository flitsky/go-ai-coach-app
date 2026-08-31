# 엔진 디바이스 벤치마크 결과

- samplesPerVisitPosition: `10`
- positions: `empty, random`
- visits: `16, 32, 64`
- timeCap: `5000ms`
- deterministic: `False`
- searchThreads: `4`

| Position | Visits | Samples | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| empty | 16 | 10 | 0 | 0.536 | 20.369 | 187.928 | 10.832 | 16.9 | 250ms |
| empty | 32 | 10 | 0 | 0.645 | 104.136 | 547.4 | 435.891 | 34.6 | 700ms |
| empty | 64 | 10 | 0 | 1.131 | 39.849 | 231.45 | 65.93 | 66.9 | 300ms |
| random | 16 | 10 | 0 | 162.565 | 168.383 | 177.699 | 175.727 | 17 | 250ms |
| random | 32 | 10 | 0 | 274.513 | 292.554 | 314.81 | 300.869 | 35 | 400ms |
| random | 64 | 10 | 0 | 481.18 | 541.364 | 586.379 | 574.045 | 67 | 750ms |
