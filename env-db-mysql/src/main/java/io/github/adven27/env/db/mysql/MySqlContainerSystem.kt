package io.github.adven27.env.db.mysql

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import mu.KLogging
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@Suppress("LongParameterList")
class MySqlContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = MYSQL_PORT,
    private var config: Config = Config(),
    private val afterStart: MySqlContainerSystem.() -> Unit = { }
) : MySQLContainer<Nothing>(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: MySqlContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    override fun start() {
        super.start()
        config = Config(jdbcUrl, username, password, driverClassName)
        apply(afterStart)
    }

    override fun start(fixedEnv: Boolean) {
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, MYSQL_PORT)
        }
        start()
    }

    override fun running() = isRunning

    override fun config() = config

    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config @JvmOverloads constructor(
        var jdbcUrl: String = "jdbc:mysql://localhost:$MYSQL_PORT/test?autoReconnect=true&useSSL=false",
        var username: String = "test",
        var password: String = "test",
        var driver: String = "com.mysql.cj.jdbc.Driver"
    ) {
        companion object {
            const val PROP_URL = "env.db.mysql.url"
            const val PROP_USER = "env.db.mysql.username"
            const val PROP_PASSWORD = "env.db.mysql.password"
            const val PROP_DRIVER = "env.db.mysql.driver"
        }

        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() =
            mapOf(PROP_URL to jdbcUrl, PROP_USER to username, PROP_PASSWORD to password, PROP_DRIVER to driver)
    }

    companion object : KLogging() {
        @JvmField
        val DEFAULT_IMAGE = "mysql:5.7.22".parseImage()
    }
}
