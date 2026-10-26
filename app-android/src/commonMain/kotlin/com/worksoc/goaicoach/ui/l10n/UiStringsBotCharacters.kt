package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import com.worksoc.goaicoach.shared.playstyle.humanPlayStyle

/**
 * 봇 캐릭터 5종의 **표시 이름과 소개 문구**(백로그 #32, 이름 체계는 이후 도장 서열 개편으로 교체).
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
 * ## 이름을 어떻게 옮겼는가 — 도장 서열 체계
 *
 * 이름은 **직함 + 별명**이고, 서열은 괄호가 아니라 **직함 자체**로 드러난다(픽커에 더는 티어
 * 라벨을 괄호로 덧붙이지 않는다 — [UiStrings.botCharacterName]이 곧 표시 이름이다):
 * 문하생(1·2단계) → 수제자(3단계) → 사범(4단계) → 관장(5단계).
 *
 * 직함은 언어마다 뜻으로 옮기되 되도록 짧은 단어를 썼다 —
 * `Apprentice` 대신 `Pupil`, `Senior Apprentice` 대신 `Sr. Pupil`처럼 축약해 화면 폭 문제를
 * 피한다. 영어는 "별명 the 직함" 어순(`Panda the Pupil`), 한국어·일본어·중국어는 "직함 별명"
 * 어순을 쓴다.
 *
 * 별명은 **발음 그대로**가 아니라 그 언어에서 실제로 쓰는 단어로 옮긴다 — 판다(동물, 이미 각
 * 언어의 실존 단어)·꼬북(거북이 애칭)이 그렇다. 다만 `돌뫼`·`반상`·`천원` 셋은 원래 실제
 * 바둑 용어(각각 돌무더기·바둑판·바둑판 중심점)라서 서열 개편 전 번역을 그대로 재사용한다 —
 * `Cairn`(돌뫼)·`Goban`(반상)·`Tengen`(천원)은 영어 바둑 커뮤니티가 실제로 쓰는 말이고,
 * 일본어·중국어는 한자어가 그대로 대응한다(반상=盤面/盘面, 천원=天元/天元).
 *
 * ⚠️ 새 캐릭터를 카탈로그에 추가하면 **네 언어 모두** 여기에 줄을 더해야 한다.
 * `UiStringsBotCharacterTest`가 카탈로그 전 종 × 전 언어를 훑어 빠진 것을 잡는다.
 */
private val BotCharacterNames: Map<String, Map<UiLanguage, String>> = mapOf(
    "fast_beginner_1" to mapOf(
        UiLanguage.Korean to "문하생 판다",
        UiLanguage.English to "Panda the Pupil",
        UiLanguage.Japanese to "門下生 パンダ",
        UiLanguage.ChineseSimplified to "门生 熊猫",
    ),
    "fast_beginner_2" to mapOf(
        UiLanguage.Korean to "문하생 돌뫼",
        UiLanguage.English to "Cairn the Pupil",
        UiLanguage.Japanese to "門下生 石丘",
        UiLanguage.ChineseSimplified to "门生 石丘",
    ),
    "fast_beginner_3" to mapOf(
        UiLanguage.Korean to "수제자 반상",
        UiLanguage.English to "Goban the Sr. Pupil",
        UiLanguage.Japanese to "高弟 盤面",
        UiLanguage.ChineseSimplified to "高徒 盘面",
    ),
    "fast_beginner_4" to mapOf(
        UiLanguage.Korean to "사범 꼬북",
        UiLanguage.English to "Turtle the Instructor",
        UiLanguage.Japanese to "師範 カメ",
        UiLanguage.ChineseSimplified to "师父 小龟",
    ),
    "fast_beginner_5" to mapOf(
        UiLanguage.Korean to "관장 천원",
        UiLanguage.English to "Tengen the Master",
        UiLanguage.Japanese to "館長 天元",
        UiLanguage.ChineseSimplified to "馆长 天元",
    ),
)

/**
 * 캐릭터 소개 — 상대 고르기 카드에서 실력 표기([botCharacterStrengthFor]) 아래에 **작게** 붙는 한 줄(백로그 #224).
 *
 * ⚠️ **캐릭터는 그 급수의 사람처럼 둔다**(#215). 2026-10-08까지 남아 있던 옛 문구는 탐색 후보의 버킷 비율을 말하던 것이라
 * 사실이 아니게 됐다 — 관장 천원의 *"언제나 최선의 수만 둡니다"*(지금은 7단 프로필), 돌뫼의 *"절반쯤은 제대로 둡니다"*.
 * **급수 숫자를 여기 적지 않는다** — 숫자는 `HumanPlayStyle.rank`에서 읽어 따로 보인다. 여기 적으면 밸런스 패치 때 갈린다.
 * ⚠️ **1단계를 얕잡아 말하지 않는다**(2026-08-31 사용자 지시 — `UiStringsBotCharacterTest.theEntryOpponentIsNeverIntroducedAsWeak`).
 */
