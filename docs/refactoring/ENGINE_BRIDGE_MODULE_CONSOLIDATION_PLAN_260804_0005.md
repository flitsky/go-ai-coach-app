# 엔진 브릿지 모듈 통합 착수 계획서 — 260804 00h05m

작성 시각: 2026-08-04 00:05 (KST)

## 0. 배경과 목표

`LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md`의 D-1/D-2에서 `RemoteEngineCoreApiAdapter`
(`EngineCoreApi`의 원격 구현체)를 만들었지만, 물리적으로는 `app-android/middleware/`에 있었고
로컬 구현체(`KataGoProcessEngineAdapter`)는 `engine-android` 모듈에 따로 있었다 — 즉 1~2계층
(엔진의 로컬/원격 구현)이 코드상 두 곳에 흩어져 있었다.

사용자 요청 배경: 향후 실제 원격 서버/DePIN(다른 사용자 기기의 연산력 제공) 확장의 근간이
되도록, **엔진의 로컬/원격 구현체를 전부 `engine-android` 모듈 하나로 물리적으로 합친다.**
이렇게 하면 3~7계층(app-android) 작업 시 엔진 내부 구현을 아예 볼 필요가 없어지고(지금은
grep 기반 `LayeringContractTest`로만 막던 경계가 Gradle 모듈 경계 + Kotlin `internal`로
컴파일 타임에 강제됨), 나중에 원격/DePIN 후보 선택 로직(Stage E)이나 실제 피어 프로토콜
(Stage F)을 얹을 때도 "엔진 구현체가 있는 자리"가 이미 하나로 정리돼 있다.

**새 모듈을 만들지 않는다** — `:engine-android`가 이미 로컬 구현체의 집이므로, 원격 구현체를
그리로 옮겨 합치는 것으로 충분하다. 이름 변경(`:engine-bridge` 등)은 이번 범위 밖(§4 참고).

## 1. 조사 결과 요약

- `RemoteEngineCoreApiAdapter.kt`(`EngineCoreApi` 원격 구현체)와 `HttpRemotePositionAnalysisTransport.kt`
  (HTTP 전송 스파이크)는 서로만 참조하고 app-android의 다른 어떤 코드도 이 둘을 아직 참조하지
  않는다(grep 확인) — 배선 전이라 이동 리스크가 낮다.
- `RemotePositionAnalysisGateway.kt`(app-android, 3계층에 가까운 어댑터)는 `HttpRemotePositionAnalysisTransport`가
  구현하는 `RemotePositionAnalysisTransport` 인터페이스 + DTO(`RemotePositionAnalysisRequest`/
  `Response`)를 같은 파일에 갖고 있다. 이 인터페이스/DTO는 전부 `:shared`의 KMP-safe 타입
  (`GameState`/`AnalysisLimit`/`AnalysisResult`/`EngineSearchMode`)만 사용해 이미
  KMP-ready하다 — `LayeringContractTest.positionAnalysisGatewayContractsStayKmpReadyAndTransportFree`의
  에러 메시지("KMP-ready ... before the middleware module split")가 정확히 이 상황을 예견하고
  있었다. **결론**: 이 인터페이스/DTO를 `:shared`로 옮기면, app-android(Gateway)와
  engine-android(Http 구현체)가 순환 의존 없이 같은 계약을 공유할 수 있다.
- `JSONObject.optNullable*` 헬퍼(`middleware/JsonNullableExtensions.kt`)는 app-android의
  persistence 스토어들도 쓰고 있어 그대로 두고, engine-android로 이동하는 파일들을 위한 작은
  사본을 engine-android 쪽에 새로 둔다(이 세션 앞부분에서 `KataGoJsonAnalysisParser.kt`의
  동일 헬퍼를 굳이 통합하지 않기로 한 것과 같은 이유 — 모듈 경계를 넘는 공유 인프라를 새로
  만들 만큼 크지 않음).
