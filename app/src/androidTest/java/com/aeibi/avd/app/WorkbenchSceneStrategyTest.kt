package com.aeibi.avd.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkbenchSceneStrategyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun workbenchRemainsComposedBehindProjectPagesAndSettings() {
        var starts = 0
        var disposals = 0
        lateinit var push: (Page) -> Unit
        lateinit var pop: () -> Unit

        composeRule.setContent {
            val backStack = rememberNavBackStack(Page.Workbench)
            SideEffect {
                push = { backStack.add(it) }
                pop = { backStack.removeLastOrNull() }
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                sceneStrategy = WorkbenchSceneStrategy(),
                entryProvider = entryProvider {
                    entry(Page.Workbench, metadata = mapOf(WORKBENCH_SCENE_ANCHOR to true)) {
                        DisposableEffect(Unit) {
                            starts++
                            onDispose { disposals++ }
                        }
                        Box(Modifier.fillMaxSize()) { Text("Workbench") }
                    }
                    Page.entries.filter { it != Page.Workbench }.forEach { page ->
                        entry(page, metadata = mapOf(WORKBENCH_SCENE_PAGE to true)) {
                            Box(Modifier.fillMaxSize()) { Text(page.name) }
                        }
                    }
                }
            )
        }

        fun open(page: Page) {
            composeRule.runOnIdle { push(page) }
            composeRule.onNodeWithText(page.name).assertIsDisplayed()
            composeRule.onNodeWithText("Workbench").assertIsNotDisplayed()
            composeRule.runOnIdle {
                assertEquals(1, starts)
                assertEquals(0, disposals)
            }
        }

        fun back() {
            composeRule.runOnIdle { pop() }
            composeRule.runOnIdle {
                assertEquals(1, starts)
                assertEquals(0, disposals)
            }
        }

        composeRule.onNodeWithText("Workbench").assertIsDisplayed()
        open(Page.Versions)
        back()
        open(Page.Build)
        back()
        open(Page.Settings)
        open(Page.Appearance)
        back()
        open(Page.Language)
        back()
        back()
        composeRule.onNodeWithText("Workbench").assertIsDisplayed()
        assertEquals(0, disposals)
    }
}

@Serializable
private enum class Page : NavKey {
    Workbench,
    Versions,
    Build,
    Settings,
    Appearance,
    Language
}
