package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.content.StudyLessonId

/**
 * 「바둑 규칙 배우기」 갈래의 문구 전문(백로그 #164). 구조는 `UiStringsStudyCategories.kt`와
 * 같다 — 키로 네 언어를 든다. 갈래마다 한 파일이고, 합치는 것과 읽는 것은
 * `UiStringsStudyLessons.kt`가 한다(선례가 필요하면 `UiStringsStudyShapes.kt`).
 *
 * ⚠️ **[UiStrings] 생성자에 넣지 말 것 — 자리가 0칸이다.** `copy$default`가 JVM 인자 한도
 * 255칸에 딱 붙어 있어 한 줄만 더해도 **앱은 컴파일되고 테스트만 통째로** `ClassFormatError`로
 * 죽는다(함정 61, `StudyHubContractTest`가 칸수를 센다). 여기처럼 곁표 + 함수로 뺀다.
 *
 * ⚠️ **본문에 박힌 숫자는 도해가 보증한다.** `territory.count`의 27·36·6집반은 판을 고치면
 * 곧바로 거짓이 되는데 화면은 아무 말도 하지 않는다 — `GoRuleLessonsTest`가 같은 숫자를
 * 판에서 직접 세어 먼저 깨지게 해 두었다. 도해를 고치면 그 테스트부터 볼 것.
 *
 * ⚠️ **마크다운을 쓰지 말 것.** [Text]가 `**굵게**`를 그대로 별표로 그린다.
 */
internal val StudyRuleLessonTitles: Map<StudyLessonId, Map<UiLanguage, String>> = mapOf(
    StudyLessonId.RuleBasics to mapOf(
        UiLanguage.Korean to "바둑판과 첫 수",
        UiLanguage.English to "The Board and the First Move",
        UiLanguage.Japanese to "碁盤と最初の一手",
        UiLanguage.ChineseSimplified to "棋盘与第一手",
    ),
    StudyLessonId.RuleLiberties to mapOf(
        UiLanguage.Korean to "활로",
        UiLanguage.English to "Liberties",
        UiLanguage.Japanese to "呼吸点（ダメ）",
        UiLanguage.ChineseSimplified to "气",
    ),
    StudyLessonId.RuleCapture to mapOf(
        UiLanguage.Korean to "단수와 따냄",
        UiLanguage.English to "Atari and Capture",
        UiLanguage.Japanese to "アタリと石を取る",
        UiLanguage.ChineseSimplified to "打吃与提子",
    ),
    StudyLessonId.RuleForbidden to mapOf(
        UiLanguage.Korean to "둘 수 없는 자리",
        UiLanguage.English to "Where You May Not Play",
        UiLanguage.Japanese to "打てない場所",
        UiLanguage.ChineseSimplified to "不能落子的地方",
    ),
    StudyLessonId.RuleKo to mapOf(
        UiLanguage.Korean to "패",
        UiLanguage.English to "Ko",
        UiLanguage.Japanese to "コウ",
        UiLanguage.ChineseSimplified to "打劫",
    ),
    StudyLessonId.RuleTerritory to mapOf(
        UiLanguage.Korean to "집과 계가",
        UiLanguage.English to "Territory and Scoring",
        UiLanguage.Japanese to "地と計算",
        UiLanguage.ChineseSimplified to "围地与数目",
    ),
)

