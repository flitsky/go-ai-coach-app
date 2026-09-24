# 리팩토링 일감 백로그 — 아키텍처 고도화 트랙

세대 시작: 2026-09-23 · 기준선: 1.0.0(versionCode 10000) · `make test TARGET=emu` 초록

> **이 문서는 리팩토링 트랙 전용 진행 관리표다.**
> 기능 일감은 `260923-_ACTIVE_BACKLOG.md`가, 설계 근거·실측·함정은
> `260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md`(이하 **진단서**)가 담당한다.
> 여기는 **"지금 무엇을 집을 것인가"** 만 답한다.

⚠️ **이 문서의 번호는 이 문서 안에서만 쓰인다**(1, 2, 3 …). 활성 백로그의 `#NNN`과 **다른 체계**다 —
커밋 메시지에는 `refactor backlog #N` 형태로 적어 구분한다.

🔴 **번호는 영구불변 ID이고, 우선순위는 「예정사항」의 등장 순서다.** 둘을 섞지 마라 —
새 일감은 **맨 뒤 번호를 받고 읽는 순서에서만 앞에 놓는다.** 중간에 번호를 끼워 넣으면
이 문서 안의 상호 참조(`#20 뒤에만`, `#9의 선행조건` 같은 줄)가 전부 다른 것을 가리키게 된다.

⚠️ **완료 항목은 「한 줄 + 커밋 해시」다.** 구현 결과를 여기 길게 쓰지 않는다 — 정본은 git이다
(`git log --grep "refactor backlog #N"`). 길게 쓰고 싶으면 그건 **커밋 메시지에 쓸 말**이다.

---

## 신규 스레드 착수 프로토콜

**고정 프롬프트**(그대로 복사):

> 이번 스레드에서는 `work/roadmap/260923-_REFACTORING_BACKLOG.md`를 읽고 **「진행 중」의 첫 번째 항목**을
> (비어 있으면 **「예정사항」의 첫 번째 항목**을) 맡아 주세요. 항목을 이해했으면 **「진행 중」으로 옮기고**
> 착수하시고, 결과물을 저와 논의한 뒤 제가 완료를 승인하면 문서를 완료로 갱신하고 끝났다고 알려 주세요.

**착수 직후 할 것 넷** — 순서가 있다.

1. **아래 「⚠️ 반드시 알아야 할 함정」을 읽는다.** 걸리는 키워드가 있으면 `docs/spec/PITFALLS.md`의
   해당 번호 전문을 편다. **81건을 다 읽지 않는다.**
2. **진단서에서 그 항목이 속한 절을 읽는다.** 왜 이 일을 하는지, 무엇을 건드리면 안 되는지가 거기 있다.
3. **선행 항목이 완료인지 확인한다.** 아래 「의존 그래프」 참고. 안 끝났으면 **집지 않는다.**
4. **끝내기 전 `make test TARGET=emu`가 초록**이어야 한다. 빨간 채로 닫지 않는다.

**caveat 셋**
- **AI 모델 표기는 사람이 스레드를 띄울 때 고르라는 것**이지 스레드가 스스로 바꾸는 값이 아니다.
- **`docs/DOCS_INDEX.md`를 항목마다 건드리지 않는다.** 문서 등재는 트랙이 끝날 때 한 번에 한다.
- **한 번에 한 스레드.** 예외는 아래 「병렬 가능」에 명시된 조합뿐이다.

---

## ⚠️ 반드시 알아야 할 함정

전문은 `docs/spec/PITFALLS.md`. **이 트랙에서 실제로 밟게 되는 것만** 추렸다.

- **함정 67 — `wiringContext`의 `remember` 키를 제거하지 마라.** 🔴 설계안 **넷이 전부** 첫 태스크로
  제안했고 근거까지 똑같이 틀렸다. `GoCoachApp.kt`의 지역 `val` 5개가 캡처되고, `engineName`/
  `engineDiagnostic`은 **KDoc이 "remember로 감싸지 않는다"고 명시적으로 금지한 값**이다.
  키를 없애면 첫 컴포지션 값으로 영구히 얼어붙고 **CI 어디에도 안 걸린다.**
  착수 조건: ⓐ 64개 멤버 전수 감사(`get() =` 또는 람다가 아닌 멤버 0건) ⓑ 지역 val 8개 먼저 지연 읽기로.
- **함정 69 — 저장 스키마 버전을 올리지 마라.** `SavedGameSessionCodec.decode`는 `schema != SchemaVersion`이면
  **즉시 `null`**이고, 9개 블롭 스토어 중 8개는 마이그레이션 경로가 없다. 올리는 순간 이어하기·출석 일수·
  보유 캐릭터가 통째로 초기화된다. **필드 추가는 `optDouble`/`optInt` 기본값 흡수로만.**
- **함정 70 — 공용 인터페이스의 기본 구현을 삭제하려면 픽스처가 먼저다.** `EngineCoreApi`를 구현하는 것은
  프로덕션 어댑터 3종만이 아니다. 손으로 쓴 페이크가 **8파일 909줄**(2026-09-23 실측 — 애초 추정 9파일 1,038줄은 틀렸다)로 흩어져 있었다.
  ✅ **#10으로 해소** — 이제 `testsupport/FakeEngineSessionClient` 한 곳만 고치면 된다.
- **함정 71 — 오퍼레이션 `Mutex`를 그냥 넣으면 앱이 언다.** `forceReset`은 락을 안 잡는데(옳다),
  그 결과 **락을 쥔 채 멈춘 호출을 기다리는 동안 다른 전부가 자기 타임아웃까지 대기**한다.
  평범한 `withLock`이 아니라 **타임아웃/세대 기반 취소**가 함께 들어가야 한다.
- **함정 72 — 공유 작업 트리에서 대량 개명·이동 금지.** 세션 여럿이 트리 하나를 공유하고
  `git checkout` 되돌리기가 금지다. 대량 이동은 **단독 점유**를 선언하고 한다.
  🔴 그리고 **`git add .` / `git commit -a`를 쓰지 마라** — 2026-09-23 정리에서 커밋 3개가 남의 변경을 삼켰다.
