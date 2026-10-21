package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.shared.content.LifeAndDeathGoal
import com.worksoc.goaicoach.shared.content.LifeAndDeathSection
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
    LifeAndDeathUnit.Intermediate to mapOf(
        UiLanguage.Korean to "중급 사활",
        UiLanguage.English to "Intermediate",
        UiLanguage.Japanese to "中級の死活",
        UiLanguage.ChineseSimplified to "中级死活",
    ),
    LifeAndDeathUnit.Advanced to mapOf(
        UiLanguage.Korean to "고급 사활",
        UiLanguage.English to "Advanced",
        UiLanguage.Japanese to "上級の死活",
        UiLanguage.ChineseSimplified to "高级死活",
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
    LifeAndDeathUnit.Intermediate to mapOf(
        UiLanguage.Korean to "첫 수 뒤의 응수까지 읽어야 풀린다.",
        UiLanguage.English to "Read past the first move — the replies decide.",
        UiLanguage.Japanese to "初手の後の応手まで読んで解く。",
        UiLanguage.ChineseSimplified to "要读到第一手之后的应对。",
    ),
    LifeAndDeathUnit.Advanced to mapOf(
        UiLanguage.Korean to "첫눈에 안 보이는 급소와 긴 수순.",
        UiLanguage.English to "A vital point you won't see at a glance, and a long line.",
        UiLanguage.Japanese to "一目では見えない急所と長い手順。",
        UiLanguage.ChineseSimplified to "一眼看不出的要点，较长的手顺。",
    ),
)

