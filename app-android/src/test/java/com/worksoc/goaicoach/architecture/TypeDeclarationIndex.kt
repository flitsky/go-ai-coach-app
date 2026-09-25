package com.worksoc.goaicoach.architecture

/**
 * 소스에서 읽은 **타입 선언 색인** — 선언 종류와 필드 타입까지 본다(refactor backlog #83).
 *
 * ## 왜 필요한가
 * 포트 시그니처 규칙(`docs/ARCHITECTURE.md` 4계층 「포트가 아는 것」 ⓐ)은 *"시그니처 타입의 필드 폐포가
 * 값인가"* 를 묻는다. import 줄로는 못 잰다 — 포트 여럿이 자기가 싣는 값과 **같은 패키지**에 있어 import
 * 줄이 아예 없고, 패키지 규칙은 같은 패키지의 정책 객체·컨트롤러까지 허용해 버린다. 그래서 타입 이름을
 * **선언까지 풀어** 그 선언이 무엇인지(데이터 클래스인가 인터페이스인가)와 필드가 무엇인지를 알아야 한다.
 * [SourceSymbolIndex]가 *"이 FQN이 있는가"* 만 답하던 것을 여기서 *"그것이 무엇이고 무엇을 담는가"* 로 넓힌다.
 *
 * ## 읽는 것
 *  - 선언 종류 — `data`/`enum`/`sealed`/`value` class, `data object`, `interface`, `fun interface`,
 *    `sealed interface`, `object`(companion 포함), 그 밖의 `class`, `annotation class`, `typealias`, 그리고 `expect`.
 *  - 주 생성자의 `val`/`var` 매개변수(필드), 본문의 프로퍼티와 그 저장 방식([PropertyStorage]) —
 *    `val x: T = …`·`by …`·`lateinit`은 저장, `get()`만 있는 것은 계산, `abstract`·인터페이스의 몸통 없는 것은 추상.
 *  - 멤버 함수의 시그니처(타입 매개변수·수신 타입·매개변수 타입·반환 타입), 상위 타입, typealias의 대상.
 *    `vararg` 매개변수·필드의 타입은 실제로 들어오는 모양인 `Array<T>`로 적는다.
 *  - 상위 **클래스** — 상위 타입 목록에서 생성자 호출(`Base()`)이 붙은 것([TypeDeclaration.superclass]).
 *    물려받는 저장 프로퍼티가 거기서 온다. 인터페이스는 상태를 물려주지 않는다.
 *  - 중첩 선언(FQN은 `바깥.안`), 파일의 `package`·`import`(별칭·`*` 포함).
 *
 * ## 못 읽는 것(알고 둔다)
 *  - **함수 본문 안의 지역 선언** — 이름으로 부를 수 없으니 폐포에 들어올 수 없다.
 *  - 타입이 적혀 있지 않은 프로퍼티·식 본문 함수의 **추론 타입** — `type`이 `null`로 남는다. 가드는 이것을
 *    *"폐포를 정할 수 없다"* 로 빨갛게 본다(초록 쪽 오차를 만들지 않는다).
 *  - 문자열 템플릿 안에 따옴표가 다시 나오는 경우(`"${if (a) "x" else "y"}"`) — 주석·문자열 제거기가
 *    [PackageImportGraph.stripCommentsAndStrings]와 같은 규칙이라 템플릿 안 따옴표를 따로 따라가지 않는다.
 *  - 기본값 식 안의 제네릭 호출 쉼표(`= mapOf<A, B>()`) — 매개변수 경계를 잘못 자를 수 있다. 잘린 조각은
 *    `val`/`var`도 `:`도 없어 아무 필드도 만들지 않는다(앞 필드의 타입은 이미 읽혔다).
 *  - 키워드와 이름이 다른 줄에 있는 선언, 백틱 이름.
 */
internal class TypeDeclarationIndex(val declarations: List<TypeDeclaration>) {

    /** FQN → 선언. `expect`/`actual`처럼 같은 FQN이 소스셋마다 따로 선언되면 여럿이다. */
    val byFqn: Map<String, List<TypeDeclaration>> = declarations.groupBy { it.fqn }

    /** 선언이 하나라도 있는 패키지 — 완전 한정 이름을 풀 때 가장 긴 패키지 접두어를 찾는 데 쓴다. */
    private val packages: Set<String> = declarations.map { it.scope.packageName }.toSet()

    /** 합성 소스를 더한 색인 — 자기검증이 실제 색인 위에 가짜 계약을 얹을 때 쓴다. */
    operator fun plus(other: List<TypeDeclaration>): TypeDeclarationIndex = TypeDeclarationIndex(declarations + other)

    /**
     * [ref]를 [scope]에서 코틀린 규칙대로 푼다 — 둘러싼 선언(안쪽부터)의 이름과 중첩 타입 → 명시 import(별칭 포함)
     * → 같은 패키지 → `*` import → 기본 import(`kotlin.*`·`kotlin.collections.*` …) 순서다. 첫 조각이
     * 소문자이고 조각이 둘 이상이면 완전 한정 이름으로 먼저 본다. [typeParameters]에 든 한 조각 이름은
     * 선언이 아니라 타입 매개변수다.
     */
    fun resolve(ref: TypeRef.Named, scope: DeclarationScope, typeParameters: Set<String>): Resolution {
        val segments = ref.segments
        val head = segments.first()
        if (segments.size == 1 && head in typeParameters) return Resolution.TypeParameter(head)

        val rendered = segments.joinToString(".")
        val candidates = buildList {
            if (head.first().isLowerCase() && segments.size > 1) add(rendered)
            scope.enclosing.asReversed().forEach { outer ->
                if (outer.substringAfterLast('.') == head) add(qualify(outer, segments))
                add(qualify("$outer.$head", segments))
            }
            scope.imports.filter { !it.isStar && it.visibleName == head }.forEach { add(qualify(it.fqn, segments)) }
            add(qualify("${scope.packageName}.$head", segments))
            scope.imports.filter { it.isStar }.forEach { add(qualify("${it.fqn}.$head", segments)) }
            DEFAULT_IMPORT_PACKAGES.forEach { add(qualify("$it.$head", segments)) }
            if (!head.first().isLowerCase() || segments.size == 1) add(rendered)
        }
        for (fqn in candidates) {
            byFqn[fqn]?.let { return Resolution.Declared(fqn, it) }
            StandardTypes.categoryOf(fqn)?.let { return Resolution.Standard(fqn, it) }
        }
        // 완전 한정 이름이 알려진 패키지 아래를 가리키는데 선언이 없으면 — 이름이 틀렸거나 색인이 못 읽었다.
        val knownPackage = (segments.size - 1 downTo 1).map { segments.take(it).joinToString(".") }.firstOrNull { it in packages }
        return Resolution.Unresolved(rendered, knownPackage)
    }

