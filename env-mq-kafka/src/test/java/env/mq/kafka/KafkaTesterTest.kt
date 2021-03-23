package env.mq.kafka

import env.core.Environment
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.AfterClass
import org.junit.Assert.assertThat
import org.junit.Test

class KafkaTesterTest {
    companion object {
        private val ENV = SomeEnvironment().apply {
            System.setProperty("SPECS_ENV_FIXED", "true")
            up()
        }

        private val SUT = KafkaTester(ENV.kafka().config().bootstrapServers.value, "topic2").apply { start() }

        @AfterClass
        fun tearDown() = SUT.stop().also { ENV.down() }
    }

    @Test
    fun sendAndReceive() {
        val expected = arrayOf("test1", "test2")
        expected.forEachIndexed { i, it -> SUT.send(it, mapOf("partition" to "$i")) }
        assertThat(SUT.receive().map { it.body }, containsInAnyOrder(*expected))
    }
}

/*
    kafka-topics --describe --topic topic1 --bootstrap-server localhost:9092
    kafka-topics --describe --topic topic2 --bootstrap-server localhost:9092
 */
class SomeEnvironment : Environment(
    "KAFKA" to KafkaContainerSystem(mapOf("topic1" to 1, "topic2" to 2))
) {
    fun kafka() = find<KafkaContainerSystem>("KAFKA")
}
