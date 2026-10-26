package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableItemId
import com.worksoc.goaicoach.application.engine.EngineBenchmarkMetric
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkSample
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.application.premium.state.PremiumSource
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureLastSetup
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.RankPromotionOffer
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.kv.json.JSONArray
import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.AttendanceCodec as AndroidAttendanceCodec
import com.worksoc.goaicoach.persistence.BotCollectionCodec as AndroidBotCollectionCodec
import com.worksoc.goaicoach.persistence.ConsumableInventoryCodec as AndroidConsumableInventoryCodec
import com.worksoc.goaicoach.persistence.EngineBenchmarkCodec as AndroidEngineBenchmarkCodec
import com.worksoc.goaicoach.persistence.GameHistoryIndexCodec as AndroidGameHistoryIndexCodec
import com.worksoc.goaicoach.persistence.GameReplayCodec as AndroidGameReplayCodec
import com.worksoc.goaicoach.persistence.GameSetupJsonCodec as AndroidGameSetupJsonCodec
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec as AndroidPlayerSetupJsonCodec
import com.worksoc.goaicoach.persistence.PremiumStateCodec as AndroidPremiumStateCodec
import com.worksoc.goaicoach.persistence.RankMeasureCodec as AndroidRankMeasureCodec
import com.worksoc.goaicoach.persistence.RankMeasureLastSetupCodec as AndroidRankMeasureLastSetupCodec
import com.worksoc.goaicoach.persistence.ReplayJsonCodec as AndroidReplayJsonCodec
import com.worksoc.goaicoach.persistence.SavedGameSessionCodec as AndroidSavedGameSessionCodec
import com.worksoc.goaicoach.persistence.UserPreferencesCodec as AndroidUserPreferencesCodec
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.GameStateReplayer
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import org.json.JSONArray as AndroidJSONArray
import org.json.JSONObject as AndroidJSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 안드로이드 저장 코덱(`persistence/`)과 iOS가 쓰는 공용 사본(`kv/`, 백로그 #294)이 **같은 글을 쓰고 서로의 글을 읽는지**.
 * 사본은 import만 바꾼 복사본이라 한쪽만 고쳐도 컴파일은 통과한다 — 그러면 iOS 저장분만 안드로이드와 다른 모양이 되고,
 * 짝 시험(`kv/`의 같은 이름 시험)은 각자 제 왕복만 보므로 그것을 모른다. 사본 머리의 「둘을 함께 고친다」를 여기서 지킨다.
 *
 * 코덱마다 셋을 본다 — ① 같은 값(기본값 · 모든 칸이 기본값이 아닌 값 · null 칸)을 두 코덱으로 쓴 글이 같다,
 * ② 서로가 쓴 글을 읽으면 원래 값이다, ③ 안드로이드 시험의 옛 저장분 · 깨진 글을 둘이 똑같이 읽는다.
 * 값은 되도록 기존 시험의 것을 그대로 쓴다([GameSetupGoldenFixtures] · [GameSetupStorageGoldenTest]의 골든과 옛 형식).
 *
 * ⚠️ 키 순서는 빼고 견준다([assertSameJson]) — JVM 시험의 org.json(Maven)은 해시 순서라 기기의 org.json(넣은 순서)과 다르다.
 * 기기 바이트는 계기 테스트 `DeviceGameSetupStorageGoldenTest`가 고정한다. 분석 캐시 · 진단 로그 코덱은
 * `KvPositionAnalysisCacheStoreTest` · `KvAppendOnlyLogsTest`가 같은 방식으로 본다.
 */
class KvStoreParityTest {

    // ── 공통 값 — 쓰는 쪽보다 먼저 적는다(초기화 순서) ─────────────────────────

    /** 시작 배치(백로그 #266)까지 전부 기본값이 아닌 판 — `GameSetupJsonCodecTest`의 그 판과 같은 모양. */
    private val setupWithStones = GameSetup(
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

    /** J9=(0,8)은 `I`를 건너뛴 열, A1=(8,0)은 행 원점의 반대쪽 끝이다(`GameReplayCoordinateGoldenTest`와 같은 점). */
    private val replayMoves: List<Move> = listOf(
        Move.Play(StoneColor.Black, BoardCoordinate(row = 0, column = 8)),
        Move.Play(StoneColor.White, BoardCoordinate(row = 8, column = 0)),
        Move.Pass(StoneColor.Black),
        Move.Resign(StoneColor.White),
    )

    private val replayScoreSnapshots = listOf(
        ScoreSnapshot(moveNumber = 1, whiteScoreLead = -2.5, whiteWinRate = 0.4, source = ScoreSnapshotSource.EngineEstimate),
        ScoreSnapshot(moveNumber = 2, whiteScoreLead = null, whiteWinRate = null, source = ScoreSnapshotSource.LocalAreaEstimate),
        ScoreSnapshot(moveNumber = 3, whiteScoreLead = 1.0, whiteWinRate = null, source = ScoreSnapshotSource.FinalScore),
    )

    private val replayEvaluations = listOf(
        MoveReviewMarker(BoardCoordinate(row = 0, column = 8), moveNumber = 1, tone = MoveReviewTone.Good, pointLoss = 0.5),
        MoveReviewMarker(BoardCoordinate(row = 8, column = 0), moveNumber = 2, tone = MoveReviewTone.Blunder, pointLoss = null),
    )

    /** 판정의 모든 칸이 기본값이 아니다 — 접바둑 보정(#89)까지. */
    private val fullJudgement = FinalScoreJudgement(
        winner = StoneColor.White,
        margin = 1.5,
        ruleset = Ruleset.Chinese,
        isEstimatedDisplay = true,
        removedBlack = 1,
        removedWhite = 2,
        blackArea = 44.0,
        whiteAreaWithKomi = 45.5,
        capturedByBlack = 3,
        capturedByWhite = 4,
        komi = 6.5,
        handicapCount = 2,
        whiteHandicapBonus = 2.0,
    )

    /** 판정의 null 칸 전부 — 승자 · 집 수 차이 · 면적 · 덤. */
    private val judgementWithNulls = FinalScoreJudgement(
        winner = null,
        margin = null,
        ruleset = Ruleset.Japanese,
        isEstimatedDisplay = false,
        removedBlack = 0,
        removedWhite = 0,
        blackArea = null,
        whiteAreaWithKomi = null,
        capturedByBlack = 0,
        capturedByWhite = 0,
        komi = null,
    )

    // ── 출석 · 봇 수집 · 소모품 · 프리미엄 ──────────────────────────────────

    @Test
    fun attendanceWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                AttendanceState(),
                AttendanceState(attendanceCount = 9, lastCheckInUtcDay = 20323L, claimedTiers = setOf(1, 2, 3)),
                AttendanceState(attendanceCount = 3, lastCheckInUtcDay = null, claimedTiers = setOf(3)),
            ),
            AndroidAttendanceCodec::encode,
            AttendanceCodec::encode,
            AndroidAttendanceCodec::decode,
            AttendanceCodec::decode,
        )
        assertSameReading(
            listOf("""{"schema":999,"attendanceCount":1}""", "not json", """{"schema":1}"""),
            AndroidAttendanceCodec::decode,
            AttendanceCodec::decode,
        )
    }

    @Test
    fun botCollectionWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                BotCollectionState(),
                BotCollectionState(claimedBots = setOf(BotCharacterId("fast_beginner_1"), BotCharacterId("fast_beginner_4"))),
                BotCollectionState(
                    claimedBots = setOf(BotCharacterId("fast_beginner_1"), BotCharacterId("bot_added_in_a_later_release")),
                    adShards = mapOf(BotCharacterId("fast_beginner_2") to 3, BotCharacterId("fast_beginner_3") to 1),
                ),
            ),
            AndroidBotCollectionCodec::encode,
            BotCollectionCodec::encode,
            AndroidBotCollectionCodec::decode,
            BotCollectionCodec::decode,
        )
        assertSameReading(
            listOf(
                // #11 이전 저장분 — 조각 키가 없다.
                """{"schema":1,"claimedBots":["fast_beginner_1"]}""",
                """{"schema":1}""",
                """{"schema":1,"claimedBots":["bot_added_in_a_later_release",""]}""",
                """{"schema":99,"claimedBots":[],"adShards":{}}""",
                "not json at all",
                "",
            ),
            AndroidBotCollectionCodec::decode,
            BotCollectionCodec::decode,
        )
    }

    @Test
    fun consumableInventoryWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                ConsumableInventory(),
                ConsumableInventory()
                    .withGranted(ConsumableCatalog.EvalOnce.id, 10)
                    .withGranted(ConsumableCatalog.TopMovesOnce.id, 3)
                    .withGranted(ConsumableCatalog.PremiumOnce.id, 1),
                ConsumableInventory(counts = mapOf(ConsumableCatalog.EvalOnce.id to 2, ConsumableItemId("some_future_item") to 7)),
            ),
            AndroidConsumableInventoryCodec::encode,
            ConsumableInventoryCodec::encode,
            AndroidConsumableInventoryCodec::decode,
            ConsumableInventoryCodec::decode,
        )
        assertSameReading(
            listOf(
                """{"schema":1,"counts":{"eval_once":0,"top_moves_once":-3,"premium_once":"twelve","some_future_item":4}}""",
                """{"schema":99,"counts":{}}""",
                "not json at all",
                "",
            ),
            AndroidConsumableInventoryCodec::decode,
            ConsumableInventoryCodec::decode,
        )
    }

    @Test
    fun premiumStateWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                PremiumState(),
                PremiumState.adGranted(nowMillis = 1_000_000L),
                PremiumState.purchased(),
                PremiumState(
                    source = PremiumSource.Purchase,
                    adGrantStartedAtMillis = 1_790_000_000_000L,
                    claimedFeatures = FeatureId.entries.toSet(),
                    lastSubscriptionVerifiedAtMillis = 1_791_000_000_000L,
                ),
            ),
            AndroidPremiumStateCodec::encode,
            PremiumStateCodec::encode,
            AndroidPremiumStateCodec::decode,
            PremiumStateCodec::decode,
        )
        assertSameReading(
            listOf(
                // `claimedFeatures` 배열 이전 — 무르기 하나짜리 불리언.
                """{"source":"None","adGrantStartedAtMillis":null,"isUndoClaimed":true}""",
                """{"source":"None","adGrantStartedAtMillis":null}""",
                "not json",
            ),
            AndroidPremiumStateCodec::decode,
            PremiumStateCodec::decode,
        )
    }

    // ── 승급 대국 ────────────────────────────────────────────────────────────

    @Test
    fun rankMeasureWritesAndReadsTheAndroidJson() {
        val state = RankMeasureState(
            rank = KgsRank.dan(2),
            hasStarted = true,
            peakRank = KgsRank.dan(3),
            consecutiveLosses = 1,
            lastCountedGameId = "1791-42",
        )
        assertParity(
            listOf(
                RankMeasureState(),
                state,
                state.copy(rank = KgsRank.kyu(15), promotionOffer = RankPromotionOffer(steps = 2, margin = 41.0, byResignation = true)),
                state.copy(promotionOffer = RankPromotionOffer(steps = 1, margin = 15.5)),
            ),
            AndroidRankMeasureCodec::encode,
            RankMeasureCodec::encode,
            AndroidRankMeasureCodec::decode,
            RankMeasureCodec::decode,
        )
        assertSameReading(
            listOf(
                """{"schema":1,"rankStep":9,"hasStarted":true}""",
                """{"promotionOffer":{"steps":0,"margin":40}}""",
                """{"promotionOffer":{"steps":2}}""",
                """{"promotionOffer":"??"}""",
                """{"promotionOffer":{"steps":9,"margin":120}}""",
                "{}",
                """{"schema":1,"rankStep":18}""",
                """{"rankStep":99}""",
                """{"rankStep":-4}""",
                """{"consecutiveLosses":-2}""",
                """{"peakRankStep":null,"lastCountedGameId":null}""",
                """{"peakRankStep":"??"}""",
                // 2026-10-07의 「최근 5판 중 3판」 · 2026-10-06의 랠리 규칙이 남긴 키.
                """{"schema":1,"rankStep":${KgsRank.dan(2).step},"hasStarted":true,"recentDanResults":"WWL","consecutiveLosses":1}""",
                """{"schema":1,"rankStep":9,"hasStarted":true,"consecutiveWins":2,"rally":true,"consecutiveLosses":1}""",
                "rank=5k",
            ),
            AndroidRankMeasureCodec::decode,
            RankMeasureCodec::decode,
        )
    }

    @Test
    fun rankMeasureLastSetupWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                RankMeasureLastSetup(),
                RankMeasureLastSetup(userColor = StoneColor.White, boardSize = BoardSize.Nineteen),
                RankMeasureLastSetup(boardSize = BoardSize.Thirteen),
            ),
            AndroidRankMeasureLastSetupCodec::encode,
            RankMeasureLastSetupCodec::encode,
            AndroidRankMeasureLastSetupCodec::decode,
            RankMeasureLastSetupCodec::decode,
        )
        assertSameReading(
            listOf("""{"userColor":"Green","boardSize":15}""", """{"boardSize":13}""", "not json"),
            AndroidRankMeasureLastSetupCodec::decode,
            RankMeasureLastSetupCodec::decode,
        )
    }

    // ── 엔진 측정값 · 설정 ───────────────────────────────────────────────────

    @Test
    fun engineBenchmarkWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                EngineBenchmarkProfile(createdAtMillis = 0L, samplesPerVisit = 0, timeCapMs = 0L, metrics = emptyList()),
                EngineBenchmarkProfile(
                    createdAtMillis = 1234L,
                    samplesPerVisit = 5,
                    timeCapMs = 5_000L,
                    metrics = listOf(
                        EngineBenchmarkMetric(visits = 16, samples = 5, minMs = 11.1, avgMs = 22.2, maxMs = 33.3),
                        EngineBenchmarkMetric(visits = 32, samples = 5, minMs = 44.4, avgMs = 55.5, maxMs = 66.6),
                    ),
                ),
                EngineBenchmarkProfile(
                    createdAtMillis = 1_791_000_000_000L,
                    samplesPerVisit = 3,
                    timeCapMs = 7_000L,
                    measurementVersion = 4,
                    benchmarkPositionName = "custom-position",
                    benchmarkPositionMoves = listOf("D4", "Q16", "pass"),
                    benchmarkRuleset = Ruleset.Chinese,
                    metrics = listOf(
                        EngineBenchmarkMetric(
                            visits = 32,
                            samples = 3,
                            minMs = 101.5,
                            maxMs = 180.25,
                            avgMs = 140.875,
                            rootMinVisits = 12,
                            rootMaxVisits = 33,
                            rootAvgVisits = 25.5,
                            fillOk = 1,
                            fillShort = 1,
                            fillUnknown = 1,
                            sampleDetails = listOf(
                                EngineBenchmarkSample(
                                    sampleIndex = 1,
                                    visits = 32,
                                    elapsedMs = 101.5,
                                    engineElapsedMs = 99L,
                                    rootVisits = 33,
                                    fillStatus = "OK",
                                    positionMoves = listOf("D4", "Q16"),
                                ),
                                EngineBenchmarkSample(
                                    sampleIndex = 2,
                                    visits = 32,
                                    elapsedMs = 140.0,
                                    engineElapsedMs = 138L,
                                    rootVisits = 12,
                                    fillStatus = "SHORT",
                                ),
                                EngineBenchmarkSample(
                                    sampleIndex = 3,
                                    visits = 32,
                                    elapsedMs = 180.25,
                                    engineElapsedMs = null,
                                    rootVisits = null,
                                    fillStatus = "UNKNOWN",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            AndroidEngineBenchmarkCodec::encode,
            EngineBenchmarkCodec::encode,
            AndroidEngineBenchmarkCodec::decode,
            EngineBenchmarkCodec::decode,
        )
        assertSameReading(
            listOf("""{"schema":99}""", "{broken"),
            AndroidEngineBenchmarkCodec::decode,
            EngineBenchmarkCodec::decode,
        )
    }

    @Test
    fun userPreferencesWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                UserPreferencesSnapshot(),
                UserPreferencesSnapshot(
                    playerSetup = PlayerSetup(
                        black = SidePlayerSetup(controller = SeatController.Human, humanGameType = HumanGameType.Teaching),
                        white = SidePlayerSetup(controller = SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 3)),
                    ),
                    ruleset = Ruleset.Chinese,
                    komi = 0.5,
                    showMoveNumbers = true,
                    showLastMoveRing = false,
                    autoPlayDelayMillis = AutoPlayDelaySetting.Slow.millis,
                    searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
                    showMoveReview = true,
                    appFontScale = 1.3f,
                ),
                everyPreferenceChanged(),
            ),
            AndroidUserPreferencesCodec::encode,
            UserPreferencesCodec::encode,
            AndroidUserPreferencesCodec::decode,
            UserPreferencesCodec::decode,
        )
        assertSameReading(
            listOf(
                """{"schema":1}""",
                // #73이 지운 필드가 남은 저장분.
                """{"schema":2,"gameSetupUxMode":"Simple","komi":0.5,"hasSeenOnboarding":true}""",
                // 스키마 1의 옛 탐색 시간 — 꺼짐 · 가장 긴 한도 · 깨진 값.
                """{"schema":1,"searchTimeSettings":{"timeCapEnabled":false,"b16Millis":1500,"b32Millis":4000,"b64Millis":7500}}""",
                """{"schema":1,"searchTimeSettings":{"timeCapEnabled":true,"b16Millis":1500,"b32Millis":4000,"b64Millis":7500}}""",
                """{"schema":1,"searchTimeSettings":{"timeCapEnabled":true,"b16Millis":-1,"b32Millis":"invalid","b64Millis":0}}""",
                """{"schema":2,"searchTimeSettings":{"limit":"invalid"}}""",
                """{"schema":2}""",
                "{broken",
                """{"schema":99}""",
            ),
            AndroidUserPreferencesCodec::decode,
            UserPreferencesCodec::decode,
        )
    }

    // ── 좌석 · 판 정체성 · 수순 조각 ─────────────────────────────────────────

    @Test
    fun playerSetupWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                PlayerSetup(),
                PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Human, humanGameType = HumanGameType.Teaching),
                    white = SidePlayerSetup(controller = SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 3)),
                ),
                PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 5)),
                    white = SidePlayerSetup(controller = SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 2)),
                ),
                rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(5)),
            ),
            { AndroidPlayerSetupJsonCodec.encodePlayerSetup(it).toString() },
            { PlayerSetupJsonCodec.encodePlayerSetup(it).toString() },
            { AndroidPlayerSetupJsonCodec.decodePlayerSetup(AndroidJSONObject(it)) },
            { PlayerSetupJsonCodec.decodePlayerSetup(JSONObject(it)) },
        )
        assertSameReading(
            listOf(
                """{"black":{"controller":"Human"},"white":{"controller":"Ai"}}""",
                // `25269f04`(첫 형식)의 좌석 — 지금은 없는 `aiEngine` 키가 있다.
                """{"black":{"controller":"Human","humanGameType":"Normal","aiEngine":"KataGo","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","aiEngine":"KataGo","playLevel":{"group":"FastBeginner","level":1}}}""",
                "{}",
            ),
            { AndroidPlayerSetupJsonCodec.decodePlayerSetup(AndroidJSONObject(it)) },
            { PlayerSetupJsonCodec.decodePlayerSetup(JSONObject(it)) },
        )
        assertEquals(AndroidPlayerSetupJsonCodec.decodePlayerSetup(null), PlayerSetupJsonCodec.decodePlayerSetup(null))

        assertParity(
            listOf(
                PlayLevelSetting(),
                PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
                PlayLevelSetting(PlayLevelGroup.CustomRank, level = 16),
            ),
            { AndroidPlayerSetupJsonCodec.encodePlayLevel(it).toString() },
            { PlayerSetupJsonCodec.encodePlayLevel(it).toString() },
            { AndroidPlayerSetupJsonCodec.decodePlayLevel(AndroidJSONObject(it)) },
            { PlayerSetupJsonCodec.decodePlayLevel(JSONObject(it)) },
        )
        assertSameReading(
            listOf("""{"group":"FastBeginner","level":1}""", "{}"),
            { AndroidPlayerSetupJsonCodec.decodePlayLevel(AndroidJSONObject(it)) },
            { PlayerSetupJsonCodec.decodePlayLevel(JSONObject(it)) },
        )
        assertEquals(AndroidPlayerSetupJsonCodec.decodePlayLevel(null), PlayerSetupJsonCodec.decodePlayLevel(null))
    }

    /** 두 저장분(이어하기 · 대국 기록)이 네 키를 넣는 순서가 다르다 — 순서마다 따로 견준다(순서 자체는 기기 골든이 본다). */
    @Test
    fun gameSetupWritesAndReadsTheAndroidJsonInBothOrders() {
        assertEquals(
            "넣는 순서의 이름이 두 코덱에서 다르다",
            AndroidGameSetupJsonCodec.Order.entries.map { it.name },
            GameSetupJsonCodec.Order.entries.map { it.name },
        )
        GameSetupJsonCodec.Order.entries.forEach { order ->
            val androidOrder = AndroidGameSetupJsonCodec.Order.valueOf(order.name)
            assertParity(
                listOf(
                    GameSetup.standard(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = DefaultKomi),
                    GameSetup.standard(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 3, komi = 0.5),
                    setupWithStones,
                ),
                { AndroidGameSetupJsonCodec.write(AndroidJSONObject(), it, androidOrder).toString() },
                { GameSetupJsonCodec.write(JSONObject(), it, order).toString() },
                { AndroidGameSetupJsonCodec.read(AndroidJSONObject(it)) },
                { GameSetupJsonCodec.read(JSONObject(it)) },
            )
        }
        assertSameReading(
            listOf(
                // 키가 하나도 없는 옛 저장분 · 시작 배치 키가 없는 옛 접바둑 · 모르는 룰 이름.
                "{}",
                """{"handicapCount":3}""",
                """{"boardSize":13,"ruleset":"Korean","handicapCount":0,"komi":7.5}""",
            ),
            { AndroidGameSetupJsonCodec.read(AndroidJSONObject(it)) },
            { GameSetupJsonCodec.read(JSONObject(it)) },
        )
    }

    @Test
    fun replayPiecesWriteAndReadTheAndroidJson() {
        assertParity(
            listOf(replayMoves, emptyList<Move>()),
            { AndroidReplayJsonCodec.encodeMoves(it, BoardSize.Nine).toString() },
            { ReplayJsonCodec.encodeMoves(it, BoardSize.Nine).toString() },
            { AndroidReplayJsonCodec.decodeMoves(AndroidJSONArray(it), BoardSize.Nine) },
            { ReplayJsonCodec.decodeMoves(JSONArray(it), BoardSize.Nine) },
        )
        assertParity(
            listOf(replayScoreSnapshots, emptyList<ScoreSnapshot>()),
            { AndroidReplayJsonCodec.encodeScoreSnapshots(it).toString() },
            { ReplayJsonCodec.encodeScoreSnapshots(it).toString() },
            { AndroidReplayJsonCodec.decodeScoreSnapshots(AndroidJSONArray(it)) },
            { ReplayJsonCodec.decodeScoreSnapshots(JSONArray(it)) },
        )
        assertParity(
            listOf(replayEvaluations, emptyList<MoveReviewMarker>()),
            { AndroidReplayJsonCodec.encodeMoveEvaluations(it, BoardSize.Nine).toString() },
            { ReplayJsonCodec.encodeMoveEvaluations(it, BoardSize.Nine).toString() },
            { AndroidReplayJsonCodec.decodeMoveEvaluations(AndroidJSONArray(it), BoardSize.Nine) },
            { ReplayJsonCodec.decodeMoveEvaluations(JSONArray(it), BoardSize.Nine) },
        )
    }

    // ── 이어하기 · 대국 기록 ─────────────────────────────────────────────────

    @Test
    fun savedSessionWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                GameSetupGoldenFixtures.defaultSession(),
                GameSetupGoldenFixtures.handicapSession(),
                GameSetupGoldenFixtures.endedSession(),
                GameSetupGoldenFixtures.endedSession().copy(finalScoreJudgement = fullJudgement),
                GameSetupGoldenFixtures.endedSession().copy(finalScoreJudgement = judgementWithNulls),
                sessionFromSetupStones(),
            ),
            AndroidSavedGameSessionCodec::encode,
            SavedGameSessionCodec::encode,
            AndroidSavedGameSessionCodec::decode,
            SavedGameSessionCodec::decode,
        )
        assertSameReading(
            listOf(
                GameSetupStorageGoldenTest.FirstFormatSession,
                GameSetupStorageGoldenTest.PreKomiHandicapSession,
                GameSetupStorageGoldenTest.HandicapSessionGolden,
                GameSetupStorageGoldenTest.EndedSessionGolden,
                GameSetupStorageGoldenTest.DefaultSessionGolden,
                "{broken",
            ),
            AndroidSavedGameSessionCodec::decode,
            SavedGameSessionCodec::decode,
        )
    }

    @Test
    fun historyIndexWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                emptyList<GameHistoryEntry>(),
                GameSetupGoldenFixtures.historyEntries(),
                listOf(
                    GameHistoryEntry(
                        id = "1791000000000-42",
                        playedAtMillis = 1_791_000_000_000L,
                        setup = setupWithStones,
                        playerSetup = rankMeasurePlayerSetup(StoneColor.White, KgsRank.dan(3)),
                        moveCount = 1,
                        humanColor = StoneColor.White,
                        winner = StoneColor.White,
                        isResign = false,
                        margin = 15.5,
                        hasReplay = true,
                        // `/`는 기기 org.json만 `\/`로 쓴다 — 읽는 쪽이 둘 다 받는지 함께 본다.
                        note = "사진/이어 둔 판",
                    ),
                ),
            ),
            AndroidGameHistoryIndexCodec::encodeAll,
            GameHistoryIndexCodec::encodeAll,
            AndroidGameHistoryIndexCodec::decodeAll,
            GameHistoryIndexCodec::decodeAll,
        )
        assertSameReading(
            listOf(
                GameSetupStorageGoldenTest.HistoryIndexGolden,
                // 덤 키가 없는 줄(백로그 #23).
                """{"schema":1,"entries":[{"id":"no-komi","playedAtMillis":1000,"boardSize":9,"ruleset":"Chinese","handicapCount":0,"playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},"moveCount":10,"humanColor":"Black","winner":"Black","isResign":false,"margin":3.5,"hasReplay":false}]}""",
                """{"schema":999,"entries":[]}""",
                "not json",
            ),
            AndroidGameHistoryIndexCodec::decodeAll,
            GameHistoryIndexCodec::decodeAll,
        )
        // #151 이관 전 `SharedPreferences` 기록 — 승자 대신 `result`.
        assertSameReading(
            listOf(
                GameSetupStorageGoldenTest.LegacyPrefsHistory,
                legacyPrefsHistory(result = "Win"),
                legacyPrefsHistory(result = "Loss"),
                legacyPrefsHistory(result = "Resign", margin = "null"),
                legacyPrefsHistory(result = "Draw", margin = "null"),
            ),
            AndroidGameHistoryIndexCodec::decodeLegacyAll,
            GameHistoryIndexCodec::decodeLegacyAll,
        )
    }

    @Test
    fun replayRecordWritesAndReadsTheAndroidJson() {
        assertParity(
            listOf(
                GameReplayData(moves = replayMoves, scoreSnapshots = replayScoreSnapshots, moveEvaluations = replayEvaluations),
                GameReplayData(moves = listOf(Move.Pass(StoneColor.Black))),
            ),
            { AndroidGameReplayCodec.encode(it, BoardSize.Nine) },
            { GameReplayCodec.encode(it, BoardSize.Nine) },
            { AndroidGameReplayCodec.decode(it, BoardSize.Nine) },
            { GameReplayCodec.decode(it, BoardSize.Nine) },
        )

        // 수순이 없는 본문은 둘 다 「없음」으로 읽는다.
        val empty = GameReplayData(moves = emptyList())
        assertSameJson(AndroidGameReplayCodec.encode(empty, BoardSize.Nine), GameReplayCodec.encode(empty, BoardSize.Nine))
        assertNull(AndroidGameReplayCodec.decode(GameReplayCodec.encode(empty, BoardSize.Nine), BoardSize.Nine))
        assertNull(GameReplayCodec.decode(AndroidGameReplayCodec.encode(empty, BoardSize.Nine), BoardSize.Nine))

        assertSameReading(
            listOf(
                // `GameReplayCoordinateGoldenTest`의 손으로 적은 9x9 기록과 같은 글.
                """{"schema":1,"moves":[{"type":"play","player":"Black","coordinate":"J9"},{"type":"play","player":"White","coordinate":"A1"},{"type":"resign","player":"Black"}],"scoreSnapshots":[],"moveEvaluations":[{"moveNumber":1,"coordinate":"J9","tone":"Good","pointLoss":0.5},{"moveNumber":2,"coordinate":"A1","tone":"Blunder","pointLoss":12.0}]}""",
                """{"schema":999}""",
                "not json",
            ),
            { AndroidGameReplayCodec.decode(it, BoardSize.Nine) },
            { GameReplayCodec.decode(it, BoardSize.Nine) },
        )
    }

    // ── 돕는 것 ──────────────────────────────────────────────────────────────

    /** 같은 값을 두 코덱으로 쓴 글이 키 순서만 빼고 같고, 서로가 쓴 글을 읽으면 원래 값으로 돌아오는가. */
    private fun <T> assertParity(
        values: List<T>,
        androidEncode: (T) -> String,
        kvEncode: (T) -> String,
        androidDecode: (String) -> T?,
        kvDecode: (String) -> T?,
    ) {
        values.forEach { value ->
            val androidText = androidEncode(value)
            val kvText = kvEncode(value)
            assertSameJson(androidText, kvText)
            assertEquals("안드로이드 코덱이 iOS 사본의 글을 원래 값으로 못 읽는다: $kvText", value, androidDecode(kvText))
            assertEquals("iOS 사본이 안드로이드 코덱의 글을 원래 값으로 못 읽는다: $androidText", value, kvDecode(androidText))
        }
    }

    /** 같은 글(옛 저장분 · 깨진 글)을 두 코덱이 똑같이 읽는가 — 둘 다 버리는 것(`null`)도 같은 답이다. */
    private fun <T> assertSameReading(
        raws: List<String>,
        androidDecode: (String) -> T,
        kvDecode: (String) -> T,
    ) {
        raws.forEach { raw ->
            assertEquals("같은 저장분을 두 코덱이 다르게 읽는다: $raw", androidDecode(raw), kvDecode(raw))
        }
    }

    /** 시작 배치에서 이어 둔 판(백로그 #266) — 승급 대국 좌석, 사람 모델의 형세 한 칸. */
    private fun sessionFromSetupStones(): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = GameStateReplayer.replay(
                setup = setupWithStones,
                moves = listOf(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("G10", BoardSize.Thirteen))),
            ),
            playerSetup = rankMeasurePlayerSetup(StoneColor.White, KgsRank.dan(3)),
            playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            topMovesEnabled = true,
            savedAtMillis = 1_791_000_000_000L,
            scoreSnapshots = listOf(
                ScoreSnapshot(moveNumber = 1, whiteScoreLead = 3.5, whiteWinRate = 0.75, source = ScoreSnapshotSource.HumanNetworkEstimate),
            ),
        )

    /** 모든 칸을 기본값과 다르게 — 토글은 지금 기본값을 뒤집어(기본값이 바뀌어도 「다르다」가 유지된다). */
    private fun everyPreferenceChanged(): UserPreferencesSnapshot {
        val defaults = UserPreferencesSnapshot()
        return UserPreferencesSnapshot(
            boardSize = BoardSize.Nineteen,
            playerSetup = rankMeasurePlayerSetup(StoneColor.White, KgsRank.kyu(12)),
            ruleset = Ruleset.Chinese,
            handicapCount = 3,
            komi = 0.5,
            topMovesEnabled = !defaults.topMovesEnabled,
            showCoordinates = !defaults.showCoordinates,
            showMoveNumbers = !defaults.showMoveNumbers,
            showLastMoveRing = !defaults.showLastMoveRing,
            showOwnershipOverlay = !defaults.showOwnershipOverlay,
            autoPlayDelayMillis = AutoPlayDelaySetting.Study.millis,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinTenSeconds),
            isDirectPlayEnabled = !defaults.isDirectPlayEnabled,
            showMoveReview = !defaults.showMoveReview,
            hasSeenOnboarding = !defaults.hasSeenOnboarding,
            appFontScale = 1.3f,
            isPlayHapticEnabled = !defaults.isPlayHapticEnabled,
            isPlayEffectEnabled = !defaults.isPlayEffectEnabled,
            isBoardMaxSize = !defaults.isBoardMaxSize,
            isLargeHeldStoneEnabled = !defaults.isLargeHeldStoneEnabled,
            settingsSchemaGeneration = 0,
        )
    }

    /** `GameHistoryCodecTest`의 옛 기록과 같은 글 — `humanColor` + `result`만 있다. */
    private fun legacyPrefsHistory(result: String, margin: String = "3.5"): String =
        """{"schema":1,"entries":[{"id":"legacy","playedAtMillis":1000,"boardSize":9,"ruleset":"Chinese","komi":6.5,"handicapCount":2,"playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},"moveCount":84,"humanColor":"Black","result":"$result","margin":$margin}]}"""
}
