package com.worksoc.goaicoach.architecture

/**
 * 포트 시그니처 규칙 ⓐ의 **판정기**(refactor backlog #83) — `docs/ARCHITECTURE.md` 4계층 「포트가 아는 것」.
 *
 * 계약(포트 인터페이스)의 매개변수·반환 타입을 [TypeDeclarationIndex]로 선언까지 풀고, 그 **필드 폐포**
 * (주 생성자 프로퍼티 + 본문 저장 프로퍼티, sealed는 하위 타입까지)를 걸으며 규칙을 잰다. 테스트 픽스처와
 * 실제 코드가 같은 판정기를 지나도록 [PortSignatureContractTest]와 떼어 두었다 — 판정기가 "항상 통과"로
 * 고장나면 기준선(위반 0)이 조용히 거짓이 된다.
 *
 * ## 규칙을 코드로 옮긴 모양
 *  - **관문** — 폐포 안의 모든 타입은 표준 값 타입([StandardTypes])이거나 불변 값 선언
 *    ([DeclarationKind.isImmutableValue]: data class·data object·enum·sealed·value class)이어야 한다.
 *    인터페이스·일반 class·object(정책 싱글턴 포함)·`expect` 선언은 여기서 걸린다 — 문서의 *"포트·클라이언트·
 *    컨트롤러·세션·상태 홀더 타입의 필드"* 를 **이름이 아니라 선언 종류로** 잡는다(이름으로 잡으면 열거형
 *    `SeatController` 같은 값이 걸린다 — `#73` 2차 실측의 유일한 오탐이었다).
 *  - **필드** — `var`, 함수 타입(`suspend` 포함), 흐름([StandardTypeCategory.READ_ONLY_FLOW]), 적히지 않은(추론)
 *    타입은 위반이다. 계산 프로퍼티(`get()`만)는 필드가 아니라 보지 않는다.
 *  - **sealed** — 같은 패키지에서 찾은 직접 하위 타입을 전부 폐포에 넣는다. 하위 타입이 data 아닌 `object`면
 *    관문에서 걸린다(*"값만 담는 sealed"* 가 아니다). 하위 타입을 하나도 못 찾으면 위반이다(색인이 못 읽은 것).
 *  - **상위 클래스** — 물려받은 저장 프로퍼티도 필드다. 값 선언의 상위 클래스([TypeDeclaration.superclass])는
 *    sealed class여야 하고 그 필드를 같은 규칙으로 잰다 — 하위 타입이 부모를 거치지 않고 시그니처에 곧장 놓여도
 *    부모의 필드가 폐포 밖으로 빠지지 않는다. 일반·추상 class, 표준 타입(`Exception` 등), 못 푼 이름은 위반이다.
 *    인터페이스 상위 타입은 상태를 물려주지 않아 보지 않는다.
 *  - **vararg** — 색인이 `Array<T>`로 적으므로 가변 배열로 걸린다.
 *  - **시그니처** — 흐름은 매개변수·반환의 **바깥 자리**에서만 컨테이너로 본다(원소는 값이어야 한다). 컨테이너
 *    안이나 필드에 든 흐름은 값이 아니다. 함수 타입은 **매개변수의 바깥 자리**에서만, 그리고
 *    [checkFunctionParameter]의 모양(원자적 갱신의 순수 변환 `(상태) -> 상태?`)을 갖추고 등록된 자리만 된다.
 *  - **제네릭** — 선언의 타입 매개변수는 자리표시로 두고 **쓰는 쪽의 타입 인자**를 값으로 잰다. 계약 메서드 자신의
 *    타입 매개변수(`fun <T> get(): T`)는 무엇이 실릴지 몰라 위반이다.
 *  - **기본 거부** — 선언도 표준 값 타입도 아닌 이름(외부 라이브러리 타입, 못 푼 이름)은 위반이다.
 *    기본값 식은 보지 않는다 — 규칙이 보는 것은 타입이다.
 */
