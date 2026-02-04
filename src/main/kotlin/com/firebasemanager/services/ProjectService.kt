package com.firebasemanager.services

import com.firebasemanager.config.AppConfig
import com.firebasemanager.firebase.FirebaseManager
import com.firebasemanager.models.ProjectConfig
import java.io.File
import java.time.Instant

object ProjectService {
    fun getAllProjects(): List<ProjectConfig> {
        return AppConfig.loadProjects()
    }
    
    fun getProject(projectId: String): ProjectConfig? {
        return AppConfig.getProject(projectId)
    }
    
    fun createProject(
        displayName: String,
        serviceAccountJson: String,
        databaseUrl: String? = null
    ): ProjectConfig {
        // Валидация сервисного ключа
        val validation = FirebaseManager.validateServiceAccount(serviceAccountJson)
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.error ?: "Invalid service account")
        }
        
        val projectId = validation.projectId!!
        
        // Проверка уникальности
        if (AppConfig.getProject(projectId) != null) {
            throw IllegalArgumentException("Project with id $projectId already exists")
        }
        
        // Сохранение сервисного ключа
        val serviceAccountPath = FirebaseManager.saveServiceAccount(projectId, serviceAccountJson)
        
        // Определение database URL
        val finalDatabaseUrl = databaseUrl 
            ?: "https://$projectId-default-rtdb.firebaseio.com/"
        
        // Создание конфигурации проекта
        val project = ProjectConfig(
            id = projectId,
            displayName = displayName,
            serviceAccountPath = serviceAccountPath,
            databaseUrl = finalDatabaseUrl,
            createdAt = Instant.now().toString()
        )
        
        // Сохранение в конфигурацию
        AppConfig.addProject(project)
        
        // Инициализация подключения
        try {
            FirebaseManager.initializeProject(project)
        } catch (e: Exception) {
            // Если не удалось инициализировать, удаляем проект
            AppConfig.removeProject(projectId)
            File(serviceAccountPath).delete()
            throw RuntimeException("Failed to initialize Firebase connection: ${e.message}", e)
        }
        
        return project
    }
    
    fun updateProject(
        projectId: String,
        displayName: String?,
        databaseUrl: String?
    ): ProjectConfig {
        var updatedProject: ProjectConfig? = null
        AppConfig.updateProject(projectId) { project ->
            updatedProject = project.copy(
                displayName = displayName ?: project.displayName,
                databaseUrl = databaseUrl ?: project.databaseUrl
            )
            updatedProject!!
        }
        return updatedProject ?: throw IllegalStateException("Failed to update project")
    }
    
    fun updateProjectWithNewKey(
        projectId: String,
        serviceAccountJson: String
    ): ProjectConfig {
        val validation = FirebaseManager.validateServiceAccount(serviceAccountJson)
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.error ?: "Invalid service account")
        }
        
        val extractedProjectId = validation.projectId!!
        if (extractedProjectId != projectId) {
            throw IllegalArgumentException("Service account project_id ($extractedProjectId) does not match project id ($projectId)")
        }
        
        val project = AppConfig.getProject(projectId)
            ?: throw IllegalArgumentException("Project not found: $projectId")
        
        // Удаляем старое подключение
        FirebaseManager.removeProject(projectId)
        
        // Удаляем старый файл ключа
        File(project.serviceAccountPath).delete()
        
        // Сохраняем новый ключ
        val newServiceAccountPath = FirebaseManager.saveServiceAccount(projectId, serviceAccountJson)
        
        // Обновляем конфигурацию
        val updatedProject = project.copy(serviceAccountPath = newServiceAccountPath)
        AppConfig.updateProject(projectId) { updatedProject }
        
        // Инициализируем с новым ключом
        FirebaseManager.initializeProject(updatedProject)
        
        return updatedProject
    }
    
    fun deleteProject(projectId: String) {
        val project = AppConfig.getProject(projectId)
            ?: throw IllegalArgumentException("Project not found: $projectId")
        
        // Удаляем подключение
        FirebaseManager.removeProject(projectId)
        
        // Удаляем файл сервисного ключа
        File(project.serviceAccountPath).delete()
        
        // Удаляем из конфигурации
        AppConfig.removeProject(projectId)
    }
    
    fun testConnection(projectId: String): Boolean {
        return FirebaseManager.testConnection(projectId)
    }
}
