package com.worksoc.goaicoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.StoneColor

internal enum class UiLanguage(
    val menuLabel: String,
) {
    Korean("한국어"),
    English("English"),
    Japanese("日本語"),
    ChineseSimplified("简体中文"),
}

internal val LocalUiStrings = staticCompositionLocalOf { UiStrings.Korean }

@Composable
internal fun ProvideUiLanguage(
    content: @Composable (UiLanguage, (UiLanguage) -> Unit) -> Unit,
) {
    var language by remember { mutableStateOf(UiLanguage.Korean) }
    val strings = remember(language) { UiStrings.forLanguage(language) }
    CompositionLocalProvider(LocalUiStrings provides strings) {
        content(language) { nextLanguage -> language = nextLanguage }
    }
}

internal data class UiStrings(
    val language: UiLanguage,
    val appTitle: String,
    val languageLabel: String,
    val close: String,
    val gameSection: String,
    val newGame: String,
    val copyLog: String,
    val benchmark: String,
    val currentModePrefix: String,
    val scoringRule: String,
    val boardSize: String,
    val playerSetup: String,
    val searchTime: String,
    val maximumSearchTimeLimit: String,
    val timeCap: String,
    val timeCapOn: String,
    val timeCapOff: String,
    val recommendedPrefix: String,
    val none: String,
    val autoDelay: String,
    val engine: String,
    val displayMenu: String,
    val directPlay: String,
    val coordinates: String,
    val moveNumbers: String,
    val lastMoveRing: String,
    val turnPrefix: String,
    val movesPrefix: String,
    val capturesPrefix: String,
    val lastPrefix: String,
    val scoreLead: String,
    val winRate: String,
    val captures: String,
    val playMove: String,
    val pass: String,
    val undo: String,
    val topMoves: String,
    val eval: String,
    val resign: String,
    val newGameAction: String,
    val resignConfirmTitle: String,
    val resignConfirmMessage: String,
    val cancel: String,
    val moveCountPrefix: String,
    val moveCountSuffix: String,
    val lastMove: String,
    val time: String,
    val stoneCountSuffix: String,
    val kataGoAnalysis: String,
    val blackAnalysis: String,
    val whiteAnalysis: String,
    val selected: String,
    val noAnalysisInfo: String,
    val scoreSnapshotsEmpty: String,
    val engineSource: String,
    val localSource: String,
    val finalScoreSource: String,
    val finalJudgementTitle: String,
    val reviewJudgement: String,
    val analyze: String,
    val later: String,
    val confirm: String,
    val rerunBenchmark: String,
    val benchmarkDoneTitle: String,
    val benchmarkRunningTitle: String,
    val benchmarkRunningBody: String,
    val benchmarkReadyMessage: String,
    val recommendedMaximumSearchTime: String,
    val benchmarkCautiousMessage: String,
    val benchmarkProgress: String,
    val benchmarkEngineSettling: String,
    val benchmarkSecuringRuntime: String,
    val benchmarkPreparing: String,
    val benchmarkSample: String,
    val benchmarkTotalProgress: String,
    val benchmarkLastResult: String,
    val benchmarkSamples: String,
    val benchmarkTimeCap: String,
    val benchmarkPosition: String,
    val benchmarkPositionMoves: String,
    val benchmarkDetailsIncluded: String,
    val resumeTitle: String,
    val resumeMoveCountPrefix: String,
    val resumeMoveCountSuffix: String,
    val resumeQuestion: String,
    val lastMovePrefix: String,
    val yes: String,
    val no: String,
    val handicap: String,
    val handicapNone: String,
) {
    /** 접바둑 N점 레이블 (예: 한국어 "접바둑 3점", 영어 "Handicap 3") */
    fun handicapLabel(count: Int): String =
        when (language) {
            UiLanguage.Korean -> "$handicap ${count}점"
            UiLanguage.English -> "$handicap $count"
            UiLanguage.Japanese -> "$handicap ${count}子"
            UiLanguage.ChineseSimplified -> "$handicap ${count}子"
        }

    fun colorLabel(color: StoneColor): String =
        when (language) {
            UiLanguage.Korean -> if (color == StoneColor.Black) "흑" else "백"
            UiLanguage.English -> if (color == StoneColor.Black) "Black" else "White"
            UiLanguage.Japanese -> if (color == StoneColor.Black) "黒" else "白"
            UiLanguage.ChineseSimplified -> if (color == StoneColor.Black) "黑" else "白"
        }

    fun controllerLabel(controller: SeatController): String =
        when (controller) {
            SeatController.Human -> when (language) {
                UiLanguage.Korean -> "유저"
                UiLanguage.English -> "Player"
                UiLanguage.Japanese -> "プレイヤー"
                UiLanguage.ChineseSimplified -> "玩家"
            }
            SeatController.Ai -> "AI"
        }

    fun sideLabel(setup: SidePlayerSetup, color: StoneColor): String =
        "${colorLabel(color)} (${controllerLabel(setup.controller)})"

    fun matchModeLabel(mode: MatchMode): String =
        when (mode) {
            MatchMode.HumanVsAi -> when (language) {
                UiLanguage.Korean -> "AI 대국"
                UiLanguage.English -> "Player vs AI"
                UiLanguage.Japanese -> "AI対局"
                UiLanguage.ChineseSimplified -> "AI 对局"
            }
            MatchMode.AiVsHuman -> when (language) {
                UiLanguage.Korean -> "AI 선공"
                UiLanguage.English -> "AI first"
                UiLanguage.Japanese -> "AI先番"
                UiLanguage.ChineseSimplified -> "AI 先手"
            }
            MatchMode.AiVsAi -> when (language) {
                UiLanguage.Korean -> "AI 자동 대국"
                UiLanguage.English -> "AI autoplay"
                UiLanguage.Japanese -> "AI自動対局"
                UiLanguage.ChineseSimplified -> "AI 自动对局"
            }
            MatchMode.LocalTwoPlayer -> when (language) {
                UiLanguage.Korean -> "2인 대국"
                UiLanguage.English -> "2P test"
                UiLanguage.Japanese -> "2Pテスト"
                UiLanguage.ChineseSimplified -> "双人测试"
            }
        }

    fun setupSummary(setup: PlayerSetup, engineName: String): String =
        listOf(
            "${colorLabel(StoneColor.Black)}: ${sideSummary(setup.black, engineName)}",
            "${colorLabel(StoneColor.White)}: ${sideSummary(setup.white, engineName)}",
        ).joinToString(" / ")

    fun sideSummary(setup: SidePlayerSetup, engineName: String): String =
        when (setup.controller) {
            SeatController.Human -> controllerLabel(SeatController.Human)
            SeatController.Ai -> "${setup.aiEngine.label.ifBlank { engineName }} ${levelLabel(setup.playLevel.safeLevel)}"
        }

    fun levelLabel(level: Int): String =
        when (language) {
            UiLanguage.Korean -> "${level}단계"
            UiLanguage.English -> "Level $level"
            UiLanguage.Japanese -> "レベル$level"
            UiLanguage.ChineseSimplified -> "$level 级"
        }

    fun playLevelGroupLabel(group: PlayLevelGroup): String =
        when (language) {
            UiLanguage.Korean -> group.label
            UiLanguage.English -> group.name
            UiLanguage.Japanese -> group.label
            UiLanguage.ChineseSimplified -> group.label
        }

    fun rulesetLabel(ruleset: Ruleset): String =
        when (ruleset) {
            Ruleset.Japanese -> when (language) {
                UiLanguage.Korean -> "집 계가 (영역 계가)"
                UiLanguage.English -> "Territory Scoring"
                UiLanguage.Japanese -> "地合計算"
                UiLanguage.ChineseSimplified -> "数目计分"
            }
            Ruleset.Chinese -> when (language) {
                UiLanguage.Korean -> "면적 계가 (영역+돌 계가)"
                UiLanguage.English -> "Area Scoring"
                UiLanguage.Japanese -> "面積計算"
                UiLanguage.ChineseSimplified -> "数子计分"
            }
        }

    fun autoPlayDelayLabel(setting: AutoPlayDelaySetting): String =
        if (setting.millis == 0L) {
            when (language) {
                UiLanguage.Korean -> "즉시"
                UiLanguage.English -> "Instant"
                UiLanguage.Japanese -> "即時"
                UiLanguage.ChineseSimplified -> "立即"
            }
        } else {
            secondsLabel(setting.millis)
        }

    fun secondsLabel(millis: Long): String {
        val seconds = millis / 1_000.0
        val value = if (millis % 1_000L == 0L) {
            seconds.toInt().toString()
        } else {
            ((seconds * 10.0).toInt() / 10.0).toString()
        }
        return when (language) {
            UiLanguage.Korean -> "${value}초"
            UiLanguage.English -> "${value}s"
            UiLanguage.Japanese -> "${value}秒"
            UiLanguage.ChineseSimplified -> "${value}秒"
        }
    }

    fun searchTimeLimitLabel(limit: SearchTimeLimit): String =
        when (language) {
            UiLanguage.Korean -> when (limit) {
                SearchTimeLimit.Off -> "사용 안 함"
                SearchTimeLimit.WithinOneSecond -> "1초 이내"
                SearchTimeLimit.WithinThreeSeconds -> "3초 이내"
                SearchTimeLimit.WithinFiveSeconds -> "5초 이내"
                SearchTimeLimit.WithinTenSeconds -> "10초 이내"
            }
            UiLanguage.English -> when (limit) {
                SearchTimeLimit.Off -> "Off"
                SearchTimeLimit.WithinOneSecond -> "Within 1 second"
                SearchTimeLimit.WithinThreeSeconds -> "Within 3 seconds"
                SearchTimeLimit.WithinFiveSeconds -> "Within 5 seconds"
                SearchTimeLimit.WithinTenSeconds -> "Within 10 seconds"
            }
            UiLanguage.Japanese -> when (limit) {
                SearchTimeLimit.Off -> "オフ"
                SearchTimeLimit.WithinOneSecond -> "1秒以内"
                SearchTimeLimit.WithinThreeSeconds -> "3秒以内"
                SearchTimeLimit.WithinFiveSeconds -> "5秒以内"
                SearchTimeLimit.WithinTenSeconds -> "10秒以内"
            }
            UiLanguage.ChineseSimplified -> when (limit) {
                SearchTimeLimit.Off -> "关闭"
                SearchTimeLimit.WithinOneSecond -> "1 秒以内"
                SearchTimeLimit.WithinThreeSeconds -> "3 秒以内"
                SearchTimeLimit.WithinFiveSeconds -> "5 秒以内"
                SearchTimeLimit.WithinTenSeconds -> "10 秒以内"
            }
        }

    companion object {
        val Korean = UiStrings(
            language = UiLanguage.Korean,
            appTitle = "Go AI Coach POC",
            languageLabel = "언어",
            close = "닫기",
            gameSection = "게임",
            newGame = "새 대국",
            copyLog = "진단 로그 복사",
            benchmark = "엔진 성능 측정",
            currentModePrefix = "현재 모드",
            scoringRule = "계가 방식",
            boardSize = "바둑판 크기",
            playerSetup = "플레이어 설정",
            searchTime = "탐색 시간",
            maximumSearchTimeLimit = "최대 탐색 시간 제한",
            timeCap = "시간 제한",
            timeCapOn = "응답시간 제한 적용",
            timeCapOff = "탐색 수 우선",
            recommendedPrefix = "추천",
            none = "없음",
            autoDelay = "AI 착수 지연",
            engine = "엔진",
            displayMenu = "표시 메뉴",
            directPlay = "바로 착수",
            coordinates = "좌표",
            moveNumbers = "수순 번호",
            lastMoveRing = "마지막 수 표시",
            turnPrefix = "차례",
            movesPrefix = "수순",
            capturesPrefix = "사석",
            lastPrefix = "최근",
            scoreLead = "집차",
            winRate = "승률",
            captures = "사석",
            playMove = "착수",
            pass = "통과",
            undo = "무르기",
            topMoves = "추천 수",
            eval = "형세 보기",
            resign = "기권",
            newGameAction = "대국 시작",
            resignConfirmTitle = "기권 확인",
            resignConfirmMessage = "정말 기권하시겠습니까?",
            cancel = "취소",
            moveCountPrefix = "수순",
            moveCountSuffix = "수",
            lastMove = "최근 착수",
            time = "시간",
            stoneCountSuffix = "개",
            kataGoAnalysis = "KataGo 분석",
            blackAnalysis = "흑 분석",
            whiteAnalysis = "백 분석",
            selected = "선택",
            noAnalysisInfo = "아직 표시할 분석 정보가 없습니다.",
            scoreSnapshotsEmpty = "첫 형세 판단 후 점수 그래프가 표시됩니다.",
            engineSource = "엔진",
            localSource = "로컬",
            finalScoreSource = "최종",
            finalJudgementTitle = "판정 결과",
            reviewJudgement = "판정 검토",
            analyze = "분석하기",
            later = "나중에",
            confirm = "확인",
            rerunBenchmark = "다시 측정",
            benchmarkDoneTitle = "기기 성능 확인 완료",
            benchmarkRunningTitle = "기기 성능 확인 중",
            benchmarkRunningBody = "AI가 생각하는 시간을 확인하고 있습니다. 1~3분 정도 걸릴 수 있습니다.",
            benchmarkReadyMessage = "AI 분석 준비가 완료되었습니다.",
            recommendedMaximumSearchTime = "권장 최대 탐색 시간",
            benchmarkCautiousMessage = "측정값이 충분하지 않아 여유 있게 권장합니다.",
            benchmarkProgress = "진행",
            benchmarkEngineSettling = "엔진 안정화 대기 중...",
            benchmarkSecuringRuntime = "실행시간 확보 중...",
            benchmarkPreparing = "준비 중",
            benchmarkSample = "샘플",
            benchmarkTotalProgress = "전체 진행률",
            benchmarkLastResult = "직전 결과",
            benchmarkSamples = "측정 샘플",
            benchmarkTimeCap = "측정 상한",
            benchmarkPosition = "측정 포지션",
            benchmarkPositionMoves = "포지션 수순",
            benchmarkDetailsIncluded = "상세 sampleDetails는 메뉴의 진단 로그 복사에 포함됩니다.",
            resumeTitle = "이전 대국 이어하기",
            resumeMoveCountPrefix = "진행 중이던",
            resumeMoveCountSuffix = "수 대국이 있습니다.",
            resumeQuestion = "이어 진행하시겠습니까?",
            lastMovePrefix = "마지막 수",
            yes = "예",
            no = "아니오",
            handicap = "접바둑",
            handicapNone = "접바둑 없음",
        )

        val English = Korean.copy(
            language = UiLanguage.English,
            languageLabel = "Language",
            close = "Close",
            gameSection = "Game",
            newGame = "New",
            copyLog = "Copy Log",
            benchmark = "Benchmark",
            currentModePrefix = "Current mode",
            scoringRule = "Scoring rule",
            boardSize = "Board size",
            playerSetup = "Player Setup",
            searchTime = "Search Time",
            maximumSearchTimeLimit = "Maximum search time limit",
            timeCap = "Time cap",
            timeCapOn = "Limit response time",
            timeCapOff = "Prefer full visits",
            recommendedPrefix = "Recommended",
            none = "None",
            autoDelay = "Auto delay",
            engine = "Engine",
            displayMenu = "Display menu",
            directPlay = "Direct play",
            coordinates = "Coords",
            moveNumbers = "Move nums",
            lastMoveRing = "Last ring",
            turnPrefix = "Turn",
            movesPrefix = "Moves",
            capturesPrefix = "Captures",
            lastPrefix = "Last",
            scoreLead = "Lead",
            winRate = "Win rate",
            captures = "Captures",
            playMove = "Play",
            pass = "Pass",
            undo = "Undo",
            topMoves = "Top moves",
            eval = "Eval",
            resign = "Resign",
            newGameAction = "Start Game",
            resignConfirmTitle = "Confirm resignation",
            resignConfirmMessage = "Are you sure you want to resign?",
            cancel = "Cancel",
            moveCountPrefix = "Moves",
            moveCountSuffix = "",
            lastMove = "Last move",
            time = "Time",
            stoneCountSuffix = "",
            kataGoAnalysis = "KataGo analysis",
            blackAnalysis = "Black analysis",
            whiteAnalysis = "White analysis",
            selected = "Selected",
            noAnalysisInfo = "No analysis to show yet.",
            scoreSnapshotsEmpty = "Score snapshots will appear after the first estimate.",
            engineSource = "engine",
            localSource = "local",
            finalScoreSource = "final",
            finalJudgementTitle = "Judgement",
            reviewJudgement = "Review",
            analyze = "Analyze",
            later = "Later",
            confirm = "OK",
            rerunBenchmark = "Measure again",
            benchmarkDoneTitle = "Device check complete",
            benchmarkRunningTitle = "Checking device performance",
            benchmarkRunningBody = "Checking how long AI analysis takes. This may take 1 to 3 minutes.",
            benchmarkReadyMessage = "AI analysis is ready.",
            recommendedMaximumSearchTime = "Recommended maximum search time",
            benchmarkCautiousMessage = "The measurement was incomplete, so this is a conservative recommendation.",
            benchmarkProgress = "Progress",
            benchmarkEngineSettling = "Waiting for engine to settle...",
            benchmarkSecuringRuntime = "securing runtime...",
            benchmarkPreparing = "Preparing",
            benchmarkSample = "Sample",
            benchmarkTotalProgress = "Total progress",
            benchmarkLastResult = "Last result",
            benchmarkSamples = "Samples",
            benchmarkTimeCap = "Time cap",
            benchmarkPosition = "Position",
            benchmarkPositionMoves = "Position moves",
            benchmarkDetailsIncluded = "Detailed sampleDetails are included in Copy Log.",
            resumeTitle = "Resume previous game",
            resumeMoveCountPrefix = "There is an unfinished",
            resumeMoveCountSuffix = "move game.",
            resumeQuestion = "Resume it?",
            lastMovePrefix = "Last move",
            yes = "Yes",
            no = "No",
            handicap = "Handicap",
            handicapNone = "No handicap",
        )

        val Japanese = Korean.copy(
            language = UiLanguage.Japanese,
            languageLabel = "言語",
            close = "閉じる",
            gameSection = "ゲーム",
            newGame = "新規",
            copyLog = "ログコピー",
            benchmark = "ベンチマーク",
            currentModePrefix = "現在のモード",
            scoringRule = "計算方式",
            boardSize = "盤サイズ",
            playerSetup = "プレイヤー設定",
            searchTime = "探索時間",
            maximumSearchTimeLimit = "最大探索時間制限",
            timeCap = "時間制限",
            timeCapOn = "応答時間を制限",
            timeCapOff = "visits優先",
            recommendedPrefix = "推奨",
            none = "なし",
            autoDelay = "自動遅延",
            engine = "エンジン",
            displayMenu = "表示メニュー",
            directPlay = "即時着手",
            coordinates = "座標",
            moveNumbers = "手数番号",
            lastMoveRing = "最終手表示",
            turnPrefix = "手番",
            movesPrefix = "手数",
            capturesPrefix = "アゲハマ",
            lastPrefix = "最近",
            scoreLead = "目差",
            winRate = "勝率",
            captures = "アゲハマ",
            playMove = "着手",
            pass = "パス",
            undo = "待った",
            topMoves = "候補手",
            eval = "形勢判断",
            resign = "投了",
            newGameAction = "対局開始",
            resignConfirmTitle = "投了の確認",
            resignConfirmMessage = "本当に投了しますか？",
            cancel = "キャンセル",
            moveCountPrefix = "手数",
            moveCountSuffix = "手",
            lastMove = "直前の手",
            time = "時間",
            stoneCountSuffix = "個",
            kataGoAnalysis = "KataGo解析",
            blackAnalysis = "黒の解析",
            whiteAnalysis = "白の解析",
            selected = "選択",
            noAnalysisInfo = "表示できる解析情報はまだありません。",
            scoreSnapshotsEmpty = "最初の形勢判断後にスコア履歴が表示されます。",
            engineSource = "エンジン",
            localSource = "ローカル",
            finalScoreSource = "最終",
            analyze = "解析する",
            later = "後で",
            confirm = "確認",
            rerunBenchmark = "再測定",
            benchmarkDoneTitle = "端末性能の確認完了",
            benchmarkRunningTitle = "端末性能を確認中",
            benchmarkRunningBody = "AIの考える時間を確認しています。1〜3分ほどかかる場合があります。",
            benchmarkReadyMessage = "AI解析の準備ができました。",
            recommendedMaximumSearchTime = "推奨最大探索時間",
            benchmarkCautiousMessage = "測定値が十分でないため、余裕を持った推奨です。",
            benchmarkProgress = "進行",
            benchmarkEngineSettling = "エンジンの安定化を待機中...",
            benchmarkSecuringRuntime = "実行時間を確保中...",
            benchmarkPreparing = "準備中",
            benchmarkSample = "サンプル",
            benchmarkTotalProgress = "全体進捗",
            benchmarkLastResult = "直前の結果",
            benchmarkSamples = "測定サンプル",
            benchmarkTimeCap = "測定上限",
            benchmarkPosition = "測定局面",
            benchmarkPositionMoves = "局面手順",
            benchmarkDetailsIncluded = "詳細なsampleDetailsはメニューのログコピーに含まれます。",
            resumeTitle = "前回の対局を再開",
            resumeMoveCountPrefix = "進行中の",
            resumeMoveCountSuffix = "手の対局があります。",
            resumeQuestion = "再開しますか？",
            lastMovePrefix = "最後の手",
            yes = "はい",
            no = "いいえ",
            handicap = "置き石",
            handicapNone = "置き石なし",
        )

        val ChineseSimplified = Korean.copy(
            language = UiLanguage.ChineseSimplified,
            languageLabel = "语言",
            close = "关闭",
            gameSection = "游戏",
            newGame = "新局",
            copyLog = "复制日志",
            benchmark = "基准测试",
            currentModePrefix = "当前模式",
            scoringRule = "数目规则",
            boardSize = "棋盘大小",
            playerSetup = "玩家设置",
            searchTime = "搜索时间",
            maximumSearchTimeLimit = "最大搜索时间限制",
            timeCap = "时间限制",
            timeCapOn = "限制响应时间",
            timeCapOff = "优先满足 visits",
            recommendedPrefix = "推荐",
            none = "无",
            autoDelay = "自动延迟",
            engine = "引擎",
            displayMenu = "显示菜单",
            directPlay = "直接落子",
            coordinates = "坐标",
            moveNumbers = "手数编号",
            lastMoveRing = "最后一手",
            turnPrefix = "轮到",
            movesPrefix = "手数",
            capturesPrefix = "提子",
            lastPrefix = "最近",
            scoreLead = "目差",
            winRate = "胜率",
            captures = "提子",
            playMove = "落子",
            pass = "停一手",
            undo = "悔棋",
            topMoves = "推荐手",
            eval = "形势判断",
            resign = "认输",
            newGameAction = "开始对局",
            resignConfirmTitle = "确认认输",
            resignConfirmMessage = "确定要认输吗？",
            cancel = "取消",
            moveCountPrefix = "手数",
            moveCountSuffix = "手",
            lastMove = "最近落子",
            time = "时间",
            stoneCountSuffix = "个",
            kataGoAnalysis = "KataGo 分析",
            blackAnalysis = "黑棋分析",
            whiteAnalysis = "白棋分析",
            selected = "选择",
            noAnalysisInfo = "暂无可显示的分析信息。",
            scoreSnapshotsEmpty = "首次形势判断后会显示分数图表。",
            engineSource = "引擎",
            localSource = "本地",
            finalScoreSource = "最终",
            analyze = "分析",
            later = "稍后",
            confirm = "确定",
            rerunBenchmark = "重新测量",
            benchmarkDoneTitle = "设备性能检查完成",
            benchmarkRunningTitle = "正在检查设备性能",
            benchmarkRunningBody = "正在检查 AI 思考所需的时间，可能需要 1 到 3 分钟。",
            benchmarkReadyMessage = "AI 分析已准备就绪。",
            recommendedMaximumSearchTime = "推荐最大搜索时间",
            benchmarkCautiousMessage = "测量数据不完整，因此给出较保守的建议。",
            benchmarkProgress = "进度",
            benchmarkEngineSettling = "等待引擎稳定...",
            benchmarkSecuringRuntime = "正在确保运行时间...",
            benchmarkPreparing = "准备中",
            benchmarkSample = "样本",
            benchmarkTotalProgress = "总进度",
            benchmarkLastResult = "上次结果",
            benchmarkSamples = "测量样本",
            benchmarkTimeCap = "测量上限",
            benchmarkPosition = "测量局面",
            benchmarkPositionMoves = "局面手顺",
            benchmarkDetailsIncluded = "详细 sampleDetails 包含在菜单的复制日志中。",
            resumeTitle = "继续上一局",
            resumeMoveCountPrefix = "有一局未完成的",
            resumeMoveCountSuffix = "手对局。",
            resumeQuestion = "要继续吗？",
            lastMovePrefix = "最后一手",
            yes = "是",
            no = "否",
            handicap = "让子",
            handicapNone = "无让子",
        )

        fun forLanguage(language: UiLanguage): UiStrings =
            when (language) {
                UiLanguage.Korean -> Korean
                UiLanguage.English -> English
                UiLanguage.Japanese -> Japanese
                UiLanguage.ChineseSimplified -> ChineseSimplified
            }
    }
}
