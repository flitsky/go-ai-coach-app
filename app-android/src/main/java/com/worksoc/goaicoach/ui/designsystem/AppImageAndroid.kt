package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.worksoc.goaicoach.R

/**
 * ⚠️ 리소스를 이름으로 찾지 않는다(`getIdentifier`) — R8이 지운 리소스를 실행 중에야 알게 된다(#47). 명시적으로 적어 두면
 * 리소스가 참조된 것으로 잡힌다.
 */
@Composable
actual fun painterFor(image: AppImage): Painter = painterResource(
    when (image) {
        AppImage.bot_fast_beginner_1 -> R.drawable.bot_fast_beginner_1
        AppImage.bot_fast_beginner_2 -> R.drawable.bot_fast_beginner_2
        AppImage.bot_fast_beginner_3 -> R.drawable.bot_fast_beginner_3
        AppImage.bot_fast_beginner_4 -> R.drawable.bot_fast_beginner_4
        AppImage.bot_fast_beginner_5 -> R.drawable.bot_fast_beginner_5
        AppImage.splash_spotlight_night -> R.drawable.splash_spotlight_night
        AppImage.splash_hanok_dojo_day1 -> R.drawable.splash_hanok_dojo_day1
        AppImage.splash_hanok_dojo_day2 -> R.drawable.splash_hanok_dojo_day2
        AppImage.study_thumb_baduk_intro_1 -> R.drawable.study_thumb_baduk_intro_1
        AppImage.study_thumb_baduk_intro_2 -> R.drawable.study_thumb_baduk_intro_2
        AppImage.study_thumb_baduk_intro_3 -> R.drawable.study_thumb_baduk_intro_3
        AppImage.reward_eval -> R.drawable.reward_eval
        AppImage.reward_top_moves -> R.drawable.reward_top_moves
        AppImage.reward_ad_skip -> R.drawable.reward_ad_skip
        AppImage.reward_undo -> R.drawable.reward_undo
        AppImage.reward_move_review -> R.drawable.reward_move_review
    },
)
