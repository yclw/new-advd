package com.aeibi.avd.domain.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.core.model.ProjectIcon
import com.aeibi.avd.data.project.project.ProjectDataError
import com.aeibi.avd.data.project.project.ProjectIconDataChange
import com.aeibi.avd.data.project.project.ProjectRepository
import java.text.Normalizer
import javax.inject.Inject
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

class CreateProjectUseCase @Inject constructor(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(request: CreateProjectRequest): OperationResult<Project> {
        val profile = when (val result = normalizeProfile(request.name, request.description)) {
            is ProfileValidation.Invalid -> return OperationResult.Failure(result.error)
            is ProfileValidation.Valid -> result.profile
        }
        return projectRepository.createProject(
            profile.name,
            profile.description,
            request.icon
        ).mapProjectError()
    }
}

data class CreateProjectRequest(val name: String, val description: String, val icon: ProjectIcon?)

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

sealed interface ProjectIconChange {
    data object Keep : ProjectIconChange
    data object Remove : ProjectIconChange
    data class Replace(val icon: ProjectIcon) : ProjectIconChange
}

data class UpdateProjectProfileRequest(
    val projectId: ProjectId,
    val name: String,
    val description: String,
    val iconChange: ProjectIconChange
)

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

private fun ProjectIconChange.toData(): ProjectIconDataChange = when (this) {
    ProjectIconChange.Keep -> ProjectIconDataChange.Keep
    ProjectIconChange.Remove -> ProjectIconDataChange.Remove
    is ProjectIconChange.Replace -> ProjectIconDataChange.Replace(icon)
}

private fun <T> OperationResult<T>.mapProjectError(): OperationResult<T> = when (this) {
    is OperationResult.Success -> this
    is OperationResult.Failure -> OperationResult.Failure(
        when (error) {
            ProjectDataError.NameAlreadyExists -> ProjectDomainError.NameAlreadyExists
            ProjectDataError.ProjectNotFound -> ProjectDomainError.ProjectNotFound
            ProjectDataError.StorageUnavailable -> ProjectDomainError.StorageUnavailable
            ProjectDataError.InvalidIcon -> ProjectDomainError.InvalidIcon
            else -> ProjectDomainError.StorageUnavailable
        }
    )
}
