package ru.raysmith.notifierbot.handlers

import ru.raysmith.notifierbot.*
import ru.raysmith.notifierbot.bg.Background
import ru.raysmith.notifierbot.database.suspendTransaction
import ru.raysmith.notifierbot.model.Location
import ru.raysmith.tgbot.core.edit
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.model.network.keyboard.KeyboardButton
import ru.raysmith.tgbot.utils.locations.LocationsDSL
import ru.raysmith.tgbot.utils.locations.LocationsWrapper
import ru.raysmith.tgbot.utils.message.MessageAction
import ru.raysmith.tgbot.utils.message.message
import ru.raysmith.tgbot.utils.messageLocation
import ru.raysmith.tgbot.utils.messageText
import ru.raysmith.tgbot.utils.n
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.jvm.optionals.getOrNull

@LocationsDSL
suspend fun LocationsWrapper<LocationConfigImpl>.setupLocations() {
    location(Location.SETTINGS) {
        onEnter { sendTimezonesMessage(actionOr(MessageAction.SEND)) }

        handle {
            handleMessage {
                suspendTransaction {
                    if (message.text == KeyboardButton.TIMEZONE_MANUALLY_TEXT) {
                        sendTimezonesListMessage(MessageAction.SEND)
                        return@suspendTransaction
                    }

                    messageLocation()
                        .convert { timezonesEngine.query(it.latitude.toDouble(), it.longitude.toDouble()).getOrNull() }
                        .onNull { sendTimezonesMessage(MessageAction.SEND, "Failed to determine the time zone by location") }
                        .onResult { zoneId ->
                            tgUser.timezone = zoneId
                            tgUser.restartAllNotifications()

                            toMenu()
                            if (tgUser.registered) {
                                send {
                                    textWithEntities {
                                        text("Timezone saved").n()
                                        n()
                                        italic(zoneId).italic(" (").italic(zoneId.rules.getOffset(Instant.now())).italic(")")
                                    }
                                    removeKeyboard { }
                                }
                            } else {
                                tgUser.registered = true
                                sendInfoMessage(MessageAction.SEND, removeKeyboard = true)
                            }
                        }
                }
            }

            handleCallbackQuery {
                isDataStartWith(CallbackQuery.TIMEZONE_SETTINGS_PREFIX) { timezone ->
                    suspendTransaction {
                        tgUser.timezone = timezone.toZoneId()
                        tgUser.restartAllNotifications()
                        toMenu()

                        if (tgUser.registered) {
                            query.message?.safeDelete()
                            send {
                                textWithEntities {
                                    text("Timezone saved\n\n")
                                    italic(timezone)
                                }
                                removeKeyboard { }
                            }
                        } else {
                            tgUser.registered = true
                            query.message?.safeDelete()
                            sendInfoMessage(MessageAction.SEND, removeKeyboard = true)
                        }
                    }
                }
            }
        }
    }

    location(Location.MENU) {
        // stub
    }

    location(Location.SETUP_INTERVAL) {
        onEnter {
            notCompletedNotification { sendNewNotifierMessage(it, actionOr(MessageAction.SEND)) }
        }

        handle {
            handleCallbackQuery {
                isDataEqual(CallbackQuery.BACK) {
                    suspendTransaction {
                        tgUser.currentNotification?.delete()
                        toMenu()
                        edit("Notification creation canceled")
                    }
                }

                isDataEqual(CallbackQuery.INTERVAL_HOUR) { toLocation(Location.INTERVAL_HOUR) }
                isDataEqual(CallbackQuery.INTERVAL_MIN) { toLocation(Location.INTERVAL_MINUTES) }
                isDataEqual(CallbackQuery.INTERVAL_SEC) { toLocation(Location.INTERVAL_SECONDS) }

                isDataEqual(CallbackQuery.CONTINUE) {
                    notCompletedNotification { notification ->
                        if (notification.interval.isZero) {
                            alert("Select at least one measure")
                            return@notCompletedNotification
                        }

                        toLocation(Location.INPUT, MessageAction.EDIT)
                    }
                }
            }
        }
    }

    location(Location.INTERVAL_HOUR) {
        onEnter { notCompletedNotification { sendHoursMessage(it, MessageAction.EDIT) } }

        handle {
            handleMessage { setupInterval() }
            handleCallbackQuery { setupInterval() }
        }
    }

    location(Location.INTERVAL_MINUTES) {
        onEnter { notCompletedNotification { sendMinutesMessage(it, MessageAction.EDIT) } }

        handle {
            handleMessage { setupInterval() }
            handleCallbackQuery { setupInterval() }
        }
    }

    location(Location.INTERVAL_SECONDS) {
        onEnter { notCompletedNotification { sendSecondsMessage(it, MessageAction.EDIT) } }

        handle {
            handleMessage { setupInterval() }
            handleCallbackQuery { setupInterval() }
        }
    }

    location(Location.INPUT) {
        onEnter { notCompletedNotification { sendEnterMessage(MessageAction.EDIT) } }

        handle {
            handleMessage {
                messageText {
                    notCompletedNotification { notification ->
                        notification.message = it
                        toLocation(Location.MESSAGE, MessageAction.SEND)
                    }
                }
            }
        }
    }

    location(Location.MESSAGE) {
        onEnter { notCompletedNotification { sendTimeMessage(it, actionOr(MessageAction.SEND)) } }

        handle {
            handleCallbackQuery {
                isDataEqual(CallbackQuery.START_HOUR) {
                    notCompletedNotification { toLocation(Location.TIME_START_HOUR) }
                }

                isDataEqual(CallbackQuery.START_MINUTE) {
                    notCompletedNotification { toLocation(Location.TIME_START_MINUTE) }
                }

                isDataEqual(CallbackQuery.END_HOUR) {
                    notCompletedNotification { toLocation(Location.TIME_END_HOUR) }
                }

                isDataEqual(CallbackQuery.END_MINUTE) {
                    notCompletedNotification { toLocation(Location.TIME_END_MINUTE) }
                }

                // Create
                isDataEqual(CallbackQuery.CREATE) {
                    notCompletedNotification { notification ->
                        notification.isCompleted = true
                        tgUser.currentNotification = null

                        val next = Background.addNotification(notification)
                            .getDelayToNext(LocalTime.now(notification.user.timezone))
                            .let { Duration.of(it, ChronoUnit.MILLIS) }
                            .botString()

                        edit {
                            textWithEntities {
                                text("Notification created").n()
                                n()
                                italic("Next in ").italic(next)
                            }
                        }

                        toMenu()
                    }
                }
            }
        }
    }

    location(Location.TIME_START_HOUR) {
        onEnter { notCompletedNotification { sendTimeSelectMessage(minutes = it.start.minute) } }

        handle {
            handleCallbackQuery {
                isDataStartWith(CallbackQuery.SET_TIME_PREFIX) { hours ->
                    notCompletedNotification { notification ->
                        notification.start = notification.start.withHour(hours.toInt())
                        toLocation(Location.MESSAGE, MessageAction.EDIT)
                    }
                }
            }
        }
    }

    location(Location.TIME_START_MINUTE) {
        onEnter { notCompletedNotification { sendTimeSelectMessage(hours = it.start.hour) } }

        handle {
            handleCallbackQuery {
                isDataStartWith(CallbackQuery.SET_START_MINUTE_PREFIX) { minutes ->
                    notCompletedNotification { notification ->
                        notification.start = notification.start.withMinute(minutes.toInt())
                        toLocation(Location.MESSAGE, MessageAction.EDIT)
                    }
                }
            }
        }
    }

    location(Location.TIME_END_HOUR) {
        onEnter { notCompletedNotification { sendTimeSelectMessage(minutes = it.start.minute) } }

        handle {
            handleCallbackQuery {
                isDataStartWith(CallbackQuery.SET_TIME_PREFIX) { hours ->
                    notCompletedNotification { notification ->
                        notification.end = notification.end.withHour(hours.toInt())
                        toLocation(Location.MESSAGE, MessageAction.EDIT)
                    }
                }
            }
        }
    }

    location(Location.TIME_END_MINUTE) {
        onEnter { notCompletedNotification { sendTimeSelectMessage(hours = it.start.hour) } }

        handle {
            handleCallbackQuery {
                isDataStartWith(CallbackQuery.SET_START_MINUTE_PREFIX) { minutes ->
                    notCompletedNotification { notification ->
                        notification.end = notification.end.withMinute(minutes.toInt())
                        toLocation(Location.MESSAGE, MessageAction.EDIT)
                    }
                }
            }
        }
    }

    location(Location.LIST) {
        onEnter { sendNotificationsMessage(tgUser, actionOr(MessageAction.SEND)) }

        handle {
            handleCallbackQuery {
                isPage(CallbackQuery.NOTIFICATIONS_PAGE_PREFIX) {
                    sendNotificationsMessage(tgUser, MessageAction.SEND, page)
                }

                isDataStartWith(CallbackQuery.NOTIFICATION_DETAILS_PREFIX) { notificationId ->
                    notification(notificationId) {
                        tgUser.currentNotification = it
                        toLocation(Location.DETAILS)
                    }
                }
            }
        }
    }

    location(Location.DETAILS) {
        onEnter { notification { sendNotification(it) } }

        // handled in global
    }
}