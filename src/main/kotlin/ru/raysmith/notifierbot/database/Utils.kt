package ru.raysmith.notifierbot.database

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transactionManager


fun <T> suspendTransaction(db: Database? = null, statement: suspend Transaction.() -> T): T =
    org.jetbrains.exposed.sql.transactions.transaction(
        db.transactionManager.defaultIsolationLevel,
        db.transactionManager.defaultReadOnly,
        db
    ) {
        runBlocking { statement() }
    }