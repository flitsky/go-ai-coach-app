package com.worksoc.goaicoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
import com.worksoc.goaicoach.application.attendance.AttendanceReward
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.gamehistory.GameHistoryResult
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
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
    val homeTagline: String,
    val languageLabel: String,
    val close: String,
    val gameSection: String,
    val newGame: String,
    val copyLog: String,
    val benchmark: String,
    val currentModePrefix: String,
    val scoringRule: String,
    val komi: String,
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
    val moveReviewToggle: String,
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
    val selected: String,
    val scoreSnapshotsEmpty: String,
    val engineSource: String,
    val localSource: String,
    val finalScoreSource: String,
    val finalJudgementTitle: String,
    val reviewJudgement: String,
    val analyze: String,
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
    val gameHistoryTitle: String,
    val homeGameHistorySubtitle: String,
    val gameHistoryEmptyMessage: String,
    val engineCopyNotice: String,
    val cacheOptTitle: String,
    val cacheOptTargetLabel: String,
    val scoreEstimateNotice: String,
    val drawLabel: String,
    val overwriteWarningTitle: String,
    val overwriteWarningMessage: String,
    val legendBest: String,
    val legendGood: String,
    val legendInaccuracy: String,
    val legendMistake: String,
    val legendBlunder: String,
    val premiumUpsellTitle: String,
    val premiumUpsellMessage: String,
    val premiumUpsellPurchaseOption: String,
    val premiumUpsellAdGrantOption: String,
    val undoClaimTitle: String,
    val undoClaimMessage: String,
    val undoClaimConfirmAction: String,
    val undoClaimSuccessMessage: String,
    /** 추천 수 버튼 라벨 — 1회성 동작이라 상태(ON/OFF)가 아니라 행위로 읽히게 한다. */
    val topMovesAction: String,
    /** 대국 메뉴의 '매 수마다' 옵션 라벨 2종. 켜면 표시가 수마다 갱신된다(프리미엄 전용). */
    val everyMoveEval: String,
    val everyMoveTopMoves: String,
    /** 대국 한 판에 한 번만 뜨는 안내 — 버튼은 1회성이고 상시 보기는 메뉴에 있다는 것. */
    val everyMoveHint: String,
    val consumableSpendTitle: String,
    val consumableSpendConfirmAction: String,
    val consumableSpendCancelAction: String,
    val premiumUpsellUseTicketAction: String,
    val attendanceRewardTitle: String,
    val attendanceRewardClaimAction: String,
    val attendanceRewardLaterAction: String,
    val engineStuckDialogTitle: String,
    val engineStuckDialogMessage: String,
    val engineStuckDialogResetAction: String,
    val engineStuckDialogWaitAction: String,
    val onboardingTitle: String,
    val onboardingSubtitle: String,
    val continueWithGoogle: String,
    val continueWithApple: String,
    val continueWithEmail: String,
    val continueWithoutAccount: String,
    val guestStartedToastMessage: String,
    val googleSignedInToastMessage: String,
    val googleSignInFailedMessage: String,
    val emailFieldLabel: String,
    val passwordFieldLabel: String,
    val emailSignInSubmitLabel: String,
    val emailSignInWeakPasswordMessage: String,
    val emailSignInFailedMessage: String,
    val emailSignedInToastMessage: String,
    val settingsTitle: String,
    val settingsAccountSectionTitle: String,
    val settingsGuestStatusMessage: String,
    val settingsGoogleStatusMessage: String,
    val settingsEmailStatusMessage: String,
    val settingsDeleteAccountButtonLabel: String,
    val settingsDeleteAccountConfirmTitle: String,
    val settingsDeleteAccountConfirmMessage: String,
    val settingsDeleteAccountSuccessMessage: String,
    val settingsDeleteAccountFailedMessage: String,
    val settingsDeleteAccountRecentLoginRequiredMessage: String,
    val settingsDevSectionTitle: String,
    val settingsDevPremiumToggleTitle: String,
    val settingsDevPremiumToggleSubtitle: String,
    val settingsDevGameSetupUxToggleTitle: String,
    val settingsDevGameSetupUxToggleSubtitle: String,
    val settingsVersionLabel: String,
    val settingsBuildTimeLabel: String,
    val settingsPrivacyPolicyLabel: String,
    val settingsDeveloperModeEnabledMessage: String,
    val premiumModeTitle: String,
    val premiumModeFeatureList: String,
    val premiumPurchaseFailedMessage: String,
    val premiumAdGrantFailedMessage: String,
    val handicapEvenGameLabel: String,
    val boardSizeShortLabel: String,
    val enginePreparingTitle: String,
    val enginePreparingSubtitle: String,
    val engineThinkingLabel: String,
    val engineRecommendingLabel: String,
    val engineOptimizingLabel: String,
    val scoringPreparingTitle: String,
) {
    fun settingsDeveloperModeCountdownMessage(remainingTaps: Int): String =
        when (language) {
            UiLanguage.Korean -> "${remainingTaps}번 더 누르면 개발자 모드가 활성화됩니다."
            UiLanguage.English -> "Tap $remainingTaps more time(s) to enable developer mode."
            UiLanguage.Japanese -> "あと${remainingTaps}回タップすると開発者モードが有効になります。"
            UiLanguage.ChineseSimplified -> "再点击 $remainingTaps 次即可启用开发者模式。"
        }

    fun cacheOptBody(initialCount: Int, maxCount: Int, moveCount: Int, targetCount: Int): String =
        when (language) {
            UiLanguage.Korean -> "이번 판의 주요 국면을 분석 캐시에 저장해도 될까요?\n다음 플레이에서 같은 흐름이 나오면 더 쾌적하게 응수할 수 있습니다.\n\n우선 초반 ${initialCount}수를 확보하고, 안정화되면 ${maxCount}수까지 확장합니다.\n대상: ${moveCount}수 대국 중 최대 ${targetCount}개 JSON 분석"
            UiLanguage.English -> "Would you like to optimize this match using local cache?\nStoring key positions helps responsiveness in future play.\n\nInitially secures the first $initialCount moves, expanding to $maxCount moves later.\nTarget: Up to $targetCount JSON analysis records out of $moveCount moves."
            UiLanguage.Japanese -> "この対局の主要な局面を分析キャッシュに保存しますか？\n次回プレイで同じ流れになった際、より素早く応答できます。\n\nまず序盤${initialCount}手を確保し、安定すれば${maxCount}手まで拡張します。\n対象：${moveCount}手の対局中、最大${targetCount}個のJSON分析"
            UiLanguage.ChineseSimplified -> "是否在分析缓存中优化本局？\n存储关键局面有利于在以后的对局中提高响应速度。\n\n初始时保存前 $initialCount 手，稳定后可扩展到 $maxCount 手。\n目标：从 $moveCount 手对局中提取最多 $targetCount 个 JSON  分析记录。"
        }

    /**
     * 1회권을 쓴 직후 띄우는 토스트. 확인 팝업(2026-08-24 결정)을 대체한다 — 오탭 여지가 낮고
     * 오탭 비용도 작아 빠른 진행을 택했다(2026-08-29 사용자 재확정). "말없이 쓰지 않는다"는
     * 원래 취지는 쓴 직후 잔량을 알리는 것으로 지킨다. [remaining]은 **차감 후** 잔량이다.
     */
    fun consumableSpentToast(item: ConsumableItem, remaining: Int): String {
        val name = consumableRewardName(item)
        return when (language) {
            UiLanguage.Korean -> "$name 사용 (잔여: ${remaining}장)"
            UiLanguage.English -> "Used one $name ($remaining left)"
            UiLanguage.Japanese -> "${name} を使用（残り${remaining}枚）"
            UiLanguage.ChineseSimplified -> "已使用${name}（剩余 $remaining 张）"
        }
    }

    /** 1회권 사용 확인 문구. 남은 장수를 함께 보여준다 — 잔량 표시는 "쓰려는 순간"에만 둔다. */
    fun consumableSpendMessage(item: ConsumableItem, remaining: Int): String {
        val name = consumableRewardName(item)
        return when (language) {
            UiLanguage.Korean -> "$name 을(를) 사용할까요?\n사용 후 ${remaining - 1}장 남습니다."
            UiLanguage.English -> "Use one $name?\n${remaining - 1} left after this."
            UiLanguage.Japanese -> "${name} を使用しますか？\n使用後は残り${remaining - 1}枚です。"
            UiLanguage.ChineseSimplified -> "要使用${name}吗？\n使用后剩余 ${remaining - 1} 张。"
        }
    }

    /** 업셀 팝업의 '광고 스킵권 사용' 선택지 — 보유 장수를 함께 보여준다. */
    fun premiumUpsellUseTicketLabel(remaining: Int): String =
        when (language) {
            UiLanguage.Korean -> "${premiumUpsellUseTicketAction} (${remaining}장 보유)"
            UiLanguage.English -> "$premiumUpsellUseTicketAction ($remaining left)"
            UiLanguage.Japanese -> "${premiumUpsellUseTicketAction}（残り${remaining}枚）"
            UiLanguage.ChineseSimplified -> "${premiumUpsellUseTicketAction}（剩余 $remaining 张）"
        }

    /** Claim 팝업의 일차 머리글("1일차"). 밀린 일차를 한 팝업에 모아 보여주기 때문에 필요하다(5.1절). */
    fun attendanceRewardDayLabel(tier: Int): String =
        when (language) {
            UiLanguage.Korean -> "${tier}일차"
            UiLanguage.English -> "Day $tier"
            UiLanguage.Japanese -> "${tier}日目"
            UiLanguage.ChineseSimplified -> "第 $tier 天"
        }

    /** 출석 보상 한 건의 표시 문구(킥오프 플랜 4.2절 정책표 = `AttendanceRewardPolicy`). */
    fun attendanceRewardLabel(reward: AttendanceReward): String =
        when (reward) {
            is AttendanceReward.PermanentFeature -> permanentFeatureRewardLabel(reward.featureId)
            is AttendanceReward.Consumable ->
                consumableRewardName(reward.item) + " " + consumableRewardAmount(reward.amount)
            // 캐릭터 이름/티어명은 한국어 리터럴 하나뿐이다(백로그 #9 확정) — 다른 언어에서도 그대로
            // 보여주고, 접두어만 언어에 맞춘다. 레벨 라벨 전체의 다국어화는 별도 일감이다.
            is AttendanceReward.BotCharacterUnlock -> {
                val tier = reward.character.toPlayLevelSetting()?.tierLabel
                val name = reward.character.name + if (tier != null) " ($tier)" else ""
                when (language) {
                    UiLanguage.Korean -> "새 캐릭터 · $name"
                    UiLanguage.English -> "New character · $name"
                    UiLanguage.Japanese -> "新キャラクター · $name"
                    UiLanguage.ChineseSimplified -> "新角色 · $name"
                }
            }
        }

    private fun permanentFeatureRewardLabel(featureId: FeatureId): String =
        if (featureId == FeatureId.Undo) {
            when (language) {
                UiLanguage.Korean -> "무르기 무제한"
                UiLanguage.English -> "Unlimited Undo"
                UiLanguage.Japanese -> "「待った」無制限"
                UiLanguage.ChineseSimplified -> "无限次悔棋"
            }
        } else {
            val name = featureRewardName(featureId)
            when (language) {
                UiLanguage.Korean -> "$name 영구 해제"
                UiLanguage.English -> "$name unlocked permanently"
                UiLanguage.Japanese -> "$name 恒久解放"
                UiLanguage.ChineseSimplified -> "$name 永久解锁"
            }
        }

    private fun featureRewardName(featureId: FeatureId): String =
        when (featureId) {
            FeatureId.Undo -> when (language) {
                UiLanguage.Korean -> "무르기"
                UiLanguage.English -> "Undo"
                UiLanguage.Japanese -> "待った"
                UiLanguage.ChineseSimplified -> "悔棋"
            }
            FeatureId.Eval -> when (language) {
                UiLanguage.Korean -> "형세 보기"
                UiLanguage.English -> "Score Estimate"
                UiLanguage.Japanese -> "形勢判断"
                UiLanguage.ChineseSimplified -> "形势判断"
            }
            FeatureId.TopMoves -> when (language) {
                UiLanguage.Korean -> "추천 수"
                UiLanguage.English -> "Top Moves"
                UiLanguage.Japanese -> "推奨手"
                UiLanguage.ChineseSimplified -> "推荐着法"
            }
            FeatureId.MoveReview -> when (language) {
                UiLanguage.Korean -> "기보 리뷰"
                UiLanguage.English -> "Move Review"
                UiLanguage.Japanese -> "棋譜レビュー"
                UiLanguage.ChineseSimplified -> "棋谱回顾"
            }
        }

    /** 모르는 종류(상위 버전에서 온 소모품)는 저장 키를 그대로 보여준다 — 지급 사실을 숨기지 않는다. */
    private fun consumableRewardName(item: ConsumableItem): String =
        when (item) {
            ConsumableCatalog.EvalOnce -> onceTicketName(featureRewardName(FeatureId.Eval))
            ConsumableCatalog.TopMovesOnce -> onceTicketName(featureRewardName(FeatureId.TopMoves))
            ConsumableCatalog.PremiumOnce -> when (language) {
                UiLanguage.Korean -> "광고 스킵권"
                UiLanguage.English -> "Ad-skip ticket"
                UiLanguage.Japanese -> "広告スキップ券"
                UiLanguage.ChineseSimplified -> "免广告券"
            }
            else -> item.id.raw
        }

    private fun onceTicketName(featureName: String): String =
        when (language) {
            UiLanguage.Korean -> "$featureName 1회권"
            UiLanguage.English -> "$featureName ticket"
            UiLanguage.Japanese -> "$featureName 1回券"
            UiLanguage.ChineseSimplified -> "$featureName 1次券"
        }

    private fun consumableRewardAmount(amount: Int): String =
        when (language) {
            UiLanguage.Korean -> "${amount}개"
            UiLanguage.English -> "x$amount"
            UiLanguage.Japanese -> "${amount}個"
            UiLanguage.ChineseSimplified -> "${amount}个"
        }

    /** 사람 플레이어 기준 결과 문구. [margin]은 [GameHistoryResult.Win]/[GameHistoryResult.Loss]에서만 쓰인다. */
    fun gameHistoryResultLabel(result: GameHistoryResult, margin: Double?): String {
        val marginText = margin?.let { "%.1f".format(it) }
        return when (result) {
            GameHistoryResult.Resign -> when (language) {
                UiLanguage.Korean -> "기권"
                UiLanguage.English -> "Resigned"
                UiLanguage.Japanese -> "投了"
                UiLanguage.ChineseSimplified -> "认输"
            }
            GameHistoryResult.Draw -> when (language) {
                UiLanguage.Korean -> "무승부"
                UiLanguage.English -> "Draw"
                UiLanguage.Japanese -> "持碁"
                UiLanguage.ChineseSimplified -> "和棋"
            }
            GameHistoryResult.Win -> when (language) {
                UiLanguage.Korean -> if (marginText != null) "승 (${marginText}집)" else "승"
                UiLanguage.English -> if (marginText != null) "Win by $marginText" else "Win"
                UiLanguage.Japanese -> if (marginText != null) "${marginText}目勝ち" else "勝ち"
                UiLanguage.ChineseSimplified -> if (marginText != null) "胜 $marginText 目" else "胜"
            }
            GameHistoryResult.Loss -> when (language) {
                UiLanguage.Korean -> if (marginText != null) "패 (${marginText}집)" else "패"
                UiLanguage.English -> if (marginText != null) "Loss by $marginText" else "Loss"
                UiLanguage.Japanese -> if (marginText != null) "${marginText}目負け" else "負け"
                UiLanguage.ChineseSimplified -> if (marginText != null) "负 $marginText 目" else "负"
            }
        }
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

    fun gameModeLabel(handicapCount: Int): String {
        val mode = if (handicapCount == 0) handicapEvenGameLabel else handicapLabel(handicapCount)
        return when (language) {
            UiLanguage.Korean -> "대국 방식: $mode"
            UiLanguage.English -> "Game mode: $mode"
            UiLanguage.Japanese -> "対局形式: $mode"
            UiLanguage.ChineseSimplified -> "对局方式: $mode"
        }
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

    /** 보드 상단 작은 엔진 상태 텍스트("생각 중"/"추천 중"/"최적화 중")용 라벨. */
    fun engineActivityLabel(indicator: EngineActivityIndicator): String =
        when (indicator) {
            EngineActivityIndicator.Preparing -> benchmarkPreparing
            EngineActivityIndicator.Thinking -> engineThinkingLabel
            EngineActivityIndicator.Recommending -> engineRecommendingLabel
            EngineActivityIndicator.Optimizing -> engineOptimizingLabel
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

    /**
     * 대국 설정 콤팩트 화면 전용 — [handicapLabel]과 달리 "접바둑" 접두어 없이 값만
     * 표시한다(셀 자체의 작은 라벨이 이미 "접바둑"을 보여주므로 중복 표기를 피함).
     * 0점(호선)은 [handicapEvenGameLabel]로 별도 표시한다.
     */
    fun compactHandicapValueLabel(count: Int): String =
        if (count == 0) {
            handicapEvenGameLabel
        } else {
            when (language) {
                UiLanguage.Korean -> "${count}점"
                UiLanguage.English -> "$count"
                UiLanguage.Japanese -> "${count}子"
                UiLanguage.ChineseSimplified -> "${count}子"
            }
        }

    /**
     * 대국 설정 콤팩트 화면 버튼 전용 — 셀 위 라벨 텍스트 없이 버튼 하나로 "무엇을,
     * 어떤 값으로" 설정했는지 알 수 있도록 "라벨 (값)" 형태로 합친 표기.
     */
    fun compactKomiLabel(komiValue: Double): String = "$komi (${komiValueLabel(komiValue)})"

    fun compactBoardSizeLabel(size: BoardSize): String =
        "$boardSizeShortLabel (${size.value}x${size.value})"

    fun compactHandicapLabel(count: Int): String = "$handicap (${compactHandicapValueLabel(count)})"

    /**
     * 대국 설정 콤팩트 화면 전용 — [rulesetLabel]의 괄호 부연 설명을 뺀 짧은 표기.
     */
    fun compactRulesetLabel(ruleset: Ruleset): String =
        when (ruleset) {
            Ruleset.Japanese -> when (language) {
                UiLanguage.Korean -> "집계가"
                UiLanguage.English -> "Territory"
                UiLanguage.Japanese -> "地合"
                UiLanguage.ChineseSimplified -> "数目"
            }
            Ruleset.Chinese -> when (language) {
                UiLanguage.Korean -> "면적계가"
                UiLanguage.English -> "Area"
                UiLanguage.Japanese -> "面積"
                UiLanguage.ChineseSimplified -> "数子"
            }
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
            SeatController.Ai -> {
                val levelText = if (setup.playLevel.group == PlayLevelGroup.FastBeginner) {
                    fastBeginnerTierLabel(setup.playLevel.safeLevel)
                } else {
                    levelLabel(setup.playLevel.safeLevel)
                }
                "${setup.aiEngine.label.ifBlank { engineName }} $levelText"
            }
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

    /** `빠른 초급` 1~5단계의 친근한 이름(초보~초고수). AI 선택 드롭다운과 대국 요약에서 쓴다. */
    fun fastBeginnerTierLabel(level: Int): String =
        when (level.coerceIn(1, PlayLevelGroup.FastBeginner.maxLevel)) {
            1 -> when (language) {
                UiLanguage.Korean -> "초보"
                UiLanguage.English -> "Novice"
                UiLanguage.Japanese -> "初心者"
                UiLanguage.ChineseSimplified -> "新手"
            }
            2 -> when (language) {
                UiLanguage.Korean -> "하수"
                UiLanguage.English -> "Rookie"
                UiLanguage.Japanese -> "初級者"
                UiLanguage.ChineseSimplified -> "低手"
            }
            3 -> when (language) {
                UiLanguage.Korean -> "중수"
                UiLanguage.English -> "Intermediate"
                UiLanguage.Japanese -> "中級者"
                UiLanguage.ChineseSimplified -> "中手"
            }
            4 -> when (language) {
                UiLanguage.Korean -> "고수"
                UiLanguage.English -> "Advanced"
                UiLanguage.Japanese -> "上級者"
                UiLanguage.ChineseSimplified -> "高手"
            }
            else -> when (language) {
                UiLanguage.Korean -> "초고수"
                UiLanguage.English -> "Master"
                UiLanguage.Japanese -> "達人"
                UiLanguage.ChineseSimplified -> "大神"
            }
        }

    fun komiValueLabel(komi: Double): String {
        val valueStr = komi.formatScoreNumber()
        return when (language) {
            UiLanguage.Korean -> "${valueStr}집"
            UiLanguage.English -> "$valueStr pts"
            UiLanguage.Japanese -> "${valueStr}目"
            UiLanguage.ChineseSimplified -> "${valueStr}目"
        }
    }

    fun rulesetLabel(ruleset: Ruleset): String =
        when (ruleset) {
            Ruleset.Japanese -> when (language) {
                UiLanguage.Korean -> "집 계가 (영역+사석 계가)"
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
