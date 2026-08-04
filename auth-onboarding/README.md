# 최초 실행 온보딩 + 계정 시스템 도입 마스터 플랜

작성일: 2026-07-29

본 문서는 **go-ai-coach** 앱에 "최초 실행 시 한 번만 뜨는 온보딩 화면"과 Firebase 기반 계정 시스템(Google/이메일/익명 로그인)을 도입하기 위한 작업 요약 및 단계별 개발 플랜입니다. `premium-mode/README.md`와 동일한 방식으로, 구현이 진행되며 계속 갱신되는 히스토리 문서로 유지합니다.

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
- 그래서 채택한 구조: 첫 실행 시 로그인을 강제하지 않고 **Firebase 익명(Anonymous) Auth로 조용히 시작** → Google/이메일 로그인은 "다른 기기에서 이어보기" 가치로 선택 제안 → 나중에 로그인하면 `linkWithCredential`로 기존 익명 UID의 데이터를 유지한 채 승격. 아이템 구매는 Play Billing 복원을 1차 안전망으로 삼고, Firestore 쪽 영구 엔타이틀먼트는 로그인한 사용자에 한해 추가 보장한다.

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
- 관련: `docs/refactoring/PLAY_FLOW_UX_REFACTORING_PLAN_260804_0553.md`.

### Step 1 재개정 — 온보딩을 다시 "얕은 허들" 로그인 화면으로 (2026-08-04)
- **배경**: 위 개정에서 온보딩을 완전히 건너뛰는 "시작하기" 단일 버튼으로 단순화했으나, 사용자 피드백으로 "설치 후 첫 화면은 로그인 화면이어야 하고, 그냥 패스시키는 지금 화면은 의미가 없다"는 방향이 확인됐다 — 첫 실행 시 로그인 여부를 가볍게라도 물어보는 게 맞다는 결론.
- **변경**: `OnboardingScreen.kt`를 Google/Apple/이메일/게스트("계정 없이 시작하기") 4버튼 구성으로 되돌렸다. Google/Apple/이메일은 여전히 "준비 중" 스텁이고, 게스트 버튼만 실제로 동작한다 — 단, 반복 노출 버그는 그대로 고쳐진 상태를 유지한다(게스트 완료 조건은 여전히 `DeviceIdentityStorePort.loadOrCreate()`, 즉시 성공).
- **Apple 버튼 신규 추가** — 위 2.1절의 "완전 후순위" 결정을 일부 뒤집는다. iOS 대응 시 Apple 로그인 제공이 App Store 정책상 사실상 필수(Google 등 제3자 로그인을 제공하면 Apple 로그인도 함께 제공해야 함)에 가깝다는 점을 고려해, UI 스텁만 지금 추가했다. 실제 연동은 여전히 후순위(Step 2/3과 동일한 성격의 별도 Step으로 착수 시 결정).
- **게스트 선택 시 안내**: "설정에서 로그인 연동 가능"이라는 별도 확인 팝업(모달)은 추가하지 않기로 했다 — 대신 홈 화면 진입 직후 가벼운 토스트("게스트로 시작했습니다. 설정에서 로그인할 수 있어요")를 1회만 띄운다. 얕은 허들이라는 취지를 유지하면서(추가 결정 요구 없음), 설정 진입점의 존재는 알리기 위함.
- `SettingsScreen.kt`도 동일하게 Google/Apple/이메일 3버튼으로 맞춰, 온보딩에서 게스트를 고른 사용자가 나중에 같은 선택지로 돌아올 수 있게 했다.

### Step 2 — Google 로그인 실제 연동
- **목적**: Step 1의 "준비 중" 스텁을 Credential Manager/One Tap 기반 실제 Google 로그인으로 교체.
- **범위**: SHA-1 인증서 지문을 Firebase 콘솔에 등록(이번 Step에서 처음 필요해짐), `AuthClientPort`에 `signInWithGoogle(...)` 메서드 추가, 익명 사용자가 로그인할 경우 `linkWithCredential`로 UID 승격(데이터 유실 없이).
- **상태**: 대기

### Step 3 — 이메일 로그인 실제 연동
- **목적**: Firebase Email/Password 또는 Email Link 로그인 연동.
- **상태**: 대기

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
3. ⬜ **Authentication → Sign-in method → 익명(Anonymous)** 활성화 (Step 1에 필요, SHA-1 불필요) — 아직 확인 안 됨, 활성화 전에는 `signInAnonymously()`가 계속 실패하지만 fire-and-forget이라 앱 사용에는 지장 없음.
4. ✅ `google-services.json` 다운로드 → `app-android/google-services.json`에 저장 (`.gitignore`에 등록되어 있어 커밋되지 않음).
5. ⬜ (Step 2 착수 시) Google 로그인용 SHA-1 인증서 지문 등록.

