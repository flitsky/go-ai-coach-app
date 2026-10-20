#!/usr/bin/env python3
"""스플래시 그림 세 안(2026-10-20) — 사범 꼬북 대 관장 천원의 대국을 문하생 판다 · 문하생 돌뫼 · 수제자 반상이 관전한다.

사용법:
    python3 scripts/make-splash-images.py <출력 폴더>

- 캐릭터는 앱의 그림(`app-android/src/main/res/drawable-nodpi/bot_fast_beginner_{1..5}.webp`, 배경 투명)을 그대로 쓴다 —
  1 판다 · 2 돌뫼 · 3 반상 · 4 꼬북 · 5 천원. 바둑판 · 돌 · 배경은 이 스크립트가 그린다.
- 글자는 넣지 않는다(`ui/splash/AppSplash.kt` — 글자를 넣으면 4개 언어 자리가 하나 더 생긴다).
- 1080x1920(9:16) · 2배로 그려 줄인다. 핵심은 가운데에 두어 화면 비율이 달라 위아래가 잘려도 괜찮게 했다.
- 같은 입력이면 같은 파일이 나온다(난수는 씨앗을 고정했다).

Pillow가 필요하다. 릴리스 게이트에는 넣지 않는다.
"""
import sys, math, random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance, ImageOps

REPO = Path(__file__).resolve().parents[1]
W, H = 1080, 1920
SS = 2  # 슈퍼샘플

def char(i, height, flip=False):
    im = Image.open(REPO / f"app-android/src/main/res/drawable-nodpi/bot_fast_beginner_{i}.webp").convert("RGBA")
    im = im.crop(im.getbbox())
    w = round(im.width * height / im.height)
    im = im.resize((w, height), Image.LANCZOS)
    return ImageOps.mirror(im) if flip else im

def stone_sprite(color, d):
    """위에서 본 돌 한 알(d 픽셀) — 방사 그라데이션 + 하이라이트."""
    big = d * 4
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    px = im.load()
    cx = cy = big / 2; r = big / 2 - 1
    hx, hy = cx - r * 0.35, cy - r * 0.38
    for y in range(big):
        for x in range(big):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy > r * r: continue
            t = math.hypot(x - hx, y - hy) / (r * 1.6)
            t = min(1.0, t)
            if color == "B":
                v = round(110 - 95 * t); c = (v, v, v + 2)
            else:
                v = round(252 - 40 * t); c = (v, v - 2, v - 8)
            px[x, y] = c + (255,)
    return im.resize((d, d), Image.LANCZOS)

# 반상의 한 국면 — 19줄 좌표(열, 행), 대국 중반쯤
POSITION = {
    "B": [(3,3),(15,15),(3,15),(16,3),(2,13),(5,16),(9,9),(13,2),(14,4),(10,15),(16,10),(4,9),(15,13),(6,3),(12,9),(9,4),(7,15),(16,7)],
    "W": [(15,3),(3,16),(16,16),(13,16),(2,5),(4,5),(15,9),(10,3),(16,5),(8,9),(15,11),(9,16),(11,13),(5,13),(13,6),(17,13),(3,10)],
}

def board_texture(size, lines=19, last=None):
    """위에서 본 바둑판 한 장(나무 + 격자 + 화점 + 돌)."""
    s = size
    im = Image.new("RGB", (s, s), (226, 186, 112))
    d = ImageDraw.Draw(im)
    for y in range(s):
        t = y / s
        d.line([(0, y), (s, y)], fill=(round(232 - 16 * t), round(194 - 16 * t), round(122 - 14 * t)))
    rnd = random.Random(3)
    grain = Image.new("L", (s, s), 0); g = ImageDraw.Draw(grain)
    for _ in range(90):
        y0 = rnd.uniform(0, s); amp = rnd.uniform(2, 10); ph = rnd.uniform(0, 6.28); k = rnd.uniform(0.004, 0.01)
        g.line([(x, y0 + amp * math.sin(k * x + ph)) for x in range(0, s + 6, 6)], fill=rnd.randint(8, 22), width=2)
    im = Image.composite(Image.new("RGB", (s, s), (160, 112, 52)), im, grain.filter(ImageFilter.GaussianBlur(1)))
    d = ImageDraw.Draw(im)
    m = s * 0.055; step = (s - 2 * m) / (lines - 1)
    lw = max(2, round(s / 420))
    for k in range(lines):
        p = m + k * step
        d.line([(m, p), (s - m, p)], fill=(70, 48, 22), width=lw)
        d.line([(p, m), (p, s - m)], fill=(70, 48, 22), width=lw)
    for c in (3, 9, 15):
        for r in (3, 9, 15):
            x, y = m + c * step, m + r * step; rr = step * 0.13
            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(70, 48, 22))
    sd = round(step * 0.96)
    sprites = {c: stone_sprite(c, sd) for c in ("B", "W")}
    shadow = Image.new("RGBA", (s, s), (0, 0, 0, 0)); sh = ImageDraw.Draw(shadow)
    for color, pts in POSITION.items():
        for c, r in pts:
            x, y = m + c * step, m + r * step
            sh.ellipse([x - sd / 2 + sd * 0.06, y - sd / 2 + sd * 0.10, x + sd / 2 + sd * 0.06, y + sd / 2 + sd * 0.10], fill=(40, 25, 5, 110))
    im = Image.alpha_composite(im.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(sd * 0.06)))
    for color, pts in POSITION.items():
        for c, r in pts:
            x, y = m + c * step, m + r * step
            im.alpha_composite(sprites[color], (round(x - sd / 2), round(y - sd / 2)))
    if last:
        c, r = last; x, y = m + c * step, m + r * step
        ImageDraw.Draw(im).ellipse([x - sd * 0.2, y - sd * 0.2, x + sd * 0.2, y + sd * 0.2], outline=(220, 60, 50), width=max(2, round(sd * 0.08)))
    return im

