package com.firebasemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectCreateRequest(
    val displayName: String,
    val projectId: String? = null,
    val databaseUrl: String? = null
)
