package com.firebasemanager

import com.firebasemanager.routes.configureRouting
import com.firebasemanager.web.webRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json

fun main() {
    try {
        println("Starting Firebase Manager server...")
        embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
            .start(wait = true)
    } catch (e: Exception) {
        println("Failed to start server: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

fun Application.module() {
    try {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
    install(CORS) {
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowMethod(io.ktor.http.HttpMethod.Patch)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        anyHost()
    }
    
    install(WebSockets) {
        pingPeriod = java.time.Duration.ofSeconds(15)
        timeout = java.time.Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        configureRouting()
        webRoutes()
        
        // Static files
        staticResources("/static", "static")
    }
        
        println("Server configured successfully. Listening on http://0.0.0.0:8080")
    } catch (e: Exception) {
        println("Error configuring application: ${e.message}")
        e.printStackTrace()
        throw e
    }
}
