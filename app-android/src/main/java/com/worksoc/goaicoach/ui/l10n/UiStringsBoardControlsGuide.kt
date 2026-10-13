package com.worksoc.goaicoach.ui.l10n

/**
 * 바둑판 조작법 가이드(백로그 #249 ③) — 문하생 판다(첫돌이)가 판 위에서 시연하며 하는 말과 단계·모드 이름.
 *
 * ⚠️ 대국 화면의 버튼·칩 이름(「착수 확인」·「착수」·「바둑판 최대」)은 **여기 다시 적지 않고 끼워 넣는다**(`%s`) —
 *   대국 화면에서 이름이 바뀌면 시연도 함께 바뀌어야 한다. 끼우는 쪽이 [UiStrings.confirmPlay]·[UiStrings.playMove]·
 *   [boardSizeToggleLabelFor]를 준다. 한국어 조사는 지금 이름에 맞춰 두었다 — 이름을 바꾸면 여기 조사도 본다.
 * ⚠️ `UiStrings` 필드 여유가 없어(리팩토링 보류 `#48`) 위성 Map으로 둔다(`UiStringsDownloadGuide`와 같은 방식).
 */
internal object UiStringsBoardControlsGuide {
    private val StepDrag = mapOf(
        UiLanguage.Korean to "돌 끌기",
        UiLanguage.English to "Drag a stone",
        UiLanguage.Japanese to "石をドラッグ",
        UiLanguage.ChineseSimplified to "拖动棋子",
    )

    /**
     * 무대 아래 한 줄 — 지금이 시연인지 직접인지를 알린다(#258, 사용자 2026-10-13: 「시연 보기 / 직접 해 보기」 알약 대신 한 줄).
     * ⚠️ 초는 적지 않는다(「잠시」) — 숫자는 `GuideIdleReturnMillis` 한 곳에만 있다. 스레드가 정한 문구다(영·일·중은 원어민 확인 없음).
     */
    private val DemoHint = mapOf(
        UiLanguage.Korean to "판을 누르면 바로 직접 해 볼 수 있어요",
        UiLanguage.English to "Touch the board to try it yourself",
        UiLanguage.Japanese to "盤に触れるとすぐ自分で試せます",
        UiLanguage.ChineseSimplified to "触摸棋盘即可亲自尝试",
    )

    private val PracticeHint = mapOf(
        UiLanguage.Korean to "손을 떼고 잠시 두면 다시 보여 드려요",
        UiLanguage.English to "Leave it for a moment and the demo comes back",
        UiLanguage.Japanese to "しばらく触れないとお手本に戻ります",
        UiLanguage.ChineseSimplified to "停一会儿会重新演示",
    )

    private val Drag1 = mapOf(
        UiLanguage.Korean to "바둑판을 길게 누르면 손끝 위에 돌이 떠올라요.",
        UiLanguage.English to "Press and hold the board — a stone appears just above your fingertip.",
        UiLanguage.Japanese to "碁盤を長押しすると、指先の少し上に石が現れます。",
        UiLanguage.ChineseSimplified to "长按棋盘，指尖上方会出现一颗棋子。",
    )

    private val Drag2 = mapOf(
        UiLanguage.Korean to "누른 채로 끌어서 둘 자리를 골라요.",
        UiLanguage.English to "Keep holding and drag to pick the spot.",
        UiLanguage.Japanese to "押したままドラッグして、打つ場所を選びます。",
        UiLanguage.ChineseSimplified to "按住不放拖动，选择落子位置。",
    )

    private val Drag3 = mapOf(
        UiLanguage.Korean to "손을 떼면 그 자리에 놓여요. 짧게 탭해도 바로 놓여요.",
        UiLanguage.English to "Lift your finger to place it. A quick tap places a stone too.",
        UiLanguage.Japanese to "指を離すとその場所に打てます。軽くタップしてもすぐ打てます。",
        UiLanguage.ChineseSimplified to "松开手指即落在该处。轻点一下也能直接落子。",
    )

    /** `%s` = 착수 확인 이름. */
    private val Confirm1 = mapOf(
        UiLanguage.Korean to "판 아래 돌 버튼(「%s」)을 누르면 켜져요.",
        UiLanguage.English to "Tap the stone button below the board (“%s”) to turn it on.",
        UiLanguage.Japanese to "盤の下の石ボタン（「%s」）を押すとオンになります。",
        UiLanguage.ChineseSimplified to "点一下棋盘下方的棋子按钮（“%s”）即可开启。",
    )

    private val Confirm2 = mapOf(
        UiLanguage.Korean to "켜져 있으면 판을 눌러 돌을 먼저 놓아 볼 수 있어요.",
        UiLanguage.English to "While it's on, tapping the board only previews the stone.",
        UiLanguage.Japanese to "オンのときは、盤を押すと石を仮に置いてみられます。",
        UiLanguage.ChineseSimplified to "开启后，点棋盘只会先预放一颗棋子。",
    )

    private val Confirm3 = mapOf(
        UiLanguage.Korean to "다른 자리를 누르면 놓아 볼 돌이 그리로 옮겨 가요.",
        UiLanguage.English to "Tap another spot and the preview moves there.",
        UiLanguage.Japanese to "別の場所を押すと、仮の石がそちらへ移ります。",
        UiLanguage.ChineseSimplified to "点其他位置，预放的棋子就会移过去。",
    )

