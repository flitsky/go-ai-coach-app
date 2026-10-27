# App Store Connect 답안지: 앱 개인정보 · 연령 등급 · 수출 규정 · 콘텐츠 권한 · 심사 메모 (2026-10-27 작성, 같은 날 검증)

> 이 문서는 **App Store Connect(ASC) 앞에 앉아 이 문서만 보고 칸을 채울 수 있게** 만들었다. `work/plans/PLAY_CONSOLE_PRIVACY_ANSWERS.md`의 iOS판이다.
> 형식은 Play 답안지와 같다. 각 질문에 답과 근거를 붙였다. 근거는 코드 경로(저장소 기준 상대 경로)나 외부 규칙 URL이다.
> 코드로 알 수 없는 것은 **⚠️ 사용자가 정할 것**으로 표시했다. 사용자가 정하지 않았는데 이 문서가 고른 값은 **「제가 정한 것」**으로 표시했다.
>
> - 대상: iOS 1.8.4(빌드 10804), 번들 `com.zenit9hub.ai.baduk`, 아이폰 전용·세로 전용, 최소 iOS 26.1. 백로그 #298의 「앱 개인정보 · 연령 등급 · 수출 규정 · 심사 메모 · 가격 · 국가」 칸이다.
> - **기준 커밋은 `43bc9ebc`다**(2026-10-27 17:20). 초안을 쓰던 중에 이 커밋이 들어왔다. 그 커밋으로 ⓐ 모든 광고 요청에 `npa=1`(비맞춤)이 붙고 ⓑ 앱을 열 때마다 UMP 동의 정보를 새로 묻는다(`app-ios/GoCoach/GoogleAdsSource.swift` `adRequest()` · `refreshConsentInfo()`, `app-ios/GoCoach/GoCoachApp.swift`). 이 답안지는 그 상태를 기준으로 고쳤다.
> - 이 문서는 **답안지일 뿐 제출이 아니다**. 입력과 제출은 사용자가 한다. 진행 상태는 #298 카드에 적는다(답과 진척을 겹쳐 적지 않는다).
> - 자리(사용자 2026-10-27 「Play 짝으로 셋」): 이 파일 · 등재 글 `work/app-store-assets/app_store_listing.txt` · 출시 절차와 #297 시험표 `work/plans/APP_STORE_LAUNCH_PLAN.md`.

## ⚠️ 이 답안지의 유효기간: 먼저 읽을 것

아래 답은 **다음 전제가 지금 참이라는 것**에 기댄다. 하나라도 바뀌면 해당 절을 다시 답한다.

