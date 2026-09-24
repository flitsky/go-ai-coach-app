#!/usr/bin/env python3
"""Development-time reference server for the app's remote-engine contracts.

Runs KataGo's analysis engine on this machine (a MacBook, in the motivating
use case) and answers HTTP requests using the exact JSON shapes the Kotlin
client already speaks. Two independent endpoints, matching two independent
Kotlin-side contracts that both already exist and are tested:

  - POST /analyze — `RemotePositionAnalysisJsonCodec`
    (`HttpRemotePositionAnalysisTransport.kt`). Narrower, read-only,
    position-analysis-only. Not wired into the real app yet.
  - POST /engine  — `RemoteEngineOperationJsonCodec`
    (`RemoteEngineCoreApiAdapter.kt`). Wider: `analyze`/`genMove`/
    `estimateScore` are implemented here; `deadStones`/`scoreFinal` return a
    clean "not implemented" error (see Scope below). This is what
    `createRemoteEngineSessionClient` (`RemoteEngineSessionBootstrap.kt`)
    talks to, and lets a phone actually *play* a full game against this
    server, not just compare analysis output.

Point `RemoteEngineCandidate.endpointUrl` at
`http://<this-machine-lan-ip>:<port>/engine` for real gameplay, or at
`.../analyze` to exercise only the narrower read-only contract.

Scope cuts (deliberate, documented rather than silently guessed at):

  - `deadStones`/`scoreFinal` are NOT implemented — real Go area/territory
    scoring and dead-stone reading is a meaningfully large amount of logic
    to get right, and the app's own endgame flow already tolerates these
    two operations failing/timing out and falls back to local judging
    (`ENGINE_API_CALL_POLICY.md`, "종국 GTP 호출 정책"). Returning a
    clean error here is the honest choice over a half-correct scorer.
  - `estimateScore` returns win rate / score lead only, no ownership
    heatmap — see `run_engine_estimate_score`'s docstring for why.
  - `genMove` approximates GTP genmove as "play the top analyzed
    candidate" — see `run_engine_gen_move`'s docstring.
  - `includePolicy`/`refinePolicyMoves` in the request `limit` are accepted
    but not honored — as of 2026-08-18 no live app call path actually sends
    a non-zero `refinePolicyMoves` (see
    docs/engine/ENGINE_STRENGTH_RESEARCH.md),
    so this is not a regression for today's app, just a known gap if that
    changes later.
  - Always runs KataGo's stateless JSON analysis engine server-side,
    regardless of the request's `searchMode` field (`GtpStatefulFast` or
    `JsonPositionAnalysis`) — a remote HTTP call is inherently
    request/response, so there is no meaningful way to offer GTP's
    stateful search-tree-reuse fast path here.

Wire-protocol status (updated 2026-09-23, refactor backlog #62):
`RemotePositionAnalysisJsonCodec.encodeState()` now puts both `komi` and
`handicapCount` on the wire as flat fields of `state` (refactor backlog #19,
commit 7ffccd17) — both `HttpRemotePositionAnalysisTransport` (`/analyze`)
and `RemoteEngineCoreApiAdapter` (`/engine`) share that codec, so every
request this script receives carries them now. Before #19, neither field was
sent, and this script assumed `DEFAULT_KOMI` (6.5) for every request — silent
and wrong for any game played at a different komi. That gap is closed here:
`build_katago_query()` reads `state["komi"]`, falling back to `DEFAULT_KOMI`
only when the field is absent (an old, pre-#19 client).

Handicap (refactor backlog #65, settled by measurement 2026-09-24):
`build_katago_query()` reads `handicapCount` to rebuild the handicap stones
as `initialStones` (`handicap_stone_positions()`, a port of the Kotlin
`BoardSize.handicapStonePositions`), and sets `initialPlayer` to the side
that actually moves first (White in a handicap game). It deliberately does
NOT send `whiteHandicapBonus`. That field picks a compensation *method*
("0"/"N"/"N-1"), it is not a number to add; KataGo counts N itself from the
black stones in `initialStones`. Left unset, the ruleset default applies
(chinese="N", japanese="0" — the same values `kata-get-rules` reports for
the app's local GTP engine). Measured with the app's model on 9/13/19,
H2-H9, both rulesets: raw-NN leads then equal local GTP `kata-raw-nn`
exactly at every handicap opening; after moves they differ by at most 0.6,
and all of that is KataGo's default `analysisIgnorePreRootHistory=true`
(0.00 with it off), not handicap.

What forcing the field would do. Source: the #65-A measurement run behind
commit b8fc9167 (20 rows: 10 positions x 2 rulesets), re-read for refactor
backlog #90 — nothing was re-measured for #90, and that run's raw output
was never committed, so this table is its in-repo copy. Forcing the
ruleset's own default ("N" under chinese, "0" under japanese) is a no-op:
raw-NN lead unchanged in all 20 rows, searched lead within 0.11. Forcing
the *other* value moves White's point lead by roughly N, not exactly N.
Shift in White's lead vs. the field left unset; komi 0.5 unless noted;
raw = raw NN (maxVisits=1, deterministic), search = searched lead (the
measurement script's SEARCH_VISITS, default 400):

                                         japanese "N"    chinese "0"
    position                        N    raw  search     raw  search
    9x9 opening                     2  +2.27  +2.24   -2.24  -2.17
    9x9 opening                     4  +1.82  +4.00   +0.14  -3.93
    9x9 after 4 moves               2  +2.24  +2.17   -1.95  -2.09
    9x9, W captured G7 (7 moves)    2  +2.19  +2.21   -2.19  -2.07
    13x13 opening, komi 6.5         5  +5.55  +5.41   -3.67  -4.75
    19x19 opening                   2  +2.40  +2.61   -2.91  -2.35
    19x19 opening                   4  +3.79  +3.47   -4.15  -4.85
    19x19 after 2 moves             4  +3.49  +3.50   -4.26  -4.31
    19x19 opening                   9  +8.50  +9.36   -5.31  -7.67
    19x19 after 2 moves, komi 7.5   6  +6.15  +6.30   -6.57  -6.52

By searched lead every row moves in the expected direction by 0.85-1.30
times N. Raw NN agrees (japanese 0.87-1.20 times N, chinese 0.59-1.45
times N) except the 9x9 H4 opening, whose raw values (+1.82, and +0.14 —
the wrong sign) are outliers next to its own searched values (+4.00 /
-3.93). The field stays unset because, left unset, the remote query uses
the same compensation the local GTP engine does; forcing the other value
would move the remote path off the local one by about the amounts above.

`build_katago_query()` is checked without KataGo by
`make test-remote-analysis-server`
(`scripts/test_run_katago_remote_analysis_server.py`, refactor backlog #90).

Usage:
    python3 scripts/run-katago-remote-analysis-server.py --port 8765

Then on the phone, point RemoteEngineCandidate.endpointUrl at
http://<mac-lan-ip>:8765/engine (both devices must be on the same network).
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

DEFAULT_KATAGO = "/opt/homebrew/bin/katago"
DEFAULT_MODEL = "/opt/homebrew/Cellar/katago/1.16.4/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
DEFAULT_CONFIG = "app-android/src/friend/assets/katago/analysis_learning.cfg"
DEFAULT_KOMI = 6.5  # fallback only now (#62) — build_katago_query() prefers state["komi"] when a client sends it


class KataGoEngine:
    """Owns one persistent `katago analysis` subprocess, serialized by a lock."""

    def __init__(self, katago: str, model: str, config: str, search_threads: int, analysis_threads: int) -> None:
        overrides = [
            "logToStderr=false",
            "logAllRequests=false",
            "logAllResponses=false",
            "logSearchInfo=false",
            f"numAnalysisThreads={analysis_threads}",
            f"numSearchThreads={search_threads}",
        ]
        self._process = subprocess.Popen(
            [katago, "analysis", "-model", model, "-config", config, "-override-config", ",".join(overrides)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )
        self._lock = threading.Lock()
        self._counter = 0
        # Warm up: first query pays model-load latency, don't let a real
        # caller eat that.
        self._query({
            "id": "warmup",
            "rules": "japanese",
            "komi": DEFAULT_KOMI,
            "boardXSize": 9,
            "boardYSize": 9,
            "initialPlayer": "B",
            "initialStones": [],
            "moves": [],
            "analyzeTurns": [0],
            "maxVisits": 1,
            "includePolicy": False,
            "includeOwnership": False,
            "includeMovesOwnership": False,
            "overrideSettings": {},
        })

    def analyze(self, query_body: dict[str, Any]) -> dict[str, Any]:
        with self._lock:
            self._counter += 1
            query_body = dict(query_body)
            query_body["id"] = f"remote-{self._counter}"
            return self._query(query_body)

    def _query(self, query_body: dict[str, Any]) -> dict[str, Any]:
        assert self._process.stdin is not None
        assert self._process.stdout is not None
        self._process.stdin.write(json.dumps(query_body, separators=(",", ":")) + "\n")
        self._process.stdin.flush()
        query_id = query_body["id"]
        while True:
            line = self._process.stdout.readline()
            if not line:
                raise RuntimeError("KataGo analysis process exited unexpectedly")
            response = json.loads(line)
            if response.get("id") != query_id:
                continue
            if response.get("isDuringSearch"):
                continue
            if "error" in response:
                raise RuntimeError(f"KataGo query failed: {response['error']}")
            return response

    def stop(self) -> None:
        with self._lock:
            self._process.terminate()


GTP_COLUMNS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"


def handicap_stone_positions(board_size: int, count: int) -> list[str]:
    """Port of the Kotlin `BoardSize.handicapStonePositions(count)`
    (shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/domain/BoardModels.kt),
    returning GTP labels in the same order: upper-right, lower-left,
    lower-right, upper-left, center, then (19x19 only) left, right, bottom,
    top side star points. Keep the two in sync — this is how the app itself
    places handicap stones (`GameState.withHandicap`, and `set_free_handicap`
    on the local GTP engine), so any drift here puts the stones somewhere the
    phone never had them. Same range checks as the Kotlin `require`s.
    """
    if board_size not in (9, 13, 19):
        raise ValueError(f"Unsupported board size: {board_size}")
    max_count = 9 if board_size == 19 else 5
    if not 0 <= count <= max_count:
        raise ValueError(f"handicapCount {count} is not supported on {board_size}x{board_size}")
    if count == 0:
        return []
    near = 2 if board_size == 9 else 3
    far = board_size - 1 - near
    mid = board_size // 2
    side = [(mid, near), (mid, far), (far, mid), (near, mid)] if board_size == 19 else []
    ordered = [(near, far), (far, near), (far, far), (near, near), (mid, mid)] + side
    return [f"{GTP_COLUMNS[column]}{board_size - row}" for row, column in ordered[:count]]


def infer_initial_stones(state: dict[str, Any]) -> list[list[str]]:
    """Recover pre-placed stones of a position that is not a handicap game
    (`handicapCount` 0 or absent): a static position (board scan), or an old
    client. Any stone present in the current board that no play-move of that
    color ever placed must have been there from the start.

    Not used for handicap games any more (refactor backlog #65): a handicap
    stone White later captured is gone from `stones`, so this inference
    silently dropped it — KataGo then counted N-1 handicap stones (1 stone
    counts as 0), changing the chinese handicap compensation and the
    japanese prisoner count. `build_katago_query()` rebuilds those from
    `handicapCount` instead.
    """
    moves = state.get("moves", [])
    played_by_color: dict[str, set[str]] = {"Black": set(), "White": set()}
    for move in moves:
        if move.get("type", "play") == "play":
            played_by_color.setdefault(move["player"], set()).add(move["point"])

    initial: list[list[str]] = []
    for stone in state.get("stones", []):
        color = stone["color"]
        point = stone["point"]
        if point not in played_by_color.get(color, set()):
            initial.append(["B" if color == "Black" else "W", point])
    return initial


def build_katago_query(request_body: dict[str, Any]) -> dict[str, Any]:
    state = request_body["state"]
    limit = request_body["limit"]
    board_size = state["boardSize"]
    rules = "chinese" if state["ruleset"] == "Chinese" else "japanese"

    moves: list[list[str]] = []
    for move in state.get("moves", []):
        player = "B" if move["player"] == "Black" else "W"
        move_type = move.get("type", "play")
        if move_type == "play":
            moves.append([player, move["point"]])
        elif move_type == "pass":
            moves.append([player, "pass"])
        # "resign" moves aren't meaningful to replay into an analysis query;
        # a resigned game shouldn't be asking for further analysis anyway.

    # Refactor backlog #65. `initialPlayer` is the side to move at turn 0. It
    # used to be hard-coded "B", so a handicap opening (no moves yet, White to
    # move) was analyzed as *Black* to move: White's lead came out 9-17 points
    # low (raw NN), and the candidates were Black's best points labeled as
    # White's — which `/engine` genMove then played for White. With moves, the
    # first move's color is the side to move at turn 0.
    if moves:
        initial_player = moves[0][0]
    else:
        initial_player = "W" if state.get("nextPlayer") == "White" else "B"

    # Handicap stones. Same split the app uses in `EngineCoreApi.syncToGameState`
    # (shared/.../application/engine/EngineSession.kt): handicapCount > 0 means
    # "newGame with the standard handicap points, then replay the moves", so
    # rebuild exactly those points; handicapCount 0 (or an old client that
    # doesn't send it) keeps inferring them from the board.
    handicap_count = state.get("handicapCount", 0)
    if handicap_count > 0:
        initial_stones = [["B", point] for point in handicap_stone_positions(board_size, handicap_count)]
    else:
        initial_stones = infer_initial_stones(state)

    query: dict[str, Any] = {
        "rules": rules,
        # Refactor backlog #62: prefer the client-sent komi (on the wire since #19,
        # commit 7ffccd17); fall back to DEFAULT_KOMI only for an old client that
        # never sends the field. See module docstring for the history.
        "komi": state.get("komi", DEFAULT_KOMI),
        "boardXSize": board_size,
        "boardYSize": board_size,
        "initialPlayer": initial_player,
        # No `whiteHandicapBonus`, on purpose (refactor backlog #65, measured against
        # the app's local GTP engine with the app's own model). It selects how
        # KataGo compensates the N stones it counts in `initialStones` — it is not a
        # count to add. The ruleset default (chinese "N", japanese "0") is exactly
        # what the local GTP engine uses (`kata-get-rules`). Forcing the default is a
        # no-op; forcing the other value moves White's lead by roughly N points
        # (0.85-1.30 times N by searched lead in the #65-A measurement). The table
        # is in the module docstring.
        "initialStones": initial_stones,
        "moves": moves,
        "analyzeTurns": [len(moves)],
        "maxVisits": limit["visits"],
        "includePolicy": False,
        "includeOwnership": False,
        "includeMovesOwnership": False,
        "overrideSettings": {},
    }
    time_millis = limit.get("timeMillis")
    if time_millis is not None:
        query["overrideSettings"]["maxTime"] = time_millis / 1000.0
    return query


def encode_candidates(
    katago_response: dict[str, Any],
    next_player: str,
    candidate_count: int,
    board_size: int,
) -> tuple[list[dict[str, Any]], int | None]:
    root_info = katago_response.get("rootInfo") or {}
    root_black_score_lead = root_info.get("scoreLead")
    reference_score_lead = -root_black_score_lead if root_black_score_lead is not None else None
    root_visits = root_info.get("visits")

    move_infos = sorted(katago_response.get("moveInfos", []), key=lambda info: info.get("order", 999_999))
    move_infos = move_infos[:candidate_count]

    candidates: list[dict[str, Any]] = []
    for index, info in enumerate(move_infos):
        move = info.get("move", "")
        move_type = "pass" if move.lower() == "pass" else "play"

        black_score_lead = info.get("scoreLead")
        score_lead = -black_score_lead if black_score_lead is not None else None
        point_loss = None
        if score_lead is not None and reference_score_lead is not None:
            raw = (score_lead - reference_score_lead) if next_player == "Black" else (reference_score_lead - score_lead)
            point_loss = 0.0 if abs(raw) < 1e-6 else max(raw, 0.0)

        black_win_rate = info.get("winrate")
        win_rate = None
        if black_win_rate is not None:
            win_rate = black_win_rate if next_player == "Black" else 1.0 - black_win_rate
            win_rate = min(1.0, max(0.0, win_rate))

        candidate: dict[str, Any] = {
            "player": next_player,
            "type": move_type,
            "engineOrder": info.get("order", index),
            "source": "EngineSearch",
            "note": f"KataGo JSON order {info.get('order', index)}",
        }
        if move_type == "play":
            candidate["point"] = move
            candidate["boardSize"] = board_size
        if win_rate is not None:
            candidate["winRate"] = win_rate
        if score_lead is not None:
            candidate["scoreLead"] = score_lead
        if point_loss is not None:
            candidate["pointLoss"] = point_loss
        if "visits" in info:
            candidate["visits"] = info["visits"]
        if "prior" in info:
            candidate["policyPrior"] = info["prior"]
        candidates.append(candidate)

    return candidates, root_visits


# --- Broader /engine endpoint (RemoteEngineOperationJsonCodec) -------------
#
# `createRemoteEngineSessionClient` (already wired end-to-end on the Kotlin
# side, `RemoteEngineSessionBootstrap.kt`) speaks this wider contract, not
# the narrower /analyze one above. It's what lets a phone actually *play* a
# full game against this server (genMove/analyze/estimateScore), not just
# compare analysis output. Scope cut, stated plainly: `deadStones`/
# `scoreFinal` are NOT implemented (see module docstring) — the app's own
# endgame flow already tolerates these failing/timing out and falls back to
# local judging, so returning a clean error here is the honest choice
# instead of half-implementing Go scoring rules in this spike.

def run_engine_analyze(engine: KataGoEngine, request_body: dict[str, Any]) -> dict[str, Any]:
    state = request_body["state"]
    next_player = request_body["player"]
    candidate_count = request_body["limit"]["candidateCount"]
    board_size = state["boardSize"]
    katago_query = build_katago_query(request_body)
    katago_response = engine.analyze(katago_query)
    candidates, root_visits = encode_candidates(katago_response, next_player, candidate_count, board_size)
    return {
        "status": {"state": "Ready", "message": "Remote engine analyze complete."},
        "summary": f"Remote (macOS reference server) analyze, {len(candidates)} candidate(s), rootVisits={root_visits}.",
        "candidates": candidates,
        "rootVisits": root_visits,
    }


def run_engine_gen_move(engine: KataGoEngine, request_body: dict[str, Any]) -> dict[str, Any]:
    """Approximates GTP genmove as "play the top-ranked analyzed candidate".

    Real KataGo genmove has its own resign/pass heuristics this doesn't
    replicate — acceptable for a dev spike since the app's actual AI-move
    path normally goes through `analyze` + client-side selection anyway
    (`AiMoveSelectionPolicy`); `genMove` is only its fallback when that
    returns nothing usable.
    """
    state = request_body["state"]
    player = request_body["player"]
    board_size = state["boardSize"]
    # Ask for just the top move; still requests the configured visits/time.
    forced_top_only_request = dict(request_body)
    forced_top_only_request["limit"] = dict(request_body["limit"], candidateCount=1)
    katago_query = build_katago_query(forced_top_only_request)
    katago_response = engine.analyze(katago_query)
    move_infos = katago_response.get("moveInfos", [])
    if not move_infos:
        return {
            "status": {"state": "Ready", "message": "Remote engine genMove found no candidates; passing."},
            "summary": "No scored candidates from KataGo; passing.",
            "move": {"player": player, "type": "pass"},
        }
    top = min(move_infos, key=lambda info: info.get("order", 999_999))
    move_text = top.get("move", "pass")
    move_json: dict[str, Any] = {"player": player}
    if move_text.lower() == "pass":
        move_json["type"] = "pass"
    else:
        move_json["type"] = "play"
        move_json["point"] = move_text
        move_json["boardSize"] = board_size
    return {
        "status": {"state": "Ready", "message": "Remote engine genMove complete."},
        "summary": f"Remote (macOS reference server) genMove selected {move_text}.",
        "move": move_json,
    }


def run_engine_estimate_score(engine: KataGoEngine, request_body: dict[str, Any]) -> dict[str, Any]:
    """Win rate / score lead only — no ownership heatmap.

    KataGo JSON analysis does return an `ownership` array with
    `includeOwnership: true`, but this script doesn't request it: the sign
    convention of that array (perspective it's reported from) wasn't
    confirmed against `KataGoAnalysisParser`'s GTP-text-based ownership
    parsing (which uses a documented 0.15 threshold on a *different*,
    text-protocol response) during this session, and a wrong sign would
    silently produce a plausible-looking but backwards heatmap — worse than
    omitting it. `whiteWinRate`/`whiteScoreLead` use the same
    `rootInfo.winrate`/`rootInfo.scoreLead` fields already verified correct
    for the /analyze path above, so those are safe to serve.
    """
    katago_query = build_katago_query(request_body)
    katago_response = engine.analyze(katago_query)
    root = katago_response.get("rootInfo") or {}
    black_winrate = root.get("winrate")
    black_score_lead = root.get("scoreLead")
    return {
        "status": {"state": "Ready", "message": "Remote engine estimateScore complete."},
        "summary": "Remote (macOS reference server) score estimate (ownership heatmap not implemented).",
        "whiteWinRate": (1.0 - black_winrate) if black_winrate is not None else None,
        "whiteScoreLead": (-black_score_lead) if black_score_lead is not None else None,
    }


def run_engine_not_implemented(operation: str) -> dict[str, Any]:
    return {
        "status": {"state": "Error", "message": f"Remote engine '{operation}' is not implemented by this dev server."},
        "summary": f"{operation} not implemented — app should fall back to local endgame judging.",
    }


ENGINE_OPERATIONS = {
    "analyze": run_engine_analyze,
    "genMove": run_engine_gen_move,
    "estimateScore": run_engine_estimate_score,
}


class AnalysisRequestHandler(BaseHTTPRequestHandler):
    engine: KataGoEngine  # set on the class before serving

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A002
        sys.stderr.write(f"[remote-analysis] {self.address_string()} - {format % args}\n")

    def do_POST(self) -> None:  # noqa: N802 (BaseHTTPRequestHandler API)
        path = self.path.rstrip("/")
        if path == "/engine":
            self._handle_engine_operation()
        elif path in ("", "/analyze"):
            self._handle_position_analyze()
        else:
            self.send_error(404, "Unknown endpoint. POST to /analyze or /engine.")

    def _handle_engine_operation(self) -> None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
            request_body = json.loads(self.rfile.read(length))
            operation = request_body.get("operation", "")
            handler = ENGINE_OPERATIONS.get(operation)
            if handler is not None:
                result = handler(self.engine, request_body)
            else:
                result = run_engine_not_implemented(operation)
            self._write_json(200, {"result": result})
        except Exception as exc:  # noqa: BLE001
            self._write_json(500, {"error": str(exc)})
            sys.stderr.write(f"[remote-analysis] /engine request failed: {exc!r}\n")

    def _write_json(self, status_code: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _handle_position_analyze(self) -> None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw_body = self.rfile.read(length)
            request_body = json.loads(raw_body)

            next_player = request_body["state"]["nextPlayer"]
            candidate_count = request_body["limit"]["candidateCount"]
            board_size = request_body["state"]["boardSize"]

            started = time.perf_counter()
            katago_query = build_katago_query(request_body)
            katago_response = self.engine.analyze(katago_query)
            elapsed_ms = (time.perf_counter() - started) * 1000.0

            candidates, root_visits = encode_candidates(katago_response, next_player, candidate_count, board_size)
            response_body = {
                "result": {
                    "status": {"state": "Ready", "message": "Remote position analysis complete."},
                    "candidates": candidates,
                    "summary": f"Remote (macOS reference server) analysis in {elapsed_ms:.0f}ms, "
                               f"{len(candidates)} candidate(s), rootVisits={root_visits}.",
                    "rootVisits": root_visits,
                },
                "diagnosticText": f"positionFingerprint={request_body.get('positionFingerprint')}",
            }
            self._write_json(200, response_body)
        except Exception as exc:  # noqa: BLE001 - report to the client, don't crash the server
            self._write_json(500, {"error": str(exc)})
            sys.stderr.write(f"[remote-analysis] /analyze request failed: {exc!r}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--search-threads", type=int, default=4)
    parser.add_argument("--analysis-threads", type=int, default=1)
    parser.add_argument("--katago", default=os.environ.get("KATAGO_BIN", DEFAULT_KATAGO))
    parser.add_argument("--model", default=os.environ.get("KATAGO_MODEL", DEFAULT_MODEL))
    parser.add_argument("--config", default=os.environ.get("KATAGO_ANALYSIS_CONFIG", DEFAULT_CONFIG))
    args = parser.parse_args()

    print(f"[remote-analysis] starting KataGo ({args.model})...", file=sys.stderr)
    engine = KataGoEngine(
        katago=args.katago,
        model=args.model,
        config=args.config,
        search_threads=args.search_threads,
        analysis_threads=args.analysis_threads,
    )
    AnalysisRequestHandler.engine = engine

    server = ThreadingHTTPServer((args.host, args.port), AnalysisRequestHandler)
    print(f"[remote-analysis] listening on http://{args.host}:{args.port}  (POST /analyze or /engine)", file=sys.stderr)
    print("[remote-analysis] full gameplay: point RemoteEngineCandidate.endpointUrl at "
          f"http://<this-machine-lan-ip>:{args.port}/engine", file=sys.stderr)
    print("[remote-analysis] analysis-only spike: .../analyze instead", file=sys.stderr)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        engine.stop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
