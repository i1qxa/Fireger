package com.firebasemanager.db

import org.jetbrains.exposed.sql.Table

object HistoryTable : Table("history_entries") {
    val id = integer("id").autoIncrement()
    val userId = long("user_id")
    val userName = varchar("user_name", 512)
    val projectId = varchar("project_id", 256)
    val actionType = varchar("action_type", 64)
    val actionText = varchar("action_text", 2048)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
