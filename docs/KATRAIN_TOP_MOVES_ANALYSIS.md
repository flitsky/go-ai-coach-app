# KaTrain Top Moves 분석 메모

작성일: 2026-06-06

## 목적

Top Moves, 착수 평가 색상, ownership overlay를 KaTrain처럼 고도화하기 위해 KaTrain의 실제 데이터 흐름을 확인하고, Go AI Coach에 적용할 순서를 정리한다.

## 확인한 KaTrain 코드

- `/tmp/katrain-src/katrain/core/engine.py`
  - KataGo analysis JSON 프로토콜로 `rootInfo`, `moveInfos`, `ownership`, `policy`를 받는다.
  - 요청에는 `maxVisits`, `includeOwnership`, `includeMovesOwnership`, `includePolicy`, `moves`, `initialStones`, `rules`, `komi`가 포함된다.
- `/tmp/katrain-src/katrain/core/game_node.py`
  - `set_analysis()`가 `moveInfos`, `rootInfo`, `ownership`, `policy`를 노드 분석 상태로 저장한다.
  - `candidate_moves`가 후보별 `pointsLost`, `relativePointsLost`, `winrateLost`를 계산한다.
- `/tmp/katrain-src/katrain/gui/badukpan.py`
  - 후보 spot 색상은 `pointsLost`를 `eval_color()`에 넣어 결정한다.
  - Top move label은 기본적으로 `top_move_delta_score`와 `top_move_visits`를 표시한다.
  - ownership은 board-size 텍스처를 만든 뒤 전체 보드에 부드럽게 블렌딩한다.
- `/tmp/katrain-src/katrain/core/game.py`
  - `extra`, `equalize`, `sweep`, `alternative` 분석 모드가 있다.
  - `sweep`은 모든 합법 후보를 빠른 visits로 한 번씩 refine 분석한다.
  - `equalize`는 이미 후보로 잡힌 수들의 visits가 비슷해지도록 추가 refine 분석한다.

## 핵심 결론

KaTrain의 초록/노랑/빨강 spot은 엔진이 직접 분류해서 주는 값이 아니다. 엔진은 `rootInfo.scoreLead`와 후보별 `scoreLead`, `winrate`, `visits`, `prior`를 주고, KaTrain이 현재 root score 대비 후보의 `pointsLost`를 계산해 색상으로 바꾼다. 별도로 `relativePointsLost`는 최선 후보 대비 손실값으로 계산된다.

> [!IMPORTANT]
> Go AI Coach의 `pointLoss`는 0 이상 손실값으로 유지한다. raw 계산상 음수 손실이 생기면 `0.0`으로 정규화하며, signed 이득/손실 표시는 별도 필드가 생기기 전까지 `pointLoss`로 처리하지 않는다. 짧은 기준 문서는 `docs/TOP_MOVES_VALUE_GUIDE.md`를 우선 참조한다.

KaTrain 기준 계산식은 다음과 같다.

```text
pointsLost = player_sign(next_player) * (root_score - candidate_scoreLead)
relativePointsLost = player_sign(next_player) * (top_move_scoreLead - candidate_scoreLead)
winrateLost = player_sign(next_player) * (root_winrate - candidate_winrate)
```

여기서 `player_sign`은 흑이면 `+1`, 백이면 `-1`이다. 즉 같은 `scoreLead`라도 현재 착수자가 누구인지에 따라 손실 방향이 바뀐다.

KaTrain 기본 `eval_thresholds`는 다음 순서다.

```text
[12, 6, 3, 1.5, 0.5, 0]
```

KaTrain의 `evaluation_class()`는 큰 손실에서 작은 손실 방향으로 threshold를 통과시킨 뒤 색상 index를 고른다. 기본 색상 테마 기준으로 해석하면 다음과 같다.

| `pointsLost` 구간 | KaTrain 색상 계열 | 의미 |
| ---: | --- | --- |
| `0.5` 이하 | 진한 초록 | 최선 또는 거의 최선 |
| `0.5` 초과, `1.5` 이하 | 연한 초록 | 좋은 수 |
| `1.5` 초과, `3` 이하 | 노랑 | 부정확 |
| `3` 초과, `6` 이하 | 주황 | 실수 |
| `6` 초과, `12` 이하 | 빨강 | 큰 실수 |
| `12` 초과 | 보라/자주 계열 | 매우 큰 실수 |

현재 Go AI Coach의 색상 단계는 모바일 화면에 맞춰 `6` 초과를 모두 빨강으로 단순화해 두었다. 레벨링 데이터 모델에서는 KaTrain처럼 `12` 초과를 별도 `SevereBlunder` bucket으로 보관하고, UI에서는 일단 빨강 계열로 표시하는 방식이 좋다.

