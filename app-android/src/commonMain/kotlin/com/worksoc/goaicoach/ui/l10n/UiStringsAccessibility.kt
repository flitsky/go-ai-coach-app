package com.worksoc.goaicoach.ui.l10n

/**
 * 화면에는 안 보이고 **스크린 리더만 읽는 이름**(QA-22, 2026-11-23).
 *
 * 기호(☰ · ◀◀ · ⏮)나 그림(←)만 그린 단추는 그 기호를 이름으로 읽힌다 — 「☰」, 「◀◀」. 그래서 이름을 따로 준다.
 * ⚠️ 낱말은 스레드가 고른 첫 값이다(사용자 확인 전). 폭은 보이는 문구와 같은 규칙(DECISIONS §1)을 지킨다.
 * ⚠️ [UiStrings] 생성자에 넣지 않는다 — 자리가 남지 않았다(함정 61). 그물은 `UiStringWidthParityTest`(폭)와
 * `UiStringsAccessibilityTest`(네 언어가 다 있는지 — 폭 그물은 언어가 빠진 표를 조용히 건너뛴다).
 */

/** 하위 화면 윗줄 ← 의 이름. 「닫기」(✕ — 창을 닫는다)와 「뒤로」(← — 한 단계 돌아간다)는 다른 일이다. */
private val BackNavigationLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "뒤로",
    UiLanguage.English to "Back",
    UiLanguage.Japanese to "戻る",
    UiLanguage.ChineseSimplified to "返回",
)

/** 대국 화면 ☰ 의 이름(펼친 뒤에는 보이는 글자 「닫기」가 이름이다). 일본어 「対局メニュー」(12칸)는 폭 11을 넘어 「メニュー」다. */
private val GameMenuButtonLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 메뉴",
    UiLanguage.English to "Game menu",
    UiLanguage.Japanese to "メニュー",
    UiLanguage.ChineseSimplified to "对局菜单",
)

/** 다시보기 · 기보 보기의 이동 단추 여섯 — 그리는 차례(⏮ ◀◀ ◀ ▶ ▶▶ ⏭)와 같다. */
internal enum class ReplayTransportStep { First, FastBackward, Previous, Next, FastForward, Last }

/**
 * ⚠️ 빨리 감기 둘의 「10」은 `ReplayFastStep`과 같아야 한다(`UiStringsAccessibilityTest`가 지킨다) — 값을 끼워 만들면 폭 그물 밖이라 표에 적었다.
 * 한국어는 「10수 앞」을 쓰지 않는다 — 「앞」이 「앞으로 감」과 「앞선 수」 둘로 읽힌다.
 */
private val ReplayTransportLabels: Map<ReplayTransportStep, Map<UiLanguage, String>> = mapOf(
    ReplayTransportStep.First to mapOf(
        UiLanguage.Korean to "처음으로",
        UiLanguage.English to "First",
        UiLanguage.Japanese to "最初へ",
        UiLanguage.ChineseSimplified to "回到开局",
    ),
    ReplayTransportStep.FastBackward to mapOf(
        UiLanguage.Korean to "이전 10수",
        UiLanguage.English to "Previous 10",
        UiLanguage.Japanese to "前の10手",
        UiLanguage.ChineseSimplified to "后退10手",
    ),
    ReplayTransportStep.Previous to mapOf(
        UiLanguage.Korean to "이전 수",
        UiLanguage.English to "Previous",
        UiLanguage.Japanese to "前の手",
        UiLanguage.ChineseSimplified to "上一手",
    ),
    ReplayTransportStep.Next to mapOf(
        UiLanguage.Korean to "다음 수",
        UiLanguage.English to "Next",
        UiLanguage.Japanese to "次の手",
        UiLanguage.ChineseSimplified to "下一手",
    ),
    ReplayTransportStep.FastForward to mapOf(
        UiLanguage.Korean to "다음 10수",
        UiLanguage.English to "Next 10",
        UiLanguage.Japanese to "次の10手",
        UiLanguage.ChineseSimplified to "前进10手",
    ),
    ReplayTransportStep.Last to mapOf(
        UiLanguage.Korean to "마지막으로",
        UiLanguage.English to "Last",
        UiLanguage.Japanese to "最後へ",
        UiLanguage.ChineseSimplified to "跳到最后",
    ),
)

internal fun backNavigationLabelFor(language: UiLanguage): String = BackNavigationLabels.getValue(language)

internal fun gameMenuButtonLabelFor(language: UiLanguage): String = GameMenuButtonLabels.getValue(language)

internal fun replayTransportLabelFor(language: UiLanguage, step: ReplayTransportStep): String =
    ReplayTransportLabels.getValue(step).getValue(language)
