package io.github.adven27.env.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.common.SingleRootFileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension
import com.github.tomakehurst.wiremock.recording.RecordSpecBuilder
import io.github.adven27.env.core.Environment.Companion.findAvailableTcpPort
import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.GenericExternalSystem
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.DEFAULT_PORT
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.PROP_PORT
import wiremock.com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
import wiremock.com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
import wiremock.com.github.jknack.handlebars.Helper
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

open class WiremockSystem @JvmOverloads constructor(
    private val wireMockConfiguration: WireMockConfiguration,
    private val defaultPort: Int = DEFAULT_PORT,
    private val record: RecordSpecBuilder? = null,
    afterStart: WireMockServer.() -> Unit = { }
) : GenericExternalSystem<AtomicReference<WireMockServer?>, WiremockSystem.Config>(
    system = AtomicReference<WireMockServer?>(),
    start = { fixedEnv, system ->
        system.set(
            WireMockServer(
                wireMockConfiguration.port(
                    if (fixedEnv) {
                        defaultPort
                    } else {
                        findAvailableTcpPort().apply {
                            mapOf(PROP_PORT to this.toString()).propagateToSystemProperties()
                        }
                    }
                )
            )
        )
        with(system.get()!!) {
            configureJsonMapper()
            start()
            record?.let {
                val path = wireMockConfiguration.filesRoot().path
                val mappingsPath = "src/test/resources/$path/mappings"
                val filesPath = "src/test/resources/$path/__files"
                File(mappingsPath).mkdirs()
                File(filesPath).mkdirs()
                enableRecordMappings(SingleRootFileSource(mappingsPath), SingleRootFileSource(filesPath))
                startRecording(it)
            }
            afterStart()
            Config(port = port())
        }
    },
    stop = {
        it.get()?.apply {
            record?.let {
                stopRecording()
                saveMappings()
            }
            stop()
        }
    },
    running = { it.get()?.isRunning == true }
) {
    val client: WireMock by lazy { WireMock(config.host, config.port) }

    @Suppress("unused")
    @JvmOverloads
    constructor(
        helpers: Map<String, Helper<Any>> = mapOf(),
        afterStart: WireMockServer.() -> Unit = { },
        record: RecordSpecBuilder? = null,
        additionalConfiguration: WireMockConfiguration.() -> WireMockConfiguration = { this },
        fixedPort: Int = DEFAULT_PORT
    ) : this(
        wireMockConfiguration = additionalConfiguration(
            wireMockConfig()
                .usingFilesUnderClasspath("wiremock")
                .extensions(object : TemplateHelperProviderExtension {
                    override fun getName() = TEMPLATE_HELPER_EXTENSION_NAME
                    override fun provideTemplateHelpers() = helpers
                })
                .globalTemplating(true)
        ),
        defaultPort = fixedPort,
        record = record,
        afterStart = afterStart
    )

    @Suppress("unused")
    @JvmOverloads
    constructor(
        additionalConfiguration: WireMockConfiguration.() -> WireMockConfiguration,
        afterStart: WireMockServer.() -> Unit = { },
        record: RecordSpecBuilder? = null
    ) : this(mapOf(), afterStart, record, additionalConfiguration, DEFAULT_PORT)

    override fun describe() = with(system.get()) {
        "${this?.baseUrl()} registered ${this?.listAllStubMappings()?.mappings?.size} mappings. \n\t" +
            config.properties.entries.joinToString("\n\t") { it.toString() }
    }

    fun interactions() = client.serveEvents.sortedBy { it.request.loggedDate }
        .map {
            Interaction(
                it.request.method.value(),
                it.request.url,
                it.request.bodyAsString,
                it.response.bodyAsString,
                it.request.loggedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                it.response.status
            )
        }

    fun popInteractions() = interactions().also { cleanInteractions() }
    fun cleanInteractions() = client.resetRequests()

    data class Config(val host: String = DEFAULT_HOST, val port: Int = DEFAULT_PORT) : ExternalSystemConfig(
        PROP_PORT to port.toString(),
        PROP_HOST to host
    ) {
        companion object {
            const val PROP_PORT = "env.wiremock.port"
            const val PROP_HOST = "env.wiremock.host"
            const val DEFAULT_PORT = 8888
            const val DEFAULT_HOST = "localhost"
        }
    }

    companion object {
        private const val TEMPLATE_HELPER_EXTENSION_NAME = "wiremock-system-template-helper-extension"

        private fun configureJsonMapper() {
            Json.getObjectMapper()
                .configure(USE_BIG_DECIMAL_FOR_FLOATS, true)
                .configure(WRITE_BIGDECIMAL_AS_PLAIN, true)
        }
    }
}
