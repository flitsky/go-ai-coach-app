# 아키텍처 진단과 고도화 계획 (9세대)

작성일: 2026-09-23
기준선: `e78aafd9` · 1.0.0(versionCode 10000) 번들 산출 완료, 제출 대기 · `make test` 초록(16초)

**성격**: `docs/ARCHITECTURE.md`(원칙)와 `docs/spec/GO_AI_COACH_ARCHITECTURE_ROADMAP.md`(매핑)가
*"무엇이 어디 있어야 하는가"* 를 말한다면, 이 문서는 **실측으로 "무엇이 실제로 어디 있는가"** 를
말하고 그 격차를 메우는 실행 계획을 담는다. 일감은 `260923-_ACTIVE_BACKLOG.md`로 나간다.

**근거**: 32개 에이전트가 12개 축을 병렬 조사하고(조사 → 적대적 반증 → 설계 4안 경합 → 4렌즈 심사),
`com.worksoc.goaicoach.*` 내부 import 엣지 **2,004개**를 전수 추출해 패키지 그래프를 만든 결과다.
모든 수치는 이 날짜의 실측이며, 낡으면 다시 재는 것이 맞다.

---

## 진행 상태 (이 절만 갱신한다 — 아래 본문은 진단 시점 그대로 둔다)

### ✅ P0 지혈 완료 — 2026-09-23, 커밋 7개 (`b19bdc57`…`01a85479`)

| 항목 | 결과 |
| --- | --- |
| 덤(komi) 유실 (§1.5 🔴) | **해소** `40c4975c` · encode/decode 왕복 + 회귀 3건. **SchemaVersion 무변**(검수자 `git diff`로 확인). 음성 대조 통과 — `put("komi")` 한 줄을 지우니 새 테스트가 빨개졌다 |
| 죽은 안전망 4개 (§1.2) | **해소** `0c33d32c` · 스캔 경로를 shared로. 넷 각각에 고의 위반을 주입해 빨개지는 것 확인 |
| 경로 하드코딩 (함정 B) | **해소** `7841ed37` · `RepoPaths.kt` 신설(경로 46곳 흡수), `repoRoot()` 중복 2벌 → 1벌, `systemProperty("repo.root")` 주입 + `inputs.files` 선언 |
| 빈 스캔 재발 방지 | **해소** `01a85479` · `ktFilesIn`에 `require(files.isNotEmpty())`. 날것 `readText()` 43곳 → `codeOnly()` |
| 항상 false인 단언 | **해소** `a0a9d17f` · 삭제. 위 `assertEquals`에 완전히 포함돼 있었음을 고의 파손으로 실증 |
| `engine-android` API 표면 | **해소** `f9cd8797` · `implementation` → `api` |
| 중복 컨트롤러 | **해소** `b19bdc57` · 970→954줄. **훅 예산은 42→42, 회수 0**(함정 H대로) |

### ⭐ P0이 밝혀낸 가장 중요한 사실 — 진단이 강화됐다

죽은 가드 넷을 되살렸더니 **진짜 위반이 0건이었다.** `codeOnly` 치환 43곳에서도 거짓 통과가 0건이었다.
즉 **260816 이후 코드는 계속 경계를 지켰고, 감시만 꺼져 있었다.**

이것은 §0의 한 줄 결론(*"아키텍처가 나쁜 게 아니라 지키는 수단이 잘못된 층위에 있다"*)의
**직접 증거**다. 고칠 대상은 코드가 아니라 **계약을 표현하는 방식**이라는 처방이 실측으로 확인됐다.

### 🆕 P0에서 새로 드러난 것 (본문에 없던 것)

| 발견 | 근거 | 성격 |
| --- | --- | --- |
| **원격 엔진 코덱이 komi·handicapCount를 안 보낸다** | `HttpRemotePositionAnalysisTransport.kt:124` `encodeState`가 boardSize/ruleset/nextPlayer/포획수/stones/moves만 담는다. `RemoteEngineCoreApiAdapter.kt:339`가 같은 코덱을 쓴다 | ⏳ **잠재.** 오늘은 터지지 않는다 — `MainActivity.kt:117`이 `BuildConfig.DEBUG && REMOTE_ENGINE_URL.isNotBlank()`로 막아 **debug + local.properties 키가 있을 때만** 원격을 탄다. 하지만 원격/DePIN이 출하되는 순간 **덤과 접바둑을 모르는 채로 분석**한다 |
| `GameHistoryStore`의 komi 기본값 불일치 | `:191` `optDouble("komi", 0.0)` ↔ `GameSessionStore`·`UserPreferencesStore`는 `DefaultKomi`(6.5) | ⚠️ 옛 기록이 0집으로 복원될 수 있다. encode(:167)는 담고 있으므로 komi 키가 없던 세대의 기록이 실재하는지 확인 필요 |
| 계약 테스트 22개가 아직 상대경로를 쓴다 | `app-android/src/test`에서 `File("src/main/java/...")` | `RepoPaths`의 `repo.root` 주입을 안 받아 **워크트리 미끄러짐 위험이 그대로**. 파일 이동(P3) 전에 흡수해야 한다 |
| `require`는 "빈 디렉터리"만 잡는다 | — | *"스캔은 되는데 금지 문자열이 낡아 아무것도 못 잡는다"* 는 여전히 조용히 통과한다. 금지 조각이 저장소에 실재하는지 보는 메타 검사가 필요 |
| `AttendanceRewardGrantTest.kt` 한 파일에 클래스 둘 | `AttendanceRewardPolicyTest` + `AttendanceRewardGrantTest` | `--tests` 필터가 **엉뚱한 클래스만 돌려 놓고 초록으로 보인다.** 실제로 한 번 속았다 |

