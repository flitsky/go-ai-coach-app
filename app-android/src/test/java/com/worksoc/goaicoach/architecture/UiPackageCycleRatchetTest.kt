package com.worksoc.goaicoach.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **ui 하위 패키지 래칫**(refactor backlog #27 C1b, C17에서 보강) — app-android `ui/` 트리(프로덕션 소스)의
 * 하위 패키지 16개 사이에 사이클이 생기거나 간선이 층(L0~L8)을 거슬러 오르는 것을 막는다.
 *
 * ## 왜 거는가
 * 분할 전의 `ui`는 한 패키지라 파일 사이 import가 아예 없었다 — 참조 그래프를 읽을 수조차 없었다.
 * 분할로 의존이 전부 import로 드러났고, 그 import가 **위층을 향하는** 순간 설계(SCC 0, 층 순서)가 무너진다.
 * 컴파일러는 그것을 막지 않는다. 그래서 첫 이동보다 먼저(C1b) 걸었고, 분할이 끝난 C17에서 예외를 걷었다.
 *
 * ## 무엇을 재는가 — 전부 예외 없이
 *  1. **사이클 0**: [PackageImportGraph]의 SCC(크기 2 이상)와 상호 참조 쌍이 **하나도 없다**. 기준선은 없다 —
 *     C17 전까지 있던 `UI_CYCLE_BASELINE_*`는 C7부터 비어 있었고, 지금은 "0"이 설계 자체다.
 *  2. **층**: [ContractSymbols.UI_LAYERS]의 순번이 층이다. 간선 A→B는 층(B) < 층(A)여야 한다 — 같은 층도 금지.
 *     실재하는 ui 패키지는 전부 배정표에 있어야 하고, 배정표의 패키지는 전부 실재해야 한다.
 *  3. **맨 아래층(L0)은 ui의 다른 패키지를 모른다** — `#27` 요구 *"l10n은 ui 기능 패키지를 모른다"*를
 *     2번의 따름정리로 두지 않고 따로 못박는다(층 표가 잘못 고쳐져도 이 문장은 살아 있어야 한다).
 *  4. **루트 0파일, 선언 = 디렉터리**: [ContractSymbols.UI_ROOT_PACKAGE]에는 파일이 없고, 파일마다
 *     `package` 선언이 제 디렉터리와 같다. 디렉터리가 층을 속이면(`play/`에 두고 `shell`로 선언) 2번은
 *     선언을 믿으므로 통과하지만, 사람과 [RepoPaths.uiFile]은 디렉터리를 믿는다.
 *
 * 판정은 순수 함수 [UiLayerVerdict]에 있고 [scannerAndVerdictsCatchPlantedUiViolations]가 합성 입력으로
 * 그 판정 자체를 시험한다(함정 24: 초록은 안전이 아니다).
 *
 * ⚠️ 보는 것은 `src/main`의 `ui/`뿐이다 — 단위 테스트 소스는 제 패키지(`ui` 루트 등)에 있어도 프로덕션 그래프가 아니다.
 */
class UiPackageCycleRatchetTest {

    /** SCC 0 — 사이클도, 그 씨앗인 상호 참조 쌍도 없다. */
    @Test
    fun uiPackageGraphHasNoCycle() {
        assertEquals(
            "ui 하위 패키지 사이에 사이클이 생겼다 — 설계는 SCC 0이다. 한쪽 방향을 끊어라: 위층의 선언을 " +
                "아래층으로 내리거나, 아래층이 위층을 부르는 import를 없애라(refactor backlog #27)." +
                currentCyclesReport() + currentPairsReport(),
            emptySet<Set<String>>() to emptySet<Pair<String, String>>(),
            graph.cycles to graph.mutualPairs,
        )
    }

    /** 간선이 층을 거슬러 오르지 않는다 — 루트는 0파일이고, 실재하는 ui 하위 패키지는 전부 층을 배정받았다. */
    @Test
    fun uiEdgesOnlyPointToLowerLayers() {
        val violations = UiLayerVerdict.violations(
            packages = graph.packages,
            edges = graph.edgeReferenceCounts.keys,
            layers = ContractSymbols.UI_LAYERS,
            root = ContractSymbols.UI_ROOT_PACKAGE,
        )

        assertEquals(
            "ui 하위 패키지의 층 순서가 깨졌다 — 간선은 더 낮은 층으로만 간다(같은 층도 금지). 새 하위 패키지는 " +
                "ContractSymbols.UI_LAYERS에 층을 배정하라. 위층을 불러야 한다면 그 선언을 아래층으로 내려라" +
                "(refactor backlog #27).\n" + violations.joinToString("\n") { "  - ${short(it)}" },
            emptyList<String>(),
            violations,
        )
    }

    /** 맨 아래층(L0)에서 ui의 다른 패키지로 나가는 간선은 0이다 — `l10n`·`designsystem`·`foundation`은 기능을 모른다. */
    @Test
    fun lowestUiLayerKnowsNoOtherUiPackage() {
        val leaving = UiLayerVerdict.edgesLeavingLowestLayer(graph.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS)

        assertEquals(
            "맨 아래층(L0)이 ui의 다른 패키지를 import 한다 — L0은 모든 화면이 쓰는 바닥이라 무엇도 부르면 안 된다. " +
                "필요한 선언을 L0으로 내려라(refactor backlog #27: l10n은 ui 기능 패키지를 모른다).\n" +
                leaving.joinToString("\n") { "  - ${short(it)}" },
            emptyList<String>(),
            leaving,
        )
    }

    /** `ui/` 바로 아래에 파일이 없고, 파일마다 `package` 선언이 제 디렉터리와 같다. */
    @Test
    fun uiRootHoldsNoFileAndEveryPackageMatchesItsDirectory() {
        val misplaced = UiLayerVerdict.misplacedFiles(
            graph.packageBySource.mapKeys { (label, _) -> label.replace('\\', '/') },
            ContractSymbols.UI_ROOT_PACKAGE,
        )

        assertEquals(
            "ui 파일이 제자리에 있지 않다 — 루트(`ui/` 바로 아래)는 0파일이고, `package` 선언은 디렉터리와 같아야 " +
                "한다. 층 판정은 선언을, 사람과 RepoPaths.uiFile은 디렉터리를 믿는다(refactor backlog #27).\n" +
                misplaced.joinToString("\n") { "  - ${short(it)}" },
            emptyList<String>(),
            misplaced,
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
     * 배정표와 현실이 **정확히 같은 패키지 집합**인가. 표에만 있는 이름은 층 판정이 허공을 보는 것이고,
     * 현실에만 있는 이름은 층 판정을 비켜 가는 것이다(후자는 [uiEdgesOnlyPointToLowerLayers]도 잡는다).
     */
    @Test
    fun uiLayerMapNamesExactlyTheRealUiPackages() {
        val mapped = ContractSymbols.UI_LAYERS.flatten().toSet()

        assertEquals(
            "UI_LAYERS와 실제 ui 패키지가 다르다 — 표에만: ${(mapped - graph.packages).sorted().map(::short)}, " +
                "현실에만: ${(graph.packages - mapped).sorted().map(::short)}(refactor backlog #27).",
            mapped,
            graph.packages,
        )
    }

    /**
     * 스캔이 **실제로 무언가를 봤는지**. `ui/`가 옮겨져 빈 그래프가 되면 SCC도 간선도 0이 되고, 사이클·층
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
     * 자기검증 — 스캐너와 판정기가 **일부러 만든 위반을 잡는가**. 오늘의 실제 그래프는 위반이 0이라 판정기가
     * "항상 통과"로 고장나 있어도 알 길이 없다 — 합성 ui 트리로 무는지 확인한다.
     *
     * 패키지 이름은 전부 [ContractSymbols.UI_LAYERS]·[ContractSymbols.UI_ROOT_PACKAGE]에서 꺼낸다 — 리터럴을
     * 적으면 [ContractSymbolContractTest]에 걸리고, **실제 배정표**로 판정해야 표가 판정기에 제대로 물려 있는지도 본다.
     * 라벨은 `ui/` 기준 상대 경로 모양으로 적는다 — 디렉터리 판정이 그것을 읽는다.
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
        fun verdictOf(g: PackageImportGraph) = UiLayerVerdict.violations(g.packages, g.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS, root)

        // 합법: shell(L8) → play(L5) → board(L3) → l10n(L0), 전부 제 디렉터리에, 루트는 비었다.
        val legal = PackageImportGraph.of(
            mapOf(
                "shell/S.kt" to "package $shell\n\nimport $play.P\n\nclass S\n",
                "play/P.kt" to "package $play\n\nimport $board.B\nimport $l10n.Strings\n\nclass P\n",
                "board/B.kt" to "package $board\n\nimport $l10n.Strings\n\nclass B\n",
                "l10n/L.kt" to "package $l10n\n\nclass Strings\n",
            ),
            root,
        )
        assertEquals(emptyList<String>(), legal.unresolvedReferences)
        assertEquals(emptySet<Set<String>>(), legal.cycles)
        assertEquals(emptyList<String>(), verdictOf(legal))
        assertEquals(emptyList<String>(), UiLayerVerdict.edgesLeavingLowestLayer(legal.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS))
        assertEquals(emptyList<String>(), UiLayerVerdict.misplacedFiles(legal.packageBySource, root))

        // 위층 간선(l10n L0 → play L5, inline FQN으로도 잡힌다) · 같은 층 간선(l10n → designsystem) · 배정 없는 패키지 ·
        // 루트에 남은 파일 · 디렉터리와 다른 선언(board/에 둔 play 파일).
        val illegal = PackageImportGraph.of(
            mapOf(
                "l10n/L.kt" to "package $l10n\n\nimport $designsystem.Theme\n\nclass Strings { val p = $play.P() }\n",
                "designsystem/D.kt" to "package $designsystem\n\nclass Theme\n",
                "play/P.kt" to "package $play\n\nclass P\n",
                "board/Wrong.kt" to "package $play\n\nclass Wrong\n",
                "zzstray/Z.kt" to "package $stray\n\nclass Z\n",
                "R.kt" to "package $root\n\nimport $l10n.Strings\n\nclass R\n",
            ),
            root,
        )
        val layerViolations = verdictOf(illegal)
        assertEquals(layerViolations.joinToString("\n"), 4, layerViolations.size)
        assertTrue(layerViolations.any { "위층" in it && l10n in it && play in it })
        assertTrue(layerViolations.any { "같은 층" in it && l10n in it && designsystem in it })
        assertTrue(layerViolations.any { "배정이 없는" in it && stray in it })
        assertTrue(layerViolations.any { "루트 패키지에 파일" in it && root in it })
        assertEquals(
            listOf("$l10n → $designsystem", "$l10n → $play"),
            UiLayerVerdict.edgesLeavingLowestLayer(illegal.edgeReferenceCounts.keys, ContractSymbols.UI_LAYERS),
        )
        val misplaced = UiLayerVerdict.misplacedFiles(illegal.packageBySource, root)
        assertEquals(misplaced.joinToString("\n"), 2, misplaced.size)
        assertTrue(misplaced.any { "루트 디렉터리" in it && "R.kt" in it })
        assertTrue(misplaced.any { "디렉터리와 다르다" in it && "board/Wrong.kt" in it && "$root.board" in it })

        // 사이클: 옮긴 패키지끼리 서로를(board ↔ play), 또는 하위 패키지가 루트를 다시(l10n ↔ 루트) import 한다.
        val cyclic = PackageImportGraph.of(
            mapOf(
                "board/B.kt" to "package $board\n\nimport $play.P\n\nclass B\n",
                "play/P.kt" to "package $play\n\nimport $board.B\n\nclass P\n",
                "l10n/L.kt" to "package $l10n\n\nimport $root.R\n\nclass Strings\n",
                "R.kt" to "package $root\n\nimport $l10n.Strings\n\nclass R\n",
            ),
            root,
        )
        assertEquals(setOf(setOf(board, play), setOf(l10n, root)), cyclic.cycles)
        assertEquals(2, cyclic.mutualPairs.size)
        // 층 판정은 사이클 판정과 따로 문다 — board ↔ play는 위층 간선(board L3 → play L5)으로, 루트는 파일로 잡힌다.
        val cyclicLayerViolations = verdictOf(cyclic)
        assertTrue(cyclicLayerViolations.any { "위층" in it && board in it && play in it })
        assertTrue(cyclicLayerViolations.any { "루트 패키지에 파일" in it })
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
 * ui 하위 패키지의 **층·배치 판정** — 그래프 스캔과 떼어 둔 순수 함수다([CycleRatchetVerdict]와 같은 이유: 자기검증이
 * 합성 입력으로 판정 자체를 시험한다).
 */
internal object UiLayerVerdict {

    /**
     * 층 위반 전부.
     *  - [layers]의 순번이 층이다. 간선 A→B는 층(B) < 층(A)일 때만 합법 — 같은 층도 위반이다.
     *  - [root]에 파일이 있으면(= [packages]에 있으면) 위반이다 — 분할 뒤 루트는 0파일이다(C17, 예외 없음).
     *  - [packages] 중 루트가 아닌데 [layers]에 없는 패키지는 위반이다 — 배정이 없으면 층 판정을 비켜 간다.
     *  - 루트·미배정 패키지가 낀 간선은 따로 세지 않는다 — 그 패키지 자체가 이미 위반으로 올라 있다.
     */
    fun violations(
        packages: Set<String>,
        edges: Collection<Pair<String, String>>,
        layers: List<List<String>>,
        root: String,
    ): List<String> {
        val layerOf = layerIndex(layers)
        val rootHoldsFiles = listOfNotNull(
            "ui 루트 패키지에 파일이 있다: $root — 분할 뒤 루트는 0파일이다. 알맞은 하위 패키지로 옮겨라"
                .takeIf { root in packages },
        )
        val unassigned = (packages - root).filter { it !in layerOf }.sorted()
            .map { "층 배정이 없는 ui 패키지: $it" }
        val misdirected = edges
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
        return rootHoldsFiles + unassigned + misdirected
    }

    /** 맨 아래층([layers]의 첫 층)에서 **나가는** 간선 전부(`from → to`, 정렬). 아래가 없으니 전부 위반이다. */
    fun edgesLeavingLowestLayer(edges: Collection<Pair<String, String>>, layers: List<List<String>>): List<String> {
        val lowest = layers.first().toSet()
        return edges.filter { (from, _) -> from in lowest }.map { (from, to) -> "$from → $to" }.sorted()
    }

    /**
     * 제자리가 아닌 파일 전부. [packageByPath]의 키는 `ui/` 기준 상대 경로(`/` 구분)다.
     *  - 디렉터리 없이 루트 바로 아래 있는 파일은 위반이다.
     *  - 선언한 패키지가 `root + 디렉터리`와 다르면 위반이다.
     */
    fun misplacedFiles(packageByPath: Map<String, String>, root: String): List<String> =
        packageByPath.entries.sortedBy { it.key }.mapNotNull { (path, declared) ->
            val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
            val expected = "$root.${directory.replace('/', '.')}"
            when {
                directory.isEmpty() -> "ui 루트 디렉터리에 파일이 있다: $path (package $declared)"
                declared != expected -> "패키지 선언이 디렉터리와 다르다: $path — 선언 $declared, 디렉터리대로면 $expected"
                else -> null
            }
        }

    private fun layerIndex(layers: List<List<String>>): Map<String, Int> =
        layers.flatMapIndexed { index, layer -> layer.map { it to index } }.toMap()
}
