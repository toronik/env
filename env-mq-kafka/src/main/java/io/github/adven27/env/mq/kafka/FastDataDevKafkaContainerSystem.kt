package io.github.adven27.env.mq.kafka

import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.FixedDynamicEnvironmentStrategy
import io.github.adven27.env.core.FixedDynamicEnvironmentStrategy.SystemPropertyToggle
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds

@Suppress("unused")
open class FastDataDevKafkaContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DockerImageName.parse(IMAGE),
    fixedDynamicEnvironmentStrategy: FixedDynamicEnvironmentStrategy = SystemPropertyToggle(),
    fixedPort: Int = PORT,
    fixedPortAdm: Int = PORT_ADM,
    private var config: Config = Config(),
    private val afterStart: FastDataDevKafkaContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    private val fixedPort: Int

    init {
        withEnv("ADV_HOST", "127.0.0.1")
        withExposedPorts(PORT, PORT_ADM)
        withStartupTimeout(ofSeconds(STARTUP_TIMEOUT))
        this.fixedPort = fixedPort
        if (fixedDynamicEnvironmentStrategy.fixedEnv()) {
            addFixedExposedPort(fixedPort, PORT)
            addFixedExposedPort(fixedPortAdm, PORT_ADM)
        }
    }

    override fun start() {
        super.start()
        config = Config(host, getMappedPort(PORT))
        apply(afterStart)
    }

    override fun config() = config

    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config @JvmOverloads constructor(
        val host: String = "localhost",
        val port: Int = PORT
    ) {
        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() = mapOf("env.mq.kafka.host" to host, "env.mq.kafka.port" to port.toString())
    }

    companion object : KLogging() {
        private const val PORT = 9092
        private const val PORT_ADM = 3030
        private const val IMAGE = "lensesio/fast-data-dev"
        private const val STARTUP_TIMEOUT = 30L
    }

    override fun running() = isRunning
}
