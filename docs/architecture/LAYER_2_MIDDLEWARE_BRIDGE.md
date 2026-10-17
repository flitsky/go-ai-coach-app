# 2계층 — Middleware / Bridge

go-ai-coach에서 이 계층이 **지금 어디에 있고, 무엇이 지키며, 무엇이 남았는가.** 정의와 경계 원칙은 `ARCHITECTURE.md` 「2계층 — Middleware / Bridge」 한 곳에만 있어 여기서 반복하지 않는다. 전체 지도·모듈 지도·계층을 가로지르는 갭은 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`.

> 2026-10-17에 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`의 「2계층」 절과 「알려진 갭」·「고도화 로드맵」의 2계층 항목을 **문장 그대로** 옮겨 만들었다(계층 단위로 추적하려고). 옮기기 전 이력은 그 파일의 `git log -p`에, 이후 이력은 이 파일의 `git log`에 있다. 「갭과 남은 일」의 번호는 옛 「고도화 로드맵」 번호 그대로다.

## 계층 배정

아래 블록은 `ContractSymbols.kt`의 `LAYER_2_PACKAGES`를 그대로 옮긴 **사본**이다(접두 `com.worksoc.goaicoach.` 생략). 정본은 코드 쪽이고, `LayerDocContractTest`가 둘이 양방향으로 같은지 본다 — 패키지를 새로 만들거나 옮기면 코드 목록을 고치고, 빨개지면 이 블록을 따라 고친다.

<!-- layer-packages: LAYER_2_PACKAGES -->
```text
shared.enginecontract
```

`:shared`·`:core:*` 밖의 2계층(`engine-android`의 어댑터, `app-android/.../middleware/`)은 코드 목록이 없다 — 아래 「지금 위치」가 정본이다.

## 지금 위치

