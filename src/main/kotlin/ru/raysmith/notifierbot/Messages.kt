package ru.raysmith.notifierbot

import ru.raysmith.notifierbot.bg.Background
import ru.raysmith.notifierbot.database.entity.Notification
import ru.raysmith.notifierbot.database.entity.User
import ru.raysmith.notifierbot.model.Location
import ru.raysmith.tgbot.core.handler.EventHandler
import ru.raysmith.tgbot.core.handler.base.CallbackQueryHandler
import ru.raysmith.tgbot.core.send
import ru.raysmith.tgbot.model.bot.BotCommand
import ru.raysmith.tgbot.model.bot.message.MessageText
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.model.network.keyboard.KeyboardButton
import ru.raysmith.tgbot.model.network.message.Message
import ru.raysmith.tgbot.model.network.message.ParseMode
import ru.raysmith.tgbot.utils.Pagination
import ru.raysmith.tgbot.utils.message.MessageAction
import ru.raysmith.tgbot.utils.message.message
import ru.raysmith.tgbot.utils.n
import ru.raysmith.utils.letIf
import ru.raysmith.utils.outcome
import ru.raysmith.utils.takeOrCut
import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit

context(LocationConfigImpl)
suspend fun EventHandler.sendTimezonesListMessage(action: MessageAction, message: String = "Choose your current timezone") = message(action) {
    text = message
    timezoneKeyboard(
        when(tgUser.location) {
            Location.INIT -> CallbackQuery.TIMEZONE_INIT_PREFIX
            Location.SETTINGS -> CallbackQuery.TIMEZONE_SETTINGS_PREFIX
            else -> error("sendTimezonesMessage not supported in ${tgUser.location} location")
        }
    )
}

context(LocationConfigImpl)
suspend fun EventHandler.sendTimezonesMessage(action: MessageAction, message: String = "Send your location or manually choose your current timezone") = message(action) {
    text = message

    replyKeyboard {
        row(KeyboardButton.TIMEZONE_MANUALLY_TEXT)
        row {
            button {
                text = "Send my location"
                requestLocation = true
            }
        }
    }
}

suspend fun EventHandler.sendUseMenuCommandsMessage(action: MessageAction) = message(action) {
    text = "Use menu commands"
}

suspend fun EventHandler.sendInfoMessage(action: MessageAction, removeKeyboard: Boolean, prefixMessage: MessageText.() -> MessageText = { this }) = send {
    textWithEntities {
        prefixMessage().n()
        n()
        text("Use commands from menu to manage your notifiers")
    }
    parseMode = ParseMode.HTML
    if (removeKeyboard) {
        removeKeyboard { }
    }
}

suspend fun EventHandler.sendNewNotifierMessage(notification: Notification, action: MessageAction = MessageAction.EDIT) = message(action) {
    text = "Setup interval"
    inlineKeyboard {
        intervalRows(notification.interval)
        row {
            button("« Cancel", CallbackQuery.BACK)
            button("Continue", CallbackQuery.CONTINUE)
        }

    }
}

context(LocationConfigImpl)
suspend fun EventHandler.sendHoursMessage(notification: Notification, action: MessageAction, prefixMessage: MessageText.() -> MessageText = { this }) = message(action) {
    textWithEntities {
        prefixMessage().n()
        n()
        text("Select hours or enter manually")
    }
    hoursSelectKeyboard(notification.interval.toHours().toInt())
}

private val intervalOutOfBoundMessageText = "The interval should not exceed 24 hours"

context(LocationConfigImpl)
suspend fun CallbackQueryHandler.alertIntervalOutOfBound() = alert(intervalOutOfBoundMessageText)

context(LocationConfigImpl)
suspend fun EventHandler.sendIntervalOutOfBoundMessage(notification: Notification, action: MessageAction) = when(tgUser.location) {
    Location.INTERVAL_HOUR -> sendHoursMessage(notification, action) { italic(intervalOutOfBoundMessageText) }
    Location.INTERVAL_MINUTES -> sendMinutesMessage(notification, action) { italic(intervalOutOfBoundMessageText) }
    Location.INTERVAL_SECONDS -> sendSecondsMessage(notification, action) { italic(intervalOutOfBoundMessageText) }
    else -> error("unknown location in interval setup: ${tgUser.location}")
}

suspend fun EventHandler.sendMinutesMessage(notification: Notification, action: MessageAction, prefixMessage: MessageText.() -> MessageText = { this }) = message(action) {
    textWithEntities {
        prefixMessage().n()
        n()
        text("Select minutes or enter manually")
    }
    minutesSelectKeyboard(notification.interval.toMinutesPart())
}

