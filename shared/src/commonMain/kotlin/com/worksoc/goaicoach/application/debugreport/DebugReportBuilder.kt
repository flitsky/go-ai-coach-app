package com.worksoc.goaicoach.application.debugreport

import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.analysis.toDisplayText
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot

data class DebugReportSnapshot(
    val mode: MatchMode,
    val playerSetup: PlayerSetup,
    val engineName: String,
    val engineDiagnostic: String,
    /**
     * 착수 진동 진단(#36). 플랫폼 API를 타므로 `shared`가 만들 수 없어 **app-android가 채워
     * 넣는다.** 기본값이 있는 이유는 이 필드를 모르는 호출부를 깨지 않기 위해서인데,
     * ⚠️ 그래서 **전달을 빠뜨리면 조용히 "not recorded"가 된다** — 실기에서 이 절이
     * "not recorded"로 보이면 기능이 아니라 배선을 먼저 의심할 것.
     */
    val hapticDiagnostic: String = "not recorded",
    /**
     * 빌드·기기 스탬프(#92). ⚠️ **`shared`는 이 값을 만들 수 없다** — `BuildConfig`도
     * `android.os.Build`도 플랫폼 API라, `hapticDiagnostic`과 똑같이 **app-android가 채워 넣는다.**
     * 기본값이 있는 이유도 같다(이 필드를 모르는 호출부를 안 깨려고). ⚠️ 그래서 **전달을 빠뜨리면
     * 조용히 "not recorded"가 된다** — 리포트에서 그렇게 보이면 배선을 먼저 의심할 것.
     * `DebugReportBuildStampTest`가 컨트롤러부터 리포트 본문까지 한 번에 묶어 둔다.
     */
    val buildStamp: String = "not recorded",
    val engineProfile: EngineProfile,
    val playLevel: PlayLevelSetting,
    val analysisPreset: AnalysisPreset,
    val analysisCacheStats: String,
    val positionAnalysisCacheStats: String = "disabled",
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val isGameEnded: Boolean,
    val topMovesEnabled: Boolean,
    val topMoveCandidateCount: Int,
    val moveAnalysisCoverage: String,
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val scoreText: String,
    val scoreSnapshots: List<ScoreSnapshot>,
    val moveReviewText: String,
    val lastMoveText: String,
    val endgameLog: String,
    val engineBenchmarkText: String,
    val turnTimeText: String = "Time B 0.0s / W 0.0s",
    val turnTimeDebugText: String = "blackMillis=0, whiteMillis=0, currentTurn=Black, currentElapsedMillis=0",
    val runtimeEventLogText: String = "Runtime event log not loaded.",
    val diagnosticEventLogText: String = "Diagnostic event log not loaded.",
    val searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    val savedSessionJson: String? = null,
    val createdAtMillis: Long = currentEpochMillis(),
)

fun GameSessionControllerState.toDebugReportSnapshot(
    engineName: String,
    engineDiagnostic: String,
    analysisCacheStats: String,
    positionAnalysisCacheStats: String = "disabled",
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    turnTimeText: String = "Time B 0.0s / W 0.0s",
    turnTimeDebugText: String = "blackMillis=0, whiteMillis=0, currentTurn=Black, currentElapsedMillis=0",
    runtimeEventLogText: String = "Runtime event log not loaded.",
    diagnosticEventLogText: String = "Diagnostic event log not loaded.",
    savedSessionJson: String? = null,
): DebugReportSnapshot =
    DebugReportSnapshot(
        mode = matchMode,
        playerSetup = playerSetup,
        engineName = engineName,
        engineDiagnostic = engineDiagnostic,
        engineProfile = core.runtimeState.engineProfile,
        playLevel = core.runtimeState.playLevel,
        analysisPreset = core.runtimeState.analysisPreset,
        analysisCacheStats = analysisCacheStats,
        positionAnalysisCacheStats = positionAnalysisCacheStats,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        isGameEnded = isGameEnded,
        topMovesEnabled = settings.topMovesEnabled,
        topMoveCandidateCount = core.analysisState.reviewAnalysis.legalPlayCount,
        moveAnalysisCoverage = core.analysisState.reviewAnalysis.coverageSummary(),
        gameState = gameState,
        engineMessage = engineMessage,
        candidateText = core.analysisState.candidateText,
        scoreText = core.scoreState.scoreText,
        scoreSnapshots = core.scoreState.scoreSnapshots,
        moveReviewText = core.moveReviewState.moveReviewText,
        lastMoveText = core.moveReviewState.lastMoveText,
        endgameLog = core.scoreState.endgameLog,
        engineBenchmarkText = benchmark.benchmarkText,
        turnTimeText = turnTimeText,
        turnTimeDebugText = turnTimeDebugText,
        runtimeEventLogText = runtimeEventLogText,
        diagnosticEventLogText = diagnosticEventLogText,
        searchTimeSettings = settings.searchTimeSettings,
        savedSessionJson = savedSessionJson,
    )