| 전제 | 지금 값 | 근거 | 지탱하는 답 |
| --- | --- | --- | --- |
| ATT 창 없음 | `NSUserTrackingUsageDescription` 없음. 앱 코드에 `ATTrackingManager` 호출 0건. GMA는 ATT 상태를 **읽기만** 한다(바이너리 문자열 `trackingAuthorizationStatus`, `requestTrackingAuthorization` 없음) | `app-ios/Info.plist`(광고 주석), `app-ios/GoCoach/GoogleAdsSource.swift`(U-86) | ① 기기 ID 「추적 = 아니오」 |
| 모든 광고 요청이 비맞춤 | `Extras.additionalParameters = ["npa": "1"]`을 보상형·배너 요청에 붙인다 | `GoogleAdsSource.swift` `adRequest()`(커밋 `43bc9ebc`) | ① 추적 답, ⑧ 방침 ③ |
| UMP 동의 정보는 앱을 열 때마다 갱신 | 창이 선 뒤 `requestConsentInfoUpdate`(폼은 띄우지 않는다). 폼은 보상형 광고 직전에 | `GoCoachApp.swift` `SceneDelegate`, `GoogleAdsSource.swift` `refreshConsentInfo()` · `gatherConsent()` | ① 1단계(광고를 안 봐도 UMP 통신이 있다), ⑧ 방침 ④ |
| 앱 자신의 코드에 네트워크 송신이 없다 | 모든 `commonMain`·`iosMain`(app-android, shared, engine-android, core/*)과 `app-ios/GoCoach`·`app-ios/AssetDownloader`에서 `URLSession`·`HttpClient`·ktor·`HttpURLConnection`·`java.net`·okhttp·`NWConnection`·분석·충돌 SDK 0건(주석의 Firebase 언급만 있다) | 소스 grep(아래 「재현용」) | ① 「개발자 자신의 수집 없음」. ⚠️ 바이너리 `nm`으로는 확인할 수 없다 — GMA가 정적으로 링크되어 `NSURLSession` 등이 앱 바이너리에 들어온다 |
| 로그인 없음 | `FeatureFlags.isLoginEnabled = false`, iOS `authClient = IosNoAuthClient` | `app-android/src/commonMain/kotlin/com/worksoc/goaicoach/ui/foundation/FeatureFlags.kt` 14행, `app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ios/IosAppPlatform.kt` 102·191행 | ① 연락처·사용자 ID 없음, 계정 삭제 요건 없음 |
| 카메라·사진·위치 권한 없음 | `app-ios/Info.plist`와 빌드 설정(`INFOPLIST_KEY_NS*`)에 권한 문구가 하나도 없다. 빌드 결과 Info.plist에도 없다 | `app-ios/Info.plist`, `app-ios/GoCoach.xcodeproj/project.pbxproj` | ① 사진·위치(정밀) 없음 |
| SDK 판 | Google Mobile Ads **12.14.0**, UMP **3.1.0**(둘 다 정적 라이브러리) | `app-ios/GoCoach.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` | ① 라벨 값 전부, ② SDK 매니페스트. 패키지를 갱신하면 12.x 안에서 오를 수 있다. archive할 때마다 Privacy Report로 다시 본다 |
| 1일 1회 뽑기는 돈으로 살 수 없음 | 광고 1편(구독자는 광고 없이)으로 24시간에 1회. 구독해도 횟수는 늘지 않는다 | `shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/dailydraw/DailyDraw.kt` | ③ Loot Boxes 「아니요」 |
| 앱 자신의 코드에 웹뷰·브라우저 없음 | 앱 코드에 `WKWebView`·`SFSafariViewController` 0건. 링크는 `LocalUriHandler.openUri`로 앱 밖에서 연다. ⚠️ 다만 **GMA·UMP는 광고와 동의 폼을 그리려고 WKWebView를 쓴다**(앱 바이너리가 WebKit을 링크). GMA에는 `SFSafariViewController` 문자열도 있다(광고 클릭 처리로 보인다, 동작은 실기 미확인) | `app-ios/GoCoach`, `app-android/src/iosMain` grep · `otool -L`/`nm -u`(재현용) | ③ Unrestricted Web Access 「아니요」 |
| 앱 자신의 코드에 암호화 없음 | KataGo는 SHA-2(해시)와 zlib(압축)뿐. ⚠️ **GMA는 OS 암호 API를 쓴다**: `CCCrypt`·`CC_SHA256`·`CC_MD5`·`SecKeyCreateEncryptedData`·`SecKeyVerifySignature`·`SecRandomCopyBytes` | ④ 참고 | ④ `ITSAppUsesNonExemptEncryption = NO` |

---

## ① 앱 개인정보(App Privacy)

### ⚠️ Apple의 「수집(collect)」은 **기기 밖으로 보내 실시간 처리보다 오래 접근할 수 있게 하는 것**이다

기기 안에서만 처리하는 데이터는 수집이 아니다. 앱 데이터는 거의 모두 기기 안에 남는다. 그래서 신고할 것은 **Google SDK(GMA·UMP)가 보내는 것**뿐이다.
(https://developer.apple.com/app-store/app-privacy-details/)

### 1단계: 수집 여부

| 질문 | 답 | 근거 |
| --- | --- | --- |
| Do you or your third-party partners collect data from this app? | **Yes** | ⚠️ **우리 코드가 아니라 Google Mobile Ads·UMP 때문이다.** Apple 규칙상 앱에 넣은 제3자 SDK가 모으는 데이터도 신고해야 한다(같은 페이지). 앱 매니페스트가 비어 있다고 「No」로 답하면 **틀린다** |

- ⚠️ **「광고를 안 보면 수집이 없다」로 줄여 답하지 않는다.** `43bc9ebc`부터 앱을 열 때마다 UMP가 Google에 동의 정보를 묻는다. 구독자도 마찬가지다(호출에 구독 조건이 없다). 배너도 첫 보상형 광고 전에 뜰 수 있다. GMA 자체(`MobileAds.shared.start()`)는 여전히 첫 보상형 광고 때 시작한다.
- 구독자에게는 광고가 없다. 그것도 뺄 이유가 되지 않는다. 지역(EEA 동의 여부)이나 유료·무료에 따라 수집이 달라도 전부 공개한다(https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy).

### 앱 자신의 수집: 없음(코드로 확인)

| 데이터 | 저장 위치 | 기기 밖으로 | 판정 |
| --- | --- | --- | --- |
| 설정·진행도(대국 설정, 화면 모드, 언어, 출석, 캐릭터·조각, 1회권, 뽑기 시각, 승급 대국 기력, 이어하기 판) | `NSUserDefaults(suiteName: go_ai_coach_*)`, 앱 전용 | 앱이 보내지 않는다 | 수집 아님 |
| 기기 식별자(앱이 만든 무작위 UUID, 하드웨어 ID 아님) | `go_ai_coach_device_identity` | 전송 경로 없음 | 수집 아님. **User ID로 답하지 않는다** |
| 대국 기록 | Application Support `files/game_history/` | 아니오 | 수집 아님. Gameplay Content는 기기 밖으로 보낼 때만 신고한다 |
| 프리미엄 상태(구독 확인 결과, 광고 1시간 시작 시각) | UserDefaults | 앱이 보내지 않는다 | 수집 아님 |
| 진단 이벤트 로그·실행 기록·분석 캐시·엔진 측정 | Application Support `files/` | 자동 업로드 없음. 「진단 로그 복사」는 **사용자가 누를 때만** 클립보드로 간다(`UIPasteboard`, `ios/IosAppPlatform.kt` 186행) | 수집 아님. 앱에 「개발자에게 보내기」 기능이 없다(`mailto` 0건) |
| 사용자가 고른 SGF 파일 | `UIDocumentPicker(asCopy)` 사본을 메모리로 읽는다 | 아니오 | 수집 아님 |
| 구독 거래 | StoreKit 2(시스템 관리) | Apple로만 간다. 개발자 서버로는 가지 않는다 | **수집 아님.** Apple이 처리하고 개발자가 볼 수 없는 결제 정보는 수집이 아니다. Apple이 수집하는 데이터는 개발자가 공개하지 않는다 |
| 엔진 모델 팩 `katago-models` | Background Assets(앱 그룹 `group.com.zenit9hub.ai.baduk`) | 내려받기만 한다(Apple → 기기) | 수집 아님 |
| iPhone 기기 백업(iCloud·컴퓨터) | OS 기능 | Apple 기능 | 앱의 수집 아님(CloudKit·`NSUbiquitousKeyValueStore` 없음, 엔타이틀먼트는 앱 그룹 하나뿐: `app-ios/GoCoach.entitlements`) |

근거 파일: `app-android/src/iosMain/kotlin/com/worksoc/goaicoach/kv/UserDefaultsKeyValueStore.kt`, `kv/IosAppStores.kt`, `kv/IosAppFileStore.kt`, `app-android/src/commonMain/kotlin/com/worksoc/goaicoach/kv/KvProgressStores.kt`(`Uuid.random`), `app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ui/gamerecord/SgfFilePickerIos.kt`, `app-ios/GoCoach/AppStoreSubscriptionSource.swift`, `app-ios/GoCoach/AppleHostedModelSource.swift`.

### 2단계: 데이터 유형별 답(Google SDK 몫)

> 값의 출처는 **앱에 실린 두 SDK의 내장 매니페스트**와 **Google의 iOS 공개 안내**(https://developers.google.com/admob/ios/privacy/data-disclosure)다. Apple 절차는 매니페스트를 합친 Xcode 보고서를 기준으로 삼는다. 출시 빌드를 Product > Archive로 만든 뒤
> Organizer > **Generate Privacy Report**로 합친 보고서와 대조해 입력한다(https://developer.apple.com/documentation/bundleresources/describing-data-use-in-privacy-manifests).
> 두 SDK가 같은 유형을 선언하면 합친다. 연결은 하나라도 「연결」이면 「연결」이다. Apple 규칙상 「당신 또는 제3자 파트너」가 연결하면 연결이다. 목적은 합집합이다.
> 두 출처가 어긋나면 **더 많이 밝히는 쪽**을 골랐다(제가 정한 것). 과대 신고는 위반이 아니고 과소 신고는 위반이다.

| ASC 범주 › 유형 | 수집 | 사용자에 연결 | 추적 | 목적(ASC 표기) | 근거 |
| --- | --- | --- | --- | --- | --- |
| Location › **Coarse Location** | 예 | **예** | 아니오 | Third-Party Advertising · Developer's Advertising or Marketing · Analytics · App Functionality | GMA: linked=true, tracking=false (3P·Dev·Analytics). UMP: linked=false (AppFunctionality). Google 안내: IP 주소로 대략적인 위치를 추정 |
| Identifiers › **Device ID** | 예 | **예** | ⚠️ **아니오**(아래 절) | Third-Party Advertising · Developer's Advertising or Marketing · Analytics | GMA: linked=true, **tracking=true**. Google 안내: 광고 식별자 **또는 앱·개발자 범위 기기 식별자**. GMA가 `identifierForVendor`를 부른다(바이너리 심볼) |
| Usage Data › **Product Interaction** | 예 | **예** | 아니오 | Third-Party Advertising · Developer's Advertising or Marketing · Analytics · App Functionality | GMA: linked=true (3P·Dev·Analytics). UMP: linked=false (AppFunctionality) |
| Usage Data › **Advertising Data** | 예 | **예** | 아니오 | Third-Party Advertising · Developer's Advertising or Marketing · Analytics | GMA: linked=true |
| Diagnostics › **Crash Data** | 예 | 아니오 | 아니오 | Analytics | GMA: linked=false (Analytics만). Google 안내: "Non-user related crash logs" |
| Diagnostics › **Performance Data** | 예 | ⚠️ **예**(보수적, 제가 정한 것) | 아니오 | Third-Party Advertising · Developer's Advertising or Marketing · Analytics · App Functionality | GMA 매니페스트: linked=false. UMP: linked=false. **그러나 Google 안내는 "User-associated performance data"라고 쓰고, 광고 표시와 다른 광고 주체와의 공유에도 쓰일 수 있다고 쓴다.** 그래서 「연결 예」로 올렸다. Xcode 보고서는 「연결 아님」으로 보일 것이다 — 그보다 많이 밝히는 것은 위반이 아니다 |
| Diagnostics › **Other Diagnostic Data** | 예 | 아니오 | 아니오 | Third-Party Advertising · Developer's Advertising or Marketing · Analytics | GMA: linked=false |

매니페스트 파일: `app-ios/build/DerivedData/Build/Products/Debug-iphonesimulator/GoCoach.app/Frameworks/GoogleMobileAds.framework/PrivacyInfo.xcprivacy`와 `.../UserMessagingPlatform.framework/PrivacyInfo.xcprivacy`. 둘 다 gitignore된 빌드 산출물이다. 각 framework의 Info.plist로 12.14.0과 3.1.0임을 확인했다. GMA 매니페스트에는 최상위 `NSPrivacyTracking` 키와 `NSPrivacyTrackingDomains` 키가 없다.

- ⚠️ **「Developer's Advertising or Marketing」**: 우리는 자사 광고를 하지 않는다. 그래도 GMA 매니페스트가 광고 관련 모든 유형에 이 목적을 선언한다. Xcode 보고서도 이 목적을 보여 준다. 따라서 **넣는다**(제가 정한 것).
- 성능 데이터를 매니페스트대로 「연결 아님」으로 둘지는 사용자가 바꿔도 된다. 어느 쪽도 Apple·Google 공식 문서로 확정되지 않는다.

**예상되는 제품 페이지 라벨(추적 = 아니오일 때)**

- 사용자에게 연결된 데이터: 위치(대략적), 식별자(기기 ID), 사용 데이터(제품 상호작용, 광고 데이터), 진단(성능)
- 사용자에게 연결되지 않은 데이터: 진단(충돌, 기타 진단)
- 사용자를 추적하는 데 사용되는 데이터: **없음**

### ⚠️ 기기 ID의 「추적」: 사용자가 정할 것(새 미결 결정 U-87 제안)

**사실**
- GMA 12.14.0 매니페스트는 Device ID를 tracking=true로 선언한다. ATT 허락 여부에 따라 달라지는 변형은 없다.
- U-86(2026-10-27 사용자): 「ATT 창 없이 비맞춤 광고만」. 코드: ATT 요청 없음 → IDFA는 0이다(Apple). GMA는 ATT 허락이 없으면 광고 요청에 IDFA를 보내지 않는다(https://developers.google.com/admob/ios/privacy/idfa). `43bc9ebc`부터 모든 요청에 `npa=1`이 붙는다.
- 앱 바이너리는 GMA를 통해 `AdSupport.framework`를 링크하고 `ASIdentifierManager`를 참조한다(Debug 빌드 `otool -L`/`nm -u`). `AppTrackingTransparency.framework`는 링크하지 않는다.
- Google: 비맞춤 광고도 빈도 제한·집계 보고·부정행위 방지에 식별자를 쓴다(https://support.google.com/admob/answer/7676680).
- Apple의 추적 정의: 앱에서 모은 사용자·기기 데이터를 **다른 회사의 데이터와 연결해** 타깃 광고나 광고 측정에 쓰거나, 데이터 브로커와 공유하는 것. 다른 개발자 앱의 데이터와 합쳐 광고에 쓰는 SDK를 넣으면 개발자가 쓰지 않아도 추적이다(https://developer.apple.com/app-store/user-privacy-and-data-use/). 추적하려면 ATT 허락이 필요하다(5.1.2(i)).
- 포럼 사례(공식 문서 아님): 2026년 8월, ASC 라벨에 「Device ID used for tracking」이 남아 있는데 빌드에 ATT가 없어 5.1.2(i)로 반려됐다는 보고가 있다. Apple DTS는 제3자 SDK가 AdSupport나 ATT를 여전히 링크하는지 물었다(https://developer.apple.com/forums/thread/840650).

| 길 | ASC 답 | 앱에 필요한 것 | 근거와 한계 |
| --- | --- | --- | --- |
| **ⓐ U-86 유지(권장, 제가 정한 것)** | Device ID 「추적 = **아니오**」 | 이미 됐다: ATT 없음 + `npa=1`(`43bc9ebc`). 선택으로 더 보탤 수 있다: `MobileAds.shared.requestConfiguration.publisherPrivacyPersonalizationState = .disabled`(Publisher Privacy Treatment, Beta, 12.14 헤더에 있음), `setPublisherFirstPartyIDEnabled(false)`(코드 변경, 승인 필요) | U-86과 맞는 유일한 답이다. **한계**: 이 답이 Apple 정의상 참이라고 판정한 Apple·Google 공식 문서는 없다. Xcode Privacy Report는 계속 tracking=true를 보여 준다. `npa=1`이나 PPT가 공개 항목을 줄인다고 쓴 Google 문서도 없다. 반려되면 심사 회신에 위 사실(ATT 없음, IDFA 0, `npa=1`, 추적 도메인 없음)을 낸다 |
| ⓑ ATT 추가 | Device ID 「추적 = **예**」 | `NSUserTrackingUsageDescription`, `ATTrackingManager.requestTrackingAuthorization`, 방침 수정. **U-86을 뒤집는다** | 매니페스트와 그대로 일치한다. 대신 ATT 창이 하나 늘고 U-86과 어긋난다 |
| ✗ 하지 말 것 | 「추적 예」로 답하고 ATT 없이 제출 | — | 위 포럼에서 반려가 보고된 조합이다 |

- 참고: 안드로이드는 `npa`를 붙이지 않는다(`app-android/src/main/java/com/worksoc/goaicoach/GoAiCoachApplication.kt`는 G 등급만 설정). 「비맞춤만」은 지금 iOS에서만 코드로 참이다. 방침(⑧)에 쓸 때 플랫폼을 나눈다.

### 고르지 않는 유형(전부 「수집 안 함」)

| 유형 | 이유 |
| --- | --- |
| Contact Info(이름·이메일·전화·주소) | 계정이 없다. 앱 안에 입력란이 없다(닉네임 줄은 꺼져 있고 기기 안에만 있다: `ui/account/MyPageScreen.kt` `ShowUserProfileRow = false`) |
| Health & Fitness, Sensitive Info, Contacts, Surroundings, Body | 해당 API·권한 없음 |
| Financial Info › Payment Info, Credit Info | 결제는 Apple이 앱 밖에서 받는다. 개발자는 볼 수 없다 |
| Purchases › Purchase History | StoreKit 결과는 기기에만 있다. GMA·UMP 매니페스트도 선언하지 않는다 |
| Location › Precise Location | 위치 권한 문구가 없다. 앱 코드에 위치 API가 없다 |
| User Content(사진·동영상·오디오·Gameplay Content·Customer Support·기타) | 기기 밖으로 안 나간다. 카메라·사진 권한 문구가 없다. 앱에 문의 보내기 기능이 없다 |
| Browsing History, Search History | 앱 안 브라우저·검색 전송 없음 |
| Identifiers › User ID | 앱 UUID는 기기 밖으로 안 나간다 |
| Usage Data › Other Usage Data, Other Data | 두 SDK 매니페스트에 없고 앱 코드도 보내지 않는다 |

### 입력 순서(ASC)

1. 앱 › App Privacy › **Privacy Policy URL**: `https://rezen.dev/apps/baduk-ai/privacy/`(앱 상수 `ui/foundation/AppLinks.kt` `PRIVACY_POLICY_URL`). ⚠️ **⑧을 고쳐 배포한 뒤** 제출한다. Privacy Choices URL은 선택이고 비워 둔다(제가 정한 것).
2. Get Started → **Yes** → 위 7개 유형을 체크 → 유형마다 목적 → 연결 여부 → 추적 여부.
3. **Publish**. 답은 앱 단위다. 앱 업데이트 없이 언제든 고칠 수 있다. 입력은 Account Holder·Admin·App Manager만 할 수 있다.
4. 출시 빌드를 archive한 뒤 Generate Privacy Report와 대조한다. 보고서와 다른 곳은 위 표의 두 곳(성능 데이터 연결, 기기 ID 추적)뿐이어야 한다. **SDK 판이 바뀌면 다시 본다**(GMA v12 폐기 예정 2027년 1분기: https://developers.google.com/admob/ios/deprecation).

### Play 답안지(A-4)와 무엇이 다른가

| Play A-4(2026-09-19 실제 제출) | App Store | 왜 다른가 |
| --- | --- | --- |
| 위치 › 대략적인 위치 | Location › Coarse Location | 같다 |
| 앱 활동 › 앱 상호작용 | Usage Data › Product Interaction **+ Advertising Data** | Apple은 광고 데이터를 따로 묻는다. GMA 매니페스트도 따로 선언한다 |
| 앱 정보 및 성능 › 진단 | Diagnostics › Crash · Performance · Other | Apple이 셋으로 나눈다 |
| 기기 또는 기타 ID | Identifiers › Device ID | Android 근거(AAID·`AD_ID` 권한·앱 세트 ID)가 iOS에서는 「IDFA 0(ATT 미사용) + IDFV 등 앱·개발자 범위 ID」로 바뀐다 |
| 「공유 = 예」 | 「연결」·「추적」 | 개념이 다르다. Apple에는 「공유」 칸이 없고 추적이 따로 있다 |
| SDK는 프로세스 시작 때 자동 초기화(`MobileAdsInitProvider`) | UMP 동의 정보는 실행마다, GMA 시작은 첫 보상형 광고 때 | 시작 시점이 달라도 답은 같다 |
| 금융 정보 = 아니오(Play Billing이 처리) | Payment·Purchases = 아니오(StoreKit, Apple이 처리) | 결제 처리자만 바뀐다 |

---

## ② 개인정보 매니페스트 점검(`app-ios/GoCoach/PrivacyInfo.xcprivacy`)

### 앱 자신의 매니페스트 대 코드·바이너리

| 키 | 매니페스트 값 | 코드·바이너리 사실 | 판정 |
| --- | --- | --- | --- |
| `NSPrivacyTracking` | `false` | 앱 자신의 코드에 추적이 없다(ATT 요청·IDFA 호출 0건) | ✅ |
| `NSPrivacyTrackingDomains` | 빈 배열 | 앱 자신의 코드에 네트워크가 없다 | ✅ |
| `NSPrivacyCollectedDataTypes` | 빈 배열 | 앱 코드는 아무것도 보내지 않는다. SDK 수집분은 SDK가 자기 매니페스트에 적는다. Apple: 앱 매니페스트가 SDK 몫을 담을 필요는 없고, Xcode가 archive 때 합친다 | ✅ **비워 두는 것이 맞다** |
| UserDefaults `CA92.1` | 있음 | `UserDefaultsKeyValueStore.kt`의 `NSUserDefaults(suiteName = go_ai_coach_*)`. 앱 전용이고 앱 그룹이 아니다 | ✅ CA92.1(앱만 읽는 정보) |
| FileTimestamp `C617.1` | 있음 | `kv/IosAppFileStore.kt` 59행 `attributesOfItemAtPath`(크기). KataGo·런타임의 `stat` 계열. 대상은 앱 컨테이너와 앱 그룹 컨테이너(모델 팩) 안의 파일이다 | ✅ C617.1(컨테이너 안 파일의 시각·크기) |
| SystemBootTime | 없음 | 앱 자신의 코드는 쓰지 않는다(AdMob 전 Release 빌드 10:22에서 `mach_absolute_time` import 0건). ⚠️ GMA가 정적으로 링크되므로 최종 바이너리에는 GMA의 호출이 들어온다. 그 몫은 **GMA 매니페스트가 `35F9.1`로 선언**한다 | ✅ 앱 매니페스트에는 지금 필요 없다 |
| DiskSpace / ActiveKeyboards | 없음 | 앱 코드에 `statfs`·`NSFileSystemFreeSize`·`activeInputModes` 0건. DiskSpace는 GMA 매니페스트가 `E174.1`로 선언한다 | ✅ |
| 머리말 주석 | "iOS 빌드는 네트워크로 아무것도 보내지 않는다", "⚠️ 광고 SDK(#296)를 붙이면 수집 항목을 다시 맞춘다" | AdMob이 붙었다(`b7583921`, `43bc9ebc`). 이 파일은 그 뒤 바뀌지 않았다 | ⚠️ **낡은 문장.** 주석이라 동작에는 영향이 없다. 아래 문안으로 고치기를 제안한다(코드 변경이라 승인 필요) |

**머리말 주석 고칠 문안(제안, 제가 정한 문구)**

```
· 추적 없음 · 앱 코드가 모으는 데이터 없음 — 앱 자신의 코드는 네트워크로 아무것도 보내지 않는다(기기 식별자도 기기 밖으로 나가지 않는다).
  광고 SDK(Google Mobile Ads · UMP, #296)가 보내는 것과 그 SDK 코드가 쓰는 이유 API는 각 SDK가 자기 PrivacyInfo.xcprivacy에 선언한다 — Apple 규칙상 여기에 다시 적지 않는다.
  App Store Connect 라벨은 archive의 Privacy Report로 맞춘다(App Store 답안지 ①).
```

### SDK 매니페스트(앱 묶음에 함께 실린다)

- 두 SDK의 xcframework는 **정적 라이브러리**다(`ar archive`). SDK 코드는 앱 바이너리에 들어가고(Debug에서는 `GoCoach.debug.dylib`), `GoCoach.app/Frameworks/`의 두 framework 폴더에는 매니페스트와 작은 껍데기(약 51KB)가 실린다(Debug 시뮬레이터 빌드에서 확인).
- GMA 12.14.0: 최상위 `NSPrivacyTracking` 키 없음, 추적 도메인 없음. 수집 7종(① 표). 이유 API: SystemBootTime `35F9.1`, UserDefaults `CA92.1`, DiskSpace `E174.1`.
- UMP 3.1.0: 수집 3종(Coarse Location·Performance Data·Product Interaction, 연결 아니오, 추적 아니오, App Functionality). 이유 API: UserDefaults `CA92.1`.
- GMA·UMP는 Apple의 「매니페스트·서명 필수 SDK 목록」에 없다(https://developer.apple.com/support/third-party-SDK-requirements/). 그래도 둘 다 매니페스트를 싣고 있다.
- `AssetDownloader` 확장: `app-ios/AssetDownloader/BackgroundDownloadHandler.swift`는 빈 `StoreDownloaderExtension`이다. 이유 API를 쓰지 않으므로 별도 매니페스트가 필요 없다. 광고 SDK도 이 확장에는 링크되지 않는다(2.5.18: 광고는 앱 본체에만).

### 최종 archive에서 한 번 더(재현 명령)

```sh
A=<archive>/Products/Applications/GoCoach.app
find "$A" -name PrivacyInfo.xcprivacy   # 앱 · GoogleMobileAds · UserMessagingPlatform 셋이 나와야 한다
otool -L "$A/GoCoach" | grep -iE "AppTrackingTransparency"   # 나오면 안 된다(ATT 없음)
# Xcode Organizer › 그 archive › Generate Privacy Report → ①의 표와 대조
# 이유 API는 업로드 때 ITMS-91053 경고가 없으면 된다. nm으로는 앱 코드와 GMA 코드를 가를 수 없다.
```

---

## ③ 연령 등급 설문

⚠️ **있는 것만 답한다.** 정의는 Apple 페이지 원문을 기준으로 했다(https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions, 2026-10-27에 다시 읽음).
문항 목록은 공개 문서와 ASC API `AgeRatingDeclaration` 속성으로 재구성했다. ASC 화면의 문구와 조금 다를 수 있다.

| 단계 | 문항 | 답 | 근거 |
| --- | --- | --- | --- |
| In-App Controls | Parental Controls | **아니요** | 보호자 통제 기능 없음 |
| | Age Assurance | **아니요** | 연령 확인 없음(Declared Age Range 등 호출 0건) |
| Capabilities | Unrestricted Web Access | **아니요**(제가 정한 것) | 정의: 「Users can navigate to any webpage within the app or freely browse the web」. 앱 자신에게는 웹뷰·브라우저가 없다. 링크 다섯 종(방침, Apple EULA, 유튜브 3편, 오픈소스 라이선스 페이지)은 Safari나 YouTube 앱으로 나간다(`ui/study/StudyVideoListScreen.kt` 122행 `uriHandler.openUri`). GMA·UMP는 광고와 동의 폼을 WKWebView로 그린다. 광고를 누르면 SDK가 랜딩 페이지를 연다(`SFSafariViewController` 문자열이 있다). 이것은 SDK의 광고 동작이고 앱의 탐색 기능이 아니라고 판단했다 |
| | User-Generated Content | **아니요** | 사용자 콘텐츠를 퍼뜨리는 기능이 없다. 닉네임 줄은 꺼져 있다 |
| | Social Media | **아니요** | 피드·좋아요·공유 없음 |
| | Social Media Disabled for Users Under 13 | **아니요** | Social Media가 없다 |
| | Messaging and Chat | **아니요** | 사용자 간 통신 없음. 2인 대국은 같은 기기에서 둔다 |
| | **Advertising** | **예** | AdMob 보상형 전면(누를 때만) + 다시보기 배너(구독자 제외). 요청 설정 G(`GoogleAdsSource.swift` init의 `maxAdContentRating = .general`) |
| Mature Themes | Profanity or Crude Humor | **None** | 모든 문구가 `UiStrings`에 있다 |
| | Horror/Fear Themes | **None** | — |
| | Alcohol, Tobacco, or Drug Use or References | **None** | 운세 368개·명언 174개에 술 관련 내용이 없다(「술술」, 『술이기』 같은 낱말뿐) |
| Medical or Wellness | Medical or Treatment Information | **None** | 진단·치료·약 안내가 없다 |
| | **Health or Wellness Topics** | ⚠️ **사용자가 정할 것. 정의를 글자대로 읽으면 「예」** | 정의: 「Content that provides self-care or lifestyle recommendations. May include: calorie tracking, dieting advice, or exercise recommendations」. 운세 368개 중 약 11개가 생활 습관을 권한다: g-15(짧은 산책), g-19(따뜻한 차), g-70(좋아하는 음식 천천히), g-71(물 한 잔), g-79(기지개), g-87(간단한 요리), g-92(허리 펴고 걷기), g-141(몸부터 움직이기), s-5(큰 결정은 하룻밤 자고), s-18(일과 휴식의 균형), s-38(일찍 잠자리에)(`ui/l10n/DailyFortuneCatalog.kt`). 초안의 「돈·건강·연애를 단정하지 않게 지었다」는 이 정의에 대한 답이 되지 못한다. 아래 「선택지」 참고 |
| Sexuality or Nudity | Mature or Suggestive Themes · Sexual Content or Nudity · Graphic Sexual Content and Nudity | **None**(셋 다) | 캐릭터 다섯은 판다·돌하르방·소년·거북·부엉이 그림이다 |
| Violence | Cartoon or Fantasy · Realistic · Prolonged Graphic or Sadistic · Guns or Other Weapons | **None**(넷 다) | 바둑 대국과 학습뿐이다. 돌을 따내는 것은 게임 규칙이다 |
| Chance-Based Activities | Gambling | **아니요** | 실제 돈이나 현금으로 바꿀 수 있는 재화를 거는 일이 없다 |
| | Simulated Gambling | **None** | 정의: 「Betting or wagering without using real money…」. 뽑기·운세에는 사용자가 거는 재화가 없다. 광고를 봐도 잃는 것이 없다(`DailyDraw.kt`, `application/dailyfortune/DailyFortune.kt`) |
| | Contests | **None**(제가 정한 것) | 정의: 「Events that allow users to compete with one another for rankings, rewards, or the achievement of personal goals」. 랭킹·리더보드·사용자 간 보상 경쟁이 없다. 승급 대국은 AI 상대로 자기 급수를 찾는 기능이고, 출석 보상은 고정표다(`application/attendance/AttendanceRewardPolicy.kt`). 같은 기기 2인 대국은 순위·보상이 걸린 행사가 아니라고 봤다. 보수적으로 **Infrequent**로 답해도 등급은 같다(전 세계 4+, 한국 All, 브라질 A6 < 광고의 A12) |
| | **Loot Boxes** | ⚠️ **아니요(권장, 사용자가 확정)** | 정의: 「Virtual containers that provide players with randomized virtual items **for purchase**」. 1일 1회 뽑기는 **돈으로 살 수 없다**. 광고 1편(구독자는 광고 없이)으로 24시간에 한 번이고, 구독해도 횟수가 늘지 않는다. 확률은 카드에 상품마다 늘 적는다(합 100, `dailyDrawOdds`). 상품은 앱 안 소모품·캐릭터뿐이라 현금 가치·거래가 없다. ⚠️ 「구독자는 광고 없이 뽑는다」를 구매로 볼 여지는 사용자 판단이다 |
| Additional Information | Age Categories and Override | **Not Applicable**(제가 정한 것) | Made for Kids로 내지 않는다(승인 뒤 되돌릴 수 없다). 더 높은 등급으로 올릴 이유도 없다 |
| | Age Suitability URL | 비움(제가 정한 것) | 선택 항목 |

### Health or Wellness Topics 선택지(⚠️ 사용자가 정할 것)

| 선택 | 결과 | 비용 |
| --- | --- | --- |
| ⓐ **「예」로 답한다**(정의대로, 보수적) | 전 세계 **9+**, 호주 9+, 한국 All, 브라질은 광고 때문에 그대로 A12 | 코드 변경 없음. Play는 대상 연령을 이미 13세 이상으로 선언했다(백로그 #296 카드) |
| ⓑ 해당 운세 약 11개를 생활 권유가 없는 글로 바꾸고 「아니요」 | 전 세계 4+ | 코드 변경(4개 언어, 승인 필요). 바꾸기 전에 「아니요」로 내면 정의와 어긋난다 |
| ⓒ 그대로 두고 「아니요」 | 전 세계 4+ | 운세는 「재미로 보세요」 고지가 붙은 오락 글이라는 해석에 기댄다. 2.3.6(정직하게 답한다)의 위험을 사용자가 진다 |

### 예상 결과

| 지역 | 등급(Health 「아니요」·Loot 「아니요」) | Health 「예」면 | Loot 「예」면(추가) | 이유 |
| --- | --- | --- | --- | --- |
| 전 세계(iOS 26 이상) | **4+** | **9+** | 9+ | Advertising은 4+로 남는다. 최소 iOS가 26.1이라 옛 표기(4+/9+/12+/17+)는 볼 일이 없다 |
| 대한민국 | **All(전체)** | All | All | Advertising·Health or Wellness·Loot boxes 모두 All(Apple 표). 「게임」 카테고리면 GRAC 등급이 적용된다. GRAC 공식 등급이 다르면 App Review가 알린다 |
| 브라질 | **A12** | A12 | **A18** | ⚠️ Advertising이 브라질에서는 A12다. 피할 수 없다. Health or Wellness는 A10 |
| 호주 | 4+ | 9+ | **16+** | Loot boxes만 호주에서 16+로 바뀐다 |

- 2026-10-22부터 한국 스토어에서 Infrequent 욕설과 선정적 주제가 12+로 바뀌었다. 우리는 둘 다 None이라 영향이 없다.
- Play와의 정합: IARC 3+, 대한민국 3세 이상(`GOOGLE_PLAY_LAUNCH_PLAN.md` §0 A-3). ⚠️ Play 답안지는 도박 「아니오」의 근거로 「확률형·뽑기가 하나도 없다」를 든다. 그 근거는 **1.8.0(#283)부터 낡았다**(답 자체는 그대로 맞다). Apple에는 뽑기를 사실대로 적었다. Play IARC를 다시 열지는 별도 판단이다(함정 58: 새 설문지는 이전 답을 불러오지 않는다).
- 2.5.18: 광고는 앱 등급에 맞아야 한다. G 등급 요청 설정은 4+·9+ 어느 쪽과도 맞는다.

---

## ④ 수출 규정(암호화)

| 질문 | 답 | 근거 |
| --- | --- | --- |
| `ITSAppUsesNonExemptEncryption` | **NO**(이미 설정됨) | `app-ios/Info.plist` 원본 파일에는 **키가 없다**. 대신 빌드 설정 `INFOPLIST_KEY_ITSAppUsesNonExemptEncryption = NO`가 앱 타깃 Debug·Release에 있다(`app-ios/GoCoach.xcodeproj/project.pbxproj` 357·406행). `GENERATE_INFOPLIST_FILE = YES`가 두 값을 합친다. 빌드 결과 `Info.plist`에 `ITSAppUsesNonExemptEncryption => false`가 있다(Release-iphoneos, Debug-iphonesimulator 확인) |
| ASC가 업로드마다 암호화 설문을 띄우는가 | **안 띄운다** | 키가 있으면 건너뛴다. TestFlight의 「Missing Compliance」도 생기지 않는다(https://developer.apple.com/help/app-store-connect/test-a-beta-version/provide-export-compliance-information-for-beta-builds) |
| 프랑스 신고·CCATS·암호화 문서 업로드 | **필요 없음**(제가 정한 판단) | 아래 표. 암호는 모두 Apple OS가 제공하는 것이다 |

**앱에 든 암호 관련 코드**

| 코드 | 무엇 | 판정 |
| --- | --- | --- |
| 앱 자체(Swift·Kotlin) | 암호화 코드 없음. 네트워크 코드도 없다. UUID 난수는 암호화가 아니다 | 해당 없음 |
| KataGo 1.16.4 | `sha2.cpp`(SHA-2 **해시**: 판 해시·모델 식별). `-lz`(zlib **압축 해제**: `.bin.gz` 모델, `project.pbxproj` 375·424행). 분산 학습용 HTTP·TLS(`BUILD_DISTRIBUTED`)는 켜지 않았다(`scripts/build-katago-ios.sh` 83~96행의 cmake 옵션에 없다. KataGo 기본값은 꺼짐) | 암호화가 아니다 |
| Google Mobile Ads · UMP | ⚠️ HTTPS 통신에 더해 **GMA가 Apple OS 암호 API를 직접 부른다**: CommonCrypto `CCCrypt`·`CC_SHA256`·`CC_MD5`, Security `SecKeyCreateEncryptedData`·`SecKeyCreateWithData`·`SecKeyVerifySignature`·`SecRandomCopyBytes`(정적 라이브러리 `nm -u`). 앱 바이너리가 `Security.framework`를 링크한다 | **OS가 제공하는 암호화**다. Apple: OS에 내장된 암호화(URLSession HTTPS 등)는 보통 문서 제출이 면제다(https://developer.apple.com/documentation/security/complying-with-encryption-export-regulations). AdMob을 쓰는 앱의 일반적 판정도 「NO」다 |
| StoreKit · Background Assets | Apple OS 프레임워크 | OS 암호화 |

- ⚠️ 초안은 「두 SDK 바이너리에 CommonCrypto·Security import 0건」이라고 적었다. 그것은 Debug 앱 묶음 안의 **껍데기 framework(약 51KB)**를 본 결과였다. 실제 SDK 코드(xcframework 정적 라이브러리)는 위 API를 쓴다. 결론(NO)은 그대로지만 근거는 「OS 암호만 쓴다」로 바뀐다.
- 미국 연말 자체 분류 보고서(self-classification report)는 이 앱에 해당하지 않는다고 판단했다(OS 암호만 호출). 이 판단은 공식 문서로 확인하지 못했다(https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance 는 이 보고서를 다루지 않는다). ⚠️ 사용자가 확인한다.
- 받기 확장 `AssetDownloader`에는 이 키가 없다. Apple은 앱 본체의 값을 본다.

---

## ⑤ 콘텐츠 권한(Content Rights)

| 질문 | 답 | 근거 |
| --- | --- | --- |
| Does your app contain, show, or access third-party content? | **Yes**(`USES_THIRD_PARTY_CONTENT`) + 「필요한 권리를 갖추었다」에 체크(제가 정한 것) | 아래 표. 제3자 기보·음악·엔진·모델·광고가 실려 있다. 「No」는 사실이 아니다 |

| 콘텐츠 | 앱 안 위치 | 출처와 권리 근거(저장소) | 남은 위험 · 할 일 |
| --- | --- | --- | --- |
| **명국 기보 33판**(에도 고전 5 · 20세기 2 · 응씨배 9 · LG배 9 · 삼성화재배 3 · 알파고 대 이세돌 5) | 대국 페이지 › 명국 기보 | 전부 CWI 「Database of Go Games」(*"The games here are in the public domain."*). 묶음마다 권리의 결이 다르다(`app-android/src/commonMain/kotlin/com/worksoc/goaicoach/ui/gamerecord/GameRecordCatalog.kt` 22~29행). ✅ 사용자 최종 판단(2026-10-19): 33판 모두 싣는다. 형세 `.eval`은 우리 엔진이 만든 것이다 | ⚠️ 한국기원은 「기보는 저작물」이라고 주장해 왔다. 2024 서울고법은 부정경쟁 판단에서 기보를 공공영역으로 봤지만 저작권 판단은 아니다. 권리 증빙을 요구받으면 CWI 고지와 이 판단을 낸다(5.2) |
| **고전 사활 12문제**(『기경중묘』 5 · 『발양론』 7) | 대국 페이지 메뉴 › 고전 사활 | 원전(1812·1713)은 공유 저작물. 정답은 1898년판·1914년판(일본 국립국회도서관 디지털, 공유 저작물). 국면은 tsumego.tasuki.org 전사본(`shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/content/LifeAndDeathProblem.kt` 41~52행). 앱에 「그림: tsumego.tasuki.org」를 적는다(`ui/l10n/UiStringsStudyLifeAndDeath.kt` 119~128행) | ⚠️ 전사본에는 라이선스 표기가 없다(`© Vít 'tasuki' Brunner`). 그래서 저장소에는 그 파일을 싣지 않았다(`engine-lab/experiments/e12_classic_life_and_death/README.md` 19~20행). 원하면 작성자에게 허락을 받아 둔다(선택) |
| **배경음악 4곡**(Kevin MacLeod: Gymnopedie No 1, Eastern Thought, Senbazuru, Shenyang) | 앱 배경음악 | CC BY 4.0. 오픈소스 라이선스 화면에 출처를 표시한다(`ios-licenses/build/ios-licenses/aboutlibraries-ios.json`의 `bundled.music:*`, `app-android/config/aboutlibraries/libraries/bgm-*.json`) | ⚠️ CC BY는 변경 사실을 밝히게 한다. 고지는 "re-encoded to Ogg Vorbis 128 kbps"인데, iOS는 AAC 96 kbps(m4a)로 다시 바꿔 싣는다(`scripts/ios-bundle-resources.sh` 50행). 문구를 플랫폼 중립으로 고치기를 제안한다(승인 필요) |
| **KataGo 1.16.4 엔진** + **신경망 둘**(kata1-b18c384nbt, b18c384nbt-humanv0) | 기기 안 AI. 모델은 Apple 호스팅 팩 | KataGo License(MIT 계열), KataGo Neural Network License(허용형, 고지 포함 조건)(`app-android/config/aboutlibraries/licenses/katago-code.json`, `katago-network.json`). 오픈소스 목록에 실려 있다 | 고지는 앱 안 목록에 있다. 팩 안에 LICENSE 파일을 함께 넣을지는 선택 |
| Eigen 3.4.0(MPL-2.0), sha2(BSD-3), ghc-filesystem·nlohmann-json·tclap(MIT), Kotlin·Compose 등(Apache-2.0) | 엔진·앱 코드 | iOS 오픈소스 목록 60개(`aboutlibraries-ios.json`). Eigen 고지에 소스 주소가 있다 | — |
| **명언 174개**(바둑 고전·속담·기사·동양 고전·서양 인물) | 대국 페이지 한 줄, 실험실 명언록 | 출처를 확인한 것만 실었다. 화면에는 명언과 말한 이만 보인다. 출처는 `DailyQuote.source`에 기록했다(`ui/l10n/DailyQuoteCatalog.kt` 9~17행) | ⚠️ 번역 일부는 「널리 쓰이는 번역」이다. 짧은 인용이라 위험은 낮다고 본다(사용자 판단) |
| **유튜브 강좌 3편**(바둑에듀 채널) | 바둑 배우기 › 유튜브 강좌 | 링크만 둔다. 앱 밖에서 연다(`ui/study/StudyVideoListScreen.kt` 122행). 앱에 영상을 넣지 않는다 | — |
| 제3자 광고 소재 | 보상형 전면·다시보기 배너 | Google AdMob, 최대 등급 G | — |
| 캐릭터 그림 5장 · 기동 그림 | 홈·대국·기동 화면 | 기동 그림은 사용자가 Gemini로 만들었다(`work/splash/README.md` 33행). **캐릭터 그림은 무엇으로 만들었는지 저장소에 기록이 없다**(`GOOGLE_PLAY_LAUNCH_PLAN.md` A-6 칸) | ⚠️ **사용자 확인**: 만든 도구와 그 약관상 상업 이용이 되는지 |
| 상표가 든 낱말: AlphaGo(DeepMind), LG배·삼성화재배·응씨배 | 기보 묶음 이름에만 | 역사적 대국을 가리키는 서술적 사용 | ⚠️ 이름·부제·키워드·스크린샷 문구에는 넣지 않는다(2.3.7 무단 상표, #298 등재 글) |

- 저작권 칸(버전 필드, 로컬라이즈 안 함): **`2026 <판매자 본인 이름>`**(© 기호는 ASC가 붙인다). 근거는 백로그의 U-85 기록(개인 계정, 판매자 이름은 본인 이름)이다.

---

## ⑥ 심사 메모 초안(App Review Information › Notes, 영어)

- **Sign-in required**: 체크하지 않는다(로그인이 없다).
- **Contact**: 이름, 이메일, 전화번호는 `+82 10-…` 국제 형식으로(2026-08-19부터 숫자만은 안 된다). ⚠️ 사용자가 넣는다.
- 아래 본문은 약 3,170바이트다(한도 4,000바이트). 7번 줄을 더하면 약 3,460바이트다. 화면 이름은 영어 UI 문자열 그대로다(`ui/l10n/UiStringsEn.kt`, `UiStringsPremiumSubscription.kt`, `UiStringsHomePages.kt`, `UiStringsDailyDraw.kt`, `UiStringsDailyFortune.kt`, `UiStringsMyInfoAndSettings.kt`, `UiStringsDownloadGuide.kt`, `UiStrings.kt`의 `myPageTitle`).
- 길 B의 근거: 무료 3회 → 1회권(자동 사용, 팝업 없음) → 업셀 순서(`ui/play/GamePlaySection.kt` 1037~1108행). 출석 1일차는 형세 1회권, 2일차는 추천 1회권을 준다(`AttendanceRewardPolicy.kt` 128~129행). 그래서 새 설치 1일차에는 「Top moves」나 「Undo」의 4번째 탭이 업셀을 연다. 기본 상대(흑 사람·백 AI, 빠른초급 1단계)는 캐릭터 판다다(`BotCharacterPerk.kt` `matchOpponentCharacter`, `PlayLevel.kt` 175행).
- 엔진 준비 화면의 근거: `ui/setup/EngineSetupGate.kt`, `ui/setup/EngineDownloadStatusBanner.kt`(진행은 **백분율**로 보이고 MB는 보이지 않는다. 「Retry」는 실패했을 때만 보인다).

```
Pocket Baduk Coach (Go AI Coach) - notes for App Review

1. No account
There is no sign-up or login. Every feature works without an account, so no demo account is needed.

2. On-device AI and the asset pack in this submission
- The Go AI is KataGo. It runs on the device CPU inside the app. The app itself does not call any developer server.
- The AI needs two neural-network files (about 197 MB in total). They are the Apple-hosted asset pack "katago-models" (prefetch on install and update), submitted with this version.
- If the pack is not on the device yet, the app opens a full-screen "Board Controls Guide" with a progress card ("Downloading Go AI engine", in percent). If the download fails, the card shows "Retry". "Later" goes to Home. After the download, games and analysis work offline.

3. Subscription: "Premium" (premium_basic_monthly, 1 month, auto-renewable)
- Path A: Home > bottom tab "My info" > "Premium plan" card > "Subscribe".
- Path B: Home > "Play" > "Normal Match" > "Start Match Now" (default opponent: the character "Panda the Pupil"). "Eval", "Top moves" and "Undo" are free 3 times each per game. The day-1 check-in gives Eval tickets, which are used automatically after the free uses, so tap "Top moves" (or "Undo") a 4th time. "Activate Premium" opens with "Watch an ad for 1 hour" and "Subscribe to Premium".
- Both dialogs show the monthly price, "Renews automatically until cancelled.", "Cancel any time in App Store subscriptions.", "Restore Purchases", "Terms of Use (EULA)" (Apple standard EULA) and "Privacy Policy". A subscriber sees "Manage or cancel" on the card. It opens App Store subscription management.
- Premium unlocks all characters and unlimited coaching, with no ads. Free users reach the same features through 3 free uses per game, check-in rewards and rewarded ads.

4. Ads (Google Mobile Ads)
- Rewarded interstitial ads play only after the user taps one of these: "Watch an ad for 1 hour" (1 hour of Premium), a locked character that unlocks with ad pieces in "My info" > "My characters" (one piece per ad), "Watch ad to change luck" ("Today's fortune") and "Watch an ad to draw" ("Daily draw"). The last two are in the "Lab" tab.
- The only banner is on the game replay screen ("Play" > "Game History" > a game). Subscribers see no ads. Max ad content rating: G.
- The app does not show the App Tracking Transparency prompt, so the IDFA is not available to the app or the SDK. Every ad request is sent as non-personalized (npa=1). Where the law requires consent (EEA, UK, Switzerland), the Google UMP consent form appears before an ad, and Settings shows "Ad privacy settings" when Google requires it. The app checks consent status at each launch.

5. Daily draw ("Lab" tab)
One draw per 24 hours, after one rewarded ad (no ad for subscribers; a subscription does not add draws). It cannot be bought with money. The card always shows the odds of each prize. Prizes are in-app items only, with no cash value and no trading.

6. Data
Game records, settings and progress are stored only on the iPhone (and in the user's own iPhone backup). The Privacy Policy opens from Settings and from both purchase dialogs.
```

⚠️ **숨은 개발자 메뉴(2.5.19 · 3.1.1): 사용자가 정할 것**

- 사실: 출시 빌드에서도 설정의 버전 글자를 10번 누르면 확인 창이 뜨고, 확인하면 1단 개발자 메뉴가 열린다(`ui/settings/SettingsScreen.kt` 76·287~300행, `ui/settings/DeveloperTestSection.kt`). 켤 때 기존 데이터가 지워지고, 켠 동안 3시간마다 새로 설치한 상태로 돌아간다(확인 창 문구).
- 1단에 실린 것: 빌드 정보, **「Grant one ticket」(누를 때마다 형세 1회권 1장 + 추천 1회권 1장, 횟수 제한 없음)**, 진단 로그 보기, AI 착수 지연, 엔진 성능 측정, 기동 그림 후보, 끄기(데이터 초기화).
- 2단(광고 본 것으로 프리미엄 1시간 등)은 `isDebugBuild && …`로 막혀 있다. iOS `isDebugBuild = Platform.isDebugBinary`이고, Xcode Release 구성은 `embedAndSignAppleFrameworkForXcode`로 release Kotlin 프레임워크를 묶으므로 닿지 않는다(`app-android/src/iosMain/kotlin/com/worksoc/goaicoach/platform/AppPlatformActualsIos.kt` 15행, `project.pbxproj` 242행).
- ⚠️ 초안의 7번 줄 "It does not unlock paid features."는 **사실이 아니다**. 1회권 버튼을 반복해 누르면 구독이 파는 기능(형세 보기·추천 수)을 돈 없이 계속 쓸 수 있다.
- **권장(제가 정한 것): 출시 빌드에서 1단 메뉴를 막거나, 적어도 「Grant one ticket」을 2단(debug 전용)으로 옮긴다**(코드 변경, 승인 필요). 막지 않으면 아래 줄을 메모에 더한다(사실대로 쓴 문안).

```
7. Developer menu: tapping the version text in Settings 10 times opens a confirmation, then a QA menu: build info, diagnostic log, AI move delay, engine benchmark, splash previews, and a button that adds one Eval and one Top-moves ticket per tap. Turning the menu on or off erases local data.
```

⚠️ **4.2.3(ii)(첫 실행에 받을 리소스는 크기를 알리고 먼저 묻는다)**: 앱은 약 197MB라는 크기를 어디에도 보여 주지 않는다. 모델 팩은 시스템이 설치·업데이트 때 받는 prefetch이고(`scripts/ios-package-model-pack.sh` 28행), 제품 페이지의 「Up to」 크기에는 essential 팩만 들어간다. 그래서 위 메모 2번에 크기를 적었다. 엔진 준비 화면이나 설명에도 크기를 적을지는 사용자가 정한다(코드 변경이면 승인 필요).

---

## ⑦ 가격 · 배포 국가(**제안**, 사용자가 정한다 — 이 절 전체가 제가 정한 것)

| 항목 | 제안 | 근거 · 주의 |
| --- | --- | --- |
| 앱 가격 | **무료** | 안드로이드와 같다 |
| 구독 | 그룹 「Premium」 하나, 상품 `premium_basic_monthly` 하나, **1개월**, 기준 국가 **대한민국 ₩3,900**. 다른 스토어프런트는 Apple 자동 환산 | 상품 ID는 `app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ios/IosStore.kt` `IosPremiumProductId`, 시험 파일은 `app-ios/StoreKit/GoCoach.storekit`. 가격·상품 ID는 사용자 결정이다. ⚠️ ₩3,900이 Apple 가격 포인트에 실제로 있는지는 ASC 가격 화면에서 확인한다. 안드로이드의 다른 나라 가격은 저장소에서 확인하지 못했다. 기간은 심사 제출 뒤 바꿀 수 없다 |
| 도입 혜택(무료 체험 등) | **없음** | 시험 파일과 같다. 넣으면 가입 화면에 기간과 그 뒤 청구 가격을 밝혀야 한다 |
| 가족 공유 | **끔** | 시험 파일과 같다. ⚠️ 한 번 켜면 끌 수 없다고 알고 있다. ASC 화면에서 확인한다 |
| 배포 국가 | 안드로이드 프로덕션 177개국(`GOOGLE_PLAY_LAUNCH_PLAN.md`)에 맞춰 **「모든 국가 및 지역」에서 시작하고 중국 본토만 뺀다** | Apple 스토어프런트는 175개다. ⚠️ 중국 본토는 ICP 신고 번호가 필요하고, 게임 카테고리면 게임 판호도 필요한 것으로 안다(확인 필요). ⚠️ EU를 넣으면 DSA trader 상태를 신고해야 한다. trader면 주소·전화·이메일이 EU 제품 페이지에 공개된다 |
| Mac(Apple silicon) · Apple Vision Pro | **끔** | 따로 끄지 않으면 자동으로 나간다(https://developer.apple.com/help/app-store-connect/manage-your-apps-availability/manage-availability-of-iphone-and-ipad-apps-on-macs-with-apple-silicon). 아이폰·세로 전용이고 두 기기에서는 시험하지 않았다 |
| 출시 방식 | **수동 출시** | 승인 뒤 AdMob 「앱 연결」, rezen.dev App Store 링크, 방침 배포를 확인하고 내보낸다. #298 「착수 때 물을 것」 항목이다 |
| App Store Small Business Program | **가입** | 개인 개발자, 연 100만 달러 미만이면 수수료 15%(https://developer.apple.com/app-store/small-business-program/) |
| 카테고리(참고) | 1차 **Games › Board**(필요하면 Strategy), 2차 **Education** | 빌드의 `LSApplicationCategoryType = public.app-category.board-games`와 맞는다. Games면 한국 지역 등급(③)과 Time Allowance가 적용된다 |

---

## ⑧ 개인정보처리방침: 제출 **전에** rezen.dev에서 고칠 것

- 원문: 형제 저장소 `/Users/ryan9kim/worksoc/rezen.dev`의 `web/site/src/components/legal/BadukAiPrivacy.astro`(마지막 변경 `7ce1cbb`, 2026-10-20). 시행일자는 「2026년 9월 22일」이다. 2026-10-27에 읽은 배포본(https://rezen.dev/apps/baduk-ai/privacy/)과 내용이 같다.
- ⚠️ **이 컴포넌트 하나가 세 곳을 그린다**: 정식 위치 `/apps/baduk-ai/privacy/`, 별칭 `/go-ai-coach/privacy/`(바둑 앱 1.6.0 이하 바이너리용), baduk-ai 레거시 호스트의 `/privacy`(`web/site/worker.js`). 그래서 고칠 문안은 **안드로이드와 iPhone을 함께** 덮어야 한다. 안드로이드 문장을 지우면 Play 신고(A-4)와 어긋난다. **더하기만** 한다.
- ⚠️ 본문 지문 계약: `contracts/policy-text.json`의 sha256을 `scripts/check-dist.mjs`(425~447행)가 정식 위치와 별칭 두 경로에서 검사한다. 문구를 바꾸면 검사가 실패하고 새 값을 출력한다. 그 값으로 두 줄을 함께 갱신한다. 계약 주석의 「앱과 Play 신고를 먼저 맞춘 뒤」도 지킨다.
- 백로그 #298 「함정 60」: **방침이 앱보다 늦으면 그 자체로 위반이다.** 필수 항목 ①~⑤와 ⑧을 배포한 뒤 제출한다.
- 아래 「고칠 문안」은 모두 초안이다(제가 정한 문구). 「무게」 칸도 이 문서의 판단이다.

| # | 빈 곳(iOS 기준) | 지금 방침 | 고칠 문안(초안) | 무게 |
| --- | --- | --- | --- | --- |
| ① | **결제 절이 Google Play만 말한다** | 5절 「결제는 Google Play가 처리…해지는 Google Play 스토어의 구독 관리에서…환불은 Google Play의 정책」 | 「결제는 Google Play(안드로이드) 또는 Apple App Store(iPhone)가 처리하며, 앱은 카드 번호를 비롯한 결제 수단 정보를 수집하거나 저장하지 않습니다. 앱이 확인하는 것은 '구독이 현재 유효한가' 하나이며, 그 결과는 이용자의 기기에만 저장되고 서비스 제공자에게 전송되지 않습니다. 구독은 해지하기 전까지 자동으로 갱신되며, 해지는 Google Play 스토어의 구독 관리 또는 App Store의 구독 관리(앱의 「구독 관리·해지」)에서 언제든 하실 수 있습니다. 환불은 각 스토어(Google Play, Apple)의 정책을 따릅니다.」 | **필수** |
| ② | **백업 절이 안드로이드 자동 백업만 말한다**(앱 안 iOS 안내와 어긋난다) | 3절·6절 「안드로이드 자동 백업…Google 계정 백업」 | 3절에 더한다: 「iPhone에서는 앱 설정값과 이용 기록(출석·모은 캐릭터·1회권·대국 기록 등)이 이용자의 iPhone 백업(iCloud 백업 또는 컴퓨터 백업)에 함께 담깁니다. 이 백업은 Apple이 제공·관리하며 서비스 제공자가 열람하거나 수집하지 않습니다. 새 iPhone으로 옮기거나 그 백업에서 복원하면 기록이 돌아오고, 앱만 삭제했다가 다시 설치하면 돌아오지 않습니다.」 6절에도 같은 취지로 한 줄. 앱 문구 근거: `ui/l10n/UiStringsUserProfile.kt` `iosLocalStorageNoticeFor` | **필수** |
| ③ | **광고 절이 안드로이드 용어뿐이다** | 1절 「기기 및 계정 식별자: 광고 ID(Advertising ID), 앱 세트 ID 등」, 4절도 같다. 성능 정보(실행 시간·응답 지연·전력 사용량)와 앱 이용 기록(실행·탭·동영상 시청)은 **이미 적혀 있다** | 1절 식별자 줄을 둘로 나눈다: 「(안드로이드) 광고 ID, 앱 세트 ID 등 / (iPhone) 이 앱은 앱 추적 투명성(ATT) 허용을 요청하지 않으므로 광고 식별자(IDFA)를 앱과 Google 모두 쓸 수 없습니다. 다만 Google은 광고 게재·측정·부정행위 방지에 앱 또는 개발자 범위의 기기 식별자를 쓸 수 있습니다.」 1절에 **없는 두 항목**을 더한다: 「충돌 기록」, 「사용자가 본 광고 정보」. 비맞춤 광고는 플랫폼을 나눠 쓴다: 「iPhone 앱은 Google에 비맞춤 광고만 요청합니다. 비맞춤 광고에서도 Google은 빈도 제한·집계 보고·부정행위 방지에 식별자를 쓸 수 있습니다.」(근거: `GoogleAdsSource.swift` `adRequest()`, https://support.google.com/admob/answer/7676680). ⚠️ 안드로이드는 `npa`를 붙이지 않으므로 「앱은 비맞춤 광고만」이라고 플랫폼 없이 쓰지 않는다. UMP를 더한다: 「앱을 열 때마다 Google 사용자 메시지 플랫폼(UMP)이 광고 동의 상태를 확인하며, 이때 동의 창 표시에 필요한 정보(대략적인 위치, 성능, 상호작용)가 Google에 전송됩니다. 동의 상태는 기기에 저장됩니다.」 근거: ①의 매니페스트, https://developers.google.com/admob/ios/privacy/data-disclosure | **필수** |
| ④ | **동의를 바꾸거나 철회하는 방법이 없다**(Android에도 없다) | — | 6절에 더한다: 「유럽경제지역·영국·스위스에서는 앱 설정의 「광고 개인정보 설정」에서 광고 동의를 바꾸거나 철회할 수 있습니다(동의가 필요한 지역에서만 보입니다).」 근거: 실제 화면 문구 `settingsAdPrivacyOptionsLabel = "광고 개인정보 설정"`(`ui/l10n/UiStringsKo.kt` 226행), `ui/monetization/AdPrivacyOptionsRow.kt`, `ui/settings/SettingsScreen.kt` 325행. 5.1.1(i)·(ii)는 동의 철회 방법을 요구한다 | **필수** |
| ⑤ | **6절의 iOS 경로가 이 앱의 광고와 맞지 않는다** | 「iOS: 설정 > 개인정보 보호 > 광고」 | 그 메뉴는 Apple 자신의 광고 설정이다. 이 앱은 ATT를 묻지 않는다. 바꿀 문안: 「iPhone: 이 앱은 추적 허용을 요청하지 않습니다. 기기 설정의 '개인정보 보호 및 보안 > 추적'에서 모든 앱의 추적 요청을 막을 수 있습니다.」 ⚠️ iOS 26의 실제 메뉴 이름은 기기에서 확인한다 | **필수**(틀린 안내) |
| ⑥ | 제3자 보호 수준 확인 문장이 없다(5.1.1(i)) | 4절은 Google AdMob만 적는다. 1절에 「Google의 파트너 사이트 정책」 링크가 있다 | 4절에 UMP(Google LLC)를 더한다. 그리고 「Google은 Google 개인정보처리방침과 광고 약관에 따라 이 방침과 같은 수준 이상으로 정보를 보호합니다」 취지의 한 줄을 더한다. ⚠️ 근거가 되는 Google 약관(AdMob 이용약관·데이터 보호 조항)은 사용자가 확인하고 쓴다 | 권장 |
| ⑦ | 설치 뒤 엔진 모델 받기가 없다 | 머리말 「AI 연산과 데이터 저장을 모두 기기 내부에서」 | 「앱은 설치 직후 AI 엔진 모델(약 197MB)을 한 번 내려받습니다(iPhone은 Apple이 호스팅하는 Background Assets, 안드로이드는 Google Play). 이때 서비스 제공자에게 전송되는 개인정보는 없으며, 내려받은 뒤에는 인터넷 없이 AI가 동작합니다.」 | 권장 |
| ⑧ | iPhone·iCloud·StoreKit·ATT·UMP라는 말이 한 번도 없다(머리말에 「애플 앱스토어」 개발자 정책 준수 한 마디뿐). 시행일자가 iOS 이전이다 | 「시행일자: 2026년 9월 22일」 | 위 문안으로 채워지면 해결된다. 시행일자를 배포일로 바꾼다 | 필수(①~⑤와 함께) |
| ⑨ | 아동·연령 문단이 없다 | — | 예: 「이 앱은 만 14세 미만 아동을 별도로 식별하거나 그 개인정보를 따로 수집하지 않습니다.」 ⚠️ 개인정보보호법상 필요한지는 사용자가 판단한다 | 선택 |
| ⑩ | 「진단 로그 복사」가 없다 | — | 「앱의 '진단 로그 복사'를 누르면 기기 모델·OS 버전·앱 빌드 정보가 든 텍스트가 클립보드에 복사됩니다. 이 정보는 이용자가 직접 붙여 넣어 보내지 않는 한 전송되지 않습니다.」 | 선택 |

**함께 볼 것: 지원 페이지 `https://rezen.dev/apps/baduk-ai/support/`**(ASC 필수 필드 Support URL)
- 원문 `web/site/src/pages/apps/baduk-ai/support/index.astro`의 FAQ가 안드로이드 전용이다(「Google 계정 백업이 켜져 있으면」, 「같은 Google Play 계정으로 다시 설치하면」). 「「학습 하기」에 규칙 6단원」 줄도 지금 홈 구성(내 정보 · 대국 · 실험실)과 맞지 않는다.
- iPhone 답(iPhone 백업, App Store 구매 복원)을 더하기를 권한다(제가 정한 것). 1.5는 실제 연락 수단을 요구한다. 문의 이메일이 페이지에 보이는지 배포본에서 확인한다.
- 앱 안에는 지원 URL·이메일이 없다. 방침 링크 안의 이메일뿐이다(`mailto` 0건). 1.5의 「앱과 지원 URL 모두에 쉬운 연락 수단」을 엄격히 읽으면 설정에 「문의하기」 링크가 필요할 수 있다(코드 변경, 사용자 판단).
- 배포: 이 기계의 Node가 Astro 요구판(≥22.12)보다 낮았던 기록이 있다(Play 답안지 A-2). 빌드·배포 환경을 먼저 확인한다.

---

## ⑨ 사용자만 할 수 있는 일

**App Store Connect · Apple Developer**
- [ ] 개인 계정 가입(U-85). Account Holder가 **Paid Applications Agreement**에 서명하고 세금·은행 정보를 넣는다. 서명 전에는 샌드박스 구매도 안 된다.
- [ ] Business › Agreements › Compliance › **Korean Law**: 전자상거래법 신원 정보(개인은 이메일 등). EU를 넣으면 **DSA trader** 상태도 신고한다.
- [ ] 앱 레코드: 이름 「포켓 바둑 코치」, 기본 언어 한국어, 번들 ID, SKU `pocket-baduk-coach-ios`(#291 제안값).
- [ ] **App Privacy**(①): 7개 유형, 성능 데이터 연결 여부, Device ID 추적 답(⚠️ 길 ⓐ/ⓑ 결정), 방침 URL, Publish.
- [ ] **연령 등급**(③): Health or Wellness Topics(ⓐ/ⓑ/ⓒ) 결정, Loot Boxes 「아니요」 확정, Contests None/Infrequent 확정.
- [ ] **콘텐츠 권한**(⑤): Yes와 권리 체크. 캐릭터 그림 출처 확인.
- [ ] 저작권 `2026 <본인 이름>`, 카테고리, 지원 URL(필수), 마케팅 URL(선택).
- [ ] **가격·배포**(⑦): 무료, 국가, Mac·Vision Pro 끄기, 출시 방식, Small Business Program.
- [ ] **구독**: 그룹 「Premium」(그룹 표시 이름 로컬라이즈) → 상품 `premium_basic_monthly`(참조 이름, 1개월, ₩3,900, 표시 이름·설명 최소 한 언어, **심사용 스크린샷**, 심사 메모) → 첫 버전 제출에 함께 넣는다(Add for Review).
- [ ] **모델 팩** `katago-models` 업로드(Transporter, `xcrun altool --upload-asset-pack` 등) → 처리 메일 → 첫 버전 제출에 **같이** 넣는다(Distribution › Asset Packs › Add for Review). 베타 패키징 도구로 만든 팩은 심사에 넣을 수 없다. 팩을 Archive하면 ID를 다시 쓸 수 없다.
- [ ] 빌드: 두 타깃(GoCoach, AssetDownloader)의 서명 팀, 실제 AdMob ID를 `app-ios/Config/Ads.local.xcconfig`에 넣고 archive(시험 ID면 `scripts/ios-bundle-resources.sh`가 멈춘다). Generate Privacy Report로 ①을 대조한다.
- [ ] ⚠️ 업로드에 쓰는 Xcode가 정식판인지 확인한다. 이 기계는 Xcode 27.1(27A9275)이고 빌드의 `DTSDKName`은 `iphoneos27.1`이다. 참고로 2027년 4월부터 iOS 27.1 SDK 이상으로 빌드한 앱은 iPhone Duo 스크린샷이 필요하다. 지금 제출에는 해당 없다.
- [ ] **App Review Information**: 연락처(+82 형식), Notes(⑥), 로그인 불필요.
- [ ] **Sandbox 계정**과 TestFlight 내부 그룹(#297). TestFlight에서 팩을 받으려면 베타 권한이 있는 실제 Apple Account로 로그인해야 한다. Sandbox 계정으로는 안 된다.

**AdMob · UMP(Google 콘솔)**
- [ ] AdMob에 iOS 앱(`com.zenit9hub.ai.baduk`)을 만든다. 보상형 전면·배너 단위를 만들고 세 ID를 `Ads.local.xcconfig`에 넣는다.
- [ ] Privacy & messaging › **유럽 규정 메시지를 iOS 앱에도 게시**한다. 게시하지 않으면 EEA 동의 창이 뜨지 않는다(Play의 A-10과 같은 관문). IDFA 설명 메시지는 만들지 않는다(ATT 없음).
- [ ] App Store 게시 뒤 AdMob **「앱 연결」**. ⚠️ app-ads.txt를 확인하는 개발자 웹사이트는 App Store 제품 페이지의 URL로 정해진다. rezen.dev 도메인을 가리키게 둔다(어느 URL 칸을 보는지는 AdMob 화면에서 확인).
- [ ] (선택) AdMob 차단 설정에서 민감 카테고리(도박 등)를 막는다. 등급과 G 요청 설정을 보강한다.

**rezen.dev(형제 저장소)**
- [ ] ⑧ ①~⑤·⑧(+⑥·⑦ 권장)을 고친다 → 지문 계약 두 줄 갱신 → 빌드·배포 → `/apps/baduk-ai/privacy/`, `/go-ai-coach/privacy/`, 레거시 호스트 `/privacy`가 모두 200이고 새 본문인지 확인한다.
- [ ] 지원 페이지 iPhone FAQ. 출시 뒤 제품 페이지에 App Store 링크.

**코드(스레드가 할 수 있지만 사용자 승인이 먼저)**
- [ ] 출시 빌드에서 1단 개발자 메뉴 막기, 또는 「Grant one ticket」을 debug 전용으로 옮기기(⑥ ⚠️).
- [ ] (Health or Wellness ⓑ를 고르면) 운세 약 11개를 생활 권유 없는 글로 바꾸기(③).
- [ ] (선택) PPT `.disabled`·first-party ID 끄기(① 길 ⓐ의 보강).
- [ ] `PrivacyInfo.xcprivacy` 머리말 주석 고치기(②).
- [ ] 음악 CC BY 고지의 「Ogg Vorbis」 문구를 플랫폼 중립으로(⑤).
- [ ] (선택) 엔진 준비 화면에 내려받을 크기(약 197MB) 표시(⑥ 4.2.3(ii)).
- [ ] (선택) 설정에 「문의하기」(1.5).

**#297 실기에서 확인할 것(이 답안지와 맞닿는 것)**
- 새 설치에서 보상형 광고를 보기 전에 다시보기 배너가 뜨는가. `43bc9ebc`가 실행 때 동의 갱신을 더해 고쳤다. 커밋 메시지에 시뮬레이터 확인 기록이 있다. 실기와 실제 광고 ID로 다시 본다.
- EEA 사용자가 「Ad privacy settings」 줄을 앱 시작 직후에 보는가(⑧ ④의 철회 경로 서술과 맞아야 한다).
- 보상형 전면 광고 앞의 선택: AdMob 정책은 거절할 수 있는 소개 화면을 요구한다(https://support.google.com/admob/answer/7313578). 코드상 네 진입점은 광고를 말하는 단추(「Watch an ad for 1 hour」, 「Watch ad to change luck」, 「Watch an ad to draw」)나 잠긴 캐릭터 카드를 누르면 **곧바로** 광고를 띄운다(조각 카드: `ui/monetization/BotCharacterUiState.kt` 347행). 이 단추가 소개 화면 요건을 채우는지 확인한다.
- 2.5.18 「부적절한 광고 신고」: 실제 광고에 AdChoices 등 신고 수단이 보이는가.
- 광고를 눌렀을 때 랜딩 페이지가 어디서 열리는가(③ Unrestricted Web Access 판단의 확인).

---

## 근거를 어떻게 모았나(재현용)

- 저장소 상태: `main` `43bc9ebc`(검증을 시작할 때는 `b7583921`이었다. 검증 중에 `GoogleAdsSource.swift`·`GoCoachApp.swift`를 고친 커밋이 들어왔다).
- 앱 자체 네트워크: `commonMain`·`iosMain` 소스 세트 전부(app-android, shared, engine-android, core/*)와 `app-ios/GoCoach`·`app-ios/AssetDownloader`에서 `URLSession|NSURLSession|HttpClient|io.ktor|HttpURLConnection|java.net|okhttp|NWConnection|Analytics|Crashlytics|Sentry|Firebase|ATTrackingManager|advertisingIdentifier|identifierForVendor|WKWebView|SFSafariViewController|mailto|CloudKit|NSUbiquitous|CoreLocation`을 grep했다. 코드 사용 0건(Firebase는 주석뿐, `UIPasteboard`는 진단 로그 복사 한 곳).
- SDK 실제 코드: `app-ios/build/SourcePackages/artifacts/.../GoogleMobileAds.xcframework/ios-arm64/GoogleMobileAds.framework/GoogleMobileAds`(정적 라이브러리)를 `nm -u`·`strings`로 봤다. 결과: `CCCrypt`·`CC_SHA256`·`CC_MD5`·`SecKey*`·`SecRandomCopyBytes`·`ASIdentifierManager`·`identifierForVendor`·`WKWebView`·`SKAdNetwork`·`SKOverlay`·`SKStoreProductViewController`·`CTTelephonyNetworkInfo`·`NSURLSession`. 문자열 `ATTrackingManager`·`trackingAuthorizationStatus`·`SFSafariViewController`는 있고 `requestTrackingAuthorization`은 없다. UMP 정적 라이브러리: `NSURLSession`·`UIDevice`·`WKWebView`.
- Debug 앱(`app-ios/build/DerivedData/Build/Products/Debug-iphonesimulator/GoCoach.app/GoCoach.debug.dylib`, 17:14 빌드): `otool -L`로 WebKit·AdSupport·Security·StoreKit·BackgroundAssets·CoreTelephony·JavaScriptCore·CFNetwork 링크, AppTrackingTransparency 링크 없음을 확인했다. `Frameworks/`의 두 framework 바이너리는 약 51KB 껍데기다(초안의 「import 0건」은 이것을 본 결과였다).
- Release 기기 바이너리 `app-ios/build/DerivedData/Build/Products/Release-iphoneos/GoCoach.app/GoCoach`(10:22 빌드, AdMob 커밋 전): `nm -u`로 `mach_absolute_time`·`statfs` import 0건, `stat`·`fstat`·`lstat`·`sysctlbyname` 있음. 앱 자신의 코드에 대한 근거로만 쓴다.
- SDK 매니페스트: Debug 앱 묶음의 `Frameworks/{GoogleMobileAds,UserMessagingPlatform}.framework/PrivacyInfo.xcprivacy`를 `plistlib`로 읽었다(GMA 12.14.0, UMP 3.1.0).
- 빌드 Info.plist: `plutil -p`로 `ITSAppUsesNonExemptEncryption => false`, `MinimumOSVersion 26.1`, `LSApplicationCategoryType board-games`, 권한 문구 없음을 확인했다(Release-iphoneos·Debug-iphonesimulator).
- 화면 문구·경로: `UiStringsEn.kt`, `UiStringsPremiumSubscription.kt`, `UiStringsHomePages.kt`, `UiStringsDailyDraw.kt`, `UiStringsDailyFortune.kt`, `UiStringsMyInfoAndSettings.kt`, `UiStringsDownloadGuide.kt`, `UiStrings.kt`(`myPageTitle`), `ui/home/GoCoachHomeScreen.kt`(대국 페이지 메뉴), `ui/monetization/PremiumUiState.kt`(업셀 창), `ui/monetization/PremiumSubscriptionNotice.kt`(두 결제 창의 공용 고지), `ui/play/GamePlaySection.kt`(무료 3회 → 1회권 → 업셀).
- 운세 생활 권유 줄: `DailyFortuneCatalog.kt`의 영어 글에서 walk·tea·meal·water·stretch·cook·sleep·rest 등을 찾아 하나씩 읽었다.
- 연령 등급 정의와 지역 표(한국·브라질·호주), Google iOS 공개 안내, 방침 배포본, 포럼 글 701508·840650: 2026-10-27에 WebFetch로 읽었다. 요약 모델을 거쳤으므로 원문 전부는 아니다. 701508은 ATT 거부 뒤 수집으로 반려된 사례라 이 문서의 근거에서 뺐다.
- 방침: 형제 저장소 소스 `BadukAiPrivacy.astro`를 직접 읽었다. 지문 계약은 `rezen.dev/contracts/policy-text.json`, `scripts/check-dist.mjs` 425~447행이다.

## 관련 문서

- `work/plans/PLAY_CONSOLE_PRIVACY_ANSWERS.md`: Play 쪽 짝. A-4의 네 줄이 ①의 출발점이다.
- `work/plans/GOOGLE_PLAY_LAUNCH_PLAN.md` §0: Play 출시 진척의 정본(A-2·A-3·A-4·A-10). iOS 진척은 백로그 #298 카드에 적는다.
- `work/roadmap/261026-_ACTIVE_BACKLOG.md` #296(수익화: 사용자 몫 목록), #297(실기 시험 표), #298(등록·심사), U-84·U-85·U-86. 새 미결 결정은 U-87부터다.
