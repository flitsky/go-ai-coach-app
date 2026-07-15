package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesCodecTest {
    @Test
    fun roundTripRestoresPlayerSetupAndDisplayToggles() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Human,
                humanGameType = HumanGameType.Teaching,
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 3),
            ),
        )
        val snapshot = UserPreferencesSnapshot(
            playerSetup = setup,
            ruleset = Ruleset.Chinese,
            topMovesEnabled = false,
            showCoordinates = false,
            showMoveNumbers = true,
            showLastMoveRing = false,
            showOwnershipOverlay = false,
            autoPlayDelayMillis = AutoPlayDelaySetting.Slow.millis,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
        )

        val encoded = UserPreferencesCodec.encode(snapshot)
        val encodedJson = JSONObject(encoded)
        val restored = UserPreferencesCodec.decode(encoded)

        assertEquals(setup, restored?.playerSetup)
        assertEquals(Ruleset.Chinese, restored?.ruleset)
        assertEquals(false, restored?.topMovesEnabled)
        assertEquals(false, restored?.showCoordinates)
        assertEquals(true, restored?.showMoveNumbers)
        assertEquals(false, restored?.showLastMoveRing)
        assertEquals(false, restored?.showOwnershipOverlay)
        assertEquals(AutoPlayDelaySetting.Slow.millis, restored?.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds), restored?.searchTimeSettings)
        assertEquals(2, encodedJson.getInt("schema"))
        val encodedSearchTime = encodedJson.getJSONObject("searchTimeSettings")
        assertEquals(SearchTimeLimit.WithinFiveSeconds.name, encodedSearchTime.getString("limit"))
        assertFalse(encodedSearchTime.has("timeCapEnabled"))
        assertFalse(encodedSearchTime.has("b16Millis"))
        assertFalse(encodedSearchTime.has("b32Millis"))
        assertFalse(encodedSearchTime.has("b64Millis"))
    }

    @Test
    fun missingOptionalFieldsUseUserFriendlyDefaults() {
        val restored = UserPreferencesCodec.decode("""{"schema":1}""")

        assertEquals(PlayerSetup(), restored?.playerSetup)
        assertEquals(Ruleset.Japanese, restored?.ruleset)
        assertFalse(restored?.topMovesEnabled ?: true)
        assertFalse(restored?.showCoordinates ?: true)
        assertFalse(restored?.showMoveNumbers ?: true)
        assertTrue(restored?.showLastMoveRing ?: false)
        assertTrue(restored?.showOwnershipOverlay ?: false)
        assertEquals(AutoPlayDelaySetting.Default.millis, restored?.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(), restored?.searchTimeSettings)
    }

    @Test
    fun schemaOneMigratesLegacyOffToOff() {
        val restored = UserPreferencesCodec.decode(
            """
            {
              "schema": 1,
              "searchTimeSettings": {
                "timeCapEnabled": false,
                "b16Millis": 1500,
                "b32Millis": 4000,
                "b64Millis": 7500
              }
            }
            """.trimIndent(),
        )

        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), restored?.searchTimeSettings)
    }

    @Test
    fun schemaOneMigratesLargestLegacyCapToSupportedCeiling() {
        val restored = UserPreferencesCodec.decode(
            """
            {
              "schema": 1,
              "searchTimeSettings": {
                "timeCapEnabled": true,
                "b16Millis": 1500,
                "b32Millis": 4000,
                "b64Millis": 7500
              }
            }
            """.trimIndent(),
        )

        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinTenSeconds), restored?.searchTimeSettings)
    }

    @Test
    fun malformedLegacyLimitValuesFallBackToSafeDefaults() {
        val restored = UserPreferencesCodec.decode(
            """
            {
              "schema": 1,
              "searchTimeSettings": {
                "timeCapEnabled": true,
                "b16Millis": -1,
                "b32Millis": "invalid",
                "b64Millis": 0
              }
            }
            """.trimIndent(),
        )

        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds), restored?.searchTimeSettings)
    }

    @Test
    fun malformedOrMissingSchemaTwoLimitUsesSafeDefault() {
        val malformed = UserPreferencesCodec.decode(
            """{"schema":2,"searchTimeSettings":{"limit":"invalid"}}""",
        )
        val missing = UserPreferencesCodec.decode("""{"schema":2}""")

        assertEquals(SearchTimeSettings(), malformed?.searchTimeSettings)
        assertEquals(SearchTimeSettings(), missing?.searchTimeSettings)
    }

    @Test
    fun invalidPayloadReturnsNull() {
        assertNull(UserPreferencesCodec.decode("{broken"))
        assertNull(UserPreferencesCodec.decode("""{"schema":99}"""))
    }
}
