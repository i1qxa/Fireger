package com.firebasemanager

import com.firebasemanager.bot.FirebaseTelegramBot

fun main() {
    val token = System.getenv("BOT_TOKEN")
        ?: throw IllegalStateException("Укажите переменную окружения BOT_TOKEN (токен бота от @BotFather)")
    try {
        println("Starting Firebase Manager Telegram bot...")
        val telegramBot = FirebaseTelegramBot(token)
        telegramBot.start()
        Thread.currentThread().join()
    } catch (e: Exception) {
        println("Failed to start bot: ${e.message}")
        e.printStackTrace()
        throw e
    }
}
