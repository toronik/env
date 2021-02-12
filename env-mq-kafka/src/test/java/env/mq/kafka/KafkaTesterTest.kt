package env.mq.kafka

import env.core.Environment
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Test

class KafkaTesterTest {
    companion object {
        private val ENV = SomeEnvironment().apply {
            System.setProperty("SPECS_ENV_FIXED", "true")
            up()
        }

        private val SUT = KafkaTester(ENV.kafka().config().bootstrapServers.value, "ni").apply { start() }

        @AfterClass
        fun tearDown() = SUT.stop().also { ENV.down() }
    }

    @Test
    fun sendAndReceive() {
        val expected = listOf("test1", "test2", "test3")

        expected.forEach { SUT.send(it, emptyMap()) }

        assertEquals(expected, SUT.receive().map { it.body })
    }
}

class SomeEnvironment : Environment("KAFKA" to KafkaContainerSystem()) {
    fun kafka() = find<KafkaContainerSystem>("KAFKA")
}
