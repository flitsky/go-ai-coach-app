package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason

/*
 * 「광고 보고 광고 스킵권 받기」(백로그 #283, 사용자 2026-10-23)의 문구 — `UiStrings` 생성자가 아니라 곁표다(함정 61).
 * 한국어 「광고 보고 +1」은 사용자와 정한 시안의 말, 나머지 문장과 영 · 일 · 중은 스레드가 정한 표기.
 */

/** 「내 정보 › 보유 중인 1회권」의 광고 스킵권 줄에 붙는 단추. */
private val WatchAdLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "광고 보고 +1",
    UiLanguage.English to "Watch ad +1",
    UiLanguage.Japanese to "広告を見て+1",
    UiLanguage.ChineseSimplified to "看广告 +1",
)

internal fun adSkipTicketAdButtonFor(language: UiLanguage): String = WatchAdLabels.getValue(language)

/** 받은 직후의 안내 — 다음은 하루 뒤라는 것까지 말한다(쿨타임이 왜 도는지). */
internal fun adSkipTicketAdEarnedFor(language: UiLanguage, ticketName: String): String =
    when (language) {
        UiLanguage.Korean -> "$ticketName 1장을 받았어요. 다음은 24시간 뒤에 받을 수 있어요."
        UiLanguage.English -> "Got 1 $ticketName. You can get the next one in 24 hours."
        UiLanguage.Japanese -> "${ticketName}を1枚獲得しました。次は24時間後に受け取れます。"
        UiLanguage.ChineseSimplified -> "已获得1张$ticketName。24小时后可再次领取。"
    }

/** 광고를 끝까지 보지 않았거나 불러오지 못했을 때 — 쿨타임은 돌지 않는다. */
internal fun adSkipTicketAdFailedFor(language: UiLanguage, reason: AdRewardFailureReason): String =
    when (reason) {
        AdRewardFailureReason.DismissedWithoutReward -> when (language) {
            UiLanguage.Korean -> "끝까지 보셔야 받을 수 있어요."
            UiLanguage.English -> "Watch to the end to get it."
            UiLanguage.Japanese -> "最後まで視聴すると受け取れます。"
            UiLanguage.ChineseSimplified -> "看完整段广告才能领取。"
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

/** 쿨타임 표시(시:분:초)를 읽어 줄 말 — 화면에는 숫자만 보인다. */
internal fun adSkipTicketAdCooldownDescriptionFor(language: UiLanguage, hours: Long, minutes: Long): String =
    when (language) {
        UiLanguage.Korean -> "다시 받기까지 ${hours}시간 ${minutes}분"
        UiLanguage.English -> "Available again in ${hours}h ${minutes}m"
        UiLanguage.Japanese -> "再び受け取れるまで${hours}時間${minutes}分"
        UiLanguage.ChineseSimplified -> "${hours}小时${minutes}分钟后可再次领取"
    }
