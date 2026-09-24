package com.worksoc.goaicoach.architecture

import java.io.File

/**
 * 아키텍처 계약 테스트가 소스를 **문자열로 읽을 때 쓰는 경로 사전**.
 *
 * ## 왜 한 곳으로 모으는가
 * 계약 테스트는 컴파일러가 못 보는 규칙(계층 방향, 호출 순서, 파일 분할)을 지키려고 소스 트리를
 * 직접 훑는다. 그래서 **파일이 옮겨지면 테스트가 같이 옮겨져야 한다.** 경로 문자열이 테스트마다
 * 흩어져 있으면 그 이사가 누락되고, 누락은 두 가지 모습으로 나타난다.
 *  - 디렉터리 스캔은 **조용히 빈 목록**이 되어 무조건 통과한다(260816에 실제로 넷이 이렇게 죽었다).
 *  - 단일 파일 읽기는 `FileNotFoundException`으로 터져 *"경계가 깨졌다"* 가 아니라 *"파일이 없다"* 로 보인다.
 *
 * 그래서 경로는 여기 한 곳에만 둔다. 파일이 이사하면 **이 파일만 고친다.**
 *
 * ## 왜 `internal`을 그대로 두는가(refactor backlog #30, 260923)
 * `ui`/`engine`/최상위(`com.worksoc.goaicoach`) 패키지의 계약 테스트 22개를 여기로 흡수하며
 * "다른 패키지가 `internal object`를 쓸 수 있는가"를 실측했다. **된다** — Kotlin의 `internal`은
 * *패키지*가 아니라 *모듈*(여기서는 `:app-android`의 `test` 소스셋 하나, 한 번의
 * `compileDebugUnitTestKotlin` 호출) 스코프다. `--rerun-tasks`로 전체 재컴파일해
 * `com.worksoc.goaicoach`/`com.worksoc.goaicoach.ui`/`com.worksoc.goaicoach.engine` 세 패키지의
 * 호출부가 전부 그대로 컴파일되는 것으로 확인했다. 그래서 패키지 이동도, 가시성을 넓히는 것도
 * 하지 않는다 — 이미 열려 있었다.
 *
 * ## 왜 클래스 안이 아니라 별도 파일인가
 * `LayeringContractTest`와 `TestAnnotationContractTest`가 각자 [repoRoot]를 **두 벌로** 선언하고
 * 있었다. 둘 중 하나만 고치면 워크트리 판정이 테스트마다 달라져 더 나쁜 상태가 된다.
 * 테스트 클래스 안에 두면 다른 클래스가 쓰려고 그 테스트 클래스를 인스턴스화해야 하므로,
 * 소유자 없는 `object`로 빼서 **둘이 같은 한 벌을 쓰게** 한다.
 */
internal object RepoPaths {

    private const val APP_ANDROID = "app-android/src/main/java/com/worksoc/goaicoach"
    private const val SHARED_COMMON = "shared/src/commonMain/kotlin/com/worksoc/goaicoach"
    private const val ENGINE_ANDROID = "engine-android/src/main/java/com/worksoc/goaicoach/engine/android"

    /**
     * 저장소 루트.
     *
     * ⚠️ **`File(".")` 상향 탐색만으로는 부족하다** — 이 저장소는 워크트리를 여러 개 두고 세션이
     * 나눠 쓴다. 상향 탐색은 *실행 디렉터리가 속한* 트리를 찾으므로, 어디서 Gradle을 띄웠느냐에
     * 따라 **검사 대상이 다른 트리로 미끄러질 수 있다.** 그래서 Gradle이
     * `-Drepo.root=<rootDir>`로 못박아 주입한 값을 먼저 쓰고(app-android/build.gradle.kts의
     * `tasks.withType<Test>` 참고), 그 값이 없을 때만 예전 탐색으로 폴백한다(IDE에서 테스트
     * 하나만 돌리는 경우 등).
     */
    val root: File by lazy { locateRoot() }

