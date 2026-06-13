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

## 다음 작업 진입 조건

2단계에 들어가기 전에 다음이 준비되어야 한다.

- `EngineSearchMode.JsonPositionAnalysis`가 runtime log와 debug report에 드러나는지 확인
- AI vs AI 자동대국 setting 또는 내부 실험 flag 설계
- JSON mode에서 선택된 AI move를 GTP game-state engine에도 동기화하는 경로 정의
- latency/visit fill 로그 포맷 확정
