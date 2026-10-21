package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.shared.content.LifeAndDeathGoal
import com.worksoc.goaicoach.shared.content.LifeAndDeathUnit

/**
 * 「기본 사활」의 네 언어 문구(백로그 #184) — 단원 이름·소개, 문제 화면의 안내·되먹임, 문제마다 풀고 나서 보이는 한 줄.
 *
 * ⚠️ **[UiStrings] 생성자에 넣지 말 것 — 자리가 0칸이다**(함정 61). 학습 단원과 같이 곁표 + 함수로 읽는다.
 * ⚠️ 문제는 모두 **흑이 먼저 둔다**(`lifeAndDeathProblems`) — 안내문이 「흑 차례」를 박아 둔 이유다. 백이 먼저 두는 문제를 더하면
 * 안내문을 색으로 가르고, 그물(`LifeAndDeathCopyContractTest`)이 함께 빨개진다.
 * ⚠️ 해설에 좌표를 쓰지 않는다 — 학습 화면의 판은 좌표를 끈다(`StudyLessonScreen`과 같은 판).
 */
private val LifeAndDeathUnitTitles: Map<LifeAndDeathUnit, Map<UiLanguage, String>> = mapOf(
    LifeAndDeathUnit.TwoEyes to mapOf(
        UiLanguage.Korean to "두 눈",
        UiLanguage.English to "Two eyes",
        UiLanguage.Japanese to "二眼",
        UiLanguage.ChineseSimplified to "两只眼",
    ),
    LifeAndDeathUnit.FalseEye to mapOf(
        UiLanguage.Korean to "옥집",
        UiLanguage.English to "False eyes",
        UiLanguage.Japanese to "欠け眼",
        UiLanguage.ChineseSimplified to "假眼",
    ),
    LifeAndDeathUnit.VitalPoint to mapOf(
        UiLanguage.Korean to "급소",
        UiLanguage.English to "Vital points",
        UiLanguage.Japanese to "急所",
        UiLanguage.ChineseSimplified to "要点",
    ),
    LifeAndDeathUnit.EyeSpace to mapOf(
        UiLanguage.Korean to "넓히기·좁히기",
        UiLanguage.English to "Widen and narrow",
        UiLanguage.Japanese to "広げる・狭める",
        UiLanguage.ChineseSimplified to "扩大与缩小眼位",
    ),
    LifeAndDeathUnit.Corner to mapOf(
        UiLanguage.Korean to "귀의 사활",
        UiLanguage.English to "The corner",
        UiLanguage.Japanese to "隅の死活",
        UiLanguage.ChineseSimplified to "角上的死活",
    ),
)

private val LifeAndDeathUnitSummaries: Map<LifeAndDeathUnit, Map<UiLanguage, String>> = mapOf(
    LifeAndDeathUnit.TwoEyes to mapOf(
        UiLanguage.Korean to "눈이 두 개면 산다 — 눈을 만들고, 눈 자리를 빼앗는다.",
        UiLanguage.English to "Two eyes live — make them, or take the eye point first.",
        UiLanguage.Japanese to "眼が二つあれば生き — 眼を作り、眼の場所を奪う。",
        UiLanguage.ChineseSimplified to "有两只眼就能活 — 做眼，或先占眼位。",
    ),
    LifeAndDeathUnit.FalseEye to mapOf(
        UiLanguage.Korean to "눈처럼 보여도 끊기는 눈은 눈이 아니다.",
        UiLanguage.English to "It looks like an eye, but if it can be cut, it isn't one.",
        UiLanguage.Japanese to "眼に見えても、切られる眼は眼ではない。",
        UiLanguage.ChineseSimplified to "看似是眼，能被切断的就不是真眼。",
    ),
    LifeAndDeathUnit.VitalPoint to mapOf(
        UiLanguage.Korean to "궁도의 한가운데를 먼저 차지하는 쪽이 이긴다.",
        UiLanguage.English to "Whoever takes the heart of the eye space first wins.",
        UiLanguage.Japanese to "眼形の真ん中を先に占めた方が勝つ。",
        UiLanguage.ChineseSimplified to "先占眼位中心的一方获胜。",
    ),
    LifeAndDeathUnit.EyeSpace to mapOf(
        UiLanguage.Korean to "안에서 넓혀 살고, 밖에서 좁혀 잡는다.",
        UiLanguage.English to "Widen from inside to live, narrow from outside to kill.",
        UiLanguage.Japanese to "内から広げて生き、外から狭めて取る。",
        UiLanguage.ChineseSimplified to "从内扩大做活，从外缩小杀棋。",
    ),
    LifeAndDeathUnit.Corner to mapOf(
        UiLanguage.Korean to "귀는 같은 모양도 변보다 좁다.",
        UiLanguage.English to "In the corner, the same shape has less room than on the side.",
        UiLanguage.Japanese to "隅では同じ形でも辺より狭い。",
        UiLanguage.ChineseSimplified to "在角上，同样的形比边上更窄。",
    ),
)

