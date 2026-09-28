// `:core:domain` — 바둑 규칙 커널(`shared.domain`)만 담는 모듈(refactor backlog #49·#81).
//
// ⚠️ **본 소스셋은 아무것에도 기대지 않는다.** 이 모듈이 따로 있는 이유가 그것 하나다 — 판·수·규칙은
//    코루틴도, 엔진 계약도, 앱 계층도 모른다는 것을 테스트가 아니라 **컴파일러가** 지키게 하려는 것.
//    그래서 이 파일의 의존 선언은 아래 commonTest의 `kotlin("test")` 한 줄뿐이어야 하고,
//    `DomainModuleBuildScriptContractTest`(app-android 아키텍처 테스트)가 이 파일을 읽어 그것을 확인한다.
//    본 소스셋에 한 줄이라도 더하고 싶다면 그 코드는 이 모듈에 속하지 않는다 — 위 모듈로 올려라.
//
// 플러그인·타깃·iOS 게이트는 `:shared`와 **같아야 한다** — 게이트가 모듈마다 다르면 iOS 변형 해석이 깨진다.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.spotless)
}

val enableIosTargets = providers.gradleProperty("enableIosTargets")
    .map(String::toBoolean)
    .getOrElse(false)

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    if (enableIosTargets) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    jvmToolchain(17)

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // 골든 판 그림 파서처럼 **위 모듈의 테스트도 함께 쓰는** 도메인 전용 픽스처. 위 모듈들은 이 디렉터리를
        // 자기 commonTest에 srcDir로 붙여 **같은 소스를 직접 컴파일한다**(`:shared`의 commonTestSupport와 같은
        // 수단 — 그쪽 build.gradle.kts 주석이 KMP에서 testFixtures를 못 쓰는 이유의 정본이다).
        // ⚠️ 여기에는 도메인 타입만 아는 것만 둔다 — 계가기를 부르는 픽스처는 계가기 모듈의 테스트에 있다.
        commonTest.get().kotlin.srcDir("src/commonTestFixtures/kotlin")
    }
}

android {
    namespace = "com.worksoc.goaicoach.core.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

// import 순서 게이트(refactor backlog #72) — `:shared`와 같다.
// ⚠️ target을 모듈의 src 밖으로 넓히지 말 것 — .claude/worktrees 아래 남의 워크트리 .kt까지 쓸어 담는다.
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
}
