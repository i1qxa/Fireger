package com.firebasemanager.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Route.configureRouting() {
    projectsRoutes()
    rulesRoutes()
    dataRoutes()
    realtimeDataRoutes()
}
