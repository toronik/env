package io.github.adven27.env.mq.rabbit

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import mu.KLogging
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

@Suppress("unused")
open class RabbitContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val defaultPortAdm: Int = PORT_ADM,
    private var config: Config = Config(),
    private val afterStart: RabbitContainerSystem.() -> Unit = { }
) : RabbitMQContainer(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: RabbitContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    override fun start(fixedEnv: Boolean) {
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, PORT)
            addFixedExposedPort(defaultPortAdm, PORT_ADM)
        }
        start()
    }

    override fun start() {
        super.start()
        config = Config(host, amqpPort)
        apply(afterStart)
    }

    override fun running() = isRunning
    override fun config(): Config = config
    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config constructor(
        val host: String = "localhost",
        val port: Int = PORT
    ) {
        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() = mapOf(PROP_HOST to host, PROP_PORT to port.toString())

        companion object {
            const val PROP_HOST = "env.mq.rabbit.host"
            const val PROP_PORT = "env.mq.rabbit.port"
        }
    }

    companion object : KLogging() {
        private const val PORT = 5672
        private const val PORT_ADM = 15672

        @JvmField
        val DEFAULT_IMAGE: DockerImageName = "rabbitmq:3.7.25-management-alpine".parseImage()
    }
}
