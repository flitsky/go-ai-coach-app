# 프리미엄 모드 도입 마스터 플랜

작성일: 2026-07-28

본 문서는 **go-ai-coach** 앱에 "일반 모드 / 프리미엄 모드" 구분과 수익화(광고 + 인앱결제) 기반을 도입하기 위한 작업 요약 및 단계별 개발 플랜입니다. `UX_IMPROVEMENT.md`와 동일한 방식으로, 구현이 진행되며 계속 갱신되는 히스토리 문서로 유지합니다. "왜 이렇게 하기로 했는가"라는 상위 원칙은 `FEATURE_ACCESS_PRINCIPLES.md`에 별도로 정리되어 있습니다 — 이 문서는 그 원칙의 실행 로그입니다.

---

## 1. 개요 및 목표

### 1.1. 배경 및 필요성
- 지금까지 무료로 전면 개방되어 있던 AI 코칭 기능(분석/형세보기/추천수/무르기/착수평가)을 **프리미엄 전용**으로 분리해 향후 수익화 기반을 마련합니다.
- 인앱결제(영구 구매)만 강제하지 않고, **광고 시청을 통한 1회성 업그레이드 찬스**를 함께 제공해 무료 사용자도 프리미엄 기능을 체험하고 전환될 여지를 남깁니다.
- 실제 "판매"(과금 아이템 등록, 광고 SDK 연동)는 뒤로 미루고, **상태값 관리 인프라와 UX 플로우를 먼저 골격으로 구축**하는 것이 이번 계획의 핵심입니다.

### 1.2. 주요 목표 (사용자 제공 4단계)
1. **프리미엄 모드 상태값 관리** 인프라 추가 (판매 로직은 이후 단계에서 천천히 추가)
2. **대국 시작 버튼** 클릭 시 "프리미엄 기능 활성화(광고 시청)하시겠습니까?" 팝업 → "예" 선택 시 상태값 활성화 (이 단계에서는 실제 광고 노출 없이 상태만 즉시 켜는 **스텁**)
3. **광고 노출 기능** 추가 (Android/Google 우선)
4. **프리미엄 모드 영구 활성화 아이템** 추가 (Android/Google Play Billing 우선)

---

## 2. 모드별 기능 매트릭스

| 기능 | 일반 모드 | 프리미엄 모드 |
| --- | :---: | :---: |
| 기권 (Resign) | ✅ | ✅ |
| 통과 (Pass) | ✅ | ✅ |
| 무르기 (Undo) | ✅ | ✅ |
| 형세 보기 (Eval) | ❌ | ✅ |
| 추천 수 (Top Moves) | ❌ | ✅ |
| 착수 평가 (Move Review) | ❌ | ✅ |

일반 모드는 대국을 진행/종료하는 최소 기능(기권, 통과)과 무르기만 제공하고, 형세 보기/추천 수/착수 평가가 프리미엄 게이팅 대상입니다.

### 2026-08-13 정정 — "분석" 삭제, "무르기" 무료 전환
- **분석(Analyze) 행 제거**: 새 분석을 실행하지 않고 캐시된 원시 엔진 로그를 그대로 보여주는 디버그성 뷰어였음이 확인되어 버튼과 다이얼로그(`EngineResponsePanel.kt`)를 통째로 삭제했다. 같은 기능은 이미 형세 보기(보드 오버레이)·추천 수(후보수 마커)가 더 나은 방식으로 제공하고 있어 기능 손실은 없다.
- **무르기(Undo)를 일반 모드에서도 무료로 전환**: 게이팅 코드를 완전히 제거해 프리미엄 여부와 무관하게 항상 사용 가능하다.
- **초도 발행 프로모션**: 위 무르기 무료 전환은 "영구 무료"가 목표가 아니라, 초도 발행 시점의 **클레임 기반 프로모션 + 그랜드파더링**으로 다시 좁혀지는 것이었다. 구체 설계는 `GOOGLE_PLAY_LAUNCH_PLAN.md` 3장. → **구현 완료(커밋 `5fc7b49`, 2026-08-13)**: 무르기는 더 이상 상시 무료가 아니라 "클레임했거나 프리미엄이면 사용 가능"이다. 저장 형태는 260814에 `claimedFeatures: Set<FeatureId>` 원장으로 일반화됐고(`PremiumState`), 판정은 `FeatureAccessPolicy.resolve()` 하나로 모였다.
- **2026-08-24 추가**: 출석 1일차 보상이 이 `FeatureId.Undo` 클레임을 그대로 재사용한다 — 즉 무르기 해제 경로가 "인게임 클레임 다이얼로그"와 "출석 보상" 둘로 늘었다(`260823-260830_OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN.md` 4.2·4.4절).
- **위 매트릭스에 없는 네 번째 경로 — 소모품 1회권(2026-08-24 신설)**: 형세 보기/추천 수는 이제 프리미엄 토글 외에 **1회권을 차감해 단발성으로** 쓸 수도 있다(`application/consumable/`, 출석 보상으로 지급). 프리미엄·영구 클레임이 재고보다 우선하며, 판정은 `decideConsumableSpend`가 `FeatureAccessPolicy.resolve()`에 위임한다. 상세는 킥오프 플랜 4.5절.

## 3. 프리미엄 활성화 경로

| 경로 | 활성화 방식 | 지속성(확정, 6장 참고) | 담당 단계 |
| --- | --- | --- | --- |
| 영구 구매 | Google Play Billing 결제 | 영구 (앱 재설치 전까지 유지) | Step 4 |
| 광고 시청 | 대국 설정 화면의 프리미엄 카드 → 리워드 전면 광고 시청 | **순수 1시간 타이머** — 그 안에는 대국을 몇 판 새로 시작하든 계속 유효(2026-08-04 결정 번복, 아래 "결정 번복" 절) | Step 2~3 |
| 미활성 | 아무 것도 안 함 | 해당 없음 (일반 모드 유지) | — |

---

## 4. 단계별 구현 계획

### Step 1 — 프리미엄 상태값 관리 (상태만, 판매 로직 제외)
- **목적**: `PremiumState` 데이터 모델과 읽기/쓰기 배선을 먼저 깔아, Step 2~4가 이 위에 얹히도록 한다.
- **범위**:
  - 상태 값 모델 설계. 최소 다음 필드를 포함:
    - `isPremiumActive: Boolean`
    - 활성화 소스: `Purchase`(영구) / `AdGrant`(한시적) / `None`
    - `AdGrant`인 경우: **부여된 대국의 식별자(해당 판에만 유효 — 다른 판/새 대국엔 자동 미적용)** + **부여 시각(1시간 만료 계산용)**
  - 대국 진행 중 1시간 초과 시 자동 만료 처리 로직 (타이머 또는 매 상태 조회 시 시각 비교로 판정 — 구현 단계에서 결정)
  - 앱 어디서 이 상태를 읽어 기능 게이팅에 쓸지 배선 (5장 참고)
  - 영속화 여부 결정 — 기존 `UserPreferencesStore` 계열과 동일한 패턴을 따를지, 별도 저장소로 분리할지. `Purchase` 소스는 영구 저장, `AdGrant`는 대국 세션 상태(대국 종료/이탈 시 자동 소멸)에 가깝게 다뤄야 함.
  - **iOS 확장을 고려한 설계**: 상태 모델 자체와 게이팅 판정 로직은 Android 전용 API에 의존하지 않는 순수 로직으로 설계하고, Google Play Billing/AdMob처럼 플랫폼 종속적인 "활성화 소스 공급자"만 인터페이스 뒤로 분리한다. Android 우선 구현이지만, 추후 iOS에서 동일 인터페이스의 다른 구현체(App Store 결제 등)만 추가하면 되도록 한다.
- **산출물**: 상태 모델 + 게이팅에 필요한 최소 읽기 API. 이 시점엔 이 상태를 켜는 실제 UI/구매/광고는 없음(테스트/개발용으로만 값 조작 가능).
- **상태**: ✅ 완료 (2026-07-28) — 상세는 8장 마일스톤과 "Step 1+2 구현 메모" 참고

### Step 2 — 대국 시작 팝업 (스텁, 실제 광고 없음)
- **목적**: 사용자가 **홈 화면 "대국 하기" 버튼**을 누르는 순간 프리미엄 업셀 기회를 제공하는 UX 플로우를 먼저 완성한다.
- **범위**:
  - 트리거 위치는 대국 설정 로비가 아니라 **홈 화면 "대국 하기" 버튼**(`GoCoachHomeScreen.kt`의 `onStartMatchClick`, `GoCoachApp.kt` 호출부). 이미 이 버튼엔 "이전 대국 이어하기 시 덮어쓰기 경고" 다이얼로그가 붙어 있으므로(2026-07-28 세션에서 추가됨), **두 팝업의 등장 순서를 이 단계에서 정의**해야 한다(예: 덮어쓰기 경고 확인 후 프리미엄 팝업, 혹은 그 반대).
  - "프리미엄 기능 활성화(광고 시청)하시겠습니까?" 확인 팝업 신설
  - "예" 선택 → (실제 광고 없이) `PremiumState`를 이번 대국 한정으로 즉시 활성화
  - "아니오" 선택 → 일반 모드로 대국 진행 (그대로 대국 설정 로비/대국 시작 진행, 재차 권유하지 않음)
  - 이미 프리미엄(영구 구매 또는 이번 판 활성 중)인 사용자에게는 팝업을 건너뛰고 바로 진행
  - **인게임 업셀 진입점**: 일반 모드로 대국 중 프리미엄 전용 버튼(분석/형세보기/추천수/무르기/착수평가)은 비활성화 상태로 노출하고, 탭하면 동일한 활성화 팝업(또는 구매 유도를 포함한 변형)을 띄운다. 즉 업셀 팝업은 "홈 화면 대국 시작 시"와 "인게임 중 잠긴 버튼 탭 시" 두 지점에서 트리거된다.
