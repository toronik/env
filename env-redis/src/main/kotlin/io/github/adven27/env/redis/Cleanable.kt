package io.github.adven27.env.redis

/**
 * Shared interface for redis systems that support per-example flushing.
 *
 * A spec author can call `env<Cleanable>().clean()` in `@BeforeExample` regardless of whether
 * the environment runs a local container ([RedisContainerSystem]) or a remote resettable system.
 *
 * Contract:
 * - [RedisContainerSystem.clean] flushes the entire Redis instance (FLUSHALL).
 * - [ResettableRedisRemote.clean] flushes only its namespaced logical database (FLUSHDB),
 *   leaving other databases intact.
 */
interface Cleanable {
    fun clean(): Any
}
