package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterId

/**
 * 봇 캐릭터 5종의 **표시 이름과 소개 문구**(백로그 #32).
 *
 * ## 왜 도메인이 아니라 여기 있는가
 *
 * 원래는 `BotCharacterCatalog`가 `name`·`description`을 한국어 리터럴로 들고 있었고, `UiStrings`가
 * 그 값을 4개 언어 문장에 그대로 끼워 넣었다. 그래서 **영어·일본어·중국어 사용자도 캐릭터
 * 이름만은 한글로 봤다** — 일본어 대국 설정에서 `관장 천원 (達人)`처럼 한 줄 안에 두 언어가
 * 섞였다(티어명은 이미 다국어였다).
 *
 * ⚠️ **번역을 카탈로그로 되돌리지 말 것.** 그쪽은 `shared`의 도메인이고 [UiLanguage]는
 * `app-android`의 UI 개념이라 계층이 뒤집힌다. 지금은 도메인이 [BotCharacterId]와 획득 경로만
 * 들고, 사람이 읽는 글자는 전부 이 파일에 있다 — **같은 사실을 두 곳에 적지 않는다**는 카탈로그
 * 자신의 원칙과도 맞는다.
 *
 * ## 이름을 어떻게 옮겼는가
 *
 * 한국어 이름은 **직함 + 바둑 용어**로 되어 있다(도장생 `반상`, 사범 `묘수`, 관장 `천원`).
 * 기계적으로 음역하면 그 말맛이 통째로 사라지므로 언어마다 다시 지었고, 두 가지를 지켰다:
 * **직함이 서열을 드러낼 것**(입문 → 연습 → 도장생 → 사범 → 관장), **이름이 그 언어권에서
 * 실제로 쓰는 바둑 용어일 것**.
 *
 * - 일본어·중국어는 한자어가 거의 그대로 대응한다(묘수=妙手/妙手, 천원=天元/天元, 반상=盤面/盘面).
 * - 영어는 대응 한자가 없어 영어권 바둑 어휘를 골랐다 — `Tesuji`(묘수), `Tengen`(천원),
 *   `Goban`(반상)은 영어 바둑 커뮤니티에서 그대로 쓰는 말이다. 2단계 `돌뫼`(돌+뫼, 돌무더기)는
 *   돌을 쌓아 만든 표식을 뜻하는 `Cairn`으로 옮겨 원뜻이 살아 있다.
 *
 * ⚠️ 새 캐릭터를 카탈로그에 추가하면 **네 언어 모두** 여기에 줄을 더해야 한다.
 * `UiStringsBotCharacterTest`가 카탈로그 전 종 × 전 언어를 훑어 빠진 것을 잡는다.
 */
private val BotCharacterNames: Map<String, Map<UiLanguage, String>> = mapOf(
    "fast_beginner_1" to mapOf(
        UiLanguage.Korean to "첫돌이",
        UiLanguage.English to "Pebble",
        UiLanguage.Japanese to "初石",
        UiLanguage.ChineseSimplified to "初子",
    ),
    "fast_beginner_2" to mapOf(
        UiLanguage.Korean to "연습생 돌뫼",
        UiLanguage.English to "Cairn the Trainee",
        UiLanguage.Japanese to "練習生 石丘",
        UiLanguage.ChineseSimplified to "练习生 石丘",
    ),
    "fast_beginner_3" to mapOf(
        UiLanguage.Korean to "도장생 반상",
        UiLanguage.English to "Goban the Student",
        UiLanguage.Japanese to "道場生 盤面",
        UiLanguage.ChineseSimplified to "道场生 盘面",
    ),
    "fast_beginner_4" to mapOf(
        UiLanguage.Korean to "사범 묘수",
        UiLanguage.English to "Tesuji the Instructor",
        UiLanguage.Japanese to "師範 妙手",
        UiLanguage.ChineseSimplified to "师父 妙手",
    ),
    "fast_beginner_5" to mapOf(
        UiLanguage.Korean to "관장 천원",
        UiLanguage.English to "Tengen the Master",
        UiLanguage.Japanese to "館長 天元",
        UiLanguage.ChineseSimplified to "馆长 天元",
    ),
)