- **산출물**: 클릭 가능한 전체 UX 플로우(홈 진입 팝업 + 인게임 잠금 버튼 업셀). 광고 SDK 의존성 없음.
- **상태**: ✅ 완료 (2026-07-28). 단 **팝업 트리거 위치는 2026-08-04에 홈 화면 → 대국 설정 화면으로 이동**했다(아래 "2026-08-04 개정" 절) — 위 범위 서술의 "홈 화면 대국 하기 버튼"은 더 이상 현행이 아니다

### Step 3 — 광고 노출 기능 (Google 우선)
- **목적**: Step 2의 스텁을 실제 리워드 광고로 교체한다.
- **범위**:
  - Google Mobile Ads(AdMob) SDK 연동, 리워드 광고 단위 등록
  - "예" 선택 → 광고 로드/시청 → 시청 완료 콜백에서만 `PremiumState` 활성화 (스텁과 달리 시청 완료가 활성화의 필요조건이 됨)
  - 광고 로드 실패/시청 중단 시 폴백 처리(일반 모드 유지 + 안내)
- **산출물**: 실제 리워드 광고 연동. iOS/타 광고 네트워크는 이번 범위 밖이지만, Step 1에서 분리한 "활성화 소스 공급자" 인터페이스를 그대로 따르는 형태로 구현해 후순위 iOS 대응 시 재설계 없이 구현체만 추가되도록 한다.
- **상태**: ✅ 완료 (2026-08-05) — 실제 AdMob 계정/광고단위(배너+보상형 전면) 연동, 빌드타입 기반 테스트/실광고 게이트 포함

### Step 4 — 프리미엄 모드 영구 활성화 아이템 (Google 우선)
- **목적**: 광고 없이 영구적으로 프리미엄을 활성화하는 결제 상품을 추가한다.
- **범위**:
  - Google Play Billing 연동, 소모성이 아닌 **비소모성(non-consumable) 상품** 1종 등록
  - 구매 완료/복원(재설치 등) 시 `PremiumState`를 영구 소스로 활성화
  - 광고 기반 한시적 활성화보다 **우선순위 높은 소스**로 취급 (구매자는 팝업 자체를 다시 보지 않음)
- **산출물**: 실제 결제 연동. iOS(App Store 결제)는 이번 범위 밖이지만, 마찬가지로 Step 1의 공급자 인터페이스를 따르도록 구현한다.
- **상태**: ✅ 완료 (2026-08-06 코드, 2026-08-09 Play Console 설정 + 실기 구매/복원 e2e 검증)

### 계층 배치 참고 (`ARCHITECTURE.md`의 7계층 기준, 2026-07-29 정리)

Step 1~2는 이미 구현됐고(`application/premium/PremiumState.kt`가 순수 포트, `ui/GoCoachApp.kt`가 App Service 오케스트레이션) 이 원칙 그대로다. Step 3~4를 실제로 구현할 때 새 코드가 어느 계층에 속하는지 미리 정리해, 착수 시점에 위치를 재논의하지 않도록 한다.

| Step | 작업 | 계층 | 근거 |
| --- | --- | --- | --- |
| Step 3 | AdMob 리워드 광고 SDK 호출 + 시청완료 콜백 판정 | **포트/원시 계층** (엔진의 2계층 `EngineCoreApi`에 대응) | "SDK를 그대로 노출"하는 얇은 경계. `AuthClientPort`와 같은 자리 — `application/premium`에 순수 인터페이스, 실제 AdMob SDK 구현체는 `ui/` 또는 전용 파일. |
| Step 3 | "광고 시청 완료 → `PremiumState.adGranted(...)` 활성화" 판단 | **App Service / Session Orchestration** (6계층) | 이미 존재하는 `activateForMatch` 람다와 동일한 성격 — UI 유스케이스 조합, 새 계층 아님. |
| Step 4 | Play Billing 구매 토큰 서버 검증(Play Developer API) + 결과 신뢰도/캐시 정책 | **Middleware / Cache Domain 성격** (4계층에 대응) | 원시 SDK 응답을 그대로 믿지 않고 서버 검증·캐시·신뢰도 등급을 조율한다는 점이 4계층의 `PositionAnalysisCacheResolver`와 같은 역할이다. 다만 물리적으로는 엔진의 `middleware/` 패키지와 합치지 않고 `application/premium/` 안에 별도 파일(예: `PurchaseVerificationGateway`)로 둔다 — 도메인이 다르므로 파일은 분리하되 "역할"만 같은 계층으로 분류한다. |
| Step 4 | 구매 완료/복원 시 `PremiumState`를 영구 소스로 반영, 팝업 재노출 억제 | **App Service / Session Orchestration** (6계층) | Step 3의 활성화 판단과 동일한 성격. |

---

## 5. 기능 게이팅 적용 예상 지점 (참고용 — 실제 구현은 각 단계 진행 시 확정)

| 파일 | 관련 내용 |
| --- | --- |
| `app-android/.../ui/GameActionButtons.kt` | 분석/형세보기(Eval)/추천수(TopMoves)/무르기(Undo) 버튼 — 일반 모드에서 비활성화 + 탭 시 업셀 팝업 트리거 |
| `app-android/.../ui/KaTrainUxPanels.kt` | "착수 평가" 토글 — 동일하게 일반 모드에서 비활성화 + 업셀 (2026-07-28에 신설된 토글) |
| `app-android/.../ui/GoCoachHomeScreen.kt`, `GoCoachApp.kt` | **홈 화면 "대국 하기" 버튼**(`onStartMatchClick`)에 Step 2 팝업 훅 삽입 위치. 기존 "이전 대국 덮어쓰기 경고" 다이얼로그와의 등장 순서 정의 필요 |
| `app-android/.../persistence/UserPreferencesStore.kt` 계열 | `PremiumState`의 영구 구매 소스 영속화 시 참고할 기존 JSON 코덱 패턴 (광고 기반 한시적 소스는 세션/대국 상태로 별도 취급) |

---

## 6. 확정된 결정사항 (2026-07-28)

아래 6가지는 문서 초안 검토 후 확정된 사항입니다.

1. **광고 시청으로 얻는 프리미엄의 지속 범위**: ~~해당 **대국 1판 한정**. 다른 판/새 대국에는 자동 적용되지 않는다.~~ → **2026-08-04 결정 번복**: 순수 **1시간 타이머**로 변경. 그 1시간 안에는 대국을 몇 판을 새로 시작하든(무르기/새 대국 포함) 계속 유효하다 — "1판 한정"이라는 매치 스코프 제약 자체를 제거함. 아래 "결정 번복" 절 참고.
2. **팝업에서 "아니오" 선택 시**: 그대로 일반 모드로 대국 진행. 재차 권유하지 않는다.
3. **팝업 트리거 위치**: **홈 화면 "대국 하기" 버튼**을 눌렀을 때 뜬다 (대국 설정 로비의 "대국 시작하기"가 아님). 5장의 게이팅 지점 표에 반영함. → **2026-08-04 개정으로 이 결정은 변경됨**: 홈 화면에서는 더 이상 팝업을 띄우지 않고 바로 대국 설정 화면으로 이동하며, 대신 그 화면 안에 프리미엄 모드 카드를 상시 노출해 사용자가 원할 때 직접 연다. 아래 "2026-08-04 개정" 절 참고.
4. **만료 규칙**: ~~한시적 활성화는 "해당 판 1회" + "최대 1시간" 두 조건 중 먼저 도달하는 쪽에서 종료된다.~~ → **2026-08-04 결정 번복으로 "해당 판 1회" 조건이 삭제됐다** (1번 항목과 아래 "결정 번복" 절 참고). 지금은 **부여 시각으로부터 1시간**이 유일한 만료 조건이다(`PremiumState.AdGrantDurationMillis`). 만료 후 버튼 잠금 방식은 5번 결정사항과 동일하게 비활성화 처리.
5. **일반 모드에서 프리미엄 전용 버튼의 UX**: **비활성화 상태로 노출하고, 탭하면 업셀 팝업**을 띄운다 (완전히 숨기지 않음). 대국 도중 만료된 경우도 동일하게 적용.
6. **iOS 대응 시점**: 전 단계 **후순위/별도 과제**로 기록한다. 다만 **호환성 및 추후 iOS 출시를 고려해 설계**해야 하므로, Step 1의 상태 모델과 게이팅 로직은 플랫폼 비종속으로 설계하고 Google 관련 구현(광고/결제)만 인터페이스 뒤로 분리한다 (Step 1/3/4에 반영함).

