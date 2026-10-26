// 엔진 어댑터 모듈 — 안드로이드와 iOS가 함께 쓰는 KMP 모듈이다(백로그 #293, 사용자 2026-10-26: 「공유 모듈로 옮기기」).
//
// 소스셋: commonMain = 어댑터 · 수명(슬롯 · 핸들) · GTP 명령 · 파서 · 분석 — 두 앱의 시간 초과 · 재시작 규칙이 **한 곳**이다.
//         androidMain = 프로세스 띄우기(`ProcessBuilder`) · 원격 엔진(HTTP) · 팩토리. iosMain = 앱 안 엔진(#293 — iOS는 자식 프로세스를 못 띄운다).
// ⚠️ iOS 타깃과 게이트는 `:shared`와 같다(`enableIosTargets` — 기본 꺼짐, 함정 75). 게이트가 모듈마다 다르면 iOS 변형 해석이 깨진다.
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
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            // 앱 안 KataGo의 다리(`src/nativeInterop/cinterop/KataGoBridge.h`) — iosMain의 InProcessKataGoRuntime이 부른다.
            target.compilations.getByName("main").cinterops.create("katagobridge") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/katagobridge.def"))
                includeDirs(project.file("src/nativeInterop/cinterop"))
            }
        }
    }

    jvmToolchain(17)

    sourceSets {
        // 공용 원자값(`kotlin.concurrent.atomics`) — 슬롯 · 핸들의 CAS와 쿼리 번호. JVM에서는 java.util.concurrent와 같은 일을 한다.
        all { languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi") }
        commonMain.dependencies {
            // ⚠️ 본 코드는 **엔진 계약·계가기·도메인만** 본다(refactor backlog #49). 어댑터가 앱 계층(`application.*`·
            //    `match`·`shared.policy` 등 `:shared`의 패키지)을 import하면 이제 컴파일 에러다 — 전에는 `api(":shared")`라
            //    아무것도 그것을 막지 않았다. 앱 계층이 필요해 보이면 그 코드는 어댑터가 아니라 `:shared`에 속한다.
            api(project(":core:enginecontract"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        // 테스트는 예전 자리(`src/test/java`) 그대로 안드로이드 JVM 단위 테스트로 돈다 — 진짜 프로세스(가짜 KataGo)를
        // 띄우는 테스트가 있어 JVM이어야 한다.
        // 테스트만 `:shared`를 본다 — 세션 클라이언트(`LocalEngineSessionClient`)와 `syncToGameState`로 어댑터를
        // 실제 호출 경로 그대로 몰아 보는 테스트가 있다. 테스트 소스셋이라 본 코드의 경계는 그대로다.
        getByName("androidUnitTest") {
            kotlin.srcDir("src/test/java")
            dependencies {
                implementation(project(":shared"))
                implementation(kotlin("test"))
                implementation("org.json:json:20240303")
            }
        }
    }
}

android {
    namespace = "com.worksoc.goaicoach.engine.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
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
