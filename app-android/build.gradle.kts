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
// 주입한다 — 코드/버전관리에 하드코딩하지 않는다(PREMIUM_MODE.md Step 3 참고).
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

// 프리미엄 영구 구매 상품 ID(PREMIUM_MODE.md Step 4) — local.properties(gitignored)의
// billing.premiumProductId 키로 주입한다. AdMob과 달리 빌드 타입별 테스트/실제 분기가 없다 —
// Play Billing은 "가짜 상품 ID"가 아니라 Play Console의 라이선스 테스터 계정으로 실제 상품에
// 대한 무과금 테스트를 지원하므로, 모든 빌드 타입이 항상 같은(실제) 상품 ID를 쓴다. 아직 Play
// Console에 상품을 등록하기 전에는 이 플레이스홀더로 폴백한다 — 존재하지 않는 ID라 Play가 상품을
// 못 찾는 형태로 안전하게 실패한다(등록 전 실수로 결제가 되는 상황은 원천적으로 생기지 않는다).
val premiumProductId: String =
    localProperties.getProperty("billing.premiumProductId") ?: "premium_lifetime_placeholder"

// 봇 캐릭터 개별 구매 상품 ID(백로그 #18, 5단계 관장 천원 4,900원) — 위 프리미엄 상품과 같은
// 방식으로 주입하고 같은 이유로 플레이스홀더 폴백을 둔다. **둘은 서로 다른 상품이다**:
// 프리미엄은 앱 전체를 켜고, 이쪽은 캐릭터 한 종의 소유권 + 그 캐릭터와 둘 때의 특전을 판다.
val botCharacterProductId: String =
    localProperties.getProperty("billing.botCharacterProductId") ?: "bot_character_placeholder"

