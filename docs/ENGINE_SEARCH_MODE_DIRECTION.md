# 엔진 탐색 모드 방향성

작성일: 2026-06-07

## 배경

실제 Android 폰에서 최신 Top Moves/전체 합법 착점 snapshot/refine 흐름이 체감상 매우 느리게 동작했다.

현재 최신 버전은 학습 데이터 품질을 높이기 위해 사용자 턴마다 다음 데이터를 확보하려 한다.

- 현재 국면의 합법 착점 전체 목록
- KataGo JSON analysis `moveInfos`
- `policy` 기반 후보
- 상위 policy 후보 일부에 대한 추가 refine query
- 착수 후 평가 dot에 사용할 pre-move analysis cache

이 구조는 학습 UX에는 유리하지만, 느린 폰에서는 대국 리듬을 크게 해칠 수 있다. 따라서 엔진 구현을 갈라치기하기보다, 같은 `EngineAdapter` 경계 뒤에 서로 다른 탐색 전략을 둔다.

## 결정 방향

앱에는 최소 2가지 엔진 탐색 방법을 제공한다.

1. `Fast Play`
2. `Study Analysis`

사용자 관점에서는 "대국을 쾌적하게 둘 것인가"와 "분석 품질을 높일 것인가"를 선택하는 설정이다.

기술 관점에서는 UI가 엔진 세부 구현을 직접 알지 않도록 유지하고, `EngineAdapter` 내부 또는 그 바로 위의 analysis policy layer에서 전략을 선택한다.

## 모드 1: Fast Play

느린 폰에서도 끊기지 않는 대국을 최우선으로 하는 모드다.

초기 기본값 후보:

- 일반 폰/실기기 기본값으로 사용
- AI 응수는 기존 `EngineProfile`의 낮은 visits/time을 그대로 사용
- 사용자 턴마다 전체 합법 착점 refine를 수행하지 않음
- 자동 pre-move analysis는 가볍게 제한
- Top Moves는 상위 N개만 빠르게 표시
- policy-only/legal-only 후보는 내부 cache와 로그에만 남기고, 실시간 UI에는 최소한만 반영
- 착수 평가 dot은 "확실히 평가된 후보"에 한해 표시하고, 없으면 `unknown`으로 처리

구현 후보:

- 오늘 변경 전 수준에 가까운 경량 분석 경로를 별도 전략으로 보존
- JSON normal analysis 1회만 수행하거나, GTP fallback의 낮은 visits 분석만 수행
- refine query는 기본 off
- `maxTime`은 250ms~700ms 범위에서 시작
- 후보 목표는 전체 합법점이 아니라 5~10개 수준
- 사용자 입력을 막는 blocking 분석 금지

기대 효과:

- 느린 폰에서도 착수 후 다음 턴까지의 대기 시간이 짧다.
- Top Moves 색상 품질은 낮아질 수 있지만, 대국이 먼저 가능하다.
- 학습용 평가가 비어 있는 수는 실수로 단정하지 않는다.

## 모드 2: Study Analysis

현재 최신 버전의 방향을 유지하는 정밀 분석 모드다.

초기 기본값 후보:

- 사용자가 명시적으로 켰을 때 사용
- 충전 중, 고성능 기기, 복기/학습 세션에서 권장
- 전체 합법 착점 snapshot을 유지
- JSON `policy`를 포함해 모든 합법 착점을 coverage로 보존
- 상위 policy 후보 일부를 budgeted refine로 `Scored` 승격
- 향후 KaTrain식 sweep/equalize를 붙일 수 있는 확장 지점으로 사용

구현 후보:

- 현재 최신 흐름을 `Study Analysis` 전략으로 이동
- refine 개수, 후보당 visits, 전체 time budget을 옵션화
- 분석 중에도 보드 입력이 가능한 구조를 유지하되, 결과는 늦게 도착하면 cache 갱신
- 새 착수나 undo가 발생하면 이전 analysis job은 취소 또는 폐기

기대 효과:

- 초록/노랑/빨강 spot, 착수 평가, 후보 비교 품질을 높일 수 있다.
- 분석 결과가 늦어도 학습 화면에서는 받아들일 수 있다.
- 이후 server engine 또는 더 큰 모델과도 같은 모델을 재사용할 수 있다.

## 사용자 설정 UX 방향

처음에는 복잡한 raw 설정을 노출하지 않는다.

권장 UI:

- `Engine speed`
  - `Fast Play`
  - `Study Analysis`

추가 고도화 후:

- `Auto`
  - 기기 성능 측정 후 기본 전략 선택
  - 첫 실행 또는 설정 화면에서 짧은 벤치마크 수행
- `Custom`
  - max candidates
  - max time
  - refine count
  - background analysis on/off

기본값은 `Fast Play`가 적절하다. 앱의 1차 목적은 사용자가 실제로 대국을 이어가는 것이고, 정밀 분석은 명시적 학습 의도가 있을 때 켜는 것이 맞다.

## 내부 설계 방향

추상화 이름 후보:

- `EngineSearchMode`
- `AnalysisStrategy`
- `AnalysisBudget`

예상 모델:

```kotlin
enum class EngineSearchMode {
    FastPlay,
    StudyAnalysis,
}
```

또는:

```kotlin
data class AnalysisStrategy(
    val mode: EngineSearchMode,
    val autoAnalyzeOnTurn: Boolean,
    val candidateTarget: CandidateTarget,
    val maxTimeMillis: Int,
    val maxVisits: Int,
    val refinePolicyMoves: Int,
    val refineVisitsPerMove: Int,
)
```

UI와 보드는 계속 다음 도메인 모델만 사용한다.

- `CandidateMove`
- `MoveAnalysisSnapshot`
- `ScoreEstimate`
- `OwnershipEstimate`

KataGo JSON/GTP/refine/sweep 여부는 UI에 노출하지 않는다.

## 중요한 정책

- 대국 흐름을 막는 분석은 금지한다.
- 느린 폰 기본값은 `Fast Play`로 둔다.
- `Study Analysis`는 켰을 때만 무거운 background refine를 수행한다.
- 분석 결과가 늦게 도착해도 현재 수순과 맞지 않으면 폐기한다.
- 착수 평가 데이터가 없으면 나쁜 수로 단정하지 않고 `unknown`으로 둔다.
- 나중에 remote/server engine을 붙여도 같은 `EngineSearchMode` 개념을 유지한다.

## 다음 구현 시 작업 순서 제안

1. 현재 최신 분석 경로를 `StudyAnalysis`로 이름 붙여 캡슐화한다.
2. 오늘 변경 전과 유사한 경량 경로를 `FastPlay`로 복원한다.
3. 설정 메뉴에 `Engine speed: Fast Play | Study Analysis`를 추가한다.
4. 기본값을 `Fast Play`로 둔다.
5. 폰에서 각 모드의 첫 턴 대기 시간, 착수 후 응답 시간, Top Moves 표시 시간을 측정한다.
6. 측정값을 기준으로 `Auto` 모드 도입 여부를 결정한다.
