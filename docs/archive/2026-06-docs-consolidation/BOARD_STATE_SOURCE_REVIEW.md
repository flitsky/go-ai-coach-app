# 보드 상태 Source of Truth 검토

작성일: 2026-05-31

## 질문

사석 제거/합법수 판정 같은 바둑 규칙 상태를 앱이 직접 구현해야 하는가, 아니면 KataGo 엔진 상태를 source of truth로 삼아야 하는가?

## 확인한 사실

### 현재 앱 상태

- `shared/GameState.play()`는 현재 다음만 처리한다.
  - 차례 검증
  - 보드 범위 검증
  - 점유 좌표 검증
  - 새 돌 추가
- 상대 그룹 liberties 계산, 사석 제거, 자살수, 패는 아직 없다.

### KataGo GTP

KataGo GTP 엔진 내부는 사석 제거를 수행한다.

검증 시나리오:

- `White E5`
- `Black E4`
- `Black D5`
- `Black E6`
- `Black F5`

결과:

- `showboard` 기준 `W stones captured: 1`
- E5 백돌은 엔진 내부 보드에서 제거됨.

다만 `genmove` 응답은 일반적으로 착수 좌표만 반환한다.

예:

```text
= C5
```

즉, “이번 수로 제거된 돌 목록”은 앱에 구조화해서 주지 않는다.

KataGo GTP에는 `showboard`, `printsgf`, `loadsgf`, `set_position`, `undo`, `final_status_list` 등이 있지만, 현재 판의 stones를 안정적인 JSON 구조로 반환하는 명령은 확인하지 못했다. `showboard`는 사람이 보기 위한 텍스트라 앱 상태 동기화 프로토콜로 쓰기에는 취약하다.

### KataGo Analysis Engine

KataGo의 analysis engine은 GTP가 아니라 JSON line protocol을 제공한다.

입력 query는 다음 형태를 가진다.

- `initialStones`
- `moves`
- `rules`
- `komi`
- `boardXSize`
- `boardYSize`
- `maxVisits`
- `includeOwnership`
- 기타 분석 설정

이 API도 엔진이 move history를 해석하고 분석하지만, response는 후보수/승률/점수/ownership/policy 중심이다. 현재 보드의 사석 제거 결과를 앱 상태로 그대로 쓰기 위한 board map 응답은 핵심 API가 아니다.

## KaTrain 분석

로컬 설치본:

- package: `KaTrain`
- version: `1.17.1`
- path: `/Users/ryan9kim/.local/pipx/venvs/katrain/lib/python3.12/site-packages/katrain`

오픈소스 repo:

- `sanderland/katrain`
- GitHub README 기준 KataGo feedback 기반 분석/대국 앱.
- PyPI 기준 license expression은 MIT.
- repository license는 KataGo binary, icon, font 등 포함 자산의 별도 라이선스 caveat가 있고, 그 외 코드/콘텐츠는 MIT 계열 문구를 사용한다.

핵심 발견:

- `katrain/core/game.py`의 `BaseGame` docstring은 “capture rules implementation”을 명시한다.
- `BaseGame._validate_move_and_update_chains()`가 다음을 직접 처리한다.
  - board를 chain id grid로 관리
  - 인접 chain merge
  - 상대 chain liberties 확인
  - liberties가 없는 상대 chain 제거
  - ko/snapback 판정
  - suicide 판정
  - prisoners 누적
- `KataGoEngine.request_analysis()`는 현재 board map을 엔진에서 받아오는 것이 아니라, `nodes_from_root`에서 moves와 placements를 모아 `initialStones`/`moves` JSON으로 넘긴다.

즉 KaTrain의 구조는 “엔진 상태를 앱 보드의 단일 source of truth로 사용”이 아니라, “앱이 game tree와 board projection을 직접 관리하고, 엔진은 분석/추천/대국 응답을 제공”하는 방식에 가깝다.