// 개발용 원격 엔진 스파이크(docs/work/roadmap/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md
// Stage E-3) — local.properties(gitignored)의 debug.remoteEngineUrl 키로 맥북 등에서 띄운
// scripts/run-katago-remote-analysis-server.py의 주소(예: http://192.168.0.10:8765/analyze)를
// 넣으면 debug 빌드가 그 서버로 분석을 위임한다. AdMob 키와 같은 이유로 friend/playInternal/
// release는 이 값을 절대 물려받지 않고 항상 빈 문자열(비활성)로 고정한다 — 지인 배포/출시
// 빌드가 실수로 개발자 개인 맥북 IP를 하드코딩한 채 나가는 사고를 원천적으로 막기 위함.
val debugRemoteEngineUrl: String? = localProperties.getProperty("debug.remoteEngineUrl")

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
        buildConfigField("String", "BOT_CHARACTER_PRODUCT_ID", "\"$botCharacterProductId\"")
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
            buildConfigField("String", "REMOTE_ENGINE_URL", "\"${debugRemoteEngineUrl ?: ""}\"")
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
            // Play Console이 "App Bundle에 네이티브 코드가 있는데 디버그 기호가 없다"고 경고했던
            // 원인 — libkatago.so를 패키징 전에 strip해서 jniLibs에 넣고 있었다(지금은 unstripped
            // 원본을 두고 scripts/build-katago-android-spike.sh 참고). 이 설정을 켜면 AGP가
            // 패키징 시 자동으로 strip해서 넣으면서, 벗겨낸 심볼을 App Bundle에 동봉해 Play
            // Console이 업로드 시점에 자동으로 가져간다(매 릴리스 수동 업로드 불필요). FULL(전체
            // DWARF, 파일 큼) 대신 SYMBOL_TABLE(함수명 수준)을 택했다 — KataGo는 소스맵 없이
            // 사내에서만 보는 크래시 로그라 함수명 단위 심볼이면 충분하다.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
                // ⚠️ **엔진은 arm64-v8a로만 빌드된다**(scripts/build-katago-android-spike.sh).
                // 이것이 없으면 번들이 armeabi-v7a/x86/x86_64 스플릿도 만드는데, 그 셋에는
                // `libkatago.so`가 없다 — 그 기기에 깔리면 **조용히 스텁 AI**로 떨어진다(#91 ⓐ).
                // 스플릿을 아예 만들지 않으면 Play가 그 기기를 걸러 준다: **설치되지 않는 편이
                // 가짜 AI로 두는 것보다 낫다.**
                // ⚠️ `defaultConfig`에 넣지 말 것 — debug에도 걸려 x86_64 에뮬레이터가 막힌다.
                abiFilters += "arm64-v8a"
            }
            // 실제 값이 없으면 테스트 값으로 폴백한다. ⚠️ **이제 이것은 정책이 아니라 자리 채우기다**
            // (백로그 #90, 2026-09-06). 구성 시점에는 `buildConfigField`가 무언가를 받아야 하므로
            // 폴백을 남기되, **출시 산출물이 실제로 만들어지는 것은 아래 `verifyReleaseAdmobKeys`가
            // 막는다.** 즉 키 없이 release를 구성할 수는 있어도 패키징할 수는 없다.
            //
            // 예전 근거는 *"출시용인데 테스트 광고가 나가는 건 눈에 바로 띄는 버그라 덜 위험하다"* 였다.
            // ⚠️ **AAB에서는 그 전제가 성립하지 않는다** — 번들은 빌드해서 그대로 올리지, 설치해서
            // 광고를 보지 않는다. 눈에 띌 사람이 아무도 없다.
            // ⚠️ `isNullOrBlank`인 이유: `admob.appId=` 처럼 **빈 값**이면 예전 `== null` 검사는
            // 통과해 `USE_TEST_ADS=false` + 빈 광고 단위 ID로 나갔다 — 폴백보다 나쁜 상태다.
            val useTestAds = realAdmobAppId.isNullOrBlank() ||
                realRewardedInterstitialAdUnitId.isNullOrBlank() ||
                realBannerAdUnitId.isNullOrBlank()
            manifestPlaceholders["admobAppId"] = realAdmobAppId ?: testAdmobAppId
            buildConfigField("boolean", "USE_TEST_ADS", useTestAds.toString())
            buildConfigField(
                "String",
                "REWARDED_INTERSTITIAL_AD_UNIT_ID",
                "\"${realRewardedInterstitialAdUnitId ?: testRewardedInterstitialAdUnitId}\"",
            )
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${realBannerAdUnitId ?: testBannerAdUnitId}\"")
            // 개발자 맥북 IP가 출시 빌드에 섞여 나갈 방법이 없도록 항상 비활성 고정.
            buildConfigField("String", "REMOTE_ENGINE_URL", "\"\"")
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
            // debug에서 initWith해도 지인 배포 채널에는 개발자 맥북 IP를 절대 물려주지 않는다.
            buildConfigField("String", "REMOTE_ENGINE_URL", "\"\"")
        }
        create("playInternal") {
            // Play Console 업로드 전용(PREMIUM_MODE.md Step 4 후속) — friend와 완전히
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
            // Play Console이 업로드마다 "이 App Bundle 유형과 연결된 난독화 파일이 없습니다"라고
            // 경고했던 원인 — playInternal이 friend→debug에서 initWith하느라 R8이 아예 돌지
            // 않았다. release와 같은 설정을 여기에도 걸어 매핑 파일이 번들 안
            // (BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map)에 자동 동봉되게
            // 한다 — 콘솔에 따로 올릴 필요가 없다. dex도 같이 줄어든다.
            // ⚠️ 이 줄들이 "실기로 검증한 코드를 그대로 올린다"는 기존 전제를 깬다. 내부 테스트
            // 빌드는 이제 R8을 거친 별개의 산출물이므로, 검증도 이 빌드 타입으로 해야 한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // release와 동일한 이유(위 release 블록 주석 참고) — initWith가 ndk 설정까지 안정적으로
            // 복사해준다는 보장이 없어(buildConfigField와 같은 사정) 명시적으로 다시 선언한다.
            // playInternal이 실제로 Play Console에 업로드되는 채널이라 이쪽도 반드시 필요하다.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
                // ⚠️ **엔진은 arm64-v8a로만 빌드된다**(scripts/build-katago-android-spike.sh).
                // 이것이 없으면 번들이 armeabi-v7a/x86/x86_64 스플릿도 만드는데, 그 셋에는
                // `libkatago.so`가 없다 — 그 기기에 깔리면 **조용히 스텁 AI**로 떨어진다(#91 ⓐ).
                // 스플릿을 아예 만들지 않으면 Play가 그 기기를 걸러 준다: **설치되지 않는 편이
                // 가짜 AI로 두는 것보다 낫다.**
                // ⚠️ `defaultConfig`에 넣지 말 것 — debug에도 걸려 x86_64 에뮬레이터가 막힌다.
                abiFilters += "arm64-v8a"
            }
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("String", "REWARDED_INTERSTITIAL_AD_UNIT_ID", "\"$testRewardedInterstitialAdUnitId\"")
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerAdUnitId\"")
            buildConfigField("String", "REMOTE_ENGINE_URL", "\"\"")
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
    // ProcessLifecycleOwner — 앱 전역 foreground 복귀(cold start 포함) 감지용.
    // Compose BOM이 이 버전을 이미 constraint로 맞춰주므로 별도 충돌 없음.
    implementation(libs.androidx.lifecycle.process)

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
    // Play In-App Update(백로그 #53) — 설정 화면이 "새 버전이 있는가"를 묻는 데만 쓴다.
    // ⚠️ **app-update-ktx는 일부러 넣지 않았다.** 이 저장소는 콜백 API를
    // suspendCancellableCoroutine으로 직접 감싸는 쪽으로 통일돼 있다(billing·AdMob·Auth 셋 다) —
    // 여기만 -ktx를 쓰면 같은 일을 두 방식으로 하게 된다.
    implementation(libs.play.app.update)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// ─────────────────────────────────────────────────────────────────────────────
