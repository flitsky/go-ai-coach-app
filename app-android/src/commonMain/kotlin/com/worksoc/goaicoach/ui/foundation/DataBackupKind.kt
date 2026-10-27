package com.worksoc.goaicoach.ui.foundation

/**
 * 기기에 쌓인 기록(출석 · 캐릭터 · 1회권 · 대국 기록)이 어디로 백업되는가(백로그 #294 — 사용자 2026-10-27 「iOS 사실대로」).
 * 「내 기록은 어떻게 보관되나요」(`LocalOnlyDataNotice.kt`)가 이것으로 문구를 고른다 — 플랫폼마다 사실이 달라서다.
 */
enum class DataBackupKind {
    /** 안드로이드 Google 자동 백업 — 앱을 다시 설치하거나 기기를 바꿔도 마지막 백업 시점으로 돌아온다. 구독은 같은 Google Play 계정으로 복원. */
    GoogleAutoBackup,

    /** iPhone 기기 백업(iCloud · 컴퓨터) — 새 iPhone으로 옮기거나 백업에서 복원할 때만 돌아온다. 앱만 지웠다 다시 깔면 돌아오지 않는다. */
    IosDeviceBackup,
}
