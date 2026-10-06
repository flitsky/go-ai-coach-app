package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureChange
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.customRank

/**
 * **기력 측정 대국**(백로그 #219)의 문구 — 내 기력을 알아보고, 비슷한 상대와 두며 배운다.
 * 급수 표기와 급수를 직접 정한 상대의 이름은 나중의 인공지능 캐릭터(#220)도 함께 쓴다.
 *
 * `UiStrings` 생성자 슬롯이 255/255로 꽉 차 있어(함정 61) 다른 화면들처럼 사이드 테이블에 둔다.
 *
 * ⚠️ 급수는 **공식 KGS 프로필 값**이지 공인 기력이 아니다 — 설정 창이 그것을 밝힌다([rankMeasureOfficialNoteFor]).
 * 기력을 증명·인증한다는 뜻의 문구를 여기에 더하지 말 것.
 */

/** 급수 한 칸의 표기 — `5급`·`3단` / `5 kyu`·`3 dan` / `5級`·`3段` / `5级`·`3段`. */
internal fun kgsRankLabelFor(language: UiLanguage, rank: KgsRank): String =
    when (language) {
        UiLanguage.Korean -> "${rank.number}${if (rank.isDan) "단" else "급"}"
        UiLanguage.English -> "${rank.number} ${if (rank.isDan) "dan" else "kyu"}"
        UiLanguage.Japanese -> "${rank.number}${if (rank.isDan) "段" else "級"}"
        UiLanguage.ChineseSimplified -> "${rank.number}${if (rank.isDan) "段" else "级"}"
    }

/** 급수를 직접 정한 상대의 이름 — 대국 화면 머리말·좌석 요약·판정 결과가 함께 쓴다. `18급 AI`. */
internal fun rankedOpponentLabelFor(language: UiLanguage, rank: KgsRank): String {
    val rankLabel = kgsRankLabelFor(language, rank)
    return when (language) {
        UiLanguage.Korean -> "$rankLabel AI"
        UiLanguage.English -> "$rankLabel AI"
        UiLanguage.Japanese -> "${rankLabel}のAI"
        UiLanguage.ChineseSimplified -> "$rankLabel AI"
    }
}

/** 홈의 두 번째 카드 제목. */
internal fun rankMeasureTitleFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "기력 측정 대국"
        UiLanguage.English -> "Rank Match"
        UiLanguage.Japanese -> "棋力測定対局"
        UiLanguage.ChineseSimplified -> "棋力测定对局"
    }

internal fun rankMeasureSubtitleFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "내 기력을 알아보고, 비슷한 상대와 두며 배웁니다."
        UiLanguage.English -> "Find your rank and learn by playing opponents at your level."
        UiLanguage.Japanese -> "自分の棋力を知り、同じくらいの相手と打って学びます。"
        UiLanguage.ChineseSimplified -> "了解自己的棋力，与水平相近的对手对弈学习。"
    }

/** 설정 창의 「현재 기력」 줄. */
internal fun rankMeasureCurrentRankFor(language: UiLanguage, rank: KgsRank): String {
    val rankLabel = kgsRankLabelFor(language, rank)
    return when (language) {
        UiLanguage.Korean -> "현재 기력: $rankLabel"
        UiLanguage.English -> "Current rank: $rankLabel"
        UiLanguage.Japanese -> "現在の棋力: $rankLabel"
        UiLanguage.ChineseSimplified -> "当前棋力：$rankLabel"
    }
}

/** 설정 창의 「최고 기력」 줄 — 아직 이긴 급수가 없으면 「측정 기록 없음」. */
internal fun rankMeasurePeakRankFor(language: UiLanguage, peak: KgsRank?): String {
    val value = peak?.let { kgsRankLabelFor(language, it) } ?: when (language) {
        UiLanguage.Korean -> "측정 기록 없음"
        UiLanguage.English -> "no record yet"
        UiLanguage.Japanese -> "測定記録なし"
        UiLanguage.ChineseSimplified -> "暂无测定记录"
    }
    return when (language) {
        UiLanguage.Korean -> "최고 기력: $value"
        UiLanguage.English -> "Best rank: $value"
        UiLanguage.Japanese -> "最高棋力: $value"
        UiLanguage.ChineseSimplified -> "最高棋力：$value"
    }
}

/** 최초 1회의 기력 선택 안내 — 지금만 고를 수 있고, 첫 판을 시작하면 잠긴다는 것까지 말한다. */
internal fun rankMeasureChooseStartingRankFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "처음 한 번만 시작 기력을 고를 수 있어요(20급~1급). 첫 대국을 시작하면 그 뒤로는 승패로만 바뀝니다."
        UiLanguage.English -> "You can pick your starting rank once (20 kyu to 1 kyu). After your first game, only wins and losses move it."
        UiLanguage.Japanese -> "開始時の棋力は最初の一度だけ選べます（20級〜1級）。最初の対局を始めると、その後は勝敗だけで変わります。"
        UiLanguage.ChineseSimplified -> "起始棋力只能选择一次（20级至1级）。开始第一局后，只会随胜负变化。"
    }

