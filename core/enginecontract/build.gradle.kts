// `:core:enginecontract` — 엔진 계약(`shared.enginecontract`)과 로컬 계가기(`shared.scoring`)를 담는 모듈
// (refactor backlog #49·#81).
//
// 계가기가 도메인이 아니라 여기 있는 이유: 계가기는 전부 계약 타입(`FinalScoreResult`·`ScoreEstimate`·
// `EngineStatus`)을 돌려준다. 그 타입을 도메인으로 내리는 대안은 import하는 파일 수십 개의 FQN을 바꾸고
// 엔진 상태를 도메인에 들이므로 기각했다.
//
// ⚠️ 본 소스셋은 `:core:domain` 하나에만 기댄다 — 코루틴도 필요 없다(`suspend`만 쓰고 외부 import가 없다).
//    앱 계층(`application.*`·`match`·`shared.policy` 등)은 이 모듈에서 **컴파일러가** 보지 못한다.
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
        commonMain.dependencies {
            // `api`인 이유: 계약·계가기의 공개 시그니처가 도메인 타입(`GameState`·`BoardCoordinate` …)을 그대로 쓴다.
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // `:core:domain`의 모듈 사이 픽스처(골든 판 파서 등) — `:shared`의 commonTestSupport와 같은 수단이다.
        commonTest.get().kotlin.srcDir("../domain/src/commonTestFixtures/kotlin")
    }
}

android {
    namespace = "com.worksoc.goaicoach.core.enginecontract"
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
