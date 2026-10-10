#!/bin/bash
# Claude Code 클라우드 세션 전용 — 단위 테스트·spotless·lint를 돌릴 수 있게 JDK 17과 Android SDK를 갖춘다.
# 로컬(맥)에서는 아무것도 하지 않는다. 여러 번 돌려도 안전하다(이미 있는 것은 건너뛴다).
# Gradle 빌드 자체는 여기서 돌리지 않는다 — 첫 빌드는 수십 분이라 세션 시작을 막는다.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

REPO="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
JDK17=/usr/lib/jvm/java-17-openjdk-amd64
SDK="$HOME/android-sdk"
PLATFORM="platforms;android-36"     # app-android의 compileSdk와 맞춘다
BUILD_TOOLS="build-tools;35.0.0"

# 1. JDK 17 — 모듈들이 jvmToolchain(17)을 쓴다
if [ ! -x "$JDK17/bin/java" ]; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless >/dev/null
fi

# 2. Android SDK (dl.google.com이 환경의 네트워크 허용 목록에 있어야 한다)
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  tmp=$(mktemp -d)
  zip=$(curl -fsS https://dl.google.com/android/repository/repository2-3.xml \
    | grep -o 'commandlinetools-linux-[0-9]*_latest.zip' | sort -u -V | tail -1)
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$zip"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/35.0.0" ] || [ ! -d "$SDK/platform-tools" ]; then
  export JAVA_HOME="$JDK17"
  yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" "$PLATFORM" "$BUILD_TOOLS" "platform-tools" >/dev/null 2>&1 \
    || { echo "session-start: sdkmanager failed to install $PLATFORM $BUILD_TOOLS" >&2; exit 1; }
fi

# 3. 사용자 수준 Gradle 설정 (프로젝트 gradle.properties보다 우선한다 — 저장소 파일은 건드리지 않는다)
#    · Kotlin 컴파일러 데몬은 이 샌드박스에서 RMI 응답을 기다리다 멈춘다 → in-process
#    · Maven Central이 첫 다운로드 때 429를 준다 → 재시도 횟수·간격을 늘린다
mkdir -p "$HOME/.gradle"
props="$HOME/.gradle/gradle.properties"
touch "$props"
sed -i '/^# >>> go-ai-coach cloud >>>$/,/^# <<< go-ai-coach cloud <<<$/d' "$props"
cat >> "$props" <<PROPS
# >>> go-ai-coach cloud >>>
org.gradle.java.home=$JDK17
kotlin.compiler.execution.strategy=in-process
systemProp.org.gradle.internal.repository.max.tentatives=12
systemProp.org.gradle.internal.repository.initial.backoff=2000
# <<< go-ai-coach cloud <<<
PROPS

# 4. local.properties (gitignore 대상)
if ! grep -qs '^sdk.dir=' "$REPO/local.properties"; then
  echo "sdk.dir=$SDK" >> "$REPO/local.properties"
fi

# 5. google-services.json(gitignore 대상, 비밀)이 없으면 default_web_client_id가 미해결로 컴파일이 깨진다
#    (docs/spec/PITFALLS.md). 가짜 문자열 리소스를 두되 .git/info/exclude로 커밋에서 뺀다.
placeholder_rel="app-android/src/debug/res/values/zz_cloud_placeholder_do_not_commit.xml"
if [ ! -f "$REPO/app-android/google-services.json" ]; then
  mkdir -p "$(dirname "$REPO/$placeholder_rel")"
  cat > "$REPO/$placeholder_rel" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<!-- 클라우드 세션 전용 — .claude/hooks/session-start.sh가 만든다. 커밋 금지(.git/info/exclude). -->
<resources>
    <string name="default_web_client_id" translatable="false">cloud-placeholder</string>
</resources>
XML
  exclude="$REPO/.git/info/exclude"
  if [ -d "$REPO/.git" ] && ! grep -qxF "$placeholder_rel" "$exclude" 2>/dev/null; then
    mkdir -p "$(dirname "$exclude")"
    echo "$placeholder_rel" >> "$exclude"
  fi
fi

# 6. 세션 셸에 넘길 환경 변수
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export JAVA_HOME=$JDK17"
    echo "export ANDROID_HOME=$SDK"
    echo "export ANDROID_SDK_ROOT=$SDK"
  } >> "$CLAUDE_ENV_FILE"
fi
