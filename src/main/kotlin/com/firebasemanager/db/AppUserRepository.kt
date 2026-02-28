package com.firebasemanager.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

private const val ROLE_ADMIN = "admin"
private const val ROLE_USER = "user"
private const val ROLE_NONE = "none"

data class AppUser(
    val userId: Long,
    val chatId: Long?,
    val name: String?,
    val avatarUrl: String?,
    val role: String,
    val accessRequestedAt: Long?,
    val updatedAt: Long?
) {
    val accessRequested: Boolean get() = accessRequestedAt != null
}

private fun ResultRow.toAppUser(): AppUser = AppUser(
    userId = this[AppUserTable.userId],
    chatId = this[AppUserTable.chatId],
    name = this[AppUserTable.name],
    avatarUrl = this[AppUserTable.avatarUrl],
    role = this[AppUserTable.role],
    accessRequestedAt = this[AppUserTable.accessRequestedAt],
    updatedAt = this[AppUserTable.updatedAt]
)

fun getOrCreateAppUser(
    userId: Long,
    chatId: Long? = null,
    name: String? = null,
    avatarUrl: String? = null
): AppUser = transaction {
    val existing = AppUserTable.select { AppUserTable.userId eq userId }.singleOrNull()
    if (existing != null) {
        val now = System.currentTimeMillis()
        AppUserTable.update({ AppUserTable.userId eq userId }) {
            if (chatId != null) it[AppUserTable.chatId] = chatId
            if (name != null) it[AppUserTable.name] = name
            if (avatarUrl != null) it[AppUserTable.avatarUrl] = avatarUrl
            it[AppUserTable.updatedAt] = now
        }
        ensureAtLeastOneAdmin()
        AppUserTable.select { AppUserTable.userId eq userId }.single().toAppUser()
    } else {
        val isFirst = AppUserTable.selectAll().empty()
        val role = if (isFirst) ROLE_ADMIN else ROLE_NONE
        val now = System.currentTimeMillis()
        AppUserTable.insert {
            it[AppUserTable.userId] = userId
            it[AppUserTable.chatId] = chatId
            it[AppUserTable.name] = name
            it[AppUserTable.avatarUrl] = avatarUrl
            it[AppUserTable.role] = role
            it[AppUserTable.updatedAt] = now
        }
        AppUserTable.select { AppUserTable.userId eq userId }.single().toAppUser()
    }
}

fun getAppUser(userId: Long): AppUser? = transaction {
    AppUserTable.select { AppUserTable.userId eq userId }.singleOrNull()?.toAppUser()
}

fun listAllAppUsers(): List<AppUser> = transaction {
    AppUserTable.selectAll().map { it.toAppUser() }
}

fun updateAppUserRole(userId: Long, role: String) = transaction {
    AppUserTable.update({ AppUserTable.userId eq userId }) {
        it[AppUserTable.role] = role
        it[AppUserTable.updatedAt] = System.currentTimeMillis()
    }
}

fun updateAppUserChatId(userId: Long, chatId: Long) = transaction {
    AppUserTable.update({ AppUserTable.userId eq userId }) {
        it[AppUserTable.chatId] = chatId
        it[AppUserTable.updatedAt] = System.currentTimeMillis()
    }
}

fun updateAppUserProfile(userId: Long, name: String?, avatarUrl: String?) = transaction {
    AppUserTable.update({ AppUserTable.userId eq userId }) {
        if (name != null) it[AppUserTable.name] = name
        if (avatarUrl != null) it[AppUserTable.avatarUrl] = avatarUrl
        it[AppUserTable.updatedAt] = System.currentTimeMillis()
    }
}

fun setAccessRequested(userId: Long) = transaction {
    val now = System.currentTimeMillis()
    AppUserTable.update({ AppUserTable.userId eq userId }) {
        it[AppUserTable.accessRequestedAt] = now
        it[AppUserTable.updatedAt] = now
    }
}

fun getAppUserChatId(userId: Long): Long? = transaction {
    AppUserTable.select { AppUserTable.userId eq userId }.singleOrNull()?.get(AppUserTable.chatId)
}

/** If there are no admins, set the first user (by userId) to admin. Call after delete or when ensuring invariants. */
fun ensureAtLeastOneAdmin() = transaction {
    val hasAdmin = AppUserTable.select { AppUserTable.role.eq(ROLE_ADMIN) }.any()
    if (hasAdmin) return@transaction
    val firstUserId = AppUserTable.selectAll().orderBy(AppUserTable.userId, SortOrder.ASC).limit(1).firstOrNull()?.get(AppUserTable.userId) ?: return@transaction
    AppUserTable.update({ AppUserTable.userId eq firstUserId }) {
        it[AppUserTable.role] = ROLE_ADMIN
        it[AppUserTable.updatedAt] = System.currentTimeMillis()
    }
}

fun deleteAppUser(userId: Long) = transaction {
    exec("DELETE FROM app_users WHERE user_id = $userId")
    ensureAtLeastOneAdmin()
}
