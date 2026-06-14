# 형세 평가와 계가 기능 결정

작성일: 2026-06-01

## 결론

대국 중 형세 지표와 양쪽 패스 이후 계가는 모두 `EngineAdapter` 계약 뒤에 둔다.

- 중간 형세 평가는 `EngineAdapter.estimateScore()`로 요청한다.
- 수순별 형세 추이는 `shared`의 `ScoreTimeline`에 `ScoreSnapshot`으로 기록한다.
- 양쪽이 연속으로 `pass`하면 `EngineAdapter.deadStones()`로 엔진이 판단한 사석을 먼저 받아 앱 `GameState`에서 제거한다.
- 화면 최종 계가는 사석 정리 후 `shared`의 `BoardScorer`가 현재 ruleset에 맞춰 계산한다.
- 단, 엔진 dead list가 비어 있거나 사석 정리가 불완전해 로컬 area final과 엔진/Top Moves 형세 추정이 크게 충돌하면, KaTrain처럼 `?`가 붙은 불확정 추정 점수를 우선 표시한다.
- `EngineAdapter.scoreFinal()`의 원문 결과는 진단 로그에 함께 남긴다.
- 엔진이 없는 `2P 테스트` 모드는 `shared`의 현재 ruleset 로컬 scoring projection을 사용한다.

## 종국 판정 SLA 정책

2026-06-14 실기기 로그에서 `Time cap OFF` 상태의 양패스 종국 처리 중 `final_status_list dead`가 약 17.9초, `final_score`가 약 28.8초 걸린 사례가 재현됐다. 일반 착수 분석은 2초 안팎으로 끝나더라도, 특정 국면/기기/엔진 상태 조합에서는 GTP 종국 명령이 사용자에게 1분 가까운 대기를 요구할 수 있다.

주의할 점은 `Search Time`의 `maxTime`이 단순히 개별 착수 분석 함수의 지역 인자만은 아니라는 점이다. 현재 KataGo GTP adapter는 `kata-set-param maxTime`을 process 전역 파라미터로 설정한다. `Time cap OFF`일 때는 `maxTime`을 명시적으로 해제/초기화하지 않고 건너뛰므로, 종국 명령이 전역 `maxTime` 상태의 영향을 받는지 여부는 별도 검증 대상이다. 사용자가 관측한 “time cap ON일 때 종국이 빨랐다”는 현상은 이 전역 상태 또는 해당 종국 국면 차이로 설명될 가능성이 있다.

따라서 종국 UX는 다음 2심판 구조를 목표로 한다.

- 부심 판정: 기본 종국 처리다. 엔진 타임 제약 옵션이 꺼져 있어도 종국 처리 전체는 5초 SLA로 제한한다. 로컬 사석 fallback, 이미 확보된 Top Moves/NN estimate, 짧은 제한의 엔진 사석 판정을 조합해 화면 결과를 빠르게 표시한다. 5초 안에 엔진 종국 결과가 충분히 오지 않으면 로컬/기존 분석 기반의 불확정 결과를 표시하고 로그에 남긴다.
- 주심 판정: 사용자가 명시적으로 `이의 제기: 주심 분석 요구`를 눌렀을 때만 실행한다. 시간 제한 없이 또는 더 긴 제한으로 실행하는 정밀 검증이며, 기본 대국 엔진 process에서 몰래 백그라운드 작업으로 돌리지 않는다. 별도 engine worker/process 또는 session snapshot 기반 작업으로 분리할 수 있을 때 활성화한다.
- 부심과 주심 결과가 의미 있게 다르면 `critical` diagnostic event로 기록한다. 개발 중에는 Copy Log/run-as 수집 대상으로 삼고, 출시 후에는 사용자 동의 기반 오류 전송 후보로 다룬다.
- `Search Time = OFF`는 대국 중 AI 착수 품질을 위한 설정이지, 종국 화면 대기를 무제한으로 허용한다는 의미가 아니다. 종국에는 별도 안전 정책을 둔다.
- 사용자가 새 게임, 무르기, 앱 종료, ruleset 변경을 수행하면 진행 중인 주심 판정은 취소하거나 현재 대국에 반영하지 않는다. 작업에는 match/session generation id를 붙이고, generation이 달라진 결과는 폐기한다. 같은 GTP process를 쓰는 작업을 timeout으로 끊었다면 process stream 오염 가능성이 있으므로 process restart 후 재동기화한다.

