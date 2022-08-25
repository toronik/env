package io.github.adven27.env.db.mysql

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@Suppress("unused")
open class MySqlContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = MYSQL_PORT,
    private val afterStart: MySqlContainerSystem.() -> Unit = { },
) : MySQLContainer<Nothing>(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: MySqlContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart,
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

    data class Config @JvmOverloads constructor(
        var jdbcUrl: String = "jdbc:mysql://localhost:$MYSQL_PORT/test?autoReconnect=true&useSSL=false",
        var username: String = "test",
        var password: String = "test",
        var driver: String = "com.mysql.cj.jdbc.Driver",
    ) : ExternalSystemConfig(
        PROP_URL to jdbcUrl,
        PROP_USER to username,
        PROP_PASSWORD to password,
        PROP_DRIVER to driver,
    ) {
        companion object {
            const val PROP_URL = "env.db.mysql.url"
            const val PROP_USER = "env.db.mysql.username"
            const val PROP_PASSWORD = "env.db.mysql.password"
            const val PROP_DRIVER = "env.db.mysql.driver"
        }
    }

    companion object : KLogging() {
        @JvmField
        val DEFAULT_IMAGE = "mysql".parseImage()
    }
}
