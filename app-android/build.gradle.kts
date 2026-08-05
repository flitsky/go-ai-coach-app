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

// Google 공식 테스트 값(https://developers.google.com/admob/android/test-ads) — 항상 공개/커밋
// 가능하고, 실제 계정과 무관하다.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedInterstitialAdUnitId = "ca-app-pub-3940256099942544/5354046379"
val testBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"

// 실제 AdMob 앱/광고단위 ID는 local.properties(gitignored, sdk.dir과 같은 파일)의 세 키로
// 주입한다 — 코드/버전관리에 하드코딩하지 않는다(premium-mode/README.md Step 3 참고).
// 다만 이 값이 실제로 쓰이는 것은 release 빌드뿐이다(아래 buildTypes 참고) — 정식 출시 전
// (디버그/친구 배포 빌드)에는 이 값이 local.properties에 있어도 무시하고 항상 위 테스트 값만
// 쓴다. AdMob은 실제 광고 단위에 인위적인 트래픽(자기 클릭, 개발 중 반복 노출 등)이 쌓이면
// 계정을 정지시킬 수 있다는 정책이 있어(Google 공식 "Understanding account suspensions due to
// invalid traffic" 문서) "사람이 토글을 깜빡할 가능성"이 없는 빌드 타입 자체를 안전장치로 쓴다.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val realAdmobAppId: String? = localProperties.getProperty("admob.appId")
val realRewardedInterstitialAdUnitId: String? = localProperties.getProperty("admob.rewardedInterstitialAdUnitId")
val realBannerAdUnitId: String? = localProperties.getProperty("admob.bannerAdUnitId")

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
        getByName("debug") {
            // ui/AdUnitIds.kt가 이 플래그로 테스트/실제 ID를 고른다 — 디버그 빌드는 local.properties
            // 내용과 무관하게 항상 true.
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("String", "REWARDED_INTERSTITIAL_AD_UNIT_ID", "\"$testRewardedInterstitialAdUnitId\"")
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerAdUnitId\"")
        }
        getByName("release") {
            // local.properties에 실제 값이 아직 없으면(미등록 상태) release 빌드도 안전하게 테스트
            // 값으로 폴백한다 — "테스트해야 하는데 실제 광고가 나가는" 상황보다 "출시용인데 테스트
            // 광고가 나가는" 상황(눈에 바로 띄는 버그)이 훨씬 덜 위험하기 때문.
            val useTestAds = realAdmobAppId == null ||
                realRewardedInterstitialAdUnitId == null ||
                realBannerAdUnitId == null
            manifestPlaceholders["admobAppId"] = realAdmobAppId ?: testAdmobAppId
            buildConfigField("boolean", "USE_TEST_ADS", useTestAds.toString())
            buildConfigField(
                "String",
                "REWARDED_INTERSTITIAL_AD_UNIT_ID",
                "\"${realRewardedInterstitialAdUnitId ?: testRewardedInterstitialAdUnitId}\"",
            )
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${realBannerAdUnitId ?: testBannerAdUnitId}\"")
        }
        create("friend") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
            // initWith가 buildConfigField/manifestPlaceholders를 항상 복사한다는 보장이 약해(AGP
            // 버전에 따라 달라질 수 있음) 명시적으로 다시 선언한다 — "friend"는 정식 출시 전 지인
            // 배포용 채널이라 이 안전장치가 가장 중요하게 적용돼야 하는 빌드이기도 하다.
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("String", "REWARDED_INTERSTITIAL_AD_UNIT_ID", "\"$testRewardedInterstitialAdUnitId\"")
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerAdUnitId\"")
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
