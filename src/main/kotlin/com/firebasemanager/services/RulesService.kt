package com.firebasemanager.services

import com.firebasemanager.firebase.FirebaseManager
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

object RulesService {
    suspend fun getRules(projectId: String): String = withContext(Dispatchers.IO) {
        try {
            // Firebase Admin SDK не предоставляет прямой API для правил
            // Используем REST API Firebase
            val project = com.firebasemanager.config.AppConfig.getProject(projectId)
                ?: throw IllegalArgumentException("Project not found: $projectId")
            
            val databaseUrl = project.databaseUrl 
                ?: "https://$projectId-default-rtdb.firebaseio.com/"
            
            // Загружаем сервисный ключ для получения access token
            val serviceAccountFile = File(project.serviceAccountPath)
            if (!serviceAccountFile.exists()) {
                throw IllegalArgumentException("Service account file not found: ${project.serviceAccountPath}")
            }
            
            // Используем правильные scope для доступа к Firebase
            val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountFile))
                .createScoped(listOf(
                    "https://www.googleapis.com/auth/firebase.database",
                    "https://www.googleapis.com/auth/userinfo.email"
                ))
            
            // Обновляем credentials для получения актуального токена
            credentials.refreshIfExpired()
            val accessToken = credentials.accessToken.tokenValue
            
            // Удаляем trailing slash и добавляем путь к правилам
            val baseUrl = databaseUrl.removeSuffix("/")
            val rulesUrl = "$baseUrl/.settings/rules.json?access_token=$accessToken"
            