따라서 우리 앱도 다음 구조가 맞다.

- `EngineAdapter`는 후보별 정량값만 DTO로 반환한다.
- 앱은 엔진 후보 DTO와 현재 `GameState`를 합쳐 `MoveAnalysisSnapshot`을 만든다.
- UI는 snapshot 중 `CandidateMove.pointLoss`가 있는 후보만 보드에 표시한다.
- 색상은 앱 공통 규칙으로 계산한다.
- KataGo JSON `scoreLead`는 흑 기준 값으로 취급한다. KaTrain은 `player_sign(B)=+1`, `player_sign(W)=-1`를 곱해 `pointsLost = player_sign(next_player) * (root_scoreLead - candidate_scoreLead)`를 계산한다.
- Go AI Coach 내부 `CandidateMove.scoreLead`는 기존 score graph convention에 맞춰 white-lead로 보관하지만, `pointLoss`는 위 KaTrain 계산과 같은 결과가 되도록 현재 착수자 관점으로 계산한다.
- 학습용 Top Moves의 순위와 큰 강조점은 KataGo `order`를 우선한다. `pointLoss`는 색상/숫자 annotation으로 사용한다. 저예산 분석에서는 root score 기반 손실값이 order와 어긋날 수 있으므로, 앱이 `pointLoss`만으로 후보 순서를 뒤집지 않는다.
- 점수 없는 policy/legal/legal-only 후보는 snapshot과 로그에는 남기지만, 보드 spot으로는 그리지 않는다.
- 표시 색상은 KaTrain식 절대 `pointsLost` threshold를 따른다. AI 레벨링 선택 정책은 이 절대 bucket에 상대 순위/분위수 bucket을 추가로 조합한다.

## 현재 반영 상태

- `Top Moves`는 점수 손실이 있는 후보만 보드에 그린다.
- 점수 손실이 없는 fallback 후보의 회색 spot은 제거했다.
- 기본 Top Moves 데이터 모델은 모든 합법 착점을 snapshot에 보존한다. 9x9 POC에서는 전체 합법 착점 수를 후보 목표로 요청하되, 실제 `pointLoss`가 채워지는 범위는 엔진 응답 품질에 따른다.
- KataGo process adapter는 `analysis_learning.cfg`가 있으면 KataGo JSON analysis protocol을 우선 사용한다.
- JSON analysis query는 현재 수순 전체, `maxVisits`, `overrideSettings.maxTime`, `includePolicy=true`를 전달하고, `moveInfos`를 `CandidateMove`로 변환한다.
- JSON 후보 spot의 `pointLoss`는 KaTrain의 `pointsLost`와 맞춰 `rootInfo.scoreLead` 대비 손실로 계산한다. `rootInfo`가 없는 예외 응답에서는 기존처럼 order 0 후보 대비 손실로 fallback한다.
- JSON `policy` 배열은 합법 착점으로 필터링해 `PolicyOnly` 후보로 snapshot에 보존한다.
- JSON normal query에서 점수가 없는 상위 policy 후보 일부는 KaTrain의 `refine_move` 방식처럼 `playedMoves + 후보수` query를 추가 실행해 `Scored` 후보로 보강한다.
- JSON analysis process는 대국용 GTP process와 별도로 뜨며 `numAnalysisThreads=1`, `numSearchThreads=4`를 사용한다.
- JSON analysis config가 없거나 실패하면 기존 GTP fallback으로 돌아간다. 이때는 최소 `후보수 * 20 visits`, `2000ms`를 적용하고, `kata-search_analyze <color> <centiseconds>` 형태로 time limit도 명시한다.
- 자동 cache의 scored 후보가 5개 미만이면 사용자가 `Top Moves`를 누를 때 `Full Analysis` 수준의 deep analysis를 1회 실행한다.
- 착수 후 돌 중앙의 평가 dot도 같은 `pointLoss` 분류를 사용한다.
- 착수 리뷰는 전체 합법 착점 snapshot에서 착수 좌표를 조회한다. 합법이지만 아직 점수 손실이 없는 착점은 `unknown`으로 처리한다.
- 색상 단계는 KaTrain 기본 임계값을 모바일 UX에 맞춰 5단계로 단순화했다.
  - `0.5`집 이하: 진한 초록
  - `1.5`집 이하: 연한 초록
  - `3.0`집 이하: 노랑
  - `6.0`집 이하: 주황
  - `12.0`집 이하: 빨강
  - 그 이상: 현재 UI는 빨강 계열로 표시하되, 내부 데이터 bucket은 `SevereBlunder`로 분리 가능
- ownership은 `Eval` 결과가 있으면 별도 메뉴 없이 보드에 표시한다.
- ownership 렌더링은 기존 사각형 heatmap 대신 교차점 주변 radial gradient로 표현한다.