fun buildDebugReport(snapshot: DebugReportSnapshot): String =
    buildDebugReport(
        mode = snapshot.mode,
        playerSetup = snapshot.playerSetup,
        engineName = snapshot.engineName,
        engineDiagnostic = snapshot.engineDiagnostic,
        hapticDiagnostic = snapshot.hapticDiagnostic,
        buildStamp = snapshot.buildStamp,
        engineProfile = snapshot.engineProfile,
        playLevel = snapshot.playLevel,
        analysisPreset = snapshot.analysisPreset,
        analysisCacheStats = snapshot.analysisCacheStats,
        positionAnalysisCacheStats = snapshot.positionAnalysisCacheStats,
        isEngineReady = snapshot.isEngineReady,
        isEngineBusy = snapshot.isEngineBusy,
        isGameEnded = snapshot.isGameEnded,
        topMovesEnabled = snapshot.topMovesEnabled,
        topMoveCandidateCount = snapshot.topMoveCandidateCount,
        moveAnalysisCoverage = snapshot.moveAnalysisCoverage,
        gameState = snapshot.gameState,
        engineMessage = snapshot.engineMessage,
        candidateText = snapshot.candidateText,
        scoreText = snapshot.scoreText,
        scoreSnapshots = snapshot.scoreSnapshots,
        moveReviewText = snapshot.moveReviewText,
        lastMoveText = snapshot.lastMoveText,
        endgameLog = snapshot.endgameLog,
        engineBenchmarkText = snapshot.engineBenchmarkText,
        turnTimeText = snapshot.turnTimeText,
        turnTimeDebugText = snapshot.turnTimeDebugText,
        runtimeEventLogText = snapshot.runtimeEventLogText,
        diagnosticEventLogText = snapshot.diagnosticEventLogText,
        searchTimeSettings = snapshot.searchTimeSettings,
        savedSessionJson = snapshot.savedSessionJson,
        createdAtMillis = snapshot.createdAtMillis,
    )

internal fun String.truncateToRecent(maxChars: Int): String {
    if (length <= maxChars) return this
    val marker = "... [trimmed to recent $maxChars characters for clipboard compatibility] ...\n"
    return marker + substring(length - maxChars)
}

data class DebugReportCopyPlan(
    val clipboardLabel: String,
    val clipboardReport: String,
    val fileReport: String,
    val engineMessage: String,
    val toastMessage: String,
    val failureToastMessage: String = "Debug report saved to file, but failed to copy to clipboard",
)

fun buildDebugReportCopyPlan(snapshot: DebugReportSnapshot): DebugReportCopyPlan {
    val fileReport = buildDebugReport(snapshot)
    val clipboardSnapshot = snapshot.copy(
        runtimeEventLogText = snapshot.runtimeEventLogText.truncateToRecent(50000),
        diagnosticEventLogText = snapshot.diagnosticEventLogText.truncateToRecent(50000),
    )
    val clipboardReport = buildDebugReport(clipboardSnapshot)

    return DebugReportCopyPlan(
        clipboardLabel = "바둑 AI debug report",
        clipboardReport = clipboardReport,
        fileReport = fileReport,
        engineMessage = "Debug report copied to clipboard. Paste it into chat for review.",
        toastMessage = "Debug report copied",
    )
}

data class DebugReportCopyResult(
    val engineMessage: String,
)

data class DebugReportCopyActionRequest(
    val controllerState: GameSessionControllerState,
    val engineName: String,
    val engineDiagnostic: String,
    /** 착수 진동 진단(#36). app-android가 채운다. */
    val hapticDiagnostic: String = "not recorded",
    /** 빌드·기기 스탬프(#92). app-android가 채운다. */
    val buildStamp: String = "not recorded",
    val analysisCacheStats: String,
    val positionAnalysisCacheStats: String,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val turnTimeText: String,
    val turnTimeDebugText: String,
    val runtimeEventLogText: String,
    val diagnosticEventLogText: String,
    val savedSessionJson: String?,
)

