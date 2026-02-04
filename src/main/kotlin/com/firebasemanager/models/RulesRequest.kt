package com.firebasemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class RulesResponse(
    val rules: String
)

@Serializable
data class RulesUpdateRequest(
    val rules: String
)
