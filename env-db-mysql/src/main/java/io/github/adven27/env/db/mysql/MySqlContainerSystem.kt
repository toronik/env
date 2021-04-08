package io.github.adven27.env.db.mysql

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.setProperties
import io.github.adven27.env.core.Environment.Prop
import io.github.adven27.env.core.Environment.Prop.Companion.set
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle
import mu.KLogging
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@Suppress("LongParameterList")
class MySqlContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = MYSQL_PORT,
    private var config: Config = Config(),
    private val afterStart: MySqlContainerSystem.() -> Unit = { }
) : MySQLContainer<Nothing>(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: MySqlContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    init {
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, MYSQL_PORT)
        }
    }

    override fun start() {
        super.start()
        config = config.refreshValues()
        apply(afterStart)
    }

    override fun running() = isRunning

    private fun Config.refreshValues() = Config(
        jdbcUrl.name set getJdbcUrl(),
        username.name set getUsername(),
        password.name set getPassword(),
        driver.name set driverClassName
    )

    fun config() = config

    data class Config @JvmOverloads constructor(
        var jdbcUrl: Prop = PROP_URL set "jdbc:mysql://localhost:$MYSQL_PORT/test?autoReconnect=true&useSSL=false",
        var username: Prop = PROP_USER set "test",
        var password: Prop = PROP_PASSWORD set "test",
        var driver: Prop = PROP_DRIVER set "com.mysql.cj.jdbc.Driver"
    ) {
        init {
            mapOf(jdbcUrl.pair(), username.pair(), password.pair(), driver.pair()).setProperties()
        }

        constructor(url: String, username: String, password: String) : this(
            PROP_URL set url,
            PROP_USER set username,
            PROP_PASSWORD set password
        )
    }

    companion object : KLogging() {
        const val PROP_URL = "env.db.mysql.url"
        const val PROP_USER = "env.db.mysql.username"
        const val PROP_PASSWORD = "env.db.mysql.password"
        const val PROP_DRIVER = "env.db.mysql.driver"

        @JvmField
        val DEFAULT_IMAGE = "mysql:5.7.22".parseImage()
    }
}
