package com.aeibi.avd.data.project.version

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ErrorCode

sealed class VersionDataError(override val code: ErrorCode, override val retryable: Boolean) :
    AppError {
    data object RepositoryUnavailable : VersionDataError(
        ErrorCode("version_repository_unavailable"),
        true
    )
    data object RepositoryAlreadyExists : VersionDataError(
        ErrorCode("version_repository_already_exists"),
        false
    )
    data object InvalidSnapshot : VersionDataError(ErrorCode("version_snapshot_invalid"), false)
    data object SnapshotNotFound : VersionDataError(ErrorCode("version_snapshot_not_found"), false)
    data object NoChanges : VersionDataError(ErrorCode("version_no_changes"), false)
    data object InvalidSnapshotType : VersionDataError(
        ErrorCode("version_snapshot_type_invalid"),
        false
    )
    data object HistoryCorrupted : VersionDataError(ErrorCode("version_history_corrupted"), false)
    data object HistoryMetadataUnsupported : VersionDataError(
        ErrorCode("version_history_metadata_unsupported"),
        false
    )
    data object OperationFailed : VersionDataError(ErrorCode("version_operation_failed"), true)
    data object InitializationStateInvalid : VersionDataError(
        ErrorCode("version_initialization_state_invalid"),
        false
    )
}