### ✅ 덤 수정 실기 검증 완료 — 2026-09-23

사용자가 실기에서 **덤 0.5 → 4수 → 앱 강제 종료 → 재시작 → 이어하기 → 설정에 0.5 유지**를 확인했다.
이 시나리오는 버그를 **구분한다** — `ui/SettingsScreen.kt`가 그리는 값은 `screenState.gameState.komi`
(설정 저장분이 아니라 **복원된 대국 상태 자체**)이고, 복원은 `SavedGameRestoreApplication`의
`buildSavedGameRestorePlan`이 `gameState`를 통째로 교체하므로, 수정 전이었다면 **6.5가 떴어야 한다.**
접바둑 분기와 옛 저장분 폴백은 `SavedGameSessionCodecTest`의 회귀 3건이 덮는다.

---

## 문서 정리 — 2026-09-23, 커밋 23+8개

진단서 작성 중 드러난 *"문서가 흩어져 있다"* 는 사용자 지적에서 출발해, **64개 문서를 코드와 전수 대조**했다.

### 실측 결과

| | 건수 |
| --- | ---: |
| 낡은 서술 | **64건** (오도 32 · 낡음 25 · 표기 7) |
| 중복 | 20건 |
| 삭제 후보 | 28건 |

**오도 32건**은 *"이 문장을 믿고 작업하면 틀린 일을 하게 되는 것"* 이다. 대표 사례:

- `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`가 *"코드는 아직 옮기지 않았다"* 로 시작 —
  **260816에 `application/` 124파일이 `:shared`로 물리 이전됐다.** 이 한 줄이 매핑 절 전체의 독법을 뒤집었다
- 같은 문서가 *"착수 계획서를 `docs/refactoring/`에 추가하라"* 고 **지시** — 그 폴더는 2026-08-17에 삭제됐고
  `DOCS_INDEX.md`가 "되살리지 말 것"이라 못박는다. 그런데 **그 `DOCS_INDEX.md` 자신이 다른 두 곳에서 "다시 만들어"** 라 한다
- `FEATURE_ACCESS_PRINCIPLES.md`가 *"`isPurchaseEnabled=false`가 아직 참"* — 코드는 `true`(2026-09-18).
  **결제가 라이브인데 원칙 문서가 반대로 말했다**
- `ENGINE.md`·`ENGINE_API_CALL_POLICY.md`의 "레벨별/visits별 time cap" — **코드에 그런 개념이 없다.**
  전역 단일 `SearchTimeLimit` 하나(Off/1/3/5/10초)다
- `OPERATIONS.md`의 `adb shell run-as com.worksoc.goaicoach` — 그건 namespace고 실제는 `com.zenit9hub.ai.baduk`.
  **복붙하면 실패한다**
- `APP_IA_AND_UI_SPEC.md`가 화면 7개 — 실제 9개(`MyPage`·`BoardScan`). 같은 문서 §2.3이 마이페이지를 다루면서 구조도엔 없었다
- `DIAGNOSTIC_EVENT_SCHEMA.md`가 `score.final_disagreement`를 *"죽은 코드"* 라 판정 —
  2026-09-06부터 `EndgameResolver`가 발행한다. **계가 불일치를 진단할 유일한 이벤트를 아무도 안 보고 있었다**

### 무엇을 했나

- **아키텍처 로드맵을 정본 자리로 되돌렸다**(141→188줄) — 실재 경로 반영, `persistence/`·`vision/` 계층 배정,
  루트 패키지를 "조립 전용"으로 명문화, **실측 갭 편입**(위반 730/2,004 · 17패키지 SCC ·
  *"사이클을 먼저 끊지 않은 모듈화는 컴파일에서 죽는다"*), 「어떻게 읽는가」 4줄 신설
- **`ARCHITECTURE.md` 4계층 정의를 확장**해 기기 영속 저장을 포함 — 이 문서는 *"새 앱에 복사해 간다"* 가 목적이라
  구멍을 두면 다음 앱에 복제된다
- **진단서 함정 A~J를 `PITFALLS.md` 67~76번으로 편입**하고 백로그 색인에 61~76 등재
  (본문에 있어도 **색인에 없으면 도달 경로가 0**이다 — 그 문서가 정한 읽는 법이 색인 경유다)
- **구조 이동 2건**: `LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`은 **개명·이동**돼 더는 없다 → `work/plans/REMOTE_ENGINE_AND_LAYERING.md`
  (Stage F/DePIN은 완결일을 적을 수 없어 기능축이다. **코드 참조 5곳 포함** 정리) ·
  MQ 킥오프를 `260818-_REMOTE_ENGINE_MQ_TRANSPORT.md`로 개명(날짜 접두사가 없어 **정렬=시간순이 깨져 있었다**)
- **흡수 후 삭제**: `ENGINE_SEARCH_TREE_REUSE_REVIEW.md`(375줄, 「다음 실험」을 `ENGINE_API_CALL_POLICY.md`로) ·
  `baduk_app_architecture_recommendation.md`(169줄, **삭제**됨 — Spark Plan 근거를 `LOGIN_AND_ACCOUNT_SYSTEM.md`로 흡수) ·
  벤치마크 원시 산출물 27개 — **`docs/`가 22M → 12M**
- `check-doc-links.py`에 **봉인 문서 예외를 좁게** 신설 — 매 실행에 예외 건수를 찍어 조용한 구멍이 되지 않게 했다

