# Go AI Coach PRD

최종 수정: 2026-06-17 (9/10절 현황은 현재 코드 기준으로 갱신함. 1~8절은 POC 시절 원본 제품 결정이며 역사적 기록으로 그대로 둔다 — 실제로 무엇이 구현됐는지는 [ARCHITECTURE.md](./ARCHITECTURE.md), [ENGINE.md](./ENGINE.md)를 따른다)

## 1. 목표

로컬 AI 분석을 돌릴 수 있고, 궁극적으로 KaTrain과 유사한 코칭 경험을 제공하는 Android-first 바둑 학습 앱을 만든다.

첫 마일스톤은 완성된 앱이 아니라 기술 POC다.

- 최소한의 Android UX
- 로컬 엔진 통신
- 9x9 보드 테스트
- 기본적인 착수 요청/응답 루프

## 2. 제품 방향

목표로 하는 최종 상태:

- 로컬 AI 대국 및 분석
- 9x9, 13x13, 19x19 지원
- 초보자 친화적 난이도 조절
- 대국 후 복기
- 실수 강조 표시
- 후보수 표시
- 점수/승률 추이
- SGF 가져오기/내보내기
- 저사양 기기를 위한 선택적 서버 엔진 fallback

참고할 품질 기준:

- KataGo 수준의 기력과 분석 품질
- KaTrain 스타일의 학습 UX
- 모바일 우선 상호작용 품질

## 3. 초기 플랫폼 결정

Kotlin Multiplatform을 1순위로 사용한다.

이유:

- 첫 번째 불확실 요소는 UI 표현력이 아니라 엔진 통신이다
- Android 로컬 엔진 통합이 가장 짧은 유효 경로다
- 공유 Kotlin 모듈이 나중에 게임 상태와 엔진 프로토콜 로직을 담을 수 있다

Flutter는 여러 보드게임 앱군으로 프로젝트가 확장되어 UI 공유 속도가 더 중요해질 경우를 위한 진지한 미래 후보로 남겨둔다.

## 4. POC 범위

POC는 최소한만 증명하면 된다.

1. Android 앱이 실행된다.
2. 단순한 보드 화면이 렌더링된다.
3. 엔진 어댑터가 초기화된다.
4. 앱이 엔진 계층에 명령을 보낸다.
5. 엔진 계층이 구조화된 응답을 반환한다.
6. UI가 응답을 표시한다.

네이티브 빌드가 준비되지 않았다면 첫 엔진 응답은 stub일 수 있다. 앱 경계는 UX를 다시 쓰지 않고도 stub을 실제 엔진으로 교체할 수 있도록 설계해야 한다.

## 5. 제안한 아키텍처

모듈:

- `app-android`: Android 셸과 Compose UI
- `shared`: 보드 상태, 규칙, 엔진 인터페이스, DTO
- `engine-android`: Android 엔진 어댑터, JNI/프로세스 브리지
- `docs`: 제품 및 아키텍처 노트

핵심 인터페이스:

- `EngineAdapter`
- `GameState`
- `BoardCoordinate`
- `Move`
- `AnalysisResult`
- `DifficultyProfile`

## 6. 엔진 어댑터 계약

UI는 엔진이 네이티브인지, 프로세스 기반인지, stub인지, 원격인지 알아서는 안 된다.

최소 API:

```kotlin
interface EngineAdapter {
    suspend fun initialize(profile: EngineProfile): EngineStatus
    suspend fun configure(profile: EngineProfile): EngineStatus
    suspend fun newGame(boardSize: BoardSize, rules: Ruleset): EngineStatus
    suspend fun playMove(move: Move): EngineStatus
    suspend fun genMove(player: StoneColor): MoveResult
    suspend fun analyze(limit: AnalysisLimit): AnalysisResult
    suspend fun stop(): EngineStatus
}
```

## 7. 학습 UX 요구사항

MVP 학습 UX:

- 보드 크기 선택: 9x9, 13x13, 19x19
- 대국 모드와 복기 모드
- AI 추천수
- 마지막 착수 표시
- 간단한 형세 추정
- 실수 심각도 라벨

이후 학습 UX:

- 약한 AI 프로필
- 나쁜 수 다시 두기
- 상위 후보 비교
- 점수/승률 그래프
- 가장 큰 실수 순 복기 큐
- SGF 주석

## 8. 난이도 전략

처음부터 엔진 원시 설정을 그대로 노출하지 않는다.

사용자 대상 프로필을 노출한다:

- Beginner
- Casual
- Intermediate
- Strong
- Full Analysis

