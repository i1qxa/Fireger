package com.firebasemanager.web.templates

import com.firebasemanager.models.ProjectConfig
import kotlinx.html.*

fun HTML.projectsPage(projects: List<ProjectConfig>) {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"Проекты - Firebase Manager" }
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css")
        link(rel = "stylesheet", href = "/static/style.css")
    }
    body {
        nav("navbar navbar-expand-lg navbar-dark bg-primary") {
            div("container-fluid") {
                a("/", classes = "navbar-brand") { +"Firebase Manager" }
                div("navbar-nav ms-auto") {
                    a("/projects", classes = "nav-link active") { +"Проекты" }
                }
            }
        }
        
        div("container mt-4") {
            div("d-flex justify-content-between align-items-center mb-4") {
                h2 { +"Проекты Firebase" }
                a("/projects/add", classes = "btn btn-success") {
                    +"Добавить проект"
                }
            }
            
            if (projects.isEmpty()) {
                div("alert alert-info") {
                    +"Нет добавленных проектов. "
                    a("/projects/add", classes = "alert-link") { +"Добавьте первый проект" }
                    +"."
                }
            } else {
                div("row") {
                    projects.forEach { project ->
                        div("col-md-6 col-lg-4 mb-4") {
                            div("card h-100 shadow-sm") {
                                div("card-body") {
                                    h5("card-title") { +project.displayName }
                                    p("card-text text-muted small") {
                                        +"ID: ${project.id}"
                                    }
                                    if (project.databaseUrl != null) {
                                        p("card-text text-muted small") {
                                            +"Database: ${project.databaseUrl}"
                                        }
                                    }
                                }
                                div("card-footer bg-transparent") {
                                    div("btn-group w-100") {
                                        attributes["role"] = "group"
                                        a("/projects/${project.id}", classes = "btn btn-sm btn-primary") {
                                            +"Управление"
                                        }
                                        a("/projects/${project.id}/edit", classes = "btn btn-sm btn-secondary") {
                                            +"Редактировать"
                                        }
                                        button(type = ButtonType.button, classes = "btn btn-sm btn-danger") {
                                            attributes["onclick"] = "deleteProject('${project.id}')"
                                            +"Удалить"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        script(src = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js") {}
        script {
            +"""
            async function deleteProject(projectId) {
                if (!confirm('Вы уверены, что хотите удалить этот проект?')) {
                    return;
                }
                
                try {
                    const response = await fetch('/api/projects/' + projectId, {
                        method: 'DELETE'
                    });
                    
                    if (response.ok) {
                        location.reload();
                    } else {
                        const error = await response.json();
                        alert('Ошибка: ' + error.error);
                    }
                } catch (error) {
                    alert('Ошибка при удалении проекта: ' + error.message);
                }
            }
            """
        }
    }
}