## 현재 구조의 한계

현재 Android local process adapter는 Top Moves에 대해 JSON analysis protocol을 우선 사용하고, 실패 시 GTP 계열 `kata-search_analyze`와 `kata-raw-nn` 파싱으로 fallback한다.

- JSON normal analysis는 GTP보다 많은 `moveInfos`를 안정적으로 준다.
- 다만 normal analysis는 상위 후보 중심이다. 점수 손실이 큰 노랑/빨강 후보는 엔진이 해당 착점을 `moveInfos`에 포함해줄 때만 표시된다.
- budgeted refine으로 일부 policy 후보를 추가 scoring하지만, 모든 합법 착점에 대해 `pointLoss`를 더 촘촘히 얻으려면 별도 full sweep 분석이 필요하다.
- `kata-raw-nn` policy는 prior 정보에는 좋지만, 후보별 실전 점수 손실을 대신할 수 없으므로 fallback 로그 전용으로 유지한다.
- ownership, policy, rootInfo, moveInfos를 한 번에 일관되게 관리하려면 JSON analysis 결과를 앱 내부 분석 cache 모델로 더 확장할 필요가 있다.
- 에뮬레이터 CPU 검증에서는 GTP 자동 2초 분석뿐 아니라 수동 `Full Analysis 5초`에서도 빈 9x9 scored 후보가 1개에 머물 수 있었다. JSON analysis normal query가 이 병목의 1차 해법이고, sweep/equalize는 다음 고도화 단계다.

## 권장 적용 순서

1. 현재 GTP adapter 유지
   - 빠른 개발/실기기 검증을 유지한다.
   - `Top Moves`는 score가 있는 후보만 표시하고 fallback spot은 숨긴다.

2. Top Moves scored 후보 품질 보강
   - 9x9 POC의 기본 Top Moves는 전체 합법 착점 snapshot을 목표로 한다.
   - 평소 AI 난이도와 분석 강도를 분리한다.
   - 사용자가 직접 `Top Moves`를 요청했을 때 cache 품질이 낮으면 1회 deep analysis로 보강한다.
   - 모든 합법점 전수 분석은 기본 자동 UX로 두지 않는다.

3. KataGo analysis JSON adapter
   - 1차 구현 완료: `EngineAdapter.analyze()` 내부에서 JSON analysis process를 우선 사용한다.
   - 앱 UI와 shared 도메인 모델은 바꾸지 않고 adapter 내부에서 `CandidateMove`로 변환한다.
   - 아직 `rootInfo`, `ownership`, `policy`, partial result를 앱 cache 모델로 모두 승격하지는 않았다.

4. Sweep/Equalize 분석 도입 검토
   - 1차 budgeted refine 구현 완료: 상위 policy 후보 일부를 낮은 visits로 추가 분석한다.
   - KaTrain처럼 모든 합법 후보를 빠르게 훑는 full sweep 분석을 추가하면 더 많은 색상 spot을 만들 수 있다.
   - 이미 후보가 된 수들의 visits를 맞추는 equalize 분석은 상위 후보 비교 품질을 높인다.
   - 모바일에서는 전체 합법점 sweep을 기본 자동 실행하기보다 수동 고급 분석으로 두는 편이 안전하다.
   - Android CPU에서 GTP search가 매우 느린 점을 고려해, JSON analysis protocol과 후보별 refine 분석을 별도 adapter 경로로 실험해야 한다.

5. KaTrain식 색상 bucket 도메인화
   - UI 함수 안에 threshold를 직접 두지 않고, shared/domain에 `MoveQualityBucket`을 둔다.
   - bucket은 `Excellent`, `Good`, `Inaccuracy`, `Mistake`, `Blunder`, `SevereBlunder`, `Unknown`으로 둔다.
   - Top Moves spot, 착수 후 돌 중앙 dot, move review 문구, AI 레벨링 정책이 같은 bucket 계산기를 사용하게 한다.
   - 단, AI 레벨링은 이 bucket만 쓰지 않고 상대 순위 bucket도 함께 사용한다.

## 의사결정

당장 앱의 기본 대국 UX에서는 JSON analysis 기반 Top Moves와 부드러운 Eval overlay를 유지한다. KaTrain식 전체 후보 색상 품질은 JSON normal analysis 이후 sweep/equalize/refine 분석을 별도 작업으로 확장한다.

이 결정은 도메인 분리 원칙과 맞다. UI는 계속 `CandidateMove`, `ScoreEstimate`, `OwnershipEstimate`만 사용하고, GTP/process/JNI/remote/JSON analysis 차이는 `EngineAdapter` 구현 내부로 숨긴다.
