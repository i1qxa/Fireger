package com.firebasemanager.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

private val dbDir = File(System.getProperty("user.dir"), "data").apply { mkdirs() }
private val dbPath = File(dbDir, "projects").absolutePath

fun initDatabase() {
    Database.connect(
        "jdbc:h2:file:$dbPath;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE",
        driver = "org.h2.Driver"
    )
    transaction {
        SchemaUtils.create(ProjectTable)
    }
}
