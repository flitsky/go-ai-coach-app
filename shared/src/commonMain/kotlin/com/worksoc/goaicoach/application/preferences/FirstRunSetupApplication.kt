package com.worksoc.goaicoach.application.preferences

/**
 * 첫 실행을 **묻지 않고** 끝낸다(백로그 #140, 2026-09-11 사용자 결정).
 *
 * 예전에는 랜딩(#51)이 실력과 계가 방식을 물었다. 사용자 피드백으로 그 화면을 없앴고, 첫 판은
 * **첫돌이와 호선, 집 계가**로 시작한다 — *"저와 함께 호선으로 둬봐요. 결과 보시고 접바둑으로
 * 조정도 가능하답니다."*(대국 설정의 첫돌이 안내, `UiStringsGuide.kt`).
 *
 * ## ⚠️ 설정을 덮어쓰지 않는다 — "봤다"는 사실만 남긴다
 * 신규 설치의 기본값([UserPreferencesSnapshot]·`PlayerSetup`·`PlayLevelSetting`)이 **이미**
 * 13줄 · 흑 사람 · 백 첫돌이(빠른 초급 1단계) · 호선 · 집 계가다. 그래서 여기서 그 값을 다시 쓸
 * 필요가 없고, 쓰지 **말아야** 한다: `hasSeenOnboarding`이 꺼진 채 설정을 가진 사람이 있을 수
 * 있다(랜딩 이전 빌드에서 곧장 올라온 사용자). 옛 랜딩의 *'나중에 할게요'* 갈래가 같은 이유로
 * 설정을 건드리지 않았고, 이 함수는 그 갈래와 같다.
 * ⚠️ 그래서 **첫 판의 모양은 기본값이 정한다** — 기본값을 바꾸면 신규 사용자의 첫 판이 함께
 * 바뀐다. `FirstRunSetupApplicationTest`가 그 기본값을 사용자 결정 그대로 못박는다.
 *
 * **순수 함수다** — 저장과 가이드 무장은 호출부(`FirstRunGate`)가 한다.
 */
fun completeFirstRun(current: UserPreferencesSnapshot): UserPreferencesSnapshot =
    current.copy(hasSeenOnboarding = true)
