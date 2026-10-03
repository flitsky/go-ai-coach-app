"""KataGo GTP 프로세스 — 맥에서 그대로, 또는 폰에서 `adb run-as`로.

⚠️ **앱과 같은 호출 순서**를 지킨다(`KataGoGtpAnalysisClient`·`KataGoProtocolCommands`):
`kata-set-param maxVisits N` → `kata-set-param maxTime S`(상한이 없으면 값 없이 보내 지운다) →
`kata-search_analyze <B|W> [센티초]`. 후보 해석도 앱과 같다 — 같은 수는 방문이 많은 줄만 남기고, `order` 순 →
방문 많은 순으로 앞에서 `max_candidates`개, 점수 손해는 **맨 앞 후보의 scoreLead와의 차**(0 아래로 안 감),
루트 방문은 자식 방문의 합 **+ 1**(백로그 #203 — 루트 자신의 첫 방문).
"""
from __future__ import annotations

import queue
import re
import subprocess
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path

from .board import PASS, Position

INFO_BOUNDARY = re.compile(r"(?=\binfo move\s)")
WHITESPACE = re.compile(r"\s+")


@dataclass
class Candidate:
    move: str
    order: int
    visits: int | None
    winrate: float | None
    score_lead: float | None
    prior: float | None
    point_loss: float | None = None

    def to_json(self) -> dict:
        return {
            "move": self.move,
            "order": self.order,
            "visits": self.visits,
            "winrate": self.winrate,
            "scoreLead": self.score_lead,
            "prior": self.prior,
            "pointLoss": self.point_loss,
        }


@dataclass
class SearchResult:
    candidates: list[Candidate]
    root_visits: int | None
    elapsed_ms: float
    raw: str = ""

    @property
    def scored(self) -> list[Candidate]:
        return [c for c in self.candidates if c.point_loss is not None]

    @property
    def scored_plays(self) -> list[Candidate]:
        """앱이 버킷을 나누는 대상 — 점수가 붙은 **착수**(통과 제외, `AiMoveSelectionPolicy`)."""
        return [c for c in self.scored if c.move.lower() != PASS]


@dataclass
class RawNN:
    policy: dict[str, float]  # GTP 좌표 → 확률(둘 수 없는 자리는 빠진다)
    pass_prob: float | None
    fields: dict[str, float] = field(default_factory=dict)  # whiteWin·whiteLead·whiteScore 등
    elapsed_ms: float = 0.0


# --- 해석(앱과 같은 규칙) ----------------------------------------------------------------------


def _info_parts(response: str) -> list[str]:
    return [part.strip() for part in INFO_BOUNDARY.split(response) if part.strip().startswith("info move ")]


