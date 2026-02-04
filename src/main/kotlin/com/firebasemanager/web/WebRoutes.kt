package com.firebasemanager.web

import com.firebasemanager.services.ProjectService
import com.firebasemanager.services.RulesService
import com.firebasemanager.web.templates.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.*

fun Route.webRoutes() {
    get("/") {
        call.respondHtml(HttpStatusCode.OK) {
            indexPage()
        }
    }
    
    get("/projects") {
        val projects = ProjectService.getAllProjects()
        call.respondHtml(HttpStatusCode.OK) {
            projectsPage(projects)
        }
    }
    
    get("/projects/add") {
        call.respondHtml(HttpStatusCode.OK) {
            projectAddPage()
        }
    }
    
    get("/projects/{id}") {
        val projectId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        
        val project = ProjectService.getProject(projectId)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        
        // Правила и данные будут загружены через API при открытии соответствующих вкладок
        call.respondHtml(HttpStatusCode.OK) {
            projectDetailPage(project, null, null)
        }
    }
    
    get("/projects/{id}/edit") {
        val projectId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        
        val project = ProjectService.getProject(projectId)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        
        call.respondHtml(HttpStatusCode.OK) {
            projectEditPage(project)
        }
    }
}
