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

// 프리미엄 영구 구매 상품 ID(premium-mode/README.md Step 4) — local.properties(gitignored)의
// billing.premiumProductId 키로 주입한다. AdMob과 달리 빌드 타입별 테스트/실제 분기가 없다 —
// Play Billing은 "가짜 상품 ID"가 아니라 Play Console의 라이선스 테스터 계정으로 실제 상품에
// 대한 무과금 테스트를 지원하므로, 모든 빌드 타입이 항상 같은(실제) 상품 ID를 쓴다. 아직 Play
// Console에 상품을 등록하기 전에는 이 플레이스홀더로 폴백한다 — 존재하지 않는 ID라 Play가 상품을
// 못 찾는 형태로 안전하게 실패한다(등록 전 실수로 결제가 되는 상황은 원천적으로 생기지 않는다).
val premiumProductId: String =
    localProperties.getProperty("billing.premiumProductId") ?: "premium_lifetime_placeholder"

// versionCode/versionName은 저장소 루트의 version.properties(커밋 대상 — local.properties와
// 달리 비밀값이 아니라 "지금까지 몇 번 릴리스했는지"를 나타내는 공유 상태다)에서 읽는다.
// `make release`/`make bundle-aab`/`make play-internal-aab`이 Gradle을 부르기 전에
// `scripts/bump-version.sh`로 이 파일을 먼저 갱신한다 — Play Console은 한 번 쓴 versionCode를
// 절대 재사용할 수 없어서(재시도 업로드마다 실제로 증가해야 함), 매 릴리스 빌드마다 자동으로
// 증가시키는 쪽이 사람이 수동으로 두 값을 맞춰 올리는 것보다 안전하다.
val versionProperties = Properties().apply {
    val versionPropertiesFile = rootProject.file("version.properties")
    if (versionPropertiesFile.exists()) {
        versionPropertiesFile.inputStream().use { load(it) }
    }
}
val appVersionCode: Int = versionProperties.getProperty("VERSION_CODE")?.toInt() ?: 1
val appVersionName: String = versionProperties.getProperty("VERSION_NAME") ?: "0.1.0"

android {
    namespace = "com.worksoc.goaicoach"
    compileSdk = 36

    defaultConfig {
        // Firebase 콘솔에 등록된 패키지명(google-services.json의 android_client_info)과
        // 정확히 일치해야 한다 — namespace(Kotlin 패키지/R·BuildConfig 생성 위치)는
        // 이 값과 독립적이라 com.worksoc.goaicoach로 그대로 둔다.
        applicationId = "com.zenit9hub.ai.baduk"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "PREMIUM_PRODUCT_ID", "\"$premiumProductId\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Play Console 업로드용 release keystore(~/.android/, 저장소 밖 — local.properties의
        // release.* 4개 키로 주입). 값이 없으면(키스토어 미생성 상태) storeFile을 세팅하지 않아
        // playInternal을 실제로 빌드하기 전까지는 make test/make dev에 영향이 없다.
        create("release") {
            val storeFilePath = localProperties.getProperty("release.storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
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
            // playInternal과 동일한 Play Console 업로드용 release keystore로 서명한다 — 이 줄이
            // 없으면 signingConfig가 지정되지 않아 bundleRelease/assembleRelease 결과물이
            // 조용히 서명되지 않은 채로 나온다(AGP는 release 빌드타입에 서명을 자동 적용하지
            // 않음). local.properties에 release.* 키가 없으면(키스토어 미생성) 서명 없이
            // 빌드되어 이 빌드도 실패한다 — playInternal과 같은 전제조건.
            signingConfig = signingConfigs.getByName("release")
            // Play Console App Bundle Explorer가 "앱 최적화/최적화 비율/난독화 비율/축소 비율/R8
            // 구성" 5개 항목을 전부 경고로 표시했던 원인 — release 빌드에서 R8이 아예 실행되지
            // 않고 있었다(minify/shrink 둘 다 AGP 기본값 false). friend/playInternal은 debug에서
            // initWith하므로 이 설정과 무관하게 그대로 유지된다(지인 배포·내부 테스트 채널 영향 없음).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        create("playInternal") {
            // Play Console 업로드 전용(premium-mode/README.md Step 4 후속) — friend와 완전히
            // 같은 debug KataGo 엔진/에셋(release 엔진 준비 불필요)을 쓰지만, release keystore로
            // 서명해 Play Console 내부 테스트 트랙에 올릴 수 있게 한다. friend 자체는 건드리지
            // 않는다(지인 배포용 debug 서명 그대로 유지).
            initWith(getByName("friend"))
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("release")
            // friend는 debug에서 initWith해 isDebuggable=true를 그대로 물려받는다 — 사이드로드만
            // 하는 friend에는 문제없지만, Play Console은 debuggable 빌드 업로드 시 게시 전 반드시
            // 꺼야 한다고 경고한다. playInternal만 명시적으로 false로 되돌린다.
            isDebuggable = false
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
        getByName("playInternal") {
            assets.srcDirs("src/friend/assets")
            jniLibs.srcDirs("src/debug/jniLibs")
        }
        getByName("release") {
            // 별도로 검증된 "release 전용" KataGo 엔진 바이너리를 새로 준비하지 않는다(사용자
            // 결정, 2026-08-09) — friend/playInternal과 동일하게 이미 검증된 debug 엔진 .so와
            // 모델/설정 에셋을 그대로 재사용한다. 스토어에 올리는 AAB/APK가 설치 후 별도
            // adb push(seed-engine) 없이도 자체적으로 동작해야 하므로 앱마켓 배포에는 필수다.
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
    // firebase-analytics는 의도적으로 뺐다 — logEvent() 등 실제 사용처가 코드에 전혀 없어
    // SDK만 자동 수집을 계속하는 상태였다(2026-08-09 grep으로 확인). 안 쓰는 수집 SDK는
    // 끄는 옵션을 만들기보다 아예 빼는 쪽이 확실하다 — 나중에 실제로 이벤트를 남기고 싶어지면
    // 이 줄을 되살리고 로그인 지점을 만들면 된다.

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    implementation(libs.play.services.ads)
    // 콜백 기반 Billing API를 suspendCancellableCoroutine으로 직접 감싼다(AndroidAuthClient/
    // AndroidRewardedInterstitialAdClient와 동일한 기존 패턴) — billing-ktx의 suspend 확장 함수는
    // 쓰지 않으므로 별도로 추가하지 않는다.
    implementation(libs.play.billing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
