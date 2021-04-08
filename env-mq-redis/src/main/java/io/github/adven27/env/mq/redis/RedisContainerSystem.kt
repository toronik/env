package io.github.adven27.env.mq.redis

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.setProperties
import io.github.adven27.env.core.Environment.Prop
import io.github.adven27.env.core.Environment.Prop.Companion.set
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds

class RedisContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = PORT,
    private var config: Config = Config(),
    private val afterStart: RedisContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    private val fixedPort: Int

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: RedisContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    init {
        withExposedPorts(PORT)
        withStartupTimeout(ofSeconds(STARTUP_TIMEOUT))
        this.fixedPort = fixedPort
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, PORT)
        }
    }

    override fun start() {
        super.start()
        config = Config(config.host.name set host, config.port.name set firstMappedPort.toString())
        apply(afterStart)
    }

    fun config() = config

    data class Config @JvmOverloads constructor(
        val host: Prop = "env.mq.redis.host" set "localhost",
        val port: Prop = "env.mq.redis.port" set PORT.toString()
    ) {
        init {
            mapOf(host.pair(), port.pair()).setProperties()
        }
    }

    companion object : KLogging() {
        private const val PORT = 6379
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE = "redis:5.0.3-alpine".parseImage()
    }

    override fun running() = isRunning
}
