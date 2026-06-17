# Engine

작성일: 2026-06-17
성격: 엔진 특성·탐색 방식·레벨 정책을 한 화면에서 보기 위한 상위 요약 문서. 상세 운영 규칙과 근거는 `ENGINE_API_CALL_POLICY.md`(648줄)에 그대로 있으며, 이 문서는 그것을 압축한 진입점이다.

## 한 줄 결론

대국 중 모든 판단(AI 착수, 사람 착수 리뷰, Top Moves 표시)은 같은 개념의 `TurnAnalysis`를 쓴다. UI는 엔진을 직접 호출하지 않고, [ARCHITECTURE.md](./ARCHITECTURE.md)의 4계층(Middleware)이 목적별 분석 예산을 정해 `EngineSessionClient.analyzePosition(state, limit, searchMode)` 뒤로 숨긴다.

## 엔진 탐색 방식 2가지

엔진을 호출하는 방식은 `EngineSearchMode`로 정책화되어 있다(`shared/EngineSearchMode.kt`).

| 모드 | 정체 | 장점 | 비용 |
| --- | --- | --- | --- |
| `GtpStatefulFast` | 기존 대국 GTP 프로세스의 `kata-search_analyze`를 그대로 쓰는 stateful 빠른 경로 | search tree 재사용으로 응답이 빠름, 체감 좋음 | position-scoped가 아니라서 원격 서버 호환이 어렵고, 여러 후보를 안정적으로 받기 어려움 |
| `JsonPositionAnalysis` | 매 요청마다 `moves`/`maxVisits`/`overrideSettings.maxTime`을 명시하는 KataGo Analysis Engine JSON query | 후보군을 안정적으로 받음, 원격 서버와 호환되는 요청 형태, 캐시하기 좋음 | GTP tree reuse를 못 쓰므로 visits가 늘수록 GTP 대비 상대적으로 느려질 수 있음 |

### 실측: B16은 GTP가 빠르고, B32/B64는 역전된다

2026-06-13 맥북/폰 벤치마크(`refactoring/ENGINE_SEARCH_MODE_ROADMAP_2026-06-13.md`)에서 같은 포지션으로 두 모드를 비교했다.

**폰 실기기 (`SM-S908N`, Eigen CPU backend, time cap 10000ms)**

| Visits | GTP 평균 | JSON 평균 | JSON/GTP |
| ---: | ---: | ---: | ---: |
| 16 (B16) | 3815ms | 4760ms | 1.25x (GTP가 빠름) |
| 32 (B32) | 7603ms | 3067ms | 0.40x (JSON이 훨씬 빠름) |
| 64 (B64) | 10168ms | 4995ms | 0.49x (JSON이 빠름) |

즉 B16에서는 GTP fast가 더 빠르지만, B32/B64로 올라가면 GTP가 오히려 더 느려지고 JSON position analysis가 역전해서 더 빨라진다. 이 결과 때문에 레벨별로 모드를 다르게 매핑한다(아래 표).

## 레벨별 매핑 — 미들웨어가 자동 선택

게임 도메인(5계층)에서 AI 캐릭터/레벨을 정할 때, 미들웨어(4계층)가 그 레벨에 맞는 탐색 모드를 자동으로 고른다. 사용자는 모드를 직접 선택하지 않는다.

코드: `shared/EngineAnalysisPolicy.kt`의 `PlayLevelSetting.aiMoveSearchMode()`

```kotlin
fun PlayLevelSetting.aiMoveSearchMode(): EngineSearchMode =
    if (group == PlayLevelGroup.FastBeginner) GtpStatefulFast else JsonPositionAnalysis
```

| 레벨 그룹 | 탐색 모드 | visits | 기본 time cap | 후보 상한 |
| --- | --- | ---: | ---: | ---: |
| 빠른 초급 (1~3단계) | `GtpStatefulFast` | 16 | 1000ms (B16) | 8 |
| 초급 (1~7단계) | `JsonPositionAnalysis` | 32 | 2000ms (B32) | 16 |
| 중급 (1~5단계) | `JsonPositionAnalysis` | 64 | 3000ms (B64) | 20 |
| 고급 (1~5단계) | `JsonPositionAnalysis` | 160 | 1000ms | 24 |