def perspective_coeffs(src, dst):
    """dst(화면) 점을 src(텍스처) 점으로 보내는 계수 — Image.transform(PERSPECTIVE)용."""
    import itertools
    A = []; B = []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); B.append(v)
    # 8x8 가우스 소거
    n = 8
    M = [row[:] + [b] for row, b in zip(A, B)]
    for i in range(n):
        piv = max(range(i, n), key=lambda r: abs(M[r][i])); M[i], M[piv] = M[piv], M[i]
        for r in range(n):
            if r != i:
                f = M[r][i] / M[i][i]
                M[r] = [a - f * b for a, b in zip(M[r], M[i])]
    return [M[i][n] / M[i][i] for i in range(n)]

def goban(canvas_size, quad, thickness, tex_size=1400, last=(16, 7)):
    """화면 위 사다리꼴 quad(왼위, 오위, 오아래, 왼아래)에 판 윗면을, 그 아래로 두께(앞면)를 그린 RGBA 층."""
    Wc, Hc = canvas_size
    tex = board_texture(tex_size, last=last)
    s = tex_size
    coeffs = perspective_coeffs([(0, 0), (s, 0), (s, s), (0, s)], quad)
    top = tex.transform((Wc, Hc), Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    mask = Image.new("L", (Wc, Hc), 0); ImageDraw.Draw(mask).polygon(quad, fill=255)
    layer = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0))
    # 앞면(두께) — 아래 두 꼭짓점에서 수직으로
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = quad
    front = [(x3, y3), (x2, y2), (x2, y2 + thickness), (x3, y3 + thickness)]
    fd = ImageDraw.Draw(layer)
    fd.polygon(front, fill=(176, 128, 66, 255))
    for k in range(6):  # 앞면 나뭇결
        yy = y3 + thickness * (k + 0.5) / 6
        fd.line([(x3, yy), (x2, yy)], fill=(158, 112, 54, 255), width=max(1, round(thickness / 40)))
    fd.line([(x3, y3 + 1), (x2, y2 + 1)], fill=(236, 200, 140, 255), width=max(2, round(thickness / 14)))  # 모서리 빛
    layer.paste(top, (0, 0), mask)
    return layer

def drop_shadow(size, box, alpha=90, blur=40):
    sh = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse(box, fill=(40, 25, 10, alpha))
    return sh.filter(ImageFilter.GaussianBlur(blur))

def finish(im, out):
    im.convert("RGB").resize((W, H), Image.LANCZOS).save(out)

def scroll(im, cx, top, w, h, ink=(40, 32, 26)):
    """족자 — 줄 · 위아래 축 · 종이 · 먹 원(엔소). 글자는 없다."""
    d = ImageDraw.Draw(im)
    d.line([(cx, top - 120), (cx - w * 0.32, top)], fill=(120, 90, 50, 255), width=6)
    d.line([(cx, top - 120), (cx + w * 0.32, top)], fill=(120, 90, 50, 255), width=6)
    d.ellipse([cx - 14, top - 134, cx + 14, top - 106], fill=(110, 80, 44, 255))
    sh = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([cx - w / 2 + 14, top + 18, cx + w / 2 + 14, top + h + 18], fill=(80, 60, 30, 60))
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14)))
    d.rectangle([cx - w / 2, top, cx + w / 2, top + h], fill=(196, 168, 122, 255))           # 비단 테두리
    d.rectangle([cx - w / 2 + 34, top + 70, cx + w / 2 - 34, top + h - 70], fill=(248, 244, 234, 255))  # 종이
    for y, hh in ((top - 16, 32), (top + h - 16, 32)):                                           # 축
        d.rounded_rectangle([cx - w / 2 - 30, y, cx + w / 2 + 30, y + hh], radius=14, fill=(92, 62, 34, 255))
    # 엔소 — 붓으로 한 번에 그은 원(굵기가 변하고 끝이 열려 있다)
    r = (w - 140) / 2; ccy = top + h / 2
    for k in range(0, 330):
        a = math.radians(110 + k)
        t = k / 330
        width = 26 * (1.0 - 0.75 * t) + 6
        x = cx + r * math.cos(a); y = ccy + r * math.sin(a) * 1.02
        d.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=ink + (round(235 - 60 * t),))


