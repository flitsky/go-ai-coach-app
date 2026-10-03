"""사람 모델(KataGo Human SL) — 프로필과, 사람 정책에서 한 수를 뽑는 레시피.

레시피(리서치 `git show b1d99ad7:work/roadmap/260928-_LEAGUE_LADDER_RESEARCH.md` §3.4):
- **R2 앱용(꼬리 누르기)**: 사람 정책 1회 평가 → 확률 1% 미만인 수에만 온도(0.85 → 0.70, 반감 80수)를 건다.
  go-bot(OGS에서 사람과 둬서 보정된 유일한 레시피)의 착수 단계를 앱이 흉내 내는 것.
- **R3 문서식(전체 온도 1.0)**: 사람 정책에 그대로 비례해 뽑는다.
둘 다 통과는 뽑지 않는다(go-bot의 `humanSLChosenMoveIgnorePass=true` — 통과는 주 모델이 정한다).

`temperature_transform`은 KataGo v1.16.4 `Search::chooseIndexWithTemperature`(`searchhelpers.cpp`)를,
`move_temperature`는 `Search::interpolateEarly`를 그대로 옮겼다 — 반감기는 19줄 기준이라 판이 작으면 빨리 식는다.
"""
from __future__ import annotations

import math
import random as _random
from dataclasses import dataclass

# 실험에서 훑는 프로필. 모델 하나에 rank_20k ~ rank_9d가 다 있다(이름이 있다고 그 세기가 나오는 것은 아니다 — 리서치 §3.5).
DEFAULT_PROFILES: tuple[str, ...] = (
    "rank_20k",
    "rank_15k",
    "rank_12k",
    "rank_10k",
    "rank_8k",
    "rank_5k",
    "rank_3k",
    "rank_1k",
    "rank_1d",
    "rank_3d",
)


@dataclass(frozen=True)
class Recipe:
    name: str
    temperature_early: float
    temperature: float
    halflife: float
    only_below_prob: float

    def describe(self) -> str:
        return (
            f"{self.name}: T {self.temperature_early}→{self.temperature} (반감 {self.halflife}수), "
            f"확률 {self.only_below_prob:g} 미만에만"
        )


# go-bot / `gtp_human5k_example.cfg`의 착수 온도 설정.
R2_TAIL = Recipe("R2-tail", temperature_early=0.85, temperature=0.70, halflife=80.0, only_below_prob=0.01)
# 문서식 — 전체에 온도 1.0(= 정책 그대로).
R3_FULL = Recipe("R3-full", temperature_early=1.0, temperature=1.0, halflife=80.0, only_below_prob=1.0)

RECIPES = {r.name: r for r in (R2_TAIL, R3_FULL)}


def move_temperature(recipe: Recipe, turn_number: int, size: int) -> float:
    """`Search::interpolateEarly` — 반감 수를 19줄 기준으로 판 크기에 맞춘다."""
    halflives = (turn_number / recipe.halflife) * 19.0 / math.sqrt(size * size)
    return recipe.temperature + (recipe.temperature_early - recipe.temperature) * (0.5**halflives)


def temperature_transform(probs: dict[str, float], temperature: float, only_below_prob: float) -> dict[str, float]:
    """`Search::chooseIndexWithTemperature`의 확률 가공(뽑기 직전). 합이 1이 되게 정규화해 돌려준다."""
    positive = {move: p for move, p in probs.items() if p > 0}
    if not positive:
        return {}
    max_p = max(positive.values())
    sum_p = sum(positive.values())
    if temperature <= 1e-4 and only_below_prob >= 1.0:
        best = max(positive, key=positive.get)
        return {best: 1.0}
    log_max = math.log(max_p)
    log_threshold = min(0.0, math.log(max(1e-50, only_below_prob)) + math.log(sum_p) - log_max)
    out: dict[str, float] = {}
    for move, p in positive.items():
        log_rel = math.log(p) - log_max
        if log_rel > log_threshold:
            new_log = log_rel
        else:
            new_log = (log_rel - log_threshold) / temperature + log_threshold
        out[move] = math.exp(new_log)
    total = sum(out.values())
    return {move: value / total for move, value in out.items()}


def choice_distribution(policy: dict[str, float], recipe: Recipe, turn_number: int, size: int) -> dict[str, float]:
    """사람 정책(통과 제외) → 이 레시피로 뽑을 때 각 수가 나올 확률."""
    return temperature_transform(policy, move_temperature(recipe, turn_number, size), recipe.only_below_prob)


def sample(distribution: dict[str, float], rng: _random.Random) -> str:
    moves = list(distribution)
    return rng.choices(moves, weights=[distribution[m] for m in moves], k=1)[0]


def human_gtp_overrides(profile: str) -> dict[str, str]:
    """GTP 프로세스에 사람 모델 설정을 거는 덮어쓰기. ⚠️ `humanSLProfile` 말고 다른 humanSL 키는 `-human-model`이 있어야 한다."""
    return {"humanSLProfile": profile}
