package com.worksoc.goaicoach.ui.l10n

/**
 * 「기보 보기」(백로그 #269) 화면의 문구 · 앱에 싣는 기보의 묶음 이름 · 대국자 이름 · 결과 표기.
 *
 * [UiStrings] 생성자는 여유가 없어(함정 61) 곁표 + 함수로 둔다. ⚠️ 리플렉션 그물 밖이라(함정 10) 네 언어가 다 찼는지는
 * `GameRecordCatalogContractTest`가 손으로 본다. 묶음 enum이 여기 있는 것은 [StudyCategory]와 같은 이유다 — l10n(L0)은 위층 타입을 볼 수 없다.
 *
 * 화면 이름은 언어마다 그 말에서 「기보를 한 수씩 놓아 본다」는 말을 골랐다(스레드가 정한 표기): 일본어 棋譜並べ · 중국어 打谱.
 * 대국자 이름은 그 나라에서 흔히 쓰는 표기를 골랐다(스레드) — 기보의 영어 표기(SGF `PB`·`PW`)가 열쇠다.
 */
private val Titles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "기보 보기",
    UiLanguage.English to "Game records",
    UiLanguage.Japanese to "棋譜並べ",
    UiLanguage.ChineseSimplified to "打谱",
)

private val Intros: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "명국을 한 수씩 놓아 봅니다. 가지고 있는 SGF 기보 파일도 열 수 있어요.",
    UiLanguage.English to "Replay famous games move by move. You can also open an SGF file you have.",
    UiLanguage.Japanese to "名局を一手ずつ並べてみましょう。お持ちのSGF棋譜ファイルも開けます。",
    UiLanguage.ChineseSimplified to "一手一手地打谱名局。也可以打开你自己的SGF棋谱文件。",
)

private val OpenFileLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "SGF 파일 열기",
    UiLanguage.English to "Open an SGF file",
    UiLanguage.Japanese to "SGFファイルを開く",
    UiLanguage.ChineseSimplified to "打开SGF文件",
)

/** 연 파일에 대회·대국 이름이 없을 때의 제목. */
private val UntitledRecordTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "SGF 기보",
    UiLanguage.English to "SGF record",
    UiLanguage.Japanese to "SGF棋譜",
    UiLanguage.ChineseSimplified to "SGF棋谱",
)

/** 파일을 열거나 읽는 데 실패했을 때 — 무엇이 문제인지에 따라 다르게 말한다. */
internal enum class GameRecordOpenFailure { CannotOpen, NotSgf, NotGo, UnsupportedBoardSize, SetupAfterStart, BadPoint }

private val OpenFailureMessages: Map<GameRecordOpenFailure, Map<UiLanguage, String>> = mapOf(
    GameRecordOpenFailure.CannotOpen to mapOf(
        UiLanguage.Korean to "파일을 열 수 없어요.",
        UiLanguage.English to "Couldn't open the file.",
        UiLanguage.Japanese to "ファイルを開けませんでした。",
        UiLanguage.ChineseSimplified to "无法打开文件。",
    ),
    GameRecordOpenFailure.NotSgf to mapOf(
        UiLanguage.Korean to "SGF 기보 파일이 아니에요.",
        UiLanguage.English to "This isn't an SGF game record.",
        UiLanguage.Japanese to "SGF形式の棋譜ではありません。",
        UiLanguage.ChineseSimplified to "这不是SGF棋谱文件。",
    ),
    GameRecordOpenFailure.NotGo to mapOf(
        UiLanguage.Korean to "바둑 기보가 아니에요.",
        UiLanguage.English to "This SGF isn't a Go game.",
        UiLanguage.Japanese to "囲碁の棋譜ではありません。",
        UiLanguage.ChineseSimplified to "这不是围棋棋谱。",
    ),
    GameRecordOpenFailure.UnsupportedBoardSize to mapOf(
        UiLanguage.Korean to "9·13·19줄 판의 기보만 열 수 있어요.",
        UiLanguage.English to "Only 9×9, 13×13 and 19×19 games can be opened.",
        UiLanguage.Japanese to "9路・13路・19路の棋譜だけ開けます。",
        UiLanguage.ChineseSimplified to "只能打开9路、13路、19路的棋谱。",
    ),
    GameRecordOpenFailure.SetupAfterStart to mapOf(
        UiLanguage.Korean to "수순 중간에 돌을 놓는 기보는 아직 열 수 없어요.",
        UiLanguage.English to "Records that add stones mid-game can't be opened yet.",
        UiLanguage.Japanese to "途中で石を置く棋譜はまだ開けません。",
        UiLanguage.ChineseSimplified to "暂不支持中途摆子的棋谱。",
    ),
    GameRecordOpenFailure.BadPoint to mapOf(
        UiLanguage.Korean to "기보에 읽을 수 없는 수가 있어요.",
        UiLanguage.English to "The record has a move that can't be read.",
        UiLanguage.Japanese to "棋譜に読めない手があります。",
        UiLanguage.ChineseSimplified to "棋谱中有无法读取的着手。",
    ),
)

