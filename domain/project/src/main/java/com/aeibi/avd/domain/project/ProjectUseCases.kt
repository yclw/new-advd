package com.aeibi.avd.domain.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.data.project.project.ProjectDataError
import com.aeibi.avd.data.project.project.ProjectIconData
import com.aeibi.avd.data.project.project.ProjectIconDataChange
import com.aeibi.avd.data.project.project.InitialWorkspaceContent
import com.aeibi.avd.data.project.project.ProjectRepository
import com.aeibi.avd.data.project.version.VersionRepository
import java.text.Normalizer
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveProjectsUseCase @Inject constructor(private val projectRepository: ProjectRepository) {
    operator fun invoke(): Flow<OperationResult<List<Project>>> =
        projectRepository.observeProjects().map { it.mapProjectError() }
}

class RefreshProjectsUseCase @Inject constructor(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(): OperationResult<Unit> =
        projectRepository.refresh().mapProjectError()
}

class CreateDraftProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(request: CreateProjectRequest): OperationResult<Project> {
        val profile = when (val result = normalizeProfile(request.name, request.description)) {
            is ProfileValidation.Invalid -> return OperationResult.Failure(result.error)
            is ProfileValidation.Valid -> result.profile
        }
        return projectRepository.createDraft(
            profile.name,
            profile.description,
            request.icon?.toData()
        ).mapProjectError()
    }
}

class InitializeBlankProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val versionRepository: VersionRepository
) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<Project> {
        when (val prepared = projectRepository.prepareInitialization(projectId, BlankWorkspaceDefinition.content)) {
            is OperationResult.Failure -> return prepared.mapProjectError()
            is OperationResult.Success -> Unit
        }
        try {
            val initialRevision = when (val created = versionRepository.createInitialRevision(projectId)) {
                is OperationResult.Failure -> {
                    projectRepository.resolveInitializationFailure(projectId, created.error)
                    return OperationResult.Failure(ProjectDomainError.InitializationFailed)
                }
                is OperationResult.Success -> created.value
            }
            return when (val published = projectRepository.publishInitialization(projectId, initialRevision.id)) {
                is OperationResult.Success -> published.mapProjectError()
                is OperationResult.Failure -> {
                    projectRepository.resolveInitializationFailure(projectId, published.error)
                    OperationResult.Failure(ProjectDomainError.InitializationFailed)
                }
            }
        } catch (error: CancellationException) {
            throw error
        }
    }
}

class RecoverProjectInitializationUseCase @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<Project?> =
        projectRepository.recoverInitialization(projectId).mapProjectError()
}

private object BlankWorkspaceDefinition {
    val content = InitialWorkspaceContent(emptyList())
}

data class CreateProjectRequest(
    val name: String,
    val description: String,
    val icon: ProjectIconUpload?
)

class UpdateProjectProfileUseCase @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(request: UpdateProjectProfileRequest): OperationResult<Project> {
        val profile = when (val result = normalizeProfile(request.name, request.description)) {
            is ProfileValidation.Invalid -> return OperationResult.Failure(result.error)
            is ProfileValidation.Valid -> result.profile
        }
        return projectRepository.updateProfile(
            projectId = request.projectId,
            name = profile.name,
            description = profile.description,
            iconChange = request.iconChange.toData()
        ).mapProjectError()
    }
}

data class UpdateProjectProfileRequest(
    val projectId: ProjectId,
    val name: String,
    val description: String,
    val iconChange: ProjectIconChange
)

class LoadProjectIconUseCase @Inject constructor(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<ProjectIconContent?> = when (
        val result = projectRepository.loadIcon(projectId).mapProjectError()
    ) {
        is OperationResult.Failure -> result
        is OperationResult.Success -> OperationResult.Success(
            result.value?.let { ProjectIconContent.fromPng(it.copyPngBytes()) }
        )
    }
}

class DeleteProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val closeProjectRuntime: ConfirmProjectRuntimeCloseUseCase
) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<Unit> {
        when (closeProjectRuntime(projectId)) {
            is OperationResult.Failure -> {
                return OperationResult.Failure(ProjectDomainError.RuntimeCloseFailed)
            }
            is OperationResult.Success -> Unit
        }
        return projectRepository.delete(projectId).mapProjectError()
    }
}

private const val MAX_PROJECT_NAME_CODE_POINTS = 60
private const val MAX_PROJECT_DESCRIPTION_CODE_POINTS = 280

private data class NormalizedProjectProfile(val name: String, val description: String)

private sealed interface ProfileValidation {
    data class Valid(val profile: NormalizedProjectProfile) : ProfileValidation
    data class Invalid(val error: ProjectDomainError) : ProfileValidation
}

private fun normalizeProfile(name: String, description: String): ProfileValidation {
    val normalizedName = name.normalized().takeIf {
        it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_PROJECT_NAME_CODE_POINTS
    } ?: return ProfileValidation.Invalid(ProjectDomainError.InvalidName)
    val normalizedDescription = description.normalized()
    if (normalizedDescription.codePointCount(0, normalizedDescription.length) >
        MAX_PROJECT_DESCRIPTION_CODE_POINTS
    ) {
        return ProfileValidation.Invalid(ProjectDomainError.InvalidDescription)
    }
    return ProfileValidation.Valid(NormalizedProjectProfile(normalizedName, normalizedDescription))
}

private fun String.normalized(): String = Normalizer.normalize(trim(), Normalizer.Form.NFC)

private fun ProjectIconUpload.toData(): ProjectIconData = ProjectIconData.fromPng(copyPngBytes())

private fun ProjectIconChange.toData(): ProjectIconDataChange = when (this) {
    ProjectIconChange.Keep -> ProjectIconDataChange.Keep
    ProjectIconChange.Remove -> ProjectIconDataChange.Remove
    is ProjectIconChange.Replace -> ProjectIconDataChange.Replace(icon.toData())
}

private fun <T> OperationResult<T>.mapProjectError(): OperationResult<T> = when (this) {
    is OperationResult.Success -> this
    is OperationResult.Failure -> OperationResult.Failure(
        when (error) {
            ProjectDataError.NameAlreadyExists -> ProjectDomainError.NameAlreadyExists
            ProjectDataError.ProjectNotFound -> ProjectDomainError.ProjectNotFound
            ProjectDataError.StorageUnavailable -> ProjectDomainError.StorageUnavailable
            ProjectDataError.InvalidState -> ProjectDomainError.InvalidState
            ProjectDataError.InvalidIcon -> ProjectDomainError.InvalidIcon
            ProjectDataError.IconTooLarge -> ProjectDomainError.IconTooLarge
            ProjectDataError.InitializationInvalid,
            ProjectDataError.InitializationRecoveryRequired -> ProjectDomainError.InitializationFailed
            else -> ProjectDomainError.StorageUnavailable
        }
    )
}
