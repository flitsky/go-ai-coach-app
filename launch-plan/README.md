# 구글 마켓 초도 버전 발행 전략

작성일: 2026-08-13

본 문서는 `feature-access-principles/README.md`의 원칙 위에서, **Google Play 초도 버전 발행**을 위한 구체적인 기능별 정책과 실행 체크리스트를 담습니다. `premium-mode/README.md`·`auth-onboarding/README.md`와 동일한 방식으로 계속 갱신되는 히스토리 문서이며, 작성 과정에서 자연스럽게 **근시일 로드맵 문서** 역할도 겸하게 될 예정입니다(작성자 본인 언급, 2026-08-13).

---

## 0. 최종 출시 체크리스트 종합 (260817 기준 — 새 스레드는 여기부터 읽을 것)

`docs/refactoring/REFACTORING_BACKLOG_260816_1744.md`(리팩토링/코드부채 전용)와 `docs/refactoring/GAMESESSION_SHARED_MIGRATION_KICKOFF_PLAN_260816_1808.md`(`application/` 트리 → `:shared` 이전)가 이 날짜에 각각 마무리됐다 — 남은 작업은 리팩토링이 아니라 **초도 시장 발행 자체**로 성격이 넘어갔다. 아래는 코드베이스를 실제로 점검(파일 존재 여부, `local.properties` 키 존재 여부, manifest 내용 등)해서 확인한 현재 상태다 — 추정이 아니라 확인된 사실 기준.

### ✅ 완료 (코드 + 실기/콘솔 검증까지 끝남, 재작업 불필요)
- **AdMob 광고**: 실계정·실광고단위(배너+보상형 전면) 연동 완료. `debug`/`friend`는 빌드타입 자체가 항상 Google 공식 테스트 ID를 강제하고, `release`만 `local.properties`의 실제 값을 쓴다 — **"테스트 광고 → 실제 광고 전환"은 이미 자동화돼 있다**, 별도 전환 작업이 필요 없다(`ui/AdUnitIds.kt`, `premium-mode/README.md` "Step 3 후속"). `local.properties`에 `admob.appId`/`admob.bannerAdUnitId`/`admob.rewardedInterstitialAdUnitId` 3개 키 모두 존재 확인(260817).
- **Play Billing**: Play Console에 비소모성 상품 `premium_lifetime` 등록·활성화, 라이선스 테스터 등록, 실제 구매 완료 + 재설치 자동 복원 두 플로우 모두 실기(에뮬레이터) e2e 검증 완료(2026-08-09, `premium-mode/README.md` "Step 4 후속"). `local.properties`의 `billing.premiumProductId`도 실제 값으로 채워져 있음(260817 확인).
- **릴리스 서명 키스토어**: `local.properties`에 `release.storeFile`/`release.keyAlias`/`release.keyPassword`/`release.storePassword` 4개 키 모두 존재 확인(260817) — `app-android/build.gradle.kts`의 `release`/`playInternal` signingConfig가 이미 이걸 참조하도록 배선돼 있다.
- **릴리스 빌드 파이프라인**: `make play-internal-aab`(Play Console 내부 테스트용)·`make bundle-aab`(정식 프로덕션용, 실제 AdMob/Billing 값 사용)·`make release`(APK) 전부 `bump-version`(version.properties의 VERSION_CODE 자동 증가, 재사용 방지)을 거치도록 완성돼 있다(커밋 `7d233f4`/`46eb5a0`). 260817 기준 `VERSION_CODE=111`/`VERSION_NAME=0.1.11`.
- **Play Console 내부 테스트 트랙**: AAB 버전 2(0.1.1) 이미 게시됨, 테스터 이메일 등록 완료(2026-08-06 작업, 2026-08-09 재확인).
- **스토어 등록정보 텍스트 초안**: `design-handoff/export/2026-08-11-v0.1.2/store_listing.txt` — 앱 이름/짧은 설명/자세한 설명/키워드까지 초안 존재. 스크린샷 4장(`screenshots/`)도 Play Console 규격(2:1, 1148x2296)에 맞춰 이미 캡처돼 있음.
- **앱 핵심 기능/안정성**: 이번 세션에서 `application/` 트리 전체(124개 파일)가 `:shared`로 이전 완료, `make test` 전체 그린, 에뮬레이터 실기 스모크 테스트(app-launch/new-game/board-tap/saved-session-prompt) 4종 모두 통과.

