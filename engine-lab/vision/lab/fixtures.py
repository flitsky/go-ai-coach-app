"""바둑판 사진 정답과 이미지 읽기 — 앱 테스트와 **같은 파일**을 읽는다(백로그 #263).

정답: `app-android/src/test/resources/<묶음>/*.txt`(커밋됨) · 이미지: `test-fixtures/<묶음>/`(저장소 밖).
형식은 앱의 `BoardPhotoFixtures.kt`와 같다 — `image:` · `size:` · `corners: TLx,TLy TRx,TRy BRx,BRy BLx,BLy`(바깥 격자선 교차점) ·
`gate:` · 격자(위에서 아래 n줄, B/W/.) · `same-as: <다른 정답의 키>`(판 크기·배치를 물려받는다).
"""

from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

REPO = Path(__file__).resolve().parents[3]

SETS = {
    "press": ("app-android/src/test/resources/board-photos", "test-fixtures/board-photos"),
    "own": ("app-android/src/test/resources/board-photos-own", "test-fixtures/board-photos-own"),
}


@dataclass
class Fixture:
    set_name: str
    key: str
    image_path: Path
    size: int
    corners: np.ndarray  # (4, 2) float — TL TR BR BL, 원본 픽셀
    truth: list  # n줄 문자열

    def image(self):
        if not self.image_path.is_file():
            return None
        return cv2.imread(str(self.image_path), cv2.IMREAD_COLOR)


def _parse(text: str) -> tuple[dict, list]:
    meta, grid = {}, []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if ": " in line and not set(line) <= set("BW."):
            k, v = line.split(": ", 1)
            meta[k] = v
        else:
            grid.append(line)
    return meta, grid


def load_set(name: str) -> list[Fixture]:
    answer_dir, image_dir = (REPO / p for p in SETS[name])
    if not answer_dir.is_dir():
        return []
    parsed = {f.stem: _parse(f.read_text(encoding="utf-8")) for f in sorted(answer_dir.glob("*.txt"))}
    out = []
    for key, (meta, grid) in parsed.items():
        base = parsed.get(meta["same-as"]) if "same-as" in meta else None
        size = int((base[0] if base else meta)["size"])
        truth = base[1] if base else grid
        pts = [tuple(map(float, p.split(","))) for p in meta["corners"].split(" ")]
        out.append(Fixture(name, key, image_dir / meta["image"], size, np.array(pts, dtype=np.float64), truth))
    return out
