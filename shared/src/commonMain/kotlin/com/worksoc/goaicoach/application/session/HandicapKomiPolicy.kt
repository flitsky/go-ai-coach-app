package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.HandicapKomi

/**
 * 접바둑을 바꿀 때 덤을 어떻게 할지 정한다(refactor backlog #93, 2026-09-24 사용자 결정 —
 * *"핸디캡 대국에서는 0.5점으로 자동 스위칭하기"*). 한국 관습(접바둑 덤 0 또는 0.5)을 따른다.
 *
 * 1. **호선 → 접바둑**: 지금 덤이 무엇이든 [HandicapKomi].
 * 2. **접바둑 → 다른 접바둑**(점수만 바꿈): 덤을 건드리지 않는다 — 사용자가 이미 고친 덤을 존중한다.
 * 3. **접바둑 → 호선**: 덤이 [HandicapKomi] 그대로면 [DefaultKomi]로 되돌리고, 사용자가 다른 값으로
 *    바꿔 뒀으면 그대로 둔다.
 *
 * 전환 뒤에도 사용자는 덤을 자유롭게 고른다 — 이 함수는 **접바둑을 바꾸는 순간에만** 불린다.
 *
 * ⚠️ **이관하지 않는다**(사용자 결정). 이미 *"접바둑 + 덤 6.5"* 로 저장된 설정은 그 사용자가 고른
 * 값이므로, 저장을 읽을 때 이 함수를 태우지 말 것. 진행 중·저장된 대국의 덤도 바꾸지 않는다 —
 * 대국 중에는 대국 설정이 잠겨 있고(`isBoardSetupLockedDuringGame`, `GameMenuSection`),
 * `GameSettingsController.changeHandicapCount`도 대국이 끝났을 때만 받는다.
 *
 * *"접바둑"* 은 **2점 이상**이다 — 접바둑 보정(`HandicapBonusRule.whiteHandicapBonus`, #89·#106)과 같은 선이다.
 */
fun komiAfterHandicapChange(previousHandicap: Int, newHandicap: Int, currentKomi: Double): Double {
    val wasHandicapGame = previousHandicap >= MinHandicapStones
    val isHandicapGame = newHandicap >= MinHandicapStones
    return when {
        !wasHandicapGame && isHandicapGame -> HandicapKomi
        wasHandicapGame && !isHandicapGame && currentKomi == HandicapKomi -> DefaultKomi
        else -> currentKomi
    }
}

/** 접바둑으로 치는 최소 돌 수. 드롭다운은 호선(0)과 2점부터를 내놓는다(`CompactScoringAndBoardSettingsPanel`). */
private const val MinHandicapStones = 2
