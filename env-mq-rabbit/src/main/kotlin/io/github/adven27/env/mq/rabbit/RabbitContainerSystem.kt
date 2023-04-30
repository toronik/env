package io.github.adven27.env.mq.rabbit

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

@Suppress("unused")
open class RabbitContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val defaultPortAdm: Int = PORT_ADM,
    private val afterStart: RabbitContainerSystem.() -> Unit = { }
) : RabbitMQContainer(dockerImageName), ExternalSystem {
    override lateinit var config: Config

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

    data class Config(
        val host: String = "localhost",
        val port: Int = PORT
    ) : ExternalSystemConfig(PROP_HOST to host, PROP_PORT to port.toString()) {
        companion object {
            const val PROP_HOST = "env.mq.rabbit.host"
            const val PROP_PORT = "env.mq.rabbit.port"
        }
    }

    companion object : KLogging() {
        private const val PORT = 5672
        private const val PORT_ADM = 15672

        @JvmField
        val DEFAULT_IMAGE: DockerImageName = "rabbitmq".parseImage()
    }
}
