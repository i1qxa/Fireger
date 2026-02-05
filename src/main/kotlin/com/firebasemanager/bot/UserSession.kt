package com.firebasemanager.bot

/**
 * Проект в памяти сессии (ключ не сохраняется на диск).
 */
data class InMemoryProject(
    val id: String,
    val displayName: String,
    var databaseUrl: String,
    val serviceAccountJson: String
)

/**
 * Сессия пользователя. Проекты и состояние живут только в памяти.
 * Сессия сбрасывается при неактивности 30 минут.
 */
data class UserSession(
    val chatId: Long,
    val projects: MutableMap<String, InMemoryProject> = mutableMapOf(),
    var lastActivityMs: Long = System.currentTimeMillis(),
    var state: BotState? = null
) {
    fun touch() {
        lastActivityMs = System.currentTimeMillis()
    }

    fun isExpired(timeoutMs: Long = 30 * 60 * 1000): Boolean =
        System.currentTimeMillis() - lastActivityMs > timeoutMs
}
