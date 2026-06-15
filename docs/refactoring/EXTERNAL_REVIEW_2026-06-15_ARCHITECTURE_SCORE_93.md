# 외부 개발자 검토 의견 - 2026-06-15 아키텍처 실행성 재평가

작성일: 2026-06-15  
목적: 외부 개발자가 2026-06-15 현재 리팩토링 이후 구조를 다시 평가한 내용을 원문 취지에 가깝게 보존한다. 내부 판단과 실행 분류는 별도 문서에서 다룬다.

## 외부 검토자가 수행한 확인

- `git log -n 30 --oneline`
- `git log --oneline | wc -l`
- `wc -l app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt`
- `find app-android/src/main/java -name "*.kt" | wc -l`
- `find app-android/src/test/java -name "*.kt" | wc -l`
- `find shared/src -name "*.kt" | wc -l`
- `find engine-android/src -name "*.kt" | wc -l`
- `git diff 929e71c 9fef835 --stat`
- `find app-android/src/test/java -name "*Test.kt" | wc -l`
- `find shared/src/commonTest -name "*Test.kt" | wc -l`
- `find engine-android/src/test -name "*Test.kt" | wc -l`
- `NEXT_REFACTORING_WORKLIST_2026-06-14.md`
- `ORCHESTRATION_SPLIT_AND_KMP_MAP_2026-06-15.md`
- `UI_STATE_HOLDER_BOUNDARY_2026-06-15.md`
- `INTERNAL_REVIEW_OF_EXTERNAL_FEEDBACK_2026-06-15.md`
- `GameSessionUiStateHolderApplication.kt`
- `EngineSessionLifecycleApplication.kt`
- `LayeringContractTest.kt`
- `EngineOperationScope.kt`
- application 파일 크기 상위 목록
- KMP 이동 스파이크 문서
- `GoCoachApp.kt` import 수와 package별 import 수
- `DIAGNOSTIC_EVENT_SCHEMA.md`

## 외부 종합 평가

종합 점수: **93 / 100**

이전 평가 이후 18건의 커밋으로 대규모 정책 분리가 이루어졌지만, 외부 검토자는 역설적 상황이 발생했다고 평가했다.

| 지표 | 이전 또는 기준 | 현재 외부 보고 | 외부 판정 |
| --- | ---: | ---: | --- |
| `GoCoachApp.kt` 줄 수 | 2,084 | 2,196 | 악화 |
| `GoCoachApp.kt` import 수 | 미기재 | 229 | 과도 |
| application 파일 수 | 37 | 49 | 밀집 |
| `LayeringContractTest` 범위 | 3개 | 5개 | 개선 |
| completion plan 패턴 적용 | 일부 | 거의 전체 | 개선 |

## 핵심 지적

외부 검토의 가장 냉정한 지적은 **"설계했으나 실행하지 않은 것"이 누적되고 있다**는 점이다.

1. `GameSessionUiStateHolder`
   - 파일은 존재하지만 `GoCoachApp.kt` 물리 분리는 아직 충분히 수행되지 않았다.

2. KMP 이동 후보 분류
   - 20개 안팎의 후보를 분류했지만 실제 KMP 물리 이동은 아직 0건이다.

3. 외부 수집 port
   - diagnostic 외부 수집 port 설계는 있으나 adapter 구현은 아직 없다.

4. application 하위 패키지
   - 필요성을 인지했지만 `application/score`, `application/autoai`, `application/engine` 같은 물리 package 도입은 아직 없다.

## 외부 제안 최우선 과제

1. **`GoCoachApp.kt`를 Controller / StateHolder / EffectLauncher로 3분할**
   - 기대 효과: +5점
   - 목표: UI 파일의 coroutine scheduling과 orchestration 책임을 더 이상 직접 소유하지 않게 한다.

2. **application 하위 패키지 도입**
   - 기대 효과: +2.5점
   - 예시: `score/`, `autoai/`, `engine/`, `diagnostic/`, `topmoves/`

3. **KMP 물리 이동 1차 실행**
   - 기대 효과: +1점
   - 최소 2개 파일을 실제로 이동해 문서상의 후보 분류를 실행으로 전환한다.

## 외부 검토의 취지

외부 검토자는 현재 구조가 나쁘다고 평가한 것이 아니라, 문서화와 정책 분리의 양이 늘어난 만큼 실제 물리 분리, package 정리, runtime adapter 구현으로 전환해야 할 시점이라고 판단했다.

핵심 메시지는 다음과 같다.

- 추상 정책 분리는 상당히 진전됐다.
- 그러나 UI 엔트리 파일과 application 루트 package는 아직 너무 큰 허브다.
- 다음 리팩토링은 새로운 정책 타입 추가보다 물리적 구조 변경과 실행 가능한 증명을 우선해야 한다.
