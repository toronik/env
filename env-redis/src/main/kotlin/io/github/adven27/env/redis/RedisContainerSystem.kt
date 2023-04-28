package io.github.adven27.env.redis

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis
import java.time.Duration.ofSeconds

@Suppress("TooManyFunctions", "unused")
open class RedisContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val afterStart: RedisContainerSystem.() -> Unit = { },
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    private val jedis: Jedis by lazy { Jedis(config.host, config.port) }

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: RedisContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart,
    )

    override fun start(fixedEnv: Boolean) {
        withExposedPorts(PORT)
        withStartupTimeout(ofSeconds(STARTUP_TIMEOUT))
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, PORT)
        }
        start()
    }

    override fun start() {
        super.start()
        config = Config(host, firstMappedPort)
        apply(afterStart)
    }

    override fun running() = isRunning

    fun keys(pattern: String = "*", vararg types: String): Collection<String> = jedis.keys(pattern).let { keys ->
        if (types.isEmpty()) keys else keys.filter { jedis.type(it) in types }
    }

    fun clean(): String = jedis.flushAll()
    fun type(key: String): String = jedis.type(key)
    fun del(vararg keys: String) = jedis.del(*keys)
    fun exists(vararg keys: String) = jedis.exists(*keys)
    fun exists(key: String) = jedis.exists(key)
    fun getMap(key: String): Map<String, String> = jedis.hgetAll(key)
    fun setMap(key: String, field: String, value: String): Long = jedis.hset(key, field, value)
    fun setMap(key: String, map: Map<String, String>): Long = jedis.hset(key, map)
    fun getList(key: String, start: Long = 0, stop: Long = -1) = jedis.lrange(key, start, stop) ?: emptyList()
    fun setList(key: String, vararg values: String) = jedis.lpush(key, *values)
    fun get(key: String): String = jedis.get(key)
    fun set(key: String, value: String): String = jedis.set(key, value)

    data class Config @JvmOverloads constructor(val host: String = "localhost", val port: Int = PORT) :
        ExternalSystemConfig("env.redis.host" to host, "env.redis.port" to port.toString())

    companion object : KLogging() {
        private const val PORT = 6379
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE = "redis".parseImage()
    }
}