    /** `%s` = 착수(확정) 이름. */
    private val Confirm4 = mapOf(
        UiLanguage.Korean to "마음에 들면 「%s」 버튼을 눌러 확정해요.",
        UiLanguage.English to "Happy with it? Press “%s” to place it.",
        UiLanguage.Japanese to "よければ「%s」ボタンで確定します。",
        UiLanguage.ChineseSimplified to "满意的话，按“%s”按钮确认落子。",
    )

    /** `%s` = 바둑판 최대 이름. */
    private val Size1 = mapOf(
        UiLanguage.Korean to "판 위 「%s」를 끄면 판 둘레에 여백이 생겨요.",
        UiLanguage.English to "Turn off “%s” above the board to add a margin around it.",
        UiLanguage.Japanese to "盤の上の「%s」をオフにすると、盤の周りに余白ができます。",
        UiLanguage.ChineseSimplified to "关闭棋盘上方的“%s”，棋盘四周会留出空白。",
    )

    private val Size2 = mapOf(
        UiLanguage.Korean to "끝줄에 둘 때 손가락이 판 밖으로 벗어나지 않아 편해요.",
        UiLanguage.English to "Handy for edge moves — your finger stays on the board.",
        UiLanguage.Japanese to "端の線に打つとき、指が盤からはみ出さず打ちやすくなります。",
        UiLanguage.ChineseSimplified to "在边线落子时，手指不会滑出棋盘，更方便。",
    )

    private val TryDrag = mapOf(
        UiLanguage.Korean to "직접 길게 눌러 끌어 보세요. 손을 떼면 놓여요.",
        UiLanguage.English to "Your turn: press, hold and drag, then lift to place.",
        UiLanguage.Japanese to "やってみましょう。長押ししてドラッグし、指を離すと打てます。",
        UiLanguage.ChineseSimplified to "试试看：长按并拖动，松手即落子。",
    )

    /** `%1$s` = 착수 확인 이름, `%2$s` = 착수(확정) 이름. */
    private val TryConfirm = mapOf(
        UiLanguage.Korean to "돌 버튼(「%1\$s」)을 켜고 판을 눌러 놓아 본 뒤, 「%2\$s」 버튼으로 확정해 보세요.",
        UiLanguage.English to "Turn on the stone button (“%1\$s”), tap to preview, then press “%2\$s”.",
        UiLanguage.Japanese to "石ボタン（「%1\$s」）をオンにして盤を押し、「%2\$s」ボタンで確定してみましょう。",
        UiLanguage.ChineseSimplified to "打开棋子按钮（“%1\$s”），点棋盘预放，再按“%2\$s”确认。",
    )

    /** `%s` = 바둑판 최대 이름. */
    private val TrySize = mapOf(
        UiLanguage.Korean to "「%s」를 눌러 켜고 꺼 보세요.",
        UiLanguage.English to "Tap “%s” to switch it on and off.",
        UiLanguage.Japanese to "「%s」を押して、オン・オフしてみましょう。",
        UiLanguage.ChineseSimplified to "点“%s”试着开关一下。",
    )

    private val WellDone = mapOf(
        UiLanguage.Korean to "잘했어요! 계속 둬 봐도 돼요.",
        UiLanguage.English to "Nice! Keep going if you like.",
        UiLanguage.Japanese to "上手です！続けて打ってみてもいいですよ。",
        UiLanguage.ChineseSimplified to "做得好！可以继续试试。",
    )

    private fun Map<UiLanguage, String>.of(language: UiLanguage): String = this[language] ?: getValue(UiLanguage.Korean)

    fun stepDrag(language: UiLanguage): String = StepDrag.of(language)
    fun demoHint(language: UiLanguage): String = DemoHint.of(language)
    fun practiceHint(language: UiLanguage): String = PracticeHint.of(language)

    /** 끌기 시연의 세 마디(누르기 → 끌기 → 떼기). */
    fun dragLines(language: UiLanguage): List<String> = listOf(Drag1.of(language), Drag2.of(language), Drag3.of(language))

    /** 착수 확인 시연의 네 마디(켜기 → 놓아 보기 → 옮기기 → 확정). */
    fun confirmLines(language: UiLanguage, confirmPlayLabel: String, playMoveLabel: String): List<String> = listOf(
        Confirm1.of(language).format(confirmPlayLabel),
        Confirm2.of(language),
        Confirm3.of(language),
        Confirm4.of(language).format(playMoveLabel),
    )

    /** 판 크기 시연의 두 마디. */
    fun sizeLines(language: UiLanguage, boardMaxLabel: String): List<String> = listOf(Size1.of(language).format(boardMaxLabel), Size2.of(language))

    fun tryDrag(language: UiLanguage): String = TryDrag.of(language)
    fun tryConfirm(language: UiLanguage, confirmPlayLabel: String, playMoveLabel: String): String =
        TryConfirm.of(language).format(confirmPlayLabel, playMoveLabel)
    fun trySize(language: UiLanguage, boardMaxLabel: String): String = TrySize.of(language).format(boardMaxLabel)
    fun wellDone(language: UiLanguage): String = WellDone.of(language)
}
