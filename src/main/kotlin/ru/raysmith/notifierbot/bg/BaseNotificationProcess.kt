package ru.raysmith.notifierbot.bg

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.raysmith.notifierbot.database.entity.Notification
import ru.raysmith.notifierbot.inPeriod
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class BaseNotificationProcess(override val notification: Notification) : NotificationProcess(notification) {

    override lateinit var job: Job

    override suspend fun cancelAndJoin() = job.cancelAndJoin()
    override suspend fun cancel(cause: CancellationException?) = job.cancel(cause)

    private fun getDelayToStart(now: LocalTime): Long {
        return if (now.inPeriod(notification)) {
            0
        } else if (now.isBefore(notification.start)) {
            notification.start.toSecondOfDay() - now.toSecondOfDay().toLong()
        } else {
            TimeUnit.HOURS.toSeconds(24) - (now.toSecondOfDay() - notification.start.toSecondOfDay())
        } * 1000
    }

    fun getDelayToNext(now: LocalTime): Long {
        if (notification.end != notification.start) {
            getDelayToStart(now).also {
                if (it > 0) {
                    return it
                }
            }
        }

        var date1 = notification.start
        while(true) {
            val date2 = date1 + notification.interval
            if (now.inPeriod(date1, date2)) {
                return ChronoUnit.MILLIS.between(now, date2).toInt().let {
                    if (it < 0) TimeUnit.HOURS.toMillis(24) + it else it.toLong()
                }
            } else if (now == date1 || now == date2) {
                return 0
            }
            date1 = date2
        }
    }

    override fun run(backgroundScope: CoroutineScope) = backgroundScope.launch {
        while (isActive) {
            val now = transaction { notification.user.now() }
            delay(getDelayToNext(now))
            notification.send()

            // delay until the end of the current second to prevent spam
            delay(Instant.now().let { it.toEpochMilli() - it.epochSecond * 1000 })
        }
    }.also { this.job = it }

}