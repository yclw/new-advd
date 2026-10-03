package com.aeibi.avd.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

internal const val WORKBENCH_SCENE_ANCHOR = "workbench_scene_anchor"
internal const val WORKBENCH_SCENE_PAGE = "workbench_scene_page"

internal class WorkbenchSceneStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>
    ): Scene<NavKey>? {
        val top = entries.lastOrNull() ?: return null
        val workbench = entries.lastOrNull {
            it.metadata[WORKBENCH_SCENE_ANCHOR] == true
        } ?: return null
        if (top != workbench && top.metadata[WORKBENCH_SCENE_PAGE] != true) return null
        return WorkbenchScene(
            key = workbench.contentKey,
            workbench = workbench,
            top = top,
            previousEntries = entries.dropLast(1)
        )
    }
}

private data class WorkbenchScene(
    override val key: Any,
    val workbench: NavEntry<NavKey>,
    val top: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>
) : Scene<NavKey> {
    override val entries: List<NavEntry<NavKey>> =
        if (top == workbench) listOf(workbench) else listOf(workbench, top)

    override val content: @Composable () -> Unit = {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(Modifier.fillMaxSize()) { workbench.Content() }
                if (top != workbench) {
                    Box(Modifier.fillMaxSize()) { top.Content() }
                }
            }
        ) { measurables, constraints ->
            val pages = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                pages.last().placeRelative(0, 0)
            }
        }
    }
}
