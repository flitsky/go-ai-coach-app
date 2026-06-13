# Engine Search Mode 전환 로드맵

작성일: 2026-06-13

## 목표

AI 자동대국은 우선 빠른 대국 체감을 위해 현행 GTP stateful fast path를 유지한다. 동시에 JSON position analysis를 실험할 수 있는 정책 경계를 먼저 만들고, 이후 단계에서 성능/공정성 데이터를 보고 전환 여부를 결정한다.

## 단계별 로드맵

| 단계 | 작업 | 상태 | 판단 기준 |
| ---: | --- | --- | --- |
| 1 | `EngineSearchMode.GtpStatefulFast` / `EngineSearchMode.JsonPositionAnalysis` 정책 분리 | 완료 | 기본 동작은 GTP fast path 유지. 자동 AI 턴 context, session interface, runtime log에 search mode가 명시되어야 함 |
| 2 | AI vs AI 자동대국에만 JSON position analysis 실험 모드 추가 | 대기 | 메뉴/설정 또는 내부 실험 flag로 자동대국만 JSON mode 선택 가능 |
| 3 | B16 vs B32, B32 vs B64, B16 vs B64 각각 50판 이상 비교 | 대기 | 승률, 평균 root visits, fill rate, 평균 착수 시간 수집 |
| 4 | 폰에서 B16/B32/B64 latency와 `rootInfo.visits` fill 수집 | 대기 | 실기기에서 B16/B32/B64가 사용 가능한 응답시간인지 확인 |
| 5 | 결과가 좋으면 AI vs AI 기본값을 JSON으로 전환, 사람 vs AI는 GTP reuse 유지 또는 옵션화 | 대기 | JSON mode가 자동대국 공정성과 체감 속도를 모두 만족할 때만 전환 |

## 1단계 구현 원칙

- `EngineSearchMode`는 search budget이 아니라 engine orchestration 정책이다.
- 현재 기본값은 반드시 `GtpStatefulFast`다.
- JSON mode는 이번 단계에서 실제 AI 착수 경로로 켜지지 않는다.
- `EngineSessionClient`와 자동 AI 실행 context에 mode를 통과시켜 다음 단계가 UI/엔진 코드를 크게 흔들지 않고 붙도록 한다.
- runtime log에 `searchMode`를 남겨 향후 실험 로그를 비교 가능하게 한다.

## 1단계 완료 기록

- `shared`에 `EngineSearchMode`를 추가했다.
- 자동 AI 턴 execution context와 `EngineSessionClient.runAutoAiTurn(...)` 경계에 `searchMode`를 통과시킨다.
- 기본값은 `GtpStatefulFast`이고, 현재 AI vs AI 자동대국 동작은 기존 GTP fast path를 유지한다.
- AI vs AI search cache 격리도 `GtpStatefulFast`일 때만 `clearSearchCache()`를 호출하도록 제한했다.
- runtime event log의 `ai_turn_begin`에 `searchMode=GtpStatefulFast`가 남는다.

## 2~5단계 보류 이유

JSON position analysis는 구조적으로 장점이 있지만, Android 실기기 latency와 자동대국 승률 분포 검증 전에는 기본값으로 바꾸지 않는다. 특히 현재 GTP 경로는 이미 빠른 대국 체감을 제공하고 있으므로, 사용자 플레이 품질을 흔드는 변경은 실험 모드와 데이터 확보 이후에 진행한다.

## 맥북 1차 latency 비교

2026-06-13에 맥북 Homebrew KataGo v1.16.4 Metal backend에서 GTP fast path와 JSON position analysis를 같은 benchmark position으로 비교했다.

- raw/summary: `docs/engine-benchmark-logs/search-mode-mac-20260613/`
- command: `ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES=10 make engine-search-mode-benchmark`
- 기준 포지션: `B E5, W C4, B E3` 이후 sample별 변형
- time cap: `5000ms`
- GTP fast: 앱의 AI vs AI 격리 흐름에 맞춰 `clear_cache` 후 `kata-search_analyze`
- JSON position analysis: `moves`, `maxVisits`, `overrideSettings.maxTime`을 넣는 요청별 analysis query
- thread 조건: 현재 앱 기본값에 맞춰 GTP `numSearchThreads=1`, JSON `numAnalysisThreads=1`, `numSearchThreads=4`

| Visits | GTP avg ms | JSON avg ms | JSON/GTP | GTP root estimate | JSON rootInfo |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 16 | 108.353 | 167.361 | 1.54x | 15 | 17 |
| 32 | 224.661 | 173.103 | 0.77x | 31 | 34.9 |
| 64 | 424.623 | 282.043 | 0.66x | 63 | 67 |

해석:

