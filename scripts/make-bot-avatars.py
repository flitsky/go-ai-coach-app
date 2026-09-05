"""캐릭터 원화 → 원형 아바타용 투명 WebP (백로그 #86).

    python3 scripts/make-bot-avatars.py \
      docs/work/artwork/캐릭터1.png app-android/src/main/res/drawable-nodpi/bot_fast_beginner_1.webp \
      ... (5쌍)

원화는 `docs/work/artwork/`에 있는 768x768 **불투명** PNG다. 아바타는 `BotCharacterAvatar`가
**원형으로 잘라** 그리므로 배경이 남아 있으면 어두운 테마에서 **흰 원판**이 된다. 그래서
배경을 알파로 뽑아내는 것이 이 스크립트의 전부다.

배경이 순백이 아니라 **미세하게 텍스처가 있는 오프화이트 패널**이라 단순 임계값으로는 갈리지
않는다. 그리고 캐릭터 안에도 흰 것들이 있다(판다 털, 거북이 든 흰 돌, 부엉이 가슴).
그래서 두 단계로 나눈다.

  ① 네 귀퉁이에서 flood fill — **연결성**으로 자르므로 캐릭터 안의 흰색은 대체로 안전하다.
     ⚠️ 허용치 38은 **실측으로 정한 상한**이다. 42부터 거북(4번)이 든 흰 돌로 새어 들어가
     초승달 모양으로 갉아먹는다. 배경이 덜 지워진다고 이 값을 올리지 말 것 — 아래 ②가 그 몫이다.
  ② 그래도 배경에 남는 얼룩은 **캐릭터와 떨어져 있는 섬**이다. 크기 임계값으로만 거르면
     판다의 붉은 주머니처럼 떨어져 있는 정당한 부속을 지울 수 있어, **연결 성분**으로 세어
     큰 것만 남긴다.
  ③ 가장자리에 남는 반투명 흰 테두리는 마스크를 1px 깎아 없앤다.

⚠️ **결과는 `drawable-nodpi/`에 넣는다.** 이 그림은 `BotCharacterAvatar`가 `Canvas` 크기에
맞춰 **그릴 때** 스케일하므로, 안드로이드가 디코드 시점에 밀도로 또 늘리고 줄일 이유가 없다.
⚠️ 다섯 장이 **같은 캔버스 크기 + 알파**여야 한다 — `BotCharacterAvatarTest`가 파일 헤더를
직접 읽어 못박는다(캐러셀에서 카드 크기가 튀는 것과 흰 원판 둘 다 막는다).
"""
from PIL import Image, ImageDraw, ImageFilter
import sys

FLOOD_TOLERANCE = 38          # 42부터 캐릭터4의 흰 돌이 뚫린다
MIN_COMPONENT_RATIO = 0.002   # 전체 픽셀의 0.2% 미만인 불투명 섬은 배경 얼룩으로 본다
OUTPUT_SIZE = 384             # 아바타 최대 노출이 84dp이므로 xxxhdpi에서도 넉넉하다
MARGIN_RATIO = 0.04


def background_mask(im, tolerance):
    """네 귀퉁이에서 번져 나간 영역 = 배경. 255=캐릭터, 0=배경."""
    w, h = im.size
    work = im.copy()
    for seed in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        ImageDraw.floodfill(work, seed, (255, 0, 255), thresh=tolerance)
    mask = Image.new("L", (w, h), 255)
    mp, wp = mask.load(), work.load()
    for y in range(h):
        for x in range(w):
            if wp[x, y] == (255, 0, 255):
                mp[x, y] = 0
    return mask


def keep_large_components(mask, max_parts=6):
    """불투명 성분 중 큰 것만 남긴다. 작은 섬 = 배경 얼룩.

    ⚠️ 성분마다 전체를 훑으면 얼룩 수백 개에 O(n²)가 된다. 대신 **중심에 가까운 불투명
    픽셀부터 씨앗으로 삼아** 몇 번만 번지고, 그때마다 한 번씩만 훑는다.
    """
    w, h = mask.size
    total = w * h
    scratch = mask.convert("RGB")
    sp = scratch.load()
    kept = Image.new("L", (w, h), 0)
    kp = kept.load()
    cx, cy = w // 2, h // 2
    marker = (0, 255, 0)

    for _ in range(max_parts):
        seed = None
        best = None
        for y in range(0, h, 2):
            for x in range(0, w, 2):
                if sp[x, y] != (255, 255, 255):
                    continue
                d = (x - cx) ** 2 + (y - cy) ** 2
                if best is None or d < best:
                    best, seed = d, (x, y)
        if seed is None:
            break
        ImageDraw.floodfill(scratch, seed, marker, thresh=0)
        size = 0
        for y in range(h):
            for x in range(w):
                if sp[x, y] == marker:
                    size += 1
        if size < total * MIN_COMPONENT_RATIO:
            for y in range(h):                      # 얼룩 — 소진 표시만 하고 버린다
                for x in range(w):
                    if sp[x, y] == marker:
                        sp[x, y] = (0, 0, 1)
            continue
        for y in range(h):
            for x in range(w):
                if sp[x, y] == marker:
                    kp[x, y] = 255
                    sp[x, y] = (0, 0, 1)
    return kept


def build(src, dst):
    im = Image.open(src).convert("RGB")
    mask = keep_large_components(background_mask(im, FLOOD_TOLERANCE))
    mask = mask.filter(ImageFilter.MinFilter(3))          # 흰 테두리 1px 깎기
    mask = mask.filter(ImageFilter.GaussianBlur(0.8))     # 알파 가장자리 부드럽게

    rgba = im.convert("RGBA")
    rgba.putalpha(mask)
    rgba = rgba.crop(rgba.getbbox())

    side = int(max(rgba.size) * (1 + MARGIN_RATIO * 2))
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(rgba, ((side - rgba.width) // 2, (side - rgba.height) // 2), rgba)
    square = square.resize((OUTPUT_SIZE, OUTPUT_SIZE), Image.LANCZOS)
    square.save(dst, "WEBP", lossless=False, quality=92, method=6)
    return square


if __name__ == "__main__":
    for src, dst in zip(sys.argv[1::2], sys.argv[2::2]):
        out = build(src, dst)
        print(f"{src} → {dst}  {out.size[0]}x{out.size[1]}")
