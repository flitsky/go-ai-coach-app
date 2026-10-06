package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.customgame.CustomRankPromotionBlock
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.customRank

/**
 * **커스텀 대국**(백로그 #217)의 문구 — 상대의 KGS 급수를 직접 고르고, 연승하면 난이도가 스스로 오른다.
 *
 * `UiStrings` 생성자 슬롯이 255/255로 꽉 차 있어(함정 61) 다른 화면들처럼 사이드 테이블에 둔다.
 *
 * ⚠️ 급수는 **공식 KGS 프로필 값**이지 공인 기력이 아니다 — 고르는 창이 그것을 밝힌다([customRankOfficialNoteFor]).
 * 「5급 실력」처럼 사용자의 기력을 말하는 문구를 여기에 더하지 말 것.
 */

/** 급수 한 칸의 표기 — `5급`·`3단` / `5 kyu`·`3 dan` / `5級`·`3段` / `5级`·`3段`. */
internal fun kgsRankLabelFor(language: UiLanguage, rank: KgsRank): String =
    when (language) {
        UiLanguage.Korean -> "${rank.number}${if (rank.isDan) "단" else "급"}"
        UiLanguage.English -> "${rank.number} ${if (rank.isDan) "dan" else "kyu"}"
        UiLanguage.Japanese -> "${rank.number}${if (rank.isDan) "段" else "級"}"
        UiLanguage.ChineseSimplified -> "${rank.number}${if (rank.isDan) "段" else "级"}"
    }

/** 급수를 직접 고른 상대의 이름 — 좌석 버튼·대국 요약·판정 결과가 함께 쓴다. `커스텀 5급`. */
internal fun customGameOpponentLabelFor(language: UiLanguage, rank: KgsRank): String {
    val rankLabel = kgsRankLabelFor(language, rank)
    return when (language) {
        UiLanguage.Korean -> "커스텀 $rankLabel"
        UiLanguage.English -> "Custom $rankLabel"
        UiLanguage.Japanese -> "カスタム $rankLabel"
        UiLanguage.ChineseSimplified -> "自定义 $rankLabel"
    }
}

/** 좌석 아래의 버튼 — 아직 캐릭터와 두는 중일 때. 누르면 급수를 고르는 창이 열린다. */
internal fun customGamePickRankFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "급수 직접 고르기"
        UiLanguage.English -> "Pick a rank"
        UiLanguage.Japanese -> "級位を直接選ぶ"
        UiLanguage.ChineseSimplified -> "直接选择级位"
    }

internal fun customRankDialogTitleFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "상대 급수 고르기"
        UiLanguage.English -> "Opponent rank"
        UiLanguage.Japanese -> "相手の級位"
        UiLanguage.ChineseSimplified -> "对手级位"
    }

/** 고르는 창의 고지 — 숫자는 공식 프로필의 것이고 공인 기력이 아니다(사용자 2026-10-04: 공식 값을 믿고 쓰되 밝힌다). */
internal fun customRankOfficialNoteFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "KGS 기준 공식 프로필 값입니다. 공인 기력이 아닙니다."
        UiLanguage.English -> "Official KGS-based profile values, not a certified rank."
        UiLanguage.Japanese -> "KGS基準の公式プロファイル値です。公認の棋力ではありません。"
        UiLanguage.ChineseSimplified -> "这是基于KGS的官方配置值，并非认证棋力。"
    }

internal fun customRankWeakerFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "더 약하게"
        UiLanguage.English -> "Weaker"
        UiLanguage.Japanese -> "弱くする"
        UiLanguage.ChineseSimplified -> "更弱"
    }

internal fun customRankStrongerFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "더 세게"
        UiLanguage.English -> "Stronger"
        UiLanguage.Japanese -> "強くする"
        UiLanguage.ChineseSimplified -> "更强"
    }

/** 고를 수 있는 범위의 끝에 닿았을 때 — 무엇을 하면 더 열리는지 말한다. */
internal fun customRankLockedNoteFor(language: UiLanguage, strongest: KgsRank): String {
    val rankLabel = kgsRankLabelFor(language, strongest)
    return when (language) {
        UiLanguage.Korean -> "지금은 ${rankLabel}까지 고를 수 있어요. 더 센 캐릭터를 얻거나 구독하면 더 높은 급수가 열립니다."
        UiLanguage.English -> "You can pick up to $rankLabel for now. Stronger characters or a subscription unlock higher ranks."
        UiLanguage.Japanese -> "今は${rankLabel}まで選べます。より強いキャラクターを手に入れるか登録すると、上の級位が開きます。"
        UiLanguage.ChineseSimplified -> "目前最高可选$rankLabel。获得更强的角色或订阅后可解锁更高级位。"
    }
}

internal fun customGameAutoAdjustLabelFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "연승하면 자동으로 올리기"
        UiLanguage.English -> "Raise on a win streak"
        UiLanguage.Japanese -> "連勝で自動的に上げる"
        UiLanguage.ChineseSimplified -> "连胜时自动提升"
    }

/** 승급 랠리의 규칙 — 2연승이면 올리고, 이어서 이길 때마다 또 올리고, 지면 다시 2연승부터(사용자 2026-10-06). */
internal fun customGameAutoAdjustDescriptionFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "2연승하면 상대를 한 단계 올리고, 그 뒤로는 이길 때마다 또 올립니다. 한 번 지면 다시 2연승부터입니다. 판이 작을수록 한 번에 더 크게 올립니다."
        UiLanguage.English -> "Two wins in a row raise your opponent one step, and every win after that raises it again. One loss and it takes two wins in a row again. Smaller boards move in bigger steps."
        UiLanguage.Japanese -> "2連勝で相手を一段階上げ、その後は勝つたびにさらに上げます。一度負けると、また2連勝からです。盤が小さいほど一度に大きく上げます。"
        UiLanguage.ChineseSimplified -> "两连胜时对手提升一档，此后每胜一局再提升一档。输一局后需重新两连胜。棋盘越小，每次提升幅度越大。"
    }

/** 대국이 끝난 뒤의 한 줄 — 다음 판부터 상대가 오른다. */
internal fun customGamePromotedFor(language: UiLanguage, promotedTo: KgsRank): String {
    val rankLabel = kgsRankLabelFor(language, promotedTo)
    return when (language) {
        UiLanguage.Korean -> "연승! 다음 대국부터 상대가 ${rankLabel}입니다."
        UiLanguage.English -> "Win streak! Your next opponent is $rankLabel."
        UiLanguage.Japanese -> "連勝！次の対局から相手は${rankLabel}です。"
        UiLanguage.ChineseSimplified -> "连胜！下一局起对手为$rankLabel。"
    }
}

/** 오를 차례였지만 못 올랐을 때의 한 줄. */
internal fun customGamePromotionBlockedFor(language: UiLanguage, block: CustomRankPromotionBlock): String =
    when (block) {
        CustomRankPromotionBlock.Locked -> when (language) {
            UiLanguage.Korean -> "연승! 더 높은 급수는 더 센 캐릭터를 얻거나 구독하면 열립니다."
            UiLanguage.English -> "Win streak! Stronger characters or a subscription unlock higher ranks."
            UiLanguage.Japanese -> "連勝！上の級位は、より強いキャラクターか登録で開きます。"
            UiLanguage.ChineseSimplified -> "连胜！获得更强的角色或订阅后可解锁更高级位。"
        }
        CustomRankPromotionBlock.AtTheTop -> when (language) {
            UiLanguage.Korean -> "연승! 이미 가장 높은 급수와 두고 있어요."
            UiLanguage.English -> "Win streak! You are already facing the highest rank."
            UiLanguage.Japanese -> "連勝！すでに最高の級位と対局しています。"
            UiLanguage.ChineseSimplified -> "连胜！你已经在与最高级位对弈。"
        }
    }

/** 판정 결과의 한 줄 — 그 판의 상대. `상대: 커스텀 5급`. */
internal fun customGameOpponentLineFor(language: UiLanguage, rank: KgsRank): String {
    val opponent = customGameOpponentLabelFor(language, rank)
    return when (language) {
        UiLanguage.Korean -> "상대: $opponent"
        UiLanguage.English -> "Opponent: $opponent"
        UiLanguage.Japanese -> "相手: $opponent"
        UiLanguage.ChineseSimplified -> "对手：$opponent"
    }
}

/**
 * 대국 기록 한 줄의 흑백 세팅 — 급수를 직접 고른 AI에는 그 급수를 붙인다. `사람:AI 5급` · `AI 3급:AI 1단`.
 * 캐릭터와 둔 판은 [UiStrings.seatMatchupLabel] 그대로다(`사람:AI`).
 */
internal fun seatMatchupLabelWithRanksFor(strings: UiStrings, playerSetup: PlayerSetup): String {
    val plain = strings.seatMatchupLabel(playerSetup)
    if (playerSetup.black.customRankOrNull() == null && playerSetup.white.customRankOrNull() == null) return plain
    val (black, white) = plain.split(":", limit = 2).let { parts -> parts.first() to parts.getOrElse(1) { "" } }
    fun withRank(label: String, side: SidePlayerSetup): String =
        side.customRankOrNull()?.let { rank -> "$label ${kgsRankLabelFor(strings.language, rank)}" } ?: label
    return "${withRank(black, playerSetup.black)}:${withRank(white, playerSetup.white)}"
}

private fun SidePlayerSetup.customRankOrNull(): KgsRank? =
    if (controller == SeatController.Ai) playLevel.customRank() else null