/**
 * 앱에 싣는 기보의 묶음 — **선언 순서가 목록의 순서다**(연도순 — 스레드가 정한 차례). 묶음 안의 판은 「N국」으로 센다.
 * 실을 판은 사용자가 정했다(2026-10-19 — 33판 모두). 빼는 판은 에셋 파일과 `BundledGameRecords`의 줄을 함께 지운다.
 */
internal enum class GameRecordGroup {
    DosakuTengen,
    DosakuMasterpiece,
    BloodVomiting,
    EarReddening,
    ShusakuCastle,
    NewFuseki,
    ShusaiRetirement,
    IngCup1,
    IngCup4,
    LgCup5,
    LgCup7,
    SamsungCup17,
    AlphaGoLeeSedol,
}

private val GroupTitles: Map<GameRecordGroup, Map<UiLanguage, String>> = mapOf(
    GameRecordGroup.DosakuTengen to mapOf(
        UiLanguage.Korean to "도사쿠, 천원 첫 수 (1670)",
        UiLanguage.English to "Dosaku: the first move on tengen (1670)",
        UiLanguage.Japanese to "道策 初手天元の一局 (1670)",
        UiLanguage.ChineseSimplified to "道策 首着天元之局 (1670)",
    ),
    GameRecordGroup.DosakuMasterpiece to mapOf(
        UiLanguage.Korean to "도사쿠 평생의 명국 (1684)",
        UiLanguage.English to "Dosaku's lifetime masterpiece (1684)",
        UiLanguage.Japanese to "道策 生涯の名局 (1684)",
        UiLanguage.ChineseSimplified to "道策 生涯名局 (1684)",
    ),
    GameRecordGroup.BloodVomiting to mapOf(
        UiLanguage.Korean to "토혈의 대국 (1835)",
        UiLanguage.English to "The blood-vomiting game (1835)",
        UiLanguage.Japanese to "吐血の局 (1835)",
        UiLanguage.ChineseSimplified to "吐血之局 (1835)",
    ),
    GameRecordGroup.EarReddening to mapOf(
        UiLanguage.Korean to "이적(耳赤)의 묘수 (1846)",
        UiLanguage.English to "The ear-reddening move (1846)",
        UiLanguage.Japanese to "耳赤の一局 (1846)",
        UiLanguage.ChineseSimplified to "耳赤之局 (1846)",
    ),
    GameRecordGroup.ShusakuCastle to mapOf(
        UiLanguage.Korean to "슈사쿠 오시로고 첫 판 (1849)",
        UiLanguage.English to "Shusaku's first castle game (1849)",
        UiLanguage.Japanese to "秀策 御城碁 第一局 (1849)",
        UiLanguage.ChineseSimplified to "秀策 御城棋 第一局 (1849)",
    ),
    GameRecordGroup.NewFuseki to mapOf(
        UiLanguage.Korean to "신포석 대국 (1933)",
        UiLanguage.English to "The New Fuseki game (1933)",
        UiLanguage.Japanese to "新布石の一局 (1933)",
        UiLanguage.ChineseSimplified to "新布局之局 (1933)",
    ),
    GameRecordGroup.ShusaiRetirement to mapOf(
        UiLanguage.Korean to "슈사이 명인 은퇴기 (1938)",
        UiLanguage.English to "Shusai's retirement game (1938)",
        UiLanguage.Japanese to "本因坊秀哉名人引退碁 (1938)",
        UiLanguage.ChineseSimplified to "本因坊秀哉名人引退棋 (1938)",
    ),
    GameRecordGroup.IngCup1 to mapOf(
        UiLanguage.Korean to "제1회 잉씨배 결승 (1989)",
        UiLanguage.English to "1st Ing Cup final (1989)",
        UiLanguage.Japanese to "第1回応氏杯決勝 (1989)",
        UiLanguage.ChineseSimplified to "第1届应氏杯决赛 (1989)",
    ),
    GameRecordGroup.IngCup4 to mapOf(
        UiLanguage.Korean to "제4회 잉씨배 결승 (2000~2001)",
        UiLanguage.English to "4th Ing Cup final (2000–2001)",
        UiLanguage.Japanese to "第4回応氏杯決勝 (2000~2001)",
        UiLanguage.ChineseSimplified to "第4届应氏杯决赛 (2000~2001)",
    ),
    GameRecordGroup.LgCup5 to mapOf(
        UiLanguage.Korean to "제5회 LG배 결승 (2001)",
        UiLanguage.English to "5th LG Cup final (2001)",
        UiLanguage.Japanese to "第5回LG杯決勝 (2001)",
        UiLanguage.ChineseSimplified to "第5届LG杯决赛 (2001)",
    ),
    GameRecordGroup.LgCup7 to mapOf(
        UiLanguage.Korean to "제7회 LG배 결승 (2003)",
        UiLanguage.English to "7th LG Cup final (2003)",
        UiLanguage.Japanese to "第7回LG杯決勝 (2003)",
        UiLanguage.ChineseSimplified to "第7届LG杯决赛 (2003)",
    ),
    GameRecordGroup.SamsungCup17 to mapOf(
        UiLanguage.Korean to "제17회 삼성화재배 결승 (2012)",
        UiLanguage.English to "17th Samsung Cup final (2012)",
        UiLanguage.Japanese to "第17回三星火災杯決勝 (2012)",
        UiLanguage.ChineseSimplified to "第17届三星杯决赛 (2012)",
    ),
    GameRecordGroup.AlphaGoLeeSedol to mapOf(
        UiLanguage.Korean to "알파고 대 이세돌 (2016)",
        UiLanguage.English to "AlphaGo vs Lee Sedol (2016)",
        UiLanguage.Japanese to "AlphaGo対李世乭 (2016)",
        UiLanguage.ChineseSimplified to "AlphaGo对李世石 (2016)",
    ),
)

