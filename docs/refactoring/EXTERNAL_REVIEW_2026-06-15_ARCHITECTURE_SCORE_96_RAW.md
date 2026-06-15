# 외부 개발자 2차 아키텍처 평가 원문 - 2026-06-15

작성일: 2026-06-15  
출처: 사용자 제공 첨부 `pasted-text.txt`  
원본 파일: `/Users/ryan9kim/.codex/attachments/202e8b8a-1941-4a69-a702-9248c9b9bab1/pasted-text.txt`  
성격: 외부 개발자가 ext.11 직후 프로젝트 구조를 재검토한 원천 데이터

## 원문

```text
Ran command: `git log --oneline -25`
Ran command: `git log --oneline | wc -l && echo "---GoCoachApp---" && wc -l app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---app-android main---" && find app-android/src/main/java -name "*.kt" | wc -l && echo "---app-android test---" && find app-android/src/test/java -name "*.kt" | wc -l && echo "---shared---" && find shared/src -name "*.kt" | wc -l && echo "---engine---" && find engine-android/src -name "*.kt" | wc -l`
Listed directory application
Ran command: `find app-android/src/main/java/com/worksoc/goaicoach/application -name "*.kt" | wc -l && echo "---by subpackage---" && for d in app-android/src/main/java/com/worksoc/goaicoach/application/*/; do echo "$(basename $d): $(find $d -name '*.kt' | wc -l)"; done`
Ran command: `grep -c "^import " app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---application imports---" && grep -c "^import com.worksoc.goaicoach.application" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---withContext---" && grep -c "withContext" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---scope.launch---" && grep -c "scope\.launch" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---LaunchedEffect---" && grep -c "LaunchedEffect" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt`
Ran command: `grep -c "scope\.launch" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt; echo "---LaunchedEffect---"; grep -c "LaunchedEffect" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt; echo "---runCatching---"; grep -c "runCatching" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt`
Ran command: `grep -c "withContext\|scope\.launch\|runCatching\|GlobalScope\|CoroutineScope(" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt`
Ran command: `grep -n "withContext\|scope\.launch\|runCatching\|GlobalScope\|CoroutineScope(" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt`
Ran command: `find shared/src -name "*.kt" | sort && echo "---new shared files---" && ls shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/`
Ran command: `cat shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/diagnostic/DiagnosticEventModel.kt | head -30 && echo "---" && cat shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/engine/EngineOperationPolicy.kt | head -30`
Ran command: `git diff 9fef835 b4fd879 --stat | head -60`
Ran command: `git diff 9fef835 b4fd879 --stat | tail -30`
Ran command: `ls app-android/src/main/java/com/worksoc/goaicoach/application/engine/ && echo "---session---" && ls app-android/src/main/java/com/worksoc/goaicoach/application/session/ && echo "---diagnostic---" && ls app-android/src/main/java/com/worksoc/goaicoach/application/diagnostic/`
Viewed EngineSessionClient.kt:1-50
Ran command: `cat app-android/src/test/java/com/worksoc/goaicoach/architecture/LayeringContractTest.kt | wc -l && echo "---match test---" && grep -l "matchDoes\|match.*DoesNot\|match.*layering" app-android/src/test/java/com/worksoc/goaicoach/architecture/LayeringContractTest.kt`
Ran command: `grep "fun " app-android/src/test/java/com/worksoc/goaicoach/architecture/LayeringContractTest.kt`
Viewed LayeringContractTest.kt:155-233
Ran command: `cat app-android/src/main/java/com/worksoc/goaicoach/application/diagnostic/LocalFileDiagnosticEventExternalSink.kt`
Ran command: `ls app-android/src/main/java/com/worksoc/goaicoach/application/savedgame/ && echo "---" && wc -l app-android/src/main/java/com/worksoc/goaicoach/application/savedgame/SavedGameRestoreApplication.kt`
Ran command: `cat app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineClock.kt && echo "---" && cat app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineEffectLauncherApplication.kt`
Ran command: `grep -c "^import " app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---application imports---" && grep -c "^import com.worksoc.goaicoach.application" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt && echo "---shared imports---" && grep -c "^import com.worksoc.goaicoach.shared" app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt`
Ran command: `find shared/src/commonTest -name "*.kt" | wc -l && echo "---test files list---" && find shared/src/commonTest -name "*.kt" -exec basename {} \; | sort`
Ran command: `cat docs/refactoring/INTERNAL_ARCHITECT_REVIEW_OF_SCORE_93_FEEDBACK_2026-06-15.md | head -80`
Ran command: `wc -l app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineDeviceBenchmarkApplication.kt app-android/src/main/java/com/worksoc/goaicoach/application/topmoves/TopMovesApplication.kt app-android/src/main/java/com/worksoc/goaicoach/application/score/ScoreDisplayApplication.kt app-android/src/main/java/com/worksoc/goaicoach/application/runtime/RuntimeEventApplication.kt app-android/src/main/java/com/worksoc/goaicoach/application/engine/EngineSessionClient.kt`
Ran command: `find app-android/src/main/java/com/worksoc/goaicoach -maxdepth 1 -name "*.kt" | sort && echo "---match pkg---" && ls app-android/src/main/java/com/worksoc/goaicoach/match/ | wc -l && echo "---middleware pkg---" && ls app-android/src/main/java/com/worksoc/goaicoach/middleware/ | wc -l`
Ran command: `cat docs/refactoring/INTERNAL_ARCHITECT_REVIEW_OF_SCORE_93_FEEDBACK_2026-06-15.md | wc -l && echo "---" && tail -60 docs/refactoring/INTERNAL_ARCHITECT_REVIEW_OF_SCORE_93_FEEDBACK_2026-06-15.md`