def _fields(tokens: list[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    index = 3
    while index < len(tokens) - 1:
        key = tokens[index]
        if key in ("pv", "info"):
            break
        out[key] = tokens[index + 1]
        index += 2
    return out


def _float(value: str | None) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def parse_candidates(response: str, max_candidates: int) -> list[Candidate]:
    """`KataGoAnalysisParser.parseCandidates`와 같다."""
    by_move: dict[str, Candidate] = {}
    for info in _info_parts(response):
        tokens = [t for t in WHITESPACE.split(info) if t]
        if len(tokens) < 3:
            continue
        fields = _fields(tokens)
        visits = int(fields["visits"]) if fields.get("visits", "").isdigit() else None
        candidate = Candidate(
            move=tokens[2],
            order=int(fields["order"]) if fields.get("order", "").lstrip("-").isdigit() else 2**31 - 1,
            visits=visits,
            winrate=_float(fields.get("winrate")),
            score_lead=_float(fields.get("scoreLead")),
            prior=_float(fields.get("prior")),
        )
        previous = by_move.get(candidate.move)
        if previous is None or (candidate.visits or -1) >= (previous.visits or -1):
            by_move[candidate.move] = candidate
    ordered = sorted(by_move.values(), key=lambda c: (c.order, -(c.visits or -1)))
    return ordered[:max_candidates]


def attach_point_loss(candidates: list[Candidate]) -> list[Candidate]:
    """`KataGoAnalysisParser.attachPointLoss`와 같다 — 맨 앞 후보 기준, 0 아래로 내리지 않는다."""
    top = next((c.score_lead for c in candidates if c.score_lead is not None), None)
    if top is None:
        return candidates
    for c in candidates:
        if c.score_lead is None:
            continue
        raw = top - c.score_lead
        c.point_loss = 0.0 if abs(raw) < 1e-6 else max(0.0, raw)
    return candidates


def root_visits_estimate(response: str) -> int | None:
    """`KataGoAnalysisParser.parseRootVisitsEstimate`와 같다 — 자식 방문 합 + 루트 자신의 1방문."""
    visits_by_move: dict[str, int] = {}
    for info in _info_parts(response):
        tokens = [t for t in WHITESPACE.split(info) if t]
        if len(tokens) < 5:
            continue
        fields = _fields(tokens)
        if not fields.get("visits", "").isdigit():
            continue
        visits = int(fields["visits"])
        if visits >= visits_by_move.get(tokens[2], -1):
            visits_by_move[tokens[2]] = visits
    if not visits_by_move:
        return None
    return sum(visits_by_move.values()) + 1


def parse_raw_nn(response: str, size: int) -> RawNN:
    """`kata-raw-nn 0` / `kata-raw-human-nn 0`의 출력. 정책 격자는 위(행 `size`)부터, 둘 수 없는 자리는 NAN."""
    lines = [line.strip() for line in response.splitlines() if line.strip()]
    fields: dict[str, float] = {}
    policy: dict[str, float] = {}
    pass_prob: float | None = None
    from .board import vertex

    index = 0
    while index < len(lines):
        tokens = lines[index].split()
        if tokens[0] == "policy" and len(tokens) == 1:
            for row in range(size):
                values = lines[index + 1 + row].split()
                for col, value in enumerate(values):
                    p = _float(value)
                    if p is not None and p == p:  # NaN 제외
                        policy[vertex(size, row, col)] = p
            index += 1 + size
            continue
        if tokens[0] == "policyPass" and len(tokens) >= 2:
            pass_prob = _float(tokens[1])
        elif len(tokens) == 2:
            value = _float(tokens[1])
            if value is not None:
                fields[tokens[0]] = value
        index += 1
    return RawNN(policy=policy, pass_prob=pass_prob, fields=fields)


# --- 프로세스 ----------------------------------------------------------------------------------


@dataclass
class AdbTarget:
    """폰의 앱 안 엔진을 그대로 쓴다 — 디버그(디버깅 가능) 빌드여야 `run-as`가 된다."""

    serial: str
    package: str = "com.zenit9hub.ai.baduk"
    executable: str | None = None  # None이면 `dumpsys package`로 찾는다
    katago_dir: str = "files/katago"

    def resolve_executable(self) -> str:
        if self.executable:
            return self.executable
        output = subprocess.check_output(["adb", "-s", self.serial, "shell", "dumpsys", "package", self.package], text=True)
        match = re.search(r"legacyNativeLibraryDir=([^\r\n]+)", output)
        if not match:
            raise RuntimeError(f"{self.package}의 legacyNativeLibraryDir를 못 찾았다 — 앱이 설치돼 있나?")
        self.executable = f"{match.group(1).strip()}/arm64/libkatago.so"
        return self.executable

    def list_files(self) -> list[str]:
        output = subprocess.check_output(
            ["adb", "-s", self.serial, "shell", "run-as", self.package, "ls", self.katago_dir], text=True
        )
        return output.split()

    def model_path(self) -> str:
        files = self.list_files()
        for name in ("model.bin", "model.bin.gz"):
            if name in files:
                return f"{self.katago_dir}/{name}"
        raise RuntimeError(f"폰의 {self.katago_dir}에 모델이 없다: {files} — 앱을 한 번 열거나 `make seed-engine`")

    def wrap(self, argv: list[str]) -> list[str]:
        return ["adb", "-s", self.serial, "shell", "run-as", self.package] + argv


def build_argv(
    *,
    mode: str,
    katago: str,
    model: str,
    config: str,
    overrides: dict[str, str],
    human_model: str | None = None,
    adb: AdbTarget | None = None,
) -> list[str]:
    argv = [katago, mode, "-model", model]
    if human_model:
        argv += ["-human-model", human_model]
    argv += ["-config", config]
    if overrides:
        argv += ["-override-config", ",".join(f"{k}={v}" for k, v in overrides.items())]
    return adb.wrap(argv) if adb else argv


class _LineReader:
    """자식 프로세스의 stdout을 줄 단위로 큐에 담는다 — 읽기에 시간 제한을 걸려고."""

    def __init__(self, stream) -> None:
        self.queue: queue.Queue[str | None] = queue.Queue()
        self.thread = threading.Thread(target=self._pump, args=(stream,), daemon=True)
        self.thread.start()

    def _pump(self, stream) -> None:
        for line in iter(stream.readline, ""):
            self.queue.put(line)
        self.queue.put(None)

    def readline(self, timeout: float | None) -> str | None:
        return self.queue.get(timeout=timeout)


class GtpEngine:
    def __init__(self, argv: list[str], *, label: str = "gtp", startup_timeout: float = 120.0) -> None:
        self.argv = argv
        self.label = label
        self.process = subprocess.Popen(
            argv,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )
        self._reader = _LineReader(self.process.stdout)
        self.version = self.send("version", timeout=startup_timeout)

    # 기본 명령 --------------------------------------------------------------------------------

    def send(self, command: str, timeout: float | None = 300.0) -> str:
        assert self.process.stdin is not None
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()
        lines: list[str] = []
        while True:
            try:
                line = self._reader.readline(timeout)
            except queue.Empty:
                raise TimeoutError(f"{self.label}: `{command}` 응답이 {timeout}초 안에 없다") from None
            if line is None:
                raise RuntimeError(f"{self.label}: 프로세스가 `{command}`를 기다리는 중에 끝났다")
            stripped = line.rstrip("\n")
            if stripped == "":
                if lines:
                    break
                continue
            lines.append(stripped)
        first = lines[0]
        if first.startswith("?"):
            raise RuntimeError(f"{self.label}: `{command}` 실패 — {' '.join(lines)}")
        if not first.startswith("="):
            raise RuntimeError(f"{self.label}: `{command}`의 응답이 이상하다 — {' '.join(lines)}")
        return "\n".join(lines)[1:].strip()

    def close(self) -> None:
        try:
            self.send("quit", timeout=5)
        except Exception:
            pass
        self.process.terminate()
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()

    def __enter__(self) -> "GtpEngine":
        return self

    def __exit__(self, *_exc) -> None:
        self.close()

    def set_param(self, key: str, value: str | None = None) -> None:
        self.send(f"kata-set-param {key}" + (f" {value}" if value is not None else ""))

    def clear_cache(self) -> None:
        self.send("clear_cache")

    # 국면 ------------------------------------------------------------------------------------

    def setup(self, position: Position) -> None:
        """앱이 대국을 맞출 때와 같은 순서(`boardsize` → `komi` → `kata-set-rules` → `clear_board` → 수순)."""
        self.send(f"boardsize {position.size}")
        self.send(f"komi {position.komi}")
        self.send(f"kata-set-rules {position.rules}")
        self.send("clear_board")
        for color, move in position.moves:
            self.send(f"play {color} {move}")

    def play(self, color: str, move: str) -> None:
        self.send(f"play {color} {move}")

    def genmove(self, color: str, timeout: float | None = 600.0) -> str:
        return self.send(f"genmove {color}", timeout=timeout)

    # 탐색 ------------------------------------------------------------------------------------

    def search_analyze(
        self,
        player: str,
        *,
        visits: int,
        time_ms: int | None,
        max_candidates: int,
        timeout: float | None = 600.0,
    ) -> SearchResult:
        """앱의 GTP 빠른 경로 한 번(`KataGoGtpAnalysisClient.searchWithGtp`)."""
        self.set_param("maxVisits", str(visits))
        if time_ms is None:
            self.set_param("maxTime")
            command = f"kata-search_analyze {player}"
        else:
            self.set_param("maxTime", f"{max(time_ms / 1000.0, 0.001)}")
            command = f"kata-search_analyze {player} {max((time_ms + 9) // 10, 1)}"
        start = time.perf_counter()
        response = self.send(command, timeout=timeout)
        elapsed = (time.perf_counter() - start) * 1000.0
        candidates = attach_point_loss(parse_candidates(response, max_candidates))
        return SearchResult(candidates=candidates, root_visits=root_visits_estimate(response), elapsed_ms=elapsed, raw=response)

    def raw_nn(self, size: int, *, human: bool = False) -> RawNN:
        start = time.perf_counter()
        response = self.send("kata-raw-human-nn 0" if human else "kata-raw-nn 0")
        result = parse_raw_nn(response, size)
        result.elapsed_ms = (time.perf_counter() - start) * 1000.0
        return result


def local_gtp(
    *,
    katago: Path,
    model: Path,
    config: Path,
    overrides: dict[str, str],
    human_model: Path | None = None,
    label: str = "gtp",
) -> GtpEngine:
    return GtpEngine(
        build_argv(
            mode="gtp",
            katago=str(katago),
            model=str(model),
            config=str(config),
            overrides=overrides,
            human_model=str(human_model) if human_model else None,
        ),
        label=label,
    )


def phone_gtp(adb: AdbTarget, *, overrides: dict[str, str], label: str = "phone-gtp") -> GtpEngine:
    """앱이 기기에 둔 모델·cfg를 그대로 쓴다. 로그·홈은 앱과 같은 폴더."""
    merged = {**overrides, "logDir": f"{adb.katago_dir}/logs", "homeDataDir": f"{adb.katago_dir}/home"}
    return GtpEngine(
        build_argv(
            mode="gtp",
            katago=adb.resolve_executable(),
            model=adb.model_path(),
            config=f"{adb.katago_dir}/gtp_learning.cfg",
            overrides=merged,
            adb=adb,
        ),
        label=label,
        startup_timeout=300.0,
    )
