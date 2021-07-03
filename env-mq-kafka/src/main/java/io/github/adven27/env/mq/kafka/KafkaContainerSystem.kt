package io.github.adven27.env.mq.kafka

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle
import mu.KLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

@Suppress("unused")
open class KafkaContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = KAFKA_PORT,
    private var config: Config = Config(),
    private val topicNameAndPartitionCount: Map<String, Int> = mapOf(),
    private val afterStart: KafkaContainerSystem.() -> Unit = { }
) : KafkaContainer(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(
        dockerImageName: DockerImageName,
        topicsAndPartitionCount: Map<String, Int>,
        afterStart: KafkaContainerSystem.() -> Unit = { }
    ) : this(
        dockerImageName = dockerImageName,
        topicNameAndPartitionCount = topicsAndPartitionCount,
        afterStart = afterStart
    )

    @JvmOverloads
    constructor(topicsAndPartitionCount: Map<String, Int>, afterStart: KafkaContainerSystem.() -> Unit = { }) : this(
        topicNameAndPartitionCount = topicsAndPartitionCount,
        afterStart = afterStart
    )

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: KafkaContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    init {
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, KAFKA_PORT)
        }
    }

    override fun start() {
        super.start()
        config = Config(bootstrapServers.toString())
        createTopics(topicNameAndPartitionCount)
        apply(afterStart)
    }

    override fun running() = isRunning
    override fun config(): Config = config
    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

    private fun createTopics(topicNameAndPartitionCount: Map<String, Int>) =
        AdminClient.create(mapOf(BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServers)).use { admin ->
            admin.createTopics(
                topicNameAndPartitionCount.map { topic -> NewTopic(topic.key, topic.value, 1.toShort()) }
            )
        }

    data class Config(val bootstrapServers: String = "PLAINTEXT://localhost:$KAFKA_PORT") {
        init {
            asMap().propagateToSystemProperties()
        }

        fun asMap() = mapOf(PROP_BOOTSTRAPSERVERS to bootstrapServers)

        companion object {
            const val PROP_BOOTSTRAPSERVERS = "env.mq.kafka.bootstrapServers"
        }
    }

    companion object : KLogging() {
        @JvmField
        val DEFAULT_IMAGE: DockerImageName = "confluentinc/cp-kafka".parseImage().withTag("5.4.3")
    }
}
