#!/usr/bin/env python3
"""활성 백로그의 「완료」 줄이 250자를 넘는지 센다 (백로그 #194 뒤 신설).

⚠️ **왜 awk 한 줄이 아니라 스크립트인가** — 처음에는 문서에 `awk ... length($0) > 250`을
적어 뒀는데 **awk는 바이트를 센다.** 한글은 1자가 3바이트라 250바이트 ≈ 83자가 되어,
규칙이 뜻한 것보다 세 배 엄했다. 게다가 `^\\| [0-9]+ \\|` 는 「완료」 표뿐 아니라
「다음」의 **우선순위 요약표**까지 잡았다.

⚠️ **규칙에 검사를 붙이는 이유가 이것이다** — 8세대는 같은 규칙을 **검사 없이** 두어
한 줄이 1,500자까지 자랐다. 그런데 **틀린 검사는 검사가 없는 것보다 나쁘다**(함정 24:
초록 ≠ 안전). 그래서 세는 단위(문자)와 범위(완료 절 이후)를 코드로 못박는다.
"""
import io
import re
import sys

LIMIT = 250
PATH = "work/roadmap/260923-_ACTIVE_BACKLOG.md"


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else PATH
    text = io.open(path, encoding="utf-8").read()

    # 「완료」 절 **이후**만 본다 — 앞쪽 표들은 이 규칙의 대상이 아니다.
    marker = re.search(r"^## 완료", text, re.M)
    if marker is None:
        print(f"'## 완료' 절을 못 찾았다: {path}", file=sys.stderr)
        return 2

    over = []
    for offset, line in enumerate(text[marker.start():].splitlines()):
        line = line.rstrip()
        if re.match(r"^\| \d+ \|", line) and len(line) > LIMIT:
            over.append((offset, len(line), line))

    for _, length, line in over:
        print(f"{length}자 (한도 {LIMIT}): {line[:70]}…")

    if over:
        print(f"\n{len(over)}줄이 한도를 넘었다 — 길게 쓸 말은 **커밋 메시지**에 쓴다.")
        return 1
    print(f"완료 줄 전부 {LIMIT}자 이내.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