---

## 7. 리스크 및 유의사항

- **스토어 정책 준수**: Google Play의 리워드 광고 UX 가이드라인(광고 시청 전 명확한 보상 고지 등), 결제 상품 등록/환불 정책을 확인해야 합니다.
- **결제 검증**: 실 결제 연동 전 Google Play 테스트 트랙/라이선스 테스터 계정으로 검증이 필요합니다.
- **SDK 도입 영향**: Google Mobile Ads SDK 추가 시 APK 크기, 초기 기동 시간에 영향이 있을 수 있어 Step 3에서 별도 확인이 필요합니다. → **확인 완료(2026-08-05)**: 디버그 APK 기준 19.00MB → 21.04MB(+2.92MB, +16%). `MobileAds.initialize()`를 앱 기동이 아니라 광고를 처음 요청하는 시점에 지연 호출하도록 설계해 콜드 스타트 경로 자체에는 영향이 없다. 자세한 내용은 아래 "Step 3 구현" 절 참고.
- **기존 기능 회귀 방지**: 5장의 게이팅 대상 버튼들은 현재 이미 동작 중인 핵심 코칭 기능이므로, 일반 모드 진입 시 이 버튼들을 잠그는 로직이 기존 사용자 플로우(특히 이미 저장된 대국 이어하기 등)를 깨지 않도록 Step 1~2에서 회귀 테스트가 필요합니다.
- **1시간 만료 판정 테스트**: 실제로 1시간을 기다려 검증하기 어려우므로, 만료 기준 시각을 주입 가능하게(테스트용 clock 추상화 등) 설계해 짧은 시간으로도 자동 만료 로직을 검증할 수 있어야 합니다.

---

## 8. 마일스톤

| 단계 | 작업 내용 | 상태 |
| --- | --- | --- |
| Step 1 | 프리미엄 상태값 관리 인프라 | ✅ 완료 (2026-07-28) — `application/premium/PremiumState.kt` + 단위 테스트 |
| Step 2 | 대국 시작 팝업 (스텁, 광고 없음) | ✅ 완료 (2026-07-28) — 홈 화면 팝업 + 인게임 잠긴 버튼 업셀까지 포함, `make test` 통과 |
| Step 3 | 광고 노출 기능 연동 (Google) | ✅ 완료 (2026-08-05) — AdMob 리워드 광고 실연동(테스트 광고 단위), `make test` 통과, 에뮬레이터 실측 |
| Step 4 | 프리미엄 영구 활성화 아이템 (Google) | ✅ 완료 (2026-08-06 코드, 2026-08-09 Play Console 설정 + 실기 검증까지 전부 완료) — Google Play Billing 9.1.0 실연동. Play Console에 비소모성 상품 `premium_lifetime`("프리미엄 영구 해제", ₩9,900) 등록·활성화, 라이선스 테스터 등록, 내부 테스트 트랙 AAB 업로드까지 모두 완료된 상태를 2026-08-09에 재확인했고, 같은 날 라이선스 테스터 계정으로 (a) 실제 구매 완료 (b) 재설치(로그인 없는 게스트) 후 자동 복원 두 플로우 모두 실기(에뮬레이터)에서 end-to-end 확인. 아래 "2026-08-09 확인" 절 참고 |

### Step 1+2 구현 메모 (2026-07-28)
- `LocalPremiumUiState`(CompositionLocal, `LocalUiStrings`와 동일 패턴)로 화면 트리 전역에 `isActive`/`activateForMatch`를 공급 — 각 게이팅 지점에 별도 파라미터를 추가하지 않고 `LocalPremiumUiState.current`만 읽으면 되도록 함.
- 게이팅 대상(분석/형세보기/추천수/무르기/착수평가)은 프리미엄 비활성 시 반투명(alpha 0.5)으로 표시하고, 탭하면 실제 동작 대신 공용 `PremiumUpsellDialog`를 띄움 (기권/통과는 게이팅 없음).
- `sessionGeneration`(기존 `GameSessionRuntimeState` 필드)을 대국 식별자로 재사용해 "해당 판 1회" 조건을 구현.
- 아직 구매/광고 SDK가 없으므로 홈 팝업 "예"는 광고 시청 없이 즉시 `PremiumState.adGranted(...)`를 부여하는 스텁 상태.

### 버그 수정: 홈 화면 활성화가 인게임에 반영되지 않던 문제 (2026-07-28)
- **증상**: 홈 화면 "대국 하기" 팝업에서 프리미엄을 활성화해도, 실제 대국 화면에 진입하면 프리미엄 버튼들이 계속 비활성 상태로 보임.
- **원인**: `activateForMatch()`가 팝업 시점(아직 대국 시작 전)의 `sessionGeneration`을 즉시 캡처했는데, 실제 대국은 `GameSetupLobby`의 "대국 시작하기" 버튼이 `GameUiEvent.StartConfiguredGame`을 디스패치할 때 비로소 `sessionGeneration`이 증가하며 시작됨 — 그 결과 홈에서 부여받은 프리미엄이 다른(예전) 세션 번호에 묶여, 정작 시작된 대국에서는 즉시 무효 판정되고 있었음.
- **수정**: `PremiumState.adGranted(sessionGeneration: Long?, ...)`가 세션을 `null`(미확정)로도 받을 수 있게 하고, `bindToSessionIfPending(sessionGeneration)`으로 나중에 확정하는 2단계 바인딩을 도입. `activateForMatch`는 이미 인게임(잠긴 버튼에서 활성화한 경우)이면 현재 세션에 즉시 묶고, 아직 홈 화면(대국 시작 전)이면 세션을 `null`로 남겨둠. `GoCoachApp.kt`에 추가한 `LaunchedEffect(runtimeState.sessionGeneration) { premiumState = premiumState.bindToSessionIfPending(...) }`가 실제 대국이 시작되어 세션이 배정되는 순간 그 번호로 확정.
- **부수 변경**: 이 수정으로 `GoCoachApp.kt`의 Compose 상태 훅 개수가 `LayeringContractTest`의 `stateHookBudget`(47) 한도를 넘어서게 되어, 기존의 30초 주기 tick(`premiumClockTickMillis` + 전용 `LaunchedEffect`)을 제거하고 `isActive` 판정 시각을 매 재구성 시점의 `System.currentTimeMillis()`로 직접 평가하도록 변경. 대국 중에는 착수/AI 응답 등으로 재구성이 충분히 자주 일어나 1시간 만료 판정에 실질적 지장은 없으나, 전용 타이머 대비 "정확히 몇 초 이내 재평가"라는 보장은 없어짐 — 트레이드오프로 남겨둠.

### 2026-08-04 개정: 업셀 팝업 트리거 위치를 홈 화면 → 대국 설정 화면으로 이동
- **배경**: 홈 화면에서 "대국 하기"를 누르자마자(대국 설정을 보기도 전에) 프리미엄 업셀 팝업이 뜨는 것이 플레이 흐름을 리뷰하는 과정에서 페인포인트로 확인됨 — 사용자가 아직 이 대국을 어떻게 설정할지 보지도 못한 시점에 결제/광고 선택을 강요받는 구조였다.
- **변경**: `GoCoachHomeScreen.kt`의 "대국 하기" 클릭은 이제 프리미엄 상태와 무관하게 곧바로 `GameSetupLobby`로 이동한다(팝업 완전 제거). 대신 `GameSetupLobby.kt`에 프리미엄 모드 카드를 상시 배치 — 비활성 상태면 탭했을 때 기존 `PremiumUpsellDialogHost`(광고 시청/구매/닫기 3선택)를 그대로 재사용해서 연다.
- **유지된 것**: 인게임 잠긴 버튼(분석/형세보기/추천수/무르기) 탭 시 업셀 팝업이 뜨는 폴백은 코드 변경 없이 그대로 유지 — 대국 설정에서 활성화를 깜빡하고 넘어간 사용자를 위한 안전망 역할을 계속 한다.
- 관련: 당시 Play Flow UX 리팩토링 계획서(2026-08-17 문서 정리로 저장소에서 제거, `DOCS_INDEX.md` "문서 보존 정책" 참고).

