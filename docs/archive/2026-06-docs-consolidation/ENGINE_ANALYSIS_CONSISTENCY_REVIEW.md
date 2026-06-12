# 엔진 분석값 일관성 검토

작성일: 2026-06-08

## 한줄 결론

`pointLoss`는 내부적으로 0 이상 손실값으로 유지하고, 보드 위 Top Moves 숫자는 KaTrain 기본 UX처럼 `-pointLoss` 델타 점수로 표시한다. 후보 순위는 `pointLoss` 오름차순이 아니라 KataGo `order`를 우선한다.

## 왜 이 문서를 따로 두는가

Top Moves, 착수 리뷰, AI 레벨링, 복기 리포트는 모두 같은 엔진 후보 데이터를 기반으로 한다. 여기서 `order`, `scoreLead`, `pointLoss`, `policy`, `refine`의 의미가 흔들리면 상위 앱 로직이 모두 흔들린다.

따라서 엔진 특화 분석값은 사용자 매뉴얼과 별개로 이 문서에서 관리한다.

## KataGo 원본 스펙 기준

KataGo analysis engine 문서 기준:

- `moveInfos`는 KataGo가 고려한 후보수 목록이다.
- `scoreLead`는 `reportAnalysisWinratesAs` 설정 관점의 점수 리드다.
- 현재 `analysis_learning.cfg`는 `reportAnalysisWinratesAs = BLACK` 이므로 raw JSON `scoreLead > 0`은 흑 유리로 해석한다.
- `order`는 KataGo가 매긴 후보 순위다. `0`이 최선, `1`이 다음 순위다.
- 단, `order`는 `playSelectionValue` 기준이다. 이 값은 승률, 점수, 기타 유틸리티를 조합하므로 `scoreLead` 손실이 가장 작은 후보와 항상 같다고 가정하면 안 된다.

## KaTrain 기준

KaTrain 원본에서 확인한 핵심 로직:

```python
pointsLost = player_sign(next_player) * (root_score - candidate_scoreLead)
relativePointsLost = player_sign(next_player) * (top_score_lead - candidate_scoreLead)
```

KaTrain은 후보 색상에는 `pointsLost`를 쓴다. Top Moves 기본 숫자 표시는 `top_move_delta_score`이고, 실제 렌더링은 다음처럼 한다.

```python
format_loss(-move_dict["pointsLost"])
```

즉 KaTrain도 내부값은 손실값이지만, 보드 위 숫자는 사용자가 직관적으로 읽기 쉬운 델타 점수다.

예시:

| 내부 `pointsLost` | 보드 표시 | 의미 |
| ---: | ---: | --- |
| `0.0` | `0.0` | 기준 대비 손실 없음 |
| `0.3` | `-0.3` | 기준 대비 0.3집 손실 |
| `-1.5` | `+1.5` | raw 계산상 root보다 1.5집 좋음 |

Go AI Coach는 현재 `CandidateMove.pointLoss`를 0 이상으로 정규화하므로 마지막 `+1.5` 케이스는 외부로 노출하지 않는다. 이득까지 표현하려면 `pointDeltaFromRoot` 같은 별도 signed 필드를 추가해야 한다.

## Go AI Coach 현재 기준

### 내부 필드

- `CandidateMove.scoreLead`: 앱 내부 white-lead convention이다. 그래프, 점수 추정, 진단용으로 쓴다.
- `CandidateMove.pointLoss`: 현재 착수자 관점에서 root score 대비 잃는 집 수다. 항상 0 이상이어야 한다.
- `CandidateMove.engineOrder`: KataGo가 직접 준 `moveInfos.order`다. policy/refine/legal fallback 후보에는 없다.
- `CandidateMove.source`: 후보 출처다.
  - `EngineSearch`: KataGo normal search 후보
  - `PolicyRefine`: policy 후보를 별도 낮은 visits query로 보강한 후보
  - `PolicyOnly`: 점수 손실 없이 policy prior만 있는 후보
  - `LegalFallback`: 룰상 합법이지만 엔진 평가가 없는 후보

### UI 표기

- 보드 Top Moves 숫자: `-pointLoss`
- 후보 상세/debug text: `loss=0.3`
- 착수 후 평가 색상 dot: `pointLoss`
- score graph: `scoreLead`/winrate timeline

이렇게 나누면 사용자는 보드에서 직관적인 델타를 보고, 디버그/테스트는 양수 손실값으로 안정적으로 검증할 수 있다.

## order와 pointLoss가 어긋나는 이유

`order 0`이 항상 `pointLoss 0.0` 또는 최소 손실이라고 기대하면 안 된다.

가능한 원인:

1. KataGo `order`는 `playSelectionValue` 기반이고, score loss 단일 기준이 아니다.
2. 낮은 visits에서는 후보별 scoreLead 추정이 불안정할 수 있다.
3. root score와 후보 score는 서로 다른 search 통계이므로 작은 budget에서는 후보 score가 root보다 좋아 보이는 raw 음수 loss가 생길 수 있다.
4. policy refine 후보는 별도 query 결과이므로 normal `moveInfos.order`와 같은 order 체계에 넣으면 안 된다.

따라서 앱 정책:

- Top Moves 순위와 큰 강조점은 `engineOrder`를 우선한다.
- `pointLoss`는 숫자/색상 annotation이다.
- `pointLoss`만으로 후보 순서를 뒤집지 않는다.
- `PolicyRefine` 후보는 coverage 보강용이며, 원본 `engineOrder`가 있는 후보와 같은 의미의 order를 갖지 않는다.

## 이번 검토에서 발견한 구조 리스크와 조치

### 리스크 1: 병합 리스트 index를 engine order처럼 사용

기존 `MoveAnalysisSnapshot`은 엔진 후보 리스트의 병합 index를 `engineOrder`로 보관했다. normal search 후보만 있을 때는 우연히 맞지만, refine/policy 후보가 섞이면 원본 KataGo order와 앱 병합 순서가 섞인다.

조치:

- `CandidateMove.engineOrder`를 추가해 원본 `moveInfos.order`를 구조적으로 보존한다.
- snapshot은 `engineOrder`가 있으면 그것을 display order로 쓰고, 없으면 병합 index를 fallback으로만 쓴다.
- field 이름도 snapshot 내부에서는 `displayOrder`로 다룬다.

### 리스크 2: 보드 숫자를 내부 loss 양수로 표시

직전 정리에서 내부 invariant만 보고 보드 숫자도 양수 `loss`로 표시했다. KaTrain 기본 UX와 사용자의 직관에는 맞지 않았다.

조치:

- 내부 `pointLoss`는 유지한다.
- 보드 숫자만 `topMoveDeltaScoreLabel = -pointLoss`로 표시한다.
- 후보 상세 텍스트는 계속 `loss=0.3`으로 둔다.

## 앞으로의 원칙

1. 엔진 raw 값, 앱 정규화 값, 사용자 표시값을 한 필드에 섞지 않는다.
2. `order`와 `pointLoss`는 서로 다른 축이다. 한쪽으로 다른 쪽을 임의 보정하지 않는다.
3. refine/policy/legal fallback 후보는 출처를 명시하고 normal search 후보와 같은 신뢰도로 취급하지 않는다.
4. 후보 병합 로직을 바꿀 때는 debug report에 source별 후보 수와 order 보존 상태를 남긴다.
5. 나중에 `+1.5 이득` 표현이 필요해지면 `pointLoss`를 음수화하지 말고 별도 signed delta 필드를 추가한다.
