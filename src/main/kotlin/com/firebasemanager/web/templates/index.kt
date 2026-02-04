package com.firebasemanager.web.templates

import kotlinx.html.*

fun HTML.indexPage() {
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        title { +"Firebase Multi-Project Manager" }
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
        
        div("container mt-5") {
            div("row") {
                div("col-md-8 offset-md-2") {
                    div("card shadow") {
                        div("card-body text-center") {
                            h1("card-title mb-4") { +"Firebase Multi-Project Manager" }
                            p("card-text lead") {
                                +"Управляйте несколькими Firebase проектами из одного места. "
                                +"Добавляйте проекты, управляйте правилами безопасности и данными Realtime Database."
                            }
                            hr {}
                            a("/projects", classes = "btn btn-primary btn-lg") {
                                +"Управление проектами"
                            }
                        }
                    }
                }
            }
        }
        
        script(src = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js") {}
    }
}
