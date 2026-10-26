#!/usr/bin/env bash
# 안드로이드 앱의 「지문」을 뜬다 — 코드를 옮기기만 한 커밋(KMP 소스셋 이동 · 빌드 플러그인 교체)이 안드로이드 산출물을
# 바꾸지 않았는지 앞뒤를 견주는 데 쓴다(백로그 #294 — iOS 이식 계획의 「옮기기 커밋 가드」).
#
# 쓰는 법: scripts/android-fingerprint.sh <출력 폴더>
#   <출력>/classes.txt          debug APK의 dex에 든 클래스 이름 전부(정렬) — 소스가 두 번 컴파일되거나 빠지면 여기서 보인다
#   <출력>/apk-entries.txt      debug APK 안의 파일 이름(dex 제외, 정렬) — 에셋 · 리소스 · 엔진 `.so`가 빠지거나 늘면 여기서 보인다
#   <출력>/runtime-<변형>.txt   debug · release의 런타임 클래스패스에서 실제로 고른 판(group:name:version, 정렬)
#   <출력>/licenses-<변형>.txt  오픈소스 목록(aboutlibraries)이 싣는 라이브러리 id(정렬)
# 견주기: diff -r <앞> <뒤>   — 비어야 한다. 다르면 그 줄이 무엇이 바뀌었는지다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:?usage: android-fingerprint.sh <output dir>}"
mkdir -p "$OUT"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
DEXDUMP="$(ls -d "$SDK"/build-tools/*/dexdump | sort -V | tail -1)"

cd "$ROOT_DIR"
./gradlew --console=plain -q :app-android:assembleDebug \
  :app-android:prepareLibraryDefinitionsDebug :app-android:prepareLibraryDefinitionsRelease

APK="app-android/build/outputs/apk/debug/app-android-debug.apk"
WORK="$(mktemp -d)"
unzip -q -o "$APK" 'classes*.dex' -d "$WORK"
for dex in "$WORK"/classes*.dex; do
  "$DEXDUMP" "$dex" | sed -n "s/^  Class descriptor  : 'L\(.*\);'$/\1/p"
done | sort > "$OUT/classes.txt"
rm -rf "$WORK"
unzip -Z1 "$APK" | grep -v -E '^classes[0-9]*\.dex$' | sort > "$OUT/apk-entries.txt"

for variant in debug release; do
  ./gradlew --console=plain -q :app-android:dependencies --configuration "${variant}RuntimeClasspath" \
    | grep -E '^[|+\\ -]+' \
    | sed -E 's/^[|+\\ -]+//; s/ \([*cn]\)$//; s/ FAILED$//' \
    | grep -E '^[^ :]+:[^ :]+' \
    | awk '{
        split($1, parts, ":"); version = parts[3];
        for (i = 1; i <= NF; i++) if ($i == "->") version = $(i + 1);
        if ($1 ~ /^project /) next;
        print parts[1] ":" parts[2] ":" version
      }' \
    | sort -u > "$OUT/runtime-$variant.txt"
  python3 - "$ROOT_DIR/app-android/build/generated/aboutLibraries/$variant/res/raw/aboutlibraries.json" > "$OUT/licenses-$variant.txt" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
for lib in sorted(data.get("libraries", []), key=lambda l: l["uniqueId"]):
    print(lib["uniqueId"], lib.get("artifactVersion", ""))
PY
done

wc -l "$OUT"/*.txt
