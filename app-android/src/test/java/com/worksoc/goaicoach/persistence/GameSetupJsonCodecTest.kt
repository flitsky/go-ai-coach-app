package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import java.lang.reflect.Modifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 판 정체성 네 키의 저장 코덱(refactor backlog #22). 두 저장분(이어하기·대국 기록)이 이 한 곳만 쓰므로, 여기가
 * [GameSetup]의 칸을 **하나도 빠뜨리지 않는지**가 곧 #1(이어하기 덤 유실) 같은 누락이 다시 안 나는 근거다.
 */
class GameSetupJsonCodecTest {
    private val nonDefault = GameSetup(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 2, komi = 0.5)

    /**
     * [GameSetup]에 칸을 더하면 여기가 빨개진다 — 저장 키는 프로퍼티 이름 그대로다. 키를 더하고, 그 키가 없는
     * 옛 저장분을 무엇으로 읽을지 기본값을 [GameSetupJsonCodec.read]에 정할 것(스키마 번호는 올리지 않는다, 함정 69).
     */
    @Test
    fun everyGameSetupPropertyIsWrittenUnderItsOwnName() {
        val properties = GameSetup::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        GameSetupJsonCodec.Order.entries.forEach { order ->
            val written = GameSetupJsonCodec.write(JSONObject(), nonDefault, order).keys().asSequence().toSet()
            assertEquals("$order 순서가 GameSetup의 칸과 다른 키를 쓴다", properties, written)
        }
    }

    @Test
    fun aNonDefaultSetupRoundTripsInEitherOrder() {
        GameSetupJsonCodec.Order.entries.forEach { order ->
            assertEquals(nonDefault, GameSetupJsonCodec.read(GameSetupJsonCodec.write(JSONObject(), nonDefault, order)))
        }
    }

    /** 키가 하나도 없는 옛 저장분 — 판 크기 9·일본·호선·덤 6.5(#1·#23이 맞춘 기본값). */
    @Test
    fun missingKeysReadAsTheHistoricDefaults() {
        assertEquals(GameSetup(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = DefaultKomi), GameSetupJsonCodec.read(JSONObject()))
    }

    /** 모르는 룰 이름은 일본으로 읽는다 — enum 상수 이름이 저장 포맷이다(함정 1). */
    @Test
    fun anUnknownRulesetNameReadsAsJapanese() {
        val json = GameSetupJsonCodec.write(JSONObject(), nonDefault, GameSetupJsonCodec.Order.HandicapThenKomi)
            .put(GameSetupJsonCodec.RulesetKey, "Korean")

        assertEquals(Ruleset.Japanese, GameSetupJsonCodec.read(json).ruleset)
    }
}
