package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.attendance.AttendanceRewardPolicy
import com.worksoc.goaicoach.application.attendance.AttendanceReward
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 캐릭터 해금 힌트(`UiStrings.botUnlockHint`)의 회귀 그물 — 백로그 #64 ⓒ에서 **새로 만들었다.**
 *
 * ⚠️ **이 문구는 그동안 아무 그물도 없었다.** 백로그는 셋이 지킨다고 적었지만 실제로는 셋 다
 * 못 본다: `UiStringsTest`는 [UiStrings]의 String **필드**를 리플렉션으로 훑는데 힌트는 **함수**라
 * 백킹 필드가 없고, `UiStringsBotCharacterTest`는 캐릭터 **이름·설명**만 훑으며,
 * [containsHangul]은 판정기일 뿐 스스로 아무것도 훑지 않는다. 그 사각지대에서 실제로 **틀린
 * 문구가 오래 살아남았다**(아래 [theShardHintNoLongerPromisesAnAttendancePath] 참고).
 */
class UiStringsBotUnlockHintTest {

    private val languages = UiLanguage.entries

    /** 조각 경로 캐릭터 둘 — 돌뫼(5개)와 묘수(10개). 카탈로그가 단일 출처다. */
    private val shardCharacters = BotCharacterCatalog.shardPathCharacters()

    private fun shardSource(index: Int) =
        shardCharacters[index].unlockSource as BotUnlockSource.AdShards

