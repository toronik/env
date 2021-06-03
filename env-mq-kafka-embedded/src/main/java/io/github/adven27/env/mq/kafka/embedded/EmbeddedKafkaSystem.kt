package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment.Companion.setProperties
import io.github.adven27.env.core.Environment.Prop
import io.github.adven27.env.core.Environment.Prop.Companion.set
import io.github.adven27.env.core.ExternalSystem
import org.springframework.kafka.test.EmbeddedKafkaBroker

open class EmbeddedKafkaSystem(
    topics: Array<String>,
    @Suppress("SpreadOperator")
    private val embeddedKafka: EmbeddedKafkaBroker = EmbeddedKafkaBroker(
        NUMBER_OF_BROKERS,
        CONTROLLED_SHUTDOWN,
        NUMBER_OF_PARTITIONS,
        *topics
    )
) : ExternalSystem {
    private var config: Config = Config()
    private var isRunning = false

    override fun running() = isRunning

    @Suppress("unused")
    fun config(): Config = config

    override fun start() {
        embeddedKafka.afterPropertiesSet()
        config = Config(PROP_BOOTSTRAPSERVERS set embeddedKafka.brokersAsString)
        isRunning = true
    }

    override fun stop() {
        embeddedKafka.destroy()
        isRunning = false
    }

    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    data class Config(val bootstrapServers: Prop = PROP_BOOTSTRAPSERVERS set "PLAINTEXT://localhost:$DEFAULT_KAFKA_PORT") {
        init {
            asMap().setProperties()
        }

        fun asMap() = mapOf(bootstrapServers.pair())
    }

    companion object {
        private const val DEFAULT_KAFKA_PORT = 9093

        private const val NUMBER_OF_BROKERS = 1
        private const val NUMBER_OF_PARTITIONS = 1
        private const val CONTROLLED_SHUTDOWN = true

        const val PROP_BOOTSTRAPSERVERS = "env.mq.kafka.bootstrapServers"
    }
}