/** 기보의 영어 표기(SGF `PB`·`PW`) → 네 언어. 없는 이름은 기보에 적힌 그대로 보인다. */
private val PlayerNames: Map<String, Map<UiLanguage, String>> = listOf(
    listOf("Honinbo Dosaku", "혼인보 도사쿠", "本因坊道策", "本因坊道策"),
    listOf("Yasui Santetsu II", "야스이 산테쓰", "安井算哲", "安井算哲"),
    listOf("Yasui Shunchi", "야스이 슌치", "安井春知", "安井春知"),
    listOf("Honinbo Jowa", "혼인보 조와", "本因坊丈和", "本因坊丈和"),
    listOf("Akaboshi Intetsu", "아카보시 인테츠", "赤星因徹", "赤星因彻"),
    listOf("Yasuda Shusaku", "야스다 슈사쿠", "安田秀策", "安田秀策"),
    listOf("Honinbo Shusaku", "혼인보 슈사쿠", "本因坊秀策", "本因坊秀策"),
    listOf("Inoue Gennan Inseki", "이노우에 겐난 인세키", "井上幻庵因碩", "井上幻庵因硕"),
    listOf("Yasui Sanchi IX", "야스이 산치", "安井算知", "安井算知"),
    listOf("Go Seigen", "오청원", "呉清源", "吴清源"),
    listOf("Honinbo Shusai", "혼인보 슈사이", "本因坊秀哉", "本因坊秀哉"),
    listOf("Kitani Minoru", "기타니 미노루", "木谷實", "木谷实"),
    listOf("Cho Hunhyun", "조훈현", "曺薫鉉", "曹薰铉"),
    listOf("Nie Weiping", "녜웨이핑", "聶衛平", "聂卫平"),
    listOf("Lee Changho", "이창호", "李昌鎬", "李昌镐"),
    listOf("Chang Hao", "창하오", "常昊", "常昊"),
    listOf("Lee Sedol", "이세돌", "李世乭", "李世石"),
    listOf("Gu Li", "구리", "古力", "古力"),
    listOf("AlphaGo", "알파고", "AlphaGo", "AlphaGo"),
).associate { (english, korean, japanese, chinese) ->
    english to mapOf(
        UiLanguage.Korean to korean,
        UiLanguage.English to english,
        UiLanguage.Japanese to japanese,
        UiLanguage.ChineseSimplified to chinese,
    )
}

