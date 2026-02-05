package com.firebasemanager.routes

import com.firebasemanager.bot.FirebaseTelegramBot
import com.firebasemanager.db.AppUser
import com.firebasemanager.db.StoredProject
import com.firebasemanager.db.deleteProjectByProjectId
import com.firebasemanager.db.getAppUser
import com.firebasemanager.db.getProjectByProjectId
import com.firebasemanager.db.getOrCreateAppUser
import com.firebasemanager.db.insertHistoryEntry
import com.firebasemanager.db.insertProject
import com.firebasemanager.db.deleteAppUser
import com.firebasemanager.db.listAllAppUsers
import com.firebasemanager.db.deleteLinkTemplate
import com.firebasemanager.db.insertLinkTemplate
import com.firebasemanager.db.listAllProjects
import com.firebasemanager.db.listHistoryEntries
import com.firebasemanager.db.listLinkTemplates
import com.firebasemanager.db.setAccessRequested
import com.firebasemanager.db.updateAppUserRole
import com.firebasemanager.db.updateProjectMetaByProjectId
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class MeResponse(val userId: Long, val role: String, val name: String?, val avatarUrl: String?, val accessRequested: Boolean)

@Serializable
private data class UserListItem(val userId: Long, val name: String?, val avatarUrl: String?, val role: String, val accessRequestedAt: Long?)

@Serializable
private data class UsersResponse(val users: List<UserListItem>)

@Serializable
private data class HistoryEntryItem(val id: Int, val date: String, val userName: String, val actionText: String, val projectId: String)

@Serializable
private data class HistoryResponse(val history: List<HistoryEntryItem>)

@Serializable
private data class TemplateListItem(val id: Int, val name: String, val link: String)

@Serializable
private data class TemplatesResponse(val templates: List<TemplateListItem>)

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

/** Gets or creates app user from initData; returns null if invalid. */
private suspend fun ApplicationCall.requireAppUser(bot: FirebaseTelegramBot): AppUser? {
    val initData = initDataOrNull() ?: return null
    val tgUser = TelegramInitData.validateAndGetUser(bot.getBotToken(), initData) ?: return null
    getOrCreateAppUser(tgUser.id, chatId = null, name = tgUser.fullName, avatarUrl = tgUser.photoUrl)
    return getAppUser(tgUser.id)
}

private fun requireRoleNotNone(appUser: AppUser?): Boolean = appUser != null && appUser.role != "none"

private fun roleLabel(role: String): String = when (role) {
    "admin" -> "Админ"
    "user" -> "Пользователь"
    else -> "Нет доступа"
}