            val url = URL(rulesUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            val responseBody = try {
                if (connection.inputStream != null) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
            } catch (e: Exception) {
                ""
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                responseBody
            } else {
                // Если ошибка авторизации, предоставляем более детальную информацию
                if (responseCode == 401 || responseCode == 403) {
                    val errorMsg = """
                        |Не удалось загрузить правила безопасности. Возможные причины:
                        |1. Сервисный ключ не имеет прав на чтение правил безопасности
                        |2. Необходимо добавить роль "Firebase Admin" или "Firebase Rules Admin" в IAM
                        |3. Проверьте, что сервисный ключ имеет доступ к проекту Firebase
                        |
                        |Решение:
                        |1. Откройте Google Cloud Console: https://console.cloud.google.com/
                        |2. Выберите проект: $projectId
                        |3. Перейдите в IAM & Admin → IAM
                        |4. Найдите сервисный аккаунт из вашего ключа
                        |5. Добавьте роль "Firebase Admin" или "Firebase Rules Admin"
                        |
                        |Ошибка API: HTTP $responseCode - $responseBody
                    """.trimMargin()
                    throw RuntimeException(errorMsg)
                }
                throw RuntimeException("Failed to fetch rules: HTTP $responseCode - $responseBody")
            }
        } catch (e: Exception) {
            throw RuntimeException("Error getting rules: ${e.message}", e)
        }
    }
    
    suspend fun updateRules(projectId: String, rules: String): Unit = withContext(Dispatchers.IO) {
        try {
            val project = com.firebasemanager.config.AppConfig.getProject(projectId)
                ?: throw IllegalArgumentException("Project not found: $projectId")
            
            val databaseUrl = project.databaseUrl 
                ?: "https://$projectId-default-rtdb.firebaseio.com/"
            
            // Загружаем сервисный ключ для получения access token
            val serviceAccountFile = File(project.serviceAccountPath)
            if (!serviceAccountFile.exists()) {
                throw IllegalArgumentException("Service account file not found: ${project.serviceAccountPath}")
            }
            
            // Используем правильные scope для доступа к Firebase
            val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountFile))
                .createScoped(listOf(
                    "https://www.googleapis.com/auth/firebase.database",
                    "https://www.googleapis.com/auth/userinfo.email"
                ))
            
            // Обновляем credentials для получения актуального токена
            credentials.refreshIfExpired()
            val accessToken = credentials.accessToken.tokenValue
            
            // Используем прямой REST API endpoint согласно документации Firebase
            // Формат: https://<DATABASE_NAME>.firebaseio.com/.settings/rules.json?access_token=<TOKEN>
            val baseUrl = databaseUrl.removeSuffix("/")
            val rulesUrl = "$baseUrl/.settings/rules.json"
            
            val url = URL(rulesUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            // Добавляем access_token в query параметр (согласно документации Firebase)
            val urlWithToken = "$rulesUrl?access_token=$accessToken"
            val finalUrl = URL(urlWithToken)
            val finalConnection = finalUrl.openConnection() as HttpURLConnection
            finalConnection.requestMethod = "PUT"
            finalConnection.setRequestProperty("Content-Type", "application/json")
            finalConnection.doOutput = true
            finalConnection.connectTimeout = 15000
            finalConnection.readTimeout = 15000
            
            // Убеждаемся, что правила в правильном формате JSON
            val rulesJson = if (rules.trim().startsWith("{")) {
                rules
            } else {
                // Если правила не в JSON формате, оборачиваем их
                "{\"rules\": $rules}"
            }
            
            // Отправляем правила
            finalConnection.outputStream.use { output ->
                output.write(rulesJson.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = finalConnection.responseCode
            val responseBody = try {
                if (finalConnection.inputStream != null) {
                    finalConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    finalConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
            } catch (e: Exception) {
                ""
            }
            
            if (responseCode !in 200..299) {
                // Если ошибка авторизации, предоставляем более детальную информацию
                if (responseCode == 401 || responseCode == 403) {
                    val errorMsg = """
                        |Не удалось обновить правила безопасности. Возможные причины:
                        |1. Сервисный ключ не имеет прав на изменение правил безопасности
                        |2. Необходимо добавить роль "Firebase Admin" или "Firebase Rules Admin" в IAM
                        |3. Проверьте, что сервисный ключ имеет доступ к проекту Firebase
                        |
                        |Решение:
                        |1. Откройте Google Cloud Console: https://console.cloud.google.com/
                        |2. Выберите проект: $projectId
                        |3. Перейдите в IAM & Admin → IAM
                        |4. Найдите сервисный аккаунт из вашего ключа
                        |5. Добавьте роль "Firebase Admin" или "Firebase Rules Admin"
                        |
                        |Альтернатива: Используйте Firebase Console для обновления правил вручную.
                        |
                        |Ошибка API: HTTP $responseCode - $responseBody
                    """.trimMargin()
                    throw RuntimeException(errorMsg)
                }
                throw RuntimeException("Failed to update rules: HTTP $responseCode - $responseBody")
            }
            
            // Проверяем ответ на наличие ошибок
            if (responseBody.contains("\"error\"")) {
                throw RuntimeException("Firebase API error: $responseBody")
            }
            
        } catch (e: Exception) {
            throw RuntimeException("Error updating rules: ${e.message}", e)
        }
    }
    
    private suspend fun updateRulesWithIdToken(
        projectId: String,
        rules: String,
        credentials: GoogleCredentials,
        project: com.firebasemanager.models.ProjectConfig
    ) {
        // Альтернативный способ: используем Firebase Admin SDK для получения ID токена
        // Но так как Admin SDK не поддерживает это напрямую, используем другой подход
        
        // Пробуем использовать правильный формат с правильными scope
        val databaseUrl = project.databaseUrl 
            ?: "https://$projectId-default-rtdb.firebaseio.com/"
        
        val baseUrl = databaseUrl.removeSuffix("/")
        val rulesUrl = "$baseUrl/.settings/rules.json"
        
        // Используем access token с правильными scope
        val accessToken = credentials.accessToken.tokenValue
        
        val url = URL("$rulesUrl?access_token=$accessToken")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        val rulesJson = if (rules.trim().startsWith("{")) {
            rules
        } else {
            "{\"rules\": $rules}"
        }
        
        connection.outputStream.use { output ->
            output.write(rulesJson.toByteArray(Charsets.UTF_8))
        }
        
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorMessage = try {
                connection.errorStream?.bufferedReader()?.use { it.readText() } 
                    ?: "HTTP $responseCode"
            } catch (e: Exception) {
                "HTTP $responseCode"
            }
            throw RuntimeException("Failed to update rules (tried alternative method): $errorMessage")
        }
    }
}
