package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.GenericExternalSystem
import org.springframework.kafka.test.EmbeddedKafkaBroker

@Suppress("unused")
open class EmbeddedKafkaSystem(
    private val embeddedKafka: EmbeddedKafkaBroker,
    defaultPort: Int = DEFAULT_KAFKA_PORT,
) : GenericExternalSystem<EmbeddedKafkaBroker, EmbeddedKafkaSystem.Config>(
    system = embeddedKafka,
    config = Config(),
    start = { fixedEnv, system ->
        if (fixedEnv) system.kafkaPorts(defaultPort)
        system.afterPropertiesSet()
        Config(system.brokersAsString)
    },
    stop = { embeddedKafka.destroy() },
    running = { System.getProperty(EmbeddedKafkaBroker.SPRING_EMBEDDED_ZOOKEEPER_CONNECT) != null }
) {

    @Suppress("SpreadOperator")
    @JvmOverloads
    constructor(
        topics: Array<String>,
        defaultPort: Int = DEFAULT_KAFKA_PORT,
    ) : this(
        EmbeddedKafkaBroker(
            NUMBER_OF_BROKERS,
            CONTROLLED_SHUTDOWN,
            NUMBER_OF_PARTITIONS,
            *topics
        ),
        defaultPort
    )

    override fun describe() =
        super.describe() + "\n\t" + config().asMap().entries.joinToString("\n\t") { it.toString() }

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