private val BotCharacterDescriptions: Map<String, Map<UiLanguage, String>> = mapOf(
    "fast_beginner_1" to mapOf(
        UiLanguage.Korean to "바둑을 막 익힌 사람처럼 둡니다. 첫 대국 상대로 좋아요.",
        UiLanguage.English to "Plays like someone who has just learned the game. A good first opponent.",
        UiLanguage.Japanese to "碁を覚えたての人のように打ちます。最初の相手にぴったり。",
        UiLanguage.ChineseSimplified to "像刚学会围棋的人一样下棋。很适合做第一位对手。",
    ),
    "fast_beginner_2" to mapOf(
        UiLanguage.Korean to "기본기를 갖춘 상대예요. 방심은 금물입니다.",
        UiLanguage.English to "Has the basics down. Don't let your guard down.",
        UiLanguage.Japanese to "基本は身についています。油断は禁物です。",
        UiLanguage.ChineseSimplified to "基本功已经扎实。可别大意。",
    ),
    "fast_beginner_3" to mapOf(
        UiLanguage.Korean to "유단자 문턱의 실력. 수읽기가 탄탄합니다.",
        UiLanguage.English to "On the doorstep of dan level. Reads solidly.",
        UiLanguage.Japanese to "有段者まであと一歩の実力。読みがしっかりしています。",
        UiLanguage.ChineseSimplified to "离段位只差一步的实力。算路扎实。",
    ),
    "fast_beginner_4" to mapOf(
        UiLanguage.Korean to "유단자답게 두텁게 두고, 빈틈을 정확히 파고듭니다.",
        UiLanguage.English to "Plays thick, dan-level Go and finds your gaps.",
        UiLanguage.Japanese to "有段者らしく厚く打ち、隙を正確に突きます。",
        UiLanguage.ChineseSimplified to "下得厚实，有段位的水准，专找破绽。",
    ),
    "fast_beginner_5" to mapOf(
        UiLanguage.Korean to "도장 최강. 고단자의 감각으로 판 전체를 봅니다.",
        UiLanguage.English to "The strongest in the dojo. Sees the whole board like a high dan.",
        UiLanguage.Japanese to "道場最強。高段者の感覚で盤全体を見ます。",
        UiLanguage.ChineseSimplified to "道场最强。以高段的眼光纵观全局。",
    ),
)

/**
 * 캐릭터의 **실력 표기** — 「15급 수준」(백로그 #224, 사용자 피드백 2026-10-08: *"문하생 판다 / (8~10급 수준) …"*).
 * 급수는 그 캐릭터가 흉내 내는 사람 모델 프로필에서 읽는다(`HumanPlayStyle.rank`). 프로필로 두지 않는 상대면 `null`.
 */
internal fun botCharacterStrengthFor(language: UiLanguage, character: BotCharacter): String? {
    val rank = character.toPlayLevelSetting()?.humanPlayStyle()?.rank ?: return null
    val label = kgsRankLabelFor(language, rank)
    return when (language) {
        UiLanguage.Korean -> "$label 수준"
        UiLanguage.English -> "about $label"
        UiLanguage.Japanese -> "${label}相当"
        UiLanguage.ChineseSimplified -> "约${label}水平"
    }
}

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

/**
 * 「최대 탐색 시간 제한」 아래에 붙는 안내 — **더 깊이 읽는 캐릭터**(초고수, 32방문)가 상대일 때만 보인다(백로그 #215).
 * ⚠️ 2026-10-06부터 **사람 모델을 못 쓰는 기기에서만** 보인다 — 사람 모델이 있으면 초고수는 7단 프로필로 두고 탐색하지 않아 이 말이 거짓이 된다.
 *
 * 그 캐릭터는 방문을 다 쓰는 데 시간이 든다(S23: 13줄 약 9초 · 19줄 약 15초) — 제한이 짧으면 거기서 잘려 덜 세게 둔다.
 * 느린 것은 고장이 아니라 설정이고, 그 손잡이가 바로 이 줄이라는 것을 말한다(사용자 2026-10-05: *"너무 늦은 동작은 사용자가
 * 최대 응답 시간 제한을 조정하면서 플레이하도록 가이드"*). ⚠️ 초 단위 숫자는 적지 않는다 — 기기마다 다르다.
 */
internal fun deepSearchingCharacterHintFor(language: UiLanguage): String =
    when (language) {
        UiLanguage.Korean -> "가장 센 상대는 더 깊이 읽습니다 — 이 제한이 길수록 세게 두고, 짧을수록 빨리 둡니다."
        UiLanguage.English -> "The strongest opponent reads deeper — a longer limit makes it stronger, a shorter one makes it faster."
        UiLanguage.Japanese -> "最強の相手はより深く読みます — この制限が長いほど強く、短いほど速く打ちます。"
        UiLanguage.ChineseSimplified -> "最强的对手会算得更深 — 此限制越长棋力越强，越短落子越快。"
    }
