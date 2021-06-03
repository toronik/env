package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment
import io.github.adven27.env.mq.kafka.embedded.EmbeddedKafkaSystem.Companion.PROP_BOOTSTRAPSERVERS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedKafkaSystemTest {
    private lateinit var sut: SomeEnvironment

    @Test
    fun embeddedKafkaSystemStartsInEnvironment() {
        sut = SomeEnvironment().apply { up() }

        assertTrue(sut.kafka().running())

        val bootstrapServers = sut.kafka().config().bootstrapServers.value
        assertNotNull(bootstrapServers)
        assertEquals(bootstrapServers, System.getProperty(PROP_BOOTSTRAPSERVERS))
    }

    @After
    fun tearDown() {
        sut.down()
    }
}

class SomeEnvironment : Environment(
    "EMBEDDED_KAFKA" to EmbeddedKafkaSystem(topics = arrayOf("some-topic"))
) {
    fun kafka() = systems["EMBEDDED_KAFKA"] as EmbeddedKafkaSystem
}
