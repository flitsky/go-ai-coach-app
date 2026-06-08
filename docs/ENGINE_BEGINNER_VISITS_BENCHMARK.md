# Beginner Visits 비교 평가

작성일: 2026-06-08

상태: 1차 실측 기반 검토 문서. 최종 제품 결정 전까지 benchmark 근거 자료로 재사용한다.

## 목적

초급 레벨링에서 `Beginner 16`을 그대로 유지할지, 또는 `32`/`64` visits를 기본값으로 올릴지 판단하기 위해 실제 KataGo analysis 결과를 비교한다.

검토 질문:

- `16`, `32`, `64` visits에서 탐색 속도는 얼마나 차이 나는가?
- 반환되는 `moveInfos` 후보 수는 얼마나 늘어나는가?
- yellow/orange/red bucket이 실제로 더 잘 확보되는가?
- 초급 AI 레벨링에 바로 `32` 또는 `64`를 쓰는 것이 합리적인가?

## 측정 환경

| 항목 | 값 |
| --- | --- |
| 머신 | MacBook 로컬 환경 |
| 엔진 | KataGo v1.16.4 |
| Backend | Metal |
| Model | `kata1-b18c384nbt-s9996604416-d4316597426.bin.gz` |
| Config | `analysis_learning.cfg` |
| Analysis threads | `numAnalysisThreads=1` |
| Search threads | `numSearchThreads=4` |
| Board | 9x9 |
| Rules | Japanese |
| Komi | 6.5 |

주의:

- 이 결과는 MacBook Metal 기준이다.
- Android 실기기에서는 latency가 더 커질 수 있으므로, 실기기 재측정이 필요하다.
- 그러나 후보 수 증가 경향과 bucket 확보 경향은 레벨링 설계의 1차 근거로 사용할 수 있다.

## 측정 방법

각 조합마다 KataGo analysis process를 새로 실행했다.

1. 더미 국면으로 warm-up query 1회 실행
2. 실제 측정 query 1회 실행
3. query elapsed time만 측정
4. `moveInfos` 후보 수, bucket 분포, 상위 후보를 기록

비교 예산:

| 이름 | Visits | Time cap | 의미 |
| --- | ---: | ---: | --- |
| B16 | 16 | 250ms | 현재 Beginner 기본값 |
| B32 | 32 | 350ms | Beginner 확장 후보 |
| B64 | 64 | 500ms | 현재 Casual visits와 동일한 후보 |

색상 bucket 기준:

| Bucket | `pointLoss` |
| --- | ---: |
| Excellent | `0.5` 이하 |
| Good | `0.5` 초과, `1.5` 이하 |
| Yellow | `1.5` 초과, `3.0` 이하 |
| Orange | `3.0` 초과, `6.0` 이하 |
| Red | `6.0` 초과, `12.0` 이하 |
| Severe | `12.0` 초과 |

## 테스트 국면

### P0: 빈 9x9

```text
Moves: none
Next: Black
```

### P1: 초반 8수

```text
1. B E5
2. W C5
3. B G6
4. W F3
5. B D4
6. W C7
7. B C4
8. W G4
Next: Black
```

### P2: 중반 20수

```text
1. B E5
2. W C5
3. B G6
4. W F3
5. B C6
6. W D4
7. B B6
8. W G4
9. B H5
10. W F6
11. B F7
12. W F5
13. B E7
14. W B5
15. B D5
16. W E4
17. B A5
18. W G7
19. B H7
20. W G8
Next: Black
```

## 요약 결과

