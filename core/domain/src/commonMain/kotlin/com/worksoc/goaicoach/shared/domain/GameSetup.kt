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
 */
data class GameSetup(
    val boardSize: BoardSize,
    val ruleset: Ruleset,
    val handicapCount: Int,
    val komi: Double,
)
