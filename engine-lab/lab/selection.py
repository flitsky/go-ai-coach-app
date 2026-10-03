"""캐릭터(빠른초급 5단계)가 후보에서 한 수를 고르는 규칙 — 앱 코드를 그대로 옮긴 것.

원본: `PlayLevel.kt`의 `candidateBucketRange`·`targetBucket`·`resolveIndexRange`, `AiMoveSelectionPolicy.select`.
- 후보(점수 붙은 착수, 0 = 최선)를 최선 {0} / 중간 {1..N-2} / 최하 {N-1}로 나눈다. 최하는 N≥3, 중간은 N≥2에서만 있다.
- 이번 수의 목표 버킷: 「지금까지 뒀어야 할 최하수 누적 목표」 ceil(worst% × k)가 이번 수에서 늘면 최하,
  아니면 중간:최선 = mid%:best% 확률(상태 저장 없음, k = 이 진영이 지금까지 둔 착수 수).
- 목표 버킷이 비어 있으면 최하 → 중간 → 최선 순으로 내려간다. 버킷 안에서는 고르게 뽑는다.
- 초고수(최선만)는 후보 1개만 요청해 그 수를 둔다. 맨 앞 후보가 통과면 단계와 상관없이 통과한다.
"""
from __future__ import annotations

import math
import random as _random

from .app_parity import Tier

BEST = "best"
MID = "mid"
WORST = "worst"


def candidate_bucket_range(n: int, bucket: str) -> range | None:
    if n <= 0:
        return None
    if bucket == BEST:
        return range(0, 1)
    if bucket == MID:
        if n <= 1:
            return None
        if n == 2:
            return range(1, 2)
        return range(1, n - 1)
    if n <= 2:
        return None
    return range(n - 1, n)


def target_bucket(tier: Tier, own_move_index: int, rng: _random.Random) -> str:
    if tier.worst_percent > 0:
        previous = math.ceil(tier.worst_percent * own_move_index / 100.0)
        current = math.ceil(tier.worst_percent * (own_move_index + 1) / 100.0)
        if current > previous:
            return WORST
    if tier.mid_percent <= 0:
        return BEST
    if tier.best_percent <= 0:
        return MID
    return MID if rng.randrange(tier.mid_percent + tier.best_percent) < tier.mid_percent else BEST


def _fallback(target: str) -> tuple[str, ...]:
    return {WORST: (WORST, MID, BEST), MID: (MID, BEST), BEST: (BEST,)}[target]


def resolve_index_range(tier: Tier, n: int, own_move_index: int, rng: _random.Random) -> range | None:
    if n <= 0:
        return None
    for bucket in _fallback(target_bucket(tier, own_move_index, rng)):
        found = candidate_bucket_range(n, bucket)
        if found is not None:
            return found
    return None


def select_rank(tier: Tier, n: int, own_move_index: int, rng: _random.Random) -> int | None:
    """후보 n개 중 이 단계가 고르는 순위(0 = 최선). n = 점수 붙은 착수 후보 수(통과 제외)."""
    if tier.best_only:
        return 0 if n > 0 else None
    found = resolve_index_range(tier, n, own_move_index, rng)
    if found is None:
        return None
    return rng.choice(list(found))


def rank_distribution(tier: Tier, n: int, own_move_indices: range = range(0, 100)) -> list[float]:
    """후보가 n개일 때 이 단계가 각 순위를 둘 **장기 확률**(k를 고르게 평균) — 표본 없이 정확히 센다."""
    if n <= 0:
        return []
    if tier.best_only:
        return [1.0] + [0.0] * (n - 1)
    total = [0.0] * n
    count = 0
    mid_share = tier.mid_percent / (tier.mid_percent + tier.best_percent) if (tier.mid_percent + tier.best_percent) else 0.0
    for k in own_move_indices:
        targets: list[tuple[str, float]]
        if tier.worst_percent > 0 and math.ceil(tier.worst_percent * (k + 1) / 100.0) > math.ceil(tier.worst_percent * k / 100.0):
            targets = [(WORST, 1.0)]
        elif tier.mid_percent <= 0:
            targets = [(BEST, 1.0)]
        elif tier.best_percent <= 0:
            targets = [(MID, 1.0)]
        else:
            targets = [(MID, mid_share), (BEST, 1.0 - mid_share)]
        for target, weight in targets:
            for bucket in _fallback(target):
                found = candidate_bucket_range(n, bucket)
                if found is not None:
                    for index in found:
                        total[index] += weight / len(found)
                    break
        count += 1
    return [value / count for value in total]
