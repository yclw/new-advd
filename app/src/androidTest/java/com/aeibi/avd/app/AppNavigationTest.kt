package com.aeibi.avd.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.aeibi.avd.feature.projects.R as ProjectsR
import com.aeibi.avd.feature.settings.R as SettingsR
import com.aeibi.avd.feature.workbench.R as WorkbenchR
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
        assertWorkspaceVisible()

        Espresso.pressBack()
        composeRule.onNodeWithText(projectName).assertIsDisplayed()
        openButton(ProjectsR.string.projects_settings)
        openButton(SettingsR.string.settings_appearance_title)
        composeRule.onNodeWithText(
            composeRule.activity.getString(SettingsR.string.settings_theme_mode_title)
        ).assertIsDisplayed()
    }

    private fun assertWorkspaceVisible() {
        composeRule.onNodeWithText(
            composeRule.activity.getString(WorkbenchR.string.workbench_placeholder)
        ).assertIsDisplayed()
    }

    private fun openButton(resourceId: Int) {
        composeRule.onNodeWithText(composeRule.activity.getString(resourceId)).performClick()
    }
}
