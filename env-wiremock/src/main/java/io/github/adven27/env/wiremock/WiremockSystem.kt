package io.github.adven27.env.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import io.github.adven27.env.core.Environment.Companion.findAvailableTcpPort
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.FixedDynamicEnvironmentStrategy
import io.github.adven27.env.core.FixedDynamicEnvironmentStrategy.SystemPropertyToggle
import io.github.adven27.env.core.GenericExternalSystem
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.DEFAULT_PORT
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.PROP_PORT
import wiremock.com.github.jknack.handlebars.Helper

open class WiremockSystem @JvmOverloads constructor(
    private val server: WireMockServer,
    afterStart: WireMockServer.() -> Unit = { }
) : GenericExternalSystem<WireMockServer>(
    system = server,
    start = { it.start(); it.afterStart() },
    stop = { it.stop() },
    running = { it.isRunning }
) {
    @Suppress("unused")
    constructor(afterStart: WireMockServer.() -> Unit) : this(afterStart = afterStart, fixedPort = 8888)

    @Suppress("unused")
    @JvmOverloads
    constructor(
        wireMockConfiguration: WireMockConfiguration,
        afterStart: WireMockServer.() -> Unit = { }
    ) : this(server = WireMockServer(wireMockConfiguration), afterStart = afterStart)

    @Suppress("unused")
    @JvmOverloads
    constructor(
        helpers: Map<String, Helper<Any>> = mapOf(),
        afterStart: WireMockServer.() -> Unit = { },
        additionalConfiguration: WireMockConfiguration.() -> WireMockConfiguration = { this },
        fixedPort: Int = DEFAULT_PORT,
        fixedDynamicEnvironmentStrategy: FixedDynamicEnvironmentStrategy = SystemPropertyToggle()
    ) : this(
        server = WireMockServer(
            additionalConfiguration.invoke(
                wireMockConfig()
                    .usingFilesUnderClasspath("wiremock")
                    .extensions(ResponseTemplateTransformer(true, helpers))
                    .port(
                        port(fixedDynamicEnvironmentStrategy, fixedPort).apply {
                            mapOf(PROP_PORT to this.toString()).propagateToSystemProperties()
                        }
                    )
            )
        ),
        afterStart = afterStart
    )

    @Suppress("unused")
    constructor(
        additionalConfiguration: WireMockConfiguration.() -> WireMockConfiguration,
        afterStart: WireMockServer.() -> Unit = { },
    ) : this(mapOf(), afterStart, additionalConfiguration, DEFAULT_PORT, SystemPropertyToggle())

    override fun describe() =
        "${system.baseUrl()} registered ${system.listAllStubMappings().mappings.size} mappings. \n\t" +
            config().asMap().entries.joinToString("\n\t") { it.toString() }

    override fun config(): Config = Config(server.port())

    data class Config(val port: Int = DEFAULT_PORT) {
        companion object {
            const val PROP_PORT = "env.wiremock.port"
            const val DEFAULT_PORT = 8888
        }

        fun asMap() = mapOf(PROP_PORT to port)
    }

    companion object {
        private fun port(strategy: FixedDynamicEnvironmentStrategy, fixedPort: Int) =
            if (strategy.fixedEnv()) fixedPort else findAvailableTcpPort()
    }
}
