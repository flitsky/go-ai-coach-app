package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대국 흐름 문구의 누락·번역 그물(백로그 #175).
 *
 * ⚠️ 이 문구들은 `Map` + 함수라 `UiStringsTest`의 **리플렉션 그물 밖**이다(함정 10) — 그래서
 * 손 그물을 여기 단다. 표에 키가 빠지면 `getValue`가 던져 **그 언어 사용자는 대국 화면을 못 연다.**
 */
class UiStringsGameFlowTest {

    private val getters: Map<String, (UiLanguage) -> String> = mapOf(
        "rematchAction" to ::rematchActionFor,
        "exitGameAction" to ::exitGameActionFor,
        "passNoticeTitle" to ::passNoticeTitleFor,
        "scoreNowPromptTitle" to ::scoreNowPromptTitleFor,
        "scoreNowPromptBody" to ::scoreNowPromptBodyFor,
    )

    @Test
    fun everyGameFlowLabelExistsInEveryLanguage() {
        getters.forEach { (name, getter) ->
            UiLanguage.entries.forEach { language ->
                assertTrue(
                    "$name 이 ${language.name}에서 비어 있다 — `getValue`가 던져 대국 화면이 안 열린다.",
                    getter(language).isNotBlank(),
                )
            }
        }
    }

    @Test
    fun nonKoreanGameFlowLabelsNeverKeepKoreanText() {
        val leaks = getters.flatMap { (name, getter) ->
            UiLanguage.entries.filter { it != UiLanguage.Korean }
                .mapNotNull { lang -> getter(lang).takeIf { it.containsHangul() }?.let { "$name(${lang.name})=$it" } }
        }
        assertEquals("번역되지 않은 대국 흐름 문구가 있다(#175):\n" + leaks.joinToString("\n"), emptyList<String>(), leaks)
    }

    /**
     * ⚠️ **"예"가 무엇을 하는지 본문이 말해야 한다**(함정 39).
     *
     * 종국은 **연속 두 번 통과**이지 이 팝업이 아니다 — 본문이 침묵하면 사용자는 *"예를 누르면
     * 지금 끝나는구나"* 로 읽는데, 실제로 일어나는 일은 **자기 통과가 한 번 더 들어가는 것**이다.
     * 본문을 짧게 줄이려는 다음 사람이 이 사실을 지우지 못하게 한다.
     */
    @Test
    fun theScoringPromptExplainsThatYesMeansPassingToo() {
        val mustMentionPass = mapOf(
            UiLanguage.Korean to "통과",
            UiLanguage.English to "passes",
            UiLanguage.Japanese to "パス",
            UiLanguage.ChineseSimplified to "停一手",
        )
        mustMentionPass.forEach { (language, word) ->
            val body = scoreNowPromptBodyFor(language)
            assertTrue(
                "${language.name} 본문이 \"예 = 나도 통과\"라는 사실을 말하지 않는다: $body (#175)",
                body.contains(word),
            )
        }
    }

    /**
     * ⚠️ **종료 팝업과 하단 액션바는 서로 다른 문구여야 한다**(#175).
     * 하나로 합치면 `UiStringsTest`가 적어 둔 *"설정을 바꿔 놓고 눌러도 되므로 Rematch는 거짓이
     * 될 수 있다"* 는 문제가 되살아난다.
     */
    @Test
    fun theRematchLabelStaysDistinctFromTheBottomBarStartLabel() {
        UiLanguage.entries.forEach { language ->
            assertTrue(
                "${language.name}에서 종료 팝업과 하단 바 문구가 같아졌다 — 둘은 참이 되는 조건이 다르다(#175).",
                rematchActionFor(language) != UiStrings.forLanguage(language).newGameAction,
            )
        }
    }
}
