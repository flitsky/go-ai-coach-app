# 착점 분석 데이터 모델 결정

작성일: 2026-06-07
최근 갱신: 2026-06-10

## 목적

학습용 대국에서는 사용자가 실제로 둔 착점이 최상위 후보가 아니더라도, 해당 착점에 대한 평가 상태를 안정적으로 추적해야 한다.

따라서 UI가 엔진 후보 리스트를 직접 소비하는 구조에서 벗어나, 분석 snapshot을 먼저 만들고, 화면은 snapshot에서 필요한 표시 레이어만 선택해 그린다.

2026-06-10 현재 모바일 기본 정책은 성능을 우선한다. 자동 pre-move 분석은 중지했고, 사용자가 `Top Moves`를 누른 경우에만 best-1 경량 분석을 수행한다. 전체 합법 착점 coverage, policy 후보, refine sweep은 향후 broad study analysis로 분리한다.

## 현재 구조

- `EngineAdapter.analyze()`는 엔진이 실제로 반환한 후보 목록을 `AnalysisResult.candidates`로 제공한다.
- `CandidateMove`는 원본 KataGo `moveInfos.order`가 있을 경우 `engineOrder`로 보존하고, 후보 출처를 `source`로 구분한다.
- 앱은 현재 `GameState`와 후보 목록을 합쳐 `MoveAnalysisSnapshot`을 만든다.
- `MoveAnalysisSnapshot`은 구조적으로 현재 착수자의 합법 착점 coverage를 보존할 수 있다.
- 각 착점은 다음 coverage 중 하나를 가진다.
  - `Scored`: 엔진이 `pointLoss`까지 준 착점
  - `PolicyOnly`: 엔진 policy prior만 있는 착점
  - `LegalOnly`: 룰상 합법이지만 아직 엔진 점수 평가가 없는 착점
- 현재 모바일 기본 보드 시각화는 수동 `Top Moves`로 얻은 `Scored` best-1 착점만 그린다.
- 착수 리뷰는 snapshot에서 실제 착수 좌표를 조회한다. 자동 pre-move snapshot을 만들지 않으므로, 같은 국면의 분석이 없거나 착점에 `pointLoss`가 없으면 실수로 단정하지 않고 `unknown`으로 남긴다.

## 현재 모바일 기본 흐름

현재 대국 중 경량 힌트/AI 응수는 broad JSON analysis를 사용하지 않는다.

1. 자동 pre-move 분석은 실행하지 않는다.
2. 사용자가 `Top Moves`를 누르면 현재 Player Setup의 visits/time으로 `candidateCount=1` 분석을 요청한다.
3. `includePolicy=false`, `refinePolicyMoves=0`, `minVisitsPerCandidate=0`, `minTimeMillis=null`을 사용한다.
4. KataGo process adapter는 이 경량 요청을 JSON analysis process가 아니라 GTP `kata-search_analyze` fast path로 처리한다.
5. 응답 후보는 `Scored` best-1 후보가 되고, 보드에는 이 후보만 표시한다.
6. 같은 국면의 cache가 있으면 엔진을 다시 호출하지 않는다.

## Broad study analysis 보류 흐름

KaTrain식 학습/복기 모드에서는 다음 흐름을 다시 사용할 수 있다.

1. normal query로 `rootInfo`, `moveInfos`, `policy`를 받는다.
2. `moveInfos`는 `Scored` 후보가 되며 `source=EngineSearch`, `engineOrder=<KataGo order>`를 가진다.
3. `policy` 배열은 현재 shared rules projection의 합법 착점과 교차 확인한 뒤 `PolicyOnly` 후보로 보존한다.
4. 아직 `pointLoss`가 없는 상위 policy 후보 일부는 `playedMoves + 후보수` 형태의 refine query로 낮은 visits 재분석을 수행한다.
5. refine query의 `rootInfo`를 부모 root score와 비교해 해당 후보의 `pointLoss`를 계산한다.

이 흐름은 현재 모바일 기본 경로에서는 비활성이다. 다시 켤 경우에도 자동 대국 경로와 분리된 수동 study mode로 두는 것이 안전하다.

## 왜 이렇게 분리하는가

엔진 분석 품질과 UI 표현은 별개로 진화해야 한다.

- 엔진이 당장 모든 합법 착점의 점수를 주지 않아도, 앱은 착점별 평가 상태를 구조적으로 추적할 수 있다.
- 나중에 KaTrain식 sweep/refine 분석을 추가하면 `LegalOnly` 또는 `PolicyOnly` 착점을 `Scored`로 채우면 된다.
- 보드 UI는 전체 spot, Top Moves only, 실수 후보만 강조, 복기용 heatmap 등으로 바뀔 수 있지만, core 분석 snapshot은 그대로 유지할 수 있다.

## 현재 한계

- 현재 모바일 기본은 best-1만 채우므로 여러 후보 색상, 착수별 자동 평가점, 전체 heatmap은 제한된다.
- JSON normal analysis와 budgeted refine을 다시 사용하더라도 모든 합법 착점의 `pointLoss`가 바로 채워지지는 않는다.
- `PolicyRefine` 후보는 별도 query 결과이므로 KataGo normal `moveInfos.order`와 같은 order를 갖지 않는다. UI 표시 순서에서는 원본 `engineOrder`가 있는 후보를 우선하고, 없는 후보는 앱 병합 순서를 fallback으로만 사용한다.
- 모든 착점을 색상 평가하려면 refine budget을 높이거나 별도 수동 full sweep 모드를 추가해야 한다.
- 모바일에서는 모든 착점을 매 턴 깊게 분석하면 응답성이 나빠질 수 있으므로, 빠른 대국 경로와 수동 study analysis 예산을 분리해야 한다.

## 다음 단계

1. `MoveAnalysisSnapshot` coverage를 debug report와 candidate text에 계속 노출한다.
2. KaTrain의 `sweep`처럼 `LegalOnly` 착점만 낮은 visits로 훑는 분석 경로를 검토한다.
3. sweep 결과를 snapshot에 merge하는 도메인 API를 추가한다.
4. UI는 `MoveAnalysisDisplayPolicy`로 표시 정책만 바꿔 실험한다.
