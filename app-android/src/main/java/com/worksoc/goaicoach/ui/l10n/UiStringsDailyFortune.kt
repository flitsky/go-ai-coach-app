package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneMaxRefreshes
import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason

/*
 * 실험실 「오늘의 운세」(백로그 #276, 사용자 2026-10-23)의 화면 문구 — `UiStrings` 생성자가 아니라 곁표다(함정 61). 운세 글 자체는 `DailyFortuneCatalog.kt`.
 *
 * 사용자가 정한 구성: 랜덤 운세 → 별자리별 운세(+ 「별자리 설정하기」) → 띠별 운세(+ 「띠 설정하기」). 별자리별 · 띠별은 근거 데이터가 없어 「(준비 중)」(I-7).
 * 사용자의 말: 갱신의 내러티브 — *"광고 시청을 통해 자신의 운세를 강하게 개입하여 더 나은 방향으로 바꾸려는 의지"*. 그래서 갱신 단추는 「운 바꾸기」이고,
 * 그 위의 한 줄이 그 의지를 바둑에 빗댄다(「한 수로 판이 바뀌듯 …」). 문장과 영 · 일 · 중은 스레드가 정한 표기(원어민 확인 없음, LLM 검수 두 번).
 * ⚠️ 등급 · 점수를 떠올리게 하는 말(「더 좋은 운」 · 「대길」)을 쓰지 않는다 — 사용자: 점수 · 등급이 드러나지 않게.
 */

/** 첫 칸의 이름 — 설정 없이 뽑는 랜덤 운세(사용자의 말 「랜덤 운세」). */
internal fun fortuneRandomTitleFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "랜덤 운세"
    UiLanguage.English -> "Random fortune"
    UiLanguage.Japanese -> "ランダム運勢"
    UiLanguage.ChineseSimplified -> "随机运势"
}

/** 오늘 아직 뽑지 않았을 때의 안내 — 사용자: 누른 순간의 타임스탬프로 뽑는다. */
internal fun fortuneDrawPromptFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "누르는 순간의 기운으로 오늘의 운세를 뽑아요."
    UiLanguage.English -> "Your fortune is drawn from the very moment you tap."
    UiLanguage.Japanese -> "押した瞬間の気で、今日の運勢を引きます。"
    UiLanguage.ChineseSimplified -> "凭你按下那一刻的气运，抽出今日运势。"
}

/** 하루의 첫 운세를 뽑는 단추(무료). */
internal fun fortuneDrawButtonFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "오늘의 운세 뽑기"
    UiLanguage.English -> "Draw today's fortune"
    UiLanguage.Japanese -> "今日の運勢を引く"
    UiLanguage.ChineseSimplified -> "抽取今日运势"
}

/** 둘째 칸 — 별자리별 운세(준비 중). */
internal fun fortuneStarSignTitleFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "별자리별 운세"
    UiLanguage.English -> "Star sign fortune"
    UiLanguage.Japanese -> "星座別の運勢"
    UiLanguage.ChineseSimplified -> "星座运势"
}

/** 둘째 칸 제목 줄 오른쪽 — 별자리를 고르는 자리(지금은 「(준비 중)」을 붙여 흐리게만 보인다). */
internal fun fortuneSetStarSignFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "별자리 설정하기"
    UiLanguage.English -> "Set star sign"
    UiLanguage.Japanese -> "星座を設定"
    UiLanguage.ChineseSimplified -> "设置星座"
}

/** 셋째 칸 — 띠별 운세(준비 중). */
internal fun fortuneZodiacTitleFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "띠별 운세"
    UiLanguage.English -> "Zodiac animal fortune"
    UiLanguage.Japanese -> "干支別の運勢"
    UiLanguage.ChineseSimplified -> "生肖运势"
}

/** 셋째 칸 제목 줄 오른쪽 — 띠를 고르는 자리(지금은 「(준비 중)」). */
internal fun fortuneSetZodiacFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "띠 설정하기"
    UiLanguage.English -> "Set zodiac animal"
    UiLanguage.Japanese -> "干支を設定"
    UiLanguage.ChineseSimplified -> "设置生肖"
}

