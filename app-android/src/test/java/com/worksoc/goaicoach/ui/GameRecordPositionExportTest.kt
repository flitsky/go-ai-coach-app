package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.gamerecord.BundledGameRecords
import com.worksoc.goaicoach.ui.gamerecord.GameRecordOpen
import com.worksoc.goaicoach.ui.gamerecord.openGameRecord
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 「기보 보기」의 미리 계산한 형세(백로그 #269)를 만들 **국면 대본**을 내보낸다 — 통과 기준을 거는 테스트가 아니다.
 * `make test`에서는 돌지 않는다 — `./gradlew :app-android:testDebugUnitTest --tests '*GameRecordPositionExportTest*' -PexportGameRecordPositions`.
 *
 * 기보를 **앱과 같은 함수**(`openGameRecord` → `readSgfGameRecord`)로 읽어 판 크기·규칙·덤·시작 배치·첫 차례·수순을 `app-android/build/game-record-positions/`에
 * JSON으로 쓴다. 실험실 `engine-lab/tools/game_record_evals.py`가 그것을 엔진에 그대로 넣는다 — 덤이 없으면 0, 규칙 매핑 같은 해석을 파이썬에서 다시 쓰지 않으려고.
 */
class GameRecordPositionExportTest {

    private val out = File(RepoPaths.root, "app-android/build/game-record-positions")

    @Test
    fun exportPositionScripts() {
        assumeTrue("-PexportGameRecordPositions를 줄 때만 돈다", System.getProperty("exportGameRecordPositions") == "true")
        out.deleteRecursively()
        out.mkdirs()
        BundledGameRecords.forEach { entry ->
            val bytes = RepoPaths.appAndroidMain("assets/${entry.assetPath}").readBytes()
            val record = (openGameRecord(bytes) as GameRecordOpen.Opened).record
            val setup = record.setup
            // 실은 기보는 모두 시작 배치(AB·AW)로 접바둑 돌을 싣는다 — 화점 자동 배치(`handicapCount`)는 엔진이 다르게 탄다(#266 메모).
            assertEquals("${entry.asset}: 화점 자동 배치가 있다 — 대본이 그 길을 모른다", 0, setup.handicapCount)
            val size = setup.boardSize
            val script = JSONObject()
                .put("asset", entry.asset)
                .put("size", size.value)
                .put("rules", setup.ruleset.katagoName)
                .put("komi", setup.komi)
                .put("firstPlayer", colorCode(setup.firstPlayer))
                .put(
                    "setup",
                    JSONArray(
                        setup.setupStones.map { (point, color) ->
                            JSONObject().put("color", colorCode(color)).put("row", point.row).put("column", point.column)
                        },
                    ),
                )
                .put(
                    "moves",
                    JSONArray(
                        record.moves.map { move ->
                            val json = JSONObject().put("color", colorCode(move.player))
                            when (move) {
                                is Move.Play -> json.put("row", move.coordinate.row).put("column", move.coordinate.column)
                                is Move.Pass -> json.put("pass", true)
                                is Move.Resign -> error("${entry.asset}: 기권이 수순에 있다")
                            }
                        },
                    ),
                )
            File(out, entry.asset.removeSuffix(".sgf") + ".json").writeText(script.toString(2))
        }
    }

    private fun colorCode(color: StoneColor): String = if (color == StoneColor.Black) "B" else "W"
}
