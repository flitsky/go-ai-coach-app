"""「기보 보기」(백로그 #269) — 앱에 실은 기보의 **매 국면 형세**를 미리 재어 앱 에셋(`*.eval`)으로 쓴다.

앱의 「형세 보기」와 **같은 모델 · 같은 명령**(`kata-raw-nn 0`, 탐색 없는 신경망 1회)이다 — 그래서 미리 잰 값과 엔진에 물은 값이 같은 뜻이다.
국면은 앱이 내보낸 대본(`app-android/build/game-record-positions/*.json`)을 그대로 넣는다 — 기보 해석(덤이 없으면 0, 규칙 매핑, 배치)은
앱의 Kotlin 한 곳(`readSgfGameRecord`)에만 있다.

    ./gradlew :app-android:testDebugUnitTest --tests '*GameRecordPositionExportTest*' -PexportGameRecordPositions
    python3 engine-lab/tools/game_record_evals.py

엔진에 넣는 순서도 앱과 같다(`KataGoProtocolCommands`): `kata-set-rules` → 덤·첫 차례·시작 배치를 담은 SGF를 `loadsgf` → 수를 하나씩 `play`.
33판 7,739국면이 맥에서 2분 안팎이다(2026-10-19 — 국면당 13ms).

`.eval` 형식(작은 끝수 · 버전 1) — 읽는 쪽은 `GameRecordEvaluation.kt`:
    머리 16바이트: "GREV" · 버전 u8 · 판 크기 u8 · 규칙 u8(0 일본식, 1 중국식) · 0 u8 · 덤 f32 · 국면 수 i32
    집 차이 블록: 국면마다 백 기준 집 차이 f32 · 백 승률 f32
    영역 블록: 첫 국면은 판 크기² 칸 i8(백 기준 −1~1 × 127, 맨 윗줄부터), 다음 국면부터는 **앞 국면과의 차이**(i8, 256으로 돈다).
    차이로 담는 것은 크기 때문이다 — 한 수로 바뀌는 칸이 적어 앱 압축 뒤 2.18MB → 1.70MB(33판, 2026-10-19 실측).
"""
from __future__ import annotations

import json
import struct
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lab import app_parity as ap  # noqa: E402
from lab import paths  # noqa: E402
from lab.gtp import local_gtp, parse_raw_nn  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
SCRIPTS = REPO / "app-android" / "build" / "game-record-positions"
ASSETS = REPO / "app-android" / "src" / "main" / "assets" / "game-records"
MAGIC = b"GREV"
VERSION = 1
RULES = {"japanese": 0, "chinese": 1}
COLUMNS = "ABCDEFGHJKLMNOPQRST"


def gtp_vertex(size: int, row: int, column: int) -> str:
    return f"{COLUMNS[column]}{size - row}"


def static_position_sgf(script: dict) -> str:
    """앱의 `staticPositionSgf`와 같은 모양 — SGF 좌표는 첫 글자가 열, 둘째가 행(a = 맨 위)."""
    def points(color: str) -> str:
        stones = sorted((s["row"], s["column"]) for s in script["setup"] if s["color"] == color)
        return "".join(f"[{chr(97 + c)}{chr(97 + r)}]" for r, c in stones)

    black, white = points("B"), points("W")
    return (
        f"(;GM[1]FF[4]SZ[{script['size']}]KM[{script['komi']}]PL[{script['firstPlayer']}]"
        + (f"AB{black}" if black else "")
        + (f"AW{white}" if white else "")
        + ")"
    )


def evaluate(engine, size: int) -> tuple[bytes, list[int]]:
    """국면 하나 — (집 차이·승률 8바이트, 영역 i8 값들)."""
    raw = engine.send("kata-raw-nn 0")
    fields = parse_raw_nn(raw, size).fields
    lines = [line.split() for line in raw.splitlines() if line.strip()]
    header = next(index for index, tokens in enumerate(lines) if tokens == ["whiteOwnership"])
    grid = [float(value) for row in lines[header + 1 : header + 1 + size] for value in row]
    if len(grid) != size * size:
        raise RuntimeError(f"영역 격자가 {len(grid)}칸이다(판 {size}줄)")
    ownership = [max(-127, min(127, round(value * 127))) for value in grid]
    return struct.pack("<ff", fields["whiteLead"], fields["whiteWin"]), ownership


def ownership_block(grids: list[list[int]]) -> bytes:
    """첫 국면은 그대로, 다음부터는 앞 국면과의 차이(256으로 돈다)."""
    out = bytearray(value & 0xFF for value in grids[0])
    for previous, current in zip(grids, grids[1:]):
        out += bytes((now - before) & 0xFF for before, now in zip(previous, current))
    return bytes(out)


def main() -> int:
    scripts = sorted(SCRIPTS.glob("*.json"))
    if not scripts:
        print(f"대본이 없다 — 먼저 -PexportGameRecordPositions로 내보낸다: {SCRIPTS}", file=sys.stderr)
        return 1
    engine = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=paths.gtp_config(),
        overrides=ap.gtp_overrides(ap.FAST_BEGINNER_VISITS),
        label="main",
    )
    try:
        with tempfile.TemporaryDirectory() as workdir:
            for path in scripts:
                script = json.loads(path.read_text(encoding="utf-8"))
                size = script["size"]
                engine.send(f"boardsize {size}")
                engine.send("clear_board")
                engine.send(f"kata-set-rules {script['rules']}")
                engine.send(f"komi {script['komi']}")
                setup_file = Path(workdir) / f"{path.stem}.sgf"
                setup_file.write_text(static_position_sgf(script), encoding="utf-8")
                engine.send(f"loadsgf {setup_file}")
                positions = len(script["moves"]) + 1
                scores, grids = [], []
                for index in range(positions):
                    if index > 0:
                        move = script["moves"][index - 1]
                        vertex = "pass" if move.get("pass") else gtp_vertex(size, move["row"], move["column"])
                        engine.send(f"play {move['color']} {vertex}")
                    score, grid = evaluate(engine, size)
                    scores.append(score)
                    grids.append(grid)
                out = bytearray(MAGIC)
                out += struct.pack("<BBBBfi", VERSION, size, RULES[script["rules"]], 0, float(script["komi"]), positions)
                out += b"".join(scores)
                out += ownership_block(grids)
                (ASSETS / f"{path.stem}.eval").write_bytes(bytes(out))
                print(f"{path.stem}: {positions}국면 · {len(out):,}바이트")
    finally:
        engine.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