- `engine-android` 테스트는 `kotlin.test`(JUnit4 브리지) 관례를 쓰지만, `kotlin("test")`가
  JUnit4를 전이 의존성으로 가져오므로 이동하는 테스트가 쓰는 `org.junit.*`(app-android와
  동일 스타일)도 그대로 컴파일/실행된다(실제 `make test`로 검증).

## 2. 이동 계획

| 파일 | 현재 위치 | 새 위치 | 비고 |
| --- | --- | --- | --- |
| `RemotePositionAnalysisTransport`(interface) + `RemotePositionAnalysisRequest`/`Response`(DTO) | `app-android/.../middleware/RemotePositionAnalysisGateway.kt`(일부) | `shared/.../shared/RemotePositionAnalysisTransport.kt`(신규) | `RemotePositionAnalysisGateway` 클래스는 그대로 두고 계약만 분리 |
| `HttpRemotePositionAnalysisTransport.kt` | `app-android/.../middleware/` | `engine-android/.../engine/android/` | 패키지 `middleware`→`engine.android` |
| `RemoteEngineCoreApiAdapter.kt` | `app-android/.../middleware/` | `engine-android/.../engine/android/` | 패키지 `middleware`→`engine.android` |
| `JsonNullableExtensions.kt` | (app-android 원본 유지) | engine-android에 작은 사본 신규 | 의도적 중복(§1 참고) |
| 관련 테스트 | `app-android/.../middleware/RemoteEngineCoreApiAdapterTest.kt`, `RemotePositionAnalysisGatewayTest.kt`(Http 부분만) | `engine-android/.../engine/android/` | Gateway 자체 테스트는 app-android에 잔류 |

## 3. 완료 정의

- [x] `RemotePositionAnalysisTransport`/DTO가 `:shared`로 이동, app-android/engine-android 양쪽에서 순환 없이 참조
- [x] `HttpRemotePositionAnalysisTransport`/`RemoteEngineCoreApiAdapter`가 `engine-android`로 물리적 이동(패키지 `com.worksoc.goaicoach.engine.android`)
- [x] 관련 테스트 이동/분리, 전부 통과
- [x] `LayeringContractTest.kt`가 새 위치를 반영(옛 경로 참조 제거, "app-android에 더 이상 없음"을 양성 검증하는 `engineImplementationsLiveInEngineAndroidNotAppAndroid` 신설)
- [x] `make test` 전체(3개 모듈) 통과

## 4. 이번 범위 밖(후속 과제로 명시)

- ~~`KataGoProcessEngineAdapter`/`StubEngineAdapter`를 `internal`로 낮추고 팩토리 함수만 공개~~ —
  260804에 완료(§5 진행 로그). `EngineCoreApiFactory`(engine-android, public)가 유일한 생성
  지점이고, app-android는 이제 구현 클래스 이름조차 컴파일 타임에 볼 수 없다.
- `:engine-android` 모듈 이름을 `:engine-bridge` 등으로 변경 — 순수 명명 변경이라 리스크 대비
  효용이 낮아 보류. 이름이 실제로 헷갈리기 시작하면 그때 별도로.
- Stage E(`RemoteEngineSessionClient`, 원격 후보 선택/신뢰도 판단)/Stage F(실제 물리 분산·DePIN) —
  `LAYERED_ARCHITECTURE_REFACTORING_PLAN`의 원칙대로 별도 사용자 승인 필요. 이 문서는 그 전제
  조건(엔진 구현체가 물리적으로 한 곳에 있음)만 마련한다.

## 5. 진행 로그

