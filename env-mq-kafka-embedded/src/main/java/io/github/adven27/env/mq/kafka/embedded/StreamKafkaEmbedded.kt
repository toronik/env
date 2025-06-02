package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties

@Suppress("unused")
open class StreamKafkaEmbedded @JvmOverloads constructor(
    val topicSource: String = "in",
    val topicSink: String = "out",
    val topicDlq: String = "dlq",
    val group: String = "group"
) : EmbeddedKafkaSystem() {

    private val properties =
        mapOf(
            PROP_TOPIC_SOURCE to topicSource,
            PROP_TOPIC_SINK to topicSink,
            PROP_TOPIC_DLQ to topicDlq,
            PROP_GROUP to group
        ).apply { propagateToSystemProperties() }

    override fun describe() =
        super.describe() +
            (config.properties + properties).entries.joinToString("\n\t", "\n\t") { it.toString() }

    companion object {
        const val PROP_TOPIC_SOURCE = "env.mq.kafka.topic.source"
        const val PROP_TOPIC_SINK = "env.mq.kafka.topic.sink"
        const val PROP_TOPIC_DLQ = "env.mq.kafka.topic.dlq"
        const val PROP_GROUP = "env.mq.kafka.group"
    }
}
