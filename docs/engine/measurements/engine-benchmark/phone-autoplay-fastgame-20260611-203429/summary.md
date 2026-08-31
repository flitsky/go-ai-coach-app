# 실기기 자동대국 초고속 진행 로그 분석

## 상황

- 수집 시각: 2026-06-11 20:34 KST
- 기기: SM-S908N
- 설정 파일 기준:
  - Black: `중급 5단계` (`B64`)
  - White: `빠른 초급 3단계` (`B16`)
  - Search Time: B16 `2000ms`, B32 `3000ms`, B64 `4500ms`
  - Auto delay: `0ms`

사용자가 보고한 “한 판이 10초 내외로 끝나는 것처럼 보이는” 문제를 확인하기 위해 앱 내부 `files/runtime_event_log.txt`를 즉시 수집했다.

## 핵심 관찰

- 전체 `ai_turn_success`: 750건
- 로그상 자동대국은 move number reset 기준 12판으로 분리된다.
- 마지막 두 판은 착수 순서가 완전히 동일했다.
- 11번째 판:
  - 70수, 약 47.3초
  - B64 평균 약 `901.6ms`, B16 평균 약 `29.9ms`
- 12번째 판:
  - 70수, 약 20.6초
  - B64 평균 약 `69.5ms`, B16 평균 약 `1.1ms`
  - 대부분의 착수가 1~4ms 안에 반환됐다.

## 원인 판단

앱의 `AnalysisResultCache`는 이미 기본 비활성 상태였고, 로그의 `fp`는 착수마다 갱신됐다. 따라서 “앱이 이전 후보수를 그대로 재사용했다”는 증거는 없다.

반면 같은 앱 프로세스와 같은 KataGo GTP 프로세스에서 동일한 AI 대국이 반복되면서, KataGo 내부 검색 트리/NN cache가 `clear_board` 이후에도 반복 국면을 사실상 즉시 반환한 정황이 강하다.

## 1차 대응

`EngineAdapter.startNewEngineGame()`에서 `clear_board`만 호출하지 않고 KataGo process를 `stop()` 후 `initialize()`하도록 바꿨다. 새 판 시작 시 프로세스를 fresh하게 만들기 위한 조치다.
