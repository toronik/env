package redis

import io.github.adven27.env.redis.RedisSystem
import io.github.adven27.env.redis.ResettableRedisRemote
import io.github.adven27.env.redis.resettableRedis
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import java.util.UUID

/**
 * Runs against a Testcontainers Redis by default.
 * With `ENV_REMOTE=true` runs against an already running Redis (`TEST_REDIS_HOST`/`TEST_REDIS_PORT`,
 * default `redis:6379`) in the logical database `TEST_REDIS_DB` — the way a shared test stack provides it.
 * The "other" database (0) is then shared, so the test only writes there a uniquely named guard key
 * and removes it afterwards.
 */
@Suppress("TooManyFunctions")
class ResettableRedisRemoteTest {

    private val remote = System.getenv("ENV_REMOTE")?.toBoolean() == true

    private val redis = if (remote) {
        null
    } else {
        GenericContainer<Nothing>("redis:7").apply { withExposedPorts(6379) }
    }

    private val host get() = redis?.host ?: System.getenv("TEST_REDIS_HOST") ?: "redis"
    private val port get() = redis?.firstMappedPort ?: System.getenv("TEST_REDIS_PORT")?.toInt() ?: 6379
    private val targetDb = if (remote) System.getenv("TEST_REDIS_DB")?.toInt() ?: TARGET_DB else TARGET_DB
    private val guardKey = "env_test_guard_${UUID.randomUUID()}"

    private val props get() = mapOf(
        "env.redis.host" to host,
        "env.redis.port" to port.toString(),
        "env.redis.database" to targetDb.toString()
    )

    @Before
    fun startContainer() {
        redis?.start()

        // Seed db 0 with a key that must survive
        raw {
            select(OTHER_DB.toULong())
            set(guardKey, "should_survive")
            select(targetDb.toULong())
            set("existing_key", "should_be_flushed")
        }
    }

    @After
    fun stopContainer() {
        System.clearProperty("SPECS_SUT_START")
        System.clearProperty("TEST_NS_OWNER")
        raw {
            select(OTHER_DB.toULong())
            del(guardKey)
            select(targetDb.toULong())
            flushDb()
        }
        redis?.stop()
    }

    @Test
    fun `start with reset gate unset flushes target db, leaves db 0 intact, sets ns owner`() {
        System.clearProperty("SPECS_SUT_START") // gate absent → shouldReset = true
        System.setProperty("TEST_NS_OWNER", "test-room")

        val system = resettableRedis(props)
        system.start(false)

        raw {
            // db 0 should still have the guard key
            select(OTHER_DB.toULong())
            assertEquals("should_survive", get(guardKey))

            // target db should be empty except for __ns_owner__
            select(targetDb.toULong())
            assertNull("existing_key should be flushed", get("existing_key"))
            val nsOwner = get("__ns_owner__")
            // the environment variable wins over the system property (a shared test stack sets it)
            val expectedOwner = System.getenv("TEST_NS_OWNER") ?: "test-room"
            assertEquals("$expectedOwner port=$targetDb", nsOwner)
        }
    }

    @Test
    fun `start with SPECS_SUT_START=false is connect-only and data survives`() {
        System.setProperty("SPECS_SUT_START", "false")

        val system = resettableRedis(props)
        system.start(false)

        raw {
            select(targetDb.toULong())
            assertEquals("should_be_flushed", get("existing_key"))
        }
    }

    @Test
    fun `resettable redis implements RedisSystem and clean flushes only its db`() {
        // Regression for #4: spec authors call env<RedisSystem>().clean() in @BeforeExample.
        // ResettableRedisRemote must flush only its logical DB (not all DBs).
        val system = started()

        // Seed: add keys to both the target DB and db 0 after start
        raw {
            select(OTHER_DB.toULong())
            set(guardKey, "must_survive")
            select(targetDb.toULong())
            set("target_key", "will_be_cleaned")
        }

        system.clean()

        raw {
            // db 0 must be untouched
            select(OTHER_DB.toULong())
            assertEquals("db 0 must survive clean()", "must_survive", get(guardKey))

            // target DB must be empty (clean flushes its db only)
            select(targetDb.toULong())
            assertNull("target_key must be gone after clean()", get("target_key"))
        }
    }

