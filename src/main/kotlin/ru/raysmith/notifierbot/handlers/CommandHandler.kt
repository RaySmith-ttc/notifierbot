package ru.raysmith.notifierbot.handlers

import ru.raysmith.notifierbot.*
import ru.raysmith.notifierbot.database.entity.Notification
import ru.raysmith.notifierbot.database.suspendTransaction
import ru.raysmith.notifierbot.model.Location
import ru.raysmith.tgbot.core.handler.location.LocationCommandHandler
import ru.raysmith.tgbot.model.bot.BotCommand
import ru.raysmith.tgbot.utils.message.MessageAction

context(LocationConfigImpl)
suspend fun LocationCommandHandler<LocationConfigImpl>.setup() {

    isCommand(BotCommand.START) {
        if (tgUser.location == Location.SETTINGS) {
            toLocation(Location.SETTINGS)
        } else {
            sendUseMenuCommandsMessage(MessageAction.SEND)
        }
    }

    if (!tgUser.registered) return

    isCommand(BotCommand.NEW) {
        suspendTransaction {
            tgUser.currentNotification = Notification.new {
                this.user = tgUser
                this.message = "No message"
            }
            toLocation(Location.SETUP_INTERVAL, MessageAction.SEND)
        }
    }

    isCommand(BotCommand.LIST) {
        toLocation(Location.LIST)
    }

    isCommand(BotCommand.TIMEZONE) {
        toLocation(Location.SETTINGS)
    }

    // alias
    isCommand(BotCommand.SETTINGS) {
        toLocation(Location.SETTINGS)
    }

}

