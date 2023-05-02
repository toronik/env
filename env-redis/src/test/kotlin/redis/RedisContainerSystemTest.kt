package redis

import io.github.adven27.env.core.Environment
import io.github.adven27.env.core.EnvironmentStrategy
import io.github.adven27.env.redis.RedisContainerSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedisContainerSystemTest {
    private val sut = SomeEnvironment()

    @Test
    fun fixedEnvironment() {
        System.setProperty(EnvironmentStrategy.SystemPropertyToggle.ENV_FIXED, "true")

        sut.up()

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
        assertEquals(RedisContainerSystem.Config(), sut.redis().config)
        assertEquals("6379", System.getProperty("env.redis.port"))

        with(sut.redis()) {
            set("k", "v")
            assertEquals("v", get("k"))

            val map = mapOf("a" to "1", "b" to "2")
            setMap("map", map)
            assertEquals("hash", type("map"))
            assertEquals(map, getMap("map"))
            setMap("map", "c", "3")
            assertEquals(map + ("c" to "3"), getMap("map"))

            setList("list", "1", "2", "3")
            assertEquals("list", type("list"))
            assertEquals(listOf("1", "2", "3"), getList("list"))
            assertEquals(listOf("2"), getList("list", 1, 1))

            assertEquals(listOf("k", "list", "map"), keys())
            assertEquals(listOf("list"), keys("l*"))
            assertEquals(listOf("map"), keys(types = arrayOf("hash")))

            assertEquals(listOf("a", "b", "c"), exec { runBlocking { it.hkeys("map") } })

            assertEquals(3, exists("k", "map", "list"))
            del("k")
            assertEquals(false, exists("k"))
            clean()
            assertEquals(0, exists("k", "map", "list"))
        }
    }

    @Test
    fun dynamicEnvironment() {
        sut.up()

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
    }

    @After
    fun tearDown() {
        sut.down()
    }
}

class SomeEnvironment : Environment("REDIS" to RedisContainerSystem()) {
    fun redis() = env<RedisContainerSystem>()
}
