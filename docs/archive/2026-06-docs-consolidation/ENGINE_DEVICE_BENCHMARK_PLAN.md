# 엔진 디바이스 벤치마크 계획

작성일: 2026-06-10

## 배경

현재 레벨링은 다음 원칙을 사용한다.

| 등급 | visits | 현재 time cap |
| --- | ---: | ---: |
| 빠른 초급 | 16 | 250ms |
| 초급 | 32 | 500ms |
| 중급 | 64 | 500ms |
| 고급 | 160 | 1000ms |

맥북 자동 대전에서는 대부분 요청 visits를 채운다. 반면 폰 실기기에서는 `fill=SHORT`가 관찰되었다. 이는 같은 visits 요청이라도 디바이스 성능에 따라 실제 탐색량이 달라질 수 있음을 의미한다.

## 현재 해석

`fill=OK`는 “요청 visits를 채웠다”는 뜻이다. “해당 visits가 충분히 강한 수를 보장한다”는 뜻은 아니다.

맥북 B16/B32 실험 결과:

| 조건 | B16 wins | B32 wins | B32 win rate | B32 avg root visits |
| --- | ---: | ---: | ---: | ---: |
| 기존 B16 250ms / B32 500ms, 50판 | 15 | 35 | 70% | 34.855 |
| B16 1000ms / B32 1000ms, 누적 100판 | 48 | 52 | 52% | 34.820 |

두 결과가 흔들리는 이유:

- B16/B32는 둘 다 절대 visits가 낮다.
- `32 visits`가 `16 visits`의 두 배이긴 하지만, 저방문수 MCTS에서 승률이 선형으로 벌어지지는 않는다.
- 현재 analysis config는 non-deterministic이며, `nnRandomize=true`와 search thread 영향이 있다.
- 9x9는 한두 수 swing이 최종 승패에 크게 반영된다.
- final evaluator도 추정 기반이므로 완전한 토너먼트 판정기는 아니다.

따라서 맥북에서 `fill=SHORT`가 거의 없는데도 100판 승률이 비슷하게 나온 것은 모순이 아니다. “방문수를 채웠지만, 16/32 visits 차이만으로는 항상 명확한 강도 차이가 나지 않는다”로 해석해야 한다.

## 폰에서 확인할 핵심 지표

앱 debug report의 `candidateText`에 다음 형식이 포함된다.

```text
Visit diagnostics: request=32, root=20, elapsedMs=501, timeCapMs=500, fill=SHORT.
```

판단 기준:

| 필드 | 의미 |
| --- | --- |
| `request` | 요청 visits |
| `root` | 실제 root visits |
| `elapsedMs` | 실제 엔진 응답 시간 |
| `timeCapMs` | 요청 time cap |
| `fill` | `root >= request`면 `OK`, 아니면 `SHORT` |

폰에서 `fill=SHORT`가 반복되면, 해당 등급의 time cap이 디바이스 성능 대비 낮다는 의미다.

## 제안: 최초 실행 디바이스 벤치마크

첫 실행 또는 엔진 asset 변경 후 다음 팝업을 표시한다.

> 최초 실행환경에서 최적 플레이를 위해 벤치마크 테스트가 진행중입니다.

목표:

1. 현재 디바이스에서 B16/B32/B64가 목표 visits를 안정적으로 채우는 최소 time cap을 측정한다.
2. 측정 결과를 로컬 설정에 저장한다.
3. 앱 기본 플레이 time cap은 측정값에 안전 여유를 붙여 사용한다.
4. 사용자는 이후 “빠른 응답 / 안정 탐색 / 수동 설정” 중 선택할 수 있다.

## 벤치마크 절차

벤치마크는 사용자 설정을 평가하지 않는다. 사용자 Player Setup, 현재 계가 규칙, `Top Moves` 토글, 현재 후보 표시 상태는 측정에 유입되면 안 된다. 측정 중에는 앱이 정한 고정 9x9 Territory 규칙과 고정 B16/B32/B64 경량 `AnalysisLimit`만 사용한다.

### 1. Warm-up

- 엔진 시작 직후 1회 warm-up query를 실행한다.
- 첫 query는 모델 로딩, 캐시 초기화, 스레드 준비 때문에 느릴 수 있으므로 측정에서 제외한다.

### 2. 고정 포지션 2~3개 측정

빈 판 하나만 쓰면 실제 대국 중반 성능을 반영하지 못할 수 있다.

권장 포지션:

| 포지션 | 목적 |
| --- | --- |
| Empty 9x9 | 초반 응답 측정 |
| Midgame 9x9 | 일반 대국 중반 측정 |
| Tactical 9x9 | 수읽기 후보가 복잡한 상황 측정 |

### 3. 등급별 time cap ladder

