package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * 앱이 그리는 그림 전부(백로그 #294 — 이식 계획 S9). **항목 이름이 곧 안드로이드 리소스 이름이다** — 안드로이드는
 * `R.drawable.<이름>`(`AppImageAndroid.kt`), iOS는 앱 묶음의 `appassets/drawable/<이름>.webp|jpg`(비트맵)이나
 * `scripts/ios-vector-drawables.py`가 옮긴 벡터(보상 아이콘)를 그린다. 그림을 더하면 여기 · 안드로이드 대응표 · 리소스를 함께 더한다
 * (`AppImageContractTest`가 셋을 견준다).
 */
@Suppress("EnumEntryName", "ktlint:standard:enum-entry-name-case")
enum class AppImage {
    bot_fast_beginner_1,
    bot_fast_beginner_2,
    bot_fast_beginner_3,
    bot_fast_beginner_4,
    bot_fast_beginner_5,
    splash_spotlight_night,
    splash_hanok_dojo_day1,
    splash_hanok_dojo_day2,
    study_thumb_baduk_intro_1,
    study_thumb_baduk_intro_2,
    study_thumb_baduk_intro_3,
    reward_eval,
    reward_top_moves,
    reward_ad_skip,
    reward_undo,
    reward_move_review,
}

/** [image]를 그리는 페인터 — 플랫폼이 리소스를 읽는다. */
@Composable
expect fun painterFor(image: AppImage): Painter
