#!/usr/bin/env bash
set -euo pipefail

# version.properties의 VERSION_CODE를 항상 1 증가시키고, VERSION_NAME은 인자로 새 버전이
# 주어지면 그 값을 그대로 쓰고, 없으면 현재 버전의 패치(fix) 자리만 1 증가시킨다.
# make release/play-internal-aab/bundle-aab이 Gradle을 부르기 전에 매번 실행해, Play
# Console의 "버전 코드가 이미 사용되었습니다" 오류(한 번 올린 versionCode는 재사용 불가)를
# 구조적으로 막는다.

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

NEW_CODE=$((CURRENT_CODE + 1))

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

cat > "$VERSION_FILE" <<EOF
VERSION_CODE=${NEW_CODE}
VERSION_NAME=${NEW_VERSION_NAME}
EOF

echo "Version bumped: ${CURRENT_NAME} (code ${CURRENT_CODE}) -> ${NEW_VERSION_NAME} (code ${NEW_CODE})"
