package com.firebasemanager.routes

import com.firebasemanager.bot.FirebaseTelegramBot
import com.firebasemanager.db.StoredProject
import com.firebasemanager.db.deleteProject
import com.firebasemanager.db.getProject
import com.firebasemanager.db.insertProject
import com.firebasemanager.db.listProjectsByUser
import com.firebasemanager.db.updateProjectMeta
import com.firebasemanager.firebase.FirebaseManager
import com.firebasemanager.services.RtdbDataService
import com.firebasemanager.services.RulesService
import com.firebasemanager.webapp.TelegramInitData
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

private val ALLOWED_STATUSES = setOf("Development", "Ready", "Moderation", "Product", "Ban")

/** Извлекает initData из заголовка X-Telegram-Init-Data или Authorization: tma <initData>. */
private fun ApplicationCall.initDataOrNull(): String? {
    request.header("X-Telegram-Init-Data")?.takeIf { it.isNotBlank() }?.let { return it }
    val auth = request.header("Authorization") ?: return null
    val prefix = "tma "
    if (auth.length > prefix.length && auth.startsWith(prefix, ignoreCase = true)) {
        return auth.drop(prefix.length).trim().takeIf { it.isNotBlank() }
    }
    return null
}

suspend fun ApplicationCall.requireUserId(bot: FirebaseTelegramBot): Long? {
    val initData = initDataOrNull() ?: return null
    return TelegramInitData.validateAndGetUserId(bot.getBotToken(), initData)
}

fun Routing.miniappApiRoutes(bot: FirebaseTelegramBot) {
    route("/api") {
            get("/projects") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                val list = listProjectsByUser(userId).map { p ->
                    mapOf(
                        "id" to p.projectId,
                        "displayName" to p.displayName,
                        "databaseUrl" to p.databaseUrl,
                        "name" to p.name,
                        "notionUrl" to p.notionUrl,
                        "status" to p.status
                    )
                }
                context.respond(mapOf("projects" to list))
            }
            post("/projects") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@post
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull()
                val key = body?.get("key") ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing key"))
                    return@post
                }
                val validation = FirebaseManager.validateServiceAccount(key)
                if (!validation.isValid) {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to (validation.error ?: "Invalid key")))
                    return@post
                }
                val projectId = validation.projectId!!
                if (getProject(userId, projectId) != null) {
                    context.respond(HttpStatusCode.Conflict, mapOf("error" to "Проект с этим ключом уже добавлен. Один ключ Firebase — один проект в приложении."))
                    return@post
                }
                val bundleId = body["bundleId"]?.trim()?.takeIf { it.isNotEmpty() }
                val name = body["name"]?.trim()?.takeIf { it.isNotEmpty() }
                val notionUrl = body["notionUrl"]?.trim()?.takeIf { it.isNotEmpty() }
                var status = body["status"]?.trim() ?: "Development"
                if (status !in ALLOWED_STATUSES) status = "Development"
                insertProject(userId, projectId, bundleId, name, notionUrl, status, key)
                context.respond(HttpStatusCode.OK, mapOf(
                    "id" to projectId,
                    "databaseUrl" to "https://$projectId-default-rtdb.firebaseio.com/",
                    "name" to name,
                    "notionUrl" to notionUrl,
                    "status" to status
                ))
            }
            put("/projects/{id}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@put
                }
                val project = getProject(userId, projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@put
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull() ?: emptyMap()
                val name = body["name"]?.trim() ?: project.name ?: ""
                val notionUrl = body["notionUrl"]?.trim() ?: project.notionUrl ?: ""
                var status = body["status"]?.trim() ?: project.status
                if (status !in ALLOWED_STATUSES) status = project.status
                updateProjectMeta(userId, projectId, name, notionUrl, status)
                context.respond(HttpStatusCode.OK, mapOf("id" to projectId))
            }
            delete("/projects/{id}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@delete
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@delete
                }
                if (getProject(userId, projectId) == null) {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@delete
                }
                deleteProject(userId, projectId)
                context.respond(HttpStatusCode.OK, mapOf("id" to projectId))
            }
            get("/projects/{id}/rules") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@get
                }
                val project = getProject(userId, projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@get
                }
                val respondCall = context
                runBlocking {
                    try {
                        val rulesJson = RulesService.getRules(project.projectId, project.databaseUrl, project.serviceAccountJson)
                        val (read, _) = parseReadWrite(rulesJson)
                        respondCall.respond(mapOf("read" to read))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to get rules")))
                    }
                }
            }
            put("/projects/{id}/rules") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@put
                }
                val project = getProject(userId, projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@put
                }
                val body = runCatching { context.receive<Map<String, Boolean?>>() }.getOrNull()
                val read = body?.get("read") ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing read"))
                    return@put
                }
                val respondCall = context
                runBlocking {
                    try {
                        val currentRules = RulesService.getRules(project.projectId, project.databaseUrl, project.serviceAccountJson)
                        val (_, write) = parseReadWrite(currentRules)
                        val newRules = """{"rules":{".read":$read,".write":$write}}"""
                        RulesService.updateRules(project.projectId, project.databaseUrl, project.serviceAccountJson, newRules)
                        respondCall.respond(mapOf("read" to read))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to update rules")))
                    }
                }
            }
            get("/projects/{id}/data") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@get
                }
                val project = getProject(userId, projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@get
                }
                val respondCall = context
                runBlocking {
                    try {
                        val dataJson = RtdbDataService.getData(project.databaseUrl, project.serviceAccountJson, "/")
                        respondCall.respondText(dataJson, ContentType.Application.Json)
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to get data")))
                    }
                }
            }
            put("/projects/{id}/data/{fieldName}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@put
                }
                val fieldName = context.parameters["fieldName"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing fieldName"))
                    return@put
                }
                val project = getProject(userId, projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@put
                }
                val valueJson = context.receiveText()
                val respondCall = context
                runBlocking {
                    try {
                        RtdbDataService.setField(project.databaseUrl, project.serviceAccountJson, "", fieldName, valueJson)
                        respondCall.respond(HttpStatusCode.OK, mapOf("field" to fieldName))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to set field")))
                    }
                }
            }
        }
}

private fun parseReadWrite(rulesJson: String): Pair<Boolean, Boolean> {
    return try {
        val root = json.parseToJsonElement(rulesJson).jsonObject
        val rules = root["rules"]?.jsonObject ?: return true to true
        val read = rules[".read"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val write = rules[".write"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        read to write
    } catch (_: Exception) {
        true to true
    }
}
