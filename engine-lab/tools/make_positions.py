#!/usr/bin/env python3
"""실험용 국면 세트를 만든다 — 사람 모델로 **사람처럼 둔 대국**에서 초반·중반·끝내기 국면을 뽑는다.

왜 사람 대국인가: 앱 사용자는 입문자다. 엔진끼리 둔 판보다 사람(급수) 판의 국면이 캐릭터가 실제로 마주치는 모양에 가깝다.
설정은 KataGo 공식 `gtp_human5k_example.cfg`(brew 사본) 그대로에 프로필만 바꾼다 — go-bot 레시피와 같다(40방문, 1% 미만 수에만 온도).
대국마다 프로필을 돌려 가며(15급·10급·5급) 판 모양을 섞는다.

결과는 `positions/<이름>.json`으로 저장하고 **커밋한다** — 같은 국면으로 다시 재야 실험끼리 비교된다.

    python3 engine-lab/tools/make_positions.py --name selfplay-human-v1
"""
from __future__ import annotations

import argparse
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lab import paths  # noqa: E402
from lab.board import BLACK, PASS, Position, opponent, save_positions  # noqa: E402
from lab.gtp import local_gtp  # noqa: E402

HUMAN_EXAMPLE_CFG = paths.BREW_KATAGO_SHARE / "configs" / "gtp_human5k_example.cfg"

# 판 크기마다: 대국 수, 뽑을 수순 번호.
PLAN = {
    9: {"games": 3, "moves": (8, 16, 26, 38, 50)},
    13: {"games": 5, "moves": (12, 24, 40, 60, 80, 100, 120)},
    19: {"games": 3, "moves": (20, 40, 70, 100, 140, 180, 220, 260)},
}
PROFILES = ("rank_15k", "rank_10k", "rank_5k")


def play_game(size: int, profile: str, max_moves: int, log_dir: str) -> list[tuple[str, str]]:
    engine = local_gtp(
        katago=paths.katago_binary(),
        model=paths.main_model(),
        config=HUMAN_EXAMPLE_CFG,
        human_model=paths.human_model(),
        overrides={
            "humanSLProfile": profile,
            "allowResignation": "false",  # 끝까지 둬야 끝내기 국면이 나온다
            # ⚠️ 예제 cfg는 사람처럼 **일부러 늦게** 둔다(delayMoveScale 2.0, 최대 10초 — CPU 0%로 기다린다).
            # 국면 만들기엔 필요 없다. 이 지연은 #212 「착수 템포」가 앱에서 흉내 낼 그 기능이다.
            "delayMoveScale": "0",
            "delayMoveMax": "0",
            "logDir": log_dir,
            "logAllGTPCommunication": "false",
            "logSearchInfo": "false",
            "logToStderr": "false",
            "numSearchThreads": "4",  # 국면 만들기는 앱과 같을 필요가 없다 — 빨리
        },
        label=f"selfplay-{size}-{profile}",
    )
    moves: list[tuple[str, str]] = []
    try:
        engine.setup(Position(id="tmp", size=size))
        color = BLACK
        passes = 0
        while len(moves) < max_moves and passes < 2:
            move = engine.genmove(color).strip()
            if move.lower() == "resign":
                break
            moves.append((color, move.upper() if move.lower() != PASS else PASS))
            passes = passes + 1 if move.lower() == PASS else 0
            color = opponent(color)
    finally:
        engine.close()
    return moves


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--name", default="selfplay-human-v1")
    parser.add_argument("--sizes", default="9,13,19")
    args = parser.parse_args()

    if not paths.human_model().exists():
        print(f"사람 모델이 없다: {paths.human_model()}", file=sys.stderr)
        return 1

    positions: list[Position] = []
    games_meta = []
    with tempfile.TemporaryDirectory() as log_dir:
        for size in [int(s) for s in args.sizes.split(",")]:
            plan = PLAN[size]
            for game in range(plan["games"]):
                profile = PROFILES[game % len(PROFILES)]
                moves = play_game(size, profile, max_moves=int(size * size * 1.3), log_dir=log_dir)
                games_meta.append({"size": size, "game": game + 1, "profile": profile, "length": len(moves)})
                print(f"{size}x{size} game {game + 1} ({profile}): {len(moves)} moves", flush=True)
                for number in plan["moves"]:
                    if number >= len(moves) - 1:
                        continue
                    prefix = moves[:number]
                    if prefix and prefix[-1][1] == PASS:
                        continue
                    positions.append(
                        Position(
                            id=f"s{size}-g{game + 1}-m{number}",
                            size=size,
                            moves=list(prefix),
                            note=f"human self-play {profile}",
                        )
                    )
    meta = {
        "name": args.name,
        "how": "KataGo gtp_human5k_example.cfg + humanSLProfile per game (go-bot recipe, 40 visits), allowResignation=false",
        "profiles": list(PROFILES),
        "games": games_meta,
        "katago": str(paths.katago_binary()),
        "mainModel": paths.MAIN_MODEL_NAME,
        "humanModel": paths.HUMAN_MODEL_NAME,
    }
    out = paths.positions_dir() / f"{args.name}.json"
    save_positions(out, positions, meta)
    print(f"{len(positions)} positions → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
