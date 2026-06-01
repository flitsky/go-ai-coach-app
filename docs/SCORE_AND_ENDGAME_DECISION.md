# 형세 평가와 계가 기능 결정

작성일: 2026-06-01

## 결론

대국 중 형세 지표와 양쪽 패스 이후 계가는 모두 `EngineAdapter` 계약 뒤에 둔다.

- 중간 형세 평가는 `EngineAdapter.estimateScore()`로 요청한다.
- 양쪽이 연속으로 `pass`하면 `EngineAdapter.scoreFinal()`로 최종 계가를 요청한다.
- 엔진이 없는 `2P 테스트` 모드는 `shared`의 로컬 area scoring projection을 사용한다.

## KataGo에서 얻을 수 있는 데이터

KataGo GTP에서 확인한 관련 명령은 다음이다.

- `kata-raw-nn 0`
  - `whiteWin`
  - `whiteLead`
  - `whiteOwnership`
  - `policy`
- `final_score`
  - 예: `B+74.5`, `W+6.5`
- `final_status_list`
  - KataGo 1.16.4 GTP 기준 인자는 `alive`, `seki`, `dead`만 허용된다.
  - `black_territory`, `white_territory` 같은 territory list 인자는 현재 GTP에서는 거부된다.

따라서 중간 형세는 `kata-raw-nn 0` 기반의 빠른 지표로 표시하고, 최종 계가는 `final_score`를 우선 사용한다.

## 중간 형세 지표

현재 앱은 `Eval` 버튼을 누르면 다음 정보를 표시한다.

- White win rate / Black win rate
- White 기준 score lead
  - 양수: White 우세
  - 음수: Black 우세
- `whiteOwnership` 기반 영향권 카운트
  - threshold는 현재 `0.15`
  - `Black`, `White`, `unclear`로 구분

주의: 이 값은 “현재 집이 확정되었다”는 의미가 아니라, 신경망이 보는 영향권/형세 지표다. 사활, 끝내기, 미확정 사석 상태에 따라 최종 계가와 다를 수 있다.

## 양패스 후 계가

`GameState.hasConsecutivePasses()`가 true가 되면 게임을 종료 상태로 전환한다.

- AI 대국 모드
  - 사람과 AI가 연속으로 pass하면 `KataGoProcessEngineAdapter.scoreFinal()`이 GTP `final_score`를 호출한다.
  - 결과는 `FinalScoreResult`로 변환해 화면에 표시한다.
- 2P 테스트 모드
  - 로컬 `BoardAreaScorer`로 중국식 area estimate를 계산한다.
  - dead-stone marking은 아직 없다.

## 종료 오류 수집

최종 계가가 실제 체감 판세와 다르게 보일 때 비교를 위해 앱에 `Copy Log` 버튼을 둔다.

- 버튼은 새 엔진 명령을 실행하지 않고, 클릭 시점의 앱 상태를 클립보드에 복사한다.
- 포함 항목:
  - 실행 모드, 엔진 이름/프로필/분석 제한, 힌트 설정
  - 현재 `GameState`: board size, ruleset, next player, move count, capture count, ko 상태, 양패스 여부
  - 9x9 보드 텍스트 덤프 (`X` Black, `O` White, `.` empty)
  - 전체 수순 목록과 현재 돌 목록
  - 마지막 종료 처리 로그와 화면에 표시된 engine/score/candidate/move review 텍스트
  - 클릭 시점의 로컬 `BoardAreaScorer` 재계산 결과
- AI 모드의 엔진 `final_score` 결과와 로컬 area score를 함께 비교할 수 있게 하기 위한 진단용 기능이다.

## 현재 한계

- 로컬 2P 계가는 “사석 지정 없는 area estimate”다.
- 실제 제품 수준 계가에는 dead-stone marking UI가 필요하다.
- `kata-raw-nn` ownership은 최종 집 판정이 아니라 중간 형세 지표다.
- `final_status_list dead`를 이용한 사석 후보 표시와 사용자의 사석 확정 UI는 후속 작업으로 분리한다.
