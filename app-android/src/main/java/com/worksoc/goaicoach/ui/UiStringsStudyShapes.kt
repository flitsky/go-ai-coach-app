package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.content.StudyLessonId

/**
 * 「바둑 기초 행마」 갈래의 문구 전문(백로그 #183). 구조는 `UiStringsStudyRules.kt`와 같다.
 *
 * ⚠️ **이 갈래는 「규칙」을 읽었다는 전제로 쓰여 있다** — 활로·단수·따냄을 다시 설명하지 않는다.
 * 순서를 섞거나 이 갈래를 허브에서 규칙보다 위로 올리면 문구가 허공에 뜬다.
 *
 * ⚠️ **판단이 섞인 문장이 있다**(2026-09-22 착수 전 고지). 규칙은 맞고 틀림이 명확하지만 행마는
 * *"이 모양이 좋다"* 가 들어간다 — 검수 때 따로 짚어야 하는 것은 아래 셋이다.
 * · `shape.extend.judge` — *"두 점을 버리는 것도 훌륭한 선택"*
 * · `shape.jump.why` — *"어디 둘지 모르겠으면 한 칸 뜀이 대체로 무난"*
 * · `shape.knight.answer` — *"빠른 행마일수록 약하다"*
 */
internal val StudyShapeLessonTitles: Map<StudyLessonId, Map<UiLanguage, String>> = mapOf(
    StudyLessonId.ShapeCut to mapOf(
        UiLanguage.Korean to "이음과 끊음",
        UiLanguage.English to "Connect and Cut",
        UiLanguage.Japanese to "つなぎと切り",
        UiLanguage.ChineseSimplified to "连与断",
    ),
    StudyLessonId.ShapeExtend to mapOf(
        UiLanguage.Korean to "뻗어서 달아나기",
        UiLanguage.English to "Extending to Escape",
        UiLanguage.Japanese to "伸びて逃げる",
        UiLanguage.ChineseSimplified to "长出逃跑",
    ),
    StudyLessonId.ShapeTigerMouth to mapOf(
        UiLanguage.Korean to "호구",
        UiLanguage.English to "The Tiger's Mouth",
        UiLanguage.Japanese to "カケツギ（虎の口）",
        UiLanguage.ChineseSimplified to "虎口",
    ),
    StudyLessonId.ShapeJump to mapOf(
        UiLanguage.Korean to "한 칸 뜀",
        UiLanguage.English to "The One-Point Jump",
        UiLanguage.Japanese to "一間トビ",
        UiLanguage.ChineseSimplified to "单关跳",
    ),
    StudyLessonId.ShapeKnight to mapOf(
        UiLanguage.Korean to "날일자",
        UiLanguage.English to "The Knight's Move",
        UiLanguage.Japanese to "ケイマ",
        UiLanguage.ChineseSimplified to "小飞",
    ),
)

internal val StudyShapeLessonSummaries: Map<StudyLessonId, Map<UiLanguage, String>> = mapOf(
    StudyLessonId.ShapeCut to mapOf(
        UiLanguage.Korean to "대각으로 놓인 두 점은 아직 이어져 있지 않습니다.",
        UiLanguage.English to "Two stones set diagonally are not connected yet.",
        UiLanguage.Japanese to "斜めに置いた二子は、まだつながっていません。",
        UiLanguage.ChineseSimplified to "斜着放的两颗子还没有连上。",
    ),
    StudyLessonId.ShapeExtend to mapOf(
        UiLanguage.Korean to "단수에 몰린 돌을 살리는 법, 그리고 언제 버릴지.",
        UiLanguage.English to "How to save a stone in atari — and when to let it go.",
        UiLanguage.Japanese to "アタリの石を助ける方法と、いつ捨てるか。",
        UiLanguage.ChineseSimplified to "救活被打吃的棋子，以及什么时候该舍。",
    ),
    StudyLessonId.ShapeTigerMouth to mapOf(
        UiLanguage.Korean to "뛰어들면 바로 잡히는, 끊기지 않는 이음.",
        UiLanguage.English to "A connection that cannot be cut — step in and you are captured.",
        UiLanguage.Japanese to "飛び込めばすぐ取られる、切れないつなぎ。",
        UiLanguage.ChineseSimplified to "断不开的连接 —— 冲进来就会被提。",
    ),
    StudyLessonId.ShapeJump to mapOf(
        UiLanguage.Korean to "한 칸을 비우고 놓아 두 배로 빠르게 넓힙니다.",
        UiLanguage.English to "Leave one point empty and cover ground twice as fast.",
        UiLanguage.Japanese to "一路あけて打ち、二倍の速さで広げます。",
        UiLanguage.ChineseSimplified to "空一路落子，扩张速度加倍。",
    ),
    StudyLessonId.ShapeKnight to mapOf(
        UiLanguage.Korean to "더 빠르지만 그만큼 약한 행마 — 언제 쓰는가.",
        UiLanguage.English to "Faster still, and weaker for it — when to use it.",
        UiLanguage.Japanese to "さらに速く、その分弱い — いつ使うか。",
        UiLanguage.ChineseSimplified to "更快，但也更薄 —— 什么时候用它。",
    ),
)

