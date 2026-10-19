package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.gamehistory.buildGameReplayTimeline
import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.gamerecord.BundledGameRecords
import com.worksoc.goaicoach.ui.gamerecord.GameRecordOpen
import com.worksoc.goaicoach.ui.gamerecord.openGameRecord
import com.worksoc.goaicoach.ui.l10n.GameRecordGroup
import com.worksoc.goaicoach.ui.l10n.GameRecordOpenFailure
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.gameRecordGroupTitleFor
import com.worksoc.goaicoach.ui.l10n.gameRecordOpenFailureFor
import com.worksoc.goaicoach.ui.l10n.gameRecordPlayerNameFor
import com.worksoc.goaicoach.ui.l10n.gameRecordResultFor
import com.worksoc.goaicoach.ui.l10n.gameRecordUntitledFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsIntroFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsOpenFileFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsTitleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「기보 보기」(백로그 #269) — 앱에 싣는 기보가 **모두 열리고 끝까지 서는지**, 목록과 파일이 같은지, 문구가 네 언어로 다 찼는지.
 *
 * 앱에 싣는 판은 사용자가 정했다(2026-10-19 — 33판 모두). 판을 빼거나 더할 때 에셋 파일과 `BundledGameRecords`를 함께 고치지 않으면
 * 목록에 눌리지 않는 줄이 생기거나(파일 없음) 실린 파일이 아무 데서도 안 보인다 — 둘 다 조용하다.
 */
class GameRecordCatalogContractTest {

    private val assetDir = RepoPaths.appAndroidMain("assets/game-records")

    private fun bytesOf(asset: String): ByteArray = assetDir.resolve(asset).readBytes()

    @Test
    fun everyListedRecordIsShippedAndEveryShippedRecordIsListed() {
        val shipped = assetDir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".sgf") }.sorted()
        val listed = BundledGameRecords.map { it.asset }
        assertEquals("목록에 같은 파일이 두 번 있다", listed.distinct(), listed)
        assertEquals("에셋 폴더의 기보와 BundledGameRecords가 다르다", shipped, listed.sorted())
        assertTrue("사용자 요청: 최소 10판(2026-10-19)", listed.size >= 10)
        assertTrue("사용자 요청: 최대 50판(2026-10-19)", listed.size <= 50)
    }

    @Test
    fun everyShippedRecordOpensAndStandsUpToTheLastMove() {
        BundledGameRecords.forEach { entry ->
            val opened = openGameRecord(bytesOf(entry.asset))
            assertTrue("${entry.asset}: 열리지 않는다 — $opened", opened is GameRecordOpen.Opened)
            val record = (opened as GameRecordOpen.Opened).record
            assertTrue("${entry.asset}: 수가 없다", record.moves.isNotEmpty())
            val timeline = buildGameReplayTimeline(record.setup, record.moves)
            assertFalse("${entry.asset}: ${timeline.truncatedAtMoveNumber}수째에서 판이 끊긴다", timeline.isTruncated)
        }
    }

    /**
     * 실은 판마다 **미리 잰 형세**(`.eval`, #269)가 있고, 그 머리가 앱이 기보를 읽은 해석과 같다 — 판 크기 · 규칙 · 덤 · 국면 수(수 + 1).
     * 해석이 바뀌었거나(덤·규칙 매핑) 기보를 바꾸고 다시 재지 않았으면 여기서 빨개진다 — `engine-lab/tools/game_record_evals.py`로 다시 잰다.
     */
    @Test
    fun everyShippedRecordHasAPrecomputedEvaluationMatchingHowTheAppReadsIt() {
        val evaluations = assetDir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".eval") }.sorted()
        assertEquals("짝 없는 .eval이 있거나 빠진 판이 있다", BundledGameRecords.map { it.asset.removeSuffix(".sgf") + ".eval" }.sorted(), evaluations)
        BundledGameRecords.forEach { entry ->
            val record = (openGameRecord(bytesOf(entry.asset)) as GameRecordOpen.Opened).record
            val evaluation = com.worksoc.goaicoach.ui.gamerecord.readGameRecordEvaluation(bytesOf(entry.evaluationPath.substringAfter('/')))
            assertTrue("${entry.asset}: .eval을 읽지 못했다", evaluation != null)
            evaluation!!
            assertEquals("${entry.asset}: 국면 수", record.moves.size + 1, evaluation.positions)
            assertEquals("${entry.asset}: 판 크기", record.setup.boardSize.value, evaluation.boardSize)
            assertEquals("${entry.asset}: 덤", record.setup.komi, evaluation.komi, 0.001)
            val expectedRules = if (record.setup.ruleset == com.worksoc.goaicoach.shared.domain.Ruleset.Chinese) 1 else 0
            assertEquals("${entry.asset}: 규칙", expectedRules, evaluation.rulesCode)
            assertTrue("${entry.asset}: 승률이 0~1 밖이다", evaluation.snapshots.all { it.whiteWinRate!! in 0.0..1.0 })
            val last = evaluation.estimateAt(evaluation.positions - 1)!!
            assertEquals("${entry.asset}: 영역 칸 수", evaluation.boardSize * evaluation.boardSize, last.ownership!!.points.size)
        }
    }

    /** 형식 읽기 — 영역은 첫 국면 그대로, 다음부터 앞 국면과의 차이(256으로 돈다)다. 심은 9줄 두 국면으로 되돌린 값과 센 결과를 본다. */
    @Test
    fun theEvaluationFormatDecodesDeltasBackToValues() {
        val cells = 81
        val first = IntArray(cells) { if (it == 0) -127 else if (it == 1) 127 else 0 }
        val second = first.copyOf().also { it[0] = 127; it[2] = -100 }
        val buffer = java.nio.ByteBuffer.allocate(16 + 2 * 8 + 2 * cells).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("GREV".toByteArray()).put(1).put(9).put(0).put(0).putFloat(6.5f).putInt(2)
        buffer.putFloat(-3.5f).putFloat(0.2f).putFloat(1.5f).putFloat(0.6f)
        first.forEach { buffer.put(it.toByte()) }
        second.indices.forEach { buffer.put(((second[it] - first[it]) and 0xFF).toByte()) }
        val evaluation = com.worksoc.goaicoach.ui.gamerecord.readGameRecordEvaluation(buffer.array())!!
        assertEquals(2, evaluation.positions)
        assertEquals(-3.5, evaluation.snapshots[0].whiteScoreLead!!, 0.0001)
        val after = evaluation.estimateAt(1)!!
        assertEquals(1.5, after.whiteScoreLead!!, 0.0001)
        assertEquals("127 → 백의 자리", 1.0, after.ownership!!.points[0].value, 0.0001)
        assertEquals("−100/127 → 흑의 자리", -100 / 127.0, after.ownership!!.points[2].value, 0.0001)
        assertEquals(2, after.ownership!!.whiteLikelyPoints)
        assertEquals(1, after.ownership!!.blackLikelyPoints)
        assertNull("머리가 틀리면 읽지 않는다", com.worksoc.goaicoach.ui.gamerecord.readGameRecordEvaluation(ByteArray(40)))
    }

    /** 분석 장치는 미리 잰 형세가 있으면 엔진보다 먼저 쓴다 — 게이트(1회권·따라가기)는 같은 파일이 그대로 정한다. 보기 화면이 그것을 넘긴다. */
    @Test
    fun theReplayAnalysisUsesThePrecomputedEstimateBeforeAskingTheEngine() {
        val analysis = RepoPaths.uiFile("ReplayAnalysis.kt").readContractSource()
        val precomputed = analysis.indexOf("currentPrecomputed?.invoke(moveNumber)")
        val engine = analysis.indexOf("engine.estimate(position)")
        assertTrue("미리 잰 형세를 엔진보다 먼저 보지 않는다", precomputed in 0 until engine)
        val viewer = RepoPaths.uiFile("GameRecordViewer.kt").readContractSource()
        assertTrue("보기 화면이 미리 잰 형세를 넘기지 않는다", viewer.contains("precomputedEstimate = evaluation?.let { it::estimateAt }"))
    }

    @Test
    fun everyShippedPlayerHasANameInEveryLanguageAndEveryGroupIsUsed() {
        val names = BundledGameRecords.flatMap { entry ->
            val record = (openGameRecord(bytesOf(entry.asset)) as GameRecordOpen.Opened).record
            listOfNotNull(record.info.blackName, record.info.whiteName)
        }.toSet()
        names.forEach { name ->
            assertTrue("$name: 한국어 표기가 없다 — 목록에 영어 이름이 그대로 보인다", gameRecordPlayerNameFor(UiLanguage.Korean, name) != name)
            UiLanguage.entries.forEach { language -> assertTrue("$name/$language", gameRecordPlayerNameFor(language, name).isNotBlank()) }
        }
        assertEquals("쓰지 않는 묶음이 있다", GameRecordGroup.entries.toSet(), BundledGameRecords.map { it.group }.toSet())
        GameRecordGroup.entries.forEach { group ->
            UiLanguage.entries.forEach { language -> assertTrue("$group/$language", gameRecordGroupTitleFor(language, group).isNotBlank()) }
        }
    }

    @Test
    fun theScreenStringsAreFilledInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            listOf(gameRecordsTitleFor(language), gameRecordsIntroFor(language), gameRecordsOpenFileFor(language), gameRecordUntitledFor(language))
                .forEach { assertTrue("$language 문구가 비었다", it.isNotBlank()) }
            GameRecordOpenFailure.entries.forEach { assertTrue("$language/$it", gameRecordOpenFailureFor(language, it).isNotBlank()) }
        }
        assertEquals("기보 보기", gameRecordsTitleFor(UiLanguage.Korean))
    }

    @Test
    fun resultsReadAsWordsAndUnknownShapesStayRaw() {
        assertEquals("흑 불계승", gameRecordResultFor(UiLanguage.Korean, "B+R"))
        assertEquals("백 불계승", gameRecordResultFor(UiLanguage.Korean, "W+Resign"))
        assertEquals("백 2집승", gameRecordResultFor(UiLanguage.Korean, "W+2"))
        assertEquals("백 반집승", gameRecordResultFor(UiLanguage.Korean, "W+0.5"))
        assertEquals("흑 7집 반 승", gameRecordResultFor(UiLanguage.Korean, "B+7.5"))
        assertEquals("무승부", gameRecordResultFor(UiLanguage.Korean, "0"))
        assertEquals("백 시간승", gameRecordResultFor(UiLanguage.Korean, "W+T"))
        assertEquals("White wins by 0.5 points", gameRecordResultFor(UiLanguage.English, "W+0.5"))
        assertEquals("黒中押し勝ち", gameRecordResultFor(UiLanguage.Japanese, "B+R"))
        assertEquals("黑胜7目半", gameRecordResultFor(UiLanguage.ChineseSimplified, "B+7.5"))
        assertNull(gameRecordResultFor(UiLanguage.Korean, "Void"))
        assertNull(gameRecordResultFor(UiLanguage.Korean, null))
    }

    /**
     * 사용자가 정한 숫자(2026-10-19): 빨리 감기 10수 — 이동 버튼 여섯은 기보 보기와 다시보기 **둘 다**(공용 `ReplayControls`) ·
     * 자동 진행 간격 1~5초, 기본 1초 — 자동 진행 줄은 **기보 보기에만**(사용자가 다시보기에 요청한 것은 버튼 여섯이다).
     */
    @Test
    fun fastStepAndAutoPlayAreTheUsersNumbers() {
        assertEquals(10, com.worksoc.goaicoach.ui.history.ReplayFastStep)
        assertEquals(1..5, com.worksoc.goaicoach.ui.gamerecord.GameRecordAutoPlaySeconds)
        assertEquals(1, com.worksoc.goaicoach.ui.gamerecord.DefaultGameRecordAutoPlaySeconds)
        val viewer = RepoPaths.uiFile("GameRecordViewer.kt").readContractSource()
        assertTrue("자동 진행 중 화면이 꺼진다", viewer.contains("KeepScreenOnWhile(active = isAutoPlaying)"))
        val replay = RepoPaths.uiFile("GameReplayScreen.kt").readContractSource()
        listOf("label = \"◀◀\"", "label = \"▶▶\"").forEach { button ->
            assertTrue("공용 이동 버튼에 $button 이 없다 — 두 화면 다 여섯이다", replay.contains(button))
        }
        UiLanguage.entries.forEach { language ->
            listOf(
                com.worksoc.goaicoach.ui.l10n.gameRecordAutoPlayFor(language),
                com.worksoc.goaicoach.ui.l10n.gameRecordPauseFor(language),
                com.worksoc.goaicoach.ui.l10n.gameRecordSecondsFor(language, 1),
            ).forEach { assertTrue("$language 자동 진행 문구가 비었다", it.isNotBlank()) }
        }
        assertEquals("3초", com.worksoc.goaicoach.ui.l10n.gameRecordSecondsFor(UiLanguage.Korean, 3))
    }

    /** 보기 화면은 다시보기와 같은 규칙을 따른다 — 뒤로 가기를 스스로 잡고, 프리미엄 판정은 `ReplayAnalysis.kt`에만 있고, 광고 자리는 늘리지 않는다. */
    @Test
    fun theViewerCatchesBackLeavesTheGateToReplayAnalysisAndShowsNoBanner() {
        val viewer = RepoPaths.uiFile("GameRecordViewer.kt").readContractSource()
        assertTrue("보기 화면이 시스템 뒤로 가기를 잡지 않는다 — 셸이 홈으로 보낸다", viewer.contains("BackHandler {"))
        assertTrue("분석은 다시보기와 같은 창구로", viewer.contains("rememberReplayAnalysis("))
        listOf("PremiumUiState", "LocalPremium", "FeatureAccess", "SubscriptionAwareBannerAd").forEach { forbidden ->
            assertFalse("보기 화면이 `$forbidden`를 직접 쓴다", viewer.contains(forbidden))
        }
    }
}
