# 최초 실행 온보딩 + 계정 시스템 도입 마스터 플랜

작성일: 2026-07-29

본 문서는 **go-ai-coach** 앱에 "최초 실행 시 한 번만 뜨는 온보딩 화면"과 Firebase 기반 계정 시스템(Google/이메일/익명 로그인)을 도입하기 위한 작업 요약 및 단계별 개발 플랜입니다. `premium-mode/README.md`와 동일한 방식으로, 구현이 진행되며 계속 갱신되는 히스토리 문서로 유지합니다. "왜 이렇게 하기로 했는가"라는 상위 원칙은 `feature-access-principles/README.md`에 별도로 정리되어 있습니다 — 이 문서는 그 원칙의 실행 로그입니다.

---

## 1. 개요 및 목표

### 1.1. 배경 및 필요성
- 기존 장기(Janggi) 앱에서 이미 Firebase Auth + Firestore/Storage + AdMob을 연동해 성공적으로 운영 중인 경험을 이 바둑 앱에도 그대로 재활용합니다 (`docs/baduk_app_architecture_recommendation.md`).
- BaaS 5종(Firebase/Supabase/PocketBase/Appwrite/Convex) 비교 검토 결과(`docs/baas_solutions_comparison.md`), 기보(SGF) 저장·보상형 광고·AdMob 시너지 관점에서 **Firebase가 최종 채택**되었습니다.
- 프리미엄 모드(`premium-mode/README.md`)의 Step 3(광고)/Step 4(구매)가 이 계정 시스템 위에 얹힐 예정이므로, 그 전제가 되는 "로그인/익명 사용자 식별" 기반을 먼저 마련하는 것이 이번 계획의 핵심입니다.

### 1.2. 핵심 논의: 계정 없이 시작하기 & 구매 복구
- **결론**: "계정 없이 시작하기"(게스트 모드)는 제공한다. 다만 앱을 지우면 구매 이력을 못 찾는다는 우려는 **로그인 여부가 아니라 아이템 결제를 어떻게 구현하느냐**로 해결한다.
- Google Play 인앱결제(Billing)는 앱 자체 로그인과 무관하게 **구매가 Google Play 계정에 귀속**된다. 재설치 후 `queryPurchases()`로 자동 복원되므로, 게스트 사용자도 이 복원 경로는 그대로 유효하다.
- 진짜 위험한 경우는 서버(Firestore)에 `premium_until`처럼 **앱 자체 계정(UID)에 귀속된 엔타이틀먼트**를 둘 때뿐이다 — 이때는 로그인하지 않은 익명 사용자가 지우면 그 기록은 복구 불가.
- ~~그래서 채택한 구조: 첫 실행 시 로그인을 강제하지 않고 **Firebase 익명(Anonymous) Auth로 조용히 시작** → Google/이메일 로그인은 "다른 기기에서 이어보기" 가치로 선택 제안 → 나중에 로그인하면 `linkWithCredential`로 기존 익명 UID를 승격~~

> ⚠️ **위 구조는 폐기됐다(2026-08-05). 이 절을 근거로 설계하지 마세요.** 파이어베이스 Auth의 익명 로그인은 이 프로젝트에서 **영구히 켜지 않기로 확정**됐다 — 재설치(로컬 세션 캐시 소실)마다 새 익명 계정이 생겨 콘솔에 고아 계정이 쌓이는 구조적 문제를 이전 앱에서 실제로 겪었기 때문이다. 아래 "`signInAnonymously()` 제거(2026-08-05)" 절이 원본 근거다.
>
> **대체된 실제 구조**: 게스트 식별은 파이어베이스와 무관한 **로컬 UUID**(`application/device/DeviceIdentityStorePort.loadOrCreate()`)가 담당한다. 재설치 후 프리미엄 복원은 앱 계정이 아니라 **Google Play 계정에 귀속된 Play Billing `queryPurchases()`**가 처리하므로(1.2절 두 번째 항목 그대로), 익명 인증 없이도 이 절이 걱정하던 문제는 생기지 않는다.
>
> ⚠️ **용어 주의**: 앱 기능인 **'계정 없이 시작하기'**(로컬 UUID)와 **파이어베이스 Auth의 익명 로그인 활성화 여부**(콘솔 설정, 현재 비활성)는 서로 다른 개념이며 혼용하지 않는다.
>
> 그 결과 **"익명 → 실계정 승격"을 전제로 쓴 아래 서술은 전부 성립하지 않는다** — Step 2/3의 `linkWithCredential` 승격 경로, Step 4의 목표 정의, 그리고 `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` 로드맵 7번이 그것이다. 로드맵 7번은 "착수 전에 목표를 다시 정의해야 한다"고 이미 표시해 뒀다(예: "게스트(로컬 ID) → 실계정 승격"). 승격 코드 자체(`AuthProvider.Anonymous`, `isPromotableAnonymousSession`, `linkGoogleCredential`/`linkEmailCredential`)는 무해해서 지우지 않고 남겨 뒀다.
>
> ⚠️ 여기에 더해 **로그인 기능 전체가 2026-08-09에 꺼졌다**(`ui/FeatureFlags.kt`의 `isLoginEnabled = false`) — 아래 "결정 번복: 이번 출시에서 로그인 기능 전체를 끄기로 결정" 절 참고.

---

## 2. 단계별 구현 계획

