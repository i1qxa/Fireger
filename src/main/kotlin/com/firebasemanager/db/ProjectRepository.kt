package com.firebasemanager.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

data class StoredProject(
    val projectId: String,
    val bundleId: String?,
    val name: String?,
    val notionUrl: String?,
    val status: String,
    val serviceAccountJson: String,
    val databaseUrl: String,
    val createdAt: Long?
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: bundleId?.takeIf { it.isNotBlank() } ?: projectId
}

private fun ResultRow.toStoredProject(): StoredProject = StoredProject(
    projectId = this[ProjectTable.projectId],
    bundleId = this[ProjectTable.bundleId],
    name = this[ProjectTable.name],
    notionUrl = this[ProjectTable.notionUrl],
    status = this[ProjectTable.status],
    serviceAccountJson = this[ProjectTable.serviceAccountJson],
    databaseUrl = "https://${this[ProjectTable.projectId]}-default-rtdb.firebaseio.com/",
    createdAt = this[ProjectTable.createdAt]
)

fun listProjectsByUser(userId: Long): List<StoredProject> = transaction {
    ProjectTable.select { ProjectTable.userId eq userId }
        .map { it.toStoredProject() }
}

/** Returns all projects (one per projectId). Used so every user with access sees the same list. */
fun listAllProjects(): List<StoredProject> = transaction {
    ProjectTable.selectAll().map { it.toStoredProject() }.distinctBy { it.projectId }
}

fun getProject(userId: Long, projectId: String): StoredProject? = transaction {
    ProjectTable.select { ProjectTable.userId.eq(userId).and(ProjectTable.projectId.eq(projectId)) }
        .singleOrNull()?.toStoredProject()
}

/** Returns project by projectId (any owner). Used when any user with access can open a project. */
fun getProjectByProjectId(projectId: String): StoredProject? = transaction {
    ProjectTable.select { ProjectTable.projectId eq projectId }.firstOrNull()?.toStoredProject()
}

fun insertProject(
    userId: Long,
    projectId: String,
    bundleId: String?,
    name: String?,
    notionUrl: String?,
    status: String,
    serviceAccountJson: String
) = transaction {
    ProjectTable.insert {
        it[ProjectTable.userId] = userId
        it[ProjectTable.projectId] = projectId
        it[ProjectTable.bundleId] = bundleId
        it[ProjectTable.name] = name
        it[ProjectTable.notionUrl] = notionUrl
        it[ProjectTable.status] = status
        it[ProjectTable.serviceAccountJson] = serviceAccountJson
        it[ProjectTable.createdAt] = System.currentTimeMillis()
    }
}

fun updateProjectMeta(userId: Long, projectId: String, name: String, notionUrl: String, status: String) = transaction {
    ProjectTable.update({ ProjectTable.userId.eq(userId).and(ProjectTable.projectId.eq(projectId)) }) {
        it[ProjectTable.name] = name
        it[ProjectTable.notionUrl] = notionUrl
        it[ProjectTable.status] = status
    }
}

/** Updates all rows with this projectId (so all users see the same meta). */
fun updateProjectMetaByProjectId(projectId: String, name: String, notionUrl: String, status: String) = transaction {
    ProjectTable.update({ ProjectTable.projectId eq projectId }) {
        it[ProjectTable.name] = name
        it[ProjectTable.notionUrl] = notionUrl
        it[ProjectTable.status] = status
    }
}

fun deleteProject(userId: Long, projectId: String) = transaction {
    val escaped = projectId.replace("'", "''")
    exec("DELETE FROM projects WHERE user_id = $userId AND project_id = '$escaped'")
}

/** Deletes all rows with this projectId (removes project for everyone). */
fun deleteProjectByProjectId(projectId: String) = transaction {
    val escaped = projectId.replace("'", "''")
    exec("DELETE FROM projects WHERE project_id = '$escaped'")
}
