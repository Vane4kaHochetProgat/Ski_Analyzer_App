/**
 * Instrumented Compose-UI tests for `MistakesScreenContent` from
 * [MistakesScreen].
 *
 * Runs on a device/emulator (`./gradlew connectedAndroidTest`) and uses
 * [createComposeRule] to mount the stateless content composable directly —
 * bypassing [MistakesViewModel] so each test owns its [MistakesUiState].
 *
 * Three render paths are exercised:
 *   * `loaded_rendersMistakeCard` — Loaded with one [UserMistakeDetailDto];
 *     asserts the title, description, severity ("high"), localized sport
 *     ("Skiing"), and the date portion of `detected_at` are all on-screen.
 *   * `empty_rendersEmptyState`   — Loaded with `emptyList()`; asserts the
 *     empty-state hint string is shown.
 *   * `error_rendersErrorMessage` — Error("network down"); asserts the
 *     formatted "Couldn't load mistakes: …" message is shown.
 *
 * `MyApplicationTheme` is wrapped around the content so theme-resolved colors
 * and shapes match production rendering.
 */

package com.vane4ka.skianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vane4ka.skianalyzer.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test

class MistakesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleMistake = UserMistakeDetailDto(
        user_mistake_id = 1,
        analysis_id = 1,
        mistake_code = "leaning_back",
        title = "Leaning Back",
        description = "Weight distribution too far back, reducing control",
        severity = "high",
        sport = "skiing",
        icon_code = "warning",
        icon_tint_hex = "#F59E0B",
        detected_at = "2026-05-21T10:00:00Z",
        notes = null
    )

    @Test
    fun loaded_rendersMistakeCard() {
        composeRule.setContent {
            MyApplicationTheme {
                MistakesScreenContent(MistakesUiState.Loaded(listOf(sampleMistake)))
            }
        }
        composeRule.onNodeWithText("Your Mistakes").assertIsDisplayed()
        composeRule.onNodeWithText("Leaning Back").assertIsDisplayed()
        composeRule.onNodeWithText("Weight distribution too far back, reducing control").assertIsDisplayed()
        composeRule.onNodeWithText("high").assertIsDisplayed()
        composeRule.onNodeWithText("Skiing").assertIsDisplayed()
        composeRule.onNodeWithText("2026-05-21").assertIsDisplayed()
    }

    @Test
    fun empty_rendersEmptyState() {
        composeRule.setContent {
            MyApplicationTheme {
                MistakesScreenContent(MistakesUiState.Loaded(emptyList()))
            }
        }
        composeRule.onNodeWithText(
            "No mistakes recorded yet — analyze a video to see your results here."
        ).assertIsDisplayed()
    }

    @Test
    fun error_rendersErrorMessage() {
        composeRule.setContent {
            MyApplicationTheme {
                MistakesScreenContent(MistakesUiState.Error("network down"))
            }
        }
        composeRule.onNodeWithText("Couldn't load mistakes: network down").assertIsDisplayed()
    }
}
