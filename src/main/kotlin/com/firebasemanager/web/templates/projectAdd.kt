package com.firebasemanager.web.templates

import kotlinx.html.*

fun HTML.projectAddPage() {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"Добавить проект - Firebase Manager" }
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
                            h4("mb-0") { +"Добавить новый проект" }
                        }
                        div("card-body") {
                            form(method = FormMethod.post, action = "/api/projects", encType = FormEncType.multipartFormData) {
                                attributes["id"] = "addProjectForm"
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "displayName"
                                        +"Название проекта"
                                    }
                                    input(type = InputType.text, classes = "form-control") {
                                        id = "displayName"
                                        name = "displayName"
                                        required = true
                                    }
                                }
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "serviceAccount"
                                        +"Сервисный ключ (JSON файл)"
                                    }
                                    input(type = InputType.file, classes = "form-control") {
                                        id = "serviceAccount"
                                        name = "serviceAccount"
                                        attributes["accept"] = "application/json"
                                        required = true
                                    }
                                    div("form-text") {
                                        +"Загрузите JSON файл сервисного ключа из Firebase Console"
                                    }
                                }
                                
                                div("mb-3") {
                                    label(classes = "form-label") {
                                        attributes["for"] = "databaseUrl"
                                        +"Database URL (опционально)"
                                    }
                                    input(type = InputType.text, classes = "form-control") {
                                        id = "databaseUrl"
                                        name = "databaseUrl"
                                        placeholder = "https://project-id-default-rtdb.firebaseio.com/"
                                    }
                                    div("form-text") {
                                        +"Если не указано, будет использован стандартный URL"
                                    }
                                }
                                
                                div("alert alert-danger") {
                                    attributes["id"] = "errorAlert"
                                    attributes["role"] = "alert"
                                    attributes["style"] = "display: none;"
                                }
                                
                                div("d-flex justify-content-between") {
                                    a("/projects", classes = "btn btn-secondary") {
                                        +"Отмена"
                                    }
                                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                                        +"Добавить проект"
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
            document.getElementById('addProjectForm').addEventListener('submit', async function(e) {
                e.preventDefault();
                
                const formData = new FormData();
                formData.append('displayName', document.getElementById('displayName').value);
                formData.append('serviceAccount', document.getElementById('serviceAccount').files[0]);
                const databaseUrl = document.getElementById('databaseUrl').value;
                if (databaseUrl) {
                    formData.append('databaseUrl', databaseUrl);
                }
                
                const errorAlert = document.getElementById('errorAlert');
                errorAlert.style.display = 'none';
                
                try {
                    const response = await fetch('/api/projects', {
                        method: 'POST',
                        body: formData
                    });
                    
                    if (response.ok) {
                        window.location.href = '/projects';
                    } else {
                        const error = await response.json();
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    errorAlert.textContent = 'Ошибка при добавлении проекта: ' + error.message;
                    errorAlert.style.display = 'block';
                }
            });
            """
        }
    }
}
