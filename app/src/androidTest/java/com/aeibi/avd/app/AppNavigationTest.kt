package com.aeibi.avd.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.aeibi.avd.feature.build.R as BuildR
import com.aeibi.avd.feature.chat.R as ChatR
import com.aeibi.avd.feature.preview.R as PreviewR
import com.aeibi.avd.feature.projects.R as ProjectsR
import com.aeibi.avd.feature.settings.R as SettingsR
import com.aeibi.avd.feature.version.R as VersionR
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensWorkbenchAndReturnsToProjects() {
        val projectName = "Navigation${UUID.randomUUID().toString().take(8)}"
        openButton(ProjectsR.string.projects_create)
        composeRule.onNodeWithTag("project_profile_name").performTextInput(projectName)
        composeRule.onNodeWithTag("project_profile_confirm").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(projectName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(projectName).performClick()
        assertChatVisible()
        composeRule.onNodeWithText(
            composeRule.activity.getString(ChatR.string.chat_prompt_label)
        ).performTextInput("Draft survives navigation")

        openChatPage(ChatR.string.chat_open_preview)
        composeRule.onNodeWithText(
            composeRule.activity.getString(PreviewR.string.preview_not_connected)
        ).assertIsDisplayed()
        assertNoPageMenu()
        openButton(PreviewR.string.preview_hide_console)
        composeRule.onNodeWithText(
            composeRule.activity.getString(PreviewR.string.preview_console_empty)
        ).assertDoesNotExist()
        openButton(PreviewR.string.preview_show_console)
        composeRule.onNodeWithText(
            composeRule.activity.getString(PreviewR.string.preview_console_empty)
        ).assertIsDisplayed()
        Espresso.pressBack()
        assertChatVisible()
        composeRule.onNodeWithText(
            composeRule.activity.getString(ChatR.string.chat_prompt_label)
        ).assertTextContains("Draft survives navigation")
        openButton(ChatR.string.chat_clear_draft)
        composeRule.onNodeWithText("Draft survives navigation").assertDoesNotExist()

        openChatPage(ChatR.string.chat_open_build)
        composeRule.onNodeWithText(
            composeRule.activity.getString(BuildR.string.build_output_empty)
        ).assertIsDisplayed()
        assertNoPageMenu()
        composeRule.onNodeWithText(
            composeRule.activity.getString(BuildR.string.build_start)
        ).assertIsNotEnabled()
        Espresso.pressBack()
        assertChatVisible()

        openChatPage(ChatR.string.chat_open_version)
        composeRule.onNodeWithText(
            composeRule.activity.getString(VersionR.string.version_history_empty)
        ).assertIsDisplayed()
        assertNoPageMenu()
        composeRule.onNodeWithText(
            composeRule.activity.getString(VersionR.string.version_create)
        ).assertIsNotEnabled()
        Espresso.pressBack()
        assertChatVisible()

        Espresso.pressBack()
        composeRule.onNodeWithText(projectName).assertIsDisplayed()
        openButton(ProjectsR.string.projects_settings)
        openButton(SettingsR.string.settings_appearance_title)
        composeRule.onNodeWithText(
            composeRule.activity.getString(SettingsR.string.settings_theme_mode_title)
        ).assertIsDisplayed()
    }

    private fun assertChatVisible() {
        composeRule.onNodeWithText(
            composeRule.activity.getString(ChatR.string.chat_empty_title)
        ).assertIsDisplayed()
    }

    private fun openButton(resourceId: Int) {
        composeRule.onNodeWithText(composeRule.activity.getString(resourceId)).performClick()
    }

    private fun openChatPage(resourceId: Int) {
        openButton(ChatR.string.chat_pages)
        openButton(resourceId)
    }

    private fun assertNoPageMenu() {
        composeRule.onNodeWithText(
            composeRule.activity.getString(ChatR.string.chat_pages)
        ).assertDoesNotExist()
    }
}