    @Test
    fun everyUnlockSourceHasCopyInEveryLanguage() {
        val sources = listOf(
            BotUnlockSource.Attendance(tier = 7),
            BotUnlockSource.AdShards(required = 10),
            BotUnlockSource.Purchase,
        )
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            sources.forEach { source ->
                val hint = strings.botUnlockHint(source, shards = 0)
                assertTrue("$language / $source 힌트가 비었다", !hint.isNullOrBlank())
            }
        }
    }

    /** 기본 제공 캐릭터에는 안내할 것이 없다 — 여기서 빈 문자열이 나오면 카드에 빈 줄이 생긴다. */
    @Test
    fun theDefaultCharacterHasNoHintAtAll() {
        languages.forEach { language ->
            assertNull(
                "$language: 기본 제공 캐릭터에 힌트가 붙었다",
                UiStrings.forLanguage(language).botUnlockHint(BotUnlockSource.Default),
            )
        }
    }

    /**
     * 그물의 본체. **한국어 아닌 언어에 한글이 남았는가**만 본다 — `UiStringsTest`가 필드에 대해
     * 쓰는 것과 같은 불변식이고, 같은 판정기([containsHangul])를 쓴다.
     */
    @Test
    fun nonKoreanHintsNeverFallBackToKorean() {
        // 자기검증. 한국어에서 한글이 안 잡히면 아래 검사는 아무것도 안 보면서 통과한다.
        val korean = allHintsFor(UiLanguage.Korean)
        assertTrue("한국어 힌트에서 한글이 안 잡혔다 — 문구를 읽고 있는지부터 의심할 것.", korean.isNotEmpty())
        assertTrue(
            "한국어가 아닌 한국어 힌트: ${korean.filterNot { it.containsHangul() }}",
            korean.all { it.containsHangul() },
        )

        val leaks = languages.filter { it != UiLanguage.Korean }.flatMap { language ->
            allHintsFor(language).filter { it.containsHangul() }.map { "$language = \"$it\"" }
        }
        assertEquals(
            "비한국어 힌트에 한글이 남아 있다:\n" + leaks.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            leaks,
        )
    }

    /**
     * #64 ⓒ의 핵심. 조각을 모을수록 **남은 수가 줄어야** 한다 — 예전 문구는 진행과 무관하게
     * *"10개 필요"* 로 고정이라, 광고를 봐도 **글자가 아무 말도 하지 않았다**(사용자 제보).
     */
    @Test
    fun theShardHintCountsDownAsShardsAccrue() {
        val required = shardSource(1).required
        val strings = UiStringsKorean

        assertEquals(
            "조각 ${required}개를 모으면 열려요\n광고를 보면 1개씩 모여요",
            strings.botUnlockHint(shardCharacters[1].unlockSource, shards = 0),
        )
        assertEquals(
            "조각 ${required - 3}개 남았어요\n광고를 보면 1개씩 모여요",
            strings.botUnlockHint(shardCharacters[1].unlockSource, shards = 3),
        )
        assertEquals(
            "조각 1개 남았어요\n광고를 보면 1개씩 모여요",
            strings.botUnlockHint(shardCharacters[1].unlockSource, shards = required - 1),
        )
    }

    /**
     * 경계는 **0개와 1개 이상 둘뿐**이다. *"다 모았는데 아직 잠김"* 은
     * `BotCollectionState.withAdShard`가 필요 수를 채우는 순간 획득으로 넘겨 버려 도달할 수 없다 —
     * 그래도 저장값이 깨져 흘러들면 *"0개 남았어요"* 라는 거짓말을 하게 되므로 바닥을 1로 막았다.
     */
    @Test
    fun aBrokenStoreNeverProducesZeroOrNegativeRemaining() {
        val required = shardSource(0).required
        listOf(required, required + 5).forEach { shards ->
            assertEquals(
                "shards=$shards 에서 남은 수가 1로 막히지 않았다",
                "조각 1개 남았어요\n광고를 보면 1개씩 모여요",
                UiStringsKorean.botUnlockHint(shardCharacters[0].unlockSource, shards = shards),
            )
        }
    }

    /**
     * ⚠️ **이 저장소가 값을 치르고 배운 것을 못 박는 테스트다.**
     *
     * 힌트는 네 언어 모두 조각을 *"7일차 출석"* 으로도 얻을 수 있다고 적고 있었는데 **거짓이었다.**
     * 확정표([AttendanceRewardPolicy])는 조각을 **5·6일차에 1개씩** 주고 그 회차는 반복되지
     * 않으며(`isRewardedTier`상 8일차 위로는 7의 배수만), 7일차는 캐릭터뿐이다. 2026-09-01
     * 사용자 결정으로 **광고 경로만** 남겼다.
     *
     * 그래서 두 가지를 함께 본다: ⓐ 문구가 출석을 약속하지 않는가, ⓑ **표가 정말로 그런가**.
     * ⓑ가 없으면 나중에 확정표가 바뀌어 출석이 진짜 경로가 되어도 이 테스트가 계속 통과해,
     * *"광고만"* 이라는 낡은 문구를 지키는 그물이 되어 버린다.
     */
    @Test
    fun theShardHintNoLongerPromisesAnAttendancePath() {
        val attendanceWords = listOf("출석", "일차", "check-in", "출석부", "出席", "签到", "日目", "天")
        languages.forEach { language ->
            val hint = requireNotNull(
                UiStrings.forLanguage(language).botUnlockHint(BotUnlockSource.AdShards(required = 10), shards = 3),
            )
            attendanceWords.forEach { word ->
                assertFalse("$language 조각 힌트가 출석을 약속한다: \"$hint\"", hint.contains(word))
            }
        }

        // ⓑ 표 쪽 사실 확인 — 조각은 5·6일차에 1개씩, 그리고 그 회차는 되돌아오지 않는다.
        val shardTiers = (1..70).filter { tier ->
            AttendanceRewardPolicy.rewardsFor(tier).any { it is AttendanceReward.BotCharacterShards }
        }
        assertEquals("확정표의 조각 회차가 5·6일차가 아니다", listOf(5, 6), shardTiers)
    }

    /**
     * 두 줄로 나눈 것이 문구의 뜻이다(사용자 지시: *"중요한 정보이면 개행해서 아랫줄에"*).
     * 접혀서 두 줄이 되는 것과 **처음부터 두 줄인 것**은 다르다 — 접힘은 폭에 따라 흔들리지만
     * 이 개행은 어느 배율에서도 첫 줄을 통째로 남긴다.
     */
    @Test
    fun theShardHintSplitsProgressAndHowToEarnOntoSeparateLines() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val source = BotUnlockSource.AdShards(required = 10)
            val fresh = requireNotNull(strings.botUnlockHint(source, shards = 0)).lines()
            val partway = requireNotNull(strings.botUnlockHint(source, shards = 4)).lines()

            assertEquals("$language: 힌트가 두 줄이 아니다", 2, fresh.size)
            assertEquals("$language: 힌트가 두 줄이 아니다", 2, partway.size)
            // 둘째 줄(획득 방법)은 진행과 무관하게 늘 같아야 한다 — 바뀌는 것은 첫 줄뿐이다.
            assertEquals("$language: 획득 방법 줄이 진행도에 따라 흔들린다", fresh[1], partway[1])
            assertTrue("$language: 진행 줄이 안 바뀐다", fresh[0] != partway[0])
        }
    }

    /** 마지막 한 개일 때 영어가 `1 shards to go`가 되지 않는지 — 수 일치가 필요한 유일한 언어다. */
    @Test
    fun theEnglishHintAgreesInNumberOnTheLastShard() {
        val source = BotUnlockSource.AdShards(required = 10)
        assertEquals(
            "1 shard to go\nOne shard per ad you watch",
            UiStringsEnglish.botUnlockHint(source, shards = 9),
        )
        assertEquals(
            "2 shards to go\nOne shard per ad you watch",
            UiStringsEnglish.botUnlockHint(source, shards = 8),
        )
    }

    /**
     * 힌트에 박힌 필요 수가 카탈로그와 갈리지 않는지. 카탈로그가 돌뫼 5개 · 묘수 10개를 들고
     * 있으므로(`BotCharacterCatalogTest.unlockPathsFollowTheConfirmedTable`), 문구도 그 값을
     * 그대로 말해야 한다 — 숫자를 손으로 적으면 두 곳이 조용히 어긋난다.
     */
    @Test
    fun theHintQuotesTheCatalogRequirementRatherThanAHandWrittenNumber() {
        shardCharacters.forEach { character ->
            val required = (character.unlockSource as BotUnlockSource.AdShards).required
            val hint = requireNotNull(UiStringsKorean.botUnlockHint(character.unlockSource, shards = 0))
            assertTrue("필요 수 ${required}가 문구에 없다: \"$hint\"", hint.contains("${required}개"))
        }
    }

    private fun allHintsFor(language: UiLanguage): List<String> {
        val strings = UiStrings.forLanguage(language)
        return listOfNotNull(
            strings.botUnlockHint(BotUnlockSource.Attendance(tier = 7)),
            strings.botUnlockHint(BotUnlockSource.AdShards(required = 10), shards = 0),
            strings.botUnlockHint(BotUnlockSource.AdShards(required = 10), shards = 3),
            strings.botUnlockHint(BotUnlockSource.Purchase),
        )
    }
}
