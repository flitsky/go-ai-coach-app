# 7계층 아키텍처 구조 심층 분석 리포트

작성일: 2026-06-14  
목적: `go-ai-coach` 프로젝트가 지향하는 7계층 도메인 격리 아키텍처에 맞춰 각 레이어의 구현 현황을 심층 분석하고, 하위 계층과 상위 계층 간의 의존성 격리로 발생할 수 있는 우려사항을 인터페이스 설계를 통해 선제적으로 극복하기 위함입니다.

---

## 1계층: Engine Runtime / Transport

### ① 개요 및 주요 컴포넌트
* **책임**: 로컬 OS 레벨에서 KataGo 바이너리/프로세스의 실행 파일을 안전하게 포크(Fork)하고 입출력 스트림(GTP/JSON I/O Transport)을 유지 및 검증합니다.
* **주요 소스 파일**:
  * [KataGoProcessConfig](file:///Users/ryan9kim/worksoc/go-ai-coach/engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProcessRuntime.kt): 실행 파일 및 가중치 모델의 검증 및 CLI 인수 빌드
  * [KataGoProcessEngineAdapter](file:///Users/ryan9kim/worksoc/go-ai-coach/engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProcessEngineAdapter.kt): `EngineCoreApi`를 구현하며 실제 `Process` 입출력 파이프 스트림 조율

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **OS 자식 프로세스 누수 (Zombie Process)**:
   * *우려*: 1계층은 상위 대국 UX의 수명 주기를 모르기 때문에, 사용자 앱 강제 종료 시 백그라운드에 자식 프로세스가 영구 잔존할 위험이 있습니다.
   * *극복*: `EngineCoreApi.stop()`을 명시적 라이프사이클 종료 규격으로 제공하고, 6계층(App Service)의 리소스 정리 라이프사이클과 연동하여 안전하게 프로세스를 `destroy()`하도록 책임을 격리했습니다.
2. **GTP Stateful 상태 오염**:
   * *우려*: 로컬 KataGo는 내부 보드 상태를 누적 관리하므로, 무르기나 강제 복원 시 1계층 내부 보드 상태와 실제 상위 도메인 상태가 어긋날 수 있습니다.
   * *극복*: 1계층은 수순 히스토리를 추적하지 않습니다. 상위 계층이 [GameStateReplayer](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/GameStateReplayer.kt)를 통해 매번 안전한 전체 수순을 1계층으로 전달하고, 1계층은 이를 단순히 순차 동기화(Replay)하는 수동적 수신자 역할만 수행하여 상태 불일치를 원천 차단합니다.

---

## 2계층: Engine Core API Domain

### ① 개요 및 주요 컴포넌트
* **책임**: 엔진이 플랫폼 환경에 종속되지 않고 공통적으로 제공할 수 있는 순수한 원시 기능(Primitive API)을 1:1 인터페이스 계약으로 정의합니다.
* **주요 소스 파일**:
  * [EngineCoreApi](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/EngineModels.kt): `initialize`, `configure`, `playMove`, `analyze`, `deadStones` 등 12개 원시 엔진 기능 인터페이스 정의
  * [EngineModels.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/EngineModels.kt): 분석용 제한 모델(`AnalysisLimit`), 난이도 모델(`DifficultyProfile`), 분석 상태/응답 데이터 구조 정의

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **글로벌 시간 설정 오염 (Time Cap Mismatch)**:
   * *우려*: KataGo의 `maxTime` 같은 설정은 프로세스 전역에 영향을 미칩니다. 상위 호출부가 특정 분석 시 `timeMillis = null`(제한 없음)로 요청을 전송한 뒤 이를 명시적으로 초기화하지 않으면, 다음 분석 호출에서도 무제한 대기하는 설정 오염이 발생합니다.
   * *극복*: 인터페이스 계약(KDoc)을 통해 `timeMillis = null` 호출은 정책적 결정에 따르며, 1계층 구현체가 전역 설정을 명시적으로 리셋/덮어쓰기하도록 강제 지침을 규격화하였습니다.
2. **장시간 차단 연산(Dead-stone/Final Score)에 의한 UI 정지**:
   * *우려*: 종국 판단 및 사석 감지 연산은 바둑판 크기와 국면에 따라 수십 초까지 대기할 수 있어, 메인 대국 루프와 UI를 완전히 멈추게 만들 수 있습니다.
   * *극복*: 인터페이스 계약에 **부심(Assistant Judge)** 정책(Endgame SLA 5초 이내에 강제 복구)과 **주심(Chief Judge)** 정책(사용자가 명시적 이의 제기를 할 때만 무제한 분석 수행)의 설계를 가이드하였습니다. 이로써 2계층 호출부는 타임아웃/격리를 강제할 수 있도록 계약 문서화되어 있습니다.

---

## 3계층: Core Rules Domain

### ① 개요 및 주요 컴포넌트
* **책임**: 바둑판의 크기, 착수 제한, 합법수 추출, 사석 판정, 사석 청소 및 계가 공식 등 플랫폼과 UI 기술에 의존하지 않는 순수 바둑 규칙 모델을 정의합니다. (Kotlin Multiplatform Core)
* **주요 소스 파일**:
  * [BoardModels.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/BoardModels.kt): `Board`, `StoneColor`, `Move` 등 핵심 도메인 데이터 클래스
  * [BoardRules.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/BoardRules.kt): 합법수 체크, 자충 및 패 검증 규칙
  * [BoardScorer.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/BoardScorer.kt): 영역 계가(Scoring) 및 사석 정리 정책

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **모바일 단말기 내 순수 규칙 연산 오버헤드**:
   * *우려*: 착수가 일어날 때마다 모든 보드 좌표를 순회하며 합법수를 체크하거나 패(Ko) 상태를 판별하는 등 무거운 연산이 발생하여 저사양 단말기에서 병목이 생길 수 있습니다.
   * *극복*: 3계층은 플랫폼/프레임워크가 없는 순수 Kotlin 코드로 빌드되어 최적의 연산 속도를 보장합니다. 또한 좌표 순회 책임을 단일 헬퍼로 응축하고, 합법수 재생성 테스트 케이스([LegalMoveGeneratorTest](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonTest/kotlin/com/worksoc/goaicoach/shared/LegalMoveGeneratorTest.kt))를 통해 계산 규칙의 안정성을 보증합니다.

---

## 4계층: Middleware / Cache Domain

### ① 개요 및 주요 컴포넌트
* **책임**: 엔진의 원시 API(`EngineCoreApi`)를 유스케이스 단위로 조합하여 분석 세션을 공급하고, 캐시의 만료(TTL) 및 신뢰도 정책에 따른 로컬/원격 캐시 공급 및 라우팅을 조율합니다.
* **주요 소스 파일**:
  * [EngineSessionClient](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/EngineSessionClient.kt): 로컬/원격을 추상화하며 UI/App Service가 바라보는 최종 엔진 게이트웨이
  * [PositionAnalysisCacheResolver](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/middleware/PositionAnalysisCacheResolver.kt): `bundled-trusted`, `operator-trusted` 등의 신뢰 레벨에 따라 재사용 가능한 캐시를 평가 및 서빙하는 도메인 Resolver
  * [EndgameResolver](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/EndgameResolver.kt): 사석 분석 및 최종 계가 연산을 미들웨어 수준에서 오케스트레이션

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **불충분한 분석 품질의 캐시 오염 및 전파**:
   * *우려*: 방문수(`visits`)가 모자란 미완성 분석이 캐시에 적재되거나, 네트워크 불량으로 끊긴 결과가 정상 캐시로 오인되어 사용자에게 잘못 추천되는 현상이 생길 수 있습니다.
   * *극복*: `PositionAnalysisCacheResolver` 내부에서 단순 캐시 히트 외에 분석 품질 필터링을 검증하도록 보강하였습니다. `DiagnosticEventLogPort`를 사용하여 visits가 미달되는 불완전 분석(`fill=SHORT`) 시 경고를 실시간 기록함으로써 진단 기반의 신뢰성을 확보하고 있습니다.

---

## 5계층: Game Domain

### ① 개요 및 주요 컴포넌트
* **책임**: 대국 자체의 흐름과 참여 주체(흑/백 Seat), 대국 상태에 기반한 경기 정책(심판, AI 난이도별 캐릭터 구성 등)을 조율합니다.
* **주요 소스 파일**:
  * [MatchReferee](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/match/MatchReferee.kt): 사람, AI, 원격 사용자 등의 착수 요청에 대해 턴 권한 및 계가 트리거 여부를 판정하는 경기 심판
  * [AiMoveSelectionPolicy](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/match/AiMoveSelectionPolicy.kt): AI의 의사결정 시, 난이도 프로필과 무작위 확률을 조합하여 최종 수를 결정하는 정책 컴포넌트
  * [PlayLevel.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/PlayLevel.kt): 난이도별 기본 설정값 모델

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **대국 주체(사람 vs AI vs 원격) 확장에 따른 제어부 복잡성**:
   * *우려*: 게임 턴이 돌아갈 때 주체가 로컬 유저인지, AI 봇인지, 혹은 네트워크 유저인지에 따라 착수 적용 로직이 파편화되어 스크린 컨트롤러가 거대해질 수 있습니다.
   * *극복*: `SeatId`(Black, White) 및 `SeatAssignment` 등의 추상화된 Seat 개념을 선언하여, 어떤 플레이어 방식이 오든 `MatchReferee` 단일 수락 통로(Single Path)로 유입되어 바인딩되도록 통제 규칙을 구조화하였습니다.

---

## 6계층: App Service / Session Orchestration

### ① 개요 및 주요 컴포넌트
* **책임**: Presentation 레이어가 호출하는 최상위 Use Case 처리 계층입니다. 새 게임 시작, 무르기, 복원, 대국 오토플레이 흐름 및 기기 벤치마크, 로깅을 담당하며 저장 포트(Port) 등의 아웃바운드 인프라를 연결합니다.
* **주요 소스 파일**:
  * [GameSessionController](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/GameSessionController.kt): 대국 세션의 코어 상태전이 및 Effect 실행 관리를 전담하는 코디네이터
  * [UndoApplication](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/UndoApplication.kt): 무르기 시 발생하는 로컬 보드 원복 및 엔진 동기화 흐름 조율
  * [ApplicationPorts.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/application/ApplicationPorts.kt): 저장소, preferences 등 플랫폼 아웃바운드 포트

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **비동기 분석 흐름과 UI 라이프사이클 엉킴**:
   * *우려*: 사용자가 화면을 전환하거나 무르기를 할 때, 백그라운드에서 실행 중이던 KataGo 코루틴 분석 태스크가 적절히 정리되지 않아 상태가 어긋나거나 불필요한 연산을 수행할 위험이 있습니다.
   * *극복*: 컨트롤러가 대국의 현재 상태(`GameSessionCoreState`, `GameSessionSettingsState` 등) 및 런타임 진행(isEngineBusy) 상황을 단일 소스 오브 트루스(SSOT)로 보관합니다. 무르기 등의 예외 발생 시 `pending` 이펙트를 명시적으로 취소하고 지연된 quiet-window 후에만 엔진 연산이 재개되도록 차단하는 이펙트 러너(Effect Runner) 구조로 설계하여 비동기 오작동을 차단합니다.

---

## 7계층: Presentation / Game UX

### ① 개요 및 주요 컴포넌트
* **책임**: UI 상태 렌더링(Compose), 사용자 입력 및 메뉴 조작 이벤트 처리, 바둑판 캔버스 드로잉을 담당합니다. 도메인 로직이나 비동기 엔진 연산 순서를 알지 못합니다.
* **주요 소스 파일**:
  * [GoCoachApp.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt): 메인 화면 레이아웃 컴포저블 및 UI 이벤트 디스패치
  * [GoBoard.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt): 터치 좌표 픽셀을 바둑 좌표로 매핑하고 캔버스에 렌더링하는 전용 드로잉 컴포넌트
  * [PlayerSetupUiState](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/presentation/PlayerSetupUiState.kt): UI 표시 문자열을 도메인 조건 계산 없이 매핑하도록 설계된 프리젠테이션 DTO

### ② 잠재적 우려사항 및 아키텍처적 극복 방안
1. **불필요한 리렌더링 및 UI 프레임 끊김 (Frame Drop)**:
   * *우려*: 361개의 보드 교차점 중 단 하나의 돌이 놓이거나 지워질 때 바둑판 컴포저블 전체가 불필요하게 무거운 연산을 반복해 화면이 끊길 수 있습니다.
   * *극복*: `PlayerSetupUiState` 및 `GameScreenState`와 같은 뷰 전용 데이터 DTO를 도입하여 컴포즈가 복잡한 조건 분기를 돌지 않고 상태값을 즉각 매핑하게 격리했습니다. 또한 [GoBoard.kt](file:///Users/ryan9kim/worksoc/go-ai-coach/app-android/src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt)의 그리기를 순수 Canvas 내부 드로잉으로 제한하여 UI 프레임을 최적으로 유지하고 있습니다.
