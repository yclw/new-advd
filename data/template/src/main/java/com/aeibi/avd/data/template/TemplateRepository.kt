package com.aeibi.avd.data.template

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.TemplateId
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun observeTemplates(): Flow<List<Template>>
    suspend fun getTemplate(templateId: TemplateId): Template?
    suspend fun loadContent(templateId: TemplateId): OperationResult<TemplateContent>
}

data class Template(val id: TemplateId, val name: String, val description: String)

data class TemplateContent(val files: List<TemplateFile>)

data class TemplateFile(val relativePath: String, val content: String)
