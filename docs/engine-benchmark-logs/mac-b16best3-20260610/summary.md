# 엔진 디바이스 벤치마크 결과

- samplesPerVisit: `5`
- positions: `b16-best-3-variants`
- visits: `16, 32, 64`
- timeCap: `5000ms`
- deterministic: `False`
- searchThreads: `4`
- generatedPositions:
  - `b16-best-3-variants`: `B E5, W C4, B E3`

| Position | Visits | Samples | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| b16-best-3-variants | 16 | 5 | 0 | 68.398 | 139.174 | 160.655 | 160.655 | 17 | 250ms |
| b16-best-3-variants | 32 | 5 | 0 | 140.606 | 200.021 | 435.463 | 435.463 | 35 | 550ms |
| b16-best-3-variants | 64 | 5 | 0 | 242.257 | 247.521 | 253.707 | 253.707 | 67 | 350ms |
