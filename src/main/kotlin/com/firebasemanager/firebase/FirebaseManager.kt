package com.firebasemanager.firebase

import com.firebasemanager.config.AppConfig
import com.firebasemanager.models.ProjectConfig
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.time.Instant

object FirebaseManager {
    private val initializedProjects = mutableMapOf<String, FirebaseApp>()
    private val serviceAccountsDir = File("config/service-accounts")
    
    init {
        try {
            serviceAccountsDir.mkdirs()
            loadAndInitializeProjects()
        } catch (e: Exception) {
            println("Warning: Failed to initialize FirebaseManager: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun loadAndInitializeProjects() {
        try {
            val projects = AppConfig.loadProjects()
            projects.forEach { project ->
                try {
                    initializeProject(project)
                } catch (e: Exception) {
                    println("Failed to initialize project ${project.id}: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            println("Warning: Failed to load projects: ${e.message}")
            // Продолжаем работу даже если нет проектов
        }
    }
    
    fun initializeProject(project: ProjectConfig): FirebaseApp {
        val serviceAccountFile = File(project.serviceAccountPath)
        if (!serviceAccountFile.exists()) {
            throw IllegalArgumentException("Service account file not found: ${project.serviceAccountPath}")
        }
        
        // Проверяем, не инициализирован ли уже проект
        if (initializedProjects.containsKey(project.id)) {
            return initializedProjects[project.id]!!
        }
        
        val serviceAccount = FileInputStream(serviceAccountFile)
        val credentials = GoogleCredentials.fromStream(serviceAccount)
        
        val databaseUrl = project.databaseUrl 
            ?: "https://${project.id}-default-rtdb.firebaseio.com/"
        
        val options = FirebaseOptions.Builder()
            .setCredentials(credentials)
            .setDatabaseUrl(databaseUrl)
            .build()
        
        val app = FirebaseApp.initializeApp(options, project.id)
        initializedProjects[project.id] = app
        
        return app
    }
    
    fun getDatabase(projectId: String): FirebaseDatabase {
        val app = initializedProjects[projectId]
            ?: throw IllegalArgumentException("Project $projectId is not initialized")
        return FirebaseDatabase.getInstance(app)
    }
    
    fun getReference(projectId: String, path: String = "/"): DatabaseReference {
        val database = getDatabase(projectId)
        return database.getReference(path)
    }
    
    fun testConnection(projectId: String): Boolean {
        return try {
            val app = initializedProjects[projectId]
            if (app == null) {
                val project = AppConfig.getProject(projectId)
                if (project != null) {
                    initializeProject(project)
                    true
                } else {
                    false
                }
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun removeProject(projectId: String) {
        val app = initializedProjects.remove(projectId)
        app?.delete()
    }
    
    fun saveServiceAccount(projectId: String, serviceAccountJson: String): String {
        val json = Json.parseToJsonElement(serviceAccountJson).jsonObject
        val extractedProjectId = json["project_id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Invalid service account JSON: missing project_id")
        
        val fileName = "$extractedProjectId.json"
        val filePath = File(serviceAccountsDir, fileName)
        filePath.writeText(serviceAccountJson)
        
        return filePath.absolutePath
    }
    
    fun validateServiceAccount(serviceAccountJson: String): ServiceAccountValidation {
        return try {
            val json = Json.parseToJsonElement(serviceAccountJson).jsonObject
            
            val projectId = json["project_id"]?.jsonPrimitive?.content
            val privateKey = json["private_key"]?.jsonPrimitive?.content
            val clientEmail = json["client_email"]?.jsonPrimitive?.content
            
            val missingFields = mutableListOf<String>()
            if (projectId == null) missingFields.add("project_id")
            if (privateKey == null) missingFields.add("private_key")
            if (clientEmail == null) missingFields.add("client_email")
            
            if (missingFields.isNotEmpty()) {
                ServiceAccountValidation(
                    isValid = false,
                    projectId = null,
                    error = "Missing required fields: ${missingFields.joinToString(", ")}"
                )
            } else {
                ServiceAccountValidation(
                    isValid = true,
                    projectId = projectId,
                    error = null
                )
            }
        } catch (e: Exception) {
            ServiceAccountValidation(
                isValid = false,
                projectId = null,
                error = "Invalid JSON format: ${e.message}"
            )
        }
    }
    
    data class ServiceAccountValidation(
        val isValid: Boolean,
        val projectId: String?,
        val error: String?
    )
}