/** 단원 목록의 칸 이름(#277) — 기본 다섯 단원과 맥에서 만든 중급 · 고급을 가른다. */
private val LifeAndDeathSectionTitles: Map<LifeAndDeathSection, Map<UiLanguage, String>> = mapOf(
    LifeAndDeathSection.Basics to mapOf(
        UiLanguage.Korean to "기본",
        UiLanguage.English to "Basics",
        UiLanguage.Japanese to "基本",
        UiLanguage.ChineseSimplified to "基础",
    ),
    LifeAndDeathSection.Practice to mapOf(
        UiLanguage.Korean to "중·고급",
        UiLanguage.English to "Intermediate & advanced",
        UiLanguage.Japanese to "中級・上級",
        UiLanguage.ChineseSimplified to "中高级",
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
    // ── 중급 · 고급(#277) — 맥에서 만든 문제. 해설은 정답 수순에서 확인한 일(양단수 · 버림돌과 되따냄 · 안의 돌 따내기)만 적는다.
    "ld.mid.1" to mapOf(
        UiLanguage.Korean to "1선에서 단수부터 칩니다. 안에 들어온 백 돌을 차례로 잡아, 잡은 자리로 눈을 만들어 삽니다.",
        UiLanguage.English to "Start with the atari on the edge. Capture the white stones inside one by one and make your eyes where they stood.",
        UiLanguage.Japanese to "一線のアタリから。中に入った白石を順に取り、取った跡で眼を作って生きます。",
        UiLanguage.ChineseSimplified to "先在一线打吃。把里面的白子逐个吃掉，用吃掉的地方做眼活棋。",
    ),
    "ld.mid.2" to mapOf(
        UiLanguage.Korean to "오른쪽 1선을 먼저 막아 궁도를 지킵니다. 왼쪽에서 단수가 와도 이어서 받으면 두 눈이 납니다.",
        UiLanguage.English to "Block the first line on the right first to keep your eye space. If White ataris on the left, connect — two eyes follow.",
        UiLanguage.Japanese to "まず右の一線を止めて眼形を守ります。左からアタリが来ても継げば二眼ができます。",
        UiLanguage.ChineseSimplified to "先挡住右边一线，守住眼位。左边被打吃时接上，就能做出两只眼。",
    ),
    "ld.mid.3" to mapOf(
        UiLanguage.Korean to "백 두 덩어리를 한 수로 단수 치는 자리입니다. 백이 한쪽을 살리면 다른 쪽을 따내 궁도가 무너집니다.",
        UiLanguage.English to "This one move puts two white groups in atari. Whichever White saves, you capture the other and the eye space collapses.",
        UiLanguage.Japanese to "白の二つの石を一手で同時にアタリにする場所です。白が片方を助ければもう片方を取り、眼形が崩れます。",
        UiLanguage.ChineseSimplified to "这一手同时打吃白棋两块。白救一边，就吃掉另一边，眼位随之崩溃。",
    ),
    "ld.mid.4" to mapOf(
        UiLanguage.Korean to "끊어서 백을 둘로 가릅니다. 1선의 두 점을 내준 뒤 되따내면 백 전체가 잡힙니다.",
        UiLanguage.English to "Cut White in two. Give up your two stones on the first line, then capture back — the whole group falls.",
        UiLanguage.Japanese to "切って白を二つに分けます。一線の二子を取らせてから取り返すと、白全体が取れます。",
        UiLanguage.ChineseSimplified to "切断，把白棋分成两块。在一线送掉两子后再反提，白棋整块被吃。",
    ),
    "ld.mid.5" to mapOf(
        UiLanguage.Korean to "안에 있는 백 한 점을 먼저 따냅니다. 백의 눈 모양이 깨져, 돌을 주고받아도 백은 두 눈을 내지 못합니다.",
        UiLanguage.English to "First capture the single white stone inside. White's eye shape breaks, and even after the exchanges White cannot make two eyes.",
        UiLanguage.Japanese to "まず中の白一子を取ります。白の眼形が崩れ、石の取り合いの後も白は二眼を作れません。",
        UiLanguage.ChineseSimplified to "先提掉里面的一颗白子。白棋眼形被破，即使互相提子，白也做不出两只眼。",
    ),
    "ld.mid.6" to mapOf(
        UiLanguage.Korean to "귀의 끊기는 자리를 먼저 잇습니다. 그러면 1선에 들어온 백 돌을 한꺼번에 잡아 눈을 냅니다.",
        UiLanguage.English to "First connect the cutting point in the corner. Then you can capture the white stones on the first line all at once and make eyes.",
        UiLanguage.Japanese to "まず隅の切れ目を継ぎます。すると一線に入った白石をまとめて取り、眼を作れます。",
        UiLanguage.ChineseSimplified to "先把角上的断点接上。这样就能把一线的白子一起吃掉做眼。",
    ),
    "ld.mid.7" to mapOf(
        UiLanguage.Korean to "1선 2·1의 자리에 먼저 두어 귀의 눈부터 확보합니다. 백이 안으로 들어오면 따내면서 두 눈이 됩니다.",
        UiLanguage.English to "Take the 2-1 point on the first line to secure the corner eye. When White comes inside, capture — and you have two eyes.",
        UiLanguage.Japanese to "一線の二の一に先に打って隅の眼を確保します。白が中に入ってきたら取って二眼になります。",
        UiLanguage.ChineseSimplified to "先下一线的二一路，确保角上的眼。白棋进来就提掉，形成两只眼。",
    ),
    "ld.mid.8" to mapOf(
        UiLanguage.Korean to "가장자리에서 단수를 치며 귀로 파고듭니다. 궁도가 좁아진 백은 두 눈을 낼 수 없습니다.",
        UiLanguage.English to "Dig into the corner with an atari on the edge. With its eye space narrowed, White cannot make two eyes.",
        UiLanguage.Japanese to "辺からアタリを打って隅に食い込みます。眼形の狭くなった白は二眼を作れません。",
        UiLanguage.ChineseSimplified to "在边上打吃，深入角里。眼位变窄的白棋做不出两只眼。",
    ),
    "ld.mid.9" to mapOf(
        UiLanguage.Korean to "한가운데에 치중합니다. 백이 귀의 흑 한 점을 따내도 백의 눈은 하나뿐입니다.",
        UiLanguage.English to "Place in the very middle. Even if White captures your corner stone, White has only one eye.",
        UiLanguage.Japanese to "真ん中に置きます。白が隅の黒一子を取っても、白の眼は一つだけです。",
        UiLanguage.ChineseSimplified to "点在正中。即使白提掉角上的黑子，白也只有一只眼。",
    ),
    "ld.mid.10" to mapOf(
        UiLanguage.Korean to "가운데의 끊김을 이어 한 덩어리로 만듭니다. 그러면 1선에 들어온 백 돌을 잡아 눈을 냅니다.",
        UiLanguage.English to "Connect the cut in the middle to make one group. Then capture the white stones on the first line and make eyes.",
        UiLanguage.Japanese to "真ん中の切れ目を継いで一つにまとめます。そして一線に入った白石を取って眼を作ります。",
        UiLanguage.ChineseSimplified to "把中间的断点接上，连成一块。然后吃掉一线的白子做眼。",
    ),
    "ld.adv.1" to mapOf(
        UiLanguage.Korean to "1선에 먼저 두어 귀의 눈을 확보합니다. 오른쪽에서 파고드는 백은 따내면서 둘째 눈을 지킵니다.",
        UiLanguage.English to "Play on the first line first to secure the corner eye. When White digs in from the right, capture and keep the second eye.",
        UiLanguage.Japanese to "まず一線に打って隅の眼を確保します。右から食い込む白は取って、二つ目の眼を守ります。",
        UiLanguage.ChineseSimplified to "先在一线落子，确保角上的眼。白从右边钻进来时提掉，守住第二只眼。",
    ),
    "ld.adv.2" to mapOf(
        UiLanguage.Korean to "1선 2·1의 자리가 백의 귀 눈을 빼앗는 급소입니다. 안의 흑 한 점을 내줘도 백의 눈은 하나뿐입니다.",
        UiLanguage.English to "The 2-1 point on the first line takes away White's corner eye. Even giving up your stone inside, White has only one eye.",
        UiLanguage.Japanese to "一線の二の一が白の隅の眼を奪う急所です。中の黒一子を取らせても、白の眼は一つだけです。",
        UiLanguage.ChineseSimplified to "一线的二一路是破掉白棋角上眼位的要点。即使送掉里面的黑子，白也只有一只眼。",
    ),
    "ld.adv.3" to mapOf(
        UiLanguage.Korean to "2선에 먼저 두어 궁도를 지킵니다. 백이 귀로 파고들어도 따내면서 두 눈을 만듭니다.",
        UiLanguage.English to "Play on the second line first to keep your eye space. Even if White digs into the corner, capture and make two eyes.",
        UiLanguage.Japanese to "まず二線に打って眼形を守ります。白が隅に食い込んでも、取って二眼を作ります。",
        UiLanguage.ChineseSimplified to "先在二线落子守住眼位。白钻进角里也能提子做出两只眼。",
    ),
    "ld.adv.4" to mapOf(
        UiLanguage.Korean to "귀의 2선에 치중합니다. 1선의 백을 따내고 이어 두면 백의 궁도는 눈 하나만큼으로 줄어듭니다.",
        UiLanguage.English to "Place on the second line in the corner. Capture on the first line and connect — White's space shrinks to a single eye.",
        UiLanguage.Japanese to "隅の二線に置きます。一線の白を取って継ぐと、白の眼形は一眼分に縮みます。",
        UiLanguage.ChineseSimplified to "点在角上二线。提掉一线的白子再接上，白棋的眼位缩成只有一只眼。",
    ),
    "ld.adv.5" to mapOf(
        UiLanguage.Korean to "끊기는 자리를 이어 한 덩어리로 만듭니다. 안에 들어온 백을 따내 그 자리로 눈을 만들어 삽니다.",
        UiLanguage.English to "Connect the cutting point to make one group. Capture the white stones that came inside and make eyes where they stood.",
        UiLanguage.Japanese to "切れ目を継いで一つにまとめます。中に入った白を取り、その跡で眼を作って生きます。",
        UiLanguage.ChineseSimplified to "接上断点，连成一块。提掉进来的白子，用那里做眼活棋。",
    ),
    "ld.adv.6" to mapOf(
        UiLanguage.Korean to "1선 여섯 칸 가운데에 치중합니다. 궁도가 둘로 나뉘어 어느 쪽도 눈이 되지 못합니다.",
        UiLanguage.English to "Place in the middle of the six-point space on the first line. The space splits in two and neither half becomes an eye.",
        UiLanguage.Japanese to "一線六目の真ん中に置きます。眼形が二つに分かれ、どちらも眼になりません。",
        UiLanguage.ChineseSimplified to "点在一线六路的中间。眼位被分成两半，哪边都成不了眼。",
    ),
    "ld.adv.7" to mapOf(
        UiLanguage.Korean to "백 한 점이 들어와 있는 궁도입니다. 귀 쪽을 먼저 지키고, 안의 백을 따내 두 눈을 만듭니다.",
        UiLanguage.English to "A white stone sits inside your space. Guard the corner side first, then capture it and make two eyes.",
        UiLanguage.Japanese to "白一子が入っている眼形です。まず隅の側を守り、中の白を取って二眼を作ります。",
        UiLanguage.ChineseSimplified to "眼位里有一颗白子。先守住角的一侧，再提掉它做出两只眼。",
    ),
    "ld.adv.8" to mapOf(
        UiLanguage.Korean to "1선에 치중합니다. 이어서 돌을 주고받아도 백의 눈은 하나를 넘지 못합니다.",
        UiLanguage.English to "Place on the first line. Through the exchanges that follow, White never gets more than one eye.",
        UiLanguage.Japanese to "一線に置きます。その後石を取り合っても、白の眼は一つを超えません。",
        UiLanguage.ChineseSimplified to "在一线点入。之后即使互相提子，白棋也只有一只眼。",
    ),
    "ld.adv.9" to mapOf(
        UiLanguage.Korean to "1선에 먼저 두어 귀를 넓혀 둡니다. 백이 안으로 들어오면 양단수가 나서 두 눈을 지킵니다.",
        UiLanguage.English to "Play on the first line first to widen the corner. When White comes inside, you get a double atari and keep two eyes.",
        UiLanguage.Japanese to "まず一線に打って隅を広げておきます。白が中に入ると両アタリになり、二眼を守れます。",
        UiLanguage.ChineseSimplified to "先在一线落子把角扩大。白棋进来时就形成双打吃，守住两只眼。",
    ),
    "ld.adv.10" to mapOf(
        UiLanguage.Korean to "귀의 1선 2·1의 자리가 급소입니다. 백이 오른쪽에서 따내도 귀에는 눈이 없습니다.",
        UiLanguage.English to "The 2-1 point on the first line in the corner is the vital point. Even capturing on the right, White has no eye in the corner.",
        UiLanguage.Japanese to "隅の一線、二の一が急所です。白が右で取っても、隅に眼はありません。",
        UiLanguage.ChineseSimplified to "角上一线的二一路是要点。白在右边提子，角上也没有眼。",
    ),
)

// 표가 비면 키를 그대로 돌려준다 — `UiStringsStudyCategories.kt`의 폴백과 같은 이유다(빈칸보다 눈에 띈다).
internal fun lifeAndDeathUnitTitleFor(language: UiLanguage, unit: LifeAndDeathUnit): String =
    LifeAndDeathUnitTitles[unit]?.get(language) ?: unit.name

internal fun lifeAndDeathUnitSummaryFor(language: UiLanguage, unit: LifeAndDeathUnit): String =
    LifeAndDeathUnitSummaries[unit]?.get(language) ?: unit.name

internal fun lifeAndDeathSectionTitleFor(language: UiLanguage, section: LifeAndDeathSection): String =
    LifeAndDeathSectionTitles[section]?.get(language) ?: section.name

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
    LifeAndDeathSection.entries.forEach { put("section.${it.name}", LifeAndDeathSectionTitles[it]) }
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
