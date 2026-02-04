package com.firebasemanager.bot

import com.firebasemanager.firebase.FirebaseManager
import com.firebasemanager.services.ProjectService
import com.firebasemanager.services.RulesService
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendDocument
import kotlinx.coroutines.runBlocking

class FirebaseTelegramBot(token: String) {

    private val bot = TelegramBot(token)
    private val chatState = mutableMapOf<Long, BotState>()

    fun start() {
        bot.setUpdatesListener(UpdatesListener { updates ->
            for (update in updates) {
                try {
                    handleUpdate(update)
                } catch (e: Exception) {
                    sendSafe(update.message()?.chat()?.id() ?: update.callbackQuery()?.message()?.chat()?.id(), "Ошибка: ${e.message}")
                }
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        })
        println("Firebase Telegram bot started.")
    }

    private fun handleUpdate(update: Update) {
        val callback = update.callbackQuery()
        if (callback != null) {
            handleCallback(callback)
            return
        }
        val msg = update.message() ?: return
        val chatId = msg.chat().id()
        val text = msg.text() ?: ""

        when {
            text.startsWith("/") -> handleCommand(chatId, text.trim())
            else -> handleText(chatId, text)
        }
    }

    private fun handleCommand(chatId: Long, command: String) {
        val parts = command.split(" ", limit = 2)
        when (parts[0].lowercase()) {
            "/start" -> sendSafe(chatId, """
                Добро пожаловать в Firebase Manager Bot.
                
                Отправьте ключ (JSON сервисного аккаунта) текстовым сообщением — проект будет добавлен.
                
                Команды:
                /projects — список проектов
                /rules — просмотр и изменение правил Realtime Database
                /link — просмотр и изменение ссылки на базу
            """.trimIndent())
            "/projects" -> sendProjectList(chatId)
            "/rules" -> sendProjectListForAction(chatId, "rules")
            "/link" -> sendProjectListForAction(chatId, "link")
            else -> sendSafe(chatId, "Неизвестная команда. Доступны: /start, /projects, /rules, /link")
        }
    }

    private fun handleText(chatId: Long, text: String) {
        val state = chatState[chatId]
        when (state) {
            is BotState.AwaitingRules -> {
                chatState.remove(chatId)
                runBlocking {
                    try {
                        RulesService.updateRules(state.projectId, text)
                        sendSafe(chatId, "Правила для проекта ${state.projectId} обновлены.")
                    } catch (e: Exception) {
                        sendSafe(chatId, "Ошибка обновления правил: ${e.message}")
                    }
                }
                return
            }
            is BotState.AwaitingLink -> {
                chatState.remove(chatId)
                val url = text.trim().removeSuffix("/") + "/"
                try {
                    ProjectService.updateProject(state.projectId, displayName = null, databaseUrl = url)
                    sendSafe(chatId, "Ссылка для проекта ${state.projectId} обновлена.")
                } catch (e: Exception) {
                    sendSafe(chatId, "Ошибка обновления ссылки: ${e.message}")
                }
                return
            }
            else -> { }
        }

        // Попытка добавить проект по ключу (JSON)
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
            val project = ProjectService.createProject(
                displayName = validation.projectId!!,
                serviceAccountJson = text.trim(),
                databaseUrl = null
            )
            sendSafe(chatId, "Проект добавлен: ${project.id}\nСсылка на БД: ${project.databaseUrl}")
        } catch (e: Exception) {
            sendSafe(chatId, "Не удалось добавить проект: ${e.message}")
        }
    }

    private fun handleCallback(callback: CallbackQuery) {
        val data = callback.data() ?: return
        val chatId = callback.message()?.chat()?.id() ?: return

        when {
            data.startsWith("rules:") -> {
                val projectId = data.removePrefix("rules:")
                showRules(chatId, projectId)
            }
            data.startsWith("rules_edit:") -> {
                val projectId = data.removePrefix("rules_edit:")
                chatState[chatId] = BotState.AwaitingRules(projectId)
                sendSafe(chatId, "Отправьте новый JSON правил для проекта $projectId")
            }
            data.startsWith("link:") -> {
                val projectId = data.removePrefix("link:")
                showLink(chatId, projectId)
            }
            data.startsWith("link_edit:") -> {
                val projectId = data.removePrefix("link_edit:")
                chatState[chatId] = BotState.AwaitingLink(projectId)
                sendSafe(chatId, "Отправьте новую ссылку на Realtime Database для проекта $projectId (например: https://PROJECT_ID-default-rtdb.firebaseio.com/)")
            }
        }
    }

    private fun sendProjectList(chatId: Long) {
        val projects = ProjectService.getAllProjects()
        if (projects.isEmpty()) {
            sendSafe(chatId, "Нет добавленных проектов. Отправьте ключ (JSON сервисного аккаунта).")
            return
        }
        sendSafe(chatId, "Проекты:\n" + projects.joinToString("\n") { "${it.id} — ${it.displayName}" })
    }

    private fun sendProjectListForAction(chatId: Long, action: String) {
        val projects = ProjectService.getAllProjects()
        if (projects.isEmpty()) {
            sendSafe(chatId, "Нет добавленных проектов. Отправьте ключ (JSON сервисного аккаунта).")
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

    private fun showRules(chatId: Long, projectId: String) {
        val project = ProjectService.getProject(projectId)
        if (project == null) {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        runBlocking {
            try {
                val rules = RulesService.getRules(projectId)
                val editKeyboard = com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(
                    arrayOf(com.pengrad.telegrambot.model.request.InlineKeyboardButton("Изменить правила").callbackData("rules_edit:$projectId"))
                )
                val text = "Правила ($projectId):\n\n<pre>${escapeHtml(rules)}</pre>"
                bot.execute(SendMessage(chatId, text).parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML).replyMarkup(editKeyboard))
            } catch (e: Exception) {
                sendSafe(chatId, "Ошибка загрузки правил: ${e.message}")
            }
        }
    }

    private fun showLink(chatId: Long, projectId: String) {
        val project = ProjectService.getProject(projectId)
        if (project == null) {
            sendSafe(chatId, "Проект не найден: $projectId")
            return
        }
        val url = project.databaseUrl ?: "https://$projectId-default-rtdb.firebaseio.com/"
        val editKeyboard = com.pengrad.telegrambot.model.request.InlineKeyboardMarkup(
            arrayOf(com.pengrad.telegrambot.model.request.InlineKeyboardButton("Изменить ссылку").callbackData("link_edit:$projectId"))
        )
        bot.execute(SendMessage(chatId, "Ссылка ($projectId):\n$url").replyMarkup(editKeyboard))
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sendSafe(chatId: Long?, text: String) {
        if (chatId == null) return
        val msg = text.take(4000)
        bot.execute(SendMessage(chatId, msg))
    }
}
