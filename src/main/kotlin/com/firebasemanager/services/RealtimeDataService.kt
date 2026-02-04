package com.firebasemanager.services

import com.firebasemanager.firebase.FirebaseManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

object RealtimeDataService {
    private val activeListeners = ConcurrentHashMap<String, ValueEventListener>()
    
    /**
     * Создает Flow для real-time обновлений данных по указанному пути
     */
    fun observeData(projectId: String, path: String): Flow<String> = callbackFlow {
        val listenerKey = "$projectId:$path"
        
        // Удаляем предыдущий listener, если есть
        activeListeners[listenerKey]?.let { oldListener ->
            try {
                val ref = FirebaseManager.getReference(projectId, path)
                ref.removeEventListener(oldListener)
            } catch (e: Exception) {
                // Игнорируем ошибки при удалении старого listener
            }
        }
        
        val ref = FirebaseManager.getReference(projectId, path)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val data = snapshot.value
                    val jsonData = convertToJsonString(data)
                    trySend(jsonData)
                } catch (e: Exception) {
                    close(e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(RuntimeException("Database error: ${error.message}"))
            }
        }
        
        activeListeners[listenerKey] = listener
        ref.addValueEventListener(listener)
        
        awaitClose {
            ref.removeEventListener(listener)
            activeListeners.remove(listenerKey)
        }
    }
    
    /**
     * Прекращает наблюдение за данными
     */
    fun stopObserving(projectId: String, path: String) {
        val listenerKey = "$projectId:$path"
        activeListeners[listenerKey]?.let { listener ->
            try {
                val ref = FirebaseManager.getReference(projectId, path)
                ref.removeEventListener(listener)
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
            activeListeners.remove(listenerKey)
        }
    }
    
    private fun convertToJsonString(data: Any?): String {
        return when (data) {
            null -> "null"
            is String -> "\"${data.replace("\"", "\\\"")}\""
            is Number -> data.toString()
            is Boolean -> data.toString()
            is Map<*, *> -> {
                val mapAsString = data.entries.joinToString(", ") { (k, v) ->
                    "\"$k\": ${convertToJsonString(v)}"
                }
                "{$mapAsString}"
            }
            is List<*> -> {
                data.joinToString(", ") { convertToJsonString(it) }.let { "[$it]" }
            }
            else -> {
                try {
                    // Пробуем сериализовать через JsonElement
                    val jsonStr = data.toString()
                    Json.parseToJsonElement(jsonStr).toString()
                } catch (e: Exception) {
                    // Fallback: простое строковое представление
                    "\"${data.toString().replace("\"", "\\\"")}\""
                }
            }
        }
    }
}
