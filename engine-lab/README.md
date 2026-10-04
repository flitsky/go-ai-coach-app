# 엔진 실험실 (engine-lab)

맥에서 파이썬으로 KataGo를 다각도로 시험하는 곳이다(백로그 #214, 2026-10-03). 앱(안드로이드·KMP)과 **엄격히 분리**돼 있다 —
앱 빌드·테스트는 이 폴더에 기대지 않고, 이 폴더는 앱 코드를 import하지 않는다.

## 규칙 다섯

1. **앱 코드를 import하지 않는다.** 앱과 맞춰야 하는 값(빠른초급 16방문·후보 8개·5단계 버킷 비율·GTP 1스레드·기본 판·덤·룰·시간 상한)은
   `lab/app_parity.py` **한 곳**에 두고, `tests/test_app_parity.py`가 **앱 소스 글자**를 읽어 비교한다.
   앱의 AI 값을 바꾸면 `make engine-lab-test`가 빨개진다 — 표를 같이 고치고, 옛 값으로 잰 실험은 그 실험 README에 「옛 앱 기준」이라 적는다.
2. **앱과 같은 호출로 잰다.** 캐릭터의 착수는 GTP `kata-search_analyze`(1스레드)이고, 후보 해석·점수 손해·루트 방문(+1)도 앱 그대로다(`lab/gtp.py`).
   캐릭터가 후보를 고르는 규칙도 앱 코드를 옮긴 것이다(`lab/selection.py`).
3. **모델과 cfg는 저장소 밖.** 사람 모델(99MB)은 공개 저장소에 올리지 않는다. 앱 cfg는 gitignore라 워크트리에 없다 — 원본(`~/worksoc/katago/config/katago/`)을 쓴다.
4. **결과는 원자료까지 커밋한다**(사용자 결정 2026-10-03) — `experiments/<실험>/runs/<시각>-<기계>-<이름>/`에 `run.json`(무엇으로 쟀나)·`samples.jsonl`(원자료)·`summary.md`.
5. **시간은 폰에서 잰다.** 맥 Metal은 폰(Eigen CPU)보다 수십 배 빠르다(16방문: 맥 108ms · S22U 3.8초). 맥 결과는 **방문 수**로 말하고, 시간 상한 실험은 `--adb-serial`로 폰에서.

## 준비물

| 무엇 | 어디 | 비고 |
| --- | --- | --- |
| KataGo 1.16.4 | `/opt/homebrew/bin/katago` (`brew install katago`) | 앱과 같은 버전. 맥은 Metal 백엔드 |
| 주 모델 `kata1-b18c384nbt-s9996604416` | brew `share/katago/` | 앱이 싣는 것과 **같은 바이트** |
| 사람 모델 `b18c384nbt-humanv0.bin.gz` | `~/worksoc/katago/models/` | 아래 명령으로 받는다(99,066,230 B) |
| 앱 cfg `gtp_learning.cfg`·`analysis_learning.cfg` | `~/worksoc/katago/config/katago/` | 앱 번들의 원본 |
| 폰 측정(선택) | 디버그 빌드가 깔린 폰 + `adb` | `run-as`로 앱 안 엔진을 그대로 쓴다 |

경로는 환경 변수로 바꿀 수 있다: `KATAGO_BIN` · `KATAGO_MODEL` · `KATAGO_HUMAN_MODEL` · `KATAGO_GTP_CONFIG` · `KATAGO_ANALYSIS_CONFIG` · `KATAGO_WORKSPACE`(`lab/paths.py`).

사람 모델 받기(KataGo 공식 릴리즈 v1.15.0, sha256은 `lab/paths.py`의 `HUMAN_MODEL_SHA256`):

```sh
mkdir -p ~/worksoc/katago/models
curl -L -o ~/worksoc/katago/models/b18c384nbt-humanv0.bin.gz \
  https://github.com/lightvector/KataGo/releases/download/v1.15.0/b18c384nbt-humanv0.bin.gz
shasum -a 256 ~/worksoc/katago/models/b18c384nbt-humanv0.bin.gz
```

파이썬은 **표준 라이브러리만** 쓴다(venv 불필요). MQ 프로토타입만 예외 — `remote/mq-prototype/` 안내를 볼 것.

## 폴더 지도

| 폴더 | 무엇 |
| --- | --- |
| `lab/` | 공용 패키지 — 경로·앱 대응표·바둑판·GTP·JSON 분석·캐릭터 선택 규칙·사람 모델 레시피·기준 평가·결과 폴더 |
| `experiments/` | 실험마다 한 폴더(`README.md` = 질문·방법·결론, `run.py`, `runs/` = 결과) |
| `positions/` | 실험에 쓰는 국면 세트(JSON, 커밋) — `tools/make_positions.py`가 만든다 |
| `cache/` | 기준 평가 결과(국면·수·방문 → 점수). 실험끼리 공유하고 커밋한다 — 같은 수를 두 번 재지 않는다 |
| `tools/` | 국면 세트 만들기 등 |
| `benchmarks/` | 옛 벤치마크 러너(레벨 매치·기기 벤치마크·탐색 모드·후보 확장). `make engine-*-benchmark`가 부른다 |
| `remote/` | 원격 분석 서버(앱 디버그 빌드의 `REMOTE_ENGINE_URL`)와 그 테스트, MQ 프로토타입 |
| `tests/` | KataGo 없이 도는 단위 테스트 + 앱 대응표 검사 |

옛 측정 기록은 `docs/engine/measurements/`에 그대로 있다(읽기 전용 — 그 숫자를 흡수한 문서가 있다). 새 결과는 여기 `experiments/`·`benchmarks/runs/`로.

## 빠른 시작

```sh
make engine-lab-test                                                        # 단위 테스트 + 앱 대응표
python3 engine-lab/experiments/e1_low_visit_candidates/run.py --label mac-v1  # E1 (맥, 방문 수별)
python3 engine-lab/experiments/e2_human_sl/run.py --label mac-v1              # E2 (사람 모델)
python3 engine-lab/experiments/e1_low_visit_candidates/run.py --label phone --adb-serial <시리얼> --sizes 13  # E1 폰(시간 상한별)
```

## 실험

| # | 질문 | 상태 | 결론 |
| --- | --- | --- | --- |
| E1 `experiments/e1_low_visit_candidates/README.md` | 방문 8·16 + 1초대로 줄이면 후보가 몇 개 나오고, 캐릭터 5단계는 실제로 무엇을 두나 — 짧게 탐색하면 최적수만 남아 실수가 실수가 아니게 되나 | 맥 ✅ · 폰 → #216 | 16방문 후보 평균 3.1개 — 41%는 「최하」 없음, 16%는 모두 최선수. 후보는 애초에 그럴듯한 수라 초보도 한 수 1.7집 손해뿐 |
| E2 `experiments/e2_human_sl/README.md` | 사람 모델 1방문 추출의 손해는 어떤가, 지금 캐릭터 5단계는 어느 프로필쯤인가(#215 준비) | 맥 ✅ | 사람 모델 20k 4.6 → 3d 2.4집. 캐릭터 5명 모두 3단보다 손해가 적다(13줄 초보 2.4 = 3단) |
| E2b `experiments/e2b_tier_vs_human/README.md` | 캐릭터 대 사람 모델 프로필, 끝까지 두면 누가 이기나(#215 준비) | 맥 ✅ | 13줄 — 초보는 사람 1단(75%)과 5단(25%) 사이, 중수 ≈ 9단, 초고수는 전승 |
| E3 `experiments/e3_resign_threshold/README.md` | 「불리한 채 N수」 기권 조건을 걸면 얼마나 자주 잘못 던지나(#213 준비) | 맥 ✅ | 점수 ≤ −10~−15집 10수 연속 → 잘못 던짐 1~2%, 진 판 2/3~3/4 정리. 승률 기준은 12% 잘못 던짐 |
| E4 `experiments/e4_kgs_rank_matches/README.md` | KGS 급수 설정(사람 모델)끼리 9·13·19줄에서 두면 급수 차이가 나는가 — 공식 값은 19줄 기준(#216) | 맥 ✅ | 3급 차 센 쪽 승률 19줄 77% · 13줄 70% · 9줄 60%. 17~11급은 어느 판에서도 거의 안 갈린다 |

## 새 실험 만들기

`experiments/eN_이름/`에 `README.md`(질문 · 방법 · 실행 · 결론 · 한계)와 `run.py`를 둔다. `run.py`는 `lab.runs.RunDir`로 결과 폴더를 만들고,
엔진은 `lab.gtp.local_gtp`/`phone_gtp`·`lab.analysis.local_analysis`로 띄운다. 앱 값은 `lab.app_parity`에서만 읽는다.
