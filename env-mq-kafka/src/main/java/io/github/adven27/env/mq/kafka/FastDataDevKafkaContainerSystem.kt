package io.github.adven27.env.mq.kafka

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds

@Suppress("unused")
open class FastDataDevKafkaContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DockerImageName.parse(IMAGE),
    private val defaultPort: Int = PORT,
    private val defaultPortAdm: Int = PORT_ADM,
    private val afterStart: FastDataDevKafkaContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    override fun start(fixedEnv: Boolean) {
        withEnv("ADV_HOST", "127.0.0.1")
        withExposedPorts(PORT, PORT_ADM)
        withStartupTimeout(ofSeconds(STARTUP_TIMEOUT))
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, PORT)
            addFixedExposedPort(defaultPortAdm, PORT_ADM)
        }
        start()
    }

    override fun start() {
        super.start()
        config = Config(host, getMappedPort(PORT))
        apply(afterStart)
    }

    override fun running() = isRunning

    data class Config @JvmOverloads constructor(
        val host: String = "localhost",
        val port: Int = PORT
    ) : ExternalSystemConfig("env.mq.kafka.host" to host, "env.mq.kafka.port" to port.toString())

    companion object {
        private const val PORT = 9092
        private const val PORT_ADM = 3030
        private const val IMAGE = "lensesio/fast-data-dev"
        private const val STARTUP_TIMEOUT = 30L
    }
}
