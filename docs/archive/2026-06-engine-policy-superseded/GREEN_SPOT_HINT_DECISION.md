# 착수 전 후보수/점수 스팟 표시 결정

> [!WARNING]
> 이 문서는 2026-06 엔진 호출 정책 정리 전의 초기 결정 기록이다. 최신 기준은 `docs/ENGINE_API_CALL_POLICY.md`를 따른다.

작성일: 2026-05-31
갱신일: 2026-06-06

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
- KataGo process adapter의 `analyze()`는 `analysis_learning.cfg`가 있으면 KataGo JSON analysis protocol을 우선 사용한다.
- JSON analysis config가 없거나 실패하면 `kata-search_analyze` 검색 후보를 사용하고, 부족분은 raw NN policy/legal 후보로 보강한다.
- 단, 보드 위 Top Moves spot은 후보별 점수 손실이 있는 후보만 그린다. policy/legal fallback처럼 점수 손실이 없는 후보는 debug text와 `Copy Log`에는 남기되 회색 spot으로 표시하지 않는다.
- 앱은 `AnalysisResult.candidates`를 그대로 UI에 연결하지 않고, 현재 `GameState`의 모든 합법 착점을 포함하는 `MoveAnalysisSnapshot`으로 변환한다.
- `MoveAnalysisSnapshot`은 각 착점을 `Scored`, `PolicyOnly`, `LegalOnly`로 구분한다. 현재 보드 UI는 `Scored` 착점만 그리지만, 착수 리뷰와 로그는 전체 snapshot을 기준으로 한다.

## 현재 구현

- `GoBoard`는 `candidateMoves`를 받아 비어 있는 교차점에 후보 spot을 표시한다.
- 검색 후보는 현재 착수자 관점의 예상 score lead를 보드 위 label로 표시한다.
- 후보 색상은 KataGo JSON analysis의 `rootInfo.scoreLead` 대비 `pointLoss` 기준으로 표시한다. `rootInfo`가 없는 예외 응답은 order 0 후보 대비 손실로 fallback한다.
  - `0.5`집 이하 손실: 진한 초록
  - `1.5`집 이하 손실: 연한 초록
  - `3.0`집 이하 손실: 노랑
  - `6.0`집 이하 손실: 주황
  - 그 이상 손실: 빨강
  - policy/legal fallback처럼 점수 손실이 없는 후보: 보드 spot 없음
- 첫 번째 후보수는 더 진하고 크게 표시해 “best move”로 구분한다.
- 사람이 착수하거나, AI 응수가 완료되거나, 새 판/undo가 실행되면 이전 후보수 표시는 지운다.
- KataGo process adapter는 JSON analysis 응답의 `moveInfos`를 우선 파싱해 후보 spot을 표시한다.
- fallback 경로에서는 `kata-search_analyze` 응답의 `info move ...` 블록을 파싱한다.
- 낮은 visits/time에서 검색 후보가 요청 개수보다 적으면 `kata-raw-nn 0`의 policy 상위 후보와 합법 착점으로 부족한 후보 목록을 채운다.
- 검색 후보는 visits/winrate/score를 가질 수 있고, policy fallback 후보는 `prior` 중심으로 표시된다.
- UI 버튼명은 `Top Moves`로 표시한다.
- `Top Moves` 버튼은 토글이다. 켜져 있으면 매 사용자 차례에 준비된 후보 spot을 자동 표시한다.
- 착수 후 평가 dot을 안정적으로 남기기 위해, 사람 차례가 되면 `Top Moves` 표시 여부와 관계없이 백그라운드 pre-move analysis를 수행한다.
- 실제 요청 후보는 현재 합법 착점 수와 내부 상한 중 작은 값이다. 9x9 POC에서는 전체 합법 착점을 목표로 한다.
- 보드에는 score가 있는 후보만 표시한다.
- Top Moves와 착수 리뷰용 pre-move analysis는 대국 AI 응수 설정보다 한 단계 높은 difficulty의 visits/time을 사용한다.
  - 예: 대국 AI가 `Beginner`이면 Top Moves 분석은 최소 `Casual` 기본값을 사용한다.
  - 사용자가 visits/time을 이미 더 높게 조정한 경우에는 현재 값과 한 단계 위 기본값 중 더 큰 값을 사용해 Top Moves가 약해지지 않게 한다.