- 260804 00h05m — 계획서 최초 작성 및 조사 완료. 착수.
- 260804 — 이동 완료.
  - `shared/.../shared/RemotePositionAnalysisTransport.kt`(신규) — `RemotePositionAnalysisTransport`/`Request`/`Response`를 app-android/middleware의 `RemotePositionAnalysisGateway.kt`에서 분리해 이동. `RemotePositionAnalysisGateway`(app-android, 잔류)는 이제 이 계약만 알고 실제 구현체 이름은 모른다.
  - `engine-android/.../engine/android/HttpRemotePositionAnalysisTransport.kt`, `RemoteEngineCoreApiAdapter.kt`(신규, 패키지 `middleware`→`engine.android`) — app-android/middleware에서 물리적으로 이동. `JsonNullableExtensions.kt` 사본을 engine-android에 신규 작성(app-android 원본은 그대로 유지, 모듈 경계상 의도적 중복).
  - 테스트: `RemoteEngineCoreApiAdapterTest.kt` 전체 이동. `RemotePositionAnalysisGatewayTest.kt`는 Gateway 자체 테스트만 app-android에 남기고, Http 트랜스포트 테스트 2개는 신규 `HttpRemotePositionAnalysisTransportTest.kt`로 engine-android에 이동.
  - `LayeringContractTest.kt`: 옛 경로를 참조하던 `httpRemoteAnalysisTransportStaysOutOfKmpReadyGatewayContracts`를 `engineImplementationsLiveInEngineAndroidNotAppAndroid`로 교체 — 새 위치 존재 + 옛 app-android 경로 부재를 양쪽 다 검증(양성 검증, Stage A 원칙).
  - 사소한 충돌: `RemotePositionAnalysisGateway.kt`의 새 KDoc이 실수로 "HttpRemotePositionAnalysisTransport"라는 이름을 프로즈에 적어 `positionAnalysisGatewayContractsStayKmpReadyAndTransportFree`의 순수 문자열 매치(주석/코드 구분 안 함)에 걸림 — 이름을 적지 않는 방향으로 문구 수정.
  - `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 2계층 위치/재편여부/핵심 갭, "알려진 갭", "고도화 로드맵" 1번을 새 상태로 갱신(이 문서는 "지금 무엇이 어디 있는가"를 담는 문서라 물리적 이동을 반영해야 함).
  - `make test` 전체 통과 확인(BUILD SUCCESSFUL) — `shared`/`engine-android`(신규 테스트 12개 포함)/`app-android`(LayeringContractTest 42개 포함) 전부.
  - 이번 범위에서 하지 않은 것(§4 그대로 유효): 가시성 강화(internal + 팩토리), 모듈 이름 변경, Stage E/F.
- 260804 — 가시성 강화 완료(§4 후속 과제 중 첫 번째). `KataGoProcessEngineAdapter`/`StubEngineAdapter`를 `internal class`로 낮추고, 신규 `engine-android/.../engine/android/EngineCoreApiFactory.kt`(public object, `local(config)`/`stub()` 두 함수)를 유일한 생성 지점으로 노출. `KataGoProcessConfig`(순수 설정 DTO, 파일 경로/오버라이드만 담음)는 public으로 유지 — app-android가 asset 탐색으로 얻은 경로를 여전히 전달해야 하므로 숨길 이유가 없다. app-android의 `EngineBootstrap.kt`(엔진 부트스트랩 판단: 에셋 없으면 stub, 있으면 local)가 `KataGoProcessEngineAdapter(...)`/`StubEngineAdapter()` 직접 호출 대신 `EngineCoreApiFactory.local(...)`/`.stub()` 호출로 교체 — 이제 이 두 클래스 이름은 Kotlin 컴파일러 차원에서 app-android에 아예 안 보인다(grep 테스트가 아니라 컴파일 에러로 강제). `LayeringContractTest.engineCoreApiConcreteAdaptersStayInternalBehindFactory` 신설 — internal modifier가 실수로 지워지지 않았는지, `EngineCoreApiFactory`가 실제로 public한지, `EngineBootstrap.kt`가 팩토리만 쓰는지 소스 레벨로도 확인. `RemoteEngineCoreApiAdapter`는 Stage D부터 이미 internal이었고 아직 배선 전이라 추가 변경 없음. `make test` 통과 확인(BUILD SUCCESSFUL, `LayeringContractTest` 43개).
