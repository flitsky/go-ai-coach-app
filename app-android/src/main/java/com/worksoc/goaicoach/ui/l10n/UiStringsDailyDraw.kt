package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason

/*
 * 실험실 「1일 1회 뽑기」(백로그 #283, 사용자 2026-10-23)의 문구 — `UiStrings` 생성자가 아니라 곁표다(함정 61).
 * 한국어 섹션 이름 「1일 1회 뽑기」는 사용자의 말, 나머지 문장과 영 · 일 · 중은 스레드가 정한 표기.
 */

private val Titles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "1일 1회 뽑기",
    UiLanguage.English to "Daily draw",
    UiLanguage.Japanese to "1日1回くじ",
    UiLanguage.ChineseSimplified to "每日一抽",
)

internal fun dailyDrawTitleFor(language: UiLanguage): String = Titles.getValue(language)

/** 뽑기 단추 — 광고를 보는 사람과 구독자(광고 없이)가 다르다. */
internal fun dailyDrawButtonFor(language: UiLanguage, withAd: Boolean): String =
    if (withAd) {
        when (language) {
            UiLanguage.Korean -> "광고 보고 뽑기"
            UiLanguage.English -> "Watch an ad to draw"
            UiLanguage.Japanese -> "広告を見て引く"
            UiLanguage.ChineseSimplified -> "看广告抽一次"
        }
    } else {
        when (language) {
            UiLanguage.Korean -> "뽑기"
            UiLanguage.English -> "Draw"
            UiLanguage.Japanese -> "引く"
            UiLanguage.ChineseSimplified -> "抽一次"
        }
    }

/** 쿨타임 동안 남은 시간 앞에 붙는 말. */
internal fun dailyDrawNextInFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "다음 뽑기까지"
        UiLanguage.English -> "Next draw in"
        UiLanguage.Japanese -> "次のくじまで"
        UiLanguage.ChineseSimplified -> "距离下次抽取"
    }

/** 쿨타임 표시(시:분:초)를 읽어 줄 말 — 화면에는 숫자만 보인다. */
internal fun dailyDrawCooldownDescriptionFor(language: UiLanguage, hours: Long, minutes: Long): String =
    when (language) {
        UiLanguage.Korean -> "다음 뽑기까지 ${hours}시간 ${minutes}분"
        UiLanguage.English -> "Next draw in ${hours}h ${minutes}m"
        UiLanguage.Japanese -> "次のくじまで${hours}時間${minutes}分"
        UiLanguage.ChineseSimplified -> "距离下次抽取还有${hours}小时${minutes}分钟"
    }

/** 확률 줄의 상품 이름 — 조각 · 완전판은 지금 대상 캐릭터의 이름을 단다. */
internal fun dailyDrawShardLabelFor(language: UiLanguage, characterName: String): String =
    when (language) {
        UiLanguage.Korean -> "$characterName 조각"
        UiLanguage.English -> "$characterName shard"
        UiLanguage.Japanese -> "${characterName}のかけら"
        UiLanguage.ChineseSimplified -> "${characterName}碎片"
    }

/** 확률 줄의 「광고 캐릭터」 — 아직 없는 광고 캐릭터가 남지 않아 이름을 달 수 없을 때(#289). */
internal fun dailyDrawAdCharacterLabelFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "광고 캐릭터"
        UiLanguage.English -> "Ad character"
        UiLanguage.Japanese -> "広告キャラクター"
        UiLanguage.ChineseSimplified -> "广告角色"
    }

/** 확률 줄에서 이미 가진 캐릭터 뒤에 붙는 말 — 뽑히면 7일 단위 보상으로 대체된다(#289). */
internal fun dailyDrawSubstituteSuffixFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "→ 7일 단위 보상"
        UiLanguage.English -> "→ weekly reward"
        UiLanguage.Japanese -> "→ 7日ごとの報酬"
        UiLanguage.ChineseSimplified -> "→ 7日奖励"
    }

internal fun dailyDrawTicketsLabelFor(language: UiLanguage, each: Int): String =
    when (language) {
        UiLanguage.Korean -> "형세 · 추천 수 1회권 ${each}장씩"
        UiLanguage.English -> "$each eval + $each top-move tickets"
        UiLanguage.Japanese -> "形勢・推奨手チケット各${each}枚"
        UiLanguage.ChineseSimplified -> "形势·推荐券各${each}张"
    }

/** 뽑은 결과 창의 제목. */
internal fun dailyDrawResultTitleFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "뽑기 결과"
        UiLanguage.English -> "You drew"
        UiLanguage.Japanese -> "くじの結果"
        UiLanguage.ChineseSimplified -> "抽取结果"
    }

/** 결과 창 아래의 한 줄 — 다음은 하루 뒤라는 것(쿨타임이 왜 도는지). */
internal fun dailyDrawComeBackFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "다음 뽑기는 24시간 뒤예요."
        UiLanguage.English -> "Your next draw is in 24 hours."
        UiLanguage.Japanese -> "次のくじは24時間後です。"
        UiLanguage.ChineseSimplified -> "24小时后可再次抽取。"
    }

/** 광고를 끝까지 보지 않았거나 불러오지 못했을 때 — 뽑지 않았고 쿨타임도 돌지 않는다. */
internal fun dailyDrawAdFailedFor(language: UiLanguage, reason: AdRewardFailureReason): String =
    when (reason) {
        AdRewardFailureReason.DismissedWithoutReward -> when (language) {
            UiLanguage.Korean -> "끝까지 보셔야 뽑을 수 있어요."
            UiLanguage.English -> "Watch to the end to draw."
            UiLanguage.Japanese -> "最後まで視聴するとくじを引けます。"
            UiLanguage.ChineseSimplified -> "看完整段广告才能抽取。"
        }
        AdRewardFailureReason.LoadFailed,
        AdRewardFailureReason.ShowFailed,
        AdRewardFailureReason.Unavailable,
        -> when (language) {
            UiLanguage.Korean -> "광고를 불러오지 못했어요, 잠시 후 다시 시도해 주세요."
            UiLanguage.English -> "Couldn't load an ad. Please try again in a moment."
            UiLanguage.Japanese -> "広告を読み込めませんでした。しばらくしてからもう一度お試しください。"
            UiLanguage.ChineseSimplified -> "广告加载失败，请稍后再试。"
        }
    }
