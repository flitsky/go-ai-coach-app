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
