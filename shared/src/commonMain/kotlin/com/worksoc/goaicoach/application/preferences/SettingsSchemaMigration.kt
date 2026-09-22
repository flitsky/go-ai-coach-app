package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.shared.SearchTimeSettings

/**
 * **설정 세대**(백로그 #188) — 기본값이 바뀌었을 때 **이미 저장한 사용자에게도** 그것이 미치게 한다.
 *
 * ## ⚠️ 이것이 푸는 문제
 * 저장된 값은 늘 이긴다. 그래서 *"최대 탐색 시간 기본값을 3초 → 10초로 올린다"* 같은 결정이
 * **한 번이라도 설정을 저장한 사용자에게는 아무 일도 하지 않는다.** 새로 깐 사람과 오래 쓴 사람이
 * 다른 앱을 쓰게 되는데, 어느 쪽도 그 사실을 모른다.
 *
 * ## ⚠️ 전체 초기화가 **아니다**
 * 되돌리는 것은 아래 표가 **세대마다 이름을 적어 둔 필드뿐**이다. 사용자가 의도적으로 고른
 * 언어·글꼴 배율·판 크기·계가 방식은 그대로 둔다 — 전체를 밀면 *"내가 맞춘 것이 사라졌다"* 가 된다.
 *
 * ## ⚠️ 제거된 필드는 여기 적지 않는다
 * 스냅샷에서 필드가 빠지면 **저절로 사라진다**(읽을 때 그 키를 아무도 안 본다). 돋보기·지연 착수가
 * 그 경우다 — 적을 것이 없다.
 *
 * ## ⚠️ `ReleaseResetCoordinator`와 다른 축이다(함정 6)
 * 그쪽이 지우는 것은 **권한 저장소 넷**이다. 하나로 묶지 말 것 — 설정을 고칠 때마다 권한이 날아간다.
 */
const val CurrentSettingsSchemaGeneration: Int = 1

/**
 * [stored] 세대로 저장된 스냅샷을 지금 세대로 올린다. 이미 최신이면 **그대로 돌려준다**(같은 인스턴스).
 *
 * ⚠️ **돌려받은 값을 저장하는 것은 호출부의 몫이다** — 저장하지 않으면 앱을 켤 때마다 다시 돈다.
 */
fun migrateSettingsSchema(snapshot: UserPreferencesSnapshot): UserPreferencesSnapshot {
    if (snapshot.settingsSchemaGeneration >= CurrentSettingsSchemaGeneration) return snapshot

    var migrated = snapshot
    // ── 세대 1 (2026-09-22, 백로그 #188) ───────────────────────────────
    // 「최대 탐색 시간 제한」 기본값이 3초 → 10초로 바뀌었다. 엔진이 느린 기기에서 탐색이 잘려
    // 「엔진 응답 지연」 팝업이 뜨던 것을 줄이려는 변경이라, **옛 값에 머무른 사용자야말로**
    // 그 혜택을 봐야 한다.
    // ⚠️ 이 필드 하나뿐이다 — 같은 날 돋보기·지연 착수도 사라졌지만 그건 스냅샷에서 빠져
    //   저절로 없어진다(위 KDoc).
    if (snapshot.settingsSchemaGeneration < 1) {
        migrated = migrated.copy(searchTimeSettings = SearchTimeSettings())
    }

    return migrated.copy(settingsSchemaGeneration = CurrentSettingsSchemaGeneration)
}