    /**
     * [sealed]의 **직접** 하위 타입 — 같은 패키지(코틀린이 sealed 하위 타입을 같은 패키지·모듈로 묶는다)에서
     * 상위 타입 목록이 [sealed]로 풀리는 선언. 하위 타입이 다시 sealed면 부르는 쪽이 한 번 더 내려간다.
     */
    fun sealedSubtypes(sealed: TypeDeclaration): List<TypeDeclaration> =
        declarations.filter { candidate ->
            candidate.scope.packageName == sealed.scope.packageName && candidate.fqn != sealed.fqn &&
                candidate.supertypes.any { supertype ->
                    supertype is TypeRef.Named &&
                        (resolve(supertype, candidate.scope, candidate.typeParameters.toSet()) as? Resolution.Declared)?.fqn == sealed.fqn
                }
        }

    private fun qualify(base: String, segments: List<String>): String =
        if (segments.size == 1) base else base + "." + segments.drop(1).joinToString(".")

    companion object {
        /** 코틀린이 모든 파일에 자동으로 import하는 패키지(JVM 기준). */
        val DEFAULT_IMPORT_PACKAGES: List<String> = listOf(
            "kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons", "kotlin.io",
            "kotlin.ranges", "kotlin.sequences", "kotlin.text", "kotlin.jvm",
        )

        /** `라벨 → 소스 본문`으로 색인을 만든다 — 자기검증 픽스처가 디스크 없이 쓰는 입구. */
        fun of(sources: Map<String, String>): TypeDeclarationIndex =
            TypeDeclarationIndex(sources.flatMap { (label, text) -> KotlinDeclarationParser.parse(label, text) })
    }
}

/** 타입 이름 하나를 푼 결과. */
internal sealed interface Resolution {
    /** 소스에 선언된 타입. */
    data class Declared(val fqn: String, val declarations: List<TypeDeclaration>) : Resolution

    /** 소스에 선언되지 않은 **표준 라이브러리 타입** 중 [StandardTypes]가 아는 것. */
    data class Standard(val fqn: String, val category: StandardTypeCategory) : Resolution

    /** 둘러싼 선언이나 함수의 타입 매개변수(`T`). */
    data class TypeParameter(val name: String) : Resolution

    /** 어디서도 못 찾았다. [knownPackage]는 이름이 가리키는 듯한 패키지(있으면). */
    data class Unresolved(val text: String, val knownPackage: String?) : Resolution
}

/**
 * 표준 라이브러리 타입의 지위(`docs/ARCHITECTURE.md` 4계층 ⓐ 1번과 *"표준 라이브러리 타입이라고 다 값은 아니다"*).
 * 목록에 없는 표준 타입은 전부 값이 아니다(기본 거부) — 여기 이름을 적는 것은 **허용**하거나 **거부 이유를
 * 또렷이 말하기** 위해서다.
 */
internal enum class StandardTypeCategory {
    /** 그 자체로 값 — 문자열·수·불리언·`Unit`·`kotlin.time`의 불변 값. */
    VALUE,

    /** 읽기 전용 표준 컨테이너 — 타입 인자도 전부 값이어야 한다. */
    CONTAINER,

    /** 읽기 전용 흐름 — **시그니처에서만** 컨테이너 자리이고(원소가 값이어야 한다), 필드로 들면 값이 아니다. */
    READ_ONLY_FLOW,

    /** 값이 아니다 — [StandardTypes.rejectionReason]이 이유를 준다. */
    NOT_VALUE,
}

internal object StandardTypes {

    private val VALUES = setOf(
        "kotlin.String", "kotlin.Char", "kotlin.Boolean",
        "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
        "kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong",
        "kotlin.Unit",
        "kotlin.time.Duration", "kotlin.time.Instant",
    )

    private val CONTAINERS = setOf(
        "kotlin.collections.List", "kotlin.collections.Set", "kotlin.collections.Map",
        "kotlin.Pair", "kotlin.Triple", "kotlin.Result",
    )

    private val READ_ONLY_FLOWS = setOf(
        "kotlinx.coroutines.flow.Flow", "kotlinx.coroutines.flow.StateFlow", "kotlinx.coroutines.flow.SharedFlow",
    )

    private const val MUTABLE_CONTAINER = "가변 컨테이너 — `var` 필드와 같다(읽기 전용 List·Set·Map으로)"
    private const val MUTABLE_FLOW = "가변 흐름 — 포트가 위 계층이 쥔 상태를 직접 고치는 통로다(`var` 필드와 같다)"
    private const val ARRAY = "가변 배열 — 가변 컨테이너다(List로)"
    private const val THROWABLE = "예외 — 원인 사슬에 아무 타입이나 실린다(실패 이유는 포트 자신의 결과 타입으로)"

