package io.github.adven27.env.db.db2

import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import org.testcontainers.containers.Db2Container
import org.testcontainers.utility.DockerImageName

@Suppress("unused", "LongParameterList")
class Db2ContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName,
    portsExposingStrategy: PortsExposingStrategy = PortsExposingStrategy.SystemPropertyToggle(),
    fixedPort: Int = DB2_PORT,
    private var config: Config = Config(),
    private val afterStart: Db2ContainerSystem.() -> Unit = { }
) : Db2Container(dockerImageName), ExternalSystem {

    init {
        acceptLicense()
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, DB2_PORT)
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
        var jdbcUrl: String = "jdbc:db2://localhost:$DB2_PORT/test",
        var username: String = "db2inst1",
        var password: String = "foobar1234",
        var driver: String = "com.ibm.db2.jcc.DB2Driver"
    ) {
        companion object {
            const val PROP_URL = "env.db.db2.url"
            const val PROP_USER = "env.db.db2.username"
            const val PROP_PASSWORD = "env.db.db2.password"
            const val PROP_DRIVER = "env.db.db2.driver"
        }

        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() =
            mapOf(PROP_URL to jdbcUrl, PROP_USER to username, PROP_PASSWORD to password, PROP_DRIVER to driver)
    }
}