### ❌ 미착수 — 진짜 남은 갭 (260817 신규 확인, 사용자가 언급한 "광고 실연동"보다 이쪽이 실제 병목)
- **앱 런처 아이콘이 없다**: `AndroidManifest.xml`의 `<application>` 태그에 `android:icon` 속성 자체가 없고, `res/` 아래 `mipmap-*`/`ic_launcher*` 파일이 하나도 없다(260817 직접 확인 — `res/`엔 `drawable`·`values`만 존재). 지금 빌드하면 기본 안드로이드 placeholder 아이콘으로 설치된다 — **Play 스토어 등록의 필수 요건이자, 이 중 가장 눈에 띄는 미완성 항목**.
- **스토어 등록용 고해상도 아이콘(512×512)·피처 그래픽(1024×500)**: `design-handoff/` 어디에도 없다. 위 런처 아이콘과 같은 소스 디자인에서 같이 만들어야 효율적.
- **개인정보처리방침(Privacy Policy)**: 저장소 전체를 검색해도 문서/URL이 전혀 없다. AdMob·Firebase(Auth SDK 번들, 플래그로 꺼져 있어도 앱엔 포함됨)·Play Billing을 쓰는 이상 Play Console 필수 제출 항목이다 — 호스팅 위치(GitHub Pages, Firebase Hosting 등)부터 정해야 한다.
- **콘텐츠 등급 설문(IARC)**, **데이터 보안(Data safety) 양식**: Play Console에서 직접 입력해야 하는 항목이라 코드로 확인 불가 — 미완료로 간주하고 착수 시 처음부터 진행. 다행히 실제 데이터 흐름은 단순한 편이다: Firebase Analytics는 의도적으로 아예 안 넣었고(사용처 없음, `app-android/build.gradle.kts` 주석), Crashlytics도 없다 — AdMob 광고ID·Play Billing 구매정보 정도만 선언하면 될 가능성이 높다(정확한 문구는 Play Console 가이드로 최종 확인 필요).
- **기획 디자이너 핸드오프 응답 대기 중**: `design-handoff/README.md` — 2026-08-11 발송 후 "아직 디자이너 회신 없음" 상태 그대로. "먼저 출시하고 고도화는 이후"라는 이번 방향과 맞게, 이 회신을 기다릴지 건너뛰고 지금 상태로 출시할지 결정 필요.

### ⚠️ 갱신 필요 (있긴 한데 최신 상태와 어긋남)
- **스토어 등록정보 텍스트의 "요금 안내" 문단이 stale하다**: `store_listing.txt`가 "해당 대국 한 판(최대 1시간)"이라고 적어뒀는데, 실제 정책은 2026-08-04에 "1판 한정" 자체가 제거되고 **순수 1시간 타이머**로 바뀌었다(그 1시간 안엔 새 대국을 여러 판 시작해도 계속 유효) — `premium-mode/README.md` "결정 번복" 절 참고. 또한 v0.1.2(2026-08-11) 이후 추가된 **무르기 초기 클레임 프로모션**(`launch-plan/README.md` 3장)도 이 텍스트엔 반영 안 돼 있다.
- **스크린샷도 v0.1.2(2026-08-11) 기준**이라, 그 이후 UI 변경(무르기 클레임 다이얼로그, 프리미엄 카드 골드 테마 등)이 반영 안 됐을 수 있다 — 재출시 전 최신 빌드로 다시 캡처할지 판단 필요.

### 참고 — 이번 방향(초도 발행 먼저, 고도화는 이후)에 대한 의견
핵심 엔지니어링(기능, 수익화 인프라, 아키텍처 리팩토링, 테스트)은 이미 상당히 성숙한 상태고, 남은 건 대부분 **코드가 아니라 자산/문서/콘솔 설정**이다 — 방향 자체가 타당하다. 다만 Google Play가 신규 개인 개발자 계정에 요구하는 **비공개 테스트(20명, 14일) 선행 조건**이 이 계정에 해당하는지는 Play Console에서 직접 확인이 필요하다 — 해당된다면 "정식 공개" 시점이 코드/자산 준비와 무관하게 그만큼 뒤로 밀린다.

---

## 1. 초도 발행 기본 원칙

