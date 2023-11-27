package ru.raysmith.notifierbot.database.entity

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import ru.raysmith.notifierbot.bg.Background
import ru.raysmith.notifierbot.model.Location
import java.time.LocalTime
import java.time.ZoneId

object Users : LongIdTable() {
    val location = enumerationByName("location", 255, Location::class).default(Location.SETTINGS)
    val timezone = varchar("timezone", 255).default("Z")
    val currentNotification = optReference("current_notification", Notifications, onDelete = ReferenceOption.SET_NULL)
    val registered = bool("registered").default(false)
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(Users) {
        fun findOrAdd(from: ru.raysmith.tgbot.model.network.User) = findOrAdd(from.id.value)
        fun findOrAdd(id: Long) = User.findById(id) ?: User.new(id) {}
    }

    var location by Users.location
    var timezone: ZoneId by Users.timezone.transform({ it.toString() }, { ZoneId.of(it) })
    var currentNotification by Notification optionalReferencedOn Users.currentNotification
    var registered by Users.registered

    /** Return a list of user notifications */
    fun notifications() = transaction { Notification.find { Notifications.user.eq(this@User.id) and Notifications.isCompleted.eq(true) }.toList() }

    fun restartAllNotifications() = transaction {
        runBlocking {
            Notification.find { Notifications.user.eq(this@User.id) and Notifications.isCompleted.eq(true) and Notifications.isPaused.eq(false) }
                .forEach {
                    Background.removeProcess(it)
                    Background.addNotification(it)
                }
        }
    }

    /** Return the current [LocalTime] for the user with their timezone */
    fun now(): LocalTime = LocalTime.now(timezone)
}