package com.worksoc.goaicoach.ui.l10n

/**
 * PAD on-demand 에셋 팩 받기의 상태 문구와 엔진 준비 화면 문구 (백로그 #245 U-73, #249).
 * 조작법 시연의 말은 `UiStringsBoardControlsGuide`에 있다(#249 — 예전의 글 카드 셋을 대신한다).
 *
 * `UiStrings` data class의 JVM 인자 수 한도(255개, `StudyHubContractTest`)를 넘지 않기 위해
 * 곁표 + 함수 패턴(`UiStringsGuide.kt` 방식)으로 구현한다.
 */
internal object UiStringsDownloadGuide {
    private val DownloadingNotice = mapOf(
        UiLanguage.Korean to "바둑 AI 엔진 다운로드 중 (모바일 데이터 환경에서는 Wi-Fi 연결을 권장합니다)",
        UiLanguage.English to "Downloading Go AI engine (Wi-Fi recommended over mobile data)",
        UiLanguage.Japanese to "囲碁AIエンジンをダウンロード中（Wi-Fi接続を推奨します）",
        UiLanguage.ChineseSimplified to "正在下载围棋 AI 引擎（推荐使用 Wi-Fi 连接）",
    )

    private val WaitingForWifi = mapOf(
        UiLanguage.Korean to "Wi-Fi 네트워크 연결을 기다리고 있어요.",
        UiLanguage.English to "Waiting for Wi-Fi connection.",
        UiLanguage.Japanese to "Wi-Fi接続を待機しています。",
        UiLanguage.ChineseSimplified to "正在等待 Wi-Fi 连接。",
    )

    private val Failed = mapOf(
        UiLanguage.Korean to "다운로드가 일시 중지되었습니다.",
        UiLanguage.English to "Download paused.",
        UiLanguage.Japanese to "ダウンロードが一時停止しました。",
        UiLanguage.ChineseSimplified to "下载已暂停。",
    )

    private val Retry = mapOf(
        UiLanguage.Korean to "다시 시도",
        UiLanguage.English to "Retry",
        UiLanguage.Japanese to "再試行",
        UiLanguage.ChineseSimplified to "重试",
    )

    private val AiReady = mapOf(
        UiLanguage.Korean to "✓ AI 준비 완료",
        UiLanguage.English to "✓ AI is ready",
        UiLanguage.Japanese to "✓ AIの準備が完了しました",
        UiLanguage.ChineseSimplified to "✓ AI 准备就绪",
    )

    private val Close = mapOf(
        UiLanguage.Korean to "닫기",
        UiLanguage.English to "Close",
        UiLanguage.Japanese to "閉じる",
        UiLanguage.ChineseSimplified to "关闭",
    )

    private val BoardControlsGuide = mapOf(
        UiLanguage.Korean to "바둑판 조작법",
        UiLanguage.English to "Board Controls Guide",
        UiLanguage.Japanese to "碁盤の操作ガイド",
        UiLanguage.ChineseSimplified to "棋盘操作指南",
    )

    private val ContinueOnMobileData = mapOf(
        UiLanguage.Korean to "모바일 데이터로 계속",
        UiLanguage.English to "Continue on Mobile Data",
        UiLanguage.Japanese to "モバイルデータで続行",
        UiLanguage.ChineseSimplified to "使用移动网络继续",
    )

    private val ConfirmationRequired = mapOf(
        UiLanguage.Korean to "모바일 데이터 다운로드 승인이 필요합니다.",
        UiLanguage.English to "Mobile data confirmation required.",
        UiLanguage.Japanese to "モバイル通信の承認が必要です。",
        UiLanguage.ChineseSimplified to "需要移动网络授权。",
    )

    private val OpenConfirmation = mapOf(
        UiLanguage.Korean to "승인 창 열기",
        UiLanguage.English to "Open Confirmation",
        UiLanguage.Japanese to "確認画面を開く",
        UiLanguage.ChineseSimplified to "打开确认窗口",
    )

