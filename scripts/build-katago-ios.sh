#!/usr/bin/env bash
# KataGo를 iOS 앱 안에서 부를 정적 라이브러리로 짓는다(백로그 #293).
#
# 안드로이드(`build-katago-android-spike.sh`)는 같은 KataGo · 같은 Eigen으로 **실행 파일**을 지어 앱이 프로세스로 띄운다.
# iOS 앱은 자식 프로세스를 띄울 수 없다(Apple DTS 답변 — 백로그 #293 카드) — 그래서 여기서는:
#   ① KataGo의 `main()`을 `katago_main()`으로 이름만 바꾸고 ② 실행 파일 대신 정적 라이브러리(`libkatago.a`)로 묶는다.
# 앱은 `app-ios/GoCoach/Engine/KataGoBridge.cpp`가 앱 안 스레드에서 `katago_main(gtp …)` · `katago_main(analysis …)`를 부르고, 두 루프의
# 입출력을 파이프로 돌린다(③) — 명령줄 · 설정 · GTP · JSON 대화는 안드로이드와 같다.
#
# 쓰는 법: scripts/build-katago-ios.sh [iphonesimulator|iphoneos]   (기본 iphonesimulator)
# 결과:   app-ios/build/katago/<SDK>/libkatago.a   (gitignore의 build/ — Xcode의 LIBRARY_SEARCH_PATHS가 이 자리를 본다)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${1:-iphonesimulator}"
BUILD_ROOT="${BUILD_ROOT:-/tmp/go-ai-coach-katago-ios-build}"
# 안드로이드와 같은 판이어야 한다 — 세기 · 기권 값은 같은 엔진을 전제로 잰 것이다(실험실 `engine-lab/`).
KATAGO_VERSION="${KATAGO_VERSION:-1.16.4}"
EIGEN_VERSION="${EIGEN_VERSION:-3.4.0}"
# 앱의 배포 대상과 같게 둔다(`app-ios/GoCoach.xcodeproj` — 지금은 자리표시 값, #293이 정한다).
DEPLOYMENT_TARGET="${DEPLOYMENT_TARGET:-16.0}"

case "$SDK" in
  iphonesimulator|iphoneos) ;;
  *) echo "SDK must be iphonesimulator or iphoneos, was: $SDK" >&2; exit 1 ;;
esac

KATAGO_ARCHIVE="$BUILD_ROOT/katago-v$KATAGO_VERSION.tar.gz"
EIGEN_ARCHIVE="$BUILD_ROOT/eigen-$EIGEN_VERSION.tar.gz"
SRC_ROOT="$BUILD_ROOT/src"
KATAGO_SRC="$SRC_ROOT/KataGo-$KATAGO_VERSION/cpp"
EIGEN_SRC="$SRC_ROOT/eigen-$EIGEN_VERSION"
BUILD_DIR="$BUILD_ROOT/build-$SDK-arm64"
OUTPUT_DIR="$ROOT_DIR/app-ios/build/katago/$SDK"

mkdir -p "$BUILD_ROOT" "$SRC_ROOT" "$OUTPUT_DIR"

if [[ ! -f "$KATAGO_ARCHIVE" ]]; then
  curl -L --fail "https://github.com/lightvector/KataGo/archive/refs/tags/v$KATAGO_VERSION.tar.gz" -o "$KATAGO_ARCHIVE"
fi
if [[ ! -d "$SRC_ROOT/KataGo-$KATAGO_VERSION" ]]; then
  tar -xzf "$KATAGO_ARCHIVE" -C "$SRC_ROOT"
fi
if [[ ! -f "$EIGEN_ARCHIVE" ]]; then
  curl -L --fail "https://gitlab.com/libeigen/eigen/-/archive/$EIGEN_VERSION/eigen-$EIGEN_VERSION.tar.gz" -o "$EIGEN_ARCHIVE"
fi
if [[ ! -d "$EIGEN_SRC" ]]; then
  tar -xzf "$EIGEN_ARCHIVE" -C "$SRC_ROOT"
fi

# ① `main` → `katago_main`. 앱에는 이미 `main`(SwiftUI의 @main)이 있다 — 그대로 두면 링크에서 겹친다.
if grep -q '^int main(int argc, const char\* const\* argv) {$' "$KATAGO_SRC/main.cpp"; then
  sed -i '' 's/^int main(int argc, const char\* const\* argv) {$/int katago_main(int argc, const char* const* argv) {/' "$KATAGO_SRC/main.cpp"
fi
grep -q '^int katago_main(int argc, const char\* const\* argv) {$' "$KATAGO_SRC/main.cpp" || { echo "main.cpp: could not rename main()" >&2; exit 1; }

