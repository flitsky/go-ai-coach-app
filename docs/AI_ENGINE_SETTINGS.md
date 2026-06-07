# AI 엔진 설정 정리

작성일: 2026-05-31

## 결론

현재 앱에 정의되고 UI로 조정 가능한 설정 중 가장 낮은 설정은 `Beginner`다.

- 기본 difficulty: `DifficultyProfile.Beginner`
- 기본 visits: `16`
- 기본 time limit: `250ms`
- UI visits 최저값: `16`
- process adapter startup thread: `numSearchThreads=1`

다만 이것은 “현재 앱이 제공하는 최저 설정”이라는 의미다. KataGo 자체는 더 낮은 visits 값으로도 실행할 수 있으므로, 실제 첫 릴리즈 플레이테스트에서 AI가 여전히 너무 강하면 `VeryEasy` 같은 더 낮은 profile을 추가하는 것이 맞다.

## 현재 profile

`shared`의 `DifficultyProfile` 기준:

| Profile | Visits | Time limit | 현재 용도 |
| --- | ---: | ---: | --- |
| Beginner | 16 | 250ms | 현재 기본값이자 앱 UI의 최저 설정 |
| Casual | 64 | 500ms | 가벼운 일반 대국 |
| Intermediate | 160 | 1,000ms | 중간 강도 |
| Strong | 400 | 2,000ms | 더 강한 분석/응수 |
| Full Analysis | 1,000 | 5,000ms | 느리지만 더 깊은 분석 |

`EngineProfile()`의 기본값은 `DifficultyProfile.Beginner`와 그 기본 `AnalysisLimit`을 사용한다. Android 화면도 시작 시 이 기본 profile로 엔진을 초기화한다.

## UI 동작

현재 Android 화면의 `Engine` 패널은 두 축을 조정한다.

- difficulty `-` / `+`
  - `Beginner -> Casual -> Intermediate -> Strong -> Full Analysis`
  - profile 변경 시 visits/time limit이 해당 difficulty 기본값으로 같이 바뀐다.
- visits `-` / `+`
  - 현재 선택 가능한 값은 `16, 64, 160, 400, 1000`
  - visits만 바꾸며, time limit은 현재 profile 값을 유지한다.

설정 변경은 엔진이 준비되어 있고 AI가 생각 중이 아닐 때만 허용한다.

`Top Moves`는 착수 후보 분석 표시를 조정한다.

- `Top Moves` 토글
  - 켜져 있으면 사람 차례가 돌아온 시점에 준비된 후보수를 보드에 표시한다.
  - 꺼져 있어도 착수 평가를 위한 pre-move analysis cache는 백그라운드에서 계속 준비한다.
- 후보 개수
  - 현재 UI에서 사용자가 직접 N을 입력하지 않는다.
- 앱은 현재 합법 착점 수와 내부 최대값 중 작은 값을 `AnalysisLimit.candidateCount`로 엔진 adapter에 전달한다.
- 9x9 POC에서는 전체 합법 착점을 목표 후보수로 사용한다.
- 앱은 엔진 응답 후보와 현재 합법 착점을 합쳐 `MoveAnalysisSnapshot`을 만든다. snapshot에는 `Scored`, `PolicyOnly`, `LegalOnly` coverage가 함께 보존된다.
- `analysis_learning.cfg`가 준비된 KataGo local process에서는 JSON analysis protocol을 우선 사용한다.
  - `moveInfos` 후보는 `WR`, `score`, `visits`, `prior`, `loss`를 가진다.
  - 보드에는 `pointLoss`가 있는 후보만 표시한다.
- JSON analysis config가 없거나 JSON analysis 프로세스가 실패하면 기존 GTP `kata-search_analyze` 경로로 fallback한다.
  - GTP fallback에서는 낮은 visits/time 설정에서 실제 score 후보가 목표 후보수보다 적을 수 있다.
  - 이 경우 앱은 부족한 후보 목록을 `kata-raw-nn` 정책 우선순위와 합법 착점 fallback으로 보강하되, 이 fallback 후보는 로그 전용이다.

Top Moves 분석은 대국 AI 응수 설정과 분리한다.

- AI 응수는 현재 `EngineProfile.analysisLimit`를 사용한다.
- Top Moves와 착수 리뷰용 pre-move analysis는 현재 difficulty보다 한 단계 높은 difficulty의 기본 visits/time을 사용한다.
  - 예: 대국 AI가 `Beginner`이면 Top Moves는 최소 `Casual` 기본값인 `64 visits / 500ms`를 사용한다.
  - 수동으로 visits/time을 이미 더 높게 올려둔 경우에는 Top Moves가 약해지지 않도록 현재 값과 한 단계 위 기본값 중 큰 값을 사용한다.
