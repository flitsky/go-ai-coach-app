# 바둑판 사진 인식 실험실 (engine-lab/vision)

백로그 #263(바둑판 사진 인식 초고도화)의 맥 쪽 실험실이다. 앱의 사진 인식(`app-android/.../vision/`)에 넣을 OpenCV 처리를
**여기서 먼저 다듬고 재고**, 앱은 같은 계산을 함수 단위로 옮긴다(실험실 바깥 `engine-lab/README.md`의 규칙을 따른다 — 앱 코드를 import하지 않는다).

## 왜 파이썬인가

OpenCV 코드는 맥의 JVM 단위 테스트에서 돌 수 없다 — 앱의 OpenCV(4.14, Play 16KB 정렬 때문에 4.11 이상)와 맞는 데스크톱 자바 OpenCV가 없다(4.9까지).
그래서 OpenCV 부분은 여기서 재고, 결과(숫자)를 앱 테스트 리소스로 넘겨 **펴고 찾는 순수 Kotlin 쪽**을 JVM에서 잰다. 앱의 OpenCV가 같은 결과를 내는지는 기기 계기 테스트가 본다.

| 실험실 | 앱 | 맞춰 보는 곳 |
| --- | --- | --- |
| `lab/perspective.py` 원근 걷어낼 틀 | `vision/OpenCvPerspective.kt` | `OpenCvPerspectiveDeviceTest`(기기) |
| `measure_perspective.py --export` → `perspective-frames.json` | `vision/PerspectiveLocate.kt` | `BoardPerspectiveLocateTest`(JVM 래칫) |

⚠️ 숫자(작업 800px · 선분 4% · ±20° · 2° · RANSAC 300회 · 원근 세기 0.06)를 바꾸면 **두 곳을 함께** 바꾸고 `--export`로 JSON을 다시 쓴다.

## 준비

이 실험실만 표준 라이브러리 밖이 필요하다(OpenCV·NumPy·Pillow). `.venv`는 git 밖이다:

```sh
/opt/homebrew/bin/python3.12 -m venv engine-lab/vision/.venv
engine-lab/vision/.venv/bin/pip install -r engine-lab/vision/requirements.txt
```

사진은 저장소 밖이다 — 기준 이미지 `scripts/fetch-board-photo-fixtures.sh`, 사용자 사진 `scripts/prepare-own-board-photos.py`.

## 돌리기

```sh
engine-lab/vision/.venv/bin/python -I engine-lab/vision/measure_perspective.py            # 원근 걷어내기 측정표
engine-lab/vision/.venv/bin/python -I engine-lab/vision/measure_perspective.py --export   # + 앱 테스트용 틀 · 기기용 변형 이미지
engine-lab/vision/.venv/bin/python -I engine-lab/vision/measure_outline.py                # (접은 길) 윤곽선으로 외곽 사각형 찾기
```

결과는 `runs/`(git 밖)에 남는다. 앱에 넘기는 것은 `--export`의 JSON뿐이다.

## 지금까지 잰 것 (2026-10-13, 기준 이미지 5장 + 변형 30장)

- **외곽 사각형(윤곽선)** — 바깥 격자선에 0.3칸 안으로 붙은 것이 35장 중 4장. 판 가장자리·사진 테두리를 잡을 뿐이라 **접었다**.
- **원근 걷어내기(소실점)** — 사다리꼴 12% 다섯 장 모두 원근 잔차 0.06~0.13 → 0.002 이하, 똑바로 찍은 사진은 하나도 건드리지 않음.
  앱에서 펴고 찾으면 사다리꼴 변형이 01 33.8→69.8% · 02 42.9→100% · 04 0→98.6%(03은 원래 100%, 05는 도면이 둘이라 그대로 0%).
