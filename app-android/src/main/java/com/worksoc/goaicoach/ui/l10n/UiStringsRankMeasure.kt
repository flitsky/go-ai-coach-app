package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureChange
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureDemotionLosses
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMarginPerStep
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMaxPromotionSteps
import com.worksoc.goaicoach.application.rankmeasure.RankPromotionOffer
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.customRank

/**
 * **승급 대국**(백로그 #219)의 문구 — 내 기력을 알아보고, 비슷한 상대와 두며 배운다.
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
        UiLanguage.Korean -> "승급 대국"
        UiLanguage.English -> "Rank Match"
        UiLanguage.Japanese -> "昇級対局"
        UiLanguage.ChineseSimplified -> "升级对局"
    }

/**
 * 홈의 두 번째 카드 부제(#288). 「이기면 오른다」고 쓰지 않는다 — 15집 미만 차로 이기면 그대로이고, 내려가는 것은 연패 뒤다(`adjustRankAfterResult`).
 * 「오르내린다」까지만 말하고 자세한 규칙은 가이드 다시보기와 설정 창이 맡는다.
 */
internal fun rankMeasureSubtitleFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "결과에 따라 급수가 오르내리며 내 기력을 잽니다."
        UiLanguage.English -> "Results move your rank up or down to find your real strength."
        UiLanguage.Japanese -> "結果に応じて級が上下し、自分の棋力を測れます。"
        UiLanguage.ChineseSimplified -> "级位随对局结果升降，测出你的真实棋力。"
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
 * 오르내리는 규칙(사용자 2026-10-07 → 2026-10-15 최종, 백로그 #268) — 이긴 집 수 차이 15집마다 한 단계를 **고른다**(급은 한 판에 최대 3급·1단까지,
 * 단은 한 단 · 15집 미만이면 그대로), 어느 구간이든 3연패하면 한 단계 내림. ⚠️ 규칙(`adjustRankAfterResult`)과 한 글자도 어긋나면 안 된다 —
 * 사용자가 화면에서 읽는 것이 규칙이다. 숫자는 규칙의 상수에서 읽는다.
 */
