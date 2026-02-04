package com.firebasemanager.web.templates

import com.firebasemanager.models.ProjectConfig
import kotlinx.html.*

fun HTML.projectEditPage(project: ProjectConfig) {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"Редактировать проект - Firebase Manager" }
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css")
        link(rel = "stylesheet", href = "/static/style.css")
    }
    body {
        nav("navbar navbar-expand-lg navbar-dark bg-primary") {
            div("container-fluid") {
                a("/", classes = "navbar-brand") { +"Firebase Manager" }
                div("navbar-nav ms-auto") {
                    a("/projects", classes = "nav-link") { +"Проекты" }
                }
            }
        }
        
        div("container mt-4") {
            div("row") {
                div("col-md-8 offset-md-2") {
                    div("card shadow") {
                        div("card-header") {
                            h4("mb-0") { +"Редактировать проект: ${project.displayName}" }
                        }
                        div("card-body") {
                            form(method = FormMethod.put, action = "/api/projects/${project.id}") {
                                attributes["id"] = "editProjectForm"
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "displayName"
                                        +"Название проекта"
                                    }
                                    input(type = InputType.text, classes = "form-control") {
                                        id = "displayName"
                                        name = "displayName"
                                        value = project.displayName
                                        required = true
                                    }
                                }
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "projectId"
                                        +"Project ID"
                                    }
                                    input(type = InputType.text, classes = "form-control") {
                                        id = "projectId"
                                        value = project.id
                                        disabled = true
                                    }
                                    div("form-text") {
                                        +"Project ID нельзя изменить"
                                    }
                                }
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "databaseUrl"
                                        +"Database URL"
                                    }
                                    input(type = InputType.text, classes = "form-control") {
                                        id = "databaseUrl"
                                        name = "databaseUrl"
                                        value = project.databaseUrl ?: ""
                                        placeholder = "https://project-id-default-rtdb.firebaseio.com/"
                                    }
                                }
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "serviceAccount"
                                        +"Обновить сервисный ключ (опционально)"
                                    }
                                    input(type = InputType.file, classes = "form-control") {
                                        id = "serviceAccount"
                                        name = "serviceAccount"
                                        attributes["accept"] = "application/json"
                                    }
                                    div("form-text") {
                                        +"Загрузите новый JSON файл сервисного ключа, если нужно обновить"
                                    }
                                }
                                
                                div("alert alert-danger") {
                                    attributes["id"] = "errorAlert"
                                    attributes["role"] = "alert"
                                    attributes["style"] = "display: none;"
                                }
                                
                                hr()
                                
                                div("d-flex justify-content-between") {
                                    a("/projects", classes = "btn btn-secondary") {
                                        +"Отмена"
                                    }
                                    div {
                                        button(type = ButtonType.button, classes = "btn btn-danger me-2") {
                                            attributes["onclick"] = "deleteProject('${project.id}')"
                                            +"Удалить проект"
                                        }
                                        button(type = ButtonType.submit, classes = "btn btn-primary") {
                                            +"Сохранить изменения"
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
            document.getElementById('editProjectForm').addEventListener('submit', async function(e) {
                e.preventDefault();
                
                const formData = {
                    displayName: document.getElementById('displayName').value,
                    databaseUrl: document.getElementById('databaseUrl').value || null
                };
                
                const errorAlert = document.getElementById('errorAlert');
                errorAlert.style.display = 'none';
                
                try {
                    const response = await fetch('/api/projects/${project.id}', {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(formData)
                    });
                    
                    if (response.ok) {
                        window.location.href = '/projects';
                    } else {
                        const error = await response.json();
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    errorAlert.textContent = 'Ошибка при сохранении: ' + error.message;
                    errorAlert.style.display = 'block';
                }
            });
            
            async function deleteProject(projectId) {
                if (!confirm('Вы уверены, что хотите удалить этот проект? Это действие нельзя отменить.')) {
                    return;
                }
                
                try {
                    const response = await fetch('/api/projects/' + projectId, {
                        method: 'DELETE'
                    });
                    
                    if (response.ok) {
                        window.location.href = '/projects';
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