    /**
     * 프로덕션 코틀린 소스가 사는 **소스셋 루트 전부**(refactor backlog #68).
     *
     * 위의 헬퍼들이 `com/worksoc/goaicoach` **패키지 루트**를 주는 것과 달리, 여기서는 소스셋
     * 자체를 준다 — [SourceSymbolIndex]가 파일의 `package` 선언을 읽어 색인하므로 디렉터리
     * 구조가 패키지와 어긋나도 상관없게 하려는 것이다. 모듈이 늘면 **여기 한 줄**을 더한다.
     *
     * ⚠️ 하나도 못 찾으면 색인이 통째로 비어 "심볼이 없다"가 아니라 "아무것도 안 봤다"가 된다.
     * 그 조용한 사망이 이 백로그 항목이 막으려는 것 자체이므로, 비면 그 자리에서 터뜨린다.
     */
    val productionSourceRoots: List<File>
        get() {
            val roots = listOf(
                "app-android/src/main",
                "shared/src/commonMain",
                "shared/src/androidMain",
                "shared/src/iosMain",
                "engine-android/src/main",
            ).map { root.resolve(it) }.filter { it.exists() }
            check(roots.isNotEmpty()) {
                "프로덕션 소스 루트를 하나도 찾지 못했다 — RepoPaths.productionSourceRoots가 낡았다."
            }
            return roots
        }

    /** `ui/GoCoachApp.kt`. 이 파일이 옮겨지면 **여기 한 줄**만 고치면 된다. */
    val goCoachApp: File get() = appAndroid("ui/GoCoachApp.kt")

    /** app-android 프로덕션 소스의 `com.worksoc.goaicoach` 패키지 루트(또는 그 아래 경로). */
    fun appAndroid(relativePath: String = ""): File = resolveUnder(APP_ANDROID, relativePath)

    /** :shared commonMain의 `com.worksoc.goaicoach` 패키지 루트(또는 그 아래 경로). */
    fun shared(relativePath: String = ""): File = resolveUnder(SHARED_COMMON, relativePath)

    /** :engine-android의 `com.worksoc.goaicoach.engine.android` 패키지 루트(또는 그 아래 경로). */
    fun engineAndroid(relativePath: String = ""): File = resolveUnder(ENGINE_ANDROID, relativePath)

    /** `ui/` 바로 아래의 파일 하나. */
    fun uiFile(fileName: String): File = appAndroid("ui/$fileName")

    /**
     * `platform/` 바로 아래의 파일 하나 — 4계층 SDK 어댑터가 사는 곳(refactor backlog #25에서
     * `ui/`에서 옮겨 왔다).
     */
    fun platformFile(fileName: String): File = appAndroid("platform/$fileName")

    /**
     * 루트 패키지(`com.worksoc.goaicoach`, 조립 전용) 바로 아래의 파일 하나. refactor backlog #26이
     * `ui/`에 숨어 있던 조립 코드(`*ControllerWiring`·`PremiumPurchaseGlue`·`GameExitRecording`)를
     * 여기로 옮겼다 — `composition/` 하위 패키지를 따로 두지 않은 이유는 루트가 이미 조립 전용으로
     * 명문화돼 있고(docs/ARCHITECTURE.md), 매니페스트에 묶인 `MainActivity`가 루트를 떠날 수 없어서다.
     */
    fun compositionFile(fileName: String): File = appAndroid(fileName)

    /**
     * 컨트롤러 배선 파일 **전부**. `LayeringContractTest`의 "GoCoachApp이 워크플로 본문을 소유하지
     * 않는다" 가드 일곱 개가 이 목록을 **한 벌로** 공유한다 — 전에는 같은 다섯 줄이 일곱 번
     * 복붙돼 있어, 파일이 이사하면 일곱 곳을 고쳐야 했다(#26에서 실제로 그랬다).
     */
    val controllerWiringFiles: List<File>
        get() = listOf(
            "GoCoachControllerWiring.kt",
            "TurnFlowControllerWiring.kt",
            "GameLifecycleControllerWiring.kt",
            "ScoringControllerWiring.kt",
            "SettingsAndDiagnosticsControllerWiring.kt",
        ).map { compositionFile(it) }

    /**
     * app-android `src/main/` 바로 아래의 한 경로 — `com.worksoc.goaicoach` 패키지 **밖**
     * (리소스 등). 예: `appAndroidMain("res/drawable")`.
     */
    fun appAndroidMain(relativePath: String): File = root.resolve("app-android/src/main/$relativePath")