`빠른 초급`은 느린 기기에서도 쾌적한 대국 체감을 우선하는 모드이고, `초급` 이상은 후보군 안정성과 레벨링 정확도를 우선하는 모드다.

## `candidateCount`가 뜻하는 것

`candidateCount`는 "엔진이 그 개수만큼 깊게 평가하라"는 강제값이 아니라 "앱이 응답 후보를 최대 몇 개까지 파싱/표시/레벨링에 쓸지"의 상한이다. 실제 scored 후보 수는 `maxVisits`/`maxTime`이 얼마나 채워졌는지에 달려 있다. 자세한 운영표(요청 10개 중 실제 1개/2개/3개/4개 이상일 때 레벨링 방식)는 `ENGINE_API_CALL_POLICY.md` → `candidateCount 의미` 섹션을 따른다.

**Top Moves 보드 표시**: `application/analysis/AnalysisSession.kt`의 `LightweightTopMoveCandidateCount = 5`. 2026-06-17 기준 최적수 보드 표시는 최대 5개 후보를 요청하며, `GoBoard.kt`의 `drawCandidateMoves()`가 1순위는 큰 원, 나머지는 작은 원으로 그린다. 이 분석은 명시적 search mode를 넘기지 않으므로 `EngineSessionClient.analyzePosition()`의 기본값인 `GtpStatefulFast`를 사용한다 — 즉 현재 대국 상대 AI가 `빠른 초급`이면 Top Moves도 같은 B16 계열 경량 분석이다.

## Visit과 강도의 관계 — 핵심만

- `visits`를 늘리면 평균적으로 더 좋은 수를 찾을 가능성은 높아지지만, 개선폭은 sublinear하게 줄어든다. B16→B32가 항상 "2배 강해짐"을 뜻하지 않는다.
- 낮은 visits에서는 신경망 policy prior가 높은 후보 위주로만 검색되어, prior가 낮은 후보는 의미 있게 평가되지 않을 수 있다.
- AI 착수 순위는 KataGo가 매긴 `moveInfos.order`를 신뢰한다. `pointLoss`는 순위를 뒤집는 기준이 아니라 표시/학습용 annotation이다.
- 전체 이론적 배경(MCTS exploration, `wideRootNoise`, playouts vs visits 차이)은 `ENGINE_API_CALL_POLICY.md` → `Visit의 의미와 탐색 원리` 섹션에 있다.

## AI vs AI 자동대국의 격리

`GtpStatefulFast`(빠른 초급)는 한 프로세스의 search tree를 공유하므로, AI vs AI 자동대국에서는 착수 분석 직전 `clearSearchCache()`를 호출해 한쪽 진영의 깊은 탐색이 다른 진영 레벨에 섞이지 않게 막는다. `JsonPositionAnalysis`(초급 이상)는 매 요청이 position-scoped라 이 격리가 필요 없다.

## 더 깊은 문서

| 문서 | 다룰 내용 |
| --- | --- |
| `ENGINE_API_CALL_POLICY.md` | 호출 우선순위, candidateCount 운영표, visit 이론, 종국 GTP cap 정책, 진단 로그 수집, 캐시 품질/origin 전체 규칙 |
| `engine-research/ENGINE_LEVEL_STRENGTH_REVIEW_2026-06-10.md` | B16/B32/B64 실제 대국 강도 검증 결과 |
| `engine-research/ENGINE_SEARCH_TREE_REUSE_REVIEW.md` | search tree 재사용/격리 정책 상세 검토 |
| `refactoring/ENGINE_SEARCH_MODE_ROADMAP_2026-06-13.md` | 두 모드 도입 로드맵, 맥북/폰 벤치마크 원본 데이터 |
| `engine-benchmark-logs/`, `engine-match-logs/` | raw 벤치마크/대국 로그 |
