package com.aeibi.avd.core.database.project

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects", primaryKeys = ["id"], indices = [Index("nameKey", unique = true)])
data class ProjectRow(
    val id: String,
    val name: String,
    val nameKey: String,
    val description: String,
    val iconRevision: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "project_icons",
    primaryKeys = ["projectId", "revision"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectRow::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectIconRow(val projectId: String, val revision: String, val png: ByteArray)

data class ProjectWithIconRow(@Embedded val project: ProjectRow, val iconPng: ByteArray?)

@Dao
interface ProjectDao {
    @Query(
        """SELECT p.*, i.png AS iconPng FROM projects AS p
           LEFT JOIN project_icons AS i ON i.projectId = p.id AND i.revision = p.iconRevision
           ORDER BY p.updatedAtEpochMillis DESC, p.id ASC"""
    )
    fun observeProjects(): Flow<List<ProjectWithIconRow>>

    @Query(
        """SELECT p.*, i.png AS iconPng FROM projects AS p
           LEFT JOIN project_icons AS i ON i.projectId = p.id AND i.revision = p.iconRevision
           ORDER BY p.updatedAtEpochMillis DESC, p.id ASC"""
    )
    suspend fun getProjects(): List<ProjectWithIconRow>

    @Query(
        """SELECT p.*, i.png AS iconPng FROM projects AS p
           LEFT JOIN project_icons AS i ON i.projectId = p.id AND i.revision = p.iconRevision
           WHERE p.id = :id"""
    )
    suspend fun getProjectWithIcon(id: String): ProjectWithIconRow?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): ProjectRow?

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE nameKey = :nameKey AND id != :exceptId)")
    suspend fun nameExists(nameKey: String, exceptId: String = ""): Boolean

    @Query("SELECT png FROM project_icons WHERE projectId = :projectId AND revision = :revision")
    suspend fun getIcon(projectId: String, revision: String): ByteArray?

    @Insert
    suspend fun insertProject(project: ProjectRow)

    @Insert
    suspend fun insertIcon(icon: ProjectIconRow)

    @Update
    suspend fun updateProject(project: ProjectRow)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Transaction
    suspend fun insert(project: ProjectRow, icon: ProjectIconRow?) {
        insertProject(project)
        if (icon != null) insertIcon(icon)
    }

    @Transaction
    suspend fun update(project: ProjectRow, icon: ProjectIconRow?) {
        if (icon != null) insertIcon(icon)
        updateProject(project)
    }
}
