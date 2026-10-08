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
python3 engine-lab/experiments/e5_phone_cost/run.py --label s23 --adb-serial <시리얼>                        # E5 폰(사람 모델 비용)
```

## 실험

| # | 질문 | 상태 | 결론 |
| --- | --- | --- | --- |
| E1 `experiments/e1_low_visit_candidates/README.md` | 방문 8·16 + 1초대로 줄이면 후보가 몇 개 나오고, 캐릭터 5단계는 실제로 무엇을 두나 — 짧게 탐색하면 최적수만 남아 실수가 실수가 아니게 되나 | 맥 ✅ · 폰 → #216 | 16방문 후보 평균 3.1개 — 41%는 「최하」 없음, 16%는 모두 최선수. 후보는 애초에 그럴듯한 수라 초보도 한 수 1.7집 손해뿐 |
| E2 `experiments/e2_human_sl/README.md` | 사람 모델 1방문 추출의 손해는 어떤가, 지금 캐릭터 5단계는 어느 프로필쯤인가(#215 준비) | 맥 ✅ | 사람 모델 20k 4.6 → 3d 2.4집. 캐릭터 5명 모두 3단보다 손해가 적다(13줄 초보 2.4 = 3단) |
| E2b `experiments/e2b_tier_vs_human/README.md` | 캐릭터 대 사람 모델 프로필, 끝까지 두면 누가 이기나(#215 준비) | 맥 ✅ | 13줄 — 초보는 사람 1단(75%)과 5단(25%) 사이, 중수 ≈ 9단, 초고수는 전승 |
| E3 `experiments/e3_resign_threshold/README.md` | 「불리한 채 N수」 기권 조건을 걸면 얼마나 자주 잘못 던지나(#213 준비) | 맥 ✅ | 점수 ≤ −10~−15집 10수 연속 → 잘못 던짐 1~2%, 진 판 2/3~3/4 정리. 승률 기준은 12% 잘못 던짐 |
| E4 `experiments/e4_kgs_rank_matches/README.md` | KGS 급수 설정(사람 모델)끼리 9·13·19줄에서 두면 급수 차이가 나는가 — 공식 값은 19줄 기준(#216) | 맥 ✅ | 3급 차 센 쪽 승률 19줄 77% · 13줄 70% · 9줄 60%. 17~11급은 어느 판에서도 거의 안 갈린다 |
| E5 `experiments/e5_phone_cost/README.md` | 폰에서 사람 모델을 올리면 한 수에 얼마가 드나 — 시간·메모리·스레드·NN 버퍼·JSON 분석(#215 단계 ①) | 폰(S23) ✅ | 사람 모델 평가 1회 0.29초(13줄) · 0.50초(19줄) — 지금 16방문의 1/15. 대가는 메모리: 신경망 하나에 약 500MB(주 + 사람 1.0GB), 설정으로 못 줄인다. 32·40방문은 8~20초. 스레드·NN 버퍼는 덕이 없다 |
| E6 `experiments/e6_single_net/README.md` | 신경망을 하나만 올려도 되나 — 사람 모델 하나로 형세·추천 수를 맡기면 얼마나 틀리나, 주 모델 하나로 급수를 낼 수 있나(#215) | 맥 ✅ | 된다(메모리 그대로). 대가: 형세 오차 중앙값 0.5 → 2.5집, 추천 수 손해 0.27 → 0.78집(탐색) · 2.0집(평가 1회), 5집 출렁임의 38%를 놓침. 영역은 같다. 주 모델 정책을 흩으면 손해는 급수만큼 나온다(온도 1.5~3) — 사람 같은지는 E7 |
| E7 `experiments/e7_main_policy_levels/README.md` | 사람 모델 없이 주 모델 정책을 흩어 급수를 내면 얼마나 사람 같은가(#215) | 맥 ✅(사람다움만) | 급수만큼 잃게 하는 온도(2~3)에서 수의 49~74%가 그 급수의 사람이 거의 안 두는 수(사람 프로필끼리는 멀어도 13%). 약해지기만 하고 사람 같아지지 않는다 |
| E8 `experiments/e8_human_only_pass/README.md` | 사람 모델만 올린 채로 두면 판이 제대로 끝나나 — 통과를 주 모델 대신 9단 프로필의 정책에 맡긴다(#215 ②b) | 맥 ✅ | 36판 모두 통과-통과로 끝남, 끝난 국면의 주 모델 최선수가 통과 35판. 수순 길이는 주 모델이 통과를 정하던 E4와 같다. 9단 정책을 더 보는 것은 판당 3~13번 |
| E9 `experiments/e9_human_net_search/README.md` | 모델 파일을 하나만 싣는다면 — 사람 모델(`rank_9d`)에 16·32방문 탐색을 맡기면 주 모델에 얼마나 미치나(#215, 2026-10-06 사용자 질문) | 맥 ✅ | 같은 방문 수의 주 모델에 48판 중 13승(27%). 추천 수 1위 손해 0.26 → 0.67집. **형세 점수 오차는 0.4 → 1.9집으로 탐색을 붙여도 그대로**(16·32방문 같음) — 대국 상대는 되고 코칭이 무뎌진다 |
| E10 `experiments/e10_hopeless_pass/README.md` | 가망 없는 판에서 AI가 통과하게 하려면 — 「AI 집 0 · 승률 ≤ 1% · 2수 연속」(사용자 안)을 기보 414판에 걸어 본다(#213, 2026-10-07) | 맥 ✅ | 승률만으로는 8.7%를 잘못 걸고, 「집 0」만 더하면 초반에 걸린다. **점수 ≤ −30집까지 걸면 18판에 잘못 0.** 사용자의 130수 대국은 28수째에 걸린다. 심판에게 묻기만 하면 멈춘다 — 문제는 묻지 않는 것. **보조(`split.py`)**: 통과를 종국(수순 길이 ≥ 판의 80%)으로 좁히면 169판에 잘못 1, 중반의 기권 제안(집 0 2수 또는 −30집 10수)은 147판에 잘못 4 |

## 새 실험 만들기

`experiments/eN_이름/`에 `README.md`(질문 · 방법 · 실행 · 결론 · 한계)와 `run.py`를 둔다. `run.py`는 `lab.runs.RunDir`로 결과 폴더를 만들고,
엔진은 `lab.gtp.local_gtp`/`phone_gtp`·`lab.analysis.local_analysis`로 띄운다. 앱 값은 `lab.app_parity`에서만 읽는다.
