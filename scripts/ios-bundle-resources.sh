#!/bin/bash
# Xcode 「Bundle Android Resources」 단계(백로그 #294 — 이식 계획 S9 · S20). 공용 화면이 읽는 안드로이드 리소스를 앱 묶음의
# `appassets/` 아래로 복사한다 — 원본은 안드로이드 쪽 하나뿐이다(두 벌로 두지 않는다).
#   appassets/drawable/  비트맵(webp · jpg) — `painterFor`가 이름으로 읽는다. 벡터(XML)는 `scripts/ios-vector-drawables.py`가 코드로 옮긴다.
#   appassets/assets/    안드로이드 `assets/` 그대로(기보 · 참고 대국) — `BundledAssets`가 같은 상대 경로로 읽는다.
set -euo pipefail
SRC="${SRCROOT}/../app-android/src/main"
DST="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/appassets"
mkdir -p "$DST/drawable" "$DST/assets"
cp -f "$SRC"/res/drawable-nodpi/*.webp "$SRC"/res/drawable/*.jpg "$DST/drawable/"
rsync -a --delete "$SRC/assets/" "$DST/assets/"
