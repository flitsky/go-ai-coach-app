package com.worksoc.goaicoach.ui.l10n

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
