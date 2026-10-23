package com.worksoc.goaicoach.application.dailyfortune

import kotlin.random.Random

/*
 * 실험실 「오늘의 운세」(백로그 #276, 사용자 2026-10-23) — 규칙은 이 파일 한 곳이다.
 * 운세 글은 화면 쪽 `ui/l10n/DailyFortuneCatalog.kt`에, 저장은 4계층 어댑터 `persistence/DailyFortuneStore.kt`에 있다.
 *
 * 사용자가 정한 것(U-82):
 * - **띠 · 별자리 중 하나를 고른다** — 생년월일을 받지 않아 나이를 모른다. 로그인 없이, 고른 것은 기기에만.
 * - 하루의 첫 운세는 무료, **갱신은 광고 1편**(구독자는 광고 없이) · **하루 3번**([DailyFortuneMaxRefreshes]).
 * - **점수 · 등급은 없다** — 갱신은 「다른 운세는 무엇이 나올까」 궁금해 돌려 보는 것이다.
 * - 운세 글은 일반 · 별자리 · 바둑 결 세 갈래.
 *
 * 스레드가 정한 것:
 * - 띠를 고르면 **일반 운세**, 별자리를 고르면 **별자리 운세** — 그리고 누구에게나 **바둑 운세**와 **행운의 자리**(천원 · 화점 …).
 * - **같은 날 · 같은 띠(별자리) · 같은 갱신 차례면 누구나 같은 운세다** — 신문 운세처럼 친구와 견줄 수 있다. 씨앗은 날 · 고른 것 · 갈래로만 정해진다.
 * - 갱신하면 **늘 다른 운세**가 나온다 — 하루의 네 칸(첫 운세 + 갱신 셋)이 한 번 섞은 차례의 서로 다른 자리다.
 *   차례는 운세 수만큼의 칸을 다 쓰면 새로 섞는다 — 그래서 갈래마다 운세 수가 **4의 배수**여야 하루의 네 칸이 한 차례 안에 든다(`DailyFortuneCatalogTest`).
 *   첫 운세만 보는 사람도 (운세 수 ÷ 4)일 동안은 같은 글을 다시 보지 않는다.
 * - 띠 · 별자리를 바꿔도 그날 쓴 갱신 횟수는 그대로다(하루 3번은 고른 것마다가 아니라 사람마다).
 * - 하루는 출석의 UTC 하루(`application.attendance.utcDayIndex` — 명언과 같다)라 한국에서는 오전 9시에 바뀐다.
 *   저장된 날과 오늘이 다르면 앞이든 뒤든 새 날로 본다 — 시계를 되감은 사람을 막지 않는다(1일 1회 뽑기와 같은 결, 함정 12 · `FEATURE_ACCESS_PRINCIPLES.md` §8.8).
 */

/** 하루에 광고(구독자는 광고 없이)로 갱신할 수 있는 횟수(사용자 2026-10-23). */
const val DailyFortuneMaxRefreshes: Int = 3

/** 하루의 칸 수 — 첫 운세 하나 + 갱신 [DailyFortuneMaxRefreshes]. 갈래마다 운세 수는 이것의 배수여야 한다. */
const val DailyFortuneSlotsPerDay: Int = 1 + DailyFortuneMaxRefreshes

/** 띠 열둘(쥐띠부터). ⚠️ [key]는 저장값이고 씨앗이다 — 바꾸면 고른 띠가 풀리고 그날의 운세가 바뀐다(함정 1과 같은 결). */
enum class ZodiacAnimal(val key: String) {
    Rat("rat"),
    Ox("ox"),
    Tiger("tiger"),
    Rabbit("rabbit"),
    Dragon("dragon"),
    Snake("snake"),
    Horse("horse"),
    Goat("goat"),
    Monkey("monkey"),
    Rooster("rooster"),
    Dog("dog"),
    Pig("pig"),
}

