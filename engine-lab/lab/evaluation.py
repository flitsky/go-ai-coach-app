"""표본 하나(국면 × 탐색 결과)에서 캐릭터 5단계가 **실제로 무엇을 두는지**와 그 수의 손해를 계산한다.

무작위 뽑기를 여러 번 돌리는 대신 `selection.rank_distribution`의 정확한 확률로 기대값을 낸다 — 같은 표본에서 늘 같은 숫자.
"""
from __future__ import annotations

from statistics import mean

from . import app_parity as ap
from .selection import rank_distribution

LOSS_THRESHOLDS = (3.0, 10.0)


def tier_outcomes(
    scored_plays: list[str],
    losses: dict[str, float],
    ref_best: str | None,
    top_is_pass: bool,
) -> dict[str, dict]:
    """단계 이름 → {expLoss, pRank0, pRefBest, pLoss3, pLoss10, distribution}.

    scored_plays: 앱이 버킷을 나누는 후보(점수 붙은 착수, 순위 순). losses: 수 → 기준 손해(집).
    맨 앞 후보가 통과면 모든 단계가 통과한다(앱 규칙) — 그 표본은 손해 0, 최선으로 센다.
    """
    out: dict[str, dict] = {}
    for tier in ap.TIERS:
        if top_is_pass:
            out[tier.name] = {"expLoss": 0.0, "pRank0": 1.0, "pRefBest": 1.0, "pLoss3": 0.0, "pLoss10": 0.0, "distribution": []}
            continue
        n = len(scored_plays)
        if n == 0:
            continue
        dist = rank_distribution(tier, n)
        if any(move not in losses for move in scored_plays):
            continue
        exp_loss = sum(p * losses[move] for p, move in zip(dist, scored_plays))
        row = {
            "expLoss": exp_loss,
            "pRank0": dist[0],
            "pRefBest": sum(p for p, move in zip(dist, scored_plays) if move == ref_best),
            "distribution": dist,
        }
        for threshold in LOSS_THRESHOLDS:
            row[f"pLoss{int(threshold)}"] = sum(p for p, move in zip(dist, scored_plays) if losses[move] >= threshold)
        out[tier.name] = row
    return out


def aggregate_tiers(rows: list[dict[str, dict]]) -> dict[str, dict]:
    """여러 표본의 tier_outcomes를 평균한다."""
    out: dict[str, dict] = {}
    for tier in ap.TIERS:
        picked = [row[tier.name] for row in rows if tier.name in row]
        if not picked:
            continue
        out[tier.name] = {
            "samples": len(picked),
            "expLoss": mean(p["expLoss"] for p in picked),
            "pRank0": mean(p["pRank0"] for p in picked),
            "pRefBest": mean(p["pRefBest"] for p in picked),
            "pLoss3": mean(p["pLoss3"] for p in picked),
            "pLoss10": mean(p["pLoss10"] for p in picked),
        }
    return out


def count_histogram(values: list[int], buckets: tuple[int, ...] = (1, 2, 3, 4, 5, 6, 7, 8)) -> dict[str, float]:
    """후보 수 분포 — 키 "0","1",…,"8"(8 = 8개 이상), 값은 비율."""
    total = len(values) or 1
    hist: dict[str, float] = {"0": sum(1 for v in values if v <= 0) / total}
    for b in buckets:
        if b == buckets[-1]:
            hist[str(b)] = sum(1 for v in values if v >= b) / total
        else:
            hist[str(b)] = sum(1 for v in values if v == b) / total
    return hist


def fmt_pct(value: float | None) -> str:
    return "–" if value is None else f"{value * 100:.0f}%"


def fmt_num(value: float | None, digits: int = 1) -> str:
    return "–" if value is None else f"{value:.{digits}f}"
