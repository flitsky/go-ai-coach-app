#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
PACKAGE="${PACKAGE:-com.zenit9hub.ai.baduk}"
SEED_DIR="/data/local/tmp/go-ai-coach-katago-seed"
MODEL_PATH="${MODEL_PATH:-/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz}"
# 사람 모델(백로그 #215) — 급수 캐릭터와 기력 측정 대국이 쓴다. 이 맥에 없으면 건너뛴다(그 기기의 급수 캐릭터는 예전 방식으로 둔다).
HUMAN_MODEL_PATH="${HUMAN_MODEL_PATH:-$HOME/worksoc/katago/models/b18c384nbt-humanv0.bin.gz}"
CONFIG_PATH="${CONFIG_PATH:-/Users/ryan9kim/worksoc/katago/config/katago/gtp_learning.cfg}"
ANALYSIS_CONFIG_PATH="${ANALYSIS_CONFIG_PATH:-/Users/ryan9kim/worksoc/katago/config/katago/analysis_learning.cfg}"

if [[ ! -f "$MODEL_PATH" ]]; then
  echo "Model not found: $MODEL_PATH" >&2
  exit 1
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "Config not found: $CONFIG_PATH" >&2
  exit 1
fi

if [[ ! -f "$ANALYSIS_CONFIG_PATH" ]]; then
  echo "Analysis config not found: $ANALYSIS_CONFIG_PATH" >&2
  exit 1
fi

"$ADB" shell mkdir -p "$SEED_DIR"
"$ADB" push "$MODEL_PATH" "$SEED_DIR/model.bin.gz"
"$ADB" push "$CONFIG_PATH" "$SEED_DIR/gtp_learning.cfg"
"$ADB" push "$ANALYSIS_CONFIG_PATH" "$SEED_DIR/analysis_learning.cfg"
"$ADB" shell run-as "$PACKAGE" mkdir -p files/katago/logs files/katago/home
"$ADB" shell run-as "$PACKAGE" cp "$SEED_DIR/model.bin.gz" files/katago/model.bin.gz
"$ADB" shell run-as "$PACKAGE" cp "$SEED_DIR/gtp_learning.cfg" files/katago/gtp_learning.cfg
"$ADB" shell run-as "$PACKAGE" cp "$SEED_DIR/analysis_learning.cfg" files/katago/analysis_learning.cfg
if [[ -f "$HUMAN_MODEL_PATH" ]]; then
  "$ADB" push "$HUMAN_MODEL_PATH" "$SEED_DIR/human.bin.gz"
  "$ADB" shell run-as "$PACKAGE" cp "$SEED_DIR/human.bin.gz" files/katago/human.bin.gz
else
  echo "Human model not found: $HUMAN_MODEL_PATH — skipped (rank characters fall back to the search-bucket method)." >&2
fi
"$ADB" shell rm -rf "$SEED_DIR"

echo "Seeded KataGo model/config into $PACKAGE app files."
