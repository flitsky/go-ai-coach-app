package com.worksoc.goaicoach.kv

// 같은 이름의 안드로이드 시험(`persistence/`)을 공용 사본(`kv`)에 그대로 돌린다(백로그 #294 — P2 짝 시험). import만 바꿨다.

import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 판 정체성 네 키의 저장 코덱(refactor backlog #22). 두 저장분(이어하기·대국 기록)이 이 한 곳만 쓰므로, 여기가
 * [GameSetup]의 칸을 **하나도 빠뜨리지 않는지**가 곧 #1(이어하기 덤 유실) 같은 누락이 다시 안 나는 근거다.
 */
class GameSetupJsonCodecTest {
    // 시작 배치(백로그 #266)까지 기본값이 아닌 판 — 키는 시작 배치가 있을 때만 쓰므로 여기서 다 나온다.
    private val nonDefault = GameSetup(
        BoardSize.Thirteen,
        Ruleset.Chinese,
        handicapCount = 2,
        komi = 0.5,
        setupStones = mapOf(
            BoardCoordinate.fromLabel("D4", BoardSize.Thirteen) to StoneColor.Black,
            BoardCoordinate.fromLabel("K10", BoardSize.Thirteen) to StoneColor.White,
        ),
        firstPlayer = StoneColor.White,
    )

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
        assertEquals(GameSetup.standard(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = DefaultKomi), GameSetupJsonCodec.read(JSONObject()))
    }

    /** 옛 접바둑 저장분(시작 배치 키 없음)은 백부터다 — 첫 차례의 기본값이 접바둑 여부를 따른다. */
    @Test
    fun anOldHandicapRecordReadsWithWhiteFirst() {
        val json = JSONObject().put(GameSetupJsonCodec.HandicapCountKey, 3)
        assertEquals(StoneColor.White, GameSetupJsonCodec.read(json).firstPlayer)
        assertEquals(emptyMap<BoardCoordinate, StoneColor>(), GameSetupJsonCodec.read(json).setupStones)
    }

    /**
     * 시작 배치가 없는 보통의 판은 **예전 그대로 네 키만** 쓴다 — 저장 바이트가 한 바이트도 달라지지 않는다(기기 골든이 고정한다).
     */
    @Test
    fun aStandardSetupWritesOnlyTheFourOldKeys() {
        val standard = GameSetup.standard(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 0, komi = 7.5)
        GameSetupJsonCodec.Order.entries.forEach { order ->
            val keys = GameSetupJsonCodec.write(JSONObject(), standard, order).keys().asSequence().toSet()
            assertEquals(
                setOf(GameSetupJsonCodec.BoardSizeKey, GameSetupJsonCodec.RulesetKey, GameSetupJsonCodec.HandicapCountKey, GameSetupJsonCodec.KomiKey),
                keys,
            )
        }
    }

    /** 모르는 룰 이름은 일본으로 읽는다 — enum 상수 이름이 저장 포맷이다(함정 1). */
    @Test
    fun anUnknownRulesetNameReadsAsJapanese() {
        val json = GameSetupJsonCodec.write(JSONObject(), nonDefault, GameSetupJsonCodec.Order.HandicapThenKomi)
            .put(GameSetupJsonCodec.RulesetKey, "Korean")

        assertEquals(Ruleset.Japanese, GameSetupJsonCodec.read(json).ruleset)
    }
}
