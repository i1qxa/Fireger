package com.firebasemanager.routes

import com.firebasemanager.models.ProjectCreateRequest
import com.firebasemanager.models.ProjectUpdateRequest
import com.firebasemanager.services.ProjectService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.projectsRoutes() {
    route("/api/projects") {
        get {
            try {
                val projects = ProjectService.getAllProjects()
                call.respond(projects)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        get("/{id}") {
            try {
                val projectId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Project ID is required")
                )
                
                val project = ProjectService.getProject(projectId)
                if (project != null) {
                    call.respond(project)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Project not found")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        post {
            try {
                val multipartData = call.receiveMultipart()
                var displayName: String? = null
                var serviceAccountJson: String? = null
                var databaseUrl: String? = null
                
                multipartData.forEachPart { part ->
                    when (part) {
                        is io.ktor.http.content.PartData.FormItem -> {
                            when (part.name) {
                                "displayName" -> displayName = part.value
                                "databaseUrl" -> databaseUrl = part.value.takeIf { it.isNotBlank() }
                            }
                        }
                        is io.ktor.http.content.PartData.FileItem -> {
                            if (part.name == "serviceAccount") {
                                serviceAccountJson = part.streamProvider().bufferedReader().readText()
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (displayName.isNullOrBlank() || serviceAccountJson.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "displayName and serviceAccount are required")
                    )
                }
                
                val project = ProjectService.createProject(
                    displayName = displayName!!,
                    serviceAccountJson = serviceAccountJson!!,
                    databaseUrl = databaseUrl?.takeIf { it.isNotBlank() }
                )
                
                call.respond(HttpStatusCode.Created, project)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        put("/{id}") {
            try {
                val projectId = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Project ID is required")
                )
                
                val request = call.receive<ProjectUpdateRequest>()
                val project = ProjectService.updateProject(
                    projectId = projectId,
                    displayName = request.displayName,
                    databaseUrl = request.databaseUrl
                )
                
                call.respond(project)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        delete("/{id}") {
            try {
                val projectId = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Project ID is required")
                )
                
                ProjectService.deleteProject(projectId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to (e.message ?: "Project not found"))
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        post("/{id}/test") {
            try {
                val projectId = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Project ID is required")
                )
                
                val isConnected = ProjectService.testConnection(projectId)
                call.respond(mapOf("connected" to isConnected))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
