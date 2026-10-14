"""사진마다 **앱이 인식한 결과를 그림 한 장으로** 사진 옆에 쓴다(백로그 #263) — `<사진 이름>_인식결과.png`.

사용자가 Finder·미리보기에서 원본과 나란히 놓고 비교한다. 한 장에: 원본 위에 앱이 찾은 격자(네 모서리에 좌표 글자 — 방향 맞추기용) ·
앱이 읽은 판(가로 A~T·세로 1~19 좌표, 주황 네모 = 확인 필요) · 판 크기·흑백 개수·핀 화면을 건너뛰는지.
재료는 `gallery.html`과 같다 — `app-android/build/reports/board-photos/gallery-<폴더>.json`(`BoardPhotoGalleryTest`).
그린 판은 픽셀을 다시 읽어 JSON 격자와 한 점도 다르지 않은지 스스로 확인한다(다르면 멈춘다).

    engine-lab/vision/.venv/bin/python -I engine-lab/vision/export_results.py test-fixtures/board-photos-web
"""

import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lab.fixtures import REPO, RESULT_SUFFIX, is_photo  # noqa: E402

FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"  # 맥 기본 한글 글꼴 — 0 보통 · 6 굵게
PANEL = 720  # 왼쪽 사진·오른쪽 판의 한 변
GAP = 24
MARGIN = 24
HEADER = 112
FOOTER = 64
COLUMNS = "ABCDEFGHJKLMNOPQRST"  # 바둑 좌표는 I를 건너뛴다

LABEL_ROOM = 40  # 왼쪽 판: 사진·격자 둘레에 모서리 글자가 들어갈 자리
COORD_BAND = 30  # 오른쪽 판: 가장자리 돌 바깥에 좌표 글자가 들어갈 띠
STONE = 0.46  # 돌 반지름(칸)
TINY_GRID = 60  # 찾은 격자의 짧은 변이 이보다 작으면(px) 모서리 글자 대신 동그라미로 짚는다(w09: 38px 격자가 주황 얼룩이었다)

INK = (34, 34, 34)
MUTED = (110, 110, 110)
PAPER = (250, 248, 244)
OUTSIDE = (226, 226, 226)  # 왼쪽 판에서 사진 바깥
WOOD = (222, 175, 95)
SURE = (46, 160, 67)
UNSURE = (239, 120, 0)


def font(size, bold=False):
    return ImageFont.truetype(FONT, size, index=6 if bold else 0)


def label(n, col, row):
    """판 위 교점(위에서 row번째 줄, 왼쪽에서 col번째 줄)의 바둑 좌표 — 세로 번호는 아래가 1."""
    return f"{COLUMNS[col]}{n - row}"


