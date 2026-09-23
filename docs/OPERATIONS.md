# Operations

작성일: 2026-06-17
성격: 스택/계가 같은 굳어진 제품 결정과, 현재 옵션 화면·진단 로그처럼 "지금 동작이 무엇인가"를 한 곳에서 보기 위한 상위 요약 문서. 각 섹션은 더 깊은 문서로 링크한다.

## 기술 스택 결정

`Kotlin Multiplatform`을 1차 구현 기준으로 쓴다. 이유는 네이티브 엔진 제어와 Android 우선 출시 속도를 동시에 얻기 위함이다. `Flutter`는 여러 보드게임 앱에 UI를 재사용하는 것이 엔진 제어보다 중요해질 경우의 대안으로 남겨둔다.

전체 비교 근거 문서(`STACK_DECISION.md`)는 2026-08-17 문서 보존 정책 전환으로 저장소에서 삭제됐다 — 결론은 위 두 문단이 그대로 담고 있고, 원문이 필요하면 `git log --all --diff-filter=D -- '**/STACK_DECISION.md'`로 복원한다.

## 계가/종국 정책

- 중간 형세는 `EngineCoreApi.estimateScore()`로 받고, 수순별 추이는 `shared/ScoreTimeline`에 기록한다.
- 양쪽 연속 pass 시 `deadStones()`로 사석을 먼저 정리한 뒤, `shared/BoardScorer`가 현재 ruleset(Area/Territory)으로 최종 계산한다.
- 종국 판정은 **부심/주심 2단계**다. 부심(기본)은 `deadStones()` 2초 cap + `scoreFinal()` 1초 cap으로 빠르게 결과를 보여주고, 주심(사용자가 "이의 제기"를 눌렀을 때만)은 시간 제한 없이 정밀 재검증한다. 두 결과가 갈리면 자동으로 덮어쓰지 않고 사용자가 최종안을 선택한다.
- 부심/주심 불일치는 `critical` diagnostic event 후보다(현재 `score.final_disagreement` 이벤트 자체는 코드에 정의만 되어 있고 호출부가 아직 연결되지 않음 — `DIAGNOSTIC_EVENT_SCHEMA.md` 참고).

상세 SLA 수치, KataGo 명령 목록, 불일치 UX 원칙은 `SCORE_AND_ENDGAME_DECISION.md`를 따른다.

## 현재 옵션 화면 — 요약

플레이 화면 액션 버튼: `Pass`, `Undo`, `Best`(Top Moves 토글), `Eval`(ownership/gradient 토글, 켜질 때 score estimate를 다시 요청).

`Menu`(햄버거) 안의 4개 영역:

| 영역 | 핵심 옵션 |
| --- | --- |
| `Player Setup` | 흑/백 각각 사람·AI 선택, AI는 봇 캐릭터 픽커로 `빠른 초급` 5단계(초보~초고수) 중 하나를 고름(주1), 둘 다 AI면 `Auto delay` |
| `Search Time` | `Time cap On/Off` + B16/B32/B64별 응답시간 cap |
| `Game` | `New`, `Copy Log`, `Benchmark`, `Scoring rule: Area | Territory` |
| `Display menu` | `Coords`, `Move nums`, `Last ring` (ownership gradient는 여기 없고 `Eval` 액션 버튼이 담당) |

주1: `PlayLevelGroup`에는 `빠른 초급` 외에 `초급`/`중급`/`고급` 세 그룹이 더 있지만(각 5~7단계),
2026-08-18부터 이 화면에서는 완전히 숨겼다 — 코드(`shared/.../PlayLevel.kt`)는 지우지 않고 남겨뒀고,
재노출은 대국장 로드맵에서 검토 예정이다(마이그레이션 없음). 그리고 2026-08-29(#10)부터는 남은
`빠른 초급` 5단계 선택 자체도 숫자 드롭다운이 아니라 **봇 캐릭터 픽커**(캐릭터 하나 = 티어 하나,
`PlayerSetupPanel.kt`)로 바뀌었다.

세부 동작, 기본값, 저장/복원 정책은 `USER_OPTION_MANUAL.md`를 따른다.

## 진단/런타임 로그 — 요약

두 가지 별개 로그 파일이 있다.

| 파일 | 형식 | 목적 |
| --- | --- | --- |
| `diagnostic_events.jsonl` | JSON, 한 줄에 한 이벤트 | slow/timeout/discarded/visit-fill-short처럼 이상 신호만 |
| `runtime_events.log` | 평문 `key=value` | 거의 모든 턴 전환을 narrative로 기록 (20개 event 종류) |

개발자가 원격 기기 로그를 볼 때는 ADB `run-as`로 두 파일을 함께 받는다.

⚠️ **패키지명 주의**: `com.worksoc.goaicoach`는 Kotlin `namespace`일 뿐이다. 실제 설치 패키지
(`applicationId`, `run-as`가 매칭하는 값)는 `com.zenit9hub.ai.baduk`다 — `app-android/build.gradle.kts`의
`defaultConfig`에서 직접 확인할 것(`Makefile`의 `APP_PACKAGE` 변수도 같은 값을 읽는다).

```bash
adb shell run-as com.zenit9hub.ai.baduk cat files/diagnostic_events.jsonl
adb shell run-as com.zenit9hub.ai.baduk cat files/runtime_events.log
```

⚠️ **debug 빌드에서만 동작한다.** `run-as`는 `android:debuggable=true`인 빌드에서만 허용된다 — 업로드용
번들(`playInternal`/`release`)은 `isDebuggable=false`라 위 명령이 그대로 거부된다(`docs/spec/PITFALLS.md` 함정
56·57 참고). 실기 로그를 봐야 하면 `make dev`/`make install-dev`로 만든 debug 빌드를 쓸 것.

이벤트 종류 전체 목록, 필드 스키마, 외부 전송(Firebase/Sentry 등) 정책은 `DIAGNOSTIC_EVENT_SCHEMA.md`를 따른다.

## 더 깊은 문서

| 문서 | 다룰 내용 |
| --- | --- |
| `SCORE_AND_ENDGAME_DECISION.md` | 계가/종국 SLA, 부심·주심 불일치 UX, KataGo raw 명령 목록 |
| `USER_OPTION_MANUAL.md` | 모든 메뉴/버튼의 상세 동작, 기본값, 저장 정책 |
| `DIAGNOSTIC_EVENT_SCHEMA.md` | diagnostic JSONL 스키마 + runtime event log 20종 전체 목록 |
