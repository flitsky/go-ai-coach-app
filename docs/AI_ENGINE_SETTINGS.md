# AI 엔진 설정 정리

작성일: 2026-05-31

## 결론

현재 앱에 정의되고 UI로 조정 가능한 설정 중 가장 낮은 설정은 `Beginner`다.

- 기본 difficulty: `DifficultyProfile.Beginner`
- 기본 visits: `16`
- 기본 time limit: `250ms`
- 기본 hint count: `1`
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

`Hints` 패널은 후보수 표시를 조정한다.

- `Hints` 토글
  - 켜져 있으면 사람 차례가 돌아온 시점에 자동으로 후보수를 분석한다.
  - 꺼져 있어도 `Hint` 버튼을 누르면 수동으로 현재 후보수를 볼 수 있다.
- `N`
  - 한 번에 표시할 후보수 개수다.
  - 현재 UI 범위는 `1-5`다.
  - `AnalysisLimit.candidateCount`로 엔진 adapter에 전달된다.
  - `Beginner`처럼 낮은 visits/time 설정에서는 KataGo 검색이 실제로 방문한 후보수가 N보다 적을 수 있다.
  - 이 경우 앱은 부족한 spot을 `kata-raw-nn` 정책 우선순위 후보로 보강한다.
  - 검색 후보는 `WR`, `score`, `visits`를 가질 수 있고, 정책 보강 후보는 `prior` 중심으로 표시된다.

## KataGo process adapter 매핑

`KataGoProcessEngineAdapter`는 설정을 다음 방식으로 엔진에 전달한다.

- process 시작 시 `-override-config maxVisits=<visits>`를 포함한다.
- process 시작 시 `numSearchThreads=1`을 적용한다.
- 설정 변경 시 GTP 명령으로 `kata-set-param maxVisits <visits>`를 보낸다.
- time limit이 있으면 `kata-set-param maxTime <seconds>`를 보낸다.

따라서 현재 앱의 최저 KataGo process 설정은 대략 다음과 같다.

```text
maxVisits=16
maxTime=0.25
numSearchThreads=1
candidateCount=1
```

주의: `candidateCount`는 “검색 방문수”가 아니라 “표시 목표 개수”다. 낮은 visits/time에서는 검색 후보가 적게 나올 수 있으므로, 앱은 표시 개수를 맞추기 위해 raw NN policy fallback을 사용한다.

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
