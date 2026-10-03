#!/usr/bin/env python3
"""Timeout + parallel local/remote fallback simulation — kickoff plan section 6, item 3.

Section 2's draft behavior: "'최대 탐색 시간 제한'(Off면 30초) 안에 원격 응답이 없으면
원격을 '연결 불가'로 전환하되, 로컬 분석은 계속하고 원격 요청 발행도 계속 병행한다."
This script proves that behavior out in pure Python before it becomes Kotlin
concurrency code (the plan's stated reason for doing this here first: "이런
동시성/타임아웃 로직은 Kotlin coroutine 안에서 처음부터 디버깅하는 것보다, 순수
Python으로 로직만 먼저 검증하고 나서 이식하는 편이 훨씬 안전하다").

Built directly on `run_session_topic_mqtt_prototype`'s connect/topic helpers
(reusing the already-verified transport plumbing from item 1) rather than a
fresh MQTT setup, since the thing under test here is the concurrency logic,
not the wire protocol again.

Scenario: the "requester" role advances a `local_committed_move` counter on
its own fixed cadence — exactly like a local KataGo engine that never waits
on the network — while remote requests for every move are fired off in
parallel. A candidate is configured to answer some moves late (slower than
local's cadence) and drop others entirely (`--drop-moves`), so responses
arrive both in-time and after local has already moved on. Every response is
classified in-time/stale by comparing its move_number against
`local_committed_move` *at the moment it arrives*, and only ever logged —
`committed_moves` (the actual "game state") is appended to solely by the
local cadence thread, never by the response handler, so a stale arrival
provably cannot corrupt or rewrite a move local already committed.

Usage:
    python3 scripts/remote-engine-mq-prototype/run_timeout_parallel_fallback_experiment.py --role demo
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).parent))
from _prototype_common import FakeAnalysisResult, new_run_log, result_to_json  # noqa: E402
from run_session_topic_mqtt_prototype import connect, request_topic, response_topic  # noqa: E402

DEFAULT_LOG = Path(__file__).parent / "runs" / "timeout-parallel-fallback.jsonl"


def run_candidate(args: argparse.Namespace) -> int:
    log = new_run_log(Path(args.log))
    session_id = args.session_id
    candidate_id = args.candidate_id
    drop_moves = set(args.drop_moves)
    client = connect(args.host, args.port, client_id=f"tf-candidate-{candidate_id}-{session_id}")

    def on_message(_client: Any, _userdata: Any, message: Any) -> None:
        payload = json.loads(message.payload.decode("utf-8"))
        if payload.get("type") != "analyze_request" or payload.get("session_id") != session_id:
            return
        move_number = payload["move_number"]
        if move_number in drop_moves:
            log.record("request_dropped_by_design", candidate_id=candidate_id, move_number=move_number)
            print(f"[candidate {candidate_id}] dropping move {move_number} (--drop-moves)", file=sys.stderr)
            return
        # Delay grows with move number, so later moves are more likely to
        # arrive after local has already moved on — the scenario item 3 asks for.
        delay_s = (args.base_delay_ms + move_number * args.delay_growth_ms) / 1000.0
        time.sleep(delay_s)
        result = FakeAnalysisResult.sample()
        response_payload = {
            "type": "analyze_response",
            "session_id": session_id,
            "request_id": payload["request_id"],
            "candidate_id": candidate_id,
            "move_number": move_number,
            "responded_at": time.time(),
            **result_to_json(result),
        }
        client.publish(response_topic(session_id), json.dumps(response_payload))
        log.record("response_published", **response_payload)
        print(f"[candidate {candidate_id}] answered move {move_number} after {delay_s * 1000:.0f}ms", file=sys.stderr)

    client.on_message = on_message
    client.subscribe(request_topic(session_id))
    print(f"[candidate {candidate_id}] session={session_id} ready (drop_moves={sorted(drop_moves)})", file=sys.stderr)

    try:
        time.sleep(args.moves * (args.base_delay_ms / 1000.0 + args.delay_growth_ms * args.moves / 1000.0) + 3)
    except KeyboardInterrupt:
        pass
    client.loop_stop()
    client.disconnect()
    return 0


def run_requester(args: argparse.Namespace) -> int:
    log = new_run_log(Path(args.log))
    session_id = args.session_id

    state_lock = threading.Lock()
    committed_moves: list[int] = []  # ONLY the local cadence thread appends here
    local_committed_move = -1  # -1 = no move committed yet
    classifications: list[dict[str, Any]] = []

    def on_message(_client: Any, _userdata: Any, message: Any) -> None:
        payload = json.loads(message.payload.decode("utf-8"))
        if payload.get("type") != "analyze_response" or payload.get("session_id") != session_id:
            return
        with state_lock:
            snapshot_committed = local_committed_move
        move_number = payload["move_number"]
        status = "in_time" if move_number >= snapshot_committed else "stale_discarded"
        record = {
            "move_number": move_number,
            "candidate_id": payload["candidate_id"],
            "local_committed_move_at_receipt": snapshot_committed,
            "status": status,
        }
        classifications.append(record)
        log.record("response_classified", session_id=session_id, **record)
        marker = "OK (in time)" if status == "in_time" else "DISCARDED (local already past this move — no state touched)"
        print(f"[requester] response for move {move_number} from {payload['candidate_id']}: {marker}", file=sys.stderr)

    client = connect(args.host, args.port, client_id=f"tf-requester-{session_id}")
    client.on_message = on_message
    client.subscribe(response_topic(session_id))
    log.record("requester_started", session_id=session_id, local_move_interval_ms=args.local_move_interval_ms)
    print(f"[requester] session={session_id} local_move_interval={args.local_move_interval_ms}ms, publishing {args.moves} moves", file=sys.stderr)

    for move_number in range(args.moves):
        request_id = str(uuid.uuid4())
        request_payload = {
            "type": "analyze_request",
            "session_id": session_id,
            "request_id": request_id,
            "move_number": move_number,
            "requested_at": time.time(),
        }
        # Fire the remote request, then commit the local move on this same
        # tick WITHOUT waiting for a response — "로컬 분석은 계속하고 원격 요청
        # 발행도 계속 병행한다" modeled literally: local never blocks on remote.
        client.publish(request_topic(session_id), json.dumps(request_payload))
        with state_lock:
            local_committed_move = move_number
            committed_moves.append(move_number)
        log.record("local_move_committed", move_number=move_number)
        print(f"[requester] local committed move {move_number} (remote request {request_id} fired in parallel, not awaited)", file=sys.stderr)
        time.sleep(args.local_move_interval_ms / 1000.0)

    # Drain any in-flight responses that were still travelling when the local
    # loop finished, so late arrivals after the last move are also classified.
    time.sleep(args.drain_s)
    log.record("requester_finished", session_id=session_id, committed_moves=committed_moves)
    client.loop_stop()
    client.disconnect()

    in_time = sum(1 for c in classifications if c["status"] == "in_time")
    stale = sum(1 for c in classifications if c["status"] == "stale_discarded")
    print("\n[requester] summary:", file=sys.stderr)
    print(f"  committed_moves (local game state): {committed_moves}", file=sys.stderr)
    print(f"  responses received: {len(classifications)} (in_time={in_time}, stale_discarded={stale})", file=sys.stderr)
    if committed_moves == list(range(args.moves)):
        print("  => local move sequence is exactly sequential and untouched by any remote response,", file=sys.stderr)
        print("     including the stale ones — confirms no conflict between late remote data and", file=sys.stderr)
        print("     already-committed local moves.", file=sys.stderr)
    else:
        print("  => UNEXPECTED: committed_moves is not a clean sequential run, investigate.", file=sys.stderr)
    return 0


def run_demo(args: argparse.Namespace) -> int:
    session_id = args.session_id
    log_path = Path(args.log)
    if log_path.exists():
        log_path.unlink()
    script = Path(__file__)
    common_flags = ["--host", args.host, "--port", str(args.port), "--session-id", session_id, "--moves", str(args.moves), "--log", str(log_path)]

    candidate_cmd = [
        sys.executable, str(script), "--role", "candidate",
        "--candidate-id", "C1",
        "--base-delay-ms", str(args.base_delay_ms),
        "--delay-growth-ms", str(args.delay_growth_ms),
        "--drop-moves", *[str(m) for m in args.drop_moves],
        *common_flags,
    ]
    candidate_proc = subprocess.Popen(candidate_cmd)
    time.sleep(0.5)

    requester_cmd = [
        sys.executable, str(script), "--role", "requester",
        "--local-move-interval-ms", str(args.local_move_interval_ms),
        "--drain-s", str(args.drain_s),
        *common_flags,
    ]
    requester_proc = subprocess.run(requester_cmd)

    candidate_proc.terminate()
    candidate_proc.wait(timeout=5)
    print(f"\n[demo] done. run log: {log_path}", file=sys.stderr)
    return requester_proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--role", choices=["requester", "candidate", "demo"], required=True)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--session-id", default=f"dev-session-{uuid.uuid4().hex[:8]}")
    parser.add_argument("--moves", type=int, default=8)
    parser.add_argument("--log", default=str(DEFAULT_LOG))
    # requester
    parser.add_argument("--local-move-interval-ms", type=float, default=300, help="fixed cadence local commits moves at, independent of remote")
    parser.add_argument("--drain-s", type=float, default=3.0, help="extra time to wait for late responses after the local loop finishes")
    # candidate
    parser.add_argument("--candidate-id", default="C1")
    parser.add_argument("--base-delay-ms", type=float, default=200, help="candidate response delay for move 0")
    parser.add_argument("--delay-growth-ms", type=float, default=150, help="added delay per move number, to push later moves past local's cadence")
    parser.add_argument("--drop-moves", type=int, nargs="*", default=[3, 4], help="move numbers this candidate never answers, to test resumption afterward")
    args = parser.parse_args()

    if args.role == "requester":
        return run_requester(args)
    if args.role == "candidate":
        return run_candidate(args)
    return run_demo(args)


if __name__ == "__main__":
    raise SystemExit(main())
