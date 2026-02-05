package com.firebasemanager.bot

import com.firebasemanager.db.getAppUserChatId
import com.firebasemanager.db.getOrCreateAppUser
import com.firebasemanager.db.getProject
import com.firebasemanager.db.insertProject
import com.firebasemanager.db.listProjectsByUser
import com.firebasemanager.firebase.FirebaseManager
import com.firebasemanager.services.RtdbDataService
import com.firebasemanager.services.RulesService
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.BotCommand
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.MenuButtonWebApp
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.WebAppInfo
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SetChatMenuButton
import com.pengrad.telegrambot.request.SetMyCommands
import com.pengrad.telegrambot.request.SetWebhook
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FirebaseTelegramBot(
    private val token: String,
    private val webhookBaseUrl: String
) {

    private val bot = TelegramBot(token)
    /** Состояние диалога: ожидание ввода значения поля (команда /link). */
    private val userState = mutableMapOf<Long, BotState>()

    fun start() {
        bot.execute(
            SetMyCommands(
                BotCommand("start", "Начать"),
                BotCommand("projects", "Список проектов"),
                BotCommand("rules", "Права на чтение"),
                BotCommand("link", "Поля в Realtime Database")
            )
        )
        bot.execute(SetWebhook().url("$webhookBaseUrl/webhook"))
        bot.execute(
            SetChatMenuButton().menuButton(
                MenuButtonWebApp("Открыть приложение", WebAppInfo("$webhookBaseUrl/app"))
            )
        )
    }

    fun getBotToken(): String = token

    /** Sends a message to the user by their Telegram user id (uses stored chat_id). No-op if chat_id is unknown. */
    fun sendMessageToUser(userId: Long, text: String) {
        val chatId = getAppUserChatId(userId) ?: return
        sendSafe(chatId, text)
    }

    fun processUpdate(update: Update) {
        try {
            handleUpdate(update)
        } catch (e: Exception) {
            val chatId = update.message()?.chat()?.id() ?: update.callbackQuery()?.message()?.chat()?.id()
            sendSafe(chatId, "Ошибка: ${e.message}")
        }
    }

    private fun handleUpdate(update: Update) {
        val callback = update.callbackQuery()
        if (callback != null) {
            syncAppUser(callback.from().id().toLong(), callback.message()?.chat()?.id(), callback.from().firstName(), callback.from().lastName())
            handleCallback(callback)
            return
        }
        val msg = update.message() ?: return
        val chatId = msg.chat().id()
        val from = msg.from() ?: return
        val userId = from.id().toLong()
        syncAppUser(userId, chatId, from.firstName(), from.lastName())
        val text = msg.text() ?: ""

        when {
            text.startsWith("/") -> handleCommand(chatId, userId, text.trim())
            else -> handleText(chatId, userId, text)
        }
    }

    private fun syncAppUser(userId: Long, chatId: Long?, firstName: String?, lastName: String?) {
        val name = listOfNotNull(firstName, lastName).joinToString(" ").trim().takeIf { it.isNotEmpty() }
        getOrCreateAppUser(userId = userId, chatId = chatId, name = name, avatarUrl = null)
    }

    private fun handleCommand(chatId: Long, userId: Long, command: String) {
        val parts = command.split(" ", limit = 2)
        when (parts[0].lowercase()) {
            "/start" -> {
                val keyboard = com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(
                    arrayOf(
                        com.pengrad.telegrambot.model.request.InlineKeyboardButton("Открыть приложение")
                            .webApp(WebAppInfo("$webhookBaseUrl/app"))
                    )
                )
                bot.execute(SendMessage(chatId, "Добро пожаловать. Нажмите кнопку ниже или откройте приложение через меню бота.").replyMarkup(keyboard))
            }
            "/projects" -> sendProjectList(chatId, userId)
            "/rules" -> {
                val projects = listProjectsByUser(userId)
                if (projects.size == 1) {
                    showRules(chatId, userId, projects.single().projectId)
                } else {
                    sendProjectListForAction(chatId, userId, "rules")
                }
            }
            "/link" -> {
                val projects = listProjectsByUser(userId)
                if (projects.size == 1) {
                    showLink(chatId, userId, projects.single().projectId)
                } else {
                    sendProjectListForAction(chatId, userId, "link")
                }
            }
            else -> sendSafe(chatId, "Неизвестная команда. Доступны: /start, /projects, /rules, /link.")
        }
    }

    private fun handleText(chatId: Long, userId: Long, text: String) {
        when (val state = userState[userId]) {
            is BotState.AwaitingFieldValue -> {
                userState.remove(userId)
                val project = getProject(userId, state.projectId)
                if (project == null) {
                    sendSafe(chatId, "Проект не найден: ${state.projectId}")
                    return
                }
                runBlocking {
                    try {
                        val valueJson = "\"${text.trim().replace("\\", "\\\\").replace("\"", "\\\"")}\""
                        RtdbDataService.setField(project.databaseUrl, project.serviceAccountJson, "", state.fieldName, valueJson)
                        sendSafe(chatId, "Поле ${state.fieldName} обновлено.")
                        showLink(chatId, userId, state.projectId)
                    } catch (e: Exception) {
                        sendSafe(chatId, "Ошибка: ${e.message}")
                    }
                }
                return
            }
            else -> { }
        }

        if (!text.trimStart().startsWith("{")) {
            sendSafe(chatId, "Отправьте JSON ключа сервисного аккаунта или используйте команды /projects, /rules, /link. Проекты можно добавить в приложении (меню бота).")
            return
        }
        try {
            val validation = FirebaseManager.validateServiceAccount(text.trim())
            if (!validation.isValid) {
                sendSafe(chatId, "Неверный ключ: ${validation.error}")
                return
            }
            val projectId = validation.projectId!!
            if (getProject(userId, projectId) != null) {
                sendSafe(chatId, "Проект уже добавлен: $projectId")
                return
            }
            val databaseUrl = "https://$projectId-default-rtdb.firebaseio.com/"
            insertProject(userId, projectId, null, projectId, null, "Development", text.trim())
            sendSafe(chatId, "Проект добавлен: $projectId\nСсылка на БД: $databaseUrl")
        } catch (e: Exception) {
            sendSafe(chatId, "Не удалось добавить проект: ${e.message}")
        }
    }

    private fun handleCallback(callback: CallbackQuery) {
        val data = callback.data() ?: return
        val chatId = callback.message()?.chat()?.id() ?: return
        val userId = callback.from().id().toLong()

        when {
            data.startsWith("rules:") -> {
                val projectId = data.removePrefix("rules:")
                showRules(chatId, userId, projectId)
            }
            data.startsWith("rules_set_read:") -> {
                val rest = data.removePrefix("rules_set_read:")
                val parts = rest.split(":", limit = 2)
                if (parts.size == 2) setReadPermission(chatId, userId, parts[0], parts[1] == "true")
            }
            data.startsWith("link:") -> {
                val projectId = data.removePrefix("link:")
                showLink(chatId, userId, projectId)
            }
            data.startsWith("link_edit_field:") -> {
                val rest = data.removePrefix("link_edit_field:")
                val parts = rest.split(":", limit = 2)
                if (parts.size == 2) {
                    userState[userId] = BotState.AwaitingFieldValue(parts[0], parts[1])
                    sendSafe(chatId, "Введите новое значение для поля ${parts[1]}:")
                }
            }
        }
    }

    private fun sendProjectList(chatId: Long, userId: Long) {
        val projects = listProjectsByUser(userId)
        if (projects.isEmpty()) {
            sendSafe(chatId, "Нет проектов. Добавьте проект в приложении (кнопка меню бота «Открыть приложение»).")
            return
        }
        sendSafe(chatId, "Проекты:\n" + projects.joinToString("\n") { "${it.projectId} — ${it.displayName}" })
    }

    private fun sendProjectListForAction(chatId: Long, userId: Long, action: String) {
        val projects = listProjectsByUser(userId)
        if (projects.isEmpty()) {
            sendSafe(chatId, "Нет проектов. Добавьте проект в приложении (кнопка меню бота).")
            return
        }
        val prefix = if (action == "rules") "rules:" else "link:"
        val keyboard = com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(
            *projects.map { p ->
                arrayOf(com.pengrad.telegrambot.model.request.InlineKeyboardButton(p.projectId).callbackData(prefix + p.projectId))
            }.toTypedArray()
        )
        bot.execute(SendMessage(chatId, "Выберите проект:").replyMarkup(keyboard))
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private fun parseReadWrite(rulesJson: String): Pair<Boolean, Boolean> {
        return try {
            val root = jsonParser.parseToJsonElement(rulesJson).jsonObject
            val rules = root["rules"]?.jsonObject ?: return true to true
            val read = rules[".read"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            val write = rules[".write"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            read to write
        } catch (_: Exception) {
            true to true
        }
    }

    private fun showRules(chatId: Long, userId: Long, projectId: String) {
        val project = getProject(userId, projectId)
        if (project == null) {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        runBlocking {
            try {
                val rules = RulesService.getRules(project.projectId, project.databaseUrl, project.serviceAccountJson)
                val (read, _) = parseReadWrite(rules)
                val status = if (read) "🟢 разрешено" else "🔴 запрещено"
                val button = if (read)
                    com.pengrad.telegrambot.model.request.InlineKeyboardButton("🔴 Запретить чтение").callbackData("rules_set_read:$projectId:false")
                else
                    com.pengrad.telegrambot.model.request.InlineKeyboardButton("🟢 Разрешить чтение").callbackData("rules_set_read:$projectId:true")
                val keyboard = com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(arrayOf(button))
                val text = "Проект $projectId\n\nЧтение (корень): $status"
                bot.execute(SendMessage(chatId, text).replyMarkup(keyboard))
            } catch (e: Exception) {
                sendSafe(chatId, "Ошибка загрузки правил: ${e.message}")
            }
        }
    }

    private fun setReadPermission(chatId: Long, userId: Long, projectId: String, read: Boolean) {
        val project = getProject(userId, projectId) ?: run {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        runBlocking {
            try {
                val currentRules = RulesService.getRules(project.projectId, project.databaseUrl, project.serviceAccountJson)
                val (_, write) = parseReadWrite(currentRules)
                val newRules = """{"rules":{".read":$read,".write":$write}}"""
                RulesService.updateRules(project.projectId, project.databaseUrl, project.serviceAccountJson, newRules)
                sendSafe(chatId, if (read) "Чтение разрешено." else "Чтение запрещено.")
                showRules(chatId, userId, projectId)
            } catch (e: Exception) {
                sendSafe(chatId, "Ошибка: ${e.message}")
            }
        }
    }

    private fun showLink(chatId: Long, userId: Long, projectId: String) {
        val project = getProject(userId, projectId)
        if (project == null) {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        runBlocking {
            try {
                val dataJson = RtdbDataService.getData(project.databaseUrl, project.serviceAccountJson, "/")
                val root = jsonParser.parseToJsonElement(dataJson).jsonObject
                val lines = mutableListOf<String>()
                val buttons = mutableListOf<com.pengrad.telegrambot.model.request.InlineKeyboardButton>()
                if (root != null) {
                    root.forEach { (key, value) ->
                        val str = when (value) {
                            is kotlinx.serialization.json.JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                        lines.add("$key: $str")
                        if (buttons.size < 5) buttons.add(
                            com.pengrad.telegrambot.model.request.InlineKeyboardButton(key).callbackData("link_edit_field:$projectId:$key")
                        )
                    }
                } else {
                    lines.add(dataJson)
                }
                val text = buildString {
                    append("Проект $projectId\n")
                    append(project.databaseUrl)
                    append("\n\nПоля в корне:\n")
                    if (lines.isEmpty()) append("(пусто)")
                    else append(lines.joinToString("\n"))
                }
                val keyboard = if (buttons.isEmpty()) null
                    else com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(
                        *buttons.map { arrayOf(it) }.toTypedArray()
                    )
                if (keyboard != null)
                    bot.execute(SendMessage(chatId, text).replyMarkup(keyboard))
                else
                    sendSafe(chatId, text)
            } catch (e: Exception) {
                sendSafe(chatId, "Ошибка загрузки данных: ${e.message}")
            }
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sendSafe(chatId: Long?, text: String) {
        if (chatId == null) return
        val msg = text.take(4000)
        bot.execute(SendMessage(chatId, msg))
    }
}
