"""폴더 안 사진마다 판 찾기에 쓸 틀 둘을 계산한다(백로그 #263) — 정답이 없는 사진 묶음(인터넷·사용자 사진)용.

- `frames.json` — 원근을 걷어낼 틀(앱의 `OpenCvPerspective` = `lab/perspective.py`).
- `lattice-frames.json` — 격자 자체로 펴고 잘라 낸 틀(앱의 `OpenCvLattice` = `lab/lattice.py`).

둘 다 사진 파일 이름 → 틀(없으면 null) JSON — 앱 JVM 테스트(`BoardPhotoGalleryTest` 등)가 읽어 앱과 같은 길(`BoardCandidates`)로 인식한다.

    engine-lab/vision/.venv/bin/python -I engine-lab/vision/frames_for_dir.py <사진 폴더>
"""

import json
import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lab import lattice, perspective  # noqa: E402
from lab.fixtures import is_photo  # noqa: E402


def main(argv):
    src = Path(argv[0])
    frames, lattices = {}, {}
    for p in sorted(src.iterdir()):
        if not is_photo(p):
            continue
        img = cv2.imread(str(p), cv2.IMREAD_COLOR)
        if img is None:
            continue
        result = perspective.rectifying_homography(img)
        if result is None:
            frames[p.name] = None
        else:
            hmat, (w, h) = result
            quad = cv2.perspectiveTransform(np.float64([[0, 0], [w, 0], [w, h], [0, h]]).reshape(-1, 1, 2), np.linalg.inv(hmat)).reshape(-1, 2)
            frames[p.name] = {"quad": quad.round(2).tolist(), "size": [int(w), int(h)]}
        found = lattice.find_board_frame(img)
        if found is None:
            lattices[p.name] = None
        else:
            quad, (w, h), info = found
            # lines = 찾은 줄 수(틀의 가로 방향, 세로 방향) · inner = 찾은 가장 바깥 줄들의 네 모서리(틀 픽셀, TL TR BR BL)
            lattices[p.name] = {"quad": np.round(quad, 2).tolist(), "size": [int(w), int(h)],
                                "lines": [int(x) for x in info["lines"]], "inner": np.round(info["inner"], 2).tolist(),
                                "regularity": [round(float(x), 3) for x in info["regularity"]]}
    for name, data in (("frames.json", frames), ("lattice-frames.json", lattices)):
        (src / name).write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"원근 틀 {sum(1 for v in frames.values() if v)}/{len(frames)} · 격자 틀 {sum(1 for v in lattices.values() if v)}/{len(lattices)} → {src}")


if __name__ == "__main__":
    main(sys.argv[1:])
