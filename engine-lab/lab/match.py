"""대국 — 서로 다른 「선수」끼리 끝까지 둔다(백로그 #214 E2b).

선수:
- `TierPlayer` — 지금 앱의 캐릭터. GTP 빠른 경로(16방문, 1스레드) → 점수 붙은 착수 후보 최대 8개 → 5단계 버킷 규칙.
  **트리를 이어 쓴다**(앱의 사람 대 AI와 같다 — AI 대 AI에서만 `clear_cache`).
- `HumanPlayer` — 사람 모델 프로필. `kata-raw-human-nn 0` 한 번 → 레시피(R2 꼬리 누르기 등)로 뽑는다.
  통과는 뽑지 않는다 — **주 모델 원시 정책의 1위가 통과일 때만** 통과한다(go-bot `IgnorePass`처럼 통과는 주 모델이 정한다).

한 GTP 프로세스(주 모델 + 사람 모델)를 두 선수가 같이 쓴다 — 모든 수를 `play`로 넣어 판을 맞춘다.
판정은 JSON 분석 엔진의 센 탐색 점수(기본 400방문, 흑 기준)로 한다 — 계가가 아니라 형세 판정이다.
"""
from __future__ import annotations

import random as _random
from dataclasses import dataclass, field

from . import app_parity as ap
from . import human
from .analysis import AnalysisEngine
from .board import BLACK, PASS, Position, opponent
from .gtp import GtpEngine
from .selection import select_rank


class Player:
    name: str

    def choose(self, engine: GtpEngine, position: Position, rng: _random.Random) -> tuple[str, dict]:
        raise NotImplementedError


@dataclass
class TierPlayer(Player):
    level: int
    visits: int = ap.FAST_BEGINNER_VISITS

    @property
    def name(self) -> str:  # type: ignore[override]
        t = ap.tier(self.level)
        return f"T{t.level}-{t.name}"

    def choose(self, engine: GtpEngine, position: Position, rng: _random.Random) -> tuple[str, dict]:
        tier = ap.tier(self.level)
        count = 1 if tier.best_only else ap.FAST_BEGINNER_CANDIDATE_COUNT
        result = engine.search_analyze(position.next_player, visits=self.visits, time_ms=None, max_candidates=count)
        scored = result.scored
        if scored and scored[0].move.lower() == PASS:
            return PASS, {"candidates": 0, "pass": True}
        plays = result.scored_plays
        if not plays:
            return PASS, {"candidates": 0}
        own = sum(1 for color, move in position.moves if color == position.next_player and move.lower() != PASS)
        rank = select_rank(tier, len(plays), own, rng)
        return plays[rank or 0].move, {"candidates": len(plays), "rank": (rank or 0) + 1, "rootVisits": result.root_visits}


@dataclass
class HumanPlayer(Player):
    profile: str
    recipe: human.Recipe = field(default_factory=lambda: human.R2_TAIL)

    @property
    def name(self) -> str:  # type: ignore[override]
        return f"{self.profile}-{self.recipe.name}"

    def choose(self, engine: GtpEngine, position: Position, rng: _random.Random) -> tuple[str, dict]:
        main = engine.raw_nn(position.size, human=False)
        top_main = max(main.policy.values(), default=0.0)
        if main.pass_prob is not None and main.pass_prob > top_main:
            return PASS, {"pass": True}
        engine.set_param("humanSLProfile", self.profile)
        raw = engine.raw_nn(position.size, human=True)
        if not raw.policy:
            return PASS, {}
        dist = human.choice_distribution(raw.policy, self.recipe, position.move_number, position.size)
        move = human.sample(dist, rng)
        return move, {"prob": round(dist[move], 4)}


@dataclass
class GameResult:
    black: str
    white: str
    moves: list[tuple[str, str]]
    black_lead: float | None
    ended_by: str

    @property
    def winner(self) -> str | None:
        if self.black_lead is None:
            return None
        return BLACK if self.black_lead > 0 else "W"


def play_game(
    engine: GtpEngine,
    judge: AnalysisEngine,
    black: Player,
    white: Player,
    *,
    size: int,
    komi: float = ap.DEFAULT_KOMI,
    rules: str = ap.DEFAULT_RULES,
    rng: _random.Random,
    max_moves: int | None = None,
    judge_visits: int = 400,
) -> GameResult:
    position = Position(id="game", size=size, komi=komi, rules=rules)
    engine.setup(position)
    engine.clear_cache()
    limit = max_moves or int(size * size * 1.6)
    passes = 0
    ended_by = "limit"
    while position.move_number < limit:
        player = black if position.next_player == BLACK else white
        move, _info = player.choose(engine, position, rng)
        color = position.next_player
        try:
            engine.play(color, move)
        except RuntimeError:
            # 고른 수가 엔진에서 불법(드문 경우 — 패 등)이면 통과로 바꾼다
            move = PASS
            engine.play(color, move)
        position.moves.append((color, move))
        passes = passes + 1 if move.lower() == PASS else 0
        if passes >= 2:
            ended_by = "pass-pass"
            break
    data = judge.query(position, max_visits=judge_visits)
    lead = data.get("rootInfo", {}).get("scoreLead")
    return GameResult(
        black=black.name,
        white=white.name,
        moves=list(position.moves),
        black_lead=float(lead) if lead is not None else None,
        ended_by=ended_by,
    )


def opponent_color(color: str) -> str:
    return opponent(color)
