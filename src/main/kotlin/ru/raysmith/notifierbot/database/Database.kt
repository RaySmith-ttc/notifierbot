package ru.raysmith.notifierbot.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import ru.raysmith.notifierbot.database.entity.Notifications
import ru.raysmith.notifierbot.database.entity.Users

object Database : BaseDatabase() {
    override val version: Int = 1

    override val tables: List<Table> = listOf(
        Users, Notifications
    )

    override fun migration(connection: Database, toVersion: Int) {
        when (toVersion) {
            2 -> {

            }
            else -> {}
        }
    }
}