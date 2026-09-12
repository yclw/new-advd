package com.aeibi.avd.data.aiconfig

import com.aeibi.avd.core.common.OperationResult
import kotlinx.coroutines.flow.Flow

interface AiConfigRepository {
    fun observeConfiguration(): Flow<AiConfiguration?>
    suspend fun update(configuration: AiConfiguration): OperationResult<Unit>
}

data class AiConfiguration(val providerType: String, val modelId: String)
