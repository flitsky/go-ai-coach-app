package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneMaxRefreshes
import com.worksoc.goaicoach.application.dailyfortune.FortuneSign
import com.worksoc.goaicoach.application.dailyfortune.FortuneStrand
import com.worksoc.goaicoach.application.dailyfortune.StarSign
import com.worksoc.goaicoach.application.dailyfortune.ZodiacAnimal
import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason

/*
 * 실험실 「오늘의 운세」(백로그 #276, 사용자 2026-10-23)의 화면 문구 — `UiStrings` 생성자가 아니라 곁표다(함정 61). 운세 글 자체는 `DailyFortuneCatalog.kt`.
 *
 * 사용자의 말: 갱신의 내러티브 — *"광고 시청을 통해 자신의 운세를 강하게 개입하여 더 나은 방향으로 바꾸려는 의지"*. 그래서 갱신 단추는 「운 바꾸기」이고,
 * 그 위의 한 줄이 그 의지를 바둑에 빗댄다(「한 수로 판이 바뀌듯 …」). 문장과 영 · 일 · 중은 스레드가 정한 표기(원어민 확인 없음).
 * ⚠️ 등급 · 점수를 떠올리게 하는 말(「더 좋은 운」 · 「대길」)을 쓰지 않는다 — 사용자: 점수 · 등급이 드러나지 않게.
 */

/** 띠 이름 — 한국어는 「용띠」, 일본어는 「たつ年」(亥는 いのしし年), 중국어는 「属龙」. */
internal fun zodiacNameFor(language: UiLanguage, animal: ZodiacAnimal): String = when (language) {
    UiLanguage.Korean -> when (animal) {
        ZodiacAnimal.Rat -> "쥐띠"
        ZodiacAnimal.Ox -> "소띠"
        ZodiacAnimal.Tiger -> "호랑이띠"
        ZodiacAnimal.Rabbit -> "토끼띠"
        ZodiacAnimal.Dragon -> "용띠"
        ZodiacAnimal.Snake -> "뱀띠"
        ZodiacAnimal.Horse -> "말띠"
        ZodiacAnimal.Goat -> "양띠"
        ZodiacAnimal.Monkey -> "원숭이띠"
        ZodiacAnimal.Rooster -> "닭띠"
        ZodiacAnimal.Dog -> "개띠"
        ZodiacAnimal.Pig -> "돼지띠"
    }
    UiLanguage.English -> when (animal) {
        ZodiacAnimal.Rat -> "Rat"
        ZodiacAnimal.Ox -> "Ox"
        ZodiacAnimal.Tiger -> "Tiger"
        ZodiacAnimal.Rabbit -> "Rabbit"
        ZodiacAnimal.Dragon -> "Dragon"
        ZodiacAnimal.Snake -> "Snake"
        ZodiacAnimal.Horse -> "Horse"
        ZodiacAnimal.Goat -> "Goat"
        ZodiacAnimal.Monkey -> "Monkey"
        ZodiacAnimal.Rooster -> "Rooster"
        ZodiacAnimal.Dog -> "Dog"
        ZodiacAnimal.Pig -> "Pig"
    }
    UiLanguage.Japanese -> when (animal) {
        ZodiacAnimal.Rat -> "ねずみ年"
        ZodiacAnimal.Ox -> "うし年"
        ZodiacAnimal.Tiger -> "とら年"
        ZodiacAnimal.Rabbit -> "うさぎ年"
        ZodiacAnimal.Dragon -> "たつ年"
        ZodiacAnimal.Snake -> "へび年"
        ZodiacAnimal.Horse -> "うま年"
        ZodiacAnimal.Goat -> "ひつじ年"
        ZodiacAnimal.Monkey -> "さる年"
        ZodiacAnimal.Rooster -> "とり年"
        ZodiacAnimal.Dog -> "いぬ年"
        ZodiacAnimal.Pig -> "いのしし年"
    }
    UiLanguage.ChineseSimplified -> when (animal) {
        ZodiacAnimal.Rat -> "属鼠"
        ZodiacAnimal.Ox -> "属牛"
        ZodiacAnimal.Tiger -> "属虎"
        ZodiacAnimal.Rabbit -> "属兔"
        ZodiacAnimal.Dragon -> "属龙"
        ZodiacAnimal.Snake -> "属蛇"
        ZodiacAnimal.Horse -> "属马"
        ZodiacAnimal.Goat -> "属羊"
        ZodiacAnimal.Monkey -> "属猴"
        ZodiacAnimal.Rooster -> "属鸡"
        ZodiacAnimal.Dog -> "属狗"
        ZodiacAnimal.Pig -> "属猪"
    }
}

