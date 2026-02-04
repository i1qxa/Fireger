package com.firebasemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectUpdateRequest(
    val displayName: String? = null,
    val databaseUrl: String? = null
)