    private val REJECTED: Map<String, String> = buildMap {
        put("kotlin.Any", "`Any` — 무엇이 실릴지 몰라 폐포를 정할 수 없다(구체 타입으로)")
        put("kotlin.Nothing", "`Nothing` — 값이 아니다")
        put("kotlin.random.Random", "`Random` — 상태를 가진 생성기다(시드 값으로)")
        put("kotlin.sequences.Sequence", "`Sequence` — 지연 계산이라 어댑터의 IO가 포트 호출 밖에서 일어난다(List나 Flow로)")
        put("kotlin.Lazy", "`Lazy` — 계산을 미뤄 쥐고 있는 동작이다")
        listOf("Throwable", "Exception", "Error", "RuntimeException", "IllegalStateException", "IllegalArgumentException")
            .forEach { put("kotlin.$it", THROWABLE) }
        listOf("Array", "IntArray", "LongArray", "ShortArray", "ByteArray", "CharArray", "FloatArray", "DoubleArray", "BooleanArray")
            .forEach { put("kotlin.$it", ARRAY) }
        listOf(
            "MutableList", "MutableSet", "MutableMap", "MutableCollection", "MutableIterable", "MutableIterator",
            "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",
        ).forEach { put("kotlin.collections.$it", MUTABLE_CONTAINER) }
        listOf("MutableStateFlow", "MutableSharedFlow").forEach { put("kotlinx.coroutines.flow.$it", MUTABLE_FLOW) }
    }

    fun categoryOf(fqn: String): StandardTypeCategory? = when (fqn) {
        in VALUES -> StandardTypeCategory.VALUE
        in CONTAINERS -> StandardTypeCategory.CONTAINER
        in READ_ONLY_FLOWS -> StandardTypeCategory.READ_ONLY_FLOW
        in REJECTED -> StandardTypeCategory.NOT_VALUE
        else -> null
    }

    fun rejectionReason(fqn: String): String = REJECTED[fqn] ?: "표준 값 타입 목록에 없다"
}

/** 선언 종류. [isImmutableValue]가 참인 것만 포트 시그니처의 폐포에 들어올 수 있다(관문). */
internal enum class DeclarationKind(val label: String, val isImmutableValue: Boolean) {
    DATA_CLASS("data class", true),
    DATA_OBJECT("data object", true),
    ENUM_CLASS("enum class", true),
    SEALED_CLASS("sealed class", true),
    SEALED_INTERFACE("sealed interface", true),
    VALUE_CLASS("value class", true),
    CLASS("class", false),
    OBJECT("object", false),
    INTERFACE("interface", false),
    FUN_INTERFACE("fun interface", false),
    ANNOTATION_CLASS("annotation class", false),
    TYPEALIAS("typealias", false),
    ;

    val isSealed: Boolean get() = this == SEALED_CLASS || this == SEALED_INTERFACE
    val isInterfaceLike: Boolean get() = this == INTERFACE || this == FUN_INTERFACE || this == SEALED_INTERFACE
}

/** 선언이 놓인 자리 — 이름을 풀 때 쓴다. [enclosing]은 바깥부터 안쪽 순서의 둘러싼 선언 FQN이다. */
internal data class DeclarationScope(
    val packageName: String,
    val imports: List<ImportDirective>,
    val enclosing: List<String>,
)

internal data class ImportDirective(val fqn: String, val alias: String?, val isStar: Boolean) {
    val visibleName: String get() = alias ?: fqn.substringAfterLast('.')
}

internal data class TypeDeclaration(
    val fqn: String,
    val simpleName: String,
    val kind: DeclarationKind,
    val isExpect: Boolean,
    val typeParameters: List<String>,
    val supertypes: List<TypeRef>,
    /** 주 생성자의 `val`/`var` 매개변수. `val`/`var`가 없는 매개변수는 필드가 아니라 빠진다. */
    val constructorFields: List<PropertyDeclaration>,
    /** 본문에 직접 선언된 프로퍼티 전부(저장·계산·추상). companion·중첩 선언의 것은 들지 않는다. */
    val bodyProperties: List<PropertyDeclaration>,
    val functions: List<FunctionSignature>,
    /** typealias의 대상 타입. */
    val aliasedType: TypeRef?,
    /** 이 선언을 **둘러싼** 자리(상위 타입을 풀 때). */
    val scope: DeclarationScope,
    /** `파일:줄`. */
    val location: String,
    /**
     * 상위 **클래스** — 상위 타입 목록에서 생성자 호출(`Base()`)이 붙은 것. 물려받는 필드가 여기서 온다.
     * 생성자 호출 없이 적힌 상위 타입(인터페이스, 보조 생성자만 있는 클래스)은 [supertypes]에만 있다.
     */
    val superclass: TypeRef? = null,
) {
    /** 이 선언의 **안쪽** 자리 — 필드·멤버 타입을 풀 때. 자기 자신과 자기 중첩 타입이 이름으로 보인다. */
    val memberScope: DeclarationScope get() = scope.copy(enclosing = scope.enclosing + fqn)

    /** 필드 — 주 생성자 프로퍼티와 본문 저장 프로퍼티(`docs/ARCHITECTURE.md` ⓐ *"값은 필드 폐포로 정한다"*). */
    val storedFields: List<PropertyDeclaration>
        get() = constructorFields + bodyProperties.filter { it.storage == PropertyStorage.STORED }
}

internal enum class PropertyStorage {
    /** 뒷받침 필드가 있다 — 주 생성자 프로퍼티, 초기값·위임(`by`)·`lateinit`, 게터 없는 클래스 본문 프로퍼티. */
    STORED,

    /** `get()`만 있다 — 필드가 아니다. 확장 프로퍼티도 여기다. */
    COMPUTED,

    /** `abstract`이거나 인터페이스의 몸통 없는 프로퍼티. */
    ABSTRACT,
}

internal data class PropertyDeclaration(
    val name: String,
    val isMutable: Boolean,
    /** 적힌 타입. 적혀 있지 않으면(추론) `null`. */
    val type: TypeRef?,
    val storage: PropertyStorage,
)

