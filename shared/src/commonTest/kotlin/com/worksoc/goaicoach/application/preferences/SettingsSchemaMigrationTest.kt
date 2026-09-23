package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 설정 세대 마이그레이션의 계약(백로그 #188).
 *
 * ⚠️ **이 조각이 없으면 기본값 변경이 기존 사용자에게 영영 안 미친다** — 저장된 값이 늘 이기므로,
 * 새로 깐 사람과 오래 쓴 사람이 다른 앱을 쓰게 되고 **어느 쪽도 그 사실을 모른다.**
 */
class SettingsSchemaMigrationTest {

    /**
     * ⚠️ **이 키가 생기기 전에 저장한 사용자가 세대 0이다** — 바로 그 사람들에게 돌아야 한다.
     * 옛 값(3초)이 10초로 올라가고, 세대가 현재로 찍힌다.
     */
    @Test
    fun anOldSnapshotGetsTheNewSearchTimeDefault() {
        val old = UserPreferencesSnapshot(
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds),
            settingsSchemaGeneration = 0,
        )

        val migrated = migrateSettingsSchema(old)

        assertEquals(SearchTimeLimit.WithinTenSeconds, migrated.searchTimeSettings.limit)
        assertEquals(CurrentSettingsSchemaGeneration, migrated.settingsSchemaGeneration)
    }

    /**
     * ⚠️ **전체 초기화가 아니다.** 사용자가 의도적으로 고른 값은 살아남아야 한다 — 밀어 버리면
     * *"내가 맞춘 것이 사라졌다"* 가 된다.
     */
    @Test
    fun theMigrationKeepsWhatTheUserDeliberatelyChose() {
        val old = UserPreferencesSnapshot(
            settingsSchemaGeneration = 0,
            appFontScale = 1.3f,
            showCoordinates = true,
            isBoardMaxSize = false,
            hasSeenOnboarding = true,
        )

        val migrated = migrateSettingsSchema(old)

        assertEquals(1.3f, migrated.appFontScale)
        assertTrue(migrated.showCoordinates)
        assertTrue(!migrated.isBoardMaxSize)
        assertTrue(migrated.hasSeenOnboarding)
    }

    /**
     * ⚠️ **올릴 것이 없으면 같은 인스턴스를 그대로 돌려준다** — 호출부(`UserPreferencesStore.load`)가
     * 그 동일성으로 *"저장해야 하는가"* 를 가른다. 매번 새 인스턴스를 만들면 앱을 켤 때마다
     * 쓸데없이 저장하고, 그 저장이 다른 필드를 덮을 위험을 만든다.
     */
    @Test
    fun anUpToDateSnapshotIsReturnedUntouched() {
        val current = UserPreferencesSnapshot(
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
        )

        assertSame(current, migrateSettingsSchema(current))
        assertEquals(
            SearchTimeLimit.WithinOneSecond,
            migrateSettingsSchema(current).searchTimeSettings.limit,
            "이미 최신인 스냅샷의 사용자 선택을 되돌렸다.",
        )
    }

    /** 두 번 돌려도 같아야 한다 — 저장에 실패해 다시 돌아도 값이 흔들리면 안 된다. */
    @Test
    fun theMigrationIsIdempotent() {
        val once = migrateSettingsSchema(UserPreferencesSnapshot(settingsSchemaGeneration = 0))
        assertEquals(once, migrateSettingsSchema(once))
    }
}
