#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_ROOT="${BUILD_ROOT:-/tmp/go-ai-coach-katago-build}"
ANDROID_SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-26}"
KATAGO_VERSION="${KATAGO_VERSION:-1.16.4}"
EIGEN_VERSION="${EIGEN_VERSION:-3.4.0}"

NDK_DIR="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
CMAKE_BIN="$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake"
KATAGO_ARCHIVE="$BUILD_ROOT/katago-v$KATAGO_VERSION.tar.gz"
EIGEN_ARCHIVE="$BUILD_ROOT/eigen-$EIGEN_VERSION.tar.gz"
KATAGO_SRC="$BUILD_ROOT/KataGo-$KATAGO_VERSION/cpp"
EIGEN_SRC="$BUILD_ROOT/eigen-$EIGEN_VERSION"
BUILD_DIR="$BUILD_ROOT/build-android-arm64"
OUTPUT_DIR="$ROOT_DIR/app-android/src/debug/jniLibs/arm64-v8a"
OUTPUT_LIB="$OUTPUT_DIR/libkatago.so"

mkdir -p "$BUILD_ROOT" "$OUTPUT_DIR"

if [[ ! -x "$CMAKE_BIN" ]]; then
  echo "CMake not found: $CMAKE_BIN" >&2
  exit 1
fi

if [[ ! -f "$KATAGO_ARCHIVE" ]]; then
  curl -L --fail "https://github.com/lightvector/KataGo/archive/refs/tags/v$KATAGO_VERSION.tar.gz" -o "$KATAGO_ARCHIVE"
fi

if [[ ! -d "$BUILD_ROOT/KataGo-$KATAGO_VERSION" ]]; then
  tar -xzf "$KATAGO_ARCHIVE" -C "$BUILD_ROOT"
fi

if [[ ! -f "$EIGEN_ARCHIVE" ]]; then
  curl -L --fail "https://gitlab.com/libeigen/eigen/-/archive/$EIGEN_VERSION/eigen-$EIGEN_VERSION.tar.gz" -o "$EIGEN_ARCHIVE"
fi

if [[ ! -d "$EIGEN_SRC" ]]; then
  tar -xzf "$EIGEN_ARCHIVE" -C "$BUILD_ROOT"
fi

rm -rf "$BUILD_DIR"
# ⚠️ **ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON은 지우면 안 된다 — 그게 없으면 Play가 AAB를
# 거부한다**(백로그 #118, 2026-09-06). 이 한 줄이 링커에 `-Wl,-z,max-page-size=16384`를 붙여
# LOAD 세그먼트 정렬을 4KB → 16KB로 올린다(NDK r27 `build/cmake/flags.cmake:35`).
# 2025-11-01부터 Play는 16KB 페이지 기기를 지원하지 않는 번들을 오류로 막는데, 811 업로드에서
# **실제로 그 오류가 떴다** — 번들 안의 `.so` 셋 중 AndroidX 둘은 이미 16KB였고 **우리가 만드는
# 이 바이너리만 4KB**였다.
# ⚠️ **NDK를 r28로 올려서 해결하려 하지 말 것**(r28부터는 이게 기본값이라 그 유혹이 있다).
# 엔진 바이너리는 기력·속도가 실측으로 고정된 산출물이라, 정렬 한 줄을 고치자고 컴파일러
# 메이저 버전을 바꾸면 **검증 범위가 통째로 넓어진다.** 여기서는 링커 플래그만 바뀐다.
"$CMAKE_BIN" -S "$KATAGO_SRC" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS="-DLITTLE_ENDIAN=1234 -DBIG_ENDIAN=4321 -DBYTE_ORDER=1234" \
  -DUSE_BACKEND=EIGEN \
  -DUSE_AVX2=0 \
  -DNO_GIT_REVISION=1 \
  -DCMAKE_MODULE_PATH="$EIGEN_SRC/cmake" \
  -DEIGEN3_INCLUDE_DIR="$EIGEN_SRC" \
  -DEIGEN3_INCLUDE_DIRS="$EIGEN_SRC"

"$CMAKE_BIN" --build "$BUILD_DIR" --target katago -- -j "${JOBS:-8}"

# 여기서 strip하지 않고 디버그 심볼이 그대로 남은 바이너리를 jniLibs에 둔다 — release/
# playInternal 빌드 시 AGP가 패키징 단계에서 자동으로 strip해서 넣고, ndk.debugSymbolLevel
# 설정에 따라 벗겨낸 심볼을 App Bundle에 동봉해 Play Console이 자동으로 받아가게 한다
# (app-android/build.gradle.kts 참고). 여기서 미리 strip해버리면 AGP가 심볼을 만들어낼
# 원본 자체가 없어져 Play Console의 "디버그 기호가 업로드되지 않았습니다" 경고를 해결할
# 방법이 없어진다.
cp "$BUILD_DIR/katago" "$OUTPUT_LIB"

echo "Wrote $OUTPUT_LIB (unstripped — release/playInternal builds strip on packaging)"