### Step 1 — 온보딩 화면 UI + 익명 인증 (이번 라운드)
- **목적**: 홈 화면보다 앞서 뜨는, 최초 1회만 노출되는 온보딩 화면을 만들고, 실제로 동작하는 로그인 수단은 "계정 없이 시작하기"(Firebase Anonymous Auth)까지만 구현한다.
- **범위**:
  - Google/이메일 로그인 버튼은 배치만 하고, 탭하면 홈 화면 "학습하기" 카드와 동일한 "준비 중" 토스트 패턴을 재사용.
  - Apple 로그인은 UI 자체를 넣지 않음 (완전 후순위).
  - Firebase 콘솔 프로젝트는 장기 앱과 별도 독립 프로젝트로 새로 생성 (Spark Plan 무료 할당량이 프로젝트 단위로 독립 적용되기 때문 — `docs/baduk_app_architecture_recommendation.md` 2장 참고).
- **산출물**: `OnboardingScreen.kt`, `application/auth/AuthState.kt`(순수 도메인, iOS 이식 전제), `application/auth/AuthClientPort.kt` + `ui/AndroidAuthClient.kt`(Firebase Auth 실제 호출), `UserPreferencesSnapshot.hasSeenOnboarding` 플래그, Gradle Firebase 의존성 스캐폴딩(google-services.json 없이도 빌드가 깨지지 않도록 조건부 플러그인 적용).
- **상태**: ✅ 완료 (2026-07-29) → 2026-08-04 개정, 아래 "Step 1 개정" 참고

### Step 1 개정 — 온보딩 완료 조건을 로컬 익명 ID로 전환 (2026-08-04)
- **배경**: google-services.json이 아직 없는 개발 환경에서는 `signInAnonymously()`가 항상 실패해, `hasSeenOnboarding`이 저장되지 않고 온보딩 화면이 매 실행마다 반복되는 문제를 실측으로 확인했다.
- **변경**: 온보딩 완료 조건을 `application/device/DeviceIdentityStorePort.loadOrCreate()`(Stage C-2에서 이미 만들어졌던, 소비자가 없던 로컬 UUID 인프라)로 바꿨다 — 네트워크 없이 항상 즉시 성공하므로 반복 노출이 사라진다. `signInAnonymously()` 호출은 제거하지 않고 버튼 탭 시 fire-and-forget으로 남겨, google-services.json이 나중에 추가되면 이 코드를 다시 건드리지 않고도 조용히 성공하기 시작한다.
- **Google/이메일 스텁 버튼은 온보딩에서 제거**하고 신규 `SettingsScreen.kt`(홈 화면 좌상단 ⚙ 진입점)로 옮겼다 — 최초 실행 필수 흐름에서 로그인 UI 자체를 걷어내고, 원하는 사용자만 나중에 강화하도록 했다. 이 개편 이후에도 Step 2/3(Google/이메일 실제 연동)의 산출물 위치는 그대로 유효하다 — `AuthClientPort`에 메서드 추가 + `SettingsScreen.kt`의 두 버튼 onClick 교체로 착수하면 된다.
- 관련: 당시 Play Flow UX 리팩토링 계획서(2026-08-17 문서 정리로 저장소에서 제거, `docs/DOCS_INDEX.md` "문서 보존 정책" 참고).

### Step 1 재개정 — 온보딩을 다시 "얕은 허들" 로그인 화면으로 (2026-08-04)
- **배경**: 위 개정에서 온보딩을 완전히 건너뛰는 "시작하기" 단일 버튼으로 단순화했으나, 사용자 피드백으로 "설치 후 첫 화면은 로그인 화면이어야 하고, 그냥 패스시키는 지금 화면은 의미가 없다"는 방향이 확인됐다 — 첫 실행 시 로그인 여부를 가볍게라도 물어보는 게 맞다는 결론.
- **변경**: `OnboardingScreen.kt`를 Google/Apple/이메일/게스트("계정 없이 시작하기") 4버튼 구성으로 되돌렸다. Google/Apple/이메일은 여전히 "준비 중" 스텁이고, 게스트 버튼만 실제로 동작한다 — 단, 반복 노출 버그는 그대로 고쳐진 상태를 유지한다(게스트 완료 조건은 여전히 `DeviceIdentityStorePort.loadOrCreate()`, 즉시 성공).
- **Apple 버튼 신규 추가** — 위 2.1절의 "완전 후순위" 결정을 일부 뒤집는다. iOS 대응 시 Apple 로그인 제공이 App Store 정책상 사실상 필수(Google 등 제3자 로그인을 제공하면 Apple 로그인도 함께 제공해야 함)에 가깝다는 점을 고려해, UI 스텁만 지금 추가했다. 실제 연동은 여전히 후순위(Step 2/3과 동일한 성격의 별도 Step으로 착수 시 결정).
- **게스트 선택 시 안내**: "설정에서 로그인 연동 가능"이라는 별도 확인 팝업(모달)은 추가하지 않기로 했다 — 대신 홈 화면 진입 직후 가벼운 토스트("게스트로 시작했습니다. 설정에서 로그인할 수 있어요")를 1회만 띄운다. 얕은 허들이라는 취지를 유지하면서(추가 결정 요구 없음), 설정 진입점의 존재는 알리기 위함.
- `SettingsScreen.kt`도 동일하게 Google/Apple/이메일 3버튼으로 맞춰, 온보딩에서 게스트를 고른 사용자가 나중에 같은 선택지로 돌아올 수 있게 했다.

