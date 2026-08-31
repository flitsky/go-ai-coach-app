package com.worksoc.goaicoach.ui

/**
 * 보드 바로 위 두 토글이 쓰는 문구(백로그 #39, 2026-08-31 사용자 지시).
 *
 * ## ⚠️ 라벨에 상태를 적지 않는다 — 2026-08-31에 두 번 고쳤다
 * 1. 처음에는 `⇅ 착수 돋보기 켜기`처럼 **누르면 될 쪽**을 적었다.
 * 2. 칠해진 테두리가 "켜짐/최대"를 뜻하게 되면서 그 방식이 깨졌다 — 강조된 버튼이 반대쪽 이름을
 *    달고 있으면 **강조가 그 반대쪽을 가리키는 것처럼** 읽힌다. 그래서 `켜짐/꺼짐`으로 바꿨다.
 * 3. 그러자 이번에는 **글자와 테두리가 같은 말을 두 번** 하게 됐다(사용자: *"켜짐/꺼짐 텍스트는
 *    없애고 테두리 색만으로 충분"*). 돋보기는 켜짐·꺼짐이 자명하므로 **라벨은 이름만** 남긴다.
 *
 * 결과적으로 이 앱 상태판의 관용구와 완전히 같아졌다 — **턴 카드도 자기 이름(`흑`/`백`)만 적고
 * 활성 여부는 색으로** 말한다.
 *
 * ⚠️ **그래서 화면에는 상태를 말하는 글자가 없다.** 스크린 리더에게는 [playMagnifierStateFor]가
 * `stateDescription`으로 넘긴다(이 저장소 `ToggleActionButton`이 쓰는 방식) — 지우면 시각장애
 * 사용자에게 이 버튼은 **상태를 알 수 없는 버튼**이 된다.
 *
 * ⚠️ **바둑판 쪽은 라벨이 계속 바뀐다.** 켜짐/꺼짐이 아니라 **이름이 다른 두 모드**라, 이름을
 * 지우면 무엇이 되는지 알 수 없다. `최대`/`여백`만으로는 무엇의 최대인지 안 읽힌다는 지적에 따라
 * **`바둑판`을 반드시 붙인다**(`theBoardSizeLabelAlwaysNamesTheBoard`가 고정한다).
 */
private val MagnifierLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "착수 돋보기",
    UiLanguage.English to "Move magnifier",
    UiLanguage.Japanese to "着手ルーペ",
    UiLanguage.ChineseSimplified to "落子放大镜",
)

private val BoardFullStates: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "바둑판 최대",
    UiLanguage.English to "Board full",
    UiLanguage.Japanese to "碁盤 最大",
    UiLanguage.ChineseSimplified to "棋盘 最大",
)

private val BoardInsetStates: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "바둑판 여백",
    UiLanguage.English to "Board inset",
    UiLanguage.Japanese to "碁盤 余白",
    UiLanguage.ChineseSimplified to "棋盘 留白",
)

/** 스크린 리더가 읽는 켜짐/꺼짐. **화면에는 나오지 않는다** — 위 KDoc 3번 참고. */
private val OnStates: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "켜짐",
    UiLanguage.English to "On",
    UiLanguage.Japanese to "オン",
    UiLanguage.ChineseSimplified to "已开启",
)

private val OffStates: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "꺼짐",
    UiLanguage.English to "Off",
    UiLanguage.Japanese to "オフ",
    UiLanguage.ChineseSimplified to "已关闭",
)

private val BoardSizeSubjects: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "바둑판 크기",
    UiLanguage.English to "Board size",
    UiLanguage.Japanese to "碁盤の大きさ",
    UiLanguage.ChineseSimplified to "棋盘大小",
)

internal fun playMagnifierLabelFor(language: UiLanguage): String =
    MagnifierLabels.getValue(language)

internal fun playMagnifierStateFor(language: UiLanguage, enabled: Boolean): String =
    if (enabled) OnStates.getValue(language) else OffStates.getValue(language)

internal fun boardSizeToggleLabelFor(language: UiLanguage, isMaxSize: Boolean): String =
    if (isMaxSize) BoardFullStates.getValue(language) else BoardInsetStates.getValue(language)

internal fun boardSizeSubjectFor(language: UiLanguage): String =
    BoardSizeSubjects.getValue(language)
