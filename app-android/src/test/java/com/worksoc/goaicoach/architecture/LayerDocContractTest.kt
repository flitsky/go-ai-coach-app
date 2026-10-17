package com.worksoc.goaicoach.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **계층 문서의 「계층 배정」 블록은 코드의 배정 목록과 같다**(2026-10-17 — 아키텍처 문서를 계층마다 한 파일로 나눴다).
 *
 * 배정의 정본은 [ContractSymbols]의 계층 목록이고, `docs/architecture/LAYER_N_*.md`의 블록은 사람이 읽으려고 둔
 * 사본이다. 예전에는 문서가 정본이고 *"두 곳을 함께 고친다"* 는 손 규칙이었는데, 손으로 맞추다 `shared.playstyle`·
 * `shared.content`가 문서의 5계층 절에서 빠져 있었다. 이 계약은 그 어긋남을 **양방향으로** 잡는다 — 코드의 그 계층에
 * 있는데 문서에 없는 패키지, 문서에 있는데 코드의 그 계층에 없는 패키지.
 *
 * 블록 모양(문서 쪽): `<!-- layer-packages: 목록이름 -->` 바로 다음 줄의 ` ```text ` 블록, 줄마다 패키지 하나(루트
 * 패키지 접두는 뺀다). ui 층은 `<!-- ui-layers: UI_LAYERS -->` 다음 블록, 줄마다 `L<n> 패키지 …`(`ui.` 접두도 뺀다).
 * 판정은 [LayerDocBlocks]가 한다 — 자기검증이 심은 어긋남으로 같은 판정기를 시험한다.
 */
class LayerDocContractTest {

    @Test
    fun everyLayerDocBlockMatchesTheCodeAssignment() {
        val offenders = LAYER_DOCS.flatMap { (fileName, listName, assigned) ->
            val doc = RepoPaths.architectureDocs.resolve(fileName)
            LayerDocBlocks.packageDrift(doc.readContractSource(), listName, assigned.map { it.removePrefix(ROOT_PREFIX) })
                .map { "$fileName ($listName): $it" }
        }
        assertEquals(
            "계층 문서의 「계층 배정」 블록이 ContractSymbols와 다르다 — 정본은 코드다. 코드를 바꿨다면 문서 블록을 " +
                "따라 고치고, 문서만 고쳤다면 되돌려라.\n" + offenders.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun uiLayerBlockMatchesUiLayers() {
        val doc = RepoPaths.architectureDocs.resolve(UI_LAYER_DOC).readContractSource()
        val expected = ContractSymbols.UI_LAYERS.map { layer -> layer.map { it.removePrefix(ContractSymbols.UI_PACKAGE) } }
        assertEquals(
            "${UI_LAYER_DOC}의 ui 층 블록이 ContractSymbols.UI_LAYERS와 다르다 — 정본은 코드다.",
            expected,
            LayerDocBlocks.uiLayers(doc),
        )
    }

    @Test
    fun theLayerDocFolderIsADeclaredTestInput() {
        // 선언이 없으면 문서만 고친 빌드에서 이 계약이 UP-TO-DATE로 건너뛰어진다(#103의 교훈). 주석으로 꺼 둔 줄은 선언이 아니다.
        val declared = RepoPaths.appAndroidBuildScript.readContractSourceLines()
            .any { it.trim() == "inputs.dir(rootDir.resolve(\"docs/architecture\"))" }
        assertTrue("app-android/build.gradle.kts의 Test 입력에 docs/architecture가 없다.", declared)
    }

    @Test
    fun blockParserCatchesPlantedDrift() {
        val doc = "앞 문장\n\n<!-- layer-packages: SAMPLE -->\n```text\nalpha\nbeta\n```\n\n뒤 문장\n"
        assertEquals("자기검증: 같은 목록을 어긋났다고 봤다", emptyList<String>(), LayerDocBlocks.packageDrift(doc, "SAMPLE", listOf("alpha", "beta")))
        assertEquals(
            "자기검증: 빠진 것·남는 것을 못 잡았다",
            listOf("문서에 없다: gamma", "코드의 이 계층에 없다: beta"),
            LayerDocBlocks.packageDrift(doc, "SAMPLE", listOf("alpha", "gamma")),
        )
        assertEquals(
            "자기검증: 블록이 없는 문서를 통과시켰다",
            listOf("블록이 없다: <!-- layer-packages: OTHER -->"),
            LayerDocBlocks.packageDrift(doc, "OTHER", listOf("alpha")),
        )
        val ui = "<!-- ui-layers: UI_LAYERS -->\n```text\nL0 l10n foundation\nL1 guide\n```\n"
        assertEquals("자기검증: ui 층을 잘못 읽었다", listOf(listOf("l10n", "foundation"), listOf("guide")), LayerDocBlocks.uiLayers(ui))
        val misnumbered = "<!-- ui-layers: UI_LAYERS -->\n```text\nL1 l10n\n```\n"
        assertTrue("자기검증: 층 번호가 틀린 줄을 통과시켰다", runCatching { LayerDocBlocks.uiLayers(misnumbered) }.isFailure)
    }

    private companion object {
        /** 루트 패키지 접두(`…goaicoach.`) — 리터럴로 적지 않고 [ContractSymbols]에서 파생한다([ContractSymbolContractTest]). */
        val ROOT_PREFIX: String = ContractSymbols.UI_PACKAGE.removeSuffix("ui.")

        const val UI_LAYER_DOC = "LAYER_7_PRESENTATION.md"

        /** 계층 문서 ↔ 코드 목록. 1계층·조립 루트는 `:shared` 패키지가 없어 블록이 없다. */
        val LAYER_DOCS: List<Triple<String, String, List<String>>> = listOf(
            Triple("LAYER_2_MIDDLEWARE_BRIDGE.md", "LAYER_2_PACKAGES", ContractSymbols.LAYER_2_PACKAGES),
            Triple("LAYER_3_EXTENDED_API.md", "LAYER_3_PACKAGES", ContractSymbols.LAYER_3_PACKAGES),
            Triple("LAYER_4_EXTERNAL_INTEGRATION.md", "LAYER_4_ALPHA_PACKAGES", ContractSymbols.LAYER_4_ALPHA_PACKAGES),
            Triple("LAYER_5_APPLICATION_DOMAIN.md", "LAYER_5_PACKAGES", ContractSymbols.LAYER_5_PACKAGES),
            Triple("LAYER_6_SESSION_CONTINUITY.md", "LAYER_6_PACKAGES", ContractSymbols.LAYER_6_PACKAGES),
        )
    }
}

/** 계층 문서의 블록을 읽는 판정기 — [LayerDocContractTest]와 그 자기검증이 같은 한 벌을 쓴다. */
internal object LayerDocBlocks {

    /** [listName] 블록과 [assigned]의 차이. 같으면 빈 목록. */
    fun packageDrift(doc: String, listName: String, assigned: List<String>): List<String> {
        val marker = "<!-- layer-packages: $listName -->"
        val listed = blockAfter(doc, marker) ?: return listOf("블록이 없다: $marker")
        return assigned.filterNot { it in listed }.map { "문서에 없다: $it" } +
            listed.filterNot { it in assigned }.map { "코드의 이 계층에 없다: $it" }
    }

    /** ui 층 블록 — 줄 순번이 층 번호다. 줄머리의 `L<n>`이 순번과 다르면 던진다. */
    fun uiLayers(doc: String): List<List<String>> {
        val lines = blockAfter(doc, "<!-- ui-layers: UI_LAYERS -->") ?: error("ui 층 블록이 없다")
        return lines.mapIndexed { index, line ->
            val parts = line.split(Regex("\\s+"))
            check(parts.first() == "L$index") { "ui 층 블록 ${index + 1}째 줄이 L$index 로 시작하지 않는다: $line" }
            parts.drop(1)
        }
    }

    private fun blockAfter(doc: String, marker: String): List<String>? {
        val lines = doc.lines()
        val at = lines.indexOfFirst { it.trim() == marker }
        if (at < 0 || lines.getOrNull(at + 1)?.trim() != "```text") return null
        val end = (at + 2 until lines.size).firstOrNull { lines[it].trim() == "```" } ?: return null
        return lines.subList(at + 2, end).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
