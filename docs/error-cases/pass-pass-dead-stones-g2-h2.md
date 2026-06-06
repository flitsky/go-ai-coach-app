# 오류 케이스: 양패스 전 우하단 백 2점 사석 처리

작성일: 2026-06-06

## 원본 로그

- 사용자 제공 debug report: `/Users/ryan9kim/.codex/attachments/5e29c4ca-478c-4c66-a655-9c5d8da6f707/pasted-text.txt`
- 앱 상태: `HumanVsAi`, `KataGo`, `boardSize=9`, `ruleset=Chinese`
- 로그 시점: `gameEnded=false`, `consecutivePasses=false`, `nextPlayer=Black`
- 마지막 수: `White C6`

## 증상

우하단 `G2`, `H2`의 백 2점은 `G1` 한 점만 활로로 가진 사석이다.

- 흑이 `G1`에 두면 `G2`, `H2`가 즉시 따인다.
- 흑/백이 이 돌을 실제로 따내지 않고 pass로 종료하면, 로컬 area score가 두 백돌을 살아있는 돌로 세면서 종료 평가가 달라질 수 있다.

사용자 관점에서는 “명확한 사석을 따내고 종료한 판”과 “사석인 채로 두고 양패스 종료한 판”의 최종 평가는 같아야 한다.

## 엔진 재현

원본 로그의 `[Moves]`를 GTP `play` 명령으로 재생한 뒤 `Black pass`, `White pass`를 추가하면 KataGo 1.16.4의 `final_status_list dead`는 다음을 반환한다.

```text
G2 H2
```

따라서 이 케이스는 로그만으로 엔진 재현이 가능하다. 단, 실제 엔진 재현은 로컬 KataGo binary/model/config가 필요하므로 일반 unit test에는 포함하지 않고, 앱 코어 로직 회귀 테스트로 고정한다.

## 회귀 테스트

관련 테스트:

- `shared/src/commonTest/kotlin/com/worksoc/goaicoach/shared/EndgameRegressionTest.kt`

고정한 불변식:

- `DeadStoneDetector.capturableDeadStones()`가 `G2`, `H2`를 사석 후보로 감지해야 한다.
- `DeadStoneCleaner`로 `G2`, `H2`를 제거한 점수는 흑이 `G1`에 두어 실제로 두 점을 따낸 뒤의 점수와 같아야 한다.
- 제거 전 로컬 area score와 제거 후 score는 달라야 한다.

## 수정 정책

- 1순위: KataGo `final_status_list dead` 결과를 사용해 사석을 정리한다.
- fallback: 엔진 결과가 비거나 엔진 조건 변경으로 흔들리더라도, pass-pass 종료 상태에서 한 활로짜리 그룹이 상대 착수 한 번으로 즉시 따이는 경우 `DeadStoneDetector`가 로컬 사석 후보로 감지한다.
- `Copy Log`의 endgame detail에는 `locallyInferredDeadStones`를 남겨 엔진 dead list와 로컬 fallback의 차이를 추적한다.
