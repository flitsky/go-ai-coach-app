// iOS 앱의 오픈소스 목록(백로그 #294). 안드로이드 목록(`app-android`의 `.android` 플러그인 → `R.raw.aboutlibraries`)은 안드로이드 변형만 알아서,
// iOS가 실제로 링크하는 것(`:app-android`의 iOS 타깃 — Compose Multiplatform · Skiko 등)을 여기서 따로 모은다. 코드는 없다.
// `./gradlew :ios-licenses:iosLicensesJson -PenableIosTargets=true` → build/ios-licenses/aboutlibraries-ios.json
// 앱 묶음에 싣는 것은 Xcode의 「Bundle Android Resources」 단계(`scripts/ios-bundle-resources.sh`)다.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.aboutlibraries)
}

kotlin {
    // 기기 타깃 하나만 — 시뮬레이터와 의존성이 같고, 내보내는 것은 기기에 실리는 목록이다.
    iosArm64()
    sourceSets {
        iosMain.dependencies {
            implementation(project(":app-android"))
        }
    }
}

aboutLibraries {
    // 안드로이드와 같다 — 네트워크 없이, 손으로 적은 항목(KataGo · 신경망 · 엔진 안 C++ 부품 · 배경음악)은 안드로이드와 같은 폴더에서.
    offlineMode = true
    collect {
        configPath = rootProject.file("app-android/config/aboutlibraries")
        // 기본은 `*CompileClasspath`만 본다 — iOS 타깃이 링크하는 것(`iosArm64CompileKlibraries`)을 보려면 전부를 켜고 그 하나로 좁힌다
        // (좁히지 않으면 코틀린 컴파일러의 의존성까지 섞인다 — 앱에 실리지 않는다).
        all = true
        filterVariants.add("iosArm64CompileKlibraries")
    }
}

// 손으로 적은 항목 가운데 iOS에 없는 것을 뺀다 — OpenCV와 그 부품(사진 분석은 iOS에 없다 — U-84) · NDK의 libc++(iOS는 시스템 것).
// ⚠️ 플러그인의 `exclusionPatterns`는 손으로 적은 항목에 듣지 않아서 내보낸 뒤에 거른다.
val androidOnlyBundled = Regex(
    """bundled\.native:(ade|carotene|cpufeatures|flatbuffers|ittnotify|kleidicv|libjpeg-turbo|libpng|libtiff|libwebp|llvm-libcxx|llvm-libcxxabi|onetbb|openexr|openjpeg|protobuf)""",
)
val exported = layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json")
val iosLicensesFile = layout.buildDirectory.file("ios-licenses/aboutlibraries-ios.json")

tasks.register("iosLicensesJson") {
    dependsOn("exportLibraryDefinitions")
    inputs.file(exported)
    outputs.file(iosLicensesFile)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val root = groovy.json.JsonSlurper().parse(exported.get().asFile) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val libraries = (root["libraries"] as List<Map<String, Any?>>).filterNot { androidOnlyBundled.matches(it["uniqueId"] as String) }
        val usedLicenses = libraries.flatMap { (it["licenses"] as? List<*>).orEmpty() }.toSet()
        @Suppress("UNCHECKED_CAST")
        root["licenses"] = (root["licenses"] as Map<String, Any?>).filterKeys { it in usedLicenses }
        // 설명의 「libkatago.so」는 안드로이드 말이다 — iOS는 앱에 정적으로 링크한다.
        root["libraries"] = libraries.map { library ->
            if (library["uniqueId"] == "bundled.native:katago-engine") {
                library + ("description" to "Go engine bundled as the app's AI (statically linked into the app).")
            } else {
                library
            }
        }
        iosLicensesFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.toJson(root))
        }
    }
}
