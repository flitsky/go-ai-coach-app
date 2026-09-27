package com.worksoc.goaicoach.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **ui 하위 패키지 래칫**(refactor backlog #27 C1b) — app-android `ui/` 트리를 하위 패키지로 나누는 동안과
 * 그 뒤에, 패키지 사이에 사이클이 생기거나 간선이 층(L0~L8)을 거슬러 오르는 것을 막는다.
 *
 * ## 왜 지금 거는가
 * 분할 전의 `ui`는 한 패키지라 파일 사이 import가 아예 없었다 — 참조 그래프를 읽을 수조차 없었다.
 * 이동 커밋(C2~C16)이 파일을 옮길 때마다 의존이 import로 드러나는데, 그 import가 **위층을 향하는** 순간
 * 설계(SCC 0, 층 순서)는 첫 커밋부터 무너진다. 컴파일러는 그것을 막지 않는다. 그래서 첫 이동보다 먼저 건다.
 *
 * ## 무엇을 재는가
 *  1. **사이클**: [PackageCycleRatchetTest]와 같은 규칙([PackageImportGraph])·같은 판정기([CycleRatchetVerdict])로
 *     [ContractSymbols.UI_CYCLE_BASELINE_SCCS]·[ContractSymbols.UI_CYCLE_BASELINE_MUTUAL_PAIRS]와 비교한다.
 *     나빠지면 빨갛고, 좋아진 채 기준선이 그대로여도 빨갛다(래칫의 톱니).
 *  2. **층**: [ContractSymbols.UI_LAYERS]의 순번이 층이다. 간선 A→B는 층(B) < 층(A)여야 한다 — 같은 층도 금지.
 *     판정은 순수 함수 [UiLayerVerdict]에 있고 [scannerAndVerdictsCatchPlantedUiViolations]가 합성 입력으로
 *     그 판정 자체를 시험한다(함정 24: 초록은 안전이 아니다).
 *
 * ⚠️ **분할 중의 루트 예외** — [ContractSymbols.UI_ROOT_PACKAGE]에는 아직 안 옮긴 파일이 층을 가리지 않고
 * 섞여 있으므로 루트가 낀 간선은 층 판정에서 뺀다. 루트가 끼는 사이클은 1번(기준선)이 지킨다 — 옮겨 간
 * 패키지가 루트를 다시 import 하면 루트와 상호 참조 쌍이 생겨 빨개진다. 루트가 비는 C16 뒤에 C17이 이 예외를
 * 없애고 "루트 0파일"을 못박는다.
 */
class UiPackageCycleRatchetTest {

    /** 기준선보다 **나빠진** SCC — 새 패키지가 사이클에 들어왔거나 SCC 둘이 합쳐졌다. */
    @Test
    fun noUiPackageJoinsACycleBeyondTheBaseline() {
        val verdict = CycleRatchetVerdict.forCycles(ContractSymbols.UI_CYCLE_BASELINE_SCCS, graph.cycles)

        assertEquals(
            "ui 패키지 사이클이 기준선보다 커졌다 — 옮긴 패키지가 위층이나 루트를 import 했다. " +
                "기준선을 늘리지 말고 의존 방향을 고쳐라(refactor backlog #27).\n" +
                verdict.worse.joinToString("\n") { "  - ${short(it)}" } + currentCyclesReport(),
            emptyList<String>(),
            verdict.worse,
        )
    }

    /** 기준선에 없는 **새 상호 참조 쌍** — 사이클의 씨앗이다. */
    @Test
    fun noNewMutualImportPairAppearsInUi() {
        val verdict = CycleRatchetVerdict.forMutualPairs(ContractSymbols.UI_CYCLE_BASELINE_MUTUAL_PAIRS, graph.mutualPairs)

        assertEquals(
            "ui 안에 기준선에 없던 상호 참조 쌍이 생겼다 — 두 패키지가 서로를 import 한다. 한쪽 방향을 " +
                "끊어라(refactor backlog #27).\n" + verdict.worse.joinToString("\n") { "  - ${short(it)}" } +
                currentPairsReport(),
            emptyList<String>(),
            verdict.worse,
        )
    }

    /**
     * 기준선보다 **좋아졌는데 기준선이 그대로**인 상태 — 이것이 래칫의 톱니다.
     *
     * ⭐ 루트 ↔ `ui.vision` 사이클은 `GoBoard`가 `ui.board`로 옮겨지는 C7에서 끊긴다. 그 커밋에서 여기가
     * 빨개지는 것이 **정상**이다 — [ContractSymbols]의 두 기준선을 비워 잠가라.
     */
    @Test
    fun uiCycleBaselineIsShrunkWhenRealityImproves() {
        val cycles = CycleRatchetVerdict.forCycles(ContractSymbols.UI_CYCLE_BASELINE_SCCS, graph.cycles)
        val pairs = CycleRatchetVerdict.forMutualPairs(ContractSymbols.UI_CYCLE_BASELINE_MUTUAL_PAIRS, graph.mutualPairs)
        val improvements = cycles.better + pairs.better

        assertEquals(
            "ui 사이클이 기준선보다 줄었다 — 기준선을 줄여라. ContractSymbols.UI_CYCLE_BASELINE_SCCS / " +
                "UI_CYCLE_BASELINE_MUTUAL_PAIRS를 아래 현재 값으로 고쳐 줄어든 것을 잠가야 다시 커지지 " +
                "않는다(refactor backlog #27).\n" + improvements.joinToString("\n") { "  - ${short(it)}" } +
                currentCyclesReport() + currentPairsReport(),
            emptyList<String>(),
            improvements,
        )
    }

    /** 간선이 층을 거슬러 오르지 않는다 — 그리고 실재하는 ui 하위 패키지는 전부 층을 배정받았다. */
    @Test
    fun uiEdgesOnlyPointToLowerLayers() {
        val violations = UiLayerVerdict.violations(
            packages = graph.packages,
            edges = graph.edgeReferenceCounts.keys,
            layers = ContractSymbols.UI_LAYERS,
            unsplitRoot = ContractSymbols.UI_ROOT_PACKAGE,
        )

        assertEquals(
            "ui 하위 패키지의 층 순서가 깨졌다 — 간선은 더 낮은 층으로만 간다(같은 층도 금지). 새 하위 패키지는 " +
                "ContractSymbols.UI_LAYERS에 층을 배정하라. 위층을 불러야 한다면 그 선언을 아래층으로 내려라" +
                "(refactor backlog #27).\n" + violations.joinToString("\n") { "  - ${short(it)}" },
            emptyList<String>(),
            violations,
        )
    }

    /**
     * 층 배정표 자체가 설계 모양인가 — 아홉 층, 패키지 열여섯, 겹침 없음, 전부 루트 **바로 아래** 한 조각.
     * 표가 망가지면(중복·오타로 두 층에 같은 이름) 판정기가 엉뚱한 층을 읽는다.
     */
    @Test
    fun uiLayerMapHasTheDesignedShape() {
        val all = ContractSymbols.UI_LAYERS.flatten()
        val prefix = "${ContractSymbols.UI_ROOT_PACKAGE}."

        assertEquals("층 수가 설계(L0~L8)와 다르다.", 9, ContractSymbols.UI_LAYERS.size)
        assertTrue("빈 층이 있다.", ContractSymbols.UI_LAYERS.all { it.isNotEmpty() })
        assertEquals("패키지 수가 설계(16)와 다르다.", 16, all.size)
        assertEquals("두 층에 같은 패키지가 있다: ${all.groupBy { it }.filterValues { it.size > 1 }.keys}", all.size, all.toSet().size)
        assertEquals(
            "루트 바로 아래 한 조각이 아닌 이름이 있다.",
            emptyList<String>(),
            all.filterNot { it.startsWith(prefix) && it.removePrefix(prefix).matches(Regex("""[a-z][a-z0-9]*""")) },
        )
    }

    /**
     * 스캔이 **실제로 무언가를 봤는지**. `ui/`가 옮겨져 빈 그래프가 되면 SCC도 간선도 0이 되고, 나빠짐·층
     * 테스트는 무조건 초록이 된다. 풀리지 않는 참조가 생겼다면 패키지 해석 규칙이 소스와 어긋난 것이다.
     */
    @Test
    fun scanSeesTheWholeUiTree() {
        assertTrue("ui .kt를 거의 못 읽었다(${graph.fileCount}개) — 경로가 낡았다.", graph.fileCount >= 100)
        assertTrue("ui 패키지를 둘 이상 못 찾았다(${graph.packages.size}개).", graph.packages.size >= 2)
        assertTrue("ui 안 간선을 하나도 못 찾았다 — 스캔이 헛돈다.", graph.edgeReferenceCounts.isNotEmpty())
        assertEquals(
            "알려진 패키지로 풀리지 않는 ui 참조가 있다 — 해석 규칙이 소스와 어긋났다.",
            emptyList<String>(),
            graph.unresolvedReferences,
        )
        assertEquals(
            "스캔이 RepoPaths.uiSourceFiles()와 다른 파일 집합을 봤다.",
            RepoPaths.uiSourceFiles().size,
            graph.fileCount,
        )
    }

    /**
     * 자기검증 — 스캐너와 두 판정기가 **일부러 만든 위반을 잡는가**. 오늘의 실제 그래프는 루트 예외 때문에
     * 층 판정이 사실상 아무것도 안 보고 통과한다. 그래서 이 판정기가 "항상 통과"로 고장나 있어도
     * 알 길이 없다 — 합성 ui 트리로 무는지 확인한다.
     *
     * 패키지 이름은 전부 [ContractSymbols.UI_LAYERS]·[ContractSymbols.UI_ROOT_PACKAGE]에서 꺼낸다 — 리터럴을
     * 적으면 [ContractSymbolContractTest]에 걸리고, **실제 배정표**로 판정해야 표가 판정기에 제대로 물려 있는지도 본다.
     */
    @Test
    fun scannerAndVerdictsCatchPlantedUiViolations() {
        val root = ContractSymbols.UI_ROOT_PACKAGE
        fun pkg(name: String) = ContractSymbols.UI_LAYERS.flatten().single { it == "$root.$name" }
        val l10n = pkg("l10n")
        val designsystem = pkg("designsystem")
        val board = pkg("board")
        val play = pkg("play")
        val shell = pkg("shell")
        val stray = "$root.zzstray"

        // 합법: shell(L8) → play(L5) → board(L3) → l10n(L0). 루트는 아무 쪽이든 참조할 수 있다(분할 중 예외).
        val legal = PackageImportGraph.of(
            mapOf(
                "S.kt" to "package $shell\n\nimport $play.P\n\nclass S\n",
                "P.kt" to "package $play\n\nimport $board.B\nimport $l10n.Strings\n\nclass P\n",
                "B.kt" to "package $board\n\nimport $l10n.Strings\n\nclass B\n",
                "L.kt" to "package $l10n\n\nclass Strings\n",
                "R.kt" to "package $root\n\nimport $shell.S\nimport $l10n.Strings\n\nclass R\n",
            ),
            root,
        )
        assertEquals(emptyList<String>(), legal.unresolvedReferences)
        assertEquals(emptySet<Set<String>>(), legal.cycles)
        assertEquals(emptyList<String>(), UiLayerVerdict.violations(legal.packages, legal.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS, root))

        // 위층 간선(l10n L0 → play L5, inline FQN으로도 잡힌다) · 같은 층 간선(l10n → designsystem) · 배정 없는 패키지.
        val illegal = PackageImportGraph.of(
            mapOf(
                "L.kt" to "package $l10n\n\nimport $designsystem.Theme\n\nclass Strings { val p = $play.P() }\n",
                "D.kt" to "package $designsystem\n\nclass Theme\n",
                "P.kt" to "package $play\n\nclass P\n",
                "Z.kt" to "package $stray\n\nclass Z\n",
            ),
            root,
        )
        val layerViolations = UiLayerVerdict.violations(illegal.packages, illegal.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS, root)
        assertEquals(3, layerViolations.size)
        assertTrue(layerViolations.any { "위층" in it && l10n in it && play in it })
        assertTrue(layerViolations.any { "같은 층" in it && l10n in it && designsystem in it })
        assertTrue(layerViolations.any { "배정이 없는" in it && stray in it })

        // 사이클: 옮긴 패키지끼리 서로를(board ↔ play), 또는 옮긴 패키지가 루트를 다시(l10n ↔ 루트) import 하면 기준선 대비 나빠짐이다.
        val cyclic = PackageImportGraph.of(
            mapOf(
                "B.kt" to "package $board\n\nimport $play.P\n\nclass B\n",
                "P.kt" to "package $play\n\nimport $board.B\n\nclass P\n",
                "L.kt" to "package $l10n\n\nimport $root.R\n\nclass Strings\n",
                "R.kt" to "package $root\n\nimport $l10n.Strings\n\nclass R\n",
            ),
            root,
        )
        assertEquals(setOf(setOf(board, play), setOf(l10n, root)), cyclic.cycles)
        val baseline = ContractSymbols.UI_CYCLE_BASELINE_SCCS
        assertTrue(CycleRatchetVerdict.forCycles(baseline, cyclic.cycles).worse.isNotEmpty())
        assertTrue(
            CycleRatchetVerdict.forMutualPairs(ContractSymbols.UI_CYCLE_BASELINE_MUTUAL_PAIRS, cyclic.mutualPairs)
                .worse.size == 2,
        )
        // 같은 층·위층 판정은 사이클 판정과 따로 문다 — board ↔ play는 위층 간선(board L3 → play L5)으로도 잡힌다.
        assertTrue(
            UiLayerVerdict.violations(cyclic.packages, cyclic.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS, root)
                .any { "위층" in it && board in it && play in it },
        )
    }

    private fun currentCyclesReport(): String =
        "\n현재 SCC(크기 2 이상):\n" + graph.cycles.sortedBy { it.min() }
            .joinToString("\n") { component -> "  (${component.size}) " + component.sorted().joinToString(", ") { short(it) } }

    private fun currentPairsReport(): String =
        "\n현재 상호 참조 쌍(A→B/B→A 건수):\n" + graph.mutualPairs.sortedBy { it.toString() }.joinToString("\n") { (a, b) ->
            "  ${short(a)} <-> ${short(b)} (${graph.edgeReferenceCounts[a to b]}/${graph.edgeReferenceCounts[b to a]})"
        }

    /** 메시지를 읽기 쉽게 앱 루트 접두어를 뗀다(`ui`, `ui.vision` …로 보인다). */
    private fun short(text: String): String = text.replace("$APP_ROOT_PACKAGE.", "")

    private companion object {
        val APP_ROOT_PACKAGE: String = ContractSymbols.MAIN_ACTIVITY.substringBeforeLast('.')

        /** 한 번만 훑는다 — 테스트들이 같은 그래프를 본다. */
        val graph: PackageImportGraph by lazy {
            PackageImportGraph.scan(RepoPaths.uiRoot, ContractSymbols.UI_ROOT_PACKAGE)
        }
    }
}

/**
 * ui 하위 패키지의 **층 판정** — 그래프 스캔과 떼어 둔 순수 함수다([CycleRatchetVerdict]와 같은 이유: 자기검증이
 * 합성 입력으로 판정 자체를 시험한다).
 *
 *  - [layers]의 순번이 층이다. 간선 A→B는 층(B) < 층(A)일 때만 합법 — 같은 층도 위반이다.
 *  - [unsplitRoot](분할 중인 루트)가 낀 간선은 보지 않는다(루트는 층이 섞인 나머지다).
 *  - [packages] 중 루트가 아닌데 [layers]에 없는 패키지는 위반이다 — 배정이 없으면 층 판정을 비켜 간다.
 */
internal object UiLayerVerdict {

    fun violations(
        packages: Set<String>,
        edges: Collection<Pair<String, String>>,
        layers: List<List<String>>,
        unsplitRoot: String,
    ): List<String> {
        val layerOf: Map<String, Int> = layers.flatMapIndexed { index, layer -> layer.map { it to index } }.toMap()
        val unassigned = (packages - unsplitRoot).filter { it !in layerOf }.sorted()
            .map { "층 배정이 없는 ui 패키지: $it" }
        val misdirected = edges
            .filter { (from, to) -> from != unsplitRoot && to != unsplitRoot }
            .mapNotNull { (from, to) ->
                val fromLayer = layerOf[from] ?: return@mapNotNull null
                val toLayer = layerOf[to] ?: return@mapNotNull null
                when {
                    toLayer > fromLayer -> "위층으로 오르는 간선: $from(L$fromLayer) → $to(L$toLayer)"
                    toLayer == fromLayer -> "같은 층 간선: $from → $to (둘 다 L$fromLayer)"
                    else -> null
                }
            }
            .sorted()
        return unassigned + misdirected
    }
}