### ⚠️ 정리가 스스로 회귀를 만들었고, 잡아서 되돌렸다

1차 정리 뒤 깨진 링크가 **6건 → 10건**으로 늘었다. 개명한 스레드가 담당 밖 참조 3곳을 못 고쳤고,
삭제 스레드가 `THREAD_HISTORY.md`의 인용을 놓쳤다. 가장 얄궂은 자리는 —
*"⚠️ 개명했다, 옛 이름으로 찾으면 없다"* 고 **경고하는 바로 그 줄이 죽은 이름을 쓰고 있었다.**

교정 후 **기준선 스크립트로 현재 트리를 재면 정확히 그 6건** — 순증 0.

⭐ **여기서 배운 것**: 1차 스레드들이 *"9건 전부 기존 문제"* 라고 보고했는데, 그건 **자기들이 늘려 놓은 뒤의 숫자를
기존값으로 착각한 것**이었다. 여러 스레드가 동시에 도는 동안 "지금 값"을 기준선으로 쓰면 자기 회귀를 남의 문제로 읽는다.
→ **기준선은 `git archive <정리 직전 커밋>`로 실측한다.**

### 남은 것

| 항목 | 왜 안 했나 |
| --- | --- |
| 봉인 문서 3건 삭제 (`260911-260916`·`260917-260919`·`260919-260919`) | ⛔ **대조가 지우면 안 된다고 답했다.** 909 핸드오프가 자평한 「틀렸던 전제 둘」(Node 버전·콘솔 정렬 순서)이 `PITFALLS.md`에 **없고**, 구독 문서는 살아 있는 `PLAY_CONSOLE_PRIVACY_ANSWERS.md`가 참조한다 |
| `GOOGLE_PLAY_LAUNCH_PLAN.md` §0 재작성 (909 → 1.0.0) | **사람이 Play Console을 봐야 닫힌다** — 910~915에 무엇이 올라갔는지 저장소가 모른다 |
| 함정 10번 색인 누락 · `PITFALLS.md` 출처 표에 67~76 행 없음 | 기존 구멍. 다음 정리 때 |
| `DOCS_INDEX.md` 갱신 로그 (파일의 ~40%) | ⚠️ **규칙의 유래**가 섞여 있다 — git log만으론 *"왜 그 규칙이 됐는가"* 에 못 간다. 분류 후 사용자 승인 대상 |
| `OFFLINE_ENGAGEMENT` 스펙 2건 | ⛔ **삭제 금지.** 소스 KDoc 6곳이 절 번호를 직접 인용한다. 흡수처가 아직 없다 |

---

## 0. 한 줄 결론

> **아키텍처가 나쁜 게 아니다. 아키텍처를 지키는 수단이 잘못된 층위에 있다.**

이 저장소는 대부분의 앱이 도달하지 못한 상태에 이미 있다 — 포트·어댑터가 완성돼 있고,
도메인 22파일의 외부 import가 `kotlin.math`/`kotlin.random` 5줄뿐이며, `:shared`가
`:app-android`를 물리적으로 모른다. **Clean Architecture가 목표로 하는 상태에 이미 도달했다.**

없는 것은 그 상태를 **지키는 강제 수단**이다. 지금 그 역할을 1,829줄짜리 문자열 스캔 테스트와
산문 문서가 하고 있고, 둘 다 코드보다 빨리 낡는다. 그래서 처방은 *새 아키텍처*가 아니라
**같은 아키텍처를 컴파일러가 알아볼 수 있는 형태로 다시 적는 것**이다.

---

## 1. 실측 — 무엇이 실제로 깨져 있나

### 1.1 계층 모델이 서술로서 성립하지 않는 구간이 있다

import 엣지 2,004개를 문서가 선언한 7계층에 배정해 방향을 검사한 결과,
**730개(36%)가 선언된 순서를 위반**한다.

| 위반 방향 | 건수 | 뜻 |
| --- | ---: | --- |
| L7 → L5 | 389 | UI가 6계층을 건너뛰고 유스케이스를 직접 부른다 |
| L3 → L5 | 169 | **엔진 서비스가 세션/도메인을 안다** — 방향이 거꾸로다 |
| L2 → L5 | 66 | 브릿지가 도메인을 안다 |
| L7 → L3·L4·L2 | 92 | UI가 엔진·SDK·브릿지를 직접 부른다 |
| 기타 | 14 | |

그리고 더 무거운 것: Tarjan SCC 결과 `application/` 안에 **17개 패키지 강결합 컴포넌트**가 있다.

```
application.{analysis, autoai, debugreport, diagnostic, endgame, engine, engine.operation,
             humanmove, preferences, runtime, savedgame, score, session, startgame,
             topmoves, undo} + middleware
```

3계층으로 선언된 `application/engine`과 5계층으로 선언된 `application/session`이
**같은 사이클 안에 있다.** 사이클 안에서는 어느 쪽이 상위인지 정의되지 않는다 —
즉 이 구간에 대해 7계층 모델은 참도 거짓도 아니고 **성립하지 않는다.**

⚠️ 이것이 **모듈 분리를 지금 시도하면 실패하는 이유**다. `application/engine`만 떼어내는 순간
session·score·match·runtime·endgame이 딸려오고, 그것들이 다시 engine을 참조해 Gradle 순환
의존으로 빌드가 멈춘다. **사이클을 먼저 끊지 않은 모듈화는 컴파일에서 죽는다.**

### 1.2 안전망 4개가 죽어 있다 — 초록이 "위반 없음"이 아니라 "검사 안 함"이다

