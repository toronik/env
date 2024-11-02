package io.github.adven27.env.smb

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode.READ_WRITE
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds

private val logger = LoggerFactory.getLogger(SmbContainerSystem::class.java)

@Suppress("LongParameterList")
open class SmbContainerSystem(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPorts: Set<Int> = PORTS,
    private val share: String = "share",
    private val containerDir: String = "/share",
    private val fileSystemBind: String? = null,
    private val resourceBind: String? = null,
    private val domain: String = "",
    private val beforeStart: SmbContainerSystem.() -> Unit = { },
    private val afterStart: SmbContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    @Suppress("unused")
    @JvmOverloads
    constructor(image: DockerImageName = DEFAULT_IMAGE, afterStart: SmbContainerSystem.() -> Unit) : this(
        dockerImageName = image,
        afterStart = afterStart
    )

    @Suppress("SpreadOperator")
    override fun start(fixedEnv: Boolean) {
        withLogConsumer(Slf4jLogConsumer(logger).withPrefix("SMB"))
        withCommand("-u", "$USER;$PASS", "-s", "$share;$containerDir;yes;no;no;all")
        fileSystemBind?.let { withFileSystemBind(fileSystemBind, containerDir, READ_WRITE) }
        resourceBind?.let { withClasspathResourceMapping(resourceBind, containerDir, READ_WRITE) }
        withExposedPorts(*PORTS.toTypedArray())
        withStartupTimeout(ofSeconds(STARTUP_TIMEOUT))
        if (fixedEnv) {
            defaultPorts.zip(PORTS).onEach { (h, c) -> addFixedExposedPort(h, c) }
        }
        start()
    }

    override fun running() = isRunning

    override fun start() {
        apply(beforeStart)
        super.start()
        config = Config(host, firstMappedPort, domain = domain, share = share)
        apply(afterStart)
    }

    data class Config @JvmOverloads constructor(
        val host: String,
        val port: Int,
        val domain: String,
        val share: String,
        val username: String = USER,
        val password: String = PASS
    ) : ExternalSystemConfig(
        PROP_HOST to host,
        PROP_PORT to port.toString(),
        PROP_DOMAIN to domain,
        PROP_SHARE to share,
        PROP_USERNAME to username,
        PROP_PASSWORD to password
    ) {
        companion object {
            private const val PREFIX = "env.smb."
            const val PROP_HOST = "${PREFIX}host"
            const val PROP_PORT = "${PREFIX}port"
            const val PROP_DOMAIN = "${PREFIX}domain"
            const val PROP_USERNAME = "${PREFIX}username"
            const val PROP_PASSWORD = "${PREFIX}password"
            const val PROP_SHARE = "${PREFIX}share"
        }
    }

    companion object {
        private const val USER = "user"
        private const val PASS = "pass"
        private val PORTS = setOf(445, 139)
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE = "dperson/samba".parseImage()
    }
}