/**
 * 별자리 열둘(양자리부터) — 고를 때 보여 주는 날짜(월 · 일)를 함께 싣는다. 해마다 경계가 하루쯤 움직여 출처마다 다르다 — 널리 쓰이는 표를 따랐다(스레드가 정한 것).
 * ⚠️ [key]는 저장값이고 씨앗이다.
 */
enum class StarSign(val key: String, val fromMonth: Int, val fromDay: Int, val toMonth: Int, val toDay: Int) {
    Aries("aries", 3, 21, 4, 19),
    Taurus("taurus", 4, 20, 5, 20),
    Gemini("gemini", 5, 21, 6, 20),
    Cancer("cancer", 6, 21, 7, 22),
    Leo("leo", 7, 23, 8, 22),
    Virgo("virgo", 8, 23, 9, 22),
    Libra("libra", 9, 23, 10, 22),
    Scorpio("scorpio", 10, 23, 11, 21),
    Sagittarius("sagittarius", 11, 22, 12, 21),
    Capricorn("capricorn", 12, 22, 1, 19),
    Aquarius("aquarius", 1, 20, 2, 18),
    Pisces("pisces", 2, 19, 3, 20),
}

/** 고른 것 — 띠 하나 또는 별자리 하나. [key]가 저장값이다(`zodiac:dragon` · `star:leo`). */
sealed interface FortuneSign {
    val key: String

    data class Zodiac(val animal: ZodiacAnimal) : FortuneSign {
        override val key: String get() = ZodiacKeyPrefix + animal.key
    }

    data class Star(val sign: StarSign) : FortuneSign {
        override val key: String get() = StarKeyPrefix + sign.key
    }

    companion object {
        /** 저장값을 되읽는다 — 모르는 값이면 `null`(아직 고르지 않은 것으로 본다). */
        fun fromKey(key: String?): FortuneSign? = when {
            key == null -> null
            key.startsWith(ZodiacKeyPrefix) ->
                ZodiacAnimal.entries.firstOrNull { it.key == key.removePrefix(ZodiacKeyPrefix) }?.let(::Zodiac)
            key.startsWith(StarKeyPrefix) ->
                StarSign.entries.firstOrNull { it.key == key.removePrefix(StarKeyPrefix) }?.let(::Star)
            else -> null
        }

        /** 고를 수 있는 전부 — 띠 열둘 다음 별자리 열둘. */
        val all: List<FortuneSign> = ZodiacAnimal.entries.map(::Zodiac) + StarSign.entries.map(::Star)
    }
}

private const val ZodiacKeyPrefix = "zodiac:"
private const val StarKeyPrefix = "star:"

/** 운세의 갈래. ⚠️ [key]는 씨앗에 들어간다 — 바꾸면 모두의 그날 운세가 바뀐다. */
enum class FortuneStrand(val key: String) {
    /** 일반 운세 — 띠를 고른 사람의 운세. */
    General("general"),

    /** 별자리 운세 — 별자리를 고른 사람의 운세. */
    Star("star"),

    /** 바둑 운세 — 누구에게나. */
    Go("go"),

    /** 행운의 자리(천원 · 화점 …) — 누구에게나. */
    LuckyPoint("lucky-point"),
}

/** 갈래마다 실린 운세 수 — 화면 쪽 카탈로그가 넘긴다(글은 shared가 모른다). */
data class FortunePoolSizes(val general: Int, val star: Int, val go: Int, val luckyPoint: Int) {
    fun of(strand: FortuneStrand): Int = when (strand) {
        FortuneStrand.General -> general
        FortuneStrand.Star -> star
        FortuneStrand.Go -> go
        FortuneStrand.LuckyPoint -> luckyPoint
    }
}

/** 오늘 보일 운세 — 갈래마다 카탈로그의 몇 번째 글인가. [mainStrand]는 띠면 일반, 별자리면 별자리. */
data class DailyFortuneReading(
    val mainStrand: FortuneStrand,
    val mainIndex: Int,
    val goIndex: Int,
    val luckyPointIndex: Int,
)