`LayeringContractTest.kt`의 import 규칙 10개 중 **4개가 0개 파일을 검사하며 무조건 통과**한다.
260816에 코드가 `shared`로 이사했는데 테스트의 스캔 경로가 따라오지 않았고,
`ktFilesIn`(:1586)이 없는 디렉터리를 조용히 빈 목록으로 돌려주기 때문이다.

| 위치 | 스캔 경로 | 실재 |
| --- | --- | --- |
| `:36` | `app-android/.../application` + `.../match` | `diagnostic` 1개만 남음 / 디렉터리 없음 |
| `:60` | `.../application/{auth,premium,device}` | 셋 다 없음 |
| `:99` | `app-android/.../match` | 없음 |
| `:930` | `app-android/.../application/score` | 없음 |

같은 파일 :1121의 테스트만 이 함정을 알아채고 스캔 대상을 shared로 옮겨 두었다.
**작성자가 함정을 알았으나 나머지 넷에 적용하지 않았다.**

### 1.3 손으로 쓴 DI 컨테이너가 임계에 도달했다

| 지표 | 실측 |
| --- | --- |
| `GoCoachAppWiringContext` 멤버 | **64개** (val 17 + 스냅샷 1 + 게터 25 + 세터 16 + 액션 5) |
| 배선 인자 총계 | **236개** |
| `UndoController` 생성자 인자 | 24개 |
| 컨트롤러 12개 중 단위 테스트에서 생성되는 것 | **1개** (나머지 11개는 테스트에 이름이 *문자열로만* 등장) |
| 의존성 하나 추가 시 수정 지점 | **3곳** |

컨트롤러 11개가 테스트에서 한 번도 생성되지 않는다는 것은 **배선이 검증되지 않는다**는 뜻이다.
이 자리에서 나는 버그는 CI가 전부 통과한 뒤 실기에서만 드러난다.

### 1.4 이름이 계층을 전달하지 못한다

`shared` 루트 패키지 22파일이 **2계층(`EngineModels.kt`의 `EngineCoreApi`)과 5계층(바둑 규칙 10여
파일)을 한 이름공간에 담는다.** `import com.worksoc.goaicoach.shared.X` 한 줄로는 X가 엔진 계약인지
바둑 규칙인지 알 수 없고, 따라서 **import 기반 규칙으로 검사할 수 없다.**
지금 이 구분을 문자열 스캔이 대신하고 있는 근본 원인이 여기다.

`app-android`도 같다 — 한 모듈에 **31,066 LOC**, 최상위 `internal` **430개**, 그리고
4계층 SDK 어댑터 6개(`AndroidBillingClient`, `AndroidAuthClient` 등, `androidx.compose` import 0건)가
**`package com.worksoc.goaicoach.ui`에 들어 있다.**

특히 `ui` 122파일은 **같은 패키지라 파일 간 import가 아예 생기지 않는다**(저장소 전체에서
`com.worksoc.goaicoach.ui` import는 10줄뿐). 참조 그래프를 **읽을 수조차 없는 상태**이고,
이것이 "ui를 어떻게 갈라야 하는가"에 지금 답할 수 없는 이유다.

### 1.5 확인된 사용자 피해 — 구조 문제보다 먼저다

| 결함 | 근거 | 증상 |
| --- | --- | --- |
| **덤(komi) 유실** 🔴 | `persistence/GameSessionStore.kt:66-79` encode에 komi 키 없음 → `GameStateReplayer.kt:10`의 기본값 6.5로 복원 | 사용자가 고른 덤이 **이어하기 후 조용히 6.5로 돌아간다.** 우회 경로 없음 |
| KataGo 프로세스 무락 기동 | `KataGoProcessEngineAdapter.kt:233-246` `ensureProcessStarted()`가 non-suspend·뮤텍스 밖, 12개 호출부 전부 락 밖. `process/input/output`에 `@Volatile` 없음 | 엔진 중복 기동·스트림 교차 |
| 엔타이틀먼트 동시 쓰기 | 스토어 load-modify-save에 락 없음 | 광고 보상과 출석 클레임이 겹치면 **앞선 클레임이 사라진다** |
| `GameHistoryStore` 비원자적 쓰기 | 대국 1건마다 `index.json` 전체 재작성 | 강제 종료 시 누적 기록 손상 |
| `AttendanceRewardGrantTest.kt:332` | 컴파일러가 *"Check for instance is always 'false'"* 경고 (Kotlin 2.4에서 에러 승격) | **그 테스트는 아무것도 검증하지 않는다** |

### 1.6 신규 기능이 오늘 막혀 있다

`UiStrings`의 생성자 파라미터가 **241개**다. JVM 한도는 255 — **여유 14칸.**
2026-09-18에 이미 런타임 `ClassFormatError`를 냈고(`UiStringsGameFlow.kt`가 그 사고를 기록),
지금은 위성 16파일의 Map 우회로로 버티고 있다.

**이것은 취향 문제가 아니라 차단이다.** 학습·리플레이·수익화 확장을 계획하면서
새 문구를 넣을 정식 자리가 없는 상태를 방치할 수 없다.

### 1.7 변경 빈도가 구조를 고발한다 (최근 6개월)

| 파일 | 변경 | 읽는 법 |
| --- | ---: | --- |
| `ui/GoCoachApp.kt` (970줄) | **311회** | 거의 모든 작업이 이 한 파일을 지난다 = 병목이자 최대 충돌면 |
| `ui/UiStrings*.kt` 5종 | **476회** 합산 | 보통 `strings.xml` 한 번이면 끝날 일 |
| `architecture/LayeringContractTest.kt` | **108회** | 가드레일이 아니라 **사람이 매번 다시 조이는 래칫** |

