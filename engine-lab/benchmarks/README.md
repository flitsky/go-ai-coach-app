# 옛 벤치마크 러너

2026-06~08에 만든 엔진 측정 스크립트들이다. 2026-10-03 백로그 #214에서 `scripts/`에서 이리로 옮기고 낡은 것을 고쳤다.
결과는 `docs/engine/measurements/`에 남아 있고(읽기 전용 기록), 새로 돌리면 기본값으로 **`benchmarks/runs/<이름>-<시각>/`**에 쓴다.

| 스크립트 | 무엇 | `make` |
| --- | --- | --- |
| `run-katago-level-match.py` | 레벨 대 레벨 대국(JSON 분석 엔진) → JSONL | – |
| `run-katago-level-matrix.py` | 위를 세 짝(B16·B32·B64)으로 돌려 요약 | `make engine-level-benchmark` |
| `run-katago-device-benchmark.py` | JSON 분석 16·32·64방문의 지연·후보 수(맥) | `make engine-device-benchmark` |
| `run-katago-search-mode-benchmark.py` | GTP 빠른 경로 vs JSON — 맥, 또는 `--adb-serial`로 폰 | `make engine-search-mode-benchmark(-phone)` |
| `run-katago-candidate-refine-experiment.py` | 후보 확장(`refinePolicyMoves`) 파이썬 이식 실험 | – |

## #214에서 고친 것

- **기본 경로** — 바이너리·모델·cfg를 `lab/paths.py` 한 곳에서 읽는다. 옛 기본 cfg(`app-android/src/friend/assets/…`)는 gitignore라 워크트리에 없었다.
- **출력 기본값** — 옛 `docs/engine-*-logs/…`(없는 폴더)·커밋된 측정 폴더 대신 `benchmarks/runs/`. Makefile 변수도 비워 두면 이 기본값을 쓴다.
- `run-katago-level-match.py` — 빠른 초급이 **2026-08-18 이전의 3단계 백분위**로 남아 있던 것을 지금의 **5단계 버킷**으로(`lab/selection.py`).
  `run-katago-level-matrix.py`의 기본 짝은 옛 「빠른 초급 3단계」(= 최선만)의 뜻을 지켜 `fast_beginner:5`로 옮겼다.
  ⚠️ 이 러너는 JSON 분석 엔진으로 돈다 — 앱의 빠른 초급은 GTP 경로다(후보 수가 조금 다르다). 후보 수 자체는 `experiments/e1_low_visit_candidates`로 잰다.
- `run-katago-search-mode-benchmark.py` — 폰 패키지 기본값을 지금의 applicationId `com.zenit9hub.ai.baduk`로(2026-08-04에 바뀌었다),
  기기의 모델 이름(`model.bin`/`model.bin.gz`)을 찾아 쓰고, GTP 루트 방문에 **루트 자신의 1방문**을 더한다(#203 — 없으면 16방문을 채워도 `root=15`·SHORT).
- `run-katago-candidate-refine-experiment.py` — 사라진 `docs/archive` 문서를 가리키던 주석을 지금 위치(`ENGINE_STRENGTH_RESEARCH.md` §1)로.
