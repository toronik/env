package io.github.adven27.env.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import io.github.adven27.env.core.Environment.Companion.findAvailableTcpPort
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.GenericExternalSystem
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.DEFAULT_PORT
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.PROP_PORT
import wiremock.com.github.jknack.handlebars.Helper
import java.util.concurrent.atomic.AtomicReference

open class WiremockSystem @JvmOverloads constructor(
    private val wireMockConfiguration: WireMockConfiguration,
    private val defaultPort: Int = DEFAULT_PORT,
    afterStart: WireMockServer.() -> Unit = { }
) : GenericExternalSystem<AtomicReference<WireMockServer?>, WiremockSystem.Config>(
    system = AtomicReference<WireMockServer?>(),
    config = Config(),
    start = { fixedEnv, system ->
        system.set(
            WireMockServer(
                wireMockConfiguration.port(
                    if (fixedEnv) defaultPort else findAvailableTcpPort().apply {
                        mapOf(PROP_PORT to this.toString()).propagateToSystemProperties()
                    }
                )
            )
        )
        with(system.get()!!) {
            start()
            afterStart()
            Config(port())
        }
    },
    stop = { it.get()?.stop() },
    running = { it.get()?.isRunning == true }
) {
    private lateinit var server: WireMockServer

    @Suppress("unused")
    constructor(afterStart: WireMockServer.() -> Unit) : this(afterStart = afterStart, fixedPort = DEFAULT_PORT)

    @Suppress("unused")
    @JvmOverloads
    constructor(
        helpers: Map<String, Helper<Any>> = mapOf(),
        afterStart: WireMockServer.() -> Unit = { },
        additionalConfiguration: WireMockConfiguration.() -> WireMockConfiguration = { this },
        fixedPort: Int = DEFAULT_PORT,
    ) : this(
        wireMockConfiguration = additionalConfiguration.invoke(
            wireMockConfig()
                .usingFilesUnderClasspath("wiremock")
                .extensions(ResponseTemplateTransformer(true, helpers))

        ),
        defaultPort = fixedPort,
        afterStart = afterStart
    )

    @Suppress("unused")
    constructor(
        additionalConfiguration: WireMockConfiguration.() -> WireMockConfiguration,
        afterStart: WireMockServer.() -> Unit = { }
    ) : this(mapOf(), afterStart, additionalConfiguration, DEFAULT_PORT)

    override fun describe() = with(system.get()) {
        "${this?.baseUrl()} registered ${this?.listAllStubMappings()?.mappings?.size} mappings. \n\t" +
            config().asMap().entries.joinToString("\n\t") { it.toString() }
    }

    data class Config(val port: Int = DEFAULT_PORT) {
        companion object {
            const val PROP_PORT = "env.wiremock.port"
            const val DEFAULT_PORT = 8888
        }

        fun asMap() = mapOf(PROP_PORT to port)
    }
}