internal fun rankMeasureWeakerFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "낮추기"
        UiLanguage.English -> "Lower"
        UiLanguage.Japanese -> "下げる"
        UiLanguage.ChineseSimplified -> "降低"
    }

internal fun rankMeasureStrongerFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "높이기"
        UiLanguage.English -> "Raise"
        UiLanguage.Japanese -> "上げる"
        UiLanguage.ChineseSimplified -> "提高"
    }

/** 오르내리는 규칙 — 2연승이면 오르고 이어서 이길 때마다 또 오르고, 2연패마다 한 단계 내린다(사용자 2026-10-06). */
internal fun rankMeasureRulesFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "2연승하면 기력이 오르고, 그 뒤로는 이길 때마다 또 오릅니다. 2연패하면 한 단계 내려갑니다. 이기고 지기를 되풀이하는 곳이 내 기력입니다."
        UiLanguage.English -> "Two wins in a row raise your rank, and every win after that raises it again. Two losses in a row lower it one step. Where you keep trading wins and losses is your rank."
        UiLanguage.Japanese -> "2連勝で棋力が上がり、その後は勝つたびにさらに上がります。2連敗で一段階下がります。勝ったり負けたりを繰り返すところが、あなたの棋力です。"
        UiLanguage.ChineseSimplified -> "两连胜时棋力提升，此后每胜一局再提升。两连败时降低一档。胜负交替出现的位置就是你的棋力。"
    }

/** 대국 중 도움이 꺼진다는 안내 — 형세 보기·추천 수·무르기(사용자 2026-10-06). */
internal fun rankMeasureNoAssistNoteFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "대국 중에는 형세 보기·추천 수·무르기를 쓸 수 없어요. 끝난 뒤 다시보기에서 볼 수 있습니다."
        UiLanguage.English -> "Score view, suggested moves and undo are off during the game. You can use them in the replay afterwards."
        UiLanguage.Japanese -> "対局中は形勢表示・おすすめの手・待ったは使えません。終局後のリプレイで見られます。"
        UiLanguage.ChineseSimplified -> "对局中无法使用形势判断、推荐着法和悔棋。结束后可在复盘中查看。"
    }

/** 고지 — 숫자는 공식 프로필의 것이고 공인 기력이 아니다(사용자 2026-10-04: 공식 값을 믿고 쓰되 밝힌다). */
internal fun rankMeasureOfficialNoteFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "KGS 기준 공식 프로필 값입니다. 공인 기력이 아닙니다."
        UiLanguage.English -> "Official KGS-based profile values, not a certified rank."
        UiLanguage.Japanese -> "KGS基準の公式プロファイル値です。公認の棋力ではありません。"
        UiLanguage.ChineseSimplified -> "这是基于KGS的官方配置值，并非认证棋力。"
    }

/**
 * 이어할 대국이 있을 때 카드를 누르면 먼저 묻는 말 — 「대국 하기」의 경고와 앞 두 문장이 같고 **끝 문장만 다르다**: 저쪽은
 * "대국 설정으로 이동하시겠습니까?"인데 이 카드는 대국 설정으로 가지 않는다(2026-10-06 에뮬레이터에서 그 문장이 그대로 떴다).
 */
internal fun rankMeasureOverwriteWarningFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "저장된 이전 대국이 존재합니다. 새 대국을 시작하면 이전 대국을 이어받을 수 없게 됩니다. 기력 측정 대국을 시작하시겠습니까?"
        UiLanguage.English -> "A saved match exists. Starting a new match may overwrite your previous progress. Start a rank match?"
        UiLanguage.Japanese -> "保存された前回の対局が存在します。新しい対局を開始すると前回の対局データが上書きされる可能性があります。棋力測定対局を始めますか？"
        UiLanguage.ChineseSimplified -> "存在已保存的前局。开始新对局可能会覆盖之前的进度。是否开始棋力测定对局？"
    }

/** 진영(흑·백)을 고르는 줄의 제목 — 사용자 요청문의 말 그대로 「플레이어」(2026-10-06). */
internal fun rankMeasureSideLabelFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "플레이어"
        UiLanguage.English -> "You play"
        UiLanguage.Japanese -> "プレイヤー"
        UiLanguage.ChineseSimplified -> "执子"
    }

