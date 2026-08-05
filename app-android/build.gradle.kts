import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // apply false로 등록만 해두고, 실제 적용은 아래에서 google-services.json 존재 여부에
    // 따라 조건부로 한다 — plugins{} 블록 안에서는 file()을 쓸 수 없어 여기서는 등록만 한다.
    alias(libs.plugins.google.services) apply false
}

// google-services.json이 아직 없으면(Firebase 콘솔 설정 전) 플러그인을 적용하지 않는다 —
// 이 플러그인은 그 파일이 없으면 빌드 자체를 실패시키므로, 설정 완료 전에도
// make test/make dev가 계속 동작하도록 조건부로 켠다.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// AdMob 앱/광고단위 ID는 local.properties(gitignored, sdk.dir과 같은 파일)의
// admob.appId / admob.rewardedAdUnitId 두 키로 덮어쓴다 — 없으면 Google 공식 테스트 ID로
// 폴백해 개발 환경에서 항상 즉시 동작한다. 실제 값이 코드/버전관리에 하드코딩되지 않는다
// (premium-mode/README.md Step 3 참고).
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val admobAppId: String = localProperties.getProperty("admob.appId")
    ?: "ca-app-pub-3940256099942544~3347511713"
val admobRewardedAdUnitId: String = localProperties.getProperty("admob.rewardedAdUnitId")
    ?: "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.worksoc.goaicoach"
    compileSdk = 35

    defaultConfig {
        // Firebase 콘솔에 등록된 패키지명(google-services.json의 android_client_info)과
        // 정확히 일치해야 한다 — namespace(Kotlin 패키지/R·BuildConfig 생성 위치)는
        // 이 값과 독립적이라 com.worksoc.goaicoach로 그대로 둔다.
        applicationId = "com.zenit9hub.ai.baduk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")

        // AndroidManifest.xml의 com.google.android.gms.ads.APPLICATION_ID meta-data가 참조하는 자리.
        manifestPlaceholders["admobAppId"] = admobAppId
        // ui/AndroidRewardedAdClient.kt가 읽는 리워드 광고 단위 ID.
        buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$admobRewardedAdUnitId\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        create("friend") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("friend") {
            assets.srcDirs("src/friend/assets")
            jniLibs.srcDirs("src/debug/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":engine-android"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    implementation(libs.play.services.ads)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
