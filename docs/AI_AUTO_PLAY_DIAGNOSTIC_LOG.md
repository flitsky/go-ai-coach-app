# AI 자동대국 진단 로그

## 목적

AI vs AI 자동대국에서 착수가 지나치게 빠르게 이어지는 원인을 분리하기 위한 런타임 로그다.

확인하려는 핵심은 다음 3가지다.

- 매 착수마다 새 국면 기준으로 `EngineAdapter.analyze()`가 호출되는가.
- 설정된 `Search Time` 값이 실제 엔진 요청의 `timeCapMs`로 내려가는가.
- 자동대국 delay가 `즉시(0ms)`일 때 엔진 응답이 빠른 구간과 맞물려 연속 착수처럼 보이는가.

## 저장 위치

앱 내부 파일:

```text
files/runtime_event_log.txt
```

ADB 수집 명령:

```bash
adb shell run-as com.worksoc.goaicoach cat files/runtime_event_log.txt
```

`Copy Log`를 누르면 디버그 리포트 하단의 `[RuntimeEventLog]` 섹션에도 같은 내용이 포함된다.

## 보존 정책

- 최대 크기: 1MB
- 1MB를 넘으면 오래된 앞부분을 버리고 최근 약 900KB만 유지한다.
- trim이 발생하면 파일 앞에 `runtime log trimmed` marker가 남는다.

## 주요 이벤트

- `app_start`: 앱 시작 시점
- `game_reset`: 새 대국 또는 대국 리셋 시점
- `engine_game_start_request`: 엔진 기반 새 대국 시작 요청
- `engine_game_start_success`: 엔진 기반 새 대국 시작 완료
- `ai_turn_schedule`: AI 턴 예약 시점
- `ai_turn_begin`: 실제 AI 분석 시작 직전
- `ai_turn_success`: AI 착수 성공 및 엔진 분석 요약
- `ai_turn_complete`: 턴 처리 완료 후 현재 상태
- `ai_turn_failure`: AI 턴 실패
- `ai_turn_endgame_*`: pass/pass 또는 보드 full 이후 종국 처리

## 해석 기준

`ai_turn_begin`과 `ai_turn_success`의 `fp`가 착수마다 달라지면 앱은 새 국면 기준으로 AI 턴을 진행하고 있는 것이다.

`summary` 안의 `KataGo search analysis with ...`와 `Visit diagnostics`를 보면 실제 요청값과 응답 시간을 확인할 수 있다.

예시:

```text
KataGo search analysis with 32 visits / 3000ms.
Visit diagnostics: request=32, root=31, elapsedMs=2031, timeCapMs=3000, fill=SHORT.
```

여기서 `timeCapMs=3000`은 최대 허용 시간이다. 엔진이 이미 충분한 결과를 갖고 있거나 더 빨리 응답하면 `elapsedMs`는 3000보다 작을 수 있다.

## 현재 의심 지점

`delay=0` 자체는 분석 생략을 뜻하지 않는다. 다만 `kata-search_analyze`가 현재 국면에서 매우 빠르게 응답하면 앱은 곧바로 다음 AI 턴을 예약한다.

따라서 다음 로그 수집에서 특히 확인할 항목은 다음과 같다.

- `ai_turn_begin`이 착수마다 1회씩만 찍히는가.
- 각 턴의 `fp`가 이전 턴과 다른가.
- `summary`의 `timeCapMs`가 메뉴 설정값과 일치하는가.
- `elapsedMs`가 비정상적으로 0~수 ms로 반복되는가.
- `root`가 요청 visits에 근접하는가, 아니면 계속 `SHORT`인가.

## 2026-06-11 분석 결론

실기기에서 자동대국이 한 판 단위로 매우 빠르게 끝나는 현상을 수집했다. 앱의 `AnalysisResultCache`는 비활성 상태였고, `fp`는 착수마다 바뀌었으므로 앱이 같은 후보수를 그대로 재사용한 문제는 아니었다.

확인된 원인은 두 단계였다.

1. 새 판 반복 시 `clear_board`만으로는 KataGo GTP 프로세스 내부 검색 트리/캐시가 충분히 비워지지 않았다.
2. 같은 판 안에서도 직전 깊은 탐색의 자식 국면이 다음 얕은 탐색에 재사용되어 B16 분석이 1~4ms로 끝나는 구간이 있었다.

대응은 다음과 같이 적용했다.

- 새 판 시작: `startNewEngineGame()`에서 KataGo process를 `stop()` 후 `initialize()`한다.
- AI 착수 분석 직전: `EngineAdapter.clearSearchCache()`를 호출한다.
- KataGo 구현체: GTP `clear_cache`를 호출한다.

랜덤 시드(`searchRandSeed`, `nnRandSeed`)는 이 정책의 대체재로 보지 않는다. 시드는 검색 다양성 또는 재현성을 조정하지만, 직전 턴이나 이전 판에서 남은 search tree/NN cache를 격리하지는 않는다.

검증 로그:

- `docs/engine-benchmark-logs/phone-autoplay-fastgame-20260611-203429/summary.md`
- `docs/engine-benchmark-logs/phone-autoplay-freshprocess-20260611-203855/summary.md`
- `docs/engine-benchmark-logs/phone-autoplay-clearcache-20260611-204836/summary.md`
