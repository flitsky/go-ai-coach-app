"""바둑판·국면 — GTP 좌표, 따내기·착수 금지·단순 패, JSON으로 저장되는 국면.

좌표는 GTP 표기(`D4`, 열 글자에 `I`가 없다, 행 1은 아래). 앱의 `BoardCoordinate.label`과 같다.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

COLUMNS = "ABCDEFGHJKLMNOPQRST"
BLACK = "B"
WHITE = "W"
PASS = "pass"


def opponent(color: str) -> str:
    return WHITE if color == BLACK else BLACK


def vertex(size: int, row: int, col: int) -> str:
    """row 0 = 맨 위(GTP 행 `size`), col 0 = A."""
    return f"{COLUMNS[col]}{size - row}"


def parse_vertex(size: int, raw: str) -> tuple[int, int] | None:
    text = raw.strip().upper()
    if text in ("PASS", ""):
        return None
    col = COLUMNS.index(text[0])
    number = int(text[1:])
    if not (0 <= col < size and 1 <= number <= size):
        raise ValueError(f"{raw} is off a {size}x{size} board")
    return size - number, col


class Board:
    def __init__(self, size: int) -> None:
        self.size = size
        self.grid: list[list[str | None]] = [[None] * size for _ in range(size)]
        self.ko_point: tuple[int, int] | None = None

    def copy(self) -> "Board":
        other = Board(self.size)
        other.grid = [row[:] for row in self.grid]
        other.ko_point = self.ko_point
        return other

    def neighbors(self, point: tuple[int, int]) -> list[tuple[int, int]]:
        row, col = point
        out = []
        for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            r, c = row + dr, col + dc
            if 0 <= r < self.size and 0 <= c < self.size:
                out.append((r, c))
        return out

    def group(self, start: tuple[int, int]) -> tuple[set[tuple[int, int]], set[tuple[int, int]]]:
        color = self.grid[start[0]][start[1]]
        stones = {start}
        liberties: set[tuple[int, int]] = set()
        stack = [start]
        while stack:
            point = stack.pop()
            for n in self.neighbors(point):
                value = self.grid[n[0]][n[1]]
                if value is None:
                    liberties.add(n)
                elif value == color and n not in stones:
                    stones.add(n)
                    stack.append(n)
        return stones, liberties

    def try_play(self, color: str, point: tuple[int, int]) -> "Board | None":
        """둔 뒤의 판, 또는 착수 금지(빈 점 아님·자충·패)면 None."""
        if self.grid[point[0]][point[1]] is not None or point == self.ko_point:
            return None
        nxt = self.copy()
        nxt.grid[point[0]][point[1]] = color
        captured: set[tuple[int, int]] = set()
        for n in nxt.neighbors(point):
            if nxt.grid[n[0]][n[1]] == opponent(color):
                stones, liberties = nxt.group(n)
                if not liberties:
                    captured |= stones
        for r, c in captured:
            nxt.grid[r][c] = None
        _, own_liberties = nxt.group(point)
        if not own_liberties:
            return None  # 자충수(일본룰·앱 규칙 모두 금지)
        nxt.ko_point = None
        if len(captured) == 1:
            stones, liberties = nxt.group(point)
            if len(stones) == 1 and len(liberties) == 1:
                nxt.ko_point = next(iter(captured))
        return nxt

    def legal_points(self, color: str) -> list[tuple[int, int]]:
        return [
            (r, c)
            for r in range(self.size)
            for c in range(self.size)
            if self.try_play(color, (r, c)) is not None
        ]

    def stone_count(self) -> int:
        return sum(1 for row in self.grid for value in row if value is not None)


@dataclass
class Position:
    """실험에 쓰는 국면 하나. `moves`는 처음부터의 수순 — 사람 모델은 직전 수에 반응하므로 전체를 넘긴다."""

    id: str
    size: int
    moves: list[tuple[str, str]] = field(default_factory=list)  # (색, GTP 좌표 또는 "pass")
    komi: float = 6.5
    rules: str = "japanese"
    note: str = ""

    @property
    def next_player(self) -> str:
        if not self.moves:
            return BLACK
        return opponent(self.moves[-1][0])

    @property
    def move_number(self) -> int:
        return len(self.moves)

    @property
    def phase(self) -> str:
        """초반·중반·끝내기 — 판 넓이에 대한 수 비율로 거칠게 가른다(판 크기마다 같은 잣대)."""
        ratio = len(self.moves) / (self.size * self.size)
        if ratio < 0.15:
            return "opening"
        if ratio < 0.5:
            return "middle"
        return "endgame"

    def board(self) -> Board:
        board = Board(self.size)
        for color, move in self.moves:
            point = parse_vertex(self.size, move)
            if point is None:
                board.ko_point = None
                continue
            nxt = board.try_play(color, point)
            if nxt is None:
                raise ValueError(f"{self.id}: illegal move {color} {move}")
            board = nxt
        return board

    def to_json(self) -> dict:
        return {
            "id": self.id,
            "size": self.size,
            "komi": self.komi,
            "rules": self.rules,
            "note": self.note,
            "moves": [[color, move] for color, move in self.moves],
        }

    @staticmethod
    def from_json(data: dict) -> "Position":
        return Position(
            id=data["id"],
            size=int(data["size"]),
            moves=[(color, move) for color, move in data["moves"]],
            komi=float(data.get("komi", 6.5)),
            rules=data.get("rules", "japanese"),
            note=data.get("note", ""),
        )


def load_positions(path: Path) -> list[Position]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return [Position.from_json(item) for item in data["positions"]]


def save_positions(path: Path, positions: list[Position], meta: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"meta": meta, "positions": [p.to_json() for p in positions]}
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