    @Test
    fun `resettableRedis returns RedisSystem and the class is public`() {
        val system: RedisSystem = resettableRedis(props)
        assertTrue(system is ResettableRedisRemote)

        val direct: RedisSystem = ResettableRedisRemote(props)
        direct.start(false)
        direct.set("k", "v")
        assertEquals("v", direct.get("k"))
    }

    @Test
    fun `helpers write to and read from its own db, db 0 is not touched`() {
        val system = started()

        system.set("k", "v")
        system.setMap("map", mapOf("a" to "1"))
        system.setList("list", "1")

        raw {
            select(targetDb.toULong())
            assertEquals("v", get("k"))
            assertEquals(listOf("a", "1"), hgetAll("map"))
            assertEquals(listOf("1"), lrange("list", 0, -1))

            select(OTHER_DB.toULong())
            assertNull(get("k"))
            assertEquals(0L, exists("map", "list"))
            assertEquals("should_survive", get(guardKey))
        }

        raw {
            select(OTHER_DB.toULong())
            set("other_db_only", "x")
        }
        try {
            assertNull("helpers must not read db 0", system.get("other_db_only"))
        } finally {
            raw {
                select(OTHER_DB.toULong())
                del("other_db_only")
            }
        }
    }

    @Test
    fun `set and get`() {
        val system = started()

        assertEquals("OK", system.set("k", "v"))
        assertEquals("v", system.get("k"))
        assertNull(system.get("absent"))
    }

    @Test
    fun `setMap with map and with field, getMap`() {
        val system = started()

        val map = mapOf("a" to "1", "b" to "2")
        assertEquals(2L, system.setMap("map", map))
        assertEquals(map, system.getMap("map"))

        assertEquals(1L, system.setMap("map", "c", "3"))
        assertEquals(map + ("c" to "3"), system.getMap("map"))
        assertEquals(emptyMap<String, String>(), system.getMap("absent"))
    }

    @Test
    fun `setList and getList with range`() {
        val system = started()

        assertEquals(3L, system.setList("list", "1", "2", "3"))
        assertEquals(listOf("1", "2", "3"), system.getList("list"))
        assertEquals(listOf("2"), system.getList("list", 1, 1))
    }

    @Test
    fun `type of keys`() {
        val system = started()
        system.set("k", "v")
        system.setMap("map", "a", "1")
        system.setList("list", "1")

        assertEquals("string", system.type("k"))
        assertEquals("hash", system.type("map"))
        assertEquals("list", system.type("list"))
        assertEquals("none", system.type("absent"))
    }

    @Test
    fun `keys by pattern and types, sorted, without the ns owner marker`() {
        val system = started()
        system.set("k", "v")
        system.setMap("map", "a", "1")
        system.setList("list", "1")

        assertEquals(listOf("k", "list", "map"), system.keys())
        assertEquals(listOf("list"), system.keys("l*"))
        assertEquals(listOf("map"), system.keys(types = arrayOf("hash")))
        assertEquals(listOf("k", "map"), system.keys("*", "hash", "string"))
    }

    @Test
    fun `exists and del`() {
        val system = started()
        system.set("k", "v")
        system.setMap("map", "a", "1")

        assertEquals(2L, system.exists("k", "map", "absent"))
        assertTrue(system.exists("k"))

        assertEquals(1L, system.del("k"))
        assertFalse(system.exists("k"))
        assertTrue(system.exists("map"))
    }

    @Test
    fun `exec runs on its own db`() {
        val system = started()
        system.setMap("map", mapOf("a" to "1", "b" to "2"))

        assertEquals(listOf("a", "b"), system.exec { runBlocking { it.hkeys("map") } })
    }

    private fun started(): RedisSystem = resettableRedis(props).also { it.start(false) }

    private fun raw(block: suspend KredsClient.() -> Unit) =
        newClient(Endpoint.from("$host:$port")).use { cl -> runBlocking { cl.block() } }

    companion object {
        private const val TARGET_DB = 5
        private const val OTHER_DB = 0
    }
}
