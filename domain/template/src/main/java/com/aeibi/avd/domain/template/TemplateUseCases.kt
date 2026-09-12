package com.aeibi.avd.domain.template

import com.aeibi.avd.core.common.TemplateId
import com.aeibi.avd.data.template.TemplateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTemplatesUseCase @Inject constructor(
    private val templateRepository: TemplateRepository
) {
    operator fun invoke(): Flow<List<TemplateSummary>> =
        templateRepository.observeTemplates().map { templates ->
            templates.map { TemplateSummary(it.id, it.name, it.description) }
        }
}

class LoadTemplateUseCase @Inject constructor(private val templateRepository: TemplateRepository) {
    suspend operator fun invoke(templateId: TemplateId): TemplateDetails? =
        templateRepository.getTemplate(templateId)?.let {
            TemplateDetails(it.id, it.name, it.description)
        }
}

data class TemplateSummary(val id: TemplateId, val name: String, val description: String)

data class TemplateDetails(val id: TemplateId, val name: String, val description: String)
