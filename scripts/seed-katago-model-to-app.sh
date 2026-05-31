#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
PACKAGE="${PACKAGE:-com.worksoc.goaicoach}"
SEED_DIR="/data/local/tmp/go-ai-coach-katago-seed"
MODEL_PATH="${MODEL_PATH:-/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz}"
CONFIG_PATH="${CONFIG_PATH:-/Users/ryan9kim/worksoc/katago/config/katago/gtp_learning.cfg}"

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "Model not found: $MODEL_PATH" >&2
  exit 1
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "Config not found: $CONFIG_PATH" >&2
  exit 1
fi

"$ADB" shell mkdir -p "$SEED_DIR"
"$ADB" push "$MODEL_PATH" "$SEED_DIR/model.bin.gz"
"$ADB" push "$CONFIG_PATH" "$SEED_DIR/gtp_learning.cfg"
"$ADB" shell run-as "$PACKAGE" mkdir -p files/katago/logs files/katago/home
"$ADB" shell run-as "$PACKAGE" cp "$SEED_DIR/model.bin.gz" files/katago/model.bin.gz
"$ADB" shell run-as "$PACKAGE" cp "$SEED_DIR/gtp_learning.cfg" files/katago/gtp_learning.cfg
"$ADB" shell rm -rf "$SEED_DIR"

echo "Seeded KataGo model/config into $PACKAGE app files."
