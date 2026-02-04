package com.firebasemanager.services

import com.firebasemanager.firebase.FirebaseManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DataService {
    suspend fun getData(projectId: String, path: String): Any? = withContext(Dispatchers.IO) {
        val ref = FirebaseManager.getReference(projectId, path)
        val latch = CountDownLatch(1)
        var result: Any? = null
        var dbError: Exception? = null
        
        ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                result = snapshot.value
                latch.countDown()
            }
            
            override fun onCancelled(error: DatabaseError) {
                dbError = RuntimeException("Database error: ${error.message}")
                latch.countDown()
            }
        })
        
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw RuntimeException("Timeout waiting for data")
        }
        
        dbError?.let { throw it }
        result
    }
    
    suspend fun setData(projectId: String, path: String, value: Any): Unit = withContext(Dispatchers.IO) {
        val ref = FirebaseManager.getReference(projectId, path)
        val latch = CountDownLatch(1)
        var dbError: Exception? = null
        
        ref.setValue(value, object : DatabaseReference.CompletionListener {
            override fun onComplete(error: DatabaseError?, ref: DatabaseReference) {
                if (error != null) {
                    dbError = RuntimeException("Database error: ${error.message}")
                }
                latch.countDown()
            }
        })
        
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw RuntimeException("Timeout setting data")
        }
        
        dbError?.let { throw it }
    }
    
    suspend fun deleteData(projectId: String, path: String): Unit = withContext(Dispatchers.IO) {
        val ref = FirebaseManager.getReference(projectId, path)
        val latch = CountDownLatch(1)
        var dbError: Exception? = null
        
        ref.removeValue(object : DatabaseReference.CompletionListener {
            override fun onComplete(error: DatabaseError?, ref: DatabaseReference) {
                if (error != null) {
                    dbError = RuntimeException("Database error: ${error.message}")
                }
                latch.countDown()
            }
        })
        
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw RuntimeException("Timeout deleting data")
        }
        
        dbError?.let { throw it }
    }
    
    fun parseJsonValue(jsonString: String): Any {
        return try {
            val element = Json.parseToJsonElement(jsonString)
            when {
                element is kotlinx.serialization.json.JsonObject -> {
                    element.entries.associate { (k, v) ->
                        k to when (v) {
                            is kotlinx.serialization.json.JsonObject -> v.entries.associate { it.key to it.value.toString() }
                            is kotlinx.serialization.json.JsonPrimitive -> {
                                val str = v.content
                                when {
                                    str == "true" || str == "false" -> str.toBoolean()
                                    str.toDoubleOrNull() != null -> str.toDouble()
                                    else -> str
                                }
                            }
                            else -> v.toString()
                        }
                    }
                }
                element is kotlinx.serialization.json.JsonPrimitive -> {
                    val str = element.content
                    when {
                        str == "true" || str == "false" -> str.toBoolean()
                        str.toDoubleOrNull() != null -> str.toDouble()
                        else -> str
                    }
                }
                else -> jsonString
            }
        } catch (e: Exception) {
            jsonString
        }
    }
}