세 번째가 핵심이다. 아키텍처 테스트가 6개월에 108번 바뀌었다는 것은,
그 테스트가 경계를 *지켜 준* 게 아니라 **경계가 바뀔 때마다 사람이 문자열을 고쳐 넣었다**는 뜻이다.
컴파일러가 강제해야 할 일을 grep이 애원하고 있다.

---

## 2. 건드리지 않는 것 — 이미 옳다

리팩토링에서 가장 비싼 실수는 멀쩡한 것을 갈아엎는 것이다. 다음은 **유지**한다.

1. **Gradle 모듈 의존 방향** — `:shared`가 `:app-android`를 전혀 모른다. 테스트보다 강한 강제 수단이다.
2. **도메인 순수성** — shared 루트 22파일의 외부 import가 5줄. KMP 이식성의 근거다.
3. **`engineOperationApplicationPoliciesStayPortable`**(`:1121`) — shared/application 285파일에서
   `android.`/`androidx.`/`java.`/`org.json.` import를 매번 전수 금지한다. **실제로 작동 중인 가드다.**
4. **줄수/상태훅 하향 래칫**(`:1354-1545`) — `GoCoachApp.kt`가 god file로 되돌아가는 것을 막는 유일한 장치.
5. **`detectForbiddenReference`**(`:1618`) — 와일드카드 import와 인라인 FQN까지 잡고, 그 탐지력 자체를
   자가 검증한다(`:1249`). 규칙을 제대로 겨누기만 하면 회피가 어렵다.
6. **`PremiumState`의 `matchGeneration` ↔ `sessionGeneration` 분리** — 실제 버그(무르기 시 프리미엄 해제)를
   고친 결과다. 7계층 모델이 담고 있는 **지식**이고, 모델을 버리면 이 지식이 같이 사라진다.
7. **`GameUiEvent` sealed 25종 + exhaustive when + 순수 `buildGameScreenState`** — MVI의 절반이
   이미 구현돼 있고 실제로 작동한다.
8. **`middleware` 게이트웨이 3파일** — 「서 있는 답」이 지키기로 한 것. 모듈 분리용 뼈대다.

---

## 3. 처방 — 목표 아키텍처

> **Hexagonal(포트·어댑터) 골격 + KMP 순수 도메인 + 화면별 StateHolder(MVI-lite) + 수동 DI**

**새 이름을 붙이지 않는다.** 이 넷은 이미 코드에 있고, 없는 것은 강제 수단뿐이다.
목표는 계층을 **3단계로 물리화**하는 것이다.

| 수단 | 무엇을 강제하나 | 언제 |
| --- | --- | --- |
| **(c) Konsist** | PSI 기반 규칙 — 주석·문자열을 애초에 코드로 보지 않는다 | 패키지가 갈라진 뒤 |
| **(b) 패키지 FQN** | 계층을 이름으로 드러내 **import 한 줄로 검사 가능**하게 | 중반 (P3) |
| **(a) Gradle 모듈** | 되돌릴 수 없는 방향만 | 최후 · 별도 승인 |

7계층 모델은 **폐기하지 않고 정의를 두 곳 확장**한다:
4계층에 **기기 영속 저장(persistence)** 을 포함하고, 루트 패키지를 **"조립 전용, 전 계층 참조 허용"** 으로
명문화한다. 지금 persistence 23파일과 실질 composition root가 모델 **밖에 떠 있는 것**이 그 증거다.

### 3.1 기각한 선택지와 이유

| 기각 | 이유 |
| --- | --- |
| **Hilt 도입** | **물리적으로 불가.** 컨트롤러 12개가 전부 `commonMain`이고 이 소스셋은 iOS 타깃으로도 컴파일된다. Hilt는 AGP+KSP에 묶여 commonMain을 처리할 수 없고, `javax.inject`를 iOS에 얹을 수 없으며, ViewModel이 없어 `hiltViewModel()` 진입점도 없다 |
| **Koin 도입** | 기술적으로 가능하나 **얻는 것이 0.** 배선 인자 236개 중 대부분이 타입이 아니라 **상태를 읽는 람다**라 컨테이너가 대신 만들어 줄 수 없고, 컴파일 검증을 런타임 해석으로 **퇴행**시킨다. `GoCoachControllerWiring.kt:143-153`에 근거까지 적힌 실재 생성 순서 제약이 컨테이너 안에서 암묵으로 숨는다 |
| **전면 MVI (Orbit/MVIKotlin)** | **이미 절반이 있고 그 절반이 작동한다.** 빠진 것은 *상태 소유자* 하나뿐이다. 91k LOC 라이브 앱에서 이벤트 경로 전면 재작성은 회귀 위험이 이득을 압도한다 |
| **표준 Clean 3계층(domain/data/presentation)으로 갈아엎기** | 이름만 바뀌고 실제 경계는 그대로다. 대신 계층마다 코드에 남은 **사고 이력**(`EngineModels.kt`의 SLA KDoc, `EngineMode.Unknown` 결정, `GoBoard`의 실기 결함 주석)을 재배치 과정에서 잃는다 |
| **기능 수직 모듈화(11모듈) 즉시 착수** | `ui`가 같은 패키지라 **참조 그래프를 미리 읽을 수 없어** "옮기고 컴파일 에러를 따라가는" 방식밖에 없다. 게다가 빌드 시간의 지배 항목은 코틀린 컴파일이 아니라 **AAB 109MB 중 93MB인 KataGo 모델 패키징**이라 모듈을 쪼개도 줄지 않는다 — **최대 비용, 최소 이득** |
| **ArchUnit** | JVM 바이트코드를 읽어 `commonTest`에서 쓸 수 없고, Compose 합성 클래스 때문에 소스 의도와 어긋난다 |
| **커스텀 Lint** | 별도 모듈 + UAST 학습 + AGP 결합. 1인 체제에 과대 |
| **kotlinx.serialization 전면 이관 / Play Asset Delivery** | 이번 범위 제외. persistence 23파일은 전부 `org.json`/`Context`/`java.io.File`에 결합돼 **그대로 shared로 옮길 수 있는 파일이 0개** — 즉 *KMP 이식* 과제이지 *현재 사용자 위험 제거* 과제가 아니다 |

