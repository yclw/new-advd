package com.aeibi.avd.feature.workbench

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aeibi.avd.core.common.ProjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkbenchRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingSectionsKeepsBothRoutesComposed() {
        val active = mutableSetOf<WorkbenchSection>()
        val disposed = mutableListOf<WorkbenchSection>()

        composeRule.setContent {
            WorkbenchRoute(
                projectId = ProjectId("project-1"),
                onNavigateBack = {},
                onVersionsRequested = {},
                onBuildRequested = {},
                onSettingsRequested = {}
            ) { section, _ ->
                DisposableEffect(section) {
                    active += section
                    onDispose {
                        active -= section
                        disposed += section
                    }
                }
                Text(section.name, modifier = Modifier.testTag("pane_${section.name.lowercase()}"))
            }
        }

        composeRule.waitForIdle()
        assertEquals(WorkbenchSection.entries.toSet(), active)
        composeRule.onNodeWithTag("pane_chat").assertIsDisplayed()
        composeRule.onNodeWithTag("pane_preview").assertIsNotDisplayed()

        composeRule.onNodeWithTag("workbench_preview").performClick()
        composeRule.onNodeWithTag("pane_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("pane_chat").assertIsNotDisplayed()
        composeRule.onNodeWithTag("workbench_chat").performClick()
        composeRule.waitForIdle()
        assertEquals(WorkbenchSection.entries.toSet(), active)
        assertTrue(disposed.isEmpty())
    }

    @Test
    fun sectionRestoresAndBackRequestsExit() {
        val projectId = ProjectId("project-1")
        val stateRestorationTester = StateRestorationTester(composeRule)
        var exits = 0
        var versionsId: ProjectId? = null
        var buildId: ProjectId? = null

        stateRestorationTester.setContent {
            WorkbenchRoute(
                projectId = projectId,
                onNavigateBack = { exits++ },
                onVersionsRequested = { versionsId = it },
                onBuildRequested = { buildId = it },
                onSettingsRequested = {}
            ) { section, _ ->
                Text(
                    section.name,
                    modifier = Modifier.testTag("content_${section.name.lowercase()}")
                )
            }
        }

        composeRule.onNodeWithTag("workbench_chat").assertIsSelected()
        composeRule.onNodeWithTag("workbench_preview").performClick()
        stateRestorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("workbench_preview").assertIsSelected()
        composeRule.onNodeWithTag("content_preview").assertIsDisplayed()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workbench_versions))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workbench_build))
            .performClick()
        assertEquals(projectId, versionsId)
        assertEquals(projectId, buildId)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workbench_back))
            .performClick()
        assertEquals(1, exits)
    }
}
