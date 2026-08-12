#!/usr/bin/env bash
set -euo pipefail

# version.properties의 VERSION_NAME은 인자로 새 버전이 주어지면 그 값을 그대로 쓰고, 없으면
# 현재 버전의 패치(fix) 자리만 1 증가시킨다. VERSION_CODE는 독립적으로 증가시키지 않고
# MAJOR*10000 + MINOR*100 + PATCH로 VERSION_NAME에서 결정론적으로 계산한다 — 그래야 두 값이
# 항상 같은 숫자를 나타내고(예: 0.1.8 -> 108), 사람이 따로 맞춰줄 필요가 구조적으로 없어진다.
# make release/play-internal-aab/bundle-aab이 Gradle을 부르기 전에 매번 실행해, Play
# Console의 "버전 코드가 이미 사용되었습니다" 오류(한 번 올린 versionCode는 재사용 불가)를
# 구조적으로 막는다 — 계산된 코드가 현재 코드보다 커야만 통과시킨다.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$REPO_ROOT/version.properties"
NEW_VERSION_NAME="${1:-}"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "version.properties not found: $VERSION_FILE" >&2
  exit 1
fi

CURRENT_CODE="$(grep '^VERSION_CODE=' "$VERSION_FILE" | cut -d= -f2)"
CURRENT_NAME="$(grep '^VERSION_NAME=' "$VERSION_FILE" | cut -d= -f2)"

if [[ -z "$CURRENT_CODE" || -z "$CURRENT_NAME" ]]; then
  echo "version.properties is missing VERSION_CODE or VERSION_NAME." >&2
  exit 1
fi

if [[ -n "$NEW_VERSION_NAME" ]]; then
  if [[ ! "$NEW_VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "VERSION must look like MAJOR.MINOR.PATCH (e.g. 0.2.0), got: $NEW_VERSION_NAME" >&2
    exit 1
  fi
else
  if [[ ! "$CURRENT_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Existing VERSION_NAME ($CURRENT_NAME) is not MAJOR.MINOR.PATCH; pass VERSION=x.y.z explicitly." >&2
    exit 1
  fi
  MAJOR="${BASH_REMATCH[1]}"
  MINOR="${BASH_REMATCH[2]}"
  PATCH="${BASH_REMATCH[3]}"
  NEW_VERSION_NAME="${MAJOR}.${MINOR}.$((PATCH + 1))"
fi

if [[ ! "$NEW_VERSION_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Unreachable: NEW_VERSION_NAME ($NEW_VERSION_NAME) is not MAJOR.MINOR.PATCH." >&2
  exit 1
fi
NEW_MAJOR="${BASH_REMATCH[1]}"
NEW_MINOR="${BASH_REMATCH[2]}"
NEW_PATCH="${BASH_REMATCH[3]}"

if (( NEW_MINOR > 99 || NEW_PATCH > 99 )); then
  echo "MINOR and PATCH must each stay 0-99 for the MAJOR*10000+MINOR*100+PATCH versionCode formula, got: $NEW_VERSION_NAME" >&2
  exit 1
fi

NEW_CODE=$((NEW_MAJOR * 10000 + NEW_MINOR * 100 + NEW_PATCH))

if (( NEW_CODE <= CURRENT_CODE )); then
  echo "Computed versionCode ($NEW_CODE) for $NEW_VERSION_NAME would not exceed the current versionCode ($CURRENT_CODE) — Play Console requires a strictly higher versionCode. Pick a higher VERSION." >&2
  exit 1
fi

cat > "$VERSION_FILE" <<EOF
VERSION_CODE=${NEW_CODE}
VERSION_NAME=${NEW_VERSION_NAME}
EOF

echo "Version bumped: ${CURRENT_NAME} (code ${CURRENT_CODE}) -> ${NEW_VERSION_NAME} (code ${NEW_CODE})"
