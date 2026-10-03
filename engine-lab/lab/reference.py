"""기준 평가 — 「그 수가 실제로 몇 집 손해인가」를 센 탐색으로 잰다.

방법: 국면마다 제한 없는 탐색(`root_visits`)으로 기준 최선수를 고른 뒤, **최선수를 포함한 모든 대상 수를 같은 조건으로**
그 수만 허용한 탐색(`allowMoves`, `move_visits`)으로 평가한다. 손해 = 최선수 점수 − 그 수 점수(둘 차례 기준, 0 아래로 안 감).
같은 방문 수로 재야 「적게 탐색한 수가 손해로 보이는」 편향이 없다.

⚠️ 기준은 **주 모델**이다(앱이 형세·계가에 쓰는 것과 같은 b18). 사람 망의 점수는 편향돼 있어 쓰지 않는다(리서치 §3.7).
결과는 `cache/reference-*.jsonl`에 쌓는다 — 같은 국면·수·방문을 두 번 재지 않는다(실험 사이에도 공유).
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from .analysis import AnalysisEngine, to_mover
from .board import PASS, Position


def position_key(position: Position) -> str:
    text = json.dumps([position.size, position.komi, position.rules, position.moves])
    return hashlib.sha1(text.encode()).hexdigest()[:16]


@dataclass
class MoveValue:
    move: str
    score: float  # 둘 차례 기준 scoreLead
    winrate: float  # 둘 차례 기준
    visits: int


class ReferenceEvaluator:
    def __init__(
        self,
        engine: AnalysisEngine,
        cache_path: Path,
        *,
        root_visits: int = 800,
        move_visits: int = 300,
        model_tag: str = "b18c384nbt-s9996604416",
    ) -> None:
        self.engine = engine
        self.root_visits = root_visits
        self.move_visits = move_visits
        self.model_tag = model_tag
        self.cache_path = cache_path
        self._cache: dict[str, dict] = {}
        if cache_path.exists():
            for line in cache_path.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    row = json.loads(line)
                    self._cache[row["key"]] = row

    def _key(self, position: Position, kind: str, move: str, visits: int) -> str:
        return f"{self.model_tag}|{position_key(position)}|{kind}|{move}|{visits}"

    def _store(self, row: dict) -> None:
        self._cache[row["key"]] = row
        self.cache_path.parent.mkdir(parents=True, exist_ok=True)
        with self.cache_path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")

    def best_move(self, position: Position) -> str:
        key = self._key(position, "best", "*", self.root_visits)
        if key not in self._cache:
            data = self.engine.query(position, max_visits=self.root_visits)
            infos = sorted(data.get("moveInfos", []), key=lambda info: info.get("order", 999))
            best = infos[0]["move"] if infos else PASS
            self._store({"key": key, "best": best, "rootVisits": data.get("rootInfo", {}).get("visits")})
        return self._cache[key]["best"]

    def values(self, position: Position, moves: list[str]) -> dict[str, MoveValue]:
        """moves 각각을 「그 수만 허용」한 같은 방문 수로 평가한다(한꺼번에 보내 병렬로)."""
        wanted = [m for m in dict.fromkeys(moves) if m]
        missing = [m for m in wanted if self._key(position, "move", m, self.move_visits) not in self._cache]
        if missing:
            queries = [
                AnalysisEngine.build_query(position, max_visits=self.move_visits, allow_moves=[m]) for m in missing
            ]
            for move, data in zip(missing, self.engine.query_many(queries)):
                infos = data.get("moveInfos", [])
                info = next((i for i in infos if i.get("move", "").upper() == move.upper()), infos[0] if infos else None)
                if info is None:
                    continue
                self._store(
                    {
                        "key": self._key(position, "move", move, self.move_visits),
                        "move": move,
                        "score": to_mover(position, float(info["scoreLead"])),
                        "winrate": to_mover_winrate(position, float(info["winrate"])),
                        "visits": int(info.get("visits", 0)),
                    }
                )
        out: dict[str, MoveValue] = {}
        for move in wanted:
            row = self._cache.get(self._key(position, "move", move, self.move_visits))
            if row:
                out[move] = MoveValue(move, row["score"], row["winrate"], row["visits"])
        return out

    def point_losses(self, position: Position, moves: list[str]) -> tuple[str, dict[str, float]]:
        """(기준 최선수, 수 → 손해 집). 최선수도 같은 방문으로 다시 재서 기준을 맞춘다."""
        best = self.best_move(position)
        values = self.values(position, [best] + list(moves))
        if best not in values:
            return best, {}
        best_score = values[best].score
        top = max(v.score for v in values.values())
        reference = max(best_score, top)  # 대상 수가 기준 최선보다 낫게 나오면 그 수를 기준으로(손해 0)
        return best, {move: max(0.0, reference - v.score) for move, v in values.items()}


def to_mover_winrate(position: Position, black_winrate: float) -> float:
    from .board import BLACK

    return black_winrate if position.next_player == BLACK else 1.0 - black_winrate