/** 별자리 이름. */
internal fun starSignNameFor(language: UiLanguage, sign: StarSign): String = when (language) {
    UiLanguage.Korean -> when (sign) {
        StarSign.Aries -> "양자리"
        StarSign.Taurus -> "황소자리"
        StarSign.Gemini -> "쌍둥이자리"
        StarSign.Cancer -> "게자리"
        StarSign.Leo -> "사자자리"
        StarSign.Virgo -> "처녀자리"
        StarSign.Libra -> "천칭자리"
        StarSign.Scorpio -> "전갈자리"
        StarSign.Sagittarius -> "사수자리"
        StarSign.Capricorn -> "염소자리"
        StarSign.Aquarius -> "물병자리"
        StarSign.Pisces -> "물고기자리"
    }
    UiLanguage.English -> when (sign) {
        StarSign.Aries -> "Aries"
        StarSign.Taurus -> "Taurus"
        StarSign.Gemini -> "Gemini"
        StarSign.Cancer -> "Cancer"
        StarSign.Leo -> "Leo"
        StarSign.Virgo -> "Virgo"
        StarSign.Libra -> "Libra"
        StarSign.Scorpio -> "Scorpio"
        StarSign.Sagittarius -> "Sagittarius"
        StarSign.Capricorn -> "Capricorn"
        StarSign.Aquarius -> "Aquarius"
        StarSign.Pisces -> "Pisces"
    }
    UiLanguage.Japanese -> when (sign) {
        StarSign.Aries -> "おひつじ座"
        StarSign.Taurus -> "おうし座"
        StarSign.Gemini -> "ふたご座"
        StarSign.Cancer -> "かに座"
        StarSign.Leo -> "しし座"
        StarSign.Virgo -> "おとめ座"
        StarSign.Libra -> "てんびん座"
        StarSign.Scorpio -> "さそり座"
        StarSign.Sagittarius -> "いて座"
        StarSign.Capricorn -> "やぎ座"
        StarSign.Aquarius -> "みずがめ座"
        StarSign.Pisces -> "うお座"
    }
    UiLanguage.ChineseSimplified -> when (sign) {
        StarSign.Aries -> "白羊座"
        StarSign.Taurus -> "金牛座"
        StarSign.Gemini -> "双子座"
        StarSign.Cancer -> "巨蟹座"
        StarSign.Leo -> "狮子座"
        StarSign.Virgo -> "处女座"
        StarSign.Libra -> "天秤座"
        StarSign.Scorpio -> "天蝎座"
        StarSign.Sagittarius -> "射手座"
        StarSign.Capricorn -> "摩羯座"
        StarSign.Aquarius -> "水瓶座"
        StarSign.Pisces -> "双鱼座"
    }
}

/** 고른 것의 이름 — 띠면 띠 이름, 별자리면 별자리 이름. */
internal fun fortuneSignNameFor(language: UiLanguage, sign: FortuneSign): String = when (sign) {
    is FortuneSign.Zodiac -> zodiacNameFor(language, sign.animal)
    is FortuneSign.Star -> starSignNameFor(language, sign.sign)
}

/** 별자리의 날짜(고를 때 보이는 작은 글자) — 월 · 일만, 연도는 없다. */
internal fun starSignDatesFor(language: UiLanguage, sign: StarSign): String = when (language) {
    UiLanguage.Korean -> "${sign.fromMonth}.${sign.fromDay}~${sign.toMonth}.${sign.toDay}"
    UiLanguage.English -> "${EnglishMonths[sign.fromMonth - 1]} ${sign.fromDay}–${EnglishMonths[sign.toMonth - 1]} ${sign.toDay}"
    UiLanguage.Japanese -> "${sign.fromMonth}/${sign.fromDay}〜${sign.toMonth}/${sign.toDay}"
    UiLanguage.ChineseSimplified -> "${sign.fromMonth}.${sign.fromDay}–${sign.toMonth}.${sign.toDay}"
}

private val EnglishMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 아직 고르지 않았을 때 카드의 안내. */
internal fun fortunePickPromptFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "띠나 별자리를 고르면 오늘의 운세가 열려요."
    UiLanguage.English -> "Choose your zodiac animal or star sign to open today's fortune."
    UiLanguage.Japanese -> "干支か星座を選ぶと、今日の運勢が開きます。"
    UiLanguage.ChineseSimplified -> "选择生肖或星座，查看今日运势。"
}

/** 고르기 단추(처음). */
internal fun fortunePickButtonFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "띠 · 별자리 고르기"
    UiLanguage.English -> "Choose"
    UiLanguage.Japanese -> "干支・星座を選ぶ"
    UiLanguage.ChineseSimplified -> "选择生肖或星座"
}

/** 고른 뒤 이름 옆의 작은 단추 — 다시 고르기. */
internal fun fortuneChangeSignFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "다시 고르기"
    UiLanguage.English -> "Change"
    UiLanguage.Japanese -> "選び直す"
    UiLanguage.ChineseSimplified -> "重新选择"
}