이 정책의 핵심은 사용자가 보는 최종 화면을 5초 안에 안정화하고, 긴 엔진 검증은 사용자가 원할 때만 수행하는 명시적 이의 제기 흐름으로 분리하는 것이다.

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

따라서 중간 형세는 `kata-raw-nn 0` 기반의 빠른 지표로 표시하고, 양패스 후에는 `final_status_list dead`로 사석을 정리한 앱 상태를 기준으로 최종 계가를 표시한다. 다만 실제 첨부 로그처럼 `final_status_list dead`가 빈 목록인데 NN score lead가 로컬 final과 크게 충돌하거나, pass 직전 Top Moves 최선 continuation과 pass 이후 final이 크게 충돌하는 경우가 있다. 이때는 판이 아직 정리되지 않은 상태로 보고 `B+28.5?`, `B+6.9?` 같은 불확정 추정 점수를 표시한다. `final_score`는 앱 표시 결과와 비교하기 위한 진단값으로 유지한다.

## KaTrain 참고 결과

KaTrain은 착수 중 포획 규칙과 prisoners를 자체 게임 상태에서 관리한다. 또한 양쪽 pass 상태의 최종 표시에서 단순히 엔진 `final_score`만 쓰지 않고, 분석 노드의 ownership을 이용한 `manual_score`를 병행한다.

참고한 공식 소스:

- https://github.com/sanderland/katrain/blob/master/katrain/core/game.py: `end_result`, `manual_score`
- https://github.com/sanderland/katrain/blob/master/katrain/core/game_node.py: 분석 결과 ownership 저장

이 결정에서 가져온 핵심은 “엔진 분석값을 UI에 바로 붙이지 않고, 앱의 종료 상태 모델을 먼저 정리한 뒤 표시한다”는 경계다.

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

## 수순별 score graph

KaTrain의 `ScoreGraph`는 착수 노드마다 분석 결과의 `scoreLead`와 `winrate`를 읽고, 그래프 위젯은 그 배열만 시각화한다. Go AI Coach도 같은 방향을 따른다.

- `ScoreSnapshot`
  - `moveNumber`
  - White 기준 `whiteScoreLead`
  - White 기준 `whiteWinRate`
  - 값의 출처인 `ScoreSnapshotSource`
- `ScoreTimeline`
  - 같은 수순의 스냅샷은 최신 값으로 교체한다.
  - 무르기 후 미래 수순 스냅샷은 잘라낸다.
  - 최종 계가 결과도 같은 timeline에 `FinalScore`로 기록한다.

Android UI는 `ScoreSnapshot` 목록을 받아 그래프를 그리기만 한다. 엔진 분석 방식, 로컬 2P 추정 방식, 최종 계가 방식은 UI와 직접 결합하지 않는다.

표시 방향은 UI에서만 변환한다. `ScoreSnapshot`은 계속 White 기준 값을 보관하되, Android score graph는 흑 우세가 위쪽, 백 우세가 아래쪽에 오도록 부호를 뒤집어 렌더링한다. 오른쪽 축 라벨 `B`/`W`는 이 화면 표시 방향을 안내한다.

## 양패스 후 계가

`GameState.hasConsecutivePasses()`가 true가 되면 게임을 종료 상태로 전환한다.

- AI 대국 모드
  - 사람과 AI가 연속으로 pass하면 `KataGoProcessEngineAdapter.deadStones()`가 GTP `final_status_list dead`를 호출한다.
  - `DeadStoneCleaner`가 엔진이 죽은 돌로 표시한 좌표를 앱 `GameState`에서 제거한다.
  - 엔진 dead list가 비어 있거나 엔진 조건 변경으로 흔들릴 수 있으므로, `DeadStoneDetector`가 한 활로짜리 즉시 포획 가능 그룹을 로컬 사석 fallback으로 추가 감지한다.
  - 제거된 상대 돌은 포획 수에도 반영한다.
  - 화면 최종 점수는 정리된 `GameState`를 `BoardScorer`로 계산한 결과를 표시한다.
  - 사석 제거가 없고 로컬 area final과 엔진 NN estimate의 White lead 차이가 10집 이상이면 `EndgameScoreSelector`가 불확정 엔진 추정 점수를 선택한다.
  - 사용자가 pass로 양패스를 만들기 직전 Top Moves 후보가 있고, 최선 continuation과 로컬 final의 White lead 차이가 10집 이상이면 `EndgameScoreSelector`가 불확정 pre-pass Top Moves 추정 점수를 선택한다.
  - KataGo `final_score` 원문은 endgame debug log에 `diagnosticKataGoFinalScore`로 남긴다.
