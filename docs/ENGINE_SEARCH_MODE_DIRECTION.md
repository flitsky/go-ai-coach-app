# 엔진 탐색 모드 방향성

작성일: 2026-06-07
갱신일: 2026-06-08

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

앱에는 최소 2가지 성격의 엔진 탐색 방법을 제공한다.

1. `Fast Play`
2. `Study Analysis`

2026-06-08 1차 구현에서는 이를 사용자 설정으로 바로 다루기 쉽도록 3단계 preset으로 구체화했다.

1. `Lite`: 느린 폰/에뮬레이터용 빠른 대국 기본값
2. `Balanced`: 속도와 힌트 품질 절충
3. `Deep`: 학습/복기용 정밀 분석

2026-06-08 후속 구현에서는 이 raw preset 버튼을 사용자 UI에서 제거하고, 플레이 레벨이 내부 preset과 엔진 예산을 자동 선택하도록 변경했다.

1. `빠른 초급`: B16 / 250ms, 내부 `Lite`
2. `초급`: B32 / 350ms, 내부 `Learning`
3. `중급`: Casual 64 / 500ms, 내부 `Balanced`
4. `고급`: Intermediate 160 / 1000ms, 내부 `Balanced`

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

현재 구현:

- `AnalysisPreset.Lite`
- 후보 목표 상한: 8개
- Top Moves difficulty 승급 없음
- JSON `includePolicy=false`
- policy refine 0개
- 후보수에 따른 최소 visits 상향 없음
- 최소 분석 시간 상향 없음
- 기본값으로 사용

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

현재 구현:

- `AnalysisPreset.Deep`
- 후보 목표 상한: 81개
- Top Moves difficulty를 한 단계 승급
- JSON `includePolicy=true`
- policy refine 최대 12개
- 후보당 최소 20 visits, 최소 2000ms 상향
- 사용자가 명시적으로 선택할 때 사용

## 중간 모드: Balanced

느린 기기에서도 어느 정도 후보 다양성을 보고 싶을 때 쓰는 절충 모드다.

현재 구현:

- `AnalysisPreset.Balanced`
- 후보 목표 상한: 20개
- Top Moves difficulty를 한 단계 승급
- JSON `includePolicy=true`
- policy refine 최대 4개
- 후보당 최소 4 visits, 최소 800ms 상향

## 사용자 설정 UX 방향

처음에는 복잡한 raw 설정을 노출하지 않는다.

현재 UI:

- `Engine`
  - `빠른 초급`
  - `초급`
  - `중급`
  - `고급`
  - 각 그룹별 단계 `- / +`

`Lite`, `Learning`, `Balanced`, `Deep`은 사용자 버튼이 아니라 내부 분석 preset이다. raw preset을 직접 노출하면 대국 난이도, 후보 coverage, 학습용 분석 품질이 한 화면에서 섞이므로 현재는 숨긴다.

추가 고도화 후:

- `Auto`
  - 기기 성능 측정 후 기본 전략 선택
  - 첫 실행 또는 설정 화면에서 짧은 벤치마크 수행
- `Custom`
  - max candidates
  - max time
  - refine count
  - background analysis on/off

기본값은 `빠른 초급 1단계`가 적절하다. 앱의 1차 목적은 사용자가 실제로 대국을 이어가는 것이고, 정밀 분석은 명시적 학습 의도가 있을 때 켜는 것이 맞다.

## 내부 설계 방향

현재 모델:

```kotlin
enum class AnalysisPreset {
    Lite,
    Learning,
    Balanced,
    Deep,
}
```

사용자 조작 모델은 별도의 `PlayLevelSetting`으로 둔다.

```kotlin
data class PlayLevelSetting(
    val group: PlayLevelGroup,
    val level: Int,
)
```

`PlayLevelSetting`은 다음 값을 함께 결정한다.

- AI 응수용 `EngineProfile`
- Top Moves/pre-move analysis용 내부 `AnalysisPreset`
- AI가 후보 중 어떤 상대 순위 구간에서 랜덤 선택할지 정하는 `MoveSelectionPolicy`

`AnalysisLimit`에는 단순 visits/time/candidates 외에도 실제 adapter가 지켜야 할 budget 필드를 둔다.

```kotlin
data class AnalysisLimit(
    val visits: Int,
    val timeMillis: Long?,
    val candidateCount: Int,
    val includePolicy: Boolean,
    val refinePolicyMoves: Int,
    val minVisitsPerCandidate: Int,
    val minTimeMillis: Long?,
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
- 느린 폰 기본값은 `Lite`로 둔다.
- `Deep`은 켰을 때만 무거운 background refine를 수행한다.
- 분석 결과가 늦게 도착해도 현재 수순과 맞지 않으면 폐기한다.
- 착수 평가 데이터가 없으면 나쁜 수로 단정하지 않고 `unknown`으로 둔다.
- 나중에 remote/server engine을 붙여도 같은 `EngineSearchMode` 개념을 유지한다.

## 분석 캐시

2026-06-08 구현에서 메모리 LRU cache를 도입했다.

cache key는 다음 정보를 포함한다.

- board size
- ruleset
- next player
- captures
- ko state
- stone placement
- move history
- analysis preset
- analysis limit
- deep/manual 여부

따라서 한수 무르기로 최근 국면에 돌아오거나, 같은 초기 국면을 다시 분석할 때 엔진을 재호출하지 않고 cache를 사용할 수 있다. 현재는 앱 프로세스 생명주기 안의 메모리 cache이며, 나중에 SGF/복기 기반 persistent cache로 확장할 수 있다.

## 다음 작업 제안

1. 폰/에뮬레이터에서 `빠른 초급`, `초급`, `중급`, `고급`의 첫 턴 대기 시간과 착수 후 응답 시간을 측정한다.
2. `빠른 초급`에서도 초기 자동 분석이 너무 느리면 시작 직후 자동 Top Moves 분석을 끄고, 첫 `Top Moves` 버튼 클릭 때만 수행하는 옵션을 추가한다.
3. 분석 job cancel/discard 정책을 강화해 새 착수 이후 늦게 도착한 결과가 화면을 덮지 않도록 한다.
4. 측정값을 기준으로 기기별 자동 fallback 또는 `Auto` 레벨 도입 여부를 결정한다.
