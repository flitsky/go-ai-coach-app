package com.worksoc.goaicoach.ui.l10n

import androidx.compose.ui.text.style.LineBreak

/**
 * 한국어 문구는 **어절 안에서 줄을 바꾸지 않는다** — 안드로이드는 한글을 글자 사이에서도 끊어(「없으 / 면」) 읽기 거북했다(에뮬레이터 2026-10-21, 홈 명언 #273).
 * 구절 단위 줄바꿈(`WordBreak.Phrase`)은 일본어에만 듣는다. 그래서 어절 안의 글자 사이에 단어 결합자(U+2060, 보이지 않는다)를 넣어 띄어쓰기에서만 끊게 한다.
 *
 * ⚠️ 여기(L0)에 있는 이유: 홈 명언(`ui.setup`)과 「기본 사활」(`ui.study`, #184)이 함께 쓴다 — 둘은 같은 층(L4)이라 서로를 부를 수 없다
 * (`UiPackageCycleRatchetTest`).
 */
internal fun keepWordsWhole(text: String, language: UiLanguage): String =
    if (language != UiLanguage.Korean) text else text.split(' ').joinToString(" ") { word -> word.toList().joinToString(WordJoiner) }

/** 이름은 한 덩어리로 줄을 넘긴다 — 「Lee / Changho 9p」 · 「주니 / 어」 · 「九 / 段」처럼 이름이 갈라졌다(에뮬레이터 2026-10-21, 홈 명언의 말한 이). */
internal fun unbreakable(name: String): String = name.toList().joinToString(WordJoiner) { if (it == ' ') "\u00A0" else it.toString() }

private const val WordJoiner = "\u2060"

/**
 * 명언의 줄바꿈 — 일본어는 구절 단위로 줄을 바꾼다(안드로이드 13+). ⚠️ `LineBreak.Heading`(줄 길이 고르기)은 쓰지 않는다 — 두 줄에 들던 영어가 세 줄이 됐다.
 * 홈 한 줄 자리(`HomeDailyQuote`)와 실험실의 명언록(#280)이 함께 쓴다.
 */
internal val QuoteLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Simple,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/** 명언 글자의 언어 — 일본어 구절 줄바꿈이 일본어 글자에만 듣고, 중국어 한자가 한국·일본 글자꼴로 그려지지 않게 화면 언어로 단다. */
internal val UiLanguage.quoteLocaleTag: String
    get() = when (this) {
        UiLanguage.Korean -> "ko"
        UiLanguage.English -> "en"
        UiLanguage.Japanese -> "ja"
        UiLanguage.ChineseSimplified -> "zh-CN"
    }
