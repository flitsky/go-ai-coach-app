package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.guide.GuideSetupFacts
import com.worksoc.goaicoach.application.guide.GuideStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가이드 문구(백로그 #128)의 **번역 누락과 빈 문구**를 잡는다.
 *
 * ## ⚠️ 리플렉션 그물이 이 문구를 못 본다
 *
 * `UiStringsTest.nonKoreanUiStringsNeverInheritKoreanTextFromTheKoreanBaseline`은 `UiStrings`의
 * **String 필드**만 훑는다. 가이드 문구는 `UiStringsGuide.kt`에서 **함수로** 나오므로 그 그물의
 * 사각지대다 — `UiStringsBotCharacters`·`UiStringsStudyVideos`가 같은 처지라 손으로 그물을 달았고,
 * 이 파일이 가이드에 대해 같은 일을 한다.
 */
class UiStringsGuideTest {

    private val facts = GuideSetupFacts(handicapCount = 5, humanPlaysBlack = true)
    private val toolLabels = GuideToolLabels(
        magnifier = "MAGNIFIER",
        boardSubject = "BOARD",
        eval = "EVAL",
        topMoves = "TOPMOVES",
    )

    private fun bodyOf(language: UiLanguage, step: GuideStep) =
        guideBodyFor(language, step, facts = facts, toolLabels = toolLabels)

    /** ①은 문구가 없는 정적 장식이라 이 그물의 대상이 아니다. */
    private val playableSteps = GuideStep.entries.filter { it != GuideStep.Landing }

    /**
     * ⚠️ **빈 문구는 조용하다** — 말풍선이 뜨고 글자만 없는 화면이 되고, 그것을 알아채는 유일한
     * 방법은 그 단계를 실기에서 보는 것이다. 실제로 ⑤가 `toolLabels`를 못 받으면 `?: ""` 로
     * 빈 문자열을 돌려주는 갈래가 있다.
     */
    @Test
    fun everyPlayableStepSpeaksInEveryLanguage() {
        val blanks = UiLanguage.entries.flatMap { language ->
            playableSteps
                .filter { step -> bodyOf(language, step).isBlank() }
                .map { step -> "${language.name}.${step.name}" }
        }
        assertEquals("문구가 비어 있는 단계가 있다(백로그 #128):\n" + blanks.joinToString("\n"), emptyList<String>(), blanks)
    }

    /**
     * ⚠️ **한글 유출**: 비한국어 문구에 한글이 남아 있으면 번역을 빠뜨린 것이다.
     *
     * 자기검증을 먼저 둔다 — 필터가 조용히 0개를 집으면 아래 검사는 아무것도 보지 않으면서 통과한다.
     */
    @Test
    fun nonKoreanGuideCopyCarriesNoHangul() {
        val koreanBodies = playableSteps.map { step -> bodyOf(UiLanguage.Korean, step) }
        assertTrue(
            "한국어 문구에서 한글을 하나도 못 찾았다 — 이 그물이 문구를 읽고 있지 않다.",
            koreanBodies.count { it.containsHangul() } == playableSteps.size,
        )

        val leaks = UiLanguage.entries
            .filter { it != UiLanguage.Korean }
            .flatMap { language ->
                playableSteps
                    .filter { step -> bodyOf(language, step).containsHangul() }
                    .map { step -> "${language.name}.${step.name} = \"${bodyOf(language, step)}\"" }
            }
        assertEquals(
            "비한국어 가이드 문구에 한글이 남아 있다 = 번역을 빠뜨렸다(백로그 #128):\n" +
                leaks.joinToString("\n"),
            emptyList<String>(),
            leaks,
        )
    }

    /**
     * ⚠️ **⑤는 화면에 적힌 라벨을 인용해야 한다.** 실기에서 한 번 어긋났다 — 설정 화면 라벨
     * (`돋보기 창 크기`)을 인용했는데 판 위 토글은 `착수 돋보기`였고, 사용자는 문구가 말하는 것을
     * 화면에서 찾을 수 없었다. 그래서 문구가 **호출부가 넘긴 라벨을 실제로 끼워 넣는지** 확인한다.
     */
    @Test
    fun theInGameCopyQuotesTheLabelItWasGiven() {
        val expected = mapOf(
            GuideStep.InGameMagnifier to toolLabels.magnifier,
            GuideStep.InGameBoardSize to toolLabels.boardSubject,
            GuideStep.InGameEval to toolLabels.eval,
            GuideStep.InGameTopMoves to toolLabels.topMoves,
        )
        UiLanguage.entries.forEach { language ->
            expected.forEach { (step, label) ->
                assertTrue(
                    "${language.name}.${step.name} 문구가 넘겨받은 라벨($label)을 인용하지 않는다 — " +
                        "가이드가 화면과 다른 낱말로 부르면 사용자가 그것을 찾지 못한다(백로그 #128).",
                    bodyOf(language, step).contains(label),
                )
            }
        }
    }

    /**
     * ⚠️ **④는 기력을 말하지 않는다.** `applyLandingSetup`이 `SelfRatedSkill`을 버려서 앱이 그것을
     * 기억하지 않으므로, *"입문을 선택하셨으니"* 는 **오늘의 코드로 말할 수 없다**(2026-09-09 사용자
     * 결정: 언급 생략). 대신 살아 있는 접바둑 값을 인용한다 — 그 둘을 함께 못박는다.
     */
    @Test
    fun theMatchSetupCopyCitesTheLiveHandicapAndNeverTheSkill() {
        val skillWords = UiLanguage.entries.flatMap { language ->
            SkillWordsThatWouldBeAGuess.map { language to it }
        }
        skillWords.forEach { (language, word) ->
            assertTrue(
                "${language.name} ④ 문구가 '$word'를 말한다 — 앱은 사용자가 고른 기력을 저장하지 " +
                    "않으므로 그것은 추측이다(백로그 #128).",
                !bodyOf(language, GuideStep.MatchSetup).contains(word),
            )
        }
        UiLanguage.entries.forEach { language ->
            assertTrue(
                "${language.name} ④ 문구가 접바둑 점수(5)를 인용하지 않는다 — 살아 있는 설정을 " +
                    "말하기로 한 것이 이 문구의 근거다.",
                bodyOf(language, GuideStep.MatchSetup).contains("5"),
            )
        }
    }

    private companion object {
        /** 랜딩의 기력 보기 다섯(한국어) — 이 낱말이 ④에 나타나면 저장되지 않는 값을 말하는 것이다. */
        val SkillWordsThatWouldBeAGuess = listOf("입문", "초급", "중급", "상급", "최상급")
    }
}
