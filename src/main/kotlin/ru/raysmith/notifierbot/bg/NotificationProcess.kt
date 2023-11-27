package ru.raysmith.notifierbot.bg

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.raysmith.notifierbot.database.entity.Notification
import ru.raysmith.notifierbot.inPeriod
import ru.raysmith.notifierbot.logger
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

sealed class NotificationProcess(open val notification: Notification) {
    abstract var job: Job
    abstract fun run(backgroundScope: CoroutineScope): Job
    abstract suspend fun cancelAndJoin()
    abstract suspend fun cancel(cause: CancellationException? = null)
}



