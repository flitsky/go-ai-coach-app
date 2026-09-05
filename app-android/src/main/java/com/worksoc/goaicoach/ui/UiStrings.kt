package com.worksoc.goaicoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.persistence.UiLanguageStore
import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
import com.worksoc.goaicoach.application.attendance.AttendanceReward
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.gamehistory.GameHistoryResult
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AllowedVia
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
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

/**
 * 화면 표시 언어를 트리 전역에 공급한다.
 *
 * **선택은 저장된다**(#34). 예전에는 `remember`뿐이라 앱을 껐다 켤 때마다 한국어로 돌아갔는데,
 * 언어 선택 UI가 홈 상단 칩에서 **설정 화면 안으로** 들어오면서 "저장되지 않는 설정"이 되어
 * 그대로 둘 수 없었다. 저장소를 별도로 두는 이유는 [UiLanguageStore]의 주석에 있다.
 */
@Composable
internal fun ProvideUiLanguage(
    content: @Composable (UiLanguage, (UiLanguage) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { UiLanguageStore(context) }
    // 저장된 이름이 없거나 알 수 없는 값이면 한국어로 시작한다 — 값 하나 때문에 앱이 죽지 않게.
    var language by remember {
        mutableStateOf(
            store.loadName()?.let { name -> UiLanguage.entries.firstOrNull { it.name == name } }
                ?: UiLanguage.Korean,
        )
    }
    val strings = remember(language) { UiStrings.forLanguage(language) }
    CompositionLocalProvider(LocalUiStrings provides strings) {
        content(language) { nextLanguage ->
            language = nextLanguage
            store.save(nextLanguage.name)
        }
    }
}

internal data class UiStrings(
    val language: UiLanguage,
    /**
     * 앱 이름(백로그 #97). ⚠️ **한국어 값은 스토어 등록정보의 `[앱 이름]`과 같아야 한다** —
     * `AppNameContractTest`가 런처 라벨·앱 안 타이틀·스토어 셋을 묶어 놓았다.
     *
     * ⚠️ **브랜드가 아니라 서술이라 언어마다 번역한다.** "바둑 AI"는 코인된 이름이 아니라
     * *"바둑 + AI"* 라는 설명이므로, 일본어 사용자에게 한글을 보여 줄 이유가 없다.
     * 실제로 `UiStringsTest`의 상속 그물이 그 실수를 잡아냈다(2026-09-05).
     *
     * ⚠️ **"코치"를 붙이지 말 것** — 코칭 기능이 아직 없다(사용자 결정). 기능이 나오면 그때
     * 네 언어와 스토어·런처를 함께 바꾼다.
     */
    val appTitle: String,
    val homeTagline: String,
    val languageLabel: String,
    /**
     * 글꼴 크기 설정(백로그 #106) — 개발자 도구에서 **정식 설정으로 승격**된 값이다.
     * ⚠️ 선택지 라벨이 **[AppFontScales]의 값 수와 짝을 이룬다.** 배율을 늘리려면 라벨도 늘릴 것
     * (`FontScaleSettingChoiceTest`가 그 어긋남을 잡는다).
     */
    val settingsFontScaleTitle: String,
    val settingsFontScaleNormal: String,
    val settingsFontScaleLarge: String,
    val close: String,
    val gameSection: String,
    val newGame: String,
    val copyLog: String,
    val benchmark: String,
    val currentModePrefix: String,
    val komi: String,
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
    /**
     * 착수 모드 스위치의 두 얼굴(#37). 대국 메뉴의 [directPlay]와 **뜻은 같지만 문자열이 다르다** —
     * 그쪽은 설정 격자의 한 줄이라 `Direct play`처럼 길어도 되지만, 이쪽은 `착수` 버튼 위
     * 100dp 남짓한 칸에 들어가야 해서 4자(전각) 대칭으로 짧게 잡았다. "모드"를 붙이면 배율
     * 1.5배에서 넘친다.
     */
    /**
     * 보드 크기 모드 칩(#38). **라벨은 현재 상태가 아니라 "누르면 무엇이 되는가"다** —
     * `playModeDirect`/`playModeConfirm`과 같은 규칙이다(#37에서 한 번 반대로 만들었다가 고쳤다).
     * 보드 모서리에 얹는 작은 칩이라 짧아야 한다.
     */
    val boardModeFull: String,
    val boardModeInset: String,
    val playModeDirect: String,
    val playModeConfirm: String,
    val coordinates: String,
    /** 착수 시 진동 토글(#36). 라벨이 "이펙트"가 아니라 "진동"인 이유는 실제로 진동만 하기
     *  때문이다 — 하지 않는 일을 약속하는 라벨은 #31에서 이미 한 번 걸렀다. */
    val playHaptic: String,
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
    val startMatch: String,
    val study: String,
    val matchSetup: String,
    val startMatchAction: String,
    /**
     * 엔진이 아직 준비되지 않아 대국을 시작할 수 없을 때의 사유(백로그 #101).
     *
     * ⚠️ **이 문구가 없으면 잠긴 버튼이 고장으로 읽힌다.** 그리고 이 문구가 없던 시절의 동작은
     * 더 나빴다 — 버튼이 눌리고 AI 대국이 **조용히 로컬 2인 대국으로 강등**돼, 사용자가
     * **터치가 죽은 판** 앞에 앉았다.
     */
    val engineNotReadyToStart: String,
    /**
     * ⚠️ 위 [engineNotReadyToStart]와 **다른 상태**다 — 저쪽은 *"아직"* 이고 이쪽은 *"안 된다"* 다.
     * 기조 1ⓒ(백로그 「핵심 동작 기조」)를 읽지 않고 둘을 섞지 말 것.
     */
    val engineUnavailableTitle: String,
    val engineUnavailableMessage: String,
    /** 대국 화면에 계속 떠 있는 짧은 표식 — 팝업은 한 번 닫으면 사라지기 때문이다. */
    val engineUnavailableBadge: String,
    val backToHome: String,
    val notImplementedMessage: String,
    val showScoreGraph: String,
    val boardPreview: String,
    val homeStartMatchSubtitle: String,
    val homeStudySubtitle: String,
    val gameHistoryTitle: String,
    val homeGameHistorySubtitle: String,
    val gameHistoryEmptyMessage: String,
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
    /**
     * 추천 수 버튼 라벨 — 1회성 동작이라 상태(ON/OFF)가 아니라 행위로 읽히게 한다.
     *
     * 2026-08-30에 한국어를 "추천 수 받기" → "추천 수 보기"로 바꿨다. 사용자가 지적한 비대칭의
     * 정체는 "동사가 있고 없고"가 아니라 **형세는 '보기', 추천 수는 '받기'라는 서로 다른 동사**였다.
     * 머리동사를 맞추니 한 줄로 대칭이 되고, 폭도 그대로다.
     *
     * ⚠️ **'형세 보기'를 '형세 판단'으로 개명하는 안은 택하지 않았다.** 용어 통일 이득(일/중은 이미
     * 形勢判断)보다 파급이 크다 — 그 문자열은 버튼([UiStrings.eval])과 보상/1회권 이름
     * ([featureRewardName])에 **서로 다른 리터럴로 두 번** 박혀 있고, 거기서 마이 페이지 재고·출석
     * 보상 팝업·사용 토스트·업셀·로비 배지가 파생되며, 2026-08-30에 확정한 Play 스토어 등록정보
     * 본문과 스크린샷 한 장까지 걸린다. 개명은 별건으로 다룬다.
     */
    val topMovesAction: String,
    /** 대국 메뉴의 '매 수마다' 옵션 라벨 2종. 켜면 표시가 수마다 갱신된다(프리미엄 전용). */
    val everyMoveEval: String,
    val everyMoveTopMoves: String,
    /**
     * 대국 한 판에 한 번만 뜨는 안내 — 버튼은 1회성이고 상시 보기는 메뉴에 있다는 것.
     * ⚠️ 잔량 문구와 **한 토스트에 두 줄로 합쳐 뜬다**(GamePlaySection.toastForTap) —
     * 안드로이드 토스트는 2줄까지만 보여주고 넘치면 자르므로 짧게 유지할 것.
     */
    val everyMoveHint: String,
    /** 캐릭터 픽커 제목. */
    val botPickerTitle: String,
    /**
     * 캐릭터 획득 축전 팝업(백로그 #69).
     *
     * ⚠️ **`fun`이 아니라 `val` 필드로 둔다** — `UiStringsTest`의 리플렉션 그물은 String **필드**만
     * 훑으므로, 함수로 노출하면 4개 언어 누락을 아무도 못 본다(함정 10번, `botUnlockHint`가 그렇게
     * 사실이 아닌 문구를 오래 달고 있었다).
     */
    val botAcquiredTitle: String,
    /** 팝업을 어떻게 닫는지 — 버튼이 없으므로 이 한 줄이 유일한 안내다. */
    val botAcquiredDismissHint: String,
    /** 픽커 닫기. */
    val botPickerCloseAction: String,

    val premiumUpsellUseTicketAction: String,
    val attendanceRewardTitle: String,
    val attendanceRewardClaimAction: String,
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
    val settingsAdvancedDeveloperModeEnabledMessage: String,
    val settingsDevTierBasicTitle: String,
    /**
     * 개발자 모드 진입/해제 안내(백로그 #99).
     *
     * ⚠️ **진입 경고가 사실이어야 한다** — 켜면 실제로 3시간마다 최초 설치 상태로 돌아간다
     * (`DeveloperModeResetCoordinator`). 문구만 겁을 주고 실제로는 안 지우면 거짓말이 되고,
     * 반대로 지우면서 말하지 않으면 데이터를 빼앗는 셈이다.
     */
    val settingsDeveloperModeOptInTitle: String,
    val settingsDeveloperModeOptInMessage: String,
    val settingsDeveloperModeOptInConfirm: String,
    val settingsDeveloperModeOffAction: String,
    val settingsDeveloperModeOffTitle: String,
    val settingsDeveloperModeOffMessage: String,
    val settingsDeveloperModeOffDoneMessage: String,
    val settingsDevTierAdvancedTitle: String,
    val settingsDevBuildInfoTitle: String,
    val settingsDevGrantTicketTitle: String,
    val settingsDevGrantTicketSubtitle: String,
    val settingsDevGrantAction: String,
    val settingsDevShardTitle: String,
    val settingsDevShardAlmostAction: String,
    val settingsDevShardClearAction: String,
    val settingsDevAttendanceTitle: String,
    val settingsDevAttendanceAdvanceAction: String,
    val settingsDevReleaseResetTitle: String,
    val settingsDevReleaseResetSubtitle: String,
    /**
     * 진행 중 대국에서 판 크기·접바둑이 잠겼을 때의 사유 문구(백로그 #75).
     *
     * ⚠️ **잠근 이유를 말하지 않으면 고장으로 읽힌다.** 이 항목을 만든 계기 자체가
     * *"바꿨는데 왜 그대로지"* 라는 어긋남이었으므로, 잠금만 넣고 설명을 빼면 같은 종류의
     * 혼란을 모양만 바꿔 남기는 셈이 된다. **어디서 바꾸면 되는지**까지 말한다.
     */
    val boardShapeLockedDuringGame: String,
    /**
     * 기기에만 저장된다는 **소실 정책 고지**(백로그 #74 ⓒ, 2026-09-05 사용자 발주).
     *
     * ⚠️ **잃기 전에 알리는 것이 이 문구의 목적이다.** 복원 안내(#74 ⓐ)는 *이미 잃은 뒤* 뜨는
     * 메시지이고 구매 플래그에 막혀 있지만, 이쪽은 구매와 무관해 지금 띄울 수 있다.
     * · ⚠️ **구매 복원은 아직 여기 적지 않는다** — `FeatureFlags.isPurchaseEnabled`가 꺼져 있어
     *   구매 자체가 불가능하다. 복원 문장은 #74 ⓐ가 열릴 때 함께 붙일 것.
     */
    val localOnlyDataNoticeTitle: String,
    val localOnlyDataNoticeBody: String,
    /** 돋보기 창 크기 선택 줄의 라벨(백로그 #85). */
    val magnifierWindowSizeLabel: String,
    /** 돋보기 확대 배율 선택 줄의 라벨(백로그 #85). */
    val magnifierZoomLabel: String,
    val settingsDevReleaseResetAction: String,
    val settingsDevReleaseResetDoneMessage: String,
    val settingsDevReleaseResetNothingMessage: String,
    val settingsDevAdGrantTitle: String,
    val settingsDevAdGrantAction: String,
    val settingsDevDiagnosticLogTitle: String,
    val settingsDevDiagnosticLogSubtitle: String,
    val settingsDevDiagnosticLogOpenAction: String,
    val settingsDevDiagnosticLogCopyAction: String,
    val settingsDevDiagnosticLogCopied: String,
    val settingsDevPremiumToggleTitle: String,
    val settingsDevPremiumToggleSubtitle: String,
    val settingsVersionLabel: String,
    val settingsBuildTimeLabel: String,
    /** 동의 폼 초기화(#89, debug 2차 전용). */
    val settingsDevConsentResetTitle: String,
    val settingsDevConsentResetSubtitle: (Boolean) -> String,
    val settingsDevConsentResetAction: String,
    val settingsPrivacyPolicyLabel: String,
    /** 광고 개인정보 옵션(동의 철회) 진입점(#89). AdMob 콘솔에서 켠 경우에만 화면에 나온다. */
    val settingsAdPrivacyOptionsLabel: String,
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
     * 학습 화면 강좌 한 편의 소개. 문구 표와 "한국어 강의임을 밝히는" 결정 근거는
     * `UiStringsStudyVideos.kt`에 있다(백로그 #33).
     */
    fun studyVideoDescription(entry: StudyVideoEntry): String =
        studyVideoDescriptionFor(language, entry.id)

    /**
     * 캐릭터의 표시 이름. 문구 표와 그 번역 근거는 `UiStringsBotCharacters.kt`에 있다(백로그 #32) —
     * 도메인([BotCharacter])은 더 이상 사람이 읽는 이름을 갖지 않는다.
     */
    fun botCharacterName(character: BotCharacter): String = botCharacterNameFor(language, character.id)

    /** 캐릭터 픽커에 붙는 한 줄 소개. */
    fun botCharacterDescription(character: BotCharacter): String =
        botCharacterDescriptionFor(language, character.id)

    /**
     * 캐릭터 픽커의 한 줄 라벨 — **이름 옆에 티어명을 병기**한다(백로그 #9 확정). 이름만으로는
     * 어느 쪽이 센지 모호해서, 캐릭터 선택이 곧 난이도 선택이라는 것이 드러나지 않는다.
     */
    /**
     * 개발자 2차의 출석 진행 버튼 부제(백로그 #71) — **지금 몇 일차이고 다음이 보상 회차인지**를
     * 말한다.
     *
     * ⚠️ **이 안내가 없으면 버튼이 고장난 것처럼 보인다.** 8~13·15~20·22~27일차는
     * `isRewardedTier`가 false라 **원래 팝업이 뜨지 않는다** — 숫자를 보여 주지 않으면 그 구간에서
     * 버튼을 눌러 놓고 "안 된다"고 오진하게 된다.
     *
     * ⚠️ **`fun`이라 `UiStringsTest`의 리플렉션 그물이 못 본다**(함정 10번) —
     * `UiStringsDevAttendanceTest`가 네 언어를 손으로 고정한다.
     */
    /**
     * 개발자 2차의 "광고 본 것으로 프리미엄 1시간" 부제(백로그 #78) — **남은 시간**을 말한다.
     *
     * ⚠️ 버튼이지 토글이 아니므로 화면이 상태를 보여 줄 다른 자리가 없다. 만료를 눈으로 확인하는
     * 것이 이 버튼의 목적 절반이라(#26의 구독 유효기간 판정 전초전) 남은 시간이 곧 결과 표시다.
     *
     * ⚠️ **`fun`이라 리플렉션 그물이 못 본다**(함정 10번) — `UiStringsDevAdGrantTest`가 손 그물이다.
     */
    fun settingsDevAdGrantSubtitle(remainingMinutes: Int?): String =
        when (language) {
            UiLanguage.Korean -> remainingMinutes?.let { "지금 활성 · ${it}분 남음" } ?: "지금 꺼져 있음 · 누르면 1시간"
            UiLanguage.English -> remainingMinutes?.let { "Active · $it min left" } ?: "Off now · tap for one hour"
            UiLanguage.Japanese -> remainingMinutes?.let { "有効 · 残り${it}分" } ?: "無効 · 押すと1時間"
            UiLanguage.ChineseSimplified -> remainingMinutes?.let { "已启用 · 剩余 $it 分钟" } ?: "未启用 · 点击获得 1 小时"
        }

    /**
     * 글꼴 배율 행 부제(백로그 #81) — **지금 배율**과 **그것이 앱에 저장된 값**임을 말한다.
     *
     * ⚠️ **"앱에 저장됨"이라고 적는다.** 이 앱은 시스템 배율을 따르지 않으므로, 시스템 값처럼
     * 읽히면 *"시스템에서 바꿨는데 왜 안 변하지"* 로 오진한다 — 그 오진이 실제로 이 항목을 만든
     * 제보였다(2026-09-04).
     *
     * ⚠️ **`fun`이라 리플렉션 그물이 못 본다**(함정 10번) — `UiStringsDevFontScaleTest`가 손 그물이다.
     */
    /**
     * 돋보기 창 크기 칩의 값 표기(백로그 #85). `1.0`이 #39 당시 크기라 **"기본"** 으로 읽히게 한다.
     *
     * ⚠️ `fun`이라 리플렉션 그물에 안 잡힌다(함정 10번) — `UiStringsMagnifierTest`가 손 그물이다.
     */
    fun magnifierSizeOptionLabel(scale: Float): String = when {
        scale == 1.0f -> when (language) {
            UiLanguage.Korean -> "기본"
            UiLanguage.English -> "Standard"
            UiLanguage.Japanese -> "標準"
            UiLanguage.ChineseSimplified -> "标准"
        }
        else -> "×$scale"
    }

    /**
     * 돋보기 확대 배율 칩의 값 표기(백로그 #85).
     *
     * ⚠️ **`1.0`을 "확대 없음"이 아니라 "판 크기 그대로"로 적는다** — 사용자의 표현이
     * *"기본 대국보드판 사이즈 그대로"* 였고, 그 편이 정확하다. 확대가 없어도 손가락이 가린
     * 자리를 위에 띄워 주므로 **돋보기로서는 여전히 동작한다.**
     */
    fun magnifierZoomOptionLabel(zoom: Float): String = when {
        zoom == 1.0f -> when (language) {
            UiLanguage.Korean -> "판 그대로"
            UiLanguage.English -> "Actual size"
            UiLanguage.Japanese -> "盤と同じ"
            UiLanguage.ChineseSimplified -> "与棋盘相同"
        }
        else -> "${(zoom * 100).toInt()}%"
    }

    fun settingsDevAttendanceSubtitle(current: Int, next: Int, nextIsRewarded: Boolean): String =
        when (language) {
            UiLanguage.Korean ->
                if (nextIsRewarded) "지금 ${current}일차 · 누르면 ${next}일차 보상" else "지금 ${current}일차 · ${next}일차는 보상 없음"
            UiLanguage.English ->
                if (nextIsRewarded) "Day $current now · tap for day $next reward" else "Day $current now · day $next has no reward"
            UiLanguage.Japanese ->
                if (nextIsRewarded) "現在${current}日目 · 押すと${next}日目の報酬" else "現在${current}日目 · ${next}日目は報酬なし"
            UiLanguage.ChineseSimplified ->
                if (nextIsRewarded) "当前第 $current 天 · 点击获得第 $next 天奖励" else "当前第 $current 天 · 第 $next 天无奖励"
        }

    fun botCharacterLabel(character: BotCharacter): String =
        "${botCharacterName(character)} (${fastBeginnerTierLabel(character.tierWithinGroup ?: 1)})"

    /**
     * 잠긴 캐릭터의 **획득 방법** 안내. 경로가 셋으로 갈리므로(출석/광고 조각/유료) 각각을
     * 구분해 보여준다 — 픽커에서 "왜 못 고르는가"가 곧 "무엇을 하면 되는가"여야 한다.
     *
     * 유료 캐릭터의 가격은 Play Console 상품이 아직 없어 적지 않는다(#18).
     */
    fun botUnlockHint(source: BotUnlockSource, shards: Int = 0): String? =
        when (source) {
            BotUnlockSource.Default -> null
            is BotUnlockSource.Attendance -> when (language) {
                UiLanguage.Korean -> "출석 ${source.tier}일차에 받을 수 있어요"
                UiLanguage.English -> "Comes with day ${source.tier} of check-in"
                UiLanguage.Japanese -> "出席${source.tier}日目で獲得"
                UiLanguage.ChineseSimplified -> "签到第${source.tier}天可获得"
            }
            is BotUnlockSource.AdShards -> botShardUnlockHint(source.required, shards)
            // 가격을 문구에 박지 않는다 — Play가 지역/통화별로 다른 값을 보여주는데 앱이 하나를
            // 적어 두면 어긋난다. 실제 금액은 구매 시트가 알려 준다(#18).
            BotUnlockSource.Purchase -> when (language) {
                UiLanguage.Korean -> "구매하면 바로 열려요 · 이 상대와 둘 땐 형세 보기·추천 수 무제한"
                UiLanguage.English -> "Buy to unlock · unlimited eval and top moves against this opponent"
                UiLanguage.Japanese -> "購入で解放 · この相手との対局では形勢・推奨手が無制限"
                UiLanguage.ChineseSimplified -> "购买即可解锁 · 与该对手对局时形势判断和推荐手无限使用"
            }
        }

    /**
     * 조각 경로 캐릭터의 해금 힌트(백로그 #64 ⓒ). **두 줄이다** — 첫 줄은 *지금 어디까지 왔는가*,
     * 둘째 줄은 *무엇을 하면 되는가*. 사용자 지시가 *"중요한 정보이면 개행해서 아랫줄에 표현하기"*
     * 였고, 줄을 미리 갈라 두면 배율이 올라 접히더라도 **첫 줄만은 통째로** 읽힌다.
     *
     * ## 예전 문구(`조각 10개 필요 — 광고 시청, 7일차 출석 (3/10)`)를 버린 이유 셋
     * ⓐ **진행이 글자에 안 드러났다.** 광고를 봐서 얼굴은 1/5이 칠해졌는데 글자는 여전히
     *   *"10개 필요"* 여서 뭐가 달라졌는지 읽히지 않았다(2026-09-01 사용자 제보). 이제 **남은
     *   수**를 말하고, 뒤에 붙던 `(3/10)`은 같은 말을 두 번 하는 것이라 뺐다.
     * ⓑ **`7일차 출석`은 사실이 아니었다.** 확정표(`AttendanceRewardPolicy`)는 조각을
     *   **5·6일차에 1개씩** 주고, 그 회차는 `isRewardedTier`상 **반복되지 않는다**(8일차 위로는
     *   7의 배수만 반복). 7일차는 캐릭터(도장생 반상)뿐이다.
     * ⓒ **그 출석 1개는 "경로"라 부르기 어렵다.** 받아도 돌뫼는 광고 4번, 묘수는 광고 9번이
     *   여전히 필요하다 — 표의 KDoc도 *"조각의 무광고 경로는 의도적으로 없다"* 고 못박고 있다.
     *   그래서 **광고만 남겼다**(2026-09-01 사용자 결정). ⚠️ 이것은 2026-08-29의 *"획득 수단을
     *   둘 다 적는다"* 를 **의도적으로 뒤집은 것**이다 — 되돌리려면 없는 경로를 만들어 놓고
     *   되돌리는 것이 아닌지부터 볼 것.
     *
     * ⚠️ **경계는 하나뿐이다(0개 / 1개 이상).** *"다 모았는데 아직 잠김"* 은 도달할 수 없다 —
     * `BotCollectionState.withAdShard`가 `next >= required`인 순간 영구 획득으로 넘기므로 그
     * 상태의 카드는 `isAvailable`이라 힌트를 아예 그리지 않는다. 아래 `coerceAtLeast(1)`은
     * 저장값이 깨져 흘러들었을 때 *"0개 남았어요"* 라는 거짓말을 하지 않으려는 방어일 뿐이다.
     */
    private fun botShardUnlockHint(required: Int, shards: Int): String {
        val remaining = (required - shards).coerceAtLeast(1)
        val progress = if (shards <= 0) {
            when (language) {
                UiLanguage.Korean -> "조각 ${required}개를 모으면 열려요"
                UiLanguage.English -> "Collect $required shards to unlock"
                UiLanguage.Japanese -> "かけら${required}個で解放"
                UiLanguage.ChineseSimplified -> "集齐 $required 个碎片即可解锁"
            }
        } else {
            when (language) {
                UiLanguage.Korean -> "조각 ${remaining}개 남았어요"
                // ⚠️ 마지막 한 개일 때 `1 shards`가 되지 않게 한다 — 네 언어 중 수 일치가
                // 필요한 것은 영어뿐이다.
                UiLanguage.English -> "$remaining shard${if (remaining == 1) "" else "s"} to go"
                UiLanguage.Japanese -> "かけら残り${remaining}個"
                UiLanguage.ChineseSimplified -> "还差 $remaining 个碎片"
            }
        }
        val howToEarn = when (language) {
            UiLanguage.Korean -> "광고를 보면 1개씩 모여요"
            UiLanguage.English -> "One shard per ad you watch"
            UiLanguage.Japanese -> "広告1回で1個たまります"
            UiLanguage.ChineseSimplified -> "每观看 1 次广告得 1 个"
        }
        return "$progress\n$howToEarn"
    }

    /** 홈의 마이 페이지 진입 카드 부제(#24). 제목은 화면 제목을 그대로 쓴다(대국 기록 카드와 동일). */
    val homeMyPageSubtitle: String
        get() = when (language) {
            UiLanguage.Korean -> "보유한 1회권을 확인해보세요."
            UiLanguage.English -> "See the one-shot tickets you hold."
            UiLanguage.Japanese -> "所持しているチケットを確認できます。"
            UiLanguage.ChineseSimplified -> "查看你持有的单次券。"
        }

    /** 마이 페이지 화면 제목(#24). 출석 현황·캐릭터 컬렉션도 앞으로 여기 붙는다. */
    val myPageTitle: String
        get() = when (language) {
            UiLanguage.Korean -> "마이 페이지"
            UiLanguage.English -> "My Page"
            UiLanguage.Japanese -> "マイページ"
            UiLanguage.ChineseSimplified -> "我的"
        }

    /** 마이 페이지의 1회권 재고 절 머리글. */
    val myPageInventoryTitle: String
        get() = when (language) {
            UiLanguage.Korean -> "보유 중인 1회권"
            UiLanguage.English -> "Your one-shot tickets"
            UiLanguage.Japanese -> "所持しているチケット"
            UiLanguage.ChineseSimplified -> "持有的单次券"
        }

    /** 재고를 어디서 쓰고 어떻게 얻는지 한 줄 안내. */
    val myPageInventoryHint: String
        get() = when (language) {
            UiLanguage.Korean -> "대국 중 버튼에도 남은 수가 표시됩니다. 매일 출석하면 더 받을 수 있어요."
            UiLanguage.English -> "The in-game buttons show the count too. Check in daily to earn more."
            UiLanguage.Japanese -> "対局中のボタンにも残数が表示されます。毎日出席すると増えます。"
            UiLanguage.ChineseSimplified -> "对局中的按钮也会显示剩余数量。每日签到可获得更多。"
        }

    /**
     * 인게임 코칭 버튼(형세 보기·추천 수)의 라벨에 **지금 상태를 괄호로 녹인다**(백로그 #24).
     *
     * 대국 화면에 재고 바를 상시 띄우는 대신 버튼 자신이 말하게 한 것이다 — 재고는 쓰는 자리
     * 바로 그곳에서만 궁금하고, 전체 목록은 마이 페이지가 맡는다.
     *
     * 표기는 상태마다 다르다(2026-08-30 사용자 확정):
     * - **프리미엄 활성** → `(∞)`. 사용 **횟수**에 제한이 없다는 뜻이다.
     * - **광고 1시간 활성** → `($TimeLimitedMark)`. 무제한이지만 **시간 한정**이라는 것이 드러나야 하므로
     *   무한대가 아니라 시계다 — `∞`로 적으면 프리미엄과 구분되지 않는다.
     *
     * ⚠️ **`∞`가 말하는 축은 "횟수"이지 "기간"이 아니다**(2026-09-03 사용자 확정, #26의 전제).
     * 프리미엄이 **영구 구매 → 월간 정기 구독**으로 바뀌어도 이 표기는 **그대로 `(∞)`다** — 구독이
     * 유효한 동안 횟수가 무제한인 것이 맞고, 유효기간은 별개 축이라 버튼이 말할 일이 아니다.
     * · ⚠️ **그때 `(⏱)`로 바꾸고 싶어질 텐데, 바꾸지 말 것.** `⏱`은 **광고 1시간**과 짝이라
     *   로비 카운트다운과 함께 읽히고, 구독에 붙이면 **"1시간 남음"으로 오독된다**(#26에 같은
     *   결정이 2026-08-31에 이미 적혀 있다).
     * · 그래서 **판정도 "영구히 샀는가"가 아니라 "지금 유효한가"여야 한다** — 구독 전환 시
     *   `PremiumState.isActive`가 그 축을 갖는다(#26 ⓐ·ⓑ).
     * - **1회권 보유** → `(3)` 처럼 남은 수.
     * - **그 밖** → 괄호 없이 이름만. 여기에 **영구 클레임([AllowedVia.Claimed])도 포함된다**:
     *   무르기처럼 클레임으로 열린 기능에는 무제한 표시를 붙이지 않는다(사용자 확정).
     *
     * ⚠️ **이 함수는 프리미엄/소모품이 걸린 버튼에만 쓴다.** 기권·통과·무르기 같은 나머지 버튼은
     * 간결한 텍스트 그대로 두는 것이 이 항목의 범위다.
     *
     * ⚠️ [AllowedVia.CharacterPerk]는 아직 표기를 정하지 않아 괄호를 붙이지 않는다 — "상대 한정
     * 무제한"이라 무한대와도 시계와도 성격이 다르다. 유료 캐릭터를 다시 열 때 함께 정한다(#18).
     */
    fun featureButtonLabel(base: String, access: FeatureAccess, remaining: Int): String =
        featureButtonMark(access, remaining)?.let { mark -> "$base ($mark)" } ?: base

    /**
     * [featureButtonLabel]에서 **괄호 안만** 떼어 낸 것(#27).
     *
     * 버튼은 이것을 **별도 `Text`로** 그려 이름보다 먼저 자리를 잡게 한다. 한 문자열로 두면
     * 표기가 문자열 끝이라, 폭이 모자랄 때 **하필 그 표기부터 사라진다** — #24가 상시 재고 바를
     * 걷어내고 그 자리를 대신하라고 심어 둔 정보가 가장 먼저 죽는 셈이었다.
     *
     * 괄호 자체는 버튼이 붙이므로 여기서는 기호/숫자만 돌려준다. 표기 규칙의 원본은
     * [featureButtonLabel]의 주석이다. [featureButtonLabel]은 지우지 않는다 — 화면 낭독기용
     * `contentDescription`으로 계속 쓴다.
     */
    fun featureButtonMark(access: FeatureAccess, remaining: Int): String? =
        when {
            access is FeatureAccess.Allowed && access.via == AllowedVia.Purchase -> UnlimitedMark
            access is FeatureAccess.Allowed && access.via == AllowedVia.AdGrant -> TimeLimitedMark
            access is FeatureAccess.Allowed -> null
            remaining > 0 -> remaining.toString()
            else -> null
        }

    /** 캐릭터를 구매해 영구 획득했을 때(#18). 특전까지 함께 알려 무엇을 샀는지 분명히 한다. */
    fun botPurchasedToast(character: BotCharacter): String =
        when (language) {
            UiLanguage.Korean -> "${botCharacterName(character)} 획득! 이 상대와 둘 땐 형세 보기·추천 수를 무제한으로 쓸 수 있어요."
            UiLanguage.English -> "${botCharacterName(character)} unlocked! Eval and top moves are unlimited against this opponent."
            UiLanguage.Japanese -> "${botCharacterName(character)} を獲得！この相手との対局では形勢・推奨手が無制限です。"
            UiLanguage.ChineseSimplified -> "已获得${botCharacterName(character)}！与该对手对局时形势判断和推荐手可无限使用。"
        }

    /**
     * 캐릭터 구매가 성사되지 않았을 때(#18). 사유별로 가른다 — 사용자가 스스로 취소한 것과
     * 상품/결제가 안 되는 것은 다음에 할 일이 다르다. 조각 광고 실패 안내와 같은 기준이다.
     */
    fun botPurchaseFailedMessage(reason: PurchaseFailureReason): String =
        when (reason) {
            // 스스로 닫은 것이므로 실패라고 말하지 않는다.
            PurchaseFailureReason.UserCancelled -> when (language) {
                UiLanguage.Korean -> "구매를 취소했어요."
                UiLanguage.English -> "Purchase cancelled."
                UiLanguage.Japanese -> "購入をキャンセルしました。"
                UiLanguage.ChineseSimplified -> "已取消购买。"
            }
            // 계좌이체 등 — 확정되면 다음 실행의 복원 조회에서 잡힌다.
            PurchaseFailureReason.Pending -> when (language) {
                UiLanguage.Korean -> "결제 확인 중이에요. 완료되면 자동으로 열립니다."
                UiLanguage.English -> "Payment is pending. It will unlock once it completes."
                UiLanguage.Japanese -> "支払いを確認中です。完了すると自動的に解放されます。"
                UiLanguage.ChineseSimplified -> "正在确认支付，完成后会自动解锁。"
            }
            else -> when (language) {
                UiLanguage.Korean -> "지금은 구매할 수 없어요. 잠시 후 다시 시도해 주세요."
                UiLanguage.English -> "Can't purchase right now. Please try again in a moment."
                UiLanguage.Japanese -> "現在購入できません。しばらくしてからお試しください。"
                UiLanguage.ChineseSimplified -> "当前无法购买，请稍后再试。"
            }
        }

    /**
     * 획득하지 않은 상대로 설정돼 있어 자동으로 낮췄다는 안내(#22).
     *
     * **조용히 바꾸지 않는다**(2026-08-29 사용자 결정) — 설정이 저 혼자 바뀌면 "내 상대가 왜
     * 바뀌었지"가 된다. 두 이름을 화살표로 나란히 보여, 무엇에서 무엇으로 바뀌었는지가 한눈에
     * 드러나게 한다. 한국어 조사(로/으로)를 피한 것도 의도적이다 — 캐릭터 이름의 받침 유무에
     * 따라 조사가 갈리는데, 이름은 콘텐츠라 앞으로 바뀔 수 있다.
     */
    fun botLevelClampedMessage(from: BotCharacter, to: BotCharacter): String =
        when (language) {
            UiLanguage.Korean -> "아직 획득하지 않은 상대라 바꿨어요: ${botCharacterName(from)} → ${botCharacterName(to)}"
            UiLanguage.English -> "Switched opponent: ${botCharacterName(from)} → ${botCharacterName(to)} (not unlocked yet)"
            UiLanguage.Japanese -> "未獲得の相手なので変更しました: ${botCharacterName(from)} → ${botCharacterName(to)}"
            UiLanguage.ChineseSimplified -> "该对手尚未解锁，已切换：${botCharacterName(from)} → ${botCharacterName(to)}"
        }

    /**
     * 조각 광고가 실패했을 때의 안내. **사유별로 문구가 다르다** — "광고를 못 불러왔다"는 구글
     * 쪽 사정이라 나중에 다시 시도하면 되지만, "끝까지 안 봤다"는 사용자가 지금 바꿀 수 있는
     * 일이다. 프리미엄용 문구(`premiumAdGrantFailedMessage`)를 재사용하면 캐릭터 조각을 모으던
     * 사용자에게 "프리미엄이 활성화되지 않았습니다"라는 엉뚱한 안내가 나간다(2026-08-29 정정).
     */
    fun botShardAdFailedMessage(reason: AdRewardFailureReason): String =
        when (reason) {
            AdRewardFailureReason.DismissedWithoutReward -> when (language) {
                UiLanguage.Korean -> "끝까지 보셔야 조각을 받을 수 있어요."
                UiLanguage.English -> "Watch to the end to earn a shard."
                UiLanguage.Japanese -> "最後まで視聴するとかけらを獲得できます。"
                UiLanguage.ChineseSimplified -> "看完整段广告才能获得碎片。"
            }
            // 로드/노출 실패와 Activity 미확보는 전부 앱 밖의 사정이라 같은 안내로 묶는다.
            AdRewardFailureReason.LoadFailed,
            AdRewardFailureReason.ShowFailed,
            AdRewardFailureReason.Unavailable,
            -> when (language) {
                UiLanguage.Korean -> "광고를 불러오지 못했어요, 잠시 후 다시 시도해 주세요."
                UiLanguage.English -> "Couldn't load an ad. Please try again in a moment."
                UiLanguage.Japanese -> "広告を読み込めませんでした。しばらくしてからもう一度お試しください。"
                UiLanguage.ChineseSimplified -> "广告加载失败，请稍后再试。"
            }
        }

    /** 조각 1개를 적립했을 때(아직 획득 전). */
    fun botShardEarnedToast(character: BotCharacter, shards: Int, required: Int): String =
        when (language) {
            UiLanguage.Korean -> "${botCharacterName(character)} 조각 $shards/$required"
            UiLanguage.English -> "${botCharacterName(character)} $shards/$required"
            UiLanguage.Japanese -> "${botCharacterName(character)} かけら $shards/$required"
            UiLanguage.ChineseSimplified -> "${botCharacterName(character)} 碎片 $shards/$required"
        }

    /** 조각을 다 모아 캐릭터를 얻었을 때. */
    fun botUnlockedToast(character: BotCharacter): String =
        when (language) {
            UiLanguage.Korean -> "${botCharacterName(character)}을(를) 얻었어요!"
            UiLanguage.English -> "${botCharacterName(character)} unlocked!"
            UiLanguage.Japanese -> "${botCharacterName(character)}を獲得しました！"
            UiLanguage.ChineseSimplified -> "已获得${botCharacterName(character)}！"
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
            // ⚠️ 여기는 한국어가 **두 갈래로** 새던 자리다(백로그 #32). 이름은 도메인 리터럴이었고,
            // 티어명은 도메인의 `PlayLevelSetting.tierLabel`(한국어 전용)을 그대로 썼다 —
            // `fastBeginnerTierLabel`이 이미 4개 언어를 갖고 있는데도 그쪽을 부르지 않았다.
            // 도메인 라벨을 UI에 직접 쓰는 곳은 이제 앱 전체에서 없다.
            is AttendanceReward.BotCharacterUnlock -> {
                val tier = reward.character.tierWithinGroup?.let { fastBeginnerTierLabel(it) }
                val name = botCharacterName(reward.character) + if (tier != null) " ($tier)" else ""
                when (language) {
                    UiLanguage.Korean -> "새 캐릭터 · $name"
                    UiLanguage.English -> "New character · $name"
                    UiLanguage.Japanese -> "新キャラクター · $name"
                    UiLanguage.ChineseSimplified -> "新角色 · $name"
                }
            }
            // 획득이 아니라 진행도라는 것이 드러나야 한다 — "새 캐릭터"로 적으면 지금 쓸 수 있는
            // 줄 알고 픽커에 갔다가 여전히 잠겨 있는 것을 보게 된다.
            is AttendanceReward.BotCharacterShards -> {
                val name = botCharacterName(reward.character)
                when (language) {
                    UiLanguage.Korean -> "$name 조각 ${reward.amount}개"
                    UiLanguage.English -> "$name shards ×${reward.amount}"
                    UiLanguage.Japanese -> "$name のかけら ${reward.amount}個"
                    UiLanguage.ChineseSimplified -> "$name 碎片 ×${reward.amount}"
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
    /**
     * 소모품의 **전체** 이름("형세 보기 1회권"). 마이 페이지처럼 여러 종류를 나란히 놓는 자리는
     * 줄마다 무엇의 표인지가 분명해야 하므로 접미사를 붙인 전체 이름을 쓴다.
     */
    fun consumableRewardName(item: ConsumableItem): String =
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

    /** 소모품 수량 표기("3개"). 마이 페이지의 재고 목록도 같은 표기를 쓴다(#24). */
    fun consumableRewardAmount(amount: Int): String =
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
                "$engineName $levelText"
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

/**
 * 조건 없이 무제한임을 나타내는 기호(#24). 번역 대상이 아니라 기호라 언어별로 같다.
 */
private const val UnlimitedMark: String = "∞"

/**
 * **시간 한정** 무제한임을 나타내는 기호(#24, 광고 1시간 활성화). 무한대와 갈라 놓는 것이
 * 요점이다 — 둘 다 "지금은 안 줄어든다"지만 하나는 영구고 하나는 곧 끝난다.
 */
private const val TimeLimitedMark: String = "⏱"
