package io.github.adven27.env.redis

import io.github.adven27.env.core.ExternalSystem

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
 * Contract:
 * - [RedisContainerSystem.clean] flushes the entire Redis instance (FLUSHALL).
 * - [ResettableRedisRemote.clean] flushes only its namespaced logical database (FLUSHDB),
 *   leaving other databases intact.
 */
interface RedisSystem : ExternalSystem {
    fun clean(): Any
}
