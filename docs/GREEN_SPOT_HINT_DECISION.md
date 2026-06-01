# 착수 전 후보수/점수 스팟 표시 결정

작성일: 2026-05-31
갱신일: 2026-06-01

## 결론

착수 전 최적수/그린스팟 표시는 앱 UI가 엔진 구현을 직접 호출하는 방식이 아니라, `EngineAdapter.analyze()`가 반환하는 `AnalysisResult.candidates`를 보드 오버레이로 그리는 방식으로 적용한다.

현재 단계에서는 `EngineAdapter.analyze()`가 반환하는 후보수를 보드 위 색상 spot과 점수 label로 표시한다. Stub adapter와 KataGo process adapter 모두 같은 `CandidateMove` 계약으로 UI에 연결한다.

KataGo에는 “방금 둔 한 수만 별도 평가하는 UI 전용 API”를 의존하지 않는다. KaTrain과 유사하게 착수 직전 후보수 분석 결과를 캐시하고, 실제 착수 좌표가 그 후보 목록에 있었는지 매칭해 착수 후 평가 marker를 표시한다.

## KaTrain 참고 방향

KaTrain류 UI는 대체로 엔진 분석 결과의 후보수 리스트를 보드 위에 시각화한다. 앱 도메인 입장에서는 “어떤 엔진이 후보를 만들었는가”보다 “후보수 DTO를 어떻게 표시할 것인가”가 더 중요한 경계다.

이 프로젝트에서는 다음 원칙을 유지한다.

- 보드 상태 canonical source는 `shared`의 `GameState`와 move history다.
- 엔진은 평가자/추천자이며, 후보수는 `CandidateMove` DTO로만 앱에 노출한다.
- UI는 `CandidateMove` 목록만 보고 오버레이를 그린다.
- KataGo process adapter의 `analyze()`는 `kata-search_analyze` 검색 후보를 우선 사용하고, 부족분은 raw NN policy 후보로 보강한다.

## 현재 구현

- `GoBoard`는 `candidateMoves`를 받아 비어 있는 교차점에 후보 spot을 표시한다.
- 검색 후보는 현재 착수자 관점의 예상 score lead를 보드 위 label로 표시한다.
- 후보 색상은 최선 후보 대비 `pointLoss` 기준으로 표시한다.
  - `0.5`집 이하 손실: green
  - `3.0`집 이하 손실: yellow
  - 그 이상 손실: red
  - policy fallback처럼 점수 손실이 없는 후보: gray-blue
- 첫 번째 후보수는 더 진하고 크게 표시해 “best move”로 구분한다.
- 사람이 착수하거나, AI 응수가 완료되거나, 새 판/undo가 실행되면 이전 후보수 표시는 지운다.
- KataGo process adapter는 `kata-search_analyze` 응답의 `info move ...` 블록을 파싱해 후보 spot을 표시한다.
- 낮은 visits/time에서 검색 후보가 요청 개수보다 적으면 `kata-raw-nn 0`의 policy 상위 후보로 부족한 spot을 채운다.
- 검색 후보는 visits/winrate/score를 가질 수 있고, policy fallback 후보는 `prior` 중심으로 표시된다.
- UI 버튼명은 좁은 모바일 폭에서 줄바꿈을 피하기 위해 `Hint`로 표시한다.
- `Hints` 토글을 켜면 사람 차례가 돌아온 시점에 자동으로 현재 판을 분석한다.
- `N` 설정으로 한 번에 표시할 후보수 개수를 1-12개 범위에서 조절한다.
- 수동 `Hint` 버튼은 토글 상태와 관계없이 현재 차례에 한 번 분석을 실행한다.
- 실제 표시 개수는 요청한 `N`과 현재 판의 합법 후보 가능 수 중 더 작은 값이다.
- `KataGoProcessEngineAdapter.analyze()`는 후보수 N이 커질 때 최소 `N * 10 visits`, `1000ms`로 힌트 검색을 일시 상향한다.
- 힌트 검색 후에는 기존 AI 대국용 `EngineProfile.analysisLimit`로 `maxVisits/maxTime`을 되돌린다. 따라서 힌트 품질 보강이 AI 응수 강도 설정을 영구적으로 바꾸지 않는다.

## 착수 후 평가

착수 후 방금 둔 수에 대한 평가는 별도 엔진 명령을 추가하지 않고, 착수 직전에 화면에 있던 후보수 목록을 사용한다.

- 실제 착수 좌표가 직전 후보 목록에 있으면 해당 후보의 `pointLoss`로 green/yellow/red marker를 돌 중앙에 표시한다.
- 직전 후보 목록에는 있으나 policy fallback 후보라서 점수 손실이 없으면 unknown으로 처리한다.
- 직전 후보 목록 밖에 둔 경우 red marker와 `outside the analyzed top N` 메시지를 표시한다.
- 사용자가 힌트를 켜지 않았거나 착수 직전 분석 cache가 없으면 착수 후 평가는 표시하지 않는다.

이 방식은 KaTrain의 `GameNode.candidate_moves`가 부모 노드 분석에서 `pointsLost`를 계산하고, 자식 노드/리뷰 UI에서 그 정보를 참조하는 흐름과 같은 방향이다.

## 근거

- KataGo analysis engine 문서는 후보 목록 `moveInfos`에 `move`, `order`, `prior`, `visits`, `winrate`, `scoreLead` 등이 포함된다고 설명한다.
- 같은 문서는 값의 관점이 `reportAnalysisWinratesAs` 설정을 따른다고 명시한다. 현재 앱 seed config인 `gtp_learning.cfg`는 기본 GTP 분석 관점과 실제 smoke test를 기준으로 색상 계산 시 현재 착수자 관점으로 변환한다.
- 로컬 KaTrain 코드 확인 결과:
  - `core/game_node.py`의 `candidate_moves`가 `pointsLost`, `relativePointsLost`, `winrateLost`를 계산한다.
  - `gui/badukpan.py`는 `pointsLost`를 `eval_color()`에 넣어 spot 색상과 `top_move_delta_score` label을 그린다.

## 후속 작업

1. 분석 취소/타임아웃 처리
2. 자동 분석이 너무 잦아질 경우 debounce/cooldown 정책 추가
3. 별도 `katago analysis` JSON 프로토콜 전환 검토: `rootInfo`와 `moveInfos`를 함께 받으면 KaTrain식 `root_score - move_score` 기반 delta score를 더 정확하게 표시할 수 있다.
