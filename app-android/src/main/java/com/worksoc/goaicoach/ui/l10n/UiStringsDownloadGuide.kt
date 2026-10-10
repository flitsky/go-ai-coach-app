package com.worksoc.goaicoach.ui.l10n

/**
 * PAD on-demand 에셋 팩 다운로드 동안 표시되는 사용 가이드 카드 3장 및 상태 문구 (백로그 #245 U-73).
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
        UiLanguage.English to "Turn on 'Confirm Move' to preview your stone placement before locking it in with 'Play'.",
        UiLanguage.Japanese to "「着手確認」をオンにすると、石を仮置きしたあと「着手」で確定できます。",
        UiLanguage.ChineseSimplified to "开启「确认落子」后，可先试放棋子，再点击「落子」确认。",
    )

    private val Card3Title = mapOf(
        UiLanguage.Korean to "바둑판 크기",
        UiLanguage.English to "Board Size",
        UiLanguage.Japanese to "碁盤の大きさ",
        UiLanguage.ChineseSimplified to "棋盘大小",
    )

    private val Card3Body = mapOf(
        UiLanguage.Korean to "판 바로 위나 메뉴의 「바둑판 최대」를 끄면 판 바깥에 여백이 생겨 끝줄에 두기 편해져요.",
        UiLanguage.English to "Turn off 'Max Board' above the board or in the menu to add outer margins for easier edge moves.",
        UiLanguage.Japanese to "盤の右上やメニューの「盤面最大」をオフにすると余白ができ、端の線にも打ちやすくなります。",
        UiLanguage.ChineseSimplified to "关闭棋盘上方或菜单中的「棋盘最大」，可在棋盘四周留出边距，便于在边缘落子。",
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

    fun cardFor(language: UiLanguage, index: Int): DownloadGuideCard =
        when (index) {
            0 -> DownloadGuideCard(
                title = Card1Title[language] ?: Card1Title.getValue(UiLanguage.Korean),
                body = Card1Body[language] ?: Card1Body.getValue(UiLanguage.Korean),
            )
            1 -> DownloadGuideCard(
                title = Card2Title[language] ?: Card2Title.getValue(UiLanguage.Korean),
                body = Card2Body[language] ?: Card2Body.getValue(UiLanguage.Korean),
            )
            else -> DownloadGuideCard(
                title = Card3Title[language] ?: Card3Title.getValue(UiLanguage.Korean),
                body = Card3Body[language] ?: Card3Body.getValue(UiLanguage.Korean),
            )
        }

    fun waitingForWifi(language: UiLanguage): String =
        WaitingForWifi[language] ?: WaitingForWifi.getValue(UiLanguage.Korean)

    fun failed(language: UiLanguage): String =
        Failed[language] ?: Failed.getValue(UiLanguage.Korean)

    fun retry(language: UiLanguage): String =
        Retry[language] ?: Retry.getValue(UiLanguage.Korean)
}
