package io.github.adven27.env.db.postgresql

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle
import mu.KLogging
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@Suppress("LongParameterList")
class PostgreSqlContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = POSTGRESQL_PORT,
    private var config: Config = Config(),
    private val afterStart: PostgreSqlContainerSystem.() -> Unit = { }
) : PostgreSQLContainer<Nothing>(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: PostgreSqlContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    init {
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, POSTGRESQL_PORT)
        }
    }

    override fun start() {
        super.start()
        config = Config(jdbcUrl, username, password, driverClassName)
        apply(afterStart)
    }

    override fun running() = isRunning

    override fun config() = config

    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config @JvmOverloads constructor(
        val jdbcUrl: String = "jdbc:postgresql://localhost:$POSTGRESQL_PORT/postgres?stringtype=unspecified",
        val username: String = "test",
        val password: String = "test",
        val driver: String = "org.postgresql.Driver"
    ) {
        companion object {
            private const val PREFIX = "env.db.postgresql."
            const val PROP_URL = "${PREFIX}url"
            const val PROP_USER = "${PREFIX}username"
            const val PROP_PASSWORD = "${PREFIX}password"
            const val PROP_DRIVER = "${PREFIX}driver"
        }

        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() =
            mapOf(PROP_URL to jdbcUrl, PROP_USER to username, PROP_PASSWORD to password, PROP_DRIVER to driver)
    }

    companion object : KLogging() {
        @JvmField
        val DEFAULT_IMAGE = "postgres:9.6.12".parseImage()
    }
}
