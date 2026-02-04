package com.firebasemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class DataResponse(
    val data: String? = null,
    val path: String
)

@Serializable
data class DataUpdateRequest(
    val data: String
)
