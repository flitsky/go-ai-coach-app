package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 캐릭터 문구의 회귀 그물(백로그 #32).
 *
 * ⚠️ **`UiStringsTest`의 그물이 이것을 못 잡는다.** 그쪽은 [UiStrings]의 String **필드**를
 * 리플렉션으로 훑는데, 캐릭터 이름·설명은 `UiStringsBotCharacters.kt`의 표를 읽는 **함수**로
 * 나온다 — 필드가 아니라서 시야에 아예 들어오지 않는다. 원래 결함도 정확히 그 사각지대에서
 * 났다: 한글이 `UiStrings`가 아니라 `shared`의 도메인 데이터에서 흘러들었다.
 */
class UiStringsBotCharacterTest {

    /**
     * 카탈로그에 캐릭터를 더하고 문구 표를 빠뜨리면 화면에 `fast_beginner_6`처럼 id가 그대로
     * 뜬다. 조회부가 그렇게 폴백하도록 되어 있으므로(눈에 띄라고 그렇게 뒀다) **컴파일도 통과하고
     * 예외도 안 난다** — 여기서만 잡힌다.
     */
    @Test
    fun everyCatalogCharacterHasCopyInEveryLanguage() {
        val missing = BotCharacterCatalog.all.flatMap { character ->
            UiLanguage.entries.flatMap { language ->
                val name = botCharacterNameFor(language, character.id)
                val description = botCharacterDescriptionFor(language, character.id)
                buildList {
                    if (name == character.id.raw) add("${language.name} 이름: ${character.id.raw}")
                    if (description == character.id.raw) add("${language.name} 설명: ${character.id.raw}")
                }
            }
        }

        assertEquals(
            "문구 표에 빠진 항목이 있다 = 화면에 id가 그대로 보인다. " +
                "`ui/UiStringsBotCharacters.kt`에 채워라:\n" + missing.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            missing,
        )
    }

    /**
     * 그물의 본체. **한국어 아닌 언어에 한글이 남았는가**만 본다 — `UiStringsTest`가 필드에 대해
     * 쓰는 것과 같은 불변식이고, 같은 판정기([containsHangul])를 쓴다.
     */
    @Test
    fun nonKoreanCharacterCopyNeverFallsBackToKorean() {
        // 자기검증. 한국어에서 한글이 안 잡히면 아래 검사는 아무것도 안 보면서 통과한다.
        val koreanCopy = BotCharacterCatalog.all.flatMap { character ->
            listOf(
                botCharacterNameFor(UiLanguage.Korean, character.id),
                botCharacterDescriptionFor(UiLanguage.Korean, character.id),
            )
        }
        assertTrue(
            "한국어 캐릭터 문구에서 한글이 하나도 안 잡혔다 — 표를 읽고 있는지부터 의심할 것.",
            koreanCopy.count { it.containsHangul() } == koreanCopy.size,
        )

        val leaks = BotCharacterCatalog.all.flatMap { character ->
            UiLanguage.entries.filter { it != UiLanguage.Korean }.flatMap { language ->
                listOf(
                    "이름" to botCharacterNameFor(language, character.id),
                    "설명" to botCharacterDescriptionFor(language, character.id),
                )
                    .filter { (_, value) -> value.containsHangul() }
                    .map { (kind, value) -> "${language.name} ${character.id.raw} $kind = \"$value\"" }
            }
        }

        assertEquals(
            "비한국어 캐릭터 문구에 한글이 남아 있다:\n" + leaks.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            leaks,
        )
    }

    /**
     * 위 그물은 "한글만 아니면 통과"라 일본어 자리에 영어를 박아도 잡지 못한다. 그래서 실제로
     * 샜던 줄 하나를 값까지 못 박는다 — **`관장 천원 (達人)`** 이 이 항목을 발행하게 만든 화면이다.
     * 이름은 한글, 티어명은 일본어로 한 줄 안에 두 언어가 섞여 있었다.
     */
    @Test
    fun thePickerLabelNoLongerMixesTwoLanguagesInOneLine() {
        val top = requireNotNull(BotCharacterCatalog.byRawId("fast_beginner_5"))

        assertEquals("관장 천원 (초고수)", UiStringsKorean.botCharacterLabel(top))
        assertEquals("Tengen the Master (Master)", UiStringsEnglish.botCharacterLabel(top))
        assertEquals("館長 天元 (達人)", UiStringsJapanese.botCharacterLabel(top))
        assertEquals("馆长 天元 (大神)", UiStringsChineseSimplified.botCharacterLabel(top))
    }

    /**
     * 이름은 **직함 + 그 언어권의 실제 바둑 용어**로 지었다(표 KDoc 참고). 기계 음역으로 되돌아가면
     * 말맛이 사라지므로, 서열이 드러나는 1·5단계 이름을 네 언어 모두 못 박아 둔다.
     */
    @Test
    fun characterNamesKeepTheDojoLadderInEveryLanguage() {
        val first = BotCharacterId("fast_beginner_1")
        assertEquals("첫돌이", botCharacterNameFor(UiLanguage.Korean, first))
        assertEquals("Pebble", botCharacterNameFor(UiLanguage.English, first))
        assertEquals("初石", botCharacterNameFor(UiLanguage.Japanese, first))
        assertEquals("初子", botCharacterNameFor(UiLanguage.ChineseSimplified, first))

        val last = BotCharacterId("fast_beginner_5")
        assertEquals("관장 천원", botCharacterNameFor(UiLanguage.Korean, last))
        assertEquals("Tengen the Master", botCharacterNameFor(UiLanguage.English, last))
        assertEquals("館長 天元", botCharacterNameFor(UiLanguage.Japanese, last))
        assertEquals("馆长 天元", botCharacterNameFor(UiLanguage.ChineseSimplified, last))
    }

    /** 모르는 id는 예외가 아니라 id 문자열로 떨어진다 — 조용히 빈칸이 되지 않게 한 선택이다. */
    @Test
    fun anUnknownIdFallsBackToTheIdItselfRatherThanBlank() {
        val unknown = BotCharacterId("not_in_the_catalog")

        UiLanguage.entries.forEach { language ->
            assertEquals("not_in_the_catalog", botCharacterNameFor(language, unknown))
            assertEquals("not_in_the_catalog", botCharacterDescriptionFor(language, unknown))
        }
    }

    /**
     * ⚠️ **1단계를 약한 상대로 소개하지 않는다**(2026-08-31 사용자 지시). 실기력이 일반 중급자를
     * 상회하는데 "일부러 자주 실수한다"고 적어 두면 첫 판에서 진 사용자가 속았다고 느낀다 —
     * 실제로 그 문구가 한동안 남아 있었고, 랜딩(#51)이 같은 원칙을 세운 뒤로는 **한 앱 안에서 두
     * 말이 공존**하는 상태였다. 랜딩 쪽 그물(`UiStringsLandingTest`)과 짝을 이룬다.
     */
    @Test
    fun theEntryOpponentIsNeverIntroducedAsWeak() {
        val dismissive = listOf(
            "일부러", "자주 실수", "약한", "쉬운",
            "on purpose", "loose", "weak", "easy",
            "わざと", "弱い", "簡単",
            "故意", "下错", "弱", "简单",
        )
        val firstTier = BotCharacterCatalog.fastBeginnerRoster.first().id

        UiLanguage.entries.forEach { language ->
            val blurb = botCharacterDescriptionFor(language, firstTier)
            dismissive.forEach { word ->
                assertFalse(
                    "$language: 1단계 소개가 상대를 얕잡아 말한다('$word'): $blurb",
                    blurb.contains(word, ignoreCase = true),
                )
            }
        }
    }
}
