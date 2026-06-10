# AI 엔진 설정 정리

작성일: 2026-05-31
최근 갱신: 2026-06-10

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

현재 Android 화면은 `Engine` raw 패널 대신 `Player Setup`에서 흑/백 진영별 역할과 AI 레벨을 설정한다.

- `빠른 초급`: B16 / 250ms
- `초급`: B32 / 500ms
- `중급`: B64 / 500ms
- `고급`: B160 / 1000ms

각 그룹 안의 단계는 같은 엔진 요청 안에서 후보 선택 정책만 바꾼다. 예를 들어 `초급 1단계`와 `초급 7단계`는 모두 B32 / 500ms 요청을 사용하고, 1단계는 낮은 후보 구간을 섞으며 7단계는 최상위 후보만 고른다.

AI 응수는 대국 속도를 위해 항상 경량 분석 요청을 사용한다.

- `includePolicy=false`
- `refinePolicyMoves=0`
- `minVisitsPerCandidate=0`
- `minTimeMillis=null`
- 예: `중급 5단계`는 `64 visits / 500ms / BestOnly`로 착수한다.

`Top Moves`는 대국 중 수동 힌트 표시 기능이다.

- 첫 설치 기본값은 꺼짐이다.
- 사람 차례가 돌아와도 자동 pre-move analysis cache를 만들지 않는다.
- 사용자가 `Top Moves`를 누른 경우에만 현재 국면을 분석한다.
- 현재 모바일 기본 요청은 best-1이다.
  - `candidateCount=1`
  - 현재 Player Setup의 visits/time을 그대로 사용
  - difficulty 한 단계 승격 없음
  - `includePolicy=false`
  - `refinePolicyMoves=0`
  - `minVisitsPerCandidate=0`
  - `minTimeMillis=null`
  - deep fallback 없음
- 같은 국면, 규칙, 차례, preset, 분석 예산의 메모리 cache가 있으면 재호출하지 않고 표시만 복원한다.

KaTrain식 broad analysis는 향후 학습/복기 모드로 분리한다.

- 전체 합법 착점 목표 후보수
- JSON `policy` 보존
- 후보별 refine sweep
- 자동 pre-move analysis
- scored 후보 부족 시 Full Analysis fallback

위 항목들은 학습 가치는 크지만 폰 실시간 대국에서는 호출 오버헤드가 커서 현재 기본 경로에서는 사용하지 않는다.

## KataGo process adapter 매핑

`KataGoProcessEngineAdapter`는 대국용 GTP process와 분석용 JSON analysis process를 둘 다 지원한다. 현재 모바일 기본 경량 분석은 GTP fast path를 우선한다.

대국/착수 생성:

- process 시작 시 `-override-config maxVisits=<visits>`를 포함한다.
- process 시작 시 `numSearchThreads=1`을 적용한다.
- 설정 변경 시 GTP 명령으로 `kata-set-param maxVisits <visits>`를 보낸다.
- time limit이 있으면 `kata-set-param maxTime <seconds>`를 보낸다.

경량 분석:

- 조건: `includePolicy=false`이고 `refinePolicyMoves=0`
- 별도 JSON analysis process를 시작하지 않는다.
- 대국용 GTP process에 `kata-set-param maxVisits`, `kata-set-param maxTime`을 적용한 뒤 `kata-search_analyze <color> <centiseconds>`를 호출한다.
- 응답의 `info move ... order ...` 후보를 `CandidateMove`로 변환한다.
- 호출 후 기존 AI 대국용 `EngineProfile.analysisLimit`로 `maxVisits/maxTime`을 되돌린다.

Broad study analysis:

- 조건: `includePolicy=true` 또는 `refinePolicyMoves>0`
- `analysis_learning.cfg`가 있으면 `katago analysis` process를 별도로 시작한다.
- 분석용 process는 대국용 GTP process와 분리되어 `numAnalysisThreads=1`, `numSearchThreads=4`를 사용한다.
- JSON query에는 현재 앱 수순 전체, rules, komi, board size, `maxVisits`, `overrideSettings.maxTime`, `includePolicy`가 들어간다.
- JSON 응답의 `moveInfos`, `policy`, refine 결과를 `CandidateMove`로 변환한다.
- seed/config가 누락되었거나 JSON analysis가 실패하면 기존 GTP `kata-search_analyze`로 fallback한다.

따라서 현재 앱의 최저 KataGo process 설정은 대략 다음과 같다.

```text
maxVisits=16
maxTime=0.25
numSearchThreads=1
candidateCount=1
includePolicy=false
refinePolicyMoves=0
```

주의: `candidateCount`는 “검색 방문수”가 아니라 “후보 목표 개수”다. 현재 모바일 기본 Top Moves는 후보 목표 개수를 1로 제한한다. 전체 합법 착점 또는 다중 후보 coverage는 broad study analysis가 다시 켜질 때만 사용한다.

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
