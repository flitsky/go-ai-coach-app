package com.worksoc.goaicoach.shared.domain

/**
 * **한 판의 정체성** — 판 크기·계가 규칙·접바둑·덤(refactor backlog #22).
 *
 * 이 네 값은 판이 시작될 때 정해지고, 저장했다 되살려도·원격 엔진에 보내도·기록으로 남겨도 **함께 가야** 한다.
 * 예전에는 코덱마다 네 값을 손으로 하나씩 골라 담아 **같은 종류의 누락이 되풀이됐다** — 이어하기가 덤을
 * 빠뜨렸고(#1), 원격 와이어가 덤·접바둑을 빠뜨렸고(#19), 새 대국·자동저장이 덤(#94)과 계가 규칙(#22)을
 * 엉뚱한 출처에서 읽었다. 이제 코덱은 [GameState.setup]이나 기록의 `setup` **하나**를 왕복한다.
 *
 * ⚠️ **기본값을 두지 않는다** — 새 칸을 더하면 이 값을 만드는 모든 자리(코덱의 decode 포함)가 컴파일에서 멈춘다.
 * 그게 이 타입의 요점이다. 새 칸을 더할 때는 저장 코덱에 **키 하나**를 더하고(`GameSetupJsonCodec`), 그 키가 없는
 * 옛 저장분을 무엇으로 읽을지 기본값을 정한다 — 스키마 번호는 올리지 않는다(함정 69).
 *
 * ⚠️ [ruleset]의 enum 상수 이름이 곧 저장 포맷이다(함정 1) — 이름을 바꾸면 옛 저장분이 기본값으로 읽힌다.
 *
 * 2026-10-15 두 칸을 더했다(백로그 #266) — [setupStones]·[firstPlayer]. 바둑판 사진에서 「이 국면부터 대국」하면 판에 **처음부터 놓인 돌**이 있는데,
 * 판을 수순만으로 다시 세우던 모든 자리(이어하기 · 무르기 · 엔진 동기화 · 대국 기록)가 그 돌을 잃었다. 보통의 대국은 [standard]로 만든다.
 */
data class GameSetup(
    val boardSize: BoardSize,
    val ruleset: Ruleset,
    val handicapCount: Int,
    val komi: Double,
    /** 첫 수 전에 판에 놓여 있던 돌 — 밖에서 받은 국면(바둑판 사진)에서 시작한 판만. 보통의 대국·접바둑은 빈 맵이다(접바둑 돌은 [handicapCount]가 낸다). */
    val setupStones: Map<BoardCoordinate, StoneColor>,
    /** 첫 수를 둘 차례 — 보통은 흑, 접바둑은 백([firstPlayerFor]). 시작 배치가 있으면 그 국면에서 고른 차례다. */
    val firstPlayer: StoneColor,
) {
    companion object {
        /** 시작 배치가 없는 보통의 판 — 새 대국 · 로비 · 옛 기록이 이것으로 만든다. */
        fun standard(boardSize: BoardSize, ruleset: Ruleset, handicapCount: Int, komi: Double): GameSetup =
            GameSetup(boardSize, ruleset, handicapCount, komi, setupStones = emptyMap(), firstPlayer = firstPlayerFor(handicapCount))

        /** 시작 배치가 없는 판의 첫 차례 — 접바둑이면 백, 아니면 흑. 이 규칙의 정의는 여기 한 곳이다. */
        fun firstPlayerFor(handicapCount: Int): StoneColor = if (handicapCount > 0) StoneColor.White else StoneColor.Black
    }
}