def photo_panel(path, r):
    """원본 위에 앱이 찾은 격자와 네 모서리 좌표 글자.

    격자가 사진 밖으로 나가면 **사진 둘레를 회색으로 넓혀** 격자 전체와 네 모서리 점을 다 그린다 — 사진 끝에 글자만 붙여 두면
    사진 아래 끝이 1줄처럼 보였다(QA 2026-10-14: f05·f16·f19 등). 넓히는 것은 사진 크기만큼까지다(실제로는 최대 0.67배 — f16).
    """
    bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
    img = Image.fromarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
    w, h = img.size
    located = bool(r.get("located"))
    corners = np.float64(r["corners"]) if located else None
    x0, y0, x1, y1 = 0.0, 0.0, float(w), float(h)
    if located:
        x0, x1 = max(min(x0, corners[:, 0].min()), -w), min(max(x1, corners[:, 0].max()), 2.0 * w)
        y0, y1 = max(min(y0, corners[:, 1].min()), -h), min(max(y1, corners[:, 1].max()), 2.0 * h)
    inner = PANEL - 2 * LABEL_ROOM
    s = min(inner / (x1 - x0), inner / (y1 - y0))
    ox = (PANEL - (x1 - x0) * s) / 2 - x0 * s
    oy = (PANEL - (y1 - y0) * s) / 2 - y0 * s

    panel = Image.new("RGB", (PANEL, PANEL), OUTSIDE)
    photo = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    panel.paste(photo, (round(ox), round(oy)))
    draw = ImageDraw.Draw(panel, "RGBA")
    draw.rectangle([round(ox) - 1, round(oy) - 1, round(ox) + photo.width, round(oy) + photo.height], outline=(120, 120, 120, 255), width=1)
    if not located:
        return panel

    n = r["boardSize"]
    quad = corners * s + np.float64([ox, oy])
    hm = cv2.getPerspectiveTransform(np.float32([[0, 0], [n - 1, 0], [n - 1, n - 1], [0, n - 1]]), np.float32(quad))

    def at(col, row):
        p = cv2.perspectiveTransform(np.float64([[[col, row]]]), hm)[0, 0]
        return float(p[0]), float(p[1])

    color = SURE if r.get("confident") else UNSURE
    # 격자는 두 배 크기로 그려 줄인다 — 2px 선을 그대로 그리면 거의 가로인 선이 끊겨 회색으로 보였고(f05), 3px로 키우면 돌을 가렸다(w28).
    # 어두운 테두리를 먼저 — 노란 도면·나무판 위에서도 격자가 보이게(w34).
    lines = Image.new("RGBA", (PANEL * 2, PANEL * 2), (0, 0, 0, 0))
    ldraw = ImageDraw.Draw(lines)
    for halo in (True, False):
        for k in range(n):
            for a, b in ((at(k, 0), at(k, n - 1)), (at(0, k), at(n - 1, k))):
                ldraw.line([(a[0] * 2, a[1] * 2), (b[0] * 2, b[1] * 2)], fill=(0, 0, 0, 80) if halo else color + (240,), width=8 if halo else 4)
    lines = lines.resize((PANEL, PANEL), Image.LANCZOS)
    panel.paste(lines, (0, 0), lines)
    draw = ImageDraw.Draw(panel, "RGBA")

    bw, bh = np.ptp(quad[:, 0]), np.ptp(quad[:, 1])
    cx, cy = quad[:, 0].mean(), quad[:, 1].mean()
    if min(bw, bh) < TINY_GRID:
        # 격자가 점처럼 작으면 모서리 글자 넷이 한데 뭉친다(f15·w09·w32) — 동그라미 하나로 자리만 짚는다.
        rad = max(40.0, max(bw, bh) / 2 + 18)
        draw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=color + (255,), width=3)
        f = font(17, bold=True)
        text = "앱이 찾은 판(아주 작음)"
        tw, th = draw.textbbox((0, 0), text, font=f)[2:]
        tx = min(max(4, cx - tw / 2), PANEL - tw - 4)
        ty = cy + rad + 6 if cy + rad + th + 12 < PANEL else cy - rad - th - 12
        draw.rounded_rectangle([tx - 5, ty - 3, tx + tw + 5, ty + th + 5], radius=5, fill=(255, 255, 255, 235))
        draw.text((tx, ty), text, font=f, fill=color + (255,))
        return panel

    f = font(17, bold=True)
    for col, row in ((0, 0), (n - 1, 0), (n - 1, n - 1), (0, n - 1)):
        x, y = at(col, row)
        draw.ellipse([x - 6, y - 6, x + 6, y + 6], fill=color + (255,), outline=(255, 255, 255, 255), width=2)
        text = label(n, col, row)
        tw, th = draw.textbbox((0, 0), text, font=f)[2:]
        # 글자는 격자 중심에서 그 모서리로 가는 방향의 바깥에 — 기울어진 격자에서도 점을 가리지 않는다.
        dx, dy = x - cx, y - cy
        norm = max(1e-6, (dx * dx + dy * dy) ** 0.5)
        reach = ((tw / 2) ** 2 + (th / 2) ** 2) ** 0.5 + 10
        lx = min(max(6, x + dx / norm * reach - tw / 2), PANEL - tw - 6)
        ly = min(max(4, y + dy / norm * reach - th / 2), PANEL - th - 8)
        draw.rounded_rectangle([lx - 5, ly - 2, lx + tw + 5, ly + th + 5], radius=5, fill=(255, 255, 255, 235))
        draw.text((lx, ly), text, font=f, fill=color + (255,))
    return panel


def board_geometry(n):
    """오른쪽 판의 여백·칸 — 가장자리 돌 바깥에 좌표 글자 띠([COORD_BAND])가 남도록 여백을 칸 크기에서 거꾸로 푼다.
    여백을 고정 44px로 두었더니 9줄 판(돌이 크다)에서 가장자리 돌이 좌표 글자를 덮었다(QA: f27·w47 등)."""
    pad = (COORD_BAND + STONE * PANEL / (n - 1)) / (1 + 2 * STONE / (n - 1))
    step = (PANEL - 2 * pad) / (n - 1)
    return pad, step


def board_panel(r):
    """앱이 읽은 판 — 좌표 글자와 확인 필요(주황 네모)."""
    n = r["boardSize"]
    pad, step = board_geometry(n)
    img = Image.new("RGB", (PANEL, PANEL), WOOD)
    draw = ImageDraw.Draw(img)
    for k in range(n):
        p = pad + k * step
        draw.line([(pad, p), (PANEL - pad, p)], fill=(40, 40, 40), width=1)
        draw.line([(p, pad), (p, PANEL - pad)], fill=(40, 40, 40), width=1)
    stars = {19: [3, 9, 15], 13: [3, 6, 9], 9: [2, 4, 6]}.get(n, [])
    for a in stars:
        for b in stars:
            x, y = pad + a * step, pad + b * step
            draw.ellipse([x - 3.5, y - 3.5, x + 3.5, y + 3.5], fill=(40, 40, 40))
    f = font(15, bold=True)
    edge = COORD_BAND / 2  # 좌표 글자는 판 가장자리와 가장자리 돌 사이 띠의 가운데
    for k in range(n):
        c = pad + k * step
        for text, (x, y) in ((COLUMNS[k], (c, edge)), (COLUMNS[k], (c, PANEL - edge)),
                             (str(n - k), (edge, c)), (str(n - k), (PANEL - edge, c))):
            draw.text((x, y), text, font=f, fill=(70, 50, 20), anchor="mm")
    rad = step * STONE
    # 확인 필요 네모는 **돌 밑에** 돌 크기로 — 돌 위에 칸 크기로 그렸더니 이웃 네모끼리 붙어 주황 그물이 되고 백돌을 깎았다(QA: w37·w50·w51).
    # 돌이 있으면 네 귀만 비죽 나오고, 빈 점이면 네모가 다 보인다.
    for row, col in r.get("uncertain", []):
        x, y = pad + col * step, pad + row * step
        draw.rectangle([x - rad, y - rad, x + rad, y + rad], outline=UNSURE, width=3)
    for row, line in enumerate(r["grid"]):
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            x, y = pad + col * step, pad + row * step
            if ch == "B":
                draw.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(22, 22, 22))
            else:
                draw.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(248, 248, 248), outline=(90, 90, 90), width=1)
    return img


