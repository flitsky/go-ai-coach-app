"""Shared helpers for the MQTT/Firestore session-topic prototypes.

Both `run_session_topic_mqtt_prototype.py` and
`run_session_topic_firestore_prototype.py` play the same 3-role scenario
(1 requester + N remote candidates) against different transports, so the
role logic that doesn't depend on the transport — fake analysis payloads,
timestamp-ordered reward ranking, JSONL run logs — lives here once.

See `docs/refactoring/REMOTE_ENGINE_MQ_TRANSPORT_KICKOFF_PLAN_260818_0825.md`
section 6, item 1.
"""

from __future__ import annotations

import json
import random
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

REWARD_POINTS_BY_RANK = [5, 3, 2, 1]  # section 2 draft: fastest 3 candidates, ranked


@dataclass
class FakeAnalysisResult:
    """Stand-in for a real KataGo response (see prototype item 2 for the real thing).

    Centered on a "true" winrate/score with small noise, so downstream
    consistency-check experiments have something non-trivial to compare —
    real KataGo runs are non-deterministic in the same way (thread race,
    batching order), which is exactly the property item 2 needs to probe.
    """

    winrate: float
    score_lead: float

    @staticmethod
    def sample(true_winrate: float = 0.55, true_score_lead: float = 3.0, noise: float = 0.02) -> "FakeAnalysisResult":
        return FakeAnalysisResult(
            winrate=min(1.0, max(0.0, true_winrate + random.uniform(-noise, noise))),
            score_lead=true_score_lead + random.uniform(-noise * 50, noise * 50),
        )


def new_run_log(path: Path) -> "RunLog":
    return RunLog(path)


class RunLog:
    """Append-only JSONL log — one line per protocol event, wall-clock timestamped.

    This is the artifact item 5 in section 2 of the kickoff plan cares
    about: "세션 ID 단일 토픽은 요청/응답 메시지에 타임스탬프가 자연히 남는다" —
    this file is that history, kept outside the broker/DB so runs are easy
    to diff and replay without a live MQTT/Firestore connection.
    """

    def __init__(self, path: Path) -> None:
        self._path = path
        self._path.parent.mkdir(parents=True, exist_ok=True)

    def record(self, event: str, **fields: Any) -> None:
        line = {"event": event, "logged_at": time.time(), **fields}
        with self._path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(line, default=str) + "\n")


def rank_responses(responses: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Sort responses by arrival time and attach reward points per section 2's draft.

    `responses` items must carry a numeric `received_at` (monotonic or wall
    clock, just needs to be comparable across candidates for one request).
    """
    ordered = sorted(responses, key=lambda r: r["received_at"])
    for rank, response in enumerate(ordered):
        response["rank"] = rank + 1
        response["reward_points"] = REWARD_POINTS_BY_RANK[rank] if rank < len(REWARD_POINTS_BY_RANK) else 0
    return ordered


def result_to_json(result: FakeAnalysisResult) -> dict[str, float]:
    return asdict(result)
