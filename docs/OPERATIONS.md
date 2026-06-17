# Operations

작성일: 2026-06-17
성격: 스택/계가 같은 굳어진 제품 결정과, 현재 옵션 화면·진단 로그처럼 "지금 동작이 무엇인가"를 한 곳에서 보기 위한 상위 요약 문서. 각 섹션은 더 깊은 문서로 링크한다.

## 기술 스택 결정

`Kotlin Multiplatform`을 1차 구현 기준으로 쓴다. 이유는 네이티브 엔진 제어와 Android 우선 출시 속도를 동시에 얻기 위함이다. `Flutter`는 여러 보드게임 앱에 UI를 재사용하는 것이 엔진 제어보다 중요해질 경우의 대안으로 남겨둔다.

전체 비교 근거는 `archive/2026-06-17-early-decisions/STACK_DECISION.md`를 따른다.

## 계가/종국 정책

- 중간 형세는 `EngineCoreApi.estimateScore()`로 받고, 수순별 추이는 `shared/ScoreTimeline`에 기록한다.
- 양쪽 연속 pass 시 `deadStones()`로 사석을 먼저 정리한 뒤, `shared/BoardScorer`가 현재 ruleset(Area/Territory)으로 최종 계산한다.
- 종국 판정은 **부심/주심 2단계**다. 부심(기본)은 `deadStones()` 2초 cap + `scoreFinal()` 1초 cap으로 빠르게 결과를 보여주고, 주심(사용자가 "이의 제기"를 눌렀을 때만)은 시간 제한 없이 정밀 재검증한다. 두 결과가 갈리면 자동으로 덮어쓰지 않고 사용자가 최종안을 선택한다.
- 부심/주심 불일치는 `critical` diagnostic event 후보다(현재 `score.final_disagreement` 이벤트 자체는 코드에 정의만 되어 있고 호출부가 아직 연결되지 않음 — [DIAGNOSTIC_EVENT_SCHEMA.md](./DIAGNOSTIC_EVENT_SCHEMA.md) 참고).

상세 SLA 수치, KataGo 명령 목록, 불일치 UX 원칙은 `SCORE_AND_ENDGAME_DECISION.md`를 따른다.

## 현재 옵션 화면 — 요약

플레이 화면 액션 버튼: `Pass`, `Undo`, `Best`(Top Moves 토글), `Eval`(ownership/gradient 토글, 켜질 때 score estimate를 다시 요청).

`Menu`(햄버거) 안의 4개 영역:

| 영역 | 핵심 옵션 |
| --- | --- |
| `Player Setup` | 흑/백 각각 사람·AI 선택, AI는 `빠른 초급`/`초급`/`중급`/`고급` + 단계, 둘 다 AI면 `Auto delay` |
| `Search Time` | `Time cap On/Off` + B16/B32/B64별 응답시간 cap |
| `Game` | `New`, `Copy Log`, `Benchmark`, `Scoring rule: Area | Territory` |
| `Display menu` | `Coords`, `Move nums`, `Last ring` (ownership gradient는 여기 없고 `Eval` 액션 버튼이 담당) |

세부 동작, 기본값, 저장/복원 정책은 `USER_OPTION_MANUAL.md`를 따른다.

## 진단/런타임 로그 — 요약

두 가지 별개 로그 파일이 있다.

| 파일 | 형식 | 목적 |
| --- | --- | --- |
| `diagnostic_events.jsonl` | JSON, 한 줄에 한 이벤트 | slow/timeout/discarded/visit-fill-short처럼 이상 신호만 |
| `runtime_events.log` | 평문 `key=value` | 거의 모든 턴 전환을 narrative로 기록 (20개 event 종류) |

개발자가 원격 기기 로그를 볼 때는 ADB `run-as`로 두 파일을 함께 받는다.

```bash
adb shell run-as com.worksoc.goaicoach cat files/diagnostic_events.jsonl
adb shell run-as com.worksoc.goaicoach cat files/runtime_events.log
```

이벤트 종류 전체 목록, 필드 스키마, 외부 전송(Firebase/Sentry 등) 정책은 `DIAGNOSTIC_EVENT_SCHEMA.md`를 따른다.

## 더 깊은 문서

| 문서 | 다룰 내용 |
| --- | --- |
| `archive/2026-06-17-early-decisions/STACK_DECISION.md` | KMP vs Flutter 전체 비교 |
| `SCORE_AND_ENDGAME_DECISION.md` | 계가/종국 SLA, 부심·주심 불일치 UX, KataGo raw 명령 목록 |
| `USER_OPTION_MANUAL.md` | 모든 메뉴/버튼의 상세 동작, 기본값, 저장 정책 |
| `DIAGNOSTIC_EVENT_SCHEMA.md` | diagnostic JSONL 스키마 + runtime event log 20종 전체 목록 |
