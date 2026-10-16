package redis

import io.github.adven27.env.redis.RedisContainerSystem
import io.github.adven27.env.redis.RedisSystem
import io.github.adven27.env.redis.ResettableRedisRemote
import io.github.adven27.env.redis.redis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.testcontainers.DockerClientFactory

/**
 * `redis()` picks its branch by `ENV_REMOTE`. The coordinates tests need no Docker and no server;
 * the live tests run where their stack is: against a running Redis with `ENV_REMOTE=true`
 * (`TEST_REDIS_HOST`/`TEST_REDIS_PORT`/`TEST_REDIS_DB`), in a container otherwise (when Docker is available).
 */
class RedisFactoryTest {
    private val remote = mapOf("ENV_REMOTE" to "true", "TEST_REDIS_DB" to "12")
    private val local = remote - "ENV_REMOTE"

    @After
    fun clearSpringProperty() {
        System.clearProperty("spring.data.redis.database")
    }

    @Test
    fun `remote branch owns its logical database and points spring at it`() {
        val system = redis(env = remote::get)

        assertTrue(system is ResettableRedisRemote)
        assertEquals(
            mapOf(
                "env.redis.host" to "redis",
                "env.redis.port" to "6379",
                "env.redis.database" to "12",
                "spring.data.redis.database" to "12"
            ),
            system.config.properties
        )
    }

    @Test
    fun `remote host, port and database can be overridden, database defaults to 0`() {
        val props = redis(env = mapOf("ENV_REMOTE" to "true", "TEST_REDIS_HOST" to "h", "TEST_REDIS_PORT" to "1")::get)
            .config.properties

        assertEquals("h", props["env.redis.host"])
        assertEquals("1", props["env.redis.port"])
        assertEquals("0", props["env.redis.database"])
        assertEquals("0", props["spring.data.redis.database"])
    }

    @Test
    fun `without the toggle it is a container and TEST_REDIS_ are ignored`() {
        assertTrue(redis(env = local::get) is RedisContainerSystem)
        assertTrue(redis(env = (local + ("ENV_REMOTE" to "false"))::get) is RedisContainerSystem)
    }

    @Test
    fun `remote stack - data helpers work in its own database and spring points at it`() {
        assumeTrue("needs ENV_REMOTE=true", envRemote())
        val database = (System.getenv("TEST_REDIS_DB") ?: "0").toInt()
        val neighbour = redis(env = { if (it == "TEST_REDIS_DB") "${(database + 1) % DATABASES}" else System.getenv(it) })

        val system = redis().also { it.start(false) }

        assertEquals(database.toString(), System.getProperty("spring.data.redis.database"))
        system.dataHelpersAsserts()
        assertTrue(
            "the owner marker is there, so keys() hides it rather than finding an empty database",
            "__ns_owner__" in system.exec { runBlocking { it.keys("*") } }
        )
        assertEquals("a key of TEST_REDIS_DB leaked into another database", emptyMap<String, String>(), neighbour.getMap("quote:1"))
        system.clean()
    }

    @Test
    fun `containers - the same call starts Testcontainers`() {
        assumeTrue("needs Docker", !envRemote() && DockerClientFactory.instance().isDockerAvailable)

        val system = redis()
        system.start(false)
        try {
            system.dataHelpersAsserts()
        } finally {
            system.stop()
        }
    }

    private fun RedisSystem.dataHelpersAsserts() {
        setMap("quote:1", mapOf("price" to "101.5", "currency" to "RUB"))

        assertEquals(mapOf("price" to "101.5", "currency" to "RUB"), getMap("quote:1"))
        assertEquals(listOf("quote:1"), keys())
    }

    private fun envRemote() = System.getenv("ENV_REMOTE")?.toBoolean() == true

    companion object {
        private const val DATABASES = 16
    }
}
