package ru.raysmith.notifierbot

import ru.raysmith.notifierbot.model.Location
import ru.raysmith.tgbot.core.BotContext
import ru.raysmith.tgbot.core.handler.LocationHandler
import ru.raysmith.tgbot.core.handler.base.CallbackQueryHandler
import ru.raysmith.tgbot.core.handler.location.LocationCallbackQueryHandler
import ru.raysmith.tgbot.model.network.CallbackQuery
import ru.raysmith.tgbot.utils.locations.LocationConfig
import ru.raysmith.tgbot.utils.locations.LocationsDSL
import ru.raysmith.tgbot.utils.locations.LocationsWrapper
import ru.raysmith.tgbot.utils.message.MessageAction

@LocationsDSL
suspend fun <T : LocationConfig> LocationsWrapper<T>.location(location: Location, newLocation: suspend ru.raysmith.tgbot.utils.locations.Location<T>.() -> Unit) {
    location(location.name, newLocation)
}

context(BotContext<*>)
suspend fun LocationHandler<LocationConfigImpl>.toLocation(location: Location, toLocationMessageAction: MessageAction? = null) {
    if (config.toLocationMessageAction == null) {
        config.toLocationMessageAction = toLocationMessageAction
    }
    toLocation(location.name)
}

context(BotContext<*>)
suspend fun LocationHandler<LocationConfigImpl>.toMenu() = toLocation(Location.MENU)

context(LocationCallbackQueryHandler<LocationConfigImpl>)
suspend fun setupBack(location: Location) {
    isDataEqual(CallbackQuery.BACK) { back(location) }
}

suspend fun CallbackQueryHandler.isBack(handler: (data: String?) -> Unit) {
    isDataEqual(CallbackQuery.BACK) {
        handler(null)
    }
}

suspend fun LocationCallbackQueryHandler<LocationConfigImpl>.back(location: Location) {
    config.toLocationMessageAction = MessageAction.EDIT
    toLocation(location.name)
}

fun LocationConfigImpl.actionOr(action: MessageAction) = toLocationMessageAction ?: action