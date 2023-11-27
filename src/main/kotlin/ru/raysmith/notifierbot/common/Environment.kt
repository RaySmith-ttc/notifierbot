package ru.raysmith.notifierbot.common

import ru.raysmith.utils.properties.PropertiesFactory
import ru.raysmith.utils.properties.getOrNull
import kotlin.reflect.KProperty

private val envs = mutableListOf<Env<*>>()

/** Значение из env.properties */
val env by lazy { Environment.value }

/** Значение из env.properties */
enum class Environment {
    DEV, PROD;

    companion object {
        val value by lazy {
            valueOf(
                PropertiesFactory.from("env.properties").getOrNull("value")?.uppercase()
                    ?: error("Не найдено значение 'value' в env.properties")
            )
        }
        fun isDev() = value != PROD
    }
}

private fun throwMissingEnv(key: String): Nothing = error("Required environment variable `$key` is missing")
private fun getenv(key: String): String? = System.getProperty(key) ?: System.getenv(key)

/**
 * Возвращает делегат со значением параметром запуска или переменной окружения. У параметра выше приоритет.
 * Если значение не установлено, возвращает [default]
 * */
@Suppress("UNCHECKED_CAST")
fun <T> env(key: String, default: T) = env(key, default) { it as T }

/**
 * Возвращает делегат со значением параметром запуска или переменной окружения. У параметра выше приоритет.
 * Если значение не установлено, возвращает [default]
 *
 * @param transform лямбда приведения строкового значения к [T]
 * */
@Suppress("UNCHECKED_CAST")
fun <T> env(key: String, default: T, transform: (String) -> T = { it as T }) = Env(key, false) {
    transform((getenv(it) ?: return@Env default)) ?: default
}

/**
 * Возвращает делегат со значением параметром запуска или переменной окружения. У параметра выше приоритет.
 * Если значение не установлено, возвращает null
 *
 * @param transform лямбда приведения строкового значения к [T]
 * */
@Suppress("UNCHECKED_CAST")
fun <T> env(key: String, transform: (String) -> T? = { it as T? }) = Env(key, false) {
    transform((getenv(it) ?: return@Env null))
}

/**
 * Возвращает делегат со значением параметром запуска или переменной окружения. У параметра выше приоритет.
 * Если значение не установлено, возвращает null
 * */
fun env(key: String) = Env(key, false) { getenv(it) }

/**
 * Возвращает делегат со значением параметром запуска или переменной окружения. У параметра выше приоритет.
 * Если значение не установлено, выбрасывает [IllegalStateException]
 * */
fun envRequired(key: String) = Env(key, true) { getenv(it) ?: throwMissingEnv(key) }

/**
 * Возвращает делегат со значением параметром запуска или переменной окружения. У параметра выше приоритет.
 * Если значение не установлено, выбрасывает [IllegalStateException]
 *
 * @param transform лямбда приведения строкового значения к [T]
 * */
@Suppress("UNCHECKED_CAST")
fun <T> envRequired(key: String, transform: (String) -> T = { it as T }) = Env(key, false) {
    transform(getenv(it) ?: throwMissingEnv(it))
}

/**
 * Обертка со значением переменной окружения
 *
 * @see env
 * @see envRequired
 * */
class Env<T : Any?>(val key: String, private val required: Boolean = false, private val getter: (key: String) -> T) {
    private var init = false
    private var cache: T? = null

    companion object {
        fun refresh() {
            envs.forEach {
                it.refresh()
            }
        }
    }

    init {
        envs.add(this)
        if (required) {
            getValue(null, null) ?: throwMissingEnv(key)
        }
    }

    /** Возвращает значение */
    fun get() = getValue(null, null)

    operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
        if (!init) {
            cache = getter(key)
            init = true
        }
        @Suppress("UNCHECKED_CAST")
        return cache as T
    }

    /** Обновляет кэш из [getter] */
    fun refresh(): T {
        cache = getter(key)

        @Suppress("UNCHECKED_CAST")
        return cache as T
    }
}
