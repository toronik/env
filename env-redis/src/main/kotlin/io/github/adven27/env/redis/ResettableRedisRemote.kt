package io.github.adven27.env.redis

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.runBlocking

@Suppress("unused")
fun resettableRedis(props: Map<String, String>): ExternalSystem = ResettableRedisRemote(props)

private fun shouldReset() = System.getProperty("SPECS_SUT_START")?.toBoolean() != false

private class ResettableRedisRemote(private val props: Map<String, String>) : ExternalSystem {
    override val config: ExternalSystemConfig = ExternalSystemConfig(props)

    override fun start(fixedEnv: Boolean) {
        props.forEach(System::setProperty)
        if (!shouldReset()) return

        val host = props.getValue("env.redis.host")
        val port = props.getValue("env.redis.port")
        val database = props.getValue("env.redis.database").toULong()
        val nsOwner = System.getenv("TEST_NS_OWNER") ?: System.getProperty("TEST_NS_OWNER") ?: ""

        newClient(Endpoint.from("$host:$port")).use { cl ->
            runBlocking {
                cl.select(database)
                cl.flushDb()
                cl.set("__ns_owner__", "$nsOwner port=${database.toLong()}")
            }
        }
    }

    override fun stop() = Unit

    override fun running() = true
}
