package com.aeibi.avd.domain.settings

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.data.aiconfig.AiConfigRepository
import com.aeibi.avd.data.aiconfig.AiConfiguration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveAiConfigurationUseCase @Inject constructor(
    private val repository: AiConfigRepository
) {
    operator fun invoke(): Flow<AiProviderConfiguration?> =
        repository.observeConfiguration().map { it?.toDomainConfiguration() }
}

class UpdateAiConfigurationUseCase @Inject constructor(private val repository: AiConfigRepository) {
    suspend operator fun invoke(request: UpdateAiConfigurationRequest): OperationResult<Unit> =
        repository.update(AiConfiguration(request.providerType, request.modelId))
}

data class AiProviderConfiguration(val providerType: String, val modelId: String)

data class UpdateAiConfigurationRequest(val providerType: String, val modelId: String)

private fun AiConfiguration.toDomainConfiguration() = AiProviderConfiguration(providerType, modelId)
