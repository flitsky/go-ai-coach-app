package com.worksoc.goaicoach.architecture

import java.io.File

/**
 * 소스 트리의 **패키지 단위 import 그래프**와 그 SCC(강한 연결 요소)(refactor backlog #33).
 *
 * ## 규칙은 설계 스레드의 실측과 한 글자도 다르면 안 된다
 * [PackageCycleRatchetTest]의 기준선([ContractSymbols.CYCLE_BASELINE_SCCS])은 설계 스레드가 커밋
 * 7c928f55에서 `scc.py`로 잰 값이다. 규칙이 조금이라도 다르면 기준선이 **다른 그래프**를 가리키게
 * 되고, 그러면 래칫은 첫날부터 틀린 것을 잠근다. 그래서 아래 규칙을 그 스크립트에서 그대로 옮겼다.
 *  - **노드** = 파일의 `package` 선언(디렉터리가 아니다). 선언이 없으면 [DEFAULT_PACKAGE].
 *  - **간선** = 서로 다른 패키지 A→B. 두 출처를 센다.
 *     1. [rootPackage]로 시작하는 `import` 한 줄마다 1건. 대상은 **알려진 패키지 중 가장 긴 접두어**로
 *        푼다 — 중첩 클래스(`a.b.Outer.Inner`)·멤버 import(`a.b.someFun`)가 여기서 풀린다.
 *     2. import가 아닌 줄의 **inline FQN**(`root.x.Y`) 출현 1건씩.
 *  - **같은 패키지 안의 참조는 세지 않는다.**
 *  - 주석(`//`, **중첩되는** 블록 주석)과 문자열·raw string·문자 리터럴을 **먼저 지운다** — KDoc 속
 *    FQN은 간선이 아니다. 지울 때 줄바꿈은 남겨 줄 구조를 유지한다.
 *  - `\w`는 파이썬 3처럼 유니코드로 본다(`(?U)`) — JVM 기본값(ASCII)과 갈리면 한글 식별자 주변에서
 *    inline FQN 판정이 스크립트와 달라진다.
 *
 * ⚠️ 문자열 스캐너는 문자열 템플릿(`"${...}"`) 안의 따옴표를 따로 추적하지 않는다 — 스크립트가
 * 그렇게 했기 때문이다. 고치고 싶어도 **기준선과 함께** 바꿔야 한다(규칙만 바꾸면 기준선이 어긋난다).
 */
