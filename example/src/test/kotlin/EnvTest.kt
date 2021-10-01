import io.github.adven27.env.core.Environment
import io.github.adven27.env.db.mysql.MySqlContainerSystem
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem
import io.github.adven27.env.grpc.GrpcMockContainerSystem
import io.github.adven27.env.mq.ibmmq.IbmMQContainerSystem
import io.github.adven27.env.mq.kafka.KafkaContainerSystem
import io.github.adven27.env.mq.rabbit.RabbitContainerSystem
import io.github.adven27.env.mq.redis.RedisContainerSystem
import io.github.adven27.env.wiremock.WiremockSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.testcontainers.containers.output.Slf4jLogConsumer

private const val PG_URL = "jdbc:postgresql://localhost:5432/test?loggerLevel=OFF"

class EnvTest {
    private lateinit var sut: SomeEnvironment

    @Test
    fun fixedEnvironment() {
        System.setProperty("SPECS_ENV_FIXED", "true")

        sut = SomeEnvironment().apply { up() }

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
        assertEquals(5672, sut.rabbit().config().port)
        assertEquals("5672", System.getProperty("env.mq.rabbit.port"))
        assertEquals(PG_URL, sut.postgres().config().jdbcUrl)
        assertEquals(PG_URL, System.getProperty("env.db.postgresql.url"))
    }

    @Test
    fun dynamicEnvironment() {
        System.setProperty("SPECS_ENV_FIXED", "false")

        sut = SomeEnvironment().apply { up() }

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
        assertNotEquals(5672, sut.rabbit().config().port)
        assertNotEquals(PG_URL, sut.postgres().config().jdbcUrl)
    }

    @After
    fun tearDown() {
        sut.down()
    }
}

class SomeEnvironment : Environment(
    "KAFKA" to KafkaContainerSystem(),
    "RABBIT" to RabbitContainerSystem(),
    "IBMMQ" to IbmMQContainerSystem(),
    "REDIS" to RedisContainerSystem(),
    "POSTGRES" to PostgreSqlContainerSystem(),
    "MYSQL" to MySqlContainerSystem(),
    "GRPC" to GrpcMockContainerSystem(1, listOf("common.proto", "wallet.proto")).apply {
        withLogConsumer(Slf4jLogConsumer(logger).withPrefix("GRPC-$serviceId"))
    },
    "WIREMOCK" to WiremockSystem()
) {
    fun rabbit() = find<RabbitContainerSystem>()
    fun postgres() = find<PostgreSqlContainerSystem>()
}
