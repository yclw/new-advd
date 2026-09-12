package com.aeibi.avd.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.model.ProjectStatus
import com.aeibi.avd.core.ui.ContentState
import com.aeibi.avd.domain.project.ProjectIconChange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createDispatchesEnteredProfile() {
        val create = composeRule.activity.getString(R.string.projects_create)
        val chooseIcon = composeRule.activity.getString(R.string.projects_pick_icon)
        val actions = mutableListOf<ProjectsAction>()

        composeRule.setContent {
            ProjectsScreen(
                uiState = ProjectsUiState(content = ContentState.Empty),
                onAction = actions::add
            )
        }

        composeRule.onNodeWithText(create).performClick()
        composeRule.onNodeWithText(chooseIcon).assertIsDisplayed()
        composeRule.onNodeWithTag("project_profile_name").performTextReplacement("New project")
        composeRule.onNodeWithTag("project_profile_description")
            .performTextReplacement("Created from androidTest")
        composeRule.onNodeWithTag("project_profile_confirm").performClick()

        val action = actions.single() as ProjectsAction.CreateConfirmed
        assertEquals("New project", action.name)
        assertEquals("Created from androidTest", action.description)
        assertEquals(null, action.icon)
    }

    @Test
    fun editDispatchesUpdatedProfile() {
        val project = projectItem()
        val actions = mutableListOf<ProjectsAction>()

        setProjectsScreen(project, actions)

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.projects_edit)
        ).performClick()
        composeRule.onNodeWithTag("project_profile_name").performTextReplacement("Renamed project")
        composeRule.onNodeWithTag("project_profile_description")
            .performTextReplacement("Updated from androidTest")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.projects_save)
        ).performClick()

        val action = actions.single() as ProjectsAction.UpdateConfirmed
        assertEquals(project.id, action.projectId)
        assertEquals("Renamed project", action.name)
        assertEquals("Updated from androidTest", action.description)
        assertEquals(ProjectIconChange.Keep, action.iconChange)
    }

    @Test
    fun deleteDispatchesConfirmationForSelectedProject() {
        val project = projectItem()
        val actions = mutableListOf<ProjectsAction>()

        setProjectsScreen(project, actions)

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.projects_delete)
        ).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.projects_delete_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("project_delete_confirm").performClick()

        val action = actions.single() as ProjectsAction.DeleteConfirmed
        assertEquals(project.id, action.projectId)
    }

    private fun setProjectsScreen(project: ProjectItem, actions: MutableList<ProjectsAction>) {
        composeRule.setContent {
            ProjectsScreen(
                uiState = ProjectsUiState(content = ContentState.Content(listOf(project))),
                onAction = actions::add
            )
        }
    }

    private fun projectItem() = ProjectItem(
        id = ProjectId("project-1"),
        name = "Original project",
        description = "Original description",
        status = ProjectStatus.DRAFT,
        icon = null
    )
}