**위치**:
- `core/enginecontract/src/commonMain/kotlin/com/worksoc/goaicoach/shared/enginecontract/EngineModels.kt`(2026-09-28 refactor backlog #49로 `:core:enginecontract` 모듈) — `EngineCoreApi` 인터페이스(1:1 원시 계약: `initialize`, `configure`, `playMove`, `analyze`, `estimateScore`, `deadStones`, `scoreFinal`, `clearSearchCache`, `stop`, `forceReset` 등), `AnalysisLimit`/`EngineProfile`/`CandidateMove` 등 순수 데이터 모델. 같은 폴더의 `RemotePositionAnalysisTransport.kt` — position-analysis 단위 원격 호출 계약(`RemotePositionAnalysisTransport`/`Request`/`Response`, 260804 이전엔 app-android에 있었음, §재편 여부 참고)
- `engine-android/.../KataGoProcessEngineAdapter.kt` — `EngineCoreApi`의 **로컬** 구현체. GTP(`KataGoGtpAnalysisClient.kt`, `KataGoProtocolCommands.kt`)와 JSON(`KataGoJsonPositionAnalysisClient.kt`, `KataGoJsonAnalysisQueryFactory.kt`, `KataGoJsonAnalysisParser.kt`) 두 경로를 조율. 두 경로 공통 파싱은 `KataGoAnalysisParser.kt`/`KataGoAnalysisContext.kt`
- `engine-android/.../StubEngineAdapter.kt` — `EngineCoreApi`의 **스텁** 구현체(엔진 없이 UI/도메인 검증용)
- `engine-android/.../RemoteEngineCoreApiAdapter.kt` — `EngineCoreApi`의 **원격** 구현체(13개 메서드 전체, 260803 Stage D). 상태변경 호출은 로컬에서 `GameState`를 추적하고, `genMove`/`analyze`/`estimateScore`/`deadStones`/`scoreFinal`만 원격 전송하는 상태 비저장 설계. `HttpRemoteEngineOperationTransport`가 HTTP 구현체
- `engine-android/.../HttpRemotePositionAnalysisTransport.kt` — `RemotePositionAnalysisTransport`의 read-only position-analysis 단위 트랜스포트 스파이크 구현체(2026-06-28 기준 기본값 off)
- `app-android/src/main/java/com/worksoc/goaicoach/middleware/RemotePositionAnalysisGateway.kt` + 같은 폴더의 `PositionAnalysisGateway.kt`/`JsonNullableExtensions.kt` — 위 트랜스포트를 3계층 `PositionAnalysisGateway` 계약으로 감싸는 어댑터(app-android에 잔류, `:shared`의 `RemotePositionAnalysisTransport`만 알고 실제 구현체 이름은 모름)

**재편 여부**: 기존 2계층(Engine Core API Domain, 계약 정의만)에 기존 4계층(Middleware/Cache Domain)의 **전송** 절반(원격 게이트웨이/트랜스포트)을 합쳤다. "계약을 정의하는 것"과 "그 계약을 실제로 어떻게 도달시키는가(로컬 stdio냐 원격 HTTP냐)"가 개념적으로 같은 책임이라고 보기 때문이다. **260804 정리**: `EngineCoreApi`의 로컬/원격 구현체를 전부 `engine-android` 모듈로 물리적으로 모았다(그 전엔 원격 구현체가 app-android/middleware에 있었음) — app-android(3~7계층) 작업 시 엔진 내부 구현을 아예 안 봐도 되도록, 그리고 향후 원격/DePIN 확장의 물리적 근간이 되도록. 이 이동을 가능케 하려고 `RemotePositionAnalysisTransport`/`Request`/`Response`(전부 `:shared`-safe 타입만 사용)도 `:shared`로 옮겼다 — app-android(Gateway)와 engine-android(Http 구현체)가 순환 의존 없이 같은 계약을 공유하기 위함.

**핵심 갭(해소됨, 260803 Stage D)**: `KataGoProcessEngineAdapter`(로컬)와 `RemoteEngineCoreApiAdapter`(원격)가 이제 `EngineCoreApi` 전체에 대해 대등한 계약을 만족한다(계약 테스트로 검증). 3계층의 후보 선택/신뢰도 판단(`selectRemoteEngineCandidate`)도 260804 Stage E-1/E-2에서 마련됐다 — `LAYER_3_EXTENDED_API.md` 참고. **260818 갱신**: 이 원격 경로는 이제 `MainActivity`에 `BuildConfig.DEBUG` 한정으로 배선돼 있다(Stage E-3 — refactor backlog #110부터 그 조립은 `GoCoachProcessRuntime`이 한다) — `local.properties`의 `debug.remoteEngineUrl`을 맥북 참조 서버(`engine-lab/remote/run-katago-remote-analysis-server.py`)로 가리키면 실제 대국이 그 서버를 왕복한다(에뮬레이터 e2e 검증 완료). 기본값은 꺼짐이며, 앱 시작 시 한 번만 원격/로컬을 고르고 런타임 실패를 감지해 되돌리지 않는 한계가 남아 있다. 그 재설계와 MQ 전송 전환은 `work/roadmap/archive/260818-_REMOTE_ENGINE_MQ_TRANSPORT.md`가 이어받았다 — Stage F(실제 물리 분산) 영역이라 앱 이식은 여전히 별도 승인 필요.

## 지키는 테스트

- `LayeringContractTest.uiAndPresentationDoNotImportRawEngineCoreApi` · `matchPoliciesDoNotImportRawEngineCoreApi` — 원시 계약(`EngineCoreApi`)을 화면·대국 정책이 직접 쥐지 않는다.
- `engine-android`의 `RemoteEngineCoreApiAdapterTest` — 원격 구현체의 상태 추적·전송·응답 해석, 그리고 시간 초과·탐색 대기를 로컬 어댑터와 같은 규칙으로 다루는지.

## 갭과 남은 일

### 열려 있는 것

- **원격 코덱이 덤(komi)과 접바둑 돌 수를 보내지 않는다** — `HttpRemotePositionAnalysisTransport.kt`의 `encodeState`(같은 코덱을 쓰는 `RemoteEngineCoreApiAdapter`도). 2026-10-17에 코드로 다시 확인했다. 원격은 디버그 전용이라 지금은 터지지 않지만 Stage F 착수 전 선결 조건이다 — 발견 경위는 `REMOTE_ENGINE_AND_LAYERING.md` §2.

### 닫힌 것

1. ~~**2계층 — 로컬/원격 계약 대등화**~~ — 완료(260803 Stage D, 260804 물리적 모듈 통합). `RemoteEngineCoreApiAdapter`가 `EngineCoreApi` 전체를 구현하고, 로컬(`KataGoProcessEngineAdapter`)과 실패/타임아웃/재시도 신뢰도가 동등함을 계약 테스트로 검증했으며, 둘 다 `engine-android` 모듈에 물리적으로 함께 있다.

- ~~2계층의 로컬/원격 구현체가 대등하지 않다~~ — 260803 Stage D에서 해소. 260804에 물리적으로도 `engine-android` 한 모듈로 모았다.
