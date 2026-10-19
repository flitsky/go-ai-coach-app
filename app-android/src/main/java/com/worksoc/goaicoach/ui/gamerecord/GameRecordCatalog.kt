package com.worksoc.goaicoach.ui.gamerecord

import com.worksoc.goaicoach.shared.gamerecord.GameRecord
import com.worksoc.goaicoach.shared.gamerecord.GameRecordProblem
import com.worksoc.goaicoach.shared.gamerecord.GameRecordRead
import com.worksoc.goaicoach.shared.gamerecord.readSgfGameRecord
import com.worksoc.goaicoach.ui.l10n.GameRecordGroup
import com.worksoc.goaicoach.ui.l10n.GameRecordOpenFailure
import java.nio.charset.Charset

/** 앱에 싣는 기보의 에셋 폴더 — `app-android/src/main/assets/game-records/`. */
internal const val GameRecordAssetFolder = "game-records"

/** 앱에 싣는 기보 한 판. [gameNumber]는 여러 판짜리 묶음(결승 5번기 등)에서만 있다. */
internal data class BundledGameRecord(val asset: String, val group: GameRecordGroup, val gameNumber: Int? = null) {
    val assetPath: String get() = "$GameRecordAssetFolder/$asset"
}

/**
 * 앱에 싣는 기보(백로그 #269) — 출처는 모두 CWI 「Database of Go Games」(`homepages.cwi.nl/~aeb/go/games/`, *"The games here are in the
 * public domain."*). 파일명은 연도로 시작해 이름순이 곧 시간순이다.
 *
 * ✅ **사용자 최종 판단(2026-10-19): 33판 모두 싣는다**(*"모두 다 넣읍시다"*). 권리의 결은 묶음마다 다르다(활성 백로그 #269 카드):
 * 에도 고전은 대국자가 모두 150년 넘게 전에 세상을 떠났다 · 20세기 명국은 대국자가 근래 세상을 떠났다 · LG배·삼성화재배는 한국에서 여는
 * 대회다(한국기원은 「기보는 저작물」이라 주장해 왔고, 2024 서울고법은 부정경쟁 판단에서 기보를 공공영역으로 봤다 — 저작권 판단은 아니다).
 * 빼는 판은 에셋 파일과 이 목록의 줄을 함께 지운다(`GameRecordCatalogContractTest`가 둘을 대조한다).
 */
internal val BundledGameRecords: List<BundledGameRecord> = buildList {
    add(BundledGameRecord("1670-dosaku-tengen.sgf", GameRecordGroup.DosakuTengen))
    add(BundledGameRecord("1684-dosaku-masterpiece.sgf", GameRecordGroup.DosakuMasterpiece))
    add(BundledGameRecord("1835-blood-vomiting.sgf", GameRecordGroup.BloodVomiting))
    add(BundledGameRecord("1846-ear-reddening.sgf", GameRecordGroup.EarReddening))
    add(BundledGameRecord("1849-shusaku-castle.sgf", GameRecordGroup.ShusakuCastle))
    add(BundledGameRecord("1933-new-fuseki.sgf", GameRecordGroup.NewFuseki))
    add(BundledGameRecord("1938-shusai-retirement.sgf", GameRecordGroup.ShusaiRetirement))
    series("1989-ing-cup-1", GameRecordGroup.IngCup1, games = 5)
    series("2000-ing-cup-4", GameRecordGroup.IngCup4, games = 4)
    series("2001-lg-cup-5", GameRecordGroup.LgCup5, games = 5)
    series("2003-lg-cup-7", GameRecordGroup.LgCup7, games = 4)
    series("2012-samsung-cup-17", GameRecordGroup.SamsungCup17, games = 3)
    series("2016-alphago-lee-sedol", GameRecordGroup.AlphaGoLeeSedol, games = 5)
}

private fun MutableList<BundledGameRecord>.series(prefix: String, group: GameRecordGroup, games: Int) {
    (1..games).forEach { number -> add(BundledGameRecord("$prefix-$number.sgf", group, gameNumber = number)) }
}

/** 기보를 읽은 결과 — 화면은 이것 하나로 보기 화면을 열거나 문구를 띄운다. */
internal sealed interface GameRecordOpen {
    data class Opened(val record: GameRecord) : GameRecordOpen

    data class Failed(val failure: GameRecordOpenFailure) : GameRecordOpen
}

/**
 * SGF 파일의 바이트를 기보로 — 글자 집합은 SGF의 `CA`가 적은 것을 따르고, 없으면 UTF-8로 읽는다(앱에 싣는 기보는 모두 ASCII다).
 * `CA`는 그 자체가 ASCII라 앞부분을 ISO-8859-1로 읽어 찾는다.
 */
internal fun openGameRecord(bytes: ByteArray): GameRecordOpen {
    val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
    val charset = Regex("""CA\[([^\]]+)]""").find(head)?.groupValues?.get(1)?.trim()
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: Charsets.UTF_8
    return when (val read = readSgfGameRecord(String(bytes, charset))) {
        is GameRecordRead.Read -> GameRecordOpen.Opened(read.record)
        is GameRecordRead.Unreadable -> GameRecordOpen.Failed(read.problem.toOpenFailure())
    }
}

private fun GameRecordProblem.toOpenFailure(): GameRecordOpenFailure = when (this) {
    GameRecordProblem.NotSgf -> GameRecordOpenFailure.NotSgf
    GameRecordProblem.NotGo -> GameRecordOpenFailure.NotGo
    GameRecordProblem.UnsupportedBoardSize -> GameRecordOpenFailure.UnsupportedBoardSize
    GameRecordProblem.SetupAfterStart -> GameRecordOpenFailure.SetupAfterStart
    GameRecordProblem.BadPoint -> GameRecordOpenFailure.BadPoint
}
