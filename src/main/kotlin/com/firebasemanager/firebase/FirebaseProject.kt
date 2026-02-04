package com.firebasemanager.firebase

import com.firebasemanager.models.ProjectConfig

data class FirebaseProject(
    val config: ProjectConfig,
    val isInitialized: Boolean = false,
    val error: String? = null
)
