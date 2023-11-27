package ru.raysmith.notifierbot

import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import net.iakovlev.timeshape.TimeZoneEngine
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import ru.raysmith.notifierbot.bg.Background
import ru.raysmith.notifierbot.common.CrashLoggerFactory
import ru.raysmith.notifierbot.database.Database
import ru.raysmith.notifierbot.database.entity.User
import ru.raysmith.notifierbot.handlers.setup
import ru.raysmith.notifierbot.handlers.setupLocations
import ru.raysmith.notifierbot.model.Location
import ru.raysmith.tgbot.core.Bot
import ru.raysmith.tgbot.core.handler.location.LocationCallbackQueryHandler
import ru.raysmith.tgbot.core.handler.location.LocationMessageHandler
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.model.network.command.BotCommand
import ru.raysmith.tgbot.model.network.command.BotCommandScopeDefault
import ru.raysmith.tgbot.model.network.updates.Update
import ru.raysmith.tgbot.utils.locations.LocationConfig
import ru.raysmith.tgbot.utils.message.MessageAction
import ru.raysmith.tgbot.utils.messageText
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

val logger = LoggerFactory.getLogger("bot")
val timezonesEngine: TimeZoneEngine = TimeZoneEngine.initialize()
val maxInterval = Duration.ofHours(24)

class LocationConfigImpl(override val update: Update) : LocationConfig {
    val tgUser by lazy { transaction { update.findFrom()?.let { User.findOrAdd(it) } }!! }

    var toLocationMessageAction: MessageAction? = null
}
fun main() = runBlocking {
    System.setProperty("log4j2.formatMsgNoLookups", "true")

    CrashLoggerFactory.init()
    Database.init()
    Background.init()

    Bot()
        .onError { e ->
            Sentry.captureException(e)
            logger.error(e.message, e)
        }
        .enableBlocking { it.findChatId() ?: it.findFrom() }
        .onStart {
            setMyCommands(listOf(
                BotCommand(ru.raysmith.tgbot.model.bot.BotCommand.NEW, "Create new notificator"),
                BotCommand(ru.raysmith.tgbot.model.bot.BotCommand.LIST, "List of notificators"),
                BotCommand(ru.raysmith.tgbot.model.bot.BotCommand.SETTINGS, "Change timezone"),
            ), BotCommandScopeDefault())
        }
        .locations<LocationConfigImpl> {
            getLocation { tgUser.location.name }
            updateLocation { transaction { tgUser.location = Location.valueOf(it.name) } }
            config { LocationConfigImpl(it) }

            global {
                handleCommand { setup() }
                handleCallbackQuery {
                    isDataEqual(CallbackQuery.DELETE_ME) {
                        query.message?.safeDelete()
                    }

                    isDataStartWithAsNotification(CallbackQuery.POSTPONE_PREFIX) { notification ->
                        Background.postpone(notification, 1.minutes)
                        answer("Postponed by 1 min.")
                        query.message?.safeDelete()
                    }

                    isDataStartWithAsNotification(CallbackQuery.NOTIFICATION_PAUSE_SWAP_PREFIX) { notification ->
                        notification.isPaused = !notification.isPaused
                        if (notification.isPaused) {
                            Background.removeProcess(notification)
                        } else {
                            Background.addNotification(notification)
                        }
                        sendNotification(notification)
                    }

                    isDataStartWith(CallbackQuery.NOTIFICATION_DELETE_PREFIX) { notificationId ->
                        notification(notificationId) {
                            sendDeleteConfirmMessage(it)
                        }
                    }

                    isDataStartWith(CallbackQuery.NOTIFICATION_DELETE_CONFIRM_PREFIX) { notificationId ->
                        notification(notificationId) { notification ->
                            notification.delete()
                            Background.removeProcess(notification)
                            toLocation(Location.LIST, MessageAction.EDIT)
                            answer("Notification deleted")
                        }
                    }
                }
            }

            setupLocations()
        }

}

context(LocationConfigImpl)
suspend fun LocationCallbackQueryHandler<LocationConfigImpl>.setupInterval() {

    isDataStartWith(CallbackQuery.HOUR_PLUS_PREFIX) { hours ->
        notCompletedNotification { notification ->
            val newInterval = notification.interval.plusHours(hours.toLong())
            if (newInterval > maxInterval) {
                alertIntervalOutOfBound()
                return@notCompletedNotification
            }
            notification.interval = newInterval
            sendHoursMessage(notification, MessageAction.EDIT)
        }

    }

    isDataStartWith(CallbackQuery.MIN_PLUS_PREFIX) { minutes ->
        notCompletedNotification { notification ->
            val newInterval = notification.interval.plusMinutes(minutes.toLong())
            if (newInterval > maxInterval) {
                alertIntervalOutOfBound()
                return@notCompletedNotification
            }
            notification.interval = newInterval
            sendMinutesMessage(notification, MessageAction.EDIT)
        }
    }

    isDataStartWith(CallbackQuery.SEC_PLUS_PREFIX) { seconds ->
        notCompletedNotification { notification ->
            val newInterval = notification.interval.plusSeconds(seconds.toLong())
            if (newInterval > maxInterval) {
                alertIntervalOutOfBound()
                return@notCompletedNotification
            }
            notification.interval = newInterval
            sendSecondsMessage(notification, MessageAction.EDIT)
        }
    }

    isDataStartWith(CallbackQuery.INTERVAL_OK) {
        toLocation(Location.SETUP_INTERVAL, MessageAction.EDIT)
    }
}

context(LocationConfigImpl)
suspend fun LocationMessageHandler<LocationConfigImpl>.setupInterval()  {

    suspend fun sendError(message: String) = notCompletedNotification { notification ->
        when(tgUser.location) {
            Location.INTERVAL_HOUR -> sendHoursMessage(notification, MessageAction.EDIT) { italic(message) }
            Location.INTERVAL_MINUTES -> sendMinutesMessage(notification, MessageAction.EDIT) { italic(message) }
            Location.INTERVAL_SECONDS -> sendSecondsMessage(notification, MessageAction.EDIT) { italic(message) }
            else -> error("unknown location in interval setup: ${tgUser.location}")
        }
    }

    messageText()
        .convert("Must be an integer") { it.toLongOrNull() }
        .verify("Must be positive") { it > 0 }
        .onNull { sendError(it!!) }
        .onResult { result ->
            notCompletedNotification { notification ->
                val newInterval = when(tgUser.location) {
                    Location.INTERVAL_HOUR -> notification.interval.minusHours(notification.interval.toHours()).plusHours(result)
                    Location.INTERVAL_MINUTES -> notification.interval.minusMinutes(notification.interval.toMinutesPart().toLong()).plusMinutes(result)
                    Location.INTERVAL_SECONDS -> notification.interval.minusSeconds(notification.interval.toSecondsPart().toLong()).plusSeconds(result)
                    else -> error("unknown location in interval setup: ${tgUser.location}")
                }

                if (newInterval > maxInterval) {
                    sendIntervalOutOfBoundMessage(notification, MessageAction.SEND)
                    return@notCompletedNotification
                }

                notification.interval = newInterval
                toLocation(Location.SETUP_INTERVAL, MessageAction.SEND)
            }
        }
}