internal class PortSignatureAudit(
    private val index: TypeDeclarationIndex,
    /** 허용한 함수 타입 매개변수 자리 — `포트.메서드(매개변수)` 모양. */
    private val registeredTransformParameters: Set<String>,
    /** `false`면 주 생성자 필드만 따라간다 — `#73` 실측(69타입)과 대조할 때만 쓴다. */
    private val followBodyProperties: Boolean = true,
) {

    class Report(
        val contracts: List<TypeDeclaration>,
        /** 폐포에 든 선언 FQN → 처음 닿은 경로. typealias는 넣지 않는다(펼친 대상이 들어간다). */
        val closure: Map<String, String>,
        val violations: List<String>,
        /** 계약 시그니처에서 만난 함수 타입 매개변수 자리 — `포트.메서드(매개변수)`. */
        val functionTypeParameters: List<String>,
    )

    private enum class Position { SIGNATURE, ELEMENT, FIELD }

    private data class TypeContext(
        val scope: DeclarationScope,
        val typeParameters: Set<String>,
        /** 참이면 타입 매개변수는 자리표시(쓰는 쪽이 잰다), 거짓이면 위반. */
        val typeParametersAreSlots: Boolean,
        val position: Position,
    )

    private class Pending(val fqn: String, val declarations: List<TypeDeclaration>, val path: String)

    private val violations = mutableListOf<String>()
    private val closure = linkedMapOf<String, String>()
    private val functionTypeParameters = mutableListOf<String>()

    /**
     * 계약 하나를 걷는 동안 방문한 선언 — **계약마다 새로** 둔다. 폐포를 계약끼리 공유하면 같은 나쁜 타입에 닿는
     * 둘째 계약부터는 조용해진다(처음 닿은 경로 하나만 보고된다). 계약마다 걸어야 어느 계약이 그 타입을 싣는지
     * 전부 드러난다. [closure]는 합집합이다.
     */
    private val visitedInContract = mutableSetOf<String>()
    private val queue = ArrayDeque<Pending>()

    fun audit(contracts: List<TypeDeclaration>): Report {
        contracts.forEach { contract ->
            visitedInContract.clear()
            auditContract(contract)
            while (queue.isNotEmpty()) visit(queue.removeFirst())
        }
        return Report(contracts, closure.toMap(), violations.distinct(), functionTypeParameters.distinct())
    }

    private fun auditContract(contract: TypeDeclaration) {
        memberOwners(contract, mutableSetOf()).forEach { owner ->
            owner.functions.forEach { function ->
                val label = "${contract.simpleName}.${function.name}"
                val context = TypeContext(
                    scope = owner.memberScope,
                    typeParameters = (owner.typeParameters + function.typeParameters).toSet(),
                    typeParametersAreSlots = false,
                    position = Position.SIGNATURE,
                )
                function.receiver?.let { checkType(it, context, "$label(수신 타입)") }
                function.parameters.forEach { parameter ->
                    val path = "$label(${parameter.name})"
                    val type = parameter.type
                    if (type is TypeRef.Function) checkFunctionParameter(type, context, path) else checkType(type, context, path)
                }
                val returnType = function.returnType
                if (returnType == null) {
                    violations += "$label: 반환 타입이 적혀 있지 않다(식 본문의 추론 타입) — 폐포를 정할 수 없으니 타입을 적어라"
                } else {
                    checkType(returnType, context, "$label: 반환")
                }
            }
            owner.bodyProperties.forEach { property ->
                val path = "${contract.simpleName}.${property.name}"
                val context = TypeContext(owner.memberScope, owner.typeParameters.toSet(), typeParametersAreSlots = false, Position.SIGNATURE)
                property.type?.let { checkType(it, context, path) }
                    ?: run { violations += "$path: 타입이 적혀 있지 않다(추론 타입) — 폐포를 정할 수 없으니 타입을 적어라" }
            }
        }
    }

    /** 계약과 그 상위 인터페이스 — 상위 인터페이스의 멤버도 계약의 시그니처다. */
    private fun memberOwners(declaration: TypeDeclaration, seen: MutableSet<String>): List<TypeDeclaration> {
        if (!seen.add(declaration.fqn)) return emptyList()
        val supers = declaration.supertypes.flatMap { supertype ->
            val resolved = (supertype as? TypeRef.Named)?.let { index.resolve(it, declaration.scope, declaration.typeParameters.toSet()) }
            when (resolved) {
                is Resolution.Declared -> resolved.declarations.filter { it.kind.isInterfaceLike }.flatMap { memberOwners(it, seen) }
                else -> {
                    violations += "${declaration.simpleName}: 상위 타입 `${supertype.render()}`을 선언까지 풀지 못해 그 멤버를 잴 수 없다"
                    emptyList()
                }
            }
        }
        return listOf(declaration) + supers
    }

    /**
     * 함수 타입 매개변수 — ⓐ의 유일한 예외. 모양은 **수신 객체 없이, `suspend` 아니고, 매개변수 하나를 받아 같은
     * 타입(널 가능 허용)을 돌려주는** 순수 변환뿐이다 — 원자적 갱신 `update(transform: (상태) -> 상태?)`의 모양.
     * 판정 람다(`-> Boolean`)·동작 콜백(`-> Unit`, `suspend`)은 이 모양을 못 갖춘다. 모양을 갖춰도 *"값만으로는
     * 같은 일을 할 수 없는가"* 는 기계가 못 가르므로 **등록된 자리만** 통과한다. 모양·등록과 무관하게 입력·출력은
     * 값이어야 한다.
     */
    private fun checkFunctionParameter(function: TypeRef.Function, context: TypeContext, path: String) {
        functionTypeParameters += path
        val shapeProblems = buildList {
            if (function.isSuspend) add("suspend — 동작을 실어 나르는 콜백이다(진행 상황은 값의 흐름으로 돌려준다)")
            if (function.receiver != null) add("수신 객체가 있다")
            if (function.parameters.size != 1) {
                add("매개변수가 하나가 아니다")
            } else if (function.returnType.nonNull() != function.parameters.single().nonNull()) {
                add("반환 타입이 입력 타입과 다르다 — 판정 람다(`-> Boolean`)·동작 콜백(`-> Unit`)은 안 된다")
            }
        }
        when {
            shapeProblems.isNotEmpty() -> violations +=
                "$path: 함수 타입 매개변수 `${function.render()}` — 원자적 갱신의 순수 변환 `(상태) -> 상태?` 모양이 아니다: " +
                shapeProblems.joinToString("; ")
            path !in registeredTransformParameters -> violations +=
                "$path: 함수 타입 매개변수 `${function.render()}`가 등록되지 않았다 — 값만으로는 같은 일을 할 수 없는지" +
                "(원자적 갱신인지) 판단한 뒤 PortSignatureContractTest.REGISTERED_PURE_TRANSFORMS에 더하라"
        }
        val element = context.copy(position = Position.ELEMENT)
        function.receiver?.let { checkType(it, element, "$path ‹수신›") }
        function.parameters.forEach { checkType(it, element, "$path ‹입력›") }
        checkType(function.returnType, element, "$path ‹출력›")
    }

    private fun checkType(ref: TypeRef, context: TypeContext, path: String) {
        when (ref) {
            TypeRef.Star -> violations += "$path: `*` — 무엇이 실릴지 몰라 폐포를 정할 수 없다"
            is TypeRef.Unparsed -> violations += "$path: 타입 `${ref.text}`을 읽지 못했다 — 색인의 파서가 이 모양을 모른다"
            is TypeRef.Function -> violations += if (context.position == Position.FIELD) {
                "$path: 함수 타입 필드 `${ref.render()}` — 동작이 값 행세를 하며 포트로 들어온다"
            } else {
                "$path: 함수 타입 `${ref.render()}` — 시그니처가 받을 수 있는 함수는 매개변수 자리의 순수 변환뿐이다"
            }
            is TypeRef.Named -> checkNamed(ref, context, path)
        }
    }

    private fun checkNamed(ref: TypeRef.Named, context: TypeContext, path: String) {
        val element = context.copy(position = Position.ELEMENT)
        when (val resolved = index.resolve(ref, context.scope, context.typeParameters)) {
            is Resolution.TypeParameter -> if (!context.typeParametersAreSlots) {
                violations += "$path: 타입 매개변수 `${resolved.name}` — 무엇이 실릴지 몰라 폐포를 정할 수 없다(구체 타입으로)"
            }
            is Resolution.Standard -> when (resolved.category) {
                StandardTypeCategory.VALUE -> Unit
                StandardTypeCategory.CONTAINER -> ref.arguments.forEach { checkType(it, element, path) }
                StandardTypeCategory.READ_ONLY_FLOW -> if (context.position == Position.SIGNATURE) {
                    ref.arguments.forEach { checkType(it, element, path) }
                } else {
                    violations += "$path: 흐름 `${ref.render()}`이 필드나 컨테이너 안에 있다 — 흐름은 시그니처의 매개변수·반환 " +
                        "자리에만 둔다(필드로 들면 값이 아니라 살아 있는 생산자다)"
                }
                StandardTypeCategory.NOT_VALUE -> violations += "$path: `${resolved.fqn}` — ${StandardTypes.rejectionReason(resolved.fqn)}"
            }
            is Resolution.Declared -> {
                ref.arguments.forEach { checkType(it, element, path) }
                val aliases = resolved.declarations.filter { it.kind == DeclarationKind.TYPEALIAS }
                aliases.forEach { alias ->
                    val aliased = alias.aliasedType ?: TypeRef.Unparsed(alias.simpleName)
                    val aliasContext = context.copy(scope = alias.scope, typeParameters = alias.typeParameters.toSet(), typeParametersAreSlots = true)
                    checkType(aliased, aliasContext, "$path ≡ ${alias.simpleName}")
                }
                val declarations = resolved.declarations - aliases.toSet()
                if (declarations.isNotEmpty()) enqueue(resolved.fqn, declarations, path)
            }
            is Resolution.Unresolved -> violations += "$path: `${resolved.text}` — 선언을 찾지 못했다(색인에도 표준 값 타입 목록에도 " +
                "없다${resolved.knownPackage?.let { " — 패키지 `$it`은 있다" } ?: ""})"
        }
    }

    private fun enqueue(fqn: String, declarations: List<TypeDeclaration>, path: String) {
        if (!visitedInContract.add(fqn)) return
        closure.putIfAbsent(fqn, path)
        queue.addLast(Pending(fqn, declarations, path))
    }

    private fun visit(pending: Pending) {
        pending.declarations.forEach { declaration ->
            val path = pending.path
            when {
                declaration.isExpect -> violations += "$path: `${declaration.simpleName}` — expect 선언이다(플랫폼마다 구현이 달라 폐포를 정할 수 없다)"
                !declaration.kind.isImmutableValue -> violations += "$path: `${declaration.simpleName}`(${declaration.kind.label}) — 불변 값 " +
                    "선언이 아니다(데이터 클래스·data object·열거형·값만 담는 sealed·값 클래스만 폐포에 든다)"
                else -> {
                    visitFields(declaration, path)
                    visitSuperclass(declaration, path, mutableSetOf(declaration.fqn))
                    if (declaration.kind.isSealed) visitSealedSubtypes(declaration, path)
                }
            }
        }
    }

    private fun visitFields(declaration: TypeDeclaration, path: String) {
        val fields = if (followBodyProperties) declaration.storedFields else declaration.constructorFields
        val context = TypeContext(declaration.memberScope, declaration.typeParameters.toSet(), typeParametersAreSlots = true, Position.FIELD)
        fields.forEach { field ->
            val fieldPath = "$path › ${declaration.simpleName}.${field.name}"
            if (field.isMutable) violations += "$fieldPath: `var` 필드 — 값은 불변이어야 한다"
            val type = field.type
            if (type == null) {
                violations += "$fieldPath: 타입이 적혀 있지 않다(추론 타입) — 폐포를 정할 수 없으니 타입을 적어라"
            } else {
                checkType(type, context, fieldPath)
            }
        }
    }

    /**
     * 상위 클래스에서 물려받은 필드. 상위 클래스를 폐포에 **넣지는 않는다** — 넣으면 sealed 부모의 형제 하위 타입까지
     * 딸려 들어와, 시그니처가 싣지도 않는 타입이 폐포를 부풀린다. 부모 자신의 관문과 필드만 여기서 잰다.
     */
    private fun visitSuperclass(declaration: TypeDeclaration, path: String, seen: MutableSet<String>) {
        val superclass = declaration.superclass ?: return
        val superPath = "$path › ${declaration.simpleName} : ${superclass.render()}"
        if (superclass !is TypeRef.Named) {
            violations += "$superPath: 상위 클래스를 읽지 못했다 — 물려받는 필드를 잴 수 없다"
            return
        }
        val typeParameters = declaration.typeParameters.toSet()
        when (val resolved = index.resolve(superclass, declaration.scope, typeParameters)) {
            is Resolution.Declared -> {
                val element = TypeContext(declaration.scope, typeParameters, typeParametersAreSlots = true, Position.ELEMENT)
                superclass.arguments.forEach { checkType(it, element, superPath) }
                resolved.declarations.forEach { parent ->
                    when {
                        parent.kind != DeclarationKind.SEALED_CLASS || parent.isExpect -> violations +=
                            "$superPath: 상위 클래스 `${parent.simpleName}`(${parent.kind.label}) — 물려받는 필드를 값으로 볼 수 없다" +
                            "(값 선언이 상속할 수 있는 클래스는 값만 담는 sealed class뿐이다)"
                        seen.add(parent.fqn) -> {
                            visitFields(parent, superPath)
                            visitSuperclass(parent, superPath, seen)
                        }
                    }
                }
            }
            is Resolution.Standard -> violations +=
                "$superPath: 상위 클래스 `${resolved.fqn}` — ${StandardTypes.rejectionReason(resolved.fqn)}"
            is Resolution.TypeParameter -> violations += "$superPath: 상위 클래스가 타입 매개변수다 — 물려받는 필드를 잴 수 없다"
            is Resolution.Unresolved -> violations +=
                "$superPath: 상위 클래스 `${resolved.text}`를 선언까지 풀지 못했다 — 물려받는 필드를 잴 수 없다"
        }
    }

    private fun visitSealedSubtypes(sealed: TypeDeclaration, path: String) {
        val subtypes = index.sealedSubtypes(sealed)
        if (subtypes.isEmpty()) {
            violations += "$path: sealed `${sealed.simpleName}`의 하위 타입을 하나도 찾지 못했다 — 색인이 하위 타입을 못 읽었다"
            return
        }
        subtypes.groupBy { it.fqn }.forEach { (fqn, declarations) ->
            enqueue(fqn, declarations, "$path › ${sealed.simpleName} ⊃ ${declarations.first().simpleName}")
        }
    }

    companion object {
        /**
         * 검사할 계약 — **이름 패턴**(`Port`로 끝나는 `interface`/`fun interface`)과 [registered]에 적힌 FQN.
         * 등록한 FQN이 색인에 인터페이스로 없으면 [ContractSet.missingRegistrations]에 담는다.
         */
        fun contractsIn(index: TypeDeclarationIndex, registered: List<String>): ContractSet {
            val byPattern = index.declarations.filter {
                (it.kind == DeclarationKind.INTERFACE || it.kind == DeclarationKind.FUN_INTERFACE) && it.simpleName.endsWith("Port")
            }
            val registeredDeclarations = registered.associateWith { fqn -> index.byFqn[fqn].orEmpty().filter { it.kind.isInterfaceLike } }
            return ContractSet(
                contracts = (byPattern + registeredDeclarations.values.flatten()).distinctBy { it.fqn }.sortedBy { it.fqn },
                missingRegistrations = registeredDeclarations.filterValues { it.isEmpty() }.keys.toList(),
            )
        }
    }

    class ContractSet(val contracts: List<TypeDeclaration>, val missingRegistrations: List<String>)
}
