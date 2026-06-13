package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
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
            searchTimeSettings = SearchTimeSettings(
                b16Millis = 1_500L,
                b32Millis = 4_000L,
                b64Millis = 7_500L,
                timeCapEnabled = false,
            ),
        )

        val restored = UserPreferencesCodec.decode(UserPreferencesCodec.encode(snapshot))

        assertEquals(setup, restored?.playerSetup)
        assertEquals(Ruleset.Chinese, restored?.ruleset)
        assertEquals(false, restored?.topMovesEnabled)
        assertEquals(false, restored?.showCoordinates)
        assertEquals(true, restored?.showMoveNumbers)
        assertEquals(false, restored?.showLastMoveRing)
        assertEquals(false, restored?.showOwnershipOverlay)
        assertEquals(AutoPlayDelaySetting.Slow.millis, restored?.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(1_500L, 4_000L, 7_500L, timeCapEnabled = false), restored?.searchTimeSettings)
    }

    @Test
    fun missingOptionalFieldsUseUserFriendlyDefaults() {
        val restored = UserPreferencesCodec.decode("""{"schema":1}""")

        assertEquals(PlayerSetup(), restored?.playerSetup)
        assertEquals(Ruleset.Japanese, restored?.ruleset)
        assertFalse(restored?.topMovesEnabled ?: true)
        assertTrue(restored?.showCoordinates ?: false)
        assertFalse(restored?.showMoveNumbers ?: true)
        assertTrue(restored?.showLastMoveRing ?: false)
        assertTrue(restored?.showOwnershipOverlay ?: false)
        assertEquals(AutoPlayDelaySetting.Default.millis, restored?.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(), restored?.searchTimeSettings)
    }

    @Test
    fun invalidPayloadReturnsNull() {
        assertNull(UserPreferencesCodec.decode("{broken"))
        assertNull(UserPreferencesCodec.decode("""{"schema":99}"""))
    }
}
