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
        // 1.8.0 — #280 세 페이지 홈(내 정보 ‹ 대국 › 실험실) · #281 움직임(Material3 1.4.0) · 명언록 · #276 오늘의 운세(랜덤 운세 — 별자리별 · 띠별은 준비 중이라 적지 않는다) ·
        // #283 1일 1회 뽑기 · #282 야간 모드의 어두운 바둑판(사용자 2026-10-23 — 1.8.0으로 낸다).
        WhatsNewRelease(versionCode = 10800, versionName = "1.8.0") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "홈이 세 페이지로 바뀌었어요. 내 정보 ‹ 대국 › 실험실을 옆으로 넘기거나 아래 탭으로 오가요. 움직임도 더 부드러워졌어요.",
                    "실험실에 「오늘의 운세」가 생겼어요. 누르는 순간 오늘의 운세를 뽑고, 광고를 보면 하루 3번까지 운을 바꿀 수 있어요(구독 중에는 광고 없이).",
                    "실험실의 「1일 1회 뽑기」 — 광고 1편으로 하루 한 번, 형세 · 추천 수 1회권 · 광고 스킵권 · 캐릭터 조각 · 캐릭터 중 하나를 뽑아요(확률은 화면에 있어요).",
                    "실험실의 「명언록」에서 명언을 카드로 넘겨 볼 수 있어요.",
                    "야간 모드에서는 바둑판도 한 단 어두워져요.",
                )
                UiLanguage.English -> listOf(
                    "Home now has three pages — My info ‹ Play › Lab. Swipe between them or use the tabs at the bottom. Everything moves more smoothly, too.",
                    "New in the Lab: Today's fortune. Draw your fortune with a tap, and watch an ad to change your luck up to three times a day (no ads while subscribed).",
                    "Daily draw in the Lab: one ad, once a day, for eval and top-move tickets, an ad-skip ticket, a character shard or a character (the odds are on screen).",
                    "Swipe through quotes as cards in the Lab's Quote book.",
                    "In dark mode the board is darker as well.",
                )
                UiLanguage.Japanese -> listOf(
                    "ホームが3ページになりました。マイ情報 ‹ 対局 › 実験室を横にめくるか、下のタブで行き来できます。動きもなめらかになりました。",
                    "実験室に「今日の運勢」ができました。押した瞬間に今日の運勢を引き、広告を見ると1日3回まで運を変えられます（サブスク中は広告なし）。",
                    "実験室の「1日1回くじ」— 広告1本で1日1回、形勢・推奨手チケット・広告スキップ券・キャラクターのかけら・キャラクターのどれかを引きます（確率は画面にあります）。",
                    "実験室の「名言録」で名言をカードでめくって見られます。",
                    "ナイトモードでは碁盤も一段暗くなります。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "主页变成了三页：我的信息 ‹ 对弈 › 实验室，可以左右滑动或用底部的标签切换。动画也更流畅了。",
                    "实验室新增「今日运势」：点一下即抽取今日运势，看广告每天最多可改运3次（订阅期间无需看广告）。",
                    "实验室的「每日一抽」：每天看1个广告抽一次，可抽到形势·推荐券、免广告券、角色碎片或角色（概率显示在画面上）。",
                    "在实验室的「名言录」中可以像翻卡片一样浏览名言。",
                    "夜间模式下棋盘也会更暗一些。",
                )
            }
        },
        // 1.7.0 — #184 · #277 사활 공부하기(기본 26 · 중급 10 · 고급 10 · 고전 12 — 모두 무료) · #272 야간 모드 · #275 윗줄의 화면 모드 버튼 · #273 오늘의 한 줄 명언 ·
        // #212 캐릭터 착수 템포 · #271 시각별 기동 그림 · 나무 바둑판 아이콘(사용자 2026-10-22 — 1.7.0으로 낸다).
        WhatsNewRelease(versionCode = 10700, versionName = "1.7.0") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "「학습 하기」에 「사활 공부하기」가 생겼어요. 판에 직접 두어 푸는 사활 문제 — 기본 26문제, 중급 · 고급 20문제, 고전 『기경중묘』 · 『발양론』 12문제(원전의 정답)를 담았어요. 틀리면 붉은 점과 상대의 응수로 알려 주고, 「정답 보기」도 있어요.",
                    "야간 모드가 생겼어요. 설정 › 「화면 모드」에서 시스템 따라가기 · 밝게 · 어둡게 · 밤 시간 자동을 고를 수 있고, 맨 윗줄의 버튼으로 바로 바꿀 수도 있어요.",
                    "홈에 「오늘의 한 줄 명언」이 하루에 하나씩 떠요(✕를 누르면 그날은 닫혀요).",
                    "캐릭터가 사람처럼 생각할 시간을 두고 둬요. 초반에는 빠르게, 그 뒤에는 캐릭터마다 제 속도로.",
                    "앱 아이콘이 나무 바둑판으로 바뀌고, 시작 화면 그림이 새로워졌어요(밤에는 밤 그림).",
                )
                UiLanguage.English -> listOf(
                    "New in Study Mode: Life and Death. Solve problems by playing on the board — 26 basic, 20 intermediate and advanced, and 12 classics from Gokyo Shumyo and Igo Hatsuyoron with their original answers. A wrong move gets a red mark and your opponent's reply, and you can always show the answer.",
                    "Night mode is here. In Settings › Theme, choose Follow system, Light, Dark or Night hours — or switch right away with the button at the top of the screen.",
                    "A quote of the day now appears on home, one each day (tap ✕ to hide it for the day).",
                    "Characters now take time to think like people: quick in the opening, then each at their own pace.",
                    "A new wooden Go board app icon and new launch pictures (a night picture after dark).",
                )
                UiLanguage.Japanese -> listOf(
                    "「学習する」に「詰碁で死活を学ぶ」ができました。盤に直接打って解く死活問題 — 基本26問、中級・上級20問、古典『碁経衆妙』・『囲碁発陽論』12問（原典の正解）を収めました。間違えると赤い印と相手の応手で知らせ、「正解を見る」もあります。",
                    "ナイトモードができました。設定 › 「画面モード」で端末に合わせる・ライト・ダーク・夜だけダークを選べ、いちばん上のボタンですぐに切り替えることもできます。",
                    "ホームに「今日の一言」が一日ひとつ表示されます（✕を押すとその日は閉じます）。",
                    "キャラクターが人のように考える時間をとって打ちます。序盤は速く、その後はキャラクターごとのペースで。",
                    "アプリのアイコンが木の碁盤になり、起動画面の絵が新しくなりました（夜は夜の絵）。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "「进行学习」新增「死活练习」：在棋盘上直接落子解死活题——基础26题、中级·高级20题、古典《碁经众妙》·《围棋发阳论》12题（原书正解）。下错时会用红点和对手的应手提示，也可以「查看答案」。",
                    "新增夜间模式。在设置 › 「显示模式」中可选跟随系统、浅色、深色、夜间自动，也可以用顶部的按钮随时切换。",
                    "主页每天显示一条「今日一言」（点 ✕ 当天不再显示）。",
                    "角色会像人一样留出思考时间再落子：开局较快，之后按各自的节奏。",
                    "应用图标换成了木质棋盘，启动画面的插画也焕然一新（夜晚显示夜景插画）。",
                )
            }
        },
        // 1.6.0 — #269 기보 보기(명국 33판 · 형세 그래프와 변곡점 · 자동 진행 · 10수씩 · SGF 열기) · #244 앱 배경음악 · 다시보기의 10수 버튼(사용자 2026-10-19 — 1.6.0으로 낸다).
        // ⚠️ 매 수 형세(형세 보기를 켠 채 따라가기)는 프리미엄이다 — 여기서는 누구에게나 보이는 그래프·변곡점만 말한다(함정 51).
        WhatsNewRelease(versionCode = 10600, versionName = "1.6.0") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "홈에 「기보 보기」가 생겼어요. 도사쿠·슈사쿠의 고전부터 이창호·이세돌, 알파고 대국까지 명국 33판을 한 수씩 놓아 보고, 판 전체 형세 그래프와 변곡점도 볼 수 있어요.",
                    "기보는 자동 진행(1~5초)과 10수씩 넘기기로 볼 수 있고, 가지고 있는 SGF 파일도 열 수 있어요.",
                    "「앱 배경음악」이 생겼어요. 대국 중에도 은은하게 이어지고, 어느 화면에서든 맨 윗줄 오른쪽의 음표 버튼으로 바로 켜고 끌 수 있어요(설정 › 소리에도 있어요).",
                    "대국 다시보기에도 10수씩 넘기는 버튼(◀◀ ▶▶)이 생겼어요.",
                )
                UiLanguage.English -> listOf(
                    "New on home: Game records. Step through 33 famous games, from Dosaku and Shusaku to Lee Changho, Lee Sedol and AlphaGo, with the whole-game score graph and turning points.",
                    "Watch a record with auto play (1–5 s per move) or jump 10 moves at a time, and open your own SGF files too.",
                    "App background music keeps playing softly during your games. Turn it on or off anytime with the note button at the top right of every screen (also in Settings › Sound).",
                    "Game replay now has 10-move buttons (◀◀ ▶▶) as well.",
                )
                UiLanguage.Japanese -> listOf(
                    "ホームに「棋譜並べ」ができました。道策・秀策の古典から李昌鎬・李世乭、AlphaGoの対局まで名局33局を一手ずつ並べ、全体の形勢グラフと変局点も見られます。",
                    "棋譜は自動再生（1〜5秒）と10手送りで見られ、お持ちのSGFファイルも開けます。",
                    "「アプリのBGM」が付きました。対局中も静かに流れ続け、どの画面でもいちばん上の右側にある音符ボタンですぐにオン・オフできます（設定 › サウンドにもあります）。",
                    "対局リプレイにも10手送りのボタン（◀◀ ▶▶）が付きました。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "主页新增「打谱」：从道策、秀策的古典名局到李昌镐、李世石以及AlphaGo的对局，33局名局可一手一手地打谱，还能看全局形势曲线和转折点。",
                    "棋谱可以自动播放（每手1~5秒）或每次前进10手，也能打开你自己的SGF文件。",
                    "新增「应用背景音乐」，对局中也会轻柔地继续播放。任何画面顶部右侧的音符按钮都能随时开关（设置 › 声音 中也可以）。",
                    "对局回放也新增了每次10手的按钮（◀◀ ▶▶）。",
                )
            }
        },
        // 1.5.1 — #268 승급은 고른다(15집마다 · 한 판에 최대 3급) · 강급은 3연패. ⚠️ 출시 버전이 바뀌면(사용자 2026-10-15: 1.5.1로 바로, 또는 기능을 더한 뒤) 번호를 고친다.
        WhatsNewRelease(versionCode = 10501, versionName = "1.5.1") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "승급 대국은 15집 이상 차로 이기면 몇 급 오를지 직접 골라요(한 판에 최대 3급). 단도 15집 이상 차로 이기면 승단할지 골라요.",
                    "승급 대국의 강급은 3연패부터예요. 빨리 다시 재고 싶으면 「급수 초기화하기」를 쓰세요.",
                )
                UiLanguage.English -> listOf(
                    "Rank games: win by 15 points or more and you choose how far to move up (at most 3 ranks per game). In the dan ranks, a win by 15 or more lets you choose to move up one dan.",
                    "Rank games now drop you a step only after 3 losses in a row. To measure again quickly, use Reset rank.",
                )
                UiLanguage.Japanese -> listOf(
                    "昇級対局は15目以上の差で勝つと、何級上がるかを自分で選べます（1局で最大3級）。段も15目以上の差で勝つと昇段するかを選べます。",
                    "昇級対局の降級は3連敗からになりました。すぐに測り直したいときは「級位をリセット」を使ってください。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "升级对局赢15目以上时，可以自己选择升几级（每局最多3级）。段位赢15目以上时可选择是否升段。",
                    "升级对局改为3连败才降一档。想尽快重新测定，可使用「重置级位」。",
                )
            }
        },
        // 1.5.0 — #263 바둑판 사진 분석(실험실을 졸업해 본 기능 — 사용자 2026-10-15) · #209 두던 판은 끝까지 프리미엄 · 급수 초기화.
        WhatsNewRelease(versionCode = 10500, versionName = "1.5.0") { language ->
            when (language) {
                UiLanguage.Korean -> listOf(
                    "홈의 「바둑판 사진 분석」: 바둑판을 비추면 판을 찾아 자동으로 찍어요. 사진첩이나 다른 앱에서 「공유」한 사진도 읽어요.",
                    "읽은 국면을 보정한 뒤 그 자리부터 AI와 대국하거나, AI 추천 수와 형세를 볼 수 있어요(프리미엄). SGF 파일로 보낼 수도 있어요.",
                    "인터넷이 오래 끊겨 구독을 확인하지 못해도, 두고 있던 판은 끝날 때까지 프리미엄 기능을 그대로 쓸 수 있어요.",
                    "승급 대국은 「급수 초기화하기」로 기력을 처음부터 다시 잴 수 있어요(승급 대국 창 · 내 정보).",
                )
                UiLanguage.English -> listOf(
                    "Board Photo Analysis on the home screen: point the camera at a board and it finds and captures it. It also reads photos from your gallery or shared from other apps.",
                    "Fix up the position, then play the AI from there or see AI moves and the score (Premium). You can also send it as an SGF file.",
                    "If your subscription can't be confirmed for a long time (e.g. offline), your current game keeps its Premium features until it ends.",
                    "Rank games: reset your rank to measure it again (Rank game window or My info).",
                )
                UiLanguage.Japanese -> listOf(
                    "ホームの「盤面写真分析」：碁盤を映すと盤を見つけて自動で撮影します。アルバムの写真や他のアプリから「共有」した写真も読み取れます。",
                    "読み取った局面を補正したら、そこからAIと対局したり、AI推奨手と形勢を見たりできます（プレミアム）。SGFファイルで送ることもできます。",
                    "インターネットに長くつながらず購読を確認できなくても、対局中の一局は終わるまでプレミアム機能をそのまま使えます。",
                    "昇級対局は「級位をリセット」で棋力を最初から測り直せます（昇級対局の画面・マイ情報）。",
                )
                UiLanguage.ChineseSimplified -> listOf(
                    "首页的「棋盘拍照分析」：对准棋盘即可自动找到并拍摄。也能读取相册中的照片或从其他应用「分享」来的照片。",
                    "校正识别出的局面后，可以从这里与 AI 对局，或查看 AI 推荐手和形势（高级版）。也可以作为 SGF 文件发送。",
                    "即使长时间断网无法确认订阅，正在下的这一局在结束前仍可继续使用高级版功能。",
                    "升级对局可以通过「重置级位」从头重新测定棋力（升级对局窗口 · 我的信息）。",
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
