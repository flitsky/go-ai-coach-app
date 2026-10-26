#!/bin/bash
# Xcode 「Bundle Android Resources」 단계(백로그 #294 — 이식 계획 S9 · S20). 공용 화면이 읽는 안드로이드 리소스를 앱 묶음의
# `appassets/` 아래로 복사한다 — 원본은 안드로이드 쪽 하나뿐이다(두 벌로 두지 않는다).
#   appassets/drawable/  비트맵(webp · jpg) — `painterFor`가 이름으로 읽는다. 벡터(XML)는 `scripts/ios-vector-drawables.py`가 코드로 옮긴다.
#   appassets/assets/    안드로이드 `assets/` 그대로(기보 · 참고 대국) — `BundledAssets`가 같은 상대 경로로 읽는다.
#   appassets/audio/     배경음악(#244) — 안드로이드 `res/raw/bgm_*.ogg`를 AAC(m4a)로 바꿔 싣는다(iOS의 AVAudioPlayer는 Ogg Vorbis를 못 연다).
#                        바꾼 파일은 DerivedData에 두고 원본이 더 새로울 때만 다시 바꾼다. ffmpeg가 없으면 음악 없이 묶는다(경고 — 앱은 조용할 뿐이다).
set -euo pipefail
SRC="${SRCROOT}/../app-android/src/main"
DST="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/appassets"
mkdir -p "$DST/drawable" "$DST/assets"
cp -f "$SRC"/res/drawable-nodpi/*.webp "$SRC"/res/drawable/*.jpg "$DST/drawable/"
rsync -a --delete "$SRC/assets/" "$DST/assets/"

mkdir -p "$DST/audio"
FFMPEG="$(command -v ffmpeg || true)"
[ -z "$FFMPEG" ] && [ -x /opt/homebrew/bin/ffmpeg ] && FFMPEG=/opt/homebrew/bin/ffmpeg  # Xcode 단계의 PATH에는 Homebrew가 없다
if [ -n "$FFMPEG" ]; then
  CACHE="${DERIVED_FILE_DIR}/bgm"
  mkdir -p "$CACHE"
  for ogg in "$SRC"/res/raw/bgm_*.ogg; do
    out="$CACHE/$(basename "$ogg" .ogg).m4a"
    if [ ! -f "$out" ] || [ "$ogg" -nt "$out" ]; then
      "$FFMPEG" -nostdin -loglevel error -y -i "$ogg" -vn -c:a aac -b:a 96k "$out.part.m4a"
      mv -f "$out.part.m4a" "$out"
    fi
    cp -f "$out" "$DST/audio/"
  done
else
  echo "warning: ffmpeg not found — background music is not bundled (brew install ffmpeg)"
fi

# 빌드 시각 — 안드로이드 `BuildConfig.BUILD_TIME`과 같은 형식(`yyyy-MM-dd HH:mm`). 대국 화면 왼쪽 위의 `v261027.0430`이 이것이다(`appBuildInfo`).
PLIST="${TARGET_BUILD_DIR}/${INFOPLIST_PATH}"
/usr/libexec/PlistBuddy -c "Delete :GoCoachBuildTime" "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :GoCoachBuildTime string $(date '+%Y-%m-%d %H:%M')" "$PLIST"
