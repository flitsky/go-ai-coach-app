"""앱과 맞춰야 하는 값 — **이 표 한 곳**에만 둔다(백로그 #214).

실험실은 앱 코드를 import하지 않는다. 대신 `tests/test_app_parity.py`가 아래 `SOURCES`의 앱 소스 **글자**를 읽어
이 표와 비교한다 — 앱이 16방문을 바꾸거나 버킷 비율을 고치면 실험실 테스트가 빨개져서, 낡은 값으로 잰 실험이
조용히 쌓이지 않는다(옛 `run-katago-level-match.py`가 2026-08-18 이전의 3단계 정의로 남아 있던 것이 그 사례다).
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Tier:
    """빠른초급의 한 단계 = 캐릭터 한 명(`BotCharacterCatalog`가 1:1로 잇는다)."""

    level: int
    name: str
    character: str
    worst_percent: int = 0
    mid_percent: int = 0
    best_percent: int = 0
    best_only: bool = False


# 빠른초급 = 캐릭터 5명이 쓰는 그룹(`PlayLevel.kt` `FastBeginner`).
FAST_BEGINNER_VISITS = 16
FAST_BEGINNER_CANDIDATE_COUNT = 8
FAST_BEGINNER_DEFAULT_TIME_MS = 1000  # 사용자 「최대 탐색 시간」이 늘 덮어쓴다(`SearchTimeSettings.applyTo`)

TIERS: tuple[Tier, ...] = (
    Tier(1, "초보", "문하생 판다", worst_percent=100),
    Tier(2, "하수", "문하생 돌뫼", worst_percent=60, mid_percent=40),
    Tier(3, "중수", "수제자 반상", worst_percent=20, mid_percent=60, best_percent=20),
    Tier(4, "고수", "사범 꼬북", worst_percent=10, mid_percent=30, best_percent=60),
    Tier(5, "초고수", "관장 천원", best_only=True),  # 후보 1개만 요청해 그 수를 둔다
)

# 사용자 「최대 탐색 시간」 선택지(밀리초, None = 끔)와 기본값.
SEARCH_TIME_LIMITS_MS: tuple[int | None, ...] = (None, 1000, 3000, 5000, 10000)
DEFAULT_SEARCH_TIME_MS = 5000

# 대국 기본값.
DEFAULT_BOARD_SIZE = 13
DEFAULT_KOMI = 6.5
DEFAULT_RULES = "japanese"

# GTP 프로세스 기동 덮어쓰기(`EngineBootstrap.kt` startupOverrides + `KataGoProcessRuntime.kt` EngineBehaviorOverrides).
GTP_STARTUP_OVERRIDES: dict[str, str] = {
    "numSearchThreads": "1",
    "allowResignation": "false",
    "assumeMultipleStartingBlackMovesAreHandicap": "false",
    "logToStderr": "false",
    "logAllGTPCommunication": "false",
    "logSearchInfo": "false",
    "startupPrintMessageToStderr": "false",
}
# JSON 분석 프로세스(`KataGoProcessRuntime.kt` buildAnalysisCommand).
ANALYSIS_NUM_ANALYSIS_THREADS = 1
ANALYSIS_SEARCH_THREADS = 4

# 위 값을 확인하는 앱 소스(저장소 루트 기준).
SOURCES = {
    "play_level": "shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/policy/PlayLevel.kt",
    "search_time": "shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/policy/SearchTimeSettings.kt",
    "engine_bootstrap": "app-android/src/main/java/com/worksoc/goaicoach/engine/EngineBootstrap.kt",
    "process_runtime": "engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProcessRuntime.kt",
    "preferences": "shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/preferences/UserPreferencesSnapshot.kt",
    "board_models": "core/domain/src/commonMain/kotlin/com/worksoc/goaicoach/shared/domain/BoardModels.kt",
    "selection_policy": "shared/src/commonMain/kotlin/com/worksoc/goaicoach/match/AiMoveSelectionPolicy.kt",
    "gtp_client": "engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoGtpAnalysisClient.kt",
    "parser": "engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoAnalysisParser.kt",
    "protocol": "engine-android/src/main/java/com/worksoc/goaicoach/engine/android/KataGoProtocolCommands.kt",
}


def tier(level: int) -> Tier:
    for item in TIERS:
        if item.level == level:
            return item
    raise KeyError(level)


def gtp_overrides(max_visits: int) -> dict[str, str]:
    """앱 GTP 프로세스와 같은 덮어쓰기 + 그 프로필의 `maxVisits`(`buildGtpCommand`)."""
    return {**GTP_STARTUP_OVERRIDES, "maxVisits": str(max_visits)}
