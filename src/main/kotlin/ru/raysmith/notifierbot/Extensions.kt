package ru.raysmith.notifierbot

import org.jetbrains.exposed.sql.transactions.transaction
import ru.raysmith.notifierbot.database.entity.Notification
import ru.raysmith.notifierbot.database.entity.User
import ru.raysmith.notifierbot.database.suspendTransaction
import ru.raysmith.tgbot.core.handler.EventHandler
import ru.raysmith.tgbot.core.handler.base.CallbackQueryHandler
import ru.raysmith.tgbot.core.handler.utils.ValueDataCallbackQueryHandler
import ru.raysmith.tgbot.core.send
import ru.raysmith.tgbot.model.bot.message.TextMessage
import ru.raysmith.tgbot.model.bot.message.keyboard.MessageInlineKeyboard
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.utils.message.MessageAction
import ru.raysmith.utils.outcome
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun String.toZoneId() = "(Z|[+-]\\d{2}:\\d{2})".toRegex().find(this)?.value?.let(ZoneId::of)
    ?: throw IllegalStateException("Wrong timezone pattern")

fun TextMessage.timezoneKeyboard(callbackQuery: String) {
    val instant = Instant.now()
    inlineKeyboard {
        ZoneId.getAvailableZoneIds().map { ZoneId.of(it).rules.getOffset(instant) }.distinct().sorted().chunked(4).forEach { row ->
            row {
                row.forEach { offset ->
                    val text = if (offset.toString() == "Z") "00:00" else offset.toString()
                    button(text, callbackQuery + offset)
                }
            }
        }
    }
}

fun LocalTime.inPeriod(start: LocalTime, end: LocalTime): Boolean {
    return when {
        start == end -> true
        start < end -> this in start..end
        start > end -> this >= start || this <= end
        else -> false
    }
}

fun LocalTime.inPeriod(notification: Notification) = this.inPeriod(notification.start, notification.end)

suspend fun CallbackQueryHandler.isDataStartWithAsNotification(
    startWith: String,
    startWithHandler: suspend ValueDataCallbackQueryHandler.(notification: Notification) -> Unit
) {
    if (!isAnswered && query.data != null && query.data!!.startsWith(startWith)) {
        val value = query.data!!.substring(startWith.length)
        if (value != CallbackQuery.EMPTY_CALLBACK_DATA) {
            ValueDataCallbackQueryHandler(query, value, client).let { handler ->
                suspendTransaction {
                    Notification.findById(value.toInt())?.apply { startWithHandler(handler, this) } ?: run {
                        query.message?.safeDelete()?.outcome(
                            whenTrue = suspend { answer { text = "Message is not valid" } },
                            whenFalse = suspend {
                                answer { text = "Message is not valid" }
                                val user = transaction { User.findOrAdd(query.from) }
                                sendNotificationsMessage(user, MessageAction.EDIT)
                            }
                        )?.invoke()
                    }
                }
            }
        }
    }
}

context(LocationConfigImpl)
suspend fun EventHandler.notCompletedNotification(block: suspend (Notification) -> Unit) {

    return suspendTransaction {
        tgUser.currentNotification?.let {
            if (it.isCompleted) null else it
        }?.also { block(it) } ?: run {
            if (this@notCompletedNotification is CallbackQueryHandler) {
                answer { text = "Message is not valid" }
            } else send("Message is not valid")
        }
    }
}

fun MessageInlineKeyboard.back(prefix: String) {
    row("« Back", prefix)
}

fun MessageInlineKeyboard.intervalRows(interval: Duration) {
    row {
        button("hour", CallbackQuery.EMPTY_CALLBACK_DATA)
        button("min", CallbackQuery.EMPTY_CALLBACK_DATA)
        button("sec", CallbackQuery.EMPTY_CALLBACK_DATA)
    }
    row {
        button(interval.toHours().toInt().let { if (it == 0) "-" else it.toString() }, CallbackQuery.INTERVAL_HOUR)
        button(interval.toMinutesPart().let { if (it == 0) "-" else it.toString() }, CallbackQuery.INTERVAL_MIN)
        button(interval.toSecondsPart().let { if (it == 0) "-" else it.toString() }, CallbackQuery.INTERVAL_SEC)
    }
}