# ② 실행 파일 대신 정적 라이브러리. 소스 목록 · 정의 · 링크 줄은 그대로다(`target_link_libraries`는 정적 라이브러리에도 쓴다).
if grep -q '^add_executable(katago$' "$KATAGO_SRC/CMakeLists.txt"; then
  sed -i '' 's/^add_executable(katago$/add_library(katago STATIC/' "$KATAGO_SRC/CMakeLists.txt"
fi
grep -q '^add_library(katago STATIC$' "$KATAGO_SRC/CMakeLists.txt" || { echo "CMakeLists.txt: could not switch katago to a static library" >&2; exit 1; }

# ③ GTP 루프(gtp.cpp)와 분석 루프(analysis.cpp)가 각자의 스트림을 쓴다 — 앱 안에서 두 엔진이 **함께** 돈다(사용자 2026-10-26:
#    「KataGo 고쳐 둘 다」 — 안드로이드처럼 대국용 · 분석용). 두 파일의 `cin` · `cout`만 다리(`KataGoBridge.cpp`)의 함수로 바꾼다 —
#    표준 입출력에 쓰는 곳은 이 두 루프뿐이고(로거는 기본이 표준 출력에 쓰지 않는다), 다른 명령(benchmark 등)은 앱이 부르지 않는다.
#    `using namespace std;` 바로 뒤에 넣는다 — 그 위의 #include(표준 헤더)는 바뀌지 않는다. `std::cout`처럼 이름공간을 붙여 쓴 곳은 없다.
patch_streams() {
  local file="$1" in_fn="$2" out_fn="$3"
  if ! grep -q "KataGoStreams::$in_fn" "$file"; then
    perl -0pi -e "s/^using namespace std;\n/using namespace std;\n\/\/ go-ai-coach(iOS, 백로그 #293): 이 루프는 앱 안 스레드에서 자기 스트림을 쓴다 — 구현은 app-ios\/GoCoach\/Engine\/KataGoBridge.cpp.\nnamespace KataGoStreams { std::istream\& $in_fn(); std::ostream\& $out_fn(); }\n#define cin KataGoStreams::$in_fn()\n#define cout KataGoStreams::$out_fn()\n/m" "$file"
  fi
  grep -q "^#define cout KataGoStreams::$out_fn()$" "$file" || { echo "$file: could not redirect cin/cout" >&2; exit 1; }
  if grep -q "std::cin\|std::cout" "$file"; then echo "$file: uses std::cin/std::cout directly — the redirect would miss it" >&2; exit 1; fi
}
patch_streams "$KATAGO_SRC/command/gtp.cpp" gtpIn gtpOut
patch_streams "$KATAGO_SRC/command/analysis.cpp" analysisIn analysisOut

# ⚠️ CMAKE_SYSTEM_PROCESSOR=aarch64: iOS 크로스 빌드에서는 비어 있어 KataGo의 CMake가 인텔 분기를 타고 `-mfpmath=sse`를 붙인다
#    (arm64 컴파일러가 모른다). aarch64면 안드로이드(NDK arm64)와 같은 ARM 분기 — `char`를 부호 있게 — 를 탄다.
# ⚠️ CMAKE_IGNORE_PREFIX_PATH: 맥의 Homebrew(/opt/homebrew · /usr/local)에 있는 libzip 같은 **macOS용** 라이브러리를
#    iOS 빌드가 집어 들면 링크에서 깨진다. libzip은 학습 데이터 쓰기에만 쓰여 없어도 GTP · analysis가 돈다(KataGo의 경고 문구).
rm -rf "$BUILD_DIR"
cmake -S "$KATAGO_SRC" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_SYSROOT="$SDK" \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
  -DCMAKE_IGNORE_PREFIX_PATH="/opt/homebrew;/usr/local" \
  -DCMAKE_BUILD_TYPE=Release \
  -DUSE_BACKEND=EIGEN \
  -DUSE_AVX2=0 \
  -DNO_GIT_REVISION=1 \
  -DCMAKE_MODULE_PATH="$EIGEN_SRC/cmake" \
  -DEIGEN3_INCLUDE_DIR="$EIGEN_SRC" \
  -DEIGEN3_INCLUDE_DIRS="$EIGEN_SRC"

cmake --build "$BUILD_DIR" --target katago -- -j "${JOBS:-8}"

cp "$BUILD_DIR/libkatago.a" "$OUTPUT_DIR/libkatago.a"
echo "Wrote $OUTPUT_DIR/libkatago.a (KataGo $KATAGO_VERSION, Eigen backend, $SDK arm64)"