private val LifeAndDeathPrompts: Map<LifeAndDeathGoal, Map<UiLanguage, String>> = mapOf(
    LifeAndDeathGoal.Kill to mapOf(
        UiLanguage.Korean to "흑 차례 — 백을 잡으세요.",
        UiLanguage.English to "Black to play — kill White.",
        UiLanguage.Japanese to "黒番 — 白を取ってください。",
        UiLanguage.ChineseSimplified to "黑先 — 请杀白。",
    ),
    LifeAndDeathGoal.Live to mapOf(
        UiLanguage.Korean to "흑 차례 — 흑을 살리세요.",
        UiLanguage.English to "Black to play — make Black live.",
        UiLanguage.Japanese to "黒番 — 黒を生きてください。",
        UiLanguage.ChineseSimplified to "黑先 — 请做活黑棋。",
    ),
)

/** 틀린 수 뒤 — 목표마다 「무엇이 잘못됐나」가 다르다(잡으려다 살려 주거나, 살리려다 잡힌다). */
private val LifeAndDeathWrong: Map<LifeAndDeathGoal, Map<UiLanguage, String>> = mapOf(
    LifeAndDeathGoal.Kill to mapOf(
        UiLanguage.Korean to "아쉬워요. 이 수로는 백이 삽니다.",
        UiLanguage.English to "Not quite — after this move, White lives.",
        UiLanguage.Japanese to "残念。この手では白が生きます。",
        UiLanguage.ChineseSimplified to "可惜，这样白棋能活。",
    ),
    LifeAndDeathGoal.Live to mapOf(
        UiLanguage.Korean to "아쉬워요. 이 수로는 흑이 잡힙니다.",
        UiLanguage.English to "Not quite — after this move, Black dies.",
        UiLanguage.Japanese to "残念。この手では黒が取られます。",
        UiLanguage.ChineseSimplified to "可惜，这样黑棋会被杀。",
    ),
)

private val LifeAndDeathKeepGoing: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "좋아요! 상대가 받았습니다. 이어서 두세요.",
    UiLanguage.English to "Good! Your opponent answered — keep going.",
    UiLanguage.Japanese to "いいですね！相手が受けました。続けて打ってください。",
    UiLanguage.ChineseSimplified to "很好！对方应了一手，请继续。",
)

private val LifeAndDeathCorrect: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "정답입니다!",
    UiLanguage.English to "Correct!",
    UiLanguage.Japanese to "正解です！",
    UiLanguage.ChineseSimplified to "正确！",
)

private val LifeAndDeathShowingAnswer: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "정답 수순입니다.",
    UiLanguage.English to "Here is the solution.",
    UiLanguage.Japanese to "正解の手順です。",
    UiLanguage.ChineseSimplified to "这是正解的手顺。",
)

private val LifeAndDeathRetry: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "다시 풀기",
    UiLanguage.English to "Try again",
    UiLanguage.Japanese to "もう一度",
    UiLanguage.ChineseSimplified to "重新做题",
)

private val LifeAndDeathShowAnswer: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "정답 보기",
    UiLanguage.English to "Show answer",
    UiLanguage.Japanese to "正解を見る",
    UiLanguage.ChineseSimplified to "查看答案",
)