### Step 2 — Google 로그인 실제 연동
- **목적**: Step 1의 "준비 중" 스텁을 Credential Manager/One Tap 기반 실제 Google 로그인으로 교체.
- **범위**: SHA-1 인증서 지문을 Firebase 콘솔에 등록(이번 Step에서 처음 필요해짐), `AuthClientPort`에 `signInWithGoogle(...)` 메서드 추가, 익명 사용자가 로그인할 경우 `linkWithCredential`로 UID 승격(데이터 유실 없이).
- **상태**: ✅ 완료 (2026-08-05) → 구현 내용은 아래 "Step 2 구현" 절 참고.

### Step 3 — 이메일 로그인 실제 연동
- **목적**: Firebase Email/Password 또는 Email Link 로그인 연동.
- **상태**: ✅ 완료 (2026-08-05, Email/Password로 구현) → 구현 내용과 Email Link를 쓰지 않은 이유는 아래 "Step 3 구현" 절 참고.

### Step 4 — 데이터 동기화 & 구매 서버 검증
- **목적**: Firestore로 기보/설정 클라우드 동기화. 프리미엄 모드 Step 4(영구 구매)의 서버 측 엔타이틀먼트 저장이 이 위에 얹힌다.
- **범위**: `users/{uid}` 문서 구조 설계, Play Billing `queryPurchases()` 복원 로직을 앱 시작 시 훅, Firestore Security Rules(1장 논의 결론 반영).
- **상태**: 대기

### (별도 후순위) Apple 로그인
- iOS 대응 시점에 맞춰 별도 과제로 진행. 이번 문서의 Step 1~4는 모두 Android/Firebase 우선이지만, `AuthState`/`AuthClientPort` 설계 자체는 플랫폼 비종속으로 만들어 두었다 (`premium-mode/README.md`의 `PremiumState` 설계 원칙과 동일).

### 계층 배치 참고 (`docs/ARCHITECTURE.md`의 7계층 기준, 2026-07-29 정리)

Step 1(익명 인증)은 이미 이 배치를 따르고 있다 — `AuthClientPort`가 포트, `AndroidAuthClient`가 어댑터, `GoCoachApp.kt`/`OnboardingScreen.kt`가 App Service 오케스트레이션. Step 2~4의 새 코드도 착수 전에 같은 기준으로 미리 배치한다.

| Step | 작업 | 계층 | 근거 |
| --- | --- | --- | --- |
| Step 2 | Google Credential Manager/One Tap SDK 호출(`signInWithGoogle`) | **포트/원시 계층** (엔진 2계층 `EngineCoreApi`에 대응) | `AuthClientPort`에 메서드 추가 + `AndroidAuthClient`(또는 SDK 의존이 무거우면 전용 파일, `docs/ARCHITECTURE.md`의 어댑터 파일 분리 기준 참고)가 실제 SDK를 감싼다. |
| Step 2 | 익명 UID → 실계정 `linkWithCredential` 승격 판단 | **App Service / Session Orchestration** (6계층) | "언제 승격할지, 승격 후 어느 화면으로 갈지"는 유스케이스 조합이지 원시 SDK 기능이 아니다. |
| Step 3 | Firebase Email/Password·Email Link SDK 호출 | **포트/원시 계층** | Step 2의 Google 로그인과 동일한 성격 — `AuthClientPort`에 메서드만 추가. |
| Step 4 | Play Billing `queryPurchases()` 복원 + Firestore 엔타이틀먼트 조율 | **Middleware / Cache Domain 성격** (4계층에 대응) | `premium-mode/README.md`의 Step 4와 동일한 판단(원시 응답을 그대로 믿지 않고 검증/캐시/신뢰도를 조율) — 실제로는 같은 기능이므로 두 문서가 가리키는 계층도 일치해야 한다. |
| Step 4 | Firestore 기보/설정 동기화(`users/{uid}` 문서 읽기/쓰기) | **Middleware / Cache Domain 성격** | 로컬/원격 데이터 조율이라는 점에서 `PositionAnalysisCacheResolver`와 같은 역할군. `application/` 안에 전용 파일(예: `application/sync/` 신설)로 분리하되 물리적으로 엔진 `middleware/`와는 합치지 않는다. |

---

## 3. 사용자가 직접 해야 하는 Firebase 콘솔 설정

