package ru.raysmith.notifierbot.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import ru.raysmith.exposedoption.Options
import ru.raysmith.exposedoption.getOrNull
import ru.raysmith.exposedoption.inc
import ru.raysmith.exposedoption.option
import ru.raysmith.notifierbot.common.Environment
import ru.raysmith.notifierbot.common.env
import ru.raysmith.notifierbot.common.envRequired
import ru.raysmith.utils.outcome
import ru.raysmith.utils.properties.PropertiesFactory
import java.time.ZoneId
import java.util.*
import kotlin.time.Duration.Companion.minutes

private val dbPass by envRequired("DB_PASS")
private val dbUser by env("DB_USER", "root")
private val dbName by env("DB_NAME", "notifierbot")
private val dbHost by env("DB_HOST", "localhost")
private val dbPort by env("DB_PORT") { it.toInt() }
private val dbDriver by env("DB_DRIVER", "com.mysql.cj.jdbc.Driver")

abstract class BaseDatabase : ru.raysmith.notifierbot.common.InitiableWithArgs<String>(),
    ru.raysmith.notifierbot.common.Versionable {
    companion object {
        const val NO_MIGRATION = -1
        val logger = LoggerFactory.getLogger("database")!!
        lateinit var properties: Properties
            private set

        val timeZone by lazy { ZoneId.of(properties["serverTimezone"]?.toString() ?: "UTC") }
    }

    private val isTest = dbHost.contains(":h2")
    abstract val tables: List<Table>
    val connection: Database get() = _connection ?: error("Can't provide connection before call Database.connect()")
    private var _connection: Database? = null

    abstract fun migration(connection: Database, toVersion: Int)

    override fun init() {
        init("db.properties")
    }

    override fun init(data: String) {
        if (isInit) return
        super.init()

        connect(data)
    }

    fun connect(dbProperties: String = "db.properties") {
        try {
            _connection = initConnection(dbProperties).also {
                transaction {
                    onConnection(isTest)
                }
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
            throw e
        }
    }

    /** Called after creation connection */
    open fun onConnection(isTest: Boolean) {}
    open fun beforeCreateTables() {}

    fun createMissingTablesAndColumns(vararg tables: Table = this.tables.toTypedArray()) = SchemaUtils.createMissingTablesAndColumns(*tables, withLogs = false)
    fun addMissingColumnsStatements(vararg tables: Table = this.tables.toTypedArray()) = SchemaUtils.addMissingColumnsStatements(*tables, withLogs = false)

    protected fun initConnection(dbProperties: String = "db.properties"): Database {
        Database.connect(hikari(dbProperties, useDatabase = false)).also {
            transaction {
                SchemaUtils.createDatabase(dbName)
            }
            TransactionManager.closeAndUnregister(it)
        }

        val config = DatabaseConfig {
            if (Environment.isDev()) {
                defaultRepetitionAttempts = 0
            }
        }

        return Database.connect(hikari(dbProperties), databaseConfig = config).also {
            transaction(it) {
                val loggerInterceptor = addLogger(object : SqlLogger {
                    override fun log(context: StatementContext, transaction: Transaction) {
                        if (logger.isDebugEnabled) {
                            logger.debug(context.expandArgs(TransactionManager.current()))
                        }
                    }
                })
                beforeCreateTables()
                SchemaUtils.create(Options)

                val databaseVersion = option<Int>("VERSION", database = it) { getOrNull() ?: set(version).value }

                var currentVersion = databaseVersion.value
                while(currentVersion != version && version != NO_MIGRATION) {
                    exposedLogger.info("Start migration from $currentVersion to ${currentVersion + 1}...")
                    migration(it, currentVersion + 1)
                    currentVersion = databaseVersion.inc().value
                }

                unregisterInterceptor(loggerInterceptor)
                createMissingTablesAndColumns()
                addMissingColumnsStatements()
                registerInterceptor(loggerInterceptor)
            }
        }
    }

    private fun hikari(dbProperties: String, useDatabase: Boolean = true): HikariDataSource {
        properties = PropertiesFactory.from(dbProperties)

        val baseUrl = dbHost + (dbPort?.let { ":$it" } ?: "")
        val jdbc = if (isTest) {
            dbHost
        } else {
            val builder = StringBuilder("jdbc:mysql://$baseUrl${useDatabase.outcome("/$dbName", "")}?")

            properties.forEach { (key, value) ->
                builder.append("$key=$value&")
            }
            builder.dropLast(1).toString()
        }

        val config = HikariConfig().apply {
            driverClassName = dbDriver
            jdbcUrl = jdbc
            maximumPoolSize = Runtime.getRuntime().availableProcessors()
            username = dbUser
            password = dbPass
            validationTimeout = 1.minutes.inWholeMilliseconds

            PropertiesFactory.from("hikari.properties").forEach { key, value ->
                addDataSourceProperty(key as String, value)
            }

            validate()
        }
        return HikariDataSource(config)
    }
}

