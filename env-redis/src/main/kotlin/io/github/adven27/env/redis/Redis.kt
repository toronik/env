package io.github.adven27.env.redis

import io.github.adven27.env.core.envRemoteOn
import org.testcontainers.utility.DockerImageName

const val TEST_REDIS_HOST = "TEST_REDIS_HOST"
const val TEST_REDIS_PORT = "TEST_REDIS_PORT"
const val TEST_REDIS_DB = "TEST_REDIS_DB"

/**
 * Redis picked by the [io.github.adven27.env.core.ENV_REMOTE] toggle.
 *
 * - `ENV_REMOTE=true`: logical database `TEST_REDIS_DB` (default 0) of an already running Redis
 *   (`TEST_REDIS_HOST`:`TEST_REDIS_PORT`, default `redis:6379`), flushed on start. The system also sets
 *   `spring.data.redis.database`, so a Spring application under test works in the same database without changes
 *   in its test configuration.
 * - otherwise: [RedisContainerSystem] from [image]; the container is created only in this branch.
 *
 * Both branches are a [RedisSystem]: data helpers (`keys`, `getMap`, `setMap`, ...) work the same way, and on
 * the remote stack every call runs in `TEST_REDIS_DB`.
 *
 * @param env source of `ENV_REMOTE` (the system property still wins) and `TEST_REDIS_*`
 */
@JvmOverloads
fun redis(
    image: DockerImageName = RedisContainerSystem.DEFAULT_IMAGE,
    env: (String) -> String? = System::getenv
): RedisSystem = if (envRemoteOn(System::getProperty, env)) {
    resettableRedis(remoteRedisProperties(env))
} else {
    RedisContainerSystem(image)
}

internal fun remoteRedisProperties(env: (String) -> String?): Map<String, String> {
    val database = env(TEST_REDIS_DB) ?: "0"
    return mapOf(
        "env.redis.host" to (env(TEST_REDIS_HOST) ?: "redis"),
        "env.redis.port" to (env(TEST_REDIS_PORT) ?: "6379"),
        "env.redis.database" to database,
        "spring.data.redis.database" to database
    )
}
