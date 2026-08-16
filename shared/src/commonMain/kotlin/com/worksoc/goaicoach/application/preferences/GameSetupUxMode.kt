package com.worksoc.goaicoach.application.preferences

/**
 * 대국 설정 화면(GameSetupLobby) 레이아웃 선택지. [Simple]은 각 설정을 행 단위로 펼쳐
 * 보여주는 기존 화면, [Compact]는 계가/덤/바둑판 크기/접바둑을 2x2 드롭다운 그리드로
 * 압축해 스크롤 없이 한눈에 보이게 한 화면이다 — 개발자 테스트 토글로 전환한다
 * ([SettingsScreen]). 첫 설치 기본값은 [Compact].
 */
enum class GameSetupUxMode {
    Simple,
    Compact,
}
