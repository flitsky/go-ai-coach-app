package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EngineResponseParserTest {

    @Test
    fun testFormatOneDecimal() {
        assertEquals("5.4", 5.37.formatOneDecimal())
        assertEquals("5.0", 5.0.formatOneDecimal())
        assertEquals("-2.4", (-2.45).formatOneDecimal())
    }

    @Test
    fun testExtractScoreLead() {
        val rawText = "someMessage\nscoreLead=5.41\notherMessage"
        assertEquals(5.41, extractScoreLead(rawText))

        val negativeScoreText = "scoreLead=-12.5"
        assertEquals(-12.5, extractScoreLead(negativeScoreText))

        val noScoreText = "no lead info here"
        assertNull(extractScoreLead(noScoreText))
    }

    @Test
    fun testExtractAiSelectedRank() {
        val rawText = "AI selected rank 1/15 with probability 95%"
        assertEquals("1/15", extractAiSelectedRank(rawText))

        val rankWithWhitespace = "AI Selected rank 3/8"
        assertEquals("3/8", extractAiSelectedRank(rankWithWhitespace))

        val invalidText = "AI did some other stuff"
        assertNull(extractAiSelectedRank(invalidText))
    }

    @Test
    fun testExtractVisitDiagnostics() {
        val log = "Visit diagnostics: request=16, root=15, elapsedMs=3354, timeCapMs=5000, fill=SHORT"
        val parsed = extractVisitDiagnostics(log)
        assertNotNull(parsed)
        assertEquals("request=16, root=15, elapsedMs=3354, timeCapMs=5000, fill=SHORT", parsed)

        val invalidLog = "no diagnostics log line"
        assertNull(extractVisitDiagnostics(invalidLog))
    }

    @Test
    fun testInferAnalysisOwner() {
        // 명시적 매치 1: "for Black"
        val explicitBlack = "this is analysis for Black on board size 19"
        assertEquals(StoneColor.Black, inferAnalysisOwner(explicitBlack))

        // 명시적 매치 2: "for White"
        val explicitWhite = "this is analysis for White"
        assertEquals(StoneColor.White, inferAnalysisOwner(explicitWhite))

        // 순서 번호 매치: "1. Black"
        val numberBlack = "1. Black has 98% winRate\n2. White has 2%"
        assertEquals(StoneColor.Black, inferAnalysisOwner(numberBlack))

        // 매치 실패
        val failureText = "no owner specified here"
        assertNull(inferAnalysisOwner(failureText))
    }

    @Test
    fun testFormatCandidateLineCompact() {
        val originalLine = "  C4 visits=100 prior=80% - 1.5 points loss"
        // visits=100 prior=80% 과 - 이탈
        assertEquals("C4", formatCandidateLineCompact(originalLine))

        val simpleLine = "D10 visits=50 prior=12%"
        assertEquals("D10", formatCandidateLineCompact(simpleLine))
    }
}