def read_back(board, n):
    """그린 판을 픽셀로 다시 읽는다 — 교점에서 대각선으로 0.25칸(선·화점·주황 네모를 피한 돌 안쪽)."""
    pad, step = board_geometry(n)
    px = np.asarray(board)
    rows = []
    for row in range(n):
        line = ""
        for col in range(n):
            x, y = round(pad + (col + 0.25) * step), round(pad + (row + 0.25) * step)
            v = px[y, x].astype(int)
            line += "B" if v.sum() < 150 else "W" if v.min() > 200 else "."
        rows.append(line)
    return rows


def compose(path, r):
    canvas = Image.new("RGB", (MARGIN * 2 + PANEL * 2 + GAP, HEADER + PANEL + FOOTER), PAPER)
    draw = ImageDraw.Draw(canvas)
    draw.text((MARGIN, 16), f"{r['file']}  →  인식 결과", font=font(30, bold=True), fill=INK)
    if r.get("located"):
        sure = r.get("confident")
        verdict = "확실 → 핀 화면 건너뜀" if sure else "불확실 → 핀 화면에서 모서리 확인"
        parts = [f"{r['boardSize']}줄", f"흑 {r['black']}", f"백 {r['white']}", verdict]
        if r.get("uncertain"):
            parts.append(f"확인 필요 {len(r['uncertain'])}곳")
        if r.get("perspective"):
            parts.append("원근 폄")
        if r.get("reader") == "net":
            parts.append("신경망 판독")
        draw.text((MARGIN, 60), "  ·  ".join(parts), font=font(22, bold=True), fill=SURE if sure else UNSURE)
    else:
        draw.text((MARGIN, 60), "판을 못 찾음 → 핀을 직접 놓는 화면으로", font=font(22, bold=True), fill=UNSURE)

    canvas.paste(photo_panel(path, r), (MARGIN, HEADER))
    if r.get("located"):
        board = board_panel(r)
        back = read_back(board, r["boardSize"])
        if back != r["grid"]:
            bad = [(i, a, b) for i, (a, b) in enumerate(zip(back, r["grid"])) if a != b]
            raise SystemExit(f"{r['file']}: 그린 판이 인식 결과와 다르다 — 줄 {bad[:3]}")
        canvas.paste(board, (MARGIN + PANEL + GAP, HEADER))
    else:
        draw.text((MARGIN + PANEL + GAP + PANEL / 2, HEADER + PANEL / 2), "읽은 판 없음", font=font(26), fill=MUTED, anchor="mm")

    legend = font(16)
    draw.text((MARGIN, HEADER + PANEL + 10),
              "왼쪽: 원본 위에 앱이 찾은 격자(초록 = 확실, 주황 = 불확실) · 회색 = 사진 바깥(격자가 사진 밖까지 이어진 자리) · "
              "모서리 글자는 오른쪽 판의 같은 좌표", font=legend, fill=MUTED)
    draw.text((MARGIN, HEADER + PANEL + 34),
              "오른쪽: 앱이 읽은 판 · 주황 네모 = 확인 필요(규칙 판독은 모서리를 흔들면 답이 바뀌는 교점, 신경망 판독은 확률이 낮은 교점 — 돌이 있으면 네 귀만 보인다)",
              font=legend, fill=MUTED)
    return canvas


def main(argv):
    folder = (REPO / argv[0]) if not Path(argv[0]).is_absolute() else Path(argv[0])
    results = json.loads((REPO / "app-android/build/reports/board-photos" / f"gallery-{folder.name}.json").read_text(encoding="utf-8"))
    photos = {p.name for p in folder.iterdir() if is_photo(p)}
    stale = sorted(photos - {r["file"] for r in results})
    if stale:
        raise SystemExit(f"인식 결과가 없는 사진 {len(stale)}장({', '.join(stale[:5])}…) — BoardPhotoGalleryTest를 먼저 돌린다")
    written = 0
    for r in results:
        path = folder / r["file"]
        if not path.is_file():
            continue
        compose(path, r).save(folder / f"{path.stem}{RESULT_SUFFIX}.png", optimize=True)
        written += 1
    print(f"{written}장 → {folder}/<사진 이름>{RESULT_SUFFIX}.png")


if __name__ == "__main__":
    main(sys.argv[1:])