### 3.2 상태 소유자 — `ViewModel`은 **app-android의 얇은 래퍼로만** 도입

현재 세션 상태의 유일한 소유자가 **컴포지션**이고, 프로세스 사망 방어는 `rememberSaveable` 1건뿐이며,
`AndroidManifest`의 `configChanges` 10개가 문제를 가려 주고 있을 뿐이다.

동시에 shared의 `GameSessionStateHolder`가 Compose·Android 무의존인 것은 **iOS 타깃을 살려 두는 유일한 근거**다.
따라서 shared 홀더는 **손대지 않고**, `GoCoachSessionViewModel`이 그것과 `viewModelScope`를 **소유하기만** 한다.
iOS는 다른 래퍼를 쓰면 된다.

### 3.3 상태 훅 예산(42/42, 여유 0) — 숫자를 올리지 않고 **분모를 바꾼다**

예산의 의도("조립만 하는 셸은 상태를 소유하지 않는다")는 옳지만 **지표가 의도와 어긋나 있어
올바른 코드를 쓰면 테스트가 깨진다.** 그 결과 두 가지 회피가 실제로 학습됐다:

- 저장소 4개에 `remember`를 **일부러 생략**해 매 재구성 재할당(`GoCoachApp.kt:151,153,155,167`)
- UI 상태를 **프로세스 전역 `object`로 이주** — 사유가 *"훅 예산 절약"* 이라고 코드에 적혀 있다
  (`GoCoachApp.kt:792,811,935` — `SplashVisibility`·`FinishedGameFlow`·`AppFontScaleState`)

⚠️ **숫자 상향도, 예산 폐지도 안 된다** — `LayeringContractTest.kt:1490-1508`이 그 숫자가 어떤 대가로
정해졌는지 기록해 두었고, 무력화하면 `GoCoachApp`이 god file로 되돌아간다.
대신 분모를 **"문자열 개수"에서 "셸이 소유해도 되는 상태의 종류 화이트리스트"** 로 바꾸고,
예산 상수를 `architecture-budgets.json`으로 분리한다.

---

## 4. 착수 전 반드시 아는 함정 — 심사에서 걸러진 것

> 이 절은 **네 개 설계안이 전부 틀렸던 지점**이다. 4렌즈 심사가 실측으로 잡아냈다.

### ⚠️ 함정 A — `wiringContext`의 `remember` 키 제거는 **하면 안 된다** (설계 4안 전부가 제안했다)

네 안 모두 `GoCoachApp.kt:478`의 remember 키 7개를 없애 `remember { }`로 만들라고 하고,
모두 *"64개 멤버가 전부 게터 람다이거나 이미 remember로 고정된 인스턴스"* 를 근거로 댔다.
**그 근거는 틀렸다.**

- `GoCoachApp.kt:288-292`의 `playerSetup`/`matchMode`/`searchTimeSettings`/`topMovesEnabled`/
  `shouldShowResumePrompt`는 **매 컴포지션마다 계산되는 평범한 지역 `val`** 이고,
  익명 객체에서 `override fun playerSetup(): PlayerSetup = playerSetup`(:513) 형태로 그대로 캡처된다.
- `:142-144`의 `engineName`/`engineDiagnostic`은 **바로 위 KDoc이 "⚠️ `remember`로 감싸지 않는다 —
  감싸는 순간 준비 전 답이 그 자리에서 굳는다"고 명시적으로 금지한 값**이다.

키를 없애면 이 값들이 첫 컴포지션 값으로 **영구히 얼어붙는다.** 증상은 *"설정이 안 먹는다"* /
*"디버그 리포트가 거짓말한다"* 로 나오고, **CI 어디에도 걸리지 않는다.**
네 안이 공통으로 제안한 회귀 테스트(*"컨트롤러 인스턴스 동일성 유지"*)는 이 버그를 **통과시킨다.**

→ **올바른 순서**: 키 제거가 아니라 ⓐ 이 8개를 먼저 람다/지연 읽기로 바꾸고 ⓑ 64개 멤버 전수 감사
(`get() =` 또는 람다가 아닌 멤버 0건 확인) ⓒ 그 다음 키 제거. 그 전에는 **착수 금지.**

### ⚠️ 함정 B — 파일을 옮기기 전에 테스트의 하드코딩 경로를 먼저 뽑는다

`LayeringContractTest.kt`가 `'GoCoachApp.kt'`를 **23회** 참조하고, `app-android/src/test` 전체에서
**16개 파일**이 이 이름을 문자열로 들고 있다. `repoRoot()`는 `LayeringContractTest.kt:1804`와
`TestAnnotationContractTest.kt:82`에 **두 벌** 선언돼 있고 둘 다 `File(".")` 상향 탐색인데,
이 저장소는 워크트리가 3개라 **실행 위치에 따라 다른 트리를 검사한다.**

