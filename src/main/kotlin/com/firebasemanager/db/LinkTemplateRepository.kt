package com.firebasemanager.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class LinkTemplate(
    val id: Int,
    val name: String,
    val link: String
)

private fun ResultRow.toLinkTemplate(): LinkTemplate = LinkTemplate(
    id = this[LinkTemplateTable.id],
    name = this[LinkTemplateTable.name],
    link = this[LinkTemplateTable.link]
)

fun listLinkTemplates(): List<LinkTemplate> = transaction {
    LinkTemplateTable.selectAll().map { it.toLinkTemplate() }
}

fun insertLinkTemplate(name: String, link: String): LinkTemplate = transaction {
    val id = LinkTemplateTable.insert {
        it[LinkTemplateTable.name] = name
        it[LinkTemplateTable.link] = link
    } get LinkTemplateTable.id
    LinkTemplateTable.select { LinkTemplateTable.id.eq(id) }.single().toLinkTemplate()
}

fun deleteLinkTemplate(id: Int) = transaction {
    exec("DELETE FROM link_templates WHERE id = $id")
}