1. ✅ https://console.firebase.google.com 에서 장기 앱과 별도인 새 프로젝트 생성 (`project-baduk-hanpan`, 2026-08-04).
2. ✅ Android 앱 추가 — 패키지명 `com.zenit9hub.ai.baduk` (2026-08-04에 등록됨). **주의**: 이 문서 작성 당시 예정했던 `com.worksoc.goaicoach`가 아니라 `com.zenit9hub.ai.baduk`로 등록됐다 — 그래서 `app-android/build.gradle.kts`의 `applicationId`도 이 값으로 맞춰 변경했다(아래 "Step 1 개정 2" 참고). `namespace`(Kotlin 패키지/R·BuildConfig 생성 위치)는 `com.worksoc.goaicoach`로 그대로 둠 — Firebase 매칭은 `applicationId`만 본다.
3. ⬜ **Authentication → Sign-in method → 익명(Anonymous)** 활성화 (Step 1에 필요, SHA-1 불필요) — **의도적으로 보류 중(2026-08-05 결정)**. 익명 로그인을 켜면 게스트로 실행될 때마다(재설치 포함) Firebase 콘솔에 영구 사용자 레코드가 쌓여 "무분별하게 늘어난다"는 우려가 제기됨 — Firebase의 공식 완화책(휴면 익명 계정 자동 삭제)은 Identity Platform 업그레이드가 전제 조건이라 단순 설정만으로는 못 켠다. 지금은 Step 4(Firestore 동기화)가 아직 없어 익명 UID에 매달린 서버 데이터도 없으므로, 켜지 않아도 게스트 플로우(`DeviceIdentityStorePort` 기반)에는 지장이 없다 — Step 4 착수 시점에 재논의. 활성화 전에는 `signInAnonymously()`가 계속 실패하지만 fire-and-forget이라 앱 사용에는 지장 없음.
4. ✅ `google-services.json` 다운로드 → `app-android/google-services.json`에 저장 (`.gitignore`에 등록되어 있어 커밋되지 않음). 2026-08-05에 Google 로그인용 OAuth 클라이언트가 포함된 버전으로 재다운로드/교체함.
5. ✅ (2026-08-05) Google 로그인용 SHA-1/SHA-256 인증서 지문 등록 — 디버그 키스토어 기준(`./gradlew :app-android:signingReport`), `google-services.json`의 `oauth_client[].android_info.certificate_hash`와 일치 확인함.
6. ✅ (2026-08-05) **Authentication → Sign-in method → Google** 활성화. 같은 시점에 **이메일/비밀번호**도 활성화됨(Step 3 착수 시 바로 쓸 수 있음, 이번 Step 2 코드는 사용하지 않음).

### Step 1 개정 2 — 실제 Firebase 프로젝트 연결 (2026-08-04)
- google-services.json을 받아 `app-android/google-services.json`에 배치하고, 루트 `build.gradle.kts`(`google-services` 플러그인 버전 4.4.4 → 4.5.0), `gradle/libs.versions.toml`(`firebaseBom` 34.16.0 → 34.17.0, `firebase-analytics` 라이브러리 항목 추가), `app-android/build.gradle.kts`(`implementation(libs.firebase.analytics)`)를 갱신했다.
- **패키지명 불일치 발견 및 해결**: 받은 google-services.json의 `package_name`이 `com.zenit9hub.ai.baduk`였는데 당시 앱의 `applicationId`는 `com.worksoc.goaicoach`였다 — 사용자에게 확인 후 `applicationId`를 `com.zenit9hub.ai.baduk`로 변경(2번 참고). `:app-android:processDebugGoogleServices` 태스크가 정상 통과함을 `make test`로 확인.
- `namespace`는 변경하지 않았다 — Kotlin 소스의 `package com.worksoc.goaicoach.*` 선언, `R`/`BuildConfig` 참조 등은 전혀 건드릴 필요가 없었다(namespace와 applicationId는 독립적인 값).
- **부수 버그 발견 및 수정**: applicationId 변경 직후 "이전 버전은 엔진이 되는데 새 패키지명 앱은 스텁으로 동작한다"는 리포트를 받음 — 원인은 `.so` 실행 파일이 아니라(APK에 번들되어 있어 무관), KataGo 모델/설정 파일(`model.bin.gz`, `gtp_learning.cfg`, `analysis_learning.cfg`)을 개발용으로 로컬 Mac에서 `adb shell run-as <package> cp ...`로 주입하는 `scripts/seed-katago-model-to-app.sh`와 `Makefile`(`reinstall-dev-engine`/`launch`)이 옛 패키지명 `com.worksoc.goaicoach`를 하드코딩하고 있던 것 — 새 패키지(`com.zenit9hub.ai.baduk`)는 앱 전용 저장소가 완전히 별개라(Android는 applicationId 단위로 파일 격리) 이 파일들이 하나도 주입되지 않은 상태였다. `EngineBootstrap.kt`가 이 파일들의 존재 여부만으로 실제 엔진(`LocalProcess`)/스텁(`Stub`)을 조용히 가른다(예외 처리 없이 단순 파일 체크).
  - `scripts/seed-katago-model-to-app.sh`의 `PACKAGE` 기본값과 `Makefile`의 3곳(`reinstall-dev-engine`의 `adb uninstall`, `launch`의 `force-stop`/`am start`)을 `app-android/build.gradle.kts`에서 `grep`으로 직접 읽어오는 `APP_PACKAGE`/`APP_NAMESPACE` 변수로 교체 — 이제 applicationId가 또 바뀌어도 하드코딩이 조용히 어긋나지 않는다. `MainActivity`의 adb 컴포넌트명은 `<APP_PACKAGE>/<APP_NAMESPACE>.MainActivity`로 구성해야 한다는 점도 확인(activity 클래스명은 namespace 기준, 설치 패키지는 applicationId 기준으로 서로 다른 값을 쓴다).
  - 실제 폰(`R5CT22WTVXP`)에 `make seed-engine` 재실행 → `files/katago/`에 세 파일 생성 확인, `make launch` 재실행 → 새 KataGo 로그 파일 2개 생성 확인(엔진 정상 기동).

