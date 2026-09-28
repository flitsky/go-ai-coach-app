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
    // ⚠️ 본 코드는 **엔진 계약·계가기·도메인만** 본다(refactor backlog #49). 어댑터가 앱 계층(`application.*`·
    //    `match`·`shared.policy` 등 `:shared`의 패키지)을 import하면 이제 컴파일 에러다 — 전에는 `api(":shared")`라
    //    아무것도 그것을 막지 않았다. 앱 계층이 필요해 보이면 그 코드는 어댑터가 아니라 `:shared`에 속한다.
    api(project(":core:enginecontract"))
    implementation(libs.kotlinx.coroutines.core)

    // 테스트만 `:shared`를 본다 — 세션 클라이언트(`LocalEngineSessionClient`)와 `syncToGameState`로 어댑터를
    // 실제 호출 경로 그대로 몰아 보는 테스트가 있다. `testImplementation`이라 본 코드의 경계는 그대로다.
    testImplementation(project(":shared"))
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
