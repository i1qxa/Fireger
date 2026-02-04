package com.firebasemanager.routes

import com.firebasemanager.models.RulesResponse
import com.firebasemanager.models.RulesUpdateRequest
import com.firebasemanager.services.RulesService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

fun Route.rulesRoutes() {
    route("/api/projects/{projectId}/rules") {
        get {
            try {
                val projectId = call.parameters["projectId"] 
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Project ID is required")
                    )
                
                val rules = runBlocking { RulesService.getRules(projectId) }
                call.respond(RulesResponse(rules = rules))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        put {
            try {
                val projectId = call.parameters["projectId"] 
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Project ID is required")
                    )
                
                val request = call.receive<RulesUpdateRequest>()
                runBlocking { RulesService.updateRules(projectId, request.rules) }
                call.respond(HttpStatusCode.OK, mapOf("message" to "Rules updated successfully"))
            } catch (e: UnsupportedOperationException) {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
