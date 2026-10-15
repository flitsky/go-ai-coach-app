package com.worksoc.goaicoach.ui.l10n

/**
 * 업데이트한 뒤 보이는 「새로워진 점」(백로그 #257, 사용자 2026-10-13) — **앱에 싣는 버전별 묶음**이다(서버가 없다). 출시 노트(`work/play-store-assets/store_listing.txt`)를
 * 바탕으로 짧게 줄여 4개 언어로 적는다. 묶음이 없는 버전(작은 수정)에는 아무것도 뜨지 않는다.
 *
 * ⚠️ **출시할 때 여기도 본다** — 버전을 올리며 사용자가 체감할 변화가 있으면 그 versionCode로 묶음을 더한다(MAJOR×10000 + MINOR×100 + PATCH).
 *   versionCode가 앱보다 새 묶음은 보이지 않으므로, 다음 출시분을 미리 적어 두어도 그 버전이 나가기 전에는 뜨지 않는다.
 * ⚠️ 그 버전에서 달라진 것의 **기록**이다 — 뒤에 규칙이 또 바뀌어도 옛 묶음의 숫자를 상수로 바꿔 끼우지 않는다(그때는 그랬다).
 * ⚠️ 없는 것을 약속하지 않는다(함정 51). 스레드가 정한 문구다(영·일·중 원어민 확인 없음).
 */
internal class WhatsNewRelease(
    val versionCode: Int,
    val versionName: String,
    private val lines: (UiLanguage) -> List<String>,
) {
    fun linesFor(language: UiLanguage): List<String> = lines(language)
}

