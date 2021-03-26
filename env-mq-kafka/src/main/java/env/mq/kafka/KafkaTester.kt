package env.mq.kafka

import com.adven.concordion.extensions.exam.mq.MqTester
import mu.KLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.time.Duration.ofMillis
import java.time.Duration.ofSeconds
import java.util.Properties

open class KafkaTester @JvmOverloads constructor(
    protected val bootstrapServers: String,
    protected val topic: String,
    protected val properties: Properties = DEFAULT_PROPERTIES,
    protected val pollTimeout: Duration = ofMillis(POLL_MILLIS),
    protected val partitionHeader: String = "partition"
) : MqTester {
    protected lateinit var producer: KafkaProducer<Long, String>
    protected lateinit var consumer: KafkaConsumer<Long, String>

    override fun purge() = logger.info("Purging topic {}...", topic).also {
        consumer.poll(ofMillis(POLL_MILLIS))
        logger.info("Topic {} is purged", topic)
    }

    override fun receive(): List<MqTester.Message> = logger.info("Reading from {}", topic).let {
        consumer.poll(pollTimeout).apply { consumer.commitAsync() }.map { MqTester.Message(it.value()) }
    }

    override fun send(message: String, headers: Map<String, String>) = logger.info("Sending to {}...", topic).also {
        producer.send(record(message, partitionFrom(headers))).get().apply {
            logger.info(
                "Sent to topic {} and partition {} with offset {}:\n{}", topic(), partition(), offset(), message
            )
        }
    }

    private fun partitionFrom(headers: Map<String, String>) = headers[partitionHeader]?.toInt()

    private fun record(value: String, partition: Int?): ProducerRecord<Long, String> =
        ProducerRecord(topic, partition, null, value)

    override fun start() {
        properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        producer = KafkaProducer<Long, String>(properties)
        consumer = KafkaConsumer<Long, String>(properties).apply { subscribe(listOf(topic)) }
        logger.info("KafkaTester started with properties:\n{}", properties)
    }

    override fun stop() {
        producer.close(ofSeconds(4))
        consumer.close(ofSeconds(4))
    }

    companion object : KLogging() {

        @JvmField
        val DEFAULT_PROPERTIES = Properties().apply {
            put(ProducerConfig.CLIENT_ID_CONFIG, "kafka-tester")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-tester")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
        private const val POLL_MILLIS: Long = 1500
    }
}
