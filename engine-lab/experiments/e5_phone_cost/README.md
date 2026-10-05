# E5 — 폰에서 사람 모델을 올리면 한 수에 얼마가 드나

**배경**(백로그 #215 단계 ①): 캐릭터가 사람 모델로 두게 하려면 폰에 신경망이 하나 더 올라간다. 리서치의 시간·메모리 숫자는 전부
옛 폰(S22U) 9줄 표본 3개에서 끌어낸 것이라 **신뢰도 낮음**이었다 — 정하기 전에 폰에서 직접 잰다.
같은 측정으로 #218이 넘긴 물음(다시보기 분석을 JSON 단일 질의로 — 분석 프로세스를 하나 더 띄우는 값)과 I-3(분석을 얼마나 깊게 돌릴 수 있나)의 근거도 얻는다.

## 방법

앱이 깔린 폰에서 `adb run-as`로 **앱 안의 KataGo(같은 바이너리·같은 주 모델·같은 cfg)**를 띄워 설정을 바꿔 가며 잰다(`run.py`).

| 설정 | 무엇 |
| --- | --- |
| `main-t1` | **지금 앱의 GTP 프로세스** — 주 모델만, 탐색 스레드 1 |
| `human-t1` · `-t2` · `-t4` | 사람 모델을 같이 올린 GTP 프로세스, 스레드 1 · 2 · 4 |
| `human-t1-buf13` · `human-t4-buf13` | 위 + NN 버퍼를 13줄로(`maxBoardXSizeForNNBuffer`) — 13줄 판만 |
| `gobot-t1` · `gobot-t4` | go-bot 레시피의 한 수(`genmove`): 사람 정책에서 착수 + 주 모델 40방문 |
| `analysis-main` · `analysis-human` | 앱의 JSON 분석 프로세스(1 × 4스레드), 주 모델만 / 사람 모델 같이 |
| `analysis-main+gtp` · `analysis-human+gtp` | 위 + 대국용 GTP 프로세스를 **함께** 띄웠을 때의 메모리 |

- 평가 1회는 `kata-raw-nn`·`kata-raw-human-nn`의 대칭 번호를 돌려 캐시를 피한다. 탐색은 매번 `clear_cache`.
- 시간은 맥에서 `adb` 왕복을 포함해 잰다(왕복 약 2ms — 표에 같이 적는다). 메모리는 `/proc/<pid>/status`의 RSS.
- 사람 프로필은 `rank_5k` 하나로 잰다 — 프로필은 입력 한 줄이라 시간·메모리에 영향이 없다.

## 폰 준비

```sh
adb -s <시리얼> push ~/worksoc/katago/models/b18c384nbt-humanv0.bin.gz /data/local/tmp/human.bin.gz
adb -s <시리얼> shell chmod 644 /data/local/tmp/human.bin.gz
adb -s <시리얼> shell "run-as com.zenit9hub.ai.baduk cp /data/local/tmp/human.bin.gz files/katago/human-b18c384nbt.bin.gz"
adb -s <시리얼> shell rm /data/local/tmp/human.bin.gz
adb -s <시리얼> shell am force-stop com.zenit9hub.ai.baduk     # 앱의 엔진이 같이 돌면 숫자가 섞인다
```

⚠️ 디버그 빌드여야 `run-as`가 된다. 끝나면 `run-as … rm files/katago/human-b18c384nbt.bin.gz`로 지운다(앱은 아직 이 파일을 모른다).

## 실행

```sh
python3 engine-lab/experiments/e5_phone_cost/run.py --label s23 --adb-serial <시리얼>
python3 engine-lab/experiments/e5_phone_cost/run.py --label s23-one --adb-serial <시리얼> --only human-t4 --sizes 13
```

결과: `runs/<시각>-<기계>-<label>/summary.md`(표) · `samples.jsonl`(표본마다).