/**
 * 운세의 진행도 — 고른 것과, 그날 쓴 갱신 횟수.
 *
 * @property signKey 고른 띠 · 별자리([FortuneSign.key]). 아직 고르지 않았으면 `null`.
 * @property day 갱신 횟수를 센 날(UTC 하루 번호). 한 번도 갱신하지 않았으면 `null`.
 * @property refreshesUsed [day]에 쓴 갱신 횟수.
 */
data class DailyFortuneProgress(
    val signKey: String? = null,
    val day: Long? = null,
    val refreshesUsed: Int = 0,
) {
    val sign: FortuneSign? get() = FortuneSign.fromKey(signKey)

    /** 오늘([today]) 쓴 갱신 횟수 — 저장된 날이 오늘이 아니면 0. */
    fun refreshesUsedOn(today: Long): Int =
        if (day == today) refreshesUsed.coerceIn(0, DailyFortuneMaxRefreshes) else 0

    /** 오늘 남은 갱신 횟수. */
    fun refreshesLeftOn(today: Long): Int = DailyFortuneMaxRefreshes - refreshesUsedOn(today)

    /** 띠 · 별자리를 고르거나 바꾼다 — 그날 쓴 갱신 횟수는 그대로다. */
    fun withSign(sign: FortuneSign): DailyFortuneProgress = copy(signKey = sign.key)
}

/**
 * 한 번 갱신한다 — 고른 것이 없거나 오늘 남은 횟수가 없으면 `null`(아무것도 바꾸지 않는다).
 * 광고는 호출부가 먼저 보인다(광고를 본 뒤에 부른다 — 광고가 실패하면 부르지 않는다).
 */
fun refreshDailyFortune(progress: DailyFortuneProgress, today: Long): DailyFortuneProgress? {
    if (progress.sign == null || progress.refreshesLeftOn(today) <= 0) return null
    return progress.copy(day = today, refreshesUsed = progress.refreshesUsedOn(today) + 1)
}

/** 오늘([today]) [sign]의 운세 — [refreshesUsed]번 갱신한 뒤의 것. 같은 값을 넣으면 어느 기기에서나 같은 답이다. */
fun dailyFortuneReading(sign: FortuneSign, today: Long, refreshesUsed: Int, sizes: FortunePoolSizes): DailyFortuneReading {
    val slot = today * DailyFortuneSlotsPerDay + refreshesUsed.coerceIn(0, DailyFortuneMaxRefreshes)
    val main = when (sign) {
        is FortuneSign.Zodiac -> FortuneStrand.General
        is FortuneSign.Star -> FortuneStrand.Star
    }
    return DailyFortuneReading(
        mainStrand = main,
        mainIndex = fortuneIndexAt(sign.key, main, slot, sizes.of(main)),
        goIndex = fortuneIndexAt(sign.key, FortuneStrand.Go, slot, sizes.go),
        luckyPointIndex = fortuneIndexAt(sign.key, FortuneStrand.LuckyPoint, slot, sizes.luckyPoint),
    )
}

/**
 * [slot]번째 칸의 글 번호 — 칸을 운세 수([poolSize])씩 묶어 한 묶음마다 한 번 섞은 차례를 쓴다(한 묶음 안에서는 겹치지 않는다).
 * 섞는 씨앗은 고른 것 · 갈래 · 묶음 번호의 FNV-1a — 플랫폼마다 다를 수 있는 `hashCode`를 쓰지 않는다. `Random(seed)`와 `shuffled`는 공통 구현이다.
 */
internal fun fortuneIndexAt(signKey: String, strand: FortuneStrand, slot: Long, poolSize: Int): Int {
    require(poolSize > 0) { "운세가 하나도 없는 갈래: ${strand.key}" }
    val size = poolSize.toLong()
    val cycle = slot.floorDiv(size)
    val position = slot.mod(size).toInt()
    val order = (0 until poolSize).shuffled(Random(fnv1a64("$signKey|${strand.key}|$cycle")))
    return order[position]
}

private fun fnv1a64(text: String): Long {
    var hash = -0x340d631b7bdddcdbL // 14695981039346656037(FNV offset basis)
    for (char in text) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L // FNV prime
    }
    return hash
}
