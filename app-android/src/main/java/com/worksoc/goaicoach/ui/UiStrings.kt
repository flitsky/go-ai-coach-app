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
import kotlin.math.roundToInt

internal enum class UiLanguage(
    val menuLabel: String,
) {
    Korean("한국어"),
    English("English"),
    Japanese("日本語"),
    ChineseSimplified("简体中文"),
}

internal val LocalUiStrings = staticCompositionLocalOf { UiStringsKorean }

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
    val maximumSearchTimeLimit: String,
    val timeCap: String,
    val timeCapOn: String,
    val timeCapOff: String,
    val recommendedPrefix: String,
    val none: String,
    val autoDelay: String,
    val engine: String,
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
    val analysis: String,
    val recommendDirectPlayOnPrompt: String,
    val recommendDirectPlayOffPrompt: String,
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
    val startMatch: String,
    val study: String,
    val matchSetup: String,
    val startMatchAction: String,
    val backToHome: String,
    val notImplementedMessage: String,
    val showScoreGraph: String,
    val boardPreview: String,
    val homeStartMatchSubtitle: String,
    val homeStudySubtitle: String,
    val engineCopyNotice: String,
    val cacheOptTitle: String,
    val cacheOptTargetLabel: String,
    val scoreEstimateNotice: String,
    val drawLabel: String,
) {
    fun cacheOptBody(initialCount: Int, maxCount: Int, moveCount: Int, targetCount: Int): String =
        when (language) {
            UiLanguage.Korean -> "이번 판의 주요 국면을 분석 캐시에 저장해도 될까요?\n다음 플레이에서 같은 흐름이 나오면 더 쾌적하게 응수할 수 있습니다.\n\n우선 초반 ${initialCount}수를 확보하고, 안정화되면 ${maxCount}수까지 확장합니다.\n대상: ${moveCount}수 대국 중 최대 ${targetCount}개 JSON 분석"
            UiLanguage.English -> "Would you like to optimize this match using local cache?\nStoring key positions helps responsiveness in future play.\n\nInitially secures the first $initialCount moves, expanding to $maxCount moves later.\nTarget: Up to $targetCount JSON analysis records out of $moveCount moves."
            UiLanguage.Japanese -> "この対局の主要な局面を分析キャッシュに保存しますか？\n次回プレイで同じ流れになった際、より素早く応答できます。\n\nまず序盤${initialCount}手を確保し、安定すれば${maxCount}手まで拡張します。\n対象：${moveCount}手の対局中、最大${targetCount}個のJSON分析"
            UiLanguage.ChineseSimplified -> "是否在分析缓存中优化本局？\n存储关键局面有利于在以后的对局中提高响应速度。\n\n初始时保存前 $initialCount 手，稳定后可扩展到 $maxCount 手。\n目标：从 $moveCount 手对局中提取最多 $targetCount 个 JSON  分析记录。"
        }

    fun removedStonesLabel(black: Int, white: Int): String =
        when (language) {
            UiLanguage.Korean -> "사석 제거: 흑 ${black}개, 백 ${white}개"
            UiLanguage.English -> "Captured stones: Black $black, White $white"
            UiLanguage.Japanese -> "アゲハマ除去: 黒 ${black}子, 白 ${white}子"
            UiLanguage.ChineseSimplified -> "提子：黑 ${black}子，白 ${white}子"
        }

    fun scoringRuleLabel(ruleLabel: String): String =
        when (language) {
            UiLanguage.Korean -> "계가 방식: $ruleLabel"
            UiLanguage.English -> "Scoring rule: $ruleLabel"
            UiLanguage.Japanese -> "整地方式: $ruleLabel"
            UiLanguage.ChineseSimplified -> "计分方式: $ruleLabel"
        }

    fun winnerMarginLabel(colorLabel: String, margin: Double): String {
        val marginText = margin.formatScoreNumber()
        return when (language) {
            UiLanguage.Korean -> "$colorLabel + ${marginText}집 승"
            UiLanguage.English -> "$colorLabel + ${marginText} points win"
            UiLanguage.Japanese -> "$colorLabel + ${marginText}目勝ち"
            UiLanguage.ChineseSimplified -> "$colorLabel + ${marginText}目胜"
        }
    }

    fun winnerWithoutMarginLabel(colorLabel: String): String =
        when (language) {
            UiLanguage.Korean -> "$colorLabel 승"
            UiLanguage.English -> "$colorLabel win"
            UiLanguage.Japanese -> "$colorLabel 勝ち"
            UiLanguage.ChineseSimplified -> "$colorLabel 胜"
        }

    fun scoreTextDetailTerritory(colorLabel: String, territory: Double, prisoners: Double, total: Double): String {
        val tVal = territory.formatScoreNumber()
        val pVal = prisoners.formatScoreNumber()
        val tot = total.formatScoreNumber()
        return when (language) {
            UiLanguage.Korean -> "$colorLabel: 집 $tVal + 사석 $pVal = ${tot}집"
            UiLanguage.English -> "$colorLabel: Territory $tVal + Prisoners $pVal = ${tot} points"
            UiLanguage.Japanese -> "$colorLabel: 地合 $tVal + アゲハマ $pVal = ${tot}目"
            UiLanguage.ChineseSimplified -> "$colorLabel: 目数 $tVal + 提子 $pVal = ${tot}目"
        }
    }

    fun scoreTextDetailArea(colorLabel: String, total: Double): String {
        val tot = total.formatScoreNumber()
        return when (language) {
            UiLanguage.Korean -> "$colorLabel: 돌 + 집 = ${tot}집"
            UiLanguage.English -> "$colorLabel: Stone + Territory = ${tot} points"
            UiLanguage.Japanese -> "$colorLabel: 石 + 地合 = ${tot}目"
            UiLanguage.ChineseSimplified -> "$colorLabel: 子数 + 目数 = ${tot}目"
        }
    }

    fun scoreTextDetailTerritoryKomi(territory: Double, prisoners: Double, komi: Double, total: Double): String {
        val tVal = territory.formatScoreNumber()
        val pVal = prisoners.formatScoreNumber()
        val kVal = komi.formatScoreNumber()
        val tot = total.formatScoreNumber()
        return when (language) {
            UiLanguage.Korean -> "백: 집 $tVal + 사석 $pVal + 덤 $kVal = ${tot}집"
            UiLanguage.English -> "White: Territory $tVal + Prisoners $pVal + Komi $kVal = ${tot} points"
            UiLanguage.Japanese -> "白: 地合 $tVal + アゲハマ $pVal + コミ $kVal = ${tot}目"
            UiLanguage.ChineseSimplified -> "白: 目数 $tVal + 提子 $pVal + 贴目 $kVal = ${tot}目"
        }
    }

    fun scoreTextDetailAreaKomi(komi: Double, total: Double): String {
        val kVal = komi.formatScoreNumber()
        val tot = total.formatScoreNumber()
        return when (language) {
            UiLanguage.Korean -> "백: 돌 + 집 + 덤 $kVal = ${tot}집"
            UiLanguage.English -> "White: Stone + Territory + Komi $kVal = ${tot} points"
            UiLanguage.Japanese -> "白: 石 + 地合 + コミ $kVal = ${tot}目"
            UiLanguage.ChineseSimplified -> "白: 子数 + 目数 + 贴目 $kVal = ${tot}目"
        }
    }

    private fun Double.formatScoreNumber(): String {
        val roundedTenth = (this * 10.0).roundToInt()
        return if (roundedTenth % 10 == 0) {
            (roundedTenth / 10).toString()
        } else {
            (roundedTenth / 10.0).toString()
        }
    }
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
        fun forLanguage(language: UiLanguage): UiStrings =
            when (language) {
                UiLanguage.Korean -> UiStringsKorean
                UiLanguage.English -> UiStringsEnglish
                UiLanguage.Japanese -> UiStringsJapanese
                UiLanguage.ChineseSimplified -> UiStringsChineseSimplified
            }
    }
}
