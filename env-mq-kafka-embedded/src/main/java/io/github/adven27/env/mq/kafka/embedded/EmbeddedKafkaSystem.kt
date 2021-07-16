package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.FixedDynamicEnvironmentStrategy
import io.github.adven27.env.core.FixedDynamicEnvironmentStrategy.SystemPropertyToggle
import org.springframework.kafka.test.EmbeddedKafkaBroker

@Suppress("unused")
open class EmbeddedKafkaSystem(private val embeddedKafka: EmbeddedKafkaBroker) : ExternalSystem {

    @Suppress("SpreadOperator")
    @JvmOverloads
    constructor(
        topics: Array<String>,
        fixedPort: Int = DEFAULT_KAFKA_PORT,
        fixedDynamicEnvironmentStrategy: FixedDynamicEnvironmentStrategy = SystemPropertyToggle(),
    ) : this(
        EmbeddedKafkaBroker(
            NUMBER_OF_BROKERS,
            CONTROLLED_SHUTDOWN,
            NUMBER_OF_PARTITIONS,
            *topics
        ).apply {
            if (fixedDynamicEnvironmentStrategy.fixedEnv()) kafkaPorts(fixedPort)
        }
    )

    private var config: Config = Config()
    private var isRunning = false

    override fun start() {
        embeddedKafka.afterPropertiesSet()
        config = Config(embeddedKafka.brokersAsString)
        isRunning = true
    }

    override fun stop() {
        embeddedKafka.destroy()
        isRunning = false
    }

    override fun running() = isRunning
    override fun config(): Config = config
    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config(val bootstrapServers: String = "PLAINTEXT://localhost:$DEFAULT_KAFKA_PORT") {
        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() = mapOf(PROP_BOOTSTRAPSERVERS to bootstrapServers)

        companion object {
            const val PROP_BOOTSTRAPSERVERS = "env.mq.kafka.bootstrapServers"
        }
    }

    companion object {
        private const val DEFAULT_KAFKA_PORT = 9093
        private const val NUMBER_OF_BROKERS = 1
        private const val NUMBER_OF_PARTITIONS = 1
        private const val CONTROLLED_SHUTDOWN = true
    }
}
