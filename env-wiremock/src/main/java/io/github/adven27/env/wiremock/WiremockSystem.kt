package io.github.adven27.env.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import io.github.adven27.env.core.Environment.Companion.findAvailableTcpPort
import io.github.adven27.env.core.Environment.Companion.setProperties
import io.github.adven27.env.core.GenericExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle

class WiremockSystem @JvmOverloads constructor(
    val server: WireMockServer,
    afterStart: WireMockServer.() -> Unit = { }
) :
    GenericExternalSystem<WireMockServer>(
        system = server,
        start = { it.start(); it.afterStart() },
        stop = { it.stop() },
        running = { it.isRunning }
    ) {

    constructor(afterStart: WireMockServer.() -> Unit) : this(afterStart = afterStart, fixedPort = 8888)

    @JvmOverloads
    constructor(
        portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
        fixedPort: Int = 8888,
        afterStart: WireMockServer.() -> Unit = { }
    ) : this(
        server = WireMockServer(
            wireMockConfig().withRootDirectory(WiremockSystem::class.java.getResource("/wiremock").path)
                .extensions(ResponseTemplateTransformer(true)).port(
                    port(portsExposingStrategy, fixedPort).apply {
                        mapOf("env.wiremock.port" to this.toString()).setProperties()
                    }
                )
        ),
        afterStart = afterStart
    )

    override fun describe() = "${system.baseUrl()} registered ${system.listAllStubMappings().mappings.size} mappings."

    companion object {
        private fun port(strategy: PortsExposingStrategy, fixedPort: Int) =
            if (strategy.fixedPorts()) fixedPort else findAvailableTcpPort()
    }
}