    private val EngineUnavailableStoreFailure = mapOf(
        UiLanguage.Korean to "AI가 제대로 동작하지 않습니다 — 대국도 분석 결과도 믿을 수 없습니다. Google Play 스토어에서 공식 버전을 설치해 주세요. 사람끼리 두는 대국은 그대로 쓸 수 있어요.",
        UiLanguage.English to "The AI is not working properly - neither its moves nor its analysis can be trusted. Please install the official version from the Google Play Store. You can still play two-player games with another person.",
        UiLanguage.Japanese to "AIが正しく動作していません — 対局も分析結果も信頼できません。Google Play ストアから公式版をインストールしてください。人同士の対局はそのまま使えます。",
        UiLanguage.ChineseSimplified to "AI 无法正常工作 — 对弈和分析结果均不可信。请从 Google Play 商店安装官方正版。双人对弈仍可照常使用。",
    )

    // ── 엔진 준비 화면(백로그 #249) ──
    private val SetupSubtitle = mapOf(
        UiLanguage.Korean to "바둑 AI를 받는 동안 바둑판 조작법을 알아 두세요.",
        UiLanguage.English to "Learn the board controls while the Go AI downloads.",
        UiLanguage.Japanese to "囲碁AIをダウンロードする間に、碁盤の操作を覚えましょう。",
        UiLanguage.ChineseSimplified to "下载围棋 AI 期间，先熟悉一下棋盘操作吧。",
    )

    private val Later = mapOf(
        UiLanguage.Korean to "나중에",
        UiLanguage.English to "Later",
        UiLanguage.Japanese to "あとで",
        UiLanguage.ChineseSimplified to "稍后",
    )

    private val Start = mapOf(
        UiLanguage.Korean to "시작하기",
        UiLanguage.English to "Start",
        UiLanguage.Japanese to "はじめる",
        UiLanguage.ChineseSimplified to "开始",
    )

    private val Preparing = mapOf(
        UiLanguage.Korean to "바둑 AI 엔진을 준비하고 있어요",
        UiLanguage.English to "Preparing the Go AI engine",
        UiLanguage.Japanese to "囲碁AIエンジンを準備しています",
        UiLanguage.ChineseSimplified to "正在准备围棋 AI 引擎",
    )

    fun downloadingNotice(language: UiLanguage): String =
        DownloadingNotice[language] ?: DownloadingNotice.getValue(UiLanguage.Korean)

    fun waitingForWifi(language: UiLanguage): String =
        WaitingForWifi[language] ?: WaitingForWifi.getValue(UiLanguage.Korean)

    fun continueOnMobileData(language: UiLanguage): String =
        ContinueOnMobileData[language] ?: ContinueOnMobileData.getValue(UiLanguage.Korean)

    fun failed(language: UiLanguage): String =
        Failed[language] ?: Failed.getValue(UiLanguage.Korean)

    fun retry(language: UiLanguage): String =
        Retry[language] ?: Retry.getValue(UiLanguage.Korean)

    fun aiReady(language: UiLanguage): String =
        AiReady[language] ?: AiReady.getValue(UiLanguage.Korean)

    fun close(language: UiLanguage): String =
        Close[language] ?: Close.getValue(UiLanguage.Korean)

    fun requiresConfirmation(language: UiLanguage): String =
        ConfirmationRequired[language] ?: ConfirmationRequired.getValue(UiLanguage.Korean)

    fun openConfirmation(language: UiLanguage): String =
        OpenConfirmation[language] ?: OpenConfirmation.getValue(UiLanguage.Korean)

    fun engineUnavailableOfficialPlatformFailureMessage(language: UiLanguage): String =
        EngineUnavailableStoreFailure[language] ?: EngineUnavailableStoreFailure.getValue(UiLanguage.Korean)

    fun boardControlsGuide(language: UiLanguage): String =
        BoardControlsGuide[language] ?: BoardControlsGuide.getValue(UiLanguage.Korean)

    fun setupSubtitle(language: UiLanguage): String =
        SetupSubtitle[language] ?: SetupSubtitle.getValue(UiLanguage.Korean)

    fun later(language: UiLanguage): String =
        Later[language] ?: Later.getValue(UiLanguage.Korean)

    fun start(language: UiLanguage): String =
        Start[language] ?: Start.getValue(UiLanguage.Korean)

    fun preparing(language: UiLanguage): String =
        Preparing[language] ?: Preparing.getValue(UiLanguage.Korean)
}
