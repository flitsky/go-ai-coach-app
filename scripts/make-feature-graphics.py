#!/usr/bin/env python3
"""Play 그래픽 이미지(1024x500) 세 안을 그린다 — A 브랜드형 · B 화면형 · C 캐릭터형(2026-10-20, `work/play-store-assets/README.md` 맨 위 절).

사용법:
    python3 scripts/make-feature-graphics.py <글꼴 폴더> <출력 폴더> A C B <대국 형세 캡처.png> <기보 보기 캡처.png>

- 글꼴은 Noto Sans CJK KR(OFL 1.1 — 안드로이드의 한글 기본 글꼴과 같은 계열)의 Black · Bold · Medium OTF다. 저장소에 싣지 않는다:
  https://github.com/notofonts/noto-cjk/tree/main/Sans/OTF/Korean
- B의 두 캡처는 에뮬레이터 원본(1080x2400)이다 — 대국 화면(형세 보기를 켠 판)과 기보 보기(형세를 켠 판 · 그래프 · 변곡점).
- 핵심(이름 · 문구 · 주 그림)은 가운데 924x400 안에 둔다(업계 관행 — Google은 픽셀 값을 공표하지 않는다). 결과는 불투명 RGB PNG.
- 문구 규칙: 앱에 있는 것만 · 가격/무료/최고/다운로드 같은 판촉어 없음 · 「로그인 없이」를 쓰지 않음(2026-09-18 결정).

Pillow가 필요하다. 릴리스 게이트에는 넣지 않는다 — 그래픽을 다시 만들 때 사람이 직접 돌린다.
"""
import sys, math, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
FONT_DIR = sys.argv[1]
OUT = sys.argv[2]
W, H = 1024, 500
CREAM = (245, 240, 230)
INK = (42, 37, 32)
SUB = (107, 100, 89)       # BrandSecondaryColor
TEAL = (14, 140, 114)      # BrandPrimaryColor

def font(weight, size):
    return ImageFont.truetype(f"{FONT_DIR}/NotoSansCJKkr-{weight}.otf", size)

def text_w(d, s, f):
    b = d.textbbox((0, 0), s, font=f); return b[2] - b[0]

def brand(path):
    im = Image.new("RGB", (W, H), CREAM)
    icon = Image.open(f"{REPO}/work/play-store-assets/app_icon/app_icon_512x512_grid.png").convert("RGB").resize((470, 470), Image.LANCZOS)
    im.paste(icon, (40, 15))
    d = ImageDraw.Draw(im)
    x = 520
    f1, f2, f3 = font("Black", 74), font("Medium", 33), font("Bold", 25)
    d.text((x, 118), "포켓 바둑 코치", font=f1, fill=INK)
    d.text((x, 232), "AI와 두고, 돌아보고,", font=f2, fill=SUB)
    d.text((x, 278), "명국을 한 수씩 놓아 보는 바둑", font=f2, fill=SUB)
    # 짧은 낱말 셋 — 알약 모양으로(텍스트 줄이 아니라 표지처럼 읽히게)
    y = 352; px = x
    for word in ("캐릭터 대국", "형세 판단", "기보 보기"):
        tw = text_w(d, word, f3)
        d.rounded_rectangle([px, y, px + tw + 32, y + 46], radius=23, fill=(255, 255, 255), outline=TEAL, width=2)
        d.text((px + 16, y + 6), word, font=f3, fill=TEAL)
        px += tw + 32 + 12
    assert px <= 974 + 12, px
    im.save(path)

def wood_board(size, lines=None, spacing=56, origin=(0, 0), seed=7):
    """나뭇결이 살짝 있는 바둑판 바탕 + 격자."""
    w, h = size
    rnd = random.Random(seed)
    im = Image.new("RGB", size, (226, 186, 112))
    d = ImageDraw.Draw(im)
    for y in range(h):  # 위아래로 아주 약한 그라데이션
        t = y / h
        d.line([(0, y), (w, y)], fill=(round(232 - 14 * t), round(193 - 14 * t), round(120 - 12 * t)))
    grain = Image.new("L", size, 0); g = ImageDraw.Draw(grain)
    for _ in range(140):
        y0 = rnd.uniform(-40, h + 40); amp = rnd.uniform(4, 16); ph = rnd.uniform(0, 6.28); k = rnd.uniform(0.004, 0.012)
        pts = [(x, y0 + amp * math.sin(k * x + ph)) for x in range(0, w + 8, 8)]
        g.line(pts, fill=rnd.randint(10, 28), width=rnd.choice((1, 2)))
    grain = grain.filter(ImageFilter.GaussianBlur(1.2))
    im = Image.composite(Image.new("RGB", size, (150, 104, 48)), im, grain)
    d = ImageDraw.Draw(im)
    ox, oy = origin
    line = (128, 92, 48)
    xs = [ox + i * spacing for i in range(-20, 40) if 0 <= ox + i * spacing <= w]
    ys = [oy + i * spacing for i in range(-20, 40) if 0 <= oy + i * spacing <= h]
    for x in xs: d.line([(x, 0), (x, h)], fill=line, width=2)
    for y in ys: d.line([(0, y), (w, y)], fill=line, width=2)
    return im

