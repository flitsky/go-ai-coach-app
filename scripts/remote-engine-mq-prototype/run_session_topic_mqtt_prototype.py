#!/usr/bin/env python3
"""MQTT session-topic prototype — Stage F kickoff, section 6 item 1.

Plays one of two roles against a real MQTT broker (local mosquitto by
default; any broker works, including a cloud one, by passing --host/--port):

  - `--role requester`: publishes N fake analyze requests on a session-scoped
    topic, collects candidate responses within a per-request timeout window,
    and ranks them by arrival time (section 2's draft 5/3/2/1 reward idea).
  - `--role candidate`: subscribes to the request topic, waits a randomized
    "thinking" delay, then publishes a fake analysis response.
  - `--role demo`: spawns one requester + N candidate subprocesses locally
    and waits for the run to finish — the one-command way to check "does
    this actually work end to end" without opening multiple terminals.

This does NOT use real KataGo output (see item 2's prototype for that) —
the point here is only to prove the transport mechanics: does a
session-scoped MQTT topic actually deliver request -> N responses with
usable timestamps, with no topic "creation" step needed (MQTT topics are
implicit in the publish/subscribe pattern itself, unlike Firestore
collections which show up once written).

Requires a broker. Quickest local one:
    brew install mosquitto  # already done as part of this prototype
    /opt/homebrew/opt/mosquitto/sbin/mosquitto -c \\
        scripts/remote-engine-mq-prototype/mosquitto-local.conf -v

Usage:
    python3 scripts/remote-engine-mq-prototype/run_session_topic_mqtt_prototype.py --role demo
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).parent))
from _prototype_common import FakeAnalysisResult, new_run_log, rank_responses, result_to_json  # noqa: E402

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print(
        "paho-mqtt not installed. Activate the prototype venv:\n"
        "  source scripts/remote-engine-mq-prototype/../.mq-prototype-venv/bin/activate",
        file=sys.stderr,
    )
    raise

DEFAULT_LOG = Path(__file__).parent / "runs" / "mqtt-session-topic.jsonl"


def request_topic(session_id: str) -> str:
    return f"go-ai-coach/dev/{session_id}/request"


def response_topic(session_id: str) -> str:
    return f"go-ai-coach/dev/{session_id}/response"


def connect(host: str, port: int, client_id: str) -> "mqtt.Client":
    client = mqtt.Client(client_id=client_id, callback_api_version=mqtt.CallbackAPIVersion.VERSION2)
    client.connect(host, port, keepalive=30)
    client.loop_start()
    return client


def run_requester(args: argparse.Namespace) -> int:
    log = new_run_log(Path(args.log))
    session_id = args.session_id
    pending: dict[str, list[dict[str, Any]]] = {}

    def on_message(_client: Any, _userdata: Any, message: Any) -> None:
        payload = json.loads(message.payload.decode("utf-8"))
        if payload.get("type") != "analyze_response" or payload.get("session_id") != session_id:
            return
        request_id = payload["request_id"]
        pending.setdefault(request_id, []).append({**payload, "received_at": time.monotonic()})

    client = connect(args.host, args.port, client_id=f"requester-{session_id}")
    client.on_message = on_message
    client.subscribe(response_topic(session_id))
    log.record("requester_started", session_id=session_id, request_topic=request_topic(session_id), response_topic=response_topic(session_id))
    print(f"[requester] session={session_id} subscribed to {response_topic(session_id)}", file=sys.stderr)

    for move_number in range(args.moves):
        request_id = str(uuid.uuid4())
        pending[request_id] = []
        request_payload = {
            "type": "analyze_request",
            "session_id": session_id,
            "request_id": request_id,
            "move_number": move_number,
            "requested_at": time.time(),
        }
        client.publish(request_topic(session_id), json.dumps(request_payload))
        log.record("request_published", **request_payload)
        print(f"[requester] move {move_number}: published request {request_id}", file=sys.stderr)

        deadline = time.monotonic() + args.timeout_s
        while time.monotonic() < deadline:
            time.sleep(0.05)
        responses = rank_responses(pending[request_id])
        log.record("responses_ranked", request_id=request_id, move_number=move_number, responses=responses)
        if responses:
            for response in responses:
                print(
                    f"[requester] move {move_number}: rank {response['rank']} "
                    f"candidate={response['candidate_id']} "
                    f"latency_ms={(response['received_at'] - deadline + args.timeout_s) * 1000:.0f} "
                    f"reward_points={response['reward_points']}",
                    file=sys.stderr,
                )
        else:
            print(f"[requester] move {move_number}: NO responses within {args.timeout_s}s (remote unreachable)", file=sys.stderr)

    log.record("requester_finished", session_id=session_id)
    client.loop_stop()
    client.disconnect()
    return 0


def run_candidate(args: argparse.Namespace) -> int:
    log = new_run_log(Path(args.log))
    session_id = args.session_id
    candidate_id = args.candidate_id
    client = connect(args.host, args.port, client_id=f"candidate-{candidate_id}-{session_id}")

    def on_message(_client: Any, _userdata: Any, message: Any) -> None:
        payload = json.loads(message.payload.decode("utf-8"))
        if payload.get("type") != "analyze_request" or payload.get("session_id") != session_id:
            return
        if args.never_respond:
            log.record("request_ignored_by_design", candidate_id=candidate_id, request_id=payload["request_id"])
            print(f"[candidate {candidate_id}] ignoring request {payload['request_id']} (--never-respond)", file=sys.stderr)
            return
        delay_s = args.delay_ms / 1000.0
        time.sleep(delay_s)
        result = FakeAnalysisResult.sample()
        response_payload = {
            "type": "analyze_response",
            "session_id": session_id,
            "request_id": payload["request_id"],
            "candidate_id": candidate_id,
            "responded_at": time.time(),
            **result_to_json(result),
        }
        client.publish(response_topic(session_id), json.dumps(response_payload))
        log.record("response_published", **response_payload)
        print(f"[candidate {candidate_id}] answered request {payload['request_id']} after {delay_s * 1000:.0f}ms", file=sys.stderr)

    client.on_message = on_message
    client.subscribe(request_topic(session_id))
    print(f"[candidate {candidate_id}] session={session_id} subscribed to {request_topic(session_id)}", file=sys.stderr)

    try:
        time.sleep(args.moves * (args.timeout_s + 0.5) + 1)
    except KeyboardInterrupt:
        pass
    client.loop_stop()
    client.disconnect()
    return 0


def run_demo(args: argparse.Namespace) -> int:
    session_id = args.session_id
    log_path = Path(args.log)
    if log_path.exists():
        log_path.unlink()
    script = Path(__file__)
    common_flags = ["--host", args.host, "--port", str(args.port), "--session-id", session_id, "--moves", str(args.moves), "--timeout-s", str(args.timeout_s), "--log", str(log_path)]

    candidate_procs = []
    for i in range(args.candidates):
        delay_ms = args.candidate_delay_ms[i] if i < len(args.candidate_delay_ms) else 500
        never_respond = i in args.never_respond_indices
        cmd = [sys.executable, str(script), "--role", "candidate", "--candidate-id", f"C{i+1}", "--delay-ms", str(delay_ms), *common_flags]
        if never_respond:
            cmd.append("--never-respond")
        candidate_procs.append(subprocess.Popen(cmd))
    time.sleep(0.5)  # let candidates subscribe before the requester starts publishing

    requester_cmd = [sys.executable, str(script), "--role", "requester", *common_flags]
    requester_proc = subprocess.run(requester_cmd)

    for proc in candidate_procs:
        proc.terminate()
        proc.wait(timeout=5)

    print(f"\n[demo] done. run log: {log_path}", file=sys.stderr)
    return requester_proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--role", choices=["requester", "candidate", "demo"], required=True)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--session-id", default=f"dev-session-{uuid.uuid4().hex[:8]}")
    parser.add_argument("--moves", type=int, default=3, help="number of fake analyze requests to send (requester/demo)")
    parser.add_argument("--timeout-s", type=float, default=5.0, help="how long the requester waits for responses per move")
    parser.add_argument("--log", default=str(DEFAULT_LOG))
    # candidate-only
    parser.add_argument("--candidate-id", default="C1")
    parser.add_argument("--delay-ms", type=float, default=500)
    parser.add_argument("--never-respond", action="store_true", help="subscribe but never answer, to simulate an unreachable candidate")
    # demo-only
    parser.add_argument("--candidates", type=int, default=2)
    parser.add_argument("--candidate-delay-ms", type=float, nargs="*", default=[300, 800], help="per-candidate delay, demo role only")
    parser.add_argument("--never-respond-indices", type=int, nargs="*", default=[], help="0-based candidate indices that should never respond, demo role only")
    args = parser.parse_args()

    if args.role == "requester":
        return run_requester(args)
    if args.role == "candidate":
        return run_candidate(args)
    return run_demo(args)


if __name__ == "__main__":
    raise SystemExit(main())