private fun getQuery(plus: Int, current: Int, prefix: String, max: Int = 59) = if (plus > 0) {
    if (current == max) CallbackQuery.EMPTY_CALLBACK_DATA
    else prefix + min(plus, max - current)
} else if (plus < 0) {
    if (current == 0) CallbackQuery.EMPTY_CALLBACK_DATA
    else if (current + plus < 0) prefix + (0 - current)
    else prefix + plus
} else CallbackQuery.EMPTY_CALLBACK_DATA


fun TextMessage.minutesSelectKeyboard(current: Int) {
    inlineKeyboard {
        row {
            button("-10", getQuery(-10, current, CallbackQuery.MIN_PLUS_PREFIX))
            button("-5", getQuery(-5, current, CallbackQuery.MIN_PLUS_PREFIX))
            button("-1", getQuery(-1, current, CallbackQuery.MIN_PLUS_PREFIX))
            button("·${current}·", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("+1", getQuery(1, current, CallbackQuery.MIN_PLUS_PREFIX))
            button("+5", getQuery(5, current, CallbackQuery.MIN_PLUS_PREFIX))
            button("+10", getQuery(10, current, CallbackQuery.MIN_PLUS_PREFIX))
        }
        row("Ok", CallbackQuery.INTERVAL_OK)
    }
}

fun TextMessage.secondsSelectKeyboard(current: Int) {
    inlineKeyboard {
        row {
            button("-10", getQuery(-10, current, CallbackQuery.SEC_PLUS_PREFIX))
            button("-5", getQuery(-5, current, CallbackQuery.SEC_PLUS_PREFIX))
            button("-1", getQuery(-1, current, CallbackQuery.SEC_PLUS_PREFIX))
            button("·${current}·", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("+1", getQuery(1, current, CallbackQuery.SEC_PLUS_PREFIX))
            button("+5", getQuery(5, current, CallbackQuery.SEC_PLUS_PREFIX))
            button("+10", getQuery(10, current, CallbackQuery.SEC_PLUS_PREFIX))
        }
        row("Ok", CallbackQuery.INTERVAL_OK)
    }
}

fun TextMessage.hoursSelectKeyboard(current: Int) {
    inlineKeyboard {
        row {
            button("-10", getQuery(-10, current, CallbackQuery.HOUR_PLUS_PREFIX, 24))
            button("-5", getQuery(-5, current, CallbackQuery.HOUR_PLUS_PREFIX, 24))
            button("-1", getQuery(-1, current, CallbackQuery.HOUR_PLUS_PREFIX, 24))
            button("·${current}·", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("+1", getQuery(1, current, CallbackQuery.HOUR_PLUS_PREFIX, 24))
            button("+5", getQuery(5, current, CallbackQuery.HOUR_PLUS_PREFIX, 24))
            button("+10", getQuery(10, current, CallbackQuery.HOUR_PLUS_PREFIX, 24))
        }
        row("Ok", CallbackQuery.INTERVAL_OK)
    }
}

fun Int?.format() = (this ?: 0).let { if (it < 10) "0$it" else it.toString() }

fun Duration.botString() = buildString {
    toHours().toInt().let { if (it != 0) append(it).append("h. ") }
    toMinutesPart().let { if (it != 0) append(it).append("m. ") }
    toSecondsPart().let { if (it != 0) append(it).append("s. ") }
}.trim().ifEmpty { "0s." }

suspend fun EventHandler.notification(id: String, block: suspend (Notification) -> Unit) = suspendTransaction {
    Notification.findById(id.toInt())?.also { block(it) } ?: run {
        if (this@notification is CallbackQueryHandler) {
            answer { text = "Message is not valid" }
        } else send("Message is not valid")
    }
}

context(LocationConfigImpl)
suspend fun EventHandler.notification(block: suspend (Notification) -> Unit) = suspendTransaction {
    tgUser.currentNotification?.let { if (it.isCompleted) it else null }?.also { block(it) } ?: run {
        if (this@notification is CallbackQueryHandler) {
            answer { text = "Message is not valid" }
        } else send("Message is not valid")
    }
}