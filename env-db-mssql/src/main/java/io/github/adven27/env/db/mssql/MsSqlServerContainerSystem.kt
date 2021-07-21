package io.github.adven27.env.db.mssql

import io.github.adven27.env.container.asCompatibleSubstituteFor
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import mu.KLogging
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.utility.DockerImageName

@Suppress("LongParameterList", "unused")
class MsSqlServerContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = MS_SQL_SERVER_PORT,
    private var config: Config = Config(),
    private val afterStart: MsSqlServerContainerSystem.() -> Unit = { },
) : MSSQLServerContainer<Nothing>(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: MsSqlServerContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart,
    )

    override fun start(fixedEnv: Boolean) {
        acceptLicense()
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, MS_SQL_SERVER_PORT)
        }
        start()
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
        var jdbcUrl: String = "jdbc:sqlserver://localhost\\Developer:$MS_SQL_SERVER_PORT",
        var username: String = "test",
        var password: String = "test",
        var driver: String = "com.microsoft.sqlserver.jdbc.SQLServerDriver",
    ) {
        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() =
            mapOf(PROP_URL to jdbcUrl, PROP_USER to username, PROP_PASSWORD to password, PROP_DRIVER to driver)
    }

    companion object : KLogging() {
        const val PROP_URL = "env.db.sqlserver.url"
        const val PROP_USER = "env.db.sqlserver.username"
        const val PROP_PASSWORD = "env.db.sqlserver.password"
        const val PROP_DRIVER = "env.db.sqlserver.driver"

        @JvmField
        val DEFAULT_IMAGE: DockerImageName =
            "mcr.microsoft.com/azure-sql-edge" asCompatibleSubstituteFor "mcr.microsoft.com/mssql/server"
    }
}
