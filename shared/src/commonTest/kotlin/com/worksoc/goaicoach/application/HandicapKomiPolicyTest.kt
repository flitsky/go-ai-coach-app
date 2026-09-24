package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.komiAfterHandicapChange
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.HandicapKomi
import com.worksoc.goaicoach.shared.domain.KomiOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 접바둑을 바꿀 때 덤이 어떻게 되는가(refactor backlog #93, 2026-09-24 사용자 결정 —
 * *"핸디캡 대국에서는 0.5점으로 자동 스위칭하기"*).
 *
 * 규칙 셋을 표로 못박는다:
 *  1. 호선 → 접바둑(2점 이상): 지금 덤이 무엇이든 [HandicapKomi].
 *  2. 접바둑 → 다른 접바둑(점수만 바꿈): 덤을 건드리지 않는다 — 사용자가 이미 고친 덤을 존중한다.
 *  3. 접바둑 → 호선: 덤이 [HandicapKomi] 그대로면 [DefaultKomi]로 되돌리고, 사용자가 다른 값으로
 *     바꿔 뒀으면 그대로 둔다.
 * 나머지(호선 → 호선)는 덤을 건드리지 않는다.
 */
class HandicapKomiPolicyTest {

    private data class Case(
        val previousHandicap: Int,
        val newHandicap: Int,
        val currentKomi: Double,
        val expectedKomi: Double,
        val why: String,
    )

    private val cases = listOf(
        // 1. 호선 → 접바둑
        Case(0, 2, DefaultKomi, HandicapKomi, "0→2: 기본 덤이면 0.5로"),
        Case(0, 9, DefaultKomi, HandicapKomi, "0→9: 최대 접바둑도 0.5로"),
        Case(0, 3, 7.5, HandicapKomi, "0→3: 덤이 7.5여도 0.5로(지금 덤이 무엇이든)"),
        Case(0, 3, HandicapKomi, HandicapKomi, "0→3: 이미 0.5면 그대로 0.5"),
        // 2. 접바둑 → 다른 접바둑
        Case(2, 5, HandicapKomi, HandicapKomi, "2→5: 0.5 그대로"),
        Case(2, 5, DefaultKomi, DefaultKomi, "2→5: 사용자가 6.5로 고쳐 뒀으면 그대로"),
        Case(9, 5, 7.5, 7.5, "9→5(판 크기 클램프와 같은 모양): 7.5 그대로"),
        // 3. 접바둑 → 호선
        Case(5, 0, HandicapKomi, DefaultKomi, "5→0: 0.5 그대로였으면 기본 덤으로"),
        Case(5, 0, 7.5, 7.5, "5→0: 사용자가 7.5로 고쳐 뒀으면 그대로"),
        Case(5, 0, DefaultKomi, DefaultKomi, "5→0: 사용자가 6.5로 고쳐 뒀으면 그대로"),
        // 호선 → 호선
        Case(0, 0, DefaultKomi, DefaultKomi, "0→0: 아무 일도 없다"),
        Case(0, 0, HandicapKomi, HandicapKomi, "0→0: 호선에서 사용자가 고른 0.5도 그대로"),
        Case(0, 0, 7.5, 7.5, "0→0: 7.5 그대로"),
    )

    @Test
    fun komiFollowsTheHandicapTransitionTable() {
        val mismatches = cases.mapNotNull { case ->
            val actual = komiAfterHandicapChange(
                previousHandicap = case.previousHandicap,
                newHandicap = case.newHandicap,
                currentKomi = case.currentKomi,
            )
            if (actual == case.expectedKomi) null else "${case.why} — 기대 ${case.expectedKomi}, 실제 $actual"
        }

        assertTrue(
            mismatches.isEmpty(),
            "접바둑 전환 표와 어긋난 칸이 있다:\n" + mismatches.joinToString("\n") { "  - $it" },
        )
    }

    /**
     * 자동으로 바꾼 값이 **드롭다운에 있어야** 사용자가 그 값을 보고, 다른 값으로 되돌릴 수 있다.
     * 되돌아가는 값([DefaultKomi])도 마찬가지다.
     */
    @Test
    fun switchedKomiValuesAreSelectableOptions() {
        assertTrue(HandicapKomi in KomiOptions, "접바둑 덤 $HandicapKomi 가 덤 선택지 $KomiOptions 에 없다")
        assertTrue(DefaultKomi in KomiOptions, "기본 덤 $DefaultKomi 가 덤 선택지 $KomiOptions 에 없다")
        assertEquals(0.5, HandicapKomi, "사용자 결정(2026-09-24)은 0.5다")
    }
}
