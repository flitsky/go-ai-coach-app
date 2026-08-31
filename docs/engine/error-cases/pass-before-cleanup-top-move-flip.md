# 오류 케이스: 사석 정리 전 pass로 최종 승자가 뒤집히는 상황

작성일: 2026-06-06

## 원본 로그

- 사용자 제공 debug report: `/Users/ryan9kim/.codex/attachments/9e97d1b9-2ada-44f5-bb2b-a9d91ab4574e/pasted-text.txt`
- 앱 상태: `HumanVsAi`, `KataGo`, `boardSize=9`, `ruleset=Chinese`
- 로그 시점: `gameEnded=false`, `consecutivePasses=false`, `nextPlayer=Black`
- 마지막 수: `White pass`

## 증상

사용자 관점에서는 흑이 우세하고, 흑이 한 수 더 둬서 사석을 실제로 따낸 뒤 pass하면 흑 승리로 계산된다. 그러나 흑이 바로 pass해 양패스 종료를 만들면 raw local area와 KataGo `final_score`가 `W+4.5` 쪽으로 기울 수 있다.

이번 케이스의 중요한 차이는 pass 직전 Top Moves 분석이다.

```text
1. Black J5 WR=100% lead=-6.9
2. Black G7 WR=100% lead=-6.8
3. Black J7 WR=100% lead=-6.9
4. Black G2 WR=100% lead=-7.0
8. Black pass WR=11% lead=+4.1
```

즉, 엔진은 pass 직전에는 흑이 정리/계속 진행하면 우세하다고 보고 있지만, 흑이 바로 pass하면 그 pass 자체를 큰 손해 수로 평가한다.

## 엔진 재현

원본 로그의 `[Moves]`를 GTP `play` 명령으로 재생한 뒤 `Black pass`를 추가하면 KataGo 1.16.4는 다음처럼 응답했다.

```text
final_status_list dead: empty
final_score: W+4.5
kata-raw-nn 0: whiteLead 3.046
```

따라서 이 케이스는 단순히 `final_status_list dead`만 신뢰해서는 해결되지 않는다. pass 이후 상태에서는 엔진도 이미 백 우세 최종 상태에 가까운 값으로 평가하기 때문이다.

## 수정 정책

- pass 직전 백그라운드 Top Moves 후보를 계가 선택 정책에 전달한다.
- 로컬 최종 계가와 pass 직전 최선 continuation의 White lead가 10집 이상 충돌하면, 최종 표시 점수는 `B+6.9?`처럼 `?`가 붙은 불확정 pre-pass Top Moves 추정으로 표시한다.
- `Copy Log`의 endgame detail에 `prePassTopMoves`를 남겨 어떤 후보 분석 때문에 최종 표시가 바뀌었는지 추적한다.
- 동시에 `DeadStoneDetector` fallback은 유지한다. 이번 fixture에서는 fallback 사석 정리를 적용하면 local area도 `B+6.5`가 된다.

## 회귀 테스트

관련 테스트:

- `shared/src/commonTest/kotlin/com/worksoc/goaicoach/shared/EndgameRegressionTest.kt`
- `shared/src/commonTest/kotlin/com/worksoc/goaicoach/shared/EndgameScoreSelectorTest.kt`

고정한 불변식:

- fallback 없이 raw local area를 계산하면 `W+4.5`가 나온다.
- `DeadStoneDetector` fallback으로 정리하면 `B+6.5`가 나온다.
- fallback이 없는 경우에도 pre-pass Top Moves 최선 후보가 raw local final과 크게 충돌하면 `EndgameScoreSelector`가 `UnsettledPrePassTopMoveEstimate`를 선택하고 `B+6.9?`를 표시한다.

## 추가 발견

KataGo 후보 손실 계산도 함께 수정했다. `kata-search_analyze` 후보별 `scoreLead`는 현재 착수자 관점이므로, `pointLoss`는 색상과 무관하게 최선 후보 `scoreLead - 후보 scoreLead`로 계산해야 한다. 기존 로직은 흑 차례에서 부호를 반대로 처리해 `Black pass`처럼 큰 손해 후보가 `loss=0.0`으로 표시될 수 있었다.