/** 갱신 단추 — 광고를 보는 사람과 구독자(광고 없이)가 다르다. 「운 바꾸기」는 사용자의 내러티브(운에 스스로 개입한다). */
internal fun fortuneRefreshButtonFor(language: UiLanguage, withAd: Boolean): String =
    if (withAd) {
        when (language) {
            UiLanguage.Korean -> "광고 보고 운 바꾸기"
            UiLanguage.English -> "Watch ad to change luck"
            UiLanguage.Japanese -> "広告を見て運を変える"
            UiLanguage.ChineseSimplified -> "看广告改运"
        }
    } else {
        when (language) {
            UiLanguage.Korean -> "운 바꾸기"
            UiLanguage.English -> "Change your luck"
            UiLanguage.Japanese -> "運を変える"
            UiLanguage.ChineseSimplified -> "改运"
        }
    }

/** 갱신 단추 위의 한 줄 — 갱신이 「운에 스스로 개입하는 일」임을 바둑에 빗댄다. */
internal fun fortuneRefreshInviteFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "한 수로 판이 바뀌듯, 오늘의 운도 내 손으로 바꿀 수 있어요."
    UiLanguage.English -> "One move can change a game — and you can change today's luck yourself, too."
    UiLanguage.Japanese -> "一手で形勢が変わるように、今日の運も自分の手で変えられます。"
    UiLanguage.ChineseSimplified -> "就像一手棋能改变整盘棋，今天的运势也能由你亲手改写。"
}

/** 오늘 남은 갱신 횟수 — 「오늘 남은 기회 2/3」. */
internal fun fortuneRefreshesLeftFor(language: UiLanguage, left: Int): String = when (language) {
    UiLanguage.Korean -> "오늘 남은 기회 $left/$DailyFortuneMaxRefreshes"
    UiLanguage.English -> "$left of $DailyFortuneMaxRefreshes changes left today"
    UiLanguage.Japanese -> "今日の残り回数 $left/$DailyFortuneMaxRefreshes"
    UiLanguage.ChineseSimplified -> "今日剩余次数 $left/$DailyFortuneMaxRefreshes"
}

/** 오늘의 갱신을 다 썼을 때. */
internal fun fortuneRefreshesDoneFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "오늘은 운을 충분히 바꿨어요. 내일 새 운세가 열려요."
    UiLanguage.English -> "You've changed your luck plenty today. A new fortune awaits tomorrow."
    UiLanguage.Japanese -> "今日はもう十分に運を変えましたね。明日また新しい運勢を引けます。"
    UiLanguage.ChineseSimplified -> "今天的运已经改得够多了，明天会有新的运势。"
}

/** 카드 맨 아래의 고지(사용자에게 물은 「재미로 보세요」 — 2026-10-23 착수 질문). */
internal fun fortuneForFunFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "운세는 재미로 보세요."
    UiLanguage.English -> "Fortunes are just for fun."
    UiLanguage.Japanese -> "運勢はお楽しみとしてご覧ください。"
    UiLanguage.ChineseSimplified -> "运势仅供娱乐。"
}

/** 광고가 끝까지 재생되지 않았거나 뜨지 않았을 때 — 운은 그대로다. */
internal fun fortuneAdFailedFor(language: UiLanguage, reason: AdRewardFailureReason): String =
    when (reason) {
        AdRewardFailureReason.DismissedWithoutReward -> when (language) {
            UiLanguage.Korean -> "끝까지 보셔야 운을 바꿀 수 있어요."
            UiLanguage.English -> "Watch to the end to change your luck."
            UiLanguage.Japanese -> "最後まで視聴すると運を変えられます。"
            UiLanguage.ChineseSimplified -> "看完整段广告才能改运。"
        }
        AdRewardFailureReason.LoadFailed,
        AdRewardFailureReason.ShowFailed,
        AdRewardFailureReason.Unavailable,
        -> dailyDrawAdFailedFor(language, reason)
    }