### 2026-08-04 추가 개정: 프리미엄 카드 디자인 고급화 + 하단 고정 위치로 이동
- **배경**: 위 개정에서 추가한 프리미엄 카드가 대국 설정 화면의 다른 일반 설정 항목들과 시각적으로 구분되지 않고, 스크롤 중간에 묻혀 눈에 잘 띄지 않는다는 피드백을 받았다.
- **색상 상수화**: `ui/PremiumTheme.kt` 신규 — `PremiumGold`/`PremiumGoldLight`/`PremiumGoldDeep`/`PremiumGoldGradient`/`PremiumCardShape`/`PremiumLockedBorder`를 정의. 프리미엄 관련 색/보더/그라디언트는 이 파일만 참조하도록 통일(하드코딩 금지).
- **카드 디자인**: 비활성 시 옅은 금색 배경 + 금색 그라디언트 보더, 활성 시 진한 금색 그라디언트 풀 배경으로 전환 — 일반 `MaterialTheme.colorScheme` 톤과 확실히 구분되는 "프리미엄" 정체성을 준다. 👑 이모지 + 굵은 타이틀로 강조.
- **위치 이동**: 스크롤 영역(보드 프리뷰 아래)이 아니라, 화면 하단 고정 바 — "대국 시작하기" 버튼 바로 위 — 로 옮겼다. 스크롤 여부와 무관하게 항상 보이고, "시작하기 직전 마지막 업셀"이라는 자리를 준다(체크아웃 직전 업셀 패턴과 동일한 의도).
- **인게임 버튼도 동일 아이덴티티 적용**: `GameActionButtons.kt`의 `ActionButton`/`SingleActionButton`/`ToggleActionButton`에 `premiumLocked: Boolean` 파라미터를 추가해, 잠긴 프리미엄 버튼(분석/형세보기/추천수/무르기)의 테두리를 `PremiumLockedBorder`(금색)로 바꿔 "프리미엄 전용 버튼"이라는 인식을 준다. 기존 흐림(alpha 0.5) 처리는 유지 — 테두리 색이 더해져 "그냥 비활성화"가 아니라 "프리미엄이라 잠겨 있다"는 의미가 더 명확해졌다.

### 2026-08-04 개발자 테스트 인프라 추가 — 구매 완료 상태 수동 주입 + 실구매 케이스 대비
- **배경**: 실 결제(Step 4) 전까지 "이미 프리미엄을 구매한 유저" 경로를 테스트할 방법이 없었다 — 업셀 팝업의 "영구 활성화(결제)" 선택지도 그동안 `notImplementedMessage` 토스트만 띄우는 순수 스텁이었다.
- **`PremiumUiState`에 `isPurchased`/`setPurchased` 추가**: `isPurchased`는 `PremiumState.source == Purchase`만 별도로 판정(설정 화면 토글 표시용), `setPurchased(Boolean)`은 `PremiumState.purchased()`/`PremiumState()`로 전역 상태를 즉시 전환하는 스텁이다. 실제 Play Billing 연동 시(Step 4) 구매 완료 콜백에서 이 함수 하나만 호출하면 되도록 미리 자리를 만들어 둠.
- **업셀 팝업 "구매하기" 버튼을 실제로 연동**: 더 이상 토스트만 띄우지 않고 `premium.setPurchased(true)`를 호출해 즉시 영구 활성화로 전환한다(여전히 실 결제는 없는 스텁).
- **`SettingsScreen.kt`에 "개발자 테스트" 섹션 추가**: `premium.isPurchased`를 보여주고 `setPurchased`로 켜고 끄는 `Switch` 하나 — 업셀 팝업의 "구매하기"와 완전히 같은 상태를 공유하므로 어느 쪽에서 바꾸든 서로 반영된다. **`BuildConfig.DEBUG`로 전체 섹션을 게이팅** — 릴리스 빌드에 노출되면 사용자가 무료로 프리미엄을 얻을 수 있는 실질적 보안/수익 문제가 되므로 반드시 유지해야 하는 가드다.
- **`GameSetupLobby.kt` 반영**: `premium.isActive`(영구 구매 또는 만료 전 광고 부여 — 기존 `PremiumState.isActive` 판정 그대로 재사용, 이번에 새로 만들지 않음)가 true면 프리미엄 카드 자체를 렌더링하지 않고, "대국 시작하기" 버튼을 금색(`PremiumGold`) + 👑 접두어로 표현한다. 즉 "영구 활성화 상태"와 "타임스탬프 기반(광고 1시간) 활성화 상태" 두 값 중 하나라도 유효하면 동일하게 이 분기를 탄다 — 두 소스를 구분해서 다르게 보여줄 필요는 없다는 판단(둘 다 "지금 프리미엄 기능을 쓸 수 있다"는 사실은 같음).
- 관련: 당시 Play Flow UX 리팩토링 계획서(2026-08-17 문서 정리로 저장소에서 제거, `DOCS_INDEX.md` "문서 보존 정책" 참고).

### 버그 수정: 홈 복귀만 해도 광고 시청 프리미엄이 조기 만료되던 문제 (2026-08-04)
- **증상**: 광고 시청으로 활성화하고 대국을 시작한 뒤, 그 대국을 마치고 나면(대국 자체를
  새로 시작하지 않았는데도) 1시간이 한참 남았는데도 프리미엄이 비활성으로 보임.
- **원인**: `GoCoachApp.kt`의 `exitToHome()`(대국 종료 후 뒤로가기/기권 후 뒤로가기/대국
  설정 화면 뒤로가기 3곳에서 공용으로 호출됨)이 다음 대국 미리보기 보드를 새로 그리려고
  `applyGameSetupPreview(...)`를 호출하는데, 이 함수가 내부적으로 "새 대국 시작"과 똑같은
  `applyGameSessionResetPlan(...)`을 재사용하면서 `matchGeneration`까지 함께 올리고
  있었다 — 프리미엄 광고 시청 활성화는 특정 `matchGeneration`에 묶이는 구조라, 실제로
  새 대국을 시작하지 않았는데도 이 값이 올라가는 순간 곧바로 무효 판정됐다.
- **수정**: `GameSessionCoreState.applyGameSessionResetPlan`에 `advanceMatchGeneration`
  파라미터를 추가해, "미리보기만 갱신"하는 경로와 "실제로 새 대국을 시작"하는 경로를
  구분했다. "대국 1판 한정" 정책 자체는 그대로 유지 — 실제로 새 대국을 시작할 때만
  `matchGeneration`이 올라간다.
- ⚠️ **바로 아래 절이 같은 날 이 "1판 한정" 정책 자체를 폐기한다** — 이 항목만 읽고 매치 스코프가
  살아 있다고 오해하지 말 것. 현행 동작은 순수 1시간 타이머다.

### 결정 번복: "대국 1판 한정" 정책을 순수 1시간 타이머로 변경 (2026-08-04)
- **배경**: 위 버그를 수정한 뒤에도 사용자가 원한 동작은 "새 대국을 시작하더라도 1시간
  카운트가 남아 있으면 다음 대국도 프리미엄으로 동작해야 한다"는 것이었다 — 즉
  2026-07-28에 확정했던 결정사항 #1("해당 대국 1판 한정")을 정면으로 뒤집는 요청.
  매치 단위 스코프를 유지하는 한 이런 요구를 만족할 방법이 없어(대국을 새로 시작하는
  순간 항상 무효화됨), "1판 한정"이라는 정책 자체를 없애기로 했다.
- **변경**: `PremiumState`에서 `adGrantMatchGeneration` 필드와 `bindToMatchIfPending(...)`을
  완전히 제거. `isActive(nowMillis)`가 매치 정보 없이 순수하게 `adGrantStartedAtMillis`
  기준 1시간 경과 여부만으로 판정한다. `GoCoachApp.kt`의 "매치 배정 시 pending 상태를
  확정 바인딩"하던 `LaunchedEffect`도 통째로 불필요해져 제거됨(그 결과 상태 훅 예산도
  줄어 여유가 생겼다). `PremiumUiState.activateForMatch` → `activateAdGrant`로 리네이밍
  (더 이상 "특정 매치용"이 아니므로). `PremiumStateStore`의 JSON 코덱에서도
  `adGrantMatchGeneration` 필드 제거.
- **유지된 것**: 1시간 만료 자체는 그대로 — 영구 구매와의 구분(대국 시작하기 버튼의
  뱃지: 카운트다운 vs ∞)도 변경 없음. `GameSessionRuntimeState.matchGeneration` 자체는
  삭제하지 않고 남겨둠 — 프리미엄 게이팅에서는 더 이상 쓰지 않지만, 세션 리셋 시점을
  구분하는 일반적인 부기(bookkeeping) 값으로는 여전히 유효하고, 직전 커밋에서 막
  고친 "홈 복귀 시 불필요하게 증가하던" 버그와도 무관하게 존재 가치가 있다고 판단.
- `PremiumStateTest.kt`/`PremiumStateStoreTest.kt`를 새 API에 맞게 갱신(매치 관련
  테스트 제거, "여러 판에 걸쳐 유지된다"는 테스트 추가).

