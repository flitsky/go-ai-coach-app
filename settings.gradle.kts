pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "go-ai-coach"

include(":core:domain")
include(":core:enginecontract")
include(":shared")
include(":engine-android")
include(":app-android")
// 두 모델의 PAD 에셋 팩(백로그 #245) — 코드 없이 `src/main/assets/katago/`만 있다. 내용은 `make prepare-friend-assets`가 넣는다(gitignore).
include(":katago_model_pack")
include(":katago_human_pack")