internal data class FunctionSignature(
    val name: String,
    val typeParameters: List<String>,
    val receiver: TypeRef?,
    val parameters: List<ParameterDeclaration>,
    /** 적힌 반환 타입. 없으면 블록 본문·본문 없음은 `Unit`, 식 본문은 추론이라 `null`. */
    val returnType: TypeRef?,
    val isSuspend: Boolean,
)

internal data class ParameterDeclaration(val name: String, val type: TypeRef, val isVararg: Boolean)

/** 타입 식. [render]는 사람이 읽을 모양(함수 화살표는 `->`)이다. */
internal sealed interface TypeRef {
    val isNullable: Boolean

    fun render(): String

    /** 널 가능 표시만 뗀 같은 타입. */
    fun nonNull(): TypeRef

    data class Named(val segments: List<String>, val arguments: List<TypeRef>, override val isNullable: Boolean) : TypeRef {
        override fun render(): String =
            segments.joinToString(".") +
                (if (arguments.isEmpty()) "" else arguments.joinToString(", ", "<", ">") { it.render() }) +
                (if (isNullable) "?" else "")

        override fun nonNull(): TypeRef = copy(isNullable = false)
    }

    data class Function(
        val isSuspend: Boolean,
        val receiver: TypeRef?,
        val parameters: List<TypeRef>,
        val returnType: TypeRef,
        override val isNullable: Boolean,
    ) : TypeRef {
        override fun render(): String {
            val body = (if (isSuspend) "suspend " else "") + (receiver?.let { it.render() + "." } ?: "") +
                parameters.joinToString(", ", "(", ")") { it.render() } + " -> " + returnType.render()
            return if (isNullable) "($body)?" else body
        }

        override fun nonNull(): TypeRef = copy(isNullable = false)
    }

    data object Star : TypeRef {
        override val isNullable: Boolean = false

        override fun render(): String = "*"

        override fun nonNull(): TypeRef = this
    }

    data class Unparsed(val text: String) : TypeRef {
        override val isNullable: Boolean = false

        override fun render(): String = text

        override fun nonNull(): TypeRef = this
    }

    companion object {
        /** 타입 식 텍스트 하나를 읽는다. 함수 화살표는 `->`도 [KotlinDeclarationParser.ARROW]도 받는다. */
        fun parse(text: String): TypeRef = TypeRefParser(text.replace("->", KotlinDeclarationParser.ARROW.toString())).parseWhole()
    }
}

/** [TypeRef.parse]의 재귀 하강 파서. 읽지 못하면 [TypeRef.Unparsed]를 돌려준다(가드가 빨갛게 본다). */
private class TypeRefParser(private val text: String) {
    private var i = 0

    fun parseWhole(): TypeRef {
        val result = runCatching { parseType() }.getOrNull()
        skipSpaces()
        return if (result == null || i < text.length) TypeRef.Unparsed(text.trim()) else result
    }

    private fun parseType(): TypeRef {
        skipSpaces()
        skipAnnotations()
        var isSuspend = false
        if (text.startsWith("suspend", i) && text.getOrNull(i + 7)?.let { it.isWhitespace() || it == '(' } == true) {
            isSuspend = true
            i += 7
            skipSpaces()
        }
        val base: TypeRef = if (peek() == '(') {
            val group = parseParenthesizedList()
            skipSpaces()
            if (peek() == KotlinDeclarationParser.ARROW) {
                i++
                TypeRef.Function(isSuspend, null, group, parseType(), isNullable = false)
            } else {
                check(group.size == 1 && !isSuspend) { "괄호 타입" }
                group.single()
            }
        } else {
            val named = parseNamed()
            if (peek() == '.' && text.getOrNull(i + 1) == '(') {
                i++
                val parameters = parseParenthesizedList()
                skipSpaces()
                check(peek() == KotlinDeclarationParser.ARROW) { "수신 타입 뒤에 화살표가 없다" }
                i++
                TypeRef.Function(isSuspend, named, parameters, parseType(), isNullable = false)
            } else {
                check(!isSuspend) { "suspend 뒤에 함수 타입이 없다" }
                named
            }
        }
        skipSpaces()
        var nullable = false
        while (peek() == '?') {
            nullable = true
            i++
            skipSpaces()
        }
        return when (base) {
            is TypeRef.Named -> if (nullable) base.copy(isNullable = true) else base
            is TypeRef.Function -> if (nullable) base.copy(isNullable = true) else base
            else -> base
        }
    }

    private fun parseNamed(): TypeRef.Named {
        val segments = mutableListOf<String>()
        val arguments = mutableListOf<TypeRef>()
        while (true) {
            skipSpaces()
            val start = i
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            check(i > start) { "이름이 없다" }
            segments += text.substring(start, i)
            skipSpaces()
            if (peek() == '<') arguments += parseTypeArguments()
            if (peek() == '.' && text.getOrNull(i + 1)?.let { it.isLetter() || it == '_' } == true) {
                i++
                continue
            }
            return TypeRef.Named(segments, arguments, isNullable = false)
        }
    }

    private fun parseTypeArguments(): List<TypeRef> {
        check(peek() == '<')
        i++
        val result = mutableListOf<TypeRef>()
        while (true) {
            skipSpaces()
            for (variance in listOf("in ", "out ")) if (text.startsWith(variance, i)) i += variance.length
            skipSpaces()
            if (peek() == '*') {
                i++
                result += TypeRef.Star
            } else {
                result += parseType()
            }
            skipSpaces()
            when (peek()) {
                ',' -> {
                    i++
                    skipSpaces()
                    // 끝 쉼표(`Map<\n  K,\n  V,\n>`)도 코틀린 문법이다.
                    if (peek() == '>') { i++; return result }
                }
                '>' -> { i++; return result }
                else -> error("타입 인자 목록이 닫히지 않았다")
            }
        }
    }