internal fun rankMeasureRulesFor(language: UiLanguage): String {
    val per = RankMeasureMarginPerStep.toInt()
    val max = RankMeasureMaxPromotionSteps
    val losses = RankMeasureDemotionLosses
    return when (language) {
        UiLanguage.Korean -> "급 구간은 이긴 집 수 차이 ${per}집마다 한 급씩, 한 판에 최대 ${max}급까지 오를 수 있고 몇 급 오를지 직접 고릅니다(${per}집 미만이면 그대로, 1단까지). 단 구간은 ${per}집 이상 차이로 이기면 한 단 오를지 고릅니다. 어느 구간이든 ${losses}연패하면 한 단계 내려갑니다."
        UiLanguage.English -> "In the kyu ranks, every $per points you win by lets you move up one rank, at most $max per game, and you choose how far (a win by less than $per keeps your rank; up to 1 dan). In the dan ranks, a win by $per points or more lets you choose to move up one dan. Anywhere, $losses losses in a row lower you one step."
        UiLanguage.Japanese -> "級では勝った目数差${per}目ごとに1級、1局で最大${max}級まで上がれ、何級上がるかは自分で選びます（${per}目未満ならそのまま、1段まで）。段では${per}目以上の差で勝つと1段上がるかを選びます。どの区間でも${losses}連敗で1段階下がります。"
        UiLanguage.ChineseSimplified -> "级位区间每多赢${per}目可升一级，每局最多${max}级，升几级由你选择（不足${per}目则保持不变，最高到1段）。段位区间赢${per}目以上可选择是否升一段。无论哪个区间，${losses}连败降一档。"
    }
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
 * 이어할 대국이 있을 때 카드를 누르면 먼저 묻는 말 — 「캐릭터 대국」의 경고와 앞 두 문장이 같고 **끝 문장만 다르다**: 저쪽은
 * "대국 설정으로 이동하시겠습니까?"인데 이 카드는 대국 설정으로 가지 않는다(2026-10-06 에뮬레이터에서 그 문장이 그대로 떴다).
 */
internal fun rankMeasureOverwriteWarningFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "저장된 이전 대국이 존재합니다. 새 대국을 시작하면 이전 대국을 이어받을 수 없게 됩니다. 승급 대국을 시작하시겠습니까?"
        UiLanguage.English -> "A saved match exists. Starting a new match may overwrite your previous progress. Start a rank match?"
        UiLanguage.Japanese -> "保存された前回の対局が存在します。新しい対局を開始すると前回の対局データが上書きされる可能性があります。昇級対局を始めますか？"
        UiLanguage.ChineseSimplified -> "存在已保存的前局。开始新对局可能会覆盖之前的进度。是否开始升级对局？"
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
 * **기력 변동 팝업**의 제목(사용자 2026-10-07: *"몇 급이 승급되었는지 사용자가 인지할 수 있도록 팝업으로"*) — 오를 수 있는 승리는 팝업이 아니라
 * 고르는 창이다(#268, [rankPromotionChoiceTitleFor]). 알릴 것이 없으면 `null` — 팝업이 없다.
 */
internal fun rankMeasureChangeTitleFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        RankMeasureChange.None -> null
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

/** 팝업 한가운데의 큰 줄 — 어디서 어디로. `10급 → 11급`. 기력이 안 바뀐 알림(이미 9단)에는 없다. */
internal fun rankMeasureChangeRanksFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        is RankMeasureChange.Demoted -> "${kgsRankLabelFor(language, change.from)} → ${kgsRankLabelFor(language, change.to)}"
        RankMeasureChange.None, RankMeasureChange.AtTheTop -> null
    }

/** 팝업의 설명 — 왜 움직였는가(3연패) · 왜 그대로인가(이미 9단). 연패 수는 규칙의 상수에서 읽는다. */
internal fun rankMeasureChangeMessageFor(language: UiLanguage, change: RankMeasureChange): String? =
    when (change) {
        RankMeasureChange.None -> null
        is RankMeasureChange.Demoted -> when (language) {
            UiLanguage.Korean -> "${RankMeasureDemotionLosses}연패로 한 단계 내려갑니다."
            UiLanguage.English -> "$RankMeasureDemotionLosses losses in a row — you move down one step."
            UiLanguage.Japanese -> "${RankMeasureDemotionLosses}連敗で1段階下がります。"
            UiLanguage.ChineseSimplified -> "${RankMeasureDemotionLosses}连败，降一档。"
        }
        RankMeasureChange.AtTheTop -> when (language) {
            UiLanguage.Korean -> "승단할 만큼 크게 이겼습니다. 이미 가장 높은 기력입니다."
            UiLanguage.English -> "You won big enough to move up. You are already at the highest rank."
            UiLanguage.Japanese -> "昇段できるほどの差で勝ちました。すでに最高の棋力です。"
            UiLanguage.ChineseSimplified -> "你赢得足以升段。你已是最高棋力。"
        }
    }

/**
 * **승급을 고르는 창**의 제목(사용자 2026-10-15, #268 — *"승급하시겠습니까?"*). 단 구간이면 「승단」이다.
 */
internal fun rankPromotionChoiceTitleFor(language: UiLanguage, rank: KgsRank): String =
    when (language) {
        UiLanguage.Korean -> if (rank.isDan) "승단하시겠습니까?" else "승급하시겠습니까?"
        UiLanguage.English -> "Move up?"
        UiLanguage.Japanese -> if (rank.isDan) "昇段しますか？" else "昇級しますか？"
        UiLanguage.ChineseSimplified -> if (rank.isDan) "要升段吗？" else "要升级吗？"
    }

/** 고르는 창의 까닭 한 줄 — 이긴 집 수 차이. 상대가 기권한 판은 그 순간의 형세라 「약」을 붙여 계가한 집 수 차이와 가른다. */
internal fun rankPromotionChoiceReasonFor(language: UiLanguage, offer: RankPromotionOffer): String {
    val margin = marginText(offer.margin)
    return if (offer.byResignation) {
        when (language) {
            UiLanguage.Korean -> "상대가 약 ${margin}집 뒤진 형세에서 기권했습니다."
            UiLanguage.English -> "Your opponent resigned about $margin points behind."
            UiLanguage.Japanese -> "相手が約${margin}目負けの形勢で投了しました。"
            UiLanguage.ChineseSimplified -> "对手在落后约${margin}目时认输。"
        }
    } else {
        when (language) {
            UiLanguage.Korean -> "${margin}집 차로 이겼습니다."
            UiLanguage.English -> "You won by $margin points."
            UiLanguage.Japanese -> "${margin}目差で勝ちました。"
            UiLanguage.ChineseSimplified -> "以${margin}目之差获胜。"
        }
    }
}

/** 고르는 창의 첫 버튼 — 사용자의 말 그대로 「현재 급수에 머무른다」(#268). 단 구간이면 「현재 단에 머무른다」. */
internal fun rankPromotionStayFor(language: UiLanguage, rank: KgsRank): String =
    when (language) {
        UiLanguage.Korean -> if (rank.isDan) "현재 단에 머무른다" else "현재 급수에 머무른다"
        UiLanguage.English -> "Stay at my current rank"
        UiLanguage.Japanese -> if (rank.isDan) "今の段にとどまる" else "今の級にとどまる"
        UiLanguage.ChineseSimplified -> if (rank.isDan) "保持当前段位" else "保持当前级位"
    }

/**
 * 고르는 창의 「+N」 버튼 — 사용자의 말 그대로 「+1급 승급한다」(#268), 괄호 안은 닿는 급수다(스레드가 더했다 — 2급에서 「+2급」이 1단이 되는 것을 보이려고).
 * 단 구간이면 「+1단 승단한다」.
 */
internal fun rankPromotionUpFor(language: UiLanguage, rank: KgsRank, steps: Int): String {
    val target = kgsRankLabelFor(language, rank.strongerBy(steps))
    return when (language) {
        UiLanguage.Korean -> if (rank.isDan) "+${steps}단 승단한다 ($target)" else "+${steps}급 승급한다 ($target)"
        UiLanguage.English -> if (rank.isDan) "Up $steps dan ($target)" else "Up $steps ${if (steps == 1) "rank" else "ranks"} ($target)"
        UiLanguage.Japanese -> if (rank.isDan) "+${steps}段昇段する（$target）" else "+${steps}級昇級する（$target）"
        UiLanguage.ChineseSimplified -> if (rank.isDan) "升${steps}段（$target）" else "升${steps}级（$target）"
    }
}

/** 고를 수 있는 폭이 1단에서 멈췄을 때 덧붙이는 한 줄 — 「+3급」이 없는 까닭을 말한다. */
internal fun rankMeasureKyuCapNoteFor(language: UiLanguage): String {
    val per = RankMeasureMarginPerStep.toInt()
    return when (language) {
        UiLanguage.Korean -> "급 구간의 승급은 1단까지입니다. 1단부터는 ${per}집 이상 차이로 이기면 한 단씩 오를 수 있습니다."
        UiLanguage.English -> "Kyu-range promotions stop at 1 dan. From there, a win by $per points or more lets you move up one dan."
        UiLanguage.Japanese -> "級での昇級は1段までです。1段からは${per}目以上の差で勝つと1段ずつ上がれます。"
        UiLanguage.ChineseSimplified -> "级位区间的提升最高到1段。从1段起，赢${per}目以上才可升一段。"
    }
}

/** 크게 이겼는데 한 판의 최대(3급)에 걸렸을 때 덧붙이는 한 줄 — 「90집 차로 이겼는데 +3급까지」가 계산 실수로 읽히지 않게. */
internal fun rankMeasureStepCapNoteFor(language: UiLanguage): String {
    val max = RankMeasureMaxPromotionSteps
    return when (language) {
        UiLanguage.Korean -> "한 판에 오를 수 있는 폭은 최대 ${max}급입니다."
        UiLanguage.English -> "A single game can move you up at most $max ranks."
        UiLanguage.Japanese -> "1局で上がれるのは最大${max}級です。"
        UiLanguage.ChineseSimplified -> "每局最多升${max}级。"
    }
}

/** 집 수 차이의 표기 — `61`, `61.5`(반집이 있을 때만 소수). */
private fun marginText(margin: Double): String {
    val magnitude = kotlin.math.abs(margin)
    return if (magnitude == kotlin.math.floor(magnitude)) magnitude.toInt().toString() else magnitude.toString()
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

/** 급수 초기화(사용자 2026-10-15) — 승급 대국 창의 제목 줄 · 내 정보의 기력 카드 오른쪽 아래. */
internal fun rankResetFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "급수 초기화하기"
    UiLanguage.English -> "Reset rank"
    UiLanguage.Japanese -> "級位をリセット"
    UiLanguage.ChineseSimplified -> "重置级位"
}

internal fun rankResetConfirmTitleFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "급수를 초기화할까요?"
    UiLanguage.English -> "Reset your rank?"
    UiLanguage.Japanese -> "級位をリセットしますか？"
    UiLanguage.ChineseSimplified -> "要重置级位吗？"
}

internal fun rankResetConfirmBodyFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "지금 기력과 최고 기력이 지워지고 처음처럼 시작 급수를 다시 고를 수 있어요. 이어 두던 승급 대국도 지워져요. 대국 기록은 그대로예요."
    UiLanguage.English -> "Your current and best rank are cleared, and you can pick a starting rank again. An unfinished rank game is removed too. Your game records stay."
    UiLanguage.Japanese -> "現在の棋力と最高棋力が消え、最初のように開始級位を選び直せます。続きの昇級対局も消えます。対局記録はそのままです。"
    UiLanguage.ChineseSimplified -> "当前棋力和最高棋力将被清除，可以像第一次一样重新选择起始级位。未下完的升级对局也会删除。对局记录保持不变。"
}

internal fun rankResetConfirmFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "초기화"
    UiLanguage.English -> "Reset"
    UiLanguage.Japanese -> "リセット"
    UiLanguage.ChineseSimplified -> "重置"
}