private val LifeAndDeathPreviousProblem: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이전 문제",
    UiLanguage.English to "Previous",
    UiLanguage.Japanese to "前の問題",
    UiLanguage.ChineseSimplified to "上一题",
)

private val LifeAndDeathNextProblem: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "다음 문제",
    UiLanguage.English to "Next",
    UiLanguage.Japanese to "次の問題",
    UiLanguage.ChineseSimplified to "下一题",
)

/**
 * 문제마다 **풀고 나서** 보이는 한 줄 — 왜 그 수인가. 「정답 보기」 끝에도 같은 줄이 뜬다.
 * ⚠️ 정답이 여럿인 문제(`ld.vital.5`·`ld.corner.4`·`ld.space.5`)는 다른 정답으로 풀어도 이 줄이 뜬다 — 그 수를 틀렸다고 말하지 않게 쓴다.
 */
private val LifeAndDeathExplanations: Map<String, Map<UiLanguage, String>> = mapOf(
    "ld.eyes.1" to mapOf(
        UiLanguage.Korean to "가운데에 두면 양쪽에 눈이 하나씩 생겨, 눈 두 개로 삽니다.",
        UiLanguage.English to "The middle point leaves one eye on each side — two eyes, alive.",
        UiLanguage.Japanese to "真ん中に打つと両側に眼が一つずつでき、二眼で生きます。",
        UiLanguage.ChineseSimplified to "下在中间，两边各有一只眼，两眼活棋。",
    ),
    "ld.eyes.2" to mapOf(
        UiLanguage.Korean to "가운데를 먼저 차지하면 백은 눈을 하나밖에 만들 수 없습니다.",
        UiLanguage.English to "Take the middle first and White can make only one eye.",
        UiLanguage.Japanese to "真ん中を先に占めると、白は眼を一つしか作れません。",
        UiLanguage.ChineseSimplified to "先占中间，白棋只能做出一只眼。",
    ),
    "ld.eyes.3" to mapOf(
        UiLanguage.Korean to "꺾인 모서리에 두면 양쪽이 따로따로 눈이 되어 삽니다.",
        UiLanguage.English to "The corner of the bend splits the space into two separate eyes.",
        UiLanguage.Japanese to "曲がり角に打つと、両側が別々の眼になって生きます。",
        UiLanguage.ChineseSimplified to "下在拐角，两边各成一只眼，棋就活了。",
    ),
    "ld.eyes.4" to mapOf(
        UiLanguage.Korean to "꺾인 모서리가 급소입니다. 여기를 빼앗기면 백은 눈 두 개를 낼 수 없습니다.",
        UiLanguage.English to "The corner of the bend is the vital point — without it White cannot make two eyes.",
        UiLanguage.Japanese to "曲がり角が急所です。ここを奪われると白は二眼を作れません。",
        UiLanguage.ChineseSimplified to "拐角是要点。被占之后，白棋做不出两只眼。",
    ),
    "ld.eyes.5" to mapOf(
        UiLanguage.Korean to "귀에는 이미 눈이 하나 있습니다. 둘째 눈이 날 자리를 먼저 차지하면 백은 한 눈뿐입니다.",
        UiLanguage.English to "White already has one eye in the corner. Take the second eye's point first and one eye is all it gets.",
        UiLanguage.Japanese to "隅にはもう眼が一つあります。二つ目の眼の場所を先に占めれば、白は一眼だけです。",
        UiLanguage.ChineseSimplified to "角上已有一只眼。先占第二只眼的位置，白棋只剩一只眼。",
    ),
    "ld.false.1" to mapOf(
        UiLanguage.Korean to "끊길 자리를 이어야 위쪽 눈이 진짜 눈이 됩니다. 끊기면 오른쪽 세 점이 단수에 몰려 그 눈을 메워야 합니다.",
        UiLanguage.English to "Connect the cutting point and the eye above is real. If cut, the three stones on the right fall into atari and must fill that eye.",
        UiLanguage.Japanese to "切られる所をつなぐと上の眼が本物になります。切られると右の三子がアタリになり、その眼を埋めることになります。",
        UiLanguage.ChineseSimplified to "补上断点，上面的眼才是真眼。被切断的话，右边三子被打吃，只能填掉那只眼。",
    ),
    "ld.false.2" to mapOf(
        UiLanguage.Korean to "끊으면 오른쪽 두 점이 단수에 몰립니다. 이으려면 눈 자리를 메워야 해서 백은 눈이 하나뿐입니다.",
        UiLanguage.English to "The cut puts the two stones on the right in atari. Saving them fills the eye point, so White has only one eye.",
        UiLanguage.Japanese to "切ると右の二子がアタリになります。つなぐには眼の場所を埋めるしかなく、白は一眼だけです。",
        UiLanguage.ChineseSimplified to "一切断，右边两子被打吃。要连回就得填掉眼位，白棋只有一只眼。",
    ),
    "ld.false.3" to mapOf(
        UiLanguage.Korean to "먼저 이어 두면 오른쪽 두 점이 떨어지지 않아, 위쪽 자리가 진짜 눈이 됩니다.",
        UiLanguage.English to "Connecting first keeps the two stones on the right attached, so the point above becomes a real eye.",
        UiLanguage.Japanese to "先につないでおくと右の二子が切り離されず、上の場所が本物の眼になります。",
        UiLanguage.ChineseSimplified to "先连上，右边两子不会被切断，上面就成了真眼。",
    ),
    "ld.false.4" to mapOf(
        UiLanguage.Korean to "눈 모양부터 갖추면 끊겨서 옥집이 됩니다. 끊길 자리를 먼저 이어야 삽니다.",
        UiLanguage.English to "Shaping the eye first lets White cut and turn it false. Connect the cutting point first to live.",
        UiLanguage.Japanese to "眼の形から作ると切られて欠け眼になります。切られる所を先につないで生きます。",
        UiLanguage.ChineseSimplified to "先做眼形会被切断成假眼。先补断点才能活。",
    ),
    "ld.false.5" to mapOf(
        UiLanguage.Korean to "끊으면 오른쪽 세 점이 단수에 몰립니다. 살리려고 이으면 눈 자리를 메우게 되어 백은 눈 두 개를 낼 수 없습니다.",
        UiLanguage.English to "The cut puts the three stones on the right in atari. Connecting them fills an eye point, so White cannot make two eyes.",
        UiLanguage.Japanese to "切ると右の三子がアタリになります。助けようとつなぐと眼の場所が埋まり、白は二眼を作れません。",
        UiLanguage.ChineseSimplified to "一切断，右边三子被打吃。连回去就会填掉眼位，白棋做不出两只眼。",
    ),
    "ld.vital.1" to mapOf(
        UiLanguage.Korean to "丁자 모양 넷은 세 갈래가 모이는 가운데가 급소입니다. 여기를 차지하면 백은 눈이 하나뿐입니다.",
        UiLanguage.English to "In the T-shaped four, the point where the three arms meet is vital. Take it and White has one eye.",
        UiLanguage.Japanese to "丁四は三方が集まる真ん中が急所です。ここを占めると白は一眼だけです。",
        UiLanguage.ChineseSimplified to "丁四的要点是三个方向交汇的中间。占住这里，白棋只有一只眼。",
    ),
    "ld.vital.2" to mapOf(
        UiLanguage.Korean to "丁자 모양 넷은 가운데에 두면 남은 세 자리가 모두 따로 눈이 되어 삽니다.",
        UiLanguage.English to "In the T-shaped four, the middle point leaves three separate eyes — alive.",
        UiLanguage.Japanese to "丁四は真ん中に打つと、残る三か所がすべて別々の眼になって生きます。",
        UiLanguage.ChineseSimplified to "丁四下在中间，剩下三处都成了单独的眼，棋就活了。",
    ),
    "ld.vital.3" to mapOf(
        UiLanguage.Korean to "십자 모양 다섯(매화오궁)은 한가운데가 급소입니다. 가운데를 빼앗긴 백은 눈을 둘로 나눌 수 없습니다.",
        UiLanguage.English to "In the cross-shaped five, the center is vital. Without it White cannot split the space into two eyes.",
        UiLanguage.Japanese to "十字形の五目（花五）は真ん中が急所です。真ん中を奪われた白は眼を二つに分けられません。",
        UiLanguage.ChineseSimplified to "十字形的五目（梅花五）要点在正中。被占中心，白棋分不出两只眼。",
    ),
    "ld.vital.4" to mapOf(
        UiLanguage.Korean to "네모에 하나가 붙은 다섯은, 튀어나온 점과 닿는 네모의 자리가 급소입니다. 백이 버티면 한 수 더 받아 마무리합니다.",
        UiLanguage.English to "In the bulky five, the vital point is the square's point next to the bump. If White resists, answer once more to finish.",
        UiLanguage.Japanese to "四角に一つ付いた五目（刀五）は、出っ張りに接する四角の点が急所です。白が粘ればもう一手受けて仕上げます。",
        UiLanguage.ChineseSimplified to "方块加一子的五目（刀把五），要点是方块中与突出那子相邻的点。白棋抵抗时再应一手即可。",
    ),
    "ld.vital.5" to mapOf(
        UiLanguage.Korean to "급소에 두면 한 수로 삽니다. 다른 자리로도 살 수 있지만, 급소가 가장 깔끔합니다.",
        UiLanguage.English to "The vital point lives in one move. Other moves can live too, but the vital point is the cleanest.",
        UiLanguage.Japanese to "急所に打てば一手で生きます。他の場所でも生きられますが、急所が一番すっきりしています。",
        UiLanguage.ChineseSimplified to "下在要点一手就活。别处也能活，但要点最干净利落。",
    ),
    "ld.vital.6" to mapOf(
        UiLanguage.Korean to "여섯이 뭉친 모양(매화육궁)도 한가운데가 급소입니다. 백이 넓히려 하면 한 수 더 막아 마무리합니다.",
        UiLanguage.English to "The rabbity six also dies to its central point. If White tries to widen, block once more to finish.",
        UiLanguage.Japanese to "花六も真ん中が急所です。白が広げようとしたら、もう一手止めて仕上げます。",
        UiLanguage.ChineseSimplified to "葡萄六的要点同样在中心。白棋想扩大时，再挡一手即可。",
    ),
    "ld.space.1" to mapOf(
        UiLanguage.Korean to "둘레의 틈을 막으면 다섯 칸이 한 줄로 남아 넉넉히 삽니다.",
        UiLanguage.English to "Closing the gap keeps five points in a row — plenty of room to live.",
        UiLanguage.Japanese to "囲みのすき間をふさぐと、五目が一列に残って十分に生きます。",
        UiLanguage.ChineseSimplified to "堵住缺口，一排五个眼位都留住了，足以做活。",
    ),
    "ld.space.2" to mapOf(
        UiLanguage.Korean to "틈으로 밀고 들어가면 백의 궁도가 줄어, 두 눈을 만들 수 없습니다.",
        UiLanguage.English to "Pushing in through the gap shrinks White's space until two eyes are impossible.",
        UiLanguage.Japanese to "すき間から押し込むと白の眼形が狭まり、二眼を作れなくなります。",
        UiLanguage.ChineseSimplified to "从缺口挤进去，白棋的眼位缩小，做不出两只眼。",
    ),
    "ld.space.3" to mapOf(
        UiLanguage.Korean to "1선으로 내려서 귀까지 궁도를 넓히면 두 눈을 낼 수 있습니다.",
        UiLanguage.English to "Descending to the first line widens the space into the corner, enough for two eyes.",
        UiLanguage.Japanese to "一線に下がって隅まで眼形を広げれば、二眼を作れます。",
        UiLanguage.ChineseSimplified to "下立到一路，把眼位扩大到角上，就能做出两只眼。",
    ),
    "ld.space.4" to mapOf(
        UiLanguage.Korean to "밖에서 1선으로 좁혀 들어갑니다. 백이 귀에서 막으면 그 돌을 따내며 궁도를 더 줄입니다.",
        UiLanguage.English to "Narrow from outside along the first line. If White blocks in the corner, capture that stone and shrink the space further.",
        UiLanguage.Japanese to "外から一線で狭めていきます。白が隅で止めたら、その石を取って眼形をさらに縮めます。",
        UiLanguage.ChineseSimplified to "从外面沿一路缩小眼位。白棋在角上挡，就提掉那颗子，继续缩小。",
    ),
    "ld.space.5" to mapOf(
        UiLanguage.Korean to "열린 쪽을 막으면 네 칸이 한 줄로 남아, 백이 들어와도 받으면 삽니다. 안쪽에 먼저 모양을 갖춰도 살 수 있습니다.",
        UiLanguage.English to "Blocking the open end keeps four in a row — answer White's placement and you live. Shaping the inside first also works.",
        UiLanguage.Japanese to "開いた方をふさぐと四目が一列に残り、白が入ってきても受ければ生きます。内側を先に整えても生きられます。",
        UiLanguage.ChineseSimplified to "堵住开口，一排四个眼位留住，白棋点进来时应一手就活。先在里面做眼形也能活。",
    ),
    "ld.corner.1" to mapOf(
        UiLanguage.Korean to "귀에서는 2·1의 자리가 급소입니다. 백이 버티면 한 수 더 받아 마무리합니다.",
        UiLanguage.English to "In the corner, the 2-1 point is vital. If White resists, answer once more to finish.",
        UiLanguage.Japanese to "隅では二の一が急所です。白が粘ればもう一手受けて仕上げます。",
        UiLanguage.ChineseSimplified to "在角上，二一路是要点。白棋抵抗时再应一手即可。",
    ),
    "ld.corner.2" to mapOf(
        UiLanguage.Korean to "같은 2·1의 자리를 먼저 두면 귀에서도 두 눈으로 삽니다.",
        UiLanguage.English to "Take that same 2-1 point first and the corner group lives with two eyes.",
        UiLanguage.Japanese to "同じ二の一に先に打てば、隅でも二眼で生きます。",
        UiLanguage.ChineseSimplified to "先下在同样的二一路，角上也能两眼做活。",
    ),
    "ld.corner.3" to mapOf(
        UiLanguage.Korean to "귀의 판육(2×3)은 바깥 공배가 없으면 2·2의 자리로 잡힙니다. 변이었다면 살았을 모양입니다.",
        UiLanguage.English to "A rectangular six in the corner with no outside liberties dies to the 2-2 point. On the side it would live.",
        UiLanguage.Japanese to "隅の板六は外ダメがないと二の二で取られます。辺なら生きている形です。",
        UiLanguage.ChineseSimplified to "角上的板六若没有外气，会被二二路杀死。在边上它本是活形。",
    ),
    "ld.corner.4" to mapOf(
        UiLanguage.Korean to "귀의 판육은 그냥 두면 잡힐 수 있습니다. 먼저 한 수 두어 두 눈을 확실히 합니다.",
        UiLanguage.English to "Left alone, a rectangular six in the corner can be killed. Spend one move first to secure two eyes.",
        UiLanguage.Japanese to "隅の板六は放っておくと取られることがあります。先に一手打って二眼を確かにします。",
        UiLanguage.ChineseSimplified to "角上的板六放着不管可能被杀。先补一手，确保两只眼。",
    ),
    "ld.corner.5" to mapOf(
        UiLanguage.Korean to "귀의 여섯도 모양의 한가운데(2·2)가 급소입니다. 백이 버티면 한 수 더 받아 마무리합니다.",
        UiLanguage.English to "This corner six also dies to the center of the shape, the 2-2 point. If White resists, answer once more.",
        UiLanguage.Japanese to "この隅の六目も形の真ん中（二の二）が急所です。白が粘ればもう一手受けて仕上げます。",
        UiLanguage.ChineseSimplified to "这个角上的六目，要点也在形的中心（二二路）。白棋抵抗时再应一手即可。",
    ),
)