    /** `(a: A, B)` — 이름 붙은 매개변수(`a:`)는 이름을 버리고 타입만 남긴다. */
    private fun parseParenthesizedList(): List<TypeRef> {
        check(peek() == '(')
        i++
        val result = mutableListOf<TypeRef>()
        skipSpaces()
        if (peek() == ')') { i++; return result }
        while (true) {
            skipSpaces()
            val nameMatch = PARAMETER_NAME.find(text.substring(i))
            if (nameMatch != null && nameMatch.range.first == 0) i += nameMatch.value.length
            result += parseType()
            skipSpaces()
            when (peek()) {
                ',' -> {
                    i++
                    skipSpaces()
                    if (peek() == ')') { i++; return result }
                }
                ')' -> { i++; return result }
                else -> error("괄호가 닫히지 않았다")
            }
        }
    }

    private fun skipAnnotations(): Unit {
        while (peek() == '@') {
            val match = ANNOTATION.find(text.substring(i)) ?: return
            i += match.value.length
            skipSpaces()
        }
    }

    private fun skipSpaces(): Unit {
        while (i < text.length && text[i].isWhitespace()) i++
    }

    private fun peek(): Char? = text.getOrNull(i)

    private companion object {
        val PARAMETER_NAME = Regex("""^[A-Za-z_]\w*\s*:(?!:)\s*""")
        val ANNOTATION = Regex("""^@[\w.]+(?::[\w.]+)?(?:\((?:[^()]|\([^()]*\))*\))?""")
    }
}

/**
 * 코틀린 소스 한 파일에서 [TypeDeclaration]을 읽는다. 문법 전체가 아니라 **선언의 머리와 필드**만 본다.
 *
 * 먼저 [PackageImportGraph.stripCommentsAndStrings]로 주석·문자열을 지우고(줄 구조는 남는다) 함수 화살표
 * `->`를 한 글자 [ARROW]로 바꾼다 — 화살표의 `>`가 꺾쇠 깊이를 깎아 먹지 않게 하려는 것이다. 그다음 괄호
 * 깊이(`()`·`[]`·`{}`)가 0인 자리를 **문장** 단위로 훑는다. 줄이 `,`·`=`·`:`·`.`로 끝나거나 다음 줄이
 * `.`·`?`·`:`·`,`로 시작하면 한 문장으로 잇는다(여러 줄에 걸친 상위 타입 목록·초기값 식).
 */
internal object KotlinDeclarationParser {

    const val ARROW = '→'

    private const val ANNOTATION = """@[\w.]+(?::[\w.]+)?(?:\((?:[^()]|\([^()]*\))*\))?"""
    private const val TYPE_MODIFIERS =
        "public|private|internal|protected|open|abstract|final|sealed|data|enum|value|inline|annotation|inner|companion|expect|actual|external|fun"
    private const val MEMBER_MODIFIERS =
        "public|private|internal|protected|open|abstract|final|override|lateinit|const|expect|actual|external|inline|suspend|tailrec|operator|infix"

    private val PACKAGE_LINE = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val IMPORT_LINE = Regex("""^\s*import\s+([\w.`]+?)(\.\*)?(?:\s+as\s+(\w+))?\s*$""", RegexOption.MULTILINE)

    private val TYPE_HEADER = Regex(
        """(?:(?:$ANNOTATION|(?:$TYPE_MODIFIERS))\s+)*(class|interface|object|typealias)\b\s*([A-Za-z_]\w*)?""",
    ).toPattern()
    private val PROPERTY_HEADER = Regex(
        """(?:(?:$ANNOTATION|(?:$MEMBER_MODIFIERS))\s+)*(val|var)\b""",
    ).toPattern()
    private val FUNCTION_HEADER = Regex(
        """(?:(?:$ANNOTATION|(?:$MEMBER_MODIFIERS))\s+)*fun\b""",
    ).toPattern()
    private val ACCESSOR_START = Regex("""^(?:(?:public|private|internal|protected)\s+)?get\b""")
    private val MODIFIER_WORD = Regex("""\b(?:$TYPE_MODIFIERS|$MEMBER_MODIFIERS)\b""")

    fun parse(label: String, source: String): List<TypeDeclaration> {
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val code = PackageImportGraph.stripCommentsAndStrings(normalized).replace("->", ARROW.toString())
        val packageName = PACKAGE_LINE.find(code)?.groupValues?.get(1) ?: ""
        val imports = IMPORT_LINE.findAll(code).map { match ->
            ImportDirective(
                fqn = match.groupValues[1].replace("`", ""),
                alias = match.groupValues[3].ifEmpty { null },
                isStar = match.groupValues[2].isNotEmpty(),
            )
        }.toList()
        val context = FileContext(label, code, DeclarationScope(packageName, imports, emptyList()))
        val collected = mutableListOf<TypeDeclaration>()
        context.scanMembers(0, code.length, owner = null, collected = collected)
        return collected
    }

    /** 한 파일을 훑는 동안의 상태. 멤버를 모아 [Members]로 돌려준다. */
    private class FileContext(val label: String, val code: String, val fileScope: DeclarationScope) {

        class Members {
            val properties = mutableListOf<PropertyDeclaration>()
            val functions = mutableListOf<FunctionSignature>()
        }

        /** [from, to) 안의 문장을 훑는다. [owner]가 `null`이면 파일 최상위다. */
        fun scanMembers(from: Int, to: Int, owner: OwnerInfo?, collected: MutableList<TypeDeclaration>): Members {
            val members = Members()
            var i = from
            while (i < to) {
                while (i < to && (code[i].isWhitespace() || code[i] == ';' || code[i] == ',')) i++
                if (i >= to) break
                // 짝 없는 닫는 괄호(주석·문자열 제거기가 여는 쪽을 삼킨 경우)에서 멈춰 서지 않도록 한 글자는 반드시 나아간다.
                val end = statementEnd(i, to).coerceAtLeast(i + 1)
                val typeHeader = TYPE_HEADER.matcher(code).region(i, end)
                val propertyHeader = PROPERTY_HEADER.matcher(code).region(i, end)
                val functionHeader = FUNCTION_HEADER.matcher(code).region(i, end)
                when {
                    typeHeader.lookingAt() && isTypeHeader(typeHeader.group(), typeHeader.group(2)) ->
                        parseTypeDeclaration(i, typeHeader.start(1), typeHeader.end(), end, typeHeader.group(1), typeHeader.group(2), owner, collected)
                    functionHeader.lookingAt() -> members.functions += parseFunction(i, functionHeader.end(), end)
                    propertyHeader.lookingAt() -> {
                        val nextStart = nextStatementStart(end, to)
                        val accessorFollows = nextStart < to && ACCESSOR_START.containsMatchIn(code.substring(nextStart, minOf(to, nextStart + 40)))
                        members.properties += parseProperty(i, propertyHeader.end(), end, propertyHeader.group(1), owner, accessorFollows)
                    }
                }
                i = end
            }
            return members
        }