internal class PackageImportGraph private constructor(
    /** 읽은 소스 파일 수 — 스캔이 헛돌지 않았는지 보는 자기검증용. */
    val fileCount: Int,
    /** 노드 전부. */
    val packages: Set<String>,
    /** 간선(A→B)마다 그 간선을 만든 참조 건수. */
    val edgeReferenceCounts: Map<Pair<String, String>, Int>,
    /** [rootPackage] 아래를 가리키는데 알려진 패키지로 풀리지 않은 참조(`파일: FQN`). */
    val unresolvedReferences: List<String>,
) {

    /** 크기 2 이상인 SCC 전부 — 크기 1(자기 자신뿐)은 사이클이 아니므로 뺀다. */
    val cycles: Set<Set<String>> by lazy { tarjan().filter { it.size > 1 }.toSet() }

    /** 서로를 참조하는 패키지 쌍 — 쌍 안은 사전순(`first < second`). */
    val mutualPairs: Set<Pair<String, String>> by lazy {
        edgeReferenceCounts.keys
            .filter { (a, b) -> a < b && (b to a) in edgeReferenceCounts }
            .toSet()
    }

    private fun tarjan(): List<Set<String>> {
        val successors = edgeReferenceCounts.keys.groupBy({ it.first }, { it.second })
            .mapValues { (_, targets) -> targets.sorted() }
        val index = mutableMapOf<String, Int>()
        val low = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        val components = mutableListOf<Set<String>>()
        var counter = 0

        fun connect(v: String) {
            index[v] = counter
            low[v] = counter
            counter++
            stack.addLast(v)
            onStack += v
            for (w in successors[v].orEmpty()) {
                if (w !in index) {
                    connect(w)
                    low[v] = minOf(low.getValue(v), low.getValue(w))
                } else if (w in onStack) {
                    low[v] = minOf(low.getValue(v), index.getValue(w))
                }
            }
            if (low[v] == index[v]) {
                val component = mutableSetOf<String>()
                do {
                    val w = stack.removeLast()
                    onStack -= w
                    component += w
                } while (w != v)
                components += component
            }
        }

        packages.sorted().forEach { if (it !in index) connect(it) }
        return components
    }

    companion object {
        const val DEFAULT_PACKAGE = "<default>"

        private val PACKAGE_DECLARATION = Regex("""(?U)^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
        private val IMPORT_LINE = Regex("""(?U)^\s*import\s+([\w.`]+)(\.\*)?(\s+as\s+\w+)?""")
        private val PACKAGE_LINE = Regex("""(?U)^\s*package\s""")
        private val CHAR_LITERAL = Regex("""'(\\.|[^'\\])*'""").toPattern()

        /** [sourceRoot] 아래의 `.kt` 전부로 그래프를 만든다. */
        fun scan(sourceRoot: File, rootPackage: String): PackageImportGraph {
            check(sourceRoot.isDirectory) {
                "사이클 래칫이 훑을 소스 루트가 없다: ${sourceRoot.absolutePath}. 옮겨졌다면 RepoPaths.kt를 갱신하라."
            }
            val sources = sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .associate { it.relativeTo(sourceRoot).path to it.readContractSource() }
            return of(sources, rootPackage)
        }

        /** `라벨 → 소스 본문`으로 그래프를 만든다 — 자기검증 픽스처가 디스크 없이 쓰는 입구. */
        fun of(sources: Map<String, String>, rootPackage: String): PackageImportGraph {
            // 파이썬의 텍스트 모드 open()은 줄바꿈을 `\n`으로 통일한다 — 같은 입력을 보게 맞춘다.
            val normalized = sources.mapValues { (_, text) -> text.replace("\r\n", "\n").replace('\r', '\n') }
            val packageOf = normalized.mapValues { (_, text) ->
                PACKAGE_DECLARATION.find(text)?.groupValues?.get(1) ?: DEFAULT_PACKAGE
            }
            val packages = packageOf.values.toSet()
            val prefix = "$rootPackage."
            val inlineFqn = Regex("""(?U)(?<![\w.])${Regex.escape(rootPackage)}(?:\.\w+)+""")

            fun resolve(fqn: String): String? {
                val parts = fqn.split('.')
                return (parts.size downTo 1).asSequence()
                    .map { parts.take(it).joinToString(".") }
                    .firstOrNull { it in packages }
            }

            val counts = mutableMapOf<Pair<String, String>, Int>()
            val unresolved = mutableListOf<String>()
            fun record(label: String, from: String, fqn: String, target: String?) {
                when {
                    target == null -> unresolved += "$label: $fqn"
                    target != from -> counts.merge(from to target, 1, Int::plus)
                }
            }

            normalized.forEach { (label, text) ->
                val from = packageOf.getValue(label)
                for (line in stripCommentsAndStrings(text).split('\n')) {
                    val import = IMPORT_LINE.find(line)
                    if (import != null) {
                        val fqn = import.groupValues[1].replace("`", "")
                        if (!fqn.startsWith(prefix)) continue
                        val isWildcard = import.groups[2] != null
                        val target = if (isWildcard && fqn in packages) fqn else resolve(fqn)
                        record(label, from, fqn, target)
                        continue
                    }
                    if (PACKAGE_LINE.containsMatchIn(line)) continue
                    inlineFqn.findAll(line).forEach { record(label, from, it.value, resolve(it.value)) }
                }
            }
            return PackageImportGraph(normalized.size, packages, counts, unresolved)
        }

        /**
         * 주석과 문자열·문자 리터럴을 지운다(줄바꿈은 남긴다). 문자열은 `""`로, 문자 리터럴은 `' '`로
         * 바꿔 토큰 경계를 유지한다. `scc.py`의 `strip()`을 그대로 옮긴 것이다 — 한 줄짜리 문자열이
         * 줄 끝에서 닫히지 않으면 그 줄바꿈까지 먹는 버릇까지 같다.
         */
        fun stripCommentsAndStrings(src: String): String {
            val out = StringBuilder(src.length)
            val n = src.length
            var i = 0
            while (i < n) {
                val c = src[i]
                if (src.startsWith("/*", i)) {
                    var depth = 1
                    i += 2
                    while (i < n && depth > 0) {
                        when {
                            src.startsWith("/*", i) -> { depth++; i += 2 }
                            src.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> { if (src[i] == '\n') out.append('\n'); i++ }
                        }
                    }
                    continue
                }
                if (src.startsWith("//", i)) {
                    while (i < n && src[i] != '\n') i++
                    continue
                }
                if (src.startsWith("\"\"\"", i)) {
                    val close = src.indexOf("\"\"\"", i + 3)
                    val end = if (close < 0) n else close + 3
                    out.append("\"\"").append("\n".repeat(src.substring(i, end).count { it == '\n' }))
                    i = end
                    continue
                }
                if (c == '"') {
                    var j = i + 1
                    while (j < n && src[j] != '"') {
                        if (src[j] == '\\') j++
                        if (j < n && src[j] == '\n') break
                        j++
                    }
                    out.append("\"\"")
                    i = j + 1
                    continue
                }
                if (c == '\'') {
                    val matcher = CHAR_LITERAL.matcher(src).region(i, n)
                    if (matcher.lookingAt()) {
                        out.append("' '")
                        i = matcher.end()
                        continue
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }
    }
}

/**
 * 래칫 판정 — 기준선과 현실을 비교해 **나빠진 것**과 **좋아진 것**을 따로 돌려준다.
 * 판정을 그래프 스캔과 떼어 둔 이유는 [PackageCycleRatchetTest]의 자기검증이 합성 입력으로
 * 이 판정 자체를 시험하기 위해서다(판정기가 "항상 통과"로 고장나면 래칫이 조용히 죽는다).
 */
internal data class CycleRatchetVerdict(val worse: List<String>, val better: List<String>) {

    companion object {
        /**
         * SCC 판정.
         *  - **나빠짐**: 현실 SCC에 기준선 어느 SCC에도 없는 패키지가 들어 있다(사이클이 커졌거나 새로
         *    생겼다), 또는 현실 SCC 하나가 기준선 SCC 둘 이상에 걸친다(둘이 합쳐졌다).
         *  - **좋아짐**: 기준선 SCC가 현실에 그대로 없고, 그와 겹치는 현실 SCC가 전부 그 **진부분집합**이다
         *    (줄었거나 쪼개졌거나 사라졌다). 커져서 달라진 것은 나빠짐으로만 센다.
         */
        fun forCycles(baseline: Collection<Set<String>>, actual: Collection<Set<String>>): CycleRatchetVerdict {
            val baselineMembers = baseline.flatten().toSet()
            val worse = actual.flatMap { component ->
                val intruders = (component - baselineMembers).sorted()
                val spanned = baseline.filter { it.any { member -> member in component } }
                listOfNotNull(
                    intruders.takeIf { it.isNotEmpty() }?.let { "기준선에 없던 패키지가 사이클에 들어왔다 $it — SCC ${component.sorted()}" },
                    spanned.takeIf { it.size > 1 }?.let { "기준선 SCC ${it.size}개가 하나로 합쳐졌다 — SCC ${component.sorted()}" },
                )
            }
            val better = baseline.filter { it !in actual }.mapNotNull { expected ->
                val overlapping = actual.filter { it.any { member -> member in expected } }
                if (overlapping.all { expected.containsAll(it) }) {
                    "기준선 SCC ${expected.sorted()}가 현실에서 ${overlapping.map { it.sorted() }}로 줄었다"
                } else {
                    null
                }
            }
            return CycleRatchetVerdict(worse, better)
        }

        /** 상호 참조 쌍 판정 — 늘면 나빠짐, 줄면 좋아짐. 쌍 안의 순서는 무시한다. */
        fun forMutualPairs(
            baseline: Collection<Pair<String, String>>,
            actual: Collection<Pair<String, String>>,
        ): CycleRatchetVerdict {
            val expected = baseline.map { it.sortedPair() }.toSet()
            val observed = actual.map { it.sortedPair() }.toSet()
            return CycleRatchetVerdict(
                worse = (observed - expected).sortedBy { it.toString() }.map { "새 상호 참조 쌍 ${it.first} <-> ${it.second}" },
                better = (expected - observed).sortedBy { it.toString() }.map { "사라진 상호 참조 쌍 ${it.first} <-> ${it.second}" },
            )
        }

        private fun Pair<String, String>.sortedPair(): Pair<String, String> =
            if (first <= second) this else second to first
    }
}
