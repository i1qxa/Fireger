package com.firebasemanager

import com.firebasemanager.bot.FirebaseTelegramBot
import com.firebasemanager.db.initDatabase
import com.firebasemanager.routes.miniappApiRoutes
import com.google.gson.Gson
import com.pengrad.telegrambot.model.Update
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
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
import java.io.File

private const val KEYSTORE_PASSWORD = "firebase-manager-keystore"
private const val KEY_ALIAS = "firebase-manager"

fun main() {
    val defaultToken = "8526577591:AAEfDi-GQ7EqNWaSAVoqZgXufgN1-Nc4-FU"
    val defaultWebhookBaseUrl = "https://91.228.155.82:8443"
    val token = System.getenv("BOT_TOKEN") ?: defaultToken
    val webhookBaseUrl = System.getenv("WEBHOOK_BASE_URL") ?: defaultWebhookBaseUrl
    val host = "0.0.0.0"

    val keyStoreFile = File("keystore.jks")
    val keyStore = if (keyStoreFile.exists()) {
        java.security.KeyStore.getInstance("JKS").apply {
            keyStoreFile.inputStream().use { load(it, KEYSTORE_PASSWORD.toCharArray()) }
        }
    } else {
        buildKeyStore {
            certificate(KEY_ALIAS) {
                password = KEYSTORE_PASSWORD
                domains = listOf("91.228.155.82", "176.113.83.188", "0.0.0.0", "localhost", "127.0.0.1")
            }
        }.also { it.saveToFile(keyStoreFile, KEYSTORE_PASSWORD) }
    }

    initDatabase()
    val telegramBot = FirebaseTelegramBot(token, webhookBaseUrl)

    val environment = applicationEngineEnvironment {
        connector {
            this.host = host
            port = 8080
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = { KEYSTORE_PASSWORD.toCharArray() },
            privateKeyPassword = { KEYSTORE_PASSWORD.toCharArray() }
        ) {
            this.host = host
            port = 8443
            // TLS 1.2 only: avoids JDK TLS 1.3 bug "Insufficient buffer remaining for AEAD" with some clients (e.g. Telegram)
            enabledProtocols = listOf("TLSv1.2")
        }
        module {
            configureRouting(telegramBot)
            environment.monitor.subscribe(ApplicationStarted) {
                telegramBot.start()
                println("Firebase Manager: HTTP 8080, HTTPS 8443; webhook and menu button set.")
            }
        }
    }

    val server = embeddedServer(Netty, environment) {}
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
