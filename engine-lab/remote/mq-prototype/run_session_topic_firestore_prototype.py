#!/usr/bin/env python3
"""Firestore session-topic prototype — Stage F kickoff, section 6 item 1.

Same 3-role scenario as `run_session_topic_mqtt_prototype.py` (1 requester +
N remote candidates), but over Firestore realtime listeners instead of an
MQTT broker, to let section 4's MQTT-vs-Firestore comparison run on real
code instead of guesses.

Talks to the **Firestore emulator**, not the app's real
`project-baduk-hanpan` project — this prototype never touches production
data or needs real credentials. Start it first:

    npx firebase-tools emulators:start --only firestore \\
        --project demo-go-ai-coach \\
        --config scripts/remote-engine-mq-prototype/firebase.json

`--project demo-go-ai-coach` (a `demo-` prefixed id) is a Firestore-emulator
convention that skips real GCP auth entirely, unlike the app's real
`project-baduk-hanpan` id.

Collections (per session, so a real port to Firestore-as-topic would create
one of these per game, matching the MQTT prototype's per-session topic):
  - `dev_sessions/{session_id}/requests`  — one doc per analyze request
  - `dev_sessions/{session_id}/responses` — one doc per candidate response

Firestore's own `SERVER_TIMESTAMP` (not the client clock) is what a real
reward/audit design would trust, so both roles use it for the fields that
matter for ranking.

Usage:
    python3 scripts/remote-engine-mq-prototype/run_session_topic_firestore_prototype.py --role demo
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).parent))
from _prototype_common import FakeAnalysisResult, new_run_log, rank_responses, result_to_json  # noqa: E402

DEFAULT_LOG = Path(__file__).parent / "runs" / "firestore-session-topic.jsonl"
DEFAULT_EMULATOR_HOST = "127.0.0.1:8080"
DEFAULT_PROJECT = "demo-go-ai-coach"


def get_client(project: str, emulator_host: str) -> Any:
    os.environ["FIRESTORE_EMULATOR_HOST"] = emulator_host
    from google.cloud import firestore  # deferred: needs FIRESTORE_EMULATOR_HOST set first

    return firestore.Client(project=project)


def requests_collection(db: Any, session_id: str) -> Any:
    return db.collection("dev_sessions").document(session_id).collection("requests")


def responses_collection(db: Any, session_id: str) -> Any:
    return db.collection("dev_sessions").document(session_id).collection("responses")


def run_requester(args: argparse.Namespace) -> int:
    from google.cloud import firestore

    log = new_run_log(Path(args.log))
    db = get_client(args.project, args.emulator_host)
    session_id = args.session_id
    responses_ref = responses_collection(db, session_id)
    log.record("requester_started", session_id=session_id, requests_collection=f"dev_sessions/{session_id}/requests", responses_collection=f"dev_sessions/{session_id}/responses")
    print(f"[requester] session={session_id} watching dev_sessions/{session_id}/responses", file=sys.stderr)

    for move_number in range(args.moves):
        request_id = str(uuid.uuid4())
        collected: list[dict[str, Any]] = []

        def on_snapshot(col_snapshot: Any, changes: Any, _read_time: Any, _request_id: str = request_id, _collected: list = collected) -> None:
            for change in changes:
                if change.type.name != "ADDED":
                    continue
                doc = change.document.to_dict()
                if doc.get("request_id") != _request_id:
                    continue
                responded_at = doc.get("responded_at")
                _collected.append({**doc, "received_at": time.monotonic(), "server_responded_at": responded_at.timestamp() if responded_at else None})

        watch = responses_ref.on_snapshot(on_snapshot)

        request_payload = {
            "request_id": request_id,
            "move_number": move_number,
            "requested_at": firestore.SERVER_TIMESTAMP,
        }
        requests_collection(db, session_id).document(request_id).set(request_payload)
        log.record("request_published", session_id=session_id, request_id=request_id, move_number=move_number)
        print(f"[requester] move {move_number}: published request {request_id}", file=sys.stderr)

        time.sleep(args.timeout_s)
        watch.unsubscribe()

        responses = rank_responses(collected)
        log.record("responses_ranked", request_id=request_id, move_number=move_number, responses=[{k: v for k, v in r.items() if k != "requested_at"} for r in responses])
        if responses:
            for response in responses:
                print(f"[requester] move {move_number}: rank {response['rank']} candidate={response['candidate_id']} reward_points={response['reward_points']}", file=sys.stderr)
        else:
            print(f"[requester] move {move_number}: NO responses within {args.timeout_s}s (remote unreachable)", file=sys.stderr)

    log.record("requester_finished", session_id=session_id)
    return 0


def run_candidate(args: argparse.Namespace) -> int:
    from google.cloud import firestore

    log = new_run_log(Path(args.log))
    db = get_client(args.project, args.emulator_host)
    session_id = args.session_id
    candidate_id = args.candidate_id
    requests_ref = requests_collection(db, session_id)
    responses_ref = responses_collection(db, session_id)
    seen_request_ids: set[str] = set()

    def on_snapshot(col_snapshot: Any, changes: Any, _read_time: Any) -> None:
        for change in changes:
            if change.type.name != "ADDED":
                continue
            doc = change.document.to_dict()
            request_id = doc.get("request_id")
            if request_id in seen_request_ids:
                continue
            seen_request_ids.add(request_id)
            if args.never_respond:
                log.record("request_ignored_by_design", candidate_id=candidate_id, request_id=request_id)
                print(f"[candidate {candidate_id}] ignoring request {request_id} (--never-respond)", file=sys.stderr)
                continue
            time.sleep(args.delay_ms / 1000.0)
            result = FakeAnalysisResult.sample()
            response_payload = {
                "request_id": request_id,
                "candidate_id": candidate_id,
                "responded_at": firestore.SERVER_TIMESTAMP,
                **result_to_json(result),
            }
            responses_ref.document(f"{request_id}-{candidate_id}").set(response_payload)
            log.record("response_published", session_id=session_id, request_id=request_id, candidate_id=candidate_id)
            print(f"[candidate {candidate_id}] answered request {request_id} after {args.delay_ms:.0f}ms", file=sys.stderr)

    watch = requests_ref.on_snapshot(on_snapshot)
    print(f"[candidate {candidate_id}] session={session_id} watching dev_sessions/{session_id}/requests", file=sys.stderr)

    try:
        time.sleep(args.moves * (args.timeout_s + 0.5) + 1)
    except KeyboardInterrupt:
        pass
    watch.unsubscribe()
    return 0


def run_demo(args: argparse.Namespace) -> int:
    session_id = args.session_id
    log_path = Path(args.log)
    if log_path.exists():
        log_path.unlink()
    script = Path(__file__)
    common_flags = ["--project", args.project, "--emulator-host", args.emulator_host, "--session-id", session_id, "--moves", str(args.moves), "--timeout-s", str(args.timeout_s), "--log", str(log_path)]

    candidate_procs = []
    for i in range(args.candidates):
        delay_ms = args.candidate_delay_ms[i] if i < len(args.candidate_delay_ms) else 500
        never_respond = i in args.never_respond_indices
        cmd = [sys.executable, str(script), "--role", "candidate", "--candidate-id", f"C{i+1}", "--delay-ms", str(delay_ms), *common_flags]
        if never_respond:
            cmd.append("--never-respond")
        candidate_procs.append(subprocess.Popen(cmd))
    time.sleep(1.0)  # let candidate listeners attach before the requester starts publishing

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
    parser.add_argument("--project", default=DEFAULT_PROJECT)
    parser.add_argument("--emulator-host", default=DEFAULT_EMULATOR_HOST)
    parser.add_argument("--session-id", default=f"dev-session-{uuid.uuid4().hex[:8]}")
    parser.add_argument("--moves", type=int, default=3)
    parser.add_argument("--timeout-s", type=float, default=5.0)
    parser.add_argument("--log", default=str(DEFAULT_LOG))
    parser.add_argument("--candidate-id", default="C1")
    parser.add_argument("--delay-ms", type=float, default=500)
    parser.add_argument("--never-respond", action="store_true")
    parser.add_argument("--candidates", type=int, default=2)
    parser.add_argument("--candidate-delay-ms", type=float, nargs="*", default=[300, 800])
    parser.add_argument("--never-respond-indices", type=int, nargs="*", default=[])
    args = parser.parse_args()

    if args.role == "requester":
        return run_requester(args)
    if args.role == "candidate":
        return run_candidate(args)
    return run_demo(args)


if __name__ == "__main__":
    raise SystemExit(main())
