# 엔진 측정 로그 — 읽는 법

이 폴더는 **원시 측정 데이터**다. 결론이 아니라 근거이고, 결론은 전부 다른 문서나 코드에 흡수돼 있다.
`docs/DOCS_INDEX.md`의 "문서 구조 정책"이 이 폴더를 **깊이 규칙 적용 대상 밖**으로 두고 있다.

작성: 2026-09-23

---

## ⚠️ 먼저 — 레벨 표기는 **측정 당시 기준**이다

로그 본문의 `빠른 초급 3단계`·`초급 7단계`·`중급 5단계` 같은 표기를 **현행 UI의 같은 이름과 동일시하면 안 된다.**

- 2026-06 측정 시점의 `빠른 초급`은 **3단계까지**였고, `3단계`는 그 그룹의 **최상단**이었다.
- 2026-09-23 기준 `PlayLevel.kt`의 `PlayLevelGroup.FastBeginner.maxLevel`은 **5**다.
  즉 같은 `빠른 초급 3단계`가 지금은 **중간 단계**를 가리킨다.

로그를 재해석할 때는 이름이 아니라 **함께 적힌 `B16`/`B32`/`B64`(visits)와 time cap**을 기준으로 대조하라.
그 둘은 측정 시점과 현재가 같은 뜻이다.

---

## 폴더 → 근거가 흡수된 곳

| 폴더 | 무엇을 쟀나 | 결론이 흡수된 곳 |
| --- | --- | --- |
| `engine-benchmark/phone-gameplay-20260611/` | 벤치마크는 느린데 실제 대국은 쾌적한 이유 | `docs/engine/ENGINE_API_CALL_POLICY.md` "benchmark 해석" 절 — *startup benchmark는 대국 속도 예측용이 아니라 `timeCapMs=5000` 진단값* |
| `engine-benchmark/phone-autoplay-diagnostic-20260611/` | 자동대국이 너무 빨리 끝나는 원인 1차 진단 | 원인이 KataGo 내부 탐색 트리 재사용이라는 판단 → 아래 두 폴더의 수정으로 이어짐 |
| `engine-benchmark/phone-autoplay-fastgame-20260611-203429/` | 같은 수순이 반복 재생되는 현상 포착 | **코드**: `startNewEngineGame()`이 `clear_board`가 아니라 `stop()` → `initialize()`를 거치도록 바뀜 |
| `engine-benchmark/phone-autoplay-freshprocess-20260611-203855/` | 위 프로세스 재시작 수정의 검증 | 위와 같음. "같은 판 안에서 B64 직후 B16이 즉시 반환" 은 남은 문제로 기록 |
| `engine-benchmark/phone-autoplay-clearcache-20260611-204836/` | AI 착수 직전 캐시 비우기 검증 | **코드**: `EngineCoreApi.clearSearchCache()` (`shared/.../EngineModels.kt`), 호출부는 `match/MatchTurnOrchestration.kt` |
| `engine-benchmark/emulator-pixel7-20260610/` | Pixel 7 에뮬레이터 startup benchmark | `fill=UNKNOWN` 원인(GTP fast path에 visit 진단 문자열 없음) → **코드**: `KataGoAnalysisParser.parseRootVisitsEstimate()`, 사용처 `KataGoGtpAnalysisClient` |
| `engine-benchmark/mac-20260610/`, `mac-b16best3-20260610/` | 맥 로컬 KataGo 기준선 | 측정 포지션 규약이 코드 상수 `EngineBenchmarkPositionName`(`b16-best-3-variants`)과 `scripts/run-katago-device-benchmark.py`로 고정됨. 설명은 `docs/spec/USER_OPTION_MANUAL.md` |
| `engine-benchmark/search-mode-{mac,phone}-20260613/` | 엔진 검색 모드 2종 비교 | `docs/ENGINE.md` — 결론 표가 본문에 있다(원본 로드맵 문서는 2026-08-17 보존 정책 전환으로 제거됨) |
| `engine-benchmark/candidate-refine-mac-20260817.{md,json}` | 후보수 refine 측정 | `docs/engine/ENGINE_STRENGTH_RESEARCH.md` |
| `engine-match/` 전체 | 레벨 간 상대 기력(승률·Elo) 대국 매트릭스 | `docs/engine/ENGINE_STRENGTH_RESEARCH.md` — 경로를 직접 인용한다. **지우지 말 것** |

---

## 2026-09-23에 지운 것

`phone-gameplay-20260611/`의 스크린샷 14장·logcat 6개·uiautomator XML 3개와
`emulator-pixel7-20260610/logcat-tail.txt`를 제거했다(약 11MB). 각 `summary.md`가 그 자리에 복원 방법을 적어 두었다.

근거: 수치는 전부 `summary.md` 본문 표로 전사돼 있고, 스크린샷은 2026-06-11 당시 UI라
현행 UI와 비교할 수 없다. 원본은 git 히스토리에 그대로 있다(당시 경로는 `docs/engine-benchmark-logs/`).

**남긴 것**과 그 이유:

- 모든 `summary.md` — 결론 본체
- `*.json` benchmark profile, `samples.jsonl` — 표로 전사된 수치의 원본
- `runtime_event_log*.txt` — 자동대국 요약이 **이 파일을 소스로 명시**한다
- `shared_prefs_*.xml`, `user_preferences.xml` — 아래 참고

### ⚠️ `user_preferences.xml` 두 개는 서로 다르다 — 둘 다 남긴다

- `phone-autoplay-fastgame-20260611-203429/user_preferences.xml` — `searchTimeSettings`(B16/B32/B64 time cap)를 **담고 있는 유일한 스냅샷**이다
- `phone-gameplay-20260611/shared_prefs_go_ai_coach_user_preferences.xml` — `searchTimeSettings`가 **없고** playerSetup도 다르다

둘 다 `"schema":1` 세대의 실제 저장 형태라 회귀 대조에 쓸 수 있다. 중복이 아니다.

---

## ⚠️ `Makefile`의 `search-mode-phone-latest`는 실재하지 않는다

`Makefile`의 `ENGINE_PHONE_SEARCH_MODE_BENCHMARK_OUT` 기본값이
`docs/engine/measurements/engine-benchmark/search-mode-phone-latest`를 가리키지만,
**2026-09-23 기준 그 폴더는 저장소에 없다.** 실재하는 폰 측정은 `search-mode-phone-20260613/` 하나뿐이다.

이것은 깨진 참조가 아니라 **출력 경로**다 — `make engine-search-mode-benchmark-phone`을 기본값으로 돌리면
그때 새로 만들어진다. 다만 `ENGINE_API_CALL_POLICY.md`의 "원격 폰 엔진 벤치마크 표준" 절은
`search-mode-phone-YYYYMMDD` 형태로 **날짜를 박아 넘기라고** 안내하므로, 문서를 따르면 `-latest`는 영영 안 생긴다.
→ 기본값과 문서 관례가 어긋나 있다. 실행할 때는 **문서 쪽(날짜 표기)을 따르라.**