각 target visits에 대해 time cap을 낮은 값부터 올려가며 측정한다.

| target | ladder |
| --- | --- |
| B16 | 250ms, 500ms, 750ms, 1000ms |
| B32 | 500ms, 750ms, 1000ms, 1500ms |
| B64 | 500ms, 1000ms, 1500ms, 2000ms, 3000ms |

각 조합은 2~3회 반복한다.

통과 기준:

- `rootVisits >= targetVisits`
- 반복 측정 중 90~95% 이상 통과
- 또는 3회 중 3회 통과

최종 cap:

```text
measuredStableMs * 1.2~1.3 safetyFactor
```

단, UX 지연을 막기 위해 상한을 둔다.

## 저장 모델

예상 저장 구조:

```kotlin
data class DeviceEngineBenchmarkProfile(
    val createdAtMillis: Long,
    val engineBuild: String,
    val modelName: String,
    val deviceModel: String,
    val b16StableTimeMs: Long,
    val b32StableTimeMs: Long,
    val b64StableTimeMs: Long,
    val sampleCount: Int,
)
```

저장 위치:

- 우선 앱 로컬 preferences
- 사용자가 `Copy Log`를 누르면 benchmark profile도 debug report에 포함
- 추후 opt-in telemetry 또는 수동 공유 로그로 다양한 디바이스 분포 수집

## 기본값 조정 정책

초기 앱 내장 기본값은 여러 실기기 데이터의 중앙값 또는 보수적 p75를 사용한다.

예시:

| 등급 | 현재 | 벤치마크 기반 후보 |
| --- | ---: | ---: |
| B16 | 250ms | max(250ms, deviceStableB16) |
| B32 | 500ms | max(500ms, deviceStableB32) |
| B64 | 500ms | max(750ms 또는 1000ms, deviceStableB64) |

사용자 옵션:

| 옵션 | 동작 |
| --- | --- |
| 자동 | 벤치마크 결과 사용 |
| 빠른 응답 | 벤치마크 결과보다 낮은 cap 허용, `fill=SHORT` 가능 |
| 안정 탐색 | 벤치마크 결과에 큰 safety factor 적용 |
| 수동 | 사용자가 ms 직접 조정 |

## 구현 순서

1. 폰 debug report로 실제 `fill=SHORT` 빈도 수집
2. B16/B32/B64 측정용 application service 설계
3. 벤치마크 progress dialog 추가
4. benchmark result preference 저장
5. `PlayLevelSetting.analysisLimit` 생성 시 device benchmark profile 반영
6. 사용자 옵션에 `자동 / 빠른 응답 / 안정 탐색 / 수동` 추가
7. debug report에 benchmark profile 포함

## 앱 탑재 1차 구현

우선 실제 time cap 반영 전에, 앱 로컬에서 벤치마크 파일을 생성하는 기능만 탑재했다.

동작:

1. 앱 시작 후 엔진 startup이 완료된다.
2. 내부 파일 `engine_benchmark_profile.json`이 없거나 현재 benchmark spec과 다르면 1회 벤치마크를 실행한다.
3. 벤치마크 중에는 닫을 수 없는 팝업으로 “사용자 개입 없이 진행됩니다. 느린 기기에서는 1~3분 정도 소요될 수 있습니다.”를 안내한다.
4. 팝업에는 `B16 실행시간 확보 중...`, `B32 실행시간 확보 중...`, `B64 실행시간 확보 중...` 단계와 `샘플 n / 5`, 전체 진행률이 표시된다.
5. 엔진 startup busy가 끝난 뒤 1.5초 안정화 대기를 둔다.
6. B16/B32/B64를 각각 5회 호출하되, `B16 -> B32 -> B64`를 한 라운드로 보고 총 5라운드를 반복한다.
7. 이 방식은 첫 측정 그룹에 warm-up/CPU ramp-up 비용이 몰리는 오염을 줄이기 위한 것이다.
8. 각 visits별 `minMs`, `avgMs`, `maxMs`, root min/avg/max, fill OK/SHORT/UNKNOWN, sample details를 저장한다.
9. 완료 후에는 별도 완료 팝업으로 B16/B32/B64별 `min / max / avg`, root visits 요약, fill count를 보여주고, 사용자가 `확인`을 누르면 닫는다.
10. 벤치마크 중에는 엔진 busy 상태로 표시하고, 완료 후 벤치마크 시작 전 game state로 엔진을 다시 sync한다.
11. `Copy Log`에는 `[EngineBenchmark]` 섹션으로 저장 파일 내용이 포함된다.