### Step 2 구현 — Google 로그인 실제 연동 (2026-08-05)
- **콘솔**: Google 로그인 활성화 + SHA-1/SHA-256(디버그) 등록 + `google-services.json` 재다운로드까지 완료(위 3장 체크리스트 4~6번). 익명(Anonymous)은 콘솔 사용자 목록이 무분별하게 늘어난다는 우려로 **의도적으로 계속 보류**(3장 3번 참고) — 이번 Step 2 코드는 이 상태에서도 정상 동작하도록 설계함(아래 참고).
- **`AuthClientPort`(포트, 플랫폼 비종속)**: `signInWithGoogle(idToken)`(신규 로그인), `linkGoogleCredential(idToken)`(익명 세션 승격), `currentAuthState()`(동기 조회) 3개 메서드 추가. "지금 익명 세션이라 승격 대상인지"는 `AuthState.isPromotableAnonymousSession`이라는 순수 함수로 분리해, 이 판단이 SDK 어댑터 안에 묻히지 않고 유스케이스 판단으로 남게 했다 — 위 "계층 배치 참고" 표가 명시한 기준.
- **`AndroidAuthClient`(어댑터)**: 위 3개 메서드의 실제 Firebase Auth 구현. `linkGoogleCredential`이 `FirebaseAuthUserCollisionException`(이 Google 계정이 이미 다른 Firebase 사용자에 연결된 경우)을 만나면 그 기존 계정으로 그냥 로그인시키는 폴백을 흡수한다 — Step 4 이전인 지금은 익명 UID에 서버 데이터가 없어 안전한 처리.
- **`ui/GoogleCredentialManagerClient.kt`(신규 파일)**: Credential Manager/Sign in with Google 호출만 전담 — Firebase Auth 호출과 SDK 실패 유형이 섞이지 않도록 분리(README 표의 "SDK 의존이 무거우면 전용 파일" 기준). `R.string.default_web_client_id`(google-services.json의 웹 OAuth 클라이언트로부터 자동 생성)를 참조한다.
- **`ui/GoogleSignInFlow.kt`(신규 파일)**: `OnboardingScreen`/`SettingsScreen`이 공유하는 시도 흐름(토큰 요청 → 승격 여부 판단 → Firebase 호출 → 실패 시 `DiagnosticEventLogPort`로 로그). 실패/취소를 조용히 삼키지 않고 항상 로그 + 토스트로 안내.
- **UI**: `OnboardingScreen`/`SettingsScreen`의 Google 버튼을 스텁에서 실제 플로우로 교체. `SettingsScreen`은 `authClient.currentAuthState()`로 초기 상태를 읽고, 로그인 성공 시 로컬 상태를 갱신해 문구를 "Google 계정으로 로그인되어 있습니다"로 바꾸고 Google 버튼 자체를 숨긴다(같은 계정으로 다시 시도할 이유를 없앰). 문자열 3개(`googleSignedInToastMessage`/`googleSignInFailedMessage`/`settingsGoogleStatusMessage`)를 4개 언어(ko/en/ja/zh) 모두에 추가.
- **의존성**: `androidx.credentials:credentials:1.6.0`, `androidx.credentials:credentials-play-services-auth:1.6.0`, `com.google.android.libraries.identity.googleid:googleid:1.2.0`(2026-08 기준 최신 안정 버전).
- **검증**:
  - `JAVA_HOME=temurin-17 make test` 통과 확인(`LayeringContractTest`의 `application/auth` 플랫폼 비종속 검사 포함).
  - 에뮬레이터(`Pixel_7_API_35`, `emulator-5554`)에 설치 후 설정 화면에서 실제 Google 계정(`flit9sky@gmail.com`)으로 로그인 End-to-end 확인 — Credential Manager 계정 선택 → Google 동의 화면 → Firebase 로그인 → 설정 화면 상태 문구/버튼이 즉시 갱신되는 것까지 실기기 로그(logcat)로 크래시 없음 확인.
  - **익명 → Google 승격(`linkGoogleCredential`) 경로는 기기에서 실측하지 못함** — 위 결정대로 Anonymous가 콘솔에서 계속 꺼져 있어 승격할 익명 세션 자체를 만들 수 없었다. 판단 로직(`AuthState.isPromotableAnonymousSession`)은 `AuthStateTest`에 유닛 테스트로 커버했고, 나중에 Anonymous를 켜면 코드 변경 없이 실기기 검증이 가능한 상태.