fun runDebugReportCopyAction(
    request: DebugReportCopyActionRequest,
    clipboard: ClipboardPort,
    mirror: DebugReportMirrorPort,
    userNotice: UserNoticePort,
): DebugReportCopyResult {
    val plan = buildDebugReportCopyPlan(
        request.controllerState.toDebugReportSnapshot(
            engineName = request.engineName,
            engineDiagnostic = request.engineDiagnostic,
            analysisCacheStats = request.analysisCacheStats,
            positionAnalysisCacheStats = request.positionAnalysisCacheStats,
            isEngineReady = request.isEngineReady,
            isEngineBusy = request.isEngineBusy,
            turnTimeText = request.turnTimeText,
            turnTimeDebugText = request.turnTimeDebugText,
            runtimeEventLogText = request.runtimeEventLogText,
            diagnosticEventLogText = request.diagnosticEventLogText,
            savedSessionJson = request.savedSessionJson,
        ).copy(hapticDiagnostic = request.hapticDiagnostic, buildStamp = request.buildStamp),
    )
    return runDebugReportCopyEffect(
        effect = GameSessionEffect.CopyDebugReport(plan),
        clipboard = clipboard,
        mirror = mirror,
        userNotice = userNotice,
    )
}

fun runDebugReportCopyEffect(
    effect: GameSessionEffect.CopyDebugReport,
    clipboard: ClipboardPort,
    mirror: DebugReportMirrorPort,
    userNotice: UserNoticePort,
): DebugReportCopyResult {
    val plan = effect.plan
    val copySuccess = clipboard.setText(plan.clipboardLabel, plan.clipboardReport)
    runCatching { mirror.save(plan.fileReport) }
    if (copySuccess) {
        userNotice.showShort(plan.toastMessage)
    } else {
        userNotice.showShort(plan.failureToastMessage)
    }
    return DebugReportCopyResult(engineMessage = plan.engineMessage)
}

fun buildDebugReport(
    mode: MatchMode,
    playerSetup: PlayerSetup,
    engineName: String,
    engineDiagnostic: String,
    hapticDiagnostic: String = "not recorded",
    buildStamp: String = "not recorded",
    engineProfile: EngineProfile,
    playLevel: PlayLevelSetting,
    analysisPreset: AnalysisPreset,
    analysisCacheStats: String,
    positionAnalysisCacheStats: String = "disabled",
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    topMovesEnabled: Boolean,
    topMoveCandidateCount: Int,
    moveAnalysisCoverage: String,
    gameState: GameState,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
    scoreSnapshots: List<ScoreSnapshot>,
    moveReviewText: String,
    lastMoveText: String,
    endgameLog: String,
    engineBenchmarkText: String,
    turnTimeText: String = "Time B 0.0s / W 0.0s",
    turnTimeDebugText: String = "blackMillis=0, whiteMillis=0, currentTurn=Black, currentElapsedMillis=0",
    runtimeEventLogText: String = "Runtime event log not loaded.",
    diagnosticEventLogText: String = "Diagnostic event log not loaded.",
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    savedSessionJson: String? = null,
    createdAtMillis: Long = currentEpochMillis(),
): String {
    val localScoreText = BoardScorer.score(gameState).toDisplayText()

    return buildString {
        appendDebugReportHeader(createdAtMillis, buildStamp)
        appendRuntimeSection(
            mode = mode,
            playerSetup = playerSetup,
            engineName = engineName,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            isGameEnded = isGameEnded,
            engineProfile = engineProfile,
            playLevel = playLevel,
            searchTimeSettings = searchTimeSettings,
            analysisPreset = analysisPreset,
            analysisCacheStats = analysisCacheStats,
            positionAnalysisCacheStats = positionAnalysisCacheStats,
            topMovesEnabled = topMovesEnabled,
            topMoveCandidateCount = topMoveCandidateCount,
            moveAnalysisCoverage = moveAnalysisCoverage,
            turnTimeText = turnTimeText,
            turnTimeDebugText = turnTimeDebugText,
        )
        appendGameStateSection(gameState)
        appendBoardSections(gameState)
        appendNamedTextSection("EndgameLog", endgameLog)
        appendNamedTextSection("LocalRulesetScoreNow", localScoreText)
        appendScoreTimelineSection(scoreSnapshots)
        appendDisplayedTextsSection(
            lastMoveText = lastMoveText,
            engineMessage = engineMessage,
            scoreText = scoreText,
            moveReviewText = moveReviewText,
            candidateText = candidateText,
        )
        appendNamedTextSection("SavedSessionJson", savedSessionJson ?: "none")
        appendNamedTextSection("EngineDiagnostic", engineDiagnostic)
        appendNamedTextSection("Haptics", hapticDiagnostic)
        appendNamedTextSection("EngineBenchmark", engineBenchmarkText)
        appendNamedTextSection("RuntimeEventLog", runtimeEventLogText)
        appendNamedTextSection("DiagnosticEventLog", diagnosticEventLogText, trailingBlankLine = false)
    }.trim()
}