- **함정 80 — `git add <경로>` 뒤의 `git commit -m`도 안전하지 않다.** 🔴 인덱스는 트리 전체가 공유한다 —
  **`add`와 `commit` 사이에 다른 세션이 스테이징하면 그게 내 커밋에 들어간다.** 실제로 35개 파일이 삼켜졌다.
  → **`git commit <경로> -m "..."`** 로 커밋하라(경로 지정 커밋은 인덱스의 나머지를 건드리지 않는다).
  삼켰으면 `git reset --soft HEAD~1`로 커밋만 취소하고 다시 낸다. ⛔ `--hard` 금지.
- **함정 75 — iOS 컴파일을 릴리스 게이트에 넣지 마라.** iOS는 아무것도 출하하지 않는다.
  안드로이드 릴리스가 출하 안 하는 타깃의 컴파일 실패로 막히면 안 된다 → **별도 타깃**으로.
- **함정 76 — 죽은 스캔을 되살리면 빨개질 각오를 한다.** `make test`는 **유일한 릴리스 게이트**다.
  드러난 위반은 **그 스레드가 끝까지 초록으로 만들고 닫는다.**
- **함정 77 — 공유 트리에서 음성 대조는 `--rerun-tasks` 없이 거짓 판독을 낸다.** 🔴 Gradle이 소스 변경을
  무시하고 컴파일을 `UP-TO-DATE`로 건너뛰어 **"멀쩡한데 안 깨진다"** 는 정반대 결론이 나온다. #9에서 실제로 두 번 걸렸다.
  **로그에서 컴파일 태스크가 실제로 돌았는지 눈으로 확인**하거나 격리 워크트리에서 돌려라.
- **함정 78 — 공유 트리에서 `build/test-results`의 테스트 개수는 믿을 수 없다.** 다른 세션이 필터 걸린
  테스트를 돌려 덮어쓴다. 개수 대조는 **소스에서** 센다(`git ls-tree`로 두 리비전의 `@Test`를 세는 식).
- **함정 79 — Lint `abortOnError=true`는 새 *Error* 만 막고 새 *Warning* 은 통과시킨다.**
  `warningsAsErrors=false`이므로 그렇다. 이 게이트로 얻는 보장은 **"새 Error 0"** 이지 "새 경고 0"이 아니다.
- **함정 81 — 계기 테스트에서 `shared_prefs` 파일만 지우는 것은 초기화가 아니다.** 🔴 안드로이드가
  `SharedPreferences`를 **프로세스 단위로 캐시**해, 같은 프로세스의 다음 테스트가 앞 테스트 설정을
  그대로 물려받는다. **단독 실행은 초록, `make test-device`로 셋을 함께 돌리면 빨강**이라
  고친 줄 알고 닫기 쉽다. 지우기 **전에** `clear().commit()`(`FreshAppState.resetToFreshInstallState()`).
- **(함정 아님, 환경)** `make test`를 **인자 없이** 치면 기기가 둘 이상일 때 `doctor`에서 죽는다.
  ✅ **`make test TARGET=emu`를 주면 2대가 붙어 있어도 그대로 통과한다**(2026-09-23 실측).
  ⚠️ 이 줄이 한때 *"기기가 둘이면 죽는다"* 로만 적혀 있어 **스레드 둘이 게이트 실행 자체를 회피했다** —
  그래서 실측 결과를 함께 적는다. **회피하지 말고 `TARGET=emu`로 돌려라.**

---

## 주요 포커스해야할 내용 서두 정리

**한 줄 결론**: 아키텍처가 나쁜 게 아니라, **아키텍처를 지키는 수단이 잘못된 층위에 있다.**

포트·어댑터는 이미 완성돼 있고 도메인 22파일의 외부 import는 5줄뿐이다. Clean Architecture가 목표로 하는
상태에 **이미 도달했다.** 없는 것은 그 상태를 지키는 **강제 수단**이다 — 지금 그 역할을 1,829줄 문자열 스캔
테스트와 산문 문서가 하고 있고 둘 다 코드보다 빨리 낡는다.

**P0에서 그것이 실증됐다** — 죽은 가드 넷을 되살렸더니 **진짜 위반이 0건**이었다. 코드는 계속 경계를
지켰고 **감시만 꺼져 있었다.** 고칠 대상은 코드가 아니라 **계약을 표현하는 방식**이다.

**목표**: Hexagonal + KMP 순수 도메인 + 화면별 StateHolder(MVI-lite) + 수동 DI — **넷 다 이미 코드에 있다.**
계층을 **패키지 FQN → Konsist → (최후) Gradle 모듈** 3단계로 물리화한다.
Hilt(commonMain 불가)·Koin(이득 0)·전면 MVI(이미 절반 작동)·모듈 우선 개편(SCC를 모듈이 원리적으로 못 잡음)은
근거와 함께 **기각**했다. 재론 시 진단서 §3.1이 답이다.

### 의존 그래프 — 순서를 어기면 컴파일에서 죽는다

```
[P1 게이트] ──┬─→ [P3 이름공간(동작 0)] ──→ [P4 사이클 절단] ──→ [P6 모듈 승격]
   9~13       │        16~23                    24~35              선택·개별 승인
              └─→ [P5 상태 소유자] 36~47
[P2 엔진 동시성] ── 독립 트랙. engine-android+application/engine에 갇혀 파일 충돌 0
[48 UiStrings 분해] ── 차단 해제. ui/ 단독 점유 필요
```

⚠️ **모듈 분리(P6)는 P4가 먼저다.** `application/` 안의 **17패키지 SCC**를 안 끊고 모듈을 나누면
`application/engine`을 뗄 때 session·score·match·runtime·endgame이 딸려오고, 그것들이 다시 engine을 참조해
**Gradle 순환 의존으로 빌드가 멈춘다.**
⚠️ **P4(도메인 이동)는 9(골든 테스트)가 먼저다.** 테스트 없이 옮기면 **옮긴 것이 맞는지 확인할 방법이 없다.**

⚠️ **번호는 완료 순으로 재사용하지 않는다** — 위 그래프의 범위 표기는 대략치이고, 정확한 선행 관계는 각 항목의 ⚠️ 줄이 정본이다.

### 병렬 가능한 조합

