# Pass 후 W+1.5로 뒤집히는 계가 케이스

작성일: 2026-06-07

## 원본 로그

- 첨부 파일: `/Users/ryan9kim/.codex/attachments/4ea72ab8-1a9e-4cfa-b609-2e40fba2b748/pasted-text.txt`
- debug report 생성 시점: `createdAtMillis=1780783478298`
- 모드: `HumanVsAi`
- 엔진: `KataGo`
- 룰셋: `Chinese`
- 상태: `gameEnded=false`, `nextPlayer=Black`
- 마지막 수: `White pass`
- 수순: 60수
- 포획 수: Black 9, White 0

## 증상

사용자는 화면상 흑이 약 9.5집 승으로 보이는 상황에서 Black이 pass해 대국을 종료하면 앱이 `W+1.5`로 뒤집힌다고 보고했다.

로그의 `[LocalAreaScoreNow]`는 다음 값을 남겼다.

- Black area 40
- White area 35 + komi 6.5 = 41.5
- 결과: `W+1.5`

이 값은 “현재 보드의 돌을 그대로 둔 중국식 area estimate”다. dead stone cleanup이 적용된 최종 계가가 아니다.

## Score graph의 B+11.4와 W+1.5가 다른 이유

`[ScoreTimeline]` 마지막 값은 다음과 같다.

- `whiteScoreLead=-11.419`
- 즉 Score graph는 `B+11.4`에 가까운 엔진 중간 형세 추정을 표시한다.

이 값은 KataGo `kata-raw-nn 0` 계열의 추정값이다. 최종 계가가 아니라, 현재 판에서 정상적으로 마무리하면 어느 쪽이 얼마나 유리한지 보는 지표다.

반면 `W+1.5`는 사석을 제거하지 않은 현재 board map을 그대로 중국식 area로 계산한 값이다. 따라서 두 값은 서로 다른 질문에 대한 답이다.

- `B+11.4`: 엔진이 본 중간 형세/마무리 기대값
- `W+1.5`: 현재 돌을 그대로 두고 사석 제거 없이 계산한 local area 값

## Top Moves가 보여준 단서

로그의 `candidateText`는 Black 차례에서 다음 후보를 보여준다.

- `Black G9`: 약 흑 10.3집 우세
- `Black J5`: 약 흑 10.1집 우세
- `Black pass`: 손실 약 8.5집

즉 pass는 최선 마무리 수가 아니며, 앱 자체도 pass가 큰 손해임을 이미 분석하고 있다.

## 로컬 KataGo 재현

같은 수순을 로컬 KataGo 1.16.4에 재생했다.

사용한 핵심 명령:

```text
boardsize 9
komi 6.5
kata-set-rules chinese
...원본 60수 재생...
kata-raw-nn 0
kata-search_analyze B 310
play B pass
final_status_list dead
final_score
kata-raw-nn 0
```

재현 결과:

- White pass 직후 `kata-raw-nn 0`: `whiteLead -11.180`
- Black pass 후 `final_status_list dead`: `H9 G8 J6`
- Black pass 후 `final_score`: `B+10.5`
- Black pass 후 `kata-raw-nn 0`: `whiteLead -2.002`

따라서 같은 상태에서 KataGo가 dead list를 제대로 반환하면 앱 최종 표시는 `W+1.5`가 아니라 `B+10.5`가 되어야 한다.

## 테스트 불변식

이번 케이스는 다음 불변식으로 고정한다.

1. 원본 보드에 Black pass만 추가하고 사석 제거 없이 `BoardAreaScorer`를 돌리면 `W+1.5`가 나온다.
2. 같은 최종 보드에서 KataGo dead list `H9/G8/J6`를 제거한 뒤 area scoring하면 `B+10.5`가 나온다.
3. dead list를 받지 못하더라도, pass 직전 Top Moves에 scored play 후보와 scored pass 후보가 있고 local final과 10집 이상 충돌하면 `UnsettledPrePassTopMoveEstimate`를 선택해야 한다.

## 추가된 테스트

`shared/src/commonTest/kotlin/com/worksoc/goaicoach/shared/EndgameRegressionTest.kt`

- `passAfterWhitePassNeedsEngineDeadListBeforeAreaScoring`
- `passAfterWhitePassUsesPrePassTopMovesIfDeadListIsMissing`

## 해석

이번 문제는 중국식/일본식 차이만으로 설명되지 않는다. 핵심은 종국 시점에 사석 정리가 적용되었는지 여부다.

다만 사용자가 수동으로 계산하는 방식이 territory scoring에 가까우면, 중국식 area estimate와 체감 결과가 계속 다르게 느껴질 수 있다. 따라서 장기적으로는 계가 방식 옵션과 사석 확인 UX가 필요하다.
