package io.github.adven27.env.db.db2

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import org.testcontainers.containers.Db2Container
import org.testcontainers.utility.DockerImageName

@Suppress("unused", "LongParameterList")
open class Db2ContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName,
    private val defaultPort: Int = DB2_PORT,
    private var config: Config = Config(),
    private val afterStart: Db2ContainerSystem.() -> Unit = { }
) : Db2Container(dockerImageName), ExternalSystem {

    override fun start(fixedEnv: Boolean) {
        acceptLicense()
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, DB2_PORT)
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

    data class Config @JvmOverloads constructor(
        var jdbcUrl: String = "jdbc:db2://localhost:$DB2_PORT/test",
        var username: String = "db2inst1",
        var password: String = "foobar1234",
        var driver: String = "com.ibm.db2.jcc.DB2Driver"
    ) : ExternalSystemConfig(
        PROP_URL to jdbcUrl,
        PROP_USER to username,
        PROP_PASSWORD to password,
        PROP_DRIVER to driver
    ) {
        companion object {
            const val PROP_URL = "env.db.db2.url"
            const val PROP_USER = "env.db.db2.username"
            const val PROP_PASSWORD = "env.db.db2.password"
            const val PROP_DRIVER = "env.db.db2.driver"
        }
    }
}