- `KataGoProcessEngineAdapter.analyze()`는 후보수 목표가 커질 때 최소 `후보수 * 20 visits`, `2000ms`로 Top Moves 분석을 일시 상향한다.
- JSON analysis query에는 `maxVisits`와 `overrideSettings.maxTime`을 전달한다.
- GTP fallback의 `kata-search_analyze`에는 time limit을 centisecond 인자로도 전달한다. Android CPU 환경에서 score가 있는 후보가 너무 적게 나오는 상황을 줄이기 위한 조치다.
- 자동 cache에 score가 있는 후보가 5개 미만이면, 사용자가 `Top Moves`를 누르는 시점에 `Full Analysis` 수준의 수동 deep analysis를 한 번 수행한다.
- Top Moves 검색 후에는 기존 AI 대국용 `EngineProfile.analysisLimit`로 `maxVisits/maxTime`을 되돌린다. 따라서 Top Moves 품질 보강이 AI 응수 강도 설정을 영구적으로 바꾸지 않는다.

## 착수 후 평가

착수 후 방금 둔 수에 대한 평가는 별도 엔진 명령을 추가하지 않고, 착수 직전에 백그라운드로 준비된 후보수 목록을 사용한다.

- 실제 착수 좌표가 직전 후보 목록에 있으면 해당 후보의 `pointLoss`로 green/yellow/red marker를 돌 중앙에 표시한다.
- 노랑/빨강 spot은 UI 색상이 아니라 엔진 후보 데이터의 문제다. JSON normal analysis가 상위 후보만 반환하고 그 후보들의 `pointLoss`가 낮으면 초록 계열만 보인다. 더 나쁜 후보까지 색칠하려면 KaTrain식 sweep/refine 분석이 필요하다.
- 직전 snapshot에 합법 착점으로는 존재하지만 아직 `pointLoss`가 없으면 착수 리뷰는 `unknown`으로 처리한다. 상위 후보 밖이라는 이유만으로 실수 색상을 찍지 않는다.
- 착수 후 marker는 바둑알 위에 작은 색상 원만 표시하고, 점수 숫자는 보드 위에 겹쳐 쓰지 않는다.
- 착수 후 marker는 단일 last marker가 아니라 `moveNumber`가 포함된 목록으로 유지한다. 따라서 AI 응수나 다음 힌트 갱신 후에도 착수 당시 평가 dot이 해당 돌 위에 남는다.
- 직전 후보 목록에는 있으나 policy fallback 후보라서 점수 손실이 없으면 unknown으로 처리한다.
- 직전 후보 목록 밖에 둔 경우 red marker와 `outside the analyzed top N` 메시지를 표시한다.
- 착수 직전 분석 cache가 실패했거나 아직 준비되지 않았으면 착수 후 평가는 표시하지 않는다. 현재 UI는 같은 KataGo process를 쓰므로 분석 중에는 입력을 잠시 막아 cache 준비 누락을 줄인다.
- 잡힌 돌, undo된 돌, 같은 좌표에 나중에 다시 둔 돌에는 오래된 marker가 재사용되지 않도록 현재 board history의 최신 착수 번호와 marker의 `moveNumber`가 일치할 때만 그린다.

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
3. 별도 `katago analysis` JSON 프로토콜 전환 검토: `rootInfo`, `moveInfos`, `ownership`, `policy`를 함께 받으면 KaTrain식 `pointsLost`, sweep/equalize 분석, partial result를 더 안정적으로 구현할 수 있다.
4. 모든 합법 착점 전수 평가는 기본 자동 Top Moves가 아니라 sweep/equalize 기반 고급 분석으로 분리한다.

자세한 KaTrain 분석 근거는 같은 archive 폴더의 `KATRAIN_TOP_MOVES_ANALYSIS.md`에 기록한다.
