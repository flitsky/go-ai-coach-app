# 엔진 API 호출 정책

작성일: 2026-06-10

## 한줄 결론

대국 중 착수 판단과 학습 피드백은 모두 `TurnAnalysis`라는 같은 개념의 엔진 분석 결과를 사용한다. UI는 엔진을 직접 호출하지 않고, application 계층이 목적별 분석 예산을 정한 뒤 `EngineAdapter.analyze()` 뒤로 숨긴다.

## 현재 결정

1. AI 차례에는 항상 현재 진영의 플레이 레벨로 `TurnAnalysis`를 요청한다.
2. AI는 반환된 후보를 `MoveSelectionPolicy`로 레벨링해 착수한다.
3. 각 레벨 그룹의 최고 단계는 항상 엔진 후보 순위의 최상위 수를 둔다.
   - `빠른 초급 3단계`: B16 후보 중 최상위 수
   - `초급 7단계`: B32 후보 중 최상위 수
   - `중급 5단계`: B64 후보 중 최상위 수
   - `고급 5단계`: B160 후보 중 최상위 수
4. 사람 차례에도 백그라운드 `TurnAnalysis`를 요청한다.
5. `Top Moves` 토글이 켜져 있으면 같은 분석 snapshot에서 후보수를 보드에 표시한다.
6. `Top Moves` 토글이 꺼져 있으면 후보수는 표시하지 않지만, 사용자가 착수했을 때 방금 둔 수가 best/green/yellow/orange/red/unknown 중 무엇인지 판단하는 리뷰 데이터로 쓴다.
7. 분석 후보에 없는 착점은 실수로 단정하지 않고 `unknown` 또는 회색 계열로 취급한다.

## 현재 구현 범위

2026-06-10 현재 모바일 기본 구현은 성능을 우선한다.

- AI 응수: 레벨별 visits/time/candidate count로 `EngineAdapter.analyze()`를 호출한다.
- 사람 착수 리뷰: 사람 차례가 오면 fast best-1 분석을 백그라운드로 요청한다.
- Top Moves 표시: 사용자가 토글을 켜면 같은 fast best-1 snapshot을 보드에 표시한다.
- Broad study analysis: 전체 합법 착점, policy 후보, refine sweep, deep fallback은 기본 대국 경로에서 비활성이다.

즉 “항상 분석 snapshot을 만든다”는 정책은 유지하되, 폰 실시간 대국에서는 best-1 경량 snapshot만 자동 생성한다. 여러 색상의 후보 분포나 전체 착점 평가가 필요하면 별도 `StudyBroad` 예산으로 분리해서 켠다.

## 호출 목적별 예산

| 목적 | 현재 예산 | 사용처 |
| --- | --- | --- |
| `AiMoveSelection` | 플레이 레벨 visits/time/candidate count, `policy=false`, `refine=0` | AI 착수 선택 |
| `HumanMoveReview` | fast best-1, `policy=false`, `refine=0` | 사용자가 둔 수의 사후 평가 |
| `TopMovesDisplay` | fast best-1, `policy=false`, `refine=0` | 보드 위 후보수 표시 |
| `ScoreGraph` | 후보 1개 score estimate | Score / Win Rate 그래프 |
| `Benchmark` | 사용자 설정과 무관한 고정 B16/B32/B64 | 기기별 엔진 성능 측정 |

`Benchmark`는 사용자 Player Setup, Top Moves 토글, 현재 계가 규칙, analysis cache를 절대 참조하지 않는다.

## 계층 경계

- `shared`: `EngineAdapter`, `AnalysisLimit`, `CandidateMove`, `MoveAnalysisSnapshot`, `PlayLevel`, `MoveSelectionPolicy` 같은 순수 모델과 정책을 둔다.
- `application`: 현재 화면 상태와 사용자 설정을 보고 어떤 목적의 `TurnAnalysis`가 필요한지 결정한다.
- `engine-android`: GTP process, JSON analysis process, 향후 JNI/remote 구현을 `EngineAdapter` 뒤에 숨긴다.
- `ui`: 분석 요청을 직접 만들지 않는다. 버튼/토글 이벤트를 application 계층에 전달하고, 반환된 snapshot과 표시 DTO만 렌더링한다.

이 경계를 유지해야 process KataGo, JNI native engine, remote server로 바꿔도 상위 학습 UX가 흔들리지 않는다.

## 후보 순위와 평가값

- 후보 순위는 KataGo `moveInfos.order`를 우선한다.
- `pointLoss`는 후보 순서를 뒤집는 기준이 아니라 색상/숫자 annotation이다.
- `scoreLead`는 그래프와 진단용 값이다.
- `pointLoss`가 없는 후보는 표시나 리뷰에서 `unknown`으로 다룬다.
- `pointLoss`는 앱 내부에서 0 이상 손실값으로 유지한다. 보드 숫자는 KaTrain식으로 `-pointLoss`를 표시할 수 있지만, 내부 모델에는 음수 이득 의미를 섞지 않는다.

자세한 값 해석은 `docs/TOP_MOVES_VALUE_GUIDE.md`와 `docs/ENGINE_ANALYSIS_CONSISTENCY_REVIEW.md`를 따른다.

## 오래된 문서 처리

다음 문서는 당시 분석 근거로는 유효하지만, 현재 기본 호출 정책과 다를 수 있어 아카이브로 이동했다.

- `docs/archive/2026-06-engine-policy-superseded/GREEN_SPOT_HINT_DECISION.md`
- `docs/archive/2026-06-engine-policy-superseded/KATRAIN_TOP_MOVES_ANALYSIS.md`

새 구현이나 의사결정은 이 문서를 우선 기준으로 삼는다. 아카이브 문서는 KaTrain 분석 히스토리와 broad study analysis 후보를 볼 때만 참고한다.

## 다음 리팩토링 방향

1. `GoCoachApp.kt`의 coroutine orchestration을 controller/service로 옮긴다.
2. `TurnAnalysis` 요청, cache hit/miss, snapshot apply를 독립 application service로 분리한다.
3. `TopMovesDisplay`와 `HumanMoveReview`를 같은 snapshot에서 파생하되 UI 표시 정책만 분리한다.
4. broad study analysis를 별도 메뉴/모드로 만들 때도 기존 `MoveAnalysisSnapshot`에 merge하는 방식으로 확장한다.
5. 기기 벤치마크 결과를 바탕으로 fast best-1 분석의 time cap을 기기별로 조정한다.
