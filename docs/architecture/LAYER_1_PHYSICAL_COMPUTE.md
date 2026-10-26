# 1계층 — Physical Compute

go-ai-coach에서 이 계층이 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「1계층 — Physical Compute」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「1계층」 절과 「고도화 로드맵」 3번을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 계층 배정

`:shared`·`:core:*`에는 이 계층 패키지가 없다 — 전부 `engine-android`다(아래 「지금 위치」).

## 지금 위치

**위치**(2026-10-26 백로그 #293부터 `:engine-android`는 KMP 모듈 — 안드로이드 · iOS가 공용 코드를 함께 쓴다): `engine-android/src/commonMain/kotlin/com/worksoc/goaicoach/engine/android/KataGoProcessRuntime.kt`(CLI 인수 빌드, `interface EngineProcessRuntime`, 띄운 엔진 하나의 줄 쓰기 · 읽기 · 수명은 `EngineProcessPipes`) · 안드로이드의 프로세스 띄우기와 파일 검증은 `engine-android/src/androidMain/kotlin/com/worksoc/goaicoach/engine/android/LocalKataGoProcessRuntime.kt`(`ProcessBuilder` — 앱에서 프로세스를 만드는 유일한 곳). iOS는 자식 프로세스를 못 띄워 같은 엔진을 앱 안 스레드로 부른다(`app-ios/GoCoach/Engine/KataGoBridge.cpp`) · `EngineProcessLifecycle.kt`(지금 프로세스 자리 `EngineProcessSlot`과 값 객체 `EngineProcessHandle` — 기동은 수명 락 안, 폐기는 세대 CAS, refactor backlog #14). 2계층 어댑터는 핸들의 writer/reader만 쓰고 프로세스를 직접 만들지 않는다.

**재편 여부**: 기존 1계층은 `engine-android` 패키지 전체(어댑터 포함)였다. 이번 재정의에서는 **"실제 KataGo 바이너리 프로세스를 실행/관리하는 부분"만** 1계층으로 좁히고, 그 바이너리에 GTP/JSON으로 말을 거는 어댑터는 2계층으로 옮겼다 — "어디서 도는가"(1계층)와 "어떻게 그것과 통신하는가"(2계층)를 분리하기 위함.

## 갭과 남은 일

### 열려 있는 것

3. **1계층 — 물리 실행 환경 추상화**: 지금은 `KataGoProcessRuntime`이 "이 기기에서 프로세스 실행"만 가정한다. 원격 서버/피어 기기라는 "다른 물리 위치"를 1계층 개념에 맞게 명시적으로 표현할 방법을 정의(예: 실행 위치를 나타내는 값 타입).

- Stage F(실제 물리 분산 — 다른 기기에서 연산)의 정본은 `260818-_REMOTE_ENGINE_MQ_TRANSPORT.md`다. 그 문서 머리말(2026-08-29) 기준으로 파이썬 프로토타입은 끝났고, 앱 이식은 별도 승인 전이다.
