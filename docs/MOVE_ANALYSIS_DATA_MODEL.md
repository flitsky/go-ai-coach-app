# 착점 분석 데이터 모델 결정

작성일: 2026-06-07

## 목적

학습용 대국에서는 사용자가 실제로 둔 착점이 최상위 후보가 아니더라도, 해당 착점에 대한 평가 상태를 안정적으로 추적해야 한다.

따라서 UI가 엔진 후보 리스트를 직접 소비하는 구조에서 벗어나, 모든 합법 착점을 포함하는 분석 snapshot을 먼저 만들고, 화면은 snapshot에서 필요한 표시 레이어만 선택해 그린다.

## 현재 구조

- `EngineAdapter.analyze()`는 엔진이 실제로 반환한 후보 목록을 `AnalysisResult.candidates`로 제공한다.
- 앱은 현재 `GameState`와 후보 목록을 합쳐 `MoveAnalysisSnapshot`을 만든다.
- `MoveAnalysisSnapshot`은 현재 착수자의 모든 합법 착점을 보존한다.
- 각 착점은 다음 coverage 중 하나를 가진다.
  - `Scored`: 엔진이 `pointLoss`까지 준 착점
  - `PolicyOnly`: 엔진 policy prior만 있는 착점
  - `LegalOnly`: 룰상 합법이지만 아직 엔진 점수 평가가 없는 착점
- 현재 보드 시각화는 `Scored` 착점만 그린다.
- 착수 리뷰는 snapshot에서 실제 착수 좌표를 조회한다. 착점이 합법이지만 아직 `pointLoss`가 없으면 실수로 단정하지 않고 `unknown`으로 남긴다.

## 왜 이렇게 분리하는가

엔진 분석 품질과 UI 표현은 별개로 진화해야 한다.

- 엔진이 당장 모든 합법 착점의 점수를 주지 않아도, 앱은 모든 합법 착점의 평가 상태를 추적할 수 있다.
- 나중에 KaTrain식 sweep/refine 분석을 추가하면 `LegalOnly` 또는 `PolicyOnly` 착점을 `Scored`로 채우면 된다.
- 보드 UI는 전체 spot, Top Moves only, 실수 후보만 강조, 복기용 heatmap 등으로 바뀔 수 있지만, core 분석 snapshot은 그대로 유지할 수 있다.

## 현재 한계

- JSON normal analysis는 상위 후보 중심이므로 모든 합법 착점의 `pointLoss`가 바로 채워지지는 않는다.
- 모든 착점을 색상 평가하려면 sweep/refine 단계가 필요하다.
- 모바일에서는 모든 착점을 매 턴 깊게 분석하면 응답성이 나빠질 수 있으므로, 자동 분석과 수동 deep 분석의 예산을 분리해야 한다.

## 다음 단계

1. `MoveAnalysisSnapshot` coverage를 debug report와 candidate text에 계속 노출한다.
2. KaTrain의 `sweep`처럼 `LegalOnly` 착점만 낮은 visits로 훑는 분석 경로를 검토한다.
3. sweep 결과를 snapshot에 merge하는 도메인 API를 추가한다.
4. UI는 `MoveAnalysisDisplayPolicy`로 표시 정책만 바꿔 실험한다.
