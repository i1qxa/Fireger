package com.firebasemanager

import com.firebasemanager.bot.FirebaseTelegramBot
import com.firebasemanager.db.initDatabase
import com.firebasemanager.routes.miniappApiRoutes
import com.google.gson.Gson
import com.pengrad.telegrambot.model.Update
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun main() {
    val token = System.getenv("BOT_TOKEN")
        ?: throw IllegalStateException("Укажите переменную окружения BOT_TOKEN (токен бота от @BotFather)")
    val webhookBaseUrl = System.getenv("WEBHOOK_BASE_URL")
        ?: throw IllegalStateException("Укажите переменную окружения WEBHOOK_BASE_URL (HTTPS URL, например https://xxx.ngrok.io)")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = "0.0.0.0"

    initDatabase()
    val telegramBot = FirebaseTelegramBot(token, webhookBaseUrl)

    val server = embeddedServer(Netty, port = port, host = host) {
        configureRouting(telegramBot)
        environment.monitor.subscribe(ApplicationStarted) {
            telegramBot.start()
            println("Firebase Manager: webhook and menu button set.")
        }
    }
    server.start(wait = true)
}

fun Application.configureRouting(bot: FirebaseTelegramBot) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    routing {
        post("/webhook") {
            val body = context.receiveText()
            context.respond(io.ktor.http.HttpStatusCode.OK)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val update = parseUpdate(body)
                    if (update != null) bot.processUpdate(update)
                } catch (e: Exception) {
                    println("Webhook process error: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        get("/app") {
            context.respondRedirect("/app/")
        }
        get("/app/") {
            val stream = context.application::class.java.classLoader.getResourceAsStream("static/app/index.html")
            if (stream == null) {
                context.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                context.respondBytes(stream.use { it.readBytes() }, io.ktor.http.ContentType.Text.Html)
            }
        }
        miniappApiRoutes(bot)
    }
}

private val gson = Gson()

private fun parseUpdate(body: String): Update? = try {
    gson.fromJson(body, Update::class.java)
} catch (_: Exception) {
    null
}
