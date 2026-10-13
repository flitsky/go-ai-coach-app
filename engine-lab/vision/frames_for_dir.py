"""폴더 안 사진마다 원근을 걷어낼 틀을 계산한다(백로그 #263) — 정답이 없는 사진 묶음(인터넷·사용자 사진)용.

앱의 `OpenCvPerspective`와 같은 계산(`lab/perspective.py`)이다. 결과는 사진 파일 이름 → 틀(없으면 null) JSON —
앱 JVM 테스트(`BoardPhotoGalleryTest`)가 읽어 앱과 같은 순서(펴기 → 판 찾기)로 인식한다.

    engine-lab/vision/.venv/bin/python -I engine-lab/vision/frames_for_dir.py <사진 폴더> [출력 JSON]
"""

import json
import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lab import perspective  # noqa: E402

SUFFIXES = {".jpg", ".jpeg", ".png"}


def main(argv):
    src = Path(argv[0])
    out = Path(argv[1]) if len(argv) > 1 else src / "frames.json"
    frames = {}
    for p in sorted(src.iterdir()):
        if p.suffix.lower() not in SUFFIXES:
            continue
        img = cv2.imread(str(p), cv2.IMREAD_COLOR)
        if img is None:
            continue
        result = perspective.rectifying_homography(img)
        if result is None:
            frames[p.name] = None
            continue
        hmat, (w, h) = result
        quad = cv2.perspectiveTransform(np.float64([[0, 0], [w, 0], [w, h], [0, h]]).reshape(-1, 1, 2), np.linalg.inv(hmat)).reshape(-1, 2)
        frames[p.name] = {"quad": quad.round(2).tolist(), "size": [int(w), int(h)]}
    out.write_text(json.dumps(frames, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{sum(1 for v in frames.values() if v)}/{len(frames)} 펼 틀 → {out}")


if __name__ == "__main__":
    main(sys.argv[1:])
