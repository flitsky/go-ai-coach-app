#!/usr/bin/env python3
"""사용자가 폰으로 찍은 바둑판 사진을 인식률 측정용으로 맞춘다(백로그 #263 단계 2).

앱이 사진을 읽는 방식(`BoardPhotoDecoder` — EXIF 회전을 적용하고 긴 변을 2048px로)을 **그대로** 흉내 내어
`test-fixtures/board-photos-own/`에 PNG로 쓴다. JVM 테스트(`javax.imageio`)는 EXIF 회전을 모르기 때문에,
여기서 세워 두지 않으면 세로 사진이 누운 채 들어가 앱과 다른 것을 재게 된다.

⚠️ **사진은 커밋하지 않는다** — 그 폴더는 `.gitignore`에 있다. 정답 배치(`app-android/src/test/resources/board-photos-own/*.txt`)만 커밋한다.

    python3 scripts/prepare-own-board-photos.py <원본 폴더>          # 예: adb pull로 받은 폴더
"""

import sys
from pathlib import Path

from PIL import Image, ImageOps

MAX_DIMENSION = 2048  # BoardPhotoDecoder.MaxDimension과 같게
REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = REPO_ROOT / "test-fixtures" / "board-photos-own"
SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".heic"}


def prepare(src: Path) -> Path:
    with Image.open(src) as img:
        upright = ImageOps.exif_transpose(img).convert("RGB")
        upright.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.BILINEAR)
        out = OUT_DIR / (src.stem + ".png")
        upright.save(out)
        return out


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    src_dir = Path(sys.argv[1])
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    photos = sorted(p for p in src_dir.iterdir() if p.suffix.lower() in SUFFIXES)
    if not photos:
        print(f"사진이 없다: {src_dir}")
        return 1
    for p in photos:
        out = prepare(p)
        with Image.open(out) as img:
            print(f"{p.name} -> {out.relative_to(REPO_ROOT)} {img.size[0]}x{img.size[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