private val BlackLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "흑",
    UiLanguage.English to "Black",
    UiLanguage.Japanese to "黒",
    UiLanguage.ChineseSimplified to "黑",
)

private val WhiteLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "백",
    UiLanguage.English to "White",
    UiLanguage.Japanese to "白",
    UiLanguage.ChineseSimplified to "白",
)

/** 「자동 진행」 — 정한 간격마다 한 수씩 넘긴다(#269, 사용자 2026-10-19). 진행 중에는 같은 버튼이 「멈춤」이 된다. */
private val AutoPlayLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "▶ 자동 진행",
    UiLanguage.English to "▶ Auto play",
    UiLanguage.Japanese to "▶ 自動再生",
    UiLanguage.ChineseSimplified to "▶ 自动播放",
)

private val PauseLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "❚❚ 멈춤",
    UiLanguage.English to "❚❚ Pause",
    UiLanguage.Japanese to "❚❚ 一時停止",
    UiLanguage.ChineseSimplified to "❚❚ 暂停",
)

internal fun gameRecordsTitleFor(language: UiLanguage): String = Titles.getValue(language)

internal fun gameRecordAutoPlayFor(language: UiLanguage): String = AutoPlayLabels.getValue(language)

internal fun gameRecordPauseFor(language: UiLanguage): String = PauseLabels.getValue(language)

/** 착수 간격 — 「1초」 · 「1s」 · 「1秒」. */
internal fun gameRecordSecondsFor(language: UiLanguage, seconds: Int): String = when (language) {
    UiLanguage.Korean -> "${seconds}초"
    UiLanguage.English -> "${seconds}s"
    UiLanguage.Japanese, UiLanguage.ChineseSimplified -> "${seconds}秒"
}

internal fun gameRecordsIntroFor(language: UiLanguage): String = Intros.getValue(language)

internal fun gameRecordsOpenFileFor(language: UiLanguage): String = OpenFileLabels.getValue(language)

internal fun gameRecordUntitledFor(language: UiLanguage): String = UntitledRecordTitles.getValue(language)

internal fun gameRecordOpenFailureFor(language: UiLanguage, failure: GameRecordOpenFailure): String =
    OpenFailureMessages.getValue(failure).getValue(language)

internal fun gameRecordGroupTitleFor(language: UiLanguage, group: GameRecordGroup): String = GroupTitles.getValue(group).getValue(language)

/** 묶음 안의 N번째 판 — 「1국」 · 「Game 1」 · 「第1局」. */
internal fun gameRecordGameNumberFor(language: UiLanguage, number: Int): String = when (language) {
    UiLanguage.Korean -> "${number}국"
    UiLanguage.English -> "Game $number"
    UiLanguage.Japanese, UiLanguage.ChineseSimplified -> "第${number}局"
}

/** 대국자 이름 — 표가 아는 이름이면 그 언어로, 모르면 기보에 적힌 그대로. */
internal fun gameRecordPlayerNameFor(language: UiLanguage, name: String): String = PlayerNames[name]?.getValue(language) ?: name

