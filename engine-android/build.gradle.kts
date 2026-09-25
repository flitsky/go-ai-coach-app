plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.worksoc.goaicoach.engine.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303")
}

// import 순서 게이트(refactor backlog #72). 어떤 ktlint 규칙이 도는지는 루트 .editorconfig가 정한다 —
// 지금은 import-ordering 하나뿐이다. 위반은 `./gradlew spotlessApply`로 고친다(격리 워크트리에서만).
// ⚠️ target을 모듈의 src 밖(루트 "**" 등)으로 넓히지 말 것 — .claude/worktrees 아래 남의 워크트리 .kt까지 쓸어 담는다.
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
}
