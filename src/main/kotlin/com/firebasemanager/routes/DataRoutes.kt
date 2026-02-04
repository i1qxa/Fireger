package com.firebasemanager.routes

import com.firebasemanager.models.DataResponse
import com.firebasemanager.models.DataUpdateRequest
import com.firebasemanager.services.DataService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.*

fun convertDataToJsonString(data: Any?): String {
    return when (data) {
        null -> "null"
        is String -> "\"${data.replace("\"", "\\\"")}\""
        is Number -> data.toString()
        is Boolean -> data.toString()
        is Map<*, *> -> {
            // Рекурсивно конвертируем Map в JSON
            val entries = data.entries.map { (k, v) ->
                "\"$k\": ${convertDataToJsonString(v)}"
            }
            "{${entries.joinToString(", ")}}"
        }
        is List<*> -> {
            // Рекурсивно конвертируем List в JSON
            val items = data.map { convertDataToJsonString(it) }
            "[${items.joinToString(", ")}]"
        }
        else -> {
            // Для других типов используем простое строковое представление
            try {
                // Пробуем преобразовать в JSON через toString и парсинг
                val jsonStr = data.toString()
                // Если это похоже на JSON, пробуем распарсить
                if (jsonStr.startsWith("{") || jsonStr.startsWith("[")) {
                    Json.parseToJsonElement(jsonStr).toString()
                } else {
                    "\"${jsonStr.replace("\"", "\\\"")}\""
                }
            } catch (e: Exception) {
                // Fallback: простое строковое представление
                "\"${data.toString().replace("\"", "\\\"")}\""
            }
        }
    }
}

fun Route.dataRoutes() {
    route("/api/projects/{projectId}/data") {
        get {
            try {
                val projectId = call.parameters["projectId"] 
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Project ID is required")
                    )
                
                val path = call.request.queryParameters["path"] ?: "/"
                
                val data = runBlocking { DataService.getData(projectId, path) }
                println("DEBUG: Got data from Firebase for project $projectId, path $path: $data")
                
                // Преобразуем данные в JSON строку
                val jsonData = convertDataToJsonString(data)
                println("DEBUG: Converted to JSON string: $jsonData")
                
                call.respond(DataResponse(data = jsonData, path = path))
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
                
                val path = call.request.queryParameters["path"] ?: "/"
                val request = call.receive<DataUpdateRequest>()
                
                val value = DataService.parseJsonValue(request.data)
                runBlocking { DataService.setData(projectId, path, value) }
                
                call.respond(HttpStatusCode.OK, mapOf("message" to "Data updated successfully"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
        
        delete {
            try {
                val projectId = call.parameters["projectId"] 
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Project ID is required")
                    )
                
                val path = call.request.queryParameters["path"] 
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Path parameter is required")
                    )
                
                runBlocking { DataService.deleteData(projectId, path) }
                call.respond(HttpStatusCode.OK, mapOf("message" to "Data deleted successfully"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
