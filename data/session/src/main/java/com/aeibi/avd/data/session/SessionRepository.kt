package com.aeibi.avd.data.session

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SessionId
import com.aeibi.avd.core.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSessions(projectId: ProjectId): Flow<List<Session>>
    suspend fun getSession(sessionId: SessionId): Session?
    suspend fun create(projectId: ProjectId, title: String): OperationResult<Session>
}