internal object WhatsNewCatalog {
    val releases: List<WhatsNewRelease> = listOf(
        // 1.5.0 — #263 바둑판 사진 분석(실험실을 졸업해 본 기능 — 사용자 2026-10-15) · #209 두던 판은 끝까지 프리미엄.
        WhatsNewRelease(versionCode = 10500, versionName = "1.5.0") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "홈의 「바둑판 사진 분석」: 바둑판을 비추면 판을 찾아 자동으로 찍어요. 사진첩이나 다른 앱에서 「공유」한 사진도 읽어요.",
                    "읽은 국면을 보정한 뒤 그 자리부터 AI와 대국하거나, AI 추천 수와 형세를 볼 수 있어요(프리미엄). SGF 파일로 보낼 수도 있어요.",
                    "인터넷이 오래 끊겨 구독을 확인하지 못해도, 두고 있던 판은 끝날 때까지 프리미엄 기능을 그대로 쓸 수 있어요.",
                )
                UiLanguage.English -> listOf(
                    "Board Photo Analysis on the home screen: point the camera at a board and it finds and captures it. It also reads photos from your gallery or shared from other apps.",
                    "Fix up the position, then play the AI from there or see AI moves and the score (Premium). You can also send it as an SGF file.",
                    "If your subscription can't be confirmed for a long time (e.g. offline), your current game keeps its Premium features until it ends.",
                )
                UiLanguage.Japanese -> listOf(
                    "ホームの「盤面写真分析」：碁盤を映すと盤を見つけて自動で撮影します。アルバムの写真や他のアプリから「共有」した写真も読み取れます。",
                    "読み取った局面を補正したら、そこからAIと対局したり、AI推奨手と形勢を見たりできます（プレミアム）。SGFファイルで送ることもできます。",
                    "インターネットに長くつながらず購読を確認できなくても、対局中の一局は終わるまでプレミアム機能をそのまま使えます。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "首页的「棋盘拍照分析」：对准棋盘即可自动找到并拍摄。也能读取相册中的照片或从其他应用「分享」来的照片。",
                    "校正识别出的局面后，可以从这里与 AI 对局，或查看 AI 推荐手和形势（高级版）。也可以作为 SGF 文件发送。",
                    "即使长时间断网无法确认订阅，正在下的这一局在结束前仍可继续使用高级版功能。",
                )
            }
        },
        // 1.4.0 — #255 업데이트 알림 · #256·#261 설정 정리 · #256·#260 내 정보의 내 캐릭터 · #258 조작법 · #259 승급 규칙.
        WhatsNewRelease(versionCode = 10400, versionName = "1.4.0") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "새 버전이 나오면 홈에서 바로 알려 드려요.",
                    "설정을 「언어 및 글자 크기」와 대국 바둑판·착수·AI 코치 설정으로 알기 쉽게 정리했어요.",
                    "「내 정보」에서 모은 캐릭터를 한눈에 보고, 광고로 조각을 모을 수 있어요.",
                    "바둑판 조작법은 판을 누르면 바로 직접 해 볼 수 있어요.",
                    "승급 대국은 이긴 집 수 차이 10집마다 한 단계 올라요(한 판에 최대 7단계). 단은 10집 이상 차이로 이기면 올라요.",
                )
                UiLanguage.English -> listOf(
                    "We'll tell you on the home screen when a new version is out.",
                    "Settings are easier to scan: language and text size, then board, stone placement and AI coach settings.",
                    "See the characters you've collected in My info, and collect pieces by watching ads.",
                    "In the board controls guide, touch the board to try it right away.",
                    "Rank games: move up one step for every 10 points you win by (at most 7 per game). In the dan ranks, a win by 10 or more moves you up.",
                )
                UiLanguage.Japanese -> listOf(
                    "新しいバージョンが出たらホームでお知らせします。",
                    "設定を「言語と文字サイズ」と、対局の盤・着手・AIコーチ設定に分かりやすく整理しました。",
                    "「マイ情報」で集めたキャラクターを一覧でき、広告でかけらを集められます。",
                    "盤の操作方法では、盤に触れるとすぐ自分で試せます。",
                    "昇級対局は勝った目数差10目ごとに1段階上がります（1局で最大7段階）。段は10目以上の差で勝つと上がります。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "有新版本时会在首页及时提醒。",
                    "设置更清晰了：语言和字号，以及对局棋盘、落子、AI 教练设置。",
                    "在「我的信息」中一览收集到的角色，并可通过看广告收集碎片。",
                    "在棋盘操作方法中，触摸棋盘即可马上亲自尝试。",
                    "升级对局每多赢10目提升一档（每局最多7档）。段位赢10目以上即可升段。",
                )
            }
        },
    )

    fun release(versionCode: Int): WhatsNewRelease? = releases.firstOrNull { it.versionCode == versionCode }

    /** 그 버전까지 실린 묶음들, 새것부터 — 설정 › 앱 정보의 「새로워진 점」이 보인다. */
    fun releasesUpTo(versionCode: Int): List<WhatsNewRelease> =
        releases.filter { it.versionCode <= versionCode }.sortedByDescending { it.versionCode }
}

/** 화면 이름 — 설정 › 앱 정보의 줄과 창의 제목. */
internal fun whatsNewTitleFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "새로워진 점"
    UiLanguage.English -> "What's new"
    UiLanguage.Japanese -> "新しくなった点"
    UiLanguage.ChineseSimplified -> "更新内容"
}

/** 홈 한 줄 — 「1.4.0에서 달라진 점」. */
internal fun whatsNewHomeLineFor(language: UiLanguage, versionName: String): String = when (language) {
    UiLanguage.Korean -> "${versionName}에서 달라진 점"
    UiLanguage.English -> "What's new in $versionName"
    UiLanguage.Japanese -> "${versionName}で変わった点"
    UiLanguage.ChineseSimplified -> "${versionName}更新内容"
}

/** 홈 한 줄의 동작 이름. */
internal fun whatsNewOpenFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "보기"
    UiLanguage.English -> "View"
    UiLanguage.Japanese -> "見る"
    UiLanguage.ChineseSimplified -> "查看"
}
