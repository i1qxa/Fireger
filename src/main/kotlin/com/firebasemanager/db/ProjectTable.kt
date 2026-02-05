package com.firebasemanager.db

import org.jetbrains.exposed.sql.Table

object ProjectTable : Table("projects") {
    val userId = long("user_id")
    val projectId = varchar("project_id", 256)
    val bundleId = varchar("bundle_id", 512).nullable()
    val name = varchar("name", 512).nullable()
    val notionUrl = varchar("notion_url", 2048).nullable()
    val status = varchar("status", 64)
    val serviceAccountJson = text("service_account_json")
    val createdAt = long("created_at").nullable()

    override val primaryKey = PrimaryKey(userId, projectId)
}
