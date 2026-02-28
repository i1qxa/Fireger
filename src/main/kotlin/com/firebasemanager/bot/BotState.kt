package com.firebasemanager.bot

/**
 * Состояние диалога с пользователем: что бот ожидает в следующем сообщении.
 */
sealed class BotState {
    /** Ожидаем значение для поля в Realtime Database (команда /link). */
    data class AwaitingFieldValue(val projectId: String, val fieldName: String) : BotState()
}