        /** `companion object`는 이름이 없어도 된다. 그 밖의 이름 없는 `object`는 식(익명 객체)이다. */
        private fun isTypeHeader(header: String, name: String?): Boolean =
            name != null || Regex("""\bcompanion\s+object\b""").containsMatchIn(header)

        private fun parseTypeDeclaration(
            start: Int,
            keywordStart: Int,
            headerEnd: Int,
            end: Int,
            keyword: String,
            explicitName: String?,
            owner: OwnerInfo?,
            collected: MutableList<TypeDeclaration>,
        ) {
            // 애너테이션 인자(`@OptIn(Foo::class)`) 안의 `class`에서 자르지 않도록 키워드의 실제 위치까지만 본다.
            val modifiers = MODIFIER_WORD.findAll(stripAnnotations(code.substring(start, keywordStart))).map { it.value }.toSet()
            val name = explicitName ?: "Companion"
            val kind = kindOf(keyword, modifiers)
            val fqn = when {
                owner != null -> "${owner.fqn}.$name"
                fileScope.packageName.isEmpty() -> name
                else -> "${fileScope.packageName}.$name"
            }
            val scope = fileScope.copy(enclosing = owner?.let { it.enclosing + it.fqn }.orEmpty())
            var i = headerEnd
            i = skipSpaces(i, end)
            val typeParameters = mutableListOf<String>()
            if (code.getOrNull(i) == '<') {
                val close = matching(i, '<', '>', end)
                typeParameters += splitTopLevel(code.substring(i + 1, close)).mapNotNull { typeParameterName(it) }
                i = skipSpaces(close + 1, end)
            }
            if (kind == DeclarationKind.TYPEALIAS) {
                val aliased = code.substring(i, end).trim().removePrefix("=").trim()
                collected += TypeDeclaration(
                    fqn, name, kind, "expect" in modifiers, typeParameters, emptyList(), emptyList(), emptyList(),
                    emptyList(), TypeRef.parse(aliased), scope, "$label:${lineOf(start)}",
                )
                return
            }
            Regex("""^(?:(?:$ANNOTATION|public|private|internal|protected)\s+)*constructor\b\s*""")
                .find(code.substring(i, end))?.let { i = skipSpaces(i + it.value.length, end) }
            var constructorFields = emptyList<PropertyDeclaration>()
            if (code.getOrNull(i) == '(') {
                val close = matching(i, '(', ')', end)
                constructorFields = splitParameters(code.substring(i + 1, close)).mapNotNull { constructorField(it) }
                i = skipSpaces(close + 1, end)
            }
            val bodyStart = findTopLevel(i, end, '{')
            val supertypeText = code.substring(i, bodyStart ?: end).trim()
            val supertypeEntries = if (supertypeText.startsWith(":")) {
                splitTopLevel(supertypeText.removePrefix(":").substringBefore(" where ")).map { it.substringBefore(" by ") }
            } else {
                emptyList()
            }
            val supertypes = supertypeEntries.map { TypeRef.parse(stripConstructorCall(it).trim()) }
            // 생성자 호출이 붙은 상위 타입이 상위 클래스다(코틀린은 하나만 허락한다).
            val superclass = supertypeEntries.firstOrNull { stripConstructorCall(it) != it }
                ?.let { TypeRef.parse(stripConstructorCall(it).trim()) }
            var bodyMembers = Members()
            if (bodyStart != null) {
                val bodyEnd = matching(bodyStart, '{', '}', end)
                bodyMembers = scanMembers(bodyStart + 1, bodyEnd, OwnerInfo(fqn, scope.enclosing, kind), collected)
            }
            collected += TypeDeclaration(
                fqn = fqn,
                simpleName = name,
                kind = kind,
                isExpect = "expect" in modifiers,
                typeParameters = typeParameters,
                supertypes = supertypes,
                constructorFields = constructorFields,
                bodyProperties = bodyMembers.properties.toList(),
                functions = bodyMembers.functions.toList(),
                aliasedType = null,
                scope = scope,
                location = "$label:${lineOf(start)}",
                superclass = superclass,
            )
        }