- ✅ **9 · 10 · 11~13 동시 실행 완료**(2026-09-23) — 파일 겹침 0으로 검증됐다. 같은 성질의 조합은 앞으로도 병렬 가능
- **P2(14~15 이후 16~21)** 는 어느 단계와도 병렬 — 대상이 engine-android에 갇혀 있다
- **P3(16~23)** 는 서로 병렬 가능하나 **전부 `LayeringContractTest.kt` 경로를 함께 커밋**해야 하므로 **직렬**
- **P5(36~47)** 는 병렬 불가 — 같은 파일을 연쇄로 만진다

---

## 일감 정리

순차적으로 다음 스레드에서 바로 인지할 수 있도록 정리 및 분류 가능해야하며, 아래 형식을 꼭 유지하면서 갱신 할것.

### 완료 사항

| # | 무엇을 했는가 | 커밋 |
| --- | --- | --- |
| 1 | **이어하기 덤(komi) 유실 수정** — encode/decode 왕복 + 회귀 3건. 스키마 버전 불변. 실기 검증 완료(덤 0.5 → 강제 종료 → 이어하기 → 유지) | `40c4975c` |
| 2 | **0개 파일을 검사하던 계층 가드 넷 복구** — 스캔 경로를 shared로. 고의 위반 주입으로 작동 확인. **진짜 위반 0건** | `0c33d32c` |
| 3 | **`RepoPaths.kt` 신설** — 경로 46곳 흡수, `repoRoot()` 2벌→1벌, `repo.root` 주입 + `inputs.files` 선언 | `7841ed37` |
| 4 | **빈 스캔을 `require`로 실패시킴 + 날것 `readText()` 43곳을 `codeOnly()`로** — 거짓 통과 0건 확인 | `01a85479` |
| 5 | **항상 false인 단언 제거** — 위 `assertEquals`에 완전히 포함돼 있었음을 고의 파손으로 실증 | `a0a9d17f` |
| 6 | **`engine-android`의 `:shared` 의존을 `api`로** (= T1-5) — 공개 시그니처에 `:shared` 타입이 등장 | `f9cd8797` |
| 7 | **`GoCoachApp`의 만들고 버리는 중복 컨트롤러 제거** — 970→954줄. **훅 예산은 42→42, 회수 0**(함정 74) | `b19bdc57` |
| 8 | **문서 64개를 코드와 전수 대조해 정리** — 오도 32건 정정, 아키텍처 로드맵을 정본 자리로, 함정 A~J를 PITFALLS 67~76으로, 구조 이동 2건, `docs/` 22M→12M | `95f47b21`…`0b54adce` |
| 9 | **도메인 골든 테스트 23개** — `BoardRules`/`BoardScorer`/두 계가기/`DeadStoneDetector`/`DeadStoneCleaner`. 판을 그림으로 적는 파서 + 표 기반. **음성 대조 8회 전부 빨개짐**을 확인했고, 그 과정에서 **아무것도 안 잡던 테스트 2개를 찾아 고쳤다** | `c3add7af` |
| 10 | **손으로 8번 복제된 엔진 페이크를 공용 자리 하나로** (`testsupport/`, `+74 −930`). @Test 수 1368→1391로 **유실 0** | `3817756c` |
| 11 | **`make test-ios` 별도 게이트 신설** — 함정 75대로 `make test`에 합치지 않았다. `System.` 주입으로 실제로 막는 것 확인 | `9b19aaaf` |
| 12 | **app-android Lint 개통** — baseline 84건(1 error+74 warnings+9 hints), `abortOnError=true`. 새 Error가 실제로 빌드를 막는 것 확인 | `76cbaed9` |
| 13 | **빌드 힙·병렬화 + `make test-device` 신설** — configuration-cache는 근거와 함께 끄고 남겼다 | `d1b3b56e` |
| 19 | **원격 국면 인코딩에 `komi`·`handicapCount`를 싣는다** — `#1`과 같은 함정이 원격에 남아 있었다. ⚠️ **와이어만 닫혔다** — 서버가 아직 그 값을 버린다(#62) | `7ffccd17` |
| 23 | **`GameHistoryStore`의 komi 기본값을 `DefaultKomi`로** — 폴백은 도달 불가이나 다른 스토어와 어긋나 읽는 사람을 헷갈리게 했다 | `ca41b1a0` |
| 59·61 | **사석 탐지가 답하는 질문을 KDoc·테스트로 못박음** — 호출부 전수 조사로 **종국에만 불린다**를 확인해 ⓐ(현 동작 유지)로 닫았다. 실행 코드 **0줄 변경**. `singleOrNull()`이 성능 필터일 뿐임도 실측 확인 | `cba287c3` |
| 28 | **`middleware` split package 해소** — 유일한 역방향이던 1파일을 `application/analysis`로. `:shared` 패키지 사이클 **0** | `2254d324` |
| 30 | **계약 소스 읽기가 파일 이동 때 안내를 준다** — 경로 22파일→1곳(`RepoPaths`), `readContractSource()`/`…Lines()` 신설. 검수 실측: `GoCoachApp.kt`를 옮기면 **77건 전부 `IllegalStateException`(절대경로+고칠 자리), `FileNotFoundException` 0건.** 🔓 **P3 관문이 열렸다** | `d1f96c6a`·`76be16dc`·`b324b3cb` |
| 63 | **실행위치 의존 상대경로 흡수** — 지목된 3건 외 **5건을 더 찾아** 총 8파일 + `File("../Makefile")` | `63a0fbed` |
| 64 | **고아 `middleware` 패키지 해소** — `#28`이 commonMain만 닫았던 것 | `dc316d78` |
| 62 | **원격 서버가 클라이언트 komi를 읽는다** — `#19`의 정직한 마감. ⚠️ `handicapCount`는 **의도적 미반영**(아래 #65) | `784a1a90` |
| 18(절반) | **정책 타입 재선언 제거** — sealed 3종 + 어댑터 삭제, 소비자 32파일 import 교체. **로직 변경 0줄**(검수 diff 전수 확인) | `1b961feb` |
| 58 | **계기 테스트 3개 전부 초록** — 원인은 탭 회귀가 아니라 둘이었다: ⓐ 로비 시작 버튼이 좌석을 안 보고 잠겨 **대국 화면에 들어간 적이 없었다**(엔진 못 뜨는 기기에서 사람끼리도 못 두던 제품 결함 동반 해소), ⓑ `shared_prefs` **파일만** 지우던 초기화가 프로세스 캐시를 못 비워 앞 테스트 설정을 물려받았다. 실기 2회 확인 | `14967120` |
| 24 | 🔓 **`shared` 루트 22파일을 의미 있는 패키지로 갈랐다** — 루트 **22→0**, commonTest 고아 패키지 **14→0**. `domain` 7·`enginecontract` 3·`policy` 6·`scoring` 4·`content` 1. ⭐ **`shared.domain` 7파일의 import가 전부 합쳐 0줄** — 바둑 규칙이 아무것도 모른다. 이제 **import 한 줄로 계층이 보인다**(#34 Konsist의 전제) | `6e31bb6d`…`2f07cf8b` |
| 29 | **`premium`/`auth`의 세 계층을 패키지로 갈랐다** — `premium.{port 4, state 3, app 5}` · `auth.{port 1, state 1}`, 루트 0. `FeatureAccessPolicy`가 **6계층임을 코드로 확정**(`PremiumState`를 파라미터로 받는다). 가드 사보타주로 생존 확인 — `RepoPaths.applicationPath` + `walkTopDown`이 새 하위까지 훑어 **경로 고칠 곳 0** | `dfd64011` |
| 20 | **`syncStaticPosition` 기본 구현 제거 + 공통 계약 스위트** — 가설에 숫자가 붙었다: `StubEngineAdapter` 0→2건, `RemoteEngineCoreApiAdapter` 0→1건. **둘 다 조용히 빠뜨리고 있었다.** 재발 방지는 삭제가 아니라 `EngineCoreApiStaticPositionContract`(Local/Remote/Stub 동일 시나리오) | `4f5ca419` |
| 60 | **안 보던 계약 셋 → 좁히지 않고 그물** — ⭐ **관찰의 뜻이 조사 중에 바뀌었다**: `0건`은 *"그 페이크의 기본값을 아무도 안 본다"* 였지 *"계약이 죽었다"* 가 아니었다. 셋 다 프로덕션 소비자가 있다 | `3ee788b4` |
| 16 | **분석 폴백이 취소를 삼키지 않는다 + 쿼리 id 충돌 불가** — 2계층에서 `CancellationException`(타임아웃 포함)을 rethrow. 폴백 사실이 `engine.analysis.fallback` 진단 이벤트로 남는다(전엔 **완전 무음**). 카운터 → `AtomicLong`, id에 국면이 실린다. ⚠️ **AI 착수 경로는 아직 덜 닫혔다(#74)** | `ec59ea32` |
| 66 | **호출부 0·테스트 0이던 public 함수 처리** | (#24 파도에 동반) |
| 71 | **모듈 사이 테스트 코드 공유 수단 신설 + 손 페이크 제거**(`−260/+45`) — 백로그가 적은 2벌이 아니라 **3벌**이었다(셋째는 androidTest가 아니라 `src/test`). `commonTestSupport` 한 디렉터리를 세 소스셋이 함께 컴파일한다. ⚠️ `testFixtures`는 **안드로이드 변형만** 내보내 `commonTest`가 빠지므로 기각(근거는 배선 옆). 음성 대조로 세 소비자 전부 빨개짐 확인 | `7dce47e2` |

### 진행 중

_(없음 — 아래 「예정사항」 첫 항목부터 집는다)_

### 예정사항

#### 잔여 — 앞선 일감이 드러낸 것 (번호는 뒤에 붙이고 **순서로** 우선순위를 표시한다)

65. **원격 `handicapCount`를 KataGo에 어떻게 실을지 판정** (AI 모델: Opus, 노력정도: 중간)
    · #62가 komi만 닫고 `handicapCount`는 **의도적으로 미반영**했다. KataGo Analysis 스키마에
      접바둑 "개수" 필드가 없고, 유일한 접바둑 필드 `whiteHandicapBonus`는 **룰셋의 기본 접바둑 보정을
      덮어쓰는 스코어링 오버라이드**다. 접바둑 돌 자체는 이미 `initialStones`로 실린다.
    · 🔴 **그대로 꽂으면 접바둑 보정이 두 번 계산될 수 있다.** 실측 없이는 **현행 미반영이 안전한 선택**이다.
    · 실측 방법을 먼저 설계하라 — 같은 국면을 `whiteHandicapBonus` 유/무로 분석해 점수 차를 본다.

68. 🔴 **계약 테스트의 FQN 문자열이 실존 심볼을 가리키는지 검증하는 메타 테스트** (AI 모델: Opus, 노력정도: 중간)
    · `LayeringContractTest`는 `"import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi"` 같은
      **FQN을 문자열 리터럴로** 들고 금지한다. 그런데 **그 FQN이 실재하는지 스스로 확인하지 않는다.**
    · 🔴 **심볼이 옮겨가면 그 규칙은 어떤 파일과도 매치하지 않고, 초록인 채 아무것도 검사하지 않는다.**
      P0(`0c33d32c`)이 되살린 죽은 가드 넷과 **정확히 같은 병**이고, `#24`에서 다시 밟을 뻔했다.
      이번엔 사람이 손으로 5개를 확인해 막았다 — **그 방어가 사람에게 달려 있다는 게 결함이다.**
    · 해법: 가드 FQN 목록을 뽑아 **소스에 그 심볼이 실재하는지** 검사하는 테스트 하나.
      `require(files.isNotEmpty())`(#4)가 "빈 디렉터리"를 막은 것과 같은 자리의, FQN 축 버전이다.
    · ⚠️ **P3의 남은 이동(#25·#26·#27) 전에 세우는 것이 낫다** — 그 이동들이 같은 위험을 또 만든다.

69. **`RepoPaths`가 못 모으는 것 둘** (AI 모델: Sonnet, 노력정도: 중간)
    · ⓐ **패키지 FQN은 여전히 흩어져 있다.** `#24`에서 고쳐야 했던 10곳(가드 5 + 자기검증 픽스처 4 + KDoc 1)이
      한 곳도 상수로 모여 있지 않았다. **P3가 움직이는 것은 파일이 아니라 패키지인데 보호는 파일 쪽에만 있다.**
    · ⓑ `app-android/src/test`에 `shared/src/commonMain/.../application/...` **하드코딩 경로 8곳**이 남아 있다
      (`ReplayRecordingContractTest`·`SubscriptionWiringContractTest`·`PlayEffectContractTest` 등).
      `#63`이 `File("src/main` 패턴만 잡아 이쪽은 집계 밖이었다.

70. **`shared.engine` vs `shared.enginecontract` — 이름으로 구분이 안 된다** (AI 모델: Sonnet, 노력정도: 낮음)
    · `#24`의 유일한 낙제점이다. app-android의 실제 import 분포가
      domain 102 · enginecontract 42 · policy 22 · … 로 이름만 봐도 계층이 읽히는데,
      **`shared.engine`(`EngineOperationPolicy.kt` 1파일, import 2건)만 예외**다.
    · `shared.enginepolicy`로 개명하거나 `shared.policy`로 흡수한다.
      ⚠️ `commonTest`의 `engine/EngineOperationPolicyTest.kt`도 함께 움직인다.

72. **import 정렬이 깨졌다** (AI 모델: Sonnet, 노력정도: 낮음)
    · `#24`의 FQN 치환이 심볼 이름 기준 정렬을 FQN에 그대로 적용해, 옮긴 파일들의 import 블록이
      알파벳 순서를 잃었다. **lint가 이를 잡지 않아 조용히 퍼진다.**
    · ktlint `import-ordering` 도입을 검토하거나, 한 번 정렬하고 끝낸다. 어느 쪽인지 판단이 먼저다.

73. **원칙 문서의 4계층 정의가 실제 포트와 어긋난다** (AI 모델: Opus, 노력정도: 중간)
    · `#29`가 드러냈다. `docs/ARCHITECTURE.md`가 *"4계층은 5계층 이상을 모른다"* 고 적는데,
      **실제 포트 둘은 그럴 수 없다**:
      - `PremiumStateStorePort` — `save(state: PremiumState)` / `load(): PremiumState`, **두 메서드 전부**
      - `AuthClientPort` — 6개 메서드 중 5개가 `AuthState`를 반환
    · 파일을 가르지 않는 한 떼어낼 수 없고, **패키지를 하나 더 신설해도 사라지지 않는다** —
      "port가 state를 안다"가 "portstate가 state를 안다"로 이름만 바뀐다.
      제네릭화는 동작 변경이라 순수 이동이 아니다. 그래서 `#29`는 **0을 만드는 대신 2를 보이게** 뒀다.
    · ⭐ **같은 문서가 스스로 반쯤 인정하고 있다** — α 포트를 *"도메인 패키지 쪽(5~6계층과 같은 위치)에 둔다"* 고
      적은 대목이 정확히 이 사정의 흔적이다.
    · → 정의를 실제에 맞게 고친다. *"포트는 자기가 실어 나르는 상태 타입을 안다"* 가 예외가 아니라 **정상**임을
      명문화하는 쪽이 정직해 보인다. ⚠️ 이 문서는 **"새 앱에 복사해 간다"가 목적**이라 구멍을 두면 복제된다.

#### P2 — 엔진 동시성 (독립 트랙 · 어느 단계와도 병렬)

14. **프로세스 수명 뮤텍스 + 1계층 실체화** (AI 모델: Opus, 노력정도: 최대)
    · `KataGoProcessEngineAdapter`의 `process`/`input`/`output`(+analysis 3종)을 값 객체로 묶고 기동·폐기를
      전용 `lifecycleMutex` 안으로. `ensureProcessStarted()`를 suspend로(double-checked).
      타임아웃 재시작은 **자기 세대 == 현재 세대일 때만** destroy(**ABA 방지**).
    · `KataGoProcessRuntime`을 실제 타입으로: `interface EngineProcessRuntime` + `LocalKataGoProcessRuntime`.
      어댑터는 핸들의 writer/reader만 쓰고 프로세스를 직접 만들지 않는다.
    · 실측 근거: `ensureProcessStarted()`가 non-suspend이고 **12개 호출부 전부 뮤텍스 밖**, `@Volatile` 없음.
15. **오퍼레이션 단위 직렬화** (AI 모델: Opus, 노력정도: 최대) — 14 뒤
    · `LocalEngineSessionClient`의 공개 suspend 메서드를 Mutex 안에서. 현재 busy 게이트는 check-then-act이고
      `syncToGameState`는 N+1개 독립 명령이라 다른 오퍼레이션이 끼어든다.
      TopMoves/ScoreEstimate는 `tryLock`으로 즉시 포기해 기존 deferral 경로로(UX 유지).
    · 🔴 **함정 71**: 평범한 `withLock` 금지. `forceReset`은 이 락을 **절대 잡지 않는다.**
17. **타임아웃 예산 단일화 + 공통 실패 타입** (AI 모델: Opus, 노력정도: 높음)
    · `searchTimeoutMillisFor`를 `:shared`로 올려 로컬/원격이 같은 함수를. 현재 같은 `AnalysisLimit`에
      **로컬은 캡+20초(캡 없으면 120초), 원격은 항상 33초**다.
    · `shared.enginecontract`에 `EngineOperationFailure`(Timeout/Transport/Protocol/EngineRejected).
      재시도는 **Transport에 한해** 2계층 안에서 1회 백오프(**탐색 타임아웃은 재시도 금지**).
18. **세대 관통 (정책 타입 중복 제거는 완료)** (AI 모델: Sonnet, 노력정도: 중간)
    · `LocalEngineSessionClient` 생성자에 `currentSessionGeneration: () -> Long` 추가.
      현재 3계층이 `0L`을 박아 넣어 **모든 `position_analysis` operationId가 g0으로 찍혀
      실제 세션 로그와 대조 불가**다.
    · ⚠️ 생성자 시그니처가 바뀌어 **`app-android`의 배선까지 번진다.** 파일 충돌면이 넓다.

21. **persistence 동시성·내구성** (AI 모델: Opus, 노력정도: 높음)
    · 엔타이틀먼트 스토어의 load-modify-save에 락이 없어 **광고 보상과 출석 클레임이 겹치면 앞선 클레임이 사라진다.**
      `GameHistoryStore`는 대국 1건마다 `index.json` 전체를 비원자적 재작성 → tmp+renameTo.
    · ⚠️ **함정 69**: 스키마 버전 불변.
22. **`GameSetup` 값 객체 — 덤 유실의 구조적 해법** (AI 모델: Opus, 노력정도: 높음)
    · 판 정체성(boardSize/ruleset/handicapCount/komi)을 값 객체로 묶어 **코덱이 그 하나만 왕복**하게.
      지금은 세 코덱이 각자 손으로 필드를 골라 담아 **같은 종류의 누락이 또 난다.**
    · ⚠️ **기존 4필드를 파생 프로퍼티로 남겨 호출부 변경 0**으로. ⚠️ **함정 69**: 스키마 번호를 올리지 않는 범위에서만.
74. 🔴 **5계층이 취소를 다시 삼킨다 — `#16`이 절반만 닫혔다** (AI 모델: Opus, 노력정도: 중간)
    · `#16`이 2계층(`attemptJsonAnalysis`)에서 `CancellationException`을 rethrow하게 만들었는데,
      **`match/MatchTurnOrchestration.kt`의 `selectAiMoveFromAnalysis`가 `runCatching { … }.getOrNull()`로
      그것을 다시 삼킨다.** 삼킨 직후 `engineAdapter.genMove(aiPlayer)`를 부른다(직접 확인).
    · 🔴 즉 **AI 착수 경로에서는 타임아웃이 여전히 예산을 두 번 쓴다.** `#16`의 커밋 본문이
      *"같은 예산으로 GTP에서 또 태우던 것이 없어졌다"* 고 적은 것은 **그 경로에 대해서는 과장**이다
      (다른 경로 — TopMoves·형세판단 — 에서는 실제로 닫혔다).
    · ⚠️ **회귀는 아니다.** 전에는 삼킴이 둘이었고 지금은 하나다. 다만 완결이 아니다.
    · 🔴 **고치면 실기가 필요하다.** 타임아웃이 위로 올라가면 **AI가 수를 못 두고 멈추거나 UI가
      에러를 보일 수 있다** — `#16`이 남긴 `needsDevice` 중 가장 큰 위험과 같은 자리다.

#### P3 — 이름공간 정렬 (순수 이동, 동작 변경 0)

> ✅ **#24가 닫혔다(2026-09-24).** `shared` 루트 22→0, `shared.domain`의 import 0줄.
> 그 과정에서 **처방 §3이 글자 그대로는 성립하지 않는다**는 것이 드러났다 — 계가기 셋이 순수 바둑
> 규칙인데 반환형 `FinalScoreResult`와 그 첫 필드 `EngineStatus`가 엔진 계약에 있다. 로컬 계가기들이
> **가짜 `EngineStatus`를 만들어 채우고 있다**(→ #67). 타입을 가르지 않고 `shared.scoring`을 신설해
> 역방향을 0으로 만들었다 — **P3의 "파일 단위로만 이동" 규칙이 선택을 결정했다.**
>
> ⚠️ **이 단계의 모든 커밋은 diff가 package/import 줄로만 구성된다.** 동작 변경이 한 줄이라도 섞이면
> 컴파일러의 완전성 보증이 사라진다. 검증: `git diff -U0`에서 package/import 이외 변경 **0줄**.
> ⚠️ **전부 `LayeringContractTest.kt` 경로를 함께 커밋**해야 하므로 **직렬로** 한다(1,829줄 파일 3중 충돌 방지).

25. **app-android 어댑터 축출** (AI 모델: Sonnet, 노력정도: 중간)
    · 4계층 SDK 어댑터 6개(`AndroidBillingClient`·`AndroidAuthClient` 등, `androidx.compose` import **0건**)가
      `package com.worksoc.goaicoach.ui`에 있다 → `platform/`으로.
26. **조립 코드 축출** (AI 모델: Sonnet, 노력정도: 중간)
    · `MainActivity`·`GoAiCoachApplication`·`*Coordinator`·`*ControllerWiring`·`PremiumPurchaseGlue` → `composition/`.
27. **`ui` 패키지 122파일 분할** (AI 모델: Opus, 노력정도: 최대) — **48 뒤에**
    · `shell/`·`play/`·`study/`·`history/`·`monetization/`·`account/`·`designsystem/`.
    · 🔴 **지금 `ui`는 같은 패키지라 파일 간 import가 아예 생기지 않는다**(저장소 전체에서 `ui` import 10줄).
      **참조 그래프를 읽을 수조차 없다** — 분할 즉시 의존이 전부 import로 드러나고, **그때 비로소 모듈 승격을
      설계할 근거가 생긴다.**
    · ⚠️ **함정 72**: `ui/` 단독 점유. 진행 중 다른 스레드에 `ui/` 금지를 알린다. 시간 상한을 못박는다.
31. **문서 정본화** (AI 모델: Sonnet, 노력정도: 낮음)
    · P3이 끝난 실제 패키지 구조를 `GO_AI_COACH_ARCHITECTURE_ROADMAP.md`에 반영.
    · ✅ **P3 직후 `ui` 패키지 간 엣지와 SCC를 실측해 기록하라** — 모듈 경계는 **이 측정을 보고** 결정한다.
      측정 전에 목표 그래프를 확정하는 것은 *"측정하기 전에 답을 적는 것"* 이다.

67. **`FinalScoreResult.status: EngineStatus`를 걷어낸다** (AI 모델: Opus, 노력정도: 높음)
    · `FinalScoreResult`는 **도메인 타입인데 엔진 필드 하나를 억지로 달고 있다.** 생산자 5곳 중
      **셋이 엔진과 무관한 로컬 계가기**이고, 그들이 `"Local area score complete."` 같은
      **가짜 `EngineStatus`를 만들어 채운다.**
    · 이것 때문에 계가기 셋이 `shared.domain`에 못 들어가고 `shared.scoring`으로 갔다.
      걷어내면 `scoring`이 `domain`으로 합쳐질 수 있다.
    · ⚠️ **동작 변경이다** — `FinalScoreResult`는 `GameSessionStore`가 직접 직렬화하는 **저장 스키마**다(함정 69).
      필드 제거는 스키마에 닿는다. **실기 검증 필요.**

#### P4 — 사이클 절단 + 계약 좁히기

32. **사이클 절단점 신설 + 의존 역전** (AI 모델: Opus, 노력정도: 최대)
    · `application/contract/` 신설 — `GameSessionEffect`처럼 3↔5가 공유하는 타입을 의존 없는 곳으로 내린다.
    · `LocalEngineCoreSessionDelegate`가 `MatchReferee`/`applyAiTurn`을 직접 부르는 대신 **3계층이 정의한
      함수 타입 파라미터로 주입**받게 뒤집는다.
    · 🔴 **17패키지 SCC**를 끊는 작업이다. 이게 끝나야 모듈 분리가 가능하다.
33. **사이클 회귀 테스트** (AI 모델: Opus, 노력정도: 높음) — 32 뒤
    · import 그래프를 읽어 **SCC 크기 > 1이면 실패**하는 테스트 하나.
      **이 하나가 47개 문자열 테스트보다 강하다.**
34. **Konsist 도입** (AI 모델: Opus, 노력정도: 높음) — 24~29 뒤
    · PSI 기반이라 **주석·문자열을 애초에 코드로 보지 않아** 날것 `readText()` 결함이 원천 소멸하고,
      KMP `commonTest`에서 직접 돌아 `:shared` 규칙을 `:shared` 안에 둘 수 있다
      (지금은 app-android 테스트가 shared 소스를 넘겨다보는 기이한 구조다).
    · ⚠️ **착수 게이트**: 모듈 승격 후에도 **남을 규칙이 몇 개인지 먼저 세라.** 대부분이 모듈로 대체될 거면
      같은 규칙을 grep→Konsist→모듈로 **세 번 쓰게 된다.**
    · ⚠️ 이관 불가능한 "이 파일이 이 함수를 부르지 마라" 계약 20여 개는 **이관하지 말고 `internal` 가시성으로 소멸**시킨다.
35. **`EngineSessionClient` 14메서드 분할** (AI 모델: Opus, 노력정도: 높음)
36. **`BoardPosition` 도입 1단계** (AI 모델: Opus, 노력정도: 높음)
    · 좌표가 String/Int로 돌아다니는 원시 타입 집착 해소. **순수 추가**로 시작해 호출부 회귀 0.
37. **합법수 판정 비용 제거** (AI 모델: Sonnet, 노력정도: 중간)
    · `BoardRules.validate(state, move): MoveRejection?`(sealed) **추가**. `play()` 시그니처와 예외는 그대로 둔다 →
      **호출부 회귀 0으로 이득만.**
    · 근거: `LegalMoveGenerator`가 `runCatching { state.play(...) }.isSuccess`로 **판 전체를 순회**하고,
      `GoBoard`가 **드래그 매 프레임** `isLegalPlay`를 부른다.
38. **도메인 알고리즘 중복 제거** (AI 모델: Opus, 노력정도: 높음) — 9 뒤
    · `BoardAreaScorer`와 `BoardTerritoryScorer`의 플러드필이 **타입 이름만 다른 바이트 단위 동일 복제**다.
      `BoardRegionAnalyzer`로 통합.
    · ⭐ **통합보다 값진 것은 단언이다** — *"두 룰셋이 같은 판에서 같은 ownership을 낸다"* 를 테스트로 심어
      **복제가 다시 갈라지는 것을 막는다.**
39. **5→6 방향 뒤집기 + 과금 게이트 판정 단일화 + `ScoreSyncRunner` 3중복 제거** (AI 모델: Opus, 노력정도: 높음)

#### P5 — 상태 소유자 (가장 위험 · 병렬 불가 · 태스크당 커밋 하나 + 실기 검증)

40. **`ViewModel` 얇은 래퍼** (AI 모델: Opus, 노력정도: 최대)
    · `GoCoachSessionViewModel`이 shared의 `GameSessionStateHolder`와 `viewModelScope`를 **소유하기만** 한다.
      ⚠️ **shared 홀더는 손대지 마라** — Compose·Android 무의존인 것이 **iOS 타깃을 살려 두는 유일한 근거**다.
    · 🔴 **인수 기준은 실기 시험이다**: 개발자 옵션 **「액티비티를 유지하지 않음」 ON** → 대국 20수 → 홈 → 복귀 시
      **판·화면 위치 생존.** 현 코드에서 **반드시 실패하고** 수정 후 **반드시 통과한다.**
    · ⚠️ `scope`를 `viewModelScope`로 옮기면 **잡 수명이 컴포지션 이탈 이후까지 늘어난다.**
      "어떤 잡이 화면 이탈에서 취소되어야 하는가"를 먼저 정의하지 않으면 유령 갱신이 생긴다.
41. **`GameSessionScope` 도입** (AI 모델: Opus, 노력정도: 높음)
    · 대국 하나가 끝났을 때 무엇이 정리되고 무엇이 남는지가 지금 정의돼 있지 않다(전부 앱 전역).
42. **`WiringContext` 5분할** (AI 모델: Opus, 노력정도: 최대)
    · 멤버 **64개**(val 17 + 스냅샷 1 + 게터 25 + 세터 16 + 액션 5), 배선 인자 **236개**를
      `SessionPorts`/`StateReader`/`StateWriter`/`EngineRuntimeAccess`/`SessionEffects`로.
    · 🔴 **함정 67을 먼저 읽어라.** `remember` 키 제거는 이 분할과 **별개**이고, 선행 감사 없이는 금지다.
43. **컨트롤러 배선 테스트** (AI 모델: Opus, 노력정도: 높음) — 42보다 **먼저**
    · 컨트롤러 **12개 중 11개가 단위 테스트에서 한 번도 생성되지 않는다**(테스트에 이름이 *문자열로만* 등장).
      배선이 검증되지 않아 여기서 나는 버그는 **CI 전부 통과 후 실기에서만** 드러난다.
    · ⚠️ *"인스턴스 동일성 유지"* 만 보는 테스트는 **함정 67의 동결 버그를 통과시킨다.**
      *"설정 변경 → 컨트롤러가 새 `playerSetup`을 본다"* 를 단언해야 한다.
44. **화면 상태 3분할 + `UiState` 파일 3개 분리** (AI 모델: Opus, 노력정도: 높음)
    · `PremiumUiState`(581줄)·`BotCharacterUiState`(569줄)·`ConsumableUiState`를 **상태/빌더 ↔ Composable 렌더러**로.
    · ⚠️ 이건 **이동이 아니라 재설계**다. P3(순수 이동)에 섞지 마라.
45. **전역 `object` 회수** (AI 모델: Opus, 노력정도: 중간)
    · `SplashVisibility`·`FinishedGameFlow`·`AppFontScaleState` — 코드에 사유가 **"훅 예산 절약"** 이라고 적혀 있다.
46. **예산 지표 교체** (AI 모델: Opus, 노력정도: 중간) — 45 뒤
    · 분모를 **"문자열 개수"에서 "셸이 소유해도 되는 상태의 종류 화이트리스트"** 로. 예산 상수를 `architecture-budgets.json`으로 분리.
    · ⚠️ **숫자 상향도 폐지도 안 된다** — `GoCoachApp`이 god file로 되돌아가는 것을 막는 **유일한 장치**다.
47. **`AppContainer` + Compose 성능** (AI 모델: Opus, 노력정도: 높음)
    · ⚠️ `@Immutable` 작업 **전에** Compose Compiler metrics를 켜고 현재 unstable 목록을 **baseline으로 커밋**하라 —
      불변이 아닌 타입에 `@Immutable`을 붙여 UI가 갱신되지 않는 **조용한 버그를 잡을 유일한 수단**이다.

#### P6 — 선택적 확장 (개별 승인 · 하나도 안 해도 완결)

48. **`UiStrings` 분해 — 차단 해제** (AI 모델: Opus, 노력정도: 최대) — **27보다 먼저**
    · 🔴 생성자 파라미터 **241개 / JVM 한도 255 — 여유 14칸.** 2026-09-18에 이미 런타임 `ClassFormatError`를 냈고
      지금은 위성 16파일 Map 우회로로 버티는 중이다. **신규 문구를 넣을 정식 자리가 없다.**
    · **"신규 기능이 오늘 막혀 있다"는 유일하게 검증된 사실**이다. 취향 문제가 아니다.
    · 기능 축 분해가 **27(`ui` 패키지 분할)의 안내도**가 된다 — 나중에 하면 같은 파일군을 두 번 만진다.
    · 검증: `git diff -U0 | grep '^[+-].*"' | sort` 로 **문구 문자열 리터럴이 하나도 안 바뀌었음**을 대조.
    · ⚠️ **함정 72**: `ui/` 단독 점유. `ui/`는 저장소에서 가장 자주 바뀌는 상위 8개 파일이 전부 모인 곳이다.
49. **모듈 후보 A 승격** (AI 모델: Opus, 노력정도: 최대) — **32·33 뒤, 별도 승인**
    · `:core:domain` → `:core:enginecontract` → `:core:application` → {`:engine-android`, `:app-android`}.
    · ⭐ **인수 기준은 테스트가 아니라 파일 한 개다**: `:core:domain`의 `build.gradle.kts`
      **`dependencies` 블록이 비어 있다는 사실 자체**가 *"도메인은 아무것도 모른다"* 를 증명한다.
    · ⚠️ `MatchReferee`가 `FinalScoreResult`를 import한다 — **`match` 4파일을 파일 단위로 재배정**하지 않으면
      첫 컴파일에서 순환으로 멈춘다.
50. **`build-logic` 컨벤션 플러그인** (AI 모델: Sonnet, 노력정도: 중간)
51. **디자인 시스템 추출** (AI 모델: Opus, 노력정도: 높음)
    · 하드코딩된 색·치수(`dp`, `Color(0xFF…)`)가 흩어져 있다. 토큰으로.
52. **바둑판 접근성** (AI 모델: Opus, 노력정도: 높음)
    · **핵심 화면인 `GoBoard.kt`에 접근성 시맨틱스가 전혀 없다.** 스크린리더로 둘 수 없다.
53. **프로덕션 오류 가시성** (AI 모델: Sonnet, 노력정도: 중간)
54. **원격 엔진 MQ 이식** (AI 모델: Opus, 노력정도: 최대) — **19 뒤**
    · 프로토타입은 260829 완료·main 반영. 남은 건 앱 이식이고 **승인 대기**다.
    · ⚠️ **19(원격 코덱 komi)를 먼저 고치지 않으면 덤을 모르는 채로 분석한다.**
55. **툴체인 업그레이드(AGP 9.0 등)** (AI 모델: Sonnet, 노력정도: 중간)
    · ⚠️ **「서 있는 답」이 이미 답했다**: 착수 전 첫 질문은 *"AGP 9.0이 아직도 필요한가"* 다.
      콘솔이 요구하지 않으면 급하지 않고, 하게 되면 R8이 함께 올라가 **함정 1(enum 상수 이름 = 저장 포맷)이 다시 열린다.**
56. **숨은 난이도 티어 추적** (AI 모델: Sonnet, 노력정도: 낮음)
    · 초급/중급/고급이 UI에서 숨겨졌지만 코드는 보존돼 있다(대국장 로드맵 예정). 되살릴지 지울지 판단.
57. **`Play Asset Delivery`** (AI 모델: Opus, 노력정도: 최대) — **단독 브랜치·단독 인터널 검증**
    · AAB 109MB 중 **93MB가 KataGo 모델**이다. ⚠️ 설치 경로가 바뀌므로 다른 작업과 **절대 병행하지 마라** —
      인터널 트랙에서 설치 실패가 났을 때 원인이 모듈 재배치인지 에셋 팩인지 분리할 수 없다.

---

## 사용자 결정 대기

| 항목 | 물어야 할 것 |
| --- | --- |
| 49 (모듈 승격) | P3·P4가 끝나 실제 import 그래프가 드러난 뒤 착수 여부를 정한다. **그 전에 목표 그래프를 확정하지 않는다.** |
| 54 (MQ 이식) | 제품 기능인가 기술 PoC인가 — 활성 백로그 U-33·34와 같은 질문 |
| 57 (PAD) | 109MB를 줄일 필요가 실제로 생겼는가(설치 이탈 관측 등) |

---

## 관련 문서

- `work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md` — **설계 근거·실측·함정 A~J·처방**. 착수 전 해당 절을 읽는다
- `docs/spec/PITFALLS.md` — 함정 1~81 전문
- `docs/ARCHITECTURE.md` — 7계층 원칙(앱 비종속)
- `docs/spec/GO_AI_COACH_ARCHITECTURE_ROADMAP.md` — 계층별 파일 매핑(정본)
- `work/roadmap/260923-_ACTIVE_BACKLOG.md` — **기능** 일감(이 문서와 번호 체계가 다르다)
