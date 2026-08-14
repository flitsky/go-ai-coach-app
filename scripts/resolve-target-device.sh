#!/usr/bin/env bash
# scripts/resolve-target-device.sh
#
# Resolves and validates the target Android device/emulator for build, install,
# and seed operations.
#
# Usage:
#   scripts/resolve-target-device.sh [--doctor | --get-serial | --export]
#
# Options / Environment:
#   TARGET=<emu|emulator|phone|device|real>
#   ANDROID_SERIAL=<serial>
#   ANDROID_HOME=<path-to-sdk>

set -euo pipefail

MODE="--doctor"
if [[ $# -ge 1 ]]; then
  MODE="$1"
fi

ANDROID_SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"

if [[ ! -x "$ADB" ]]; then
  if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
  else
    if [[ "$MODE" == "--doctor" ]]; then
      echo "❌ adb not found under ANDROID_HOME/platform-tools or PATH." >&2
    fi
    exit 1
  fi
fi

TARGET="${TARGET:-}"
ANDROID_SERIAL="${ANDROID_SERIAL:-}"

# Parse connected devices with state 'device'
# Output lines: serial is_emulator model
RAW_DEVICES=$("$ADB" devices -l | awk 'NR>1 && $2=="device" {
  serial=$1;
  is_emu=(serial ~ /^emulator-/) ? "1" : "0";
  model="unknown";
  for(i=3; i<=NF; i++) {
    if ($i ~ /^model:/) {
      sub(/^model:/, "", $i);
      model=$i;
    }
  }
  print serial " " is_emu " " model;
}')

TOTAL_COUNT=$(echo "$RAW_DEVICES" | grep -c . || true)

# Collect lists
EMULATOR_SERIALS=()
EMULATOR_MODELS=()
PHONE_SERIALS=()
PHONE_MODELS=()
ALL_SERIALS=()
ALL_MODELS=()
ALL_IS_EMU=()

if [[ "$TOTAL_COUNT" -gt 0 ]]; then
  while read -r serial is_emu model; do
    [[ -z "$serial" ]] && continue
    ALL_SERIALS+=("$serial")
    ALL_MODELS+=("$model")
    ALL_IS_EMU+=("$is_emu")
    if [[ "$is_emu" == "1" ]]; then
      EMULATOR_SERIALS+=("$serial")
      EMULATOR_MODELS+=("$model")
    else
      PHONE_SERIALS+=("$serial")
      PHONE_MODELS+=("$model")
    fi
  done <<< "$RAW_DEVICES"
fi

TOTAL_COUNT=${#ALL_SERIALS[@]}
EMU_COUNT=${#EMULATOR_SERIALS[@]}
PHONE_COUNT=${#PHONE_SERIALS[@]}

SELECTED_SERIAL=""
SELECTED_MODEL=""
SELECTED_TYPE=""
WARNING_MSG=""

# Resolution Logic
if [[ -n "$TARGET" ]]; then
  case "$TARGET" in
    emu|emulator)
      if [[ "$EMU_COUNT" -eq 0 ]]; then
        if [[ "$MODE" == "--doctor" ]]; then
          echo "❌ No active emulator connected (TARGET=$TARGET)." >&2
          echo "   Please start an emulator in Android Studio or omit TARGET to use a connected phone." >&2
        fi
        exit 1
      elif [[ "$EMU_COUNT" -eq 1 ]]; then
        SELECTED_SERIAL="${EMULATOR_SERIALS[0]}"
        SELECTED_MODEL="${EMULATOR_MODELS[0]}"
        SELECTED_TYPE="Emulator"
      else
        if [[ "$MODE" == "--doctor" ]]; then
          echo "==========================================================================" >&2
          echo "⚠️  Multiple emulators connected ($EMU_COUNT found):" >&2
          echo "==========================================================================" >&2
          for i in "${!EMULATOR_SERIALS[@]}"; do
            echo "  [Emulator] ${EMULATOR_SERIALS[$i]} (Model: ${EMULATOR_MODELS[$i]})" >&2
          done
          echo "--------------------------------------------------------------------------" >&2
          echo "💡 Please specify serial: make <command> ANDROID_SERIAL=<serial>" >&2
          echo "==========================================================================" >&2
        fi
        exit 1
      fi
      ;;
    phone|device|real)
      if [[ "$PHONE_COUNT" -eq 0 ]]; then
        if [[ "$MODE" == "--doctor" ]]; then
          echo "❌ No physical phone connected via USB (TARGET=$TARGET)." >&2
          echo "   Please connect a phone or run with TARGET=emu to use the emulator." >&2
        fi
        exit 1
      elif [[ "$PHONE_COUNT" -eq 1 ]]; then
        SELECTED_SERIAL="${PHONE_SERIALS[0]}"
        SELECTED_MODEL="${PHONE_MODELS[0]}"
        SELECTED_TYPE="Phone"
      else
        if [[ "$MODE" == "--doctor" ]]; then
          echo "==========================================================================" >&2
          echo "⚠️  Multiple physical phones connected ($PHONE_COUNT found):" >&2
          echo "==========================================================================" >&2
          for i in "${!PHONE_SERIALS[@]}"; do
            echo "  [Phone]    ${PHONE_SERIALS[$i]} (Model: ${PHONE_MODELS[$i]})" >&2
          done
          echo "--------------------------------------------------------------------------" >&2
          echo "💡 Please specify serial: make <command> ANDROID_SERIAL=<serial>" >&2
          echo "==========================================================================" >&2
        fi
        exit 1
      fi
      ;;
    *)
      if [[ "$MODE" == "--doctor" ]]; then
        echo "❌ Unknown TARGET='$TARGET'. Supported values: emu, phone" >&2
      fi
      exit 1
      ;;
  esac

elif [[ -n "$ANDROID_SERIAL" ]]; then
  # Check if the configured serial is actually connected
  FOUND=0
  for i in "${!ALL_SERIALS[@]}"; do
    if [[ "${ALL_SERIALS[$i]}" == "$ANDROID_SERIAL" ]]; then
      SELECTED_SERIAL="${ALL_SERIALS[$i]}"
      SELECTED_MODEL="${ALL_MODELS[$i]}"
      SELECTED_TYPE=$([[ "${ALL_IS_EMU[$i]}" == "1" ]] && echo "Emulator" || echo "Phone")
      FOUND=1
      break
    fi
  done

  if [[ "$FOUND" -eq 0 ]]; then
    if [[ "$TOTAL_COUNT" -eq 1 ]]; then
      # Auto-fallback to the single connected device!
      SELECTED_SERIAL="${ALL_SERIALS[0]}"
      SELECTED_MODEL="${ALL_MODELS[0]}"
      SELECTED_TYPE=$([[ "${ALL_IS_EMU[0]}" == "1" ]] && echo "Emulator" || echo "Phone")
      WARNING_MSG="⚠️  Configured ANDROID_SERIAL ($ANDROID_SERIAL) is disconnected.\n👉 Auto-switching to connected device: $SELECTED_SERIAL ($SELECTED_MODEL)"
    elif [[ "$TOTAL_COUNT" -eq 0 ]]; then
      if [[ "$MODE" == "--doctor" ]]; then
        echo "❌ Configured ANDROID_SERIAL ($ANDROID_SERIAL) is not connected and no other device is available." >&2
        echo "   Please run 'unset ANDROID_SERIAL' or connect a device." >&2
      fi
      exit 1
    else
      if [[ "$MODE" == "--doctor" ]]; then
        echo "==========================================================================" >&2
        echo "⚠️  Configured ANDROID_SERIAL ($ANDROID_SERIAL) is not connected." >&2
        echo "   Multiple other devices/emulators are available ($TOTAL_COUNT found):" >&2
        echo "==========================================================================" >&2
        for i in "${!ALL_SERIALS[@]}"; do
          TYPE_LABEL=$([[ "${ALL_IS_EMU[$i]}" == "1" ]] && echo "[Emulator]" || echo "[Phone]   ")
          echo "  $TYPE_LABEL ${ALL_SERIALS[$i]} (Model: ${ALL_MODELS[$i]})" >&2
        done
        echo "--------------------------------------------------------------------------" >&2
        echo "💡 How to choose a target:" >&2
        echo "  - By preset:     make <command> TARGET=emu      # Target emulator" >&2
        echo "                   make <command> TARGET=phone    # Target physical phone" >&2
        echo "  - By serial:     make <command> ANDROID_SERIAL=<serial>" >&2
        echo "  - Or clear env:  unset ANDROID_SERIAL" >&2
        echo "==========================================================================" >&2
      fi
      exit 1
    fi
  fi

else
  # Neither TARGET nor ANDROID_SERIAL is set
  if [[ "$TOTAL_COUNT" -eq 0 ]]; then
    # No devices connected at all
    if [[ "$MODE" == "--doctor" ]]; then
      echo "⚠️  No connected Android device or emulator detected." >&2
      echo "   (You can still build APKs with 'make dev', but install/launch requires a device)" >&2
    fi
    # Not fatal for pure doctor check, but selected serial is empty
    SELECTED_SERIAL=""
  elif [[ "$TOTAL_COUNT" -eq 1 ]]; then
    SELECTED_SERIAL="${ALL_SERIALS[0]}"
    SELECTED_MODEL="${ALL_MODELS[0]}"
    SELECTED_TYPE=$([[ "${ALL_IS_EMU[0]}" == "1" ]] && echo "Emulator" || echo "Phone")
  else
    # 2 or more devices connected
    if [[ "$MODE" == "--doctor" ]]; then
      echo "==========================================================================" >&2
      echo "⚠️  Multiple Android devices/emulators connected ($TOTAL_COUNT found):" >&2
      echo "==========================================================================" >&2
      for i in "${!ALL_SERIALS[@]}"; do
        TYPE_LABEL=$([[ "${ALL_IS_EMU[$i]}" == "1" ]] && echo "[Emulator]" || echo "[Phone]   ")
        echo "  $TYPE_LABEL ${ALL_SERIALS[$i]} (Model: ${ALL_MODELS[$i]})" >&2
      done
      echo "--------------------------------------------------------------------------" >&2
      echo "💡 How to choose a target:" >&2
      echo "  - By preset:     make <command> TARGET=emu      # Target emulator" >&2
      echo "                   make <command> TARGET=phone    # Target physical phone" >&2
      echo "  - By serial:     make <command> ANDROID_SERIAL=<serial>" >&2
      echo "==========================================================================" >&2
    fi
    exit 1
  fi
fi

# Output based on mode
case "$MODE" in
  --get-serial)
    echo -n "$SELECTED_SERIAL"
    ;;
  --export)
    if [[ -n "$SELECTED_SERIAL" ]]; then
      echo "export ANDROID_SERIAL=\"$SELECTED_SERIAL\""
    fi
    ;;
  --doctor|*)
    if [[ -n "$WARNING_MSG" ]]; then
      echo -e "$WARNING_MSG" >&2
    fi
    if [[ -n "$SELECTED_SERIAL" ]]; then
      echo "Target Device: [$SELECTED_TYPE] $SELECTED_MODEL ($SELECTED_SERIAL)"
    fi
    ;;
esac