fun Routing.miniappApiRoutes(bot: FirebaseTelegramBot) {
    route("/api") {
            get("/me") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                context.respond(MeResponse(
                    userId = appUser.userId,
                    role = appUser.role,
                    name = appUser.name,
                    avatarUrl = appUser.avatarUrl,
                    accessRequested = appUser.accessRequested
                ))
            }
            post("/access-request") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@post
                }
                setAccessRequested(appUser.userId)
                context.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
            get("/projects") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }
                val list = listAllProjects().map { p ->
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
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
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
                if (getProjectByProjectId(projectId) != null) {
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
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@put
                }
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@put
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull() ?: emptyMap()
                val name = body["name"]?.trim() ?: project.name ?: ""
                val notionUrl = body["notionUrl"]?.trim() ?: project.notionUrl ?: ""
                var status = body["status"]?.trim() ?: project.status
                if (status !in ALLOWED_STATUSES) status = project.status
                updateProjectMetaByProjectId(projectId, name, notionUrl, status)
                context.respond(HttpStatusCode.OK, mapOf("id" to projectId))
            }
            delete("/projects/{id}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@delete
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@delete
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@delete
                }
                if (getProjectByProjectId(projectId) == null) {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@delete
                }
                deleteProjectByProjectId(projectId)
                context.respond(HttpStatusCode.OK, mapOf("id" to projectId))
            }
            get("/projects/{id}/rules") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@get
                }
                val project = getProjectByProjectId(projectId) ?: run {
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
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@put
                }
                val project = getProjectByProjectId(projectId) ?: run {
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
                        val userName = getAppUser(userId)?.name ?: userId.toString()
                        insertHistoryEntry(userId, userName, projectId, "rules_change", "Смена правил чтения: " + (if (read) "разрешено" else "запрещено"))
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
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing project id"))
                    return@get
                }
                val project = getProjectByProjectId(projectId) ?: run {
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
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
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
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
                    return@put
                }
                val oldValue = context.request.queryParameters["oldValue"]
                val valueJson = context.receiveText()
                val respondCall = context
                runBlocking {
                    try {
                        RtdbDataService.setField(project.databaseUrl, project.serviceAccountJson, "", fieldName, valueJson)
                        val userName = getAppUser(userId)?.name ?: userId.toString()
                        val newValPreview = valueJson.take(200).let { if (it.length < valueJson.length) "$it…" else it }
                        insertHistoryEntry(userId, userName, projectId, "field_change", "Изменение ссылки $fieldName с «${oldValue ?: "?"}» на «$newValPreview»")
                        respondCall.respond(HttpStatusCode.OK, mapOf("field" to fieldName))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to set field")))
                    }
                }
            }
            get("/users") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }
                val users = listAllAppUsers().map { u ->
                    UserListItem(
                        userId = u.userId,
                        name = u.name,
                        avatarUrl = u.avatarUrl,
                        role = u.role,
                        accessRequestedAt = u.accessRequestedAt
                    )
                }
                context.respond(UsersResponse(users = users))
            }
            put("/users/{userId}") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@put
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@put
                }
                val targetUserId = context.parameters["userId"]?.toLongOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid userId"))
                    return@put
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull()
                val role = body?.get("role")?.trim()?.takeIf { it in setOf("admin", "user", "none") } ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid role (admin, user, none)"))
                    return@put
                }
                if (getAppUser(targetUserId) == null) {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    return@put
                }
                updateAppUserRole(targetUserId, role)
                bot.sendMessageToUser(targetUserId, "Ваши права доступа изменены: ${roleLabel(role)}.")
                context.respond(HttpStatusCode.OK, mapOf("userId" to targetUserId, "role" to role))
            }
            delete("/users/{userId}") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@delete
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@delete
                }
                val targetUserId = context.parameters["userId"]?.toLongOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid userId"))
                    return@delete
                }
                if (getAppUser(targetUserId) == null) {
                    context.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    return@delete
                }
                deleteAppUser(targetUserId)
                context.respond(HttpStatusCode.OK, mapOf("userId" to targetUserId))
            }
            get("/history") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }
                val projectIdFilter = context.request.queryParameters["projectId"]?.takeIf { it.isNotBlank() }
                val entries = listHistoryEntries(projectIdFilter).map { e ->
                    val dateStr = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.ROOT).format(java.util.Date(e.createdAt))
                    HistoryEntryItem(
                        id = e.id,
                        date = dateStr,
                        userName = e.userName,
                        actionText = e.actionText,
                        projectId = e.projectId
                    )
                }
                context.respond(HistoryResponse(history = entries))
            }
            get("/templates") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@get
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }
                val list = listLinkTemplates().map { t -> TemplateListItem(id = t.id, name = t.name, link = t.link) }
                context.respond(TemplatesResponse(templates = list))
            }
            post("/templates") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@post
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@post
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull()
                val name = body?.get("name")?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing name"))
                    return@post
                }
                val link = body?.get("link")?.trim() ?: ""
                val created = insertLinkTemplate(name, link)
                context.respond(HttpStatusCode.OK, TemplateListItem(id = created.id, name = created.name, link = created.link))
            }
            delete("/templates/{id}") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing initData"))
                    return@delete
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@delete
                }
                val id = context.parameters["id"]?.toIntOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid id"))
                    return@delete
                }
                deleteLinkTemplate(id)
                context.respond(HttpStatusCode.OK, mapOf("id" to id))
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
