package io.github.adven27.env.selenium

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Suppress("unused")
open class SeleniumContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val afterStart: SeleniumContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {

    companion object {
        private const val PORT = 4444
        private const val STARTUP_TIMEOUT = 30L
        private const val SHARED_MEMORY_BYTES = 2L * 1024 * 1024 * 1024

        @JvmField
        val DEFAULT_IMAGE = "selenium/standalone-chrome".parseImage()
    }

    override lateinit var config: Config

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: SeleniumContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    override fun start(fixedEnv: Boolean) {
        withExposedPorts(PORT)
        withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT))
        if (fixedEnv) addFixedExposedPort(defaultPort, PORT)
        start()
    }

    override fun start() {
        withEnv("START_XVFB", "true")
        withEnv("SE_NODE_OVERRIDE_MAX_SESSIONS", "true")
        withEnv("SE_NODE_MAX_SESSIONS", "2")
        withSharedMemorySize(SHARED_MEMORY_BYTES)
        super.start()
        config = Config(host, firstMappedPort)
        apply(afterStart)
    }

    override fun running() = isRunning

    data class Config @JvmOverloads constructor(
        val host: String = "localhost",
        val port: Int = PORT
    ) : ExternalSystemConfig(
        "env.selenium.host" to host,
        "env.selenium.port" to port.toString()
    )
}
