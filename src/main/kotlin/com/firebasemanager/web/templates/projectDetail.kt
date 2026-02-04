package com.firebasemanager.web.templates

import com.firebasemanager.models.ProjectConfig
import kotlinx.html.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.*

fun HTML.projectDetailPage(project: ProjectConfig, rules: String? = null, data: String? = null) {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"${project.displayName} - Firebase Manager" }
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css")
        link(rel = "stylesheet", href = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.2/codemirror.min.css")
        link(rel = "stylesheet", href = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.2/theme/monokai.min.css")
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
            div("row mb-3") {
                div("col") {
                    h2 { +project.displayName }
                    p("text-muted") {
                        +"Project ID: ${project.id}"
                    }
                }
                div("col-auto") {
                    a("/projects/${project.id}/edit", classes = "btn btn-secondary") {
                        +"Редактировать"
                    }
                }
            }
            
            ul("nav nav-tabs") {
                attributes["role"] = "tablist"
                li("nav-item") {
                    a("#rules-tab", classes = "nav-link active") {
                        attributes["data-bs-toggle"] = "tab"
                        attributes["role"] = "tab"
                        +"Правила безопасности"
                    }
                }
                li("nav-item") {
                    a("#data-tab", classes = "nav-link") {
                        attributes["data-bs-toggle"] = "tab"
                        attributes["role"] = "tab"
                        +"Данные"
                    }
                }
            }
            
            div("tab-content mt-3") {
                div("tab-pane fade show active") {
                    attributes["id"] = "rules-tab"
                    attributes["role"] = "tabpanel"
                    div("card") {
                        div("card-header d-flex justify-content-between align-items-center") {
                            +"Правила безопасности Realtime Database"
                            button(type = ButtonType.button, classes = "btn btn-sm btn-primary") {
                                attributes["onclick"] = "saveRules()"
                                +"Сохранить правила"
                            }
                        }
                        div("card-body") {
                            textArea(classes = "form-control", rows = "20") {
                                id = "rulesEditor"
                                +"Загрузка правил..."
                            }
                            div("alert alert-danger mt-2") {
                                attributes["id"] = "rulesErrorAlert"
                                attributes["role"] = "alert"
                                attributes["style"] = "display: none;"
                            }
                        }
                    }
                }
                
                div("tab-pane fade") {
                    attributes["id"] = "data-tab"
                    attributes["role"] = "tabpanel"
                    div("card") {
                        div("card-header") {
                            +"Данные Realtime Database"
                        }
                        div("card-body") {
                            div("mb-3") {
                                div("d-flex justify-content-between align-items-center mb-2") {
                                    label(classes = "form-label mb-0") {
                                        attributes["for"] = "dataPath"
                                        +"Путь к данным"
                                    }
                                    small("text-muted") {
                                        attributes["id"] = "realtimeStatus"
                                        +"⚪ Обновления остановлены"
                                    }
                                }
                                div("input-group") {
                                    input(type = InputType.text, classes = "form-control") {
                                        id = "dataPath"
                                        value = "/"
                                        placeholder = "/"
                                    }
                                    button(type = ButtonType.button, classes = "btn btn-primary") {
                                        attributes["onclick"] = "loadData()"
                                        +"Загрузить"
                                    }
                                    button(type = ButtonType.button, classes = "btn btn-secondary") {
                                        attributes["id"] = "stopRealtimeBtn"
                                        attributes["onclick"] = "stopRealtimeUpdates()"
                                        +"Остановить обновления"
                                    }
                                }
                            }
                            div("mb-3") {
                                textArea(classes = "form-control", rows = "15") {
                                    id = "dataEditor"
                                    placeholder = "Данные будут отображены здесь..."
                                }
                            }
                            div("btn-group") {
                                button(type = ButtonType.button, classes = "btn btn-success") {
                                    attributes["onclick"] = "saveData()"
                                    +"Сохранить данные"
                                }
                                button(type = ButtonType.button, classes = "btn btn-danger") {
                                    attributes["onclick"] = "deleteData()"
                                    +"Удалить данные"
                                }
                            }
                            div("alert alert-danger mt-2") {
                                attributes["id"] = "dataErrorAlert"
                                attributes["role"] = "alert"
                                attributes["style"] = "display: none;"
                            }
                        }
                    }
                }
            }
        }
        
        script(src = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js") {}
        script(src = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.2/codemirror.min.js") {}
        script(src = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.2/mode/javascript/javascript.min.js") {}
        script(src = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.2/mode/json/json.min.js") {}
        script {
            // Экранируем project.id для безопасного использования в JavaScript
            val projectIdEscaped = project.id.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            unsafe {
                raw("""
            const projectId = '$projectIdEscaped';
            const rulesEditor = CodeMirror.fromTextArea(document.getElementById('rulesEditor'), {
                lineNumbers: true,
                mode: 'application/json',
                theme: 'monokai',
                indentUnit: 2
            });
            
            const dataEditor = CodeMirror.fromTextArea(document.getElementById('dataEditor'), {
                lineNumbers: true,
                mode: 'application/json',
                theme: 'monokai',
                indentUnit: 2
            });
            
            async function loadRules() {
                const errorAlert = document.getElementById('rulesErrorAlert');
                errorAlert.style.display = 'none';
                
                try {
                    const response = await fetch('/api/projects/' + projectId + '/rules');
                    
                    if (response.ok) {
                        const result = await response.json();
                        console.log('Received rules:', result);
                        
                        if (result.rules) {
                            try {
                                // Парсим JSON правила для форматирования
                                const jsonRules = typeof result.rules === 'string' 
                                    ? JSON.parse(result.rules) 
                                    : result.rules;
                                rulesEditor.setValue(JSON.stringify(jsonRules, null, 2));
                            } catch (parseError) {
                                console.error('Error parsing rules:', parseError);
                                // Если не удалось распарсить, показываем как есть
                                rulesEditor.setValue(result.rules);
                            }
                        } else {
                            rulesEditor.setValue('{\n  "rules": {\n    ".read": true,\n    ".write": true\n  }\n}');
                        }
                    } else {
                        const error = await response.json();
                        console.error('Error loading rules:', error);
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    console.error('Exception loading rules:', error);
                    errorAlert.textContent = 'Ошибка при загрузке правил: ' + error.message;
                    errorAlert.style.display = 'block';
                }
            }
            
            async function saveRules() {
                const rules = rulesEditor.getValue();
                const errorAlert = document.getElementById('rulesErrorAlert');
                errorAlert.style.display = 'none';
                
                try {
                    const response = await fetch('/api/projects/' + projectId + '/rules', {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ rules: rules })
                    });
                    
                    if (response.ok) {
                        alert('Правила сохранены успешно');
                    } else {
                        const error = await response.json();
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    errorAlert.textContent = 'Ошибка при сохранении правил: ' + error.message;
                    errorAlert.style.display = 'block';
                }
            }
            
            let realtimeWebSocket = null;
            let isRealtimeEnabled = false;
            
            async function loadData() {
                const path = document.getElementById('dataPath').value || '/';
                const errorAlert = document.getElementById('dataErrorAlert');
                errorAlert.style.display = 'none';
                
                // Останавливаем предыдущее подключение, если есть
                if (realtimeWebSocket) {
                    realtimeWebSocket.close();
                    realtimeWebSocket = null;
                    isRealtimeEnabled = false;
                }
                
                try {
                    const response = await fetch('/api/projects/' + projectId + '/data?path=' + encodeURIComponent(path));
                    
                    if (response.ok) {
                        const result = await response.json();
                        console.log('Received data:', result);
                        
                        if (result.data && result.data !== 'null') {
                            try {
                                // Парсим JSON данные
                                const jsonData = typeof result.data === 'string' 
                                    ? JSON.parse(result.data) 
                                    : result.data;
                                dataEditor.setValue(JSON.stringify(jsonData, null, 2));
                            } catch (parseError) {
                                console.error('Error parsing data:', parseError);
                                // Если не удалось распарсить, показываем как есть
                                dataEditor.setValue(result.data);
                            }
                        } else {
                            dataEditor.setValue('{}');
                        }
                        
                        // Запускаем real-time обновления
                        startRealtimeUpdates(path);
                    } else {
                        const error = await response.json();
                        console.error('Error loading data:', error);
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    console.error('Exception loading data:', error);
                    errorAlert.textContent = 'Ошибка при загрузке данных: ' + error.message;
                    errorAlert.style.display = 'block';
                }
            }
            
            function startRealtimeUpdates(path) {
                if (realtimeWebSocket && realtimeWebSocket.readyState === WebSocket.OPEN) {
                    return; // Уже подключено
                }
                
                const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                const wsUrl = wsProtocol + '//' + window.location.host + '/api/projects/' + projectId + '/data/realtime?path=' + encodeURIComponent(path);
                
                realtimeWebSocket = new WebSocket(wsUrl);
                isRealtimeEnabled = true;
                
                realtimeWebSocket.onopen = function() {
                    console.log('Real-time подключение установлено');
                    updateRealtimeStatus(true);
                };
                
                realtimeWebSocket.onmessage = function(event) {
                    try {
                        const message = JSON.parse(event.data);
                        console.log('WebSocket message received:', message);
                        
                        if (message.type === 'data') {
                            // Обновляем данные в редакторе только если пользователь не редактирует
                            if (!dataEditor.hasFocus()) {
                                try {
                                    const jsonData = typeof message.data === 'string' 
                                        ? JSON.parse(message.data) 
                                        : message.data;
                                    dataEditor.setValue(JSON.stringify(jsonData, null, 2));
                                } catch (parseError) {
                                    console.error('Error parsing real-time data:', parseError);
                                    // Если не удалось распарсить, показываем как есть
                                    dataEditor.setValue(typeof message.data === 'string' 
                                        ? message.data 
                                        : JSON.stringify(message.data));
                                }
                            }
                        } else if (message.type === 'error') {
                            console.error('Ошибка real-time: ' + message.error);
                            errorAlert.textContent = 'Ошибка real-time: ' + message.error;
                            errorAlert.style.display = 'block';
                        } else if (message.type === 'connected') {
                            console.log('Подключено к real-time обновлениям для пути: ' + message.path);
                            updateRealtimeStatus(true);
                        }
                    } catch (error) {
                        console.error('Ошибка при обработке сообщения:', error, event.data);
                    }
                };
                
                realtimeWebSocket.onerror = function(error) {
                    console.error('Ошибка WebSocket: ' + error);
                    updateRealtimeStatus(false);
                };
                
                realtimeWebSocket.onclose = function() {
                    console.log('Real-time подключение закрыто');
                    updateRealtimeStatus(false);
                    // Пытаемся переподключиться через 3 секунды, если это не было ручное закрытие
                    if (isRealtimeEnabled) {
                        setTimeout(() => {
                            if (isRealtimeEnabled) {
                                startRealtimeUpdates(path);
                            }
                        }, 3000);
                    }
                };
            }
            
            function stopRealtimeUpdates() {
                isRealtimeEnabled = false;
                if (realtimeWebSocket) {
                    realtimeWebSocket.close();
                    realtimeWebSocket = null;
                }
                updateRealtimeStatus(false);
            }
            
            function updateRealtimeStatus(connected) {
                // Можно добавить визуальный индикатор статуса
                const statusIndicator = document.getElementById('realtimeStatus');
                if (statusIndicator) {
                    statusIndicator.textContent = connected ? '🟢 Обновления в реальном времени активны' : '⚪ Обновления остановлены';
                    statusIndicator.style.color = connected ? 'green' : 'gray';
                }
            }
            
            // Останавливаем подключение при закрытии страницы
            window.addEventListener('beforeunload', function() {
                stopRealtimeUpdates();
            });
            
            async function saveData() {
                const path = document.getElementById('dataPath').value || '/';
                const data = dataEditor.getValue();
                const errorAlert = document.getElementById('dataErrorAlert');
                errorAlert.style.display = 'none';
                
                try {
                    JSON.parse(data); // Validate JSON
                    
                    const response = await fetch('/api/projects/' + projectId + '/data?path=' + encodeURIComponent(path), {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ data: data })
                    });
                    
                    if (response.ok) {
                        alert('Данные сохранены успешно');
                    } else {
                        const error = await response.json();
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    if (error instanceof SyntaxError) {
                        errorAlert.textContent = 'Ошибка: Неверный формат JSON';
                    } else {
                        errorAlert.textContent = 'Ошибка при сохранении данных: ' + error.message;
                    }
                    errorAlert.style.display = 'block';
                }
            }
            
            async function deleteData() {
                const path = document.getElementById('dataPath').value || '/';
                
                if (!confirm('Вы уверены, что хотите удалить данные по пути "' + path + '"?')) {
                    return;
                }
                
                const errorAlert = document.getElementById('dataErrorAlert');
                errorAlert.style.display = 'none';
                
                try {
                    const response = await fetch('/api/projects/' + projectId + '/data?path=' + encodeURIComponent(path), {
                        method: 'DELETE'
                    });
                    
                    if (response.ok) {
                        alert('Данные удалены успешно');
                        dataEditor.setValue('{}');
                    } else {
                        const error = await response.json();
                        errorAlert.textContent = 'Ошибка: ' + error.error;
                        errorAlert.style.display = 'block';
                    }
                } catch (error) {
                    errorAlert.textContent = 'Ошибка при удалении данных: ' + error.message;
                    errorAlert.style.display = 'block';
                }
            }
            
            // Загружаем правила при переключении на вкладку правил
            const rulesTabLink = document.querySelector('a[data-bs-toggle="tab"][href="#rules-tab"]');
            if (rulesTabLink) {
                rulesTabLink.addEventListener('shown.bs.tab', function() {
                    console.log('Rules tab shown, loading rules...');
                    loadRules();
                });
            }
            
            // Загружаем данные при переключении на вкладку данных
            const dataTabLink = document.querySelector('a[data-bs-toggle="tab"][href="#data-tab"]');
            if (dataTabLink) {
                dataTabLink.addEventListener('shown.bs.tab', function() {
                    console.log('Data tab shown, loading data...');
                    loadData();
                });
            }
            
            // Загружаем правила при загрузке страницы, если активна вкладка правил
            setTimeout(function() {
                const rulesTab = document.getElementById('rules-tab');
                if (rulesTab && rulesTab.classList.contains('active')) {
                    console.log('Rules tab is active on load, loading rules...');
                    loadRules();
                }
                
                const dataTab = document.getElementById('data-tab');
                if (dataTab && dataTab.classList.contains('active')) {
                    console.log('Data tab is active on load, loading data...');
                    loadData();
                }
            }, 500);
            
            // Обновляем путь при изменении поля пути
            document.getElementById('dataPath').addEventListener('change', function() {
                stopRealtimeUpdates();
                loadData();
            });
            
            // Также загружаем при нажатии Enter в поле пути
            document.getElementById('dataPath').addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    stopRealtimeUpdates();
                    loadData();
                }
            });
            """.trimIndent())
            }
        }
    }
}
