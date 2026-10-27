package com.worksoc.goaicoach.ui.l10n

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

/** 마이 페이지 머리말의 전적 줄 — 아직 센 판이 없을 때. 0승 0패나 0%라고 말하지 않는다. */
internal fun myPageNoRecordFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "전적 없음"
        UiLanguage.English -> "No games yet"
        UiLanguage.Japanese -> "戦績なし"
        UiLanguage.ChineseSimplified -> "暂无战绩"
    }

/**
 * 마이 페이지에 남은 고지 **한 줄**(백로그 #241) — 누르면 「내 기록은 어떻게 보관되나요」의 본문이 열린다.
 * ⚠️ 소실도 안전도 단정하지 않는다(본문의 원칙 그대로) — "기기에 보관된다"는 사실만 말한다.
 */
internal fun localOnlyDataNoticePointerFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "기록은 이 기기에 보관돼요"
        UiLanguage.English -> "These records are kept on this device"
        UiLanguage.Japanese -> "この記録はこの端末に保存されます"
        UiLanguage.ChineseSimplified -> "这些记录保存在本机"
    }

/**
 * 「내 기록은 어떻게 보관되나요」의 iOS 본문(백로그 #294 — 사용자 2026-10-27 「iOS 사실대로」). iPhone은 앱 데이터를 기기 백업(iCloud · 컴퓨터)에 담는다 —
 * 새 iPhone으로 옮기거나 그 백업에서 복원하면 돌아오고, 앱만 지웠다 다시 설치하면 돌아오지 않는다. ⚠️ 구독 복원 문장은 iOS 결제(#296)가 생길 때 더한다(없는 것을 약속하지 않는다).
 */
internal fun iosLocalStorageNoticeFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean ->
            "출석 기록·모은 캐릭터·1회권은 이 iPhone에 쌓입니다. iPhone 백업(iCloud 또는 컴퓨터)에 함께 담겨서, 새 iPhone으로 옮기거나 그 백업에서 복원하면 돌아와요. 앱만 지웠다 다시 설치하면 돌아오지 않아요."
        UiLanguage.English ->
            "Check-ins, characters and single-use tickets build up on this iPhone. They are part of your iPhone backup (iCloud or computer), so they come back when you move to a new iPhone or restore from that backup. Deleting and reinstalling only the app does not bring them back."
        UiLanguage.Japanese ->
            "出席記録・集めたキャラクター・1回券はこのiPhoneにたまります。iPhoneのバックアップ（iCloudまたはコンピュータ）に含まれるので、新しいiPhoneへの移行やそのバックアップからの復元で戻ります。アプリだけを削除して再インストールした場合は戻りません。"
        UiLanguage.ChineseSimplified ->
            "签到记录、收集的角色和单次券会积累在这台 iPhone 上。它们包含在 iPhone 备份（iCloud 或电脑）中，迁移到新 iPhone 或从该备份恢复时会一并恢复。仅删除并重新安装本应用则不会恢复。"
    }
