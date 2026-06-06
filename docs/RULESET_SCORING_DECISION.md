# 계가 방식 옵션화 결정 메모

작성일: 2026-06-07

## 결론

Go AI Coach는 현재 9x9 POC에서 중국식 area scoring을 기본으로 사용한다. 그러나 첫 릴리즈 이후 사용자층을 넓히려면 계가 방식은 옵션으로 제공해야 한다.

권장 방향은 다음과 같다.

- 기본 POC: 중국식 area scoring 유지
- 제품 설정: `중국식/Area`, `일본/한국식 Territory` 옵션 제공
- 지역 기본값:
  - 한국/일본 사용자: territory scoring 기본값 검토
  - 중국/대만/일부 국제 서버 사용자: area scoring 기본값 검토
  - 글로벌 기본값은 앱 타깃 사용자층 확정 후 결정

## 중국식이 유일한 국제 표준인가?

아니다. 바둑에는 단일 국제 표준 계가만 있는 것이 아니다.

- 중국식은 area scoring이다. 돌 + 집을 센다.
- 일본/한국식은 territory scoring이다. 집 + 상대 사석/포로를 센다.
- AGA, Tromp-Taylor 등 컴퓨터 바둑이나 서버/대회에서 쓰는 변형도 있다.

현재 앱의 중국식 기본값은 “중국식이 더 정통이라서”가 아니라 POC 구현 안정성 때문이다. area scoring은 앱이 현재 보드의 돌과 빈 영역만으로 비교적 단순하게 계산할 수 있다. 반면 territory scoring은 사석 합의, 세키, 분쟁 처리, cleanup phase가 더 중요하다.

## KataGo와 규칙

KataGo는 중국식만 지원하는 엔진이 아니다.

- KataGo 공식 README는 config에서 `rules=japanese`, `rules=chinese` 등을 설정하거나, GTP 콘솔에서 `kata-set-rules japanese`처럼 동적으로 바꿀 수 있다고 설명한다.
- KataGo rules 문서는 `ScoringRule`을 Area와 Territory로 나누고, Territory rules에서는 양패스 후 바로 끝나지 않고 cleanup phase가 이어지는 구조를 설명한다.
- 로컬 KataGo 1.16.4 실행 로그도 초기 기본은 `TrompTaylor rules`라고 출력한다. 즉 현재 중국식 기본은 앱 seed config와 `newGame()` 구현 선택의 결과이지, KataGo가 중국식만 강제해서가 아니다.

KataGo 주 개발자의 국적이나 민족성을 계가 방식의 근거로 삼는 것은 적절하지 않다. 공식적으로 확인 가능한 것은 KataGo 프로젝트가 `lightvector/KataGo`이고, 저자로 David J. Wu가 알려져 있다는 점이다. 규칙 기본값은 개발자 배경보다 self-play, 컴퓨터 규칙 명확성, config 기본값, GUI/앱 선택의 영향을 받는다.

## 현재 앱 상태

현재 구현은 다음과 같다.

- `Ruleset` enum에는 `Chinese`, `Japanese`가 이미 있다.
- `GameState.empty()` 기본값은 `Ruleset.Chinese`다.
- `KataGoProcessEngineAdapter.newGame()`은 `komi 6.5`를 설정하지만, ruleset별 `kata-set-rules` 호출은 아직 명시적으로 분기하지 않는다.
- `BoardAreaScorer`는 이름 그대로 중국식 area estimate만 계산한다.
- `Copy Log`의 `[LocalAreaScoreNow]`도 중국식 area estimate다.

따라서 현재 앱에서 `Japanese`를 선택하는 UI를 바로 붙이면 안 된다. enum만 있고, 실제 엔진 규칙/로컬 계가/종국 처리/문서가 완전히 연결되어 있지 않다.

## 옵션화에 필요한 작업

1. 게임 설정 모델 추가
   - `GameSettings(boardSize, ruleset, komi)` 형태로 shared 도메인에 둔다.
   - `GameState` 생성과 새 대국 시작이 이 설정을 사용하게 한다.

2. 엔진 규칙 동기화
   - `EngineAdapter.newGame(boardSize, ruleset)`에서 KataGo process adapter가 `kata-set-rules chinese/japanese`를 명시 호출한다.
   - ruleset 변경 시 엔진을 새 판으로 재동기화한다.

3. 로컬 계가 분리
   - `BoardAreaScorer`는 중국식 전용으로 유지한다.
   - territory scoring은 별도 `BoardTerritoryScorer` 또는 `FinalScoreCalculator` 뒤에 둔다.
   - 일본/한국식은 사석 합의 또는 엔진 dead-stone 결과가 없으면 정확히 계산하기 어렵다.

4. 종국 UX 보강
   - 일본/한국식에서는 pass/pass 후 바로 확정하지 않고 사석 확인/cleanup 화면이 필요하다.
   - 사용자가 죽은 돌을 지정하거나 엔진 추천 사석을 승인하는 UX가 필요하다.

5. 로그/테스트 확장
   - debug report에 `ruleset`, `scoringMode`, `komi`, `engineRules`를 명확히 남긴다.
   - 동일 보드에서 area/territory 결과가 다른 케이스를 회귀 테스트로 보관한다.

## 제품 판단

초기 개발 속도와 엔진 안정성만 보면 중국식 area scoring을 유지하는 것이 합리적이다. 그러나 한국/일본 사용자에게는 territory scoring이 더 익숙하다. 사용자가 “내가 계산하면 흑승인데 앱은 백승”이라고 느끼는 대부분의 혼란은 area/territory 차이와 사석 처리 UX 부재에서 발생한다.

따라서 제품 단계에서는 `계가 방식` 옵션과 `종국 사석 확인 UX`를 함께 넣는 것이 맞다. 옵션만 제공하고 사석 확인 UX가 없으면 일본/한국식 계가의 신뢰성이 떨어진다.