| 국면 | Budget | Elapsed | moveInfos | Bucket 분포 |
| --- | --- | ---: | ---: | --- |
| P0 빈 보드 | B16 | 189.6ms | 5 | Excellent 5 |
| P0 빈 보드 | B32 | 361.5ms | 13 | Excellent 13 |
| P0 빈 보드 | B64 | 517.8ms | 25 | Excellent 21, Good 4 |
| P1 초반 8수 | B16 | 172.6ms | 2 | Excellent 2 |
| P1 초반 8수 | B32 | 381.5ms | 7 | Excellent 5, Good 1, Yellow 1 |
| P1 초반 8수 | B64 | 512.7ms | 6 | Excellent 5, Good 1 |
| P2 중반 20수 | B16 | 304.1ms | 2 | Excellent 1, Orange 1 |
| P2 중반 20수 | B32 | 360.3ms | 6 | Excellent 1, Good 1, Yellow 1, Orange 2, Red 1 |
| P2 중반 20수 | B64 | 520.2ms | 7 | Excellent 1, Good 2, Yellow 1, Orange 2, Red 1 |

## 상위 후보 리스트

### P0 빈 보드

| Budget | 후보 |
| --- | --- |
| B16 | E5 loss 0.034 Excellent, F5 loss 0.283 Excellent, D5 loss 0.283 Excellent, E4 loss 0.283 Excellent, E6 loss 0.283 Excellent |
| B32 | E5 loss 0.098 Excellent, F5 loss 0.362 Excellent, D5 loss 0.362 Excellent, E4 loss 0.362 Excellent, E6 loss 0.362 Excellent, F6 loss 0.133 Excellent, F4 loss 0.133 Excellent, D6 loss 0.133 Excellent |
| B64 | E5 loss 0.026 Excellent, F5 loss 0.218 Excellent, D5 loss 0.218 Excellent, E4 loss 0.218 Excellent, E6 loss 0.218 Excellent, F6 loss 0.125 Excellent, F4 loss 0.125 Excellent, D6 loss 0.125 Excellent |

해석:

- 빈 보드는 후보가 늘어도 대부분 excellent/green 계열이다.
- 이 국면만으로는 yellow/orange/red 확보 여부를 판단하기 어렵다.
- B64는 후보 수가 크게 늘지만, 색상 다양성은 제한적이다.

### P1 초반 8수

| Budget | 후보 |
| --- | --- |
| B16 | D8 loss 0.000 Excellent, E2 loss 0.054 Excellent |
| B32 | D8 loss 0.000 Excellent, E2 loss 0.215 Excellent, E7 loss 0.000 Excellent, E8 loss 0.249 Excellent, D7 loss 0.353 Excellent, H5 loss 0.695 Good, H3 loss 2.352 Yellow |
| B64 | D8 loss 0.000 Excellent, E2 loss 0.125 Excellent, E7 loss 0.000 Excellent, E8 loss 0.000 Excellent, D7 loss 0.273 Excellent, H5 loss 0.661 Good |

해석:

- B16은 후보가 2개뿐이라 레벨링 선택지가 부족하다.
- B32는 7개 후보를 반환했고 yellow 후보까지 확보했다.
- B64가 항상 B32보다 색상 다양성이 좋은 것은 아니다. 방문 수가 늘어도 엔진이 좋은 후보 쪽으로 탐색을 집중하면 yellow/orange가 줄 수 있다.

### P2 중반 20수

| Budget | 후보 |
| --- | --- |
| B16 | H8 loss 0.000 Excellent, H3 loss 3.098 Orange |
| B32 | H8 loss 0.000 Excellent, H3 loss 4.138 Orange, G5 loss 7.653 Red, H4 loss 1.990 Yellow, F8 loss 1.367 Good, E6 loss 4.884 Orange |
| B64 | H8 loss 0.006 Excellent, H3 loss 2.329 Yellow, G5 loss 7.336 Red, H4 loss 0.872 Good, F8 loss 0.688 Good, E6 loss 4.035 Orange, C4 loss 5.922 Orange |

해석:

- 중반 국면에서는 B32부터 레벨링에 쓸 만한 bucket이 확보된다.
- B32와 B64 모두 Good/Yellow/Orange/Red가 나온다.
- B64는 후보가 1개 더 많고, Good 후보도 더 안정적으로 포함했다.
- 다만 latency는 B64가 520ms 수준으로 올라간다.

## 비교 판단

### B16

장점:

- 가장 빠르다.
- 느린 폰에서 대국 리듬을 유지하기 쉽다.
- 현재 Beginner 기본값과 일관된다.

