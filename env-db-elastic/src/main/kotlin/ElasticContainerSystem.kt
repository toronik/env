import io.github.adven27.env.container.asCompatibleSubstituteFor
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds
import javax.net.ssl.SSLContext

@Suppress("unused")
open class ElasticContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = DEFAULT_PORT,
    private val securityEnabled: Boolean = false,
    private val password: String = ELASTICSEARCH_DEFAULT_PASSWORD,
    private val afterStart: ElasticContainerSystem.() -> Unit = { }
) : ElasticsearchContainer(dockerImageName), ExternalSystem {

    override lateinit var config: Config

    override fun describe() = "ElasticSearch Container System: $config"
    override fun running() = isRunning

    override fun start(fixedEnv: Boolean) {
        if (!securityEnabled) {
            disableSecurity()
        } else {
            withPassword(password)
        }
        withStartupTimeout(ofSeconds(STARTUP_TIMEOUT))
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, DEFAULT_PORT)
        }
        start()
    }

    override fun start() {
        super.start()
        config = initConfig()
        apply(afterStart)
    }

    private fun initConfig() = if (securityEnabled) {
        Config(
            host,
            firstMappedPort.toString(),
            DEFAULT_USER,
            envMap["ELASTIC_PASSWORD"].toString(),
            createSslContextFromCa()
        )
    } else {
        Config(host, firstMappedPort.toString())
    }

    private fun disableSecurity() {
        withEnv(
            mapOf(
                // major version 8 is secured by default, so we need to manually disable authentication
                "xpack.security.enabled" to "false",
                "xpack.security.enrollment.enabled" to "true"
            )
        )
    }

    companion object : KLogging() {
        private const val DEFAULT_PORT = 9200
        private const val DEFAULT_USER = "elastic"
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE: DockerImageName =
            "elasticsearch:8.6.0" asCompatibleSubstituteFor "docker.elastic.co/elasticsearch/elasticsearch"
    }

    data class Config @JvmOverloads constructor(
        val host: String = "localhost",
        val port: String = DEFAULT_PORT.toString(),
        val user: String = DEFAULT_USER,
        val password: String = ELASTICSEARCH_DEFAULT_PASSWORD,
        val sslContext: SSLContext? = null
    ) :
        ExternalSystemConfig(
            HOST_PROP to host,
            PORT_PROP to port,
            USER_PROP to user,
            PASSWORD_PROP to password
        ) {

        fun hostAndPort() = "$host:$port"

        companion object {
            const val HOST_PROP = "env.db.elastic.host"
            const val PORT_PROP = "env.db.elastic.port"
            const val USER_PROP = "env.db.elastic.user"
            const val PASSWORD_PROP = "env.db.elastic.password"
        }
    }
}
