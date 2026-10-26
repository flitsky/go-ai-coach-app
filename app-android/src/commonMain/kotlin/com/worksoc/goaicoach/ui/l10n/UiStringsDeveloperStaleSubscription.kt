package com.worksoc.goaicoach.ui.l10n

/** 개발자 2차의 「72시간 지난 구독 만들기」 한 줄(백로그 #209 확인용) — 개발자 화면 문구도 네 언어를 지킨다. */
internal object UiStringsDeveloperStaleSubscription {
    fun title(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "구독 확인을 72시간 넘게 못 한 상태로"
        UiLanguage.English -> "Make the subscription check over 72 hours old"
        UiLanguage.Japanese -> "購読の確認を72時間以上できていない状態に"
        UiLanguage.ChineseSimplified -> "设为超过72小时未确认订阅"
    }

    fun subtitle(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "구독(Purchase)으로 두고 마지막 확인 시각을 72시간 전보다 앞으로 옮긴다. 다음 포그라운드 복귀 때 판정된다(#209)."
        UiLanguage.English -> "Sets a subscription with its last check older than 72 hours. It is judged on the next return to the foreground (#209)."
        UiLanguage.Japanese -> "購読状態にして最終確認時刻を72時間より前に移す。次にフォアグラウンドへ戻った時に判定される(#209)。"
        UiLanguage.ChineseSimplified -> "设为订阅并把最后确认时间移到72小时之前。下次回到前台时判定(#209)。"
    }

    fun action(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "만들기"
        UiLanguage.English -> "Apply"
        UiLanguage.Japanese -> "適用"
        UiLanguage.ChineseSimplified -> "应用"
    }

    fun done(language: UiLanguage): String = when (language) {
        UiLanguage.Korean -> "72시간 지난 구독으로 맞췄습니다 — 앱을 배경에 보냈다 돌아오면 판정합니다."
        UiLanguage.English -> "Set to a stale subscription — send the app to the background and back to judge it."
        UiLanguage.Japanese -> "72時間経過した購読にしました — アプリをバックグラウンドから戻すと判定します。"
        UiLanguage.ChineseSimplified -> "已设为超过72小时的订阅 — 将应用切到后台再回来即会判定。"
    }
}
