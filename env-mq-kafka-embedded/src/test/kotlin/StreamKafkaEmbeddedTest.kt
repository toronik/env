import io.github.adven27.env.core.Environment
import io.github.adven27.env.mq.kafka.embedded.EmbeddedKafkaSystem
import io.github.adven27.env.mq.kafka.embedded.EmbeddedKafkaSystem.Config.Companion.PROP_BOOTSTRAPSERVERS
import io.github.adven27.env.mq.kafka.embedded.StreamKafkaEmbedded
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamKafkaEmbeddedTest {
    private lateinit var sut: SomeEnvironment

    @Test
    fun streamKafkaEmbeddedStartsInEnvironment() {
        sut = SomeEnvironment().apply { up() }

        assertTrue(sut.kafka().running())
        assertEquals(sut.kafka().config.bootstrapServers, System.getProperty(PROP_BOOTSTRAPSERVERS))
    }

    @After
    fun tearDown() {
        sut.down()
    }
}

class SomeEnvironment : Environment(
    "EMBEDDED_KAFKA" to StreamKafkaEmbedded(
        EmbeddedKafkaSystem(topics = arrayOf("some-topic"))
    )
) {
    fun kafka() = env<StreamKafkaEmbedded>()
}