각 프로필은 내부적으로 visits, 시간 제한, 모델, 서버/로컬 모드에 매핑될 수 있다.

## 9. 현재 구현 현황 (2026-06-17 갱신)

앱은 1~8절에서 설명한 POC 단계를 한참 지났다. 현재 상태:

- 완전한 9x9 플레이 루프: 합법수, 패/자충 규칙, 따냄, 양패스 종국 감지, Area/Territory 계가, 무르기, 새 게임, 저장된 대국 이어하기.
- 로컬 KataGo 엔진(`libkatago.so`)이 **두 가지** 탐색 orchestration 모드(`GtpStatefulFast`, `JsonPositionAnalysis`)를 지원한다 — [ENGINE.md](./ENGINE.md) 참고. 엔진 없이 UI 작업을 할 수 있는 stub 어댑터도 그대로 사용 가능하다.
- AI 난이도 4그룹(`빠른 초급`/`초급`/`중급`/`고급`), 각각 고유한 visits/time-cap/candidate-count 정책과 탐색 모드를 가진다.
- Top Moves 보드 표시(최대 5개 후보, 1순위는 큰 원), 착수 후 복기 색상(green/yellow/orange/red/unknown), 점수/승률 그래프, 기기 벤치마킹, 디버그 리포트 복사, 진단/런타임 이벤트 로깅.
- `app-android/application/`은 17개 기능 도메인 패키지로 분해되어 있고, 각각 작은 컨트롤러 + 순수함수 application 패턴을 따른다 — 전체 계층 지도는 [ARCHITECTURE.md](./ARCHITECTURE.md) 참고.
- `shared` 모듈: 보드 규칙, 계가, 엔진 코어 API 계약, 분석 정책. 여전히 Android 우선이며 iOS/기타 KMP 타겟은 아직 검증되지 않았다.
- 13x13/19x19, SGF 가져오기/내보내기, 서버 엔진 fallback(2절의 목표 최종 상태)은 **아직 구현되지 않았다**.

## 10. 로드맵 (2026-06-17 상태 갱신)

Phase 0: 저장소와 문서. **완료.**

Phase 1: stub 엔진을 가진 Android KMP 스켈레톤. **완료** (2026-05-31).

Phase 2: 로컬 엔진 브리지 POC. **완료.** 로컬 KataGo 프로세스 어댑터가 단순 smoke test가 아니라 기본 엔진 경로다.

Phase 3: 9x9 플레이 루프. **완료.** 전체 규칙, 계가, 무르기, 저장/이어하기, AI 레벨, Top Moves, 점수 그래프 모두 출시됨.

Phase 4: 13x13, 19x19 지원. **착수 전.**

Phase 5: KaTrain에서 영감을 받은 복기 UX. **일부 완료.** Top Moves 표시, 착수 복기 색상, 점수/승률 그래프는 존재한다. 넓은 다중 후보 학습 모드와 SGF 주석은 아직 만들지 않았다.

Phase 6: 선택적 서버 fallback. **착수 전.** 원격 position-analysis 게이트웨이/캐시 골격(`middleware/Remote*`)은 있지만 아직 `RemoteEngineSessionClient`는 없다 — ARCHITECTURE.md의 "알려진 갭" 참고.

## 11. 열린 리스크

- 모바일 엔진 패키징 크기
- 발열로 인한 throttling과 배터리 소모
- 네이티브 빌드 복잡도
- 모델 배포와 업데이트 경로
- 대용량 엔진 에셋에 대한 앱스토어 정책
- 충분한 학습 가치를 노출하면서도 UX를 단순하게 유지하기
- Android에서 KataGo를 실행한 뒤의 프로세스 라이프사이클과 timeout 처리

## 12. 다음 스레드 핸드오프 (2026-05-31 시점 기록, 역사적 참고용)

당시 스켈레톤 이후 권장했던 다음 작업:

로컬 KataGo 아티팩트를 빌드하거나 재사용한 뒤, 같은 공유 인터페이스 뒤에 프로세스 기반 `EngineAdapter`를 추가한다. JNI/네이티브 패키징으로 넘어가기 전에 프로세스 stdio/GTP 스타일 명령 흐름을 먼저 증명하는 것을 우선한다.

당시 로컬 조사 결과: 테스트한 Android 15 에뮬레이터에서는 앱 private 데이터를 실제 앱 프로세스의 실행 파일 위치로 쓸 수 없었다. `libkatago.so`라는 이름으로 네이티브 라이브러리 경로를 통해 실행 파일을 패키징/추출하고, 모델/설정을 앱 파일에 seed하는 방식이면 디버그 스파이크가 동작했다.