단점:

- 후보가 2~5개 수준에 머물 수 있다.
- 레벨링용 bucket이 부족하다.
- 특히 초반 국면에서 AI가 선택할 수 있는 “그럴듯한 약한 수”가 거의 없다.

판단:

- `Fast Beginner` fallback으로는 유지할 가치가 있다.
- 학습용 기본값으로는 후보 수가 부족하다.

### B32

장점:

- 후보 수가 눈에 띄게 늘어난다.
- P1/P2에서 Good/Yellow/Orange/Red bucket이 생기기 시작한다.
- B64보다 latency 부담이 작다.
- 초급 AI가 best만 두지 않고 “그럴듯한 실수”를 고르기 위한 최소 데이터 확보선으로 보인다.

단점:

- B16 대비 latency가 증가한다.
- 항상 B64보다 후보가 적은 것은 아니지만, 국면에 따라 후보 다양성이 흔들릴 수 있다.
- 느린 Android 폰에서는 350ms cap이 실제 UX상 더 무겁게 느껴질 수 있다.

판단:

- `Learning Beginner`의 1차 기본값 후보로 가장 적절하다.
- 단, AI 착수는 반드시 `MoveSelectionPolicy`로 제어해야 한다. B32에서 best move만 두면 초급 체감이 강해질 수 있다.

### B64

장점:

- 후보 수가 가장 많아지는 경향이 있다.
- P0/P2에서 후보 coverage가 좋아졌다.
- 중반 국면에서 Good/Yellow/Orange/Red 분포가 비교적 안정적이다.

단점:

- latency가 500ms 부근까지 올라간다.
- 현재 `Casual`과 같은 visits이므로, 초급 기본값으로 쓰면 등급 경계가 흐려질 수 있다.
- 항상 B32보다 yellow/orange/red가 더 많이 나온다는 보장은 없다.
- 느린 폰에서는 매 턴 자동 분석 기본값으로 쓰기 부담스럽다.

판단:

- 초급 대국 기본값보다는 `Top Moves`, `복기`, `Learning Wide`, 수동 보강 분석에 적합하다.
- 기본 `Beginner`를 64로 올리는 것은 아직 이르다.

## 종합 결론

현재 데이터 기준으로는 `Beginner 16`을 학습용 기본값으로 계속 쓰기에는 후보 수가 부족하다.

다만 `64`를 바로 초급 기본값으로 올리는 것도 부담이 있다. `64`는 현재 `Casual`과 같은 visits이며, latency가 500ms 부근으로 올라가고, 초급/중급 경계가 흐려질 수 있다.

따라서 1차 권장안은 다음과 같다.

| 용도 | 권장값 |
| --- | --- |
| 느린 폰/빠른 대국 fallback | `Beginner 16 / 250ms` |
| 학습용 초급 기본값 | `Beginner 32 / 350ms` |
| 후보 보강/Top Moves 수동 분석 | `64 / 500ms` |
| 복기/정밀 학습 | `Balanced` 또는 `Deep` |

제품 방향:

1. `Beginner 32`를 `Learning Beginner` 기본 후보로 검토한다.
2. `Beginner 16`은 `Fast Beginner`로 유지한다.
3. `64`는 초급 기본 응수보다는 후보 보강 분석에 우선 사용한다.
4. 실제 AI 착수는 visits 증가와 별개로 `MoveSelectionPolicy`가 선택해야 한다.
5. Android 실기기에서 같은 benchmark를 실행해 latency 기준을 재검증한다.

## 다음 액션

1. shared/domain에 `MoveQualityBucket`을 추가한다.
2. `Beginner 32`를 별도 `Learning Beginner` profile 또는 `PlayLevel` 조합으로 설계한다.
3. AI 착수용 `MoveSelectionPolicy`를 구현해 `Best/Good/Yellow/Orange/Red`와 상대 bucket을 함께 사용한다.
4. 실기기에서 `16/32/64` benchmark를 재측정한다.
5. 실기기 결과까지 반영한 뒤 제품 기본값을 확정한다.