// 표가 비면 키를 그대로 돌려준다 — `UiStringsStudyCategories.kt`의 폴백과 같은 이유다(빈칸보다 눈에 띈다).
internal fun lifeAndDeathUnitTitleFor(language: UiLanguage, unit: LifeAndDeathUnit): String =
    LifeAndDeathUnitTitles[unit]?.get(language) ?: unit.name

internal fun lifeAndDeathUnitSummaryFor(language: UiLanguage, unit: LifeAndDeathUnit): String =
    LifeAndDeathUnitSummaries[unit]?.get(language) ?: unit.name

internal fun lifeAndDeathPromptFor(language: UiLanguage, goal: LifeAndDeathGoal): String =
    LifeAndDeathPrompts[goal]?.get(language) ?: goal.name

internal fun lifeAndDeathWrongFor(language: UiLanguage, goal: LifeAndDeathGoal): String =
    LifeAndDeathWrong[goal]?.get(language) ?: goal.name

internal fun lifeAndDeathExplanationFor(language: UiLanguage, problemId: String): String =
    LifeAndDeathExplanations[problemId]?.get(language) ?: problemId

internal fun lifeAndDeathKeepGoingFor(language: UiLanguage): String =
    LifeAndDeathKeepGoing[language] ?: LifeAndDeathKeepGoing.getValue(UiLanguage.Korean)