/** 판 크기를 고르는 줄의 제목. */
internal fun rankMeasureBoardSizeLabelFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "바둑판"
        UiLanguage.English -> "Board"
        UiLanguage.Japanese -> "碁盤"
        UiLanguage.ChineseSimplified -> "棋盘"
    }

/** 기력이 오를수록 큰 판으로 좁힌다는 안내 — 못 고르는 판이 있을 때만 보인다. */
internal fun rankMeasureBoardLimitNoteFor(language: UiLanguage, rank: KgsRank): String =
    if (rank.isDan) {
        when (language) {
            UiLanguage.Korean -> "단 구간은 19줄에서만 잽니다."
            UiLanguage.English -> "Dan ranks are measured on 19×19 only."
            UiLanguage.Japanese -> "段の区間は19路盤だけで測ります。"
            UiLanguage.ChineseSimplified -> "段位区间只在19路棋盘上测定。"
        }
    } else {
        when (language) {
            UiLanguage.Korean -> "10급부터는 13줄·19줄에서 잽니다."
            UiLanguage.English -> "From 10 kyu up, ranks are measured on 13×13 and 19×19."
            UiLanguage.Japanese -> "10級からは13路盤・19路盤で測ります。"
            UiLanguage.ChineseSimplified -> "10级起在13路和19路棋盘上测定。"
        }
    }

internal fun rankMeasureStartFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "대국 시작"
        UiLanguage.English -> "Start"
        UiLanguage.Japanese -> "対局開始"
        UiLanguage.ChineseSimplified -> "开始对局"
    }

/** 대국이 끝난 뒤의 한 줄 — 기력이 바뀌었을 때만 알린다. 그대로면 `null`. */
internal fun rankMeasureChangeNoticeFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        RankMeasureChange.None -> null
        is RankMeasureChange.Promoted -> kgsRankLabelFor(language, change.to).let { rankLabel ->
            when (language) {
                UiLanguage.Korean -> "연승! 기력이 ${rankLabel}으로 올랐습니다."
                UiLanguage.English -> "Win streak! Your rank rose to $rankLabel."
                UiLanguage.Japanese -> "連勝！棋力が${rankLabel}に上がりました。"
                UiLanguage.ChineseSimplified -> "连胜！棋力升至$rankLabel。"
            }
        }
        is RankMeasureChange.Demoted -> kgsRankLabelFor(language, change.to).let { rankLabel ->
            when (language) {
                UiLanguage.Korean -> "2연패 — 기력이 ${rankLabel}으로 내려갑니다."
                UiLanguage.English -> "Two losses in a row — your rank drops to $rankLabel."
                UiLanguage.Japanese -> "2連敗 — 棋力が${rankLabel}に下がります。"
                UiLanguage.ChineseSimplified -> "两连败 — 棋力降至$rankLabel。"
            }
        }
        RankMeasureChange.AtTheTop -> when (language) {
            UiLanguage.Korean -> "연승! 이미 가장 높은 기력입니다."
            UiLanguage.English -> "Win streak! You are already at the highest rank."
            UiLanguage.Japanese -> "連勝！すでに最高の棋力です。"
            UiLanguage.ChineseSimplified -> "连胜！你已是最高棋力。"
        }
    }

/** 다음 판의 판 크기가 기력 때문에 바뀌었을 때의 한 줄 — 「재 대국」이 말없이 다른 판으로 시작하지 않게. */
internal fun rankMeasureBoardChangedFor(language: UiLanguage, rank: KgsRank, boardSize: BoardSize): String {
    val rankLabel = kgsRankLabelFor(language, rank)
    val size = boardSize.value
    return when (language) {
        UiLanguage.Korean -> "${rankLabel}은 ${size}줄부터 잽니다 — ${size}줄로 시작합니다."
        UiLanguage.English -> "$rankLabel is measured on $size×$size and up — starting on $size×$size."
        UiLanguage.Japanese -> "${rankLabel}は${size}路盤から測ります — ${size}路盤で始めます。"
        UiLanguage.ChineseSimplified -> "${rankLabel}从${size}路棋盘起测定 — 以${size}路开始。"
    }
}

/** 판정 결과의 한 줄 — 그 판의 상대. `상대: 18급 AI`. */
internal fun rankedOpponentLineFor(language: UiLanguage, rank: KgsRank): String {
    val opponent = rankedOpponentLabelFor(language, rank)
    return when (language) {
        UiLanguage.Korean -> "상대: $opponent"
        UiLanguage.English -> "Opponent: $opponent"
        UiLanguage.Japanese -> "相手: $opponent"
        UiLanguage.ChineseSimplified -> "对手：$opponent"
    }
}

/**
 * 대국 기록 한 줄의 흑백 세팅 — 급수를 직접 정한 AI에는 그 급수를 붙인다. `사람:AI 5급` · `AI 3급:AI 1단`.
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
