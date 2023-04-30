package io.github.adven27.env.wiremock

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.Json
import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.BindMode.READ_ONLY
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import wiremock.com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
import wiremock.com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
import java.lang.System.getenv
import java.time.Duration

@Suppress("TooManyFunctions", "unused")
open class WiremockContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val afterStart: WiremockContainerSystem.() -> Unit = { }
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {

    companion object : KLogging() {
        private const val PORT = 8080
        private const val STARTUP_TIMEOUT = 30L

        @JvmField
        val DEFAULT_IMAGE = "wiremock/wiremock".parseImage()

        private fun configureJsonMapper() {
            Json.getObjectMapper()
                .configure(USE_BIG_DECIMAL_FOR_FLOATS, true)
                .configure(WRITE_BIGDECIMAL_AS_PLAIN, true)
        }
    }

    override lateinit var config: Config

    val client: WireMock by lazy { WireMock(config.host, config.port) }

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: WiremockContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    override fun start(fixedEnv: Boolean) {
        withExposedPorts(PORT)
        withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT))
        if (fixedEnv) addFixedExposedPort(defaultPort, PORT)
        start()
    }

    override fun start() {
        withClasspathResourceMapping("wiremock", "/home/wiremock", READ_ONLY)
        super.start()
        config = Config(host, firstMappedPort)
        apply(afterStart)
    }

    override fun running() = isRunning

    data class Config @JvmOverloads constructor(
        val host: String = "localhost",
        val port: Int = PORT
    ) : ExternalSystemConfig(
        "env.wiremock.host" to host,
        "env.wiremock.external-host" to (if (getenv("CI") == null) host else "host.docker.internal"),
        "env.wiremock.port" to port.toString()
    )

    fun interactions() = client.serveEvents.sortedBy { it.request.loggedDate }
        .map { Interaction(it.request.url, it.request.bodyAsString, it.response.bodyAsString) }

    fun popInteractions() = interactions().also { cleanInteractions() }
    fun cleanInteractions() = client.resetRequests()

    data class Interaction(val url: String, val req: String, val resp: String)
}
