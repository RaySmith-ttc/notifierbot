package ru.raysmith.notifierbot

import ru.raysmith.tgbot.model.bot.BotCommand
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.model.network.keyboard.KeyboardButton

val BotCommand.Companion.NEW get() = "new"
val BotCommand.Companion.LIST get() = "list"
val BotCommand.Companion.TIMEZONE get() = "timezone"

val KeyboardButton.Companion.TIMEZONE_MANUALLY_TEXT: String get() = "Choose manually"

val CallbackQuery.Companion.DELETE_ME: String get() = "delete_me"
val CallbackQuery.Companion.POSTPONE_PREFIX: String get() = "postpone_"

val CallbackQuery.Companion.TIMEZONE_INIT_PREFIX: String get() = "timezone_init_"
val CallbackQuery.Companion.TIMEZONE_SETTINGS_PREFIX: String get() = "timezone_settings_"
val CallbackQuery.Companion.INTERVAL_HOUR: String get() = "interval_h"
val CallbackQuery.Companion.INTERVAL_MIN: String get() = "interval_m"
val CallbackQuery.Companion.INTERVAL_SEC: String get() = "interval_s"

val CallbackQuery.Companion.INTERVAL_OK: String get() = "interval_ok"

val CallbackQuery.Companion.MIN_PLUS_PREFIX: String get() = "min_plus_"
val CallbackQuery.Companion.SEC_PLUS_PREFIX: String get() = "sec_plus_"
val CallbackQuery.Companion.HOUR_PLUS_PREFIX: String get() = "hour_plus_"

val CallbackQuery.Companion.BACK: String get() = "back"
val CallbackQuery.Companion.CONTINUE: String get() = "continue"
val CallbackQuery.Companion.CREATE: String get() = "create"

val CallbackQuery.Companion.START_HOUR: String get() = "start_h"
val CallbackQuery.Companion.START_MINUTE: String get() = "start_m"
val CallbackQuery.Companion.END_HOUR: String get() = "end_h"
val CallbackQuery.Companion.END_MINUTE: String get() = "end_m"

val CallbackQuery.Companion.SET_TIME_PREFIX: String get() = "set_time_"
val CallbackQuery.Companion.SET_START_HOUR_PREFIX: String get() = "set_start_h_"
val CallbackQuery.Companion.SET_START_MINUTE_PREFIX: String get() = "set_start_m_"
val CallbackQuery.Companion.SET_END_HOUR_PREFIX: String get() = "set_end_h_"
val CallbackQuery.Companion.SET_END_MINUTE_PREFIX: String get() = "set_end_m_"

val CallbackQuery.Companion.NOTIFICATIONS_PAGE_PREFIX: String get() = "notifications_p_"
val CallbackQuery.Companion.NOTIFICATION_DETAILS_PREFIX: String get() = "notification_details_"
val CallbackQuery.Companion.NOTIFICATION_PAUSE_SWAP_PREFIX: String get() = "notification_pause_"
val CallbackQuery.Companion.NOTIFICATION_DELETE_PREFIX: String get() = "notification_delete_"
val CallbackQuery.Companion.NOTIFICATION_DELETE_CONFIRM_PREFIX: String get() = "notification_confirm_delete"
