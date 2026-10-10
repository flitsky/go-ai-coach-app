package com.worksoc.goaicoach.ui.l10n

/**
 * PAD on-demand 에셋 팩 다운로드 동안 표시되는 사용 가이드 카드 및 상태 문구 (백로그 #245 U-73).
 *
 * `UiStrings` data class의 JVM 인자 수 한도(255개, `StudyHubContractTest`)를 넘지 않기 위해
 * 곁표 + 함수 패턴(`UiStringsGuide.kt` 방식)으로 구현한다.
 */
internal data class DownloadGuideCard(
    val title: String,
    val body: String,
)

internal object UiStringsDownloadGuide {
    private val Card1Title = mapOf(
        UiLanguage.Korean to "착수 방법 ①",
        UiLanguage.English to "How to Move (1)",
        UiLanguage.Japanese to "着手方法 ①",
        UiLanguage.ChineseSimplified to "落子方法 ①",
    )

    private val Card1Body = mapOf(
        UiLanguage.Korean to "바둑판을 길게 누르면 손끝에 놓을 돌이 나타나요. 그대로 끌면서 돌을 옮길 수 있어요.",
        UiLanguage.English to "Long-press the board to reveal a stone under your finger, then drag to position it.",
        UiLanguage.Japanese to "碁盤を長押しすると指先に石が現れます。そのままドラッグして石を動かせます。",
        UiLanguage.ChineseSimplified to "长按棋盘，指尖会出现棋子，拖动即可移动落子位置。",
    )

    private val Card2Title = mapOf(
        UiLanguage.Korean to "착수 방법 ②",
        UiLanguage.English to "How to Move (2)",
        UiLanguage.Japanese to "着手方法 ②",
        UiLanguage.ChineseSimplified to "落子方法 ②",
    )

    private val Card2Body = mapOf(
        UiLanguage.Korean to "「착수 확인」을 켜면 돌을 미리 놓아 본 뒤 「착수」로 확정할 수 있어요.",
        UiLanguage.English to "Turn on 'Confirm Move' to preview your stone placement before locking it in with 'Place'.",
        UiLanguage.Japanese to "「着手確認」をオンにすると、石を仮置きしたあと「着手」で確定できます。",
        UiLanguage.ChineseSimplified to "开启「落子确认」后，可先试放棋子，再点击「落子」确认。",
    )

    private val Card3Title = mapOf(
        UiLanguage.Korean to "바둑판 크기",
        UiLanguage.English to "Board Size",
        UiLanguage.Japanese to "碁盤の大きさ",
        UiLanguage.ChineseSimplified to "棋盘大小",
    )

    private val Card3Body = mapOf(
        UiLanguage.Korean to "판 바로 위나 메뉴의 「바둑판 최대」를 끄면 판 바깥에 여백이 생겨 끝줄에 두기 편해져요.",
        UiLanguage.English to "Turn off 'Board full' above the board or in the menu to add outer margins for easier edge moves.",
        UiLanguage.Japanese to "盤の右上やメニューの「碁盤 最大」をオフにすると余白ができ、端の線にも打ちやすくなります。",
        UiLanguage.ChineseSimplified to "关闭棋盘上方或菜单中的「棋盘 最大」，可在棋盘四周留出边距，便于在边缘落子。",
    )

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

    private val PreparingHumanModel = mapOf(
        UiLanguage.Korean to "사람처럼 두는 AI 모델을 준비하고 있어요",
        UiLanguage.English to "Preparing human-style AI model…",
        UiLanguage.Japanese to "人間らしいAIモデルを準備しています…",
        UiLanguage.ChineseSimplified to "正在准备拟人 AI 模型…",
    )

    private val HumanModelReady = mapOf(
        UiLanguage.Korean to "✓ 사람 모델 다운로드 완료 (앱 재실행 시 적용)",
        UiLanguage.English to "✓ Human-style AI downloaded (applies on app restart)",
        UiLanguage.Japanese to "✓ 人間らしいAIのダウンロード完了（アプリ再起動時に適用）",
        UiLanguage.ChineseSimplified to "✓ 拟人 AI 下载完成（重启应用后生效）",
    )

    private val PrevGuide = mapOf(
        UiLanguage.Korean to "이전 가이드",
        UiLanguage.English to "Previous guide",
        UiLanguage.Japanese to "前のガイド",
        UiLanguage.ChineseSimplified to "上一页",
    )

    private val NextGuide = mapOf(
        UiLanguage.Korean to "다음 가이드",
        UiLanguage.English to "Next guide",
        UiLanguage.Japanese to "次のガイド",
        UiLanguage.ChineseSimplified to "下一页",
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

    fun cards(language: UiLanguage): List<DownloadGuideCard> = listOf(
        DownloadGuideCard(
            title = Card1Title[language] ?: Card1Title.getValue(UiLanguage.Korean),
            body = Card1Body[language] ?: Card1Body.getValue(UiLanguage.Korean),
        ),
        DownloadGuideCard(
            title = Card2Title[language] ?: Card2Title.getValue(UiLanguage.Korean),
            body = Card2Body[language] ?: Card2Body.getValue(UiLanguage.Korean),
        ),
        DownloadGuideCard(
            title = Card3Title[language] ?: Card3Title.getValue(UiLanguage.Korean),
            body = Card3Body[language] ?: Card3Body.getValue(UiLanguage.Korean),
        ),
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

    fun preparingHumanModel(language: UiLanguage): String =
        PreparingHumanModel[language] ?: PreparingHumanModel.getValue(UiLanguage.Korean)

    fun humanModelReady(language: UiLanguage): String =
        HumanModelReady[language] ?: HumanModelReady.getValue(UiLanguage.Korean)

    fun previousGuide(language: UiLanguage): String =
        PrevGuide[language] ?: PrevGuide.getValue(UiLanguage.Korean)

    fun nextGuide(language: UiLanguage): String =
        NextGuide[language] ?: NextGuide.getValue(UiLanguage.Korean)

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
}
