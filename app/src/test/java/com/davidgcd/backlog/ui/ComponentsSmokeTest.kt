package com.davidgcd.backlog.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.StatusChoice
import com.davidgcd.backlog.ui.prefs.ThemeMode
import com.davidgcd.backlog.ui.theme.BacklogTheme
import com.davidgcd.backlog.ui.theme.Glass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the design-system components in every theme under Robolectric (no emulator): a missing palette, a crash in a
 * composable or a lost accessibility role shows up here instead of on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ComponentsSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher("has role $role") { it.config.getOrNull(SemanticsProperties.Role) == role }

    @Test
    fun `every theme renders the main components`() {
        var mode by mutableStateOf(ThemeMode.DARK)
        compose.setContent {
            BacklogTheme(mode = mode) {
                Column {
                    GradientButton("Ajouter", onClick = {})
                    GlassPill("Jeux", selected = true, onClick = {})
                    StatCard("12", "Terminé", selected = true, onClick = {})
                    GameListItem(name = "Hades", coverImageId = null, statusLabel = "Joué", onClick = {})
                }
            }
        }
        ThemeMode.entries.forEach { next ->
            mode = next
            compose.waitForIdle()
            compose.onNodeWithText("Ajouter").assertIsDisplayed()
            compose.onNodeWithText("Hades").assertIsDisplayed()
        }
    }

    @Test
    fun `the primary button is announced as a button and answers taps`() {
        var taps = 0
        compose.setContent { BacklogTheme { GradientButton("Ajouter", onClick = { taps++ }) } }
        compose.onNodeWithText("Ajouter").assert(hasRole(Role.Button))
        compose.onNodeWithText("Ajouter").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `pills expose their selected state to accessibility services`() {
        compose.setContent {
            BacklogTheme {
                GlassPill("Jeux", selected = true, onClick = {})
                GlassPill("Livres", selected = false, onClick = {})
            }
        }
        compose.onNodeWithText("Jeux").assertIsSelected()
        compose.onNodeWithText("Jeux").assert(hasRole(Role.RadioButton))
    }

    @Test
    fun `a stat tile filters when tapped`() {
        var clicked = false
        compose.setContent { BacklogTheme { StatCard("3", "Joué", onClick = { clicked = true }) } }
        compose.onNodeWithText("Joué").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `the status badge of a row opens the status menu and picks one`() {
        var picked: String? = null
        compose.setContent {
            BacklogTheme {
                GameListItem(
                    name = "Hades",
                    coverImageId = null,
                    statusLabel = "Backlog",
                    statusChoices = listOf(
                        StatusChoice("Backlog", Glass.Blue, selected = true, onSelect = { picked = "Backlog" }),
                        StatusChoice("Terminé", Glass.Green, selected = false, onSelect = { picked = "Terminé" }),
                    ),
                    onClick = {},
                )
            }
        }
        compose.onNodeWithText("Backlog").performClick()
        compose.onNodeWithText("Terminé").performClick()
        assertEquals("Terminé", picked)
    }

    @Test
    fun `text uses the palette of the active theme`() {
        var mode by mutableStateOf(ThemeMode.DARK)
        val seen = mutableMapOf<ThemeMode, Long>()
        compose.setContent { BacklogTheme(mode = mode) { seen[mode] = Glass.Text.value.toLong(); Text("x") } }
        compose.waitForIdle()
        mode = ThemeMode.LIGHT
        compose.waitForIdle()
        assertTrue("light and dark themes must not share a text colour", seen[ThemeMode.DARK] != seen[ThemeMode.LIGHT])
    }
}
