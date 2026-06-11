# 폰 AI 자동대국 진단 요약

소스: `runtime_event_log_locked.txt`  
기기: SM-S908N, USB ADB  
관측 설정: 흑 `초급 7단계`(B32), 백 `빠른 초급 3단계`(B16), 자동대국 delay `즉시`(0ms)

## 핵심 확인

- 앱은 매 착수마다 AI 분석을 호출했다. 각 `ai_turn_success`의 before/after fingerprint가 달라서, 동일 국면을 반복 사용한 것이 아니라 착수마다 보드 상태가 전진했다.
- 메뉴의 Search Time 설정은 실제 엔진 호출까지 전달됐다.
- B16: `16 visits / 2000ms`
- B32: `32 visits / 3000ms`
- 매우 빠르게 이어지는 구간은 주로 B16 턴에서 발생했다.
- B16 sample 21회: engine elapsed `min 1ms / avg 489.1ms / max 2235ms`
- B16 100ms 미만 응답: `11 / 21`
- B32 sample 22회: engine elapsed `min 614ms / avg 2445.1ms / max 3372ms`
- B32 100ms 미만 응답: `0 / 22`
- 모든 sample은 `fill=SHORT`였다. 다만 B16의 즉시 응답 구간도 `root=15`가 많았고, 요청값 16에 거의 근접한 후보 정보가 매우 빠르게 반환됐다.

## 1차 해석

현재 로그만 보면 앱이 분석을 생략하고 착수하는 문제로 보이지는 않는다. `ai_turn_begin`과 `ai_turn_success`가 각 수마다 1회씩 찍히고, 착수 전후 fingerprint도 매번 변경된다.

의심 지점은 KataGo GTP `kata-search_analyze`가 B16 저방문수 요청에서 기존 search tree 또는 현재 국면의 이미 확보된 탐색 정보를 활용해 매우 빠르게 응답하는 점이다. 현재 구현에서 `timeCapMs`는 최대 허용 시간이며, 최소 사고 시간을 강제하지 않는다.

따라서 `timeCapMs=2000`이어도 엔진이 1~4ms에 결과를 반환하면 앱은 delay `즉시` 설정에 따라 곧바로 다음 AI 턴을 예약한다.

## 다음 수정 후보

1. 자동대국에는 엔진 search time cap과 별개로 최소 턴 표시 시간 또는 최소 autoplay delay를 둘지 검토한다.
2. AI vs AI의 기본 delay는 `즉시`보다 `0.5초` 또는 `1초`가 더 자연스럽다. 단, 사용자가 명시적으로 즉시를 선택하면 허용할 수 있다.
3. 품질 관점에서 매번 새 탐색을 강제해야 한다면 KataGo search tree/cache clear 명령 가능 여부를 별도 검토한다.
4. B16에서 즉시 반환이 과도하면 자동대국용 B16만 최소 visits 또는 minimum time 정책을 별도로 둘 수 있다.
5. `runtime_event_log.txt`는 향후 유사 이슈 분석을 위해 유지하고, `Copy Log`에 계속 포함한다.