- 맥북에서는 JSON position analysis도 B16/B32/B64 root visits를 안정적으로 채웠다.
- B16은 JSON이 GTP보다 느렸고, B32/B64는 현재 앱 thread 조건에서는 JSON이 더 빨랐다.
- GTP fast path는 JSON의 `rootInfo.visits` 같은 원본 root 값을 주지 않으므로 후보별 visits 합산 추정치를 기록한다. 그래서 `15/31/63`처럼 목표보다 1 낮게 보일 수 있다.
- 이 결과는 2단계 JSON 실험 모드를 진행할 근거는 되지만, 폰 기본값 전환 근거로는 부족하다. 다음에는 Android 실기기에서 같은 로그 포맷으로 latency와 fill을 수집해야 한다.

## 폰 1차 latency 비교

2026-06-13에 Android 실기기 `SM-S908N`에서 앱에 번들된 KataGo v1.16.4 Eigen(CPU) backend를 ADB `run-as`로 실행해 같은 benchmark를 수행했다.

- raw/summary: `docs/engine-benchmark-logs/search-mode-phone-20260613/`
- command: `python3 scripts/run-katago-search-mode-benchmark.py --samples 3 --time-cap-ms 10000 --adb-serial 192.168.35.3:45513 --out-dir docs/engine-benchmark-logs/search-mode-phone-20260613`
- 기준 포지션: `B E5, W C4, B E3` 이후 sample별 변형
- time cap: `10000ms`
- GTP fast: 앱의 AI vs AI 격리 흐름에 맞춰 `clear_cache` 후 `kata-search_analyze`
- JSON position analysis: `moves`, `maxVisits`, `overrideSettings.maxTime`을 넣는 요청별 analysis query
- thread 조건: GTP `numSearchThreads=1`, JSON `numAnalysisThreads=1`, `numSearchThreads=4`

| Visits | GTP avg ms | JSON avg ms | JSON/GTP | GTP root estimate | JSON rootInfo |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 16 | 3815.258 | 4759.967 | 1.25x | 15 | 17 |
| 32 | 7602.830 | 3067.418 | 0.40x | 31 | 35 |
| 64 | 10167.884 | 4994.955 | 0.49x | 47 | 67 |

해석:

- 이 폰에서는 B32/B64 기준 JSON position analysis가 GTP fast보다 빠르게 목표 root visits를 채웠다.
- JSON은 B16/B32/B64 모두 `rootInfo` 기준 fill OK였다.
- GTP fast의 root 값은 원본 root visits가 아니라 후보별 visits 합산 추정치다. B16/B32의 `15/31`은 이 추정 방식 때문에 목표보다 1 낮게 보일 수 있지만, B64의 `47`은 10초 cap에서도 목표를 채우지 못한 신호로 본다.
- 따라서 AI vs AI 레벨링 공정성과 visit fill을 중시하는 실험에서는 JSON mode 우선 검증 가치가 높다.
- 사람 vs AI 기본 대국은 현재 체감 속도가 중요한 경로이므로 GTP fast 유지가 여전히 합리적이다.

## 원격 폰 데이터 수집 표준

앞으로 엔진 모드별 폰 데이터 수집은 ADB `run-as` benchmark를 기본 방식으로 사용한다.

```bash
ENGINE_PHONE_BENCHMARK_SERIAL=<adb-serial> \
ENGINE_SEARCH_MODE_BENCHMARK_SAMPLES=3 \
ENGINE_PHONE_SEARCH_MODE_BENCHMARK_OUT=docs/engine-benchmark-logs/search-mode-phone-YYYYMMDD \
make engine-search-mode-benchmark-phone
```

이 방식의 목적은 앱 UI를 거치지 않고, 폰에 설치된 debug 앱의 실제 bundled `libkatago.so`, app-private model/config를 직접 실행해 GTP fast와 JSON position analysis의 순수 엔진 비용을 비교하는 것이다.

운영 기준:

- 엔진 모드 비교, B16/B32/B64 root fill, thread/config 변경 전후 비교는 `run-as` benchmark를 우선한다.
- 사용자 체감 속도, benchmark popup, 앱 lifecycle, 화면 반응성은 별도 UX 기반 benchmark로 확인한다.
- raw/summary는 항상 `docs/engine-benchmark-logs/search-mode-phone-<date>/` 아래에 저장한다.
- 같은 폰에서 반복 측정할 때도 output directory를 날짜 또는 실험명으로 분리해 이전 결과를 덮어쓰지 않는다.

## 다음 작업 진입 조건

2단계에 들어가기 전에 다음이 준비되어야 한다.

- `EngineSearchMode.JsonPositionAnalysis`가 runtime log와 debug report에 드러나는지 확인
- AI vs AI 자동대국 setting 또는 내부 실험 flag 설계
- JSON mode에서 선택된 AI move를 GTP game-state engine에도 동기화하는 경로 정의
- latency/visit fill 로그 포맷 확정
