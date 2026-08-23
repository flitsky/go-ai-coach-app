package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHistoryCodecTest {
    private fun sampleEntry(id: String = "1000-1") = GameHistoryEntry(
        id = id,
        playedAtMillis = 1_000L,
        boardSize = 9,
        ruleset = Ruleset.Chinese,
        komi = 6.5,
        handicapCount = 2,
        playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        ),
        moveCount = 84,
        winner = StoneColor.Black,
        margin = 3.5,
    )

    @Test
    fun roundTripRestoresAllFields() {
        val encoded = GameHistoryCodec.encodeAll(listOf(sampleEntry()))

        val decoded = GameHistoryCodec.decodeAll(encoded)

        assertEquals(listOf(sampleEntry()), decoded)
    }

    @Test
    fun roundTripPreservesNullWinnerAndMargin() {
        val jigo = sampleEntry(id = "jigo").copy(winner = null, margin = null)

        val decoded = GameHistoryCodec.decodeAll(GameHistoryCodec.encodeAll(listOf(jigo)))

        assertEquals(listOf(jigo), decoded)
        assertNull(decoded.single().winner)
        assertNull(decoded.single().margin)
    }

    @Test
    fun appendPreservesOrderAcrossMultipleEntries() {
        val entries = listOf(sampleEntry("a"), sampleEntry("b"), sampleEntry("c"))

        val decoded = GameHistoryCodec.decodeAll(GameHistoryCodec.encodeAll(entries))

        assertEquals(listOf("a", "b", "c"), decoded.map { it.id })
    }

    @Test
    fun decodeAllReturnsEmptyListForUnknownSchema() {
        val raw = """{"schema":999,"entries":[]}"""

        assertTrue(GameHistoryCodec.decodeAll(raw).isEmpty())
    }

    @Test
    fun decodeAllReturnsEmptyListForGarbageInput() {
        assertTrue(GameHistoryCodec.decodeAll("not json").isEmpty())
    }
}
