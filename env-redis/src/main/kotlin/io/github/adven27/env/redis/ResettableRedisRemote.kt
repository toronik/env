package io.github.adven27.env.redis

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.shouldReset
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.runBlocking

@Suppress("unused")
@JvmName("resettableRedisSystem")
fun resettableRedis(props: Map<String, String>): RedisSystem = ResettableRedisRemote(props)

/** Keeps the 5.6.2 JVM signature for binaries compiled against it. */
@Suppress("unused")
@Deprecated("Binary compatibility only", level = DeprecationLevel.HIDDEN)
@JvmName("resettableRedis")
fun resettableRedisCompat(props: Map<String, String>): ExternalSystem = ResettableRedisRemote(props)

/**
 * Redis that is already running (e.g. a shared test stack): owns only the logical database `env.redis.database`.
 * Every call, including the [RedisSystem] data helpers, runs in that database.
 */
class ResettableRedisRemote(private val props: Map<String, String>) : RedisSystem {
    override val config: ExternalSystemConfig = ExternalSystemConfig(props)

    private val endpoint get() = Endpoint.from("${props.getValue("env.redis.host")}:${props.getValue("env.redis.port")}")
    private val database get() = props.getValue("env.redis.database").toULong()

    override fun start(fixedEnv: Boolean) {
        props.forEach(System::setProperty)
        if (!shouldReset()) return

        val nsOwner = System.getenv("TEST_NS_OWNER") ?: System.getProperty("TEST_NS_OWNER") ?: ""
        exec { cl ->
            runBlocking {
                cl.flushDb()
                cl.set(NS_OWNER_KEY, "$nsOwner port=${database.toLong()}")
            }
        }
    }

    override fun stop() = Unit

    override fun running() = true

    /**
     * Flushes only the namespaced logical database configured for this remote system.
     * Safe to call in [@BeforeExample] — does NOT flush other logical databases.
     */
    override fun clean(): Any = exec { runBlocking { it.flushDb() } }

    override fun <T> exec(f: (KredsClient) -> T): T = newClient(endpoint).use { cl ->
        runBlocking { cl.select(database) }
        f(cl)
    }

    /** Same as [RedisSystem.keys] but without the namespace owner marker, so specs see the same keys as in a container. */
    override fun keys(pattern: String, vararg types: String): Collection<String> =
        super.keys(pattern, *types).filter { it != NS_OWNER_KEY }

    companion object {
        private const val NS_OWNER_KEY = "__ns_owner__"
    }
}