## 선택지 비교

### 1. 엔진 상태를 source of truth로 사용

장점:

- 엔진이 해석한 규칙과 앱 표시가 일치한다는 직관이 있다.
- process adapter가 안정적이면 앱 룰 구현량을 줄일 수 있다.

단점:

- GTP `genmove`는 잡힌 돌 목록을 구조화해서 반환하지 않는다.
- `showboard` 텍스트 parsing은 취약하다.
- stub, remote, offline, engine restart, undo, SGF import/export에서 앱 상태를 재구성하기 어렵다.
- UI 표시가 엔진 roundtrip에 묶이면 터치 직후 반응성과 에러 복구가 나빠진다.
- engine process가 죽거나 교체될 때 현재 보드 상태를 복구하려면 결국 move history replay가 필요하다.

### 2. 앱이 최소 룰 projection을 구현

장점:

- UI 보드는 즉시 갱신된다.
- 엔진이 stub/process/JNI/remote로 바뀌어도 shared state는 유지된다.
- move history에서 언제든 board를 재구성할 수 있다.
- SGF, undo/redo, branch, training review, offline replay에 유리하다.
- KaTrain과 같은 방향이다.

단점:

- 앱 룰 구현 버그 가능성이 있다.
- KataGo rules 옵션과 앱 rules 옵션이 다르면 mismatch가 발생할 수 있다.

### 3. Hybrid: 앱 move history가 canonical, 엔진은 validator/evaluator

권장안.

원칙:

- canonical source는 `GameRecord` 또는 `GameState`의 rules/komi/move history.
- board map은 shared rules engine이 move history에서 계산한 projection.
- KataGo는 같은 move history를 받아 분석/응수/검증을 담당.
- debug build에서는 필요 시 `showboard` parser 또는 `printsgf`로 엔진과 앱 projection을 샘플 비교한다.

이 방식은 “엔진과 상태 이상 가능성”을 줄이면서도, 앱이 엔진 출력 텍스트에 종속되지 않는다.

## 권장 결정

지금 바로 큰 룰 엔진을 만들지는 않는다.

하지만 사석 제거는 UI 대국 앱의 필수 상태 projection이므로 `shared`에 작게 구현하는 것이 맞다.

최소 구현 순서:

1. `BoardRules.applyMove(state, move)` 또는 `GameState.play()` 내부에 그룹/liberty 기반 capture 추가.
2. 반환값에 `captured: List<BoardCoordinate>`를 포함할 수 있는 `MoveApplication` DTO 추가 검토.
3. 자살수 금지/허용은 ruleset에 맞춰 분리.
4. simple ko는 우선 구현하거나, 최소한 직전 단일 capture recapture를 막는 형태로 시작.
5. debug-only 엔진 동기화 검증을 별도 adapter 기능으로 둔다.

## KaTrain 코드를 repo 하위 폴더로 둘지

권장하지 않는다.

이유:

- 전체 KaTrain은 Kivy UI, assets, bundled KataGo binaries, fonts, packaging 파일이 섞여 있어 이 repo에 넣으면 노이즈가 크다.
- 라이선스 caveat가 코드 외 자산까지 따라온다.
- 현재 필요한 것은 전체 앱이 아니라 `core/game.py`, `core/engine.py`, `core/sgf_parser.py`의 설계 참고다.

대안:

1. sibling reference repo로 clone:
   - `/Users/ryan9kim/worksoc/katrain-reference`
2. 이 repo에는 분석 문서만 유지:
   - `docs/BOARD_STATE_SOURCE_REVIEW.md`
3. 실제 구현은 Kotlin으로 새로 작성:
   - KaTrain 알고리즘 구조는 참고하되, 코드를 그대로 복사하지 않는다.

필요하면 다음 단계에서 sparse checkout 또는 shallow clone으로 KaTrain reference repo를 별도 위치에 둘 수 있다.
