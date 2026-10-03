# 원격 엔진 MQ 프로토타입 (Stage F)

원격 엔진 요청을 MQ(세션 토픽)로 나르고 여러 후보 엔진이 답하는 구조의 프로토타입이다. 2026-08-18에 만들어 2026-08-29에 main에 들어왔고,
**앱 이식은 승인 전**이다. 설계·결과는 `260818-_REMOTE_ENGINE_MQ_TRANSPORT.md` §7(반복 승률 편차 0.0023~0.0186, 허용오차 제안 약 0.10~0.11).
2026-10-03 백로그 #214에서 `scripts/remote-engine-mq-prototype/`에서 이리로 옮겼다(내용 변경은 경로 표기뿐).

| 파일 | 무엇 |
| --- | --- |
| `_prototype_common.py` | 가짜 분석 결과, JSONL 기록, 도착 순위 보상(5/3/2/1) |
| `run_session_topic_mqtt_prototype.py` | MQTT 세션 토픽에서 요청자·후보 엔진들(`--role demo`가 전부 띄운다) |
| `run_session_topic_firestore_prototype.py` | 같은 시나리오를 Firestore 에뮬레이터로(`firebase.json`·`firestore.rules`) |
| `run_consistency_check_experiment.py` | **진짜 KataGo**로 정합성 — 원격 분석 서버(`../run-katago-remote-analysis-server.py`)의 엔진을 그대로 쓴다 |
| `run_timeout_parallel_fallback_experiment.py` | 로컬·원격 경주(시간 초과 시 대체) |
| `mosquitto-local.conf` | localhost:1883 |

## 준비(이 폴더만 외부 패키지가 필요하다)

실험실의 나머지는 표준 라이브러리만 쓴다. 이 프로토타입만 MQTT·Firestore 클라이언트가 필요하다 — 옛 venv는 사라졌으니 새로 만든다:

```sh
python3 -m venv engine-lab/remote/.mq-prototype-venv
source engine-lab/remote/.mq-prototype-venv/bin/activate
pip install paho-mqtt google-cloud-firestore
```

MQTT 브로커는 `brew install mosquitto` 후 `mosquitto -c engine-lab/remote/mq-prototype/mosquitto-local.conf -v`.
실행 기록은 `runs/`에 쌓이고 **gitignore**다(가짜 분석 결과로 돈 프로토타입 기록이라 실험실 결과와 달리 커밋하지 않는다).
