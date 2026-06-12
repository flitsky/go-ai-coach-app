# 엔진 경계 및 빌드 산출물 분리 결정

작성일: 2026-05-31

## 결정

앱 개발과 KataGo 엔진 빌드는 강하게 분리한다.

앱 repo의 주 관심사는 다음으로 제한한다.

- 바둑 도메인 모델과 규칙 projection
- Android UI와 대국 UX
- `EngineAdapter` 계약
- 엔진 산출물 실행/참조에 필요한 최소 wiring
- 엔진 설정, lifecycle, timeout, 오류 처리

KataGo 자체 빌드는 앱 개발 루프에서 분리된 외부 산출물 생성 과정으로 관리한다.

## 이유

엔진 빌드는 초기에 ABI, backend, model/config 위치, Android 실행 제약을 확정하기 위해 중요하다.

하지만 그 결정이 안정화된 뒤에는 앱 기능 개발 대부분이 다음 영역에서 진행된다.

- 착수/사석/패/집계 등 shared 도메인 로직
- AI 대국 흐름
- 분석 결과 표시
- 난이도/성능 설정 UI
- SGF, 복기, 훈련 기능

따라서 앱 개발자가 매번 KataGo source, CMake, NDK, Eigen, native build cache를 직접 다루는 구조는 피한다.

## 경계 원칙

1. 앱 코드는 `EngineAdapter`만 의존한다.
2. UI와 shared 도메인 코드는 KataGo process, JNI, remote server 세부사항을 모른다.
3. 엔진 구현체는 `engine-android` 모듈 내부에 캡슐화한다.
4. KataGo source tree는 앱 repo에 vendoring하지 않는다.
5. 빌드된 engine binary/model/config는 앱이 참조하는 산출물로 취급한다.
6. 산출물 배치 방식은 Gradle task나 별도 release artifact fetch 단계로 추상화한다.
7. debug/release/product flavor별 엔진 산출물 선택은 앱 빌드 설정의 책임으로 둔다.
8. native binary와 model 파일은 기본적으로 git 추적 대상이 아니다.

## 선호 구조

장기적으로 다음 형태를 선호한다.

```text
go-ai-coach/
  shared/                 # 규칙, board state, move history, engine DTO
  engine-android/          # EngineAdapter 구현체와 Android process/JNI wiring
  app-android/             # Compose UI와 앱 lifecycle
  scripts/                 # 앱 개발 보조 스크립트, spike 재현 스크립트
  docs/                    # 결정/운영 문서

external engine artifact pipeline
  katago source/build       # 앱 repo 외부
  android arm64 binary      # 버전 고정 산출물
  model/config              # 버전 고정 산출물
```

앱 repo에는 산출물의 “생성 방법”과 “참조 방법”을 문서화하되, 엔진 source와 큰 binary는 넣지 않는다.

## 현재 스파이크 코드의 위치

현재 `scripts/build-katago-android-spike.sh`는 제품용 build pipeline이 아니라 검증 재현용이다.

이 스크립트의 역할은 다음을 증명하는 데 있다.

- Android `arm64-v8a` KataGo 빌드가 가능하다.
- emulator에서 GTP smoke test가 통과한다.
- 앱 process에서 실행 가능한 packaging 경로가 무엇인지 확인한다.

초기 결정이 확정되면 이 스크립트는 다음 중 하나로 정리한다.

- 별도 engine artifact repo/pipeline으로 이동
- CI artifact 생성 스크립트로 이동
- 앱 repo에서는 artifact download/copy task만 유지

## 릴리즈 패키징 결정

마켓 릴리즈 빌드는 debug seed 방식에 의존하지 않는다.

현재 `make install-dev-engine`의 model/config seed는 개발/검증용 편의 흐름이다. 제품 릴리즈에서는 사용자가 ADB seed를 수행할 수 없으므로, 최종 배포 artifact 안에 엔진 실행에 필요한 산출물이 포함되거나 앱이 공식 다운로드 UX로 가져올 수 있어야 한다.

권장 릴리즈 구조:

```text
Google Play AAB
  base module
    lib/arm64-v8a/libkatago.so      # executable native library
    app code / EngineAdapter wiring
  engine asset pack or base assets
    model.bin.gz                    # neural net model
    gtp.cfg                         # pinned config
```

원칙:

- `libkatago.so`는 executable code이므로 Play Asset Delivery asset pack이 아니라 Android native library 경로에 둔다.
- model/config는 data asset이므로 base assets 또는 Play Asset Delivery asset pack에 둘 수 있다.
- 첫 마켓 릴리즈에서는 모델 1개가 약 93MB이므로 install-time delivery가 현실적인 기본 후보이다.
- 모델이 커지거나 복수 모델을 제공하면 fast-follow/on-demand asset pack 또는 앱 내 모델 다운로드/검증 UX를 검토한다.
- debug APK만 설치했을 때 stub fallback이 되는 현재 구조는 개발용으로 유지할 수 있지만, release flavor에서는 model/config 누락 시 빌드가 실패해야 한다.
- release build는 `engine-artifacts.lock` 같은 metadata로 binary/model/config checksum과 버전을 검증해야 한다.

따라서 "엔진 빌드는 앱 개발 루프에서 분리"한다는 결정은 유지하되, "릴리즈 artifact에는 엔진 실행 산출물이 포함"되어야 한다. 분리 대상은 빌드 과정/source tree이지, 사용자에게 전달되는 runtime 산출물이 아니다.

## 다음 작업에 주는 영향

사석 처리 같은 핵심 바둑 규칙은 엔진 구현체가 아니라 `shared` 도메인 로직에 둔다.

엔진은 다음 책임을 갖는다.

- 합법 착수 검증 보조
- AI 응수 생성
- 분석/평가 제공
- 성능 profile 적용

앱은 다음 책임을 유지한다.

- move history 관리
- board projection 관리
- UI 즉시 반영
- undo/replay/SGF import/export의 기준 상태 유지

이 구조가 있어야 나중에 `StubEngineAdapter`, process KataGo, JNI KataGo, remote KataGo를 바꿔도 앱 기능 개발이 흔들리지 않는다.
