# 릴리즈 이후 Repository 운영 의사결정 초안

작성일: 2026-05-31

## 논의 배경

첫 마켓 릴리즈 이후에는 “현재 개발자가 빠르게 기능을 붙이는 상황”보다 다음 상황이 더 중요해진다.

- 새 장비에서 주 개발자가 다시 셋업한다.
- 다른 개발자가 프로젝트를 인수인계 받는다.
- 릴리즈 빌드 재현성이 필요하다.
- 엔진 빌드와 앱 기능 개발이 서로 방해하지 않아야 한다.
- 코드 라인 수와 기능 수가 늘어도 도메인 경계가 흐려지지 않아야 한다.

## 공식 가이드에서 가져올 원칙

Android 공식 architecture guide는 앱이 커질수록 책임과 경계를 명확히 정의해야 한다고 설명한다. UI는 데이터 모델로부터 구동하고, 단일 source of truth와 unidirectional data flow를 권장한다.

Android 공식 modularization guide는 multi-module project를 권장하면서 다음 이점을 강조한다.

- loosely coupled, self-contained module
- strict visibility control
- scalability
- ownership
- encapsulation
- testability

Common modularization patterns는 low coupling / high cohesion을 기본 원칙으로 둔다. 또한 dependency inversion을 통해 API module과 implementation module을 분리하고, feature는 구현체가 아니라 abstraction에 의존하도록 권장한다.

Gradle 공식 문서는 multi-project build를 “작고 집중된 모듈을 함께 빌드/테스트/릴리즈하는 구조”로 설명한다. 빌드 로직이 커지면 `buildSrc`보다 composite `build-logic`이 더 확장성 있는 선택지가 될 수 있다.

## 결정 후보 1: 엔진 빌드 완전 외부화

앱 repo는 엔진 source/build script를 거의 갖지 않고, 사전에 만들어진 binary/model/config artifact만 받는다.

장점:

- 앱 개발 루프가 가장 빠르다.
- NDK/CMake/Eigen/KataGo source 이슈가 앱 개발자를 방해하지 않는다.
- 릴리즈 빌드에서 artifact checksum 기반 검증이 쉽다.
- 엔진 팀/앱 팀 분리가 가능하다.

단점:

- 새 개발자가 artifact 저장소 접근 권한을 얻어야 한다.
- artifact가 사라지거나 접근 불가하면 셋업이 막힌다.
- 엔진 재빌드가 필요할 때 별도 문맥 전환이 크다.

## 결정 후보 2: 앱 repo에서 `make prebuild`로 엔진 빌드

앱 repo가 pinned 설정으로 KataGo를 빌드하고 결과물을 앱 참조 폴더에 넣는다.

장점:

- 새 환경에서도 한 명령으로 복구 가능하다.
- artifact 생성 과정이 투명하다.
- 외부 artifact 저장소가 없어도 재현 가능하다.

단점:

- 앱 repo가 NDK/CMake/native build complexity를 계속 떠안는다.
- 개발자가 기능 개발 중 불필요하게 긴 엔진 빌드와 마주칠 수 있다.
- 릴리즈 빌드가 로컬 환경 차이에 영향을 받을 수 있다.

## 권장안: artifact-first + pinned prebuild fallback

최종 권장안은 둘 중 하나만 고르는 방식이 아니다.

기본 개발 루프는 prebuilt artifact를 받는 방식으로 유지하고, `make prebuild-engine`은 pinned 설정으로 artifact를 복구하는 fallback으로 둔다.

```text
make doctor
  - JDK / Android SDK / NDK / adb / required artifact 존재 확인

make fetch-engine
  - 확정된 engine binary/model/config artifact 다운로드 또는 사내 저장소/릴리즈에서 복사
  - checksum 검증
  - 앱 참조 위치에 배치

make prebuild-engine
  - artifact가 없거나 엔진을 재생성해야 할 때만 사용
  - pinned KataGo version / NDK / CMake / Eigen / backend / ABI로 빌드
  - 산출물 checksum 생성
  - 앱 참조 위치에 배치

make dev
  - artifact 존재와 checksum을 먼저 확인
  - 없으면 자동 native build를 하지 않고 `make fetch-engine` 또는 `make prebuild-engine` 안내
  - Android debug build/test 실행

make release
  - release용 artifact와 checksum이 없으면 즉시 실패
  - 로컬에서 암묵적으로 엔진을 빌드하지 않음
```

이 구조가 릴리즈 이후 운영에는 가장 균형이 좋다.

새 개발자는 `make doctor`와 `make fetch-engine`으로 빠르게 시작할 수 있다. artifact 접근이 막히거나 재현성이 필요하면 `make prebuild-engine`으로 pinned 빌드를 수행한다. 하지만 일반 앱 개발 중 `make dev`가 긴 native build를 갑자기 수행하지는 않는다.

## 산출물 관리 원칙

엔진 산출물은 git에 넣지 않는다.

대신 다음 metadata를 git에 넣는다.

