package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.GenericExternalSystem
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.EmbeddedKafkaZKBroker

@Suppress("unused")
open class EmbeddedKafkaSystem @JvmOverloads constructor(
    private val embeddedKafka: EmbeddedKafkaBroker,
    advertisedHost: String? = null,
    defaultPort: Int = DEFAULT_KAFKA_PORT
) : GenericExternalSystem<EmbeddedKafkaBroker, EmbeddedKafkaSystem.Config>(
    system = embeddedKafka,
    start = { fixedEnv, system ->
        val port = if (fixedEnv) defaultPort else Environment.findAvailableTcpPort()
        var bootstrapServers = "localhost:$port"
        advertisedHost?.let {
            advertisedListener(it, port).also { (broker, props) ->
                system.brokerProperties(props)
                bootstrapServers += ", $broker"
            }
        }
        system.kafkaPorts(port).afterPropertiesSet()
        Config(bootstrapServers)
    },
    stop = { embeddedKafka.destroy() },
    running = { System.getProperty(EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS) != null }
) {

    @Suppress("SpreadOperator")
    constructor(
        topics: Array<String>,
        properties: MutableMap<String, String> = mutableMapOf(),
        advertisedHost: String? = null,
        defaultPort: Int = DEFAULT_KAFKA_PORT
    ) : this(
        EmbeddedKafkaZKBroker(
            NUMBER_OF_BROKERS,
            CONTROLLED_SHUTDOWN,
            NUMBER_OF_PARTITIONS,
            *topics
        ).brokerProperties(mapOf("group.initial.rebalance.delay.ms" to "0") + properties),
        advertisedHost,
        defaultPort
    )

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

        private fun advertisedListener(host: String, port: Int) =
            Environment.findAvailableTcpPort().let {
                ("$host:$it") to mapOf(
                    "listeners" to "PLAINTEXT://:$port, REMOTE://:$it",
                    "advertised.listeners" to "PLAINTEXT://localhost:$port, REMOTE://$host:$it",
                    "listener.security.protocol.map" to "PLAINTEXT:PLAINTEXT, REMOTE:PLAINTEXT"
                )
            }
    }
}
