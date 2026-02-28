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

@Serializable
private data class ErrorResponse(val error: String)

@Serializable
private data class ProjectListItem(
    val id: String,
    val displayName: String,
    val databaseUrl: String,
    val name: String?,
    val notionUrl: String?,
    val status: String,
    val bundleId: String?,
    val developerId: Long?,
    val developerName: String?
)

@Serializable
private data class ProjectsResponse(val projects: List<ProjectListItem>)

@Serializable
private data class IdResponse(val id: String)

@Serializable
private data class CreateProjectResponse(val id: String, val databaseUrl: String, val name: String?, val notionUrl: String?, val status: String)

@Serializable
private data class SuccessResponse(val success: Boolean)

@Serializable
private data class RulesResponse(val read: Boolean)

@Serializable
private data class FieldResponse(val field: String)

@Serializable
private data class UserRoleResponse(val userId: Long, val role: String)

@Serializable
private data class UserIdResponse(val userId: Long)

@Serializable
private data class TemplateIdResponse(val id: Int)

@Serializable
private data class CreateProjectRequest(
    val key: String,
    val bundleId: String? = null,
    val name: String? = null,
    val notionUrl: String? = null,
    val status: String? = null,
    val developerId: String? = null
)

@Serializable
private data class UpdateProjectRequest(
    val name: String? = null,
    val notionUrl: String? = null,
    val status: String? = null,
    val developerId: String? = null,
    val bundleId: String? = null
)

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
                try {
                    val appUser = context.requireAppUser(bot) ?: run {
                        context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                        return@get
                    }
                    context.respond(MeResponse(
                        userId = appUser.userId,
                        role = appUser.role,
                        name = appUser.name,
                        avatarUrl = appUser.avatarUrl,
                        accessRequested = appUser.accessRequested
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                    context.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Internal error"))
                }
            }
            post("/access-request") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                    return@post
                }
                setAccessRequested(appUser.userId)
                val initData = context.initDataOrNull()
                val tgUser = initData?.let { TelegramInitData.validateAndGetUser(bot.getBotToken(), it) }
                val userName = tgUser?.fullName ?: appUser.name ?: "—"
                val userInfo = buildString {
                    append("Пользователь просит доступ к приложению.\n\n")
                    append("Имя: $userName\n")
                    append("ID: ${appUser.userId}\n")
                    tgUser?.username?.takeIf { it.isNotBlank() }?.let { append("Username: @$it\n") }
                }
                bot.sendMessageToAdmins(userInfo)
                context.respond(HttpStatusCode.OK, SuccessResponse(true))
            }
            get("/projects") {
                try {
                    val userId = context.requireUserId(bot) ?: run {
                        context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                        return@get
                    }
                    if (!requireRoleNotNone(getAppUser(userId))) {
                        context.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                        return@get
                    }
                    var list = listAllProjects().map { p ->
                        val developerName = p.developerId?.let { getAppUser(it)?.name }
                        ProjectListItem(
                            id = p.projectId,
                            displayName = p.displayName,
                            databaseUrl = p.databaseUrl,
                            name = p.name,
                            notionUrl = p.notionUrl,
                            status = p.status,
                            bundleId = p.bundleId,
                            developerId = p.developerId,
                            developerName = developerName
                        )
                    }
                    val developerIdFilter = context.request.queryParameters["developerId"]?.toLongOrNull()
                    val bundleIdFilter = context.request.queryParameters["bundleId"]?.trim()?.takeIf { it.isNotEmpty() }
                    if (developerIdFilter != null) {
                        list = list.filter { it.developerId == developerIdFilter }
                    }
                    if (bundleIdFilter != null) {
                        list = list.filter { (it.bundleId ?: "").contains(bundleIdFilter, ignoreCase = true) }
                    }
                    context.respond(ProjectsResponse(projects = list))
                } catch (e: Exception) {
                    e.printStackTrace()
                    context.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to load projects"))
                }
            }
            post("/projects") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                    return@post
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                    return@post
                }
                val body = runCatching { context.receive<CreateProjectRequest>() }.getOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                    return@post
                }
                val key = body.key
                val validation = FirebaseManager.validateServiceAccount(key)
                if (!validation.isValid) {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse(validation.error ?: "Invalid key"))
                    return@post
                }
                val projectId = validation.projectId!!
                if (getProjectByProjectId(projectId) != null) {
                    context.respond(HttpStatusCode.Conflict, ErrorResponse("Проект с этим ключом уже добавлен. Один ключ Firebase — один проект в приложении."))
                    return@post
                }
                val bundleId = body.bundleId?.trim()?.takeIf { it.isNotEmpty() }
                val name = body.name?.trim()?.takeIf { it.isNotEmpty() }
                val notionUrl = body.notionUrl?.trim()?.takeIf { it.isNotEmpty() }
                var status = body.status?.trim() ?: "Development"
                if (status !in ALLOWED_STATUSES) status = "Development"
                val developerId = body.developerId?.trim()?.toLongOrNull()
                insertProject(userId, projectId, bundleId, name, notionUrl, status, key, developerId)
                context.respond(HttpStatusCode.OK, CreateProjectResponse(
                    id = projectId,
                    databaseUrl = "https://$projectId-default-rtdb.firebaseio.com/",
                    name = name,
                    notionUrl = notionUrl,
                    status = status
                ))
            }
            put("/projects/{id}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                    return@put
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing project id"))
                    return@put
                }
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))
                    return@put
                }
                val body = runCatching { context.receive<UpdateProjectRequest>() }.getOrNull() ?: UpdateProjectRequest()
                val name = body.name?.trim() ?: project.name ?: ""
                val notionUrl = body.notionUrl?.trim() ?: project.notionUrl ?: ""
                var status = body.status?.trim() ?: project.status
                if (status !in ALLOWED_STATUSES) status = project.status
                val developerId = body.developerId?.trim()?.toLongOrNull()
                val updateDeveloper = body.developerId != null
                val bundleId = body.bundleId?.trim()?.takeIf { it.isNotEmpty() }
                val updateBundleId = body.bundleId != null
                updateProjectMetaByProjectId(projectId, name, notionUrl, status, developerId, updateDeveloper, bundleId, updateBundleId)
                context.respond(HttpStatusCode.OK, IdResponse(projectId))
            }
            delete("/projects/{id}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                    return@delete
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                    return@delete
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing project id"))
                    return@delete
                }
                if (getProjectByProjectId(projectId) == null) {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))
                    return@delete
                }
                deleteProjectByProjectId(projectId)
                context.respond(HttpStatusCode.OK, IdResponse(projectId))
            }
            get("/projects/{id}/rules") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@get
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@get
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing project id"))
                    return@get
                }
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse( "Project not found"))
                    return@get
                }
                val respondCall = context
                runBlocking {
                    try {
                        val rulesJson = RulesService.getRules(project.projectId, project.databaseUrl, project.serviceAccountJson)
                        val (read, _) = parseReadWrite(rulesJson)
                        respondCall.respond(RulesResponse(read = read))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to get rules"))
                    }
                }
            }
            put("/projects/{id}/rules") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing initData"))
                    return@put
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing project id"))
                    return@put
                }
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse( "Project not found"))
                    return@put
                }
                val body = runCatching { context.receive<Map<String, Boolean?>>() }.getOrNull()
                val read = body?.get("read") ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing read"))
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
                        respondCall.respond(RulesResponse(read = read))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to update rules"))
                    }
                }
            }
            get("/projects/{id}/data") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@get
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@get
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing project id"))
                    return@get
                }
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse( "Project not found"))
                    return@get
                }
                val respondCall = context
                runBlocking {
                    try {
                        val dataJson = RtdbDataService.getData(project.databaseUrl, project.serviceAccountJson, "/")
                        respondCall.respondText(dataJson, ContentType.Application.Json)
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, ErrorResponse( (e.message ?: "Failed to get data")))
                    }
                }
            }
            put("/projects/{id}/data/{fieldName}") {
                val userId = context.requireUserId(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@put
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@put
                }
                val projectId = context.parameters["id"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing project id"))
                    return@put
                }
                val fieldName = context.parameters["fieldName"] ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing fieldName"))
                    return@put
                }
                val project = getProjectByProjectId(projectId) ?: run {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse( "Project not found"))
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
                        respondCall.respond(HttpStatusCode.OK, FieldResponse(field = fieldName))
                    } catch (e: Exception) {
                        respondCall.respond(HttpStatusCode.InternalServerError, ErrorResponse( (e.message ?: "Failed to set field")))
                    }
                }
            }
            get("/users") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@get
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
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
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@put
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@put
                }
                val targetUserId = context.parameters["userId"]?.toLongOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing or invalid userId"))
                    return@put
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull()
                val role = body?.get("role")?.trim()?.takeIf { it in setOf("admin", "user", "none") } ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing or invalid role (admin, user, none)"))
                    return@put
                }
                if (getAppUser(targetUserId) == null) {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse( "User not found"))
                    return@put
                }
                updateAppUserRole(targetUserId, role)
                bot.sendMessageToUser(targetUserId, "Ваши права доступа изменены: ${roleLabel(role)}.")
                context.respond(HttpStatusCode.OK, UserRoleResponse(userId = targetUserId, role = role))
            }
            delete("/users/{userId}") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@delete
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@delete
                }
                val targetUserId = context.parameters["userId"]?.toLongOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing or invalid userId"))
                    return@delete
                }
                if (getAppUser(targetUserId) == null) {
                    context.respond(HttpStatusCode.NotFound, ErrorResponse( "User not found"))
                    return@delete
                }
                deleteAppUser(targetUserId)
                context.respond(HttpStatusCode.OK, UserIdResponse(userId = targetUserId))
            }
            get("/history") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@get
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
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
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@get
                }
                if (!requireRoleNotNone(getAppUser(userId))) {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@get
                }
                val list = listLinkTemplates().map { t -> TemplateListItem(id = t.id, name = t.name, link = t.link) }
                context.respond(TemplatesResponse(templates = list))
            }
            post("/templates") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@post
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@post
                }
                val body = runCatching { context.receive<Map<String, String?>>() }.getOrNull()
                val name = body?.get("name")?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing name"))
                    return@post
                }
                val link = body?.get("link")?.trim() ?: ""
                val created = insertLinkTemplate(name, link)
                context.respond(HttpStatusCode.OK, TemplateListItem(id = created.id, name = created.name, link = created.link))
            }
            delete("/templates/{id}") {
                val appUser = context.requireAppUser(bot) ?: run {
                    context.respond(HttpStatusCode.Unauthorized, ErrorResponse( "Invalid or missing initData"))
                    return@delete
                }
                if (appUser.role != "admin") {
                    context.respond(HttpStatusCode.Forbidden, ErrorResponse( "Access denied"))
                    return@delete
                }
                val id = context.parameters["id"]?.toIntOrNull() ?: run {
                    context.respond(HttpStatusCode.BadRequest, ErrorResponse( "Missing or invalid id"))
                    return@delete
                }
                deleteLinkTemplate(id)
                context.respond(HttpStatusCode.OK, TemplateIdResponse(id = id))
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
