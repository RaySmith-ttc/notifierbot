package ru.raysmith.notifierbot.bg

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import ru.raysmith.notifierbot.database.entity.Notification
import ru.raysmith.notifierbot.database.entity.Notifications
import java.util.*
import kotlin.time.Duration

object Background {

    private val logger = LoggerFactory.getLogger("bg")
    private val job = Job()
    private val backgroundScope = CoroutineScope(Dispatchers.Default + job)
    private val processes = Collections.synchronizedList<NotificationProcess>(mutableListOf())

    private fun addAndRun(process: NotificationProcess) {
        processes.add(process)
        process.run(backgroundScope)
        logger.debug("Start process {} for notification #{}", process::class.simpleName, process.notification.id)
    }

    fun getBaseNotificationProcess(notification: Notification) = processes.find {
        it is BaseNotificationProcess && it.notification == notification
    } as? BaseNotificationProcess

    fun init() = backgroundScope.launch {
        transaction {
            Notification.find {
                Notifications.isCompleted.eq(true) and Notifications.isPaused.eq(false)
            }.forEach { notification ->
                addAndRun(BaseNotificationProcess(notification))
            }
        }
    }

    fun addNotification(notification: Notification): BaseNotificationProcess {
        return BaseNotificationProcess(notification).also(::addAndRun)
    }

    fun postpone(notification: Notification, time: Duration): PostponeNotificationProcess {
        return PostponeNotificationProcess(notification, time).also(::addAndRun)
    }

    fun removeProcess(notification: Notification) = runBlocking {
        processes.filter { it.notification == notification }.forEach {
            it.cancel()
            logger.debug("Process {} stopped for notification #{}", it::class.simpleName, notification.id)
        }
    }
}

