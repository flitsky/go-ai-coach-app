package com.worksoc.goaicoach.ui

/**
 * 보드 바로 위 토글이 쓰는 문구(백로그 #39, 2026-08-31 사용자 지시).
 *
 * ## ⚠️ 라벨에 상태를 적지 않는다 — 2026-08-31에 두 번 고쳤다
 * 강조된 버튼이 반대쪽 이름을 달고 있으면 **강조가 그 반대쪽을 가리키는 것처럼** 읽히고,
 * `켜짐/꺼짐`을 함께 적으면 **글자와 테두리가 같은 말을 두 번** 한다. 그래서 라벨은 이름만
 * 남기고 상태는 색으로 말한다 — 턴 카드가 자기 이름(`흑`/`백`)만 적는 것과 같은 관용구다.
 *
 * ⚠️ **바둑판 쪽은 라벨이 계속 바뀐다.** 켜짐/꺼짐이 아니라 **이름이 다른 두 모드**라, 이름을
 * 지우면 무엇이 되는지 알 수 없다. `최대`/`여백`만으로는 무엇의 최대인지 안 읽힌다는 지적에 따라
 * **`바둑판`을 반드시 붙인다**(`theBoardSizeLabelAlwaysNamesTheBoard`가 고정한다).
 *
 * ⚠️ **[OnStates]·[OffStates]는 돋보기가 사라진 뒤에도 남는다**(백로그 #188) — 스크린 리더가
 * 읽는 켜짐/꺼짐이라 다른 토글이 `stateDescription`으로 쓸 수 있다. 쓰는 곳이 0이 되면 그때 뺄 것.
 */

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

internal fun boardSizeToggleLabelFor(language: UiLanguage, isMaxSize: Boolean): String =
    if (isMaxSize) BoardFullStates.getValue(language) else BoardInsetStates.getValue(language)

internal fun boardSizeSubjectFor(language: UiLanguage): String =
    BoardSizeSubjects.getValue(language)
