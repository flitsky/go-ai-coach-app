package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.savedgame.*

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSessionPromptApplicationTest {
    @Test
    fun checkPlanShowsPromptWhenSavedSessionExists() {
        val snapshot = savedSnapshot()

        val plan = buildSavedSessionCheckPlan(snapshot)

        assertEquals(snapshot, plan.pendingSavedSession)
        assertTrue(plan.shouldShowResumePrompt)
        assertTrue(plan.hasCheckedSavedSession)
    }

    @Test
    fun checkPlanMarksCheckedWhenNoSavedSessionExists() {
        val plan = buildSavedSessionCheckPlan(null)

        assertNull(plan.pendingSavedSession)
        assertFalse(plan.shouldShowResumePrompt)
        assertTrue(plan.hasCheckedSavedSession)
    }

    @Test
    fun dismissPlanClearsPromptButKeepsCheckedState() {
        val plan = buildSavedSessionDismissPlan()

        assertNull(plan.pendingSavedSession)
        assertFalse(plan.shouldShowResumePrompt)
        assertTrue(plan.hasCheckedSavedSession)
    }

    @Test
    fun uiStateAppliesSavedSessionCheckPlan() {
        val snapshot = savedSnapshot()

        val state = SavedSessionUiState()
            .applyPrompt(buildSavedSessionCheckPlan(snapshot))

        assertEquals(snapshot, state.pendingSavedSession)
        assertTrue(state.shouldShowResumePrompt)
        assertTrue(state.hasCheckedSavedSession)
    }

    @Test
    fun uiStateDismissClearsPromptButKeepsCheckedState() {
        val snapshot = savedSnapshot()
        val prompted = SavedSessionUiState()
            .applyPrompt(buildSavedSessionCheckPlan(snapshot))

        val dismissed = prompted.dismiss()

        assertNull(dismissed.pendingSavedSession)
        assertFalse(dismissed.shouldShowResumePrompt)
        assertTrue(dismissed.hasCheckedSavedSession)
    }

    private fun savedSnapshot(): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = GameState.empty()
                .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 1L,
        )
}
