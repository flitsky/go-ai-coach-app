package com.worksoc.goaicoach.ui

/**
 * 정식 릴리즈 초기화 안내가 쓰는 문구(백로그 #63). 구조는 `UiStringsAppUpdate.kt`와 같다 —
 * 화면 한 조각이 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 *
 * ⚠️ **"초기화"를 사고처럼 들리게 쓰지 않는다.** 이 안내를 붙인 이유가 *공지 없이 밀면 "버그로
 * 사라졌다"로 읽힌다* 는 것이었는데, 문구가 사과조이면 같은 인상을 준다. **정식 출시라는 사실과
 * 지금부터 새로 쌓인다는 사실**을 담담히 말한다.
 */
private val ReleaseResetTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "정식 출시로 새로 시작해요",
    UiLanguage.English to "Starting fresh for launch",
    UiLanguage.Japanese to "正式リリースで新しく始まります",
    UiLanguage.ChineseSimplified to "正式发布，重新开始",
)

/**
 * ⚠️ **무엇이 지워졌는지 구체적으로 적는다.** "기록이 초기화되었습니다"로만 두면 사용자는 기보나
 * 설정까지 날아갔는지 확인하러 들어가 봐야 한다 — 남은 것도 함께 말해 주는 편이 문의를 줄인다.
 */
private val ReleaseResetBodies: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to
        "테스트 기간에 모은 출석·캐릭터·1회권은 초기화됐어요. " +
        "대국 기록과 설정은 그대로 있어요. 오늘부터 출석 1일차로 다시 쌓여요.",
    UiLanguage.English to
        "Attendance, characters and one-shot tickets from the test period have been reset. " +
        "Your game records and settings are untouched. Attendance starts again from day 1 today.",
    UiLanguage.Japanese to
        "テスト期間に集めた出席・キャラクター・1回券はリセットされました。" +
        "対局記録と設定はそのままです。今日から出席1日目として貯まります。",
    UiLanguage.ChineseSimplified to
        "测试期间累积的签到、角色和单次券已重置。" +
        "对局记录和设置保持不变。今天开始重新从签到第1天累积。",
)

private val ReleaseResetConfirmLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "확인",
    UiLanguage.English to "Got it",
    UiLanguage.Japanese to "確認",
    UiLanguage.ChineseSimplified to "知道了",
)

internal fun releaseResetTitleFor(language: UiLanguage): String =
    ReleaseResetTitles.getValue(language)

internal fun releaseResetBodyFor(language: UiLanguage): String =
    ReleaseResetBodies.getValue(language)

internal fun releaseResetConfirmLabelFor(language: UiLanguage): String =
    ReleaseResetConfirmLabels.getValue(language)
