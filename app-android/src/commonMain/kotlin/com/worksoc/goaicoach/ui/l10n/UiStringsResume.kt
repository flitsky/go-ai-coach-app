package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.rankmeasure.rankMeasureMatchup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.playstyle.humanPlayStyle

/**
 * 「이전 대국 이어하기」 알약의 둘째 줄(백로그 #287, 사용자 2026-10-24) — **어떤 판을 이어 두는지**를 한 줄로.
 * 대국(승급 대국이면) · 상대 · 내 돌 · 판 크기와 수: 「승급 대국 · 8급 AI · 흑 · 19줄 47수」 · 「수제자 반상(1급) · 백 · 13줄 47수」 ·
 * 「판다(15급) · 흑 3점 · 9줄 21수」 · 「둘이서 · 19줄 112수」.
 *
 * - 「흑」 · 「백」은 **내 돌 색**이다(다음 차례가 아니다 — 차례는 판을 열면 보인다). 사람이 정확히 한 명일 때만 적는다.
 * - 접바둑(2점 이상)은 늘 흑이 놓으므로 「흑 N점」 — 내가 흑이면 내 돌 자리에 합치고, 아니면 따로 적는다.
 * - 저장 시각은 적지 않는다(사용자 2026-10-24 — 360dp에서 두 줄로 넘친다).
 */
internal fun resumeSummaryFor(strings: UiStrings, snapshot: SavedGameSnapshot, engineName: String): String {
    val language = strings.language
    val setup = snapshot.playerSetup
    val state = snapshot.gameState
    val humanColors = StoneColor.entries.filter { setup.sideFor(it).controller == SeatController.Human }
    val myColor = humanColors.singleOrNull()
    val handicap = state.handicapCount.takeIf { it >= 2 }?.let { resumeHandicapLabelFor(language, strings.colorLabel(StoneColor.Black), it) }
    val matchup = setup.rankMeasureMatchup()
    val parts = buildList {
        when {
            matchup != null -> {
                add(rankMeasureTitleFor(language))
                add(rankedOpponentLabelFor(language, matchup.opponentRank))
            }
            humanColors.size == 2 -> add(resumeTwoPlayersFor(language))
            humanColors.isEmpty() -> add(resumeAiVsAiFor(language))
            myColor != null -> add(resumeOpponentLabelFor(strings, setup.sideFor(myColor.opponent), engineName))
        }
        when {
            myColor == StoneColor.Black && handicap != null -> add(handicap)
            myColor != null -> {
                add(strings.colorLabel(myColor))
                handicap?.let(::add)
            }
            else -> handicap?.let(::add)
        }
        add(resumeBoardAndMovesFor(language, state.boardSize.value, state.moves.size))
    }
    return parts.joinToString(" · ")
}

/** 상대 AI — 캐릭터면 「이름(급수)」, 급수를 직접 정한 AI면 「8급 AI」, 그 밖은 대국 설정 요약과 같은 이름. */
private fun resumeOpponentLabelFor(strings: UiStrings, opponent: SidePlayerSetup, engineName: String): String {
    val character = BotCharacterCatalog.forPlayLevel(opponent.playLevel) ?: return strings.sideSummary(opponent, engineName)
    val name = strings.botCharacterName(character)
    val rank = character.toPlayLevelSetting()?.humanPlayStyle()?.rank ?: return name
    return "$name(${kgsRankLabelFor(strings.language, rank)})"
}

private fun resumeHandicapLabelFor(language: UiLanguage, black: String, stones: Int): String =
    when (language) {
        UiLanguage.Korean -> "$black ${stones}점"
        UiLanguage.English -> "$black, $stones handicap"
        UiLanguage.Japanese -> "$black ${stones}子局"
        UiLanguage.ChineseSimplified -> "$black 让${stones}子"
    }

private fun resumeTwoPlayersFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "둘이서"
        UiLanguage.English -> "Two players"
        UiLanguage.Japanese -> "二人で対局"
        UiLanguage.ChineseSimplified -> "双人对弈"
    }

private fun resumeAiVsAiFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "AI끼리"
        UiLanguage.English -> "AI vs AI"
        UiLanguage.Japanese -> "AI同士"
        UiLanguage.ChineseSimplified -> "AI对弈"
    }

private fun resumeBoardAndMovesFor(language: UiLanguage, size: Int, moves: Int): String =
    when (language) {
        UiLanguage.Korean -> "${size}줄 ${moves}수"
        UiLanguage.English -> "$size×$size, $moves moves"
        UiLanguage.Japanese -> "${size}路 ${moves}手"
        UiLanguage.ChineseSimplified -> "${size}路 ${moves}手"
    }