        private fun parseProperty(
            start: Int,
            headerEnd: Int,
            end: Int,
            keyword: String,
            owner: OwnerInfo?,
            accessorFollows: Boolean,
        ): PropertyDeclaration {
            val header = stripAnnotations(code.substring(start, headerEnd))
            var i = skipSpaces(headerEnd, end)
            if (code.getOrNull(i) == '<') i = skipSpaces(matching(i, '<', '>', end) + 1, end)
            val rest = code.substring(i, end)
            // 이름 앞에 `수신타입.`이 있으면 확장 프로퍼티다 — 뒷받침 필드를 가질 수 없다.
            val nameMatch = Regex("""^(?:([\w.<>?*, ]+?)\.)?([A-Za-z_]\w*)\s*(?=[:=]|\bby\b|\bget\b|$)""").find(rest.substringBefore('\n'))
            val isExtension = nameMatch?.groupValues?.get(1)?.isNotEmpty() == true
            val name = nameMatch?.groupValues?.get(2) ?: "?"
            val afterName = rest.substring(nameMatch?.range?.last?.plus(1) ?: 0)
            val typeText = if (afterName.trimStart().startsWith(":")) {
                cutAtTopLevel(afterName.trimStart().removePrefix(":"), listOf("=", " by ", " get(", " get ", "\n")).trim()
            } else {
                null
            }
            val afterType = if (typeText != null) afterName.trimStart().removePrefix(":").trimStart().removePrefix(typeText) else afterName
            val trimmedAfter = afterType.trimStart()
            val hasInitializer = trimmedAfter.startsWith("=")
            val hasDelegate = trimmedAfter.startsWith("by ") || trimmedAfter.startsWith("by\n")
            val hasInlineGetter = Regex("""^(?:(?:public|private|internal|protected)\s+)?get\b""").containsMatchIn(trimmedAfter)
            val modifiers = MODIFIER_WORD.findAll(header).map { it.value }.toSet()
            val storage = when {
                isExtension -> PropertyStorage.COMPUTED
                hasInitializer || hasDelegate || "lateinit" in modifiers -> PropertyStorage.STORED
                "abstract" in modifiers -> PropertyStorage.ABSTRACT
                hasInlineGetter || accessorFollows -> PropertyStorage.COMPUTED
                owner != null && owner.kind.isInterfaceLike -> PropertyStorage.ABSTRACT
                else -> PropertyStorage.STORED
            }
            return PropertyDeclaration(name, keyword == "var", typeText?.let { TypeRef.parse(it) }, storage)
        }

        private fun parseFunction(start: Int, headerEnd: Int, end: Int): FunctionSignature {
            val header = stripAnnotations(code.substring(start, headerEnd))
            var i = skipSpaces(headerEnd, end)
            val typeParameters = mutableListOf<String>()
            if (code.getOrNull(i) == '<') {
                val close = matching(i, '<', '>', end)
                typeParameters += splitTopLevel(code.substring(i + 1, close)).mapNotNull { typeParameterName(it) }
                i = skipSpaces(close + 1, end)
            }
            val open = findTopLevel(i, end, '(') ?: return FunctionSignature("?", typeParameters, null, emptyList(), null, false)
            val beforeParameters = code.substring(i, open).trim()
            val name = beforeParameters.substringAfterLast('.').trim()
            val receiver = beforeParameters.substringBeforeLast('.', "").trim().takeIf { it.isNotEmpty() }?.let { TypeRef.parse(it) }
            val close = matching(open, '(', ')', end)
            val parameters = splitParameters(code.substring(open + 1, close)).mapNotNull { functionParameter(it) }
            val tail = code.substring(close + 1, end)
            val trimmedTail = tail.trimStart()
            val returnType = when {
                trimmedTail.startsWith(":") ->
                    TypeRef.parse(cutAtTopLevel(trimmedTail.removePrefix(":"), listOf("=", "{", " where ", "\n")).trim())
                trimmedTail.startsWith("=") -> null
                else -> TypeRef.Named(listOf("Unit"), emptyList(), isNullable = false)
            }
            return FunctionSignature(name, typeParameters, receiver, parameters, returnType, isSuspend = Regex("""\bsuspend\b""").containsMatchIn(header))
        }

        private fun constructorField(parameter: String): PropertyDeclaration? {
            val match = Regex("""^(?:(?:$ANNOTATION|(?:$MEMBER_MODIFIERS|vararg))\s+)*(val|var)\s+([A-Za-z_]\w*)\s*:(.*)$""", RegexOption.DOT_MATCHES_ALL)
                .find(parameter.trim()) ?: return null
            val typeText = cutAtTopLevel(match.groupValues[3], listOf("=")).trim()
            val type = TypeRef.parse(typeText).let { if (isVararg(parameter)) varargArrayOf(it) else it }
            return PropertyDeclaration(match.groupValues[2], match.groupValues[1] == "var", type, PropertyStorage.STORED)
        }

        private fun functionParameter(parameter: String): ParameterDeclaration? {
            val match = Regex("""^(?:(?:$ANNOTATION|vararg|crossinline|noinline)\s+)*([A-Za-z_]\w*)\s*:(.*)$""", RegexOption.DOT_MATCHES_ALL)
                .find(parameter.trim()) ?: return null
            val typeText = cutAtTopLevel(match.groupValues[2], listOf("=")).trim()
            val vararg = isVararg(parameter)
            val type = TypeRef.parse(typeText).let { if (vararg) varargArrayOf(it) else it }
            return ParameterDeclaration(match.groupValues[1], type, vararg)
        }

        /** `vararg x: T`는 받는 쪽에 `Array<T>`로 들어온다 — 가변 배열이다. */
        private fun varargArrayOf(element: TypeRef): TypeRef = TypeRef.Named(listOf("Array"), listOf(element), isNullable = false)

        /** 수식어 자리(이름 앞)에 `vararg`가 있는가. */
        private fun isVararg(parameter: String): Boolean = VARARG.containsMatchIn(parameter.substringBefore(':'))

        // ── 문장·괄호 도구 ─────────────────────────────────────────────

        /** [start]에서 시작한 문장의 끝 — 깊이 0의 줄바꿈이나 `;`. 이어지는 줄은 한 문장으로 본다. */
        fun statementEnd(start: Int, to: Int): Int {
            var i = start
            var depth = 0
            while (i < to) {
                val c = code[i]
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> { depth--; if (depth < 0) return i }
                    ';' -> if (depth == 0) return i
                    '\n' -> if (depth == 0 && !continuesPastNewline(start, i, to)) return i
                }
                i++
            }
            return to
        }