이제 데이터를 충분히 수집했습니다. 평가 리포트를 작성하겠습니다.

Edited project_evaluation_report.md

2차 평가 리포트를 [project_evaluation_report.md](file:///Users/ryan9kim/.gemini/antigravity/brain/af48d751-29dd-42f0-af5e-2e71666bf012/project_evaluation_report.md)에 작성했습니다.

---

## 2차 종합 판정: **96 / 100** (+3점 상승)

이전 93점 평가의 6개 지적 사항을 **전부 방향성 있게 실행**한 결과입니다.

### 핵심 지표 비교

| 지표 | 93점 시점 | **현재** |
|:---|:---:|:---:|
| Application 하위 패키지 | 0개 | **16개** ✅ |
| GoCoachApp `withContext` | 11개 | **0개** ✅ |
| GoCoachApp `scope.launch` | 6개 | **0개** ✅ |
| GoCoachApp import | 229개 | **127개** ✅ |
| KMP 물리 이동 | 0건 | **2건** ✅ |
| External Sink adapter | 없음 | **구현 완료** ✅ |

### 가장 중요한 성과

이전 평가에서 "역설적 상황"으로 지적했던 **"정책을 분리할수록 UI가 오히려 커지는 문제"**가 이번에 해소되었습니다. `withContext`와 `scope.launch`가 0이 되어 UI가 엔진 IO 정책을 직접 소유하지 않게 되었습니다.

### 100점까지 4점 — 잔여 과제

| 과제 | 기여 | 난이도 |
|:---|:---:|:---:|
| 1. GoCoachApp 최종 분리 (`LaunchedEffect` 이관, import 40개대) | +2.5 | 중 |
| 2. KMP 이동 파일 `commonTest` 추가 | +1.0 | 쉬움 |
| 3. Shared 바둑 규칙 테스트 보강 | +0.5 | 쉬움 |
| 4. 실제 네트워크 Diagnostic sink adapter | +1.0 | 중 |

**가장 빠른 점수 확보 경로**: 과제 2+3은 코드 추가만으로 완료 가능하며, 합산 +1.5점을 즉시 획득할 수 있습니다. 과제 1의 `GameSessionEffectLauncher`를 실제 파일로 분리하면 96→98.5점으로 도달합니다.
```