### Step 1 개정 2 — 실제 Firebase 프로젝트 연결 (2026-08-04)
- google-services.json을 받아 `app-android/google-services.json`에 배치하고, 루트 `build.gradle.kts`(`google-services` 플러그인 버전 4.4.4 → 4.5.0), `gradle/libs.versions.toml`(`firebaseBom` 34.16.0 → 34.17.0, `firebase-analytics` 라이브러리 항목 추가), `app-android/build.gradle.kts`(`implementation(libs.firebase.analytics)`)를 갱신했다.
- **패키지명 불일치 발견 및 해결**: 받은 google-services.json의 `package_name`이 `com.zenit9hub.ai.baduk`였는데 당시 앱의 `applicationId`는 `com.worksoc.goaicoach`였다 — 사용자에게 확인 후 `applicationId`를 `com.zenit9hub.ai.baduk`로 변경(2번 참고). `:app-android:processDebugGoogleServices` 태스크가 정상 통과함을 `make test`로 확인.
- `namespace`는 변경하지 않았다 — Kotlin 소스의 `package com.worksoc.goaicoach.*` 선언, `R`/`BuildConfig` 참조 등은 전혀 건드릴 필요가 없었다(namespace와 applicationId는 독립적인 값).
- **부수 버그 발견 및 수정**: applicationId 변경 직후 "이전 버전은 엔진이 되는데 새 패키지명 앱은 스텁으로 동작한다"는 리포트를 받음 — 원인은 `.so` 실행 파일이 아니라(APK에 번들되어 있어 무관), KataGo 모델/설정 파일(`model.bin.gz`, `gtp_learning.cfg`, `analysis_learning.cfg`)을 개발용으로 로컬 Mac에서 `adb shell run-as <package> cp ...`로 주입하는 `scripts/seed-katago-model-to-app.sh`와 `Makefile`(`reinstall-dev-engine`/`launch`)이 옛 패키지명 `com.worksoc.goaicoach`를 하드코딩하고 있던 것 — 새 패키지(`com.zenit9hub.ai.baduk`)는 앱 전용 저장소가 완전히 별개라(Android는 applicationId 단위로 파일 격리) 이 파일들이 하나도 주입되지 않은 상태였다. `EngineBootstrap.kt`가 이 파일들의 존재 여부만으로 실제 엔진(`LocalProcess`)/스텁(`Stub`)을 조용히 가른다(예외 처리 없이 단순 파일 체크).
  - `scripts/seed-katago-model-to-app.sh`의 `PACKAGE` 기본값과 `Makefile`의 3곳(`reinstall-dev-engine`의 `adb uninstall`, `launch`의 `force-stop`/`am start`)을 `app-android/build.gradle.kts`에서 `grep`으로 직접 읽어오는 `APP_PACKAGE`/`APP_NAMESPACE` 변수로 교체 — 이제 applicationId가 또 바뀌어도 하드코딩이 조용히 어긋나지 않는다. `MainActivity`의 adb 컴포넌트명은 `<APP_PACKAGE>/<APP_NAMESPACE>.MainActivity`로 구성해야 한다는 점도 확인(activity 클래스명은 namespace 기준, 설치 패키지는 applicationId 기준으로 서로 다른 값을 쓴다).
  - 실제 폰(`R5CT22WTVXP`)에 `make seed-engine` 재실행 → `files/katago/`에 세 파일 생성 확인, `make launch` 재실행 → 새 KataGo 로그 파일 2개 생성 확인(엔진 정상 기동).

---

## 4. Step 1 구현 메모 (2026-07-29)

- `PremiumState`(`application/premium/PremiumState.kt`)와 동일한 스타일로 `application/auth/AuthState.kt` 설계 — Android/Compose import 0개, `data class AuthState(isSignedIn, provider, uid)` + `AuthProvider` enum(`Anonymous`/`Google`/`Email`).
- 실제 SDK 호출은 `AuthClientPort`(포트) + `ui/AndroidAuthClient.kt`(Firebase 구현)로 분리 — `signInAnonymously()`만 구현, Google/이메일 메서드는 그 기능을 실제로 붙일 때 추가(YAGNI).
- `google-services.json`이 없어도 `make test`/`make dev`가 깨지지 않도록, `app-android/build.gradle.kts`에서 `google-services` 플러그인을 `alias(...) apply false`로 등록만 해두고 `if (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }`로 조건부 적용.
- Firebase BOM 34.16.0(2026-07 기준 최신) 기준 `firebase-auth-ktx`가 더 이상 별도 아티팩트로 관리되지 않아(-ktx 확장이 본체에 통합됨), `firebase-auth`를 사용.
- `hasSeenOnboarding` 플래그는 새 저장소를 만들지 않고 기존 `UserPreferencesSnapshot`/`UserPreferencesStore`(SharedPreferences+JSON) 패턴에 필드 하나로 추가 — 스키마 버전은 올리지 않음(`optBoolean` 기본값으로 안전하게 하위 호환).
- `GoCoachApp.kt`의 `currentDestination` 초기값 계산식만 바꿔(`hasSeenOnboarding` 기준 `Onboarding`/`Home` 분기) 새 Compose 상태 훅을 추가하지 않았다 — `LayeringContractTest`의 `stateHookBudget`(47)이 거의 소진된 상태였기 때문. `AndroidAuthClient`도 내부 상태가 없는 얇은 래퍼라 `remember`로 캐시하지 않고 매 재구성마다 새로 생성해도 무해하다는 점을 이용해 훅 예산을 아꼈다.

이 문서는 각 단계 착수/완료 시점마다 위 마일스톤 표의 상태와 관련 섹션을 갱신하며, 완료된 단계도 지우지 않고 이력으로 남깁니다.
