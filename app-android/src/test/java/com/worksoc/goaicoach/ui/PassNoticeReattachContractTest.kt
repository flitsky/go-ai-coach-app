package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #201 — 「통과」 알림은 **이 화면이 떠 있는 동안 생긴 통과만** 알린다.
 *
 * 복기(다시보기)로 가면 대국 화면이 컴포지션을 떠난다. 돌아와 다시 붙을 때 「이미 알린 수순」이 0에서 시작하면
 * 양통과로 끝난 판의 마지막 통과를 새 통과로 읽어 **돌아올 때마다** 알림을 다시 띄웠다(2026-09-30 사용자 발견).
 */
class PassNoticeReattachContractTest {

    private val host by lazy { RepoPaths.uiFile("PassNoticeHost.kt").readContractSource() }

    @Test
    fun theAnnouncedMoveCountStartsFromTheCurrentMoveCountWhenTheScreenAttaches() {
        assertTrue(
            "이미 알린 수순이 지금 수순(moveCount)에서 시작하지 않는다 — 복기에서 돌아올 때마다 마지막 통과를 다시 알린다(#201).",
            host.contains("var announcedMoveCount by remember { mutableIntStateOf(moveCount) }"),
        )
        assertFalse(
            "이미 알린 수순이 0에서 시작한다 — 화면이 다시 붙을 때 옛 통과를 새 통과로 읽는다(#201).",
            host.contains("var announcedMoveCount by remember { mutableIntStateOf(0) }"),
        )
    }
}
