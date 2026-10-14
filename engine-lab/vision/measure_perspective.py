"""원근 걷어내기 측정표(백로그 #263 단계 3) — 사진마다: 정답 격자 사각형의 원근 잔차를 펴기 **전·후**로.

원근 잔차 = 마주 보는 변 길이 비의 최댓값 - 1(0 = 평행사변형). 사다리꼴 12% 변형은 약 0.13이다.
펴기 뒤가 전보다 커지면(멀쩡한 사진을 흔들면) 실패다. 결과의 `frames.json`은 **원근을 걷어낸 틀**(원본에서 펼 사각형 TL TR BR BL + 펼 크기)이다 — 앱이 OpenCV로 같은 것을 계산하고,
펴서 격자를 찾는 쪽(순수 Kotlin)은 이 기록으로 JVM에서 잰다.

    engine-lab/vision/.venv/bin/python -I engine-lab/vision/measure_perspective.py [--export] [묶음 ...]

`--export`: 틀을 앱 테스트 리소스(`app-android/src/test/resources/board-photos/perspective-frames.json`, 숫자뿐이라 커밋한다)로 쓰고,
변형 이미지를 `test-fixtures/board-photos/variants/`(저장소 밖)로 쓴다 — 기기 계기 테스트가 같은 이미지로 OpenCV 결과를 맞춰 본다.
"""

import json
import sys
import time
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lab import fixtures, lattice, perspective, variants  # noqa: E402


def measure(name, img, corners):
    started = time.perf_counter()
    perspective.rectifying_homography.last_strength = None
    result = perspective.rectifying_homography(img)
    strength = perspective.rectifying_homography.last_strength
    ms = (time.perf_counter() - started) * 1000
    before = perspective.keystone_residual(corners)
    if result is None:
        return {"key": name, "before": before, "after": before, "applied": False, "ms": round(ms), "frame": None, "strength": strength}
    hmat, size = result
    moved = cv2.perspectiveTransform(corners.reshape(-1, 1, 2), hmat).reshape(-1, 2)
    after = perspective.keystone_residual(moved)
    w, h = size
    quad = cv2.perspectiveTransform(np.float64([[0, 0], [w, 0], [w, h], [0, h]]).reshape(-1, 1, 2), np.linalg.inv(hmat)).reshape(-1, 2)
    frame = {"quad": quad.round(2).tolist(), "size": [int(w), int(h)]}
    return {"key": name, "before": before, "after": after, "applied": True, "ms": round(ms), "frame": frame, "strength": strength}


def lattice_frame(img):
    """격자 틀 하나를 JSON 꼴로(`frames_for_dir.py`의 `lattice-frames.json`과 같은 꼴) — 못 찾으면 None."""
    found = lattice.find_board_frame(img)
    if found is None:
        return None
    quad, (w, h), info = found
    return {"quad": np.round(quad, 2).tolist(), "size": [int(w), int(h)], "lines": [int(x) for x in info["lines"]],
            "inner": np.round(info["inner"], 2).tolist(), "regularity": [round(float(x), 3) for x in info["regularity"]]}


def main(argv):
    export = "--export" in argv
    sets = [a for a in argv if not a.startswith("--")] or ["press"]
    rows = []
    lattices = {}
    variant_dir = fixtures.REPO / "test-fixtures" / "board-photos" / "variants"
    for set_name in sets:
        for f in fixtures.load_set(set_name):
            img = f.image()
            if img is None:
                continue
            rows.append(measure(f.key, img, f.corners))
            lattices[f.key] = lattice_frame(img)
            if set_name == "press":
                for vname, vimg, vcorners in variants.all_variants(img, f.corners):
                    rows.append(measure(f"{f.key} {vname}", vimg, vcorners))
                    lattices[f"{f.key} {vname}"] = lattice_frame(vimg)
                    if export:
                        variant_dir.mkdir(parents=True, exist_ok=True)
                        cv2.imwrite(str(variant_dir / f"{f.key} {vname}.png"), vimg)
    out_dir = Path(__file__).resolve().parent / "runs" / time.strftime("perspective-%Y%m%d-%H%M%S")
    out_dir.mkdir(parents=True, exist_ok=True)
    lines = [f"## 원근 걷어내기 — {len(rows)}장", "", "| 사진 | 폈나 | 원근 세기 | 원근 잔차 전 | 후 | ms(맥) |", "| --- | --- | --- | --- | --- | --- |"]
    for r in rows:
        mark = "○" if r["applied"] else "-"
        st = "-" if r["strength"] is None else f"{r['strength']:.3f}"
        lines.append(f"| {r['key']} | {mark} | {st} | {r['before']:.3f} | {r['after']:.3f} | {r['ms']} |")
    worse = [r["key"] for r in rows if r["after"] > r["before"] + 0.02]
    fixed = [r["key"] for r in rows if r["before"] > 0.05 and r["after"] <= 0.03]
    lines += ["", f"원근을 걷어낸 사진(전 > 0.05 → 후 ≤ 0.03): {len(fixed)} · 오히려 나빠진 사진(+0.02 넘게): {len(worse)} {worse}"]
    report = "\n".join(lines)
    (out_dir / "report.md").write_text(report + "\n", encoding="utf-8")
    frames = json.dumps({r["key"]: r["frame"] for r in rows}, ensure_ascii=False, indent=1)
    (out_dir / "frames.json").write_text(frames + "\n", encoding="utf-8")
    if export:
        (fixtures.REPO / "app-android/src/test/resources/board-photos/perspective-frames.json").write_text(frames + "\n", encoding="utf-8")
        # 격자 틀(`lab/lattice.py`)도 — 앱 JVM 측정표가 기준 이미지·변형을 앱과 같은 후보 경로(`BoardCandidates`)로 잰다
        (fixtures.REPO / "app-android/src/test/resources/board-photos/lattice-frames.json").write_text(
            json.dumps(lattices, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(report)
    print(f"\n→ {out_dir.relative_to(fixtures.REPO)}")


if __name__ == "__main__":
    main(sys.argv[1:])
