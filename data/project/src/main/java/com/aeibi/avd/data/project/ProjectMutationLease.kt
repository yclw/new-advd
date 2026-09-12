package com.aeibi.avd.data.project

import com.aeibi.avd.core.common.ProjectId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class ProjectMutationLease @Inject constructor() {
    private val leases = ConcurrentHashMap<ProjectId, Mutex>()

    suspend fun <T> withLease(projectId: ProjectId, action: suspend () -> T): T =
        leases.computeIfAbsent(projectId) { Mutex() }.withLock { action() }
}