internal fun lifeAndDeathCorrectFor(language: UiLanguage): String =
    LifeAndDeathCorrect[language] ?: LifeAndDeathCorrect.getValue(UiLanguage.Korean)

internal fun lifeAndDeathShowingAnswerFor(language: UiLanguage): String =
    LifeAndDeathShowingAnswer[language] ?: LifeAndDeathShowingAnswer.getValue(UiLanguage.Korean)

internal fun lifeAndDeathRetryFor(language: UiLanguage): String =
    LifeAndDeathRetry[language] ?: LifeAndDeathRetry.getValue(UiLanguage.Korean)

internal fun lifeAndDeathShowAnswerFor(language: UiLanguage): String =
    LifeAndDeathShowAnswer[language] ?: LifeAndDeathShowAnswer.getValue(UiLanguage.Korean)

internal fun lifeAndDeathPreviousProblemFor(language: UiLanguage): String =
    LifeAndDeathPreviousProblem[language] ?: LifeAndDeathPreviousProblem.getValue(UiLanguage.Korean)

internal fun lifeAndDeathNextProblemFor(language: UiLanguage): String =
    LifeAndDeathNextProblem[language] ?: LifeAndDeathNextProblem.getValue(UiLanguage.Korean)

/** 표가 실제로 덮는 언어 — **그물 전용**(`LifeAndDeathCopyContractTest`). 폴백과 견주면 빠진 줄을 못 본다(`studyLessonTitleLanguages`와 같은 이유). */
internal fun lifeAndDeathCopyLanguages(): Map<String, Set<UiLanguage>> = buildMap {
    fun put(key: String, table: Map<UiLanguage, String>?) = put(key, table?.filterValues { it.isNotBlank() }?.keys.orEmpty())
    LifeAndDeathUnit.entries.forEach {
        put("title.${it.name}", LifeAndDeathUnitTitles[it])
        put("summary.${it.name}", LifeAndDeathUnitSummaries[it])
    }
    LifeAndDeathGoal.entries.forEach {
        put("prompt.${it.name}", LifeAndDeathPrompts[it])
        put("wrong.${it.name}", LifeAndDeathWrong[it])
    }
    put("keepGoing", LifeAndDeathKeepGoing)
    put("correct", LifeAndDeathCorrect)
    put("showingAnswer", LifeAndDeathShowingAnswer)
    put("retry", LifeAndDeathRetry)
    put("showAnswer", LifeAndDeathShowAnswer)
    put("previous", LifeAndDeathPreviousProblem)
    put("next", LifeAndDeathNextProblem)
}

/** 해설 표의 열쇠와 언어 — 문제를 지웠는데 해설만 남거나, 문제를 더했는데 해설이 없는 것을 그물이 본다. */
internal fun lifeAndDeathExplanationLanguages(): Map<String, Set<UiLanguage>> =
    LifeAndDeathExplanations.mapValues { (_, table) -> table.filterValues { it.isNotBlank() }.keys }
