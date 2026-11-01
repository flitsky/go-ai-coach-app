# 플랫폼 차이 — 안드로이드와 iOS

> **이 문서가 답하는 것**: 같은 앱이 안드로이드와 iOS에서 **사용자에게 무엇이 다르게 보이는가 · 왜 · 코드는 어디서 갈리는가 · 판마다 양쪽에서 확인했는가.**
> 진척(누가 무엇을 하는 중인가)은 백로그 카드(#291 ~ #298)가, 결정의 정본은 `DECISIONS.md`가, 동작의 정본은 코드가 갖는다. 이 문서는 그 셋을 플랫폼 차이 하나로 묶어 본다.
> 2026-11-01 사용자 요청으로 만들었다 — 그전에는 같은 사실이 백로그 「지금의 앱」 iOS 줄 · #292 ~ #298 카드 · App Store 답안지 · 출시 계획 · 봉인된 이식 계획 · rezen.dev 방침에 나뉘어 있었다.

**언제 고치나**
1. 한쪽 플랫폼에만 들어가는 변경을 커밋할 때 → §1(일부러 다르게) 또는 §2(일부러가 아닌 차이).
2. 판에 실리는 일감을 백로그 완료 줄로 적을 때 → §3에 한 줄. 완료 줄 끝에도 iOS 상태를 적는다(「iOS 같은 코드 · 시뮬레이터 ⏳」처럼).
3. 결정이 바뀌면 → §1의 근거 칸이 `DECISIONS.md`의 새 줄을 가리키게.
4. 개수 · 버전처럼 낡는 값은 날짜를 붙여 쓴다.

---

## 0. 지금 (2026-11-01 기준)

| | 안드로이드 | iOS |
| --- | --- | --- |
| 출시 | 프로덕션 **1.8.4**(2026-10-24) · 1.8.5 준비 중(#299 — 작업 트리에서 판 번호만 올림, 커밋 전) | **출시 전** — 시뮬레이터까지 |
| 판 번호 | 1.8.5 / 10805 | 안드로이드와 같은 번호(`bump-version.sh`가 함께 올린다 — 사용자 2026-10-27) |
| 막힌 곳 | — | Apple 개발자 가입(사용자) → 서명 · 실기기 · 모델 받기 · TestFlight(#291 · #297) |
| 기기 · OS | 폰 · 태블릿 · Android 8.0(API 26) 이상 · 폰은 세로 고정, 큰 화면(sw≥600dp)은 회전 | **iPhone만** · iOS **26.1** 이상 · 세로 고정 |

**코드를 얼마나 함께 쓰나**(2026-11-01 기준 — 파일 수): 화면 · 배선 `app-android/src/commonMain` 244 · 규칙 · 정책 · 세션은 `:shared` ·
iOS 전용 `app-android/src/iosMain` 44(Kotlin) + `app-ios/`(Swift · C++ · 설정) · 안드로이드 전용 `app-android/src/main` 111.
**화면과 규칙을 고치면 두 플랫폼이 함께 바뀐다** — 따로 확인해야 하는 것은 §4의 짝 코드다.

---

## 1. 일부러 다른 것

| 영역 | 안드로이드 | iOS | 근거 | 갈리는 자리 |
| --- | --- | --- | --- | --- |
| 바둑판 사진 분석 | 있음 — 홈 카메라 단추 · 실험실 타일 · 공유로 받기 | **없음** — 단추 · 타일이 감춰진다 | U-84 | `IosAppPlatform.kt`가 `boardScan`을 null로 두고, 홈(`GoCoachHomeScreen.kt`) · 실험실(`HomeLabPage.kt`)이 그것을 보고 감춘다 |
| 대상 기기 · 회전 | 폰 · 태블릿 — 큰 화면(sw≥600dp)은 회전한다 | iPhone만(iPad는 다음 판) · 세로 고정 · Mac · Vision Pro 스토어에는 내지 않는다 | U-84 · 사용자 2026-11-01 | `MainActivity.kt`(회전 정책) · `project.pbxproj` `TARGETED_DEVICE_FAMILY = 1` |
| 최소 OS | Android 8.0 | iOS 26.1 | 사용자 2026-10-27(최소 26) · 26.1은 26.0.x 결함 때문(2026-11-01 확인) | `IPHONEOS_DEPLOYMENT_TARGET` |
| 로그인 | 꺼져 있다(코드는 있다 — Firebase) | 없다 — 온보딩을 건너뛰고 기기 식별자만 만든다 | #294(2026-11-01 확인) | `OnboardingSlotIos.kt` · `AccountSettingsSlotIos.kt` |
| 엔진 모델 받기 | Play 자산 팩(PAD) — 앱을 처음 열 때 | Apple 호스팅 Background Assets — 설치 · 업데이트 때부터 | 사용자 2026-10-27 | `AppleHostedModelSource.swift` |
| 엔진 실행 | KataGo를 **별도 프로세스**로 · 멈추면 강제 종료 | KataGo를 **앱 안 스레드**로 · 멈추면 입력 파이프를 닫고 응답을 비운다 | 사용자 2026-10-26(두 쪽 모두 대국용과 분석용을 함께 띄운다) · 멈춤 처리는 2026-11-01 확인 | `LocalKataGoProcessRuntime.kt` · `InProcessKataGoRuntime.kt` · `KataGoBridge.cpp` |
| 구독 | Google Play `premium_basic` | App Store `premium_basic_monthly`(그룹 「Premium」) · **「구매 복원」 단추는 iOS만** · 약관은 Apple 표준 EULA | U-86 · 상품 ID와 약관은 2026-11-01 확인 | `AppStoreSubscriptionSource.swift` · 문구는 `AppStoreKind` |
| 광고 | 기기 설정 · 동의에 따라 맞춤 광고 · 광고 SDK의 자체 초기화 장치가 앱 시작 때 돈다(앱 코드의 초기화는 두 쪽 모두 첫 광고 때) | **비맞춤만**(`npa=1`) · 추적 허용 창(ATT) 없음 · SDK는 첫 광고 때 시작 | U-86 · U-87 | `AndroidRewardedInterstitialAdClient.kt` · `GoogleAdsSource.swift` |
| 광고 동의(UMP) | 앱 시작마다 확인 · EEA 강제 디버그 있음 | 앱 시작마다 확인 · EEA 강제 디버그 **없음** | #296(2026-11-01 확인) | `FORCE_EEA_CONSENT_DEBUG`(안드로이드 빌드 설정) |
| 기기 백업 | Google 자동 백업(엔진 폴더 · 웹뷰만 뺀다) | iCloud · 컴퓨터 백업(엔진 폴더만 뺀다) | #186 · #294 | `backup_rules.xml` · `IosProcessRuntime.kt` |
| 보관 안내 문구 | Google 백업 · Play 복원 | iOS 사실대로 | 사용자 2026-10-27 | `iosLocalStorageNoticeFor`(`UiStringsUserProfile.kt`) |
| 진동 | 시스템 설정을 따르지 않는다(#36) | iOS 「시스템 햅틱」을 따른다 — 꺼져 있으면 울리지 않는다 | 사용자 2026-10-27 | `IosAppPlatform.kt` |
| 배경음악 | ogg · 오디오 포커스 | m4a(빌드 때 ffmpeg 96kbps) · 무음 스위치면 조용 | #294(2026-11-01 확인) | `IosBackgroundMusic.kt` |
| 앱 업데이트 알림 · 스토어 링크 | Play 인앱 업데이트 · 스토어 링크 | 없다(감춘다) — App Store ID가 생기면 다시 본다 | App Store ID가 아직 없다(출시 전) | `StoreListingIos.kt` |
| 「새로워진 점」 | 모든 판 | iOS 첫 출시 판 **뒤의 판만** | #294(2026-11-01 확인) | `AppPlatformActualsIos.kt`의 첫 출시 판 상수 · `platformReleases` |
| 짧은 알림 | 토스트 | 화면 위에 겹쳐 띄운다(약 2초 · 3.5초) | 이식 계획(2026-11-01 확인) | `IosNotice.kt` |
| 출시 초기화(베타 기록 지우기) | 있다 | 없다(지울 베타 기록이 없다) | #294(2026-11-01 확인) | — |
| 진단 로그 복사 | 클립보드 | 클립보드 — Handoff가 켜져 있으면 같은 Apple 계정 기기에도 잠시 담긴다 | Apple 기능 | 방침 1절 |
| 연령 · 개인정보 신고 | Play: 대상 연령 13세 이상(함정 52 · 54) · 데이터 보안은 대략적 위치 · 앱 상호작용 · 진단(충돌 기록 없음) · 기기 ID | App Store: 9+(Health or Wellness — 사용자 2026-10-27) · 앱 개인정보는 충돌 기록까지 | 두 답안지 | `PLAY_CONSOLE_PRIVACY_ANSWERS.md` · `APP_STORE_PRIVACY_ANSWERS.md` |

방침(rezen.dev)은 두 앱이 **같은 본문**을 읽고, 다른 곳은 문장마다 「안드로이드」 · 「iPhone」으로 나눈다(2026-10-28 시행).

---

## 2. 일부러가 아닌 차이 · 열린 것

| 무엇 | 어느 쪽 | 상태 | 어디서 |
| --- | --- | --- | --- |
| 다시보기의 ⏮ ⏭가 컬러 이모지로 그려진다(U+FE0E로는 안 된다) | iOS | 열림 | #294 발견 ② |
| 영어 + 「크게」에서 홈 카드 제목이 단어 중간에서 꺾인다 | iOS에서 봤다 · 공용 배치라 안드로이드도 같을 것(안드로이드에서는 아직 안 봤다) | 열림 | #294 발견 ③ |
| 「내 정보」에서 왼쪽 가장자리 밀기가 페이저를 거꾸로 민다 | iOS | 사용자 물음 | #294 발견 ⑧ |
| 일본어 명언이 구절 단위로 꺾이지 않는다(iOS는 구절 줄바꿈이 없다) | iOS | 알려진 한계 | #294 발견 ⑨ · `QuoteLineBreakIos.kt` |
| 첫 출시 판 상수가 10804(1.8.4)다 — iOS가 1.8.5 뒤로 처음 나가면, 1.8.5 묶음이 더해질 때 설정의 「새로워진 점」 목록에 iOS 사용자가 겪지 않은 변경이 보인다 | iOS | 첫 출시 판이 정해질 때 고친다 | `AppPlatformActualsIos.kt` |
| 모델 받기 전에 크기를 묻지 않는다(심사 4.2.3) | iOS | 사용자 물음 | `APP_STORE_LAUNCH_PLAN.md` 열린 물음 6 |
| 아이폰이 영어여도 앱은 한국어로 시작한다 — 영어 심사자 | 둘 다(심사는 iOS) | 사용자 물음 | 열린 물음 3 |
| 앱 아이콘이 임시본이다(스토어용 512px를 1024px로 키움) | iOS | 원본을 사용자가 준다(2026-11-01) | `AppIcon-1024.png` |
| 실기기에서 아직 안 돌려 본 것 — 모델 받기 · 아이폰 속도 · 실제 광고 · 샌드박스 구독 | iOS | 개발자 가입 뒤 | #293 · #296 · #297 |

---

## 3. 판마다 — 양쪽에서 확인했나

> 판에 실리는 일감마다 한 줄. iOS 칸은 「✅ 기기 또는 시뮬레이터 · 날짜」 · 「⏳」 · 「해당 없음(이유)」 중 하나다.

| 판 | 무엇 | 안드로이드 | iOS |
| --- | --- | --- | --- |
| 1.8.4 | iOS 첫 판의 기준 — 사진 분석을 뺀 전부(U-84) | ✅ 프로덕션 2026-10-24 | ✅ 시뮬레이터 한 바퀴 2026-10-27 · 실기기 ⏳ |
| 1.8.5 | 화면 코드를 공용으로 옮김(#294 W0 ~ W20) — 이 코드가 안드로이드에 나가는 첫 판 | 게이트 · APK 클래스 대조 ✅ · **폰 한 바퀴 ⏳** | 해당 없음(iOS가 이 코드로 만들어졌다) |
| 1.8.5 | #299 운세 · 뽑기 단추 · 바꾼 운세 쌓기 | ✅ 폰 2026-10-29 | 같은 코드 · 저장은 짝(§4) · ⏳ |

---

## 4. 한쪽을 고치면 다른 쪽도 — 짝 코드

> 화면 · 규칙은 공용이라 한 번 고치면 된다. 아래는 **플랫폼마다 따로 있는** 구현이라, 한쪽을 고치면 다른 쪽도 고쳐야 한다.
> 이음새 34개의 전체 목록은 봉인된 이식 계획 `261027-_IOS_PORT_PLAN.md` §3에 있다.

| 무엇 | 안드로이드 | iOS | 지키는 것 |
| --- | --- | --- | --- |
| 저장소(설정 · 진행도) | `app-android/src/main`의 SharedPreferences 저장소(예: `DailyFortuneStore.kt`) | `IosAppStores.kt`가 공용 코덱(`commonMain`의 `kv/` — `KvProgressStores.kt` 등, iOS만 배선)을 `UserDefaultsKeyValueStore.kt` · `IosAppFileStore.kt` 위에 얹는다 | `KvStoreParityTest.kt` · 함정 1 · 69(키 · enum 이름이 곧 저장 포맷). #299가 두 쪽을 함께 고친 예다 |
| 구독 | Play Billing(`PremiumPurchaseGlue.kt`) | `IosStore.kt` → `AppStoreSubscriptionSource.swift` | 판정은 공용 `runPremiumPurchaseApplication` 하나 |
| 보상형 광고 · 배너 | `PremiumPurchaseGlue.kt`(`showRewardedAdOnce`) · `AndroidRewardedInterstitialAdClient.kt` · `BannerAdViewAndroid.kt` | `AppMonetizationLocalIos.kt` → `GoogleAdsSource.swift` · `BannerAdViewIos.kt` | 깔때기는 `showRewardedAdOnce` 하나(함정 99 — 광고 동안 배경음악을 놓는다) |
| 엔진 실행 | `LocalKataGoProcessRuntime.kt` | `InProcessKataGoRuntime.kt` · `IosProcessRuntime.kt` · `KataGoBridge.cpp` | 시간 초과 · 재시작 규칙은 공용(함정 71 · 85) |
| 모델 받기 | PAD | `IosEngineModels.kt` → `AppleHostedModelSource.swift` | 같은 엔진 준비 화면 · 다시 시도 |
| 배경음악 | `BackgroundMusicPlayer.kt` | `IosBackgroundMusic.kt` | 전면 광고 동안 멈춤 |
| 진동 | `PlayHaptics.kt` | `IosAppPlatform.kt` | 진단 리포트의 `[Haptics]` 절 |
| 앱 시작 | `GoAiCoachApplication.kt` · `MainActivity.kt` | `IosAppStartup.kt`(`runIosAppStartup`) · `MainViewController.kt` · 광고 동의 확인은 `GoCoachApp.swift`(창이 선 뒤) | 시작 때 하는 일(광고 동의 확인 · 개발자 초기화 · 출석 · 음악)을 **양쪽이 따로** 적는다 — 한쪽에 단계를 더하면 다른 쪽도 |
| 스토어 이름이 들어간 문구 | — | — | `AppStoreKind` 한 곳에서 가른다(해지 · 확인 실패 문구) |