internal val StudyRuleLessonSummaries: Map<StudyLessonId, Map<UiLanguage, String>> = mapOf(
    StudyLessonId.RuleBasics to mapOf(
        UiLanguage.Korean to "돌은 어디에 놓고, 누가 먼저 두는가.",
        UiLanguage.English to "Where stones go, and who plays first.",
        UiLanguage.Japanese to "石はどこに置き、どちらが先に打つか。",
        UiLanguage.ChineseSimplified to "棋子下在哪里，谁先落子。",
    ),
    StudyLessonId.RuleLiberties to mapOf(
        UiLanguage.Korean to "돌이 숨 쉬는 자리 — 뒤의 규칙이 모두 여기서 나온다.",
        UiLanguage.English to "The points a stone breathes through — every later rule builds on this.",
        UiLanguage.Japanese to "石が息をする場所 — この先の規則はすべてここから出る。",
        UiLanguage.ChineseSimplified to "棋子呼吸的地方 —— 后面的规则都由此而来。",
    ),
    StudyLessonId.RuleCapture to mapOf(
        UiLanguage.Korean to "마지막 활로를 메우면 상대 돌을 들어낸다.",
        UiLanguage.English to "Fill the last liberty and the stone comes off the board.",
        UiLanguage.Japanese to "最後の呼吸点をふさぐと相手の石を取り上げる。",
        UiLanguage.ChineseSimplified to "填上最后一口气，就能把对方的棋子提走。",
    ),
    StudyLessonId.RuleForbidden to mapOf(
        UiLanguage.Korean to "활로가 없어지는 자리 — 단, 따내는 수는 둘 수 있다.",
        UiLanguage.English to "A point that leaves no liberties — unless the move captures.",
        UiLanguage.Japanese to "呼吸点がなくなる場所 — ただし相手を取る手は打てる。",
        UiLanguage.ChineseSimplified to "落下就没有气的点 —— 但能提子的手可以下。",
    ),
    StudyLessonId.RuleKo to mapOf(
        UiLanguage.Korean to "바로 되따낼 수 없다 — 팻감이 필요한 이유.",
        UiLanguage.English to "You cannot take straight back — this is why ko threats exist.",
        UiLanguage.Japanese to "すぐには取り返せない — コウ立てが要る理由。",
        UiLanguage.ChineseSimplified to "不能立刻提回 —— 这就是需要劫材的原因。",
    ),
    StudyLessonId.RuleTerritory to mapOf(
        UiLanguage.Korean to "둘러싼 빈 자리를 세고, 덤을 더해 승부를 가린다.",
        UiLanguage.English to "Count the empty points you surround, then add komi.",
        UiLanguage.Japanese to "囲んだ空点を数え、コミを足して勝敗を決める。",
        UiLanguage.ChineseSimplified to "数出围住的空点，再加上贴目分出胜负。",
    ),
)

/**
 * 단계 본문 — 키는 `StudyLessonStep.id`다. **갈래 접두사(`rule.`)를 반드시 단다** —
 * 표를 합쳐 쓰므로 접두사가 없으면 갈래끼리 열쇠가 부딪힌다(`StudyLessonsTest`가 센다). 좌표가 아니라 짧은 이름을 쓰는 이유는 도해를 다듬어도
 * 문구를 그대로 두는 경우가 흔하기 때문이다(`StudyVideoEntry.id`와 같은 판단).
 */
