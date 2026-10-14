package io.github.adven27.env.redis

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds

@Suppress("TooManyFunctions", "SpreadOperator", "unused")
open class RedisContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val afterStart: RedisContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), RedisSystem {
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

    override fun <T> exec(f: (KredsClient) -> T) = client.use(f)

    override fun clean(): String = exec { runBlocking { it.flushAll() } }

    data class Config @JvmOverloads constructor(val host: String = "localhost", val port: Int = PORT) :
        ExternalSystemConfig("env.redis.host" to host, "env.redis.port" to port.toString())

    companion object {
        private const val PORT = 6379
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE = "redis".parseImage()
    }
}