### Step 3 구현 — 이메일 로그인 실제 연동 (2026-08-05)
- **Email/Password vs Email Link 결정**: 콘솔에는 이메일/비밀번호와 함께 "이메일 링크(비밀번호 없는 로그인)"도 켜둔 상태였지만, 이번 라운드는 **Email/Password로 구현**했다. Email Link가 예전에 의존하던 Firebase Dynamic Links가 2025-08-25에 완전히 셧다운됐고, 그 이후의 공식 대체 경로(Firebase Hosting 기본 도메인 `PROJECT_ID.firebaseapp.com` + AndroidManifest 딥링크 인텐트 필터)조차 "프로젝트가 이미 새 도메인 구성으로 돼 있는지, 아니면 Admin SDK로 한 번 마이그레이션 호출을 해줘야 하는지"가 이 앱처럼 셧다운 이후에 새로 만든 프로젝트 기준으로도 문서상 명확히 확인되지 않았다 — 잘못 만들면 "링크를 눌러도 앱이 안 열리는" 방식으로 조용히 깨질 위험이 있어, 이번엔 안전하고 자체완결적인 Email/Password를 선택했다. Email Link는 이 불확실성을 콘솔/실기기로 직접 확인한 뒤 후속 작업으로 붙일 수 있다(포트 배치 기준은 표에 이미 정리돼 있음).
- **`AuthClientPort`/`AndroidAuthClient`**: `signInWithEmail`/`linkEmailCredential`을 Google과 동일한 모양으로 추가. 다만 내부 순서는 다르다 — **먼저 계정 생성을 시도하고, 이미 가입된 이메일이면(`FirebaseAuthUserCollisionException`) 그 계정으로 로그인**하는 순서를 택했다. 반대 순서(로그인 먼저, 실패 시 가입)는 최신 Firebase Auth가 "가입 안 된 이메일"과 "비밀번호 오류"를 계정 열거(enumeration) 방지 목적으로 같은 예외로 뭉뚱그릴 수 있어 신뢰할 수 없기 때문이다 — 반면 이메일 중복(충돌)은 여전히 명확히 구분되는 신호다. `linkCredentialOnce`를 `AuthProvider`를 인자로 받도록 일반화해 Google/이메일이 공유한다.
- **UI**: 신규 `EmailSignInDialog.kt`(이메일+비밀번호 2필드, 가입/로그인을 사용자가 직접 고르지 않고 버튼은 "계속하기" 하나) + `EmailSignInFlow.kt`(Google과 동일한 공유 시도 흐름). 비밀번호 6자 미만/이메일 형식 오류 시 클라이언트에서 버튼을 미리 비활성화하고, 실제 실패 판정은 항상 Firebase 응답을 신뢰한다. `FirebaseAuthWeakPasswordException`은 전용 메시지로, 그 외 실패(주로 비밀번호 오류)는 계정 존재 여부를 노출하지 않는 "이메일 또는 비밀번호를 확인해주세요" 문구로 안내한다.
- **부수 수정(계정 전환 사고 방지)**: `SettingsScreen`에서 Google 버튼만 조건부로 숨기던 기존 로직을 "실계정(Google 또는 이메일) 로그인 중이면 두 버튼 다 숨김"으로 일반화했다 — 그렇지 않으면 예를 들어 Google로 로그인한 사용자가 실수로 이메일 버튼을 눌러 완전히 다른 계정으로 조용히 전환될 수 있었다(로그아웃 UI가 없어 되돌릴 방법도 없음). 이메일 기능을 추가하면서 새로 생긴 위험을 같은 라운드에서 막았다.
- **검증**: `make test` 통과. 에뮬레이터(`emulator-5554`)에서 앱 데이터를 초기화해가며 세 가지 실제 경로를 전부 확인했다 — (1) 신규 이메일 가입 → 홈 화면 진입 및 설정 화면 상태 문구/버튼 갱신, (2) 같은 이메일+올바른 비밀번호로 재로그인(충돌 폴백 경로), (3) 같은 이메일+틀린 비밀번호(다이얼로그가 닫히지 않고 "이메일 또는 비밀번호를 확인해주세요" 토스트만 표시, 재시도 가능). 세 경우 모두 크래시 없음(logcat 확인).

### `signInAnonymously()` 제거 (2026-08-05)
- Step 2/3 작업 중 사용자가 파이어베이스 Auth의 익명 로그인을 이 프로젝트에서 절대 켜지 않기로 확정했다(위 3장 3번, 이전 앱에서 재설치마다 새 익명 계정이 쌓여 허수 유저가 늘어나는 문제를 실제로 겪었음을 확인). 이에 따라 `AuthClientPort`/`AndroidAuthClient`의 `signInAnonymously()`와 `OnboardingScreen.kt` 게스트 버튼의 fire-and-forget 호출을 완전히 제거했다.
- "Step 1 개정"(2026-08-04)에서는 이 호출을 일부러 남겨뒀었다 — 당시엔 "Firebase 설정이 아직 안 됐을 뿐"이라는 열린 가능성이었고, 나중에 `google-services.json`이 추가되면 코드를 안 건드려도 조용히 성공하기 시작하는 게 장점이었다. 지금은 그 전제가 달라졌다: Firebase는 이미 완전히 설정돼 있고(Google/이메일 로그인 실제 동작 중), 익명 로그인만 별도로 "구조적 한계 때문에 계속 끈다"는 확정된 결정이라 열린 가능성이 아니다 — 그래서 죽은 코드로 남겨두지 않고 제거했다. 나중에 이 결정이 다시 바뀌면 `signInWithGoogle`/`signInWithEmail`과 같은 패턴으로 다시 추가하면 된다.
- `AuthProvider.Anonymous`와 `AuthState.isPromotableAnonymousSession`, `linkGoogleCredential`/`linkEmailCredential`의 승격 로직은 그대로 남겨뒀다 — 이건 "익명 로그인을 시도하는 코드"가 아니라 "혹시 익명 세션이 있다면 승격하는 코드"라 무해하고, 나중에 이 결정이 바뀌어도 다시 만들 필요가 없다.
- 검증: `make test` 통과, `grep -rn signInAnonymously app-android/src`로 잔여 참조 없음 확인.

---

## 4. Step 1 구현 메모 (2026-07-29)

