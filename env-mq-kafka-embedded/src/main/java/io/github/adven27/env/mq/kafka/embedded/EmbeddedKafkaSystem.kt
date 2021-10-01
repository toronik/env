package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.ExternalSystemConfig
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
    constructor(
        topics: Array<String>,
        properties: MutableMap<String, String> = mutableMapOf(),
        defaultPort: Int = DEFAULT_KAFKA_PORT,
    ) : this(
        EmbeddedKafkaBroker(
            NUMBER_OF_BROKERS,
            CONTROLLED_SHUTDOWN,
            NUMBER_OF_PARTITIONS,
            *topics
        ).brokerProperties(mapOf("group.initial.rebalance.delay.ms" to "0") + properties),
        defaultPort
    )

    constructor(topics: Array<String>, properties: MutableMap<String, String> = mutableMapOf()) :
        this(topics, properties, DEFAULT_KAFKA_PORT)

    @Suppress("SpreadOperator")
    constructor(vararg topics: String) : this(topics = arrayOf(*topics))

    override fun toString() = "Embedded Kafka Broker"

    open class Config(val bootstrapServers: String = "PLAINTEXT://localhost:$DEFAULT_KAFKA_PORT") :
        ExternalSystemConfig(PROP_BOOTSTRAPSERVERS to bootstrapServers) {
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
