package com.firebasemanager.bot

import com.firebasemanager.firebase.FirebaseManager
import com.firebasemanager.services.RtdbDataService
import com.firebasemanager.services.RulesService
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.BotCommand
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SetMyCommands
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FirebaseTelegramBot(token: String) {

    private val bot = TelegramBot(token)
    private val sessions = mutableMapOf<Long, UserSession>()
    private val sessionTimeoutMs = 30 * 60 * 1000L // 30 минут

    fun start() {
        bot.execute(
            SetMyCommands(
                BotCommand("start", "Начать"),
                BotCommand("projects", "Список проектов"),
                BotCommand("rules", "Права на чтение"),
                BotCommand("link", "Поля в Realtime Database")
            )
        )
        bot.setUpdatesListener(UpdatesListener { updates ->
            for (update in updates) {
                try {
                    handleUpdate(update)
                } catch (e: Exception) {
                    val chatId = update.message()?.chat()?.id() ?: update.callbackQuery()?.message()?.chat()?.id()
                    sendSafe(chatId, "Ошибка: ${e.message}")
                }
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        })
        println("Firebase Telegram bot started (session timeout: 30 min).")
    }

    private fun getOrCreateSession(chatId: Long): UserSession {
        var session = sessions[chatId]
        if (session != null && session.isExpired(sessionTimeoutMs)) {
            sessions.remove(chatId)
            session = null
        }
        if (session == null) {
            session = UserSession(chatId = chatId)
            sessions[chatId] = session
        }
        session.touch()
        return session
    }

    private fun handleUpdate(update: Update) {
        val callback = update.callbackQuery()
        if (callback != null) {
            handleCallback(callback)
            return
        }
        val msg = update.message() ?: return
        val chatId = msg.chat().id()
        val session = getOrCreateSession(chatId)
        val text = msg.text() ?: ""

        when {
            text.startsWith("/") -> handleCommand(chatId, session, text.trim())
            else -> handleText(chatId, session, text)
        }
    }

    private fun handleCommand(chatId: Long, session: UserSession, command: String) {
        val parts = command.split(" ", limit = 2)
        when (parts[0].lowercase()) {
            "/start" -> sendSafe(chatId, """
                Добро пожаловать в Firebase Manager Bot.
                
                У каждого пользователя свои проекты. Они хранятся только в памяти: если 30 минут не будет сообщений — сеанс сбросится и проекты нужно будет добавить снова.
                
                Отправьте ключ (JSON сервисного аккаунта) — проект добавится в текущий сеанс.
                
                Команды:
                /projects — список ваших проектов в сеансе
                /rules — права на чтение в Realtime Database
                /link — поля в Realtime Database (field_a, field_b и т.д.)
            """.trimIndent())
            "/projects" -> sendProjectList(chatId, session)
            "/rules" -> {
                if (session.projects.size == 1) {
                    showRules(chatId, session, session.projects.keys.single())
                } else {
                    sendProjectListForAction(chatId, session, "rules")
                }
            }
            "/link" -> {
                if (session.projects.size == 1) {
                    showLink(chatId, session, session.projects.keys.single())
                } else {
                    sendProjectListForAction(chatId, session, "link")
                }
            }
            else -> sendSafe(chatId, "Неизвестная команда. Доступны: /start, /projects, /rules, /link.")
        }
    }

    private fun handleText(chatId: Long, session: UserSession, text: String) {
        when (val state = session.state) {
            is BotState.AwaitingFieldValue -> {
                session.state = null
                val project = session.projects[state.projectId]
                if (project == null) {
                    sendSafe(chatId, "Проект не найден: ${state.projectId}")
                    return
                }
                runBlocking {
                    try {
                        val valueJson = "\"${text.trim().replace("\\", "\\\\").replace("\"", "\\\"")}\""
                        RtdbDataService.setField(project.databaseUrl, project.serviceAccountJson, "", state.fieldName, valueJson)
                        sendSafe(chatId, "Поле ${state.fieldName} обновлено.")
                        showLink(chatId, session, state.projectId)
                    } catch (e: Exception) {
                        sendSafe(chatId, "Ошибка: ${e.message}")
                    }
                }
                return
            }
            else -> { }
        }

        if (!text.trimStart().startsWith("{")) {
            sendSafe(chatId, "Отправьте JSON ключа сервисного аккаунта или используйте команды /projects, /rules, /link")
            return
        }
        try {
            val validation = FirebaseManager.validateServiceAccount(text.trim())
            if (!validation.isValid) {
                sendSafe(chatId, "Неверный ключ: ${validation.error}")
                return
            }
            val projectId = validation.projectId!!
            val databaseUrl = "https://$projectId-default-rtdb.firebaseio.com/"
            val project = InMemoryProject(
                id = projectId,
                displayName = projectId,
                databaseUrl = databaseUrl,
                serviceAccountJson = text.trim()
            )
            session.projects[projectId] = project
            sendSafe(chatId, "Проект добавлен в сеанс: $projectId\nСсылка на БД: $databaseUrl\n(сеанс до 30 мин бездействия)")
        } catch (e: Exception) {
            sendSafe(chatId, "Не удалось добавить проект: ${e.message}")
        }
    }

    private fun handleCallback(callback: CallbackQuery) {
        val data = callback.data() ?: return
        val chatId = callback.message()?.chat()?.id() ?: return
        val session = getOrCreateSession(chatId)

        when {
            data.startsWith("rules:") -> {
                val projectId = data.removePrefix("rules:")
                showRules(chatId, session, projectId)
            }
            data.startsWith("rules_set_read:") -> {
                val rest = data.removePrefix("rules_set_read:")
                val parts = rest.split(":", limit = 2)
                if (parts.size == 2) setReadPermission(chatId, session, parts[0], parts[1] == "true")
            }
            data.startsWith("link:") -> {
                val projectId = data.removePrefix("link:")
                showLink(chatId, session, projectId)
            }
            data.startsWith("link_edit_field:") -> {
                val rest = data.removePrefix("link_edit_field:")
                val parts = rest.split(":", limit = 2)
                if (parts.size == 2) {
                    session.state = BotState.AwaitingFieldValue(parts[0], parts[1])
                    sendSafe(chatId, "Введите новое значение для поля ${parts[1]}:")
                }
            }
        }
    }

    private fun sendProjectList(chatId: Long, session: UserSession) {
        val projects = session.projects.values.toList()
        if (projects.isEmpty()) {
            sendSafe(chatId, "В сеансе нет проектов. Отправьте ключ (JSON сервисного аккаунта). Сеанс — 30 мин бездействия.")
            return
        }
        sendSafe(chatId, "Проекты в сеансе:\n" + projects.joinToString("\n") { "${it.id} — ${it.displayName}" })
    }

    private fun sendProjectListForAction(chatId: Long, session: UserSession, action: String) {
        val projects = session.projects.values.toList()
        if (projects.isEmpty()) {
            sendSafe(chatId, "В сеансе нет проектов. Отправьте ключ (JSON сервисного аккаунта).")
            return
        }
        val prefix = if (action == "rules") "rules:" else "link:"
        val keyboard = com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(
            *projects.map { p ->
                arrayOf(com.pengrad.telegrambot.model.request.InlineKeyboardButton(p.id).callbackData(prefix + p.id))
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

    private fun showRules(chatId: Long, session: UserSession, projectId: String) {
        val project = session.projects[projectId]
        if (project == null) {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        runBlocking {
            try {
                val rules = RulesService.getRules(project.id, project.databaseUrl, project.serviceAccountJson)
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

    private fun setReadPermission(chatId: Long, session: UserSession, projectId: String, read: Boolean) {
        val project = session.projects[projectId] ?: run {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        runBlocking {
            try {
                val currentRules = RulesService.getRules(project.id, project.databaseUrl, project.serviceAccountJson)
                val (_, write) = parseReadWrite(currentRules)
                val newRules = """{"rules":{".read":$read,".write":$write}}"""
                RulesService.updateRules(project.id, project.databaseUrl, project.serviceAccountJson, newRules)
                sendSafe(chatId, if (read) "Чтение разрешено." else "Чтение запрещено.")
                showRules(chatId, session, projectId)
            } catch (e: Exception) {
                sendSafe(chatId, "Ошибка: ${e.message}")
            }
        }
    }

    private fun showLink(chatId: Long, session: UserSession, projectId: String) {
        val project = session.projects[projectId]
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