- `PremiumState`(`application/premium/PremiumState.kt`)와 동일한 스타일로 `application/auth/AuthState.kt` 설계 — Android/Compose import 0개, `data class AuthState(isSignedIn, provider, uid)` + `AuthProvider` enum(`Anonymous`/`Google`/`Email`).
- 실제 SDK 호출은 `AuthClientPort`(포트) + `ui/AndroidAuthClient.kt`(Firebase 구현)로 분리 — `signInAnonymously()`만 구현, Google/이메일 메서드는 그 기능을 실제로 붙일 때 추가(YAGNI).
- `google-services.json`이 없어도 `make test`/`make dev`가 깨지지 않도록, `app-android/build.gradle.kts`에서 `google-services` 플러그인을 `alias(...) apply false`로 등록만 해두고 `if (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }`로 조건부 적용.
- Firebase BOM 34.16.0(2026-07 기준 최신) 기준 `firebase-auth-ktx`가 더 이상 별도 아티팩트로 관리되지 않아(-ktx 확장이 본체에 통합됨), `firebase-auth`를 사용.
- `hasSeenOnboarding` 플래그는 새 저장소를 만들지 않고 기존 `UserPreferencesSnapshot`/`UserPreferencesStore`(SharedPreferences+JSON) 패턴에 필드 하나로 추가 — 스키마 버전은 올리지 않음(`optBoolean` 기본값으로 안전하게 하위 호환).
- `GoCoachApp.kt`의 `currentDestination` 초기값 계산식만 바꿔(`hasSeenOnboarding` 기준 `Onboarding`/`Home` 분기) 새 Compose 상태 훅을 추가하지 않았다 — `LayeringContractTest`의 `stateHookBudget`(47)이 거의 소진된 상태였기 때문. `AndroidAuthClient`도 내부 상태가 없는 얇은 래퍼라 `remember`로 캐시하지 않고 매 재구성마다 새로 생성해도 무해하다는 점을 이용해 훅 예산을 아꼈다.

### 계정 삭제 기능 추가 — Google Play 정책 대응 (2026-08-09)

