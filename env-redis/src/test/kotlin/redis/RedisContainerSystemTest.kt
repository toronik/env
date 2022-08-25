package redis

import io.github.adven27.env.core.Environment
import io.github.adven27.env.mq.redis.RedisContainerSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedisContainerSystemTest {
    private val sut = SomeEnvironment()

    @Test
    fun fixedEnvironment() {
        System.setProperty("SPECS_ENV_FIXED", "true")

        sut.up()

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
        assertEquals(RedisContainerSystem.Config(), sut.redis().config)
        assertEquals("6379", System.getProperty("env.redis.port"))
    }

    @Test
    fun dynamicEnvironment() {
        System.setProperty("SPECS_ENV_FIXED", "false")

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
