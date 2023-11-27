package ru.raysmith.notifierbot.bg

import kotlinx.coroutines.*
import ru.raysmith.notifierbot.database.entity.Notification
import kotlin.time.Duration

class PostponeNotificationProcess(override val notification: Notification, val time: Duration) : NotificationProcess(notification) {

    override lateinit var job: Job

    override suspend fun cancelAndJoin() = job.cancelAndJoin()
    override suspend fun cancel(cause: CancellationException?) = job.cancel(cause)

    override fun run(backgroundScope: CoroutineScope) = backgroundScope.launch {
        delay(time)
        notification.send()
    }.also { this.job = it }
}