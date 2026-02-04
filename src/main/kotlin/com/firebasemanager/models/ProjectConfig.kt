package com.firebasemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
    val id: String,
    val displayName: String,
    val serviceAccountPath: String,
    val databaseUrl: String? = null,
    val createdAt: String? = null
)

@Serializable
data class ProjectsConfig(
    val projects: List<ProjectConfig> = emptyList()
)
