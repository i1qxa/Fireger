package com.firebasemanager.db

import org.jetbrains.exposed.sql.Table

object LinkTemplateTable : Table("link_templates") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 512)
    val link = varchar("link", 2048)

    override val primaryKey = PrimaryKey(id)
}
