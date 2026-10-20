package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

/**
 * 5계층 — **캐릭터의 착수 템포**(백로그 #212, 사용자 2026-10-20). 캐릭터 성향 도메인의 둘째 칸이다(첫 칸은 [HumanPlayStyle]).
 *
 * 캐릭터는 탐색하지 않는다 — 사람 정책을 한 번 보고(0.3~0.5초) 수를 고른다. 그래서 템포는 수를 **고르는** 시간이 아니라
 * **두는 순간**이다: 수는 지금처럼 고르고, 돌이 판에 놓이는 때만 맞춘다(사용자가 고른 안 「생각 시간 연출 — 세기는 그대로」).
 * 무엇을 두는지는 그대로라 캐릭터의 급수가 바뀌지 않는다.
 *
 * | 구간([TempoBand]) | 이번 수의 번호 ÷ 판의 자리 수 | 9줄 | 13줄 | 19줄 | 두기까지 |
 * | --- | --- | --- | --- | --- | --- |
 * | 빠르게 | 10% 이하 | 1~8수 | 1~16수 | 1~36수 | 1초 |
 * | 적당히 | 10% 초과 ~ 20% 이하 | 9~16수 | 17~33수 | 37~72수 | 2초 |
 * | 제 템포 | 그 뒤 | 17수~ | 34수~ | 73수~ | 캐릭터마다([ownMillis]) |
 *
 * - **형세가 [SeriousDeficit]집 이상 불리하면** 빠르게·적당히 구간에서도 제 템포로 둔다 — 빠른 착수를 푼다(사용자).
 *   매 수 다시 본다 — 따라붙으면 다시 빠르게 둔다.
 * - **상한은 사용자의 「최대 탐색 시간」**이다 — 어느 구간이든 그보다 끌지 않는다. 「끔」이면 상한이 없다.
 * - 엔진이 이미 그만큼 썼으면 더 기다리지 않는다. 기다림은 앱이 연출한다(`application.autoai`의 AI 차례) — 이 타입은 숫자만 정한다.
 *
 * ⚠️ 구간의 비율(10%·20%)은 **여기 하나**에 둔다. 기권·통과가 보는 국면([GamePhase] — 30·60·80%)과는 다른 잣대다 —
 * 이 두 구간은 그 「초반」 안을 잘게 나눈 것이다.
 *
 * ## 누가 무엇을 정했나
 * - **사용자**(2026-10-20): 판의 자리 수의 10%마다 구간 · 10%까지 1초 · 20%까지 2초 · 10집 이상 불리하면 푼다 · 그 뒤는 캐릭터마다.
 * - **스레드가 정했다**(사용자 승인 전): 캐릭터마다의 제 템포([moveTempo]의 표) · 형세는 가장 최근의 신경망 평가로 · 매 수 다시 본다.
 *
 * @property ownMillis 이 캐릭터가 「제 템포」 구간에서 두기까지 걸리는 시간(밀리초).
 */
data class MoveTempo(val ownMillis: Long) {
    /** 이번 수([moveNumber], 1부터)가 어느 구간인가. [ownScoreLead]는 AI 기준 점수차(뒤지면 −) — 모르면 형세로 풀지 않는다. */
    fun bandAt(moveNumber: Int, boardSize: BoardSize, ownScoreLead: Double?): TempoBand {
        val points = boardSize.value * boardSize.value
        val isSerious = ownScoreLead != null && ownScoreLead <= -SeriousDeficit
        return when {
            isSerious -> TempoBand.Own
            moveNumber <= points * QuickUntilShare -> TempoBand.Quick
            moveNumber <= points * BriskUntilShare -> TempoBand.Brisk
            else -> TempoBand.Own
        }
    }

    /** [band]에서 두기까지의 시간 — 사용자의 최대 탐색 시간([capMillis], 「끔」이면 `null`)을 넘지 않는다. */
    fun targetMillis(band: TempoBand, capMillis: Long?): Long {
        val millis = when (band) {
            TempoBand.Quick -> QuickMillis
            TempoBand.Brisk -> BriskMillis
            TempoBand.Own -> ownMillis
        }
        return capMillis?.let(millis::coerceAtMost) ?: millis
    }

    companion object {
        /** 「빠르게」 구간의 끝 — 판의 자리 수의 이만큼 번째 수까지(19줄 36수). */
        const val QuickUntilShare: Double = 0.10

        /** 「적당히」 구간의 끝(19줄 72수). */
        const val BriskUntilShare: Double = 0.20

        const val QuickMillis: Long = 1_000L
        const val BriskMillis: Long = 2_000L

        /** 이만큼(집) 이상 뒤지면 빠른 착수를 푼다. */
        const val SeriousDeficit: Double = 10.0

        /**
         * [player](AI) 기준의 지금 형세 — 형세 기록에서 **가장 최근의 신경망 평가**(뒤지면 −). 없으면 `null`.
         * 국소 계가는 척도가 달라 쓰지 않는다([HopelessPosition]과 같은 선).
         */
        fun ownScoreLead(player: StoneColor, snapshots: List<ScoreSnapshot>): Double? {
            val latest = snapshots
                .filter { snapshot -> snapshot.source.isNetworkEstimate && snapshot.whiteScoreLead != null }
                .maxByOrNull { snapshot -> snapshot.moveNumber }
                ?: return null
            val whiteScoreLead = latest.whiteScoreLead ?: return null
            return if (player == StoneColor.White) whiteScoreLead else -whiteScoreLead
        }
    }
}

/** 착수 템포의 구간 — [MoveTempo.bandAt]. */
enum class TempoBand {
    /** 판의 10%까지 — 1초. */
    Quick,

    /** 판의 20%까지 — 2초. */
    Brisk,

    /** 그 뒤, 또는 크게 뒤질 때 — 캐릭터마다. */
    Own,
}

/**
 * 이 단계의 캐릭터의 착수 템포, 없으면 `null`(지금처럼 엔진이 답하는 대로 곧바로 둔다).
 *
 * | 캐릭터 | 문하생 판다 | 문하생 돌뫼 | 수제자 반상 | 사범 꼬북 | 관장 천원 |
 * | --- | --- | --- | --- | --- | --- |
 * | 제 템포 | 2초 | 2.5초 | 3초 | 4초 | 5초 |
 *
 * 셀수록 신중하다 — 스레드가 정했다(사용자가 고른 안의 예 「판다 2초 ~ 천원 5초」 사이를 메웠다). 기본 최대 탐색 시간이 5초라
 * 천원은 기본 설정에서 그 상한에 닿는다.
 *
 * 숨겨 둔 그룹(초급·중급·고급)과 급수를 직접 정한 상대(승급 대국, #219)는 **중립**이다 — 캐릭터가 아니다.
 * ⚠️ 키는 단계 설정이다 — 이 층(5계층)은 캐릭터(`BotCharacter`, 6계층)를 모른다([humanPlayStyle]과 같은 선).
 */
fun PlayLevelSetting.moveTempo(): MoveTempo? {
    if (group != PlayLevelGroup.FastBeginner) return null
    return when (safeLevel) {
        1 -> MoveTempo(ownMillis = 2_000L)
        2 -> MoveTempo(ownMillis = 2_500L)
        3 -> MoveTempo(ownMillis = 3_000L)
        4 -> MoveTempo(ownMillis = 4_000L)
        5 -> MoveTempo(ownMillis = 5_000L)
        else -> null
    }
}
