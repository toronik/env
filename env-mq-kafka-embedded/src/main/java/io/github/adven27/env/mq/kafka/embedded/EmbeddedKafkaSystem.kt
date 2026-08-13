package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.GenericExternalSystem
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker

@Suppress("unused")
open class EmbeddedKafkaSystem @JvmOverloads constructor(
    private val embeddedKafka: EmbeddedKafkaBroker,
    advertisedHost: String? = null,
    defaultPort: Int = DEFAULT_KAFKA_PORT
) : GenericExternalSystem<EmbeddedKafkaBroker, EmbeddedKafkaSystem.Config>(
    system = embeddedKafka,
    start = { fixedEnv, system ->
        val kraft = system is EmbeddedKafkaKraftBroker
        require(!kraft || advertisedHost == null) {
            "advertisedHost is not supported by the embedded KRaft broker: KafkaClusterTestKit binds its own " +
                "listeners and an override changes the advertised address only. " +
                "Use the containerized Kafka (env-mq-kafka) to reach a broker from outside the JVM."
        }
        if (kraft && fixedEnv) {
            throw UnsupportedOperationException(
                "Fixed port is not supported by the embedded KRaft broker: KafkaClusterTestKit binds its sockets " +
                    "before it is told which ports to use, so the requested $defaultPort is ignored. " +
                    "Run a dynamic environment and take the address from ${Config.PROP_BOOTSTRAPSERVERS}, " +
                    "or use the containerized Kafka (env-mq-kafka), which does keep a fixed port."
            )
        }
        val port = if (fixedEnv) defaultPort else Environment.findAvailableTcpPort()
        val advertised = advertisedHost?.let { host ->
            advertisedListener(host, port).let { (remote, props) ->
                system.brokerProperties(props)
                remote
            }
        }
        system.kafkaPorts(port).afterPropertiesSet()
        Config(listOfNotNull(system.brokersAsString, advertised).joinToString(", "))
    },
    stop = { embeddedKafka.destroy() },
    running = { System.getProperty(EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS) != null }
) {

    @Suppress("SpreadOperator")
    constructor(
        topics: Array<String> = emptyArray(),
        properties: MutableMap<String, String> = mutableMapOf(),
        advertisedHost: String? = null,
        defaultPort: Int = DEFAULT_KAFKA_PORT
    ) : this(
        EmbeddedKafkaKraftBroker(
            NUMBER_OF_BROKERS,
            NUMBER_OF_PARTITIONS,
            *topics
        ).brokerProperties(mapOf("group.initial.rebalance.delay.ms" to "0") + properties),
        advertisedHost,
        defaultPort
    )

    @Suppress("SpreadOperator")
    constructor(vararg topics: String) : this(topics = arrayOf(*topics))

    fun addTopics(vararg topics: String) {
        embeddedKafka.addTopicsWithResults(*topics)
            .filterValues { it != null }
            .let { failures ->
                require(failures.isEmpty()) {
                    "Не удалось создать топики: " + failures.entries.joinToString { "${it.key}: ${it.value.message}" }
                }
            }
    }

    fun topics(): Set<String> = embeddedKafka.topics

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