private val BotCharacterDescriptions: Map<String, Map<UiLanguage, String>> = mapOf(
    // ⚠️ **1단계를 약한 상대로 소개하지 않는다(2026-08-31 사용자 지시).** 실제 기력이 일반
    // 중급자를 상회해서, 애초에 그래서 호선이 아니라 접바둑 기능을 넣었다 — "일부러 자주
    // 실수한다"고 적어 두면 첫 판에서 진 사용자가 속았다고 느낀다. 그래서 "최선의 수는 아니지만
    // 만만치 않다"로 틀을 바꿨고, 5단계의 "언제나 최선의 수만 둡니다"와 대비를 이룬다.
    // 랜딩(#51)이 세운 "상대를 얕잡아 말하지 않는다" 원칙과 같은 축이다.
    "fast_beginner_1" to mapOf(
        UiLanguage.Korean to "최선의 수를 두지는 못하지만, 결코 만만치 않은 상대예요.",
        UiLanguage.English to "Doesn't always find the best move, but a capable opponent.",
        UiLanguage.Japanese to "最善手とはいきませんが、決して侮れない相手です。",
        UiLanguage.ChineseSimplified to "未必能下出最佳一手，但实力不容小觑。",
    ),
    "fast_beginner_2" to mapOf(
        UiLanguage.Korean to "기본기를 익히는 중. 절반쯤은 제대로 둡니다.",
        UiLanguage.English to "Still learning the basics. Gets it right about half the time.",
        UiLanguage.Japanese to "基本を習得中。半分くらいはきちんと打ちます。",
        UiLanguage.ChineseSimplified to "正在打基础。大概有一半下得像样。",
    ),
    "fast_beginner_3" to mapOf(
        UiLanguage.Korean to "웬만한 수는 받아칩니다. 방심하면 한 방 먹어요.",
        UiLanguage.English to "Answers most moves soundly. Drop your guard and you'll pay.",
        UiLanguage.Japanese to "たいていの手には応じます。油断すると一発くらいます。",
        UiLanguage.ChineseSimplified to "一般的手都能应对。一旦大意就会挨一下。",
    ),
    "fast_beginner_4" to mapOf(
        UiLanguage.Korean to "수를 읽고 빈틈을 파고듭니다. 실수는 놓치지 않아요.",
        UiLanguage.English to "Reads ahead and finds the gaps. Won't miss your mistakes.",
        UiLanguage.Japanese to "先を読んで隙を突きます。ミスは見逃しません。",
        UiLanguage.ChineseSimplified to "算路清晰，专找破绽。你的失误逃不掉。",
    ),
    "fast_beginner_5" to mapOf(
        UiLanguage.Korean to "도장 최강. 언제나 최선의 수만 둡니다.",
        UiLanguage.English to "The strongest in the dojo. Plays only the best move.",
        UiLanguage.Japanese to "道場最強。常に最善手だけを打ちます。",
        UiLanguage.ChineseSimplified to "道场最强。永远只下最佳一手。",
    ),
)

/**
 * 표에 없는 id일 때 [BotCharacterId.raw]를 그대로 돌려준다.
 *
 * ⚠️ **비어 있는 문자열이나 예외가 아니라 id를 준다.** 이 경로는 카탈로그에 캐릭터를 더하고
 * 표를 빠뜨렸을 때, 또는 상위 버전에서 저장된 id가 남아 있을 때 닿는다 — 화면에 `fast_beginner_6`
 * 처럼 보이면 눈에 띄어 바로 고칠 수 있지만, 빈칸이면 조용히 지나간다.
 */
private fun lookup(
    table: Map<String, Map<UiLanguage, String>>,
    language: UiLanguage,
    id: BotCharacterId,
): String = table[id.raw]?.get(language) ?: id.raw

internal fun botCharacterNameFor(language: UiLanguage, id: BotCharacterId): String =
    lookup(BotCharacterNames, language, id)

internal fun botCharacterDescriptionFor(language: UiLanguage, id: BotCharacterId): String =
    lookup(BotCharacterDescriptions, language, id)
