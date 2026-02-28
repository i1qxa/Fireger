package com.firebasemanager.webapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Проверка подписи initData от Telegram Web App и извлечение user.id / полного объекта user.
 * Алгоритм (по документации Telegram): secret_key = HMAC-SHA256(key="WebAppData", message=bot_token);
 * data_check_string = sorted key=value (без hash), joined \n;
 * computed = HMAC-SHA256(secret_key, data_check_string); сравнить hex с hash.
 */
data class TelegramUser(
    val id: Long,
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null,
    val photoUrl: String? = null
) {
    val fullName: String?
        get() = listOfNotNull(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
}

object TelegramInitData {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Проверяет initData и возвращает полный объект пользователя из user JSON или null при неверной подписи/отсутствии user.
     */
    fun validateAndGetUser(botToken: String, initData: String): TelegramUser? {
        if (initData.isBlank()) return null
        val params = initData.split("&").associate { part ->
            val idx = part.indexOf('=')
            if (idx < 0) part to ""
            else URLDecoder.decode(part.substring(0, idx), Charsets.UTF_8) to
                URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8)
        }
        val hash = params["hash"] ?: return null
        val dataCheckString = params.filter { it.key != "hash" }
            .toSortedMap()
            .entries.joinToString("\n") { "${it.key}=${it.value}" }
        val secretKey = hmacSha256("WebAppData".toByteArray(Charsets.UTF_8), botToken.toByteArray(Charsets.UTF_8))
        val computed = hmacSha256Hex(secretKey, dataCheckString.toByteArray(Charsets.UTF_8))
        if (!computed.equals(hash, ignoreCase = true)) return null
        val userJson = params["user"] ?: return null
        return try {
            val user = json.parseToJsonElement(userJson).jsonObject
            val id = user["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
            TelegramUser(
                id = id,
                firstName = user["first_name"]?.jsonPrimitive?.content,
                lastName = user["last_name"]?.jsonPrimitive?.content,
                username = user["username"]?.jsonPrimitive?.content,
                photoUrl = user["photo_url"]?.jsonPrimitive?.content
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Проверяет initData и возвращает Telegram user id или null при неверной подписи/отсутствии user.
     */
    fun validateAndGetUserId(botToken: String, initData: String): Long? =
        validateAndGetUser(botToken, initData)?.id

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    private fun hmacSha256Hex(key: ByteArray, message: ByteArray): String {
        val hash = hmacSha256(key, message)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