파일을 옮기는 순간 16개 파일이 단언 실패가 아니라 **`FileNotFoundException`으로 동시에** 터지고,
메시지가 *"경계가 깨졌다"* 가 아니라 *"파일이 없다"* 로 나와 원인 파악이 늦는다.
→ **경로 상수화 + `systemProperty("repo.root", rootDir.absolutePath)` 주입을 어떤 이동보다 먼저.**

### ⚠️ 함정 C — 저장 스키마는 **필드 추가만, 버전 불변**

`SavedGameSessionCodec.decode`는 `json.optInt("schema") != SchemaVersion`이면 **곧바로 `null`을 반환**한다
(`GameSessionStore.kt:83-85`). 그리고 **9개 JSON 블롭 스토어 중 8개는 마이그레이션 경로가 없다.**
스키마 번호를 한 번이라도 올리면 이어하기 슬롯·종국 결과·**출석 일수·보유 캐릭터가 통째로 초기화**된다.

→ 모든 필드 추가는 `optDouble`/`optInt` 기본값 흡수로만. `SchemaVersion` 상수를 건드리는 커밋은 **승인 대상.**

### ⚠️ 함정 D — `EngineCoreApi.syncStaticPosition` 기본 구현 삭제는 **픽스처 다음**

기본 구현 제거 판단 자체는 옳다(기본값이 있어서 원격·스텁 **둘 다 조용히 빠뜨렸다**).
그러나 공용 픽스처가 없어 각 테스트가 손으로 쓴 페이크가 흩어져 있고, **그 전부가 동시에 컴파일 에러**가 된다.
→ 순서를 뒤집는다: **공통 계약 테스트 스위트(Local/Remote/Stub 세 구현체를 같은 시나리오로) 먼저,
기본 구현 삭제는 그 다음.** 재발을 막는 실체는 삭제가 아니라 그 스위트다.

### ⚠️ 함정 E — 오퍼레이션 단위 `Mutex`는 그대로 넣으면 앱이 언다

`forceReset`은 설계상 이 락을 잡지 않는다(옳다). 그런데 그 결과 **락을 쥔 채 멈춘 호출이
`forceReset`으로 죽은 프로세스를 기다리는 동안 다른 모든 오퍼레이션이 자기 타임아웃까지 대기**한다.
착수·무르기가 수십 초 얼어붙고, **단위 테스트로는 재현되지 않는다.**
→ 평범한 `withLock`이 아니라 **타임아웃/세대 기반 취소**가 함께 들어가야 한다.

### ⚠️ 함정 F — 공유 작업 트리에서 대규모 개명·이동은 단독 점유로

세션 여럿이 작업 트리 하나를 공유하고 `git checkout` 되돌리기가 금지돼 있다.
`*Application.kt` → `*UseCase.kt` 59파일 일괄 개명 같은 것은 **어떤 경계도 강제하지 않는
순수 미학 작업이면서 최대 충돌면**을 만든다 → **채택하지 않는다.**
`ui/` 재배치·UiStrings 분해는 **`parallelizable: false`** 로 못박고, 착수 전 다른 스레드에 알린다.

### ⚠️ 함정 G — 죽은 코드를 릴리스 블로커로 착각하지 말 것

원격 `decodeMove`의 `boardSize` 기본값 9 좌표 오독(`HttpRemotePositionAnalysisTransport.kt:205-217`)은
**프로덕션 소비자가 0건**이다(참조가 전부 KDoc 주석). 살아 있는 원격 경로는
`RemoteEngineCoreApiAdapter.kt:325`에서 이미 `request.state.boardSize`를 권위로 넘긴다.
→ 제출 전 가치가 있는 P0 항목은 **덤 유실 하나**다.

### ⚠️ 함정 H — 예산 회수 계산 오류

`GoCoachApp.kt:398-412`의 중복 `EngineBenchmarkController` 삭제가 *"상태 훅 예산 15줄 회수"* 로 적혔으나,
이 지역 변수는 `remember`/`mutableStateOf`/`LaunchedEffect`를 **하나도 포함하지 않는다.**
회수되는 것은 **줄수 예산뿐이고 훅 예산 42/42는 1도 줄지 않는다.**
(삭제 자체는 안전하다 — `controllers.benchmarkController`만 :583, :819에서 쓰인다.)

### ⚠️ 함정 I — iOS 컴파일을 릴리스 게이트에 넣지 않는다

`make test`에 `:shared:compileKotlinIosSimulatorArm64`를 넣는 것은 게이트로서는 옳지만,
iOS는 **아무것도 출하하지 않는 타깃**이다. 안드로이드 릴리스가 출하하지 않는 타깃의 컴파일 실패로
막히면 안 된다 → 별도 `make test-ios`로 둔다.

### ⚠️ 함정 J — `require(files.isNotEmpty())`는 **현재 초록인 테스트를 즉시 빨갛게** 만든다

죽은 경로 4개를 shared로 고치는 순간, 260816 이후 검사되지 않던 구간의 **실제 위반이 무더기로 드러날 수
있다.** 그게 이 태스크의 성과이지만, `make test`는 이 저장소의 **유일한 릴리스 게이트**(`Makefile:137`)다.
→ 드러난 위반은 같은 스레드가 **끝까지 초록으로 만들고 닫는다.** 빨간 채로 남기지 않는다.

---

## 5. 로드맵