- **로그인 없음** — `FeatureFlags.isLoginEnabled = false` 유지. (`auth-onboarding/README.md`, 2026-08-09 결정 그대로)
- **앱 내 결제 없음** — `FeatureFlags.isPurchaseEnabled = false` 유지. 인프라 자체는 이미 구현·검증 완료 상태라(`premium-mode/README.md` 8장), 켜는 비용은 빌드가 아니라 "플래그 + 필요 시 신규 SKU 등록"뿐입니다.
- 위 두 가지는 **새 결정이 아니라 기존 결정의 확인**입니다 — 초도 발행 시점에도 이대로 유지합니다.

---

## 2. 기능별 초도 정책

| 기능 | 초도 정책 | 향후 계획 |
| --- | --- | --- |
| 기권 / 통과 | 상시 무료 | 변경 없음 |
| 형세 보기(Eval) / 추천 수(Top Moves) | **광고 시청 1시간 임시 활성화만** 제공 (프리미엄 영구 구매 버튼은 비노출) | `isPurchaseEnabled=true` 전환 시 영구 구매 옵션 추가 예정 |
| 무르기(Undo) | **초기 유저 한정 무료 활성화 프로모션** — 클레임 시 영구 무료, 기기 로컬 저장 | 이후 버전에서 무르기도 광고/구매 게이팅 대상으로 전환될 수 있음. 단 **이미 클레임한 유저는 그랜드파더링으로 계속 무료** — 3장 참고 |
| 착수 평가(Move Review) | 형세 보기/추천 수와 동일(광고 1시간만) | 동일 |

**현재 코드 상태 (2026-08-14 갱신)**: 위 표의 "초기 유저 한정 무료 활성화 프로모션"은 커밋 `5fc7b49`(2026-08-13)로 **구현 완료**됐습니다 — `ui/GamePlaySection.kt`의 클레임 다이얼로그(무르기 버튼 탭 시 노출)와 `PremiumState.isUndoClaimed`(로컬 그랜드파더링 플래그)가 실제로 존재하며, 무르기는 더 이상 "게이팅 없는 상시 무료"가 아니라 "클레임했거나 프리미엄이면 사용 가능"으로 좁혀진 상태입니다. 이 절이 한동안 "아직 구현되지 않은 목표 상태"라고 적어뒀던 것 자체가 구현 완료 후 갱신되지 않아 실제 코드와 어긋나 있었습니다 — 아래 5장도 함께 정정했습니다.

---

## 3. "무르기" 초기 프로모션 — 그랜드파더링(선점 혜택 유지) 메커니즘

**목적**: 초도 발행 시점에 무르기를 무료로 체험/획득한 유저는, 이후 버전에서 무르기의 기본 정책이 광고/유료로 바뀌더라도 계속 무료로 쓸 수 있어야 한다. "먼저 써본 사람 손해 안 보게" 하는 선점 혜택입니다.

**핵심 설계 원칙 — 정책이 아니라 클레임 사실을 저장한다**:
게이팅 로직을 "지금 무르기가 기본으로 무료인가?"로 판정하면 안 됩니다. 미래에 기본값이 바뀌는 순간 과거 유저까지 같이 잠기기 때문입니다. 대신 **"이 유저가 무료 클레임을 받은 적이 있는가"라는 사실 자체를 로컬에 한 번 기록**하고, 게이팅은 그 기록을 OR 조건으로 확인해야 합니다.

```
로컬 저장: claimedFeatures = {"undo"}   // 클레임 시점에 1회 기록, 이후 절대 재평가 안 함

isUndoUnlocked =
    claimedFeatures.contains("undo")   // 그랜드파더링 — 정책이 바뀌어도 항상 true
    || premium.isActive                // 일반 프리미엄(영구구매/광고 1시간)
```

즉 "무르기가 오늘 기본으로 무료냐"를 다시 묻는 게 아니라 "이 사람이 예전에 무료 클레임을 받았느냐"만 확인하는 구조가 핵심입니다. 이건 지난번 검토에서 제안했던 "기능 활성화 원장(ledger)" 개념의 실제 사례이기도 합니다 — `claimedFeatures`가 그 원장의 한 항목이 되는 것.

**재설치 시 초기화 캐비앗**: `feature-access-principles/README.md` 6장 원칙("앱 내 활동/프로모션으로 얻은 기능 = 기기 로컬 저장, 재설치 시 초기화 고지")이 여기도 그대로 적용됩니다. 새 예외를 만들지 않습니다 — 그랜드파더링도 결국 로컬 저장이라, 재설치하면 클레임 기록도 함께 사라집니다. 클레임 팝업 문구에 "이 기기/앱 설치에 저장됩니다"를 명시하는 걸 권장합니다.

---

