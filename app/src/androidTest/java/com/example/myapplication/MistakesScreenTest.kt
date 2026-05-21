package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.ui.theme.MyApplicationTheme
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