> 원칙: **어느 단계에서 멈춰도 코드베이스는 일관되게 남는다.**
> 그리고 **순수 이동과 동작 변경을 같은 커밋에 절대 섞지 않는다** — 순수 이동은 diff가 전부
> package/import 줄이라 리뷰 비용이 0에 수렴하고 컴파일러가 완전성을 보증하지만,
> 동작 변경이 한 줄이라도 섞이면 그 보증이 사라진다.

| 단계 | 목표 | 병렬 | 모델 | 제출 전 가능 |
| --- | --- | --- | --- | --- |
| **P0 지혈** | 사용자 체감 결함 + 죽은 안전망 | ✅ | Sonnet 위주 | ✅ |
| **P1 게이트** | 이후 전부가 기댈 검증 경로 복구·확장 | ✅ | Sonnet | ✅ |
| **P2 엔진 동시성** | 1~3계층 경쟁 조건 차단 | 독립 트랙 | **Opus** | ⚠️ 실기 검증 후 |
| **P3 이름공간 정렬** | 계층을 패키지 FQN으로 드러냄 (**동작 변경 0**) | ⚠️ 직렬 | Sonnet | ❌ |
| **P4 사이클 절단** | 17패키지 SCC 해소 + 계약 좁히기 | 일부 | **Opus** | ❌ |
| **P5 상태 소유자** | "컴포저블이 ViewModel"인 구조 종료 | ❌ | **Opus** | ❌ |
| **P6 선택적 확장** | 모듈 승격 등 — **하나도 안 해도 완결** | — | — | 개별 승인 |

### 순서에 대한 두 가지 정정 (심사 반영)

1. **UiStrings 분해를 P6에서 P1 직후로 올린다.** 241/255는 *"신규 기능이 오늘 막혀 있다"* 는
   **유일하게 검증된 사실**이고, 학습·리플레이·수익화 확장이 전제라면 이것이 가장 늦을 수 없다.
   게다가 기능 축 분해가 **`ui` 패키지 분할의 안내도**가 된다 — 나중에 하면 같은 파일군을 두 번 만진다.
2. **모듈 경계는 P3이 드러낸 실제 import 그래프를 보고 결정한다.** 지금 목표 그래프를 확정하는 것은
   *"측정하기 전에 답을 적는 것"* 이다. P3 직후 패키지 간 엣지와 SCC를 실측해 기록하는 것을
   태스크로 둔다.

### 인수 기준은 "테스트 초록"이 아니라 **지금 반드시 실패하는 실기 시험**으로

- **P5**: 개발자 옵션 **「액티비티를 유지하지 않음」 ON** → 대국 20수 → 홈 → 복귀 시
  판·화면 위치 생존. **현 코드에서 반드시 실패하고 수정 후 반드시 통과한다.**
- **P3**: `git diff -U0 | grep '^[+-]'` 에서 **package/import 이외 변경 0줄**.
- **P0 덤**: `SavedGameSessionCodecTest`에 komi 0.5/7.5 왕복 단언.
- **모듈 승격(P6)**: `:core:domain`의 `build.gradle.kts` **`dependencies` 블록이 비어 있다는 사실 자체**.
  테스트가 아니라 **파일 한 개**가 *"도메인은 아무것도 모른다"* 를 증명한다.

---

## 6. 설계 경합 결과 (참고)

4개 안을 4개 렌즈(회귀 안전성 / 실질 이득 / 순서와 병렬성 / 미래 적합성)로 독립 심사했다.

| 안 | 총점(200) | 렌즈별 | 판정 |
| --- | ---: | --- | --- |
| **계약을 문서에서 컴파일러로 — 중단 가능한 7계층 재정박** | **152** | 39·39·37·37 | **채택.** 4개 렌즈 전부에서 1위 |
| 얇은 셸 · 한 개의 소유자 | 130 | 31·31·36·32 | UiStrings 우선 · 실기 인수 시험 **이식** |
| 두꺼운 도메인 커널 | 118 | 34·32·25·27 | 골든 테스트 게이트 · `GameSetup` VO · `BoardRegionAnalyzer` **이식** |
| 모듈 경계 = 계층 경계 | 109 | 30·23·29·27 | 모듈 우선은 **기각** — SCC를 모듈이 원리적으로 못 잡는다 |

채택안이 1위인 이유는 **중단 가능성을 설계 제약으로 명시한 유일한 안**이기 때문이다.
*"순수 이동과 동작 변경을 같은 커밋에 섞지 않는다"*, *"릴리즈 제출 직전 P5 착수 금지"*,
*"P6은 하나도 안 해도 완결"* 이 전부 회귀 억제 장치다.

4위안이 낮은 이유가 중요하다 — *":shared 내부가 이미 단방향이므로 모듈이 계층 강제를 흡수한다"* 는
핵심 추론이 **실측으로 반증됐다.** 7계층 서사가 실제로 무너지는 지점(application 내부 17패키지 SCC)은
그 안의 목표 그래프에서 **전부 `:core:application` 한 모듈 안**에 들어간다.
**모듈 경계는 이것을 원리적으로 못 본다.**

---

## 관련 문서

- `docs/ARCHITECTURE.md` — 7계층 원칙 (앱 비종속)
- `docs/spec/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` — 계층별 파일 매핑 ⚠️ **이 문서의 §1이 그 매핑의 실측 갱신본이다**
- `docs/spec/PITFALLS.md` — 함정 1~66 전문. **이 문서 §4의 A~J는 아직 여기 없다**
- `work/roadmap/260923-_ACTIVE_BACKLOG.md` — 일감은 여기로 나간다
