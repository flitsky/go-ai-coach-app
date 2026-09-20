package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

/**
 * 앱에 번들로 들어가는 **참고 기보** 하나(2026-09-20 사용자 요청) — 최고등급 AI가 그 바로 아래
 * 등급에게 2점을 접어 주고 0.5집으로 이긴, 대국 기록 화면 최상단에 항상 고정 노출되는 예시 판.
 *
 * ⚠️ **`GameHistoryStore`를 거치지 않는다.** 사용자 대국 기록(`game_history` 디렉터리)에 섞이면
 * 초기화(`wipeToFreshInstall`)로 지워지거나 U-4 저장 상한 계산에 끼어드는 등, 사용자 데이터가
 * 아닌 것이 사용자 데이터의 규칙을 따르게 된다. 대신 이 파일이 에셋에서 직접 읽어 화면
 * 컴포지션 시점에 `GameHistoryScreen`이 목록 맨 앞에 끼워 넣는다 — 실기 대국 기록이 하나도
 * 없어도, 초기화를 해도 **항상 같은 모습으로** 다시 나타난다.
 *
 * ⚠️ **원본은 실제로 두어진 대국이다** — 에뮬레이터에서 캐릭터 레벨4(2점 접힘, 흑) 대
 * 레벨5(2점 접어줌, 백)로 137수, 백 0.5집승. 손으로 지어낸 기보가 아니다.
 */
const val ReferenceGameHistoryId = "reference-handicap2-top-tier-ai"

private const val ReferenceGameAssetPath = "reference_games/handicap2_top_tier_ai.json"
private val ReferenceGameBoardSize = BoardSize(13)

fun referenceGameHistoryEntry(): GameHistoryEntry =
    GameHistoryEntry(
        id = ReferenceGameHistoryId,
        // 실제로 그 판이 벌어진 시각 — 항상 목록 맨 앞에 끼워 넣으므로 정렬에는 쓰이지 않는다.
        playedAtMillis = 1789870977417L,
        boardSize = ReferenceGameBoardSize.value,
        ruleset = Ruleset.Japanese,
        komi = 6.5,
        handicapCount = 2,
        playerSetup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 4),
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 5),
            ),
        ),
        moveCount = 137,
        humanColor = null,
        winner = StoneColor.White,
        isResign = false,
        margin = 0.5,
        hasReplay = true,
        // ⚠️ **고정값이다 — 수정 UI를 안 단다.** 사용자 기록의 한 줄 평과 같은 자리에 나오지만,
        // 이 판별은 `GameHistoryScreen.kt`가 `entry.id == ReferenceGameHistoryId`로 한다.
        note = "사범 꼬북이 관장과 2점 접바둑을 두었으나 0.5집 석패한 기록입니다.",
    )

fun loadReferenceGameReplay(context: Context): GameReplayData? =
    runCatching {
        context.assets.open(ReferenceGameAssetPath).bufferedReader().use { it.readText() }
    }.getOrNull()?.let { raw -> GameReplayCodec.decode(raw, ReferenceGameBoardSize) }
