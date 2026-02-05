package com.firebasemanager.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder

data class HistoryEntry(
    val id: Int,
    val userId: Long,
    val userName: String,
    val projectId: String,
    val actionType: String,
    val actionText: String,
    val createdAt: Long
)

private fun ResultRow.toHistoryEntry(): HistoryEntry = HistoryEntry(
    id = this[HistoryTable.id],
    userId = this[HistoryTable.userId],
    userName = this[HistoryTable.userName],
    projectId = this[HistoryTable.projectId],
    actionType = this[HistoryTable.actionType],
    actionText = this[HistoryTable.actionText],
    createdAt = this[HistoryTable.createdAt]
)

fun insertHistoryEntry(
    userId: Long,
    userName: String,
    projectId: String,
    actionType: String,
    actionText: String
) = transaction {
    val now = System.currentTimeMillis()
    HistoryTable.insert {
        it[HistoryTable.userId] = userId
        it[HistoryTable.userName] = userName
        it[HistoryTable.projectId] = projectId
        it[HistoryTable.actionType] = actionType
        it[HistoryTable.actionText] = actionText
        it[HistoryTable.createdAt] = now
    }
}

fun listHistoryEntries(projectIdFilter: String?): List<HistoryEntry> = transaction {
    val query = when {
        projectIdFilter == null || projectIdFilter == "all" || projectIdFilter.isEmpty() ->
            HistoryTable.selectAll().orderBy(HistoryTable.createdAt, SortOrder.DESC)
        else ->
            HistoryTable.select { HistoryTable.projectId eq projectIdFilter }.orderBy(HistoryTable.createdAt, SortOrder.DESC)
    }
    query.map { it.toHistoryEntry() }
}
