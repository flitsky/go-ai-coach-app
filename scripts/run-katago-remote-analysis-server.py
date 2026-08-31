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
    (`docs/engine/ENGINE_API_CALL_POLICY.md`, "종국 GTP 호출 정책"). Returning a
    clean error here is the honest choice over a half-correct scorer.
  - `estimateScore` returns win rate / score lead only, no ownership
    heatmap — see `run_engine_estimate_score`'s docstring for why.
  - `genMove` approximates GTP genmove as "play the top analyzed
    candidate" — see `run_engine_gen_move`'s docstring.
  - `includePolicy`/`refinePolicyMoves` in the request `limit` are accepted
    but not honored — as of 2026-08-18 no live app call path actually sends
    a non-zero `refinePolicyMoves` (see
    docs/engine/ENGINE_CANDIDATE_EXPANSION_REVIEW_2026-08-17.md),
    so this is not a regression for today's app, just a known gap if that
    changes later.
  - Always runs KataGo's stateless JSON analysis engine server-side,
    regardless of the request's `searchMode` field (`GtpStatefulFast` or
    `JsonPositionAnalysis`) — a remote HTTP call is inherently
    request/response, so there is no meaningful way to offer GTP's
    stateful search-tree-reuse fast path here.

Known wire-protocol gap found while building this (not this script's bug):
neither `RemotePositionAnalysisJsonCodec.encodeState()` nor
`RemoteEngineOperationJsonCodec` sends `komi` at all. This script assumes
`DefaultKomi` (6.5) for every request, which happens to match the app's
current default new-game komi (2026-08-18), but would be silently wrong for
any game played at a different komi. Fix belongs in the Kotlin codec (add a
`komi` field/parse it here), not here.

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
DEFAULT_KOMI = 6.5  # see module docstring: not carried by the wire protocol yet


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


def infer_initial_stones(state: dict[str, Any]) -> list[list[str]]:
    """Recover pre-placed (handicap) stones the wire state carries but the
    move list doesn't. `GameState.withHandicap()` (Kotlin) puts handicap
    stones only in `stones`, never in `moves` — so any stone present in the
    current board that no play-move of that color ever placed must have
    been there from the start. Good enough for handicap games; would not
    reconstruct a position built from a custom SGF setup with stranger
    initial arrangements, but the app has no such feature today.
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

    query: dict[str, Any] = {
        "rules": rules,
        "komi": DEFAULT_KOMI,
        "boardXSize": board_size,
        "boardYSize": board_size,
        "initialPlayer": "B",
        "initialStones": infer_initial_stones(state),
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
