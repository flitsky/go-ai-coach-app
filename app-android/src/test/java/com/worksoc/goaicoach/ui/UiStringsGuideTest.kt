package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.guide.GuideStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    private val toolLabels = GuideToolLabels(
        eval = "EVAL",
        topMoves = "TOPMOVES",
    )

    private fun bodyOf(language: UiLanguage, step: GuideStep) =
        guideBodyFor(language, step, toolLabels = toolLabels)

    /** (한때 ①은 문구 없는 장식이라 여기서 빠졌다 — #140이 ①을 없애 이제 모든 단계가 말한다.) */
    private val playableSteps = GuideStep.entries

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
     * ⚠️ **마이페이지 인사는 단계가 아니라서 위 그물이 못 본다** — 그런데 표(`Map`)에서
     * `getValue`로 꺼내므로 언어 키가 하나 빠지면 **화면이 예외로 죽는다**(빈 문구가 아니다).
     * 늘 보이는 한 줄이라 그 언어 사용자는 마이페이지를 아예 열 수 없다.
     */
    @Test
    fun theMyPageGreetingExistsInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            val greeting = guideMyPageGreetingFor(language)
            assertTrue(
                "${language.name} 마이페이지 인사가 비어 있다 — 표에 키가 없으면 `getValue`가 " +
                    "던져 화면이 죽는다(백로그 #128).",
                greeting.isNotBlank(),
            )
        }
    }

    /**
     * ⚠️ **인자를 안 넘기면 시끄럽게 실패해야 한다.**
     *
     * 2026-09-09까지 `guideBodyFor`가 `toolLabels?.x ?: ""` 로 **조용히 폴백**해서, ⑤가 라벨 없이
     * *"«»를 켜 두면…"* 이라는 빈 인용부호로 나갔다(④도 같은 폴백으로 5점 접바둑 사용자에게 *"호선으로
     * 맞춰 뒀어요"* 라고 말했었다 — #140이 ④를 인자 없는 한 문구로 바꿔 그 갈래는 사라졌다).
     * 폴백을 없앤 것이 되돌려지지 않게 못박는다 — 조용한 거짓말보다 시끄러운 실패가 낫다.
     */
    @Test
    fun theCopyRefusesToGuessWhatItWasNotGiven() {
        UiLanguage.entries.forEach { language ->
            GuideStep.entries.filter { it.target != null }.forEach { step ->
                assertThrows(IllegalArgumentException::class.java) {
                    guideBodyFor(language, step)
                }
            }
        }
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
     * ⚠️ **④는 사용자가 준 문구 그대로다**(백로그 #140, 2026-09-11: *"제가 제안한 문구를 다국어로
     * 번역 적용"*). 한국어는 글자 하나까지, 줄바꿈까지 그 문구다 — 다듬고 싶어지면 사용자에게 먼저 묻는다.
     * 다른 세 언어도 **두 줄**이다: 권유 한 줄, 접바둑으로 조정할 수 있다는 안내 한 줄.
     */
    @Test
    fun theMatchSetupCopyIsTheUsersOwnWordsInTwoLines() {
        assertEquals(
            "저와 함께 호선으로 둬봐요.\n결과 보시고 접바둑으로 조정도 가능하답니다.",
            guideBodyFor(UiLanguage.Korean, GuideStep.MatchSetup),
        )
        UiLanguage.entries.forEach { language ->
            val lines = guideBodyFor(language, GuideStep.MatchSetup).split("\n")
            assertEquals("${language.name} ④ 문구가 두 줄이 아니다: $lines", 2, lines.size)
            assertTrue("${language.name} ④ 문구에 빈 줄이 있다: $lines", lines.none { it.isBlank() })
        }
    }

    /**
     * ⚠️ **한글 판정기의 사각지대: 다른 언어에 섞인 영어 낱말.**
     *
     * 2026-09-09 감사가 일본어 ⑤에서 *"私が good と見る5か所"* 를 집어냈다 — `good`이 그대로
     * 박혀 있었고, [nonKoreanGuideCopyCarriesNoHangul]은 **한글만** 보므로 통과시켰다. 번역을
     * 절반만 한 흔적은 대체로 이렇게 남는다.
     *
     * ⚠️ 라벨은 **호출부가 넘긴 값**이라(테스트에서 `MAGNIFIER` 같은 ASCII를 쓴다) 검사 전에
     * 걷어낸다. 한국어도 함께 본다 — 한국어 문구에 영어 낱말이 남는 것도 같은 실수다.
     */
    @Test
    fun copyInAKoreanOrCjkLanguageCarriesNoStrayEnglishWord() {
        val latinWord = Regex("""[A-Za-z]{2,}""")
        // 자기검증: 영어 문구에서는 반드시 걸려야 한다 — 걸리지 않으면 이 검사기가 죽어 있다.
        assertTrue(
            "영어 문구에서 라틴 낱말을 못 찾았다 — 이 그물이 문구를 읽고 있지 않다.",
            playableSteps.all { step -> latinWord.containsMatchIn(stripLabels(bodyOf(UiLanguage.English, step))) },
        )

        val leaks = UiLanguage.entries
            .filter { it != UiLanguage.English }
            .flatMap { language ->
                playableSteps.mapNotNull { step ->
                    val body = stripLabels(bodyOf(language, step))
                    latinWord.find(body)?.let { "${language.name}.${step.name} = \"${it.value}\"" }
                }
            }
        assertEquals(
            "비영어 문구에 영어 낱말이 남아 있다 = 번역을 절반만 했다(백로그 #128):\n" +
                leaks.joinToString("\n"),
            emptyList<String>(),
            leaks,
        )
    }

    /** 호출부가 넘긴 라벨은 문구의 몫이 아니다 — 검사 전에 걷어낸다. */
    private fun stripLabels(body: String): String =
        listOf(toolLabels.eval, toolLabels.topMoves)
            .fold(body) { text, label -> text.replace(label, "") }

    /**
     * ⚠️ **⑤ 추천 수 문구는 개수를 말하지 않는다**(2026-09-09 사용자 지적).
     *
     * 네 언어가 *"자리 다섯 곳 / five spots / 5か所 / 五个点"* 이라고 적고 있었는데, 5는 엔진에
     * **요청하는** 후보 수일 뿐이다 — 화면에 찍히는 것은 엔진이 실제로 점수를 매긴 것이라
     * 끝내기·좁은 판에서는 그보다 적다. 하나만 떠도 문구는 참이어야 한다.
     *
     * ⚠️ 숫자 전부를 금지할 수는 없다 — 같은 문장이 *"1회권"* 을 말한다. 그래서 **개수를 세는
     * 낱말**만 막는다.
     */
    @Test
    fun theTopMovesCopyNeverPromisesAFixedNumberOfCandidates() {
        UiLanguage.entries.forEach { language ->
            val body = bodyOf(language, GuideStep.InGameTopMoves)
            CountWordsThatWouldOverpromise.forEach { word ->
                assertTrue(
                    "${language.name} ⑤ 문구가 '$word'라고 개수를 못박는다 — 엔진이 그보다 적게 " +
                        "돌려주는 자리(끝내기·좁은 판)에서 거짓이 된다(2026-09-09 사용자 지적).",
                    // ⚠️ `ignoreCase` 없이는 영어가 문장 첫머리에서 *"Five spots…"* 로 돌아올 때
                    // 그물을 그대로 빠져나간다 — 목록이 소문자 `five`만 들고 있기 때문이다.
                    !body.contains(word, ignoreCase = true),
                )
            }
        }
    }

    private companion object {
        /** ⑤가 개수를 세면 나타나는 낱말들 — 네 언어에서 실제로 쓰였던 것 그대로. */
        val CountWordsThatWouldOverpromise =
            listOf("다섯", "five", "5か所", "五个", "五個", "5곳", "5개", "다섯 곳")
    }
}