# ── 1안: 도장 대국(낮) ──

def scene_dojo(out):
    Wc, Hc = W * SS, H * SS
    im = Image.new("RGBA", (Wc, Hc))
    d = ImageDraw.Draw(im)
    floor_y = 1900
    for y in range(Hc):  # 벽(크림) → 바닥(나무)
        if y < floor_y:
            t = y / floor_y; c = (round(247 - 10 * t), round(242 - 16 * t), round(231 - 24 * t))
        else:
            t = (y - floor_y) / (Hc - floor_y); c = (round(200 - 34 * t), round(154 - 32 * t), round(96 - 26 * t))
        d.line([(0, y), (Wc, y)], fill=c + (255,))
    for x in range(180, Wc, 360):  # 장지문 살(아주 옅게)
        d.line([(x, 300), (x, floor_y - 40)], fill=(228, 216, 194, 255), width=10)
    for y in range(440, floor_y - 60, 400):
        d.line([(0, y), (Wc, y)], fill=(228, 216, 194, 255), width=8)
    d.rectangle([0, floor_y - 40, Wc, floor_y], fill=(170, 124, 70, 255))  # 걸레받이
    scroll(im, 1080, 330, 520, 760)
    for y in range(floor_y + 140, Hc, 210):  # 마루 판자
        d.line([(0, y), (Wc, y)], fill=(176, 130, 76, 255), width=6)
    # 뒤 가운데 — 수제자 반상(판 너머에 서서 내려다본다)
    c = char(3, 760); cx, bottom = 1080, 1930
    im.alpha_composite(drop_shadow(im.size, [cx - 240, bottom - 40, cx + 240, bottom + 40], 70, 30))
    im.alpha_composite(c, (round(cx - c.width / 2), bottom - 760))
    # 대국자 — 사범 꼬북(왼쪽) · 관장 천원(오른쪽, 판을 보도록 뒤집음)
    for i, h, cx, bottom, flip in [(4, 1040, 430, 2380, False), (5, 1040, 1730, 2380, True)]:
        c = char(i, h, flip)
        im.alpha_composite(c, (round(cx - c.width / 2), bottom - h))
    # 바둑판
    quad = [(600, 1960), (1560, 1960), (1800, 2620), (360, 2620)]
    im.alpha_composite(drop_shadow(im.size, [300, 2650, 1860, 2930], 130, 50))
    im.alpha_composite(goban(im.size, quad, 160))
    # 앞 양쪽 — 문하생 판다(왼쪽 앞) · 문하생 돌뫼(오른쪽 앞)
    for i, h, cx, bottom, flip in [(1, 860, 330, 3560, True), (2, 860, 1830, 3560, False)]:
        c = char(i, h, flip)
        im.alpha_composite(drop_shadow(im.size, [cx - 280, bottom - 50, cx + 280, bottom + 50], 90, 30))
        im.alpha_composite(c, (round(cx - c.width / 2), bottom - h))
    finish(im, out)


# ── 2안: 둘러선 관전(위에서 본 판) ──