    /**
     * `application/` 아래의 한 경로를 **지금 실재하는 트리에서** 찾는다.
     *
     * `application/`은 app-android → :shared로 파일 단위 이전을 거쳤고(260816 웨이브 1~6)
     * 지금은 `diagnostic/LocalFileDiagnosticEventExternalSink.kt` 하나만 app-android에 남았다.
     * 정책이 바뀌지 않았는데도 파일이 건너가는 순간 테스트가 깨지는 것을 막으려고, shared를 먼저
     * 보고 없으면 app-android로 떨어진다. [relativePath]는 `.../application/` 뒤의 부분
     * (예: `engine/EngineSession.kt`, `score`).
     */
    fun applicationPath(relativePath: String = ""): File = sharedFirst("application", relativePath)

    /**
     * `match/` 아래의 한 경로. `application/`과 같은 이유로 shared를 먼저 본다
     * (260816에 `match/`는 통째로 :shared로 건너갔다).
     */
    fun matchPath(relativePath: String = ""): File = sharedFirst("match", relativePath)

    private fun sharedFirst(packageDir: String, relativePath: String): File {
        val tail = if (relativePath.isEmpty()) packageDir else "$packageDir/$relativePath"
        val sharedPath = shared(tail)
        if (sharedPath.exists()) return sharedPath
        return appAndroid(tail)
    }

    private fun resolveUnder(base: String, relativePath: String): File =
        if (relativePath.isEmpty()) root.resolve(base) else root.resolve("$base/$relativePath")

    private fun locateRoot(): File {
        System.getProperty("repo.root")?.takeIf { it.isNotBlank() }?.let { injected ->
            val candidate = File(injected).canonicalFile
            check(File(candidate, "settings.gradle.kts").exists()) {
                "-Drepo.root=$injected 가 저장소 루트가 아니다(settings.gradle.kts가 없다)."
            }
            return candidate
        }
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        error("Could not locate repository root from ${File(".").canonicalPath}")
    }
}

/**
 * 계약 테스트가 소스를 **문자열로 읽는 전용 진입점**(refactor backlog #30, 260923).
 *
 * ## 왜 날것 `readText()`를 그대로 두면 안 되는가
 * 이 파일이 없으면(주로 파일이 옮겨져서) 날것 `readText()`는 `java.io.FileNotFoundException`을
 * 던진다 — 메시지가 **"파일이 없다"** 로만 읽히고, 계약 테스트의 진짜 존재 이유인
 * **"경계가 깨졌다"** 로는 읽히지 않는다. 검수자가 `ui/GoCoachApp.kt`를 실제로 `git mv` 해서
 * 확인한 결과, 612건 중 77건이 이 모습으로 죽었고 의미 있는 단언 실패는 0건이었다 — P3의
 * 다른 파일 이동에서도 원인 파악이 그만큼 늦어진다는 뜻이다.
 *
 * 그래서 계약 테스트는 `File.readText()`를 직접 부르지 말고 이 확장 함수를 거친다. 파일이
 * 있으면 동작은 `readText()`와 완전히 같고, 없으면 **절대경로**와 **고칠 자리**(`RepoPaths.kt`)를
 * 못박은 메시지로 [IllegalStateException]을 던진다.
 */
internal fun File.readContractSource(): String {
    check(exists()) {
        "이 계약이 보는 소스가 없다: $absolutePath. 파일이 옮겨졌다면 RepoPaths.kt를 갱신하라."
    }
    return readText()
}

/**
 * [readContractSource]와 같은 진입점을, 줄 단위 계약(줄 수 예산 등)이 쓰는 `File.readLines()`
 * 형태로 준다.
 *
 * ⚠️ **`readContractSource().lines()`로 대체하지 마라** — 동작이 달라진다. `File.readLines()`는
 * `BufferedReader.readLine()` 기반이라 파일이 개행으로 끝나도 끝에 빈 줄을 추가하지 않는 반면,
 * Kotlin의 `String.lines()`는 구분자로 나누는 방식이라 **개행으로 끝나면 빈 문자열이 하나
 * 더 생긴다.** 이 저장소의 파일은 관례상 끝에 개행이 있으므로, 바꿔치면 줄 수 예산 계약들이
 * 전부 조용히 1줄씩 밀린다 — 읽는 방식만 바꾸고 단언은 그대로 두어야 하므로 `readLines()`를
 * 그대로 위임한다.
 */
internal fun File.readContractSourceLines(): List<String> {
    check(exists()) {
        "이 계약이 보는 소스가 없다: $absolutePath. 파일이 옮겨졌다면 RepoPaths.kt를 갱신하라."
    }
    return readLines()
}