/** 고르는 창의 제목. */
internal fun fortunePickerTitleFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "띠 · 별자리 고르기"
    UiLanguage.English -> "Zodiac animal or star sign"
    UiLanguage.Japanese -> "干支・星座を選ぶ"
    UiLanguage.ChineseSimplified -> "选择生肖或星座"
}

/** 고르는 창의 탭 — 띠 / 별자리. */
internal fun fortuneZodiacTabFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "띠"
    UiLanguage.English -> "Zodiac"
    UiLanguage.Japanese -> "干支"
    UiLanguage.ChineseSimplified -> "生肖"
}

internal fun fortuneStarTabFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "별자리"
    UiLanguage.English -> "Star sign"
    UiLanguage.Japanese -> "星座"
    UiLanguage.ChineseSimplified -> "星座"
}

/** 고르는 창 아래의 한 줄 — 무엇을 받지 않는지(생년월일)와 어디에 남는지. */
internal fun fortunePickerNoteFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "생년월일은 묻지 않아요. 고른 것은 이 기기에만 남아요."
    UiLanguage.English -> "We never ask for your birthday. Your choice stays on this device."
    UiLanguage.Japanese -> "生年月日はお聞きしません。選んだものはこの端末にだけ残ります。"
    UiLanguage.ChineseSimplified -> "不会询问你的生日。你的选择只保存在本设备上。"
}

/** 운세 갈래의 제목 — 띠 운세 · 별자리 운세 · 바둑 운세 · 행운의 자리. */
internal fun fortuneStrandLabelFor(language: UiLanguage, strand: FortuneStrand): String = when (strand) {
    FortuneStrand.General -> when (language) {
        UiLanguage.Korean -> "띠 운세"
        UiLanguage.English -> "Zodiac reading"
        UiLanguage.Japanese -> "干支の運勢"
        UiLanguage.ChineseSimplified -> "生肖运势"
    }
    FortuneStrand.Star -> when (language) {
        UiLanguage.Korean -> "별자리 운세"
        UiLanguage.English -> "Star sign reading"
        UiLanguage.Japanese -> "星座の運勢"
        UiLanguage.ChineseSimplified -> "星座运势"
    }
    FortuneStrand.Go -> when (language) {
        UiLanguage.Korean -> "바둑 운세"
        UiLanguage.English -> "Go reading"
        UiLanguage.Japanese -> "囲碁の運勢"
        UiLanguage.ChineseSimplified -> "围棋运势"
    }
    FortuneStrand.LuckyPoint -> when (language) {
        UiLanguage.Korean -> "행운의 자리"
        UiLanguage.English -> "Lucky point"
        UiLanguage.Japanese -> "ラッキーポイント"
        UiLanguage.ChineseSimplified -> "幸运点位"
    }
}

/** 갱신 단추 — 광고를 보는 사람과 구독자(광고 없이)가 다르다. 「운 바꾸기」는 사용자의 내러티브(운에 스스로 개입한다). */
internal fun fortuneRefreshButtonFor(language: UiLanguage, withAd: Boolean): String =
    if (withAd) {
        when (language) {
            UiLanguage.Korean -> "광고 보고 운 바꾸기"
            UiLanguage.English -> "Watch an ad to change your luck"
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
    UiLanguage.English -> "One move can turn a game — and your luck is yours to turn, too."
    UiLanguage.Japanese -> "一手で盤面が変わるように、今日の運も自分の手で変えられます。"
    UiLanguage.ChineseSimplified -> "一手棋能扭转棋局，今天的运势也能由你亲手改变。"
}

/** 오늘 남은 갱신 횟수 — 「오늘 남은 기회 2/3」. */
internal fun fortuneRefreshesLeftFor(language: UiLanguage, left: Int): String = when (language) {
    UiLanguage.Korean -> "오늘 남은 기회 $left/$DailyFortuneMaxRefreshes"
    UiLanguage.English -> "$left of $DailyFortuneMaxRefreshes left today"
    UiLanguage.Japanese -> "今日の残り $left/$DailyFortuneMaxRefreshes 回"
    UiLanguage.ChineseSimplified -> "今日剩余 $left/$DailyFortuneMaxRefreshes 次"
}

/** 오늘의 갱신을 다 썼을 때. */
internal fun fortuneRefreshesDoneFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "오늘은 운을 충분히 바꿨어요. 내일 새 운세가 열려요."
    UiLanguage.English -> "You've changed your luck enough for today. A new fortune opens tomorrow."
    UiLanguage.Japanese -> "今日はもう十分に運を変えました。明日また新しい運勢が開きます。"
    UiLanguage.ChineseSimplified -> "今天已经改过运了，明天会有新的运势。"
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
