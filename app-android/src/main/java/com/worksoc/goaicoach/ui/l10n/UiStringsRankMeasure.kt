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

/**
 * 오르내리는 규칙(사용자 2026-10-07) — 급 구간은 이길 때마다 집 수 차이 10집에 한 단계(최소 한 단계, 1단까지), 단 구간은 최근 5판 중
 * 3승이면 한 단, 어느 구간이든 2연패하면 한 단계 내림. ⚠️ 규칙(`adjustRankAfterResult`)과 한 글자도 어긋나면 안 된다 —
 * 사용자가 화면에서 읽는 것이 규칙이다.
 */
internal fun rankMeasureRulesFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "급 구간은 이길 때마다 오릅니다 — 이긴 집 수 차이 10집마다 한 단계(최소 한 단계, 1단까지). 단 구간은 최근 5판 중 3판을 이기면 한 단 오릅니다. 어느 구간이든 2연패하면 한 단계 내려갑니다."
        UiLanguage.English -> "In the kyu ranks every win raises your rank — one step for every 10 points you win by (at least one step, up to 1 dan). In the dan ranks you go up one dan when you win 3 of your last 5 games. Anywhere, two losses in a row lower you one step."
        UiLanguage.Japanese -> "級では勝つたびに上がります — 勝った目数差10目ごとに1段階（最低1段階、1段まで）。段では直近5局のうち3勝で1段上がります。どの区間でも2連敗で1段階下がります。"
        UiLanguage.ChineseSimplified -> "级位区间每胜一局都会提升 — 每赢10目提升一档（至少一档，最高到1段）。段位区间在最近5局中赢3局时升一段。无论哪个区间，两连败降一档。"
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

/**
 * **기력 변동 팝업**의 제목(사용자 2026-10-07: *"몇 급이 승급되었는지 사용자가 인지할 수 있도록 팝업으로"*). 그대로면 `null` — 팝업이 없다.
 */
internal fun rankMeasureChangeTitleFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        RankMeasureChange.None -> null
        is RankMeasureChange.Promoted -> when (language) {
            UiLanguage.Korean -> if (change.to.isDan) "승단!" else "승급!"
            UiLanguage.English -> "Promoted!"
            UiLanguage.Japanese -> if (change.to.isDan) "昇段！" else "昇級！"
            UiLanguage.ChineseSimplified -> if (change.to.isDan) "升段！" else "升级！"
        }
        is RankMeasureChange.Demoted -> when (language) {
            UiLanguage.Korean -> if (change.from.isDan) "강단" else "강급"
            UiLanguage.English -> "Demoted"
            UiLanguage.Japanese -> if (change.from.isDan) "降段" else "降級"
            UiLanguage.ChineseSimplified -> if (change.from.isDan) "降段" else "降级"
        }
        RankMeasureChange.AtTheTop -> when (language) {
            UiLanguage.Korean -> "최고 기력"
            UiLanguage.English -> "Top rank"
            UiLanguage.Japanese -> "最高棋力"
            UiLanguage.ChineseSimplified -> "最高棋力"
        }
    }

/** 팝업 한가운데의 큰 줄 — 어디서 어디로. `15급 → 9급`. 기력이 안 바뀐 알림(이미 9단)에는 없다. */
internal fun rankMeasureChangeRanksFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        is RankMeasureChange.Promoted -> "${kgsRankLabelFor(language, change.from)} → ${kgsRankLabelFor(language, change.to)}"
        is RankMeasureChange.Demoted -> "${kgsRankLabelFor(language, change.from)} → ${kgsRankLabelFor(language, change.to)}"
        RankMeasureChange.None, RankMeasureChange.AtTheTop -> null
    }

/**
 * 팝업의 설명 — **왜, 몇 단계** 움직였는가. 급 구간의 승급은 이긴 집 수 차이와 오른 단계 수를 말하고, 단 구간의 승단은 최근 전적을 말한다.
 * ⚠️ 단계 수는 **실제로 오른 만큼**이다(`RankMeasureChange.Promoted.steps`) — 1단에서 멈췄으면 식의 값보다 작다.
 */
internal fun rankMeasureChangeMessageFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        RankMeasureChange.None -> null
        is RankMeasureChange.Promoted -> change.margin.let { wonBy ->
            when {
                change.from.isDan -> when (language) {
                    UiLanguage.Korean -> "최근 5판 중 3판을 이겨 한 단 올랐습니다."
                    UiLanguage.English -> "You won 3 of your last 5 games and moved up one dan."
                    UiLanguage.Japanese -> "直近5局のうち3勝して1段上がりました。"
                    UiLanguage.ChineseSimplified -> "最近5局中赢了3局，升一段。"
                }
                wonBy == null -> when (language) {
                    UiLanguage.Korean -> "이겨서 ${change.steps}단계 올랐습니다."
                    UiLanguage.English -> "You won and moved up ${stepsInEnglish(change.steps)}."
                    UiLanguage.Japanese -> "勝って${change.steps}段階上がりました。"
                    UiLanguage.ChineseSimplified -> "获胜，提升${change.steps}档。"
                }
                // 기권한 순간의 형세는 어림값이다 — 「약」을 붙여 계가한 집 수 차이와 가른다.
                change.byResignation -> marginText(wonBy).let { margin ->
                    when (language) {
                        UiLanguage.Korean -> "상대가 약 ${margin}집 뒤진 형세에서 기권해 ${change.steps}단계 올랐습니다."
                        UiLanguage.English -> "Your opponent resigned about $margin points behind — you moved up ${stepsInEnglish(change.steps)}."
                        UiLanguage.Japanese -> "相手が約${margin}目負けの形勢で投了し、${change.steps}段階上がりました。"
                        UiLanguage.ChineseSimplified -> "对手在落后约${margin}目时认输，提升${change.steps}档。"
                    }
                }
                else -> marginText(wonBy).let { margin ->
                    when (language) {
                        UiLanguage.Korean -> "${margin}집 차로 이겨 ${change.steps}단계 올랐습니다."
                        UiLanguage.English -> "You won by $margin points and moved up ${stepsInEnglish(change.steps)}."
                        UiLanguage.Japanese -> "${margin}目差で勝ち、${change.steps}段階上がりました。"
                        UiLanguage.ChineseSimplified -> "以${margin}目之差获胜，提升${change.steps}档。"
                    }
                }
            }
        }
        is RankMeasureChange.Demoted -> when (language) {
            UiLanguage.Korean -> "2연패로 한 단계 내려갑니다."
            UiLanguage.English -> "Two losses in a row — you move down one step."
            UiLanguage.Japanese -> "2連敗で1段階下がります。"
            UiLanguage.ChineseSimplified -> "两连败，降一档。"
        }
        RankMeasureChange.AtTheTop -> when (language) {
            UiLanguage.Korean -> "최근 5판 중 3판을 이겼습니다. 이미 가장 높은 기력입니다."
            UiLanguage.English -> "You won 3 of your last 5 games. You are already at the highest rank."
            UiLanguage.Japanese -> "直近5局のうち3勝しました。すでに最高の棋力です。"
            UiLanguage.ChineseSimplified -> "最近5局中赢了3局。你已是最高棋力。"
        }
    }

/** 크게 이겼지만 1단에서 멈췄을 때 덧붙이는 한 줄 — 단계 수가 집 수 차이보다 적은 까닭을 말한다. */
internal fun rankMeasureKyuCapNoteFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "급 구간의 승급은 1단까지입니다. 1단부터는 최근 5판 중 3판을 이기면 오릅니다."
        UiLanguage.English -> "Kyu-range promotions stop at 1 dan. From there you go up by winning 3 of your last 5 games."
        UiLanguage.Japanese -> "級での昇級は1段までです。1段からは直近5局のうち3勝で上がります。"
        UiLanguage.ChineseSimplified -> "级位区间的提升最高到1段。从1段起，最近5局中赢3局才会提升。"
    }

/** 집 수 차이의 표기 — `61`, `61.5`(반집이 있을 때만 소수). */
private fun marginText(margin: Double): String {
    val magnitude = kotlin.math.abs(margin)
    return if (magnitude == kotlin.math.floor(magnitude)) magnitude.toInt().toString() else magnitude.toString()
}

private fun stepsInEnglish(steps: Int): String = if (steps == 1) "1 step" else "$steps steps"

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
