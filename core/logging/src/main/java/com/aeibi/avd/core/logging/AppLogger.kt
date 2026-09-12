package com.aeibi.avd.core.logging

import com.aeibi.avd.core.common.ProjectId

interface AppLogger {
    fun debug(event: LogEvent)
    fun info(event: LogEvent)
    fun warn(event: LogEvent, error: Throwable? = null)
    fun error(event: LogEvent, error: Throwable)
}

data class LogEvent(
    val name: String,
    val operationId: String? = null,
    val projectId: ProjectId? = null,
    val attributes: Map<String, String> = emptyMap()
)
