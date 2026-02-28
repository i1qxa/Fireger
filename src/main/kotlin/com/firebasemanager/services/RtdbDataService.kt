package com.firebasemanager.services

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Чтение и запись данных Realtime Database по REST API (для сессий бота, без AppConfig).
 * Использует OkHttp для надёжного TLS-рукопожатия с Firebase.
 */
object RtdbDataService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun credentialsFromJson(serviceAccountJson: String): GoogleCredentials =
        GoogleCredentials.fromStream(ByteArrayInputStream(serviceAccountJson.toByteArray(Charsets.UTF_8)))
            .createScoped(
                listOf(
                    "https://www.googleapis.com/auth/firebase.database",
                    "https://www.googleapis.com/auth/userinfo.email"
                )
            )

    /**
     * Нормализация URL базы: гарантируем https:// и удаляем хвостовой слэш.
     */
    private fun normalizeBaseUrl(databaseUrl: String): String {
        val withScheme = if (databaseUrl.startsWith("http")) databaseUrl else "https://$databaseUrl"
        return withScheme.removeSuffix("/")
    }

    /**
     * Путь в БД: "" или "/" — корень. Иначе например "field_a" или "parent/child".
     */
    private fun pathToUrlSegment(path: String): String {
        val p = path.trim().trimStart('/').trimEnd('/')
        return if (p.isEmpty()) "" else p
    }

    /**
     * GET данные по пути. Возвращает JSON-строку (объект, примитив или null).
     */
    suspend fun getData(databaseUrl: String, serviceAccountJson: String, path: String = "/"): String = withContext(Dispatchers.IO) {
        val credentials = credentialsFromJson(serviceAccountJson)
        credentials.refreshIfExpired()
        val token = credentials.accessToken.tokenValue
        val base = normalizeBaseUrl(databaseUrl)
        val segment = pathToUrlSegment(path)
        val pathPart = if (segment.isEmpty()) "/" else "/$segment"
        val url = "$base$pathPart.json?access_token=$token"
        val request = Request.Builder().url(url).get().build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw RuntimeException("Failed to get data: HTTP ${response.code} - $body")
        body
    }

    /**
     * Записать значение в поле по пути. parentPath — путь к родителю ("" для корня), fieldName — имя поля, valueJson — JSON значения (например "\"текст\"" или "123").
     */
    suspend fun setField(databaseUrl: String, serviceAccountJson: String, parentPath: String, fieldName: String, valueJson: String): Unit = withContext(Dispatchers.IO) {
        val credentials = credentialsFromJson(serviceAccountJson)
        credentials.refreshIfExpired()
        val token = credentials.accessToken.tokenValue
        val base = normalizeBaseUrl(databaseUrl)
        val parent = pathToUrlSegment(parentPath)
        val pathPart = if (parent.isEmpty()) "/$fieldName" else "/$parent/$fieldName"
        val url = "$base$pathPart.json?access_token=$token"
        val body = valueJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).put(body).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) throw RuntimeException("Failed to set field: HTTP ${response.code} - $responseBody")
        if (responseBody.contains("\"error\"")) throw RuntimeException("Firebase error: $responseBody")
    }
}
