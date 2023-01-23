import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@Suppress("unused")
open class MongoContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = DEFAULT_PORT,
    private val afterStart: MongoContainerSystem.() -> Unit = { },
) : MongoDBContainer(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: MongoContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart,
    )

    override fun start(fixedEnv: Boolean) {
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, DEFAULT_PORT)
        }
        start()
    }

    override fun start() {
        super.start()
        config = Config(host, firstMappedPort.toString())
        apply(afterStart)
    }

    override fun running() = isRunning

    data class Config @JvmOverloads constructor(
        val host: String,
        val port: String,
        val url: String = "mongodb://$host:$port",
    ) : ExternalSystemConfig(
        PROP_HOST to host,
        PROP_PORT to port,
        PROP_URL to url,
    ) {
        companion object {
            private const val PREFIX = "env.db.mongo."
            const val PROP_URL = "${PREFIX}url"
            const val PROP_HOST = "${PREFIX}host"
            const val PROP_PORT = "${PREFIX}port"
        }
    }

    companion object : KLogging() {
        @JvmField
        val DEFAULT_IMAGE = "mongo".parseImage()
        const val DEFAULT_PORT = 27017
    }
}
