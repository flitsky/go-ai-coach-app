package com.worksoc.goaicoach.architecture

import java.io.File

/**
 * 프로덕션 소스를 훑어 **"이 FQN이 실재하는가"** 에 답하는 색인(refactor backlog #68).
 *
 * ## 왜 리플렉션이 아니라 소스 스캔인가
 * `Class.forName`은 여기서 못 쓴다.
 *  - `:shared`는 **별도 모듈**이고, 이 소스셋(`:app-android`의 `test`)의 테스트 런타임
 *    클래스패스에 무엇이 실려 있는지는 Gradle 설정에 달려 있다. 클래스패스에 없으면
 *    `ClassNotFoundException`이 나는데, 그건 **"심볼이 사라졌다"와 구분이 안 된다** —
 *    이 백로그 항목이 막으려는 바로 그 혼동이다.
 *  - 최상위 함수는 클래스가 아니라 `XxxKt` 파사드의 메서드라 이름으로 찾기가 취약하고,
 *    `typealias`는 아예 런타임에 남지 않는다.
 *  - `commonMain`의 `expect` 선언, iOS 타깃 전용 선언은 JVM 테스트 클래스패스에 아예 없다.
 *
 * 반면 가드가 막으려는 것은 **소스에 적힌 import 문자열**이다. 그러니 판정도 소스에서 한다 —
 * 클래스패스 구성에 흔들리지 않고, 실패 메시지가 "어느 파일에 없다"로 곧장 읽힌다.
 *
 * ## 디렉터리가 아니라 `package` 선언을 믿는다
 * 코틀린은 디렉터리와 패키지가 어긋나도 컴파일된다. 그래서 [RepoPaths.productionSourceRoots]
 * 아래의 `.kt`를 전부 읽어 각 파일의 `package` 줄로 색인한다 — P3의 파일 이동이 디렉터리만
 * 옮기고 패키지를 안 고치는(또는 그 반대) 경우에도 답이 흔들리지 않는다.
 */
internal object SourceSymbolIndex {

    private val PACKAGE_LINE = Regex("""^\s*package\s+([\w.]+)""")

    /** 최상위 선언만 본다 — 들여쓰기가 없는(칼럼 0) 줄이어야 한다. */
    private val MODIFIERS = """(?:[a-z]+\s+)*"""

    private val filesByPackage: Map<String, List<File>> by lazy {
        val files = RepoPaths.productionSourceRoots
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        check(files.isNotEmpty()) {
            "프로덕션 .kt 파일을 하나도 못 읽었다 — 색인이 비면 모든 FQN이 '없다'로 나온다."
        }
        files.groupBy { file ->
            file.useLines { lines -> lines.firstNotNullOfOrNull { PACKAGE_LINE.find(it)?.groupValues?.get(1) } }
                ?: ""
        }
    }

    /** 색인이 실제로 무언가를 읽었는지 — 자기검증용. */
    val indexedFileCount: Int get() = filesByPackage.values.sumOf { it.size }

    /** 색인이 본 패키지 전부 — 실패 메시지에 "가까운 이름"을 보여줄 때 쓴다. */
    val knownPackages: Set<String> get() = filesByPackage.keys - ""

    /**
     * [packagePrefix]로 시작하는 패키지가 하나라도 있는가.
     * 접미 `.`은 가드 표기(`import com.example.foo.`)를 그대로 넘겨도 되게 벗겨 낸다.
     */
    fun packageExists(packagePrefix: String): Boolean {
        val prefix = packagePrefix.trimEnd('.')
        if (prefix.isEmpty()) return false
        return knownPackages.any { it == prefix || it.startsWith("$prefix.") }
    }

    /** [fqn]이 가리키는 **타입**(class/interface/object/typealias)이 선언돼 있는가. */
    fun typeExists(fqn: String): Boolean {
        val (packageName, simpleName) = split(fqn) ?: return false
        val declaration = Regex(
            """^$MODIFIERS(?:class|interface|object|typealias)\s+${Regex.escape(simpleName)}\b""",
            RegexOption.MULTILINE,
        )
        return filesByPackage[packageName].orEmpty().any { declaration.containsMatchIn(it.readText()) }
    }

    /**
     * [fqn]이 가리키는 **최상위 함수**가 선언돼 있는가. 확장 함수도 최상위 선언이면 포함한다
     * (`fun EngineCoreApi.estimateScoreForState(` 같은 옛 헬퍼가 정확히 이 모양이었다).
     */
    fun topLevelFunctionExists(fqn: String): Boolean {
        val (packageName, simpleName) = split(fqn) ?: return false
        val declaration = Regex(
            """^${MODIFIERS}fun\s+(?:<[^>\n]*>\s*)?(?:[\w.<>?, ]+\.)?${Regex.escape(simpleName)}\s*\(""",
            RegexOption.MULTILINE,
        )
        return filesByPackage[packageName].orEmpty().any { declaration.containsMatchIn(it.readText()) }
    }

    /** `a.b.C` → `a.b`와 `C`. 조각이 하나뿐이면 답할 수 없다. */
    private fun split(fqn: String): Pair<String, String>? {
        val trimmed = fqn.trimEnd('.')
        if (!trimmed.contains('.')) return null
        return trimmed.substringBeforeLast('.') to trimmed.substringAfterLast('.')
    }
}