측정 호출은 `ms 미지정` 또는 무제한 호출이 아니다. 현재 1차 구현은 `timeCapMs=5000`을 충분히 큰 상한으로 주고, B16/B32/B64 요청이 그 안에서 완료되는 실제 elapsed time을 저장한다. 따라서 일반적으로는 요청 visits를 채우는 데 걸린 시간을 측정하고, 5초 안에도 목표 visits를 못 채우는 기기라면 그 한계가 `maxMs` 또는 후속 diagnostics에 드러난다.

복원할 저장 대국이 있어도 benchmark는 실행된다. benchmark는 사용자 설정과 독립된 고정 benchmark state에서만 측정하고, 완료 후 benchmark 시작 전 game state로 엔진을 다시 sync한다. 이후 사용자가 저장 대국을 복원하면 engine state는 다시 해당 대국으로 맞춰진다.

저장 위치:

```text
<app internal files>/engine_benchmark_profile.json
```

저장 예시:

```json
{
  "schema": 1,
  "createdAtMillis": 1780000000000,
  "measurementVersion": 5,
  "samplesPerVisit": 5,
  "timeCapMs": 5000,
  "benchmarkPositionName": "b16-best-3-variants",
  "benchmarkRuleset": "Japanese",
  "benchmarkPositionMoves": ["Black E5", "White C4", "Black E3"],
  "metrics": [
    {
      "visits": 16,
      "samples": 5,
      "minMs": 160.1,
      "maxMs": 190.2,
      "avgMs": 171.3,
      "rootMinVisits": 16,
      "rootMaxVisits": 18,
      "rootAvgVisits": 16.8,
      "fillOk": 5,
      "fillShort": 0,
      "fillUnknown": 0,
      "sampleDetails": [
        {
          "sampleIndex": 1,
          "visits": 16,
          "elapsedMs": 170.2,
          "engineElapsedMs": 165,
          "rootVisits": 17,
          "fillStatus": "OK"
        }
      ]
    }
  ]
}
```

현재 범위:

- 저장만 수행한다.
- 실제 `PlayLevelSetting.analysisLimit`에는 아직 반영하지 않는다.
- 다음 단계에서 저장된 benchmark profile을 읽어 `자동 / 빠른 응답 / 안정 탐색 / 수동` 정책에 반영한다.

SM-S908N 1차 저장 확인:

아래 값은 최초 10회 샘플 구현 당시 측정값이다. 현재 앱 기본은 느린 폰 부담을 낮추기 위해 5회 샘플로 조정했다.

| Visits | Min ms | Avg ms | Max ms |
| ---: | ---: | ---: | ---: |
| 16 | 1491.209 | 3089.351 | 5844.946 |
| 32 | 783.649 | 1449.502 | 2590.657 |
| 64 | 1934.299 | 2687.389 | 4227.159 |

이 결과는 폰에서는 현재 고정 time cap이 부족할 수 있음을 보여준다. 다음 단계에서는 저장된 benchmark profile을 읽어 실제 엔진 요청 time cap에 반영해야 한다.

SM-S908N 5회 샘플 재측정 확인:

| Visits | Min ms | Avg ms | Max ms |
| ---: | ---: | ---: | ---: |
| 16 | 685.887 | 2309.363 | 5034.612 |
| 32 | 38.841 | 1811.875 | 2944.291 |
| 64 | 2323.803 | 4111.532 | 5272.528 |

팝업 표시 확인:

```text
엔진 벤치마크 진행 중
사용자 개입 없이 진행됩니다. 느린 기기에서는 1~3분 정도 소요될 수 있습니다.
B16 실행시간 확보 중...
샘플 1 / 5 · 전체 진행률 0 / 15
```

완료 팝업 예시:

```text
엔진 벤치마크 완료
측정 샘플: B16/B32/B64 각각 5회
측정 상한: 5000ms

B16: min 160.1ms / max 190.2ms / avg 171.3ms
  root min=16, avg=16.8, max=18 / fill OK=5, SHORT=0, UNKNOWN=0
B32: min 280.1ms / max 330.2ms / avg 295.3ms
  root min=32, avg=33.2, max=35 / fill OK=5, SHORT=0, UNKNOWN=0
B64: min 520.1ms / max 650.2ms / avg 570.3ms
  root min=64, avg=65.4, max=68 / fill OK=5, SHORT=0, UNKNOWN=0
```

## 현재 결론

방문수 기반 레벨링은 유지한다.

다만 time cap은 고정 제품 상수가 아니라 디바이스별 안정화 값으로 봐야 한다. 폰에서 `fill=SHORT`가 반복된다면, 해당 디바이스에서는 현재 time cap이 부족하므로 자동 벤치마크 기반 보정 기능을 추가하는 방향이 타당하다.

## 로컬 벤치마크 스크립트

맥북 또는 개발 머신에서 같은 성능 측정을 반복할 수 있도록 Makefile 타깃을 추가했다.

```bash
make engine-device-benchmark
```

기본값:

| 항목 | 값 |
| --- | --- |
| samples | 10 |
| visits | 16, 32, 64 |
| positions | b16-best-3-variants |
| time cap | 5000ms |
| output | `docs/engine-benchmark-logs/mac-20260610` |

옵션 예시:

```bash
make engine-device-benchmark \
  ENGINE_DEVICE_BENCHMARK_SAMPLES=20 \
  ENGINE_DEVICE_BENCHMARK_OUT=docs/engine-benchmark-logs/mac-custom \
  ENGINE_DEVICE_BENCHMARK_ARGS='--positions random --deterministic'
```

주의:

- 앱의 Top Moves `AnalysisResultCache`는 benchmark 경로에 들어가지 않는다. benchmark는 `EngineAdapter.analyze()`를 직접 호출한다.
- 다만 KataGo JSON analysis process 자체는 살아 있는 프로세스이므로, 같은 포지션을 반복 질의하면 내부 search/NN 재사용 영향이 섞일 수 있다.
- 이를 줄이기 위해 기본 benchmark 포지션은 `b16-best-3-variants`를 사용한다. 먼저 B16 최적수 3수 prefix를 만든 뒤, sample별 deterministic 변형 수순을 붙여 서로 다른 포지션에서 측정한다.
- `empty`는 같은 빈 판 반복 질의라 cache/reuse 영향을 보기 위한 비교용에 가깝다.
- `random`은 sample마다 새 random legal 9x9 position을 생성한다. 완전 랜덤 분포 확인이 필요할 때만 보조로 사용한다.

## 맥북 1차 로컬 벤치마크 결과

실행:

```bash
make engine-device-benchmark
```

결과:

- summary: `docs/engine-benchmark-logs/mac-20260610/summary.md`
- raw samples: `docs/engine-benchmark-logs/mac-20260610/samples.jsonl`
- 조건: non-deterministic, `numSearchThreads=4`, time cap `5000ms`, random position은 sample마다 새로 생성

| Position | Visits | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| random | 16 | 0 | 162.565 | 168.383 | 177.699 | 175.727 | 17.0 | 250ms |
| random | 32 | 0 | 274.513 | 292.554 | 314.810 | 300.869 | 35.0 | 400ms |
| random | 64 | 0 | 481.180 | 541.364 | 586.379 | 574.045 | 67.0 | 750ms |

맥북 기준 해석:

- B16/B32/B64 모두 `fill=OK`로 안정적으로 목표 visits를 채웠다.
- random position 기준으로 B16은 현재 250ms 기본값이면 충분하다.
- B32는 현재 500ms 기본값이면 충분하다.
- B64는 현재 500ms에서는 일부 random position에서 부족할 수 있으므로, 안정 cap 후보는 750ms 이상이다.

## 맥북 B16 3수 prefix 벤치마크 결과

실행:

```bash
make engine-device-benchmark \
  ENGINE_DEVICE_BENCHMARK_SAMPLES=5 \
  ENGINE_DEVICE_BENCHMARK_OUT=docs/engine-benchmark-logs/mac-b16best3-20260610
```

결과:

- summary: `docs/engine-benchmark-logs/mac-b16best3-20260610/summary.md`
- raw samples: `docs/engine-benchmark-logs/mac-b16best3-20260610/samples.jsonl`
- 생성 prefix: `B E5, W C4, B E3`
- 조건: non-deterministic, `numSearchThreads=4`, time cap `5000ms`, sample별 deterministic 변형 포지션

| Position | Visits | SHORT | Min ms | Avg ms | Max ms | P90 ms | Root avg | Recommended cap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| b16-best-3-variants | 16 | 0 | 68.398 | 139.174 | 160.655 | 160.655 | 17 | 250ms |
| b16-best-3-variants | 32 | 0 | 140.606 | 200.021 | 435.463 | 435.463 | 35 | 550ms |
| b16-best-3-variants | 64 | 0 | 242.257 | 247.521 | 253.707 | 253.707 | 67 | 350ms |

해석:

- 모든 샘플이 `fill=OK`로 목표 root visits를 채웠다.
- 같은 포지션을 그대로 5회 반복했을 때는 0ms대 응답이 발생해 KataGo analysis process 내부 재사용 영향이 컸다. 따라서 앱에는 단일 고정 포지션 반복이 아니라 `b16-best-3-variants` 방식을 적용한다.
- B16과 B32의 elapsed가 선형적으로 벌어지지는 않는다. 저방문수 구간에서는 JSON analysis 호출, NN 평가, search thread scheduling, 직전 같은 sample 포지션의 낮은 visits 검색 재사용이 함께 섞인다.
- 이 benchmark는 “요청 visits를 채우는가”를 판정하는 용도에 더 적합하며, B16/B32의 실력 차이를 elapsed만으로 판단하면 안 된다.
