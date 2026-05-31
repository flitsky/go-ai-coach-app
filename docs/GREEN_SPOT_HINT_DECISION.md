# 착수 전 후보수/그린스팟 표시 결정

작성일: 2026-05-31

## 결론

착수 전 최적수/그린스팟 표시는 앱 UI가 엔진 구현을 직접 호출하는 방식이 아니라, `EngineAdapter.analyze()`가 반환하는 `AnalysisResult.candidates`를 보드 오버레이로 그리는 방식으로 적용한다.

현재 단계에서는 stub adapter가 반환하는 후보수를 보드 위 초록 원으로 표시한다. 실제 KataGo 후보수는 process adapter에 `kata-analyze` streaming parser와 취소 경로를 구현한 뒤 같은 UI 계약으로 연결한다.

## KaTrain 참고 방향

KaTrain류 UI는 대체로 엔진 분석 결과의 후보수 리스트를 보드 위에 시각화한다. 앱 도메인 입장에서는 “어떤 엔진이 후보를 만들었는가”보다 “후보수 DTO를 어떻게 표시할 것인가”가 더 중요한 경계다.

이 프로젝트에서는 다음 원칙을 유지한다.

- 보드 상태 canonical source는 `shared`의 `GameState`와 move history다.
- 엔진은 평가자/추천자이며, 후보수는 `CandidateMove` DTO로만 앱에 노출한다.
- UI는 `CandidateMove` 목록만 보고 초록 오버레이를 그린다.
- KataGo process adapter의 `analyze()` 구현은 별도 단계에서 `kata-analyze` 파싱과 cancellation까지 함께 다룬다.

## 현재 구현

- `GoBoard`는 `candidateMoves`를 받아 비어 있는 교차점에 초록색 spot을 표시한다.
- 첫 번째 후보수는 더 진하고 크게 표시해 “best move”로 구분한다.
- 사람이 착수하거나, AI 응수가 완료되거나, 새 판/undo가 실행되면 이전 후보수 표시는 지운다.
- 현재 process adapter는 분석 미구현 상태이므로 KataGo 모드에서 `Analyze`는 한계 메시지를 보여주며 후보 spot은 표시하지 않는다.

## 후속 작업

1. `kata-analyze` 요청/응답 parser 추가
2. 분석 취소/타임아웃 처리
3. 후보수별 win rate, score lead, visits를 보드 위 compact label로 표시
4. 대국 중 자동 low-cost background analysis 여부 결정
