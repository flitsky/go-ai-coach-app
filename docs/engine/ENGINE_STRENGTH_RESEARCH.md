# 엔진 강도·후보수 연구 (실측 근거 통합)

통합일: 2026-08-31 (백로그 #62)

**세 문서를 한 문서로 합친 것이다** — `ENGINE_BEGINNER_VISITS_BENCHMARK.md`(2026-06-08),
`ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md`(2026-06-10~12),
`ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md`(2026-08-17).

⚠️ **각 절의 본문은 원본 그대로다**(제목 단계만 한 칸 내렸다). 합치면서 요약하거나 다시 쓰지
않았다 — 이 문서들은 `189.6ms`, `16.984`, `3453턴 중 1턴`처럼 **다시 쓰면 반드시 잃는 숫자**로
채워져 있고, 그 숫자가 이 문서의 존재 이유다. 새로 쓴 것은 이 머리말뿐이다.

⚠️ **본문이 서로를 옛 파일명으로 인용한다** — 그 셋이 지금 이 문서의 세 절이다.

| 본문에 나오는 이름 | 지금 위치 |
| --- | --- |
| `ENGINE_BEGINNER_VISITS_BENCHMARK.md` | 이 문서 **1절** |
| `ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` | 이 문서 **2절** |
| `ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md` | 이 문서 **3절** |

본문을 고쳐 이름을 바꾸면 "원본 그대로"라는 성질이 깨지므로 **인용은 손대지 않고 이 표를 둔다.**

## 왜 한 문서인가

셋은 **같은 축의 연속된 실측**이다 — *"어떤 엔진 설정이 어떤 강도와 후보수를 내는가"*.
3절이 1절의 P0/P1/P2 국면을 **그대로 재사용**하고, 2절의 사고 기록을 3절이 근거로 인용한다.
따로 두면 어느 문서에 어떤 수치가 있는지 매번 찾아야 하고, 실제로 그렇게 파편화돼 있었다.

## 관통하는 결론 (세 절이 함께 말하는 것)

1. **`maxVisits`를 올리는 것은 후보수를 늘리는 신뢰할 수 있는 방법이 아니다.** 1절에서 P1의
   B64(6개)가 B32(7개)보다 적었고, 3절에서 P0의 B32·B64가 똑같이 13개로 나왔다. 두 번 다른
   조건에서 재현됐다.
2. **`maxTime`은 품질 하한이 아니라 상한이다.** 2절에서 time cap을 250/500ms → 1000ms로 늘려도
   평균 root visits가 `16.98 → 16.97`, `34.86 → 34.90`으로 사실상 변하지 않았다(맥북 기준).
   느린 기기에서 목표 visits를 채우는 **안전장치**로서만 의미가 있다.
3. **저방문수 구간에서는 visits 차이가 강도 차이로 잘 번역되지 않는다.** 2절 150판에서
   `B32 best` vs `B64 best`가 23:27로 거의 붙었다.
4. **후보수를 결정론적으로 늘리는 레버는 `refinePolicyMoves` 하나뿐이다.** 3절에서 요청한 만큼
   정확히 늘어남을 확인했고(`+4`면 정확히 +4), `wideRootNoise`는 최상위 후보 정확도를 깎아
   *"각 그룹 최고 단계는 항상 최상위 수"* 불변식과 충돌하므로 배제됐다.
5. **그 레버의 비용은 base visits에 달려 있다.** B16에서 310~350ms, B32/B64에서 몇 ms~40ms.
   그래서 후보 확장은 `빠른 초급`(B16/GTP)이 아니라 **JSON 경로에만** 얹는다.

## ⚠️ 무엇이 이미 대체됐는가 (읽을 때 반드시 함께 볼 것)

세 절은 **작성 시점의 제품 구조**를 전제로 쓰였고, 그 구조는 그 뒤 바뀌었다.
**실측 수치는 그대로 유효하지만, 레벨 구성 표는 지금 코드와 다르다.**

| 절의 전제 | 지금 (2026-08-31 코드 확인) |
| --- | --- |
| 1절 "레벨링 적용안" — `Fast Beginner` 3단계 + `Learning Beginner` 7단계 | **`빠른 초급`이 5단계다**(`PlayLevel.kt`의 `FastBeginner.maxLevel = 5`). 후보 분류 기반 5단계 재정립이 2026-08-18에 배포됐다 |
| 2절 "현재 앱 구현" — `빠른 초급 3단계` / `초급 7단계` / `중급 5단계` | `초급`·`중급`·`고급` 그룹은 **대국 설정 UI에서 숨겨져 있다**(코드는 보존, 대국장 로드맵 예정). 사용자가 고르는 것은 `빠른 초급` 5단계뿐이다 |
| 2절 질문 — *"빠른 초급 3단계가 초급 7단계를 자주 이긴다"* | 그 현상은 150판 매트릭스에서 **재현되지 않았다**(15:35로 B32 우세). 2절 자체가 그 결론을 담고 있다 |

## 지금 열려 있는 질문

- **AI 착수 전용 refine 예산값**이 미정이다. `refinePolicyMoves`는 코드에 있고 값이 `0`이다
  (`EngineAnalysisPolicy.kt`) — 레버는 배선됐고 꺼져 있다. 3절 5장이 그 이유와 다음 실험을 적었다.
- **폰 실기기 재측정**이 남았다. 세 절의 latency는 전부 맥북 Metal 기준이고, 폰(Eigen CPU)에서는
  JSON이 GTP보다 유리해지는 역전이 이미 관측됐다(3절).
- 후보 증가가 **레벨링 품질**(색상·승률 분리)까지 개선하는지는 측정되지 않았다.

---

## 1. 2026-06-08 — Beginner Visits 비교 (B16/B32/B64 후보수·latency 최초 실측)

> 원본: `ENGINE_BEGINNER_VISITS_BENCHMARK.md` — **Beginner Visits 비교 평가**
> 작성일: 2026-06-08
> 상태: 1차 실측 기반 검토 문서. 최종 제품 결정 전까지 benchmark 근거 자료로 재사용한다.

### 목적

초급 레벨링에서 `Beginner 16`을 그대로 유지할지, 또는 `32`/`64` visits를 기본값으로 올릴지 판단하기 위해 실제 KataGo analysis 결과를 비교한다.

검토 질문:

- `16`, `32`, `64` visits에서 탐색 속도는 얼마나 차이 나는가?
- 반환되는 `moveInfos` 후보 수는 얼마나 늘어나는가?
- yellow/orange/red bucket이 실제로 더 잘 확보되는가?
- 초급 AI 레벨링에 바로 `32` 또는 `64`를 쓰는 것이 합리적인가?

### 측정 환경

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

### 측정 방법

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

### 테스트 국면

#### P0: 빈 9x9

```text
Moves: none
Next: Black
```

#### P1: 초반 8수

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

#### P2: 중반 20수

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

### 요약 결과

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

### 상위 후보 리스트

#### P0 빈 보드

| Budget | 후보 |
| --- | --- |
| B16 | E5 loss 0.034 Excellent, F5 loss 0.283 Excellent, D5 loss 0.283 Excellent, E4 loss 0.283 Excellent, E6 loss 0.283 Excellent |
| B32 | E5 loss 0.098 Excellent, F5 loss 0.362 Excellent, D5 loss 0.362 Excellent, E4 loss 0.362 Excellent, E6 loss 0.362 Excellent, F6 loss 0.133 Excellent, F4 loss 0.133 Excellent, D6 loss 0.133 Excellent |
| B64 | E5 loss 0.026 Excellent, F5 loss 0.218 Excellent, D5 loss 0.218 Excellent, E4 loss 0.218 Excellent, E6 loss 0.218 Excellent, F6 loss 0.125 Excellent, F4 loss 0.125 Excellent, D6 loss 0.125 Excellent |

해석:

- 빈 보드는 후보가 늘어도 대부분 excellent/green 계열이다.
- 이 국면만으로는 yellow/orange/red 확보 여부를 판단하기 어렵다.
- B64는 후보 수가 크게 늘지만, 색상 다양성은 제한적이다.

#### P1 초반 8수

| Budget | 후보 |
| --- | --- |
| B16 | D8 loss 0.000 Excellent, E2 loss 0.054 Excellent |
| B32 | D8 loss 0.000 Excellent, E2 loss 0.215 Excellent, E7 loss 0.000 Excellent, E8 loss 0.249 Excellent, D7 loss 0.353 Excellent, H5 loss 0.695 Good, H3 loss 2.352 Yellow |
| B64 | D8 loss 0.000 Excellent, E2 loss 0.125 Excellent, E7 loss 0.000 Excellent, E8 loss 0.000 Excellent, D7 loss 0.273 Excellent, H5 loss 0.661 Good |

해석:

- B16은 후보가 2개뿐이라 레벨링 선택지가 부족하다.
- B32는 7개 후보를 반환했고 yellow 후보까지 확보했다.
- B64가 항상 B32보다 색상 다양성이 좋은 것은 아니다. 방문 수가 늘어도 엔진이 좋은 후보 쪽으로 탐색을 집중하면 yellow/orange가 줄 수 있다.

#### P2 중반 20수

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

### 비교 판단

#### B16

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

#### B32

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

#### B64

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

### 종합 결론

현재 데이터 기준으로는 `Beginner 16`을 학습용 기본값으로 계속 쓰기에는 후보 수가 부족하다.

다만 `64`를 바로 초급 기본값으로 올리는 것도 부담이 있다. `64`는 현재 `Casual`과 같은 visits이며, latency가 500ms 부근으로 올라가고, 초급/중급 경계가 흐려질 수 있다.

따라서 1차 권장안은 다음과 같다.

| 용도 | 권장값 |
| --- | --- |
| 느린 폰/빠른 대국 fallback | `Beginner 16 / 250ms` |
| 학습용 초급 기본값 | `Beginner 32 / 500ms` |
| 후보 보강/Top Moves 수동 분석 | `64 / 500ms` |
| 복기/정밀 학습 | `Balanced` 또는 `Deep` |

제품 방향:

1. `Beginner 32`를 `Learning Beginner` 기본 후보로 검토한다.
2. `Beginner 16`은 `Fast Beginner`로 유지한다.
3. `64`는 초급 기본 응수보다는 후보 보강 분석에 우선 사용한다.
4. 실제 AI 착수는 visits 증가와 별개로 `MoveSelectionPolicy`가 선택해야 한다.
5. Android 실기기에서 같은 benchmark를 실행해 latency 기준을 재검증한다.

### 레벨링 적용안

benchmark 결과를 반영하면 초급 레벨링은 색상 bucket 비율보다 상대 순위 기반 선택이 더 단순하다.

정렬 기준:

- scored 후보는 KataGo가 반환한 `moveInfos.order` 순서를 우선한다.
- `0%`는 엔진 order 최상위 후보, `100%`는 엔진 order 최하위 scored 후보로 본다.
- 각 레벨은 정해진 percentile window 안에서 균등 랜덤으로 착수한다.
- `pointLoss`는 후보의 색상/숫자 피드백에만 사용한다. 저예산 분석에서는 `order`와 `pointLoss`가 어긋날 수 있으므로 앱이 손실값만으로 후보 순서를 뒤집지 않는다.

#### Fast Beginner

| 레벨 | Budget | 선택 정책 |
| --- | --- | --- |
| FB 1 | B16 | 하위 50% 후보 중 랜덤 |
| FB 2 | B16 | 상위 50% 후보 중 랜덤 |
| FB 3 | B16 | 최적수 |

해석:

- B16은 후보 수가 적으므로 3단계면 충분하다.
- 빠른 대국과 느린 폰 fallback에 적합하다.
- FB 3은 B16 best라서 강한 중급은 아니지만, Fast 계열의 상한으로는 명확하다.

#### Learning Beginner

| 레벨 | Budget | 선택 정책 | Percentile window |
| --- | --- | --- | ---: |
| LB 1 | B32 | 최하위 30% 후보 중 랜덤 | 70~100% |
| LB 2 | B32 | 하위 50% 후보 중 랜덤 | 50~100% |
| LB 3 | B32 | 중위 40~70% 후보 중 랜덤 | 40~70% |
| LB 4 | B32 | 상위 30~60% 후보 중 랜덤 | 30~60% |
| LB 5 | B32 | 상위 10~50% 후보 중 랜덤 | 10~50% |
| LB 6 | B32 | 상위 30% 후보 중 랜덤 | 0~30% |
| LB 7 | B32 | 최적수 | 0% |

해석:

- B32는 benchmark에서 B16보다 후보 수와 bucket 다양성이 뚜렷하게 좋아졌다.
- B64보다 latency 부담이 낮으므로 학습형 초급 기본값으로 적절하다.
- 초급 1~7단계는 모두 동일한 B32 / 500ms 요청을 사용한다. 레벨링은 엔진 요청을 바꾸지 않고 선택 정책만 바꾸는 방식으로 운영한다.
- 랜덤 선택은 같은 레벨에서도 매판 다른 체감 난이도를 만든다.
- 단, 사용자가 보는 평가 색상은 상대 순위가 아니라 절대 `pointLoss` 기준으로 유지한다.

fallback:

- scored 후보가 부족하면 window를 가장 가까운 후보까지 확장한다.
- Learning 계열에서 scored 후보가 3개 미만이면 `64 / 500ms` 보강 분석을 검토한다.
- pass는 종국 또는 엔진 최상위 pass가 아닌 경우 선택 후보에서 제외한다.

### 다음 액션

완료:

1. shared/domain에 percentile window 기반 `MoveSelectionPolicy`를 추가했다.
2. `Fast Beginner` 3단계와 `Learning Beginner` 7단계를 포함하는 `PlayLevelSetting`을 정의했다. `Learning Beginner`는 모든 단계에서 B32 / 500ms 요청을 공유하고, 단계별 차이는 `MoveSelectionPolicy`만으로 만든다.
3. 사용자 메뉴에서는 raw `Lite/Balanced/Deep` 버튼을 제거하고 `빠른 초급`, `초급`, `중급`, `고급` 그룹과 단계 조정 UX로 변경했다.
4. AI 대국 응수는 현재 `PlayLevelSetting`의 분석 예산으로 scored 후보를 받은 뒤, 단계별 상대 순위 구간에서 랜덤 선택하도록 연결했다.

남은 작업:

1. shared/domain에 `MoveQualityBucket`을 추가해 사용자 표시 색상과 평가 문구를 더 명시적으로 관리한다.
2. 실제 Android 폰에서 `16/32/64` benchmark를 재측정한다.
3. 실기기 결과까지 반영한 뒤 제품 기본값과 자동 fallback 기준을 확정한다.
4. 후보가 너무 적을 때 `64 / 500ms` 보강 분석을 자동 수행할지 결정한다.

---

## 2. 2026-06-10~12 — 레벨 강도 검토와 150판 매트릭스

> 원본: `ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` — **엔진 레벨 강도 검토**
> 작성일: 2026-06-10

### 질문

`빠른 초급 3단계(B16 best)`가 `초급 7단계(B32 best)`를 자주 이기는 현상이 있다.

확인할 내용:

1. `16 visits`와 `32 visits`의 기대 차이는 무엇인가?
2. 엔진 응답시간을 강제할 수 있는가?
3. KataGo 공식 설명에 최적 품질을 위한 최소 시간 제안이 있는가?
4. 우리 설정에서 놓친 부분은 무엇인가?

### 2026-06-11 추가 검증: cache isolation 적용 매트릭스

앱 쪽 정책 변경 후, 맥북 반복 대국 테스트도 “이전 분석의 cache 영향을 줄인 상태”로 다시 돌렸다.

주의할 점:

- 앱 실제 자동대국 경로는 GTP `kata-search_analyze`와 GTP `clear_cache`를 사용한다.
- 기존 맥북 매트릭스 스크립트는 KataGo JSON `analysis` 엔진을 직접 사용한다.
- 로컬 Homebrew KataGo v1.16.4 Metal analysis engine은 JSON special action `clear_cache`를 받으면 SIGSEGV(-11)로 종료되는 문제가 재현됐다.
- 따라서 이번 매트릭스는 직접 `clear_cache` 대신 `nnCacheSizePowerOfTwo=0` override를 사용해 NN cache를 사실상 1엔트리로 줄이는 `tiny-nn-cache` 격리 모드로 실행했다.
- search tree 재사용 이슈는 원래 GTP process에서 가장 크게 관측됐고, JSON analysis 엔진은 요청 단위 분석에 가깝다. 이번 결과는 앱 자동대국과 완전히 동일한 검증은 아니지만, 레벨별 visits/time 차이가 통계적으로 어떻게 드러나는지 보는 근거로는 유효하다.

스크립트 변경:

- `scripts/run-katago-level-match.py`의 기본 time cap을 앱 기본값과 맞췄다.
  - B16: `16 visits / 1000ms`
  - B32: `32 visits / 2000ms`
  - B64: `64 visits / 3000ms`
- 기본 cache isolation은 `tiny-nn-cache`다.
- `--cache-isolation none | tiny-nn-cache | clear-cache` 옵션을 추가했다. 단, 현재 로컬 v1.16.4 Metal에서는 `clear-cache`가 크래시하므로 기본값으로 쓰지 않는다.

실행:

```bash
ENGINE_MATCH_OUT=docs/engine/measurements/engine-match/matrix-tinycache-20260611 \
ENGINE_MATCH_GAMES=50 \
make engine-level-benchmark
```

결과:

- summary: `docs/engine/measurements/engine-match/matrix-tinycache-20260611/summary.md`
- raw logs:
  - `docs/engine/measurements/engine-match/matrix-tinycache-20260611/B16-vs-B32.jsonl`
  - `docs/engine/measurements/engine-match/matrix-tinycache-20260611/B16-vs-B64.jsonl`
  - `docs/engine/measurements/engine-match/matrix-tinycache-20260611/B32-vs-B64.jsonl`

| Matchup | 기대 | 실제 | 해석 |
| --- | ---: | ---: | --- |
| B16 vs B32 | `25% : 75%` | `40% : 60%` | B32가 우세하지만 기대보다 약하다. B16/B32는 둘 다 절대 visits가 낮아 격차가 작다. |
| B16 vs B64 | `12% : 88%` | `16% : 84%` | 기대와 거의 비슷하다. B64는 B16보다 명확히 강하다. |
| B32 vs B64 | `25% : 75%` | `18% : 82%` | 기대보다 B64가 더 강하게 나왔다. B32/B64 구분은 충분히 드러난다. |

평균 root visits:

| Matchup | Level | Avg root visits | Avg elapsed |
| --- | --- | ---: | ---: |
| B16 vs B32 | B16 | `16.995` | `172.421ms` |
| B16 vs B32 | B32 | `34.961` | `285.602ms` |
| B16 vs B64 | B16 | `16.994` | `169.114ms` |
| B16 vs B64 | B64 | `66.944` | `521.464ms` |
| B32 vs B64 | B32 | `34.966` | `272.563ms` |
| B32 vs B64 | B64 | `66.969` | `502.051ms` |

판단:

- 맥북에서는 B16/B32/B64 모두 요청 visits를 안정적으로 채운다.
- `B16 < B32 < B64` 방향성은 재현됐다.
- 다만 B16과 B32의 격차는 제품 기대치인 `25:75`보다 약하다. 이는 설정 오류라기보다 `16 -> 32`가 여전히 초저방문수 구간이라는 점, 9x9의 큰 swing, 저방문수에서의 후보 order 흔들림 때문으로 보는 것이 합리적이다.
- B64는 B16/B32 양쪽에 대해 충분히 강한 차이를 보인다.
- 앱과 100% 같은 검증이 필요하면 다음 단계는 JSON analysis 스크립트가 아니라 GTP 기반 자동대국 harness를 만들어 GTP `clear_cache`와 `kata-search_analyze`를 그대로 사용해야 한다.

### 현재 앱 구현

현재 AI 응수는 `MatchPolicy.selectAiMoveFromAnalysis()`에서 다음 흐름으로 동작한다.

1. 현재 진영의 `PlayLevelSetting.analysisLimit`로 `EngineAdapter.analyze()` 호출
2. `pointLoss`가 있는 searched 후보만 선택 대상으로 사용
3. `MoveSelectionPolicy`가 지정한 후보 index 범위에서 착수 선택
4. `BestOnly`는 후보 range가 `0..0`이므로 엔진이 반환한 첫 번째 searched 후보만 선택

현재 주요 레벨:

| 레벨 | 요청 | 선택 |
| --- | --- | --- |
| 빠른 초급 3단계 | `16 visits / 1000ms` 기본 | `BestOnly` |
| 초급 1~7단계 | `32 visits / 2000ms` 기본 | 1~6단계는 percentile window, 7단계는 `BestOnly` |
| 중급 | `64 visits / 3000ms` 기본 | 단계별 percentile window 또는 `BestOnly` |

따라서 `빠른 초급 3단계`와 `초급 7단계`의 차이는 현재 다음 하나다.

- 같은 `BestOnly` 선택 정책
- 단, search budget이 `16 visits`에서 `32 visits`로 증가

### KataGo 공식 동작 기준

KataGo analysis engine의 query는 `maxVisits`를 받을 수 있고, 지정하지 않으면 analysis config의 기본값을 사용한다. 우리 JSON query도 `maxVisits`를 직접 넣는다.

KataGo config의 `maxTime`은 "이만큼은 반드시 생각하라"가 아니라 검색 시간을 제한하는 cap이다. `maxVisits`, `maxPlayouts`, `maxTime`은 모두 search limit이다.

따라서 `maxVisits=32`, `maxTime=0.5`를 같이 넣으면 실무적으로는 다음처럼 이해해야 한다.

- `32 visits`에 먼저 도달하면 500ms를 다 쓰기 전에 멈출 수 있다.
- 500ms에 먼저 도달하면 32 visits를 채우기 전에 멈출 수 있다.
- 즉 `maxTime`은 품질을 높이는 최소 시간 보장이 아니라 과도한 지연을 막는 상한이다.

KataGo 공식 config는 `numSearchThreads`에 대해 benchmark로 튜닝하라고 설명한다. 공식 문서에 “최소 몇 ms 이상이면 최적 품질” 같은 고정 제안은 없다. 하드웨어, backend, board size, search threads, batch 상황에 따라 달라진다.

### 16과 32 visits의 기대 차이

기대 효과:

- `32 visits`는 `16 visits`보다 root 후보를 조금 더 검증한다.
- 후보별 `scoreLead`, `winrate`, `pointLoss` 추정이 조금 더 안정될 가능성이 있다.
- 같은 `BestOnly`라면 장기적으로는 B32가 B16보다 더 좋은 수를 고를 가능성이 높다.

하지만 현재 수준에서는 기대 차이가 작고, 승률 차이가 선형으로 커진다고 보면 안 된다.

- `16 -> 32`는 두 배이지만 절대량은 여전히 매우 낮다.
- MCTS는 "방문수 두 배 = 수 품질 두 배"가 아니다. 이미 policy prior가 강하게 찍은 후보를 확인하는 데 방문수가 쓰일 수 있고, 실제로 대안 후보를 충분히 넓게 탐색하지 못할 수도 있다.
- `16 visits`에서도 policy prior가 좋은 수를 정확히 찍으면 강한 수를 둘 수 있다. 반대로 `32 visits`도 잘못된 초기 prior 또는 저예산 score 흔들림을 완전히 제거하지 못한다.
- 9x9는 한 수의 swing이 크고 판 수가 짧아, 한두 번의 후보 평가 흔들림이 최종 승패로 바로 이어질 수 있다.
- 9x9 초반/중반은 한두 수 swing이 커서 10판 표본으로 강약을 단정하기 어렵다.
- KataGo `order`는 점수 손실 하나만이 아니라 `playSelectionValue` 기준이다.
- 저예산에서는 후보별 평가가 흔들릴 수 있다.
- 현재 `analysis_learning.cfg`는 `nnRandomize = true`다. 저예산에서는 이 랜덤화가 결과 흔들림을 더 크게 만들 수 있다.

즉 `초급 7단계(B32 best)`가 `빠른 초급 3단계(B16 best)`보다 항상 이긴다고 기대하면 안 된다. 다만 반복 실험에서 계속 역전된다면 설정 또는 실험 조건을 더 통제해야 한다.

### 놓친 가능성이 큰 설정

#### 1. `maxTime`을 최소 thinking time으로 오해

현재 가장 큰 혼선 포인트다.

`500ms`는 “최소 500ms 동안 더 좋은 수를 찾는다”가 아니다. `32 visits`가 빨리 끝나면 품질은 사실상 `32 visits` 품질이다.

초급을 B32로 고정하겠다는 제품 결정이 있다면, 초급 7단계의 품질을 더 올리는 방법은 `500ms`를 늘리는 것이 아니라 아래 항목을 조정해야 한다.

#### 2. analysis randomization

현재 bundled `analysis_learning.cfg`:

```text
nnRandomize = true
```

공식 config도 `nnRandomize`와 `nnRandSeed`를 제공한다. 강도 비교와 재현 테스트 목적이라면 다음을 별도 실험 모드에서 검토할 수 있다.

```text
nnRandomize = false
```

또는 seed 고정:

```text
nnRandSeed = <fixed seed>
searchRandSeed = <fixed seed>
```

주의: 실제 사용자 대국에서는 약간의 랜덤성이 자연스럽게 느껴질 수 있다. 하지만 레벨 강도 검증에는 방해가 된다.

#### 3. search thread 수

우리 analysis process는 runtime override로 `numAnalysisThreads=1`, `numSearchThreads=4`를 사용한다.

KataGo example config는 같은 MCTS tree 안에서 여러 thread를 쓰면 고정 playout 기준으로 검색 품질이 약간 약해질 수 있다고 설명한다. 빠른 응답에는 유리하지만, 아주 낮은 visits에서는 이 영향이 상대적으로 커질 수 있다.

강도 검증 실험에서는 다음 두 조건을 비교할 가치가 있다.

| 목적 | numSearchThreads |
| --- | ---: |
| 실사용 응답성 | 4 |
| 고정 visits 품질/재현성 검증 | 1 |

#### 4. 너무 낮은 절대 visits

B16과 B32는 둘 다 매우 낮은 search다. 초급/중급 구분을 `16/32/64`로 유지한다면, B32의 기대 강도 차이는 “명확히 더 안정적”이라기보다 “조금 더 검증한다”에 가깝다.

따라서 B16 best와 B32 best 사이 승률 격차가 작게 나오는 것은 이상 현상이라기보다 가능한 현상이다.

#### 5. AI 응수와 Top Moves 분석 예산 혼합

중급 5단계의 표면 설정은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| 그룹 | `중급` |
| difficulty label | `Casual` |
| visits | `64` |
| time cap | `500ms` |
| candidate count | `20` |
| 선택 정책 | `BestOnly` |

하지만 기존 구현에서는 `중급`이 `Balanced` preset을 공유하면서 AI 응수에도 다음 보강이 같이 들어갔다.

- `minVisitsPerCandidate = 4`
- `minTimeMillis = 800ms`
- `refinePolicyMoves = 4`

그 결과 실제 adapter의 effective limit은 `64/500`이 아니라 최소 `80 visits / 800ms`가 될 수 있고, policy refine query까지 추가될 수 있었다.

조치:

- AI 대국 응수는 Top Moves용 보강을 사용하지 않는다.
- AI 응수 분석 요청은 `includePolicy=false`, `refinePolicyMoves=0`, `minVisitsPerCandidate=0`, `minTimeMillis=null`로 정규화한다.
- 따라서 중급 5단계 AI 응수는 다시 `64 visits / 500ms` 요청이 된다.

Top Moves/힌트 분석은 학습 UI 품질을 위해 기존 `Balanced` 보강을 유지할 수 있다.

### 제안

#### 1차: 설정 변경 전 검증부터

바로 레벨 값을 바꾸기보다, 먼저 실험 조건을 고정한다.

1. AI-vs-AI 자동 대전 테스트 harness를 만든다.
2. 같은 매치업을 흑백 교대 20~50판 이상 실행한다.
3. `B16 best` vs `B32 best` 승률, 평균 집 차이, pass 시점, 선택 후보 order, raw visits를 기록한다.
4. `nnRandomize=true/false`, `numSearchThreads=4/1`을 비교한다.

초기 smoke 결과:

- script: `scripts/run-katago-level-match.py`
- log: `docs/engine/measurements/engine-match/fb3-vs-lb7-det-20260610.jsonl`
- 조건: deterministic, `numSearchThreads=1`, warm-up 후 4판, 흑백 교대
- 결과: `초급 7단계` 3승, `빠른 초급 3단계` 1승

이 결과는 표본이 작아 결론으로 쓰기에는 부족하지만, 적어도 deterministic/warm-up 조건에서는 B32 best가 B16 best보다 우세한 경향을 보였다.

#### 1차 150판 매트릭스 결과

요청한 3개 조합을 각각 50판씩 실행했다.

- command: `make engine-level-benchmark ENGINE_MATCH_GAMES=50 ENGINE_MATCH_OUT=docs/engine/measurements/engine-match/matrix-20260610`
- script: `scripts/run-katago-level-matrix.py`
- summary: `docs/engine/measurements/engine-match/matrix-20260610/summary.md`
- raw logs:
  - `docs/engine/measurements/engine-match/matrix-20260610/B16-vs-B32.jsonl`
  - `docs/engine/measurements/engine-match/matrix-20260610/B16-vs-B64.jsonl`
  - `docs/engine/measurements/engine-match/matrix-20260610/B32-vs-B64.jsonl`
- 조건: 실사용에 가까운 non-deterministic, `numSearchThreads=4`, 흑백 교대, warm-up, final evaluator `400 visits / 2000ms`
- 소요 시간: 약 23분 41초

| Matchup | Left wins | Right wins | Left win rate | Avg left lead |
| --- | ---: | ---: | ---: | ---: |
| 빠른 초급 3단계(B16 best) vs 초급 7단계(B32 best) | 15 | 35 | 30% | -5.766 |
| 빠른 초급 3단계(B16 best) vs 중급 5단계(B64 best) | 10 | 40 | 20% | -5.620 |
| 초급 7단계(B32 best) vs 중급 5단계(B64 best) | 23 | 27 | 46% | -0.276 |

평균 응답 시간과 root visits:

| Matchup | Level | Avg elapsed | Avg root visits |
| --- | --- | ---: | ---: |
| B16 vs B32 | 빠른 초급 3단계 | 32.843ms | 16.984 |
| B16 vs B32 | 초급 7단계 | 126.339ms | 34.855 |
| B16 vs B64 | 빠른 초급 3단계 | 17.530ms | 16.977 |
| B16 vs B64 | 중급 5단계 | 189.444ms | 66.867 |
| B32 vs B64 | 초급 7단계 | 61.802ms | 34.907 |
| B32 vs B64 | 중급 5단계 | 231.842ms | 66.904 |

해석:

- 이번 150판 데이터에서는 `B32 best`가 `B16 best`에게 열세라는 현상은 재현되지 않았다. 오히려 B32가 35승 15패로 우세했다.
- `B64 best`는 `B16 best`를 40승 10패로 이겨, B16/B64 차이는 충분히 드러났다.
- 더 중요한 발견은 `B32 best`와 `B64 best`가 23승 27패로 거의 비슷하게 나온 점이다. 즉 현재 저예산 9x9 BestOnly 조건에서는 `32 -> 64 visits` 차이가 기대보다 약하게 분리된다.
- 따라서 사용자가 폰에서 본 `B16 > B32` 현상은 작은 표본, 흑백 순서, 랜덤성, 구버전 빌드, 또는 이전 AI 응수 경로에서 Top Moves 보강이 섞였던 문제의 영향일 가능성이 높다.
- 다만 `B32`와 `B64`의 강도 차이가 약한 것은 실제 제품 레벨링 관점에서 계속 추적해야 한다. 중급이 확실히 더 강하게 느껴져야 한다면 `B64`의 visits/time만이 아니라 selection policy, 랜덤성, 엔진 config, 후반 pass 정책까지 함께 조정해야 한다.

주의:

- 이번 승패 판정은 대국 종료 후 final evaluator analysis estimate를 사용한다. 실제 앱의 사석 정리/계가 최종 로직과 완전히 동일한 검증은 아니다.
- 상대 강도 비교용 데이터로는 충분히 의미가 있지만, 마켓 릴리즈 전에는 앱 내 `final_score/final_status_list` 기반 종료 결과와 자동 대전 harness 결과를 한 번 더 결합하는 것이 좋다.

#### B16/B32 time cap 1000ms 추가 실험

`maxTime` 부족으로 B32가 충분히 32 visits를 채우지 못하는지 확인하기 위해, 방문수는 그대로 두고 B16/B32의 time cap만 모두 `1000ms`로 늘린 50판 실험을 추가했다.

- command: `python3 scripts/run-katago-level-match.py --black fast_beginner:3 --white beginner:7 --black-time-ms 1000 --white-time-ms 1000 --games 50 --swap-colors --seed 20260611 --out docs/engine/measurements/engine-match/b16-vs-b32-time1000-20260610.jsonl`
- summary: `b16-vs-b32-time1000-20260610-summary.md`
- raw log: `docs/engine/measurements/engine-match/b16-vs-b32-time1000-20260610.jsonl`
- 조건: 실사용에 가까운 non-deterministic, `numSearchThreads=4`, 흑백 교대, warm-up, final evaluator `400 visits / 2000ms`
- 소요 시간: 약 6분 26초

| 조건 | B16 wins | B32 wins | B32 win rate | B32 avg lead | B16 avg root visits | B32 avg root visits |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 기존 B16 250ms / B32 500ms | 15 | 35 | 70% | +5.766 | 16.984 | 34.855 |
| B16 1000ms / B32 1000ms | 24 | 26 | 52% | +2.564 | 16.981 | 34.899 |

방문수 미달은 기존 조건에서 3453턴 중 1턴, 1000ms 조건에서 3367턴 중 0턴이었다.

해석:

- 맥북에서는 기존 B16/B32 조건에서도 대부분 요청 visits를 이미 채우고 있다.
- time cap을 `1000ms`로 늘려도 평균 root visits는 거의 변하지 않았다. 따라서 맥북 기준으로는 `maxTime`이 아니라 `maxVisits`가 먼저 search를 끝낸다.
- 이번 추가 50판에서 B32 우세가 약해졌지만, root visits가 동일하므로 시간 증가 효과라기보다 저방문수, `nnRandomize=true`, search thread, 9x9 swing에 따른 표본 흔들림으로 봐야 한다.
- 폰에서는 500ms 안에 B32 visits를 못 채울 가능성이 있으므로, time cap 확대는 “강도 향상”보다는 “느린 기기에서 목표 visits를 안정적으로 채우는 안전장치”로 의미가 있다.

#### B16/B32 time cap 1000ms 2차 50판

동일 조건을 새 seed로 50판 더 실행했다.

- command: `python3 scripts/run-katago-level-match.py --black fast_beginner:3 --white beginner:7 --black-time-ms 1000 --white-time-ms 1000 --games 50 --swap-colors --seed 20260612 --out docs/engine/measurements/engine-match/b16-vs-b32-time1000-r2-20260610.jsonl`
- summary: `b16-vs-b32-time1000-r2-20260610-summary.md`
- raw log: `docs/engine/measurements/engine-match/b16-vs-b32-time1000-r2-20260610.jsonl`

| 조건 | B16 wins | B32 wins | B32 win rate | B32 avg lead | B16 avg root visits | B32 avg root visits |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| B16 1000ms / B32 1000ms, 1차 | 24 | 26 | 52% | +2.564 | 16.981 | 34.899 |
| B16 1000ms / B32 1000ms, 2차 | 24 | 26 | 52% | +4.302 | 16.961 | 34.742 |
| B16 1000ms / B32 1000ms, 누적 100판 | 48 | 52 | 52% | +3.433 | 16.971 | 34.820 |

누적 방문수 미달:

| Level | total turns | root visits below request |
| --- | ---: | ---: |
| B16 | 3380 | 5 |
| B32 | 3381 | 18 |

해석:

- 1000ms 조건 100판 누적에서도 B32는 근소 우세에 그쳤다.
- 평균 root visits는 B16 약 `17`, B32 약 `35`로 기존과 거의 같다.
- 맥북 기준으로는 time cap을 더 늘리는 것이 B32 강도 차이를 뚜렷하게 만든다는 근거가 약하다.
- 이제 핵심은 폰에서 실제로 B32/B64가 request visits를 못 채우는지 확인하는 것이다. 앱 debug report에는 `Visit diagnostics: request=..., root=..., elapsedMs=..., timeCapMs=..., fill=OK/SHORT`가 남도록 반영했다.

#### 2차: 검증용 deterministic mode 추가

사용자 대국 기본값은 유지하되, 개발/검증용으로 deterministic analysis config를 추가한다.

후보:

```text
nnRandomize = false
numSearchThreads = 1
```

이 모드는 레벨 강도 비교용이며, 실사용 기본값으로 바로 쓰지는 않는다.

#### 3차: 제품 레벨링 재판단

검증 후에도 `B32 best`가 `B16 best`를 안정적으로 이기지 못한다면, 다음 중 하나를 선택해야 한다.

1. `빠른 초급 3단계`를 BestOnly가 아니라 상위 후보 랜덤으로 낮춘다.
2. `초급 7단계`는 B32 유지하되 deterministic settings를 적용한다.
3. 초급/중급 경계 정책을 재검토한다.
4. `16/32/64`를 엔진 search 강도만이 아니라 `MoveSelectionPolicy` 중심의 제품 레벨로 재정의한다.

현재 제품 원칙이 `빠른 초급=16`, `초급=32`, `중급=64`라면 1번 또는 2번이 가장 보수적이다.

### 현재 결론

놓친 가능성이 가장 큰 것은 `maxTime`보다 `랜덤성`과 `저방문수에서의 불안정성`이다.

`500ms`를 늘리는 것만으로는 `B32` 품질이 반드시 좋아지지 않는다. `maxVisits=32`가 먼저 걸리면 search는 끝난다. 따라서 레벨 강도를 검증하려면 deterministic analysis 조건과 충분한 반복 대전 로그가 먼저 필요하다.

### 참고 근거

- KataGo Analysis Engine: https://github.com/lightvector/KataGo/blob/master/docs/Analysis_Engine.md
- KataGo GTP example config: https://github.com/lightvector/KataGo/blob/master/cpp/configs/gtp_example.cfg
- KataGo analysis example config: https://github.com/lightvector/KataGo/blob/master/cpp/configs/analysis_example.cfg

---

## 3. 2026-08-17 — 후보수 확장 레버 검토 (`refinePolicyMoves`)

> 원본: `ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md` — **엔진 후보수 확장 검토 — 빠른 초급/초급 더블체크 + 레벨링용 후보 확장 레버**
> 작성일: 2026-08-17
> 상태: 방향성 검토 문서. 이 문서는 **앱(Kotlin) 코드를 바꾸지 않는다.** 결론은 "다음에 무엇을 실험/구현할지"에 대한 권장안이며, 실제 반영은 별도 착수로 분리한다.

### 배경 질문 (사용자 요청 원문 요약)

1. `빠른 초급`/`초급` 두 모드에 대해 기존 문서를 더블체크하고 재정리한다.
2. 앞으로 AI 난이도 조정이 원활하도록, **후보수(candidate moves) 반환이 많이 되는 방식**을 찾아 정립한다. 단, 너무 느려지면 안 된다.
3. 기억 확인: `빠른 초급`은 엔진의 "빠른 리서치" 기능, `초급`은 "JSON 탐색" 기능을 쓴 것 같다 — 그리고 후보수를 늘리는 데는 JSON 탐색 쪽이 유리할 수 있다.
4. 엔진 분석 시 `16/32/64` 모드와 각각의 시간 제한을 걸 수 있었던 것 같다.
5. 앱을 매번 빌드/실행하지 않고, 터미널의 Python으로 엔진 서치 기능을 먼저 고도화한 뒤 앱에 탑재하는 방법을 고려해달라.

아래는 이 5개 질문에 대한 더블체크 결과와, 그 위에서 새로 실측한 데이터, 그리고 권장 방향이다.

### 1. 더블체크: `빠른 초급`/`초급`은 이미 잘 정리돼 있었다

사용자가 기억하는 "깊이 있게 정리된 문서"는 `ENGINE.md`와 그 딥다이브인 `ENGINE_API_CALL_POLICY.md`다. 오늘 이 두 문서를 실제 코드(`shared/src/commonMain/kotlin/.../PlayLevel.kt`, `EngineAnalysisPolicy.kt`)와 한 줄씩 대조했다.

#### 1-1. 레벨별 표는 코드와 정확히 일치한다

| 레벨 그룹 | 탐색 모드 | visits | 기본 time cap | candidateCount 상한 |
| --- | --- | ---: | ---: | ---: |
| 빠른 초급 (1~3단계) | `GtpStatefulFast` | 16 | 1000ms (B16) | 8 |
| 초급 (1~7단계) | `JsonPositionAnalysis` | 32 | 2000ms (B32) | 16 |
| 중급 (1~5단계) | `JsonPositionAnalysis` | 64 | 3000ms (B64) | 20 |
| 고급 (1~5단계) | `JsonPositionAnalysis` | 160 | 1000ms | 24 |

`PlayLevelGroup`(`shared/PlayLevel.kt:16-55`)의 `visits`/`timeMillis`/`candidateCount` 값이 이 표와 완전히 같다. `아이MoveSearchMode()`(`shared/EngineAnalysisPolicy.kt:46-51`)도 문서에 적힌 `if (group == FastBeginner) GtpStatefulFast else JsonPositionAnalysis` 그대로다. **사용자의 기억 3, 4번은 정확하다** — `빠른 초급`은 GTP stateful fast(빠른 리서치), `초급` 이상은 JSON position analysis이고, `16/32/64` 방문수와 그에 딸린 time cap(사용자가 `Search Time` 메뉴에서 조정 가능한 B16/B32/B64 프리셋)이 실제로 존재한다.

#### 1-2. 문서에 없던 것 두 가지를 코드에서 발견했다

문서 표만 보면 "빠른 초급도 최대 8개, 초급도 최대 16개 후보를 받는다"처럼 읽히지만, 실제 **AI 착수 선택 경로**는 이보다 더 좁게 동작한다.

**(a) 빠른 초급 3단계(BestOnly)는 8개가 아니라 1개만 요청한다.**

```kotlin
// shared/EngineAnalysisPolicy.kt:53-70
EngineSearchMode.GtpStatefulFast -> {
    val count = if (selectionPolicy is MoveSelectionPolicy.BestOnly) 1 else base.candidateCount
    base.fastCandidateAnalysis(candidateCount = count)
}
```

`빠른 초급` 1~2단계(퍼센타일 선택)는 문서대로 최대 8개를 요청하지만, 3단계(`BestOnly`)는 엔진에 후보 1개만 요청한다. 후보수 확장 논의에서 `빠른 초급`을 제외하는 근거 중 하나다.

**(b) `초급` 이상(JSON 경로)의 AI 착수는 `refinePolicyMoves`/`minVisitsPerCandidate`가 항상 0으로 꺼져 있다.**

```kotlin
// shared/EngineAnalysisPolicy.kt:53-70
EngineSearchMode.JsonPositionAnalysis ->
    base.copy(
        includePolicy = true,
        refinePolicyMoves = 0,      // <- 항상 0
        minVisitsPerCandidate = 0,  // <- 항상 0
        minTimeMillis = null,
    )
```

이건 중요한 발견이다. 아래 3절에서 자세히 다루지만, 요약하면: **엔진 어댑터에는 이미 "후보를 늘리는 기능"이 구현돼 있는데, AI 착수(레벨링) 경로에서는 지금 그 기능을 쓰지 않는다.** `TopMovesDisplay`/`HumanMoveReview` 경로도 마찬가지로 `fastCandidateAnalysis()`를 거치며 항상 `refinePolicyMoves=0`으로 정규화된다(`EngineAnalysisPolicy.kt:10-22`). 즉 2026-08-17 기준 이 앱은 **어떤 화면에서도 `refinePolicyMoves > 0`인 요청을 실제로 보내지 않는다.**

이 결론은 코드 전체 grep(`grep -rn "aiMoveAnalysisLimitWith\|fastCandidateAnalysis\|turnAnalysisLimitFor"`)으로 모든 호출부(`AiMoveSelectionPolicy.kt`, `AnalysisSession.kt`, `AutoAiPolicyApplication.kt` 등)를 확인해 검증했다.

### 2. 왜 이런 구조가 됐는지 (과거 기록과 대조)

`ENGINE_BEGINNER_VISITS_BENCHMARK.md`(2026-06-08)가 이 문제의 출발점이었다. 당시 실측:

| 국면 | B16 후보수 | B32 후보수 | B64 후보수 |
| --- | ---: | ---: | ---: |
| P0 빈 보드 | 5 | 13 | 25 |
| P1 초반 8수 | 2 | 7 | 6 |
| P2 중반 20수 | 2 | 6 | 7 |

결론: "`Beginner 16`을 학습용 기본값으로 쓰기엔 후보 수가 부족하다"(P1에서 2개뿐). 이 발견이 지금의 `빠른 초급=B16 GTP`, `초급=B32 JSON` 분리로 이어졌다. 그리고 이 문서의 "남은 작업" 4번에 이미 이렇게 적혀 있었다:

> 4. 후보가 너무 적을 때 `64/500ms` 보강 분석을 자동 수행할지 결정한다.

이 결정은 아직 내려지지 않았다. 대신 `refinePolicyMoves`라는, 방문수를 통째로 올리는 것보다 더 정교한 메커니즘이 `engine-android/KataGoJsonPositionAnalysisClient.kt`에 구현됐지만(2026-06-13 전후로 추정), `EngineAnalysisPolicy.kt`가 AI 착수 경로에서 이를 0으로 눌러놓은 채 지금까지 왔다. `ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md`의 "5. AI 응수와 Top Moves 분석 예산 혼합" 절을 보면, 과거에 `Balanced` 프리셋의 `minVisitsPerCandidate=4`/`refinePolicyMoves=4` 보강이 AI 착수 경로에 실수로 섞여 레벨 강도 실험이 오염된 사고가 있었다. `EngineAnalysisPolicy.kt`의 강제 0-초기화는 그 사고의 재발 방지책으로 보인다 — 즉 "후보 확장 기능이 나빠서" 꺼둔 게 아니라 "AI 착수와 Top Moves 예산이 섞이지 않게" 안전장치로 전부 꺼둔 것이다. 이 문서가 제안하는 방향은 이 안전장치를 없애자는 게 아니라, **AI 착수 전용의 명시적이고 작은 refine 예산을 새로 만들어 정책적으로 통제하자**는 것이다(5절).

### 3. 후보수를 늘리는 4가지 방법 비교

KataGo 문서(`ENGINE_API_CALL_POLICY.md` "Visit의 의미와 탐색 원리" 절)와 코드를 근거로 실제 후보수를 늘리는 레버는 4가지가 있다.

| 레버 | 방식 | 후보수 증가 | 속도 비용 | `order=0`(최상위 정확도) 영향 | 현재 상태 |
| --- | --- | --- | --- | --- | --- |
| A. `maxVisits` 자체를 올림 (16→32→64→…) | 더 오래 탐색 | 불확실 — 국면에 따라 오히려 줄기도 함(B64가 B32보다 적은 사례 실측됨) | 큼, sublinear 개선 | 좋아짐(더 안정) | 이미 레벨 정의 자체 |
| B. `wideRootNoise` 상향 | MCTS 탐색을 억지로 넓힘 | 늘어남 | 중간 | **나빠짐** — top move 정확도를 깎는 대가로 다양성을 얻는 KataGo 공식 설계 | 미사용 |
| C. `includePolicy=true`만 켜서 policy-only fallback 채움 | 검색 안 한 나머지를 policy prior만으로 채움 | 후보 리스트 길이는 늘지만 `pointLoss`가 없어 레벨링(퍼센타일 선택)에는 못 씀 | 거의 0 | 없음 | **이미 켜져 있음**(JSON 경로는 항상 `includePolicy=true`)이지만 레벨링에 기여 못함 |
| D. `refinePolicyMoves` (policy 상위 후보를 개별 소예산 재검색) | policy 상위 미탐색 후보 N개에 대해 각각 8-visit 추가 쿼리를 날려 실제 `pointLoss`를 채움 | **요청한 만큼 정확히 늘어남**(아래 실측 참고) | 조건부 — 아래 4절 실측 참고 | 없음 — 항상 기존 `order=0` 뒤에 덧붙여짐(코드 구조상 원천적으로 안전) | **이미 구현됐지만 AI 착수 경로에서 강제로 0으로 꺼짐** |

**B(wideRootNoise)를 배제하는 이유**: 이 앱의 불변식은 "각 레벨 그룹의 최고 단계는 항상 엔진 후보 순위의 최상위 수를 둔다"(`ENGINE_API_CALL_POLICY.md` 결정 3번)이다. `wideRootNoise`는 KataGo 공식 문서 기준으로 "top move를 덜 깊고 정확하게 보더라도 다양성을 높이는" 옵션이라, 같은 분석 결과를 최상위 단계의 착수 판단에도 쓰는 현재 구조에서는 최상위 수 자체가 흔들릴 위험이 있다. 별도 분석 인스턴스로 완전히 분리하지 않는 한 채택하지 않는 게 안전하다.

**D(`refinePolicyMoves`)가 유력한 이유**: 이미 구현돼 있고, 구조적으로 `order=0`을 건드리지 않으며(기존 scored 후보 뒤에만 덧붙임), 요청한 refine 개수만큼 결정론적으로 후보가 늘어난다(운에 좌우되는 A와 다름). 남은 질문은 "얼마나 비싼가"였고, 이건 실측이 필요했다.

### 4. 터미널 실측: `refinePolicyMoves`의 실제 비용

기존 `scripts/run-katago-level-match.py`류는 raw visits/time만 바꿀 수 있고 `refinePolicyMoves`를 흉내내지 못해서, 새 스크립트를 추가했다.

**신규: [`scripts/run-katago-candidate-refine-experiment.py`](../../scripts/run-katago-candidate-refine-experiment.py)**

`KataGoJsonAnalysisQueryFactory.build()`/`KataGoJsonPositionAnalysisClient.refineJsonPolicyCandidates()`의 refine 쿼리(policy 상위 후보에 수를 하나 얹어 `analyzeTurns`를 한 수 미루고 `maxVisits=8`로 재검색)를 Python으로 그대로 포팅했다. 기존 `ENGINE_BEGINNER_VISITS_BENCHMARK.md`와 같은 3개 국면(P0 빈 보드, P1 초반 8수, P2 중반 20수)을 그대로 재사용해 과거 데이터와 비교 가능하게 했다.

실행(맥북 M-시리즈, Metal backend, `numSearchThreads=4`, `numAnalysisThreads=1`, `analysis_learning.cfg`, time cap 5000ms로 visits가 먼저 걸리게 설정):

```bash
python3 scripts/run-katago-candidate-refine-experiment.py \
  --visits 16,32,64 --refine-budgets 0,4,8,12 --time-cap-ms 5000 \
  --out docs/engine/measurements/engine-benchmark/candidate-refine-mac-20260817.md
```

원본 결과: `candidate-refine-mac-20260817.md` (36행 전체)

#### 발췌 — refine 0 vs 8 (현재 `Balanced`=4, `Deep`=12와 비교용으로 8도 추가 측정)

| Position | Visits | Scored (refine=0) | Scored (refine=8) | Baseline ms | Refine 8개 추가 비용 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 빈 9x9 | 16 | 5 | 13 | 0.5 | 334.2 |
| P0 빈 9x9 | 32 | 13 | 21 | 435.9 | 306.9 |
| P0 빈 9x9 | 64 | 13 | 21 | 248.7 | 4.7 |
| P1 초반 8수 | 16 | 2 | 10 | 170.5 | 343.5 |
| P1 초반 8수 | 32 | 3 | 11 | 126.5 | 37.3 |
| P1 초반 8수 | 64 | 4 | 12 | 233.2 | 4.9 |
| P2 중반 20수 | 16 | 3 | 11 | 170.0 | 350.0 |
| P2 중반 20수 | 32 | 4 | 12 | 150.0 | 15.7 |
| P2 중반 20수 | 64 | 5 | 13 | 234.9 | 13.8 |

#### 관찰 1 — refine은 정확히 요청한 만큼 후보를 늘린다

`refine_budget=4`면 항상 정확히 +4, `8`이면 +8, `12`면 +12다(예: P1-B16 `2→6→10→14`). `maxVisits`를 올리는 것과 달리 **국면에 좌우되지 않는 결정론적 증가**다. 이게 "레벨링이 원활하려면 후보가 몇 개 이상 필요하다"는 제품 요구사항에 정확히 맞는 성질이다.

#### 관찰 2 — refine 비용은 base visits에 크게 좌우된다 (B16에서 특히 비쌈)

B16에서는 refine 4~12개에 **310~350ms**가 붙는다(후보 1개당 대략 30~80ms). 반면 B32/B64에서는 대체로 **몇 ms~40ms**로 거의 공짜다(단, P0-B32의 refine=8/12처럼 정책 후보가 baseline 탐색과 안 겹치면 다시 300ms대로 튈 때도 있다). 원인은 NN 캐시 재사용으로 보인다 — 방문수가 큰 baseline 탐색(32/64)이 내부적으로 이미 policy 상위 후보 주변을 평가해 놓기 때문에, 그 다음 refine 쿼리는 이미 계산된 값을 캐시에서 재사용할 확률이 높다. 반대로 16 visits짜리 얕은 탐색은 refine이 요청하는 이웃 후보들을 거의 새로 계산해야 한다.

**이것이 "빠른 초급(B16)에는 후보 확장 레버를 얹지 말자"는 3-2절 권장을 실측으로 뒷받침한다.** 같은 개수의 추가 후보를 얻는 비용이 B16에서는 B32/B64보다 훨씬 크다 — 구조(GTP vs JSON)뿐 아니라 순수 latency 관점에서도 `빠른 초급`은 후보 확장에 불리하다.

#### 관찰 3 — B64가 항상 B32보다 후보가 많은 건 아니다

P0에서 B32/B64 모두 `refine=0` 기준 13개로 동일했다. `ENGINE_BEGINNER_VISITS_BENCHMARK.md`가 이미 지적했던 "B64가 항상 B32보다 후보 다양성이 좋은 건 아니다"가 오늘 다른 실행 조건(단일 프로세스 재사용, time cap 5000ms)에서도 재현됐다.

#### 실측의 한계 (다음 실험에서 보완할 점)

- 표본 1회(n=1)다. `run-katago-level-match.py`처럼 반복 샘플링하지 않았다 — latency 숫자는 경향 확인용이지 SLA 근거로 쓰면 안 된다.
- 맥북 Metal 기준이다. `ENGINE.md`가 보여주듯 폰 실기기(Eigen CPU)에서는 JSON이 GTP보다 훨씬 유리해지는 역전이 이미 확인된 바 있다(B32에서 JSON 3067ms vs GTP 7603ms) — refine 비용도 폰에서 다시 재보는 게 맞다. 기존 `scripts/run-katago-search-mode-benchmark.py`의 ADB `run-as` 경로를 재사용하면 앱과 100% 동일한 바이너리/모델로 측정할 수 있다.
- 후보 증가가 색상/승률 레벨링 품질까지 실제로 개선하는지는 확인하지 않았다(이번 실험은 "몇 개 늘릴 수 있고 얼마나 드는가"만 측정). 이건 5절의 다음 단계다.

### 5. 권장 방향 (확신 있는 부분은 결정, 나머지는 다음 실험으로 분리)

#### 결정해도 되는 것 (근거 충분, 확신 높음)

1. **`빠른 초급`(GTP, B16)에는 후보 확장 레버를 넣지 않는다.** 구조적으로(BestOnly면 애초에 후보 1개만 요청) + 실측으로(refine 비용이 B32/B64 대비 훨씬 큼) 이중으로 부적합하다. `빠른 초급`은 지금처럼 "느린 기기에서도 쾌적한 대국"이라는 원래 목적에 집중한다.
2. **후보 확장 노력은 `초급` 이상(JSON position analysis)에 집중한다.** 사용자의 기억("JSON 탐색이 후보 늘리기에 유리")이 정확했다 — 구조적으로도(policy 배열을 이미 받고 있음), 실측으로도(refine 비용이 훨씬 낮음) JSON 경로가 유리하다.
3. **레버는 `wideRootNoise`가 아니라 `refinePolicyMoves`다.** `wideRootNoise`는 최상위 후보 정확도를 깎는 대가로 다양성을 사는 옵션이라 "최고 단계는 항상 최상위 수" 불변식과 충돌한다. `refinePolicyMoves`는 이미 구현돼 있고 이 불변식을 구조적으로 해치지 않는다.

#### 다음 실험으로 넘기는 것 (지금 결정하기엔 이름)

1. **AI 착수 전용 refine 예산값.** `Balanced`(4)/`Deep`(12)은 원래 Top Moves/학습 분석용으로 설계된 값이라 AI 착수에 그대로 쓰기엔 검증이 안 됐다. 오늘 실측 기준으로는 `초급`(B32)에서 refine 4~8 정도가 latency 대비 이득이 커 보이지만, 이건 레벨링 품질(퍼센타일 구간이 실제로 색깔/승률 차이를 만드는지)까지 봐야 확정할 수 있다.
2. **`run-katago-level-match.py`에 refine 로직 이식 후 AI vs AI 매치 재실행.** 지금 이 스크립트는 `includePolicy=False`만 쓰고 refine을 모른다. `run-katago-candidate-refine-experiment.py`의 refine 쿼리 로직을 이식해서, "초급 7단계(refine 포함) vs 초급 7단계(refine 없음)" 또는 "초급 3단계(refine 포함, 퍼센타일 구간이 더 세밀해진 상태) vs 초급 3단계(현재)" 같은 승률/집차이 매트릭스를 50판 이상 돌려야 실제 레벨링 개선 여부를 판단할 수 있다.
3. **폰 실기기 재검증.** `make engine-search-mode-benchmark-phone` 경로로 ADB `run-as` 기반 실측을 폰에서도 반복한다.
4. 위 실험들이 만족스러우면 그때 `EngineAnalysisPolicy.aiMoveAnalysisLimitWith()`의 JSON 분기에 `refinePolicyMoves`를 (Top Moves용 `Balanced`/`Deep`과는 별개의 값으로) 명시적으로 채우는 앱 변경을 별도로 착수한다. **이 문서 범위에는 포함하지 않는다.**

### 6. 터미널 기반 엔진 실험 인프라 현황

사용자가 "앱을 매번 빌드하지 않고 터미널 파이썬으로 먼저 고도화"를 요청했는데, 실제로 이미 상당한 인프라가 갖춰져 있었다. 오늘 그 위에 실험 1개를 더 추가했다.

| 스크립트 | 역할 | Makefile 타겟 |
| --- | --- | --- |
| `scripts/run-katago-level-match.py` | 레벨 A vs 레벨 B 1:1 반복 대국, 승률/집차이 JSONL 로그 | (직접 실행) |
| `scripts/run-katago-level-matrix.py` | 여러 레벨 조합을 한 번에 매트릭스로 실행 | `make engine-level-benchmark` |
| `scripts/run-katago-device-benchmark.py` | 기기별 B16/B32/B64 순수 성능(맥북) | `make engine-device-benchmark` |
| `scripts/run-katago-search-mode-benchmark.py` | GTP fast vs JSON position analysis latency 비교, 맥북/폰(ADB `run-as`) 둘 다 지원 | `make engine-search-mode-benchmark[-phone]` |
| **`scripts/run-katago-candidate-refine-experiment.py`(신규)** | `refinePolicyMoves` 후보 확장 레버의 후보수 증가량과 latency 비용 측정 | 없음(직접 실행) |

전부 앱 바이너리 없이 로컬 Homebrew KataGo(`/opt/homebrew/bin/katago`)와 번들 모델/config를 직접 구동한다. 폰 실측이 필요할 때만 ADB `run-as`로 실제 설치된 앱의 KataGo 산출물을 그대로 실행한다(`ENGINE_API_CALL_POLICY.md` "원격 폰 엔진 벤치마크 표준" 절). 이 경로는 앱을 빌드/설치/재시작할 필요 없이 반복 실험할 수 있다 — 사용자가 요청한 방향과 정확히 일치하는 기존 관례다. 새 스크립트도 이 관례(같은 옵션 이름, 같은 국면 데이터, `docs/engine/measurements/engine-benchmark/`에 결과 저장)를 그대로 따랐다.

새 스크립트는 아직 Makefile 타겟이 없다 — 반복 사용 가치가 확인되면(5절의 다음 실험들이 이 스크립트를 계속 쓰게 되면) 타겟을 추가하는 게 자연스럽다. 지금은 보류한다.

### 참고 문서

- `ENGINE.md` — 이 문서가 더블체크한 원본 요약
- `ENGINE_API_CALL_POLICY.md` — 딥다이브, `candidateCount 의미`/`Visit의 의미와 탐색 원리` 절
- `ENGINE_BEGINNER_VISITS_BENCHMARK.md` — 이 문제의 최초 발견, 오늘 실험이 재사용한 P0/P1/P2 국면 출처(2026-08-17: `docs/archive/`에서 이 폴더로 이동 — 근거는 5절 하단 정책 각주 참고)
- `ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` — `Balanced` 프리셋이 AI 응수에 실수로 섞였던 과거 사고 기록
- `ENGINE_SEARCH_TREE_REUSE_REVIEW.md` — GTP tree reuse/JSON position-scoped 분석의 구조적 차이
- `candidate-refine-mac-20260817.md` — 오늘 실측 원본 데이터(36행)
- `scripts/run-katago-candidate-refine-experiment.py` — 오늘 추가한 실험 스크립트

---