suspend fun EventHandler.sendSecondsMessage(notification: Notification, action: MessageAction, prefixMessage: MessageText.() -> MessageText = { this }) = message(action) {
    textWithEntities {
        prefixMessage().n()
        n()
        text("Select seconds or enter manually")
    }
    secondsSelectKeyboard(notification.interval.toSecondsPart())
}

suspend fun EventHandler.sendEnterMessage(action: MessageAction) = message(action) {
    text = "Enter notification text"
}

suspend fun EventHandler.sendTimeMessage(notification: Notification, action: MessageAction) = message(action) {

    textWithEntities {
        text("Select the time at which notifications will be received\n\n")
        italic("Set the same time to receive notifications 24/7")
    }
    inlineKeyboard {
        row {
            button("hour", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("min", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("—", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("hour", CallbackQuery.EMPTY_CALLBACK_DATA)
            button("min", CallbackQuery.EMPTY_CALLBACK_DATA)
        }
        row {
            button(notification.start.hour.format(), CallbackQuery.START_HOUR)
            button(notification.start.minute.format(), CallbackQuery.START_MINUTE)
            button("—", CallbackQuery.EMPTY_CALLBACK_DATA)
            button(notification.end.hour.format(), CallbackQuery.END_HOUR)
            button(notification.end.minute.format(), CallbackQuery.END_MINUTE)
        }
        row("Create »", CallbackQuery.CREATE)
    }
}

suspend fun EventHandler.sendTimeSelectMessage(hours: Int? = null, minutes: Int? = null) = edit {
    text = "Select ${(hours == null).outcome("hour", "minutes")}"

    inlineKeyboard {
        if (hours == null) {
            generateSequence(0) { it + 1 }.take(24).chunked(6).forEach { row ->
                row {
                    row.forEach {
                        button("${it.format()}:${minutes.format()}", CallbackQuery.SET_TIME_PREFIX + it)
                    }
                }
            }
        } else {
            generateSequence(0) { it + 5 }.take(12).chunked(4).forEach { row ->
                row {
                    row.forEach {
                        button("${hours.format()}:${it.format()}", CallbackQuery.SET_TIME_PREFIX + it)
                    }
                }
            }
        }
    }
}

suspend fun EventHandler.sendNotificationsMessage(user: User, action: MessageAction, page: Long = Pagination.PAGE_FIRST) = message(action) {
    val items = user.notifications()
    text = items.isEmpty().outcome(
        "You don't have any notification yet. Create a new one with the /${BotCommand.NEW} command",
        "List of notifications"
    )

    inlineKeyboard {
        pagination(items, CallbackQuery.NOTIFICATIONS_PAGE_PREFIX, page) { n ->
            button(n.message.letIf(n.isPaused) { "$it ⏳" }, CallbackQuery.NOTIFICATION_DETAILS_PREFIX + n.id)
        }
    }
}

suspend fun EventHandler.sendNotification(notification: Notification) = edit {
    textWithEntities {
        if (notification.isPaused) {
            bold("Paused ⏳\n\n")
        }
        bold("Interval: ").code(notification.interval.botString()).text("\n")
        bold("Time: ")
            .code(notification.start.format(timeFormat))
            .text(" — ")
            .code(notification.end.format(timeFormat)).text("\n")

        if (!notification.isPaused) {
            Background.getBaseNotificationProcess(notification)?.let {
                bold("Next in: ").code(
                    it.getDelayToNext(LocalTime.now(notification.user.timezone))
                        .let { ms -> Duration.of(ms, ChronoUnit.MILLIS) }
                        .botString()
                ).text("\n")
            }
        }
        bold("Message: ").text(notification.message.takeOrCut(4096 - currentTextLength))
    }

    inlineKeyboard {
        row(notification.isPaused.outcome("Turn on", "Turn off"), CallbackQuery.NOTIFICATION_PAUSE_SWAP_PREFIX + notification.id)
        row("Delete", CallbackQuery.NOTIFICATION_DELETE_PREFIX + notification.id)
        back(getPreviousPageCallback(CallbackQuery.NOTIFICATIONS_PAGE_PREFIX))
    }
}

suspend fun EventHandler.sendDeleteConfirmMessage(notification: Notification) = edit {
    text = "Are you sure you want to delete notification?"

    inlineKeyboard {
        row("Yes", CallbackQuery.NOTIFICATION_DELETE_CONFIRM_PREFIX + notification.id)
        row("Cancel", CallbackQuery.NOTIFICATION_DETAILS_PREFIX + notification.id)
    }
}