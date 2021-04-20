package io.github.adven27.env.db.oracle

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.setProperties
import io.github.adven27.env.core.Environment.Prop
import io.github.adven27.env.core.Environment.Prop.Companion.set
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle
import mu.KLogging
import org.testcontainers.containers.OracleContainer
import org.testcontainers.utility.DockerImageName

@Suppress("LongParameterList")
class OracleContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = PORT,
    private var config: Config = Config(),
    private val afterStart: OracleContainerSystem.() -> Unit = { }
) : OracleContainer(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: OracleContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    init {
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, PORT)
        }
        withEnv("ORACLE_ALLOW_REMOTE", "true")
        withEnv("ORACLE_DISABLE_ASYNCH_IO", "true")
    }

    override fun start() {
        super.start()
        config = config.refreshValues()
        apply(afterStart)
    }

    private fun Config.refreshValues() = Config(
        jdbcUrl.name set getJdbcUrl(),
        username.name set getUsername(),
        password.name set getPassword(),
        driver.name set driverClassName
    )

    override fun running() = isRunning

    fun config() = config

    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config @JvmOverloads constructor(
        var jdbcUrl: Prop = PROP_URL set "jdbc:oracle:thin:system/oracle@localhost:$PORT:xe",
        var username: Prop = PROP_USER set "system",
        var password: Prop = PROP_PASSWORD set "oracle",
        var driver: Prop = PROP_DRIVER set "oracle.jdbc.OracleDriver"
    ) {
        init {
            asMap().setProperties()
        }

        fun asMap() = mapOf(jdbcUrl.pair(), username.pair(), password.pair(), driver.pair())

        constructor(url: String, username: String, password: String) : this(
            PROP_URL set url,
            PROP_USER set username,
            PROP_PASSWORD set password
        )
    }

    companion object : KLogging() {
        const val PROP_URL = "env.db.oracle.url"
        const val PROP_USER = "env.db.oracle.username"
        const val PROP_PASSWORD = "env.db.oracle.password"
        const val PROP_DRIVER = "env.db.oracle.driver"
        private const val PORT = 1521

        @JvmField
        val DEFAULT_IMAGE = "oracleinanutshell/oracle-xe-11g".parseImage()
    }
}
