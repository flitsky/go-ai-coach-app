# 착수 전 후보수/그린스팟 표시 결정

작성일: 2026-05-31

## 결론

착수 전 최적수/그린스팟 표시는 앱 UI가 엔진 구현을 직접 호출하는 방식이 아니라, `EngineAdapter.analyze()`가 반환하는 `AnalysisResult.candidates`를 보드 오버레이로 그리는 방식으로 적용한다.

현재 단계에서는 `EngineAdapter.analyze()`가 반환하는 후보수를 보드 위 초록 원으로 표시한다. Stub adapter와 KataGo process adapter 모두 같은 `CandidateMove` 계약으로 UI에 연결한다.

## KaTrain 참고 방향

KaTrain류 UI는 대체로 엔진 분석 결과의 후보수 리스트를 보드 위에 시각화한다. 앱 도메인 입장에서는 “어떤 엔진이 후보를 만들었는가”보다 “후보수 DTO를 어떻게 표시할 것인가”가 더 중요한 경계다.

이 프로젝트에서는 다음 원칙을 유지한다.

- 보드 상태 canonical source는 `shared`의 `GameState`와 move history다.
- 엔진은 평가자/추천자이며, 후보수는 `CandidateMove` DTO로만 앱에 노출한다.
- UI는 `CandidateMove` 목록만 보고 초록 오버레이를 그린다.
- KataGo process adapter의 `analyze()`는 `kata-search_analyze` 검색 후보를 우선 사용하고, 부족분은 raw NN policy 후보로 보강한다.

## 현재 구현

- `GoBoard`는 `candidateMoves`를 받아 비어 있는 교차점에 초록색 spot을 표시한다.
- 첫 번째 후보수는 더 진하고 크게 표시해 “best move”로 구분한다.
- 사람이 착수하거나, AI 응수가 완료되거나, 새 판/undo가 실행되면 이전 후보수 표시는 지운다.
- KataGo process adapter는 `kata-search_analyze` 응답의 `info move ...` 블록을 파싱해 후보 spot을 표시한다.
- 낮은 visits/time에서 검색 후보가 요청 개수보다 적으면 `kata-raw-nn 0`의 policy 상위 후보로 부족한 spot을 채운다.
- 검색 후보는 visits/winrate/score를 가질 수 있고, policy fallback 후보는 `prior` 중심으로 표시된다.
- UI 버튼명은 좁은 모바일 폭에서 줄바꿈을 피하기 위해 `Hint`로 표시한다.
- `Hints` 토글을 켜면 사람 차례가 돌아온 시점에 자동으로 현재 판을 분석한다.
- `N` 설정으로 한 번에 표시할 후보수 개수를 1-5개 범위에서 조절한다.
- 수동 `Hint` 버튼은 토글 상태와 관계없이 현재 차례에 한 번 분석을 실행한다.
- 실제 표시 개수는 요청한 `N`과 현재 판의 합법 후보 가능 수 중 더 작은 값이다.

## 후속 작업

1. 분석 취소/타임아웃 처리
2. 후보수별 win rate, score lead, visits를 보드 위 compact label로 표시
3. 자동 분석이 너무 잦아질 경우 debounce/cooldown 정책 추가
