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
            // 바둑 규칙 커널(`shared.domain`)은 `:core:domain`에 있다(refactor backlog #49). `api`인 이유: 이 모듈의
            // 공개 시그니처가 도메인 타입을 그대로 쓰므로, 이 모듈을 쓰는 쪽도 그 타입을 봐야 한다.
            api(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // ⚠️ **모듈 사이에 테스트 코드를 나누는 이 저장소의 유일한 수단이다**(리팩토링 백로그 #71).
        // 여기 있는 것은 `:shared`의 commonTest뿐 아니라 `:app-android`의 test·androidTest도
        // **같은 소스를 직접 컴파일해** 쓴다. 그래서 이 디렉터리에는 **여러 모듈이 함께 쓰는 것만**
        // 둔다 — 한 모듈 전용 픽스처는 그 모듈의 테스트 소스셋에 두어야 한다.
        //
        // ⚠️ **Gradle 모듈로 빼거나 `testFixtures`로 바꾸려 하지 말 것.** 둘 다 이 자리에선 막힌다:
        //   · 새 모듈은 `:shared`에 의존해야 하는데 `:shared`의 commonTest가 그것을 다시 쓰면
        //     **`:shared` ↔ 새 모듈 순환**이다. 그 순환을 푸는 것이 원래 `java-test-fixtures`인데
        //     **KMP에서는 쓸 수 없다.**
        //   · AGP의 `testFixtures`는 **안드로이드 변형만** 내보낸다. `commonTest`는 공통 메타데이터와
        //     (게이트가 열리면) iOS까지 컴파일되므로 안드로이드 전용 아티팩트에 의존할 수 없다.
        //     즉 `testFixtures`로는 **정작 지금 픽스처를 제일 많이 쓰는 commonTest가 빠진다.**
        commonTest.get().kotlin.srcDir("src/commonTestSupport/kotlin")
        // 도메인 골든 판 파서 등 `:core:domain`의 모듈 사이 픽스처 — 같은 수단(srcDir)으로 함께 컴파일한다.
        commonTest.get().kotlin.srcDir("../core/domain/src/commonTestFixtures/kotlin")
    }
}

android {
    namespace = "com.worksoc.goaicoach.shared"
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
