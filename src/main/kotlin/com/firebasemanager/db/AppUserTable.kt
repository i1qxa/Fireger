package com.firebasemanager.db

import org.jetbrains.exposed.sql.Table

object AppUserTable : Table("app_users") {
    val userId = long("user_id")
    val chatId = long("chat_id").nullable()
    val name = varchar("name", 512).nullable()
    val avatarUrl = varchar("avatar_url", 2048).nullable()
    val role = varchar("role", 32)
    val accessRequestedAt = long("access_requested_at").nullable()
    val updatedAt = long("updated_at").nullable()

    override val primaryKey = PrimaryKey(userId)
}
