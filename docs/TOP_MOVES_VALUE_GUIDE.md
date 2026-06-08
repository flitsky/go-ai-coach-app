# Top Moves 값 표기 가이드

작성일: 2026-06-08

## 한줄 요약

`pointLoss`는 엔진 원본값이 아니라 앱이 계산한 0 이상 손실값이며, 후보 순위는 KataGo `order`, 후보 숫자/색상은 `pointLoss` annotation으로만 해석한다.

## 중요 가이드

> [!IMPORTANT]
> `pointLoss`에 음수 이득 의미를 넣지 않는다. 화면에 `+1.5 이득` 같은 signed 값을 보여주고 싶다면 `pointDeltaFromRoot` 같은 별도 필드를 추가한다.

KataGo analysis JSON은 `loss`를 직접 주지 않는다. KataGo가 주는 핵심 값은 다음이다.

- `moveInfos.order`: 엔진이 제시한 후보 순서
- `rootInfo.scoreLead`: 현재 root 국면 점수 리드
- `moveInfos[].scoreLead`: 해당 후보를 둔 뒤의 점수 리드
- `winrate`, `visits`, `prior`: 보조 분석값

Go AI Coach는 KaTrain과 같은 방식으로 `rootInfo.scoreLead`와 후보 `scoreLead`를 비교해 `pointLoss`를 계산한다.

```text
rawLoss = 현재 착수자 관점에서 root score 대비 후보 score가 잃는 집 수
pointLoss = max(rawLoss, 0.0)
```

따라서 raw 계산 중에는 후보가 root보다 더 좋게 평가되어 음수가 생길 수 있지만, 앱 외부로 노출되는 `CandidateMove.pointLoss`는 항상 0 이상이어야 한다.

## UI 표기 규칙

- 보드 위 후보 숫자는 `loss`를 양수로 표시한다.
  - `0.0`: 손실 없음
  - `0.3`: 현재 root 기준 0.3집 손실
- 후보 상세 텍스트도 `loss=0.3`처럼 표시한다.
- 음수 `loss=-0.3`은 표시하지 않는다.
- `+0.3` 같은 이득 표시는 현재 `pointLoss` 모델에서 하지 않는다.

## 순위와 색상

- 후보 순위와 첫 번째 큰 강조점은 KataGo `moveInfos.order`를 따른다.
- `pointLoss`가 더 낮더라도 앱이 후보 순서를 임의로 뒤집지 않는다.
- 색상은 `pointLoss` 기반이다.
- 저예산 분석에서는 `order`와 `pointLoss`가 어긋날 수 있다. 이 경우도 순위는 `order`, 설명은 `loss`로 분리한다.

## 향후 확장 규칙

다른 엔진 또는 고도화 분석에서 signed 평가값이 필요하면 다음처럼 분리한다.

- `pointLoss`: 0 이상 손실값, 색상/리뷰/학습 피드백용
- `pointDeltaFromRoot`: 부호 있는 점수 변화량, `+`는 이득, `-`는 손실
- `scoreLead`: 그래프/계가/진단용 lead 값

같은 필드에 loss와 gain 의미를 섞지 않는다.
