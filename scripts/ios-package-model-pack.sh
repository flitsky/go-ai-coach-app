#!/usr/bin/env bash
# iOS 엔진 모델 팩(백로그 #293 — 사용자 2026-10-27: 설치 뒤 Apple이 호스팅하는 Background Assets로 받는다).
# 안드로이드 PAD 팩 둘(katago_model_pack · katago_human_pack)과 같은 두 모델을 팩 하나(`katago-models`)로 묶는다 — 앱은 `katago/<파일>`로 찾는다
# (`app-ios/GoCoach/AppleHostedModelSource.swift`). 설정 둘(gtp_learning.cfg · analysis_learning.cfg)은 안드로이드처럼 앱에 싣는다(ios-bundle-resources.sh).
#
# 쓰는 법: scripts/ios-package-model-pack.sh <model.bin.gz> <human.bin.gz>     (make ios-package-model-pack — FRIEND_* 경로)
# 결과:   app-ios/build/asset-packs/katago-models.aar → App Store Connect에 올린다(Transporter, 또는 `xcrun altool --upload-asset-pack`).
# ⚠️ 정식 판 Xcode로 묶는다(베타로 묶은 팩은 심사에 못 낸다). 팩 판은 앱 판과 따로 올라가고, 올린 판은 **옛 앱에도** 간다 —
#    모델이 바뀌어 옛 앱과 맞지 않으면 팩 ID를 새로 짓는다.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODEL="${1:?usage: ios-package-model-pack.sh <model.bin.gz> <human.bin.gz>}"
HUMAN="${2:?usage: ios-package-model-pack.sh <model.bin.gz> <human.bin.gz>}"
OUT="$ROOT/app-ios/build/asset-packs"
STAGE="$OUT/staging"

rm -rf "$STAGE"
mkdir -p "$STAGE/katago"
cp "$MODEL" "$STAGE/katago/model.bin.gz"
cp "$HUMAN" "$STAGE/katago/human.bin.gz"

# prefetch: 설치 때 시작해 앱을 연 뒤에도 이어 받는다(앱이 부르는 on-demand 받기는 iOS 27에서 백그라운드로 가면 끊기는 알려진 문제가 있다).
# sourceRoot는 매니페스트 자리 기준의 상대 경로여야 한다(절대 경로는 거부된다).
cat > "$OUT/Manifest.json" <<JSON
{
  "assetPackID": "katago-models",
  "downloadPolicy": { "prefetch": { "installationEventTypes": ["firstInstallation", "subsequentUpdate"] } },
  "fileSelectors": [
    { "file": "katago/model.bin.gz" },
    { "file": "katago/human.bin.gz" }
  ],
  "platforms": ["iOS"],
  "sourceRoot": "staging"
}
JSON

xcrun ba-package evaluate "$OUT/Manifest.json"
# ⚠️ -o는 절대 경로로 — 상대 경로는 sourceRoot 기준으로 풀려 실패한다.
xcrun ba-package package "$OUT/Manifest.json" -o "$OUT/katago-models.aar"
ls -la "$OUT/katago-models.aar"
