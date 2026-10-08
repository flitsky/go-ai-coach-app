package com.worksoc.goaicoach.ui.l10n

/**
 * **AI의 기권 제안**(백로그 #213, 사용자 2026-10-07)의 문구 — 형세가 가망 없이 기울면 AI가 한 판에 한 번 기권을 제안하고,
 * 사용자가 받아들이거나 계속 둔다.
 *
 * `UiStrings` 생성자 슬롯이 255/255로 꽉 차 있어(함정 61) 다른 화면들처럼 사이드 테이블에 둔다.
 */
internal fun aiResignationOfferTitleFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "상대가 기권을 제안합니다"
        UiLanguage.English -> "Your opponent offers to resign"
        UiLanguage.Japanese -> "相手が投了を申し出ています"
        UiLanguage.ChineseSimplified -> "对手提出认输"
    }

/** 받아들이면 어떻게 되고, 거절하면 어떻게 되는지 — 다시 묻지 않는다는 것까지 말한다. */
internal fun aiResignationOfferBodyFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "형세가 크게 기울었습니다. 받아들이면 승리로 대국이 끝나고, 계속 두면 이 대국에서는 다시 묻지 않습니다."
        UiLanguage.English -> "The game has swung far in your favour. Accept to end it as a win, or keep playing — you will not be asked again in this game."
        UiLanguage.Japanese -> "形勢が大きく傾きました。受け入れると勝ちで対局が終わり、続けるとこの対局ではもう尋ねません。"
        UiLanguage.ChineseSimplified -> "形势已大幅倾斜。接受则以获胜结束对局；继续对弈的话，本局不再询问。"
    }

internal fun aiResignationOfferAcceptFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "기권 받기"
        UiLanguage.English -> "Accept"
        UiLanguage.Japanese -> "受け入れる"
        UiLanguage.ChineseSimplified -> "接受"
    }

internal fun aiResignationOfferDeclineFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "계속 두기"
        UiLanguage.English -> "Keep playing"
        UiLanguage.Japanese -> "続ける"
        UiLanguage.ChineseSimplified -> "继续下"
    }
