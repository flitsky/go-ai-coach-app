#!/usr/bin/env bash
set -euo pipefail

# 바둑판 사진 인식 기준 이미지를 받는다(백로그 #210 — 실험실 「바둑판 사진 분석」 안정화).
#
# ⚠️ **이미지는 커밋하지 않는다.** 이 저장소는 공개(GitHub public)이고, 아래는 언론사·위키의
# 사진이다 — 커밋하면 남의 저작물을 재배포하게 된다. 그래서 받는 곳(`test-fixtures/board-photos/`)은
# `.gitignore`에 있고, 저장소에는 **이 스크립트(출처 목록)만** 남긴다. 다른 기계에서는 이걸 다시 돌리면 된다.
# 이미지에서 우리가 손으로 뽑은 **정답 배치(어느 교점에 흑/백)** 는 사실 정보라 커밋해도 된다.
#
# 사용자 제공(2026-10-02). 파일명 = 번호_출처_내용.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/test-fixtures/board-photos"
mkdir -p "$OUT_DIR"

UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

fetch() {
  local name="$1" url="$2"
  if [[ -s "$OUT_DIR/$name" ]]; then
    echo "있음: $name"
    return
  fi
  curl -fsSL --retry 2 -A "$UA" -o "$OUT_DIR/$name" "$url"
  echo "받음: $name ($(wc -c < "$OUT_DIR/$name" | tr -d ' ') bytes)"
}

fetch 01_namuwiki.webp "https://i.namu.wiki/i/WpfPhO7vlwUvkjDkNJIE1f-EQZbSMequWe-hyqss8F0wJ_tzMRfLu1TtV0qiChTOqMzg1loE7WqFaIfJK1IHDPnDHRuK2eBJccupegPnDBepWkOjFZqC41UIxWMKBFdqb8Vpd6cx1on9WXpOZQ1DRA.webp"
fetch 02_yna_20240912.jpg "https://img7.yna.co.kr/etc/inner/KR/2024/09/12/AKR20240912053600007_01_i_P4.jpg"
fetch 03_yna_20170109.jpg "https://img9.yna.co.kr/etc/inner/KR/2017/01/09/AKR20170109070900007_01_i_P4.jpg"
fetch 04_kado_200212_kid.jpg "https://cdn.kado.net/news/photo/200212/kd_kid_baduk.jpg"
fetch 05_kado_20050627.jpg "https://cdn.kado.net/news/photo/200506/baduk_20050627.jpg"
