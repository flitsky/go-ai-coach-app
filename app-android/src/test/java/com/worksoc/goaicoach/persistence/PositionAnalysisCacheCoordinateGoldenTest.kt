package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOrigin
import com.worksoc.goaicoach.application.analysis.positionAnalysisCacheKeyFor
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * **분석 캐시 좌표 골든 — 저장된 row/column 정수와 저장된 키 문자열을 리터럴로 고정한다**(refactor backlog #36).
 *
 * 분석 캐시는 좌표를 **두 가지 방식으로** 담는다.
 * - 후보 수의 좌표는 `row`/`column` **정수**다(`D4` 같은 문자열이 아니다). 판 크기 검사 없이
 *   `BoardCoordinate(row, column)`로 바로 읽는다 — 키 지문에 판 크기가 들어 있어서다.
 * - 키(`positionFingerprint`)는 [com.worksoc.goaicoach.shared.domain.analysisFingerprint]가 만든
 *   **문자열**이고 그 안에 좌표 표기와 `describe` 문구(`"White pass"`)가 들어 있다.
 *
 * 앞의 것은 행과 열이 뒤바뀌거나 원점이 뒤집히면, 뒤의 것은 표기·문구가 바뀌면 이미 저장된 캐시가
 * 엉뚱한 점을 가리키거나 **아무 데서도 실패하지 않고 전부 미스**가 된다. `JsonPositionAnalysisCacheStoreTest`는
 * 왕복만 보므로 둘 다 못 잡는다. 여기서는 기기에 쓰인 모양을 리터럴 JSON으로 두고, 지금 코드가 같은
 * 국면에서 **같은 키를 다시 만드는지**까지 본다. 국면과 후보는 정수 좌표로 만든다.
 */
class PositionAnalysisCacheCoordinateGoldenTest {

    @Test
    fun literalCacheEntryDecodesToIntegerCoordinates() {
        val decoded = JsonPositionAnalysisCacheCodec.decode(CacheFile19x19)

        assertEquals("리터럴 캐시 파일이 통째로 버려졌다", 1, decoded.size)
        assertEquals(
            listOf(
                Move.Play(StoneColor.Black, BoardCoordinate(3, 15)), // Q16
                Move.Play(StoneColor.Black, BoardCoordinate(18, 18)), // T1
                Move.Pass(StoneColor.Black),
            ),
            decoded.single().result.candidates.map { it.move },
        )
    }

    /** 저장된 키 문자열이 지금 코드가 같은 국면에서 만드는 키와 같아야 캐시가 맞는다. */
    @Test
    fun storedKeyStillMatchesTheKeyComputedForTheSamePosition() {
        val decoded = JsonPositionAnalysisCacheCodec.decode(CacheFile19x19).single()

        assertEquals(StoredFingerprint, decoded.key.positionFingerprint)
        assertEquals(positionAnalysisCacheKeyFor(GoldenState, EngineSearchMode.JsonPositionAnalysis, GoldenLimit), decoded.key)
    }

    @Test
    fun integerBuiltCandidatesEncodeAsGoldenRowAndColumn() {
        val entry = PositionAnalysisCacheEntry(
            key = positionAnalysisCacheKeyFor(GoldenState, EngineSearchMode.JsonPositionAnalysis, GoldenLimit),
            result = AnalysisResult(
                status = EngineStatus.ready("ready"),
                candidates = listOf(
                    CandidateMove(move = Move.Play(StoneColor.Black, BoardCoordinate(3, 15))),
                    CandidateMove(move = Move.Pass(StoneColor.Black)),
                ),
                summary = "",
            ),
            createdAtMillis = 1_780_000_000_000L,
            requestedRootVisits = 32,
            rootVisits = 35,
            origin = PositionAnalysisCacheOrigin.LocalUser,
        )

        val json = JSONObject(JsonPositionAnalysisCacheCodec.encode(listOf(entry)))

        assertEquals(1, json.getInt("schema"))
        val stored = json.getJSONArray("entries").getJSONObject(0)
        assertEquals(StoredFingerprint, stored.getJSONObject("key").getString("positionFingerprint"))
        val candidates = stored.getJSONObject("result").getJSONArray("candidates")
        val play = candidates.getJSONObject(0).getJSONObject("move")
        assertEquals(listOf("play", "Black", 3, 15), listOf(play.getString("type"), play.getString("player"), play.getInt("row"), play.getInt("column")))
        assertFalse("캐시는 좌표를 문자열로 담지 않는다", play.has("coordinate"))
        val pass = candidates.getJSONObject(1).getJSONObject("move")
        assertEquals(listOf("pass", "Black"), listOf(pass.getString("type"), pass.getString("player")))
        assertFalse("통과 수에는 row가 없다", pass.has("row"))
    }

    private companion object {
        /**
         * 19x19에서 흑 Q4=(15,15), 백 통과 — 다음은 흑이라 후보도 흑의 수다. `Q`는 `I`를 건너뛴 뒤의 열이고
         * 4선은 행 원점을 뒤집으면 16선이 되므로, 알파벳·원점 어느 쪽이 바뀌어도 저장된 키와 어긋난다.
         */
        val GoldenState: GameState = GameState.empty(BoardSize.Nineteen, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate(15, 15)))
            .play(Move.Pass(StoneColor.White))

        const val StoredFingerprint =
            "size=19|rules=Japanese|next=Black|capturedB=0|capturedW=0|ko=none|koFor=none|stones=Q4:B,|moves=Black Q4;White pass;"

        val GoldenLimit = AnalysisLimit(
            visits = 32,
            timeMillis = 2_000L,
            candidateCount = 16,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        /** `JsonPositionAnalysisCacheCodec.encode`가 쓰는 모양 그대로의 캐시 파일(후보 셋: Q16, T1, 통과). */
        val CacheFile19x19 = """
            {
              "schema": 1,
              "entries": [
                {
                  "createdAtMillis": 1780000000000,
                  "requestedRootVisits": 32,
                  "rootVisits": 35,
                  "origin": "LocalUser",
                  "key": {
                    "positionFingerprint": "$StoredFingerprint",
                    "searchMode": "JsonPositionAnalysis",
                    "limit": {
                      "visits": 32,
                      "timeMillis": 2000,
                      "candidateCount": 16,
                      "includePolicy": true,
                      "refinePolicyMoves": 0,
                      "minVisitsPerCandidate": 0
                    }
                  },
                  "result": {
                    "status": {"state": "Ready", "message": "ready"},
                    "summary": "",
                    "rootVisits": 35,
                    "elapsedMillis": 3067,
                    "candidates": [
                      {"move": {"type": "play", "player": "Black", "row": 3, "column": 15}, "winRate": 0.55, "visits": 12, "engineOrder": 0, "source": "Unknown"},
                      {"move": {"type": "play", "player": "Black", "row": 18, "column": 18}, "winRate": 0.4, "visits": 3, "engineOrder": 1, "source": "Unknown"},
                      {"move": {"type": "pass", "player": "Black"}, "winRate": 0.1, "visits": 1, "engineOrder": 2, "source": "Unknown"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