        /** 줄바꿈 앞뒤만 본다 — 문장 전체를 잘라 붙이면 긴 파일에서 제곱으로 느려진다. */
        private fun continuesPastNewline(start: Int, newline: Int, to: Int): Boolean {
            var back = newline - 1
            while (back >= start && code[back].isWhitespace()) back--
            if (back < start) return false
            val last = code[back]
            // `<`로 끝난 줄은 여러 줄 타입 인자(`): List<` ⏎ `Snapshot,` ⏎ `>`)이거나 줄을 넘긴 비교식이다.
            if (last in ",=:.<$ARROW") return true
            if ((last == '&' || last == '|') && back > start && code[back - 1] == last) return true
            if (last == ':' || (last == '?' && back + 1 < newline && code[back + 1] == ':')) return true
            var ahead = newline + 1
            while (ahead < to && code[ahead].isWhitespace()) ahead++
            if (ahead >= to) return false
            val first = code[ahead]
            return first == '.' || first == '?' || first == ':' || first == ',' ||
                ((first == '&' || first == '|') && code.getOrNull(ahead + 1) == first)
        }

        private fun nextStatementStart(from: Int, to: Int): Int {
            var i = from
            while (i < to && (code[i].isWhitespace() || code[i] == ';')) i++
            return i
        }

        /** [open]의 여는 괄호에 짝이 맞는 닫는 괄호의 위치. 꺾쇠는 꺾쇠만 센다. */
        private fun matching(open: Int, opening: Char, closing: Char, limit: Int): Int {
            var depth = 0
            var i = open
            while (i < limit) {
                val c = code[i]
                if (c == opening) depth++
                if (c == closing) {
                    depth--
                    if (depth == 0) return i
                }
                i++
            }
            return limit
        }

        /** [from, to)에서 괄호 깊이 0의 [target] 위치. */
        private fun findTopLevel(from: Int, to: Int, target: Char): Int? {
            var depth = 0
            var i = from
            while (i < to) {
                val c = code[i]
                if (depth == 0 && c == target) return i
                when (c) {
                    '(', '[', '{', '<' -> depth++
                    ')', ']', '}', '>' -> depth--
                }
                i++
            }
            return null
        }

        private fun skipSpaces(from: Int, to: Int): Int {
            var i = from
            while (i < to && code[i].isWhitespace()) i++
            return i
        }

        fun lineOf(offset: Int): Int = code.substring(0, offset).count { it == '\n' } + 1
    }

    /** 멤버를 훑을 때의 주인(둘러싼 선언). */
    private data class OwnerInfo(val fqn: String, val enclosing: List<String>, val kind: DeclarationKind)

    private fun kindOf(keyword: String, modifiers: Set<String>): DeclarationKind = when (keyword) {
        "typealias" -> DeclarationKind.TYPEALIAS
        "interface" -> when {
            "sealed" in modifiers -> DeclarationKind.SEALED_INTERFACE
            "fun" in modifiers -> DeclarationKind.FUN_INTERFACE
            else -> DeclarationKind.INTERFACE
        }
        "object" -> if ("data" in modifiers) DeclarationKind.DATA_OBJECT else DeclarationKind.OBJECT
        else -> when {
            "data" in modifiers -> DeclarationKind.DATA_CLASS
            "enum" in modifiers -> DeclarationKind.ENUM_CLASS
            "sealed" in modifiers -> DeclarationKind.SEALED_CLASS
            "value" in modifiers || "inline" in modifiers -> DeclarationKind.VALUE_CLASS
            "annotation" in modifiers -> DeclarationKind.ANNOTATION_CLASS
            else -> DeclarationKind.CLASS
        }
    }

    private val ANNOTATION_TOKEN = Regex(ANNOTATION)
    private val VARARG = Regex("""\bvararg\b""")

    /** 머리에서 애너테이션을 걷는다 — 애너테이션 인자 속 낱말을 수식어로 읽지 않게. */
    private fun stripAnnotations(header: String): String = ANNOTATION_TOKEN.replace(header, " ")

    /** `out T : Bound` → `T`. */
    private fun typeParameterName(entry: String): String? =
        Regex("""^(?:(?:in|out|reified)\s+|@\w+\s+)*([A-Za-z_]\w*)""").find(entry.trim())?.groupValues?.get(1)

    /** `Foo<A>(x, y)` → `Foo<A>` — 상위 타입 목록의 생성자 호출을 떼어 낸다. */
    private fun stripConstructorCall(entry: String): String {
        var depth = 0
        entry.forEachIndexed { index, c ->
            when (c) {
                '<' -> depth++
                '>' -> depth--
                '(' -> if (depth == 0) return entry.substring(0, index)
            }
        }
        return entry
    }

    /** 괄호·꺾쇠 깊이 0의 쉼표로 나눈다. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in text) {
            when (c) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> depth--
            }
            if (c == ',' && depth == 0) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
        }
        if (current.isNotBlank()) parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 매개변수 목록을 나눈다. [splitTopLevel]과 달리 **기본값 식(`=` 뒤)에서는 꺾쇠를 세지 않는다** —
     * `= if (a < b) …` 같은 비교가 꺾쇠 깊이를 어긋나게 해 뒤 매개변수를 통째로 삼키지 않게 하려는 것이다.
     */
    private fun splitParameters(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var angle = 0
        var inDefault = false
        val current = StringBuilder()
        text.forEachIndexed { index, c ->
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                '<' -> if (!inDefault) angle++
                '>' -> if (!inDefault) angle--
                '=' -> if (depth == 0 && angle == 0 && text.getOrNull(index + 1) != '=' &&
                    text.getOrNull(index - 1)?.let { it in "=!<>" } != true
                ) {
                    inDefault = true
                }
            }
            if (c == ',' && depth == 0 && angle <= 0) {
                parts += current.toString()
                current.clear()
                inDefault = false
                angle = 0
            } else {
                current.append(c)
            }
        }
        if (current.isNotBlank()) parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** [text]를 괄호·꺾쇠 깊이 0에서 [stops] 중 먼저 나오는 것 앞까지 자른다. */
    private fun cutAtTopLevel(text: String, stops: List<String>): String {
        var depth = 0
        text.forEachIndexed { index, c ->
            if (depth == 0 && stops.any { text.startsWith(it, index) }) return text.substring(0, index)
            when (c) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> depth--
            }
        }
        return text
    }
}
