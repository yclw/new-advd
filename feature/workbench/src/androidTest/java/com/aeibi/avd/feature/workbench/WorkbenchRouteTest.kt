package com.aeibi.avd.feature.workbench

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkbenchRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsPlaceholderAndRequestsBack() {
        var exits = 0

        composeRule.setContent {
            WorkbenchRoute(onNavigateBack = { exits++ })
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workbench_placeholder))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workbench_back))
            .performClick()

        assertEquals(1, exits)
    }
}