/**
 * 단계 본문 — 키는 `StudyLessonStep.id`다. **갈래 접두사(`shape.`)를 반드시 단다**(`rule.`과
 * 같은 이유로, 표를 합쳐 쓰기 때문이다).
 */
internal val StudyShapeStepBodies: Map<String, Map<UiLanguage, String>> = mapOf(
    "shape.cut.diagonal" to mapOf(
        UiLanguage.Korean to "위와 아래에 같은 모양이 하나씩 있습니다. 흑 두 점이 대각으로 놓여 붙어 있는 것처럼 보이지만, 바둑에서 이어졌다고 하려면 가로세로로 맞닿아야 합니다. 대각선은 이음이 아닙니다.",
        UiLanguage.English to "The same shape appears twice, once above and once below. The two black stones sit diagonally and look joined, but in Go stones are connected only when they touch along a line. A diagonal is not a connection.",
        UiLanguage.Japanese to "同じ形が上と下に一つずつあります。黒の二子は斜めに置かれていてつながって見えますが、囲碁でつながっているとは縦横で接していることです。斜めはつなぎではありません。",
        UiLanguage.ChineseSimplified to "上下各有一个相同的形状。两颗黑子斜着摆放，看上去连着，但围棋里只有沿着线相邻才算连接。斜线并不是连接。",
    ),
    "shape.cut.cut" to mapOf(
        UiLanguage.Korean to "백이 위쪽 모양의 사이를 파고들었습니다. 이것을 끊는다고 합니다. 흑 두 점은 이제 한 덩어리가 아니라 따로 노는 돌이라, 각각 따로 살길을 찾아야 합니다.",
        UiLanguage.English to "White has pushed into the gap in the upper shape. This is called cutting. The two black stones are no longer one group — each has to find its own way to live.",
        UiLanguage.Japanese to "白が上の形のすき間に割って入りました。これを切るといいます。黒の二子はもう一つの塊ではなく、それぞれ別に生きる道を探さなければなりません。",
        UiLanguage.ChineseSimplified to "白棋挤进了上面那个形状的空隙，这叫作断。两颗黑子不再是一块棋，各自都得另找活路。",
    ),
    "shape.cut.connect" to mapOf(
        UiLanguage.Korean to "아래쪽은 흑이 그 자리를 먼저 차지했습니다. 세 점이 한 덩어리가 되어 활로를 함께 쓰고, 백은 이제 이 흑을 끊을 수 없습니다. 같은 모양인데 한 수 차이로 결과가 갈렸습니다.",
        UiLanguage.English to "Below, Black took that point first. The three stones are now one group sharing their liberties, and White can no longer cut them. The same shape, one move apart, two very different results.",
        UiLanguage.Japanese to "下は黒が先にその点を占めました。三子が一つの塊になって呼吸点を共有し、白はもう切れません。同じ形なのに、一手の差で結果が分かれました。",
        UiLanguage.ChineseSimplified to "下面这块，黑棋抢先占住了那一点。三颗子成为一块棋、共用气，白棋再也断不开。同样的形状，一手之差，结果完全不同。",
    ),

    "shape.extend.atari" to mapOf(
        UiLanguage.Korean to "흑 한 점이 백에 둘러싸여 활로가 하나만 남았습니다. 그대로 두면 다음 수에 따먹힙니다.",
        UiLanguage.English to "The black stone is surrounded and has one liberty left. Leave it alone and White takes it next move.",
        UiLanguage.Japanese to "黒の一子が白に囲まれ、呼吸点が一つしか残っていません。放っておけば次の一手で取られます。",
        UiLanguage.ChineseSimplified to "这颗黑子被白棋围住，只剩一口气。放着不管，下一手就会被提掉。",
    ),
    "shape.extend.run" to mapOf(
        UiLanguage.Korean to "흑이 아래로 한 칸 뻗었습니다. 두 점이 한 덩어리가 되면서 활로가 셋으로 늘어, 당장은 잡히지 않습니다. 이것이 달아나는 가장 기본적인 방법입니다.",
        UiLanguage.English to "Black extends one point downward. The two stones become one group with three liberties, so nothing is captured for now. This is the most basic way to run.",
        UiLanguage.Japanese to "黒が下へ一つ伸びました。二子が一つの塊になって呼吸点が三つに増え、すぐには取られません。これが逃げる一番基本の形です。",
        UiLanguage.ChineseSimplified to "黑棋向下长了一手。两颗子连成一块，气增加到三口，暂时提不掉了。这就是逃跑最基本的方法。",
    ),
    "shape.extend.chase" to mapOf(
        UiLanguage.Korean to "그러나 백이 다시 앞을 막았습니다. 흑의 활로는 도로 둘. 뻗기만 되풀이하면 상대만 두터워지고 흑은 계속 쫓깁니다.",
        UiLanguage.English to "But White blocks again, and Black is back to two liberties. Repeating the extension only makes the opponent stronger while Black keeps running.",
        UiLanguage.Japanese to "しかし白がまた前をふさぎました。黒の呼吸点は再び二つ。伸びるだけを繰り返すと相手が厚くなるばかりで、黒は追われ続けます。",
        UiLanguage.ChineseSimplified to "可白棋又挡住了前面，黑棋的气又回到两口。一味地长，只会让对方变厚，自己一直被追着跑。",
    ),
    "shape.extend.judge" to mapOf(
        UiLanguage.Korean to "그래서 달아나기 전에 한 번 생각합니다. 내 편 돌이 있는 쪽으로 뻗고 있는가, 달아나 봤자 결국 잡히지는 않는가. 두 점을 버리고 더 큰 곳을 차지하는 것도 훌륭한 선택입니다. 달아남은 목적이 아니라 수단입니다.",
        UiLanguage.English to "So think before you run. Are you heading toward your own stones? Will running actually save you? Giving up two stones to take a bigger point elsewhere is often the better choice. Escaping is a means, not the goal.",
        UiLanguage.Japanese to "だから逃げる前に一度考えます。自分の石のあるほうへ伸びているか、逃げても結局取られはしないか。二子を捨てて大きな場所を占めるのも立派な選択です。逃げることは目的ではなく手段です。",
        UiLanguage.ChineseSimplified to "所以逃之前先想一想：是不是朝着自己的棋子在长？逃了之后真的能活吗？舍掉这两颗子去占更大的地方，往往才是好选择。逃跑是手段，不是目的。",
    ),

    "shape.tiger.shape" to mapOf(
        UiLanguage.Korean to "흑 세 점이 빈 자리 하나를 감싸고 있습니다. 이 모양을 호구라고 합니다. 세 점은 서로 직접 맞닿아 있지 않은데, 백이 그 빈 자리로 뛰어들면 어떻게 될까요?",
        UiLanguage.English to "Three black stones wrap around a single empty point. This shape is called a tiger's mouth. The stones do not actually touch each other — so what happens if White steps into the gap?",
        UiLanguage.Japanese to "黒の三子が空点を一つ囲んでいます。この形をカケツギ（虎の口）といいます。三子は直接くっついていませんが、白がその空点に飛び込むとどうなるでしょうか。",
        UiLanguage.ChineseSimplified to "三颗黑子围住了一个空点，这种形状叫作虎口。三颗子彼此并不相接 —— 那么白棋冲进这个空点会怎样呢？",
    ),
    "shape.tiger.in" to mapOf(
        UiLanguage.Korean to "뛰어든 백 한 점은 남은 활로가 아래 하나뿐입니다. 놓자마자 단수에 몰린 셈입니다. 둘 수 없는 자리는 아니지만, 두면 손해입니다.",
        UiLanguage.English to "The white stone that stepped in has just one liberty left, below. It is in atari the instant it is played. The move is legal — it is simply a losing one.",
        UiLanguage.Japanese to "飛び込んだ白の一子は、残る呼吸点が下の一つだけです。打った瞬間にアタリです。打てない場所ではありませんが、打てば損なだけです。",
        UiLanguage.ChineseSimplified to "冲进来的这颗白子只剩下方一口气，落子的瞬间就已经被打吃。这里并非不能下，只是下了就亏。",
    ),
    "shape.tiger.take" to mapOf(
        UiLanguage.Korean to "흑이 남은 활로를 메워 곧바로 따냈습니다. 그래서 호구는 끊기지 않는 이음으로 칩니다. 굳이 빈 자리를 메우지 않아도 이어진 것과 같아서, 그 한 수를 다른 큰 곳에 쓸 수 있습니다.",
        UiLanguage.English to "Black fills the last liberty and takes the stone at once. That is why a tiger's mouth counts as an uncuttable connection: you do not need to fill the gap, so you can spend that move somewhere bigger.",
        UiLanguage.Japanese to "黒が残りの呼吸点をふさいですぐに取りました。だからカケツギは切れないつなぎとして数えます。わざわざ空点を埋めなくてもつながっているのと同じなので、その一手を他の大きい場所に使えます。",
        UiLanguage.ChineseSimplified to "黑棋填上最后一口气，当即把它提掉。所以虎口算作断不开的连接：不必去补那个空点，省下的这一手可以用在更大的地方。",
    ),

    "shape.jump.jump" to mapOf(
        UiLanguage.Korean to "돌 하나에서 한 칸을 비우고 놓는 것을 한 칸 뜀이라고 합니다. 나란히 붙여 두는 것보다 두 배 빠르게 넓히면서도, 쉽게 끊기지 않습니다.",
        UiLanguage.English to "Playing one point away from your stone, leaving a gap, is called a one-point jump. It covers twice the ground of a solid extension, and it is still hard to cut.",
        UiLanguage.Japanese to "石から一路あけて打つことを一間トビといいます。並べて打つより二倍の速さで広げられて、しかも簡単には切れません。",
        UiLanguage.ChineseSimplified to "从一颗子出发、空一路落子，叫作单关跳。它比紧挨着长快一倍，而且并不容易被断开。",
    ),
    "shape.jump.cut" to mapOf(
        UiLanguage.Korean to "백이 그 사이를 갈라 보았습니다. 빈 칸이 있으니 끊을 수 있을 것 같은데, 정말 그럴까요?",
        UiLanguage.English to "White tries to split the gap. There is an empty point, so it looks cuttable — but is it?",
        UiLanguage.Japanese to "白がそのすき間を割ってみました。空いているから切れそうに見えますが、本当にそうでしょうか。",
        UiLanguage.ChineseSimplified to "白棋试着从中间分断。那里是空的，看上去可以断 —— 真的可以吗？",
    ),
    "shape.jump.atari" to mapOf(
        UiLanguage.Korean to "흑이 한쪽에서 밀어붙이자 갈라 들어온 백 한 점이 먼저 단수에 몰렸습니다. 위아래 흑에 끼여 활로를 늘릴 곳이 없습니다. 한 칸 뜀이 튼튼하다는 말은 이런 뜻입니다.",
        UiLanguage.English to "Black presses from one side and the cutting stone is the one in atari. Squeezed between the two black stones, it has nowhere to gain liberties. That is what it means to say the one-point jump is solid.",
        UiLanguage.Japanese to "黒が一方から押すと、割って入った白の一子のほうが先にアタリになりました。上下の黒に挟まれて呼吸点を増やす場所がありません。一間トビが強いというのはこういう意味です。",
        UiLanguage.ChineseSimplified to "黑棋从一边压过去，反倒是那颗断的白子先被打吃了。被上下两颗黑子夹住，它没有地方可以增加气。所谓单关跳结实，指的就是这个。",
    ),
    "shape.jump.why" to mapOf(
        UiLanguage.Korean to "그래서 한 칸 뜀은 입문자가 가장 먼저 익힐 행마입니다. 넓히고 싶은데 어디에 둘지 모르겠으면, 내 돌에서 한 칸 뛰는 것이 대체로 무난합니다.",
        UiLanguage.English to "This is why the one-point jump is the first shape a beginner should learn. When you want to expand and cannot decide where, jumping one point from your own stone is rarely wrong.",
        UiLanguage.Japanese to "だから一間トビは初心者が最初に覚えるべき行き方です。広げたいけれどどこに打てばいいか分からないときは、自分の石から一間トビしておけばだいたい無難です。",
        UiLanguage.ChineseSimplified to "所以单关跳是初学者最该先学会的一手。想扩张又不知道下在哪里时，从自己的棋子跳一路，通常都不会错。",
    ),

    "shape.knight.shape" to mapOf(
        UiLanguage.Korean to "이번에는 비스듬히 한 칸 더 벌렸습니다. 이 모양을 날일자라고 합니다. 한 칸 뜀보다 옆으로 더 빨리 나아가서, 귀나 변을 차지하러 갈 때 씁니다.",
        UiLanguage.English to "This time Black spreads one point further, on a diagonal. This shape is called a knight's move. It travels sideways faster than a one-point jump, so it is used when heading for a corner or a side.",
        UiLanguage.Japanese to "今度は斜めにもう一路広げました。この形をケイマといいます。一間トビより横へ速く進むので、隅や辺を占めに行くときに使います。",
        UiLanguage.ChineseSimplified to "这次黑棋斜着再多扩一路，这个形状叫作小飞。它比单关跳横向走得更快，常用在抢占角和边的时候。",
    ),
    "shape.knight.press" to mapOf(
        UiLanguage.Korean to "다만 한 칸 뜀보다는 약합니다. 백이 이렇게 사이에 붙여 오면 끊길 여지가 생깁니다.",
        UiLanguage.English to "It is weaker than a one-point jump, though. When White attaches in between like this, a cut starts to become possible.",
        UiLanguage.Japanese to "ただし一間トビよりは弱いです。白がこうして間にツケてくると、切られる余地が出てきます。",
        UiLanguage.ChineseSimplified to "不过它比单关跳要薄。白棋像这样贴过来，就有了断开的余地。",
    ),
    "shape.knight.answer" to mapOf(
        UiLanguage.Korean to "흑이 아래에서 받아 두 점을 이었습니다. 빠른 행마일수록 약하다는 것이 원칙입니다. 상대 돌이 많은 곳에서는 한 칸 뜀으로 단단하게, 넓은 곳에서는 날일자로 빠르게 — 어느 쪽이 좋은지는 판 위의 사정이 정합니다.",
        UiLanguage.English to "Black answers from below and joins the two stones. The rule is simple: the faster the shape, the thinner it is. Where the opponent is strong, jump solidly; in open space, use the knight's move. The board decides which is right.",
        UiLanguage.Japanese to "黒が下から受けて二子をつなぎました。速い形ほど薄いというのが原則です。相手の石が多いところでは一間トビで固く、広いところではケイマで速く — どちらがよいかは盤上の事情が決めます。",
        UiLanguage.ChineseSimplified to "黑棋从下面接应，把两颗子连了起来。原则很简单：走得越快，棋就越薄。对方棋子多的地方用单关跳走厚，开阔的地方用小飞走快 —— 哪个好，由盘上的形势决定。",
    ),
)
