#!/usr/bin/env python3
"""사용자가 폰으로 찍은 바둑판 사진을 인식률 측정용으로 맞춘다(백로그 #263 단계 2).

앱이 사진을 읽는 방식(`BoardPhotoDecoder` — EXIF 회전을 적용하고 긴 변을 2048px로)을 **그대로** 흉내 내어
`test-fixtures/board-photos-own/`에 PNG로 쓴다. JVM 테스트(`javax.imageio`)는 EXIF 회전을 모르기 때문에,
여기서 세워 두지 않으면 세로 사진이 누운 채 들어가 앱과 다른 것을 재게 된다.

이름은 짧은 키(`o01.png` …)로 붙이고 원본 이름·기종·찍은 때는 `sources.json`에 적는다 — 정답 파일(`o01.txt`)이 그 키를 쓴다.
이미 적힌 원본은 같은 키를 다시 쓰고, 새 원본만 다음 번호를 받는다(다시 돌려도 정답이 엉뚱한 사진을 가리키지 않게).

⚠️ **사진은 커밋하지 않는다** — 그 폴더는 `.gitignore`에 있다. 정답 배치(`app-android/src/test/resources/board-photos-own/*.txt`)만 커밋한다.

    python3 scripts/prepare-own-board-photos.py <원본 폴더>          # 예: adb pull로 받은 폴더
"""

import json
import sys
from pathlib import Path

from PIL import Image, ImageOps

MAX_DIMENSION = 2048  # BoardPhotoDecoder.MaxDimension과 같게
REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = REPO_ROOT / "test-fixtures" / "board-photos-own"
SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".heic"}


SOURCES = OUT_DIR / "sources.json"
EXIF_MODEL, EXIF_MAKE, EXIF_TAKEN = 0x0110, 0x010F, 0x0132


def prepare(src: Path, key: str) -> dict:
    with Image.open(src) as img:
        exif = img.getexif()
        upright = ImageOps.exif_transpose(img).convert("RGB")
        upright.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.BILINEAR)
        upright.save(OUT_DIR / f"{key}.png")
    camera = " ".join(str(exif.get(t, "")).strip() for t in (EXIF_MAKE, EXIF_MODEL)).strip()
    return {"key": key, "file": f"{key}.png", "original": src.name, "camera": camera, "taken": str(exif.get(EXIF_TAKEN, ""))}


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
    known = {s["original"]: s for s in json.loads(SOURCES.read_text())} if SOURCES.exists() else {}
    used = {int(s["key"][1:]) for s in known.values()}
    for p in photos:
        key = known[p.name]["key"] if p.name in known else f"o{max(used, default=0) + 1:02d}"
        used.add(int(key[1:]))
        known[p.name] = prepare(p, key)
        with Image.open(OUT_DIR / f"{key}.png") as img:
            print(f"{p.name} -> {key}.png {img.size[0]}x{img.size[1]}")
    SOURCES.write_text(json.dumps(sorted(known.values(), key=lambda s: s["key"]), ensure_ascii=False, indent=1) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
