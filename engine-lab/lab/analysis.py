"""KataGo JSON 분석 엔진 — 기준 평가(센 탐색)와 사람 정책 받기에 쓴다.

⚠️ 앱의 분석 cfg(`analysis_learning.cfg`)는 `reportAnalysisWinratesAs = BLACK`이다 — 점수·승률이 **흑 기준**이다.
[`to_mover`]로 둘 차례인 쪽 기준으로 바꿔 쓴다. (GTP 경로는 기본값 SIDETOMOVE라 이미 둘 차례 기준이다.)
⚠️ Homebrew 1.16.4(Metal)에서 JSON `clear_cache`를 보내면 SIGSEGV로 죽는다(옛 `run-katago-level-match.py:140-147`) —
이 클라이언트는 그 명령을 보내지 않는다.
"""
from __future__ import annotations

import itertools
import json
import queue
import subprocess
from pathlib import Path

from .board import BLACK, Position
from .gtp import AdbTarget, _LineReader, build_argv


class AnalysisEngine:
    def __init__(self, argv: list[str], *, label: str = "analysis", startup_timeout: float = 180.0) -> None:
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
        self._ids = itertools.count(1)
        self._pending: dict[str, dict] = {}
        self.version = self._query_version(startup_timeout)

    def _query_version(self, timeout: float) -> str:
        qid = f"v{next(self._ids)}"
        self._write({"id": qid, "action": "query_version"})
        data = self._wait(qid, timeout)
        return f"{data.get('version')} {data.get('git_hash', '')}".strip()

    def _write(self, payload: dict) -> None:
        assert self.process.stdin is not None
        self.process.stdin.write(json.dumps(payload) + "\n")
        self.process.stdin.flush()

    def _wait(self, qid: str, timeout: float | None) -> dict:
        while qid not in self._pending:
            try:
                line = self._reader.readline(timeout)
            except queue.Empty:
                raise TimeoutError(f"{self.label}: {qid} 응답이 {timeout}초 안에 없다") from None
            if line is None:
                raise RuntimeError(f"{self.label}: 프로세스가 끝났다({qid} 대기 중)")
            line = line.strip()
            if not line.startswith("{"):
                continue
            data = json.loads(line)
            if "warning" in data and "id" not in data:
                continue
            if data.get("isDuringSearch"):
                continue
            self._pending[str(data.get("id"))] = data
        data = self._pending.pop(qid)
        if "error" in data:
            raise RuntimeError(f"{self.label}: {qid} 실패 — {data['error']} ({data.get('field', '')})")
        return data

    @staticmethod
    def build_query(
        position: Position,
        *,
        max_visits: int,
        allow_moves: list[str] | None = None,
        include_policy: bool = False,
        override_settings: dict | None = None,
    ) -> dict:
        query: dict = {
            "moves": [[color, move] for color, move in position.moves],
            "rules": position.rules,
            "komi": position.komi,
            "boardXSize": position.size,
            "boardYSize": position.size,
            "maxVisits": max_visits,
        }
        if allow_moves:
            query["allowMoves"] = [{"player": position.next_player, "moves": allow_moves, "untilDepth": 1}]
        if include_policy:
            query["includePolicy"] = True
        if override_settings:
            query["overrideSettings"] = override_settings
        return query

    def query(self, position: Position, timeout: float | None = 600.0, **kwargs) -> dict:
        return self.query_many([self.build_query(position, **kwargs)], timeout=timeout)[0]

    def query_many(self, queries: list[dict], timeout: float | None = 600.0) -> list[dict]:
        """한꺼번에 보내고 모은다 — `numAnalysisThreads`만큼 동시에 돈다."""
        ids = []
        for query in queries:
            qid = f"q{next(self._ids)}"
            ids.append(qid)
            self._write({**query, "id": qid})
        return [self._wait(qid, timeout) for qid in ids]

    def close(self) -> None:
        try:
            assert self.process.stdin is not None
            self.process.stdin.close()
        except Exception:
            pass
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.kill()

    def __enter__(self) -> "AnalysisEngine":
        return self

    def __exit__(self, *_exc) -> None:
        self.close()


def to_mover(position: Position, black_value: float) -> float:
    """흑 기준 점수를 둘 차례인 쪽 기준으로."""
    return black_value if position.next_player == BLACK else -black_value


def local_analysis(
    *,
    katago: Path,
    model: Path,
    config: Path,
    overrides: dict[str, str],
    human_model: Path | None = None,
    label: str = "analysis",
) -> AnalysisEngine:
    return AnalysisEngine(
        build_argv(
            mode="analysis",
            katago=str(katago),
            model=str(model),
            config=str(config),
            overrides=overrides,
            human_model=str(human_model) if human_model else None,
        ),
        label=label,
    )


def phone_analysis(adb: AdbTarget, *, overrides: dict[str, str], label: str = "phone-analysis") -> AnalysisEngine:
    merged = {**overrides, "logDir": f"{adb.katago_dir}/logs", "homeDataDir": f"{adb.katago_dir}/home"}
    return AnalysisEngine(
        build_argv(
            mode="analysis",
            katago=adb.resolve_executable(),
            model=adb.model_path(),
            config=f"{adb.katago_dir}/analysis_learning.cfg",
            overrides=merged,
            adb=adb,
        ),
        label=label,
        startup_timeout=300.0,
    )
