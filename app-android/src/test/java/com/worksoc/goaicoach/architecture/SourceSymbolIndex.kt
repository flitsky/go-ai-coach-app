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

    /**
     * 프로덕션 소스 전체의 **타입 선언 색인**(refactor backlog #83) — 이 FQN이 *있는가*를 넘어 *무엇이고
     * 무엇을 담는가*(선언 종류, 주 생성자·본문 저장 프로퍼티의 타입, 멤버 함수 시그니처)까지 답한다.
     * 포트 시그니처 가드([PortSignatureContractTest])가 시그니처 타입을 선언까지 풀어 필드 폐포를 걷는 데 쓴다.
     * 읽는 것과 못 읽는 것은 [TypeDeclarationIndex]에 적었다. 같은 파일 집합([RepoPaths.productionSourceRoots])을
     * 한 번만 읽는다 — FQN 실존 판정과 선언 색인이 서로 다른 트리를 보는 일이 없다.
     */
    val typeDeclarations: TypeDeclarationIndex by lazy {
        TypeDeclarationIndex.of(
            filesByPackage.values.flatten().associate { file -> file.relativeTo(RepoPaths.root).path to file.readText() },
        )
    }

    /** 소스 텍스트 하나의 타입 선언 — 자기검증이 합성 소스로 직접 부른다(읽는 모양은 [KotlinDeclarationParser]). */
    fun typeDeclarationsIn(label: String, source: String): List<TypeDeclaration> =
        KotlinDeclarationParser.parse(label, source)

    /** 색인이 본 패키지 전부 — 실패 메시지에 "가까운 이름"을 보여줄 때 쓴다. */
    val knownPackages: Set<String> get() = filesByPackage.keys - ""

    /**
     * [packageName]을 **정확히** 선언한 프로덕션 파일 전부(하위 패키지는 빼고, 모든 소스 루트에서).
     * 계층 배정 가드(refactor backlog #84)가 "이 계층 패키지의 파일"을 디렉터리가 아니라 `package`
     * 선언으로 모을 때 쓴다 — :shared 세 소스셋과 app-android에 흩어진 같은 패키지를 한 번에 본다.
     */
    fun filesDeclaring(packageName: String): List<File> = filesByPackage[packageName].orEmpty()

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

    // ── 열거용 선언 문법(refactor backlog #79) ────────────────────────────
    // [typeExists]/[topLevelFunctionExists]의 [MODIFIERS]보다 넓다. 두 판정기는 그대로 두었다.

    /**
     * 선언 앞머리 — 소문자 수식어(`internal`·`const`·`suspend`·`data` …)와 **같은 줄의 애너테이션**
     * (`@Composable`, `@Suppress("x")`, `@OptIn(Foo::class)`)이 섞여 몇 개든 온다. 애너테이션 인자의
     * 괄호는 두 겹까지 본다. 줄을 넘지 않는다(`[ \t]`) — `@file:` 줄이 다음 줄 선언과 이어 붙지 않는다.
     */
    private val DECLARATION_PREFIX =
        """(?:(?:@[\w.:]+(?:\((?:[^()\n]|\([^()\n]*\))*\))?|[a-z]+)[ \t]+)*"""

    /** 선언의 타입 매개변수 — `<T : Comparable<T>>`처럼 **세 겹까지** 중첩된 꺾쇠를 본다. */
    private val TYPE_PARAMETERS = """<(?:[^<>\n]|<(?:[^<>\n]|<[^<>\n]*>)*>)*>"""

    /** 확장 선언의 수신 타입(`Foo.`, `List<*>.`, `Map<K, V>?.`). */
    private val RECEIVER = """(?:[\w.<>?*, ]+\.)?"""

    private val TYPE_DECLARATION = Regex(
        """^$DECLARATION_PREFIX(?:class|interface|object|typealias)[ \t]+([A-Za-z_]\w*)""",
        RegexOption.MULTILINE,
    )
    private val TOP_LEVEL_FUNCTION_DECLARATION = Regex(
        """^${DECLARATION_PREFIX}fun[ \t]+(?:$TYPE_PARAMETERS[ \t]*)?$RECEIVER([A-Za-z_]\w*)[ \t]*\(""",
        RegexOption.MULTILINE,
    )

    /** `val`/`var`/`const val`/`lateinit var`, 확장 프로퍼티 포함. 이름 뒤에 `.`·`<`·글자가 오면 아니다. */
    private val TOP_LEVEL_PROPERTY_DECLARATION = Regex(
        """^${DECLARATION_PREFIX}(?:val|var)[ \t]+(?:$TYPE_PARAMETERS[ \t]*)?$RECEIVER([A-Za-z_]\w*)(?![\w.<])""",
        RegexOption.MULTILINE,
    )

    /**
     * [packageName] 바로 아래 선언된 최상위 심볼의 단순 이름 — 읽는 모양은
     * [topLevelDeclaredSimpleNamesIn]에 적었다(refactor backlog #79).
     *
     * [typeExists]/[topLevelFunctionExists]는 "이 이름이 있는가"만 답한다. 루트 패키지 매처처럼
     * "이 조각이 **패키지 이름**(`ui`·`platform` 등)이 아니라 **실제로 선언된 심볼**인가"를 가르려면
     * 후보 하나하나를 추측해 물어볼 수 없다 — 색인이 **무엇이 있는지 목록**을 내놓아야 한다.
     */
    fun topLevelDeclaredSimpleNames(packageName: String): Set<String> =
        filesByPackage[packageName].orEmpty().flatMap { file -> topLevelDeclaredSimpleNamesIn(file.readText()) }.toSet()

    /**
     * 소스 텍스트 하나에서 최상위 선언의 단순 이름을 나열한다. 자기검증이 합성 소스로 직접 부른다.
     *
     * **읽는 것**: 칼럼 0에서 시작하는 타입(class/interface/object/typealias, `enum`·`data`·`sealed`·
     * `fun interface` 포함), 함수(확장 포함), 프로퍼티(`val`/`var`/`const val`, 확장 포함). 앞머리의
     * 수식어와 **같은 줄 애너테이션**, **중첩 제네릭 경계**(`fun <T : Comparable<T>> f(`)를 건너뛴다.
     *
     * **못 읽는 것**(알고 둔다): 들여쓴 최상위 선언, 이름이 키워드와 다른 줄에 있는 선언, 여러 줄에
     * 걸친 애너테이션의 닫는 줄에 선언이 붙은 경우(`) internal fun f()`), 세 겹을 넘는 꺾쇠·두 겹을
     * 넘는 애너테이션 괄호, 백틱 이름. 반대로 칼럼 0에서 이 모양을 띤 문자열·주석 줄은 이름으로
     * 섞일 수 있다(빨강 쪽 오차).
     *
     * 이 목록에서 빠진 이름은 [LayeringContractTest]의 루트 매처에서 **inline FQN** 쪽만 못 본다 —
     * import 쪽은 이 목록 없이 한 조각 정규식으로 잡는다.
     */
    fun topLevelDeclaredSimpleNamesIn(source: String): Set<String> =
        listOf(TYPE_DECLARATION, TOP_LEVEL_FUNCTION_DECLARATION, TOP_LEVEL_PROPERTY_DECLARATION)
            .flatMap { declaration -> declaration.findAll(source).map { it.groupValues[1] }.toList() }
            .toSet()
}