/** 「흑 이창호 · 백 이세돌」 — 이름이 없는 쪽은 색만. */
internal fun gameRecordPlayersFor(language: UiLanguage, black: String?, white: String?): String {
    val blackPart = listOfNotNull(BlackLabels.getValue(language), black?.let { gameRecordPlayerNameFor(language, it) }).joinToString(" ")
    val whitePart = listOfNotNull(WhiteLabels.getValue(language), white?.let { gameRecordPlayerNameFor(language, it) }).joinToString(" ")
    return "$blackPart · $whitePart"
}

/**
 * SGF의 결과(`RE`)를 그 언어의 말로 — `B+R` 흑 불계승 · `W+2` 백 2집승 · `W+0.5` 백 반집승 · `B+7.5` 흑 7집 반 승 · `0`/`Draw` 무승부 ·
 * `+T` 시간승 · `+F` 기권승(부전승). 모르는 꼴은 null — 화면이 원문을 그대로 보인다.
 */
internal fun gameRecordResultFor(language: UiLanguage, result: String?): String? {
    val text = result?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text == "0" || text.equals("Draw", ignoreCase = true) || text.equals("Jigo", ignoreCase = true)) {
        return when (language) {
            UiLanguage.Korean -> "무승부"
            UiLanguage.English -> "Draw"
            UiLanguage.Japanese -> "持碁"
            UiLanguage.ChineseSimplified -> "和棋"
        }
    }
    val winner = when (text.firstOrNull()?.uppercaseChar()) {
        'B' -> BlackLabels.getValue(language)
        'W' -> WhiteLabels.getValue(language)
        else -> return null
    }
    if (text.getOrNull(1) != '+') return null
    val how = text.substring(2).trim()
    val margin = how.toDoubleOrNull()
    return when {
        how.isEmpty() -> when (language) {
            UiLanguage.Korean -> "$winner 승"
            UiLanguage.English -> "$winner wins"
            UiLanguage.Japanese -> "${winner}勝ち"
            UiLanguage.ChineseSimplified -> "${winner}胜"
        }
        how.startsWith("R", ignoreCase = true) -> when (language) {
            UiLanguage.Korean -> "$winner 불계승"
            UiLanguage.English -> "$winner wins by resignation"
            UiLanguage.Japanese -> "${winner}中押し勝ち"
            UiLanguage.ChineseSimplified -> "${winner}中盘胜"
        }
        how.startsWith("T", ignoreCase = true) -> when (language) {
            UiLanguage.Korean -> "$winner 시간승"
            UiLanguage.English -> "$winner wins on time"
            UiLanguage.Japanese -> "${winner}時間勝ち"
            UiLanguage.ChineseSimplified -> "${winner}超时胜"
        }
        how.startsWith("F", ignoreCase = true) -> when (language) {
            UiLanguage.Korean -> "$winner 기권승"
            UiLanguage.English -> "$winner wins by forfeit"
            UiLanguage.Japanese -> "${winner}不戦勝"
            UiLanguage.ChineseSimplified -> "${winner}不战胜"
        }
        margin != null -> marginResultFor(language, winner, margin)
        else -> null
    }
}

private fun marginResultFor(language: UiLanguage, winner: String, margin: Double): String {
    val whole = margin.toInt()
    val hasHalf = margin - whole >= 0.5
    return when (language) {
        UiLanguage.Korean -> when {
            whole == 0 && hasHalf -> "$winner 반집승"
            hasHalf -> "$winner ${whole}집 반 승"
            else -> "$winner ${whole}집승"
        }
        UiLanguage.English -> "$winner wins by ${if (hasHalf) "$whole.5" else "$whole"} points"
        UiLanguage.Japanese -> when {
            whole == 0 && hasHalf -> "${winner}半目勝ち"
            hasHalf -> "${winner}${whole}目半勝ち"
            else -> "${winner}${whole}目勝ち"
        }
        UiLanguage.ChineseSimplified -> when {
            whole == 0 && hasHalf -> "${winner}胜半目"
            hasHalf -> "${winner}胜${whole}目半"
            else -> "${winner}胜${whole}目"
        }
    }
}
