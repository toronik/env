package io.github.adven27.env.mq.kafka.embedded

import io.github.adven27.env.core.Environment
import io.github.adven27.env.core.Environment.Config
import io.github.adven27.env.core.EnvironmentStrategy
import io.github.adven27.env.mq.kafka.embedded.EmbeddedKafkaSystem.Config.Companion.PROP_BOOTSTRAPSERVERS
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG
import org.apache.kafka.clients.admin.AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedKafkaSystemTest {
    private lateinit var sut: Environment

    @Test
    fun embeddedKafkaSystemStartsInEnvironment() {
        val env = SomeEnvironment().apply { up() }
        sut = env

        assertTrue(env.kafka().running())
        assertEquals(env.kafka().config.bootstrapServers, System.getProperty(PROP_BOOTSTRAPSERVERS))
    }

    @Test
    fun reportedAddressAcceptsClients() {
        val env = SomeEnvironment().apply { up() }
        sut = env

        Admin.create(
            mapOf(
                BOOTSTRAP_SERVERS_CONFIG to env.kafka().config.bootstrapServers,
                REQUEST_TIMEOUT_MS_CONFIG to "8000",
                DEFAULT_API_TIMEOUT_MS_CONFIG to "12000"
            )
        ).use { assertTrue(it.listTopics().names().get().contains("some-topic")) }
    }

    @Test
    fun topicsCanBeAddedToARunningEnvironment() {
        val env = SomeEnvironment().apply { up() }
        sut = env

        env.kafka().addTopics("late-topic")

        assertTrue(env.kafka().topics().contains("late-topic"))
        Admin.create(
            mapOf(
                BOOTSTRAP_SERVERS_CONFIG to env.kafka().config.bootstrapServers,
                REQUEST_TIMEOUT_MS_CONFIG to "8000",
                DEFAULT_API_TIMEOUT_MS_CONFIG to "12000"
            )
        ).use { assertTrue(it.listTopics().names().get().contains("late-topic")) }
    }

    @Test
    fun fixedEnvironmentIsNotSupported() {
        val port = Environment.findAvailableTcpPort()
        val env = FixedEnvironment(port)

        val thrown = assertThrows(Exception::class.java) { env.up() }
        val unsupported = generateSequence(thrown as Throwable) { it.cause }
            .filterIsInstance<UnsupportedOperationException>()
            .first()

        assertTrue(unsupported.message!!.contains(port.toString()))
        assertTrue(unsupported.message!!.contains("env-mq-kafka"))
    }

    @After
    fun tearDown() {
        if (this::sut.isInitialized) {
            sut.down()
        }
    }
}

class SomeEnvironment : Environment("EMBEDDED_KAFKA" to EmbeddedKafkaSystem("some-topic")) {
    fun kafka() = env<EmbeddedKafkaSystem>()
}

class FixedEnvironment(port: Int) : Environment(
    Config(envStrategy = EnvironmentStrategy.FixedEnv),
    "EMBEDDED_KAFKA" to EmbeddedKafkaSystem(arrayOf("fixed-topic"), defaultPort = port)
) {
    fun kafka() = env<EmbeddedKafkaSystem>()
}
