"""외곽 사각형 찾기 측정표(백로그 #263 단계 3) — 사진마다: 후보 수 · 1등 후보의 모서리 오차(칸) · 후보 중 가장 가까운 것의 오차.

오차 = 정답 바깥 격자선 교차점과 사각형 꼭짓점의 거리 중 가장 큰 것 ÷ 그 자리의 칸 간격. **0.3칸 안**이면 「딱 붙었다」.

    engine-lab/vision/.venv/bin/python -I engine-lab/vision/measure_outline.py [묶음 ...]   # 기본: press(원본 + 변형)
결과는 `engine-lab/vision/runs/outline-<시각>/`에 report.md와 quads.json(앱 JVM 측정이 읽을 수 있게)으로 남는다.
"""

import json
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lab import fixtures, outline, variants  # noqa: E402

TIGHT = 0.3


def cell_size(corners: np.ndarray, n: int) -> float:
    sides = [np.linalg.norm(corners[i] - corners[(i + 1) % 4]) for i in range(4)]
    return float(np.mean(sides)) / (n - 1)


def corner_error(quad: np.ndarray, corners: np.ndarray, n: int) -> float:
    return float(np.max(np.linalg.norm(quad - corners, axis=1))) / cell_size(corners, n)


def measure(name, img, corners, n):
    started = time.perf_counter()
    cands = outline.candidates(img)
    ms = (time.perf_counter() - started) * 1000
    errors = [corner_error(q, corners, n) for q, _ in cands]
    return {
        "key": name,
        "candidates": len(cands),
        "top_error": errors[0] if errors else None,
        "best_error": min(errors) if errors else None,
        "best_rank": int(np.argmin(errors)) + 1 if errors else None,
        "ms": round(ms),
        "quads": [{"corners": q.round(1).tolist(), "score": round(float(s), 4)} for q, s in cands],
    }


def main(argv):
    sets = argv or ["press"]
    rows = []
    for set_name in sets:
        for f in fixtures.load_set(set_name):
            img = f.image()
            if img is None:
                continue
            rows.append(measure(f"{f.key}", img, f.corners, f.size))
            if set_name == "press":
                for vname, vimg, vcorners in variants.all_variants(img, f.corners):
                    rows.append(measure(f"{f.key} {vname}", vimg, vcorners, f.size))
    out_dir = Path(__file__).resolve().parent / "runs" / time.strftime("outline-%Y%m%d-%H%M%S")
    out_dir.mkdir(parents=True, exist_ok=True)
    fmt = lambda v: "-" if v is None else f"{v:.2f}"  # noqa: E731
    lines = [
        f"## 외곽 사각형 — {len(rows)}장 (딱 붙음 = {TIGHT}칸 안)",
        "",
        "| 사진 | 후보 | 1등 오차(칸) | 가장 가까운 후보 오차(칸) · 순위 | ms(맥) |",
        "| --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        best = "-" if r["best_error"] is None else f"{r['best_error']:.2f} · {r['best_rank']}등"
        lines.append(f"| {r['key']} | {r['candidates']} | {fmt(r['top_error'])} | {best} | {r['ms']} |")
    tight_top = sum(1 for r in rows if r["top_error"] is not None and r["top_error"] <= TIGHT)
    tight_any = sum(1 for r in rows if r["best_error"] is not None and r["best_error"] <= TIGHT)
    lines += ["", f"딱 붙은 1등: {tight_top}/{len(rows)} · 후보 안에 딱 붙은 것이 있음: {tight_any}/{len(rows)}"]
    report = "\n".join(lines)
    (out_dir / "report.md").write_text(report + "\n", encoding="utf-8")
    (out_dir / "quads.json").write_text(json.dumps({r["key"]: r["quads"] for r in rows}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(report)
    print(f"\n→ {out_dir.relative_to(fixtures.REPO)}")


if __name__ == "__main__":
    main(sys.argv[1:])
