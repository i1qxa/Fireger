package com.firebasemanager.bot

/**
 * Состояние диалога с пользователем: что бот ожидает в следующем сообщении.
 */
sealed class BotState {
    data class AwaitingRules(val projectId: String) : BotState()
    data class AwaitingLink(val projectId: String) : BotState()
}
