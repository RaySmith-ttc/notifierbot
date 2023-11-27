package ru.raysmith.notifierbot.database.entity

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.transactions.transaction
import ru.raysmith.exposedoption.COLLATE_UTF8MB4_GENERAL_CI
import ru.raysmith.notifierbot.DELETE_ME
import ru.raysmith.notifierbot.NOTIFICATION_DETAILS_PREFIX
import ru.raysmith.notifierbot.POSTPONE_PREFIX
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.utils.botContext
import ru.raysmith.tgbot.utils.toChatId
import java.time.Duration
import java.time.LocalTime

object Notifications : IntIdTable() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val message = text("message", collate = COLLATE_UTF8MB4_GENERAL_CI)
    val interval = varchar("interval", 255).default(Duration.ZERO.toString())
    val start = time("start").default(LocalTime.of(0, 0))
    val end = time("end").default(LocalTime.of(0, 0))
    val isCompleted = bool("is_completed").default(false)
    val isPaused = bool("is_paused").default(false)
}

class Notification(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Notification>(Notifications)

    var user by User referencedOn Notifications.user
    var message by Notifications.message
    var interval: Duration by Notifications.interval.transform({ it.toString() }, { Duration.parse(it) })
    var start by Notifications.start
    var end by Notifications.end
    var isCompleted by Notifications.isCompleted
    var isPaused by Notifications.isPaused

    suspend fun send() = botContext {
        send(transaction { user.id.value }.toChatId()) {
            text = message
            inlineKeyboard {
                row {
                    button("Postpone 1 min.", CallbackQuery.POSTPONE_PREFIX + this@Notification.id)
                    button("OK", CallbackQuery.DELETE_ME)
                }
//                row("Open notification details »", CallbackQuery.NOTIFICATION_DETAILS_PREFIX + this@Notification.id)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Notification) return false
        return other.id == id
    }

    override fun hashCode(): Int {
        var result = user.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + interval.hashCode()
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + isCompleted.hashCode()
        result = 31 * result + isPaused.hashCode()
        return result
    }
}