internal val StudyRuleStepBodies: Map<String, Map<UiLanguage, String>> = mapOf(
    "rule.basics.points" to mapOf(
        UiLanguage.Korean to "바둑은 가로줄과 세로줄이 만나는 자리, 곧 교차점에 돌을 놓습니다. 칸 안이 아닙니다. 비어 있는 교차점이면 어디든 놓을 수 있어, 지금 판에는 놓을 곳이 81군데 있습니다.",
        UiLanguage.English to "In Go you place stones on the intersections where the lines cross, not inside the squares. Any empty intersection will do — this board has 81 of them.",
        UiLanguage.Japanese to "囲碁は線と線が交わる点、つまり交点に石を置きます。マスの中ではありません。空いている交点ならどこでもよく、この盤には81か所あります。",
        UiLanguage.ChineseSimplified to "围棋把棋子下在横线与竖线相交的点上，而不是格子里。任何空着的交叉点都可以下，这块棋盘上共有81个。",
    ),
    "rule.basics.black" to mapOf(
        UiLanguage.Korean to "흑이 먼저 둡니다. 한 번 놓은 돌은 자리를 옮기지 않습니다 — 장기나 체스와 다른 점입니다. 돌이 판을 떠나는 경우는 따먹힐 때뿐입니다.",
        UiLanguage.English to "Black plays first. A stone never moves again once it is placed — unlike chess pieces. The only way a stone leaves the board is by being captured.",
        UiLanguage.Japanese to "黒が先に打ちます。置いた石は動きません — 将棋やチェスと違うところです。石が盤を離れるのは取られたときだけです。",
        UiLanguage.ChineseSimplified to "黑棋先行。棋子一旦落下就不再移动 —— 这一点和象棋不同。棋子离开棋盘的唯一方式就是被提掉。",
    ),
    "rule.basics.white" to mapOf(
        UiLanguage.Korean to "다음은 백 차례입니다. 둘은 한 수씩 번갈아 두고, 마땅히 둘 곳이 없으면 한 수를 거를 수도 있습니다.",
        UiLanguage.English to "Now it is White's turn. The two players alternate, one stone at a time, and may pass when there is nothing worth playing.",
        UiLanguage.Japanese to "次は白の番です。二人は一手ずつ交互に打ち、打つところがなければ手を抜く（パスする）こともできます。",
        UiLanguage.ChineseSimplified to "接下来轮到白棋。双方轮流各下一手，实在没有好点时也可以虚手（停一手）。",
    ),
    "rule.basics.goal" to mapOf(
        UiLanguage.Korean to "이렇게 빈 곳을 나누어 가지며 판을 채웁니다. 목표는 상대보다 집을 많이 만드는 것 — 내 돌로 둘러싼 빈 자리가 집입니다.",
        UiLanguage.English to "Play continues like this, each side staking out empty areas. The goal is to end up with more territory than your opponent — territory is the empty space your stones surround.",
        UiLanguage.Japanese to "こうして空いたところを分け合いながら盤を埋めていきます。目的は相手より多くの地を作ること — 自分の石で囲んだ空点が地です。",
        UiLanguage.ChineseSimplified to "双方就这样分占空处，把棋盘填满。目标是比对方围到更多的地 —— 被自己棋子围住的空点就是地。",
    ),

    "rule.liberties.center" to mapOf(
        UiLanguage.Korean to "돌에 맞닿은 빈 교차점을 활로라고 합니다. 한가운데 놓인 돌 하나는 위·아래·왼쪽·오른쪽으로 활로가 넷입니다. 대각선은 세지 않습니다.",
        UiLanguage.English to "The empty points directly next to a stone are its liberties. A stone in the middle has four — up, down, left and right. Diagonals do not count.",
        UiLanguage.Japanese to "石に接している空点を呼吸点（ダメ）といいます。盤の真ん中の石は上下左右の四つを持ちます。斜めは数えません。",
        UiLanguage.ChineseSimplified to "与棋子紧邻的空点叫作气。棋盘正中的一颗子有上下左右四口气，斜线方向不算。",
    ),
    "rule.liberties.corner" to mapOf(
        UiLanguage.Korean to "귀에 놓인 돌은 활로가 둘뿐이고, 가장자리는 셋입니다. 그래서 같은 한 점이라도 귀에 가까울수록 쉽게 잡힙니다.",
        UiLanguage.English to "A stone in the corner has only two liberties, and one on the edge has three. The same lone stone is much easier to capture near the corner.",
        UiLanguage.Japanese to "隅の石は呼吸点が二つ、辺なら三つしかありません。同じ一子でも隅に近いほど取られやすいのです。",
        UiLanguage.ChineseSimplified to "角上的棋子只有两口气，边上的有三口。同样是一颗子，越靠近角就越容易被提。",
    ),
    "rule.liberties.connect" to mapOf(
        UiLanguage.Korean to "돌을 나란히 이으면 활로를 함께 씁니다. 흑 두 점은 이제 한 덩어리이고 활로는 여섯 — 따로 있을 때보다 훨씬 튼튼합니다.",
        UiLanguage.English to "Stones placed side by side share their liberties. These two black stones are now one group with six liberties — far sturdier than two separate stones.",
        UiLanguage.Japanese to "石を並べてつなぐと呼吸点を共有します。黒の二子はひとつの塊になり呼吸点は六つ — ばらばらのときよりずっと強くなります。",
        UiLanguage.ChineseSimplified to "棋子连在一起就共用气。这两颗黑子现在是一块棋，共有六口气 —— 比分开时结实得多。",
    ),

    "rule.capture.atari" to mapOf(
        UiLanguage.Korean to "백 한 점이 흑에 둘러싸여 활로가 하나만 남았습니다. 이 상태를 단수라고 하고, 흑은 남은 한 곳에 두어 백 돌을 잡을 수 있습니다. 반대로 백은 이어서 활로를 늘려 달아날 수도 있습니다.",
        UiLanguage.English to "The white stone is surrounded and has a single liberty left. This is called atari: Black can capture it by filling that last point. White, for their part, could try to run by extending and gaining liberties.",
        UiLanguage.Japanese to "白の一子が黒に囲まれ、呼吸点が一つしか残っていません。この状態をアタリといい、黒は残りの一点に打てば白石を取れます。逆に白は継いで呼吸点を増やし、逃げることもできます。",
        UiLanguage.ChineseSimplified to "这颗白子被黑棋围住，只剩下一口气。这种状态叫打吃：黑棋只要填上最后一口气就能提掉它。反过来白棋也可以长出一手来增加气，设法逃跑。",
    ),
    "rule.capture.take" to mapOf(
        UiLanguage.Korean to "마지막 활로가 메워지는 순간 백 돌은 판에서 들려 나갑니다. 돌이 사라진 자리는 다시 빈 교차점이 되어, 나중에 누구든 그곳에 둘 수 있습니다.",
        UiLanguage.English to "The moment the last liberty is filled, the white stone comes off the board. The point it stood on is empty again and either player may use it later.",
        UiLanguage.Japanese to "最後の呼吸点がふさがれた瞬間、白石は盤から取り上げられます。石が消えたところは再び空点になり、後でどちらが打っても構いません。",
        UiLanguage.ChineseSimplified to "最后一口气被填上的瞬间，白子就被从棋盘上提走。它原来的位置重新变成空点，之后谁都可以在那里落子。",
    ),
    "rule.capture.prisoner" to mapOf(
        UiLanguage.Korean to "따낸 돌은 사석으로 따로 모아 둡니다. 대국이 끝나면 상대 집을 메우는 데 쓰이므로, 한 점을 따내는 것은 실제로는 두 집에 가까운 이득입니다.",
        UiLanguage.English to "Captured stones are kept aside as prisoners. At the end they are used to fill the opponent's territory, so taking one stone is worth close to two points.",
        UiLanguage.Japanese to "取った石はアゲハマとして取り置きます。終局後に相手の地を埋めるのに使うので、一子取ることは実質二目近い得になります。",
        UiLanguage.ChineseSimplified to "提掉的子要放在一边作为提子。终局时用来填对方的地，所以提掉一颗子实际接近两目的便宜。",
    ),

    "rule.forbidden.suicide" to mapOf(
        UiLanguage.Korean to "판 가운데 빈 자리 한 곳이 흑 넉 점에 완전히 둘러싸여 있습니다. 백이 그곳에 두면 놓는 순간 활로가 하나도 없어지므로, 그런 자리에는 애초에 둘 수 없습니다. 이것을 착수금지라고 합니다. 왼쪽 위 귀의 모양은 다음 장에서 봅니다.",
        UiLanguage.English to "The empty point in the middle is completely surrounded by four black stones. A white stone played there would have no liberties at all, so the move is simply not allowed — this is the suicide rule. The shape in the top-left corner comes up on the next page.",
        UiLanguage.Japanese to "盤の中央の空点が黒四子に完全に囲まれています。白がそこに打つと置いた瞬間に呼吸点がなくなるため、そもそも打てません。これを着手禁止点といいます。左上隅の形は次のページで見ます。",
        UiLanguage.ChineseSimplified to "棋盘中央那个空点被四颗黑子完全围住。白棋下在那里的瞬间就一口气也没有，因此根本不能落子 —— 这就是禁着点。左上角那块棋形在下一页再看。",
    ),
    "rule.forbidden.exception" to mapOf(
        UiLanguage.Korean to "단 예외가 하나 있습니다. 그 수로 상대 돌을 따낼 수 있다면 둘 수 있습니다. 왼쪽 위 귀를 보면 흑 세 점의 활로가 A9 한 곳뿐입니다.",
        UiLanguage.English to "There is one exception: if the move captures enemy stones, it is legal. Look at the top-left corner — the three black stones have only A9 left as a liberty.",
        UiLanguage.Japanese to "ただし例外が一つあります。その手で相手の石を取れるなら打てます。左上隅を見ると、黒三子の呼吸点はA9の一点だけです。",
        UiLanguage.ChineseSimplified to "但有一个例外：如果这一手能提掉对方的棋子，就可以下。看左上角，那三颗黑子只剩下A9这一口气。",
    ),
    "rule.forbidden.capture" to mapOf(
        UiLanguage.Korean to "백이 그 자리에 두자 흑 세 점이 먼저 들려 나가고, 비워진 자리가 백 돌의 활로가 됩니다. 순서가 중요합니다 — 따냄이 먼저, 활로 확인은 그다음입니다.",
        UiLanguage.English to "White plays there, the three black stones are removed first, and the freed points become liberties for the new white stone. The order matters: captures are resolved before liberties are checked.",
        UiLanguage.Japanese to "白がそこに打つと黒三子が先に取り上げられ、空いた点が白石の呼吸点になります。順序が大事です — 取りが先、呼吸点の確認は後です。",
        UiLanguage.ChineseSimplified to "白棋下在那里后，三颗黑子先被提掉，空出来的点就成了这颗白子的气。顺序很关键 —— 先提子，再看气。",
    ),

    "rule.ko.shape" to mapOf(
        UiLanguage.Korean to "이런 모양을 패라고 합니다. 지금 흑이 D5에 두면 백 한 점을 따낼 수 있습니다.",
        UiLanguage.English to "This shape is called a ko. Black can play at D5 right now and capture the white stone.",
        UiLanguage.Japanese to "この形をコウといいます。今、黒がD5に打てば白の一子を取れます。",
        UiLanguage.ChineseSimplified to "这种形状叫作劫。现在黑棋下在D5就能提掉那颗白子。",
    ),
    "rule.ko.take" to mapOf(
        UiLanguage.Korean to "흑이 백 한 점을 따냈습니다. 그런데 이번에는 방금 놓인 흑 한 점도 활로가 하나뿐이라, 백이 똑같이 되따낼 수 있는 모양이 되었습니다.",
        UiLanguage.English to "Black has taken the white stone. But the new black stone also has just one liberty, so White is now in a position to take straight back.",
        UiLanguage.Japanese to "黒が白の一子を取りました。ところが今度は打ったばかりの黒石も呼吸点が一つしかなく、白が同じように取り返せる形になっています。",
        UiLanguage.ChineseSimplified to "黑棋提掉了那颗白子。可是刚下的这颗黑子同样只有一口气，白棋现在也能照样提回来。",
    ),
    "rule.ko.forbidden" to mapOf(
        UiLanguage.Korean to "그대로 두면 같은 자리를 서로 무한히 주고받게 됩니다. 그래서 규칙이 막습니다 — 방금 따낸 자리를 바로 다음 수에 되따낼 수는 없습니다.",
        UiLanguage.English to "Left alone, the two sides would take the same point back and forth forever. The rule forbids it: you may not immediately recapture on the point that was just taken.",
        UiLanguage.Japanese to "そのままでは同じ場所を延々と取り合うことになります。だから規則が止めます — 取られたばかりの点をすぐ次の手で取り返すことはできません。",
        UiLanguage.ChineseSimplified to "如果不加限制，双方就会在同一点上无休止地互相提来提去。所以规则禁止：刚被提掉的那一点，不能在下一手立刻提回。",
    ),
    "rule.ko.threat" to mapOf(
        UiLanguage.Korean to "그래서 백은 다른 곳에 한 수 둡니다. 상대가 받지 않으면 손해가 큰 자리를 고르는데, 이런 수를 팻감이라고 합니다.",
        UiLanguage.English to "So White plays somewhere else first, choosing a move the opponent cannot afford to ignore. Such a move is called a ko threat.",
        UiLanguage.Japanese to "そこで白はまず別の場所に打ちます。相手が受けなければ損が大きい場所を選ぶので、これをコウ立てといいます。",
        UiLanguage.ChineseSimplified to "于是白棋先在别处下一手，挑一个对方不应就会吃亏的地方。这样的一手叫作劫材。",
    ),
    "rule.ko.answer" to mapOf(
        UiLanguage.Korean to "흑이 팻감을 받았습니다. 서로 한 수씩 지나갔으니 이제 금지가 풀립니다.",
        UiLanguage.English to "Black answers the threat. A move has passed on each side, so the ban is lifted.",
        UiLanguage.Japanese to "黒がコウ立てに応じました。互いに一手ずつ経ったので、禁止が解けます。",
        UiLanguage.ChineseSimplified to "黑棋应了这手劫材。双方各下了一手，禁止就此解除。",
    ),
    "rule.ko.retake" to mapOf(
        UiLanguage.Korean to "백이 패를 되따냅니다. 이번에는 흑이 팻감을 찾을 차례 — 패는 이렇게 주고받으며 이어지고, 팻감이 먼저 떨어진 쪽이 그 자리를 내줍니다.",
        UiLanguage.English to "White takes the ko back. Now it is Black who must find a threat — a ko goes on like this, trade after trade, until one side runs out of threats and gives the point up.",
        UiLanguage.Japanese to "白がコウを取り返します。今度は黒がコウ立てを探す番 — コウはこうして取り合いが続き、コウ立てが先に尽きたほうがその場所を譲ります。",
        UiLanguage.ChineseSimplified to "白棋把劫提了回来。这回轮到黑棋去找劫材了 —— 打劫就这样你来我往，谁先用完劫材，谁就要让出这个点。",
    ),

    "rule.territory.fence" to mapOf(
        UiLanguage.Korean to "판이 세로로 완전히 갈렸습니다. 왼쪽 세 줄은 흑 돌에만 둘러싸여 있고, 오른쪽 네 줄은 백 돌에만 둘러싸여 있습니다. 이렇게 한쪽 돌로만 둘러싸인 빈 교차점이 그 사람의 집입니다.",
        UiLanguage.English to "The board is now split down the middle. The three columns on the left are enclosed only by black stones, the four on the right only by white. Empty points enclosed by one player alone are that player's territory.",
        UiLanguage.Japanese to "盤が縦にきれいに分かれました。左の三列は黒石だけに囲まれ、右の四列は白石だけに囲まれています。こうして一方の石だけで囲まれた空点が、その人の地です。",
        UiLanguage.ChineseSimplified to "棋盘被竖着完全分开了。左边三列只被黑子围住，右边四列只被白子围住。像这样只被一方棋子围住的空点，就是那一方的地。",
    ),
    "rule.territory.pass" to mapOf(
        UiLanguage.Korean to "더 둘 곳이 없으면 한 수를 거릅니다. 흑이 먼저 걸렀습니다.",
        UiLanguage.English to "When there is nothing left worth playing, a player passes. Black passes first.",
        UiLanguage.Japanese to "打つところがなくなれば手を抜きます（パス）。まず黒がパスしました。",
        UiLanguage.ChineseSimplified to "没有地方可下时就虚手（停一手）。黑棋先停了一手。",
    ),
    "rule.territory.end" to mapOf(
        UiLanguage.Korean to "백도 이어서 걸렀습니다. 양쪽이 연달아 거르면 그것으로 대국이 끝납니다.",
        UiLanguage.English to "White passes as well. Two passes in a row end the game.",
        UiLanguage.Japanese to "白も続けてパスしました。両者が続けてパスすると、そこで対局は終わりです。",
        UiLanguage.ChineseSimplified to "白棋也跟着停了一手。双方连续虚手，对局就此结束。",
    ),
    "rule.territory.count" to mapOf(
        UiLanguage.Korean to "이제 집을 셉니다. 흑은 27집, 백은 36집입니다. 여기에 나중에 두기 시작한 백이 받는 덤 6집반을 더하면 백 42집반이 되어, 백이 15집반 차이로 이깁니다.",
        UiLanguage.English to "Now count. Black has 27 points, White 36. Adding the 6.5-point komi that White receives for playing second gives White 42.5 — a win by 15.5 points.",
        UiLanguage.Japanese to "ここで地を数えます。黒は27目、白は36目。後手番の白が受け取るコミ6目半を足すと白は42目半となり、白の15目半勝ちです。",
        UiLanguage.ChineseSimplified to "现在开始数目。黑棋27目，白棋36目。再加上后行的白棋所得的贴目6目半，白棋共42目半，白棋以15目半获胜。",
    ),
)
