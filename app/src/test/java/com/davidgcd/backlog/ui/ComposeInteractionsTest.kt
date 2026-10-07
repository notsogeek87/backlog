package com.davidgcd.backlog.ui

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.davidgcd.backlog.ui.components.FilterSortSheet
import com.davidgcd.backlog.ui.components.SheetChoice
import com.davidgcd.backlog.ui.components.SheetSection
import com.davidgcd.backlog.ui.components.SwipeAction
import com.davidgcd.backlog.ui.components.SwipeActionRow
import com.davidgcd.backlog.ui.ranking.RankItem
import com.davidgcd.backlog.ui.ranking.RankingList
import com.davidgcd.backlog.ui.theme.BacklogTheme
import com.davidgcd.backlog.ui.theme.Glass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gestures of the new screens, run under Robolectric: the swipe, the filter sheet and the ranking drag. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ComposeInteractionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `swiping a row to the right runs its start action and the row stays in place`() {
        var triggered = 0
        compose.setContent {
            BacklogTheme {
                SwipeActionRow(
                    startAction = SwipeAction("Terminé", Icons.Filled.Archive, Glass.Green) { triggered++ },
                    endAction = null,
                ) { Text("Hades", modifier = Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
        compose.onNodeWithText("Hades").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(1, triggered)
        compose.onNodeWithText("Hades").assertIsDisplayed()
    }

    @Test
    fun `the filter sheet applies a choice and closes from its result button`() {
        var chosen: String? = null
        var dismissed = false
        compose.setContent {
            BacklogTheme {
                FilterSortSheet(
                    sections = listOf(
                        SheetSection.Choices("Statut", listOf(SheetChoice("Tous", true) {}, SheetChoice("Terminé", false) { chosen = "Terminé" })),
                    ),
                    resultLabel = "Voir 3 résultats",
                    onReset = null,
                    onDismiss = { dismissed = true },
                )
            }
        }
        // The sheet lives in its own window, where Robolectric does not route touches: trigger the click action itself.
        compose.onNodeWithText("Terminé").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Terminé", chosen)
        compose.onNodeWithText("Voir 3 résultats").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(dismissed)
    }

    @Test
    fun `dragging a ranking row by its handle moves it down once, on drop`() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val items = listOf("a", "b", "c", "d").map { RankItem(it, "Titre $it") }
        compose.setContent {
            BacklogTheme {
                RankingList(items = items, onMove = { from, to -> moves += from to to }, onItemClick = {})
            }
        }
        // The handle is merged into its card for TalkBack: reach it in the unmerged tree.
        compose.onAllNodesWithContentDescription("Déplacer", useUnmergedTree = true)[0].performTouchInput {
            down(center)
            moveBy(Offset(0f, 250f))
            up()
        }
        compose.waitForIdle()
        assertEquals("exactly one write, on drop", 1, moves.size)
        assertEquals(0, moves.single().first)
        assertTrue("moved down the list", moves.single().second > 0)
    }

    @Test
    fun `the top and bottom buttons of a row jump straight to the ends`() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val items = listOf("a", "b", "c").map { RankItem(it, "Titre $it") }
        compose.setContent {
            BacklogTheme {
                RankingList(items = items, onMove = { from, to -> moves += from to to }, onItemClick = {})
            }
        }
        // The second row: « tout en haut » then « tout en bas ».
        compose.onAllNodesWithContentDescription("Mettre tout en haut")[1].performClick()
        compose.onAllNodesWithContentDescription("Mettre tout en bas")[1].performClick()
        assertEquals(listOf(1 to 0, 1 to 2), moves)
    }
}
