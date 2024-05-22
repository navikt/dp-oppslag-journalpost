package no.nav.dagpenger.oppslag.journalpost.id

import ch.qos.logback.core.util.OptionHelper.getEnv
import ch.qos.logback.core.util.OptionHelper.getSystemProperty
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

// Understands how to create a data source from environment variables
internal object PostgresDataSourceBuilder {
    const val DB_USERNAME_KEY = "DB_USERNAME"
    const val DB_PASSWORD_KEY = "DB_PASSWORD"
    const val DB_URL_KEY = "DB_JDBC_URL"

    private fun getOrThrow(key: String): String = getEnv(key) ?: getSystemProperty(key)

    private val logger = KotlinLogging.logger {}
    val dataSource by lazy {
        HikariDataSource().apply {
            jdbcUrl =
                getOrThrow(DB_URL_KEY).ensurePrefix("jdbc:postgresql://").stripCredentials().also {
                    logger.info("Connecting to $it")
                }
            username = getOrThrow(DB_USERNAME_KEY)
            password = getOrThrow(DB_PASSWORD_KEY)
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }
    }

    private fun flyWayBuilder() = Flyway.configure().connectRetries(10)

    private val flyWayBuilder: FluentConfiguration = Flyway.configure().connectRetries(10)

    fun clean() = flyWayBuilder.cleanDisabled(false).dataSource(dataSource).load().clean()

    internal fun runMigration(initSql: String? = null): Int =
        flyWayBuilder
            .dataSource(dataSource)
            .initSql(initSql)
            .load()
            .migrate()
            .migrations
            .size

    internal fun runMigrationTo(target: String): Int =
        flyWayBuilder()
            .dataSource(dataSource)
            .target(target)
            .load()
            .migrate()
            .migrations
            .size
}

private fun String.stripCredentials() = this.replace(Regex("://.*:.*@"), "://")

private fun String.ensurePrefix(prefix: String) =
    if (this.startsWith(prefix)) {
        this
    } else {
        prefix + this.substringAfter("//")
    }
