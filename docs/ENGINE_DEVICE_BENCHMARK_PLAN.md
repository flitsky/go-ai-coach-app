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
| positions | empty, random |
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

- `empty`는 같은 빈 판을 반복 질의하므로 KataGo 내부 cache/reuse 영향이 섞일 수 있다.
- `random`은 sample마다 새 random legal 9x9 position을 생성하므로, 디바이스 안정 time cap 판단에는 `random` 결과를 우선 사용한다.

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