def characters(path):
    im = wood_board((W, H), spacing=62, origin=(30, 26))
    # 글자 자리에 크림 판(바둑판 위에 놓인 이름표처럼)
    panel = Image.new("RGBA", (W, H), (0, 0, 0, 0)); pd = ImageDraw.Draw(panel)
    pd.rounded_rectangle([56, 42, 600, 176], radius=22, fill=(245, 240, 230, 238))
    im = Image.alpha_composite(im.convert("RGBA"), panel)
    d = ImageDraw.Draw(im)
    d.text((84, 52), "포켓 바둑 코치", font=font("Black", 56), fill=INK)
    d.text((86, 126), "15급부터 7단까지, 다섯 상대와 한 판", font=font("Medium", 29), fill=SUB)
    # 다섯 캐릭터 — 약한 상대부터 왼쪽으로(문하생 판다 · 문하생 돌뫼 · 수제자 반상 · 사범 꼬북 · 관장 천원)
    size = 236
    xs = [62, 226, 390, 554, 718]
    for i, x in enumerate(xs, start=1):
        c = Image.open(f"{REPO}/app-android/src/main/res/drawable-nodpi/bot_fast_beginner_{i}.webp").convert("RGBA").resize((size, size), Image.LANCZOS)
        # 발밑 그림자
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0)); sd = ImageDraw.Draw(sh)
        sd.ellipse([x + 40, 418, x + size - 40, 442], fill=(60, 35, 10, 90))
        im = Image.alpha_composite(im, sh.filter(ImageFilter.GaussianBlur(6)))
        im.alpha_composite(c, (x, 208))
    im.convert("RGB").save(path)

def card(capture, box, height, radius=22):
    im = Image.open(capture).convert("RGB").crop(box)
    w = round(im.width * height / im.height)
    im = im.resize((w, height), Image.LANCZOS).convert("RGBA")
    m = Image.new("L", im.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, height - 1], radius=radius, fill=255)
    im.putalpha(m)
    return im

def place(base, im, xy, angle):
    rot = im.rotate(angle, resample=Image.BICUBIC, expand=True)
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    sm = Image.new("RGBA", rot.size, (0, 0, 0, 0)); sm.putalpha(rot.getchannel("A").point(lambda a: a * 0.45))
    shadow.alpha_composite(sm, (xy[0] + 6, xy[1] + 10))
    base.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10)))
    base.alpha_composite(rot, xy)

def screens(path, game_capture, records_capture):
    im = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(im)
    for x in range(W):  # 왼쪽 위 밝은 청록 → 오른쪽 아래 짙은 청록
        t = x / W
        d.line([(x, 0), (x, H)], fill=(round(16 - 6 * t), round(150 - 40 * t), round(122 - 32 * t), 255))
    d.text((66, 140), "포켓 바둑 코치", font=font("Black", 56), fill=(255, 255, 255))
    d.text((68, 230), "두면서 형세를 보고,", font=font("Medium", 30), fill=(214, 240, 232))
    d.text((68, 274), "명국은 한 수씩 놓아 보는 바둑", font=font("Medium", 30), fill=(214, 240, 232))
    f = font("Bold", 22); y = 344; px = 68
    for word in ("형세 판단", "추천 수", "기보 보기"):
        tw = text_w(d, word, f)
        d.rounded_rectangle([px, y, px + tw + 28, y + 40], radius=20, outline=(214, 240, 232), width=2)
        d.text((px + 14, y + 5), word, font=f, fill=(255, 255, 255))
        px += tw + 28 + 10
    # 오른쪽 — 실제 화면 두 장(기기 틀 없이): 대국 중 형세 · 기보 보기(그래프 · 형세)
    game = card(game_capture, (0, 420, 1080, 1950), 358)
    rec = card(records_capture, (0, 370, 1080, 1870), 358)
    place(im, game, (500, 62), 4)
    place(im, rec, (676, 66), -4)
    im.convert("RGB").save(path)


if __name__ == "__main__":
    which = sys.argv[3:] or ["A", "C"]
    if "A" in which: brand(f"{OUT}/feature_graphic_1024x500_A_brand.png")
    if "C" in which: characters(f"{OUT}/feature_graphic_1024x500_C_characters.png")
    if "B" in which: screens(f"{OUT}/feature_graphic_1024x500_B_screens.png", sys.argv[sys.argv.index("B") + 1], sys.argv[sys.argv.index("B") + 2])
