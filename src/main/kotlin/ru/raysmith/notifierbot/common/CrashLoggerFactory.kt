package ru.raysmith.notifierbot.common

import io.sentry.Sentry
import io.sentry.SentryOptions
import kotlinx.coroutines.CancellationException
import ru.raysmith.notifierbot.logger
import ru.raysmith.tgbot.network.TelegramApiException
import java.net.SocketException
import java.net.SocketTimeoutException

object CrashLoggerFactory {

    private val blackList = setOf(
        SocketTimeoutException::class, TelegramApiException::class, SocketException::class,
        CancellationException::class,
    )

    fun init() {
        Sentry.init { options ->
            options.dsn = "https://a830266dad01418d975e3680aa1f40c6@o1115014.ingest.sentry.io/6148704"
            options.tracesSampleRate = 1.0
            options.isDebug = Environment.isDev()
            options.environment = env.name.lowercase()
            options.isEnableAutoSessionTracking = false
            options.isEnableUncaughtExceptionHandler = true

            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (event.isCrashed && event.throwable != null) {
                    logger.error(event.throwable!!.message, event.throwable!!)
                }

                if (Environment.isDev()) {
                    return@BeforeSendCallback null
                }

                if (event.throwable != null && event.throwable!!::class in blackList) {
                    return@BeforeSendCallback null
                }

                event
            }
        }
    }
}