```text
engine-artifacts.lock
  katago_version
  katago_commit
  backend
  abi
  min_sdk
  ndk_version
  cmake_version
  eigen_version
  binary_sha256
  model_name
  model_sha256
  config_sha256
```

로컬 산출물 위치는 ignore한다.

```text
artifacts/engine/android/arm64-v8a/libkatago.so
artifacts/engine/model/model.bin.gz
artifacts/engine/config/gtp.cfg
app-android/src/debug/jniLibs/arm64-v8a/libkatago.so
```

앱은 직접 파일 경로를 흩뿌리지 않고 Gradle source set 또는 `engine-android`의 artifact resolver를 통해 참조한다.

## 도메인 분리 운영 원칙

초기에는 모듈 수를 과하게 늘리지 않는다. 대신 코드가 커질 때 나눌 기준을 미리 정한다.

1차 구조:

```text
shared
  - rules
  - game record / move history
  - board projection
  - engine DTO / EngineAdapter API

engine-android
  - StubEngineAdapter
  - KataGoProcessEngineAdapter
  - future JNI / remote adapter
  - artifact resolver

app-android
  - Compose UI
  - ViewModel/state holder
  - Android lifecycle
```

규모가 커질 때 분리 후보:

```text
shared-rules
shared-sgf
shared-analysis
engine-api
engine-katago-process
feature-game
feature-review
feature-settings
core-ui
core-testing
```

분리 trigger는 단순 line count 하나로만 잡지 않는다.

다음 중 2개 이상이 동시에 발생하면 분리를 검토한다.

- 한 모듈이 3,000~5,000 LOC를 넘고 변경 이유가 3개 이상으로 갈라진다.
- 같은 모듈에 UI, engine transport, rules, persistence가 섞인다.
- 테스트가 느려져서 특정 도메인만 독립 실행하기 어렵다.
- 한 기능 변경이 무관한 기능 파일을 반복적으로 건드린다.
- `internal`로 숨겨야 할 구현이 public으로 새고 있다.
- 순환 의존 또는 양방향 참조가 생긴다.

## 과도한 도메인 분리 방지 원칙

모듈을 나누는 것만큼 합치는 것도 운영 원칙으로 둔다.

월 1회 또는 큰 기능 release 전 다음을 점검한다.

- 모듈당 public API가 실제로 필요한가?
- 1~2개 클래스뿐인 모듈이 너무 많지 않은가?
- 서로 항상 같이 변경되는 모듈이 분리되어 있지 않은가?
- common/util이 의미 없는 잡동사니가 되고 있지 않은가?
- build time과 IDE sync time이 모듈 수 증가로 악화되지 않았는가?

합칠 기준:

- 두 모듈이 3회 이상 같은 PR에서 함께 변경된다.
- 한 모듈 public API의 소비자가 1개뿐이고 앞으로도 늘 가능성이 낮다.
- 분리 때문에 테스트/빌드/이해 비용이 줄지 않고 늘어난다.

## 추상화와 주입 방향

추상화는 “미래에 바뀔 것 같은 모든 것”에 만들지 않는다.

다음 조건에서는 abstraction module 또는 interface를 둔다.

- 구현체가 실제로 2개 이상 있다.
- test double이 필요하다.
- platform별 구현이 다르다.
- 릴리즈 flavor별로 구현이 바뀐다.
- 외부 시스템이나 native/process boundary를 감싼다.

현재 이 조건을 만족하는 대표 경계는 `EngineAdapter`다.

권장 의존 방향:

```text
app-android
  -> feature modules
  -> shared domain / engine-api
  -> implementation is injected at app boundary

engine-katago-process
  -> engine-api

engine-stub
  -> engine-api
```

Android DI는 지금 당장 Hilt를 도입하지 않아도 된다. 하지만 feature와 engine 구현체가 늘어나는 시점에는 Hilt 또는 lightweight manual DI를 명확한 composition root로 둔다.

## 최종 권장 결정

1. `make dev`와 `make release`는 엔진 artifact가 없으면 친절한 메시지로 실패한다.
2. `make fetch-engine`을 기본 artifact 준비 명령으로 둔다.
3. `make prebuild-engine`은 fallback이며 pinned 설정으로만 동작한다.
4. release build는 로컬에서 엔진을 암묵적으로 빌드하지 않는다.
5. 도메인 분리는 low coupling / high cohesion 기준으로 진행한다.
6. API와 구현 분리는 실제로 구현체가 갈리는 경계에 우선 적용한다.
7. 모듈 수가 늘어나면 정기적으로 통합 후보를 검토한다.

## 참고 자료

- Android app architecture: https://developer.android.com/topic/architecture
- Android modularization guide: https://developer.android.com/topic/modularization
- Android common modularization patterns: https://developer.android.com/topic/modularization/patterns
- Gradle multi-project builds: https://docs.gradle.org/current/userguide/multi_project_builds.html
- Gradle structuring multi-project builds: https://docs.gradle.org/current/userguide/multi_project_builds_intermediate.html
