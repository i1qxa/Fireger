package com.firebasemanager.config

import com.firebasemanager.models.ProjectConfig
import com.firebasemanager.models.ProjectsConfig
import kotlinx.serialization.json.Json
import java.io.File

object AppConfig {
    private const val CONFIG_FILE = "projects.json"
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    fun loadProjects(): List<ProjectConfig> {
        val configFile = File(CONFIG_FILE)
        val rawList = if (configFile.exists()) {
            try {
                val content = configFile.readText()
                val config = json.decodeFromString<ProjectsConfig>(content)
                config.projects
            } catch (e: Exception) {
                println("Error loading projects config: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
        // Оставляем только проекты, у которых есть файл ключа
        val valid = rawList.filter { File(it.serviceAccountPath).exists() }
        if (valid.size < rawList.size) {
            saveProjects(valid)
            println("Removed ${rawList.size - valid.size} project(s) with missing key files from config")
        }
        return valid
    }
    
    fun saveProjects(projects: List<ProjectConfig>) {
        val configFile = File(CONFIG_FILE)
        val config = ProjectsConfig(projects)
        try {
            configFile.writeText(json.encodeToString(ProjectsConfig.serializer(), config))
        } catch (e: Exception) {
            throw RuntimeException("Failed to save projects config: ${e.message}", e)
        }
    }
    
    fun addProject(project: ProjectConfig) {
        val projects = loadProjects().toMutableList()
        if (projects.any { it.id == project.id }) {
            throw IllegalArgumentException("Project with id ${project.id} already exists")
        }
        projects.add(project)
        saveProjects(projects)
    }
    
    fun updateProject(projectId: String, updateFn: (ProjectConfig) -> ProjectConfig) {
        val projects = loadProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index == -1) {
            throw IllegalArgumentException("Project with id $projectId not found")
        }
        projects[index] = updateFn(projects[index])
        saveProjects(projects)
    }
    
    fun removeProject(projectId: String) {
        val projects = loadProjects().toMutableList()
        val removed = projects.removeIf { it.id == projectId }
        if (!removed) {
            throw IllegalArgumentException("Project with id $projectId not found")
        }
        saveProjects(projects)
    }
    
    fun getProject(projectId: String): ProjectConfig? {
        return loadProjects().find { it.id == projectId }
    }
}
