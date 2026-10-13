package io.github.adven27.env.redis

import io.github.adven27.env.core.ExternalSystem
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import kotlinx.coroutines.runBlocking

/**
 * Type-safe interface for all Redis systems in an [io.github.adven27.env.core.Environment].
 *
 * Because [RedisSystem] extends [ExternalSystem], consumers can resolve any redis system
 * without a cast:
 *
 * ```kotlin
 * // By type — works when there is exactly one redis system in the environment
 * env.env<RedisSystem>().clean()
 *
 * // By name — works even when there are multiple redis systems
 * env.env<RedisSystem>("REDIS").clean()
 * ```
 *
 * Data helpers ([keys], [getMap], [setMap], ...) are built on [exec] and behave the same
 * for every implementation, so specs work with `env<RedisSystem>()` both against a container and a remote Redis.
 *
 * Contract:
 * - [RedisContainerSystem.clean] flushes the entire Redis instance (FLUSHALL).
 * - [ResettableRedisRemote.clean] flushes only its namespaced logical database (FLUSHDB),
 *   leaving other databases intact; its [exec] always runs in that database.
 */
@Suppress("TooManyFunctions", "SpreadOperator")
interface RedisSystem : ExternalSystem {
    fun clean(): Any

    /** Runs [f] with a client connected to the database this system owns. */
    fun <T> exec(f: (KredsClient) -> T): T

    fun keys(pattern: String = "*", vararg types: String): Collection<String> = exec { cl ->
        runBlocking {
            cl.keys(pattern)
                .let { keys -> if (types.isEmpty()) keys else keys.filter { cl.type(it) in types } }
                .sorted()
        }
    }

    fun type(key: String): String = exec { runBlocking { it.type(key) } }
    fun del(vararg keys: String): Long = exec { runBlocking { it.del(*keys) } }
    fun exists(vararg keys: String): Long = exec { runBlocking { it.exists(*keys) } }
    fun exists(key: String): Boolean = exec { runBlocking { it.exists(key) == 1L } }
    fun getMap(key: String): Map<String, String> =
        exec { runBlocking { it.hgetAll(key).chunked(2).associate { it[0] to it[1] } } }

    fun setMap(key: String, field: String, value: String): Long = exec { runBlocking { it.hset(key, field to value) } }
    fun setMap(key: String, map: Map<String, String>): Long =
        exec { cl -> runBlocking { map.toList().let { cl.hset(key, it.first(), *it.drop(1).toTypedArray()) } } }

    fun getList(key: String, start: Int = 0, stop: Int = -1): List<String> =
        exec { runBlocking { it.lrange(key, start, stop) } }

    fun setList(key: String, vararg values: String): Long =
        exec { runBlocking { it.rpush(key, values.first(), *values.drop(1).toTypedArray()) } }

    fun get(key: String): String? = exec { runBlocking { it.get(key) } }
    fun set(key: String, value: String): String? = exec { runBlocking { it.set(key, value) } }
}
