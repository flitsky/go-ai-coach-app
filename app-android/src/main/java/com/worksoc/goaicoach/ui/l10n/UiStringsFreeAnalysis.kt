package com.worksoc.goaicoach.ui.l10n

/**
 * **대국 한 판의 무료 사용**(백로그 #228, 사용자 2026-10-08) 문구 — 캐릭터와 두는 대국에서 형세 보기·추천 수를 기능마다 3회씩
 * 무료로 쓴다. 버튼의 남은 횟수 표기와, 쓴 직후의 한 줄.
 *
 * `UiStrings` 생성자 슬롯이 255/255로 꽉 차 있어(함정 61) 다른 화면들처럼 사이드 테이블에 둔다.
 */
internal fun freeAnalysisMarkFor(language: UiLanguage, remaining: Int): String =
    when (language) {
        UiLanguage.Korean -> "무료 $remaining"
        UiLanguage.English -> "Free $remaining"
        UiLanguage.Japanese -> "無料$remaining"
        UiLanguage.ChineseSimplified -> "免费$remaining"
    }

/**
 * 무료 사용을 쓴 직후의 한 줄 — 이번 판에 몇 번 남았는지 말한다. 다 썼으면 그것을 말한다(다음 탭부터는 1회권·광고·구독이다).
 * ⚠️ 한 줄로 둔다 — 「매 수마다 보려면 메뉴에서」 안내와 한 토스트에 함께 실린다(토스트는 두 줄까지다).
 */
internal fun freeAnalysisUsedToastFor(language: UiLanguage, remaining: Int): String =
    if (remaining > 0) {
        when (language) {
            UiLanguage.Korean -> "무료로 사용했어요 · 이번 판 ${remaining}회 남음"
            UiLanguage.English -> "Free use · $remaining left this game"
            UiLanguage.Japanese -> "無料で使用 · この対局は残り${remaining}回"
            UiLanguage.ChineseSimplified -> "免费使用 · 本局还剩${remaining}次"
        }
    } else {
        when (language) {
            UiLanguage.Korean -> "무료로 사용했어요 · 이번 판의 무료 횟수를 다 썼어요"
            UiLanguage.English -> "Free use · no free uses left this game"
            UiLanguage.Japanese -> "無料で使用 · この対局の無料分は終わりです"
            UiLanguage.ChineseSimplified -> "免费使用 · 本局免费次数已用完"
        }
    }
