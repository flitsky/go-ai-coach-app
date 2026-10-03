"""실험실이 쓰는 파일 위치 — 바이너리·모델·cfg·결과 폴더.

모두 환경 변수로 바꿀 수 있다. 기본값은 이 맥의 실제 위치다.

⚠️ **모델과 cfg는 저장소 밖에 둔다.**
- 사람 모델(99MB)은 공개 저장소에 올리지 않는다 — `~/worksoc/katago/models/`(워크트리마다 따라오지 않으니 모든 워크트리가 같은 파일을 본다).
- 앱이 싣는 cfg(`app-android/src/friend/assets/katago/*.cfg`)는 **gitignore**라 워크트리에 없다. 원본은
  `~/worksoc/katago/config/katago/`이고 바이트가 같다(`Makefile`의 `FRIEND_CONFIG_PATH`). 그쪽을 먼저 본다.
"""
from __future__ import annotations

import os
from pathlib import Path

LAB_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = LAB_ROOT.parent

KATAGO_WORKSPACE = Path(os.environ.get("KATAGO_WORKSPACE", str(Path.home() / "worksoc" / "katago")))

BREW_KATAGO_SHARE = Path("/opt/homebrew/Cellar/katago/1.16.4/share/katago")
MAIN_MODEL_NAME = "kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
HUMAN_MODEL_NAME = "b18c384nbt-humanv0.bin.gz"

# 받은 파일을 확인하는 값(2026-10-03에 잰 것). 사람 모델은 KataGo GitHub 릴리즈 v1.15.0, 99,066,230 B.
HUMAN_MODEL_SHA256 = "637746e44f0efe00ad1245a50aa9bbf0716efe364c43965ead97bd6835d84ab5"
HUMAN_MODEL_URL = "https://github.com/lightvector/KataGo/releases/download/v1.15.0/b18c384nbt-humanv0.bin.gz"


def _first_existing(*candidates: Path) -> Path:
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return candidates[0]


def katago_binary() -> Path:
    return Path(os.environ.get("KATAGO_BIN", "/opt/homebrew/bin/katago"))


def main_model() -> Path:
    """앱이 싣는 것과 **같은 바이트**의 주 모델(b18c384nbt). brew가 가진 사본을 쓴다."""
    if "KATAGO_MODEL" in os.environ:
        return Path(os.environ["KATAGO_MODEL"])
    return _first_existing(BREW_KATAGO_SHARE / MAIN_MODEL_NAME, KATAGO_WORKSPACE / "models" / MAIN_MODEL_NAME)


def human_model() -> Path:
    if "KATAGO_HUMAN_MODEL" in os.environ:
        return Path(os.environ["KATAGO_HUMAN_MODEL"])
    return KATAGO_WORKSPACE / "models" / HUMAN_MODEL_NAME


def gtp_config() -> Path:
    if "KATAGO_GTP_CONFIG" in os.environ:
        return Path(os.environ["KATAGO_GTP_CONFIG"])
    return _first_existing(
        KATAGO_WORKSPACE / "config" / "katago" / "gtp_learning.cfg",
        REPO_ROOT / "app-android" / "src" / "friend" / "assets" / "katago" / "gtp_learning.cfg",
    )


def analysis_config() -> Path:
    if "KATAGO_ANALYSIS_CONFIG" in os.environ:
        return Path(os.environ["KATAGO_ANALYSIS_CONFIG"])
    return _first_existing(
        KATAGO_WORKSPACE / "config" / "katago" / "analysis_learning.cfg",
        REPO_ROOT / "app-android" / "src" / "friend" / "assets" / "katago" / "analysis_learning.cfg",
    )


def positions_dir() -> Path:
    return LAB_ROOT / "positions"


def experiments_dir() -> Path:
    return LAB_ROOT / "experiments"