- KataGo process adapter는 후보수 목표가 커질 때 여기에 추가로 최소 `후보수 * 20 visits`, `2000ms`까지 Top Moves 분석을 일시 상향한다.
- 자동 cache에 점수 손실이 있는 후보가 5개 미만이면, 사용자가 `Top Moves`를 눌렀을 때 수동 deep analysis를 1회 실행한다. 이때는 `Full Analysis` 기본값인 `1000 visits / 5000ms` 이상을 사용한다.
- GTP fallback을 사용한 Top Moves 검색 후에는 기존 AI 대국용 `EngineProfile.analysisLimit`로 `maxVisits/maxTime`을 되돌린다. JSON analysis 경로는 별도 analysis process에 전체 수순을 query로 보내므로 GTP 대국 process의 응수 강도 설정을 바꾸지 않는다.

## KataGo process adapter 매핑

`KataGoProcessEngineAdapter`는 대국용 GTP process와 분석용 JSON analysis process를 분리한다.

대국/착수 생성:

- process 시작 시 `-override-config maxVisits=<visits>`를 포함한다.
- process 시작 시 `numSearchThreads=1`을 적용한다.
- 설정 변경 시 GTP 명령으로 `kata-set-param maxVisits <visits>`를 보낸다.
- time limit이 있으면 `kata-set-param maxTime <seconds>`를 보낸다.

Top Moves:

- `analysis_learning.cfg`가 있으면 `katago analysis` process를 별도로 시작한다.
- 분석용 process는 대국용 GTP process와 분리되어 `numAnalysisThreads=1`, `numSearchThreads=4`를 사용한다.
- query에는 현재 앱 수순 전체, rules, komi, board size, `maxVisits`, `overrideSettings.maxTime`, `includePolicy=true`가 들어간다.
- JSON 응답의 `moveInfos`를 `CandidateMove`로 변환한다.
- seed/config가 누락되었거나 JSON analysis가 실패하면 기존 GTP `kata-search_analyze`로 fallback한다.

따라서 현재 앱의 최저 KataGo process 설정은 대략 다음과 같다.

```text
maxVisits=16
maxTime=0.25
numSearchThreads=1
candidateCount=현재 9x9 합법 착점 수, 빈 보드 예시 81
```

주의: `candidateCount`는 “검색 방문수”가 아니라 “후보 목표 개수”다. 현재 9x9 Top Moves에서는 합법 착점 수까지 목표로 올릴 수 있지만, 실제 `pointLoss`가 채워지는 후보 수는 KataGo 응답 품질과 분석 예산에 따라 달라진다. GTP fallback에서는 낮은 visits/time에서 검색 후보가 적게 나올 수 있으므로, 앱은 로그 completeness를 위해 raw NN policy/legal fallback을 보관한다. 다만 점수 손실이 없는 fallback은 보드에 그리지 않는다.

## Stub adapter 주의점

Stub adapter의 difficulty는 실제 강함을 의미하지 않는다.

Stub은 엔진 통신 구조와 UI 흐름 검증용이며, profile별로 deterministic 후보 좌표 우선순위만 일부 다르게 사용한다. 실제 대국 강도 평가는 KataGo process adapter 또는 이후 JNI/remote adapter에서만 의미가 있다.

## 현재 판단

현재 설정은 “앱이 제공하는 최저 설정”으로는 맞다.

하지만 9x9 초급자 대국 관점에서는 `16 visits / 250ms`도 충분히 강하게 느껴질 수 있다. 첫 마켓 릴리즈 전에는 다음 중 하나를 추가 검토한다.

1. `VeryEasy`: `visits=4`, `time=100ms`
2. `Beginner`를 `visits=8`, `time=150ms`로 낮추고 현재 Beginner는 `Casual`에 가깝게 재배치
3. 초급자 모드에서 엔진 후보수 중 항상 최상위가 아닌 낮은 순위 후보를 선택하는 handicap 정책 추가

3번은 “엔진 자체 설정”이 아니라 “응수 선택 정책”이므로, 도메인/엔진 경계를 유지하려면 별도 `MoveSelectionPolicy` 같은 application layer 정책으로 두는 것이 좋다.
