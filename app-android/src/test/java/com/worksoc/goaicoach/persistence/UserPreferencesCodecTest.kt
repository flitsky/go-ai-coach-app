package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
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
    }

    @Test
    fun missingOptionalFieldsUseUserFriendlyDefaults() {
        val restored = UserPreferencesCodec.decode("""{"schema":1}""")

        assertEquals(PlayerSetup(), restored?.playerSetup)
        assertEquals(Ruleset.Japanese, restored?.ruleset)
        assertTrue(restored?.topMovesEnabled ?: false)
        assertTrue(restored?.showCoordinates ?: false)
        assertFalse(restored?.showMoveNumbers ?: true)
        assertTrue(restored?.showLastMoveRing ?: false)
        assertTrue(restored?.showOwnershipOverlay ?: false)
        assertEquals(AutoPlayDelaySetting.Default.millis, restored?.autoPlayDelayMillis)
    }

    @Test
    fun invalidPayloadReturnsNull() {
        assertNull(UserPreferencesCodec.decode("{broken"))
        assertNull(UserPreferencesCodec.decode("""{"schema":99}"""))
    }
}
