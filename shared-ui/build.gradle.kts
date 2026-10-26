// `:shared-ui` — 안드로이드와 iOS가 함께 쓰는 Compose Multiplatform 화면 모듈(백로그 #292 · U-83, 사용자 2026-10-26).
//
// 지금은 iOS 뼈대의 첫 화면 하나뿐이다. #294가 `app-android`의 화면(`ui/…`)을 이리로 옮기고, 그때부터 안드로이드도
// 이 모듈의 화면을 받는다 — 그 전까지 `app-android`는 이 모듈에 기대지 않는다(안드로이드 빌드 · 게이트는 그대로다).
//
// ⚠️ Compose Multiplatform의 판은 앱의 Compose BOM과 **같은 줄**이어야 한다 — 이유와 맞춘 값은 `gradle/libs.versions.toml`의
//    `composeMultiplatform` 줄. 한쪽만 올리면 #294에서 안드로이드 화면이 다른 판으로 끌려간다.
// ⚠️ iOS 타깃과 게이트는 `:shared`와 같다(`enableIosTargets` — 기본 꺼짐, 함정 75). 게이트가 모듈마다 다르면 iOS 변형 해석이 깨진다.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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
            target.binaries.framework {
                // Xcode 프로젝트(`app-ios/`)가 `import SharedUI`로 부르는 이름이다. 정적 프레임워크라 앱에 따로 싣지 않는다.
                baseName = "SharedUI"
                isStatic = true
            }
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
        }
    }
}

android {
    namespace = "com.worksoc.goaicoach.sharedui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

// import 순서 게이트 — `:shared`와 같다. ⚠️ target을 모듈의 src 밖으로 넓히지 말 것(함정 84).
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
}
