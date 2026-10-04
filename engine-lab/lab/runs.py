"""실험 한 번의 결과 폴더 — `experiments/<실험>/runs/<YYYYMMDD-HHMM>-<기계>-<이름>/`.

넣는 것: `run.json`(무엇으로 쟀나 — 인자·KataGo·모델 해시·앱 커밋·기계), `samples.jsonl`(원자료, 한 줄 = 표본 하나),
`summary.json`·`summary.md`(요약). 사용자 결정(2026-10-03)으로 **원자료까지 전부 커밋**한다.
"""
from __future__ import annotations

import datetime as _dt
import hashlib
import json
import os
import platform
import subprocess
import sys
from pathlib import Path

from . import paths

_SHA_CACHE: dict[str, str] = {}


def sha256(path: Path) -> str | None:
    if not path.exists():
        return None
    key = f"{path}:{path.stat().st_size}:{path.stat().st_mtime_ns}"
    if key not in _SHA_CACHE:
        h = hashlib.sha256()
        with path.open("rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
        _SHA_CACHE[key] = h.hexdigest()
    return _SHA_CACHE[key]


def git_commit() -> str | None:
    try:
        commit = subprocess.check_output(["git", "-C", str(paths.REPO_ROOT), "rev-parse", "--short", "HEAD"], text=True).strip()
        dirty = subprocess.check_output(["git", "-C", str(paths.REPO_ROOT), "status", "--porcelain", "--", "engine-lab"], text=True).strip()
        return commit + ("+lab-dirty" if dirty else "")
    except Exception:
        return None


def host_tag() -> str:
    """결과 폴더 이름에 붙는 기계 표시. ⚠️ **호스트 이름을 쓰지 않는다** — 맥 호스트 이름엔 사람 이름이 들어 있고(「○○의 MacBook Pro」)
    결과는 공개 저장소에 커밋된다. 기본은 `darwin-arm64` 같은 일반 표시, 바꾸려면 `ENGINE_LAB_HOST`."""
    tag = os.environ.get("ENGINE_LAB_HOST") or f"{platform.system()}-{platform.machine()}"
    return tag.lower().replace(" ", "-")


class RunDir:
    """`resume=True`면 이름이 고정된 폴더(`runs/<label>/`)를 쓰고, 이미 있으면 **이어서** 쓴다 — 끊긴 실행을 다시 돌려도
    `samples.jsonl`에 이어 붙고 `run.json`에 재개 시각이 쌓인다. 무엇이 끝났는지는 실험이 `completed_samples()`로 읽어 건너뛴다."""

    def __init__(
        self,
        experiment: str,
        label: str,
        args: dict,
        *,
        engine_versions: dict | None = None,
        resume: bool = False,
    ) -> None:
        stamp = _dt.datetime.now().strftime("%Y%m%d-%H%M")
        safe_label = "".join(ch if ch.isalnum() or ch in "-_." else "-" for ch in label)
        name = safe_label if resume else f"{stamp}-{host_tag()}-{safe_label}"
        self.path = paths.experiments_dir() / experiment / "runs" / name
        existing = resume and (self.path / "run.json").exists()
        self.path.mkdir(parents=True, exist_ok=resume)
        self.samples_path = self.path / "samples.jsonl"
        if existing:
            self.meta = json.loads((self.path / "run.json").read_text(encoding="utf-8"))
            self.meta.setdefault("resumedAt", []).append(_dt.datetime.now().isoformat(timespec="seconds"))
            self.meta["args"] = args
            self._samples = self.samples_path.open("a", encoding="utf-8")
            self._write_meta()
            return
        self._samples = self.samples_path.open("a", encoding="utf-8")
        self.meta = {
            "experiment": experiment,
            "label": label,
            "startedAt": _dt.datetime.now().isoformat(timespec="seconds"),
            "args": args,
            "appCommit": git_commit(),
            "python": sys.version.split()[0],
            "machine": f"{platform.system()} {platform.machine()} {host_tag()}",
            "katago": str(paths.katago_binary()),
            "engineVersions": engine_versions or {},
            "models": {
                "main": {"path": str(paths.main_model()), "sha256": sha256(paths.main_model())},
                "human": {"path": str(paths.human_model()), "sha256": sha256(paths.human_model())},
            },
            "configs": {
                "gtp": {"path": str(paths.gtp_config()), "sha256": sha256(paths.gtp_config())},
                "analysis": {"path": str(paths.analysis_config()), "sha256": sha256(paths.analysis_config())},
            },
        }
        self._write_meta()

    def _write_meta(self) -> None:
        (self.path / "run.json").write_text(json.dumps(self.meta, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    def set_engine_versions(self, versions: dict) -> None:
        self.meta["engineVersions"] = versions
        self._write_meta()

    def sample(self, row: dict) -> None:
        self._samples.write(json.dumps(row, ensure_ascii=False) + "\n")
        self._samples.flush()

    def completed_samples(self) -> list[dict]:
        """이미 저장된 표본(이어하기에서 건너뛸 것). 마지막 줄이 쓰다 끊겨 깨졌으면 그 줄만 버린다."""
        if not self.samples_path.exists():
            return []
        rows = []
        for line in self.samples_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                continue
        return rows

    def write_summary(self, summary: dict, markdown: str) -> None:
        """중간 요약 — 도는 동안에도 덮어써서, 끊겨도 그때까지의 결과가 남는다."""
        (self.path / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        (self.path / "summary.md").write_text(markdown, encoding="utf-8")

    def finish(self, summary: dict, markdown: str) -> None:
        self._samples.close()
        self.meta["finishedAt"] = _dt.datetime.now().isoformat(timespec="seconds")
        self._write_meta()
        self.write_summary(summary, markdown)


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