- **배경**: Play Console에 앱을 등록하며 "데이터 보안" 설문을 진행하던 중, Google Play 정책상 앱 내 계정 생성(이 앱은 Google/이메일 로그인 둘 다 지원)을 지원하는 앱은 (1) 앱 내 삭제 경로와 (2) 앱을 지우고도 접근 가능한 웹 링크/이메일 두 가지를 **모두** 제공해야 한다는 사실을 확인했다(Google 공식 문서 [Understanding Google Play's app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111) 기준, 2026-08-09 WebFetch로 재확인). 웹 링크 쪽은 `mailto:` 링크로도 요건을 satisfies한다는 점까지 확인해 Play Console 폼에는 연락처 이메일을 등록했고(코드 변경 아님), 이 절은 그중 앱 내 경로 구현을 기록한다.
- **`AuthClientPort`/`AndroidAuthClient`**: `suspend fun deleteAccount(): Result<Unit>` 추가 — `FirebaseAuth.getInstance().currentUser?.delete()`를 기존 어댑터들과 동일한 `suspendCancellableCoroutine` 콜백 래핑 패턴으로 구현. 로그인 세션이 오래되면 Firebase가 `FirebaseAuthRecentLoginRequiredException`을 던지는데, 별도 재인증 플로우를 새로 만들지 않고 이 예외만 구분해 "재로그인 후 다시 시도" 안내로 갈라 보여주는 선에서 범위를 제한했다(YAGNI — 이 앱 사용 패턴상 재로그인이 자주 필요할 세션 길이가 아니라고 판단).
- **`AccountDeletionFlow.kt`**(신규): `EmailSignInFlow.kt`/`GoogleSignInFlow.kt`와 동일한 얇은 글루 패턴 — 실패 시 진단 로그를 남기되, `FirebaseAuthRecentLoginRequiredException`은 `account_deletion_recent_login_required`로 별도 코드를 부여해 일반 실패(`account_deletion_failed`)와 구분한다.
- **`SettingsScreen.kt`**: "계정 삭제" 버튼(`MaterialTheme.colorScheme.error` 톤)을 계정 상태 문구 바로 아래 배치하고 `hasRealAccount`일 때만 노출(게스트는 삭제할 서버 계정 자체가 없음). **`BuildConfig.DEBUG` 게이팅을 의도적으로 하지 않았다** — 다른 "개발자 테스트" 섹션과 달리, 이 기능은 실사용자가 릴리스 빌드에서 항상 쓸 수 있어야 하는 정책 요구사항이기 때문. 삭제 전 `AlertDialog`로 "영구 삭제, 되돌릴 수 없음" 확인을 받고(구매 프리미엄은 Play Billing이 Google 계정에 귀속되므로 이 삭제로 사라지지 않는다는 점도 명시), 삭제 성공 시 `authState`를 다시 읽어와(자동으로 게스트 상태로 갱신됨) UI가 즉시 로그인 버튼들을 다시 보여주도록 했다 — 별도 네비게이션/화면 전환 없이 같은 화면에서 자연스럽게 반영된다.
- **범위에서 제외한 것**: Firestore 등 클라우드 DB에 저장된 사용자 데이터 삭제는 다루지 않는다 — Step 4(Firestore 동기화)가 아직 보류 상태라 애초에 클라우드에 저장되는 사용자 데이터가 없다(프리미엄 상태/기기 식별자 모두 기기 로컬 SharedPreferences). 나중에 Step 4가 진행되면 그때 이 삭제 경로도 함께 확장해야 한다.
- **검증**: `make test` 통과. 에뮬레이터(`emulator-5554`)에서 실제로 테스트 이메일 계정을 새로 만들고 → 설정 화면에 "계정 삭제" 버튼이 로그인 후에만 나타나는 것 확인 → 확인 다이얼로그 → 삭제 확정 → "계정이 삭제되었습니다" 토스트 + 화면이 즉시 게스트 상태로 전환(로그인 버튼 재노출, 삭제 버튼 소멸)까지 end-to-end로 실측했다. 프리미엄 구매 완료 토글이 삭제 후에도 유지되는 것도 함께 확인(설계대로 Firebase 계정과 프리미엄 상태가 독립적임을 실증).

### 결정 번복: 이번 출시에서 로그인 기능 전체를 끄기로 결정 (2026-08-09)

- **배경**: 계정 삭제 기능(위 절)을 구현하던 중, 사용자가 "계정 로그인이 꼭 필요한가"라는 질문을 던졌다. 검토 결과: (1) 이 앱의 크로스 디바이스 요구는 사실상 "프리미엄 구매가 재설치 후에도 유지되는 것" 하나뿐이고, 이는 이미 Play Billing만으로 완전히 해결돼 있다(2026-08-09 실기 검증, 로그인 여부와 무관하게 동작) — 1장의 "핵심 논의"에서 애초에 이렇게 설계했던 그대로다. (2) Firestore 동기화(Step 4, 계정에 진짜로 뭔가를 귀속시키는 유일한 기능)는 여전히 미착수라, 지금 Google/이메일 로그인은 상태 문구를 바꾸는 것 외엔 실질 기능이 없다. (3) 반면 계정 생성을 지원한다는 사실 자체가 Google Play Data Safety 공개 항목(이메일/사용자 ID 수집)과 인앱 계정 삭제 의무를 계속 발생시킨다. **실질 기능 0 대비 정책/UX 비용만 있는 상태**라고 결론 내려, 이번 출시에서는 로그인 기능 자체를 끄기로 결정했다.
- **구현 방식**: 기능을 삭제하지 않고 **컴파일타임 스위치**로 껐다 — `ui/FeatureFlags.kt` 신규, `internal object FeatureFlags { const val isLoginEnabled = false }`. 이 값을 `true`로 되돌리고 다시 빌드하면 온보딩/설정 화면의 기존 로그인 UI가 코드 변경 없이 그대로 되살아난다(로그인 관련 포트/어댑터/글루/UI 코드는 전부 그대로 남겨둠 — 이번 절 이전의 Step 1~3 산출물 전부 유효).
- **`initialDestination()` 함수 신설**(`FeatureFlags.kt`): 로그인이 꺼져 있으면 온보딩 화면 자체를 건너뛰고 항상 홈으로 직행한다. 이때 온보딩의 "계정 없이 시작하기"가 하던 `DeviceIdentityStorePort.loadOrCreate()`(게스트 ID 생성) 호출을 이 함수 안에서 대신 수행해, 온보딩 화면을 한 번도 안 띄워도 프리미엄 구매 복원 등 게스트 기반 기능이 그대로 동작하게 했다 — `loadOrCreate()`는 이미 있으면 그대로 반환하는 멱등 호출이라 매 실행마다 불러도 안전하다. `GoCoachApp.kt`가 라인/상태 훅 예산이 정확히 꽉 찬 상태(849/849, 47/47)라, 이 판단 로직을 별도 파일로 빼서 `GoCoachApp.kt`는 기존 `remember { mutableStateOf(...) }` 초기값 계산식 한 줄만 함수 호출로 교체했다(순 라인 변화 0).
- **`SettingsScreen.kt`**: "계정" 섹션(제목/상태 문구/Google·Apple·이메일 버튼/계정 삭제 버튼)을 전부 `if (FeatureFlags.isLoginEnabled)`로 감쌌다 — 로그인 수단이 하나도 없는데 "로그인하면 다른 기기에서도 이어볼 수 있어요" 안내만 남으면 존재하지 않는 기능을 홍보하는 셈이라 통째로 숨긴다.
- **부수 결정 — Firebase Analytics 의존성 완전 제거**: 로그인을 재검토하며 데이터 수집 전반을 다시 훑어본 결과 `firebase-analytics` SDK가 포함돼 있으나 `logEvent()` 등 실제 호출이 코드 어디에도 없다는 것을 확인했다(grep으로 재확인) — 자동 수집만 계속되는 죽은 의존성이었다. 껐다 켰다 할 로직/UI가 없는 순수 수집 SDK라 플래그로 끄기보다 `app-android/build.gradle.kts`에서 의존성 자체를 제거했다. `premium-mode/README.md` 문서의 Data Safety 관련 향후 참고사항: 이 변경 이후 남는 데이터 수집원은 AdMob(광고 ID)과 Play Billing(구매 내역)뿐이다.
- **검증**: `make test` 통과(`LayeringContractTest`의 `GoCoachApp.kt` 849/849줄·47/47훅 예산 검사 포함, Firebase Analytics 제거로 인한 컴파일 에러 없음). 에뮬레이터에서 앱 데이터 완전 초기화 후 재실행 — 온보딩 화면 자체가 뜨지 않고 곧바로 홈 진입, 설정 화면에 "계정" 섹션이 전혀 없음, 그리고 **로그인을 한 번도 하지 않은 완전 게스트 상태에서도 대국 설정 화면에 프리미엄 👑∞가 정상 표시**(Play Billing 복원이 로그인과 완전히 무관하게 동작)됨을 실측 확인했다.

이 문서는 각 단계 착수/완료 시점마다 위 마일스톤 표의 상태와 관련 섹션을 갱신하며, 완료된 단계도 지우지 않고 이력으로 남깁니다.
