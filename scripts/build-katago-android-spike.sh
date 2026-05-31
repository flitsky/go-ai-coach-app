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
"$CMAKE_BIN" -S "$KATAGO_SRC" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS="-DLITTLE_ENDIAN=1234 -DBIG_ENDIAN=4321 -DBYTE_ORDER=1234" \
  -DUSE_BACKEND=EIGEN \
  -DUSE_AVX2=0 \
  -DNO_GIT_REVISION=1 \
  -DCMAKE_MODULE_PATH="$EIGEN_SRC/cmake" \
  -DEIGEN3_INCLUDE_DIR="$EIGEN_SRC" \
  -DEIGEN3_INCLUDE_DIRS="$EIGEN_SRC"

"$CMAKE_BIN" --build "$BUILD_DIR" --target katago -- -j "${JOBS:-8}"

"$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip" \
  -o "$OUTPUT_LIB" \
  "$BUILD_DIR/katago"

echo "Wrote $OUTPUT_LIB"
