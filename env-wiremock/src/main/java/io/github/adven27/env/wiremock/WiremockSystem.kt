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
import wiremock.com.github.jknack.handlebars.Helper

open class WiremockSystem @JvmOverloads constructor(
    val server: WireMockServer,
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
        fixedDynamicEnvironmentStrategy: FixedDynamicEnvironmentStrategy = SystemPropertyToggle(),
        fixedPort: Int = 8888,
        afterStart: WireMockServer.() -> Unit = { }
    ) : this(
        server = WireMockServer(
            wireMockConfig()
                .withRootDirectory(WiremockSystem::class.java.getResource("/wiremock").path)
                .extensions(ResponseTemplateTransformer(true, helpers))
                .port(
                    port(fixedDynamicEnvironmentStrategy, fixedPort).apply {
                        mapOf("env.wiremock.port" to this.toString()).propagateToSystemProperties()
                    }
                )
        ),
        afterStart = afterStart
    )

    override fun describe() = "${system.baseUrl()} registered ${system.listAllStubMappings().mappings.size} mappings."
    override fun config(): Any = Config(server.port())

    data class Config(val port: Int = 8888)

    companion object {
        private fun port(strategy: FixedDynamicEnvironmentStrategy, fixedPort: Int) =
            if (strategy.fixedEnv()) fixedPort else findAvailableTcpPort()
    }
}