// 출시 산출물에 실제 AdMob 키가 들어갔는지 못박는다 (백로그 #90)
//
// **왜 필요한가.** `local.properties`는 gitignore 대상이라 기계마다 없을 수 있다. 그런데 release
// 블록은 키가 없으면 **조용히 구글 테스트 광고 ID로 폴백**한다 — 기계를 바꾸거나 새로 클론한
// 상태에서 `make bundle-aab`을 돌리면 **서명된 정식 AAB가 테스트 광고를 달고 스토어로 나간다**
// (수익 0 + AdMob 정책 문제). AAB는 설치해서 광고를 보지 않으므로 아무도 알아채지 못한다.
// 유일한 신호였던 개발자 행의 `REAL ADS` 문자열(`DeveloperTestSection.kt`)은 ⚠️ #99 이후
// **3시간마다 꺼져서** 다시 10탭해야 보인다 — 사람이 지키는 안전장치로는 더 약해졌다.
//
// ⚠️ **`getByName("release") { … }` 안에서 `error()`를 던지지 말 것.** 빌드타입 블록은 **어느
// Gradle 태스크를 돌려도 구성 시점에 평가되므로**, 키가 없는 기계에서 `testDebugUnitTest`조차
// 못 돌게 된다 — 정확히 이 검사가 보호하려던 "새로 클론한 기계"가 먼저 막힌다. 그래서 검사를
// **태스크로 만들어 패키징 태스크에 매단다**: 구성은 통과하고, 산출물을 만들 때 실행되며 실패한다.
//
// ⚠️ **`assembleRelease`/`bundleRelease`에 `doFirst`로 붙이지 말 것.** 그 둘은 수명주기 태스크라
// 실제 패키징이 **끝난 뒤에** 실행된다 — 실패해도 APK/AAB는 이미 디스크에 남는다. 산출물을 진짜
// 만드는 `packageRelease`(APK)·`packageReleaseBundle`(AAB)에 매달아야 한다.
val realAdmobKeys = mapOf(
    "admob.appId" to realAdmobAppId,
    "admob.rewardedInterstitialAdUnitId" to realRewardedInterstitialAdUnitId,
    "admob.bannerAdUnitId" to realBannerAdUnitId,
)
val googleSampleAdIds = setOf(testAdmobAppId, testRewardedInterstitialAdUnitId, testBannerAdUnitId)

val verifyReleaseAdmobKeys = tasks.register("verifyReleaseAdmobKeys") {
    group = "verification"
    description = "release 산출물이 실제 AdMob 키로 빌드되는지 확인한다 (백로그 #90)."
    doLast {
        val problems = realAdmobKeys.mapNotNull { (key, value) ->
            when {
                value.isNullOrBlank() -> "$key — local.properties에 없거나 값이 비어 있다"
                // ⚠️ 구글 샘플 ID를 실제 키 자리에 붙여 넣으면 폴백보다 **나쁘다** —
                // `USE_TEST_ADS`가 false가 되어 개발자 행조차 "REAL ADS"라고 거짓말한다.
                value in googleSampleAdIds -> "$key — 구글 샘플 광고 ID다(실제 키가 아니다)"
                !value.startsWith("ca-app-pub-") -> "$key — `ca-app-pub-`로 시작하지 않는다: $value"
                else -> null
            }
        }
        if (problems.isNotEmpty()) {
            error(
                buildString {
                    appendLine("출시 산출물을 만들 수 없다 — 실제 AdMob 키가 준비되지 않았다(백로그 #90).")
                    problems.forEach { appendLine("  · $it") }
                    appendLine()
                    appendLine("local.properties(gitignored)에 세 키를 넣고 다시 시도할 것:")
                    appendLine("  admob.appId=ca-app-pub-…~…")
                    appendLine("  admob.rewardedInterstitialAdUnitId=ca-app-pub-…/…")
                    appendLine("  admob.bannerAdUnitId=ca-app-pub-…/…")
                    appendLine()
                    append("테스트 광고로 배포하려면 release가 아니라 friend/playInternal 빌드를 쓸 것 ")
                    append("— 그 둘은 USE_TEST_ADS를 하드코딩한다.")
                },
            )
        }
    }
}

// ⚠️ friend/playInternal/debug는 여기 걸리지 않는다 — 그 셋은 폴백을 쓰는 것이 아니라
// `USE_TEST_ADS="true"`를 **하드코딩**해서 실제 키를 아예 참조하지 않는다. 손대지 말 것.
tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }
    .configureEach { dependsOn(verifyReleaseAdmobKeys) }