## 4. 초도 발행 체크리스트 (기능 유/무료 범위만)

- [x] 로그인 기능 OFF 유지 (`isLoginEnabled=false`)
- [x] 결제 UI OFF 유지 (`isPurchaseEnabled=false`)
- [x] `premium-mode/README.md` 기능 매트릭스를 현재 코드 상태(분석 삭제, 무르기 무료)에 맞게 정정 — 이번 문서 작업과 함께 처리(아래 "관련 문서" 갱신 이력 참고)
- [x] 무르기 클레임 버튼 UI + 로컬 그랜드파더링 플래그 구현 — 커밋 `5fc7b49`(2026-08-13)로 완료. 단, 저장 형태는 이 문서가 예시로 든 `claimedFeatures`류(기능별 원장)가 아니라 `PremiumState.isUndoClaimed: Boolean` 단일 플래그다 — 무르기 하나뿐이라 임시로는 충분하지만, 두 번째 클레임형 기능이 생기기 전에 원장으로 일반화하는 작업이 `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` "5/6계층 — 기능 엔타이틀먼트 정책 도입" 항목으로 남아 있다.
- [x] 클레임 프로모션 노출 위치/문구 확정 — 대국 화면 액션 버튼 영역(무르기 버튼 탭 시 인게임 다이얼로그), `ui/GamePlaySection.kt`의 `undoClaimTitle`/`undoClaimMessage` 문구로 구현됨. 아래 예시로 들었던 대국 설정 화면/설정 메뉴 "혜택" 섹션은 채택되지 않았다.
- [ ] 형세보기/추천수 영구 구매 버튼을 언제 켤지(= `isPurchaseEnabled=true` 전환 시점) 별도 결정

이 체크리스트는 "기능 유/무료" 범위로 의도적으로 좁혔습니다. 스토어 등록정보·심사 등 발행 전반의 나머지 체크리스트는 이 문서의 범위 밖입니다(필요해지면 별도 문서로).

---

## 5. 결정사항

### 이미 확정 (다른 문서에서 인용)
- 로그인/결제 UI를 이번 출시에서 끈다. (`auth-onboarding/README.md`, `premium-mode/README.md`, 2026-08-09)
- 로컬 저장 기반 기능은 재설치 시 초기화될 수 있음을 고지한다. (`feature-access-principles/README.md` 6장)

### 이 문서에서 신규 확정
- 형세 보기/추천 수는 초도 발행에서 **광고 1시간만** 제공하고, 영구 구매 버튼은 노출하지 않는다.
- 무르기는 초도 발행 유저에게 **무료 활성화 프로모션**을 제공하고, 이후 정책이 바뀌어도 **클레임한 유저는 그랜드파더링으로 계속 무료**로 유지한다. (`feature-access-principles/README.md`에서 미확정으로 남겨뒀던 "무르기를 다시 유료로 되돌릴지" 질문에 대한 답 — 단순 유료 전환이 아니라 "클레임+그랜드파더링" 구조로 확정)

### 2026-08-14 갱신 — 위 "확인 필요" 두 항목 해소
- 무르기는 커밋 `5fc7b49`(2026-08-13)로 바로 "클레임 필요" 상태로 좁혀졌습니다 — 당분간 게이팅 없이 유지하는 중간 단계 없이 곧장 전환.
- 클레임 UI는 대국 화면 액션 버튼 영역(무르기 버튼 탭 시 인게임 다이얼로그)으로 확정·구현됨. 대국 설정 화면/설정 메뉴 "혜택" 섹션 안은 채택되지 않았습니다.
- 다음 단계는 배치 그 자체가 아니라 저장 구조 일반화입니다 — `docs/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` "5/6계층 — 기능 엔타이틀먼트 정책 도입" 항목 참고 (단일 플래그 → 기능별 원장, 판정을 프레젠테이션에서 5계층으로 이동).

### 확인 필요 (아직 미정)
- 형세보기/추천수 영구 구매 버튼을 언제 켤지(= `isPurchaseEnabled=true` 전환 시점).

---

## 관련 문서
- `feature-access-principles/README.md` — 이 문서가 따르는 상위 원칙
- `premium-mode/README.md` — 기능 매트릭스는 이번 작업으로 정정됨(분석 행 제거, 무르기 무료 반영) — 상세 변경 이력은 그 문서의 "문서 이력"/날짜 절 참고
- `auth-onboarding/README.md` — 로그인 OFF 결정의 원본 근거
