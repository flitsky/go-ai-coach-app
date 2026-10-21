package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 「기본 사활」의 문제 전부(백로그 #184) — **바이너리에 못박는다**(학습 단원과 같은 판단, U-19).
 *
 * 기본 다섯 단원은 모두 **직접 만든 문제**다(사용자 2026-10-21 「섞기」의 기본 쪽) — 직삼·곡삼·매화오 같은 기본 모양은 바둑의 공용 지식이라
 * 저작물이 아니고, 배치는 여기서 지었다. 중급 · 고급(#277)은 **맥에서 만든 문제**다 — `LifeAndDeathCandidateExportTest`(씨앗 277)가 그리고
 * 풀이기가 증명한 후보 가운데, 실험실 E11이 사람 모델로 「첫눈에 정답을 둘 확률」을 재고 주 모델로 생사를 판 전체에서 다시 본 것을 골랐다
 * (`engine-lab/experiments/e11_life_and_death_grades/`). 문제마다 흑이 먼저 둔다. 판은 9줄 판 하나이고 문제는 왼쪽 위 귀와 윗변에 놓인다 —
 * 귀만 잘라 보여 주지 않는 이유는 학습 단원과 같다(`StudyLesson`의 KDoc).
 *
 * ⚠️ **정답은 여기 없다** — [LifeAndDeathProblem.answer]는 지은이의 의도일 뿐이고 판정은 [LifeAndDeathSolver]가 한다.
 * 문제를 고치면 `LifeAndDeathProblemsTest`가 풀이기에게 그 의도가 정말 정답인지, 아직 결판나지 않은 국면인지,
 * 국면 수가 [LifeAndDeathSolver.NodeBudget] 아래인지 되묻는다.
 * ⚠️ **둘레는 판에 보이는 그대로 단단해야 한다** — 풀이기는 문제 밖(`,`)에 아무도 두지 않는다고 보지만, 판을 보는 사람은 그렇게 보지
 * 않는다. 처음 판은 벽이 대각선으로만 닿아 끊기는 자리가 있었고 급소 단원은 왼쪽 벽이 아예 단수였다(사용자 2026-10-21) — 풀이기에게는
 * 멀쩡한 문제였다. 그래서 **한 색의 바깥 벽은 끊긴 데 없는 한 덩어리**이고 문제 밖 활로가 넉넉하다(테스트가 센다). 반대로 목표 무리의
 * 공배가 문제 밖에 남으면 아무도 메우지 못해 잡을 수 없는 무리가 되므로, 목표 무리의 공배는 전부 문제 안에 둔다(이것도 테스트가 센다).
 * ⚠️ id는 저장값이다(푼 기록) — 바꾸지 말 것. 순서가 곧 화면의 순서다.
 */
val lifeAndDeathProblems: List<LifeAndDeathProblem> = listOf(
    // ── ① 두 눈 ────────────────────────────────────────────────────────
    // 셋이 한 줄(직삼): 가운데에 두면 양쪽이 눈 둘이 된다 — 살리는 쪽도, 잡는 쪽도 같은 자리다.
    problem(
        "ld.eyes.1", LifeAndDeathUnit.TwoEyes, LifeAndDeathGoal.Live, target = "A8", answer = "B9",
        ". . . X O , , , ,",
        "X X X X O , , , ,",
        "O O O O O , , , ,",
    ),
    problem(
        "ld.eyes.2", LifeAndDeathUnit.TwoEyes, LifeAndDeathGoal.Kill, target = "A8", answer = "B9",
        ". . . O X , , , ,",
        "O O O O X , , , ,",
        "X X X X X , , , ,",
    ),
    // 셋이 꺾인 모양(곡삼): 꺾인 모서리가 둘 모두와 닿는 자리다.
    problem(
        "ld.eyes.3", LifeAndDeathUnit.TwoEyes, LifeAndDeathGoal.Live, target = "B8", answer = "A9",
        ". . X O , , , , ,",
        ". X X O , , , , ,",
        "X X X O , , , , ,",
        "O O O O , , , , ,",
    ),
    problem(
        "ld.eyes.4", LifeAndDeathUnit.TwoEyes, LifeAndDeathGoal.Kill, target = "B8", answer = "A9",
        ". . O X , , , , ,",
        ". O O X , , , , ,",
        "O O O X , , , , ,",
        "X X X X , , , , ,",
    ),
    // 눈 자리 빼앗기: 귀의 눈 하나는 이미 있다 — 둘째 눈이 날 자리를 먼저 차지한다.
    problem(
        "ld.eyes.5", LifeAndDeathUnit.TwoEyes, LifeAndDeathGoal.Kill, target = "A8", answer = "D9",
        ". O . . X , , , ,",
        "O O O . X , , , ,",
        "X X X X X , , , ,",
    ),

    // ── ② 옥집 ────────────────────────────────────────────────────────
    // 눈의 대각선을 이어야 진짜 눈이다 — 끊기면 오른쪽 세 점이 단수에 몰려 그 눈을 메워야 한다.
    problem(
        "ld.false.1", LifeAndDeathUnit.FalseEye, LifeAndDeathGoal.Live, target = "B8", answer = "D8",
        ". X . X X O , , ,",
        "X X X . X O , , ,",
        "O O O O O O , , ,",
    ),
    // 끊어서 옥집으로: 끊긴 두 점은 눈 자리 하나로만 숨을 쉰다.
    problem(
        "ld.false.2", LifeAndDeathUnit.FalseEye, LifeAndDeathGoal.Kill, target = "A8", answer = "C8",
        ". O . O X , , , ,",
        "O O . O X , , , ,",
        "X X X X X , , , ,",
    ),
    problem(
        "ld.false.3", LifeAndDeathUnit.FalseEye, LifeAndDeathGoal.Live, target = "A8", answer = "C8",
        ". X . X O , , , ,",
        "X X . X O , , , ,",
        "O O O O O , , , ,",
    ),
    // 옥집 함정: 눈 모양을 먼저 갖추면 대각선을 끊겨 옥집이 된다 — 끊길 자리를 먼저 잇는다.
    problem(
        "ld.false.4", LifeAndDeathUnit.FalseEye, LifeAndDeathGoal.Live, target = "B8", answer = "D8",
        ". X . X O , , , ,",
        "X X . . O , , , ,",
        "X X X X O , , , ,",
        "O O O O O , , , ,",
    ),
    problem(
        "ld.false.5", LifeAndDeathUnit.FalseEye, LifeAndDeathGoal.Kill, target = "A8", answer = "C8",
        ". O . . O X , , ,",
        "O O . O O X , , ,",
        "X X X X X X , , ,",
    ),

    // ── ③ 급소 ────────────────────────────────────────────────────────
    // 넷이 丁자(정자): 세 갈래가 모이는 가운데.
    problem(
        "ld.vital.1", LifeAndDeathUnit.VitalPoint, LifeAndDeathGoal.Kill, target = "B9", answer = "D9",
        "X O . . . O X , ,",
        "X O O . O O X , ,",
        "X O O O O O X , ,",
        "X X X X X X X , ,",
    ),
    problem(
        "ld.vital.2", LifeAndDeathUnit.VitalPoint, LifeAndDeathGoal.Live, target = "B9", answer = "D9",
        "O X . . . X O , ,",
        "O X X . X X O , ,",
        "O X X X X X O , ,",
        "O O O O O O O , ,",
    ),
    // 다섯이 십자(매화오): 한가운데.
    problem(
        "ld.vital.3", LifeAndDeathUnit.VitalPoint, LifeAndDeathGoal.Kill, target = "B9", answer = "D8",
        "X O O . O O X , ,",
        "X O . . . O X , ,",
        "X O O . O O X , ,",
        "X O O O O O X , ,",
        "X X X X X X X , ,",
    ),
    // 다섯이 뭉친 모양(화오 — 네모에 하나): 튀어나온 점과 닿는 네모의 점.
    problem(
        "ld.vital.4", LifeAndDeathUnit.VitalPoint, LifeAndDeathGoal.Kill, target = "B9", answer = "D9",
        "X O . . . O X , ,",
        "X O . . O O X , ,",
        "X O O O O O X , ,",
        "X X X X X X X , ,",
    ),
    // 같은 모양을 살리는 쪽에서 — 급소가 가장 깔끔하지만, 다른 두 자리로도 산다(풀이기가 셋 다 정답으로 본다).
    problem(
        "ld.vital.5", LifeAndDeathUnit.VitalPoint, LifeAndDeathGoal.Live, target = "B9", answer = "D9",
        "O X . . . X O , ,",
        "O X . . X X O , ,",
        "O X X X X X O , ,",
        "O O O O O O O , ,",
    ),
    // 여섯(매화육궁): 한가운데 — 백이 버티면 한 수 더 받는다.
    problem(
        "ld.vital.6", LifeAndDeathUnit.VitalPoint, LifeAndDeathGoal.Kill, target = "B9", answer = "D8",
        "X O O . O O X , ,",
        "X O . . . O X , ,",
        "X O O . . O X , ,",
        "X O O O O O X , ,",
        "X X X X X X X , ,",
    ),

    // ── ④ 넓히기·좁히기 ────────────────────────────────────────────────
    // 둘레의 틈: 막으면 다섯이 한 줄(산다), 밀려 들어오면 궁도가 줄어든다.
    problem(
        "ld.space.1", LifeAndDeathUnit.EyeSpace, LifeAndDeathGoal.Live, target = "A8", answer = "D8",
        ". . . . . X O , ,",
        "X X X . X X O , ,",
        "O O O O O O O , ,",
    ),
    problem(
        "ld.space.2", LifeAndDeathUnit.EyeSpace, LifeAndDeathGoal.Kill, target = "A8", answer = "D8",
        ". . . . . O X , ,",
        "O O O . O O X , ,",
        "X X X X X X X , ,",
    ),
    // 귀 쪽의 틈: 1선으로 내려서 궁도를 넓힌다 / 밖에서 1선으로 좁힌다.
    problem(
        "ld.space.3", LifeAndDeathUnit.EyeSpace, LifeAndDeathGoal.Live, target = "B8", answer = "A8",
        ". . . . X O , , ,",
        ". X X X X O , , ,",
        "O O O O O O , , ,",
    ),
    problem(
        "ld.space.4", LifeAndDeathUnit.EyeSpace, LifeAndDeathGoal.Kill, target = "B8", answer = "A8",
        ". . . . . O X , ,",
        ". O O O O O X , ,",
        "X X X X X X X , ,",
    ),
    // 끝이 열린 궁도: 열린 쪽을 막아 넷을 지킨다 — 안쪽에 먼저 모양을 갖춰도 산다(풀이기가 둘 다 정답으로 본다).
    problem(
        "ld.space.5", LifeAndDeathUnit.EyeSpace, LifeAndDeathGoal.Live, target = "B8", answer = "B9",
        ". . . . . . X O ,",
        "O X X X X X X O ,",
        "O O O O O O O O ,",
    ),

    // ── ⑤ 귀의 사활 ────────────────────────────────────────────────────
    // 귀의 화오: 2·1의 자리가 급소다.
    problem(
        "ld.corner.1", LifeAndDeathUnit.Corner, LifeAndDeathGoal.Kill, target = "C8", answer = "B9",
        ". . . O X , , , ,",
        ". . O O X , , , ,",
        "O O O O X , , , ,",
        "X X X X X , , , ,",
    ),
    problem(
        "ld.corner.2", LifeAndDeathUnit.Corner, LifeAndDeathGoal.Live, target = "C8", answer = "B9",
        ". . . X O , , , ,",
        ". . X X O , , , ,",
        "X X X X O , , , ,",
        "O O O O O , , , ,",
    ),
    // 귀의 판육(2×3): 바깥 공배가 없으면 2·2의 자리로 죽는다 — 변이라면 산다.
    problem(
        "ld.corner.3", LifeAndDeathUnit.Corner, LifeAndDeathGoal.Kill, target = "D8", answer = "B8",
        ". . . O X , , , ,",
        ". . . O X , , , ,",
        "O O O O X , , , ,",
        "X X X X X , , , ,",
    ),
    problem(
        "ld.corner.4", LifeAndDeathUnit.Corner, LifeAndDeathGoal.Live, target = "D8", answer = "B8",
        ". . . X O , , , ,",
        ". . . X O , , , ,",
        "X X X X O , , , ,",
        "O O O O O , , , ,",
    ),
    problem(
        "ld.corner.5", LifeAndDeathUnit.Corner, LifeAndDeathGoal.Kill, target = "C9", answer = "B8",
        ". . O O X , , , ,",
        ". . . O X , , , ,",
        "O . O O X , , , ,",
        "O O O O X , , , ,",
        "X X X X X , , , ,",
    ),

    // ── ⑥ 중급 (#277) ────────────────────────────────────────────────
    // 맥에서 만들고 풀이기가 증명한 문제 — 쉬운 것부터(사람 모델이 첫눈에 정답을 둘 확률이 높은 순).
    // 1선에서 단수부터 — 안에 들어온 백 돌을 차례로 잡아 그 자리로 눈을 만든다.
    problem(
        "ld.mid.1", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Live, target = "A7", answer = "C9",
        "O . . O . X O , ,",
        ". . . X X O O , ,",
        "X X X O O O , , ,",
        "O O O O , , , , ,",
    ),
    // 변의 문제: 오른쪽 1선을 먼저 막아 궁도를 지킨다. 왼쪽 단수는 이어서 받는다.
    problem(
        "ld.mid.2", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Live, target = "D8", answer = "F9",
        "O X . . . . . O ,",
        "O X . X . X O O ,",
        "O O X X X O O , ,",
        ", O O O O O , , ,",
    ),
    // 양단수 — 백 두 덩어리를 한 수로 단수 친다.
    problem(
        "ld.mid.3", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Kill, target = "D9", answer = "B8",
        ". . . O . O X , ,",
        "O . . O . X X , ,",
        "X O O X X X , , ,",
        "X X X X , , , , ,",
    ),
    // 끊어서 둘로 가르고, 1선 두 점을 내준 뒤 되따낸다.
    problem(
        "ld.mid.4", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Kill, target = "A8", answer = "E8",
        ". . X . . O X , ,",
        "O O O O . O X , ,",
        "X X X X . X X , ,",
        ", , , X X X , , ,",
    ),
    // 안의 백 한 점을 먼저 따내 눈 모양을 깬다.
    problem(
        "ld.mid.5", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Kill, target = "D8", answer = "C8",
        ". . . . X . O X ,",
        "O O . O O O X X ,",
        "X X O X X X X , ,",
        ", X X X , , , , ,",
    ),
    // 귀의 끊김을 먼저 잇고, 1선에 들어온 백을 한꺼번에 잡는다.
    problem(
        "ld.mid.6", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Live, target = "B8", answer = "A8",
        ". O . O . . X O ,",
        ". X X X X X O O ,",
        "X O O O O O O , ,",
        "O O , , , , , , ,",
    ),
    // 1선 2·1의 자리로 귀의 눈부터 — 들어오는 백은 따낸다.
    problem(
        "ld.mid.7", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Live, target = "B7", answer = "B9",
        ". . . . X O , , ,",
        "X . . . . O , , ,",
        "O X X X O O , , ,",
        "O O O O O , , , ,",
    ),
    // 가장자리에서 단수 치며 귀로 파고든다.
    problem(
        "ld.mid.8", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Kill, target = "C8", answer = "A8",
        ". O . . . X X , ,",
        ". . O O . X X , ,",
        "O O X X X X , , ,",
        "X X X , , , , , ,",
    ),
    // 한가운데 치중 — 귀의 흑 한 점을 내줘도 백의 눈은 하나.
    problem(
        "ld.mid.9", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Kill, target = "B8", answer = "D8",
        ". . . . . O X , ,",
        "X O . . O X X , ,",
        "O O O O X X , , ,",
        "X X X X X , , , ,",
    ),
    // 가운데 끊김을 이어 한 덩어리로, 1선의 백을 잡아 눈을 낸다.
    problem(
        "ld.mid.10", LifeAndDeathUnit.Intermediate, LifeAndDeathGoal.Live, target = "A8", answer = "E8",
        ". O . O . . X O ,",
        "X X X X . X O O ,",
        "O O O O X O O , ,",
        ", , , O O O , , ,",
    ),

    // ── ⑦ 고급 (#277) ────────────────────────────────────────────────
    // 같은 길로 만든 문제 가운데 첫 수가 가장 안 보이는 것 — 살리기와 잡기를 번갈아.
    // 1선에 먼저 두어 귀의 눈을 확보하고, 오른쪽에서 파고드는 백은 따낸다.
    problem(
        "ld.adv.1", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Live, target = "A9", answer = "C9",
        "X . . . . . X O ,",
        "X X . . . X O O ,",
        "O O X X X O O , ,",
        ", O O O O O , , ,",
    ),
    // 1선 2·1의 자리로 백의 귀 눈을 빼앗는다 — 안의 흑 한 점은 내준다.
    problem(
        "ld.adv.2", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Kill, target = "C9", answer = "B9",
        ". . O X . . O X ,",
        "O . O . O . X X ,",
        "X O X X X X X , ,",
        "X X X , , , , , ,",
    ),
    // 2선 한가운데가 궁도를 둘로 나누는 급소.
    problem(
        "ld.adv.3", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Live, target = "E9", answer = "C8",
        ". . . . X X O , ,",
        ". . . X X O O , ,",
        ". X X O O O , , ,",
        "O O O O , , , , ,",
    ),
    // 귀의 2선에 치중 — 1선을 따내고 이어 궁도를 하나로 줄인다.
    problem(
        "ld.adv.4", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Kill, target = "F9", answer = "A8",
        ". . . . . O X X ,",
        ". . O O O O X X ,",
        ". O X X X X X , ,",
        "X X X , , , , , ,",
    ),
    // 끊김을 이어 한 덩어리로 — 안의 백을 따내 그 자리로 눈을 만든다.
    problem(
        "ld.adv.5", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Live, target = "F9", answer = "D8",
        "O . O . . X O , ,",
        ". X X . . X O , ,",
        "X O O X X O O , ,",
        "O O O O O O , , ,",
    ),
    // 1선 여섯 칸의 한가운데 치중 — 궁도가 둘로 나뉘어 어느 쪽도 눈이 안 된다.
    problem(
        "ld.adv.6", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Kill, target = "A8", answer = "D9",
        ". . . . . . X X ,",
        "O O O . O . X X ,",
        "X X X O X O X X ,",
        ", , X X X X X , ,",
    ),
    // 안에 백 한 점이 든 궁도 — 귀 쪽부터 지키고 안의 백을 따낸다.
    problem(
        "ld.adv.7", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Live, target = "A7", answer = "B8",
        ". . . . X O O , ,",
        ". . O . . X O , ,",
        "X X X X X O O , ,",
        "O O O O O O , , ,",
    ),
    // 1선 치중 뒤 돌을 주고받아도 백의 눈은 하나.
    problem(
        "ld.adv.8", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Kill, target = "B8", answer = "E9",
        ". . . . . . O X ,",
        ". O O O O O X X ,",
        "X X X X X X X , ,",
    ),
    // 1선을 먼저 이어 두면 백이 들어올 때 양단수가 난다.
    problem(
        "ld.adv.9", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Live, target = "B7", answer = "B9",
        ". . X O . X O , ,",
        "X . . . X O O , ,",
        "O X X X O O , , ,",
        "O O O O O , , , ,",
    ),
    // 귀의 1선 2·1이 급소 — 백이 오른쪽에서 따내도 귀에 눈이 없다.
    problem(
        "ld.adv.10", LifeAndDeathUnit.Advanced, LifeAndDeathGoal.Kill, target = "A8", answer = "B9",
        ". . . O X . O X ,",
        "O O . . . O X X ,",
        "O X O O O X X , ,",
        "X X X X X X , , ,",
    ),
)

/** 한 단원의 문제(화면의 순서). */
fun lifeAndDeathProblemsOf(unit: LifeAndDeathUnit): List<LifeAndDeathProblem> =
    lifeAndDeathProblems.filter { it.unit == unit }

/** 위 줄부터 적고, 적지 않은 아래 줄은 문제 밖(`,`)으로 채운다 — 문제가 모두 위쪽에 있어 그림이 짧아진다. */
private fun problem(
    id: String,
    unit: LifeAndDeathUnit,
    goal: LifeAndDeathGoal,
    target: String,
    answer: String,
    vararg rows: String,
): LifeAndDeathProblem =
    LifeAndDeathProblem(
        id = id,
        unit = unit,
        goal = goal,
        toPlay = StoneColor.Black,
        target = target,
        answer = answer,
        diagram = rows.toList() + List(BoardLines - rows.size) { OutsideRow },
    )

private const val BoardLines = 9
private const val OutsideRow = ", , , , , , , , ,"
