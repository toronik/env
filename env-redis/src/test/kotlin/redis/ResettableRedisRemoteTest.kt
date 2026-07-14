package redis

import io.github.adven27.env.redis.resettableRedis
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.GenericContainer

class ResettableRedisRemoteTest {

    private val redis = GenericContainer<Nothing>("redis:7").apply {
        withExposedPorts(6379)
    }

    @Before
    fun startContainer() {
        redis.start()

        // Seed db 0 with a key that must survive
        val host = redis.host
        val port = redis.firstMappedPort
        newClient(Endpoint.from("$host:$port")).use { cl ->
            runBlocking {
                cl.select(0UL)
                cl.set("db0_key", "should_survive")
                cl.select(TARGET_DB.toULong())
                cl.set("existing_key", "should_be_flushed")
            }
        }
    }

    @After
    fun stopContainer() {
        System.clearProperty("SPECS_SUT_START")
        System.clearProperty("TEST_NS_OWNER")
        redis.stop()
    }

    @Test
    fun `start with reset gate unset flushes target db, leaves db 0 intact, sets ns owner`() {
        System.clearProperty("SPECS_SUT_START") // gate absent → shouldReset = true
        System.setProperty("TEST_NS_OWNER", "test-room")

        val host = redis.host
        val port = redis.firstMappedPort

        val props = mapOf(
            "env.redis.host" to host,
            "env.redis.port" to port.toString(),
            "env.redis.database" to TARGET_DB.toString()
        )

        val system = resettableRedis(props)
        system.start(false)

        newClient(Endpoint.from("$host:$port")).use { cl ->
            runBlocking {
                // db 0 should still have db0_key
                cl.select(0UL)
                assertEquals("should_survive", cl.get("db0_key"))

                // target db should be empty except for __ns_owner__
                cl.select(TARGET_DB.toULong())
                assertNull("existing_key should be flushed", cl.get("existing_key"))
                val nsOwner = cl.get("__ns_owner__")
                assertEquals("test-room port=$TARGET_DB", nsOwner)
            }
        }
    }

    @Test
    fun `start with SPECS_SUT_START=false is connect-only and data survives`() {
        System.setProperty("SPECS_SUT_START", "false")

        val host = redis.host
        val port = redis.firstMappedPort

        val props = mapOf(
            "env.redis.host" to host,
            "env.redis.port" to port.toString(),
            "env.redis.database" to TARGET_DB.toString()
        )

        val system = resettableRedis(props)
        system.start(false)

        newClient(Endpoint.from("$host:$port")).use { cl ->
            runBlocking {
                cl.select(TARGET_DB.toULong())
                assertEquals("should_be_flushed", cl.get("existing_key"))
            }
        }
    }

    companion object {
        private const val TARGET_DB = 5
    }
}