### Step 3 구현 — AdMob 리워드 광고 실제 연동 (2026-08-05)
- **배경**: `ui/PremiumUiState.kt`의 `activateAdGrant`가 광고 없이 탭 즉시 `PremiumState.adGranted(...)`를 부여하던 스텁을, 실제 AdMob 리워드 광고 시청 완료 콜백 안에서만 활성화하도록 교체했다. 같은 날 먼저 진행된 Google/이메일 로그인 실연동(`LOGIN_AND_ACCOUNT_SYSTEM.md` Step 2/3)의 포트/어댑터 분리 방식과 작업 흐름을 그대로 따랐다.
- **계층 배치(4장 표 그대로 적용)**: `application/premium/AdRewardPort.kt`(순수 인터페이스, `AdRewardOutcome`/`AdRewardFailureReason` 포함)가 포트, `ui/AndroidRewardedAdClient.kt`가 실제 Google Mobile Ads SDK 어댑터다. `AuthClientPort`/`AndroidAuthClient`와 완전히 같은 자리 — `LayeringContractTest.authPremiumAndDeviceApplicationPackagesStayPlatformFree`가 `application/premium`에 android/ui/persistence import를 금지하므로, 포트 메서드 시그니처(`suspend fun showRewardedAd(): AdRewardOutcome`)에는 `Activity`를 노출하지 않고 대신 어댑터 생성자가 `Activity`/광고단위 ID를 받는다 — `AndroidAuthClient`가 `FirebaseAuth.getInstance()` 싱글턴을 내부에서 직접 쓰는 것과 같은 이유의 설계.
- **"시청 완료 → 상태 전이" 판단을 GoCoachApp.kt에 인라인하지 않고 분리**: 4장 표는 이 판단을 App Service 계층(기존 `activateForMatch`류 람다와 동일한 성격)으로 분류했지만, `GoCoachApp.kt`가 이미 라인(856/880)·상태 훅(47/47, 여유 0) 예산을 거의 다 쓴 상태라 그대로 인라인하면 예산을 넘길 위험이 있었다. 그래서 `application/premium/PremiumAdGrantApplication.kt`에 순수 함수 `runPremiumAdGrantApplication(...)`으로 추출해(입력: `AdRewardOutcome` + 현재 시각, 출력: 다음 `PremiumState`(또는 상태 유지를 뜻하는 `null`) + 항상 남기는 진단 이벤트), `GoCoachApp.kt`의 `activateAdGrant` 람다는 광고 클라이언트 호출 + 이 함수 호출 + 결과 반영 3줄짜리 얇은 글루로만 남겼다. 최종 856→869줄(예산 880 이내), 상태 훅 47(불변) — 새 `remember`/`mutableStateOf`/`LaunchedEffect`를 추가하지 않았다.
- **로딩/실패 UX**: `PremiumUpsellDialog`에 `isAdGrantInProgress` 상태를 추가해(`ui/PremiumUiState.kt`, `GoCoachApp.kt`가 아니라 다이얼로그 자신의 `remember`로 소유 — 예산이 빠듯한 쪽을 건드리지 않는 기존 패턴 재사용) 광고 로드~노출~판정이 끝날 때까지 세 버튼을 모두 비활성화하고 광고 버튼 자리에 진행 표시를 보여준다. `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`로 이 구간에는 뒤로가기/바깥 탭으로 닫지 못하게 막았다 — 닫히면 코루틴 스코프가 취소되어 이미 화면에 떠 있는 실제 광고의 시청 결과를 영영 못 받기 때문. 실패/중단(`AdRewardOutcome.NotRewarded`) 시에는 일반 모드를 유지한 채 안내 토스트만 띄우고 팝업은 닫지 않아, 바로 재시도하거나 다른 선택지를 고를 수 있다.
- **광고 단위 ID 관리**: `app-android/build.gradle.kts`가 `local.properties`(gitignored, `sdk.dir`과 같은 파일)의 `admob.appId`/`admob.rewardedAdUnitId` 두 키를 읽어 각각 `manifestPlaceholders["admobAppId"]`(매니페스트의 `com.google.android.gms.ads.APPLICATION_ID` meta-data가 참조)와 `BuildConfig.REWARDED_AD_UNIT_ID`로 연결한다. 두 키가 없으면 Google 공식 테스트 ID(`ca-app-pub-3940256099942544~3347511713` / `.../5224354917`, 커밋해도 무방한 공개 값)로 폴백 — 실제 값은 코드/버전관리에 전혀 들어가지 않는다. **사용자가 나중에 해야 할 일**: [admob.google.com](https://admob.google.com)에서 계정/앱 등록 + 리워드 광고 단위 발급 → `local.properties`에 `admob.appId=...`/`admob.rewardedAdUnitId=...` 두 줄만 추가하면 코드 변경 없이 다음 빌드부터 실제 값이 반영된다(2026-08-05 확인 시점 기준, 사용자는 아직 미등록 상태 — "테스트 ID로 우선 진행"을 명시적으로 선택함).
- **지연 SDK 초기화**: `MobileAds.initialize(...)`를 앱 기동 시점이 아니라 `AndroidRewardedAdClient.showRewardedAd()`가 처음 호출되는 시점(= 사용자가 실제로 광고 시청을 선택했을 때)에만 호출한다 — 프리미엄 광고 기능을 한 번도 안 쓰는 세션의 콜드 스타트 시간에는 영향이 없도록 하기 위함. 반복 호출은 SDK가 멱등 처리하므로 별도의 "이미 초기화됨" 플래그는 두지 않았다.
- **의존성**: `com.google.android.gms:play-services-ads:25.4.0`(2026-08 기준 Google 공식 릴리스 노트로 확인한 최신 안정 버전).
- **검증**:
  - `JAVA_HOME=temurin-17 make test` 통과 — 신규 `PremiumAdGrantApplicationTest`(4건: 보상 획득 시 전이/로그, 시청 중단/로드 실패 시 상태 유지 + 사유별 로그, detail 미제공 시 빈 문자열 처리) 포함, `LayeringContractTest`(43건, `application/premium` 플랫폼-프리 검사 + `GoCoachApp.kt` 라인/상태훅 예산 검사 포함) 전부 green.
  - **APK 크기/빌드 시간**(`git stash`로 되돌린 커밋 전 상태와 비교, 둘 다 `--rerun-tasks`로 클린 빌드): 디버그 APK 19,000,286 → 22,062,824 바이트(+2.92MB, 약 +16%) · `:app-android:assembleDebug` 30s → 37s. 디버그 빌드(코드/리소스 축소 없음) 기준 수치이며, 실 릴리스 빌드는 R8/리소스 shrink로 이보다 작아질 가능성이 있다.
  - **에뮬레이터 실측**(`Pixel_7_API_35`, `emulator-5554`, 게스트 세션): (1) **정상 시청 완료** — 대국 설정 화면 프리미엄 카드 → 업셀 팝업 → "광고 시청으로 1시간 활성화" 탭 → 로딩 표시 → Google 테스트 리워드 광고 로드/노출 → "Reward granted" → 닫기 → 팝업 자동 닫힘 + 프리미엄 즉시 활성화("대국 시작하기" 버튼이 금색 + `59:58` 카운트다운으로 전환) + 진단 로그 `premium_ad_grant_activated` 기록까지 전부 확인. (2) **광고 로드 실패**(에뮬레이터 wifi/데이터를 강제로 꺼서 재현) — 로딩 표시 후 안내 토스트("광고 시청이 완료되지 않아...") 노출, 팝업은 닫히지 않고 유지, 일반 모드 그대로 유지, 진단 로그 `premium_ad_grant_not_rewarded`(`reason=LoadFailed`, 실제 SDK 에러 메시지 `Unable to resolve host "googleads.g.doubleclick.net"` 포함)까지 확인. 두 경로 모두 logcat에 크래시 없음. **시청 중 이탈(`DismissedWithoutReward`)은 실기기에서 별도 재현하지 못함** — Google 테스트 리워드 광고가 노출 직후 거의 즉시 보상을 부여해 "다 보기 전에 닫기" 타이밍을 에뮬레이터 조작만으로 만들 수 없었다. 이 분기는 위 "광고 로드 실패" 경로와 완전히 같은 처리 경로(`runPremiumAdGrantApplication`의 `NotRewarded` 분기)를 타므로 코드 리뷰 + `PremiumAdGrantApplicationTest`의 유닛 테스트로 대신 커버했다.
- **신규 파일**: `application/premium/AdRewardPort.kt`, `application/premium/PremiumAdGrantApplication.kt`, `ui/AndroidRewardedAdClient.kt`, `app-android/src/test/.../application/PremiumAdGrantApplicationTest.kt`.

### Step 3 후속 — 실제 광고 단위(배너 + 리워드 전면) 연동 + 출시 전 테스트모드 안전장치 (2026-08-05)
- **배경**: 사용자가 AdMob 콘솔에서 앱(`ca-app-pub-6644510399396628~8010375588`)과 광고 단위 2개(배너 `.../3630332783`, "보상형" `.../5703776449`)를 실제로 발급받고, 이 두 단위를 앱에 연동해달라고 요청했다. 동시에 "정식 출시 전에 실제 광고를 연동하면 계정 패널티를 받는다고 알고 있는데 여전히 유효한지, 개발 중엔 안전하게 테스트모드로 동작하게 해달라"는 요청도 함께 받았다.
- **정책 확인 (여전히 유효함, 2026-08-05 기준)**: Google 공식 문서(["Understanding account suspensions due to invalid traffic"](https://blog.google/products/admob/understanding-account-suspensions-due-invalid-traffic/), [AdMob 고객센터](https://support.google.com/admob/answer/3342099?hl=en))에 따르면 실제 광고 단위에 인위적인 트래픽(자기 클릭, 개발 중 반복 노출 등)이 쌓이면 계정이 정지되고 그동안의 수익이 광고주에게 환불될 수 있다. Google이 공식으로 권장하는 안전한 테스트 방법은 두 가지: ①실제 계정과 무관한 [Google 공식 테스트 광고 단위](https://developers.google.com/admob/android/test-ads) 사용, ②테스트 기기 등록(`RequestConfiguration`) 후 실제 광고 단위 사용. 이번 작업은 ①번 방식을 "사람이 깜빡할 수 없는" 빌드 타입 안전장치로 자동화했다(테스트 기기 등록은 기기별 등록이 필요해 채택하지 않음 — 아래 참고).
- **포맷 정정: "보상형(Rewarded)"이 아니라 "보상형 전면(Rewarded Interstitial)"**: 사용자가 콘솔에서 실제로 발급받은 두 번째 광고 단위는 안내 문구("보상형 전면 광고 구현 가이드", `rewarded-interstitial` 링크)상 **Rewarded Interstitial** 포맷이다 — 이전 라운드(Step 3 최초 구현)에서 쓴 순수 **Rewarded**(`RewardedAd`)와는 다른 포맷이며, 광고 단위 ID는 발급 시점에 포맷이 고정되어 서로 바꿔 쓸 수 없다. 두 포맷의 정책상 차이는 "Rewarded는 사용자가 명시적으로 옵트인해야 하고, Rewarded Interstitial은 옵트인 없이도 노출 가능하되 광고 시작 전 보상 고지 화면이 필요하다"는 점인데, 이 앱은 이미 업셀 팝업에서 명시적 옵트인 버튼("광고 시청으로 1시간 활성화")을 거치므로 어느 포맷이든 정책 요건을 충족한다. `ui/AndroidRewardedAdClient.kt`를 `ui/AndroidRewardedInterstitialAdClient.kt`로 이름/구현을 바꿔(`com.google.android.gms.ads.rewarded.RewardedAd` → `com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd`, API 레퍼런스로 패키지 경로 직접 확인) 실제 발급받은 포맷과 맞췄다 — `AdRewardPort`/`PremiumAdGrantApplication`/`PremiumUiState`/`GoCoachApp.kt` 호출부는 전혀 건드리지 않았다(포트/어댑터 분리 설계가 의도한 대로, SDK 클래스 교체가 어댑터 파일 안에서 끝남).
- **출시 전 테스트모드 안전장치 (빌드 타입 기반)**: `app-android/build.gradle.kts`의 `buildTypes`에 `USE_TEST_ADS` BuildConfig 플래그를 추가해, **debug/friend는 local.properties 내용과 무관하게 항상 `true`(Google 테스트 App ID/광고단위 강제 사용)**, **release만 local.properties에 실제 값이 모두 있을 때 `false`**로 만들었다. `friend`는 `initWith(getByName("debug"))`에 의존하지 않고 세 필드를 명시적으로 다시 선언한다(AGP `initWith`가 `buildConfigField`/`manifestPlaceholders`를 항상 복사한다는 보장이 약하고, "friend"가 정식 출시 전 지인 배포용 채널이라 이 안전장치가 가장 중요하게 적용돼야 하는 빌드이기도 하기 때문). 실제 판단 로직(테스트/실제 어느 쪽을 쓸지)은 Gradle에 흩어두지 않고 `ui/AdUnitIds.kt` 한 파일에 모아, 코드 리뷰로 한눈에 안전성을 확인할 수 있게 했다. **로컬에서 직접 확인**: `generateDebugBuildConfig`/`generateFriendBuildConfig`/`generateReleaseBuildConfig` + `processXxxMainManifest`를 각각 실행해 생성된 `BuildConfig.java`/병합 매니페스트를 직접 읽어, debug/friend는 `USE_TEST_ADS=true`+Google 테스트 App ID·광고단위, release는 `USE_TEST_ADS=false`+실제 App ID·광고단위(`local.properties`에서 로드)가 정확히 나오는 것을 확인했다 — 에뮬레이터를 만지기 전에 먼저 이 확인부터 마쳐, 테스트 도중 실수로 실제 광고 인벤토리에 노출/요청이 나가는 일을 원천 차단했다.
- **배너 광고 신규 추가**: `ui/BannerAdView.kt` — Compose가 `AdView`(View 기반)를 직접 지원하지 않아 `AndroidView`로 감싸고, `AdSize.getLargeAnchoredAdaptiveBannerAdSize`(적응형 배너, 구 API `getCurrentOrientationAnchoredAdaptiveBannerAdSize`는 deprecated로 확인되어 신API로 바로 적용)로 화면 너비에 맞춘 배너를 로드한다. `onRelease` 콜백에서 `AdView.destroy()`를 호출해 컴포지션 이탈 시 리소스를 정리한다. **배치**: 홈 화면(`GoCoachHomeScreen.kt`) 맨 아래 — 대국 화면 등 실제 게임 플레이 화면에는 넣지 않았다(오조작 유도/게임 경험 방해 우려, AdMob 정책의 "실수 클릭 유도 배치 금지" 취지와도 맞음). 기존 콘텐츠 Column을 `weight(1f)`로 감싸 배너가 하단에 고정되면서도 기존 24dp 패딩/중앙 정렬은 전혀 건드리지 않았다.
- **`local.properties`에 실제 값 반영**: 사용자가 채팅으로 직접 전달한 실제 App ID/광고단위 ID 3개를 `admob.appId`/`admob.rewardedInterstitialAdUnitId`/`admob.bannerAdUnitId` 키로 기록했다(gitignored, git에 올라가지 않음) — release 빌드를 만들 때만 실제로 쓰인다.
- **검증**: `make test` 통과(레이아웃/BuildConfig 변경만 있고 로직 테스트는 이전 라운드 것 그대로 green). 에뮬레이터(`Pixel_7_API_35`) 실측 — 홈 화면 하단에 Google 공식 테스트 배너("Nice job! This is a 320x50 test ad.") 렌더링 확인, 업셀 팝업 → 광고 시청 → Google 테스트 리워드 전면 광고("Flood-It!" 비디오, "Reward in 7 seconds" 카운트다운) 노출 → "Reward granted" → 닫기 → 프리미엄 정상 활성화(60분 카운트다운) + 진단 로그 `premium_ad_grant_activated` 기록까지 재확인, 두 기능 모두 크래시 없음(전체 세션 logcat에 `FATAL EXCEPTION` 0건).
- **하지 않은 것**: 테스트 기기 ID 등록(`RequestConfiguration.setTestDeviceIds`)은 추가하지 않았다 — 빌드 타입 자체가 이미 "디버그/친구 빌드는 항상 테스트 광고"를 보장해 기기별 등록이 없어도 안전하고, 각 개발자가 자기 기기 광고 ID를 찾아 하드코딩해야 하는 번거로움/누락 위험만 추가되기 때문(YAGNI). 실제 광고 단위의 실물 렌더링을 눈으로 확인하고 싶다면 release 빌드(local.properties 값 사용)를 별도로 만들어 확인하되, 이 경우부터는 절대 광고를 클릭하지 않아야 한다.

### Step 4 구현 — Google Play Billing 실제 연동 (2026-08-06)
- **배경**: `ui/PremiumUiState.kt`의 `setPurchased(true)`가 실제 결제 없이 즉시 영구 활성화로 전환하던 스텁(2026-08-04 도입)을, 실제 Google Play Billing 비소모성(non-consumable/"one-time product") 상품 구매로 교체했다. Step 3(AdMob 리워드 광고 실연동)의 포트/어댑터 분리 패턴을 그대로 따랐다.
- **사전 확인**: 착수 시점에 Play Console 비소모성 상품·라이선스 테스터 계정이 모두 미등록 상태였다 — 사용자에게 등록 절차를 안내하고, AdMob 때와 동일하게 "코드는 플레이스홀더로 먼저 완성, 실제 값은 `local.properties`로 나중에 주입" 방식을 사용자 동의하에 선택했다.
- **계층 배치(4장 표 그대로 적용)**: `application/premium/PurchasePort.kt`(순수 인터페이스, `PurchaseOutcome`/`PurchaseFailureReason` 포함)가 포트, `ui/AndroidBillingClient.kt`가 실제 Play Billing SDK 어댑터다 — `AdRewardPort`/`AndroidRewardedInterstitialAdClient`와 완전히 같은 자리. "구매 완료 → 상태 전이" 판단은 `application/premium/PremiumPurchaseApplication.kt`의 순수 함수 `runPremiumPurchaseApplication`으로 분리했다(App Service 계층, 기존 `runPremiumAdGrantApplication`과 동일한 성격).
- **명시적 구매와 앱 시작 복원을 하나의 판정 함수로 공유**: `PurchaseTrigger`(`Explicit`/`Restore`) 값만 다르게 넘겨 상태 전이 로직(`Purchased`면 영구 활성화, 그 외엔 상태 불변) 자체는 완전히 재사용하고, 진단 로그의 `code`/`severity`만 트리거별로 구분했다(예: 복원 조회에서 "소유한 구매 없음"은 대부분의 사용자에게 정상적인 기본 상태라 `Warning`이 아니라 `Info`로 남긴다).
- **복원은 절대 다운그레이드하지 않는다**: 앱 시작 시 `queryPurchasesAsync`로 조회해 소유 중인 구매가 없다고 나와도, 로컬에 이미 있던 `PremiumSource.Purchase` 상태를 되돌리지 않는다(`nextState = null`, 즉 상태 유지). 일시적인 네트워크 응답 하나로 결제한 사용자의 접근권을 조용히 빼앗는 위험이, 드문 환불 케이스가 잠시 더 유지되는 위험보다 크다고 판단한 보수적 선택이다 — 백엔드 영수증 검증/환불 감지는 이번 범위 밖으로 남겨둔다.
- **`GoCoachApp.kt` 예산 초타이트 대응(878/880줄, 47/47 상태 훅 — 여유 2줄·0훅)**: Step 3 시점에 이미 예산을 거의 다 써서, 이번엔 "어댑터 생성+판정 함수 호출+로그 기록" 시퀀스 전체를 `ui/PremiumPurchaseGlue.kt`(같은 `ui` 패키지, import 불필요)의 `performPremiumPurchase`/`performPremiumPurchaseRestore`로 옮겨 `GoCoachApp.kt`의 `purchasePremium` 글루는 결과 반영 4줄만 남겼다. 앱 시작 복원도 `LaunchedEffect`를 별도 컴포저블(`PremiumUiState.kt`의 `PremiumPurchaseRestoreEffect`)에 남겨 `GoCoachApp.kt`의 상태 훅 예산에 전혀 영향을 주지 않았다(`PremiumUpsellDialogHost`가 `isAdGrantInProgress`를 자체 `remember`로 소유하는 것과 같은 이유). 최종 868→878줄, 상태 훅 47(불변).
- **상품 ID 관리**: `app-android/build.gradle.kts`가 `local.properties`의 `billing.premiumProductId` 키를 읽어 `BuildConfig.PREMIUM_PRODUCT_ID`로 연결한다. **AdMob과 달리 빌드 타입별 테스트/실제 분기가 없다** — Play Billing은 "가짜 상품 ID"가 아니라 Play Console의 라이선스 테스터 계정으로 실제 상품에 대해 무과금 테스트하는 것이 정식 테스트 방법이라, 모든 빌드 타입이 항상 같은 값을 쓴다. 값이 없으면 `premium_lifetime_placeholder`(존재하지 않는 ID)로 폴백해 Play가 상품을 못 찾는 형태로 안전하게 실패한다. **사용자가 나중에 해야 할 일**: Play Console에 비소모성 상품 등록 후 `local.properties`에 `billing.premiumProductId=<실제 상품 ID>` 한 줄만 추가하면 다음 빌드부터 반영된다.
- **라이브러리 버전**: `com.android.billingclient:billing:9.1.0`(2026-06-18 릴리스, 확인 시점 기준 최신 안정 버전). Google 정책상 2026-08-31부터 v8 이상 필수인데 자연히 충족한다. `billing-ktx`의 suspend 확장 함수는 쓰지 않고, `AndroidAuthClient`/`AndroidRewardedInterstitialAdClient`와 동일하게 콜백 기반 API를 `suspendCancellableCoroutine`으로 직접 감쌌다(기존 어댑터들과의 일관성 우선, 그리고 ktx 확장 함수명이 버전마다 미묘하게 바뀌어 온 것과 달리 콜백 API는 안정적으로 유지되어 왔다).
- **연결 수명 주기**: 구매 버튼 1개 + 앱 시작 복원 조회 1번뿐인 사용 패턴상, `BillingClient`를 계속 유지하지 않고 매 호출마다 연결→작업→`endConnection()`을 짧게 반복한다(`AndroidRewardedInterstitialAdClient`가 광고 클라이언트를 매번 새로 만드는 것과 동일한 이유) — `GoCoachApp.kt`에 연결을 담아둘 `remember`를 추가할 필요가 없어져 예산 문제도 함께 해결된다. `enableAutoServiceReconnection()`은 이런 단발성 연결에는 의미가 없어 의도적으로 쓰지 않았다.
- **`ITEM_ALREADY_OWNED` 방어 처리**: 로컬 상태가 어떤 이유로든(예: 개발자 테스트 토글로 초기화 후 재구매 시도) Play의 실제 소유 여부와 어긋나 있으면, `launchBillingFlow`가 이 응답 코드를 동기 반환한다 — 이 경우 에러로 보여주지 않고 기존 소유권을 조회해 정상 활성화로 처리하도록 별도 분기를 추가했다.
- **개발자 테스트 토글 유지 결정**: `SettingsScreen.kt`의 "개발자 테스트" 섹션 프리미엄 토글(`setPurchased`, `BuildConfig.DEBUG` 게이팅)은 실 결제 연동 후에도 그대로 유지하기로 사용자와 확정했다 — 실제 결제 없이 빠르게 QA할 수 있는 경로로 계속 쓸모 있다는 판단.
- **검증**:
  - `JAVA_HOME=temurin-17 make test` 통과 — 신규 `PurchasePort`/`PremiumPurchaseApplication`(`PremiumPurchaseApplicationTest` 6건: 명시적/복원 구매 활성화, 취소 시 상태 유지, 복원 미소유 시 Info 로그, 그 외 실패 시 Warning 로그, detail 미제공 시 빈 문자열) 포함, `LayeringContractTest`(플랫폼-프리 검사 + `GoCoachApp.kt` 878/880줄·47/47훅 예산 검사 포함) 전부 green.
  - **에뮬레이터 실측**(`Pixel_7_API_35`, `emulator-5554`): (1) **앱 시작 복원** — 로그에 `premium_purchase_restore_not_found`(reason=NotFound) 정상 기록, 크래시 없음. (2) **구매 버튼 실제 연동 확인** — 미등록 플레이스홀더 상품 ID로 실제 Play Billing에 연결 후 상품 조회 실패 → `premium_purchase_not_completed`(reason=ProductUnavailable) 로그 + "구매가 완료되지 않아..." 토스트 정상 노출, 팝업 유지(재시도 가능), 크래시 없음. (3) **개발자 토글 유지 확인** — 토글로 영구 구매 상태를 켠 뒤 앱을 강제 종료·재시작해도 복원 조회가 그 상태를 되돌리지 않음(`premium_purchase_restore_not_found`가 다시 기록되지만 로컬 Purchase 상태·"대국 시작하기" 버튼의 👑∞ 표시는 그대로 유지)을 확인 — "복원은 다운그레이드하지 않는다" 설계가 실제로 의도대로 동작함을 검증. (4) 기존 광고 시청 경로(Step 3)는 이번 변경으로 건드리지 않았고, 실제로 이전 세션에서 부여된 광고 기반 활성화(카운트다운 배지)가 그대로 남아있는 것도 확인했다.
  - **실 구매 완료/재설치 복원 플로우는 이번 범위에서 검증하지 못함** — Play Console에 상품이 아직 없고 라이선스 테스터 계정도 없어, "결제 성공 → `Purchased` 반환 → 영구 활성화" 경로 자체는 코드 리뷰 + 위 유닛 테스트로만 커버했다. 사용자가 등록을 마치면 `local.properties`에 실제 상품 ID를 추가한 뒤, 라이선스 테스터 계정으로 실 기기/에뮬레이터에서 (a) 신규 구매 완료, (b) 앱 재설치 후 자동 복원 두 플로우를 마저 확인해야 한다.
- **신규 파일**: `application/premium/PurchasePort.kt`, `application/premium/PremiumPurchaseApplication.kt`, `ui/AndroidBillingClient.kt`, `ui/PremiumPurchaseGlue.kt`, `app-android/src/test/.../application/PremiumPurchaseApplicationTest.kt`.

### Step 4 후속 — Play Console 설정 실제 상태 재확인 + 실기 구매/복원 e2e 검증 (2026-08-09)

- **배경**: 위 2026-08-06 로그는 "Play Console에 상품이 아직 없고 라이선스 테스터 계정도 없다"고 기록했지만, 출시 준비 마무리 세션에서 Claude in Chrome으로 Play Console(`play.google.com/console`, 계정 `flit9sky@gmail.com`, 개발자 ID `6220696780099532887`)에 직접 접속해 확인한 결과 **이미 전부 완료돼 있었다** — 8/6 로그 이후 별도 세션에서 진행되고 문서에 반영되지 않은 상태였다.
- **Play Console 확인 사실**:
  - `PREMIUM_MODE.md` Step 4가 참조하는 상품 `premium_lifetime`("프리미엄 영구 해제")이 **일회성 제품**으로 등록되어 있고, 구매 옵션 `premium-lifetime-purchase`가 **활성** 상태 — 173개 국가/지역에 가격 책정 완료(대한민국 ₩9,900, VAT 없음), 최근 업데이트 2026-08-06.
  - **내부 테스트** 트랙에 AAB 버전 2(0.1.1)가 게시됨(2026-08-06 17:07), 테스터 이메일 목록에 `flit9sky@gmail.com` 포함.
  - **라이선스 테스트**(설정 → 라이선스 테스트)에서 위와 동일한 "내부 테스트용 이메일 목록"이 라이선스 테스터 목록으로 이미 선택돼 있음 — 즉 `flit9sky@gmail.com`으로 로그인된 기기의 Play Billing 구매는 전부 "Test card, always approves"로 처리되는 무과금 테스트 주문이 된다.
  - `local.properties`의 `billing.premiumProductId`도 8/6 시점에 이미 실제 값 `premium_lifetime`로 채워져 있었다(더 이상 플레이스홀더가 아님) — 코드 변경은 필요 없었다.
- **동시에 발견한 별개 미커밋 작업**: `Makefile`/`app-android/build.gradle.kts`에 Play Console 업로드용 `playInternal` 빌드 타입(release keystore 서명, `make play-internal-aab` 타겟)이 추가돼 있었으나 커밋되지 않은 상태였다 — 위 내부 테스트 AAB(8/6 게시분)를 만드는 데 실제로 쓰인 것으로 보인다. 이 문서 갱신과 별개로 사용자 확인 후 커밋 여부를 결정한다.
- **실기 e2e 검증**(`Pixel_7_API_35`, `emulator-5554`, Google 계정 `flit9sky@gmail.com` 로그인 상태 — 라이선스 테스터와 일치 확인 후 진행):
  - **(a) 실제 구매 완료**: 앱 데이터 초기화 후 게스트로 진입 → 대국 설정 화면 프리미엄 카드 → 업셀 팝업 → "프리미엄 영구 활성화(결제)" → 실제 Play Billing 구매 시트가 "Test card, always approves" / "This is a test order, you will not be charged" 문구와 함께 뜸(라이선스 테스터로 처리되는 것을 시트에서 직접 확인) → 1-tap buy → "Payment successful" → 앱 UI가 즉시 👑`∞` 뱃지로 전환됨을 확인.
  - **(b) 재설치 후 자동 복원(로그인 없는 사용자)**: 위 구매 직후 앱 데이터를 다시 초기화(재설치와 동일한 로컬 상태)하고 재실행 → 온보딩에서 **로그인하지 않고** "계정 없이 시작하기"로 게스트 진입 → 홈 → 대국 설정 화면에 진입한 순간 별도 조작 없이 곧바로 👑`∞` 뱃지가 표시됨. `PremiumPurchaseRestoreEffect`가 앱 시작 시 Firebase/게스트 인증 상태와 무관하게 Play Billing 소유 구매를 조회해 자동 반영한다는 설계가 실제로 로그인 없는 사용자에게도 그대로 동작함을 확인했다.
  - 두 플로우 모두 크래시 없음. 이로써 2026-08-06 로그의 "실 구매 완료/재설치 복원 플로우는 검증하지 못함" 남은 갭이 해소됐다.
- **결론**: Step 4는 코드·Play Console 설정·실기 검증까지 전부 완료됐다. 남은 것은 실제 서비스 오픈(스토어 공개 출시) 시점의 일반 절차(스토어 등록정보, 콘텐츠 등급, 프로덕션 트랙 승급 등)뿐이며 이는 Step 4의 범위가 아니다.

### 배너 광고 재노출 위치 — 보류 결정 (2026-08-09)

- 2026-08-08에 홈 화면 하단 배너 광고 호출부를 임시 제거한 뒤(`BannerAdView.kt`/`AdUnitIds.kt` 등 인프라는 그대로 유지, 커밋 `59d880c`), 재노출 위치를 이 세션에서 사용자에게 확인했다.
- **결정**: 출시 시점엔 배너 광고를 노출하지 않는다. 향후 "대국 복기하기"(기보 복기) 기능이 추가되는 시점에 그 화면에 배너를 넣을 예정 — 지금 다시 판단할 필요 없이 그 기능 착수 시 함께 결정한다.

### 업셀 팝업 버튼 순서/강조 — 현행 유지 확정 (2026-08-09)

- `PremiumUpsellDialog`(`ui/PremiumUiState.kt`)의 현재 순서(광고 시청=강조된 주 버튼 → 결제=보조 버튼 → 아니오)를 사용자에게 재확인한 결과, 의도적인 배치로 그대로 유지하기로 확정했다. 코드 변경 없음.

### ⚠️ 미착수 결정 — 프리미엄을 월 구독으로 전환 (2026-08-24 결정, 2026-08-29에 이 문서로 인계)

- **결정 내용**: 프리미엄 자체를 **월 구독으로 전환**하기로 했다. 지금의 `premium_lifetime`(비소모성 영구, ₩9,900)은 이 방향과 맞지 않는다.
- **범위**: 상품 타입·만료/갱신 축·복원 로직이 전부 바뀌는 작업이라 **이 트랙(`PREMIUM_MODE.md`)의 몫**이다. 부담이 적은 시점이기도 하다 — 내부 테스트 트랙에만 게시된 상태라 마이그레이션할 실구매자가 없다.
- ⚠️ **이 결정은 2026-08-24부터 2026-08-29까지 이 문서에 도달하지 못했다.** 결정이 내려진 곳은 `260823-260830_OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN.md` 7장이고, 그 문서는 "이 전환은 이 문서의 범위 밖이며 `PREMIUM_MODE.md` 트랙에서 별도로 다룬다"고 정확히 위임했지만 — 받는 쪽인 이 문서에 아무도 옮겨 적지 않아 `PREMIUM_MODE.md`·`GOOGLE_PLAY_LAUNCH_PLAN.md`·`FEATURE_ACCESS_PRINCIPLES.md` 어디에도 "구독"이라는 단어가 한 번도 없었다. **다른 트랙으로 결정을 넘길 때는 넘기는 문서에 적는 것으로 끝내지 말고, 받는 문서에도 그 자리에서 한 줄 남긴다.**
- **가격 확정(2026-08-30 갱신)**: **월 3,900원**. 구독 기간 동안 **앱 내 모든 기능**이 열리며 **유료 캐릭터도 사용 가능**하다. 방침은 *"기능이 늘면서 가치가 오르면 그때 가격도 조정"*.
  · ⚠️ **이 줄은 2026-09-01까지 "월 5,000~10,000원"으로 남아 있었다** — 2026-08-30에 3,900원으로 좁힌 사용자 확정이 백로그(#26)에만 적히고 여기로 오지 않았다. **바로 위 불릿이 경고하는 그 실패가 같은 문단에서 한 번 더 일어난 것이다.**
- **캐릭터 보유와의 관계(2026-09-01 재확정)**: **첫돌이를 제외한 캐릭터를 1종 이상 보유하면 매 판 N회(현재 3)** 프리미엄 기능을 1회권보다 **먼저** 쓴다 — **무제한이 아니다.** 획득 방법(유료·광고·출석)은 가리지 않는다. 구독이 파는 것은 ⓐ 모든 캐릭터 ⓑ **대국 중 무제한** ⓒ **대국 종료 후 기능**(미구현)이고, **ⓒ가 구독만의 축이라 값을 지킨다.** 정본은 `FEATURE_ACCESS_PRINCIPLES.md` **8.3-2**.
- ~~**캐릭터 개별 구매와의 관계(2026-08-29)**~~ (위로 대체됨): 유료 캐릭터 구매자는 **그 캐릭터와 두는 동안만** 인게임 프리미엄 기능(형세 보기·추천 수)을 무제한 쓴다 — 캐릭터 구매 유도 장치다. 두 상품이 겹치지 않는 이유와 프리미엄에 더 붙을 축(대국 종료 후 분석, 지난 기보 분석)은 `FEATURE_ACCESS_PRINCIPLES.md` **8장**에 정리돼 있다. ⚠️ 그 8.3절이 짚은 **`FeatureAccessPolicy`의 구조 변경**(판정이 대국 상대에 의존하게 됨)을 착수 전에 반드시 읽을 것.
- **선행 확인**: 착수 전에 `premium_lifetime`을 이미 구매한 계정(라이선스 테스터 포함)을 어떻게 처리할지, 그리고 구독 상품을 Play Console에 새로 등록해야 하는지 사용자에게 확인할 것.
- **관련**: 봇 캐릭터 **개별 구매(₩4,900, 백로그 #18)** 는 **이것과 별개 상품**이다(단발성 결제·영구 소유 / 이쪽은 월 구독). ⚠️ **이 줄은 두 번 낡아 있었다** — ⓐ 가격을 ₩9,900으로 적고 *"가격이 같아 혼동하기 쉽다"* 고 했는데, 캐릭터는 2026-08-29에 **4,900원으로 인하 확정**됐으므로 이제 값이 같지 않다(같았던 것은 `premium_lifetime`과의 우연이었다). ⓑ **"5단계"도 좁다** — 2026-09-01 확정된 판매 모델은 **구매가 무료 경로를 대체하지 않고 병렬로 붙는** 것이라, 출석 보상 캐릭터도 광고 조각 캐릭터도 함께 판다(**첫돌이를 뺀 네 종 전부**). ⚠️ 다만 지금 코드가 그 두 축을 표현하지 못해 착수 전 처리할 것이 셋 있다 — `FEATURE_ACCESS_PRINCIPLES.md` **8.3-2** 참고.

---

이 문서는 각 단계 착수/완료 시점마다 위 마일스톤 표의 상태와 관련 섹션을 갱신하며, 완료된 단계도 지우지 않고 이력으로 남깁니다.
