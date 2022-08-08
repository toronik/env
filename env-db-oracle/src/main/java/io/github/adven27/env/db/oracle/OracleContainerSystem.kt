package io.github.adven27.env.db.oracle

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.OracleContainer
import org.testcontainers.utility.DockerImageName

@Suppress("LongParameterList", "unused")
open class OracleContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private var config: Config = Config(),
    private val afterStart: OracleContainerSystem.() -> Unit = { },
) : OracleContainer(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: OracleContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart,
    )

    override fun start(fixedEnv: Boolean) {
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, PORT)
        }
        withEnv("ORACLE_ALLOW_REMOTE", "true")
        withEnv("ORACLE_DISABLE_ASYNCH_IO", "true")
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
        var jdbcUrl: String = "jdbc:oracle:thin:system/oracle@localhost:$PORT:xe",
        var username: String = "system",
        var password: String = "oracle",
        var driver: String = "oracle.jdbc.OracleDriver",
    ) : ExternalSystemConfig(
        PROP_URL to jdbcUrl,
        PROP_USER to username,
        PROP_PASSWORD to password,
        PROP_DRIVER to driver,
    ) {
        companion object {
            const val PROP_URL = "env.db.oracle.url"
            const val PROP_USER = "env.db.oracle.username"
            const val PROP_PASSWORD = "env.db.oracle.password"
            const val PROP_DRIVER = "env.db.oracle.driver"
        }
    }

    companion object : KLogging() {
        private const val PORT = 1521

        @JvmField
        val DEFAULT_IMAGE = "oracleinanutshell/oracle-xe-11g".parseImage()
    }
}
