package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.premium.state.MatchFreeUses

/**
 * 「가이드 다시보기」에만 있는 문구(#261, 사용자 2026-10-13 — *"내용이 낡았다"*). 첫 실행 가이드의 단계 문구(`UiStringsGuide.kt`)와 달리
 * 코치마크로 뜨지 않고 다시보기 한 장에만 실린다 — 첫돌이 가이드 이후에 생긴 것들(승급 대국 · 대국 기록 · 학습 하기 · 착수 확인 돌 버튼 ·
 * 무르기 · 내 캐릭터 · 구독 · 바둑판 조작법)을 소개한다.
 *
 * ⚠️ 화면의 이름(버튼·메뉴·절 이름)은 **여기 다시 적지 않고 끼워 넣는다** — 화면에서 이름이 바뀌면 다시보기도 함께 바뀐다.
 * ⚠️ 없는 것을 약속하지 않는다(함정 51) — 숫자는 상수에서 읽는다([MatchFreeUses.PerMatch]). 스레드가 정한 문구다(영·일·중 원어민 확인 없음).
 */
internal object UiStringsGuideReplay {
    fun myCharacters(language: UiLanguage, title: String): String = when (language) {
        UiLanguage.Korean -> "「$title」에서 모은 친구들을 볼 수 있어요. 잠긴 친구는 광고를 보고 조각을 모으면 열려요."
        UiLanguage.English -> "See the friends you've collected in «$title». Locked ones open as you collect pieces by watching ads."
        UiLanguage.Japanese -> "「$title」で集めた仲間を見られます。ロック中の仲間は広告を見てかけらを集めると開きます。"
        UiLanguage.ChineseSimplified -> "在「$title」里可以看到收集到的伙伴。未解锁的伙伴看广告收集碎片后即可解锁。"
    }

    fun premium(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "프리미엄을 구독하면 모든 캐릭터와 모든 기능이 열려요."
        UiLanguage.English -> "Subscribe to Premium to open every character and every feature."
        UiLanguage.Japanese -> "プレミアムを購読すると、すべてのキャラクターとすべての機能が開きます。"
        UiLanguage.ChineseSimplified -> "订阅高级版即可解锁所有角色和所有功能。"
    }

    fun rankMeasure(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "같은 기력의 AI와 둬요. 이기면 기력이 오르고, 두 판 연달아 지면 한 단계 내려가요. 형세 보기 같은 도움 없이 실력으로만 둬요."
        UiLanguage.English -> "Play an AI of your own rank. Win to move up; lose two in a row to drop one step. No hints here — just your own play."
        UiLanguage.Japanese -> "同じ棋力のAIと打ちます。勝てば棋力が上がり、2連敗すると1段階下がります。形勢表示などの手助けなしで、実力だけで打ちます。"
        UiLanguage.ChineseSimplified -> "与同等棋力的 AI 对弈。赢了棋力上升，连输两局降一级。没有形势判断等帮助，全凭实力。"
    }

    fun gameHistory(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "지난 대국을 다시 보고, 형세 그래프로 흐름이 바뀐 곳을 짚어 볼 수 있어요."
        UiLanguage.English -> "Replay past games and spot where the game turned on the evaluation graph."
        UiLanguage.Japanese -> "過去の対局を見返し、形勢グラフで流れが変わった所を確かめられます。"
        UiLanguage.ChineseSimplified -> "可以回看以前的对局，用形势图找出局势转折的地方。"
    }

    fun study(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "바둑의 규칙과 기초를 차근차근 익힐 수 있어요."
        UiLanguage.English -> "Learn the rules and basics of Go step by step."
        UiLanguage.Japanese -> "囲碁のルールと基本を少しずつ身につけられます。"
        UiLanguage.ChineseSimplified -> "可以一步步学习围棋的规则和基础。"
    }

    /** 착수 확인 돌 버튼 — 대국 화면의 버튼 이름 둘(`confirmPlay`·`playMove`)을 끼운다. */
    fun confirmButton(language: UiLanguage, confirmLabel: String, playLabel: String): String = when (language) {
        UiLanguage.Korean -> "판 아래 돌 버튼(「$confirmLabel」)을 켜면 판을 눌러 먼저 놓아 보고, 「$playLabel」 버튼으로 확정해요. 잘못 두는 일이 줄어요."
        UiLanguage.English -> "Turn on the stone button («$confirmLabel») under the board to try a spot first, then confirm with «$playLabel». Fewer slips."
        UiLanguage.Japanese -> "盤の下の石ボタン(「$confirmLabel」)をオンにすると、まず仮に置いてみてから「$playLabel」で確定します。打ち間違いが減ります。"
        UiLanguage.ChineseSimplified -> "打开棋盘下方的棋子按钮(「$confirmLabel」)后，可以先试放，再用「$playLabel」确定，减少误下。"
    }

    fun undo(language: UiLanguage, label: String): String {
        val n = MatchFreeUses.PerMatch
        return when (language) {
            UiLanguage.Korean -> "«$label»는 방금 둔 수를 물러요. 캐릭터와 두는 판마다 ${n}번은 무료예요."
            UiLanguage.English -> "«$label» takes back your last move. Free $n times in each game against a character."
            UiLanguage.Japanese -> "「$label」は直前の手を戻します。キャラクターとの対局では毎局${n}回まで無料です。"
            UiLanguage.ChineseSimplified -> "「$label」可以撤回刚下的一手。与角色对局时每局可免费使用${n}次。"
        }
    }

    /** 바둑판 조작법으로 가는 길 — 설정의 절 이름(`helpTitle`)과 그 줄 이름(`guideTitle`)을 끼운다. */
    fun boardControls(language: UiLanguage, helpTitle: String, guideTitle: String): String = when (language) {
        UiLanguage.Korean -> "바둑판을 다루는 법은 설정 › 「$helpTitle」 › 「$guideTitle」에서 언제든 제가 다시 보여 드려요."
        UiLanguage.English -> "Want to see how to handle the board again? Settings › «$helpTitle» › «$guideTitle», any time."
        UiLanguage.Japanese -> "盤の操作方法は、設定 › 「$helpTitle」 › 「$guideTitle」でいつでもまたお見せします。"
        UiLanguage.ChineseSimplified -> "棋盘的操作方法，随时可以在 设置 › 「$helpTitle」 › 「$guideTitle」 里再看一遍。"
    }
}
