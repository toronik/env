package io.github.adven27.env.redis

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds

@Suppress("TooManyFunctions", "SpreadOperator", "unused")
open class RedisContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val afterStart: RedisContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    private val client: KredsClient by lazy { newClient(Endpoint.from("""${config.host}:${config.port}""")) }

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: RedisContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
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

    fun <T> exec(f: (KredsClient) -> T) = client.use(f)

    fun keys(pattern: String = "*", vararg types: String): Collection<String> = exec { cl ->
        runBlocking {
            cl.keys(pattern)
                .let { keys -> if (types.isEmpty()) keys else keys.filter { cl.type(it) in types } }
                .sorted()
        }
    }

    fun clean(): String = exec { runBlocking { it.flushAll() } }

    fun type(key: String): String = exec { runBlocking { it.type(key) } }
    fun del(vararg keys: String) = exec { runBlocking { it.del(*keys) } }
    fun exists(vararg keys: String) = exec { runBlocking { it.exists(*keys) } }
    fun exists(key: String) = exec { runBlocking { it.exists(key) == 1L } }
    fun getMap(key: String): Map<String, String> =
        exec { runBlocking { it.hgetAll(key).chunked(2).associate { it[0] to it[1] } } }

    fun setMap(key: String, field: String, value: String): Long = exec { runBlocking { it.hset(key, field to value) } }
    fun setMap(key: String, map: Map<String, String>): Long =
        exec { cl -> runBlocking { map.toList().let { cl.hset(key, it.first(), *it.drop(1).toTypedArray()) } } }

    fun getList(key: String, start: Int = 0, stop: Int = -1) = exec { runBlocking { it.lrange(key, start, stop) } }
    fun setList(key: String, vararg values: String) =
        exec { runBlocking { it.rpush(key, values.first(), *values.drop(1).toTypedArray()) } }

    fun get(key: String): String? = exec { runBlocking { it.get(key) } }
    fun set(key: String, value: String): String? = exec { runBlocking { it.set(key, value) } }

    data class Config @JvmOverloads constructor(val host: String = "localhost", val port: Int = PORT) :
        ExternalSystemConfig("env.redis.host" to host, "env.redis.port" to port.toString())

    companion object : KLogging() {
        private const val PORT = 6379
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE = "redis".parseImage()
    }
}
