#!/usr/bin/env python3
"""에뮬레이터 캡처(1080x2400)를 Play 스토어 스크린샷 규격(1296x2304 · 정확히 9:16 · PNG · RGB)으로 만든다.

사용법:
    adb exec-out screencap -p > /tmp/home.png
    python3 scripts/make-store-screenshots.py --out /tmp/out 01_home=/tmp/home.png 02_setup=/tmp/setup.png

절차(`work/play-store-assets/README.md`와 같다):
  1. 상태 표시줄(위 104px)을 잘라 낸다 — 시계·알림 아이콘이 스토어에 찍히지 않게.
  2. 남은 화면(1080x2296)을 1296x2304 캔버스 가운데에 놓는다. 화면은 하나도 자르지 않는다.
  3. 모자란 가장자리는 **그 이미지 자신의 가장자리 열·행을 늘려** 채운다 — 앱 배경색으로 칠하면 다이얼로그의 어두운 막이 깔린
     컷에서 밝은 띠가 생긴다.

Pillow가 필요하다(`pip install pillow`). 릴리스 게이트에는 넣지 않는다 — 스크린샷을 다시 찍을 때 사람이 직접 돌린다.
"""
import argparse
import os

from PIL import Image

CAPTURE_SIZE = (1080, 2400)
STATUS_BAR_PX = 104
STORE_SIZE = (1296, 2304)


def to_store_format(capture: Image.Image) -> Image.Image:
    if capture.size != CAPTURE_SIZE:
        raise SystemExit(f"캡처 크기가 {CAPTURE_SIZE}가 아니다: {capture.size} — 상태 표시줄 높이와 여백을 다시 정해야 한다")
    body = capture.convert("RGB").crop((0, STATUS_BAR_PX, CAPTURE_SIZE[0], CAPTURE_SIZE[1]))
    body_width, body_height = body.size
    width, height = STORE_SIZE
    left, top = (width - body_width) // 2, (height - body_height) // 2
    canvas = Image.new("RGB", STORE_SIZE)
    canvas.paste(body, (left, top))
    # 좌우 — 화면의 맨 왼쪽·오른쪽 열을 늘린다.
    canvas.paste(body.crop((0, 0, 1, body_height)).resize((left, body_height)), (0, top))
    right = width - left - body_width
    canvas.paste(body.crop((body_width - 1, 0, body_width, body_height)).resize((right, body_height)), (left + body_width, top))
    # 상하 — 좌우를 채운 뒤의 맨 위·아래 행을 늘린다.
    canvas.paste(canvas.crop((0, top, width, top + 1)).resize((width, top)), (0, 0))
    bottom = height - top - body_height
    canvas.paste(canvas.crop((0, top + body_height - 1, width, top + body_height)).resize((width, bottom)), (0, top + body_height))
    return canvas


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True, help="결과 PNG를 둘 폴더")
    parser.add_argument("shots", nargs="+", metavar="이름=캡처.png")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    for shot in args.shots:
        name, source = shot.split("=", 1)
        target = os.path.join(args.out, name + ".png")
        to_store_format(Image.open(source)).save(target, "PNG", optimize=True)
        print(f"{name}: {STORE_SIZE[0]}x{STORE_SIZE[1]} RGB · {os.path.getsize(target):,} bytes")


if __name__ == "__main__":
    main()
