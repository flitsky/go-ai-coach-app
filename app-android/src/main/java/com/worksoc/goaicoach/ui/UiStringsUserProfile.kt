package com.worksoc.goaicoach.ui

/**
 * 마이 페이지의 「나」 줄이 쓰는 문구(백로그 #165).
 *
 * ⚠️ **[UiStrings] 생성자에 넣지 말 것 — 자리가 0칸이다.** `copy$default`가 JVM 인자 한도
 * 255칸에 딱 붙어 있어 한 줄만 더해도 **앱은 컴파일되고 테스트만 통째로** `ClassFormatError`로
 * 죽는다(함정 61). `UiStringsGameFlow.kt`·`UiStringsStudyLessons.kt`가 먼저 간 길이다.
 */
private val NicknamePlaceholders: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이름을 지어 주세요",
    UiLanguage.English to "Pick a name",
    UiLanguage.Japanese to "名前をつけてください",
    UiLanguage.ChineseSimplified to "取一个名字吧",
)

private val NicknameDialogTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "닉네임",
    UiLanguage.English to "Nickname",
    UiLanguage.Japanese to "ニックネーム",
    UiLanguage.ChineseSimplified to "昵称",
)

/**
 * ⚠️ **"무엇이 있다"가 아니라 "무엇을 하면 된다"로 적는다**(함정 39). 여기서는 상한을 그냥
 * 알리는 것이 아니라 **왜 그 상한인지**가 보이게 둔다 — 한 줄에 들어가야 하기 때문이다.
 */
private val NicknameDialogHints: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "한 줄에 들어가도록 최대 12자까지 쓸 수 있어요. 비우면 이름 없음으로 돌아갑니다.",
    UiLanguage.English to "Up to 12 characters so it fits on one line. Leave it empty to go back to no name.",
    UiLanguage.Japanese to "一行に収まるよう12文字までです。空にすると名前なしに戻ります。",
    UiLanguage.ChineseSimplified to "最多12个字，以便显示在一行内。留空即可恢复为无名。",
)

/** 아바타 원의 접근성 설명 — 원 안의 글자는 장식이라 읽어 줄 것이 따로 필요하다. */
private val AvatarDescriptions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "내 아바타",
    UiLanguage.English to "My avatar",
    UiLanguage.Japanese to "自分のアバター",
    UiLanguage.ChineseSimplified to "我的头像",
)

private val EditNicknameDescriptions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "닉네임 바꾸기",
    UiLanguage.English to "Change nickname",
    UiLanguage.Japanese to "ニックネームを変更",
    UiLanguage.ChineseSimplified to "修改昵称",
)

internal fun nicknamePlaceholderFor(language: UiLanguage): String =
    NicknamePlaceholders.getValue(language)

internal fun nicknameDialogTitleFor(language: UiLanguage): String =
    NicknameDialogTitles.getValue(language)

internal fun nicknameDialogHintFor(language: UiLanguage): String =
    NicknameDialogHints.getValue(language)

internal fun avatarDescriptionFor(language: UiLanguage): String =
    AvatarDescriptions.getValue(language)

internal fun editNicknameDescriptionFor(language: UiLanguage): String =
    EditNicknameDescriptions.getValue(language)
