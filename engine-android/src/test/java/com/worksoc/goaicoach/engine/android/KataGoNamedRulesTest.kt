package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.HandicapBonusRule
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.json.JSONObject

/**
 * 접바둑 보정 방식의 단일 출처(`Ruleset.handicapBonusRule`)를 엔진 명령·쿼리가 **정말 읽는가**(refactor backlog #106).
 *
 * 앱 계가와 엔진이 보정을 따로 정하던 시절에는 한쪽만 바꾸면 #89(AI·그래프는 N, 최종 승패는 0)가 돌아올 수
 * 있었다. 지금 룰셋은 전부 KataGo 이름 룰의 기본값과 같아 덮어쓰기가 나가지 않으므로, 빌더가 값을 읽는지는
 * **지금 룰셋에 없는 조합**을 GTP 본체 오버로드와 쿼리 팩토리의 `handicapBonusRule` 인자에 넣어 확인한다.
 */
class KataGoNamedRulesTest {
    /**
     * ⭐ 일관성 가드 — 모든 룰셋의 보정 방식이 그 이름 룰의 KataGo 기본값과 같다. 그래서 GTP·JSON 둘 다
     * 덮어쓰기 없이 예전과 같은 바이트가 나간다.
     *
     * ⚠️ 룰셋의 보정 방식을 일부러 바꾸다 이 테스트가 빨개졌다면: 로컬 GTP·JSON은 덮어쓰기를 알아서 싣지만
     * **원격 요청은 싣지 않는다**(`RemotePositionAnalysisJsonCodec.encodeState`는 룰셋 이름만 보내고, 서버는
     * 이름 룰 기본값을 쓴다 — #65). 이 가드를 풀기 전에 와이어와 `scripts/run-katago-remote-analysis-server.py`를
     * 먼저 넓혀라. 안 그러면 원격 AI만 다른 보정으로 둔다.
     */
    @Test
    fun everyRulesetUsesItsKataGoNamedDefaultSoNoOverrideIsSent() {
        for (ruleset in Ruleset.entries) {
            assertEquals(
                KataGoNamedRules.defaultHandicapBonus(ruleset.katagoName),
                ruleset.handicapBonusRule,
                "$ruleset(${ruleset.katagoName})의 보정 방식이 KataGo 기본값과 다르다 — 원격 경로가 따라오지 못한다(KDoc 참고).",
            )
            assertEquals(listOf("kata-set-rules ${ruleset.katagoName}"), KataGoProtocolCommands.ruleCommands(ruleset))
            val query = query(ruleset)
            assertEquals(ruleset.katagoName, query.getString("rules"))
            assertFalse(query.has("whiteHandicapBonus"), "$ruleset: 기본값과 같으면 덮어쓰기 키가 없어야 한다 — $query")
        }
    }

    @Test
    fun gtpAppendsTheWhiteHandicapBonusOverrideOnlyWhenTheRuleDiffersFromTheNamedDefault() {
        assertEquals(
            listOf("kata-set-rules chinese", "kata-set-rule whiteHandicapBonus N-1"),
            KataGoProtocolCommands.ruleCommands("chinese", HandicapBonusRule.NMinusOne),
        )
        assertEquals(
            listOf("kata-set-rules chinese", "kata-set-rule whiteHandicapBonus 0"),
            KataGoProtocolCommands.ruleCommands("chinese", HandicapBonusRule.Zero),
        )
        assertEquals(
            listOf("kata-set-rules japanese", "kata-set-rule whiteHandicapBonus N"),
            KataGoProtocolCommands.ruleCommands("japanese", HandicapBonusRule.N),
        )
        assertEquals(
            listOf("kata-set-rules japanese"),
            KataGoProtocolCommands.ruleCommands("japanese", HandicapBonusRule.Zero),
        )
    }

    /**
     * JSON 쿼리는 `rules`를 이름 룰 그대로 두고 **최상위 `whiteHandicapBonus`** 를 더한다 — KataGo 분석 엔진이
     * `rules`보다 우선해 읽는 필드다(Analysis_Engine.md). 룰 전체를 객체로 옮겨 적는 길은 버렸다: 빠진 키는
     * 이름 룰이 아니라 KataGo 기본값으로 채워지고, 옮겨 적을 값도 문서 표와 소스가 다른 것이 있다.
     */
    @Test
    fun jsonQueryKeepsTheNamedRuleAndAddsTheTopLevelBonusOnlyWhenItDiffers() {
        val chineseNMinusOne = query(Ruleset.Chinese, HandicapBonusRule.NMinusOne)
        assertEquals("chinese", chineseNMinusOne.getString("rules"))
        assertEquals("N-1", chineseNMinusOne.getString("whiteHandicapBonus"))

        val japaneseN = query(Ruleset.Japanese, HandicapBonusRule.N)
        assertEquals("japanese", japaneseN.getString("rules"))
        assertEquals("N", japaneseN.getString("whiteHandicapBonus"))

        val chineseZero = query(Ruleset.Chinese, HandicapBonusRule.Zero)
        assertEquals("0", chineseZero.getString("whiteHandicapBonus"))

        val japaneseZero = query(Ruleset.Japanese, HandicapBonusRule.Zero)
        assertEquals("japanese", japaneseZero.getString("rules"))
        assertFalse(japaneseZero.has("whiteHandicapBonus"), japaneseZero.toString())
    }

    @Test
    fun whiteHandicapBonusValuesAreSpelledTheWayKataGoReadsThem() {
        assertEquals(
            mapOf(HandicapBonusRule.N to "N", HandicapBonusRule.NMinusOne to "N-1", HandicapBonusRule.Zero to "0"),
            HandicapBonusRule.entries.associateWith { rule -> rule.katagoValue },
        )
    }

    /** 표에 없는 이름 룰은 조용히 기본값을 짐작하지 않고 던진다 — 새 룰셋은 표에 먼저 적어야 한다. */
    @Test
    fun anUnknownNamedRuleIsRejectedInsteadOfGuessed() {
        assertFailsWith<IllegalArgumentException> {
            KataGoProtocolCommands.ruleCommands("aga", HandicapBonusRule.NMinusOne)
        }
    }

    private fun query(
        ruleset: Ruleset,
        handicapBonusRule: HandicapBonusRule = ruleset.handicapBonusRule,
    ): JSONObject =
        KataGoJsonAnalysisQueryFactory.build(
            id = "q",
            boardSize = BoardSize.Nine,
            ruleset = ruleset,
            playedMoves = emptyList(),
            limit = AnalysisLimit(visits = 8),
            handicapBonusRule = handicapBonusRule,
        )
}
