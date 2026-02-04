package com.firebasemanager.routes

import com.firebasemanager.services.RealtimeDataService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger

fun Route.realtimeDataRoutes() {
    route("/api/projects/{projectId}/data/realtime") {
        webSocket {
            val projectId = call.parameters["projectId"]
                ?: return@webSocket close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Project ID is required"))
            
            val path = call.request.queryParameters["path"] ?: "/"
            
            try {
                // Отправляем начальное сообщение о подключении
                send(Frame.Text(Json.encodeToString(JsonObject.serializer(), 
                    buildJsonObject {
                        put("type", "connected")
                        put("projectId", projectId)
                        put("path", path)
                    }
                )))
                
                // Подписываемся на обновления данных
                val dataFlow = RealtimeDataService.observeData(projectId, path)
                
                // Отправляем обновления клиенту
                dataFlow.onEach { jsonData ->
                    val message = Json.encodeToString(JsonObject.serializer(),
                        buildJsonObject {
                            put("type", "data")
                            put("data", Json.parseToJsonElement(jsonData))
                            put("path", path)
                        }
                    )
                    send(Frame.Text(message))
                }.collect()
                
            } catch (e: Exception) {
                val errorMessage = Json.encodeToString(JsonObject.serializer(),
                    buildJsonObject {
                        put("type", "error")
                        put("error", e.message ?: "Unknown error")
                    }
                )
                send(Frame.Text(errorMessage))
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.message ?: "Error"))
            }
        }
    }
}
