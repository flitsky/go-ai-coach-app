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
// iOS 앱의 오픈소스 목록(백로그 #294) — iOS 타깃을 켤 때만 들인다(`enableIosTargets`, 함정 75). 안드로이드 빌드는 이 모듈을 모른다.
if (providers.gradleProperty("enableIosTargets").map(String::toBoolean).getOrElse(false)) {
    include(":ios-licenses")
}
