package env.mq.kafka

import env.core.Environment.Companion.setProperties
import env.core.Environment.Prop
import env.core.Environment.Prop.Companion.set
import env.core.ExternalSystem
import env.core.PortsExposingStrategy
import env.core.PortsExposingStrategy.SystemPropertyToggle
import mu.KLogging
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

open class KafkaContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DockerImageName.parse("confluentinc/cp-kafka").withTag("5.4.3"),
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = KAFKA_PORT,
    private var config: Config = Config(),
    private val afterStart: KafkaContainerSystem.() -> Unit = { }
) : KafkaContainer(dockerImageName), ExternalSystem {

    init {
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, KAFKA_PORT)
        }
    }

    override fun start() {
        super.start()
        config = Config(config.bootstrapServers.name set bootstrapServers.toString())
        apply(afterStart)
    }

    override fun running() = isRunning

    @Suppress("unused")
    fun config(): Config = config

    data class Config(val bootstrapServers: Prop = PROP_BOOTSTRAPSERVERS set "PLAINTEXT://localhost:$KAFKA_PORT") {
        init {
            mapOf(bootstrapServers.pair()).setProperties()
        }
    }

    companion object : KLogging() {
        const val PROP_BOOTSTRAPSERVERS = "env.mq.kafka.bootstrapServers"
    }
}
