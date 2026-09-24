package com.worksoc.goaicoach.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **가드가 들고 있는 FQN이 실존 심볼을 가리키는지** 검사하는 메타 계약(refactor backlog #68).
 *
 * ## 무엇을 막는가
 * [LayeringContractTest]의 import 가드는 FQN **문자열**로 위반을 찾는다. 심볼이 다른 패키지로
 * 이사하면 그 문자열은 어떤 파일과도 매치하지 않고 — **가드는 자기가 죽은 것을 모른다.**
 * 증상이 "초록"뿐이라 아무도 알아채지 못한다. 260816의 P0(`0c33d32c`)가 정확히 그랬고,
 * `#24`의 `EngineModels.kt` 이사 때는 **사람이 같은 커밋에서 문자열 다섯을 고쳐** 겨우 막았다.
 * 방어가 주의력에 달려 있는 것이 결함이므로, 여기서 기계에 넘긴다.
 *
 * ## 세 가지를 못박는다
 *  1. [everyGuardedSymbolResolvesInSource] — 등록된 주소가 소스에 실재하는가.
 *  2. [noArchitectureTestCarriesAnUnregisteredFqnLiteral] — 새 가드가 FQN을 **몰래 리터럴로**
 *     들고 오지 않았는가. 등록부를 우회하면 1번이 볼 수 없으므로 이쪽이 등록부를 강제한다.
 *  3. [everyFqnInTheRegistryIsAlsoListedForChecking] — 등록부에 적힌 주소가 검사 목록에도
 *     올라 있는가(상수만 더하고 목록에 안 넣으면 1번이 못 본다).
 *  4. [symbolIndexDistinguishesPresentFromAbsent] — 색인 자체가 무엇이든 "있다"고 답하는
 *     고장난 상태가 아닌가(음성 대조군).
 */
class ContractSymbolContractTest {

    /**
     * 등록된 FQN이 소스에 실재하는지 — **이 테스트가 이 백로그 항목의 본체다.**
     *
     * ⚠️ 여기가 빨개졌다면 [LayeringContractTest]의 가드 하나가 **아무것도 안 보는 채 초록**일
     * 가능성이 높다. 심볼이 옮겨졌으면 [ContractSymbols]의 상수를 새 주소로 고친다.
     */
    @Test
    fun everyGuardedSymbolResolvesInSource() {
        check(ContractSymbols.GUARDED.isNotEmpty()) {
            "등록부가 비었다 — 이 테스트가 아무것도 검사하지 않으면서 초록이 된다."
        }
        val offenders = ContractSymbols.GUARDED.mapNotNull { symbol -> verdictFor(symbol) }

        assertEquals(
            "계약 가드가 **실재하지 않는 주소**를 들고 있다 — 그 가드는 어떤 파일과도 매치하지 " +
                "않으면서 초록이다. 심볼이 옮겨졌으면 ContractSymbols.kt를 갱신하라.\n" +
                offenders.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * `architecture/` 아래에서 FQN 리터럴은 [ContractSymbols]에만 산다.
     *
     * 등록부는 **누가 등록해 줘야** 쓸모가 있다. 새 가드가 FQN을 함수 안에 리터럴로 적으면
     * [everyGuardedSymbolResolvesInSource]는 그 존재조차 모르므로, 리터럴이 등록부 밖에
     * 나타나는 순간 여기서 막는다.
     *
     * ⚠️ **등록된 값과 똑같아도 통과시키지 않는다.** 처음엔 "등록부에 있는 주소면 어디 적혀 있든
     * 괜찮다"로 짰는데, 그러면 가드에 베껴 둔 사본이 남는다 — 심볼이 이사해 [ContractSymbols]의
     * 상수만 고치면 그 사본은 낡은 주소인 채 남아 **혼자 죽는다.** 주소는 한 벌만 존재해야 한다.
     *
     * ⚠️ 자기검증 픽스처는 제외한다 — [ContractSymbols.FIXTURE_FUNCTIONS]에 적힌 함수의 본문은
     * **일부러 가짜 FQN으로 위반 소스를 만든다.** 이름으로 제외하므로, 그 함수가 실제로 있는지도
     * 함께 못박는다(이름이 바뀌면 제외가 조용히 아무 데도 안 걸리게 된다).
     */
    @Test
    fun noArchitectureTestCarriesAnUnregisteredFqnLiteral() {
        val seenFixtures = mutableSetOf<String>()
        val offenders = architectureSources().flatMap { file ->
            val scannable = commentFreeLines(blankFixtureBodies(file.readContractSourceLines(), seenFixtures))
            scannable.flatMapIndexed { index, line ->
                FQN_LITERAL.findAll(line)
                    .map { "${file.relativeTo(RepoPaths.root).path}:${index + 1}  ${it.value}" }
                    .toList()
            }
        }

        assertEquals(
            "FQN 리터럴은 ContractSymbols.kt에만 둔다 — 등록부 밖의 주소는 실존 검사를 받지 " +
                "못한 채 초록이 될 수 있고, 등록된 값을 베껴 둔 사본은 상수만 고쳤을 때 조용히 " +
                "낡는다(backlog #68). 상수를 참조하도록 바꿔라.\n" + offenders.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            offenders,
        )
        assertEquals(
            "제외하기로 한 자기검증 픽스처를 소스에서 찾지 못했다 — 이름이 바뀌었다면 " +
                "ContractSymbols.FIXTURE_FUNCTIONS를 갱신하라.",
            ContractSymbols.FIXTURE_FUNCTIONS.toSet(),
            seenFixtures,
        )
    }

    /**
     * 등록부에 **적어만 두고 [ContractSymbols.GUARDED]에는 안 넣은** 상수를 잡는다.
     *
     * 상수를 하나 더하고 [ContractSymbols.GUARDED]에 넣는 것을 잊으면, 가드는 그 상수를 쓰는데
     * [everyGuardedSymbolResolvesInSource]는 그 주소를 **보지 않는다** — 리터럴을 등록부로 옮긴
     * 보람이 딱 그만큼 사라진다. 등록부 안의 FQN은 예외 없이 목록에 올라 있어야 한다.
     */
    @Test
    fun everyFqnInTheRegistryIsAlsoListedForChecking() {
        val listed = ContractSymbols.GUARDED.map { it.fqn.trimEnd('.') }.toSet()
        val registryFile = RepoPaths.root.resolve("$ARCHITECTURE_TEST_DIR/$REGISTRY_FILE_NAME")
        val declared = commentFreeLines(registryFile.readContractSourceLines())
            .flatMap { line -> FQN_LITERAL.findAll(line).map { it.value }.toList() }
            .toSet()
        val unlisted = (declared - listed).sorted()

        assertTrue("등록부에서 FQN을 하나도 못 읽었다 — 스캔이 헛돌고 있다.", declared.isNotEmpty())
        assertEquals(
            "ContractSymbols에 적혀 있는데 GUARDED 목록에 없는 주소가 있다 — 그 주소는 실존 " +
                "검사를 받지 않는다(backlog #68).\n" + unlisted.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            unlisted,
        )
    }

    /**
     * 음성 대조군: 색인이 **없는 것을 없다고** 답하는가.
     *
     * 색인이 고장나 무엇이든 "있다"고 답하면 [everyGuardedSymbolResolvesInSource]는 영원히
     * 초록이 되고, 그건 이 테스트가 막으려던 것과 똑같은 종류의 사망이다. 그래서 실존하는
     * 패키지와, 거기에 존재할 수 없는 이름을 나란히 물어본다.
     *
     * ⚠️ 여기서 쓰는 이름은 [ContractSymbols]의 상수에서 **파생**시킨다 — 리터럴을 적으면
     * [noArchitectureTestCarriesAnUnregisteredFqnLiteral]에 걸리고, 상수 자체가 사보타주로
     * 바뀌어도 이 대조군은 흔들리지 않아야 하기 때문이다.
     */
    @Test
    fun symbolIndexDistinguishesPresentFromAbsent() {
        val absent = ContractSymbols.ENGINE_CORE_API + "ThisSymbolDoesNotExist"

        assertTrue(
            "프로덕션 소스를 거의 못 읽었다 — 색인이 비면 모든 주소가 '없다'가 된다 " +
                "(읽은 파일 ${SourceSymbolIndex.indexedFileCount}개).",
            SourceSymbolIndex.indexedFileCount > 100,
        )
        assertTrue(
            "실존하는 패키지를 못 찾는다 — 색인이 패키지 선언을 읽지 못하고 있다.",
            SourceSymbolIndex.packageExists(ContractSymbols.ENGINE_CORE_API.substringBeforeLast('.')),
        )
        assertFalse("존재할 수 없는 타입을 '있다'고 답한다: $absent", SourceSymbolIndex.typeExists(absent))
        assertFalse(
            "존재할 수 없는 최상위 함수를 '있다'고 답한다: $absent",
            SourceSymbolIndex.topLevelFunctionExists(absent),
        )
        assertFalse(
            "존재할 수 없는 패키지를 '있다'고 답한다: $absent",
            SourceSymbolIndex.packageExists(absent),
        )
    }

    /** 등록 항목 하나를 판정해 위반이면 사람이 읽을 한 줄로, 아니면 `null`로 돌려준다. */
    private fun verdictFor(symbol: GuardedSymbol): String? {
        val label = "${symbol.fqn} (${symbol.kind}, ${symbol.why})"
        val packageName = symbol.fqn.trimEnd('.').substringBeforeLast('.')
        return when (symbol.expectation) {
            SymbolExpectation.MUST_EXIST -> when (symbol.kind) {
                SymbolKind.TYPE ->
                    if (SourceSymbolIndex.typeExists(symbol.fqn)) null else "$label: 선언된 타입이 없다"
                SymbolKind.PACKAGE ->
                    if (SourceSymbolIndex.packageExists(symbol.fqn)) null else "$label: 그런 패키지가 없다"
                SymbolKind.TOP_LEVEL_FUNCTION ->
                    if (SourceSymbolIndex.topLevelFunctionExists(symbol.fqn)) null
                    else "$label: 선언된 최상위 함수가 없다"
            }
            // 없는 것이 정상이지만, 주소가 통째로 허공을 가리키면 가드가 무의미해진다.
            // 그래서 패키지는 실재해야 하고, 심볼이 되살아났다면 분류를 고치라고 말한다.
            SymbolExpectation.ABSENT_BY_DESIGN -> when {
                !SourceSymbolIndex.packageExists(packageName) ->
                    "$label: 금지 대상의 패키지 `$packageName` 자체가 사라졌다 — 가드가 허공을 막고 있다"
                symbol.kind == SymbolKind.TOP_LEVEL_FUNCTION &&
                    SourceSymbolIndex.topLevelFunctionExists(symbol.fqn) ->
                    "$label: 없어야 할 심볼이 되살아났다 — ContractSymbols의 분류를 고쳐라"
                symbol.kind == SymbolKind.TYPE && SourceSymbolIndex.typeExists(symbol.fqn) ->
                    "$label: 없어야 할 타입이 되살아났다 — ContractSymbols의 분류를 고쳐라"
                else -> null
            }
        }
    }

    /** `architecture/` 아래의 계약 소스 — 등록부 자신은 뺀다(리터럴이 사는 유일한 자리이므로). */
    private fun architectureSources(): List<File> {
        val dir = RepoPaths.root.resolve(ARCHITECTURE_TEST_DIR)
        val files = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != REGISTRY_FILE_NAME }
            .toList()
        check(files.isNotEmpty()) { "계약 소스를 하나도 못 찾았다 — 경로가 낡았다: ${dir.path}" }
        return files
    }

    /**
     * 줄 구조를 유지한 채 주석과 `package`/`import` 줄을 걷어낸다.
     *
     * ⚠️ **KDoc을 반드시 지워야 한다** — 이 저장소의 계약 테스트는 설명에 FQN을 자주 적는다
     * (예: `forbiddenReferenceOffenders`의 KDoc). 문서의 언급을 리터럴로 오인하면 이 테스트는
     * 고칠 수 없는 빨강이 된다.
     *
     * ⚠️ **여러 줄 블록 주석을 상태 기계로 따라가면 안 된다** — 처음엔 그렇게 짰다가
     * `LayeringContractTest`가 **문자열 안에 적어 둔 블록 주석 시작 기호**에서
     * 주석 모드로 들어가, 거기서 100줄 넘게(픽스처 통째로) 삼켜 버렸다. 그 상태로도 위반은
     * 0건이라 **초록이었다** — 이 테스트가 막으려던 종류의 사망을 이 테스트가 저지를 뻔했다.
     * 그래서 줄 **접두사**로만 가른다 — 이 저장소의 KDoc은 예외 없이 여는 줄, 별표로 시작하는
     * 본문 줄, 닫는 줄의 세 모양뿐이라 접두사만으로 전부 걸러진다.
     *
     * 한계는 알고 둔다 — 문자열 안의 `//` 뒤는 잘린다. FQN을 **가리는** 쪽(거짓 음성)이라
     * 등록부를 우회하는 데 쓸 수는 없고(리터럴은 `//` 앞에 와야 컴파일된다), 잘못 빨개지지도 않는다.
     */
    private fun commentFreeLines(lines: List<String>): List<String> =
        lines.map { raw ->
            val trimmed = raw.trimStart()
            val isCommentOrDeclaration = trimmed.startsWith("//") || trimmed.startsWith("*") ||
                trimmed.startsWith("/*") || trimmed.startsWith("package ") || trimmed.startsWith("import ")
            if (isCommentOrDeclaration) "" else raw.replace(Regex("/\\*.*?\\*/"), "").substringBefore("//")
        }

    /**
     * [ContractSymbols.FIXTURE_FUNCTIONS]에 적힌 함수의 **본문을 빈 줄로 덮는다**(줄 번호는 유지).
     * 찾은 함수 이름을 [seen]에 담아, 호출부가 "제외하려던 것을 정말 제외했는지" 확인할 수 있게 한다.
     */
    private fun blankFixtureBodies(lines: List<String>, seen: MutableSet<String>): List<String> {
        val result = lines.toMutableList()
        ContractSymbols.FIXTURE_FUNCTIONS.forEach { name ->
            val start = result.indexOfFirst { Regex("""\bfun\s+${Regex.escape(name)}\s*\(""").containsMatchIn(it) }
            if (start < 0) return@forEach
            seen += name
            var depth = 0
            var opened = false
            var cursor = start
            while (cursor < result.size) {
                depth += result[cursor].count { it == '{' } - result[cursor].count { it == '}' }
                if (result[cursor].contains('{')) opened = true
                result[cursor] = ""
                if (opened && depth <= 0) break
                cursor++
            }
        }
        return result
    }

    private companion object {
        const val ARCHITECTURE_TEST_DIR = "app-android/src/test/java/com/worksoc/goaicoach/architecture"
        const val REGISTRY_FILE_NAME = "ContractSymbols.kt"

        /**
         * 이 저장소의 FQN 모양. 접두사는 [ContractSymbols]에서 파생시켜, 루트 패키지가 바뀌면
         * 여기도 함께 따라오게 한다.
         */
        val FQN_LITERAL: Regex = Regex(
            Regex.escape(ContractSymbols.ENGINE_CORE_API.split('.').take(3).joinToString(".")) +
                """(?:\.[A-Za-z_][A-Za-z0-9_]*)+""",
        )
    }
}
