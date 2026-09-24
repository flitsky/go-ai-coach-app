package com.worksoc.goaicoach.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **패키지 사이클 래칫**(refactor backlog #33) — `shared/src/commonMain`의 패키지 import 그래프가
 * 기준선보다 나빠지는 것도, 좋아진 채 기준선이 그대로 남는 것도 막는다.
 *
 * ## 왜 "나빠지면 실패"만으로는 안 되는가
 * 사이클을 끊는 작업(`#32`)이 SCC를 줄여도 기준선이 그대로면, 끊어진 자리가 **다시 이어져도 초록**이다
 * — 기준선이 허락하는 한도 안이기 때문이다. 줄어든 것을 그 자리에서 잠가야 래칫이 된다. 그래서
 * 좋아지면 [cycleBaselineIsShrunkWhenRealityImproves]가 **"기준선을 줄여라"** 로 빨개진다.
 *
 * ## 무엇을 재는가
 * 규칙은 [PackageImportGraph]에 있다(설계 스레드의 `scc.py`와 같은 규칙). 기준선은
 * [ContractSymbols.CYCLE_BASELINE_SCCS]·[ContractSymbols.CYCLE_BASELINE_MUTUAL_PAIRS]에 있다 —
 * 패키지 이름도 FQN이라 등록부 밖에 적으면 [ContractSymbolContractTest]가 막는다. 등록부에 둔 덕에
 * 기준선 구성원은 실존 검사도 받는다.
 *
 * ⚠️ 노드·간선 총수는 래칫하지 않는다 — 사이클과 무관한 이동(예: #70의 shared.engine 흡수)으로도 바뀐다.
 * 최종 목표(SCC 0)는 `#32` 이후의 일이고, 지금은 현재를 잠근다.
 */
class PackageCycleRatchetTest {

    /** 기준선보다 **나빠진** SCC — 새 패키지가 사이클에 들어왔거나 SCC 둘이 합쳐졌다. */
    @Test
    fun noPackageJoinsACycleBeyondTheBaseline() {
        val verdict = CycleRatchetVerdict.forCycles(ContractSymbols.CYCLE_BASELINE_SCCS, graph.cycles)

        assertEquals(
            "패키지 사이클이 기준선보다 커졌다 — 새로 만든 import가 사이클에 패키지를 끌어들였다. " +
                "기준선을 늘리지 말고 의존 방향을 고쳐라(refactor backlog #33).\n" +
                verdict.worse.joinToString("\n") { "  - ${short(it)}" } + currentCyclesReport(),
            emptyList<String>(),
            verdict.worse,
        )
    }

    /** 기준선에 없는 **새 상호 참조 쌍** — 사이클의 씨앗이다. SCC 안끼리라도 새 쌍은 막는다. */
    @Test
    fun noNewMutualImportPairAppears() {
        val verdict = CycleRatchetVerdict.forMutualPairs(ContractSymbols.CYCLE_BASELINE_MUTUAL_PAIRS, graph.mutualPairs)

        assertEquals(
            "기준선에 없던 상호 참조 쌍이 생겼다 — 두 패키지가 서로를 import 한다. 한쪽 방향을 " +
                "끊어라(refactor backlog #33).\n" + verdict.worse.joinToString("\n") { "  - ${short(it)}" } +
                currentPairsReport(),
            emptyList<String>(),
            verdict.worse,
        )
    }

    /**
     * 기준선보다 **좋아졌는데 기준선이 그대로**인 상태 — 이것이 래칫의 톱니다.
     *
     * 빨개졌다면 축하할 일이다. 메시지의 "현재 값"대로 [ContractSymbols]의 기준선을 **줄여** 잠가라.
     * 그대로 두면 끊어진 사이클이 다시 이어져도 아무것도 빨개지지 않는다.
     */
    @Test
    fun cycleBaselineIsShrunkWhenRealityImproves() {
        val cycles = CycleRatchetVerdict.forCycles(ContractSymbols.CYCLE_BASELINE_SCCS, graph.cycles)
        val pairs = CycleRatchetVerdict.forMutualPairs(ContractSymbols.CYCLE_BASELINE_MUTUAL_PAIRS, graph.mutualPairs)
        val improvements = cycles.better + pairs.better

        assertEquals(
            "사이클이 기준선보다 줄었다 — 기준선을 줄여라. ContractSymbols.CYCLE_BASELINE_SCCS / " +
                "CYCLE_BASELINE_MUTUAL_PAIRS를 아래 현재 값으로 고쳐 줄어든 것을 잠가야 다시 커지지 " +
                "않는다(refactor backlog #33).\n" + improvements.joinToString("\n") { "  - ${short(it)}" } +
                currentCyclesReport() + currentPairsReport(),
            emptyList<String>(),
            improvements,
        )
    }

    /**
     * 스캔이 **실제로 무언가를 봤는지**. 소스 루트가 옮겨져 빈 그래프가 되면 SCC도 0이 되고, 그건
     * "나빠짐" 테스트 둘을 초록으로 만든다(좋아짐 테스트만 빨개지는데, 그 메시지는 "축하"로 읽힌다).
     * 풀리지 않는 참조가 생겼다면 패키지 해석 규칙이 소스와 어긋난 것이다.
     */
    @Test
    fun scanSeesTheWholeCommonMainGraph() {
        assertTrue("commonMain .kt를 거의 못 읽었다(${graph.fileCount}개) — 경로가 낡았다.", graph.fileCount > 100)
        assertTrue("패키지를 거의 못 찾았다(${graph.packages.size}개).", graph.packages.size > 20)
        assertTrue("간선을 거의 못 찾았다(${graph.edgeReferenceCounts.size}개).", graph.edgeReferenceCounts.size > 100)
        assertEquals(
            "알려진 패키지로 풀리지 않는 참조가 있다 — 해석 규칙이 소스와 어긋났다.",
            emptyList<String>(),
            graph.unresolvedReferences,
        )
    }

    /**
     * 자기검증 — 스캐너와 판정기가 **일부러 만든 위반을 잡는가**(함정 24: 초록은 안전이 아니다).
     *
     * 합성 소스의 패키지 이름은 [ContractSymbols.MAIN_ACTIVITY]에서 파생시킨다 — 리터럴 FQN을 적으면
     * [ContractSymbolContractTest]에 걸리고, 루트가 바뀌어도 이 픽스처가 따라가게 하려는 것이다.
     */
    @Test
    fun scannerAndVerdictCatchPlantedViolations() {
        val root = ContractSymbols.MAIN_ACTIVITY.substringBeforeLast('.')
        val (x, y, z, w) = listOf("$root.fx", "$root.fy", "$root.fz", "$root.fw")
        val planted = PackageImportGraph.of(
            mapOf(
                // x → y: 평범한 import.
                "X.kt" to "package $x\n\nimport $y.Foo\n\nclass X\n",
                // y → z: import 없이 쓴 inline FQN. 주석·KDoc·문자열·raw string 속 w 언급은 간선이 아니다.
                "Y.kt" to "package $y\n/** see $w.Nope */\n// $w.Nope\n/* /* nested $w.A */ $w.B */\n" +
                    "class Foo { val s = \"$w.Nope\"; val r = \"\"\"\n$w.Nope\n\"\"\"; val p = $z.Bar() }\n",
                // z → x: 중첩 클래스 import는 가장 긴 패키지 접두어(x)로 풀린다. 같은 패키지 참조는 간선이 아니다.
                "Z.kt" to "package $z\n\nimport $x.Outer.Inner\nimport $z.Bar\n\nclass Bar\n",
                "W.kt" to "package $w\n\nclass Nope\n",
            ),
            root,
        )

        assertEquals(setOf(setOf(x, y, z)), planted.cycles)
        assertEquals(emptySet<Pair<String, String>>(), planted.mutualPairs)
        assertEquals(setOf(x to y, y to z, z to x), planted.edgeReferenceCounts.keys)
        assertEquals(emptyList<String>(), planted.unresolvedReferences)

        // 판정기: 같으면 조용, 커지면 나빠짐, 줄면 좋아짐, 합쳐지면 나빠짐.
        val base = listOf(setOf(x, y), setOf(z, w))
        assertEquals(CycleRatchetVerdict(emptyList(), emptyList()), CycleRatchetVerdict.forCycles(base, base))
        assertTrue(CycleRatchetVerdict.forCycles(listOf(setOf(x, y)), listOf(setOf(x, y, z))).let { it.worse.isNotEmpty() && it.better.isEmpty() })
        assertTrue(CycleRatchetVerdict.forCycles(base, listOf(setOf(x, y))).let { it.worse.isEmpty() && it.better.size == 1 })
        assertTrue(CycleRatchetVerdict.forCycles(base, listOf(setOf(x, y, z, w))).worse.isNotEmpty())
        val pairs = CycleRatchetVerdict.forMutualPairs(listOf(x to y), listOf(z to y))
        assertEquals(1, pairs.worse.size)
        assertEquals(1, pairs.better.size)
        assertEquals(CycleRatchetVerdict(emptyList(), emptyList()), CycleRatchetVerdict.forMutualPairs(listOf(x to y), listOf(y to x)))
    }

    private fun currentCyclesReport(): String =
        "\n현재 SCC(크기 2 이상):\n" + graph.cycles.sortedBy { it.min() }
            .joinToString("\n") { component -> "  (${component.size}) " + component.sorted().joinToString(", ") { short(it) } }

    private fun currentPairsReport(): String =
        "\n현재 상호 참조 쌍(A→B/B→A 건수):\n" + graph.mutualPairs.sortedBy { it.toString() }.joinToString("\n") { (a, b) ->
            "  ${short(a)} <-> ${short(b)} (${graph.edgeReferenceCounts[a to b]}/${graph.edgeReferenceCounts[b to a]})"
        }

    /** 메시지를 읽기 쉽게 루트 접두어를 뗀다. */
    private fun short(text: String): String = text.replace("$ROOT_PACKAGE.", "")

    private companion object {
        val ROOT_PACKAGE: String = ContractSymbols.MAIN_ACTIVITY.substringBeforeLast('.')

        /** 한 번만 훑는다 — 테스트 넷이 같은 그래프를 본다. */
        val graph: PackageImportGraph by lazy {
            PackageImportGraph.scan(RepoPaths.sharedCommonMainKotlin, ROOT_PACKAGE)
        }
    }
}