- 2P 테스트 모드
  - 로컬 `BoardScorer`로 현재 계가 방식의 estimate를 계산한다.
  - 엔진 상태와 동기화되지 않으므로 자동 사석 제거는 아직 적용하지 않는다.

## 종료 오류 수집

최종 계가가 실제 체감 판세와 다르게 보일 때 비교를 위해 앱에 `Copy Log` 버튼을 둔다.

- 버튼은 새 엔진 명령을 실행하지 않고, 클릭 시점의 앱 상태를 클립보드에 복사한다.
- 포함 항목:
  - 실행 모드, 엔진 이름/프로필/분석 제한, Top Moves 설정
  - 현재 `GameState`: board size, ruleset, next player, move count, capture count, ko 상태, 양패스 여부
  - 9x9 보드 텍스트 덤프 (`X` Black, `O` White, `.` empty)
  - 전체 수순 목록과 현재 돌 목록
  - 마지막 종료 처리 로그와 화면에 표시된 engine/score/candidate/move review 텍스트
  - 클릭 시점의 로컬 `BoardScorer` 재계산 결과
- AI 모드의 엔진 `final_score` 결과와 로컬 ruleset score를 함께 비교할 수 있게 하기 위한 진단용 기능이다.
  - AI 모드 양패스 종료 후에는 정리 전/후 stone count, 제거된 사석 좌표, 표시 점수 출처, 로컬 ruleset score, 엔진 estimate, KataGo 원문 final score가 함께 기록된다.
  - pass 직전 Top Moves 후보가 계가 선택에 영향을 준 경우를 추적하기 위해 `prePassTopMoves`도 함께 기록한다.

## 현재 한계

- 로컬 2P 계가는 “사석 지정 없는 현재 ruleset estimate”다.
- 실제 제품 수준 계가에는 엔진 자동 사석 판정 결과를 사용자가 확인/수정하는 dead-stone marking UI가 필요하다.
- `kata-raw-nn` ownership은 최종 집 판정이 아니라 중간 형세 지표다.
- `final_status_list dead`는 AI 모드 종료 정리에 사용하지만, 사용자가 사석을 직접 확정하는 협상형 계가 UI는 후속 작업으로 분리한다.

## 오류 케이스 회귀 테스트

- `docs/error-cases/pass-pass-dead-stones-g2-h2.md`
  - 사용자 debug report에서 우하단 `G2`, `H2` 백 2점이 사석인데, 실제로 따내고 종료한 경우와 사석인 채로 pass 종료한 경우의 평가가 달라지는 문제를 기록했다.
  - 해당 케이스는 `EndgameRegressionTest`로 고정했다.
- `docs/error-cases/pass-before-cleanup-top-move-flip.md`
  - 사용자 debug report에서 pass 직전 Top Moves는 흑의 정리/continuation을 강하게 추천하지만, 흑이 바로 pass하면 raw final이 백 승리로 뒤집히는 문제를 기록했다.
  - 해당 케이스는 `EndgameRegressionTest`와 `EndgameScoreSelectorTest`로 고정했다.
- `docs/error-cases/pass-after-white-pass-score-flip-20260607.md`
  - White pass 직후 Score graph는 `B+11.4` 형세를 보이지만, 사석 제거 없이 local area를 계산하면 `W+1.5`가 되는 문제를 기록했다.
  - 로컬 KataGo 1.16.4 재현에서는 Black pass 후 `final_status_list dead`가 `H9 G8 J6`, `final_score`가 `B+10.5`를 반환했다.
  - 해당 케이스는 `EndgameRegressionTest.passAfterWhitePassNeedsEngineDeadListBeforeAreaScoring`과 `EndgameRegressionTest.passAfterWhitePassUsesPrePassTopMovesIfDeadListIsMissing`로 고정했다.