def scene_circle(out):
    Wc, Hc = W * SS, H * SS
    # 가운데 크림 → 가장자리 옅은 청록 원형 그라데이션
    small = Image.new("RGB", (216, 384)); px = small.load()
    for y in range(384):
        for x in range(216):
            t = min(1.0, math.hypot((x - 108) / 108, (y - 200) / 150) / 1.25)
            px[x, y] = (round(250 - 42 * t), round(245 - 14 * t), round(232 - 6 * t))
    im = small.resize((Wc, Hc), Image.BICUBIC).convert("RGBA")
    ring = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0)); rd = ImageDraw.Draw(ring)
    for r, a in ((1180, 60), (1420, 36)):
        rd.ellipse([1080 - r, 1950 - r, 1080 + r, 1950 + r], outline=(255, 255, 255, a), width=18)
    im.alpha_composite(ring)
    # 판 뒤 — 수제자 반상(왼쪽) · 문하생 돌뫼(가운데) · 관장 천원(오른쪽, 대국자)
    for i, h, cx, bottom, flip in [(3, 760, 560, 1420, False), (2, 700, 1080, 1360, False), (5, 980, 1600, 1500, True)]:
        c = char(i, h, flip); im.alpha_composite(c, (round(cx - c.width / 2), bottom - h))
    # 위에서 본 판(살짝 두께)
    x0, y0, s = 330, 1220, 1500
    im.alpha_composite(drop_shadow(im.size, [x0 - 60, y0 + 120, x0 + s + 60, y0 + s + 140], 120, 60))
    d = ImageDraw.Draw(im)
    d.rectangle([x0, y0 + s - 10, x0 + s, y0 + s + 70], fill=(176, 128, 66, 255))
    d.line([(x0, y0 + s + 2), (x0 + s, y0 + s + 2)], fill=(236, 200, 140, 255), width=10)
    im.alpha_composite(board_texture(s, last=(16, 7)), (x0, y0))
    # 판 앞 — 사범 꼬북(왼쪽 아래, 대국자) · 문하생 판다(오른쪽 아래)
    for i, h, cx, bottom, flip in [(4, 1040, 560, 3420, False), (1, 840, 1640, 3460, True)]:
        c = char(i, h, flip)
        im.alpha_composite(drop_shadow(im.size, [cx - 300, bottom - 50, cx + 300, bottom + 50], 80, 30))
        im.alpha_composite(c, (round(cx - c.width / 2), bottom - h))
    finish(im, out)


# ── 3안: 스포트라이트(밤) ──

def dim(c, k):
    rgb = ImageEnhance.Brightness(c.convert("RGB")).enhance(k)
    rgb.putalpha(c.getchannel("A")); return rgb

def scene_spotlight(out):
    Wc, Hc = W * SS, H * SS
    im = Image.new("RGBA", (Wc, Hc)); d = ImageDraw.Draw(im)
    for y in range(Hc):
        t = y / Hc
        d.line([(0, y), (Wc, y)], fill=(round(28 - 12 * t), round(38 - 14 * t), round(52 - 18 * t), 255))
    # 바닥의 빛 웅덩이
    pool = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0))
    ImageDraw.Draw(pool).ellipse([80, 2150, 2080, 3250], fill=(255, 214, 150, 70))
    im.alpha_composite(pool.filter(ImageFilter.GaussianBlur(120)))
    # 관전자 셋 — 뒤 어둠 속(조금만 밝게)
    for i, h, cx, bottom, flip, k in [(1, 700, 640, 1960, False, 0.62), (3, 720, 1080, 1880, False, 0.70), (2, 700, 1520, 1960, True, 0.62)]:
        c = dim(char(i, h, flip), k); im.alpha_composite(c, (round(cx - c.width / 2), bottom - h))
    # 조명 원뿔 — 위 가운데에서 판으로
    cone = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0))
    ImageDraw.Draw(cone).polygon([(980, -50), (1180, -50), (1960, 2900), (200, 2900)], fill=(255, 236, 190, 46))
    im.alpha_composite(cone.filter(ImageFilter.GaussianBlur(70)))
    # 대국자 — 판 양옆, 조명을 받는다
    for i, h, cx, bottom, flip in [(4, 1060, 400, 2560, False), (5, 1060, 1760, 2560, True)]:
        c = char(i, h, flip); im.alpha_composite(c, (round(cx - c.width / 2), bottom - h))
    quad = [(560, 2120), (1600, 2120), (1880, 2860), (280, 2860)]
    im.alpha_composite(drop_shadow(im.size, [220, 2900, 1940, 3200], 150, 50))
    board = goban(im.size, quad, 170)
    im.alpha_composite(board)
    # 빛 알갱이 — 원뿔 안에 드문드문
    rnd = random.Random(11); sp = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0)); sd = ImageDraw.Draw(sp)
    for _ in range(70):
        y = rnd.uniform(150, 2000); half = 100 + (y / 2900) * 760
        x = 1080 + rnd.uniform(-half, half); r = rnd.uniform(3, 9)
        sd.ellipse([x - r, y - r, x + r, y + r], fill=(255, 240, 200, rnd.randint(70, 160)))
    im.alpha_composite(sp.filter(ImageFilter.GaussianBlur(2)))
    finish(im, out)


if __name__ == "__main__":
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    scene_dojo(out / "splash_v1_dojo.png")
    scene_circle(out / "splash_v2_circle.png")
    scene_spotlight(out / "splash_v3_spotlight.png")
