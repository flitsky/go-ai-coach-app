package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 원격 엔진에 실어 보내는 국면(`encodeState`)의 골든 — 판 정체성을 값 객체 하나로 묶어도 와이어가 그대로다
 * (refactor backlog #22). 네 값(판 크기·룰·접바둑·덤)이 전부 기본값이 아닌 판과 호선 판을 함께 본다.
 * 골든은 **#22 이전 코드(`adf89db3`)의 JVM 출력**이다(키 순서는 JVM `org.json`의 해시 순서).
 * `komi`·`handicapCount`가 빠지면 서버가 6.5·맞바둑으로 가정한다(#19) — 이 골든이 그것도 함께 잡는다.
 */
class RemoteGameSetupWireGoldenTest {

    @Test
    fun aHandicapPositionGoesOverTheWireUnchanged() {
        val state = GameState.withHandicap(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 2, komi = 0.5)
            .play(Move.Play(StoneColor.White, BoardCoordinate(row = 2, column = 3)))
            .play(Move.Pass(StoneColor.Black))

        assertEquals(HandicapStateGolden, RemotePositionAnalysisJsonCodec.encodeState(state).toString())
    }

    @Test
    fun anEvenPositionGoesOverTheWireUnchanged() {
        val state = GameState.empty(BoardSize.Nineteen, Ruleset.Japanese, komi = 7.5)

        assertEquals(EvenStateGolden, RemotePositionAnalysisJsonCodec.encodeState(state).toString())
    }

    /**
     * [GameSetup]에 칸을 더하면 여기가 빨개진다 — 와이어에 그 키가 없으면 서버는 기본값으로 가정한다(#19의 덤·접바둑).
     * 와이어 키는 프로퍼티 이름 그대로다.
     */
    @Test
    fun everyGameSetupPropertyGoesOverTheWire() {
        val properties = GameSetup::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
        val encoded = RemotePositionAnalysisJsonCodec.encodeState(GameState.empty(BoardSize.Nine))

        properties.forEach { property -> assertTrue("와이어에 `$property`가 없다", encoded.has(property)) }
    }

    private companion object {
        const val HandicapStateGolden =
            """{"capturedByWhite":0,"komi":0.5,"handicapCount":2,"capturedByBlack":0,"boardSize":13,"nextPlayer":"White","moves":[{"type":"play","point":"D11","player":"White"},{"type":"pass","player":"Black"}],"ruleset":"Chinese","stones":[{"color":"White","point":"D11"},{"color":"Black","point":"K10"},{"color":"Black","point":"D4"}],"koPoint":null,"koForbiddenFor":null}"""
        const val EvenStateGolden =
            """{"capturedByWhite":0,"komi":7.5,"handicapCount":0,"capturedByBlack":0,"boardSize":19,"nextPlayer":"Black","moves":[],"ruleset":"Japanese","stones":[],"koPoint":null,"koForbiddenFor":null}"""
    }
}
