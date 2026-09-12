package com.aeibi.avd.core.model

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SessionId

data class Session(val id: SessionId, val projectId: ProjectId, val title: String)
