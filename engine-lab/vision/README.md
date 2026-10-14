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

## 평가 갤러리 — 정답이 없는 사진을 눈으로 본다

정답이 있는 기준 이미지만 보면 실제 사진의 틀림이 안 보인다(함정 92). 사진 폴더(`test-fixtures/board-photos-web/` · `board-photos-own/`, git 밖)를
**앱과 같은 순서로** 인식해 HTML로 늘어놓는다:

```sh
engine-lab/vision/.venv/bin/python -I engine-lab/vision/frames_for_dir.py test-fixtures/board-photos-web       # 원근 틀 + 격자 틀(OpenCV)
./gradlew :app-android:testDebugUnitTest --tests '*BoardPhotoGalleryTest*'                                     # 앱 순서로 인식 → build/reports/board-photos/gallery-*.json
engine-lab/vision/.venv/bin/python -I engine-lab/vision/render_gallery.py test-fixtures/board-photos-web       # → 그 폴더의 gallery.html
engine-lab/vision/.venv/bin/python -I engine-lab/vision/export_results.py test-fixtures/board-photos-web      # → 사진마다 옆에 <이름>_인식결과.png
```

`<이름>_인식결과.png`는 Finder·미리보기에서 원본과 나란히 놓고 보는 한 장짜리 결과다(사용자 2026-10-14) — 원본 위 찾은 격자와 네 모서리 좌표(A19·T19·T1·A1),
읽은 판(좌표 글자 · 주황 네모 = 확인 필요). 그린 판은 픽셀을 다시 읽어 인식 결과와 한 점도 다르지 않은지 스스로 확인한다.
사진 폴더를 훑는 쪽(`frames_for_dir.py` · `BoardPhotoGalleryTest`)은 이 꼬리(`_인식결과`)가 붙은 파일을 사진으로 치지 않는다.

갤러리는 사진마다 원본 위 찾은 격자(초록 = 핀 건너뜀, 주황 = 핀 화면) · 읽은 판(주황 네모 = 확인 필요) · 출처·라이선스(`sources*.json`) ·
평가 버튼과 메모(브라우저에 저장, 「평가 복사」로 한 번에 옮긴다)를 보인다. 인터넷 이미지는 위키미디어 공용·Flickr(Openverse)의 CC·퍼블릭 도메인만 모았다 —
재배포하지 않는다. ⚠️ 위키미디어는 미리보기 크기를 한꺼번에 요청하면 600초씩 막는다(429) — 2초 간격, 이미 만들어진 크기(1280·960…)부터.

## 격자 틀 — 판 자체의 격자로 펴고 잘라 내기 (`lab/lattice.py`, 2026-10-14)

원근 틀(`lab/perspective.py`)은 사진 전체에서 가장 센 두 방향으로 소실점을 잡는다 — 다리 달린 판의 옆면·다다미·벽이 이기면 엉뚱하게 펴고,
잘 펴도 칸이 납작하거나 판이 사진의 일부라 격자 찾기가 엉뚱한 곳을 잡았다(갤러리 w07·w08·w09·w13·w33). 격자 틀은 **같은 간격으로 늘어선 선
무리 둘**을 찾는다: 긴 LSD 선분 쌍마다 소실점(무작위 없음) → 소실점 둘씩 아핀으로 편 뒤 무리마다 선 위치 히스토그램(긴 선은 중앙값의 2배로 자름)의
자기상관 = 칸 간격과 고른 정도 → 가장 고른 쌍으로 칸이 정사각인 격자 좌표 ↔ 사진 좌표를 만들고, 찾은 줄 + 여유 3칸(표준 크기보다 덜 셌으면 그만큼 더)을
틀로 잘라 낸다. 결과 JSON에는 찾은 줄 수(`lines`)와 가장 바깥 줄들의 네 모서리(`inner`, 틀 좌표)도 담는다 — 앱이 그 격자로 판 찾기를 **시작**한다
(`BoardLocator.locateFromLattice`). 앱 쪽은 `OpenCvLattice`(함수 단위로 같은 계산 — 기기 테스트 `OpenCvLatticeDeviceTest`가 변형 30장으로 대조).
첫 판독이 확실하지 않을 때만 후보로 들어간다(`BoardCandidates`). 판이 작고 흐린 저해상도 사진(500px)에서는 못 잡는다 — 그때는 다른 후보가 이긴다.

## 신경망 돌 판정 (`stone_net.py`, 백로그 #263 단계 5 — 2026-10-14 첫 학습)

교점마다 판을 편 그림에서 ±0.75칸을 24×24로 잘라(`StonePatches` — 앱 판정과 **같은 함수**) 빈 점·흑·백으로 가르는 작은 합성곱망이다(가중치 1만 개 남짓, 33KB).

```sh
./gradlew :app-android:testDebugUnitTest --tests '*StonePatchExportTest*' -PexportStonePatches   # 정답 있는 사진 → runs/patches/(교점 조각)
engine-lab/vision/.venv/bin/python -I engine-lab/vision/stone_net.py            # 대국 묶음 5겹 교차 검증 — 규칙과 같은 조각에서 견준다
engine-lab/vision/.venv/bin/python -I engine-lab/vision/stone_net.py --export   # + 전부로 학습 → 테스트 자산(가중치 · 대조 벡터)
```

- 학습 데이터: 정답 있는 사진마다 정답 모서리 · 앱이 찾은 모서리 · ±0.2칸 흔든 모서리 넷 · (기준 이미지) 변형 30장. 학습 때 돌리기·뒤집기·밝기·노란 조명 변형.
- 검증은 **대국 묶음 단위**(같은 대국을 다른 각도로 찍은 사진은 한 묶음)로 — 학습에 안 쓴 대국으로 잰다.
- 앱 쪽 계산은 순수 Kotlin(`StoneNet`) — 맥 JVM 테스트가 앱과 같은 계산을 한다. `StoneNetParityTest`가 PyTorch와 같은 로짓을 내는지 본다. 실행 엔진 `.so`는 없다.
- **첫 결과(2026-10-14)**: 정답 모서리 신경망 97.3% · 규칙 97.8%, 앱 모서리 98.6% · 99.5% — 아직 규칙보다 낮다. 규칙이 흔들리는 교점만 맡겨도 99.41% 대 99.48%.
  실물 판 사진은 거의 같고(w08~w33 100%), **처음 보는 그림 양식**(기준 이미지 04 인쇄 90% · 기보 w48 84%)에서 약하다 — 대국 23묶음이라 양식마다 한 장뿐이다.
  망을 키우거나 오래 학습해도 그대로였다(데이터 다양성이 한계). 그래서 **앱 판정 경로에는 아직 쓰지 않고** 모델도 테스트 자산에만 둔다.
  사용자 사진(S23)과 합성 데이터로 다시 학습해 규칙을 넘으면 `src/main/assets/vision/`으로 옮겨 연결한다.

## 지금까지 잰 것 (2026-10-13, 기준 이미지 5장 + 변형 30장)

- **외곽 사각형(윤곽선)** — 바깥 격자선에 0.3칸 안으로 붙은 것이 35장 중 4장. 판 가장자리·사진 테두리를 잡을 뿐이라 **접었다**.
- **원근 걷어내기(소실점)** — 사다리꼴 12% 다섯 장 모두 원근 잔차 0.06~0.13 → 0.002 이하, 똑바로 찍은 사진은 하나도 건드리지 않음.
  앱에서 펴고 찾으면 사다리꼴 변형이 01 33.8→69.8% · 02 42.9→100% · 04 0→98.6%(03은 원래 100%, 05는 도면이 둘이라 그대로 0%).
